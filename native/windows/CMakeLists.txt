cmake_minimum_required(VERSION 3.20)
project(absolute_control_windows_helper LANGUAGES CXX)

if(NOT WIN32)
    message(FATAL_ERROR "This isolated prototype targets Windows only.")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(input_policy STATIC src/input_policy.cpp)
target_include_directories(input_policy PUBLIC src)

add_executable(absolute-control-input-helper
    src/absolute_control_input_helper.cpp
)
target_link_libraries(absolute-control-input-helper PRIVATE input_policy user32)

add_executable(input_policy_tests tests/input_policy_tests.cpp)
target_link_libraries(input_policy_tests PRIVATE input_policy)

if(MSVC)
    foreach(target input_policy absolute-control-input-helper input_policy_tests)
        target_compile_options(${target} PRIVATE /W4 /WX /permissive-)
    endforeach()
endif()

enable_testing()
add_test(NAME input_policy_tests COMMAND input_policy_tests)
