/*
 * native/oneapi/perft_oneapi_jni.cpp
 *
 * Optional oneAPI/SYCL backend for split-depth bulk perft.
 *
 * Unlike the CUDA/ROCm shims (which include native/common/perft_gpu_impl.inl and
 * launch the shared __global__ kernel), the SYCL backend is standalone: it reuses
 * the portable move generator in native/common/perft_core.h directly inside a
 * sycl::parallel_for. perft_iter is recursion-free, which SYCL device code
 * requires. Each work-item unpacks one frontier position and counts
 * perft(remainingDepth); the host sums the results.
 */

#include <jni.h>
#include <sycl/sycl.hpp>

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <string>
#include <vector>

#include "../common/perft_core.h"

namespace {

using namespace crtk_perft;

std::string to_lower(std::string v) {
    for (char& c : v) {
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    }
    return v;
}

bool is_intel_gpu(const sycl::device& dev) {
    if (!dev.is_gpu()) {
        return false;
    }
    std::string vendor = to_lower(dev.get_info<sycl::info::device::vendor>());
    return vendor.find("intel") != std::string::npos;
}

std::vector<sycl::device> intel_gpus() {
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

} // namespace

extern "C" JNIEXPORT jint JNICALL Java_chess_nn_perft_oneapi_Support_nativeDeviceCount(JNIEnv*, jclass) {
    return static_cast<jint>(intel_gpus().size());
}

extern "C" JNIEXPORT jlongArray JNICALL Java_chess_nn_perft_oneapi_Backend_nativeBulkPerft(
        JNIEnv* env, jclass, jlongArray packedArray, jint count, jint depth) {
    if (packedArray == nullptr || count <= 0 || depth < 0) {
        return nullptr;
    }
    jsize words = env->GetArrayLength(packedArray);
    if (static_cast<jlong>(words) < static_cast<jlong>(count) * PACK_WORDS) {
        return nullptr;
    }

    auto devices = intel_gpus();
    if (devices.empty()) {
        return nullptr;
    }

    Tables tables;
    build_tables(tables);

    jlong* packed = env->GetLongArrayElements(packedArray, nullptr);
    if (packed == nullptr) {
        return nullptr;
    }

    std::vector<unsigned long long> host(static_cast<size_t>(count), 0ULL);
    bool ok = false;
    try {
        sycl::queue q(devices.front());
        size_t packedCount = static_cast<size_t>(count) * PACK_WORDS;
        uint64_t* dPacked = sycl::malloc_device<uint64_t>(packedCount, q);
        Tables* dTables = sycl::malloc_device<Tables>(1, q);
        unsigned long long* dCounts = sycl::malloc_device<unsigned long long>(static_cast<size_t>(count), q);
        if (dPacked != nullptr && dTables != nullptr && dCounts != nullptr) {
            q.memcpy(dPacked, packed, packedCount * sizeof(uint64_t)).wait_and_throw();
            q.memcpy(dTables, &tables, sizeof(Tables)).wait_and_throw();
            int remaining = depth;
            q.parallel_for(sycl::range<1>(static_cast<size_t>(count)), [=](sycl::id<1> id) {
                int i = static_cast<int>(id[0]);
                Position p;
                unpack_position(dPacked + static_cast<long>(i) * PACK_WORDS, p);
                dCounts[i] = static_cast<unsigned long long>(perft_iter(*dTables, p, remaining));
            }).wait_and_throw();
            q.memcpy(host.data(), dCounts, static_cast<size_t>(count) * sizeof(unsigned long long)).wait_and_throw();
            ok = true;
        }
        if (dPacked != nullptr) sycl::free(dPacked, q);
        if (dTables != nullptr) sycl::free(dTables, q);
        if (dCounts != nullptr) sycl::free(dCounts, q);
    } catch (const sycl::exception&) {
        ok = false;
    }

    env->ReleaseLongArrayElements(packedArray, packed, JNI_ABORT);
    if (!ok) {
        return nullptr;
    }

    jlongArray result = env->NewLongArray(count);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, count, reinterpret_cast<const jlong*>(host.data()));
    }
    return result;
}

extern "C" JNIEXPORT jlongArray JNICALL Java_chess_nn_perft_oneapi_Backend_nativeBulkPerftDetailed(
        JNIEnv* env, jclass, jlongArray packedArray, jint count, jint depth) {
    if (packedArray == nullptr || count <= 0 || depth < 0) {
        return nullptr;
    }
    jsize words = env->GetArrayLength(packedArray);
    if (static_cast<jlong>(words) < static_cast<jlong>(count) * PACK_WORDS) {
        return nullptr;
    }

    auto devices = intel_gpus();
    if (devices.empty()) {
        return nullptr;
    }

    Tables tables;
    build_tables(tables);

    jlong* packed = env->GetLongArrayElements(packedArray, nullptr);
    if (packed == nullptr) {
        return nullptr;
    }

    const int fields = 7;
    std::vector<unsigned long long> host(static_cast<size_t>(count) * fields, 0ULL);
    bool ok = false;
    try {
        sycl::queue q(devices.front());
        size_t packedCount = static_cast<size_t>(count) * PACK_WORDS;
        uint64_t* dPacked = sycl::malloc_device<uint64_t>(packedCount, q);
        Tables* dTables = sycl::malloc_device<Tables>(1, q);
        unsigned long long* dCounts =
                sycl::malloc_device<unsigned long long>(static_cast<size_t>(count) * fields, q);
        if (dPacked != nullptr && dTables != nullptr && dCounts != nullptr) {
            q.memcpy(dPacked, packed, packedCount * sizeof(uint64_t)).wait_and_throw();
            q.memcpy(dTables, &tables, sizeof(Tables)).wait_and_throw();
            int remaining = depth;
            q.parallel_for(sycl::range<1>(static_cast<size_t>(count)), [=](sycl::id<1> id) {
                int i = static_cast<int>(id[0]);
                Position p;
                unpack_position(dPacked + static_cast<long>(i) * PACK_WORDS, p);
                PerftCounts c;
                perft_detailed_iter(*dTables, p, remaining, c);
                unsigned long long* o = dCounts + static_cast<long>(i) * fields;
                o[0] = c.nodes;
                o[1] = c.captures;
                o[2] = c.enPassant;
                o[3] = c.castles;
                o[4] = c.promotions;
                o[5] = c.checks;
                o[6] = c.checkmates;
            }).wait_and_throw();
            q.memcpy(host.data(), dCounts, static_cast<size_t>(count) * fields
                    * sizeof(unsigned long long)).wait_and_throw();
            ok = true;
        }
        if (dPacked != nullptr) sycl::free(dPacked, q);
        if (dTables != nullptr) sycl::free(dTables, q);
        if (dCounts != nullptr) sycl::free(dCounts, q);
    } catch (const sycl::exception&) {
        ok = false;
    }

    env->ReleaseLongArrayElements(packedArray, packed, JNI_ABORT);
    if (!ok) {
        return nullptr;
    }

    jlongArray result = env->NewLongArray(count * fields);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, count * fields, reinterpret_cast<const jlong*>(host.data()));
    }
    return result;
}
