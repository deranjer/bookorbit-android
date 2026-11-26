# Device test checklist

Record phone model, Android version and the app version shown in Settings. Debug
builds are development artifacts, not a promise of a stable release. Prefer the
isolated synthetic fixture server; do not delete production books or history.

## 0.9.14 streaming seek authorization (2026-11-26)

Phone report: streamed audiobook, screen off, headset skip-back by 10 seconds
raises an approximate “connection to server” error; retries with screen on may
work. This is not the local-file case and is not yet physical-device acceptance.

The previous ordinary-audiobook HTTP factory captured a bearer token at DataSource
creation and did not install AuthInterceptor/TokenAuthenticator. A reused source
could send an expired header after seek and could not refresh after 401. The new
factory retains configured TLS/CA trust and existing single-flight refresh, reads
live authorization for each request and preserves Range/source URL on the one
authorization retry. Absolute media URLs bypass BaseUrlInterceptor. Account leases,
server prefix and known modern/legacy audiobook routes are checked; redirects
are not followed. No playback restart, source replacement, download or extra
statistics session is used to conceal an HTTP failure.

Repeated instrumentation also exposed a separate command-advertising defect:
BookAggregatingPlayer added synthetic previous/skip-back commands in its getter
but forwarded raw ExoPlayer available-command events without them. A single-item
stream could therefore lose its previous command after state changes. After the
raw event, the wrapper now publishes its corrected command set and corresponding
onEvents, matching its existing posted metadata-correction pattern and preserving
standard ForwardingPlayer forwarding. A deterministic upstream-event/removal regression complements the
end-to-end seek test. The failed repeat in `20261126T104626410340Z` is retained
as failure evidence, not counted as a pass.
An intermediate Kotlin listener-delegation adapter was rejected: Java default
callbacks were not delegated, leaving the controller BUFFERING while the raw
player was PLAYING (`20261126T105220838283Z`). It is not in the final source.

Four JVM cases cover modern/legacy URLs under a reverse-proxy prefix, live tokens,
401 refresh preserving Range, account/destination refusal and redirect/503 bounds.
`StreamingSeekTest` uses actual HTTP Range WAV playback, the production HTTP
factory, BookAggregatingPlayer, a MediaSession and MediaController:

- Start a 180-second synthetic stream at 60 seconds, turn the emulator screen off
  and assert `PowerManager.isInteractive == false`.
- Expire the accepted server credential, invoke `seekBack()`, require a new Range
  request, one 401/refresh/retry with the exact same Range, and continued playback
  around 50 seconds with the screen still off. Queue/media ID/URI stay unchanged.
- Invoke previous-item with SKIP_BACK configured and verify another 10-second
  rewind without a second refresh.
- Separately reuse one DataSource across open/close after a token update and
  reject a subsequent account switch.

First run: two cases passed in
`.local/instrumentation/20261126T104317713682Z/`; final versioned reruns are recorded
in `docs/CLIENT_PLAN.md`. This is a controlled standalone session, not a physical
Bluetooth stack, OEM battery-management/Doze or production-server test. Synthetic
PCM is silent. No audible-output claim is made.

Final series: **three cases, five consecutive passes** in
`.local/instrumentation/20261126T105520838194Z/`. Auto, local narration, online
native clips and software decoder regressions pass in `20261126T105545403180Z`,
`20261126T105552288937Z`, `20261126T105557614949Z`, `20261126T105602446218Z`.

Media3 1.11.1 already defaults to WAKE_MODE_LOCAL with the current player settings;
the absence of an explicit setWakeMode call was not evidence of a missing CPU
lock. No speculative network/Wi-Fi lock or broad automatic retry was added.

## 0.9.13 local native narration (2026-11-26)

Both APKs/lints pass, with 551 JVM tests per flavor, 27 reader Node and 14 Python
checks. The local EPUB path feeds the same service-owned queue/MediaSession as
streamed SMIL; it never falls back to HTTP on local parse/revision/resource errors.
Snapshots are SHA-256 verified against the reader's edition fingerprint; audio
is extracted on demand to seekable temporary files with account checks.

Final emulator instrumentation: `LocalNativeNarrationTest`, two cases twice,
`.local/instrumentation/20261126T102520179598Z/`. Tests cover actual local PCM
playback through the production renderer factory, exact clip seek, natural
transition, restart after ended, fresh-source restore, changed-original snapshot
isolation, rejected revisions, account invalidation and temporary-file cleanup.
The configured server URL is unreachable (`127.0.0.1:1`). Online native clip,
Auto and software decoder regressions also pass respectively in
`20261126T102530579718Z`, `20261126T102535309031Z`, `20261126T102541620094Z`.

