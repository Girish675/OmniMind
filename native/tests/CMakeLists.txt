cmake_minimum_required(VERSION 3.22)

add_executable(test_core
    test_core.cpp
)

target_include_directories(test_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../include
    ${CMAKE_CURRENT_SOURCE_DIR}/../src
)

target_link_libraries(test_core PRIVATE
    omnimind_core
)
