cmake_minimum_required(VERSION 3.18)
project(rocm_backends LANGUAGES CXX HIP)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_HIP_STANDARD 17)
set(CMAKE_HIP_STANDARD_REQUIRED ON)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

find_package(JNI REQUIRED)
find_package(HIP REQUIRED)

add_library(lc0j_rocm SHARED lc0j_rocm_jni.hip)
target_include_directories(lc0j_rocm PRIVATE ${JNI_INCLUDE_DIRS})

add_library(t5_rocm SHARED t5_rocm_jni.hip)
target_include_directories(t5_rocm PRIVATE ${JNI_INCLUDE_DIRS})

target_link_libraries(lc0j_rocm PRIVATE HIP::device)
target_link_libraries(t5_rocm PRIVATE HIP::device hipblas)

set_target_properties(lc0j_rocm PROPERTIES
  HIP_SEPARABLE_COMPILATION ON
)
set_target_properties(t5_rocm PROPERTIES
  HIP_SEPARABLE_COMPILATION ON
)
