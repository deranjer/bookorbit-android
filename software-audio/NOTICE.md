# Bundled software audio decoding

Media3 FFmpeg decoder integration: Copyright The Android Open Source Project,
Apache-2.0. FFmpeg: Copyright the FFmpeg developers, LGPL-2.1-or-later for this
configuration. Full license texts accompany this notice in the APK assets.

Exact upstream files, versions, source URLs and SHA-256 hashes are recorded in
`sources.lock.json`. The FFmpeg release signature is verified against fingerprint
FCF986EA15E6E293A5644F10B4322F04D67658D8 before building.

The application source includes the build and link recipes at `software-audio/`
and `tools/android/prepare-software-audio.py`. Rebuilding them produces the JNI
library for replacement/relinking with modified FFmpeg sources. FFmpeg codecs
are statically linked into `libffmpegJNI.so`; no FFmpeg executable, encoder,
network protocol, GPL-only or nonfree component is enabled.

Source: https://github.com/monkeCode/bookorbit-android
FFmpeg source: https://ffmpeg.org/releases/ffmpeg-8.1.3.tar.xz
Media3 source: https://github.com/androidx/media/tree/1.11.1/libraries/decoder_ffmpeg

This module handles local PCM decoding. It does not provide server transcoding,
encode an alternative Cast stream or circumvent DRM.
