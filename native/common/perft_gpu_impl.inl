/*
 * native/common/perft_gpu_impl.inl
 *
 * Shared GPU implementation of the split-depth perft bulk counter, included by
 * the CUDA (perft_cuda) and ROCm (perft_rocm) JNI shims. A shim defines the
 * PERFT_GPU_* portability macros and PERFT_JNI_PREFIX, then includes this file.
 *
 * The Java driver (chess.debug.gpu.GpuPerft) expands the move tree on the CPU to
 * a split depth, packs every frontier position into PACK_WORDS little-endian
 * longs, and calls nativeBulkPerft. Each device thread unpacks one frontier
 * position and computes perft(remainingDepth) for it; the host sums the results.
 *
 * Every CUDA-ism is routed through a macro (PERFT_GLOBAL for __global__,
 * PERFT_LAUNCH for the <<<>>> launch, and the PERFT_GPU_* runtime calls) so the
 * exact same source compiles and runs on the CPU under g++ for testing — see
 * native/test/perft_host_jni_shim.cpp.
 *
 * JNI surface (names parameterized by PERFT_JNI_PREFIX):
 *   Support.nativeDeviceCount() -> int
 *   Backend.nativeBulkPerft(long[] packed, int count, int remainingDepth) -> long[]
 */
#include <jni.h>

#include <cstdint>
#include <vector>

#include "perft_core.h"

#ifndef PERFT_GLOBAL
#define PERFT_GLOBAL __global__
#endif

// Kernel launch indirection so a host build can emulate the grid on the CPU.
#ifndef PERFT_LAUNCH
#define PERFT_LAUNCH(kernel, grid, block, ...) kernel<<<grid, block>>>(__VA_ARGS__)
#endif

#define PERFT_CAT2(a, b) a##b
#define PERFT_CAT(a, b) PERFT_CAT2(a, b)
#define PERFT_JNI(name) PERFT_CAT(PERFT_JNI_PREFIX, name)

namespace {

using namespace crtk_perft;

// One thread per frontier position: unpack and count perft(depth).
PERFT_GLOBAL void perft_bulk_kernel(const uint64_t* packed, int count, int depth,
        const Tables* tables, unsigned long long* counts) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= count) {
        return;
    }
    Position p;
    unpack_position(packed + (long) i * PACK_WORDS, p);
    counts[i] = (unsigned long long) perft_iter(*tables, p, depth);
}

// One thread per frontier position: detailed counters (7 per position).
PERFT_GLOBAL void perft_detailed_kernel(const uint64_t* packed, int count, int depth,
        const Tables* tables, unsigned long long* counts) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i >= count) {
        return;
    }
    Position p;
    unpack_position(packed + (long) i * PACK_WORDS, p);
    PerftCounts c;
    perft_detailed_iter(*tables, p, depth, c);
    unsigned long long* o = counts + (long) i * 7;
    o[0] = c.nodes;
    o[1] = c.captures;
    o[2] = c.enPassant;
    o[3] = c.castles;
    o[4] = c.promotions;
    o[5] = c.checks;
    o[6] = c.checkmates;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL PERFT_JNI(Support_nativeDeviceCount)(JNIEnv*, jclass) {
    int count = 0;
    if (PERFT_GPU_GET_DEVICE_COUNT(&count) != PERFT_GPU_SUCCESS) {
        return 0;
    }
    return (jint) count;
}

