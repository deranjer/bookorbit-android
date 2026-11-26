#!/usr/bin/env bash
set -euo pipefail
source_dir="$1"
prefix="$2"
abi="$3"
cc="$4"
ar="$5"
ranlib="$6"
api="$7"
toolchain="$(dirname "$cc")"
case "$abi" in
    armeabi-v7a) arch=arm; cpu=armv7-a; triple=armv7a-linux-androideabi ;;
    arm64-v8a) arch=aarch64; cpu=armv8-a; triple=aarch64-linux-android ;;
    x86) arch=x86; cpu=i686; triple=i686-linux-android ;;
    x86_64) arch=x86_64; cpu=x86-64; triple=x86_64-linux-android ;;
    *) exit 1 ;;
esac

# Decode only; Media3 handles containers/network and the existing AudioSink handles PCM output.
exec "$source_dir/configure" \
    --prefix="$prefix" --target-os=android --arch="$arch" --cpu="$cpu" \
    --enable-cross-compile --cc="$toolchain/${triple}${api}-clang" \
    --ar="$ar" --ranlib="$ranlib" --nm="$toolchain/llvm-nm" --strip="$toolchain/llvm-strip" \
    --enable-pic --enable-static --disable-shared --disable-debug --enable-small \
    --disable-everything --disable-autodetect --disable-programs --disable-doc \
    --disable-avdevice --disable-avformat --disable-avfilter --disable-swscale \
    --disable-network --disable-x86asm --disable-gpl --disable-version3 --disable-nonfree \
    --enable-swresample \
    --enable-decoder=aac,mp3,ac3,eac3,dca,truehd,mlp,vorbis,opus,flac,alac,amrnb,amrwb,pcm_mulaw,pcm_alaw
