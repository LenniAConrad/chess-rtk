/*
 * native/cuda/t5_cuda_jni.cu
 *
 * CUDA backend for the T5 tag-to-text pipeline.
 *
 * Exposes:
 *   - chess.nn.t5.cuda.Support.nativeDeviceCount() -> int
 *   - chess.nn.t5.cuda.Backend.nativeCreate(String) -> long
 *   - chess.nn.t5.cuda.Backend.nativeDestroy(long) -> void
 *   - chess.nn.t5.cuda.Backend.nativeGenerateIds(long, int[], int) -> int[]
 */

#include <jni.h>

#include <cuda_runtime.h>
#include <cuda_fp16.h>
#include <cuda_bf16.h>
#include <cublas_v2.h>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

static inline int device_count() {
    int count = 0;
    cudaError_t err = cudaGetDeviceCount(&count);
    if (err != cudaSuccess) return 0;
    return count;
}

extern "C" JNIEXPORT jint JNICALL Java_chess_nn_t5_cuda_Support_nativeDeviceCount(JNIEnv*, jclass) {
    return device_count();
}

static bool cuda_ok(cudaError_t err) {
    return err == cudaSuccess;
}

// -------------------------
// T5 CUDA backend (native)
// -------------------------

struct T5Config {
    int vocabSize = 0;
    int dModel = 0;
    int dKv = 0;
    int dFf = 0;
    int numLayers = 0;
    int numDecoderLayers = 0;
    int numHeads = 0;
    int relBuckets = 0;
    int relMaxDistance = 0;
    int padId = 0;
    int eosId = 0;
    int decoderStartId = 0;
    int unkId = 0;
    bool gatedGelu = false;
    float layerNormEps = 1e-6f;
};

struct HostTensor {
    std::vector<float> data;
    std::vector<int> shape;
};

struct HostModel {
    T5Config cfg;
    std::unordered_map<std::string, HostTensor> tensors;
};

struct DeviceBufferF {
    float* ptr = nullptr;
    size_t capacity = 0;

    bool ensure(size_t count) {
        if (count <= capacity) {
            return true;
        }
        if (ptr) {
            cudaFree(ptr);
            ptr = nullptr;
            capacity = 0;
        }
        if (!cuda_ok(cudaMalloc(reinterpret_cast<void**>(&ptr), sizeof(float) * count))) {
            return false;
        }
        capacity = count;
        return true;
    }

    void release() {
        if (ptr) {
            cudaFree(ptr);
            ptr = nullptr;
            capacity = 0;
        }
    }
};

struct DeviceBufferH {
    half* ptr = nullptr;
    size_t capacity = 0;

    bool ensure(size_t count) {
        if (count <= capacity) {
            return true;
        }
        if (ptr) {
            cudaFree(ptr);
            ptr = nullptr;
            capacity = 0;
        }
        if (!cuda_ok(cudaMalloc(reinterpret_cast<void**>(&ptr), sizeof(half) * count))) {
            return false;
        }
        capacity = count;
        return true;
    }

    void release() {
        if (ptr) {
            cudaFree(ptr);
            ptr = nullptr;
            capacity = 0;
        }
    }
};

struct DeviceBufferB {
    __nv_bfloat16* ptr = nullptr;
    size_t capacity = 0;

    bool ensure(size_t count) {
        if (count <= capacity) {
            return true;
        }
        if (ptr) {
            cudaFree(ptr);
            ptr = nullptr;
            capacity = 0;
        }
        if (!cuda_ok(cudaMalloc(reinterpret_cast<void**>(&ptr), sizeof(__nv_bfloat16) * count))) {
            return false;
        }
        capacity = count;
        return true;
    }

    void release() {
        if (ptr) {
            cudaFree(ptr);
            ptr = nullptr;
            capacity = 0;
        }
    }
};

struct DeviceBufferI {
    int* ptr = nullptr;
    size_t capacity = 0;

    bool ensure(size_t count) {
        if (count <= capacity) {
            return true;
        }
        if (ptr) {
            cudaFree(ptr);
            ptr = nullptr;
            capacity = 0;
        }
        if (!cuda_ok(cudaMalloc(reinterpret_cast<void**>(&ptr), sizeof(int) * count))) {
            return false;
        }
        capacity = count;
        return true;
    }

    void release() {
        if (ptr) {
            cudaFree(ptr);
            ptr = nullptr;
            capacity = 0;
        }
    }
};

struct AttentionWeights {
    float* wq = nullptr;
    float* wk = nullptr;
    float* wv = nullptr;
    float* wo = nullptr;
    half* wq16 = nullptr;
    half* wk16 = nullptr;
    half* wv16 = nullptr;
    half* wo16 = nullptr;
    __nv_bfloat16* wqbf = nullptr;
    __nv_bfloat16* wkbf = nullptr;
    __nv_bfloat16* wvbf = nullptr;
    __nv_bfloat16* wobf = nullptr;
};

struct FfnWeights {
    float* wi0 = nullptr;
    float* wi1 = nullptr;
    float* wo = nullptr;
    half* wi016 = nullptr;
    half* wi116 = nullptr;
    half* wo16 = nullptr;
    __nv_bfloat16* wi0bf = nullptr;
    __nv_bfloat16* wi1bf = nullptr;
    __nv_bfloat16* wobf = nullptr;
};

struct EncoderLayer {
    float* ln1 = nullptr;
    AttentionWeights attn;
    float* ln2 = nullptr;
    FfnWeights ffn;
};

struct DecoderLayer {
    float* ln1 = nullptr;
    AttentionWeights selfAttn;
    float* ln2 = nullptr;
    AttentionWeights crossAttn;
    float* ln3 = nullptr;
    FfnWeights ffn;
};

struct Workspace {
    DeviceBufferF x;
    DeviceBufferF norm;
    DeviceBufferF q;
    DeviceBufferF k;
    DeviceBufferF v;
    DeviceBufferF attn;
    DeviceBufferF out;
    DeviceBufferF ff0;
    DeviceBufferF ff1;
    DeviceBufferF tmp;
    DeviceBufferF logits;
    DeviceBufferF reduceMax;
    DeviceBufferI reduceIdx;
    DeviceBufferI ids;
    DeviceBufferH halfA;
    DeviceBufferB bfA;
};

struct DecoderCache {
    float* k = nullptr; // [maxLen * dAttn]
    float* v = nullptr; // [maxLen * dAttn]
    int capacity = 0;
};

enum class DType {
    F32,
    F16,
    BF16
};

struct T5Gpu {
    T5Config cfg;
    cublasHandle_t handle = nullptr;
    DType dtype = DType::F32;
    float* d_shared = nullptr;
    float* d_encoderRelBias = nullptr;
    float* d_decoderRelBias = nullptr;
    std::vector<EncoderLayer> encoder;
    std::vector<DecoderLayer> decoder;
    float* d_encoderFinalLn = nullptr;
    float* d_decoderFinalLn = nullptr;
    float* d_lmHead = nullptr;
    half* d_lmHead16 = nullptr;
    __nv_bfloat16* d_lmHeadBf = nullptr;
    Workspace ws;
};

struct EncoderState {
    int seq = 0;
    float* hidden = nullptr;
};

static bool cublas_ok(cublasStatus_t status) {
    return status == CUBLAS_STATUS_SUCCESS;
}

static bool supports_bf16() {
    int major = 0;
    int minor = 0;
    if (cudaDeviceGetAttribute(&major, cudaDevAttrComputeCapabilityMajor, 0) != cudaSuccess) return false;
    if (cudaDeviceGetAttribute(&minor, cudaDevAttrComputeCapabilityMinor, 0) != cudaSuccess) return false;
    return major >= 8; // Ampere+ supports BF16
}

static DType parse_dtype() {
    const char* env = std::getenv("CRTK_T5_CUDA_DTYPE");
    if (!env) {
        return DType::F32;
    }
    std::string value(env);
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    if (value == "fp16" || value == "f16" || value == "half") {
        return DType::F16;
    }
    if (value == "bf16" || value == "bfloat16") {
        return supports_bf16() ? DType::BF16 : DType::F16;
    }
    return DType::F32;
}

static inline __host__ __device__ half to_half(float v) {
    return __float2half_rn(v);
}

static inline __host__ __device__ __nv_bfloat16 to_bf16(float v) {
    return __float2bfloat16(v);
}

static void cuda_free(void* p) {
    if (p) cudaFree(p);
}

static bool cuda_alloc(float** out, size_t count) {
    *out = nullptr;
    return cuda_ok(cudaMalloc(reinterpret_cast<void**>(out), sizeof(float) * count));
}

static bool cuda_alloc(half** out, size_t count) {
    *out = nullptr;
    return cuda_ok(cudaMalloc(reinterpret_cast<void**>(out), sizeof(half) * count));
}

static bool cuda_alloc(__nv_bfloat16** out, size_t count) {
    *out = nullptr;
    return cuda_ok(cudaMalloc(reinterpret_cast<void**>(out), sizeof(__nv_bfloat16) * count));
}

static bool cuda_copy_to_device(float* dst, const std::vector<float>& src) {
    return cuda_ok(cudaMemcpy(dst, src.data(), sizeof(float) * src.size(), cudaMemcpyHostToDevice));
}

// ---- file parsing helpers ----

