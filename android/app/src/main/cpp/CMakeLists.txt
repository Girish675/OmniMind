cmake_minimum_required(VERSION 3.22)
project(omnimind_jni C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Configure llama.cpp flags for Android
set(LLAMA_STANDALONE OFF CACHE BOOL "Disable standalone" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Disable examples" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Disable tests" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Disable server" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Static sub-libraries" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "Disable native instructions" FORCE)
set(GGML_CPU_AARCH64 ON CACHE BOOL "ARM NEON" FORCE)

set(NATIVE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../native")

# Add llama.cpp subdirectory
add_subdirectory("${NATIVE_ROOT}/llama.cpp" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp")

# Define omnimind_jni shared library
add_library(omnimind_jni SHARED
    omnimind_jni.cpp
    "${NATIVE_ROOT}/src/omnimind_core.cpp"
)

target_include_directories(omnimind_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${NATIVE_ROOT}/include
    ${NATIVE_ROOT}/src
    ${NATIVE_ROOT}/llama.cpp/include
    ${NATIVE_ROOT}/llama.cpp/ggml/include
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(omnimind_jni PRIVATE
    llama
    ggml
    ${log-lib}
    ${android-lib}
)