Manual retained-emulator acceptance uses the downloaded **Read Along Chapters**:

- Disable Wi-Fi and mobile data; `dumpsys connectivity` says
  `Active default network: none`. Open from Profile → Downloads.
- Restart narration, press Home. Over 12 seconds MediaSession remains PLAYING and
  advances item 2/0ms → item 3/1782ms without errors. Evidence:
  `.local/contracts/offline-native-narration-background.json`.
- Actual text highlight is visible in
  `.local/device-screenshots/offline-narration-highlight.png`.
- Pause and allow the existing checkpoint writer to persist 0.849 seconds at
  `OEBPS/second.xhtml#one`, `OEBPS/second.mp3`. Force-stop the application without
  clearing data. Reopen the downloaded edition while still offline. There is no
  audio session before explicit Play; Play restores item 2 at **849ms**.
  `.local/contracts/offline-native-narration-{saved-before-process,process-restore}.json`
  and `offline-narration-process-restore.png`. This is force-stop/reopen, not an
  OS low-memory kill test. Emulator networking was re-enabled afterward.

The first Android run exposed unsupported Harmony JAXP flags; mandatory DTD
refusal and a throwing entity resolver now work without those optional flags.
The first UI restart exposed a Media3 NPE caused by redundant renderer capability
invalidation; the shared renderer now ignores an unchanged MIME preference.
Both failures were fixed and rerun, not counted as successful evidence.

Remaining acceptance: SAF-backed EPUBs, large-book extraction latency/space,
physical audible output, Bluetooth/screen-off transport, OS-kill scenarios.
Selection-based “Слушать отсюда” remains a separate unfinished requirement.
Phone reports from 2026-11-26 (system navigation overlap and headset seek error)
are tracked explicitly in `docs/CLIENT_PLAN.md`; emulator success does not close them.

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

## Unreleased native narration foundation (2026-11-23)

- Android/Media3 Range instrumentation passes four tests twice against synthetic
  HTTP responses: exact file ID, single-encoded literal-percent asset URL, requested
  bytes/auth header, stale-account read refusal and allowlist refusal. Evidence:
  `.local/instrumentation/20261123T175108428507Z/`.
- The existing real-service Android Auto regression initially failed with library
  root result **-4**, before playback. Media3 1.11.1's deprecated `super.onConnect`
  returns an empty-command sentinel; copying it strips browse/player commands.
  `onConnectAsync` now uses the controller-aware defaults and adds the private load
  command only for this package **and UID**. Two consecutive complete tests pass,
  including legacy voice start, search, live downloads, chapters and empty-queue
  resume: `.local/instrumentation/20261123T175440827447Z/`.
- A standalone real ExoPlayer + MediaSession test passes twice with synthetic
  silent WAV and exact SMIL clipping: asset-clock checkpoint, natural fragment
  transition, system next/previous narrated section, foreground-before-play hook,
  and shared delegate survival after wrapper detach. Initial evidence:
  `.local/instrumentation/20261123T175733896933Z/`.
- Reading/narration handles and a single-activity arbiter have JVM tests for stale
  callbacks, exact-file progress and disjoint READ/LISTEN durations, including
  reader closure while narration remains active.
- Final development check: both full/fdroid APKs and lints pass, with **511 JVM
  tests per flavor**, 21 reader Node checks and 10 Android-tool Python checks.
  After the final test changes, native ExoPlayer/MediaSession (also checking that
  an old account cannot resume buffered audio), asynchronous 127-to-one Cast-style
  queue replacement, and the real-service Auto test each pass twice. Evidence:
  `.local/instrumentation/20261123T180014273074Z/`,
  `.local/instrumentation/20261123T180020246348Z/`,
  `.local/instrumentation/20261123T180027495206Z/`. The Cast-style test uses a
  delayed local delegate, **not a physical Cast receiver**.

These are development components, **not a released background read-along feature**.
The native clip wrapper is not yet selected by PlaybackService or reader controls;
offline native assets, JS highlighting, process restoration and service-purpose
handoff remain unfinished. Silent synthetic playback does not establish audible
output, spoken alignment, physical Bluetooth/Auto/Cast or real-device acceptance.
The published APK remains 0.9.10 (`d818ffa`); experimental builds retain code 910
and must not replace its archived APKs.

