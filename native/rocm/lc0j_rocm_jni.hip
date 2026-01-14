/*
 * native/rocm/lc0j_rocm_jni.hip
 *
 * Overview
 * --------
 * This translation unit implements the optional ROCm backend for the Java LC0 evaluator in this repo
 * (see src/chess/lc0/rocm/Support.java and src/chess/lc0/rocm/Backend.java).
 *
 * It builds into a shared library (lc0j_rocm) that is loaded via JNI and provides:
 *   - a lightweight ROCm availability check (device count)
 *   - a minimal, batch-size-1 LC0J ".bin" evaluator that runs a single forward pass:
 *       input planes -> trunk -> policy logits + value(WDL)
 *
 * JNI surface
 * -----------
 * Exported native methods (names must match their Java declarations):
 *   - chess.lc0.rocm.Support.nativeDeviceCount() -> int
 *   - chess.lc0.rocm.Backend.nativeCreate(String weightsPath) -> long (opaque handle)
 *   - chess.lc0.rocm.Backend.nativeDestroy(long handle) -> void
 *   - chess.lc0.rocm.Backend.nativeGetInfo(long handle) -> long[7]
 *   - chess.lc0.rocm.Backend.nativePredict(long handle, float[] encoded, float[] policyOut, float[] wdlOut) -> float
 *
 * Data / shapes
 * -------------
 * This backend expects the LC0 "classical" input encoding used by this repo:
 *   - encoded input: float[inputC * 64], where squares are ordered 0..63 (8x8).
 *   - weights format: LC0J ".bin" (magic "LC0J", version 1), matching the Java CPU loader.
 *
 * Internals
 * ---------
 * The evaluator stores all weights and intermediate work buffers on the GPU:
 *   - Convolution weights: float[outC][inC][k][k] (k is 1 or 3)
 *   - Bias vectors: float[outC]
 *   - Dense weights: float[outD][inD], biases float[outD]
 * It uses simple HIP kernels on the default stream. There is no batching.
 *
 * Error handling / limitations
 * ----------------------------
 * - This is intentionally small and pragmatic; it is not a full LC0 implementation.
 * - Many failures return 0/false and let Java fall back to CPU (unless ROCm is forced).
 * - Kernel launches are not exhaustively checked for async errors; hard failures typically surface
 *   on the next hipMemcpy/hip API call.
 * - Device selection is currently fixed to device 0.
 * - Treat a GpuNet instance as single-threaded; callers should not share one handle across threads.
 */

#include <jni.h>

#include <hip/hip_runtime.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <memory>
#include <string>
#include <vector>

static inline int device_count() {
    int count = 0;
    hipError_t err = hipGetDeviceCount(&count);
    if (err != hipSuccess) return 0;
    return count;
}

extern "C" JNIEXPORT jint JNICALL Java_chess_lc0_rocm_Support_nativeDeviceCount(JNIEnv*, jclass) {
    return device_count();
}

// -------------------------
// Minimal LC0J GPU evaluator
// -------------------------

struct ConvLayer {
    int inC = 0;
    int outC = 0;
    int k = 0;
    float* d_w = nullptr;   // [outC][inC][k][k]
    float* d_b = nullptr;   // [outC]
    int64_t params = 0;
};

struct DenseLayer {
    int inD = 0;
    int outD = 0;
    float* d_w = nullptr;  // [outD][inD]
    float* d_b = nullptr;  // [outD]
    int64_t params = 0;
};

struct SeUnit {
    int channels = 0;
    int hidden = 0;
    float* d_w1 = nullptr; // [hidden][channels]
    float* d_b1 = nullptr; // [hidden]
    float* d_w2 = nullptr; // [2*channels][hidden]
    float* d_b2 = nullptr; // [2*channels]
    int64_t params = 0;
};

struct ResidualBlock {
    ConvLayer conv1;
    ConvLayer conv2;
    bool hasSe = false;
    SeUnit se;
};

