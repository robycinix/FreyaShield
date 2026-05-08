cmake_minimum_required(VERSION 3.22)
project(callguardian_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(callguardian_core
    src/call_analyzer.cpp
    src/decision_engine.cpp
    src/phone_number_normalizer.cpp
    src/policy_config.cpp
    src/spoof_detector.cpp
    src/whitelist_matcher.cpp
)

target_include_directories(callguardian_core PUBLIC include)

add_executable(callguardian_core_tests
    tests/test_call_analyzer.cpp
    tests/test_phone_number_normalizer.cpp
    tests/test_spoof_detector.cpp
)

target_link_libraries(callguardian_core_tests PRIVATE callguardian_core)