static bool read_bytes(std::ifstream& f, void* dst, size_t n) {
    f.read(reinterpret_cast<char*>(dst), static_cast<std::streamsize>(n));
    return bool(f);
}

static bool read_i32_be(std::ifstream& f, int32_t& out) {
    uint8_t b[4];
    if (!read_bytes(f, b, 4)) return false;
    out = (static_cast<int32_t>(b[0]) << 24) | (static_cast<int32_t>(b[1]) << 16) |
          (static_cast<int32_t>(b[2]) << 8) | static_cast<int32_t>(b[3]);
    return true;
}

static bool read_f32_be(std::ifstream& f, float& out) {
    int32_t i = 0;
    if (!read_i32_be(f, i)) return false;
    uint32_t u = static_cast<uint32_t>(i);
    std::memcpy(&out, &u, sizeof(float));
    return true;
}

static bool read_string_be(std::ifstream& f, std::string& out) {
    int32_t len = 0;
    if (!read_i32_be(f, len)) return false;
    if (len < 0 || len > 10000000) return false;
    out.resize(static_cast<size_t>(len));
    if (len == 0) return true;
    return read_bytes(f, out.data(), static_cast<size_t>(len));
}

static bool read_float_array_le(std::ifstream& f, size_t count, std::vector<float>& out) {
    out.resize(count);
    if (count == 0) return true;
    std::vector<uint8_t> buf(count * 4);
    if (!read_bytes(f, buf.data(), buf.size())) return false;
    for (size_t i = 0; i < count; i++) {
        uint32_t v = static_cast<uint32_t>(buf[i * 4]) |
                     (static_cast<uint32_t>(buf[i * 4 + 1]) << 8) |
                     (static_cast<uint32_t>(buf[i * 4 + 2]) << 16) |
                     (static_cast<uint32_t>(buf[i * 4 + 3]) << 24);
        std::memcpy(&out[i], &v, sizeof(float));
    }
    return true;
}

static std::vector<std::string> required_tensors(const T5Config& cfg) {
    std::vector<std::string> names;
    names.reserve(32 + cfg.numLayers * 9 + cfg.numDecoderLayers * 13);
    names.emplace_back("shared.weight");
    names.emplace_back("encoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight");
    names.emplace_back("decoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight");
    names.emplace_back("encoder.final_layer_norm.weight");
    names.emplace_back("decoder.final_layer_norm.weight");
    names.emplace_back("lm_head.weight");

    for (int i = 0; i < cfg.numLayers; i++) {
        std::string prefix = "encoder.block." + std::to_string(i) + ".";
        names.emplace_back(prefix + "layer.0.layer_norm.weight");
        names.emplace_back(prefix + "layer.0.SelfAttention.q.weight");
        names.emplace_back(prefix + "layer.0.SelfAttention.k.weight");
        names.emplace_back(prefix + "layer.0.SelfAttention.v.weight");
        names.emplace_back(prefix + "layer.0.SelfAttention.o.weight");
        names.emplace_back(prefix + "layer.1.layer_norm.weight");
        names.emplace_back(prefix + "layer.1.DenseReluDense.wi_0.weight");
        names.emplace_back(prefix + "layer.1.DenseReluDense.wi_1.weight");
        names.emplace_back(prefix + "layer.1.DenseReluDense.wo.weight");
    }

    for (int i = 0; i < cfg.numDecoderLayers; i++) {
        std::string prefix = "decoder.block." + std::to_string(i) + ".";
        names.emplace_back(prefix + "layer.0.layer_norm.weight");
        names.emplace_back(prefix + "layer.0.SelfAttention.q.weight");
        names.emplace_back(prefix + "layer.0.SelfAttention.k.weight");
        names.emplace_back(prefix + "layer.0.SelfAttention.v.weight");
        names.emplace_back(prefix + "layer.0.SelfAttention.o.weight");
        names.emplace_back(prefix + "layer.1.layer_norm.weight");
        names.emplace_back(prefix + "layer.1.EncDecAttention.q.weight");
        names.emplace_back(prefix + "layer.1.EncDecAttention.k.weight");
        names.emplace_back(prefix + "layer.1.EncDecAttention.v.weight");
        names.emplace_back(prefix + "layer.1.EncDecAttention.o.weight");
        names.emplace_back(prefix + "layer.2.layer_norm.weight");
        names.emplace_back(prefix + "layer.2.DenseReluDense.wi_0.weight");
        names.emplace_back(prefix + "layer.2.DenseReluDense.wi_1.weight");
        names.emplace_back(prefix + "layer.2.DenseReluDense.wo.weight");
    }

    return names;
}

static bool load_t5_bin(const std::string& path, HostModel& out) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return false;

    int32_t magic = 0;
    if (!read_i32_be(f, magic)) return false;
    if (magic != 0x4C545454) return false; // LTTT
    int32_t version = 0;
    if (!read_i32_be(f, version)) return false;
    if (version != 1) return false;

    std::string name;
    if (!read_string_be(f, name)) return false;

    T5Config cfg;
    if (!read_i32_be(f, cfg.vocabSize)) return false;
    if (!read_i32_be(f, cfg.dModel)) return false;
    if (!read_i32_be(f, cfg.dKv)) return false;
    if (!read_i32_be(f, cfg.dFf)) return false;
    if (!read_i32_be(f, cfg.numLayers)) return false;
    if (!read_i32_be(f, cfg.numDecoderLayers)) return false;
    if (!read_i32_be(f, cfg.numHeads)) return false;
    if (!read_i32_be(f, cfg.relBuckets)) return false;
    if (!read_i32_be(f, cfg.relMaxDistance)) return false;
    if (!read_i32_be(f, cfg.padId)) return false;
    if (!read_i32_be(f, cfg.eosId)) return false;
    if (!read_i32_be(f, cfg.decoderStartId)) return false;
    if (!read_i32_be(f, cfg.unkId)) return false;
    int32_t gated = 0;
    if (!read_i32_be(f, gated)) return false;
    cfg.gatedGelu = gated == 1;
    int32_t fp16 = 0;
    if (!read_i32_be(f, fp16)) return false;
    if (!read_f32_be(f, cfg.layerNormEps)) return false;

    int32_t spVocab = 0;
    if (!read_i32_be(f, spVocab)) return false;
    for (int i = 0; i < spVocab; i++) {
        std::string piece;
        if (!read_string_be(f, piece)) return false;
    }
    for (int i = 0; i < spVocab; i++) {
        float score = 0.f;
        if (!read_f32_be(f, score)) return false;
    }

    int32_t tensorCount = 0;
    if (!read_i32_be(f, tensorCount)) return false;

    std::vector<std::string> required = required_tensors(cfg);
    std::unordered_map<std::string, bool> requiredMap;
    requiredMap.reserve(required.size() * 2);
    for (const auto& nameReq : required) {
        requiredMap[nameReq] = false;
    }

    for (int t = 0; t < tensorCount; t++) {
        std::string tensorName;
        if (!read_string_be(f, tensorName)) return false;
        int32_t dims = 0;
        if (!read_i32_be(f, dims)) return false;
        if (dims < 0 || dims > 8) return false;
        std::vector<int> shape(static_cast<size_t>(dims));
        int64_t total = 1;
        for (int i = 0; i < dims; i++) {
            int32_t dim = 0;
            if (!read_i32_be(f, dim)) return false;
            shape[i] = dim;
            total *= dim;
        }
        int32_t count = 0;
        if (!read_i32_be(f, count)) return false;
        if (count != total) return false;

        auto it = requiredMap.find(tensorName);
        if (it != requiredMap.end()) {
            HostTensor ht;
            ht.shape = std::move(shape);
            if (!read_float_array_le(f, static_cast<size_t>(count), ht.data)) return false;
            out.tensors.emplace(tensorName, std::move(ht));
            it->second = true;
        } else {
            if (count > 0) {
                f.seekg(static_cast<std::streamoff>(count) * 4, std::ios::cur);
                if (!f) return false;
            }
        }
    }

    for (const auto& entry : requiredMap) {
        if (!entry.second) {
            return false;
        }
    }

    out.cfg = cfg;
    return true;
}

static std::vector<float> transpose2d(const std::vector<float>& in, int rows, int cols) {
    std::vector<float> out;
    out.resize(static_cast<size_t>(rows) * static_cast<size_t>(cols));
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            out[static_cast<size_t>(c) * rows + r] = in[static_cast<size_t>(r) * cols + c];
        }
    }
    return out;
}

static bool should_transpose(const std::string& name, const HostTensor& t) {
    if (t.shape.size() != 2) return false;
    if (name == "shared.weight") return false;
    if (name.find("relative_attention_bias.weight") != std::string::npos) return false;
    return true;
}

static bool upload_tensor_f32(const std::string& name, const HostTensor& t, float** outPtr) {
    std::vector<float> data;
    if (should_transpose(name, t)) {
        data = transpose2d(t.data, t.shape[0], t.shape[1]);
    } else {
        data = t.data;
    }
    if (!cuda_alloc(outPtr, data.size())) return false;
    if (!cuda_copy_to_device(*outPtr, data)) return false;
    return true;
}