struct GpuNet {
    int inputC = 0;
    int trunkC = 0;
    int blocks = 0;
    int policyC = 0;
    int valueC = 0;
    int valueHidden = 0;
    int policySize = 0;
    int64_t paramCount = 0;

    ConvLayer inputLayer;
    std::vector<ResidualBlock> tower;
    ConvLayer policyStem;
    ConvLayer policyOut;
    ConvLayer valueConv;
    DenseLayer valueFc1;
    DenseLayer valueFc2;

    int* d_policyMap = nullptr;
    int policyMapLen = 0;
    float* d_policyMapped = nullptr; // [policySize]

    // Workspace (device).
    float* d_in = nullptr;        // [inputC*64]
    float* d_cur = nullptr;       // [trunkC*64]
    float* d_next = nullptr;      // [trunkC*64]
    float* d_tmp = nullptr;       // [trunkC*64]
    float* d_scratch = nullptr;   // [trunkC*64]
    float* d_policyHidden = nullptr; // [trunkC*64]
    float* d_policyPlanes = nullptr; // [policyC*64]
    float* d_valueInput = nullptr;   // [valueC*64]
    float* d_fc1 = nullptr;          // [valueHidden]
    float* d_logits = nullptr;       // [3]

    // SE workspace (max sizes: trunkC, 2*trunkC).
    float* d_sePooled = nullptr;  // [trunkC]
    float* d_seHidden = nullptr;  // [maxHidden] (we assume constant across blocks; allocate max found)
    float* d_seGates = nullptr;   // [2*trunkC]
    int seMaxHidden = 0;
};

static void cuda_free(void* p) {
    if (p) hipFree(p);
}

template <typename T>
static bool cuda_alloc(T** out, size_t count) {
    *out = nullptr;
    hipError_t err = hipMalloc(reinterpret_cast<void**>(out), sizeof(T) * count);
    return err == hipSuccess;
}

template <typename T>
static bool cuda_copy_to_device(T* dst, const std::vector<T>& src) {
    hipError_t err = hipMemcpy(dst, src.data(), sizeof(T) * src.size(), hipMemcpyHostToDevice);
    return err == hipSuccess;
}

template <typename T>
static bool cuda_copy_to_device(T* dst, const T* src, size_t count) {
    hipError_t err = hipMemcpy(dst, src, sizeof(T) * count, hipMemcpyHostToDevice);
    return err == hipSuccess;
}

// ---- file parsing helpers (LC0J .bin) ----

static bool read_u8(std::ifstream& f, uint8_t& out) {
    f.read(reinterpret_cast<char*>(&out), 1);
    return bool(f);
}

static bool read_i32(std::ifstream& f, int32_t& out) {
    f.read(reinterpret_cast<char*>(&out), 4);
    return bool(f);
}

static bool read_bytes(std::ifstream& f, void* dst, size_t n) {
    f.read(reinterpret_cast<char*>(dst), static_cast<std::streamsize>(n));
    return bool(f);
}

static bool read_float_array(std::ifstream& f, std::vector<float>& out) {
    int32_t size = 0;
    if (!read_i32(f, size)) return false;
    if (size < 0) return false;
    out.resize(static_cast<size_t>(size));
    return read_bytes(f, out.data(), sizeof(float) * out.size());
}

static bool load_conv(std::ifstream& f, ConvLayer& out) {
    int32_t oc, ic, k;
    if (!read_i32(f, oc) || !read_i32(f, ic) || !read_i32(f, k)) return false;
    std::vector<float> w;
    std::vector<float> b;
    if (!read_float_array(f, w) || !read_float_array(f, b)) return false;
    out.inC = ic;
    out.outC = oc;
    out.k = k;
    out.params = static_cast<int64_t>(w.size()) + static_cast<int64_t>(b.size());
    if (!cuda_alloc(&out.d_w, w.size())) return false;
    if (!cuda_alloc(&out.d_b, b.size())) return false;
    if (!cuda_copy_to_device(out.d_w, w)) return false;
    if (!cuda_copy_to_device(out.d_b, b)) return false;
    return true;
}

