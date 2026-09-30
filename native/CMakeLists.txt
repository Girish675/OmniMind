cmake_minimum_required(VERSION 3.22)
project(omnimind_native C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Configure llama.cpp as library dependency
set(LLAMA_STANDALONE OFF CACHE BOOL "Disable llama standalone" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Disable llama examples" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Disable llama tests" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Disable llama server" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Static build for internal linking" FORCE)

if (ANDROID)
    set(GGML_NATIVE OFF CACHE BOOL "Disable native instructions when cross compiling" FORCE)
    set(GGML_CPU_AARCH64 ON CACHE BOOL "Enable AArch64 ARM NEON for Android" FORCE)
endif()

# Add llama.cpp submodule
add_subdirectory(llama.cpp)

# Define OmniMind native core library
add_library(omnimind_core STATIC
    src/omnimind_core.cpp
)

target_include_directories(omnimind_core PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

target_link_libraries(omnimind_core PUBLIC
    llama
    ggml
)

option(OMNIMIND_BUILD_TESTS "Build native core tests" ON)
if (OMNIMIND_BUILD_TESTS AND NOT ANDROID)
    add_subdirectory(tests)
endif()