## Reader controls and concise UI copy (2026-11-24)

Unreleased working build, still code 910. Both flavor APKs/lints pass, with
**517 JVM tests per flavor**, 23 reader Node checks and 10 Android-tool Python
checks. The added JVM cases cover shared 30/40/30 tap zones, manga mirroring,
scroll-mode menu taps, invalid coordinates, older settings acquiring the original
40px margin, and serialization of the extended settings. Node checks execute the
bridge styling functions against a renderer fixture, including font/spacing and
switching between paginated and scrolled layouts.

`ReaderControlsUiTest` passes three tests twice on the isolated emulator:
`.local/instrumentation/20261123T220533062410Z/`. Tests cover help-zone labels and
RTL placement, dismiss, scroll-mode menu zones, font/flow/margin changes and reset.
These UI-only tests use a blank activity and never open user books or contact a
server. They do not establish physical-device gesture behavior or OCR support.

An in-place emulator upgrade retains the fixture login and opens streamed Read
Along Chapters with its blue literal-percent SVG. Reopening help via the toolbar
shows translucent 30/40/30 zones over the book; dismiss returns to the same view.
Changing font 16→17 and paginated→continuous updates the real WebView; help then
shows menu taps across all three zones. Reopening settings retains the chosen
font value. Font and flow were restored to their initial test values afterward.
Screenshots are under `.local/device-screenshots/reader-controls-*.png` (book,
zones, settings, scrolled, scrolled-zones, settings-restored). Percentages can
change with layout in this short fixture; exact-position preservation on a long
book still needs separate acceptance.

The native comic screen uses the same modal and hit-zone function, with RTL-aware
labels; an actual CBR/CB7 screen was not visually exercised in this increment.
EPUB and native comic swipe engines/animations remain different. Translation,
dictionary, selectable comic OCR and the reported **server web-reader CBZ 404**
remain open in `docs/CLIENT_PLAN.md`; none is claimed fixed by these controls.
RU/EN AI descriptions/status messages are shortened. Send confirmations retain
one brief notice about sharing text/photos with the AI service and possible fees.

## 0.9.11 reading tools and comic fixes (2026-11-24)

Working APKs are archived at `.local/apks/working-0.9.11-20261124/`, separately
from the published `d818ffa` APKs. Code 911, same signing certificate SHA-256
`9f8472c84b54b5033314fb3cd26ac949a2b7d818a78530263c3e8dc955c64f0d`.
Both APKs/lints pass, **532 JVM tests per flavor**, 23 reader Node checks,
10 Android-tool Python checks and 3 server-policy parser checks. The source is
still an uncommitted working tree, not a new published Forgejo/GitHub release.

### Selection tools

- EPUB word selection exposes Translate and Dictionary alongside highlights and
  reading help. Google receives text only after the panel's Translate button;
  Wiktionary receives the word only after Dictionary. These public online lookups
  are separate from server AI/attestation and do not use BookOrbit credentials.
- The emulator translates the actual selected synthetic EPUB word `small` to
  `маленький`, and the OCR sentence `Hello world!` to `Привет, мир!` through the
  real public Google endpoint. Wiktionary returns real definitions for `Hello`
  after its case-sensitive 404 is resolved with a lowercase lookup. Definitions
  are currently English; the picker chooses the word's language. No paid AI call
  or user book was used. Public service availability is not guaranteed.
- JVM tests cover query encoding, exact selected input/language, no auth/cookies,
  invalid input, missing-language refusal, dictionary case and punctuation.
  `ReadingToolsUiTest` passes twice, proving opening is inert until the button:
  `.local/instrumentation/20261123T230930033736Z/`.
- Screenshots: `epub-selection-tools.png`, `epub-translation-result.png`,
  `comic-translation-result.png`, `comic-dictionary-fixed.png`.

### Local OCR and comic navigation

- Both flavors bundle Tesseract4Android 4.9.0 and pinned Apache-2.0 English/Russian
  fast models with hashes/licenses in `assets/ocr/`. OCR processes the active image
  on-device; it never uploads a page. Recognition is capped at a 2400px longest
  edge and 10,000 words; cached coordinates are limited to six pages per reader.
  Cancellation discards stale results; it does not immediately interrupt a native
  recognition call already running. One worker at a time bounds that work.
