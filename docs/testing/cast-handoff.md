# Test PR #84: Chromecast queue MIME type

Install the **full debug** APK (`com.bookorbit.debug`); fdroid builds intentionally have no Cast.
Use the same Wi-Fi network as the Chromecast and sign into your server in the debug app.

1. Stream an MP3 audiobook locally, pause at a recognizable position, then connect to Cast.
   Expect no crash; play/pause, seek and chapter navigation should work at the expected position.
   Also connect while the phone is actively playing: the phone must go silent immediately,
   and the app's pause/resume controls must control the receiver without duplicate local audio.
2. Repeat with an M4B audiobook. The server's extensionless stream URL should no longer cause
   the Cast queue to claim the file is MP3: its server format is carried into the media item.
3. Disconnect Cast. Expect the phone to resume controls at the same book position, without
   duplicate audio. Reconnect once and test another chapter or file in a multi-file book.
4. Download an MP3 and M4B to the app's default private storage; repeat Cast playback from each
   downloaded copy. SAF/content-URI Cast playback is an existing limitation, not fixed here.
   Also test M4A. While connected, pause the first book, select a different book, and press Listen.
   The receiver must load the new book without disconnecting. Repeat in the opposite direction,
   then disconnect both during loading and after playback starts: the phone must retain the new
   book and its requested/current position rather than returning to the original book.
5. If the receiver cannot fetch the stream, verify the app remains usable. Synchronous handoff
   failures retain local playback and log a warning; asynchronous receiver failures are a
   separate path and are not covered by that catch block.

Use `adb logcat` filtered to `PlaybackService`, `CastPlayer`, or the app's process if a handoff
fails. Keep device model, Android version, Chromecast model and book format with the result.

For slow large-M4B startup, also capture `CastProxyServer` debug logs. These record requested byte
ranges, upstream status/content range, header latency and completed transfer size/time, without
URLs, credentials or proxy tokens. An upstream 200 in response to a range request is flagged:
it can force the receiver to read a large file before finding end-of-file MP4 metadata. A local
download comparison helps separate the upstream server path from receiver/file parsing.

The `CastProxyRangeTest` Android test checks an eight-byte tail request on a virtual 8 GiB source,
verifying large offsets and 206/Content-Range forwarding without downloading a large book.
It also replaces a local queue, checks that stale URLs are rejected, and verifies local ranges,
invalid-range responses and Cast CORS/preflight headers.

Debug builds retain a bounded trace in `cache/cast-proxy-trace.log`, readable with
`adb shell run-as com.bookorbit.debug cat cache/cast-proxy-trace.log`. It includes local request
ranges and bytes/time as well as remote header timing, and survives wireless-debugging drops.

Physical Pixel 9 Pro / Android 17 findings: pause/resume now works. Both streamed and downloaded
M4A/M4B copies start slowly; the small MP3 starts immediately. The inspected downloaded M4A is
648,249,033 bytes and already has its 7,726,136-byte `moov` box at offset 24, before `mdat`.
End-of-file metadata is therefore not the explanation for that file. Receiver request timing is
still needed before attributing the delay to file size, Wi-Fi throughput or MP4 parsing.

Follow-up two-minute startup investigation: the downloaded M4A has an AAC audio track plus a
QuickTime chapter-text track containing 498 samples spread across the file. In the retained
trace, 190 of 191 audio byte-range requests exactly match chapter sample offsets rounded down
to 32 KiB; the one unmatched request is the eventual playback/resume read. The early trace
rotated out, so this does not measure the entire two-minute interval. Individual retained scan
transfers generally complete in tens of milliseconds. This strongly implicates receiver chapter
preloading rather than upstream latency or end-of-file moov placement. Next controlled test:
serve an audio-only version to Cast while retaining the app's own chapters, then compare startup
and seeking with the original. That mitigation has not yet been implemented or verified.

### Controlled metadata-overlay experiment

A local HTTP fixture reused only the real file's `ftyp`/`moov` header and original layout,
with invented chapter labels and simulated zero media payload. It retrieved no source audio
and did not modify the downloaded book. FFprobe 8.0.1 results:

| Header / parser mode | HTTP requests | Chapter reads beyond header | Parsed chapters |
| --- | ---: | ---: | ---: |
| Original header | 500 | 498 | 498 |
| Original header, ignore_chapters control | 2 | 0 | 0 |
| Chapter track + reference hidden as free boxes | 2 | 0 | 0 |

The overlay changed two four-byte box types (8 bytes total). File size, audio sample tables,
audio offsets, AAC settings and declared duration remained unchanged; cover metadata remained
available. This is a parsing/request-count experiment, not an audio decoding or Chromecast
playback test, and its PC timings do not predict device startup time.

