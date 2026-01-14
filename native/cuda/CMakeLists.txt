cmake_minimum_required(VERSION 3.18)
project(lc0j_cuda LANGUAGES CXX CUDA)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Helps IDEs (including VSCode C/C++ extension) discover the correct include paths
# for CUDA and JNI via `compile_commands.json`.
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# CMake may put include flags into a response file for nvcc (includes_CUDA.rsp),
# which many IDE indexers don't read. Disable that so `compile_commands.json`
# contains the actual `-I...` arguments (fixes `#include <jni.h>` squiggles).
set(CMAKE_CUDA_USE_RESPONSE_FILE_FOR_INCLUDES OFF)

find_package(JNI REQUIRED)
find_package(CUDAToolkit REQUIRED)

add_library(lc0j_cuda SHARED lc0j_cuda_jni.cu)
target_include_directories(lc0j_cuda PRIVATE ${JNI_INCLUDE_DIRS})

# Link CUDA runtime.
target_link_libraries(lc0j_cuda PRIVATE CUDA::cudart)

set_target_properties(lc0j_cuda PROPERTIES
  CUDA_SEPARABLE_COMPILATION ON
)
