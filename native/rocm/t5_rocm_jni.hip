/*
 * native/rocm/t5_rocm_jni.hip
 *
 * ROCm backend for the T5 tag-to-text pipeline.
 *
 * Exposes:
 *   - chess.nn.t5.rocm.Support.nativeDeviceCount() -> int
 *   - chess.nn.t5.rocm.Kernels.nativeMatmul(...) -> boolean
 *   - chess.nn.t5.rocm.Backend.nativeCreate(String) -> long
 *   - chess.nn.t5.rocm.Backend.nativeDestroy(long) -> void
 *   - chess.nn.t5.rocm.Backend.nativeGenerateIds(long, int[], int) -> int[]
 */

#include <jni.h>

#include <hip/hip_runtime.h>
#include <hipblas/hipblas.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

static inline int device_count() {
    int count = 0;
    hipError_t err = hipGetDeviceCount(&count);
    if (err != hipSuccess) return 0;
    return count;
}

extern "C" JNIEXPORT jint JNICALL Java_chess_nn_t5_rocm_Support_nativeDeviceCount(JNIEnv*, jclass) {
    return device_count();
}

// -------------------------
// Legacy matmul kernel (JNI)
// -------------------------

__global__ void matmul_kernel(const float* a, const float* bT, float* out, int m, int n, int k) {
    int row = blockIdx.y * blockDim.y + threadIdx.y;
    int col = blockIdx.x * blockDim.x + threadIdx.x;
    if (row >= m || col >= n) {
        return;
    }
    const float* aRow = a + row * k;
    const float* bRow = bT + col * k;
    float sum = 0.0f;
    for (int p = 0; p < k; p++) {
        sum += aRow[p] * bRow[p];
    }
    out[row * n + col] = sum;
}

static bool hip_ok(hipError_t err) {
    return err == hipSuccess;
}

extern "C" JNIEXPORT jboolean JNICALL Java_chess_nn_t5_rocm_Kernels_nativeMatmul(
    JNIEnv* env, jclass, jfloatArray aArr, jint m, jint k, jfloatArray bArr, jint n, jfloatArray outArr) {
    if (aArr == nullptr || bArr == nullptr || outArr == nullptr) {
        return JNI_FALSE;
    }
    if (m <= 0 || n <= 0 || k <= 0) {
        return JNI_FALSE;
    }

    jsize aLen = env->GetArrayLength(aArr);
    jsize bLen = env->GetArrayLength(bArr);
    jsize outLen = env->GetArrayLength(outArr);
    long expectedA = static_cast<long>(m) * static_cast<long>(k);
    long expectedB = static_cast<long>(n) * static_cast<long>(k);
    long expectedOut = static_cast<long>(m) * static_cast<long>(n);
    if (aLen != expectedA || bLen != expectedB || outLen != expectedOut) {
        return JNI_FALSE;
    }

    jfloat* a = env->GetFloatArrayElements(aArr, nullptr);
    jfloat* b = env->GetFloatArrayElements(bArr, nullptr);
    jfloat* out = env->GetFloatArrayElements(outArr, nullptr);
    if (a == nullptr || b == nullptr || out == nullptr) {
        if (a) env->ReleaseFloatArrayElements(aArr, a, JNI_ABORT);
        if (b) env->ReleaseFloatArrayElements(bArr, b, JNI_ABORT);
        if (out) env->ReleaseFloatArrayElements(outArr, out, JNI_ABORT);
        return JNI_FALSE;
    }

    float* d_a = nullptr;
    float* d_b = nullptr;
    float* d_out = nullptr;
    bool ok = true;

    if (!hip_ok(hipMalloc(reinterpret_cast<void**>(&d_a), sizeof(float) * aLen))) ok = false;
    if (ok && !hip_ok(hipMalloc(reinterpret_cast<void**>(&d_b), sizeof(float) * bLen))) ok = false;
    if (ok && !hip_ok(hipMalloc(reinterpret_cast<void**>(&d_out), sizeof(float) * outLen))) ok = false;

    if (ok && !hip_ok(hipMemcpy(d_a, a, sizeof(float) * aLen, hipMemcpyHostToDevice))) ok = false;
    if (ok && !hip_ok(hipMemcpy(d_b, b, sizeof(float) * bLen, hipMemcpyHostToDevice))) ok = false;

    if (ok) {
        dim3 block(16, 16);
        dim3 grid((n + block.x - 1) / block.x, (m + block.y - 1) / block.y);
        matmul_kernel<<<grid, block>>>(d_a, d_b, d_out, m, n, k);
        ok = hip_ok(hipGetLastError());
    }

    if (ok && !hip_ok(hipDeviceSynchronize())) {
        ok = false;
    }

    if (ok && !hip_ok(hipMemcpy(out, d_out, sizeof(float) * outLen, hipMemcpyDeviceToHost))) {
        ok = false;
    }

    if (d_a) hipFree(d_a);
    if (d_b) hipFree(d_b);
    if (d_out) hipFree(d_out);

    env->ReleaseFloatArrayElements(aArr, a, JNI_ABORT);
    env->ReleaseFloatArrayElements(bArr, b, JNI_ABORT);
    env->ReleaseFloatArrayElements(outArr, out, ok ? 0 : JNI_ABORT);

    return ok ? JNI_TRUE : JNI_FALSE;
}

