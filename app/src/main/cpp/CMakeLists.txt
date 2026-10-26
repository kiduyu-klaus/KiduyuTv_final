cmake_minimum_required(VERSION 3.22.1)
project(kiduyutv_mpv LANGUAGES CXX)

# Link the checked-in ABI library in place; do not rename/copy ELF dependencies.
add_library(mpv SHARED IMPORTED)
set_target_properties(mpv PROPERTIES IMPORTED_LOCATION
    "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libmpv.so")

add_library(kiduyu_mpv SHARED mpv_jni.cpp)
target_include_directories(kiduyu_mpv PRIVATE "${CMAKE_CURRENT_LIST_DIR}/include")
target_compile_features(kiduyu_mpv PRIVATE cxx_std_17)
target_compile_options(kiduyu_mpv PRIVATE -Wall -Wextra -Werror=return-type)
find_library(android-lib android)
find_library(log-lib log)
target_link_libraries(kiduyu_mpv PRIVATE mpv ${android-lib} ${log-lib})