static bool load_dense(std::ifstream& f, DenseLayer& out, int expectedOut) {
    int32_t od, id;
    if (!read_i32(f, od) || !read_i32(f, id)) return false;
    if (od != expectedOut) return false;
    std::vector<float> w;
    std::vector<float> b;
    if (!read_float_array(f, w) || !read_float_array(f, b)) return false;
    out.inD = id;
    out.outD = od;
    out.params = static_cast<int64_t>(w.size()) + static_cast<int64_t>(b.size());
    if (!cuda_alloc(&out.d_w, w.size())) return false;
    if (!cuda_alloc(&out.d_b, b.size())) return false;
    if (!cuda_copy_to_device(out.d_w, w)) return false;
    if (!cuda_copy_to_device(out.d_b, b)) return false;
    return true;
}

static bool load_se(std::ifstream& f, SeUnit& out, bool& present, int channels, int& maxHidden) {
    uint8_t p = 0;
    if (!read_u8(f, p)) return false;
    present = (p != 0);
    if (!present) return true;
    int32_t hidden = 0;
    int32_t expectedChannels = 0;
    if (!read_i32(f, hidden) || !read_i32(f, expectedChannels)) return false;
    if (expectedChannels != channels) return false;
    std::vector<float> w1, b1, w2, b2;
    if (!read_float_array(f, w1) || !read_float_array(f, b1) || !read_float_array(f, w2) || !read_float_array(f, b2)) return false;
    out.channels = channels;
    out.hidden = hidden;
    out.params = static_cast<int64_t>(w1.size() + b1.size() + w2.size() + b2.size());
    maxHidden = std::max(maxHidden, hidden);

    if (!cuda_alloc(&out.d_w1, w1.size())) return false;
    if (!cuda_alloc(&out.d_b1, b1.size())) return false;
    if (!cuda_alloc(&out.d_w2, w2.size())) return false;
    if (!cuda_alloc(&out.d_b2, b2.size())) return false;
    if (!cuda_copy_to_device(out.d_w1, w1)) return false;
    if (!cuda_copy_to_device(out.d_b1, b1)) return false;
    if (!cuda_copy_to_device(out.d_w2, w2)) return false;
    if (!cuda_copy_to_device(out.d_b2, b2)) return false;
    return true;
}

// ---- HIP kernels ----

__device__ __forceinline__ float relu(float x) { return x > 0.0f ? x : 0.0f; }
__device__ __forceinline__ float sigmoid(float x) { return 1.0f / (1.0f + expf(-x)); }

__global__ void k_conv3x3_no_bias(const float* __restrict__ input, const float* __restrict__ w,
                                 int inC, int outC, float* __restrict__ out) {
    int oc = blockIdx.x;
    int s = threadIdx.x;
    if (oc >= outC || s >= 64) return;
    int row = s >> 3;
    int col = s & 7;
    float acc = 0.0f;
    const int kk = 9;
    const int ocBase = oc * inC * kk;
    for (int ic = 0; ic < inC; ic++) {
        const int inBase = ic * 64;
        const int wBase = ocBase + ic * kk;
        // ky=-1..1, kx=-1..1
        int idx = 0;
        for (int ky = -1; ky <= 1; ky++) {
            int r = row + ky;
            if (r < 0 || r >= 8) {
                idx += 3;
                continue;
            }
            int inRowBase = inBase + (r << 3);
            for (int kx = -1; kx <= 1; kx++, idx++) {
                int c = col + kx;
                if (c < 0 || c >= 8) continue;
                acc += input[inRowBase + c] * w[wBase + idx];
            }
        }
    }
    out[oc * 64 + s] = acc;
}

__global__ void k_conv1x1_no_bias(const float* __restrict__ input, const float* __restrict__ w,
                                 int inC, int outC, float* __restrict__ out) {
    int oc = blockIdx.x;
    int s = threadIdx.x;
    if (oc >= outC || s >= 64) return;
    float acc = 0.0f;
    const int ocBase = oc * inC;
    for (int ic = 0; ic < inC; ic++) {
        acc += input[ic * 64 + s] * w[ocBase + ic];
    }
    out[oc * 64 + s] = acc;
}

