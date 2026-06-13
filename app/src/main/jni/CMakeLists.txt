cmake_minimum_required(VERSION 3.22.1)
project("konamiku")

# ── Dobby (LSPosed fork, built from source with -fPIC) ───────────────────
set(DOBBY_GENERATE_SHARED OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLE         OFF CACHE BOOL "" FORCE)
set(BUILD_TEST            OFF CACHE BOOL "" FORCE)
add_subdirectory(Dobby)

# ── libpmm.so ────────────────────────────────────────────────────────────
add_library(pmm SHARED bridge.c)

target_include_directories(pmm PRIVATE
        Dobby/include
)

target_link_libraries(pmm
        dobby
        log
)