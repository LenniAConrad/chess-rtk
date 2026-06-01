/*
 * native/cuda/perft_cuda_jni.cu
 *
 * Optional CUDA backend for split-depth bulk perft (legal-move tree node
 * counting), NOT position evaluation. The PERFT_GPU_* macros below are just a
 * thin GPU-runtime abstraction (cudaMalloc/cudaMemcpy/...) so the shared kernel
 * in perft_gpu_impl.inl can also target ROCm (hip*) and a CPU host build; they
 * have nothing to do with the BT4/OTIS neural-network backends.
 */

#include <cuda_runtime.h>

#define PERFT_JNI_PREFIX Java_chess_nn_perft_cuda_
#define PERFT_GPU_MALLOC(ptr, bytes) cudaMalloc(reinterpret_cast<void**>(ptr), bytes)
#define PERFT_GPU_FREE(ptr) cudaFree(ptr)
#define PERFT_GPU_MEMCPY(dst, src, bytes, kind) cudaMemcpy(dst, src, bytes, kind)
#define PERFT_GPU_MEMCPY_H2D cudaMemcpyHostToDevice
#define PERFT_GPU_MEMCPY_D2H cudaMemcpyDeviceToHost
#define PERFT_GPU_MEMSET(ptr, value, bytes) cudaMemset(ptr, value, bytes)
#define PERFT_GPU_DEVICE_SYNCHRONIZE() cudaDeviceSynchronize()
#define PERFT_GPU_GET_DEVICE_COUNT(ptr) cudaGetDeviceCount(ptr)
#define PERFT_GPU_LAST_ERROR() cudaGetLastError()
#define PERFT_GPU_SUCCESS cudaSuccess

#include "../common/perft_gpu_impl.inl"