__global__ void k_add_bias_relu(float* x, const float* __restrict__ b, int channels) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    int total = channels * 64;
    if (idx >= total) return;
    int ch = idx >> 6;
    x[idx] = relu(x[idx] + b[ch]);
}

__global__ void k_add_bias(float* x, const float* __restrict__ b, int channels) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    int total = channels * 64;
    if (idx >= total) return;
    int ch = idx >> 6;
    x[idx] = x[idx] + b[ch];
}

__global__ void k_add_residual_relu(const float* __restrict__ convOut, const float* __restrict__ bias,
                                   const float* __restrict__ residual, int channels, float* __restrict__ dest) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    int total = channels * 64;
    if (idx >= total) return;
    int ch = idx >> 6;
    float v = convOut[idx] + bias[ch] + residual[idx];
    dest[idx] = relu(v);
}

__global__ void k_se_pool(const float* __restrict__ convOut, const float* __restrict__ bias,
                          int channels, float* __restrict__ pooled) {
    int ch = blockIdx.x;
    int t = threadIdx.x;
    if (ch >= channels || t >= 64) return;
    __shared__ float buf[64];
    buf[t] = convOut[ch * 64 + t];
    __syncthreads();
    for (int stride = 32; stride > 0; stride >>= 1) {
        if (t < stride) buf[t] += buf[t + stride];
        __syncthreads();
    }
    if (t == 0) {
        pooled[ch] = (buf[0] * (1.0f / 64.0f)) + bias[ch];
    }
}

__global__ void k_se_fc1(const float* __restrict__ pooled, const float* __restrict__ w1, const float* __restrict__ b1,
                         int channels, int hidden, float* __restrict__ outHidden) {
    int h = blockIdx.x * blockDim.x + threadIdx.x;
    if (h >= hidden) return;
    float acc = b1[h];
    const float* row = w1 + (static_cast<size_t>(h) * channels);
    for (int ch = 0; ch < channels; ch++) {
        acc += row[ch] * pooled[ch];
    }
    outHidden[h] = relu(acc);
}

__global__ void k_se_fc2(const float* __restrict__ hiddenVec, const float* __restrict__ w2, const float* __restrict__ b2,
                         int hidden, int outDim, float* __restrict__ gates) {
    int o = blockIdx.x * blockDim.x + threadIdx.x;
    if (o >= outDim) return;
    float acc = b2[o];
    const float* row = w2 + (static_cast<size_t>(o) * hidden);
    for (int h = 0; h < hidden; h++) {
        acc += row[h] * hiddenVec[h];
    }
    gates[o] = acc;
}

__global__ void k_se_apply(const float* __restrict__ convOut, const float* __restrict__ bias,
                           const float* __restrict__ residual, const float* __restrict__ gates,
                           int channels, float* __restrict__ dest) {
    int ch = blockIdx.x;
    int s = threadIdx.x;
    if (ch >= channels || s >= 64) return;
    float gamma = sigmoid(gates[ch]);
    float betaExtra = gates[ch + channels];
    int idx = ch * 64 + s;
    float z = convOut[idx] + bias[ch];
    float v = gamma * z + residual[idx] + betaExtra;
    dest[idx] = relu(v);
}

__global__ void k_policy_map(const float* __restrict__ planes, int planesLen,
                             const int* __restrict__ policyMap, int outLen,
                             float* __restrict__ outPolicy) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= outLen) return;
    int idx = policyMap[i];
    if (idx >= 0 && idx < planesLen) {
        outPolicy[i] = planes[idx];
    } else {
        outPolicy[i] = 0.0f;
    }
}