- `ComicOcrTest` passes twice against a drawn synthetic bitmap with real native
  OCR and word coordinates: `.local/instrumentation/20261123T230924426865Z/`.
  `ComicOcrOverlayTest` passes twice for fitted-image hit coordinates, word tap,
  sentence hold and double-tap zoom: `.local/instrumentation/20261123T230914731597Z/`.
- A separate original `Orbit OCR Comic` fixture has numbered images 1, 2, 10 and
  English/Russian speech bubbles (`tools/test-server/ocr-fixture.py`). In the
  actual native reader, both scripts receive correctly aligned word boxes;
  tapping selects `Hello`, holding selects `Hello world!`, and swiping advances
  to image 2 with a new OCR result. Screenshots: `comic-native-ocr-zones.png`,
  `comic-native-ocr-selected.png`, `comic-native-ocr-sentence.png`,
  `comic-swipe-page-two.png`.
- CBZ now shares the CBR/CB7 native pager, but CBZ resolves an offline-first local
  ZIP. Pages are extracted lazily with size bounds and index-based filenames;
  extraction cache is capped at 128 pages/256 MiB per opened archive revision.
  Full archive SHA-256 scopes that revision. JVM tests cover numeric order,
  old lexicographic Foliate-CFI image mapping, hidden/path entries and changed
  archive refusal. A downloaded CBZ never falls back to server page URLs on a
  local extraction failure. CBR/CB7 still require server-rendered images.
- EPUB and comic taps now share 30/40/30 zones, RTL mirroring and 300ms/ease-out
  transitions, with an animation toggle. Their drag/fling physics, PDF gestures,
  physical-device acceptance, vertical-script OCR and difficult artwork remain
  unfinished acceptance work. This is not a claim of complete gesture parity.

### Server web CBZ and edition choice

- Stock 3.9.0 receives `format=cbz` in the exact selected book/file route. A full
  WebView run also exposed **401** on image requests: JS API requests carried
  bearer auth, `<img>` requests did not. The client now proxies only the exact
  selected file's same-origin numeric comic-image paths, with the captured native
  session owner; it sends no JWT in the URL or response headers. Wrong origins,
  files, methods/query strings are excluded; account changes stop buffered reads.
- `ServerComicImagesTest` passes three cases twice, plus once after the final
  license-only APK repack: `.local/instrumentation/20261123T230908485874Z/` and
  `.local/instrumentation/20261123T231728401326Z/`. HTTP smoke now checks synthetic
  CBZ lists and images (`.local/contracts/cbz-reader.json`). The real server
  WebView successfully renders **page 2** from the position saved in native
  reading: `comic-server-web-fixed.png`. Production server code was not modified.
  A separate failure in a standalone browser is not established fixed by this.
- The split `Read | ▾` button opens the remembered exact edition; only the arrow
  opens the picker. Choice is persisted by server/account/book, with prior local
  progress as an initial fallback. The second EPUB was chosen, the app upgraded,
  and the main button reopened the alternate edition directly with its own
  position. Screenshots: `edition-split-button.png`, `edition-split-choices.png`,
  `edition-last-remembered-after-upgrade.png`. `ReadEditionButtonTest` passes twice:
  `.local/instrumentation/20261123T230940365162Z/`.
- Existing `ReaderControlsUiTest` (3 cases) and `AutoMediaSessionTest` also pass:
  `.local/instrumentation/20261123T230957937380Z/`,
  `.local/instrumentation/20261123T231007657441Z/`.

Foreground EPUB/SMIL read-along is retained. Native background read-along remains
unconnected. Neither silent fixtures nor this emulator establish audible narration,
real-device OCR quality, Bluetooth/Cast acceptance or successful paid server AI.

## Selection and comic gesture rework (2026-11-24, unreleased)

User complaints and the running checklist are in `docs/CLIENT_PLAN.md`, under
the new 2026-11-24 requests. Archived 0.9.11 APKs remain unchanged. These checks
refer to the working tree, not a delivered release or physical-device acceptance.

- fdroid build, lint and 540 JVM tests passed. The new JVM tests cover measured
  panel placement, spatial OCR groups with interleaved word indices, punctuation,
  confidence filtering and the shared native/EPUB motion contract.
