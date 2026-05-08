cmake_minimum_required(VERSION 3.22.1)
project(callguardian_android LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CORE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../core_engine)

add_library(callguardian_core STATIC
    ${CORE_DIR}/src/call_analyzer.cpp
    ${CORE_DIR}/src/decision_engine.cpp
    ${CORE_DIR}/src/phone_number_normalizer.cpp
    ${CORE_DIR}/src/policy_config.cpp
    ${CORE_DIR}/src/spoof_detector.cpp
    ${CORE_DIR}/src/whitelist_matcher.cpp
)

target_include_directories(callguardian_core PUBLIC ${CORE_DIR}/include)

add_library(callguardian_jni SHARED core_bridge.cpp)

target_link_libraries(callguardian_jni PRIVATE callguardian_core)
