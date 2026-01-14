/*
 * native/oneapi/lc0j_oneapi_jni.cpp
 *
 * Overview
 * --------
 * This translation unit implements the optional oneAPI backend for the Java LC0 evaluator in this repo
 * (see src/chess/lc0/oneapi/Support.java and src/chess/lc0/oneapi/Backend.java).
 *
 * It builds into a shared library (lc0j_oneapi) that is loaded via JNI and provides:
 *   - a lightweight Intel GPU availability check (device count)
 *   - a minimal, batch-size-1 LC0J ".bin" evaluator that runs a single forward pass:
 *       input planes -> trunk -> policy logits + value(WDL)
 *
 * JNI surface
 * -----------
 * Exported native methods (names must match their Java declarations):
 *   - chess.lc0.oneapi.Support.nativeDeviceCount() -> int
 *   - chess.lc0.oneapi.Backend.nativeCreate(String weightsPath) -> long (opaque handle)
 *   - chess.lc0.oneapi.Backend.nativeDestroy(long handle) -> void
 *   - chess.lc0.oneapi.Backend.nativeGetInfo(long handle) -> long[7]
 *   - chess.lc0.oneapi.Backend.nativePredict(long handle, float[] encoded, float[] policyOut, float[] wdlOut) -> float
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
 * It uses simple SYCL kernels on an in-order queue. There is no batching.
 *
 * Error handling / limitations
 * ----------------------------
 * - This is intentionally small and pragmatic; it is not a full LC0 implementation.
 * - Many failures return 0/false and let Java fall back to CPU (unless oneAPI is forced).
 * - Kernel launches are not exhaustively checked for async errors; hard failures typically surface
 *   on the next queue memcpy/wait.
 * - Device selection is currently fixed to the first Intel GPU.
 * - Treat a SyclNet instance as single-threaded; callers should not share one handle across threads.
 */

#include <jni.h>

#include <sycl/sycl.hpp>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <memory>
#include <string>
#include <vector>

static std::string to_lower(std::string v) {
    for (char& c : v) {
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    }
    return v;
}

static bool is_intel_gpu(const sycl::device& dev) {
    if (!dev.is_gpu()) return false;
    std::string vendor = to_lower(dev.get_info<sycl::info::device::vendor>());
    return vendor.find("intel") != std::string::npos;
}

static std::vector<sycl::device> intel_gpus() {
    std::vector<sycl::device> out;
    try {
        auto devices = sycl::device::get_devices(sycl::info::device_type::gpu);
        for (const auto& dev : devices) {
            if (is_intel_gpu(dev)) {
                out.push_back(dev);
            }
        }
    } catch (const sycl::exception&) {
        return {};
    }
    return out;
}

static inline int device_count() {
    return static_cast<int>(intel_gpus().size());
}

static bool select_intel_gpu(sycl::device& out) {
    auto devices = intel_gpus();
    if (devices.empty()) {
        return false;
    }
    out = devices.front();
    return true;
}

