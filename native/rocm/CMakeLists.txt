cmake_minimum_required(VERSION 3.18)
project(lc0j_rocm LANGUAGES CXX HIP)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_HIP_STANDARD 17)
set(CMAKE_HIP_STANDARD_REQUIRED ON)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

find_package(JNI REQUIRED)
find_package(HIP REQUIRED)

add_library(lc0j_rocm SHARED lc0j_rocm_jni.hip)
target_include_directories(lc0j_rocm PRIVATE ${JNI_INCLUDE_DIRS})

target_link_libraries(lc0j_rocm PRIVATE HIP::device)

set_target_properties(lc0j_rocm PROPERTIES
  HIP_SEPARABLE_COMPILATION ON
)