__global__ void k_dense(const float* __restrict__ x, const float* __restrict__ w, const float* __restrict__ b,
                        int inD, int outD, int reluAct, float* __restrict__ y) {
    int o = blockIdx.x * blockDim.x + threadIdx.x;
    if (o >= outD) return;
    float acc = b[o];
    const float* row = w + (static_cast<size_t>(o) * inD);
    for (int i = 0; i < inD; i++) acc += row[i] * x[i];
    y[o] = reluAct ? relu(acc) : acc;
}

static inline void launch_conv_no_bias(const ConvLayer& layer, const float* input, float* out) {
    if (layer.k == 3) {
        k_conv3x3_no_bias<<<layer.outC, 64>>>(input, layer.d_w, layer.inC, layer.outC, out);
    } else if (layer.k == 1) {
        k_conv1x1_no_bias<<<layer.outC, 64>>>(input, layer.d_w, layer.inC, layer.outC, out);
    }
}

// Evaluate one position. Writes:
// - outPolicyDev: [policySize]
// - outWdlHost: [3]
// Returns scalar value (W-L).
static bool eval_one(GpuNet* net, const float* encodedHost, float* outPolicyHost, float* outWdlHost, float& outValue) {
    if (!net) return false;
    if (net->inputC != 112) return false;
    if (hipMemcpy(net->d_in, encodedHost, sizeof(float) * net->inputC * 64, hipMemcpyHostToDevice) != hipSuccess) return false;

    // input conv
    launch_conv_no_bias(net->inputLayer, net->d_in, net->d_cur);
    k_add_bias_relu<<<(net->inputLayer.outC * 64 + 255) / 256, 256>>>(net->d_cur, net->inputLayer.d_b, net->inputLayer.outC);

    // residual tower
    for (int bi = 0; bi < net->blocks; bi++) {
        ResidualBlock& b = net->tower[bi];
        launch_conv_no_bias(b.conv1, net->d_cur, net->d_tmp);
        k_add_bias_relu<<<(b.conv1.outC * 64 + 255) / 256, 256>>>(net->d_tmp, b.conv1.d_b, b.conv1.outC);

        launch_conv_no_bias(b.conv2, net->d_tmp, net->d_scratch);
        if (!b.hasSe) {
            k_add_residual_relu<<<(b.conv2.outC * 64 + 255) / 256, 256>>>(net->d_scratch, b.conv2.d_b, net->d_cur, b.conv2.outC, net->d_next);
        } else {
            k_se_pool<<<b.conv2.outC, 64>>>(net->d_scratch, b.conv2.d_b, b.conv2.outC, net->d_sePooled);
            k_se_fc1<<<(b.se.hidden + 255) / 256, 256>>>(net->d_sePooled, b.se.d_w1, b.se.d_b1, b.conv2.outC, b.se.hidden, net->d_seHidden);
            k_se_fc2<<<((2 * b.conv2.outC) + 255) / 256, 256>>>(net->d_seHidden, b.se.d_w2, b.se.d_b2, b.se.hidden, 2 * b.conv2.outC, net->d_seGates);
            k_se_apply<<<b.conv2.outC, 64>>>(net->d_scratch, b.conv2.d_b, net->d_cur, net->d_seGates, b.conv2.outC, net->d_next);
        }
        std::swap(net->d_cur, net->d_next);
    }

    // policy head
    launch_conv_no_bias(net->policyStem, net->d_cur, net->d_policyHidden);
    k_add_bias_relu<<<(net->policyStem.outC * 64 + 255) / 256, 256>>>(net->d_policyHidden, net->policyStem.d_b, net->policyStem.outC);
    launch_conv_no_bias(net->policyOut, net->d_policyHidden, net->d_policyPlanes);
    k_add_bias<<<(net->policyOut.outC * 64 + 255) / 256, 256>>>(net->d_policyPlanes, net->policyOut.d_b, net->policyOut.outC);

    // map policy planes -> policy vector
    k_policy_map<<<(net->policySize + 255) / 256, 256>>>(net->d_policyPlanes, net->policyOut.outC * 64, net->d_policyMap, net->policySize, net->d_policyMapped);
    if (hipMemcpy(outPolicyHost, net->d_policyMapped, sizeof(float) * net->policySize, hipMemcpyDeviceToHost) != hipSuccess) {
        return false;
    }

    // value head
    launch_conv_no_bias(net->valueConv, net->d_cur, net->d_valueInput);
    k_add_bias_relu<<<(net->valueConv.outC * 64 + 255) / 256, 256>>>(net->d_valueInput, net->valueConv.d_b, net->valueConv.outC);
    // fc1: input is valueC*64 vector
    k_dense<<<(net->valueHidden + 255) / 256, 256>>>(net->d_valueInput, net->valueFc1.d_w, net->valueFc1.d_b, net->valueFc1.inD, net->valueFc1.outD, 1, net->d_fc1);
    // fc2 -> logits[3]
    k_dense<<<1, 32>>>(net->d_fc1, net->valueFc2.d_w, net->valueFc2.d_b, net->valueFc2.inD, net->valueFc2.outD, 0, net->d_logits);

    float logitsHost[3];
    if (hipMemcpy(logitsHost, net->d_logits, sizeof(float) * 3, hipMemcpyDeviceToHost) != hipSuccess) return false;

    // softmax on host
    float m = std::max(logitsHost[0], std::max(logitsHost[1], logitsHost[2]));
    float e0 = std::exp(logitsHost[0] - m);
    float e1 = std::exp(logitsHost[1] - m);
    float e2 = std::exp(logitsHost[2] - m);
    float s = e0 + e1 + e2;
    float w = (s > 0.0f) ? (e0 / s) : 0.0f;
    float d = (s > 0.0f) ? (e1 / s) : 0.0f;
    float l = (s > 0.0f) ? (e2 / s) : 0.0f;
    outWdlHost[0] = w;
    outWdlHost[1] = d;
    outWdlHost[2] = l;
    outValue = w - l;
    return true;
}

