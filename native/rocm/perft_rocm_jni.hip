/*
 * native/rocm/perft_rocm_jni.hip
 *
 * Optional ROCm/HIP backend for split-depth bulk perft.
 */

#include <hip/hip_runtime.h>

#define PERFT_JNI_PREFIX Java_chess_nn_perft_rocm_
#define PERFT_GPU_MALLOC(ptr, bytes) hipMalloc(reinterpret_cast<void**>(ptr), bytes)
#define PERFT_GPU_FREE(ptr) hipFree(ptr)
#define PERFT_GPU_MEMCPY(dst, src, bytes, kind) hipMemcpy(dst, src, bytes, kind)
#define PERFT_GPU_MEMCPY_H2D hipMemcpyHostToDevice
#define PERFT_GPU_MEMCPY_D2H hipMemcpyDeviceToHost
#define PERFT_GPU_MEMSET(ptr, value, bytes) hipMemset(ptr, value, bytes)
#define PERFT_GPU_DEVICE_SYNCHRONIZE() hipDeviceSynchronize()
#define PERFT_GPU_GET_DEVICE_COUNT(ptr) hipGetDeviceCount(ptr)
#define PERFT_GPU_LAST_ERROR() hipGetLastError()
#define PERFT_GPU_SUCCESS hipSuccess

#include "../common/perft_gpu_impl.inl"