This matches FFmpeg's `mov_read_chapters` behavior: it loops through chapter sample index entries,
seeks to each sample's byte offset, reads its text, then seeks back. FFmpeg's documented
`ignore_chapters` input option suppresses chapter parsing, but no equivalent sender option was
found in the Cast track-selection documentation inspected. References:
- https://github.com/FFmpeg/FFmpeg/blob/master/libavformat/mov.c
- https://ffmpeg.org/ffmpeg-formats.html#mov_002fmp4_002f3gp
- https://ffmpeg.org/pipermail/ffmpeg-devel/2017-August/214826.html

Recommended implementation: a Cast-proxy-only metadata overlay that hides referenced chapter
tracks, leaving original book bytes untouched. Preserve byte positions and content length, apply
patches consistently to every overlapping range, and use the same representation for streamed
and downloaded sources. Keep BookOrbit's existing server/book chapter metadata and controls.
The production parser needs bounded, validated box traversal and chapter-reference matching;
unsupported/malformed layouts should pass through unchanged. Validate range boundaries,
seeking, resume, chapters, cover art and queue replacement before Chromecast acceptance.
Full remuxing is a fallback if a metadata overlay is incompatible with a receiver, but requires
processing/caching the complete book. Fast-start relocation alone does not address this file,
whose moov is already at the front.

### Chapter filter device retest

The Cast proxy now hides only referenced QuickTime text chapter tracks and their `chap`
references, using same-length `free` boxes. Original files, audio data/offsets, cover metadata,
and BookOrbit's own chapter metadata remain intact. This runs for downloaded and streamed MP4
queues. Discovery is cached per queue target and bounded to 16 top-level headers, a 16 MiB moov
and a 15-second I/O budget; unknown, fragmented, malformed or unsupported layouts pass through.
Remote metadata probes require exact 206/Content-Range responses and identity encoding, avoiding
accidental full-book downloads when Range is ignored. Every response range gets the same overlay,
including a request intersecting only part of a patched field. The debug trace limit is now 256 KiB.

Reconnect Chromecast after installing this revision to invalidate old receiver URLs/cache. Start
with the same downloaded slow M4A, note start time, pause/resume, seek well ahead, and try chapter
navigation. Confirm cover art, then repeat from a server-only book. Look for
`MP4 chapter filter patches=2` for the inspected two-track book; compare whether the hundreds of
chapter-position requests disappear. A fallback logs zero patches or unavailable and retains the
original representation. Finally select a different book without disconnecting and test returning
to phone playback. Physical startup improvement remains pending this retest.

### Multi-file MP3 progress retest

Physical testing now confirms the filtered M4A starts in seconds with matching time and working
start/stop controls. A separate 127-file MP3 book exposed frozen phone time and a scrubber that
snaps back while the receiver seeks correctly. Media3 1.5.1 lazily represents distant Cast queue
entries as placeholders; requiring every file ID to match prevented the app progress poller from
accepting any updates. Placeholder metadata also omitted durations needed for whole-book time.

Progress identity now checks the active file plus queue size and proxy-generation URI. The Cast
wrapper obtains durations/chapters from the complete source queue, while preserving Cast's real
queue and active index. Disconnect uses the active item rather than requiring all distant items.
Regression coverage uses a 127-file partial queue, a cross-file seek, advancing playback, chapter
mode, old-generation rejection and return-position preservation. On device, play the MP3 for ten
seconds, scrub into another file/chapter, confirm the phone keeps advancing at the new time, then
pause and disconnect to verify position continuity.

### Queue replacement crash follow-up

Physical testing confirmed MP3 progress after the partial-queue fix, then switching to a one-file
M4B crashed in Media3 1.5.1 `MediaUtils.mergePlayerInfo` / `PlayerInfo.Builder.build`. The controller
optimistically masked the new one-item queue while the receiver still emitted an index from the
old 127-item queue. This happened before audio loading, not during M4B decoding.

Phone-initiated loads now use an app-private session command. The service validates the request,
routes it through the Cast proxy, and applies queue/resume/speed/play state together. The phone
follows actual receiver timeline updates rather than masking a new queue prematurely. Requests
retain media configuration and metadata using Media3's chunked BundleListRetriever transport;
only the app's own controller receives the custom command. Superseded requests are discarded.
Android Auto's standard browse/set-media-items entry points remain available.

A real MediaController/MediaSession regression uses a delayed 127-to-1 queue change and an old
index-41 update in between. It asserts a consistent old queue until the receiver acknowledges the
new one, then a connected controller at index zero. Retest MP3 playback, select M4B without
leaving Cast, then return to MP3. Verify playback, displayed time and seeking in both books.