static void destroy_net(GpuNet* net) {
    if (!net) return;
    auto freeConv = [&](ConvLayer& c) {
        cuda_free(c.d_w);
        cuda_free(c.d_b);
        c.d_w = nullptr;
        c.d_b = nullptr;
    };
    auto freeDense = [&](DenseLayer& d) {
        cuda_free(d.d_w);
        cuda_free(d.d_b);
        d.d_w = nullptr;
        d.d_b = nullptr;
    };
    auto freeSe = [&](SeUnit& s) {
        cuda_free(s.d_w1);
        cuda_free(s.d_b1);
        cuda_free(s.d_w2);
        cuda_free(s.d_b2);
        s.d_w1 = s.d_b1 = s.d_w2 = s.d_b2 = nullptr;
    };

    freeConv(net->inputLayer);
    for (auto& b : net->tower) {
        freeConv(b.conv1);
        freeConv(b.conv2);
        if (b.hasSe) freeSe(b.se);
    }
    freeConv(net->policyStem);
    freeConv(net->policyOut);
    freeConv(net->valueConv);
    freeDense(net->valueFc1);
    freeDense(net->valueFc2);
    cuda_free(net->d_policyMap);
    cuda_free(net->d_policyMapped);

    cuda_free(net->d_in);
    cuda_free(net->d_cur);
    cuda_free(net->d_next);
    cuda_free(net->d_tmp);
    cuda_free(net->d_scratch);
    cuda_free(net->d_policyHidden);
    cuda_free(net->d_policyPlanes);
    cuda_free(net->d_valueInput);
    cuda_free(net->d_fc1);
    cuda_free(net->d_logits);
    cuda_free(net->d_sePooled);
    cuda_free(net->d_seHidden);
    cuda_free(net->d_seGates);

    delete net;
}

