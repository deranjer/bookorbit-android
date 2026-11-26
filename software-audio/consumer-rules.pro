# DefaultRenderersFactory loads this constructor by name; JNI registers decoder methods by name.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { public <init>(...); }
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder { *; }
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary { *; }
