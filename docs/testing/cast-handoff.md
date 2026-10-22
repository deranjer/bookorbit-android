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