static bool upload_tensor_f16(const std::string& name, const HostTensor& t, half** outPtr) {
    std::vector<float> data;
    if (should_transpose(name, t)) {
        data = transpose2d(t.data, t.shape[0], t.shape[1]);
    } else {
        data = t.data;
    }
    std::vector<half> h(data.size());
    for (size_t i = 0; i < data.size(); i++) {
        h[i] = to_half(data[i]);
    }
    if (!cuda_alloc(outPtr, h.size())) return false;
    if (!cuda_ok(cudaMemcpy(*outPtr, h.data(), sizeof(half) * h.size(), cudaMemcpyHostToDevice))) return false;
    return true;
}

static bool upload_tensor_bf16(const std::string& name, const HostTensor& t, __nv_bfloat16** outPtr) {
    std::vector<float> data;
    if (should_transpose(name, t)) {
        data = transpose2d(t.data, t.shape[0], t.shape[1]);
    } else {
        data = t.data;
    }
    std::vector<__nv_bfloat16> b(data.size());
    for (size_t i = 0; i < data.size(); i++) {
        b[i] = to_bf16(data[i]);
    }
    if (!cuda_alloc(outPtr, b.size())) return false;
    if (!cuda_ok(cudaMemcpy(*outPtr, b.data(), sizeof(__nv_bfloat16) * b.size(), cudaMemcpyHostToDevice))) return false;
    return true;
}

static bool upload_tensor_auto(const std::string& name,
                               const HostTensor& t,
                               DType dtype,
                               float** fOut,
                               half** hOut,
                               __nv_bfloat16** bOut,
                               bool forceF32) {
    if (forceF32 || dtype == DType::F32) {
        return upload_tensor_f32(name, t, fOut);
    }
    if (dtype == DType::F16) {
        return upload_tensor_f16(name, t, hOut);
    }
    return upload_tensor_bf16(name, t, bOut);
}

static bool gemm_row_major_f32(cublasHandle_t handle,
                               bool transA,
                               bool transB,
                               int m,
                               int n,
                               int k,
                               const float* A,
                               const float* B,
                               float* C) {
    if (m <= 0 || n <= 0 || k <= 0) return true;
    float alpha = 1.0f;
    float beta = 0.0f;
    cublasOperation_t opB = transB ? CUBLAS_OP_T : CUBLAS_OP_N;
    cublasOperation_t opA = transA ? CUBLAS_OP_T : CUBLAS_OP_N;
    return cublas_ok(cublasSgemm(handle,
                                 opB,
                                 opA,
                                 n,
                                 m,
                                 k,
                                 &alpha,
                                 B,
                                 n,
                                 A,
                                 k,
                                 &beta,
                                 C,
                                 n));
}

__global__ void float_to_half_kernel(const float* in, half* out, int n) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (idx < n) {
        out[idx] = to_half(in[idx]);
    }
}

__global__ void float_to_bf16_kernel(const float* in, __nv_bfloat16* out, int n) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (idx < n) {
        out[idx] = to_bf16(in[idx]);
    }
}

static bool gemm_row_major(T5Gpu* gpu,
                           int m,
                           int n,
                           int k,
                           const float* A,
                           const float* Bf,
                           const half* Bh,
                           const __nv_bfloat16* Bb,
                           float* C) {
    if (gpu->dtype == DType::F32) {
        return gemm_row_major_f32(gpu->handle, false, false, m, n, k, A, Bf, C);
    }
    if (m <= 0 || n <= 0 || k <= 0) return true;
    int total = m * k;
    int threads = 256;
    int blocks = (total + threads - 1) / threads;
    float alpha = 1.0f;
    float beta = 0.0f;
    cublasOperation_t opB = CUBLAS_OP_N;
    cublasOperation_t opA = CUBLAS_OP_N;

    if (gpu->dtype == DType::F16) {
        if (!gpu->ws.halfA.ensure(static_cast<size_t>(total))) return false;
        float_to_half_kernel<<<blocks, threads>>>(A, gpu->ws.halfA.ptr, total);
        if (!cuda_ok(cudaGetLastError())) return false;
        return cublas_ok(cublasGemmEx(gpu->handle,
                                      opB,
                                      opA,
                                      n,
                                      m,
                                      k,
                                      &alpha,
                                      Bh,
                                      CUDA_R_16F,
                                      n,
                                      gpu->ws.halfA.ptr,
                                      CUDA_R_16F,
                                      k,
                                      &beta,
                                      C,
                                      CUDA_R_32F,
                                      n,
                                      CUBLAS_COMPUTE_32F,
                                      CUBLAS_GEMM_DEFAULT_TENSOR_OP));
    }

    if (!gpu->ws.bfA.ensure(static_cast<size_t>(total))) return false;
    float_to_bf16_kernel<<<blocks, threads>>>(A, gpu->ws.bfA.ptr, total);
    if (!cuda_ok(cudaGetLastError())) return false;
    return cublas_ok(cublasGemmEx(gpu->handle,
                                  opB,
                                  opA,
                                  n,
                                  m,
                                  k,
                                  &alpha,
                                  Bb,
                                  CUDA_R_16BF,
                                  n,
                                  gpu->ws.bfA.ptr,
                                  CUDA_R_16BF,
                                  k,
                                  &beta,
                                  C,
                                  CUDA_R_32F,
                                  n,
                                  CUBLAS_COMPUTE_32F,
                                  CUBLAS_GEMM_DEFAULT_TENSOR_OP));
}

static inline const float* pick_f(const float* f, DType dtype) {
    return dtype == DType::F32 ? f : nullptr;
}

static inline const half* pick_h(const half* h, DType dtype) {
    return dtype == DType::F16 ? h : nullptr;
}

static inline const __nv_bfloat16* pick_b(const __nv_bfloat16* b, DType dtype) {
    return dtype == DType::BF16 ? b : nullptr;
}

__device__ float gelu_device(float x) {
    return 0.5f * x * (1.0f + tanhf(sqrtf(2.0f / 3.14159265358979323846f) * (x + 0.044715f * x * x * x)));
}

__global__ void embed_kernel(const int* ids, int seq, int vocab, int dModel, int unkId, const float* emb, float* out) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    int total = seq * dModel;
    if (idx >= total) return;
    int row = idx / dModel;
    int col = idx % dModel;
    int id = ids[row];
    if (id < 0 || id >= vocab) id = unkId;
    out[idx] = emb[id * dModel + col];
}

__global__ void add_in_place_kernel(float* a, const float* b, int n) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (idx < n) {
        a[idx] += b[idx];
    }
}

__global__ void gelu_mul_kernel(float* a, const float* b, int n) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (idx < n) {
        a[idx] = gelu_device(a[idx]) * b[idx];
    }
}

__global__ void rms_norm_kernel(const float* in, float* out, const float* weight, int rows, int cols, float eps) {
    int row = blockIdx.x;
    if (row >= rows) return;
    float sum = 0.0f;
    for (int c = threadIdx.x; c < cols; c += blockDim.x) {
        float v = in[row * cols + c];
        sum += v * v;
    }
    __shared__ float shared[256];
    shared[threadIdx.x] = sum;
    __syncthreads();
    for (int stride = blockDim.x / 2; stride > 0; stride >>= 1) {
        if (threadIdx.x < stride) {
            shared[threadIdx.x] += shared[threadIdx.x + stride];
        }
        __syncthreads();
    }
    float inv = rsqrtf(shared[0] / cols + eps);
    for (int c = threadIdx.x; c < cols; c += blockDim.x) {
        out[row * cols + c] = in[row * cols + c] * inv * weight[c];
    }
}

__device__ int rel_bucket(int relative, int buckets, int maxExact, int maxDistance, bool bidirectional) {
    int n = relative;
    int offset = 0;
    if (bidirectional) {
        if (n > 0) {
            offset = buckets;
        }
        n = n < 0 ? -n : n;
    } else {
        n = -n;
        if (n < 0) n = 0;
    }
    int bucket = 0;
    if (n < maxExact) {
        bucket = n;
    } else {
        float logVal = logf(static_cast<float>(n) / maxExact) / logf(static_cast<float>(maxDistance) / maxExact);
        int scaled = static_cast<int>(maxExact + (buckets - maxExact) * logVal);
        if (scaled > buckets - 1) scaled = buckets - 1;
        bucket = scaled;
    }
    return offset + bucket;
}

__global__ void attn_scores_kernel(const float* q, const float* k, float* attn,
                                   int qLen, int kLen, int heads, int dKv, int dAttn,
                                   bool causal, const float* relBias, int relBuckets, int relMaxDistance,
                                   bool bidirectional) {
    int j = blockIdx.x * blockDim.x + threadIdx.x;
    int i = blockIdx.y * blockDim.y + threadIdx.y;
    int h = blockIdx.z;
    if (i >= qLen || j >= kLen || h >= heads) return;
    if (causal && j > i) {
        attn[(h * qLen + i) * kLen + j] = -1e9f;
        return;
    }
    int qBase = i * dAttn + h * dKv;
    int kBase = j * dAttn + h * dKv;
    float sum = 0.0f;
    for (int d = 0; d < dKv; d++) {
        sum += q[qBase + d] * k[kBase + d];
    }
    if (relBias != nullptr) {
        int buckets = bidirectional ? relBuckets / 2 : relBuckets;
        int maxExact = buckets / 2;
        int bucket = rel_bucket(j - i, buckets, maxExact, relMaxDistance, bidirectional);
        sum += relBias[bucket * heads + h];
    }
    attn[(h * qLen + i) * kLen + j] = sum;
}

