/*
 * native/rocm/lc0_bt4_rocm_jni.hip
 *
 * Optional ROCm/HIP backend for LC0 BT4 attention-body inference.
 */

#include <hip/hip_runtime.h>

#define BT4_JNI_PREFIX Java_chess_nn_lc0_bt4_rocm_
#define BT4_GPU_MALLOC(ptr, bytes) hipMalloc(reinterpret_cast<void**>(ptr), bytes)
#define BT4_GPU_FREE(ptr) hipFree(ptr)
#define BT4_GPU_MEMCPY(dst, src, bytes, kind) hipMemcpy(dst, src, bytes, kind)
#define BT4_GPU_MEMCPY_H2D hipMemcpyHostToDevice
#define BT4_GPU_MEMCPY_D2H hipMemcpyDeviceToHost
#define BT4_GPU_MEMSET(ptr, value, bytes) hipMemset(ptr, value, bytes)
#define BT4_GPU_DEVICE_SYNCHRONIZE() hipDeviceSynchronize()
#define BT4_GPU_GET_DEVICE_COUNT(ptr) hipGetDeviceCount(ptr)
#define BT4_GPU_LAST_ERROR() hipGetLastError()
#define BT4_GPU_SUCCESS hipSuccess

#include "../common/lc0_bt4_gpu_impl.inl"
