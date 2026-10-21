# Test PR #84: Chromecast queue MIME type

Install the **full debug** APK (`com.bookorbit.debug`); fdroid builds intentionally have no Cast.
Use the same Wi-Fi network as the Chromecast and sign into your server in the debug app.

1. Stream an MP3 audiobook locally, pause at a recognizable position, then connect to Cast.
   Expect no crash; play/pause, seek and chapter navigation should work at the expected position.
2. Repeat with an M4B audiobook. The server's extensionless stream URL should no longer cause
   the Cast queue to claim the file is MP3: its server format is carried into the media item.
3. Disconnect Cast. Expect the phone to resume controls at the same book position, without
   duplicate audio. Reconnect once and test another chapter or file in a multi-file book.
4. Download an MP3 and M4B to the app's default private storage; repeat Cast playback from each
   downloaded copy. SAF/content-URI Cast playback is an existing limitation, not fixed here.
5. If the receiver cannot fetch the stream, verify the app remains usable. Synchronous handoff
   failures retain local playback and log a warning; asynchronous receiver failures are a
   separate path and are not covered by that catch block.

Use `adb logcat` filtered to `PlaybackService`, `CastPlayer`, or the app's process if a handoff
fails. Keep device model, Android version, Chromecast model and book format with the result.
