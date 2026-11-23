cmake_minimum_required(VERSION 3.22.1)
project(nobodylame C)

file(GLOB LAME_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame/*.c)

add_library(nobodylame SHARED ${LAME_SOURCES} nobody_lame.c)

target_include_directories(nobodylame PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/lame/include
        ${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame)

# LAME is normally configured by autotools; these replace config.h.
target_compile_definitions(nobodylame PRIVATE
        STDC_HEADERS
        HAVE_STDINT_H
        HAVE_LIMITS_H
        HAVE_ERRNO_H
        HAVE_FCNTL_H
        HAVE_STRCHR
        HAVE_MEMCPY
        HAVE_IEEE754_FLOAT32_T
        ieee754_float32_t=float)

target_compile_options(nobodylame PRIVATE -O2 -Wno-shift-negative-value -Wno-absolute-value)

# 16 KB page alignment (required for Android 15+ devices that use 16 KB pages).
target_link_options(nobodylame PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(nobodylame m)