- `ComicOcrOverlayTest`: 2 tests passed in
  `.local/instrumentation/20261124T172058892997Z/`. Stationary hold, drag after
  hold, ordinary swipe refusal, fitted coordinates and double-tap are covered.
- `SelectionActionsUiTest`: passed in
  `.local/instrumentation/20261124T172106494478Z/`. A real measured toolbar stays
  inside a 280x400dp viewport at 140% font size and outside the selected rectangle.
- `ReaderPagerMotionTest`: 2 tests passed in
  `.local/instrumentation/20261124T172122555673Z/`: slow short drag cancellation,
  fast one-page flick, reverse direction and RTL boundaries.
- `ComicOcrTest`: the original native OCR text test passed in
  `.local/instrumentation/20261124T172112362218Z/`. A newly added blank/artwork
  test requires a newer test APK and is not included in that run.
- Actual native fixture reader: a stationary hold over `Hello` selects
  `Hello world!` directly. `selection-fixes-comic-held.png` shows the panel above
  the selection, without covering it. A subsequent UI simplification removes
  the range-mode toggle and replaces the long clear action with a close icon;
  that later panel still needs a new installed APK check.
- Actual pan at 2.5x zoom: a 200px horizontal input produces 181px image motion
  after initial touch slop, measured by screenshot alignment. Evidence:
  `.local/contracts/comic-pan-check.json`, screenshots
  `selection-fixes-pan-before.png` and `selection-fixes-pan-after.png`.

Spatial grouping is a conservative word/line proximity heuristic, not an image
bubble detector. Adjacent bubbles, complex artwork, low-confidence real text,
all zoom levels and transformed long-drag selection still need acceptance.
Comic selections are temporary: server-synced comic highlights are not implemented.

Follow-up verification of the final compact-panel working tree: both APKs, both
lints and 540 JVM tests per flavor pass (no failures/errors/skips). On the newly
installed fdroid APK, `ComicOcrTest` now passes both text and blank/simple-artwork
cases (`.local/instrumentation/20261124T172552621310Z/`), the two overlay tests
pass again (`20261124T172556958997Z/`) and measured toolbar placement passes again
(`20261124T172604509126Z/`). Actual native-reader stationary hold again selects
`Hello world!`; the final one-row copy/translate/dictionary/close panel is above
the selection: `.local/device-screenshots/selection-fixes-comic-compact-final.png`.
Blank/simple-shape acceptance does not establish false-positive quality on comics.

## First service-connected online native SMIL run (2026-11-24)

The working tree now connects online EPUB narration to the existing service-owned
ExoPlayer and MediaSession. It does not create a second playback service/player.
The reader delegates controls and follows the exact SMIL fragment through a
non-playing WebView overlay. Other editions and offline local sources retain the
foreground web engine until a native local source is implemented.

- First fdroid APK, test APK, unit tests and lint pass. Both-flavor verification
  of subsequent control/label changes is still running at this journal entry.
- 27 reader Node checks pass, including native fragment following without web
  audio and without duplicate fragment navigation.
- Auto session regression passes: `.local/instrumentation/20261124T185821418481Z/`.
- Native exact-clip/session transport regression passes:
  `.local/instrumentation/20261124T190023164917Z/`.
- In the actual retained fixture account, open streamed `Read Along Chapters`
  and start narration. Sending Home leaves the app's system MediaSession in
  PLAYING; two dumps three seconds apart show clip positions 5361 and 8188ms.
  `.local/contracts/native-service-background.json` retains filtered evidence.
- Returning to the app displays the second chapter and highlights its current
  SMIL sentence: `native-service-return.png`. `native-service-playing.png` shows
  the earlier first chapter. The older foreground-only subtitle in those images
  is corrected for native mode in the subsequent source revision.

The fixture is synthetic silent audio. These checks do not establish audible
output, physical-device behavior, offline native narration, process restoration,
Cast narration or complete audiobook/narration handover acceptance. No server
source/configuration was changed; fixture progress/session writes are normal
client behavior, not production-book changes.

Final follow-up build of the safety/control changes passes for both flavors:
542 JVM tests each, both APKs, both lints, 27 reader Node and 10 Android-tool
Python checks. Final installed-fdroid regressions:
`.local/instrumentation/20261124T190458825555Z/` (Auto) and
`.local/instrumentation/20261124T190505023248Z/` (native clip/session transport).
The manual background/return sample above preceded the last handover guards and
subtitle update; those last changes have build/unit/regression evidence but not
a repeated manual background sample yet. No new stable release is claimed.

