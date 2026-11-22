# Device test checklist

Record phone model, Android version and the app version shown in Settings. Debug
builds are development artifacts, not a promise of a stable release. Prefer the
isolated synthetic fixture server; do not delete production books or history.

## Both 0.9.0 and 0.9.1

1. Log in, browse the library, open a description and search for a book.
2. For two EPUB editions of one book, choose the second file. Reopen it and check
   its position; opening the first edition must not reuse the second's position.
3. Choose the App/native reader mode, download only that edition, enable airplane
   mode and reopen it from Downloads. Server/Web reader mode requires connectivity.
4. Stream an audiobook, pause/resume, lock the screen and test Bluetooth controls.
5. Download audio explicitly and verify playback in airplane mode. Browsing alone
   must not create permanent downloads.

## Additive downloads (0.9.1 only)

1. Download text only. Open the completed book's **Add files** dialog.
2. Existing text is checked and locked. Choose audio and confirm.
3. While audio downloads, open the existing text in airplane mode. Its local file
   and position must still be available. Restore connectivity to finish audio.
4. Reopen **Add files**, add the alternate EPUB, and check both editions offline.
5. Interrupt an audio transfer, restore connectivity and check it resumes. If it
   eventually fails, previously finished files must remain usable; Retry must keep
   the selected files and their existing bytes.
6. Delete an offline download while it runs. It must not reappear when the old
   worker finishes. Immediately starting a new download must not resurrect the old
   selection or corrupt its new files.
7. Repeat with a user-chosen SAF folder and with its permission revoked. Report
   fallback-storage behavior separately from ordinary internal storage.

Swiping an app out of Recents, Android killing its process, and **Force stop** are
different tests. Android intentionally suppresses jobs after Force stop until the
app is started again; do not expect background work to defeat that restriction.

Report exact steps, expected/actual behavior, app version and whether the server
was reachable. Never send passwords, access tokens, full private logs or user books.
CBR/CB7 still require server-rendered pages; CBZ uses the local reading engine.
