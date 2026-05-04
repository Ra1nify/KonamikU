cmake_minimum_required(VERSION 3.22.1)
project("konamiku")

set(DOBBY_BUILD_EXAMPLE   OFF CACHE BOOL "" FORCE)
set(DOBBY_BUILD_TEST      OFF CACHE BOOL "" FORCE)
set(DOBBY_GENERATE_SHARED OFF CACHE BOOL "" FORCE)
add_subdirectory(Dobby)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(RUST_TARGET "aarch64-linux-android")
else()
    message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
endif()

set(RUST_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/pmm-rust/target/${RUST_TARGET}/release")

add_library(pmm_rust STATIC IMPORTED)
set_target_properties(pmm_rust PROPERTIES
    IMPORTED_LOCATION "${RUST_LIB_DIR}/libpmm_rust.a"
)

add_library(pmm SHARED bridge.c)

target_include_directories(pmm PRIVATE
    Dobby/include
)

target_link_libraries(pmm
    pmm_rust
    dobby
    android
    log
)