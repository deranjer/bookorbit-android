cmake_minimum_required(VERSION 3.21)
project(bookorbit_software_audio C CXX)
set(CMAKE_CXX_STANDARD 17)
include(ExternalProject)

set(ffmpeg_prefix "${CMAKE_CURRENT_BINARY_DIR}/ffmpeg-install")
set(ffmpeg_libraries)
foreach(name avcodec swresample avutil)
    list(APPEND ffmpeg_libraries "${ffmpeg_prefix}/lib/lib${name}.a")
endforeach()

ExternalProject_Add(ffmpeg_native
    SOURCE_DIR "${IMPORTED_SOURCES}/ffmpeg"
    BINARY_DIR "${CMAKE_CURRENT_BINARY_DIR}/ffmpeg-build"
    CONFIGURE_COMMAND bash "${CMAKE_CURRENT_SOURCE_DIR}/configure-ffmpeg.sh"
        "${IMPORTED_SOURCES}/ffmpeg" "${ffmpeg_prefix}" "${ANDROID_ABI}"
        "${CMAKE_C_COMPILER}" "${CMAKE_AR}" "${CMAKE_RANLIB}" "${FFMPEG_ANDROID_API}"
    BUILD_COMMAND make -j2
    INSTALL_COMMAND make install-libs install-headers
    BUILD_BYPRODUCTS ${ffmpeg_libraries}
)
ExternalProject_Add_StepDependencies(ffmpeg_native configure "${CMAKE_CURRENT_SOURCE_DIR}/configure-ffmpeg.sh")

add_library(ffmpegJNI SHARED "${IMPORTED_SOURCES}/media3/src/main/jni/ffmpeg_jni.cc")
add_dependencies(ffmpegJNI ffmpeg_native)
target_include_directories(ffmpegJNI PRIVATE "${ffmpeg_prefix}/include")
target_link_libraries(ffmpegJNI PRIVATE ${ffmpeg_libraries} log android m)
target_link_options(ffmpegJNI PRIVATE "-Wl,-z,max-page-size=16384")
if(ANDROID_ABI STREQUAL "arm64-v8a")
    # Match Media3's JNI recipe: FFmpeg's AArch64 assembly references internal static symbols.
    target_link_options(ffmpegJNI PRIVATE "-Wl,-Bsymbolic")
endif()
set_target_properties(ffmpegJNI PROPERTIES CXX_VISIBILITY_PRESET hidden VISIBILITY_INLINES_HIDDEN ON)