__global__ void softmax_kernel(float* attn, int rows, int cols) {
    int row = blockIdx.x;
    if (row >= rows) return;
    float maxVal = -1e20f;
    for (int c = threadIdx.x; c < cols; c += blockDim.x) {
        float v = attn[row * cols + c];
        if (v > maxVal) maxVal = v;
    }
    __shared__ float sharedMax[256];
    sharedMax[threadIdx.x] = maxVal;
    __syncthreads();
    for (int stride = blockDim.x / 2; stride > 0; stride >>= 1) {
        if (threadIdx.x < stride) {
            if (sharedMax[threadIdx.x + stride] > sharedMax[threadIdx.x]) {
                sharedMax[threadIdx.x] = sharedMax[threadIdx.x + stride];
            }
        }
        __syncthreads();
    }
    float maxRow = sharedMax[0];
    float sum = 0.0f;
    for (int c = threadIdx.x; c < cols; c += blockDim.x) {
        float expv = expf(attn[row * cols + c] - maxRow);
        attn[row * cols + c] = expv;
        sum += expv;
    }
    __shared__ float sharedSum[256];
    sharedSum[threadIdx.x] = sum;
    __syncthreads();
    for (int stride = blockDim.x / 2; stride > 0; stride >>= 1) {
        if (threadIdx.x < stride) {
            sharedSum[threadIdx.x] += sharedSum[threadIdx.x + stride];
        }
        __syncthreads();
    }
    float inv = sharedSum[0] == 0.0f ? 0.0f : 1.0f / sharedSum[0];
    for (int c = threadIdx.x; c < cols; c += blockDim.x) {
        attn[row * cols + c] *= inv;
    }
}

__global__ void attn_scores_softmax_single_kernel(const float* q, const float* k, float* attn,
                                                  int seq, int heads, int dKv, int dAttn,
                                                  const float* relBias, int relBuckets, int relMaxDistance,
                                                  int pos, bool bidirectional) {
    int h = blockIdx.x;
    if (h >= heads) return;

    float localMax = -1e20f;
    int qBase = h * dKv;
    for (int j = threadIdx.x; j < seq; j += blockDim.x) {
        int kBase = j * dAttn + h * dKv;
        float sum = 0.0f;
        for (int d = 0; d < dKv; d++) {
            sum += q[qBase + d] * k[kBase + d];
        }
        if (relBias != nullptr) {
            int buckets = bidirectional ? relBuckets / 2 : relBuckets;
            int maxExact = buckets / 2;
            int bucket = rel_bucket(j - pos, buckets, maxExact, relMaxDistance, bidirectional);
            sum += relBias[bucket * heads + h];
        }
        attn[h * seq + j] = sum;
        if (sum > localMax) {
            localMax = sum;
        }
    }

    __shared__ float sharedMax[256];
    sharedMax[threadIdx.x] = localMax;
    __syncthreads();
    for (int stride = blockDim.x / 2; stride > 0; stride >>= 1) {
        if (threadIdx.x < stride) {
            if (sharedMax[threadIdx.x + stride] > sharedMax[threadIdx.x]) {
                sharedMax[threadIdx.x] = sharedMax[threadIdx.x + stride];
            }
        }
        __syncthreads();
    }
    float maxVal = sharedMax[0];

    float localSum = 0.0f;
    for (int j = threadIdx.x; j < seq; j += blockDim.x) {
        float expv = expf(attn[h * seq + j] - maxVal);
        attn[h * seq + j] = expv;
        localSum += expv;
    }

    __shared__ float sharedSum[256];
    sharedSum[threadIdx.x] = localSum;
    __syncthreads();
    for (int stride = blockDim.x / 2; stride > 0; stride >>= 1) {
        if (threadIdx.x < stride) {
            sharedSum[threadIdx.x] += sharedSum[threadIdx.x + stride];
        }
        __syncthreads();
    }
    float inv = sharedSum[0] == 0.0f ? 0.0f : 1.0f / sharedSum[0];

    for (int j = threadIdx.x; j < seq; j += blockDim.x) {
        attn[h * seq + j] *= inv;
    }
}

__global__ void attn_out_kernel(const float* attn, const float* v, float* out,
                                int qLen, int kLen, int heads, int dKv, int dAttn) {
    int d = blockIdx.x * blockDim.x + threadIdx.x;
    int i = blockIdx.y * blockDim.y + threadIdx.y;
    int h = blockIdx.z;
    if (d >= dKv || i >= qLen || h >= heads) return;
    float sum = 0.0f;
    int attnBase = (h * qLen + i) * kLen;
    int vBase = h * dKv + d;
    for (int j = 0; j < kLen; j++) {
        sum += attn[attnBase + j] * v[j * dAttn + vBase];
    }
    out[i * dAttn + vBase] = sum;
}

__global__ void argmax_block_kernel(const float* data, int n, float* blockMax, int* blockIdxOut) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    float v = (idx < n) ? data[idx] : -1e20f;
    __shared__ float smax[256];
    __shared__ int sidx[256];
    smax[threadIdx.x] = v;
    sidx[threadIdx.x] = idx;
    __syncthreads();
    for (int stride = blockDim.x / 2; stride > 0; stride >>= 1) {
        if (threadIdx.x < stride) {
            float other = smax[threadIdx.x + stride];
            if (other > smax[threadIdx.x]) {
                smax[threadIdx.x] = other;
                sidx[threadIdx.x] = sidx[threadIdx.x + stride];
            }
        }
        __syncthreads();
    }
    if (threadIdx.x == 0) {
        blockMax[blockIdx.x] = smax[0];
        blockIdxOut[blockIdx.x] = sidx[0];
    }
}

static bool argmax_device(Workspace& ws, const float* logits, int vocab, int& outIdx) {
    const int threads = 256;
    int blocks = (vocab + threads - 1) / threads;
    if (!ws.reduceMax.ensure(static_cast<size_t>(blocks))) return false;
    if (!ws.reduceIdx.ensure(static_cast<size_t>(blocks))) return false;
    argmax_block_kernel<<<blocks, threads>>>(logits, vocab, ws.reduceMax.ptr, ws.reduceIdx.ptr);
    if (!cuda_ok(cudaGetLastError())) return false;

    std::vector<float> hMax(blocks);
    std::vector<int> hIdx(blocks);
    if (!cuda_ok(cudaMemcpy(hMax.data(), ws.reduceMax.ptr, sizeof(float) * blocks, cudaMemcpyDeviceToHost))) return false;
    if (!cuda_ok(cudaMemcpy(hIdx.data(), ws.reduceIdx.ptr, sizeof(int) * blocks, cudaMemcpyDeviceToHost))) return false;

    float best = -1e20f;
    int bestIdx = 0;
    for (int i = 0; i < blocks; i++) {
        if (hMax[i] > best) {
            best = hMax[i];
            bestIdx = hIdx[i];
        }
    }
    outIdx = bestIdx;
    return true;
}

static bool run_layer_norm(const float* in, float* out, const float* weight, int rows, int cols, float eps) {
    dim3 grid(rows);
    dim3 block(256);
    rms_norm_kernel<<<grid, block>>>(in, out, weight, rows, cols, eps);
    return cuda_ok(cudaGetLastError());
}

static bool run_softmax(float* attn, int rows, int cols) {
    dim3 grid(rows);
    dim3 block(256);
    softmax_kernel<<<grid, block>>>(attn, rows, cols);
    return cuda_ok(cudaGetLastError());
}

static bool run_attention(const T5Config& cfg,
                          float* q,
                          float* k,
                          float* v,
                          float* attn,
                          float* out,
                          int qLen,
                          int kLen,
                          bool causal,
                          const float* relBias,
                          bool bidirectional) {
    dim3 block(16, 16);
    dim3 grid((kLen + block.x - 1) / block.x, (qLen + block.y - 1) / block.y, cfg.numHeads);
    int dAttn = cfg.numHeads * cfg.dKv;
    attn_scores_kernel<<<grid, block>>>(q, k, attn, qLen, kLen, cfg.numHeads, cfg.dKv, dAttn, causal, relBias,
                                        cfg.relBuckets, cfg.relMaxDistance, bidirectional);
    if (!cuda_ok(cudaGetLastError())) return false;
    if (!run_softmax(attn, cfg.numHeads * qLen, kLen)) return false;
    dim3 blockOut(16, 8);
    dim3 gridOut((cfg.dKv + blockOut.x - 1) / blockOut.x, (qLen + blockOut.y - 1) / blockOut.y, cfg.numHeads);
    attn_out_kernel<<<gridOut, blockOut>>>(attn, v, out, qLen, kLen, cfg.numHeads, cfg.dKv, dAttn);
    return cuda_ok(cudaGetLastError());
}