extern "C" JNIEXPORT jlongArray JNICALL PERFT_JNI(Backend_nativeBulkPerft)(
        JNIEnv* env, jclass, jlongArray packedArray, jint count, jint depth) {
    if (packedArray == nullptr || count <= 0 || depth < 0) {
        return nullptr;
    }
    jsize words = env->GetArrayLength(packedArray);
    if ((jlong) words < (jlong) count * crtk_perft::PACK_WORDS) {
        return nullptr;
    }

    crtk_perft::Tables tables;
    crtk_perft::build_tables(tables);

    jlong* packed = env->GetLongArrayElements(packedArray, nullptr);
    if (packed == nullptr) {
        return nullptr;
    }

    size_t packedBytes = (size_t) count * crtk_perft::PACK_WORDS * sizeof(uint64_t);
    size_t countBytes = (size_t) count * sizeof(unsigned long long);

    uint64_t* dPacked = nullptr;
    crtk_perft::Tables* dTables = nullptr;
    unsigned long long* dCounts = nullptr;
    jlongArray result = nullptr;

    bool allocated = PERFT_GPU_MALLOC(&dPacked, packedBytes) == PERFT_GPU_SUCCESS
            && PERFT_GPU_MALLOC(&dTables, sizeof(crtk_perft::Tables)) == PERFT_GPU_SUCCESS
            && PERFT_GPU_MALLOC(&dCounts, countBytes) == PERFT_GPU_SUCCESS;

    if (allocated
            && PERFT_GPU_MEMCPY(dPacked, packed, packedBytes, PERFT_GPU_MEMCPY_H2D) == PERFT_GPU_SUCCESS
            && PERFT_GPU_MEMCPY(dTables, &tables, sizeof(crtk_perft::Tables), PERFT_GPU_MEMCPY_H2D)
                    == PERFT_GPU_SUCCESS) {
        int block = 64;
        int grid = (count + block - 1) / block;
        PERFT_LAUNCH(perft_bulk_kernel, grid, block, dPacked, (int) count, (int) depth, dTables, dCounts);
        PERFT_GPU_DEVICE_SYNCHRONIZE();

        std::vector<unsigned long long> host((size_t) count);
        if (PERFT_GPU_MEMCPY(host.data(), dCounts, countBytes, PERFT_GPU_MEMCPY_D2H) == PERFT_GPU_SUCCESS) {
            result = env->NewLongArray(count);
            if (result != nullptr) {
                env->SetLongArrayRegion(result, 0, count, reinterpret_cast<const jlong*>(host.data()));
            }
        }
    }

    if (dPacked != nullptr) {
        PERFT_GPU_FREE(dPacked);
    }
    if (dTables != nullptr) {
        PERFT_GPU_FREE(dTables);
    }
    if (dCounts != nullptr) {
        PERFT_GPU_FREE(dCounts);
    }
    env->ReleaseLongArrayElements(packedArray, packed, JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jlongArray JNICALL PERFT_JNI(Backend_nativeBulkPerftDetailed)(
        JNIEnv* env, jclass, jlongArray packedArray, jint count, jint depth) {
    if (packedArray == nullptr || count <= 0 || depth < 0) {
        return nullptr;
    }
    jsize words = env->GetArrayLength(packedArray);
    if ((jlong) words < (jlong) count * crtk_perft::PACK_WORDS) {
        return nullptr;
    }

    crtk_perft::Tables tables;
    crtk_perft::build_tables(tables);

    jlong* packed = env->GetLongArrayElements(packedArray, nullptr);
    if (packed == nullptr) {
        return nullptr;
    }

    const int fields = 7;
    size_t packedBytes = (size_t) count * crtk_perft::PACK_WORDS * sizeof(uint64_t);
    size_t countBytes = (size_t) count * fields * sizeof(unsigned long long);

    uint64_t* dPacked = nullptr;
    crtk_perft::Tables* dTables = nullptr;
    unsigned long long* dCounts = nullptr;
    jlongArray result = nullptr;

    bool allocated = PERFT_GPU_MALLOC(&dPacked, packedBytes) == PERFT_GPU_SUCCESS
            && PERFT_GPU_MALLOC(&dTables, sizeof(crtk_perft::Tables)) == PERFT_GPU_SUCCESS
            && PERFT_GPU_MALLOC(&dCounts, countBytes) == PERFT_GPU_SUCCESS;

    if (allocated
            && PERFT_GPU_MEMCPY(dPacked, packed, packedBytes, PERFT_GPU_MEMCPY_H2D) == PERFT_GPU_SUCCESS
            && PERFT_GPU_MEMCPY(dTables, &tables, sizeof(crtk_perft::Tables), PERFT_GPU_MEMCPY_H2D)
                    == PERFT_GPU_SUCCESS) {
        int block = 64;
        int grid = (count + block - 1) / block;
        PERFT_LAUNCH(perft_detailed_kernel, grid, block, dPacked, (int) count, (int) depth, dTables, dCounts);
        PERFT_GPU_DEVICE_SYNCHRONIZE();

        std::vector<unsigned long long> host((size_t) count * fields);
        if (PERFT_GPU_MEMCPY(host.data(), dCounts, countBytes, PERFT_GPU_MEMCPY_D2H) == PERFT_GPU_SUCCESS) {
            result = env->NewLongArray(count * fields);
            if (result != nullptr) {
                env->SetLongArrayRegion(result, 0, count * fields,
                        reinterpret_cast<const jlong*>(host.data()));
            }
        }
    }

    if (dPacked != nullptr) {
        PERFT_GPU_FREE(dPacked);
    }
    if (dTables != nullptr) {
        PERFT_GPU_FREE(dTables);
    }
    if (dCounts != nullptr) {
        PERFT_GPU_FREE(dCounts);
    }
    env->ReleaseLongArrayElements(packedArray, packed, JNI_ABORT);
    return result;
}

#undef PERFT_JNI
#undef PERFT_CAT
#undef PERFT_CAT2