// -------------------------
// T5 ROCm backend (native)
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
            hipFree(ptr);
            ptr = nullptr;
            capacity = 0;
        }
        if (!hip_ok(hipMalloc(reinterpret_cast<void**>(&ptr), sizeof(float) * count))) {
            return false;
        }
        capacity = count;
        return true;
    }

    void release() {
        if (ptr) {
            hipFree(ptr);
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
            hipFree(ptr);
            ptr = nullptr;
            capacity = 0;
        }
        if (!hip_ok(hipMalloc(reinterpret_cast<void**>(&ptr), sizeof(int) * count))) {
            return false;
        }
        capacity = count;
        return true;
    }

    void release() {
        if (ptr) {
            hipFree(ptr);
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
};

struct FfnWeights {
    float* wi0 = nullptr;
    float* wi1 = nullptr;
    float* wo = nullptr;
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
};

struct T5Gpu {
    T5Config cfg;
    hipblasHandle_t handle = nullptr;
    float* d_shared = nullptr;
    float* d_encoderRelBias = nullptr;
    float* d_decoderRelBias = nullptr;
    std::vector<EncoderLayer> encoder;
    std::vector<DecoderLayer> decoder;
    float* d_encoderFinalLn = nullptr;
    float* d_decoderFinalLn = nullptr;
    float* d_lmHead = nullptr;
    Workspace ws;
};

struct EncoderState {
    int seq = 0;
    float* hidden = nullptr;
};

static bool hipblas_ok(hipblasStatus_t status) {
    return status == HIPBLAS_STATUS_SUCCESS;
}

static void cuda_free(void* p) {
    if (p) hipFree(p);
}

static bool cuda_alloc(float** out, size_t count) {
    *out = nullptr;
    return hip_ok(hipMalloc(reinterpret_cast<void**>(out), sizeof(float) * count));
}

static bool cuda_copy_to_device(float* dst, const std::vector<float>& src) {
    return hip_ok(hipMemcpy(dst, src.data(), sizeof(float) * src.size(), hipMemcpyHostToDevice));
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

static bool upload_tensor(const std::string& name, const HostTensor& t, float** outPtr) {
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

static bool gemm_row_major(hipblasHandle_t handle,
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
    hipblasOperation_t opB = transB ? HIPBLAS_OP_T : HIPBLAS_OP_N;
    hipblasOperation_t opA = transA ? HIPBLAS_OP_T : HIPBLAS_OP_N;
    // Row-major GEMM via column-major: C^T = op(B)^T * op(A)^T
    return hipblas_ok(hipblasSgemm(handle,
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
    if (!hip_ok(hipGetLastError())) return false;

    std::vector<float> hMax(blocks);
    std::vector<int> hIdx(blocks);
    if (!hip_ok(hipMemcpy(hMax.data(), ws.reduceMax.ptr, sizeof(float) * blocks, hipMemcpyDeviceToHost))) return false;
    if (!hip_ok(hipMemcpy(hIdx.data(), ws.reduceIdx.ptr, sizeof(int) * blocks, hipMemcpyDeviceToHost))) return false;

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
    return hip_ok(hipGetLastError());
}

static bool run_softmax(float* attn, int rows, int cols) {
    dim3 grid(rows);
    dim3 block(256);
    softmax_kernel<<<grid, block>>>(attn, rows, cols);
    return hip_ok(hipGetLastError());
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
    if (!hip_ok(hipGetLastError())) return false;
    if (!run_softmax(attn, cfg.numHeads * qLen, kLen)) return false;
    dim3 blockOut(16, 8);
    dim3 gridOut((cfg.dKv + blockOut.x - 1) / blockOut.x, (qLen + blockOut.y - 1) / blockOut.y, cfg.numHeads);
    attn_out_kernel<<<gridOut, blockOut>>>(attn, v, out, qLen, kLen, cfg.numHeads, cfg.dKv, dAttn);
    return hip_ok(hipGetLastError());
}

static bool run_embed(const T5Config& cfg, Workspace& ws, const std::vector<int>& ids, const float* shared, float* out) {
    if (!ws.ids.ensure(ids.size())) return false;
    if (!hip_ok(hipMemcpy(ws.ids.ptr, ids.data(), sizeof(int) * ids.size(), hipMemcpyHostToDevice))) return false;
    int total = static_cast<int>(ids.size()) * cfg.dModel;
    int threads = 256;
    int blocks = (total + threads - 1) / threads;
    embed_kernel<<<blocks, threads>>>(ws.ids.ptr, static_cast<int>(ids.size()), cfg.vocabSize, cfg.dModel, cfg.unkId, shared, out);
    return hip_ok(hipGetLastError());
}

static void destroy_gpu(T5Gpu* gpu) {
    if (!gpu) return;
    cuda_free(gpu->d_shared);
    cuda_free(gpu->d_encoderRelBias);
    cuda_free(gpu->d_decoderRelBias);
    cuda_free(gpu->d_encoderFinalLn);
    cuda_free(gpu->d_decoderFinalLn);
    cuda_free(gpu->d_lmHead);
    for (auto& layer : gpu->encoder) {
        cuda_free(layer.ln1);
        cuda_free(layer.attn.wq);
        cuda_free(layer.attn.wk);
        cuda_free(layer.attn.wv);
        cuda_free(layer.attn.wo);
        cuda_free(layer.ln2);
        cuda_free(layer.ffn.wi0);
        cuda_free(layer.ffn.wi1);
        cuda_free(layer.ffn.wo);
    }
    for (auto& layer : gpu->decoder) {
        cuda_free(layer.ln1);
        cuda_free(layer.selfAttn.wq);
        cuda_free(layer.selfAttn.wk);
        cuda_free(layer.selfAttn.wv);
        cuda_free(layer.selfAttn.wo);
        cuda_free(layer.ln2);
        cuda_free(layer.crossAttn.wq);
        cuda_free(layer.crossAttn.wk);
        cuda_free(layer.crossAttn.wv);
        cuda_free(layer.crossAttn.wo);
        cuda_free(layer.ln3);
        cuda_free(layer.ffn.wi0);
        cuda_free(layer.ffn.wi1);
        cuda_free(layer.ffn.wo);
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
    if (gpu->handle) {
        hipblasDestroy(gpu->handle);
        gpu->handle = nullptr;
    }
    delete gpu;
}

static bool build_gpu(const HostModel& host, T5Gpu*& out) {
    auto* gpu = new T5Gpu();
    gpu->cfg = host.cfg;
    if (!hipblas_ok(hipblasCreate(&gpu->handle))) {
        delete gpu;
        return false;
    }
#ifdef HIPBLAS_TENSOR_OP_MATH
    hipblasSetMathMode(gpu->handle, HIPBLAS_TENSOR_OP_MATH);
#endif

    auto get = [&](const std::string& name) -> const HostTensor* {
        auto it = host.tensors.find(name);
        if (it == host.tensors.end()) return nullptr;
        return &it->second;
    };

    const HostTensor* shared = get("shared.weight");
    if (!shared || !upload_tensor("shared.weight", *shared, &gpu->d_shared)) {
        destroy_gpu(gpu);
        return false;
    }

    const HostTensor* encBias = get("encoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight");
    const HostTensor* decBias = get("decoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight");
    if (!encBias || !upload_tensor("encoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight", *encBias, &gpu->d_encoderRelBias)) {
        destroy_gpu(gpu);
        return false;
    }
    if (!decBias || !upload_tensor("decoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight", *decBias, &gpu->d_decoderRelBias)) {
        destroy_gpu(gpu);
        return false;
    }

    const HostTensor* encFinal = get("encoder.final_layer_norm.weight");
    const HostTensor* decFinal = get("decoder.final_layer_norm.weight");
    if (!encFinal || !upload_tensor("encoder.final_layer_norm.weight", *encFinal, &gpu->d_encoderFinalLn)) {
        destroy_gpu(gpu);
        return false;
    }
    if (!decFinal || !upload_tensor("decoder.final_layer_norm.weight", *decFinal, &gpu->d_decoderFinalLn)) {
        destroy_gpu(gpu);
        return false;
    }

    const HostTensor* lmHead = get("lm_head.weight");
    if (!lmHead || !upload_tensor("lm_head.weight", *lmHead, &gpu->d_lmHead)) {
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
        if (!upload_tensor(prefix + "layer.0.layer_norm.weight", *ln1, &layer.ln1) ||
            !upload_tensor(prefix + "layer.0.SelfAttention.q.weight", *wq, &layer.attn.wq) ||
            !upload_tensor(prefix + "layer.0.SelfAttention.k.weight", *wk, &layer.attn.wk) ||
            !upload_tensor(prefix + "layer.0.SelfAttention.v.weight", *wv, &layer.attn.wv) ||
            !upload_tensor(prefix + "layer.0.SelfAttention.o.weight", *wo, &layer.attn.wo) ||
            !upload_tensor(prefix + "layer.1.layer_norm.weight", *ln2, &layer.ln2) ||
            !upload_tensor(prefix + "layer.1.DenseReluDense.wi_0.weight", *wi0, &layer.ffn.wi0) ||
            !upload_tensor(prefix + "layer.1.DenseReluDense.wi_1.weight", *wi1, &layer.ffn.wi1) ||
            !upload_tensor(prefix + "layer.1.DenseReluDense.wo.weight", *wo2, &layer.ffn.wo)) {
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
        if (!upload_tensor(prefix + "layer.0.layer_norm.weight", *ln1, &layer.ln1) ||
            !upload_tensor(prefix + "layer.0.SelfAttention.q.weight", *wq, &layer.selfAttn.wq) ||
            !upload_tensor(prefix + "layer.0.SelfAttention.k.weight", *wk, &layer.selfAttn.wk) ||
            !upload_tensor(prefix + "layer.0.SelfAttention.v.weight", *wv, &layer.selfAttn.wv) ||
            !upload_tensor(prefix + "layer.0.SelfAttention.o.weight", *wo, &layer.selfAttn.wo) ||
            !upload_tensor(prefix + "layer.1.layer_norm.weight", *ln2, &layer.ln2) ||
            !upload_tensor(prefix + "layer.1.EncDecAttention.q.weight", *wq2, &layer.crossAttn.wq) ||
            !upload_tensor(prefix + "layer.1.EncDecAttention.k.weight", *wk2, &layer.crossAttn.wk) ||
            !upload_tensor(prefix + "layer.1.EncDecAttention.v.weight", *wv2, &layer.crossAttn.wv) ||
            !upload_tensor(prefix + "layer.1.EncDecAttention.o.weight", *wo2, &layer.crossAttn.wo) ||
            !upload_tensor(prefix + "layer.2.layer_norm.weight", *ln3, &layer.ln3) ||
            !upload_tensor(prefix + "layer.2.DenseReluDense.wi_0.weight", *wi0, &layer.ffn.wi0) ||
            !upload_tensor(prefix + "layer.2.DenseReluDense.wi_1.weight", *wi1, &layer.ffn.wi1) ||
            !upload_tensor(prefix + "layer.2.DenseReluDense.wo.weight", *wo3, &layer.ffn.wo)) {
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
        if (!gemm_row_major(gpu->handle, false, false, seq, dAttn, dModel, gpu->ws.norm.ptr, l.attn.wq, gpu->ws.q.ptr)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dAttn, dModel, gpu->ws.norm.ptr, l.attn.wk, gpu->ws.k.ptr)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dAttn, dModel, gpu->ws.norm.ptr, l.attn.wv, gpu->ws.v.ptr)) return false;
        if (!run_attention(gpu->cfg, gpu->ws.q.ptr, gpu->ws.k.ptr, gpu->ws.v.ptr, gpu->ws.attn.ptr, gpu->ws.out.ptr,
                           seq, seq, false, gpu->d_encoderRelBias, true)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dModel, dAttn, gpu->ws.out.ptr, l.attn.wo, gpu->ws.tmp.ptr)) return false;
        int total = seq * dModel;
        int threads = 256;
        int blocks = (total + threads - 1) / threads;
        add_in_place_kernel<<<blocks, threads>>>(gpu->ws.x.ptr, gpu->ws.tmp.ptr, total);
        if (!hip_ok(hipGetLastError())) return false;

        if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.norm.ptr, l.ln2, seq, dModel, gpu->cfg.layerNormEps)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dFf, dModel, gpu->ws.norm.ptr, l.ffn.wi0, gpu->ws.ff0.ptr)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dFf, dModel, gpu->ws.norm.ptr, l.ffn.wi1, gpu->ws.ff1.ptr)) return false;
        int ffTotal = seq * dFf;
        int ffBlocks = (ffTotal + threads - 1) / threads;
        gelu_mul_kernel<<<ffBlocks, threads>>>(gpu->ws.ff0.ptr, gpu->ws.ff1.ptr, ffTotal);
        if (!hip_ok(hipGetLastError())) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dModel, dFf, gpu->ws.ff0.ptr, l.ffn.wo, gpu->ws.tmp.ptr)) return false;
        add_in_place_kernel<<<blocks, threads>>>(gpu->ws.x.ptr, gpu->ws.tmp.ptr, total);
        if (!hip_ok(hipGetLastError())) return false;
    }

    if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.x.ptr, gpu->d_encoderFinalLn, seq, dModel, gpu->cfg.layerNormEps)) return false;

    enc.seq = seq;
    if (!cuda_alloc(&enc.hidden, static_cast<size_t>(seq) * dModel)) return false;
    if (!hip_ok(hipMemcpy(enc.hidden, gpu->ws.x.ptr, sizeof(float) * seq * dModel, hipMemcpyDeviceToDevice))) return false;
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
        if (!gemm_row_major(gpu->handle, false, false, encSeq, dAttn, dModel, enc.hidden, l.crossAttn.wk, d_k)) {
            cuda_free(d_k);
            cuda_free(d_v);
            return false;
        }
        if (!gemm_row_major(gpu->handle, false, false, encSeq, dAttn, dModel, enc.hidden, l.crossAttn.wv, d_v)) {
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

static bool run_decoder_step(T5Gpu* gpu,
                             const std::vector<int>& outIds,
                             const EncoderState& enc,
                             const std::vector<float*>& encK,
                             const std::vector<float*>& encV,
                             int& nextId) {
    int seq = static_cast<int>(outIds.size());
    int dModel = gpu->cfg.dModel;
    int dAttn = gpu->cfg.numHeads * gpu->cfg.dKv;
    int dFf = gpu->cfg.dFf;
    int encSeq = enc.seq;

    if (!gpu->ws.x.ensure(static_cast<size_t>(seq) * dModel)) return false;
    if (!gpu->ws.norm.ensure(static_cast<size_t>(seq) * dModel)) return false;
    if (!gpu->ws.q.ensure(static_cast<size_t>(seq) * dAttn)) return false;
    if (!gpu->ws.k.ensure(static_cast<size_t>(seq) * dAttn)) return false;
    if (!gpu->ws.v.ensure(static_cast<size_t>(seq) * dAttn)) return false;
    if (!gpu->ws.attn.ensure(static_cast<size_t>(gpu->cfg.numHeads) * seq * std::max(seq, encSeq))) return false;
    if (!gpu->ws.out.ensure(static_cast<size_t>(seq) * dAttn)) return false;
    if (!gpu->ws.ff0.ensure(static_cast<size_t>(seq) * dFf)) return false;
    if (!gpu->ws.ff1.ensure(static_cast<size_t>(seq) * dFf)) return false;
    if (!gpu->ws.tmp.ensure(static_cast<size_t>(seq) * dModel)) return false;

    if (!run_embed(gpu->cfg, gpu->ws, outIds, gpu->d_shared, gpu->ws.x.ptr)) return false;

    for (int layer = 0; layer < gpu->cfg.numDecoderLayers; layer++) {
        DecoderLayer& l = gpu->decoder[static_cast<size_t>(layer)];
        if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.norm.ptr, l.ln1, seq, dModel, gpu->cfg.layerNormEps)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dAttn, dModel, gpu->ws.norm.ptr, l.selfAttn.wq, gpu->ws.q.ptr)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dAttn, dModel, gpu->ws.norm.ptr, l.selfAttn.wk, gpu->ws.k.ptr)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dAttn, dModel, gpu->ws.norm.ptr, l.selfAttn.wv, gpu->ws.v.ptr)) return false;
        if (!run_attention(gpu->cfg, gpu->ws.q.ptr, gpu->ws.k.ptr, gpu->ws.v.ptr, gpu->ws.attn.ptr, gpu->ws.out.ptr,
                           seq, seq, true, gpu->d_decoderRelBias, false)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dModel, dAttn, gpu->ws.out.ptr, l.selfAttn.wo, gpu->ws.tmp.ptr)) return false;
        int total = seq * dModel;
        int threads = 256;
        int blocks = (total + threads - 1) / threads;
        add_in_place_kernel<<<blocks, threads>>>(gpu->ws.x.ptr, gpu->ws.tmp.ptr, total);
        if (!hip_ok(hipGetLastError())) return false;

        if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.norm.ptr, l.ln2, seq, dModel, gpu->cfg.layerNormEps)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dAttn, dModel, gpu->ws.norm.ptr, l.crossAttn.wq, gpu->ws.q.ptr)) return false;
        if (!run_attention(gpu->cfg, gpu->ws.q.ptr, encK[static_cast<size_t>(layer)], encV[static_cast<size_t>(layer)],
                           gpu->ws.attn.ptr, gpu->ws.out.ptr, seq, encSeq, false, nullptr, true)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dModel, dAttn, gpu->ws.out.ptr, l.crossAttn.wo, gpu->ws.tmp.ptr)) return false;
        add_in_place_kernel<<<blocks, threads>>>(gpu->ws.x.ptr, gpu->ws.tmp.ptr, total);
        if (!hip_ok(hipGetLastError())) return false;

        if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.norm.ptr, l.ln3, seq, dModel, gpu->cfg.layerNormEps)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dFf, dModel, gpu->ws.norm.ptr, l.ffn.wi0, gpu->ws.ff0.ptr)) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dFf, dModel, gpu->ws.norm.ptr, l.ffn.wi1, gpu->ws.ff1.ptr)) return false;
        int ffTotal = seq * dFf;
        int ffBlocks = (ffTotal + threads - 1) / threads;
        gelu_mul_kernel<<<ffBlocks, threads>>>(gpu->ws.ff0.ptr, gpu->ws.ff1.ptr, ffTotal);
        if (!hip_ok(hipGetLastError())) return false;
        if (!gemm_row_major(gpu->handle, false, false, seq, dModel, dFf, gpu->ws.ff0.ptr, l.ffn.wo, gpu->ws.tmp.ptr)) return false;
        add_in_place_kernel<<<blocks, threads>>>(gpu->ws.x.ptr, gpu->ws.tmp.ptr, total);
        if (!hip_ok(hipGetLastError())) return false;
    }

    if (!run_layer_norm(gpu->ws.x.ptr, gpu->ws.x.ptr, gpu->d_decoderFinalLn, seq, dModel, gpu->cfg.layerNormEps)) return false;

    if (!gpu->ws.logits.ensure(gpu->cfg.vocabSize)) return false;

    const float* lastRow = gpu->ws.x.ptr + (seq - 1) * dModel;
    if (!gemm_row_major(gpu->handle, false, false, 1, gpu->cfg.vocabSize, dModel, lastRow, gpu->d_lmHead, gpu->ws.logits.ptr)) return false;

    if (!argmax_device(gpu->ws, gpu->ws.logits.ptr, gpu->cfg.vocabSize, nextId)) return false;
    return true;
}