static bool run_attention_single(const T5Config& cfg,
                                 float* q,
                                 float* k,
                                 float* v,
                                 float* attn,
                                 float* out,
                                 int kLen,
                                 const float* relBias,
                                 bool bidirectional,
                                 int pos) {
    if (kLen <= 0) return false;
    dim3 block(256);
    dim3 grid(cfg.numHeads);
    int dAttn = cfg.numHeads * cfg.dKv;
    attn_scores_softmax_single_kernel<<<grid, block>>>(q, k, attn, kLen, cfg.numHeads, cfg.dKv, dAttn,
                                                       relBias, cfg.relBuckets, cfg.relMaxDistance, pos, bidirectional);
    if (!cuda_ok(cudaGetLastError())) return false;
    dim3 blockOut(128, 1);
    dim3 gridOut((cfg.dKv + blockOut.x - 1) / blockOut.x, 1, cfg.numHeads);
    attn_out_kernel<<<gridOut, blockOut>>>(attn, v, out, 1, kLen, cfg.numHeads, cfg.dKv, dAttn);
    return cuda_ok(cudaGetLastError());
}

static bool run_embed(const T5Config& cfg, Workspace& ws, const std::vector<int>& ids, const float* shared, float* out) {
    if (!ws.ids.ensure(ids.size())) return false;
    if (!cuda_ok(cudaMemcpy(ws.ids.ptr, ids.data(), sizeof(int) * ids.size(), cudaMemcpyHostToDevice))) return false;
    int total = static_cast<int>(ids.size()) * cfg.dModel;
    int threads = 256;
    int blocks = (total + threads - 1) / threads;
    embed_kernel<<<blocks, threads>>>(ws.ids.ptr, static_cast<int>(ids.size()), cfg.vocabSize, cfg.dModel, cfg.unkId, shared, out);
    return cuda_ok(cudaGetLastError());
}

static void destroy_gpu(T5Gpu* gpu) {
    if (!gpu) return;
    cuda_free(gpu->d_shared);
    cuda_free(gpu->d_encoderRelBias);
    cuda_free(gpu->d_decoderRelBias);
    cuda_free(gpu->d_encoderFinalLn);
    cuda_free(gpu->d_decoderFinalLn);
    cuda_free(gpu->d_lmHead);
    cuda_free(gpu->d_lmHead16);
    cuda_free(gpu->d_lmHeadBf);
    for (auto& layer : gpu->encoder) {
        cuda_free(layer.ln1);
        cuda_free(layer.attn.wq);
        cuda_free(layer.attn.wk);
        cuda_free(layer.attn.wv);
        cuda_free(layer.attn.wo);
        cuda_free(layer.attn.wq16);
        cuda_free(layer.attn.wk16);
        cuda_free(layer.attn.wv16);
        cuda_free(layer.attn.wo16);
        cuda_free(layer.attn.wqbf);
        cuda_free(layer.attn.wkbf);
        cuda_free(layer.attn.wvbf);
        cuda_free(layer.attn.wobf);
        cuda_free(layer.ln2);
        cuda_free(layer.ffn.wi0);
        cuda_free(layer.ffn.wi1);
        cuda_free(layer.ffn.wo);
        cuda_free(layer.ffn.wi016);
        cuda_free(layer.ffn.wi116);
        cuda_free(layer.ffn.wo16);
        cuda_free(layer.ffn.wi0bf);
        cuda_free(layer.ffn.wi1bf);
        cuda_free(layer.ffn.wobf);
    }
    for (auto& layer : gpu->decoder) {
        cuda_free(layer.ln1);
        cuda_free(layer.selfAttn.wq);
        cuda_free(layer.selfAttn.wk);
        cuda_free(layer.selfAttn.wv);
        cuda_free(layer.selfAttn.wo);
        cuda_free(layer.selfAttn.wq16);
        cuda_free(layer.selfAttn.wk16);
        cuda_free(layer.selfAttn.wv16);
        cuda_free(layer.selfAttn.wo16);
        cuda_free(layer.selfAttn.wqbf);
        cuda_free(layer.selfAttn.wkbf);
        cuda_free(layer.selfAttn.wvbf);
        cuda_free(layer.selfAttn.wobf);
        cuda_free(layer.ln2);
        cuda_free(layer.crossAttn.wq);
        cuda_free(layer.crossAttn.wk);
        cuda_free(layer.crossAttn.wv);
        cuda_free(layer.crossAttn.wo);
        cuda_free(layer.crossAttn.wq16);
        cuda_free(layer.crossAttn.wk16);
        cuda_free(layer.crossAttn.wv16);
        cuda_free(layer.crossAttn.wo16);
        cuda_free(layer.crossAttn.wqbf);
        cuda_free(layer.crossAttn.wkbf);
        cuda_free(layer.crossAttn.wvbf);
        cuda_free(layer.crossAttn.wobf);
        cuda_free(layer.ln3);
        cuda_free(layer.ffn.wi0);
        cuda_free(layer.ffn.wi1);
        cuda_free(layer.ffn.wo);
        cuda_free(layer.ffn.wi016);
        cuda_free(layer.ffn.wi116);
        cuda_free(layer.ffn.wo16);
        cuda_free(layer.ffn.wi0bf);
        cuda_free(layer.ffn.wi1bf);
        cuda_free(layer.ffn.wobf);
    }
    gpu->ws.x.release();
    gpu->ws.norm.release();
    gpu->ws.q.release();
    gpu->ws.k.release();
    gpu->ws.v.release();
    gpu->ws.attn.release();
    gpu->ws.out.release();
    gpu->ws.ff0.release();
    gpu->ws.ff1.release();
    gpu->ws.tmp.release();
    gpu->ws.logits.release();
    gpu->ws.reduceMax.release();
    gpu->ws.reduceIdx.release();
    gpu->ws.ids.release();
    gpu->ws.halfA.release();
    gpu->ws.bfA.release();
    if (gpu->handle) {
        cublasDestroy(gpu->handle);
        gpu->handle = nullptr;
    }
    delete gpu;
}

