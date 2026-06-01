/*
 * native/cuda/otis_cuda_jni.cu
 *
 * Optional CUDA backend for OTIS policy/WDL inference.
 */

#include <cuda_runtime.h>

#define OTIS_JNI_PREFIX Java_chess_nn_otis_cuda_
#define BT4_GPU_MALLOC(ptr, bytes) cudaMalloc(reinterpret_cast<void**>(ptr), bytes)
#define BT4_GPU_FREE(ptr) cudaFree(ptr)
#define BT4_GPU_MEMCPY(dst, src, bytes, kind) cudaMemcpy(dst, src, bytes, kind)
#define BT4_GPU_MEMCPY_H2D cudaMemcpyHostToDevice
#define BT4_GPU_MEMCPY_D2H cudaMemcpyDeviceToHost
#define BT4_GPU_MEMSET(ptr, value, bytes) cudaMemset(ptr, value, bytes)
#define BT4_GPU_DEVICE_SYNCHRONIZE() cudaDeviceSynchronize()
#define BT4_GPU_GET_DEVICE_COUNT(ptr) cudaGetDeviceCount(ptr)
#define BT4_GPU_LAST_ERROR() cudaGetLastError()
#define BT4_GPU_SUCCESS cudaSuccess

#include "../common/otis_gpu_impl.inl"
