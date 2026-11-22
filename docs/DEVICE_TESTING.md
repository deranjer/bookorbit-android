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

## 0.9.5 additional emulator checks

- Switched the application's Android locale to `ru-RU`. Home and the redesigned
  Search render in Russian (`36`–`37`); the fixture's English book metadata remains
  original content, not UI strings. All base string/plural resources have Russian
  counterparts and formatting checks; whole-app large-font visual acceptance is
  still in progress.
- Paused the second SMIL fragment and inspected the persisted audio checkpoint:
  exact EPUB fingerprint, section 0, `OEBPS/chapter.xhtml#two`, resource
  `OEBPS/audio.mp3`, asset clock **11.724068 s**. Force-stopped and relaunched the
  app, reopened the edition and observed inert Play controls (`40`–`41`). Explicit
  Play then Pause saved **13.668752 s**, continuing within that resource rather
  than jumping to its clip start (10 s) or mapping the text percentage (`42`).
- Explicitly downloaded the read-along EPUB (`43`–`44`). With emulator Wi-Fi and
  mobile data disabled, the edition opened and resumed foreground playback with
  the expected fragment highlight and Pause control (`45`). Networking was
  restored afterwards. Silent fixture/no audio output limitations still apply.
- The first photo capability check surfaced a real server error code,
  `APP_ATTEST_REQUIRED`, which the initial generic error mapper incorrectly
  classified as a provider failure. Added the explicit mapping and regression
  cases; the final APK reports the correct restriction (`46`, `48`).
- Russian Search at 1.4× system font exposed clipped navigation labels and an
  edge-to-edge hint (`49`). Adaptive navigation space, concise tab labels and hint
  padding/alignment fix these (`51`); system font scale was restored to 1.0.

The local checkpoint is scoped by server, account, file ID and SHA-256 of actual
EPUB bytes. It is not cross-device narration sync and does not create another
listening/reading session. Server narration coordinates, native background
read-along and real provider answers remain separate work.

## 0.9.6 native streaming checks

- Selected **Приложение · EPUB онлайн** (`52`), opened undownloaded Read Along
  Chapters (`54`), used the TOC (`55`) and loaded its second chapter (`56`).
- Explicit foreground Play and previous-fragment controls traverse the unnarrated
  interlude back to the first narrated chapter (`57`). These are silent fixtures;
  this does not prove audible speech or alignment. Embedded audio currently loads
  a whole current asset Blob, not a native Media3 range stream.
- With Wi-Fi/data disabled, the explicitly downloaded Read Along Orbit still opens
  locally in streaming mode, without the online label (`58`). Network restored.
- After refreshing fixture metadata by Force stop/relaunch (no app-data clearing),
  the SVG named `OEBPS/art 100%.svg` renders blue, while adversarial publisher white
  backgrounds, red html/body borders and shadows do not appear on the dark canvas
  (`59`). Text and illustration remain readable.
- The list of `reader-cache-v2` complete files is identical before/after these
  streaming checks, including the refreshed SVG fixture (one preexisting file).
  This checks the complete-file reader cache, not a claim of zero network buffers.

Both APK/lint variants pass; 428 JVM tests per flavor, 21 reader Node checks,
5 SQLite checks, 2 localization checks and 3 server-policy parser checks pass.
Native cached-file mode remains the default. PDF is still a complete-file reader.
Hardware attestation and real provider/output/device acceptance remain unverified.

## 0.9.7 enrollment UI and protocol checks

- In-place emulator upgrade retains login, Russian locale and reader progress
  (`60`). Settings shows version 0.9.7 and an initially unregistered key (`61`).
- Opening registration displays a confirmation explaining certificate disclosure,
  device/security prerequisites, no provider call and no server-policy change
  (`62`). Confirming against the isolated HTTP-only server fails closed, leaves
  the key unregistered and shows the localized explanation (`63`).
- This is an HTTPS-prerequisite/UI check, **not** successful hardware attestation
  or proof that emulator hardware claims are trusted. Production trust was not
  weakened to make the emulator pass.
- Separate JVM tests cover synthetic enrollment, scope/receipt failures, cleanup,
  SEC1 key IDs, canonical bytes and actual local HTTPS signed requests. The server's
  upstream verifier also accepts a JVM-produced public synthetic signature vector
  and rejects mutated bytes. No hardware/private production material is in it.

Both APKs/lints and 447 JVM tests per flavor pass. A disposable companion-server
checkout builds and passes 102 attestation/contract tests with targeted ESLint/
Prettier checks. The running isolated server and production server remain unchanged;
no AI provider was called. Physical-device/hardware acceptance remains necessary.

## 0.9.8 guarded reads

In-place emulator upgrade starts successfully and renders Home with its retained
login (`64`), checking the new enrollment/main-client dependency wiring at runtime.
The emulator has no registered key, so this is not an attestation success claim.
Both APKs/lints and 450 JVM tests per flavor pass; local HTTPS tests verify exact
guarded GET signing without adding headers to unguarded progress/file routes.

## 0.9.9 synthetic Story Guide UI

`StoryGuideSheetTest` runs Compose against a local MockWebServer with a synthetic
two-chapter guide. It checks no identities by default, explicit completed-chapter
selection, a separate pre-revelation identity, local search, disappearance after
merge and no canonical future names. Exactly two read-only HTTP requests occur;
chapter changes/search do not call a provider or write progress/sessions.

The initial run passed, but a rerun failed because the keyboard could obscure the
result. Added Search IME handling to hide the keyboard and clear focus, then ran the
final test **three consecutive times successfully**. Evidence is under
`.local/instrumentation/20261122T230959517914Z/`. The harness explicitly rejects JUnit
failure output even when `adb shell am instrument` returns exit code zero; 3 Python
parser checks cover that behavior. No arbitrary sleeps or ignored assertions were
used to make the scenario pass.

Both debug APKs/lints and 461 JVM tests per flavor pass, alongside existing Node,
SQLite/localization/policy checks. This is automated synthetic emulator UI evidence,
not full visual, physical-device, real guide generation or hardware acceptance.

## 0.9.10 ownership safeguards

Six new synthetic regressions first failed against the old code: three delayed
refresh ownership cases and three open-reading-session ownership cases. After the
fixes, the full suite passes 475 JVM tests per flavor, alongside both APKs/lints.
Additional cases cover stale refresh-cookie rotation, old 401 replay prevention,
logout/relogin generations, queued-request refusal before sending, valid refresh
reuse and transient failures preserving offline login without replaying rotation.

The final APK passes the synthetic Story Guide instrumentation again (evidence:
`.local/instrumentation/20261122T232708104800Z/`). An in-place emulator upgrade keeps
its fixture login and opens online Read Along Chapters with the blue SVG (`65`).
Closing the reader reports a **34-second** session for exact file 84 successfully
to the isolated stock server. The owner lease is a local Retrofit tag, not an extra
server DTO field. This is not a test of production history or audible/background
narration. Physical-device/provider/hardware acceptance remains unverified.
