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

## 0.9.4 emulator evidence (2026-11-22)

Environment: isolated Docker/KVM Pixel 6, Android 36 Google APIs x86_64;
synthetic BookOrbit 3.9.0 production-mode server only. This is **not physical
phone validation**, audio-output validation or acceptance of all UI screens.
Private screenshots/XML are retained in `.local/device-screenshots/`.

Verified:

- 0.9.3 → 0.9.4 installation over the same debug certificate preserves login
  and the existing text-only offline download (`09`–`11`).
- A 75-track book offers exactly two edition cards: text and audiobook (`07`).
  Adding the audiobook selects all missing tracks as one action (`29`); completed
  storage contains 75 MP3s plus the original EPUB, in the original plan directory.
- Existing text still opens after adding audio with emulator Wi-Fi/mobile data
  disabled (`34`); networking was restored afterwards.
- Home → book → Home returns to Home (`12`). Library → book → Library returns
  to its list (`31`); Authors → Library resets its subsection to Books (`32`).
- EPUB/SMIL explicit playback produces the playing control and visible fragment
  highlight (`20`); pause, 1.25× speed and next-fragment highlighting work
  (`22`–`23`). Leaving the app and returning presents paused controls (`25`).
  The fixture is silent and the emulator has no audio output: audible playback,
  headphone routing and synchronization against spoken words remain untested.
- Light/dark reader mode changes work (`27`–`28`). The synthetic dark EPUB has
  one continuous chapter/margin canvas without page borders. Reader chrome has
  its own surface; the user's original EPUB still needs visual acceptance.
- Story Guide explicitly reports production attestation restrictions and does
  not offer generation (`35`). No provider call was made.

During testing, regenerated EPUB bytes initially disagreed with cached server
metadata. The reader rejected them instead of accepting an incomplete cache.
The fixture generator now updates directory mtimes and smoke waits for the scan
to finish; no client integrity check was disabled.

Still required: physical-device tests, real spoken EPUB overlays, long-book page
following, external audio alignment, background MediaSession read-along, Bluetooth/
Cast/Auto, process-death restoration, SAF interruption, and authorized AI generation.
