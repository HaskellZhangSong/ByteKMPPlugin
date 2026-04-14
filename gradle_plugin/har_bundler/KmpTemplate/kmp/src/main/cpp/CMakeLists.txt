# the minimum version of CMake.
cmake_minimum_required(VERSION 3.5.0)
project(KmpTemplate)

set(NATIVERENDER_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR})
set(ENTRY_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR}/../../../)

if(DEFINED PACKAGE_FIND_FILE)
    include(${PACKAGE_FIND_FILE})
endif()

include_directories(${NATIVERENDER_ROOT_PATH}
                    ${NATIVERENDER_ROOT_PATH}/include)

add_library(kmp SHARED napi_init.cpp)
target_link_libraries(kmp PUBLIC libace_napi.z.so)
target_link_libraries(kmp PUBLIC libhilog_ndk.z.so)
target_link_libraries(kmp PUBLIC libbundle_ndk.z.so)
target_link_libraries(kmp PUBLIC libuv.so)
target_link_libraries(kmp PUBLIC ${ENTRY_ROOT_PATH}/libs/arm64-v8a/libkmp.so)