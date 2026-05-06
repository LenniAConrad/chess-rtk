cmake_minimum_required(VERSION 3.18)
project(oneapi_backends LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

find_package(JNI REQUIRED)

add_library(lc0_oneapi SHARED lc0_cnn_oneapi_jni.cpp lc0_bt4_oneapi_jni.cpp)
target_include_directories(lc0_oneapi PRIVATE ${JNI_INCLUDE_DIRS})
target_compile_options(lc0_oneapi PRIVATE -fsycl)
target_link_options(lc0_oneapi PRIVATE -fsycl)

add_library(t5_oneapi SHARED t5_oneapi_jni.cpp)
target_include_directories(t5_oneapi PRIVATE ${JNI_INCLUDE_DIRS})
target_compile_options(t5_oneapi PRIVATE -fsycl)
target_link_options(t5_oneapi PRIVATE -fsycl)