extern "C" JNIEXPORT jint JNICALL Java_chess_lc0_oneapi_Support_nativeDeviceCount(JNIEnv*, jclass) {
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

struct SyclNet {
    sycl::queue q;

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
    float* d_seHidden = nullptr;  // [maxHidden]
    float* d_seGates = nullptr;   // [2*trunkC]
    int seMaxHidden = 0;
};

static void sycl_free(sycl::queue& q, void* p) {
    if (p) {
        sycl::free(p, q);
    }
}

template <typename T>
static bool sycl_alloc(sycl::queue& q, T** out, size_t count) {
    *out = nullptr;
    try {
        const sycl::device dev = q.get_device();
        if (dev.has(sycl::aspect::usm_device_allocations)) {
            *out = sycl::malloc_device<T>(count, q);
        } else if (dev.has(sycl::aspect::usm_shared_allocations)) {
            *out = sycl::malloc_shared<T>(count, q);
        }
    } catch (const sycl::exception&) {
        *out = nullptr;
    }
    return *out != nullptr;
}

template <typename T>
static bool sycl_copy_to_device(sycl::queue& q, T* dst, const std::vector<T>& src) {
    try {
        q.memcpy(dst, src.data(), sizeof(T) * src.size()).wait();
        return true;
    } catch (const sycl::exception&) {
        return false;
    }
}

template <typename T>
static bool sycl_copy_to_device(sycl::queue& q, T* dst, const T* src, size_t count) {
    try {
        q.memcpy(dst, src, sizeof(T) * count).wait();
        return true;
    } catch (const sycl::exception&) {
        return false;
    }
}

template <typename T>
static bool sycl_copy_to_host(sycl::queue& q, T* dst, const T* src, size_t count) {
    try {
        q.memcpy(dst, src, sizeof(T) * count).wait();
        return true;
    } catch (const sycl::exception&) {
        return false;
    }
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

static bool load_conv(std::ifstream& f, ConvLayer& out, sycl::queue& q) {
    int32_t inC = 0;
    int32_t outC = 0;
    int32_t k = 0;
    if (!read_i32(f, inC) || !read_i32(f, outC) || !read_i32(f, k)) return false;
    if (inC <= 0 || outC <= 0 || (k != 1 && k != 3)) return false;
    out.inC = inC;
    out.outC = outC;
    out.k = k;
    std::vector<float> w, b;
    if (!read_float_array(f, w) || !read_float_array(f, b)) return false;
    const int64_t expected = static_cast<int64_t>(outC) * inC * k * k;
    if (static_cast<int64_t>(w.size()) != expected) return false;
    if (static_cast<int64_t>(b.size()) != outC) return false;
    out.params = static_cast<int64_t>(w.size() + b.size());

    if (!sycl_alloc(q, &out.d_w, w.size())) return false;
    if (!sycl_alloc(q, &out.d_b, b.size())) return false;
    if (!sycl_copy_to_device(q, out.d_w, w)) return false;
    if (!sycl_copy_to_device(q, out.d_b, b)) return false;
    return true;
}

static bool load_dense(std::ifstream& f, DenseLayer& out, int expectedOut, sycl::queue& q) {
    int32_t inD = 0;
    int32_t outD = 0;
    if (!read_i32(f, inD) || !read_i32(f, outD)) return false;
    if (inD <= 0 || outD <= 0 || outD != expectedOut) return false;
    out.inD = inD;
    out.outD = outD;
    std::vector<float> w, b;
    if (!read_float_array(f, w) || !read_float_array(f, b)) return false;
    const int64_t expected = static_cast<int64_t>(outD) * inD;
    if (static_cast<int64_t>(w.size()) != expected) return false;
    if (static_cast<int64_t>(b.size()) != outD) return false;
    out.params = static_cast<int64_t>(w.size() + b.size());

    if (!sycl_alloc(q, &out.d_w, w.size())) return false;
    if (!sycl_alloc(q, &out.d_b, b.size())) return false;
    if (!sycl_copy_to_device(q, out.d_w, w)) return false;
    if (!sycl_copy_to_device(q, out.d_b, b)) return false;
    return true;
}

static bool load_se(std::ifstream& f, SeUnit& out, bool& present, int channels, int& maxHidden, sycl::queue& q) {
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

    if (!sycl_alloc(q, &out.d_w1, w1.size())) return false;
    if (!sycl_alloc(q, &out.d_b1, b1.size())) return false;
    if (!sycl_alloc(q, &out.d_w2, w2.size())) return false;
    if (!sycl_alloc(q, &out.d_b2, b2.size())) return false;
    if (!sycl_copy_to_device(q, out.d_w1, w1)) return false;
    if (!sycl_copy_to_device(q, out.d_b1, b1)) return false;
    if (!sycl_copy_to_device(q, out.d_w2, w2)) return false;
    if (!sycl_copy_to_device(q, out.d_b2, b2)) return false;
    return true;
}

// ---- SYCL kernels ----

static inline float relu(float x) { return x > 0.0f ? x : 0.0f; }

static inline float sigmoid(float x) {
    return 1.0f / (1.0f + sycl::exp(-x));
}

static void k_conv3x3_no_bias(sycl::queue& q, const float* input, const float* w,
                              int inC, int outC, float* out) {
    size_t total = static_cast<size_t>(outC) * 64;
    q.parallel_for(sycl::range<1>(total), [=](sycl::id<1> tid) {
        int idx = static_cast<int>(tid[0]);
        int oc = idx >> 6;
        int s = idx & 63;
        if (oc >= outC) return;
        int row = s >> 3;
        int col = s & 7;
        float acc = 0.0f;
        const int kk = 9;
        const int ocBase = oc * inC * kk;
        for (int ic = 0; ic < inC; ic++) {
            const int inBase = ic * 64;
            const int wBase = ocBase + ic * kk;
            int wIdx = 0;
            for (int ky = -1; ky <= 1; ky++) {
                int r = row + ky;
                if (r < 0 || r >= 8) {
                    wIdx += 3;
                    continue;
                }
                int inRowBase = inBase + (r << 3);
                for (int kx = -1; kx <= 1; kx++, wIdx++) {
                    int c = col + kx;
                    if (c < 0 || c >= 8) continue;
                    acc += input[inRowBase + c] * w[wBase + wIdx];
                }
            }
        }
        out[oc * 64 + s] = acc;
    });
}

static void k_conv1x1_no_bias(sycl::queue& q, const float* input, const float* w,
                              int inC, int outC, float* out) {
    size_t total = static_cast<size_t>(outC) * 64;
    q.parallel_for(sycl::range<1>(total), [=](sycl::id<1> tid) {
        int idx = static_cast<int>(tid[0]);
        int oc = idx >> 6;
        int s = idx & 63;
        if (oc >= outC) return;
        float acc = 0.0f;
        const int ocBase = oc * inC;
        for (int ic = 0; ic < inC; ic++) {
            acc += input[ic * 64 + s] * w[ocBase + ic];
        }
        out[oc * 64 + s] = acc;
    });
}

static void k_add_bias_relu(sycl::queue& q, float* x, const float* b, int channels) {
    size_t total = static_cast<size_t>(channels) * 64;
    q.parallel_for(sycl::range<1>(total), [=](sycl::id<1> tid) {
        int idx = static_cast<int>(tid[0]);
        int ch = idx >> 6;
        x[idx] = relu(x[idx] + b[ch]);
    });
}

static void k_add_bias(sycl::queue& q, float* x, const float* b, int channels) {
    size_t total = static_cast<size_t>(channels) * 64;
    q.parallel_for(sycl::range<1>(total), [=](sycl::id<1> tid) {
        int idx = static_cast<int>(tid[0]);
        int ch = idx >> 6;
        x[idx] = x[idx] + b[ch];
    });
}

static void k_add_residual_relu(sycl::queue& q, const float* convOut, const float* bias,
                                const float* residual, int channels, float* dest) {
    size_t total = static_cast<size_t>(channels) * 64;
    q.parallel_for(sycl::range<1>(total), [=](sycl::id<1> tid) {
        int idx = static_cast<int>(tid[0]);
        int ch = idx >> 6;
        float v = convOut[idx] + bias[ch] + residual[idx];
        dest[idx] = relu(v);
    });
}

static void k_se_pool(sycl::queue& q, const float* convOut, const float* bias,
                      int channels, float* pooled) {
    if (channels <= 0) return;
    q.submit([&](sycl::handler& cgh) {
        sycl::local_accessor<float, 1> buf(sycl::range<1>(64), cgh);
        cgh.parallel_for(
                sycl::nd_range<1>(
                        sycl::range<1>(static_cast<size_t>(channels) * 64),
                        sycl::range<1>(64)),
                [=](sycl::nd_item<1> item) {
                    int ch = item.get_group(0);
                    int t = item.get_local_id(0);
                    buf[t] = convOut[ch * 64 + t];
                    item.barrier(sycl::access::fence_space::local_space);
                    for (int stride = 32; stride > 0; stride >>= 1) {
                        if (t < stride) buf[t] += buf[t + stride];
                        item.barrier(sycl::access::fence_space::local_space);
                    }
                    if (t == 0) {
                        pooled[ch] = (buf[0] * (1.0f / 64.0f)) + bias[ch];
                    }
                });
    });
}

static void k_se_fc1(sycl::queue& q, const float* pooled, const float* w1, const float* b1,
                     int channels, int hidden, float* outHidden) {
    size_t total = static_cast<size_t>(hidden);
    q.parallel_for(sycl::range<1>(total), [=](sycl::id<1> tid) {
        int h = static_cast<int>(tid[0]);
        float acc = b1[h];
        const float* row = w1 + (static_cast<size_t>(h) * channels);
        for (int ch = 0; ch < channels; ch++) {
            acc += row[ch] * pooled[ch];
        }
        outHidden[h] = relu(acc);
    });
}

static void k_se_fc2(sycl::queue& q, const float* hiddenVec, const float* w2, const float* b2,
                     int hidden, int outDim, float* gates) {
    size_t total = static_cast<size_t>(outDim);
    q.parallel_for(sycl::range<1>(total), [=](sycl::id<1> tid) {
        int o = static_cast<int>(tid[0]);
        float acc = b2[o];
        const float* row = w2 + (static_cast<size_t>(o) * hidden);
        for (int h = 0; h < hidden; h++) {
            acc += row[h] * hiddenVec[h];
        }
        gates[o] = acc;
    });
}

static void k_se_apply(sycl::queue& q, const float* convOut, const float* bias,
                       const float* residual, const float* gates,
                       int channels, float* dest) {
    size_t total = static_cast<size_t>(channels) * 64;
    q.parallel_for(sycl::range<1>(total), [=](sycl::id<1> tid) {
        int idx = static_cast<int>(tid[0]);
        int ch = idx >> 6;
        float gamma = sigmoid(gates[ch]);
        float betaExtra = gates[ch + channels];
        float z = convOut[idx] + bias[ch];
        float v = gamma * z + residual[idx] + betaExtra;
        dest[idx] = relu(v);
    });
}

static void k_policy_map(sycl::queue& q, const float* planes, int planesLen,
                         const int* policyMap, int outLen,
                         float* outPolicy) {
    size_t total = static_cast<size_t>(outLen);
    q.parallel_for(sycl::range<1>(total), [=](sycl::id<1> tid) {
        int i = static_cast<int>(tid[0]);
        int idx = policyMap[i];
        if (idx >= 0 && idx < planesLen) {
            outPolicy[i] = planes[idx];
        } else {
            outPolicy[i] = 0.0f;
        }
    });
}

static void k_dense(sycl::queue& q, const float* x, const float* w, const float* b,
                    int inD, int outD, int reluAct, float* y) {
    size_t total = static_cast<size_t>(outD);
    q.parallel_for(sycl::range<1>(total), [=](sycl::id<1> tid) {
        int o = static_cast<int>(tid[0]);
        float acc = b[o];
        const float* row = w + (static_cast<size_t>(o) * inD);
        for (int i = 0; i < inD; i++) acc += row[i] * x[i];
        y[o] = reluAct ? relu(acc) : acc;
    });
}

static inline void launch_conv_no_bias(sycl::queue& q, const ConvLayer& layer, const float* input, float* out) {
    if (layer.k == 3) {
        k_conv3x3_no_bias(q, input, layer.d_w, layer.inC, layer.outC, out);
    } else if (layer.k == 1) {
        k_conv1x1_no_bias(q, input, layer.d_w, layer.inC, layer.outC, out);
    }
}

// Evaluate one position. Writes:
// - outPolicyHost: [policySize]
// - outWdlHost: [3]
// Returns scalar value (W-L).
static bool eval_one(SyclNet* net, const float* encodedHost, float* outPolicyHost, float* outWdlHost, float& outValue) {
    if (!net) return false;
    if (net->inputC != 112) return false;
    if (!sycl_copy_to_device(net->q, net->d_in, encodedHost, static_cast<size_t>(net->inputC) * 64)) return false;

    // input conv
    launch_conv_no_bias(net->q, net->inputLayer, net->d_in, net->d_cur);
    k_add_bias_relu(net->q, net->d_cur, net->inputLayer.d_b, net->inputLayer.outC);

    // residual tower
    for (int bi = 0; bi < net->blocks; bi++) {
        ResidualBlock& b = net->tower[bi];
        launch_conv_no_bias(net->q, b.conv1, net->d_cur, net->d_tmp);
        k_add_bias_relu(net->q, net->d_tmp, b.conv1.d_b, b.conv1.outC);

        launch_conv_no_bias(net->q, b.conv2, net->d_tmp, net->d_scratch);
        if (!b.hasSe) {
            k_add_residual_relu(net->q, net->d_scratch, b.conv2.d_b, net->d_cur, b.conv2.outC, net->d_next);
        } else {
            k_se_pool(net->q, net->d_scratch, b.conv2.d_b, b.conv2.outC, net->d_sePooled);
            k_se_fc1(net->q, net->d_sePooled, b.se.d_w1, b.se.d_b1, b.conv2.outC, b.se.hidden, net->d_seHidden);
            k_se_fc2(net->q, net->d_seHidden, b.se.d_w2, b.se.d_b2, b.se.hidden, 2 * b.conv2.outC, net->d_seGates);
            k_se_apply(net->q, net->d_scratch, b.conv2.d_b, net->d_cur, net->d_seGates, b.conv2.outC, net->d_next);
        }
        std::swap(net->d_cur, net->d_next);
    }

    // policy head
    launch_conv_no_bias(net->q, net->policyStem, net->d_cur, net->d_policyHidden);
    k_add_bias_relu(net->q, net->d_policyHidden, net->policyStem.d_b, net->policyStem.outC);
    launch_conv_no_bias(net->q, net->policyOut, net->d_policyHidden, net->d_policyPlanes);
    k_add_bias(net->q, net->d_policyPlanes, net->policyOut.d_b, net->policyOut.outC);

    // map policy planes -> policy vector
    k_policy_map(net->q, net->d_policyPlanes, net->policyOut.outC * 64, net->d_policyMap, net->policySize, net->d_policyMapped);
    if (!sycl_copy_to_host(net->q, outPolicyHost, net->d_policyMapped, net->policySize)) return false;

    // value head
    launch_conv_no_bias(net->q, net->valueConv, net->d_cur, net->d_valueInput);
    k_add_bias_relu(net->q, net->d_valueInput, net->valueConv.d_b, net->valueConv.outC);
    // fc1: input is valueC*64 vector
    k_dense(net->q, net->d_valueInput, net->valueFc1.d_w, net->valueFc1.d_b, net->valueFc1.inD, net->valueFc1.outD, 1, net->d_fc1);
    // fc2 -> logits[3]
    k_dense(net->q, net->d_fc1, net->valueFc2.d_w, net->valueFc2.d_b, net->valueFc2.inD, net->valueFc2.outD, 0, net->d_logits);

    float logitsHost[3];
    if (!sycl_copy_to_host(net->q, logitsHost, net->d_logits, 3)) return false;

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

static void destroy_net(SyclNet* net) {
    if (!net) return;
    auto freeConv = [&](ConvLayer& c) {
        sycl_free(net->q, c.d_w);
        sycl_free(net->q, c.d_b);
        c.d_w = nullptr;
        c.d_b = nullptr;
    };
    auto freeDense = [&](DenseLayer& d) {
        sycl_free(net->q, d.d_w);
        sycl_free(net->q, d.d_b);
        d.d_w = nullptr;
        d.d_b = nullptr;
    };
    auto freeSe = [&](SeUnit& s) {
        sycl_free(net->q, s.d_w1);
        sycl_free(net->q, s.d_b1);
        sycl_free(net->q, s.d_w2);
        sycl_free(net->q, s.d_b2);
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
    sycl_free(net->q, net->d_policyMap);
    sycl_free(net->q, net->d_policyMapped);

    sycl_free(net->q, net->d_in);
    sycl_free(net->q, net->d_cur);
    sycl_free(net->q, net->d_next);
    sycl_free(net->q, net->d_tmp);
    sycl_free(net->q, net->d_scratch);
    sycl_free(net->q, net->d_policyHidden);
    sycl_free(net->q, net->d_policyPlanes);
    sycl_free(net->q, net->d_valueInput);
    sycl_free(net->q, net->d_fc1);
    sycl_free(net->q, net->d_logits);
    sycl_free(net->q, net->d_sePooled);
    sycl_free(net->q, net->d_seHidden);
    sycl_free(net->q, net->d_seGates);

    delete net;
}

static SyclNet* create_net(const std::string& path) {
    if (device_count() <= 0) return nullptr;

    sycl::device dev;
    if (!select_intel_gpu(dev)) return nullptr;

    sycl::queue q;
    try {
        q = sycl::queue(dev, sycl::property::queue::in_order{});
    } catch (const sycl::exception&) {
        return nullptr;
    }

    std::ifstream f(path, std::ios::binary);
    if (!f) return nullptr;

    char magic[4];
    if (!read_bytes(f, magic, 4)) return nullptr;
    if (magic[0] != 'L' || magic[1] != 'C' || magic[2] != '0' || magic[3] != 'J') return nullptr;
    int32_t version = 0;
    if (!read_i32(f, version) || version != 1) return nullptr;

    auto net = std::make_unique<SyclNet>();
    net->q = q;

    auto fail = [&]() -> SyclNet* {
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

    if (!load_conv(f, net->inputLayer, net->q)) return fail();
    net->paramCount += net->inputLayer.params;

    net->tower.resize(static_cast<size_t>(blocks));
    int maxHidden = 0;
    for (int i = 0; i < blocks; i++) {
        ResidualBlock& b = net->tower[i];
        if (!load_conv(f, b.conv1, net->q)) return fail();
        if (!load_conv(f, b.conv2, net->q)) return fail();
        net->paramCount += b.conv1.params + b.conv2.params;
        bool present = false;
        if (!load_se(f, b.se, present, b.conv2.outC, maxHidden, net->q)) return fail();
        b.hasSe = present;
        if (b.hasSe) net->paramCount += b.se.params;
    }
    net->seMaxHidden = maxHidden;

    if (!load_conv(f, net->policyStem, net->q)) return fail();
    if (!load_conv(f, net->policyOut, net->q)) return fail();
    if (!load_conv(f, net->valueConv, net->q)) return fail();
    if (net->policyOut.outC != policyC) return fail();
    if (net->valueConv.outC != valueC) return fail();
    net->paramCount += net->policyStem.params + net->policyOut.params + net->valueConv.params;

    if (!load_dense(f, net->valueFc1, valueHidden, net->q)) return fail();
    if (!load_dense(f, net->valueFc2, 3, net->q)) return fail();
    net->paramCount += net->valueFc1.params + net->valueFc2.params;

    // policy map
    int32_t mapEntries = 0;
    if (!read_i32(f, mapEntries)) return fail();
    if (mapEntries != policyMapLen) return fail();
    std::vector<int32_t> policyMap(static_cast<size_t>(mapEntries));
    if (!read_bytes(f, policyMap.data(), sizeof(int32_t) * policyMap.size())) return fail();
    if (!sycl_alloc(net->q, &net->d_policyMap, policyMap.size())) return fail();
    if (!sycl_copy_to_device(net->q, net->d_policyMap, policyMap.data(), policyMap.size())) return fail();

    // ensure EOF
    f.peek();
    if (!f.eof()) {
        // trailing bytes are treated as error (matches Java loader).
        return fail();
    }

    // workspace allocations
    if (!sycl_alloc(net->q, &net->d_in, static_cast<size_t>(inputC) * 64)) return fail();
    if (!sycl_alloc(net->q, &net->d_cur, static_cast<size_t>(trunkC) * 64)) return fail();
    if (!sycl_alloc(net->q, &net->d_next, static_cast<size_t>(trunkC) * 64)) return fail();
    if (!sycl_alloc(net->q, &net->d_tmp, static_cast<size_t>(trunkC) * 64)) return fail();
    if (!sycl_alloc(net->q, &net->d_scratch, static_cast<size_t>(trunkC) * 64)) return fail();
    if (!sycl_alloc(net->q, &net->d_policyHidden, static_cast<size_t>(trunkC) * 64)) return fail();
    if (!sycl_alloc(net->q, &net->d_policyPlanes, static_cast<size_t>(policyC) * 64)) return fail();
    if (!sycl_alloc(net->q, &net->d_valueInput, static_cast<size_t>(valueC) * 64)) return fail();
    if (!sycl_alloc(net->q, &net->d_fc1, static_cast<size_t>(valueHidden))) return fail();
    if (!sycl_alloc(net->q, &net->d_logits, 3)) return fail();

    if (!sycl_alloc(net->q, &net->d_sePooled, static_cast<size_t>(trunkC))) return fail();
    if (net->seMaxHidden > 0) {
        if (!sycl_alloc(net->q, &net->d_seHidden, static_cast<size_t>(net->seMaxHidden))) return fail();
    } else {
        if (!sycl_alloc(net->q, &net->d_seHidden, 1)) return fail();
    }
    if (!sycl_alloc(net->q, &net->d_seGates, static_cast<size_t>(2 * trunkC))) return fail();
    if (!sycl_alloc(net->q, &net->d_policyMapped, static_cast<size_t>(net->policySize))) return fail();

    return net.release();
}

// ---- JNI for OneapiLc0 ----

static jlong backend_nativeCreate(JNIEnv* env, jstring jpath) {
    if (!jpath) return 0;
    const char* cpath = env->GetStringUTFChars(jpath, nullptr);
    if (!cpath) return 0;
    std::string path(cpath);
    env->ReleaseStringUTFChars(jpath, cpath);

    SyclNet* net = create_net(path);
    return reinterpret_cast<jlong>(net);
}

extern "C" JNIEXPORT jlong JNICALL Java_chess_lc0_oneapi_Backend_nativeCreate(JNIEnv* env, jclass, jstring jpath) {
    return backend_nativeCreate(env, jpath);
}

extern "C" JNIEXPORT void JNICALL Java_chess_lc0_oneapi_Backend_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    destroy_net(reinterpret_cast<SyclNet*>(handle));
}

extern "C" JNIEXPORT jlongArray JNICALL Java_chess_lc0_oneapi_Backend_nativeGetInfo(JNIEnv* env, jclass, jlong handle) {
    SyclNet* net = reinterpret_cast<SyclNet*>(handle);
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

extern "C" JNIEXPORT jfloat JNICALL Java_chess_lc0_oneapi_Backend_nativePredict(
        JNIEnv* env, jclass, jlong handle, jfloatArray jencoded, jfloatArray joutPolicy, jfloatArray joutWdl) {
    SyclNet* net = reinterpret_cast<SyclNet*>(handle);
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