extern "C" JNIEXPORT jlong JNICALL Java_chess_nn_t5_rocm_Backend_nativeCreate(JNIEnv* env, jclass, jstring path) {
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

extern "C" JNIEXPORT void JNICALL Java_chess_nn_t5_rocm_Backend_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    T5Gpu* gpu = reinterpret_cast<T5Gpu*>(handle);
    destroy_gpu(gpu);
}

extern "C" JNIEXPORT jintArray JNICALL Java_chess_nn_t5_rocm_Backend_nativeGenerateIds(
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

    std::vector<int> outputIds;
    outputIds.reserve(static_cast<size_t>(maxNewTokens) + 1);
    outputIds.push_back(gpu->cfg.decoderStartId);

    for (int step = 0; step < maxNewTokens; step++) {
        int nextId = 0;
        if (!run_decoder_step(gpu, outputIds, enc, encK, encV, nextId)) {
            cuda_free(enc.hidden);
            free_encoder_kv(encK);
            free_encoder_kv(encV);
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

    jintArray outArr = env->NewIntArray(static_cast<jsize>(outputIds.size()));
    if (!outArr) return nullptr;
    env->SetIntArrayRegion(outArr, 0, static_cast<jsize>(outputIds.size()), outputIds.data());
    return outArr;
}