## 0.9.12 bundled software audio decoding (2026-11-25)

Both full/fdroid APKs and lints pass, with **544 JVM tests per flavor**, 27 reader
Node and 14 Android-tool Python tests. The four new Python cases cover verified
source caching, corrupt downloads, size bounds and tar traversal/link refusal.
The working APKs are archived separately at `.local/apks/working-0.9.12-20261125/`;
`build-info.json` records SHA-256s, certificate and acceptance boundaries. The
archived 0.9.11 APKs are untouched. No public release/push was performed.

The new `software-audio` module builds official Media3 1.11.1 JNI/Java sources
against FFmpeg 8.1.3. Downloads are hash-pinned; the release PGP signature is
verified. No opaque third-party codec AAR, encoder, CLI, FFmpeg network protocol,
GPL-only or nonfree component is included. Generated APK assets include license
texts and provenance. Build/relink instructions are in `software-audio/README.md`.

`SoftwareAudioTest` instrumentation passes four cases **twice** in
`.local/instrumentation/20261125T092622305464Z/`:

1. The real JNI library loads and reports all declared audio decoders available.
2. ALAC produces PCM using the bundled decoder through the production factory;
   seek/second-track selection retain source URI and queue identity.
3. AC3 produces PCM and supports seek. This case explicitly prefers FFmpeg:
   a device's AC3 passthrough must not count as software-decoding evidence.
4. AAC first uses the platform decoder. An injected initialization failure then
   selects FFmpeg once at the same source time; the next track restores platform
   preference. This is deliberate fault injection, not a natural device failure.

The initial disabling-only retry failed this test: MappingTrackSelector still
mapped the group to the disabled renderer. The final implementation invalidates
platform format capabilities for that MIME/item so the group re-maps to FFmpeg.
Network/source/HTTP and DRM failures cannot trigger this codec retry.

`tools/android/check-software-audio-apk.py` verifies the real ELF class/machine
for armeabi-v7a, arm64-v8a, x86 and x86_64, all LOAD segments and uncompressed ZIP
entries aligned to 16 KiB, and license/source assets in both APKs. Results:
`.local/contracts/software-audio-package-{fdroid,full}.json`.

Auto regression passes in `.local/instrumentation/20261125T092709565563Z/` and
native narration clip/session transport in `20261125T092717592417Z/`. The actual
streamed `Read Along Chapters` also survives Home with the shared service still
PLAYING, transitioning from SMIL item 2 at 5876ms to item 3 at 4ms. Evidence:
`.local/contracts/software-audio-readalong-background.json` and
`software-audio-readalong-return.png`.

Original fixtures are silent. Audible output, physical ABI execution, performance,
battery, decoding every declared codec, release shrinking, offline native
narration and producing a Cast-compatible encoded stream remain unverified.
# 0.9.15: слушать из выделения (2026-11-26)

- «Слушать отсюда» находится только под ⋮ выделения с точным SMIL anchor.
  Неозвученный заголовок действия не имеет; кнопка начала главы удалена.
- Retained-эмулятор без сети: выбор первой озвученной строки → Home → native
  BUFFERING/PLAYING item 2 с 0 ms; повторный выбор второй строки → item 3.
  `.local/contracts/selection-native-narration-offline.json`, screenshots
  `selection-narration-{menu,playing,unnarrated-menu}.png`.
- Финальный code915: selection UI 3, local narration 2, native clips 1,
  streaming 3, Auto 1 прошли. Каталоги доказательств перечислены в
  `.local/apks/working-0.9.15-20261126/build-info.json`.
- Оба APK/lint и 559 JVM-тестов/flavor прошли; подпись прежняя, zipalign 16KiB
  проверен. Архив `.local/apks/working-0.9.15-20261126/` не заменяет старые APK.
- Это не физическая проверка слышимости, Bluetooth/OEM/Doze или SAF.
# 0.9.16: периоды и история статистики (2026-11-26)

- Retained fixture: переключены 30/7 дней, проверены дневные итоги в UTC,
  предыдущий период и недельная история с названиями книг/источниками/часами.
  Screenshots `statistics-{period30,period7,history-list}.png`.
- Сеть отключена до Refresh: явные ошибки статистики и истории, без выдуманных
  нулей. Сеть восстановлена, повтор успешен (`statistics-offline.png`).
