# Audio compatibility without server changes

Audit date: 2026-11-24. Read-only source inspected at
`.local/server-compat-source`, commit `c1dd6b4f1947ff50843a155e12e70f1e7d15f0c7`.
This establishes the local stock source contract, not every deployed server version.
No production files, configuration, libraries or books were changed.

## Existing backend

- `server/src/modules/audiobook/audiobook.controller.ts:45-85` exposes the manifest
  and `GET /api/v1/audiobooks/:bookId/assets/:assetId/content`. The content handler
  verifies asset access, selects MIME from the stored format and sends the
  original file using `createReadStream`, optionally with byte Range/206.
  It has no codec, bitrate, start-time or output-profile query DTO and no encoder.
- `server/src/modules/book/book.controller.ts:401-416` deliberately refuses audio
  on the generic `books/files/:fileId/serve` route. It is not a transcode fallback.
- A source search for transcoding, HLS/m3u8 and conversion controller routes found
  no playback transcode endpoint. The FFmpeg runner under
  `modules/file-write/formats/audio/` belongs to metadata writing, not a temporary
  playback rendition. Triggering metadata edits is not an acceptable workaround.

Changing a URL extension or adding `codec=aac` cannot manufacture a new stream.
Range addresses original bytes, not the time positions of a converted file.

## Client-only path

1. Keep direct play first. Inspect the actual media track, not just `.m4b` or MIME:
   a container can contain different codecs. Retain original asset/file identity.
2. Let Media3 try another installed decoder when initialization fails. This is
   decoder fallback, not transcoding and not a guarantee for unsupported codecs.
3. For missing codecs, bundle a reviewed software decoder supporting those codecs
   and ABIs, subject to licensing/build-size/security review. Decode directly to
   PCM for local playback where possible. Merely enabling extension-renderer mode
   does not install a decoder library.
4. If a destination really needs a different encoded stream (notably Cast), encode
   on Android after decoding, to a bounded temporary cache or a correctly seekable
   local proxy. This needs a working input decoder; MediaCodec/Transformer alone
   cannot decode an otherwise unsupported input. Do not download/transcode an
   entire multi-track edition in response to browsing or synchronization.
5. Keep source time and track identity across retries. Cancel on account/output
   changes, retain owner-bound auth, bound cache/work/concurrency and avoid
   duplicating listening sessions. Do not treat HTTP/auth/network failures as
   decoder incompatibility. Avoid an infinite retry loop.

Local decoding does not establish Cast receiver compatibility. Actual on-device
transcoding is not implemented yet; installed-decoder fallback alone must not be
advertised as the requested full compatibility feature.

## Software decoder implementation in progress (2026-11-25)

`software-audio/` now builds the official Media3 1.11.1 FFmpeg JNI/Java extension
against FFmpeg 8.1.3 from source. Raw upstream files and the release tarball are
hash-pinned; the FFmpeg PGP signature is checked against a pinned fingerprint.
The build deliberately does not consume an unofficial prebuilt decoder AAR.
The first build needs Python 3, GnuPG, make, NDK 28.2.13676358, CMake 3.30.5 and
network access for verified sources; cached source builds can run offline.

The recipe enables AAC, MP3, AC3/EAC3, DTS, TrueHD/MLP, Vorbis, Opus, FLAC,
ALAC, AMR-NB/WB and G.711 decoders, with no encoders, external codec libraries,
FFmpeg executables, network protocols, GPL-only/nonfree/version3 components.
The resulting FFmpeg configuration reports LGPL-2.1-or-later. Its license and
Media3's Apache license, provenance lock and relinking recipe notice are APK
assets. All four Android ABIs and 16 KiB JNI alignment are intended build targets.
Do not interpret intended coverage as tested decoding for every codec/device.

The shared ExoPlayer uses extension mode ON: prefer normal direct/platform
decoding, use FFmpeg when the input format lacks platform support. If a platform
audio decoder advertises support but fails, `SoftwareAudioFallback` marks that
input MIME unsupported by the platform renderer and invalidates its capabilities
for one retry on the same media item and source time. Simply disabling a renderer
does not re-map its track group in Media3 and was rejected by the injected-fault
test. Source/HTTP/network and DRM errors are not converted to codec retries.
Another media item restores normal renderer preference. Cast still requires a compatible receiver stream;
this is not an encoder or a replacement for a server-transcoding API.

Real JNI decoding and seek/queue checks passed on the x86_64 emulator. ALAC uses
the normal production factory; AC3 explicitly prefers the software renderer in
its test because a device can expose AC3 passthrough without a software decoder.
AAC fault injection proves platform preference, software retry at the same time,
and restored platform preference on the next item. Four tests passed twice in
`.local/instrumentation/20261125T092622305464Z/`. All declared MIME decoders are
present according to the loaded JNI library; actual media decoding has not been
tested for every declared codec. Four-ABI JNI presence, license assets and 16 KiB
LOAD/ZIP alignment checks also pass for the fdroid APK. Physical-device audible
output, performance/battery, release shrinking and Cast encoding remain unverified.

## Related annotation audit

`modules/annotation/dto/create-annotation.dto.ts` accepts exactly one of `cfi` or
`pdf`, with text, color, style, note and `bookFileId`. Its geometry DTO is explicitly
PDF. `annotation.service.ts:101-108` rejects `pdf` for a non-PDF file. There is no
dedicated comic word/image geometry in this contract. A comic cannot be submitted
as a PDF annotation. A CFI-based page anchor may allow a text quote to be stored,
but its validation, navigation and cross-client coordinate representation require
separate investigation; this is not evidence of synchronized OCR highlight support.
