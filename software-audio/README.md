# Software audio module

Builds the official Media3 FFmpeg decoder against hash-pinned, signature-verified
FFmpeg sources. It is included in both application flavors. There is no prebuilt
third-party decoder AAR and no server change.

## Build

Use the existing Linux Docker build wrapper:

```sh
bash tools/android/gradle.sh :app:assembleFdroidDebug :app:assembleFullDebug
```

The image provides Python 3, GnuPG, make, NDK 28.2.13676358 and CMake 3.30.5.
The first build downloads approximately 12 MB of FFmpeg source plus the small
Media3 decoder files; subsequent builds use verified cached files. Source
preparation can be run separately before an offline build:

```sh
bash tools/android/gradle.sh :software-audio:prepareSoftwareAudioSources
```

The `.local/native-audio-sources/` cache and `software-audio/build/` generated
sources are build inputs/artifacts, not files to commit. `sources.lock.json` pins
every downloaded file; changing Media3 requires updating the lock with the app.

FFmpeg builds out of tree per ABI with two make jobs per build. Only the declared
audio decoders and resampler are enabled. Media3 provides demuxing, authenticated
HTTP Range, timeline/clipping and the audio sink. No encoder, command-line
program or network protocol is built into FFmpeg. This is local PCM decoding,
not a Cast-compatible transcode stream and not DRM support.

## Verify

```sh
python tools/android/check-software-audio-apk.py app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
python tools/android/run-instrumentation.py --class com.bookorbit.feature.player.SoftwareAudioTest --install
```

The APK check verifies all four JNI ELF architectures, 16 KiB ELF LOAD and ZIP
alignment, and bundled license/source notices. Instrumentation uses original
silent ALAC, AC3 and AAC fixtures, checks actual decoder names and PCM output,
seek and queue identity. A separate injected decoder failure checks one-time
fallback without pretending to be a naturally occurring device failure.
Silent fixtures do not establish audible output, battery cost or all-codec
compatibility on physical devices.

See `NOTICE.md` and APK assets `software-audio/` for licenses and source provenance.