- Финальный code916: UI period/partial failure и lazy list 3000 строк прошли
  2 раза (`20261126T113809788266Z`). 571 JVM-тест/flavor, оба APK/lint,
  подпись и 16KiB zipalign прошли. Архив `.local/apks/working-0.9.16-20261126/`.
- Daily timezone берётся из профиля сервера; история явно UTC ISO week.
  READ/LISTEN здесь не различается контрактом, формат M4B не является доказательством
  LISTEN. Длительности истории не добавляются к daily totals. Физической приёмки нет.
# 0.9.17: скачиваемые офлайн-словари (2026-11-26)

- EN→RU FreeDict/WikDict скачан явной кнопкой в настройках. В читалке без
  активной сети найден `book` с переводами, screenshot `dictionary-offline-book.png`,
  JSON `.local/contracts/dictionary-offline-book.json`. Сеть затем восстановлена.
- Оба официальных source-пакета 2025.11.23 отдельно импортированы без сети,
  поиски `book`/`книга` успешны (`20261126T120100886929Z`, 22.675 s).
- 4 safety-теста × 3 прогона: SHA-512/отмена медленной загрузки/повреждённое
  обновление сохраняют прежний индекс; atomic reinstall и удаление работают;
  SQL-параметры, ENTITY/license/size bounds проверены (`20261126T120004019586Z`).
- Открытие офлайн-панели не скачивает пакет и не отправляет слово:
  1 UI-тест × 2 прогона (`20261126T120029976545Z`). Прежний online lookup также прошёл.
- Настройки показывают размер/версию/лицензию, исходный архив и SHA-512 доступны
  в notices; управление без открытия книги (`dictionary-settings-final.png`).
- Оба APK/lint, 574 JVM-теста/flavor, 31 Node и 14 Python прошли. Подпись прежняя,
  16KiB zipalign проверен. Архив `.local/apks/working-0.9.17-20261126/`.
- Это эмуляторная приёмка двух направлений словаря, не весь FreeDict и не проверка
  физического телефона. Ограничения импорта являются safety bounds, не квотой книг.
# 0.9.18: подключение ИИ и прямая помощь (2026-11-26)

- Серверный путь сохранён; прямой выбирается явно, не является on-device моделью.
  Поддержаны форматы OpenAI Chat Completions и Google Gemini generateContent.
  Model ID задаётся пользователем, сохранение не проверяет доступность/цену и не
  делает платный запрос. Прямой Story Guide явно unsupported, server fallback нет.
- Android Keystore + UI: ключ зашифрован в файле prefs, не появляется в повторно
  открытом поле, удаление не переключает маршрут: 1 тест × 2
  (`20261126T123034196900Z`). Изолированные test-pref/key aliases удалены,
  реальные credentials retained fixture не менялись.
- Прямой запрос только после подтверждения с провайдером/моделью: 1 UI mock-тест × 2
  (`20261126T123019938156Z`). Выделенный текст отправлен только локальному
  MockWebServer, BookOrbit запросов 0. Настоящим провайдерам текст не отправлялся.
- Ручная проверка пустого settings dialog: FLAG_SECURE в dumpsys, API-токен не
  вводился, маршрут не сохранялся. `.local/contracts/ai-connection-secure-window.json`.
  Secure window намеренно не используется для скриншота секрета.
- 590 JVM-тестов/flavor, оба APK/lint, прежняя подпись и zipalign 16KiB прошли.
  Архив `.local/apks/working-0.9.18-20261126/`; regressions/evidence в build-info.
- Live paid-provider, физическая приёмка, качество ответов, полная spoiler-safety
  и структурированный direct Story Guide не приняты. Production не изменялся.
# 0.9.19: изоляция аннотаций (2026-11-26)

- Кеш разделён по server/user/readerFileId; owner-tag и readerFileId передаются
  серверу без дополнительных DTO-полей. Очереди не coalesce/delete чужие строки;
  legacy JSON и операции без владельца сохраняются, но не присваиваются аккаунту.
- Настоящая in-memory Room и локальный HTTP: 1 тест × 3 на финальном APK
  (`20261126T130721009435Z`). Пересоздание repository сохраняет проекцию/заметки;
  это не disk-reopen или process-death. No-op scheduler не запускает fixture worker.
- Read-only fixture GET book 5/file 84: 200, пустой список. Доказательство в
  `.local/contracts/annotation-reader-projection.json`; реальная серверная
  подсветка/её повторное открытие этим запросом не проверены.