static bool build_gpu(const HostModel& host, T5Gpu*& out) {
    auto* gpu = new T5Gpu();
    gpu->cfg = host.cfg;
    if (!cublas_ok(cublasCreate(&gpu->handle))) {
        delete gpu;
        return false;
    }
    cublasSetMathMode(gpu->handle, CUBLAS_TENSOR_OP_MATH);
    gpu->dtype = parse_dtype();

    auto get = [&](const std::string& name) -> const HostTensor* {
        auto it = host.tensors.find(name);
        if (it == host.tensors.end()) return nullptr;
        return &it->second;
    };

    const HostTensor* shared = get("shared.weight");
    if (!shared || !upload_tensor_auto("shared.weight", *shared, DType::F32, &gpu->d_shared, nullptr, nullptr, true)) {
        destroy_gpu(gpu);
        return false;
    }

    const HostTensor* encBias = get("encoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight");
    const HostTensor* decBias = get("decoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight");
    if (!encBias || !upload_tensor_auto("encoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight", *encBias,
                                        DType::F32, &gpu->d_encoderRelBias, nullptr, nullptr, true)) {
        destroy_gpu(gpu);
        return false;
    }
    if (!decBias || !upload_tensor_auto("decoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight", *decBias,
                                        DType::F32, &gpu->d_decoderRelBias, nullptr, nullptr, true)) {
        destroy_gpu(gpu);
        return false;
    }

    const HostTensor* encFinal = get("encoder.final_layer_norm.weight");
    const HostTensor* decFinal = get("decoder.final_layer_norm.weight");
    if (!encFinal || !upload_tensor_auto("encoder.final_layer_norm.weight", *encFinal, DType::F32,
                                         &gpu->d_encoderFinalLn, nullptr, nullptr, true)) {
        destroy_gpu(gpu);
        return false;
    }
    if (!decFinal || !upload_tensor_auto("decoder.final_layer_norm.weight", *decFinal, DType::F32,
                                         &gpu->d_decoderFinalLn, nullptr, nullptr, true)) {
        destroy_gpu(gpu);
        return false;
    }

    const HostTensor* lmHead = get("lm_head.weight");
    if (!lmHead || !upload_tensor_auto("lm_head.weight", *lmHead, gpu->dtype,
                                       &gpu->d_lmHead, &gpu->d_lmHead16, &gpu->d_lmHeadBf, false)) {
        destroy_gpu(gpu);
        return false;
    }

    gpu->encoder.resize(static_cast<size_t>(gpu->cfg.numLayers));
    for (int i = 0; i < gpu->cfg.numLayers; i++) {
        EncoderLayer layer;
        std::string prefix = "encoder.block." + std::to_string(i) + ".";
        auto ln1 = get(prefix + "layer.0.layer_norm.weight");
        auto wq = get(prefix + "layer.0.SelfAttention.q.weight");
        auto wk = get(prefix + "layer.0.SelfAttention.k.weight");
        auto wv = get(prefix + "layer.0.SelfAttention.v.weight");
        auto wo = get(prefix + "layer.0.SelfAttention.o.weight");
        auto ln2 = get(prefix + "layer.1.layer_norm.weight");
        auto wi0 = get(prefix + "layer.1.DenseReluDense.wi_0.weight");
        auto wi1 = get(prefix + "layer.1.DenseReluDense.wi_1.weight");
        auto wo2 = get(prefix + "layer.1.DenseReluDense.wo.weight");
        if (!ln1 || !wq || !wk || !wv || !wo || !ln2 || !wi0 || !wi1 || !wo2) {
            destroy_gpu(gpu);
            return false;
        }
        if (!upload_tensor_auto(prefix + "layer.0.layer_norm.weight", *ln1, DType::F32,
                                &layer.ln1, nullptr, nullptr, true) ||
            !upload_tensor_auto(prefix + "layer.0.SelfAttention.q.weight", *wq, gpu->dtype,
                                &layer.attn.wq, &layer.attn.wq16, &layer.attn.wqbf, false) ||
            !upload_tensor_auto(prefix + "layer.0.SelfAttention.k.weight", *wk, gpu->dtype,
                                &layer.attn.wk, &layer.attn.wk16, &layer.attn.wkbf, false) ||
            !upload_tensor_auto(prefix + "layer.0.SelfAttention.v.weight", *wv, gpu->dtype,
                                &layer.attn.wv, &layer.attn.wv16, &layer.attn.wvbf, false) ||
            !upload_tensor_auto(prefix + "layer.0.SelfAttention.o.weight", *wo, gpu->dtype,
                                &layer.attn.wo, &layer.attn.wo16, &layer.attn.wobf, false) ||
            !upload_tensor_auto(prefix + "layer.1.layer_norm.weight", *ln2, DType::F32,
                                &layer.ln2, nullptr, nullptr, true) ||
            !upload_tensor_auto(prefix + "layer.1.DenseReluDense.wi_0.weight", *wi0, gpu->dtype,
                                &layer.ffn.wi0, &layer.ffn.wi016, &layer.ffn.wi0bf, false) ||
            !upload_tensor_auto(prefix + "layer.1.DenseReluDense.wi_1.weight", *wi1, gpu->dtype,
                                &layer.ffn.wi1, &layer.ffn.wi116, &layer.ffn.wi1bf, false) ||
            !upload_tensor_auto(prefix + "layer.1.DenseReluDense.wo.weight", *wo2, gpu->dtype,
                                &layer.ffn.wo, &layer.ffn.wo16, &layer.ffn.wobf, false)) {
            destroy_gpu(gpu);
            return false;
        }
        gpu->encoder[static_cast<size_t>(i)] = layer;
    }

    gpu->decoder.resize(static_cast<size_t>(gpu->cfg.numDecoderLayers));
    for (int i = 0; i < gpu->cfg.numDecoderLayers; i++) {
        DecoderLayer layer;
        std::string prefix = "decoder.block." + std::to_string(i) + ".";
        auto ln1 = get(prefix + "layer.0.layer_norm.weight");
        auto wq = get(prefix + "layer.0.SelfAttention.q.weight");
        auto wk = get(prefix + "layer.0.SelfAttention.k.weight");
        auto wv = get(prefix + "layer.0.SelfAttention.v.weight");
        auto wo = get(prefix + "layer.0.SelfAttention.o.weight");
        auto ln2 = get(prefix + "layer.1.layer_norm.weight");
        auto wq2 = get(prefix + "layer.1.EncDecAttention.q.weight");
        auto wk2 = get(prefix + "layer.1.EncDecAttention.k.weight");
        auto wv2 = get(prefix + "layer.1.EncDecAttention.v.weight");
        auto wo2 = get(prefix + "layer.1.EncDecAttention.o.weight");
        auto ln3 = get(prefix + "layer.2.layer_norm.weight");
        auto wi0 = get(prefix + "layer.2.DenseReluDense.wi_0.weight");
        auto wi1 = get(prefix + "layer.2.DenseReluDense.wi_1.weight");
        auto wo3 = get(prefix + "layer.2.DenseReluDense.wo.weight");
        if (!ln1 || !wq || !wk || !wv || !wo || !ln2 || !wq2 || !wk2 || !wv2 || !wo2 || !ln3 || !wi0 || !wi1 || !wo3) {
            destroy_gpu(gpu);
            return false;
        }
        if (!upload_tensor_auto(prefix + "layer.0.layer_norm.weight", *ln1, DType::F32,
                                &layer.ln1, nullptr, nullptr, true) ||
            !upload_tensor_auto(prefix + "layer.0.SelfAttention.q.weight", *wq, gpu->dtype,
                                &layer.selfAttn.wq, &layer.selfAttn.wq16, &layer.selfAttn.wqbf, false) ||
            !upload_tensor_auto(prefix + "layer.0.SelfAttention.k.weight", *wk, gpu->dtype,
                                &layer.selfAttn.wk, &layer.selfAttn.wk16, &layer.selfAttn.wkbf, false) ||
            !upload_tensor_auto(prefix + "layer.0.SelfAttention.v.weight", *wv, gpu->dtype,
                                &layer.selfAttn.wv, &layer.selfAttn.wv16, &layer.selfAttn.wvbf, false) ||
            !upload_tensor_auto(prefix + "layer.0.SelfAttention.o.weight", *wo, gpu->dtype,
                                &layer.selfAttn.wo, &layer.selfAttn.wo16, &layer.selfAttn.wobf, false) ||
            !upload_tensor_auto(prefix + "layer.1.layer_norm.weight", *ln2, DType::F32,
                                &layer.ln2, nullptr, nullptr, true) ||
            !upload_tensor_auto(prefix + "layer.1.EncDecAttention.q.weight", *wq2, gpu->dtype,
                                &layer.crossAttn.wq, &layer.crossAttn.wq16, &layer.crossAttn.wqbf, false) ||
            !upload_tensor_auto(prefix + "layer.1.EncDecAttention.k.weight", *wk2, gpu->dtype,
                                &layer.crossAttn.wk, &layer.crossAttn.wk16, &layer.crossAttn.wkbf, false) ||
            !upload_tensor_auto(prefix + "layer.1.EncDecAttention.v.weight", *wv2, gpu->dtype,
                                &layer.crossAttn.wv, &layer.crossAttn.wv16, &layer.crossAttn.wvbf, false) ||
            !upload_tensor_auto(prefix + "layer.1.EncDecAttention.o.weight", *wo2, gpu->dtype,
                                &layer.crossAttn.wo, &layer.crossAttn.wo16, &layer.crossAttn.wobf, false) ||
            !upload_tensor_auto(prefix + "layer.2.layer_norm.weight", *ln3, DType::F32,
                                &layer.ln3, nullptr, nullptr, true) ||
            !upload_tensor_auto(prefix + "layer.2.DenseReluDense.wi_0.weight", *wi0, gpu->dtype,
                                &layer.ffn.wi0, &layer.ffn.wi016, &layer.ffn.wi0bf, false) ||
            !upload_tensor_auto(prefix + "layer.2.DenseReluDense.wi_1.weight", *wi1, gpu->dtype,
                                &layer.ffn.wi1, &layer.ffn.wi116, &layer.ffn.wi1bf, false) ||
            !upload_tensor_auto(prefix + "layer.2.DenseReluDense.wo.weight", *wo3, gpu->dtype,
                                &layer.ffn.wo, &layer.ffn.wo16, &layer.ffn.wobf, false)) {
            destroy_gpu(gpu);
            return false;
        }
        gpu->decoder[static_cast<size_t>(i)] = layer;
    }

    out = gpu;
    return true;
}

