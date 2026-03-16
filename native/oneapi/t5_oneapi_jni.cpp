/*
 * native/oneapi/t5_oneapi_jni.cpp
 *
 * oneAPI backend for the T5 tag-to-text pipeline.
 *
 * Exposes:
 *   - chess.nn.t5.oneapi.Support.nativeDeviceCount() -> int
 *   - chess.nn.t5.oneapi.Kernels.nativeMatmul(...) -> boolean
 *   - chess.nn.t5.oneapi.Backend.nativeCreate(String) -> long
 *   - chess.nn.t5.oneapi.Backend.nativeDestroy(long) -> void
 *   - chess.nn.t5.oneapi.Backend.nativeGenerateIds(long, int[], int) -> int[]
 */

#include <jni.h>

#include <sycl/sycl.hpp>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstring>
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

extern "C" JNIEXPORT jint JNICALL Java_chess_nn_t5_oneapi_Support_nativeDeviceCount(JNIEnv*, jclass) {
    return device_count();
}

extern "C" JNIEXPORT jboolean JNICALL Java_chess_nn_t5_oneapi_Kernels_nativeMatmul(
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

    sycl::device dev;
    if (!select_intel_gpu(dev)) {
        env->ReleaseFloatArrayElements(aArr, a, JNI_ABORT);
        env->ReleaseFloatArrayElements(bArr, b, JNI_ABORT);
        env->ReleaseFloatArrayElements(outArr, out, JNI_ABORT);
        return JNI_FALSE;
    }

    bool ok = true;
    try {
        sycl::queue q(dev);
        size_t aCount = static_cast<size_t>(aLen);
        size_t bCount = static_cast<size_t>(bLen);
        size_t outCount = static_cast<size_t>(outLen);
        float* d_a = sycl::malloc_device<float>(aCount, q);
        float* d_b = sycl::malloc_device<float>(bCount, q);
        float* d_out = sycl::malloc_device<float>(outCount, q);
        if (!d_a || !d_b || !d_out) {
            if (d_a) sycl::free(d_a, q);
            if (d_b) sycl::free(d_b, q);
            if (d_out) sycl::free(d_out, q);
            ok = false;
        }
        if (ok) {
            q.memcpy(d_a, a, sizeof(float) * aCount);
            q.memcpy(d_b, b, sizeof(float) * bCount);
            q.parallel_for(sycl::range<2>(static_cast<size_t>(m), static_cast<size_t>(n)), [=](sycl::id<2> idx) {
                int row = static_cast<int>(idx[0]);
                int col = static_cast<int>(idx[1]);
                float sum = 0.0f;
                const float* aRow = d_a + row * k;
                const float* bRow = d_b + col * k;
                for (int p = 0; p < k; p++) {
                    sum += aRow[p] * bRow[p];
                }
                d_out[row * n + col] = sum;
            });
            q.memcpy(out, d_out, sizeof(float) * outCount);
            q.wait();
            sycl::free(d_a, q);
            sycl::free(d_b, q);
            sycl::free(d_out, q);
        }
    } catch (const sycl::exception&) {
        ok = false;
    }

    env->ReleaseFloatArrayElements(aArr, a, JNI_ABORT);
    env->ReleaseFloatArrayElements(bArr, b, JNI_ABORT);
    env->ReleaseFloatArrayElements(outArr, out, ok ? 0 : JNI_ABORT);

    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL Java_chess_nn_t5_oneapi_Backend_nativeCreate(JNIEnv*, jclass, jstring) {
    return 0;
}

extern "C" JNIEXPORT void JNICALL Java_chess_nn_t5_oneapi_Backend_nativeDestroy(JNIEnv*, jclass, jlong) {}

extern "C" JNIEXPORT jintArray JNICALL Java_chess_nn_t5_oneapi_Backend_nativeGenerateIds(
    JNIEnv*, jclass, jlong, jintArray, jint) {
    return nullptr;
}
