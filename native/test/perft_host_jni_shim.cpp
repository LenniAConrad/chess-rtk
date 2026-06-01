/*
 * native/test/perft_host_jni_shim.cpp
 *
 * CPU emulation of the CUDA perft backend. It compiles native/common/perft_gpu_impl.inl
 * with every CUDA-ism neutralized (PERFT_GLOBAL/PERFT_LAUNCH + PERFT_GPU_* macros run
 * on the host), exporting the real Java_chess_nn_perft_cuda_* JNI symbols. Build it
 * into libperft_host.so and point CRTK_PERFT_CUDA_LIB at it to exercise the full
 * Java -> JNI -> shared C++ perft path on a machine with no GPU.
 *
 * Build:
 *   g++ -O2 -std=c++17 -fPIC -shared \
 *       -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
 *       native/test/perft_host_jni_shim.cpp -o /tmp/libperft_host.so
 */
#include <cstdlib>
#include <cstring>

// Emulate the CUDA grid builtins with mutable host globals (single-threaded test).
struct PerftHostDim3 {
    int x;
};
static PerftHostDim3 blockIdx{0};
static PerftHostDim3 threadIdx{0};
static PerftHostDim3 blockDim{1};

#define PERFT_GLOBAL
#define PERFT_LAUNCH(kernel, grid, block, ...)                       \
    do {                                                             \
        blockDim.x = (block);                                        \
        for (int _b = 0; _b < (grid); ++_b) {                        \
            blockIdx.x = _b;                                         \
            for (int _t = 0; _t < (block); ++_t) {                   \
                threadIdx.x = _t;                                    \
                kernel(__VA_ARGS__);                                 \
            }                                                        \
        }                                                            \
    } while (0)

#define PERFT_GPU_MALLOC(ptr, bytes) (*reinterpret_cast<void**>(ptr) = std::malloc(bytes), *(ptr) ? 0 : 1)
#define PERFT_GPU_FREE(p) (std::free(p), 0)
#define PERFT_GPU_MEMCPY(d, s, n, kind) (std::memcpy((void*) (d), (const void*) (s), (n)), 0)
#define PERFT_GPU_MEMCPY_H2D 0
#define PERFT_GPU_MEMCPY_D2H 0
#define PERFT_GPU_MEMSET(p, v, n) (std::memset((void*) (p), (v), (n)), 0)
#define PERFT_GPU_DEVICE_SYNCHRONIZE() ((void) 0)
#define PERFT_GPU_GET_DEVICE_COUNT(ptr) (*(ptr) = 1, 0)
#define PERFT_GPU_LAST_ERROR() 0
#define PERFT_GPU_SUCCESS 0

#define PERFT_JNI_PREFIX Java_chess_nn_perft_cuda_
#include "../common/perft_gpu_impl.inl"