static bool run_encoder(T5Gpu* gpu, const std::vector<int>& ids, EncoderState& enc) {
    int seq = static_cast<int>(ids.size());
    int dModel = gpu->cfg.dModel;
    int dAttn = gpu->cfg.numHeads * gpu->cfg.dKv;
    int dFf = gpu->cfg.dFf;

    if (!gpu->ws.x.ensure(static_cast<size_t>(seq) * dModel)) return false;
    if (!gpu->ws.norm.ensure(static_cast<size_t>(seq) * dModel)) return false;
    if (!gpu->ws.q.ensure(static_cast<size_t>(seq) * dAttn)) return false;
    if (!gpu->ws.k.ensure(static_cast<size_t>(seq) * dAttn)) return false;
    if (!gpu->ws.v.ensure(static_cast<size_t>(seq) * dAttn)) return false;
    if (!gpu->ws.attn.ensure(static_cast<size_t>(gpu->cfg.numHeads) * seq * seq)) return false;
    if (!gpu->ws.out.ensure(static_cast<size_t>(seq) * dAttn)) return false;
    if (!gpu->ws.ff0.ensure(static_cast<size_t>(seq) * dFf)) return false;
    if (!gpu->ws.ff1.ensure(static_cast<size_t>(seq) * dFf)) return false;
    if (!gpu->ws.tmp.ensure(static_cast<size_t>(seq) * dModel)) return false;

    if (!run_embed(gpu->cfg, gpu->ws, ids, gpu->d_shared, gpu->ws.x.ptr)) return false;

    for (int layer = 0; layer < gpu->cfg.numLayers; layer++) {
        EncoderLayer& l = gpu->encoder[static_cast<size_t>(layer)];
        if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.norm.ptr, l.ln1, seq, dModel, gpu->cfg.layerNormEps)) return false;
        if (!gemm_row_major(gpu, seq, dAttn, dModel, gpu->ws.norm.ptr,
                            pick_f(l.attn.wq, gpu->dtype),
                            pick_h(l.attn.wq16, gpu->dtype),
                            pick_b(l.attn.wqbf, gpu->dtype),
                            gpu->ws.q.ptr)) return false;
        if (!gemm_row_major(gpu, seq, dAttn, dModel, gpu->ws.norm.ptr,
                            pick_f(l.attn.wk, gpu->dtype),
                            pick_h(l.attn.wk16, gpu->dtype),
                            pick_b(l.attn.wkbf, gpu->dtype),
                            gpu->ws.k.ptr)) return false;
        if (!gemm_row_major(gpu, seq, dAttn, dModel, gpu->ws.norm.ptr,
                            pick_f(l.attn.wv, gpu->dtype),
                            pick_h(l.attn.wv16, gpu->dtype),
                            pick_b(l.attn.wvbf, gpu->dtype),
                            gpu->ws.v.ptr)) return false;
        if (!run_attention(gpu->cfg, gpu->ws.q.ptr, gpu->ws.k.ptr, gpu->ws.v.ptr, gpu->ws.attn.ptr, gpu->ws.out.ptr,
                           seq, seq, false, gpu->d_encoderRelBias, true)) return false;
        if (!gemm_row_major(gpu, seq, dModel, dAttn, gpu->ws.out.ptr,
                            pick_f(l.attn.wo, gpu->dtype),
                            pick_h(l.attn.wo16, gpu->dtype),
                            pick_b(l.attn.wobf, gpu->dtype),
                            gpu->ws.tmp.ptr)) return false;
        int total = seq * dModel;
        int threads = 256;
        int blocks = (total + threads - 1) / threads;
        add_in_place_kernel<<<blocks, threads>>>(gpu->ws.x.ptr, gpu->ws.tmp.ptr, total);
        if (!cuda_ok(cudaGetLastError())) return false;

        if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.norm.ptr, l.ln2, seq, dModel, gpu->cfg.layerNormEps)) return false;
        if (!gemm_row_major(gpu, seq, dFf, dModel, gpu->ws.norm.ptr,
                            pick_f(l.ffn.wi0, gpu->dtype),
                            pick_h(l.ffn.wi016, gpu->dtype),
                            pick_b(l.ffn.wi0bf, gpu->dtype),
                            gpu->ws.ff0.ptr)) return false;
        if (!gemm_row_major(gpu, seq, dFf, dModel, gpu->ws.norm.ptr,
                            pick_f(l.ffn.wi1, gpu->dtype),
                            pick_h(l.ffn.wi116, gpu->dtype),
                            pick_b(l.ffn.wi1bf, gpu->dtype),
                            gpu->ws.ff1.ptr)) return false;
        int ffTotal = seq * dFf;
        int ffBlocks = (ffTotal + threads - 1) / threads;
        gelu_mul_kernel<<<ffBlocks, threads>>>(gpu->ws.ff0.ptr, gpu->ws.ff1.ptr, ffTotal);
        if (!cuda_ok(cudaGetLastError())) return false;
        if (!gemm_row_major(gpu, seq, dModel, dFf, gpu->ws.ff0.ptr,
                            pick_f(l.ffn.wo, gpu->dtype),
                            pick_h(l.ffn.wo16, gpu->dtype),
                            pick_b(l.ffn.wobf, gpu->dtype),
                            gpu->ws.tmp.ptr)) return false;
        add_in_place_kernel<<<blocks, threads>>>(gpu->ws.x.ptr, gpu->ws.tmp.ptr, total);
        if (!cuda_ok(cudaGetLastError())) return false;
    }

    if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.x.ptr, gpu->d_encoderFinalLn, seq, dModel, gpu->cfg.layerNormEps)) return false;

    enc.seq = seq;
    if (!cuda_alloc(&enc.hidden, static_cast<size_t>(seq) * dModel)) return false;
    if (!cuda_ok(cudaMemcpy(enc.hidden, gpu->ws.x.ptr, sizeof(float) * seq * dModel, cudaMemcpyDeviceToDevice))) return false;
    return true;
}

static bool precompute_encoder_kv(T5Gpu* gpu, const EncoderState& enc,
                                  std::vector<float*>& outK,
                                  std::vector<float*>& outV) {
    int encSeq = enc.seq;
    int dAttn = gpu->cfg.numHeads * gpu->cfg.dKv;
    int dModel = gpu->cfg.dModel;
    outK.resize(static_cast<size_t>(gpu->cfg.numDecoderLayers));
    outV.resize(static_cast<size_t>(gpu->cfg.numDecoderLayers));
    for (int layer = 0; layer < gpu->cfg.numDecoderLayers; layer++) {
        DecoderLayer& l = gpu->decoder[static_cast<size_t>(layer)];
        float* d_k = nullptr;
        float* d_v = nullptr;
        if (!cuda_alloc(&d_k, static_cast<size_t>(encSeq) * dAttn)) return false;
        if (!cuda_alloc(&d_v, static_cast<size_t>(encSeq) * dAttn)) {
            cuda_free(d_k);
            return false;
        }
        if (!gemm_row_major(gpu, encSeq, dAttn, dModel, enc.hidden,
                            pick_f(l.crossAttn.wk, gpu->dtype),
                            pick_h(l.crossAttn.wk16, gpu->dtype),
                            pick_b(l.crossAttn.wkbf, gpu->dtype),
                            d_k)) {
            cuda_free(d_k);
            cuda_free(d_v);
            return false;
        }
        if (!gemm_row_major(gpu, encSeq, dAttn, dModel, enc.hidden,
                            pick_f(l.crossAttn.wv, gpu->dtype),
                            pick_h(l.crossAttn.wv16, gpu->dtype),
                            pick_b(l.crossAttn.wvbf, gpu->dtype),
                            d_v)) {
            cuda_free(d_k);
            cuda_free(d_v);
            return false;
        }
        outK[static_cast<size_t>(layer)] = d_k;
        outV[static_cast<size_t>(layer)] = d_v;
    }
    return true;
}

static void free_encoder_kv(std::vector<float*>& kv) {
    for (float* p : kv) {
        cuda_free(p);
    }
    kv.clear();
}

static void free_decoder_cache(std::vector<DecoderCache>& cache);

static bool alloc_decoder_cache(const T5Config& cfg, int maxLen, std::vector<DecoderCache>& out) {
    if (maxLen <= 0) return false;
    int dAttn = cfg.numHeads * cfg.dKv;
    out.resize(static_cast<size_t>(cfg.numDecoderLayers));
    for (int layer = 0; layer < cfg.numDecoderLayers; layer++) {
        DecoderCache cache;
        cache.capacity = maxLen;
        size_t count = static_cast<size_t>(maxLen) * dAttn;
        if (!cuda_alloc(&cache.k, count)) {
            free_decoder_cache(out);
            return false;
        }
        if (!cuda_alloc(&cache.v, count)) {
            cuda_free(cache.k);
            free_decoder_cache(out);
            return false;
        }
        out[static_cast<size_t>(layer)] = cache;
    }
    return true;
}

static void free_decoder_cache(std::vector<DecoderCache>& cache) {
    for (auto& c : cache) {
        cuda_free(c.k);
        cuda_free(c.v);
        c.k = nullptr;
        c.v = nullptr;
        c.capacity = 0;
    }
    cache.clear();
}

