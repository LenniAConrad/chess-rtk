cmake_minimum_required(VERSION 3.18)
project(lc0j_oneapi LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

find_package(JNI REQUIRED)

add_library(lc0j_oneapi SHARED lc0j_oneapi_jni.cpp)
target_include_directories(lc0j_oneapi PRIVATE ${JNI_INCLUDE_DIRS})
target_compile_options(lc0j_oneapi PRIVATE -fsycl)
target_link_options(lc0j_oneapi PRIVATE -fsycl)