static GpuNet* create_net(const std::string& path) {
    if (device_count() <= 0) return nullptr;
    // pick device 0
    if (hipSetDevice(0) != hipSuccess) return nullptr;

    std::ifstream f(path, std::ios::binary);
    if (!f) return nullptr;

    char magic[4];
    if (!read_bytes(f, magic, 4)) return nullptr;
    if (magic[0] != 'L' || magic[1] != 'C' || magic[2] != '0' || magic[3] != 'J') return nullptr;
    int32_t version = 0;
    if (!read_i32(f, version) || version != 1) return nullptr;

    auto net = std::make_unique<GpuNet>();
    auto fail = [&]() -> GpuNet* {
        destroy_net(net.release());
        return nullptr;
    };
    int32_t inputC, trunkC, blocks, policyC, valueC, valueHidden, policyMapLen, wdlOutputs;
    if (!read_i32(f, inputC) || !read_i32(f, trunkC) || !read_i32(f, blocks) || !read_i32(f, policyC) ||
        !read_i32(f, valueC) || !read_i32(f, valueHidden) || !read_i32(f, policyMapLen) || !read_i32(f, wdlOutputs)) {
        return nullptr;
    }
    if (wdlOutputs != 3) return nullptr;

    net->inputC = inputC;
    net->trunkC = trunkC;
    net->blocks = blocks;
    net->policyC = policyC;
    net->valueC = valueC;
    net->valueHidden = valueHidden;
    net->policyMapLen = policyMapLen;
    net->policySize = policyMapLen;

    if (!load_conv(f, net->inputLayer)) return fail();
    net->paramCount += net->inputLayer.params;

    net->tower.resize(static_cast<size_t>(blocks));
    int maxHidden = 0;
    for (int i = 0; i < blocks; i++) {
        ResidualBlock& b = net->tower[i];
        if (!load_conv(f, b.conv1)) return fail();
        if (!load_conv(f, b.conv2)) return fail();
        net->paramCount += b.conv1.params + b.conv2.params;
        bool present = false;
        if (!load_se(f, b.se, present, b.conv2.outC, maxHidden)) return fail();
        b.hasSe = present;
        if (b.hasSe) net->paramCount += b.se.params;
    }
    net->seMaxHidden = maxHidden;

    if (!load_conv(f, net->policyStem)) return fail();
    if (!load_conv(f, net->policyOut)) return fail();
    if (!load_conv(f, net->valueConv)) return fail();
    if (net->policyOut.outC != policyC) return fail();
    if (net->valueConv.outC != valueC) return fail();
    net->paramCount += net->policyStem.params + net->policyOut.params + net->valueConv.params;

    if (!load_dense(f, net->valueFc1, valueHidden)) return fail();
    if (!load_dense(f, net->valueFc2, 3)) return fail();
    net->paramCount += net->valueFc1.params + net->valueFc2.params;

    // policy map
    int32_t mapEntries = 0;
    if (!read_i32(f, mapEntries)) return fail();
    if (mapEntries != policyMapLen) return fail();
    std::vector<int32_t> policyMap(static_cast<size_t>(mapEntries));
    if (!read_bytes(f, policyMap.data(), sizeof(int32_t) * policyMap.size())) return fail();
    if (!cuda_alloc(&net->d_policyMap, policyMap.size())) return fail();
    if (hipMemcpy(net->d_policyMap, policyMap.data(), sizeof(int32_t) * policyMap.size(), hipMemcpyHostToDevice) != hipSuccess) return fail();

    // ensure EOF
    f.peek();
    if (!f.eof()) {
        // trailing bytes are treated as error (matches Java loader).
        return fail();
    }

    // workspace allocations
    if (!cuda_alloc(&net->d_in, static_cast<size_t>(inputC) * 64)) return fail();
    if (!cuda_alloc(&net->d_cur, static_cast<size_t>(trunkC) * 64)) return fail();
    if (!cuda_alloc(&net->d_next, static_cast<size_t>(trunkC) * 64)) return fail();
    if (!cuda_alloc(&net->d_tmp, static_cast<size_t>(trunkC) * 64)) return fail();
    if (!cuda_alloc(&net->d_scratch, static_cast<size_t>(trunkC) * 64)) return fail();
    if (!cuda_alloc(&net->d_policyHidden, static_cast<size_t>(trunkC) * 64)) return fail();
    if (!cuda_alloc(&net->d_policyPlanes, static_cast<size_t>(policyC) * 64)) return fail();
    if (!cuda_alloc(&net->d_valueInput, static_cast<size_t>(valueC) * 64)) return fail();
    if (!cuda_alloc(&net->d_fc1, static_cast<size_t>(valueHidden))) return fail();
    if (!cuda_alloc(&net->d_logits, 3)) return fail();

    if (!cuda_alloc(&net->d_sePooled, static_cast<size_t>(trunkC))) return fail();
    if (net->seMaxHidden > 0) {
        if (!cuda_alloc(&net->d_seHidden, static_cast<size_t>(net->seMaxHidden))) return fail();
    } else {
        if (!cuda_alloc(&net->d_seHidden, 1)) return fail();
    }
    if (!cuda_alloc(&net->d_seGates, static_cast<size_t>(2 * trunkC))) return fail();
    if (!cuda_alloc(&net->d_policyMapped, static_cast<size_t>(net->policySize))) return fail();

    return net.release();
}