static bool run_decoder_step_cached(T5Gpu* gpu,
                                    const std::vector<int>& outIds,
                                    const EncoderState& enc,
                                    const std::vector<float*>& encK,
                                    const std::vector<float*>& encV,
                                    std::vector<DecoderCache>& cache,
                                    int& nextId) {
    int seq = static_cast<int>(outIds.size());
    int pos = seq - 1;
    int dModel = gpu->cfg.dModel;
    int dAttn = gpu->cfg.numHeads * gpu->cfg.dKv;
    int dFf = gpu->cfg.dFf;
    int encSeq = enc.seq;

    if (!gpu->ws.x.ensure(dModel)) return false;
    if (!gpu->ws.norm.ensure(dModel)) return false;
    if (!gpu->ws.q.ensure(dAttn)) return false;
    if (!gpu->ws.k.ensure(dAttn)) return false;
    if (!gpu->ws.v.ensure(dAttn)) return false;
    if (!gpu->ws.attn.ensure(static_cast<size_t>(gpu->cfg.numHeads) * std::max(seq, encSeq))) return false;
    if (!gpu->ws.out.ensure(dAttn)) return false;
    if (!gpu->ws.ff0.ensure(dFf)) return false;
    if (!gpu->ws.ff1.ensure(dFf)) return false;
    if (!gpu->ws.tmp.ensure(dModel)) return false;

    std::vector<int> lastId(1, outIds.back());
    if (!run_embed(gpu->cfg, gpu->ws, lastId, gpu->d_shared, gpu->ws.x.ptr)) return false;

    for (int layer = 0; layer < gpu->cfg.numDecoderLayers; layer++) {
        DecoderLayer& l = gpu->decoder[static_cast<size_t>(layer)];
        DecoderCache& c = cache[static_cast<size_t>(layer)];
        if (pos >= c.capacity) return false;

        if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.norm.ptr, l.ln1, 1, dModel, gpu->cfg.layerNormEps)) return false;
        if (!gemm_row_major(gpu, 1, dAttn, dModel, gpu->ws.norm.ptr,
                            pick_f(l.selfAttn.wq, gpu->dtype),
                            pick_h(l.selfAttn.wq16, gpu->dtype),
                            pick_b(l.selfAttn.wqbf, gpu->dtype),
                            gpu->ws.q.ptr)) return false;
        if (!gemm_row_major(gpu, 1, dAttn, dModel, gpu->ws.norm.ptr,
                            pick_f(l.selfAttn.wk, gpu->dtype),
                            pick_h(l.selfAttn.wk16, gpu->dtype),
                            pick_b(l.selfAttn.wkbf, gpu->dtype),
                            gpu->ws.k.ptr)) return false;
        if (!gemm_row_major(gpu, 1, dAttn, dModel, gpu->ws.norm.ptr,
                            pick_f(l.selfAttn.wv, gpu->dtype),
                            pick_h(l.selfAttn.wv16, gpu->dtype),
                            pick_b(l.selfAttn.wvbf, gpu->dtype),
                            gpu->ws.v.ptr)) return false;

        float* kDst = c.k + static_cast<size_t>(pos) * dAttn;
        float* vDst = c.v + static_cast<size_t>(pos) * dAttn;
        if (!cuda_ok(cudaMemcpy(kDst, gpu->ws.k.ptr, sizeof(float) * dAttn, cudaMemcpyDeviceToDevice))) return false;
        if (!cuda_ok(cudaMemcpy(vDst, gpu->ws.v.ptr, sizeof(float) * dAttn, cudaMemcpyDeviceToDevice))) return false;

        if (!run_attention_single(gpu->cfg, gpu->ws.q.ptr, c.k, c.v, gpu->ws.attn.ptr, gpu->ws.out.ptr,
                                  seq, gpu->d_decoderRelBias, false, pos)) return false;
        if (!gemm_row_major(gpu, 1, dModel, dAttn, gpu->ws.out.ptr,
                            pick_f(l.selfAttn.wo, gpu->dtype),
                            pick_h(l.selfAttn.wo16, gpu->dtype),
                            pick_b(l.selfAttn.wobf, gpu->dtype),
                            gpu->ws.tmp.ptr)) return false;
        add_in_place_kernel<<<(dModel + 255) / 256, 256>>>(gpu->ws.x.ptr, gpu->ws.tmp.ptr, dModel);
        if (!cuda_ok(cudaGetLastError())) return false;

        if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.norm.ptr, l.ln2, 1, dModel, gpu->cfg.layerNormEps)) return false;
        if (!gemm_row_major(gpu, 1, dAttn, dModel, gpu->ws.norm.ptr,
                            pick_f(l.crossAttn.wq, gpu->dtype),
                            pick_h(l.crossAttn.wq16, gpu->dtype),
                            pick_b(l.crossAttn.wqbf, gpu->dtype),
                            gpu->ws.q.ptr)) return false;
        if (!run_attention_single(gpu->cfg, gpu->ws.q.ptr, encK[static_cast<size_t>(layer)], encV[static_cast<size_t>(layer)],
                                  gpu->ws.attn.ptr, gpu->ws.out.ptr, encSeq, nullptr, true, 0)) return false;
        if (!gemm_row_major(gpu, 1, dModel, dAttn, gpu->ws.out.ptr,
                            pick_f(l.crossAttn.wo, gpu->dtype),
                            pick_h(l.crossAttn.wo16, gpu->dtype),
                            pick_b(l.crossAttn.wobf, gpu->dtype),
                            gpu->ws.tmp.ptr)) return false;
        add_in_place_kernel<<<(dModel + 255) / 256, 256>>>(gpu->ws.x.ptr, gpu->ws.tmp.ptr, dModel);
        if (!cuda_ok(cudaGetLastError())) return false;

        if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.norm.ptr, l.ln3, 1, dModel, gpu->cfg.layerNormEps)) return false;
        if (!gemm_row_major(gpu, 1, dFf, dModel, gpu->ws.norm.ptr,
                            pick_f(l.ffn.wi0, gpu->dtype),
                            pick_h(l.ffn.wi016, gpu->dtype),
                            pick_b(l.ffn.wi0bf, gpu->dtype),
                            gpu->ws.ff0.ptr)) return false;
        if (!gemm_row_major(gpu, 1, dFf, dModel, gpu->ws.norm.ptr,
                            pick_f(l.ffn.wi1, gpu->dtype),
                            pick_h(l.ffn.wi116, gpu->dtype),
                            pick_b(l.ffn.wi1bf, gpu->dtype),
                            gpu->ws.ff1.ptr)) return false;
        int ffBlocks = (dFf + 255) / 256;
        gelu_mul_kernel<<<ffBlocks, 256>>>(gpu->ws.ff0.ptr, gpu->ws.ff1.ptr, dFf);
        if (!cuda_ok(cudaGetLastError())) return false;
        if (!gemm_row_major(gpu, 1, dModel, dFf, gpu->ws.ff0.ptr,
                            pick_f(l.ffn.wo, gpu->dtype),
                            pick_h(l.ffn.wo16, gpu->dtype),
                            pick_b(l.ffn.wobf, gpu->dtype),
                            gpu->ws.tmp.ptr)) return false;
        add_in_place_kernel<<<(dModel + 255) / 256, 256>>>(gpu->ws.x.ptr, gpu->ws.tmp.ptr, dModel);
        if (!cuda_ok(cudaGetLastError())) return false;
    }

    if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.x.ptr, gpu->d_decoderFinalLn, 1, dModel, gpu->cfg.layerNormEps)) return false;

    if (!gpu->ws.logits.ensure(gpu->cfg.vocabSize)) return false;
    if (!gemm_row_major(gpu, 1, gpu->cfg.vocabSize, dModel, gpu->ws.x.ptr,
                        pick_f(gpu->d_lmHead, gpu->dtype),
                        pick_h(gpu->d_lmHead16, gpu->dtype),
                        pick_b(gpu->d_lmHeadBf, gpu->dtype),
                        gpu->ws.logits.ptr)) return false;

    if (!argmax_device(gpu->ws, gpu->ws.logits.ptr, gpu->cfg.vocabSize, nextId)) return false;
    return true;
}

extern "C" JNIEXPORT jlong JNICALL Java_chess_nn_t5_cuda_Backend_nativeCreate(JNIEnv* env, jclass, jstring path) {
    if (path == nullptr) return 0;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    if (!cpath) return 0;
    std::string pathStr(cpath);
    env->ReleaseStringUTFChars(path, cpath);

    HostModel host;
    if (!load_t5_bin(pathStr, host)) {
        return 0;
    }

    T5Gpu* gpu = nullptr;
    if (!build_gpu(host, gpu)) {
        return 0;
    }
    return reinterpret_cast<jlong>(gpu);
}

extern "C" JNIEXPORT void JNICALL Java_chess_nn_t5_cuda_Backend_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    T5Gpu* gpu = reinterpret_cast<T5Gpu*>(handle);
    destroy_gpu(gpu);
}

extern "C" JNIEXPORT jintArray JNICALL Java_chess_nn_t5_cuda_Backend_nativeGenerateIds(
    JNIEnv* env, jclass, jlong handle, jintArray inputIdsArr, jint maxNewTokens) {
    if (handle == 0 || inputIdsArr == nullptr) return nullptr;
    if (maxNewTokens < 0) maxNewTokens = 0;

    T5Gpu* gpu = reinterpret_cast<T5Gpu*>(handle);
    jsize len = env->GetArrayLength(inputIdsArr);
    std::vector<int> inputIds(static_cast<size_t>(len));
    env->GetIntArrayRegion(inputIdsArr, 0, len, inputIds.data());

    if (inputIds.empty() || inputIds.back() != gpu->cfg.eosId) {
        inputIds.push_back(gpu->cfg.eosId);
    }

    EncoderState enc;
    if (!run_encoder(gpu, inputIds, enc)) {
        cuda_free(enc.hidden);
        return nullptr;
    }

    std::vector<float*> encK;
    std::vector<float*> encV;
    if (!precompute_encoder_kv(gpu, enc, encK, encV)) {
        cuda_free(enc.hidden);
        free_encoder_kv(encK);
        free_encoder_kv(encV);
        return nullptr;
    }

    std::vector<DecoderCache> cache;
    int maxLen = maxNewTokens + 1;
    if (!alloc_decoder_cache(gpu->cfg, maxLen, cache)) {
        cuda_free(enc.hidden);
        free_encoder_kv(encK);
        free_encoder_kv(encV);
        free_decoder_cache(cache);
        return nullptr;
    }

    std::vector<int> outputIds;
    outputIds.reserve(static_cast<size_t>(maxNewTokens) + 1);
    outputIds.push_back(gpu->cfg.decoderStartId);

    for (int step = 0; step < maxNewTokens; step++) {
        int nextId = 0;
        if (!run_decoder_step_cached(gpu, outputIds, enc, encK, encV, cache, nextId)) {
            cuda_free(enc.hidden);
            free_encoder_kv(encK);
            free_encoder_kv(encV);
            free_decoder_cache(cache);
            return nullptr;
        }
        if (nextId == gpu->cfg.eosId) {
            break;
        }
        outputIds.push_back(nextId);
    }

    cuda_free(enc.hidden);
    free_encoder_kv(encK);
    free_encoder_kv(encV);
    free_decoder_cache(cache);

    jintArray outArr = env->NewIntArray(static_cast<jsize>(outputIds.size()));
    if (!outArr) return nullptr;
    env->SetIntArrayRegion(outArr, 0, static_cast<jsize>(outputIds.size()), outputIds.data());
    return outArr;
}