- Оба APK/lint, 603 JVM-теста/flavor, 31 Node и 14 Python прошли. Финальные
  selection/narration/streaming/Auto/AI/dictionary/stats regressions прошли;
  evidence в `.local/apks/working-0.9.19-20261126/build-info.json`.
- Подпись прежняя, 16KiB zipalign проверен. Retained downloads не очищены.
  Полная привязка ревизии файла, uncertain-create reconciliation, синхронизация
  геометрии комикса и физическая приёмка остаются открытыми. Production не менялся.
# 0.9.20: неопределённая доставка CREATE (2026-11-26)

- До отправки CREATE сохраняется состояние `create-uncertain`. Потеря ответа,
  отмена или рестарт не вызывают автоматического повторного POST. Подтверждённый
  ответ завершает создание и сохраняет следующие edit/delete; явный HTTP 401
  допускает повтор после авторизации. Это не автоматическая сверка с сервером.
- Room + локальный HTTP: сервер получил POST, затем разорвал соединение;
  база на диске закрыта и открыта повторно. Текст/заметка остались, три flush
  не повторили POST. Вместе с account/file isolation: 2 теста × 3
  (`20261126T131453476161Z`). Это не принудительное завершение процесса Android.
- UI показывает неподтверждённые текст/заметку и предупреждение, не рисует CFI
  и запрещает удаление неизвестного объекта: 1 тест × 2 (`20261126T131500920557Z`).
- Если процесс умер до фактической отправки, запрос также не возобновится сам.
  Нужна проверка сервера; resolver UI пока нет. Доставка старых `create` из прошлых
  версий задним числом не установлена. Ручной downgrade не поддержан.
- Оба APK/lint, 606 JVM-тестов/flavor и 14 Python прошли; selection, narration,
  streaming, Auto, AI, dictionary, stats и OCR overlay regressions прошли.
  Архив `.local/apks/working-0.9.20-20261126/`, подпись прежняя, zipalign 16KiB
  проверен. Schema 11, retained data не очищены. Production не изменялся.
# 0.9.21: каталог языков и расширяемые подключения ИИ (2026-11-26)

- В APK только metadata FreeDict: 305 пар, включая версии с `+fd1`. Выбор
  языка ищется по локализованному имени/коду, установленные фильтруются, выбор
  сохраняется. Каталог обновляется явно и кешируется; открытие не делает сеть.
- Catalog UI fra-eng/reopen/inertness: 1 × 2 (`20261126T164700255868Z`).
  Dictionary safety, cache reopen/failed refresh, corruption/cancellation:
  5 × 3 (`20261126T164739533545Z`). Четыре настоящих source-пакета EN↔RU,
  FR→EN и ES→EN импортированы и найдены без сети, 18.379 s
  (`20261126T164814907358Z`). Не каждый пакет каталога проверен на импорте.
  CC BY-SA и GPL licenses/COPYING сохраняются отдельно, не подменяются одной.
- ИИ: OpenAI, Gemini, OpenRouter, Ollama native и OpenAI-compatible text adapters.
  Custom URL prefix/optional token/HTTP opt-in и endpoint-specific consent:
  2 UI-теста × 2 (`20261126T164719360209Z`), фактический POST только локальному
  MockWebServer, без BookOrbit bearer/cookies. Платные и реальные модельные
  серверы не вызывались. OpenRouter request/response проверен JVM mock-тестами.
- Keystore 1 × 2 (`20261126T164747310032Z`), OpenAI consent 1 × 2
  (`20261126T164757810301Z`), остальные regression evidence в build-info.
- В retained settings вручную проверены 305 пар и пять provider chips; токен
  не вводился, unsaved direct choice отменён. Dictionary screenshots
  `dictionary-{catalog-305-settings-final,language-chooser-final}.png`,
  `.local/contracts/expanded-catalog-and-ai-settings.json`. Старый индекс сохранён.
- Оба APK/lint, 615 JVM-тестов/flavor, 31 Node и 14 Python прошли. Подпись прежняя,
  zipalign 16KiB проверен. Архив `.local/apks/working-0.9.21-20261126/`.
- Не заявлены: live model availability/качество/цены, физический LAN/OEM-тест,
  мультимодальность/tools/direct Story Guide или импорт абсолютно всех пакетов.
  Никаких изменений production, истории или книг не делалось.