// ---- JNI for RocmLc0 ----

static jlong backend_nativeCreate(JNIEnv* env, jstring jpath) {
    if (!jpath) return 0;
    const char* cpath = env->GetStringUTFChars(jpath, nullptr);
    if (!cpath) return 0;
    std::string path(cpath);
    env->ReleaseStringUTFChars(jpath, cpath);

    GpuNet* net = create_net(path);
    return reinterpret_cast<jlong>(net);
}

extern "C" JNIEXPORT jlong JNICALL Java_chess_lc0_rocm_Backend_nativeCreate(JNIEnv* env, jclass, jstring jpath) {
    return backend_nativeCreate(env, jpath);
}

extern "C" JNIEXPORT void JNICALL Java_chess_lc0_rocm_Backend_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    destroy_net(reinterpret_cast<GpuNet*>(handle));
}

extern "C" JNIEXPORT jlongArray JNICALL Java_chess_lc0_rocm_Backend_nativeGetInfo(JNIEnv* env, jclass, jlong handle) {
    GpuNet* net = reinterpret_cast<GpuNet*>(handle);
    if (!net) return nullptr;
    jlong vals[7];
    vals[0] = net->inputC;
    vals[1] = net->trunkC;
    vals[2] = net->blocks;
    vals[3] = net->policyC;
    vals[4] = net->valueC;
    vals[5] = net->policySize;
    vals[6] = net->paramCount;
    jlongArray arr = env->NewLongArray(7);
    if (!arr) return nullptr;
    env->SetLongArrayRegion(arr, 0, 7, vals);
    return arr;
}

extern "C" JNIEXPORT jfloat JNICALL Java_chess_lc0_rocm_Backend_nativePredict(
        JNIEnv* env, jclass, jlong handle, jfloatArray jencoded, jfloatArray joutPolicy, jfloatArray joutWdl) {
    GpuNet* net = reinterpret_cast<GpuNet*>(handle);
    if (!net) return 0.0f;
    if (!jencoded || !joutPolicy || !joutWdl) return 0.0f;

    const jsize encLen = env->GetArrayLength(jencoded);
    const jsize polLen = env->GetArrayLength(joutPolicy);
    const jsize wdlLen = env->GetArrayLength(joutWdl);
    if (encLen != net->inputC * 64) return 0.0f;
    if (polLen != net->policySize) return 0.0f;
    if (wdlLen != 3) return 0.0f;

    std::vector<float> encoded(static_cast<size_t>(encLen));
    env->GetFloatArrayRegion(jencoded, 0, encLen, encoded.data());

    std::vector<float> policy(static_cast<size_t>(polLen));
    float wdl[3] = {0, 0, 0};
    float value = 0.0f;
    if (!eval_one(net, encoded.data(), policy.data(), wdl, value)) {
        return 0.0f;
    }

    env->SetFloatArrayRegion(joutPolicy, 0, polLen, policy.data());
    env->SetFloatArrayRegion(joutWdl, 0, 3, wdl);
    return value;
}
