# Independent Android client plan

**Updated: November 26, 2026 · active development branch: `feat/independent-android-client`**

This is the maintained English plan for the AGPL fork of
[deranjer/bookorbit-android](https://github.com/deranjer/bookorbit-android).
It is a translation and consolidation of the original requirements and implementation
notes, not a declaration that all work is finished. The complete bilingual source log
is preserved in [history](history/CLIENT_PLAN.source-2026-11-26.md), including failed
experiments, exact local evidence paths and superseded intermediate status reports.

**Start here:** [Roadmap](../ROADMAP.md) · [README](../README.md) ·
[Device acceptance](DEVICE_TESTING.md) · [AI contracts](AI_COMPATIBILITY.md) ·
[Audio contracts](AUDIO_COMPATIBILITY.md)

## Status vocabulary

| Status | Meaning |
| --- | --- |
| Implemented | A code path exists; this alone does not prove it works on a device. |
| Automatically checked | The stated build, JVM, HTTP, SQLite or emulator scenario passed. |
| Device acceptance pending | Real hardware, the user's media/deployment or long-running behavior still needs testing. |
| Contract blocked | A required server API or trust policy is unavailable; do not invent an equivalent. |

Historical checkboxes sometimes predate later implementation. The current status
below takes precedence; preserved historical reports remain dated rather than rewritten.

## Non-negotiable requirements

1. **Independent identity and attribution.** Keep AGPL/upstream notices. Application
   ID: `io.github.monkecode.bookorbitclient`, debug suffix `.debug`, source namespace
   `com.bookorbit`, OIDC callback `bookorbitclient://oauth2-callback`. Never impersonate
   the official package or use its signing identity.
2. **Do not mutate production during development.** No changes to the user's server,
   TrueNAS, BookBridge, books, provider configuration or reading history without
   separate approval. Use isolated infrastructure and original synthetic media.
3. **Exact identity everywhere.** Preserve server/account/book/edition/file identity,
   ordered audio manifests, source clocks and revisions. Never identify an edition
   merely by its filename, extension, size or position in a list.
4. **No invented activity.** A text locator and audio clock are separate coordinates
   of one activity. Do not create duplicate queues, READ/LISTEN sessions or events,
   infer LISTEN from an audio format, or add overlapping session durations to daily totals.
5. **Explicit, additive offline content.** Browsing, syncing or opening a chooser
   must not create permanent downloads. Adding an edition must not remove existing
   content. No Premium gates or book/download quotas.
6. **Safe ownership and delivery.** Capture the owner when activity/requests start.
   Stale responses must not write to a new account, erase newer writes or resurrect
   deleted data. Ambiguous annotation CREATE delivery must not trigger an automatic duplicate.
7. **Explicit AI disclosure.** Settings do not make paid requests. Show the active
   route/provider/model/endpoint and confirm data transmission/cost. Do not silently
   retry a paid POST or fall back to another provider/server.
8. **Honest acceptance.** Distinguish inherited code, implementation, automated
   checks, emulator behavior, physical acceptance and audible output. Silent fixtures
   do not prove spoken synchronization or headphone routing.

## Current checkpoint

**0.9.23/code923** is the latest completed development checkpoint. Both APK/lint
tasks and **627 JVM tests per flavor**, **34 Node** checks (31 reader plus three
policy checks) and **14 Python** checks passed. Room schema remains **11**.
Diagnostic logging instrumentation passed two cases across three runs; the real
Android document picker exported a safe readable file on the isolated emulator.
See [Device testing](DEVICE_TESTING.md) for boundaries and private evidence paths.

Local immutable APK archives and `build-info.json` live under ignored `.local/apks/`.
They are not public downloads. A source push is not authorization to create a release,
publish an APK or deploy a server change.

## User feedback and acceptance ledger

| Report / requirement | Implemented response | Remaining acceptance |
| --- | --- | --- |
| Choosing 75 individual audio files is unacceptable | One audiobook edition/action containing all exact ordered track IDs; additive missing-track selection | Device interruption/SAF and complete metadata presentation |
| The UI looks dated and overly generic | Screen-by-screen redesign, editorial book detail, compact actions and fullscreen AI setup | Agreed style variants, consistent whole-app design and user acceptance |
| Dark reader shows page rectangles | Shared canvas/root surface and publisher root border/shadow fixes | User's EPUB, rotation/columns/live theme changes and diverse WebViews |
| Tapping the current tab does not return to its root | Nested-stack reset and detail-pane clearing | All configurable tabs, rail/two-pane and Back regressions on hardware |
| System navigation overlaps player controls | Navigation-bar insets, checked with gesture/three-button layouts | Physical/OEM acceptance |
| Headset skip-back fails on streaming audio with screen off | Live authenticated Range transport and corrected wrapper command publication | The original phone/headset/server/Doze scenario, not just controlled emulator tests |
| Selection toolbar is large, obscures text or leaves the screen | Measured compact positioning, separate overflow/palette, 48 dp action targets | Complex selections, rotation, large fonts and hardware |
| OCR hold/range jumps across speech bubbles | Spatial word groups, hold-and-drag expansion, confidence/geometry filtering | Real adjacent bubbles, handwriting, complex artwork and other writing directions |
| Comic panning accelerates with zoom | Removed repeated scale multiplication from screen-space deltas | Multitouch, arbitrary zoom and physical devices |
| Dictionaries were restricted to EN↔RU | 305-pair metadata catalog with searchable localized directions | Package-by-package compatibility; only four real packages import-tested |
| AI settings are extremely inconvenient | Fullscreen form, provider dropdown, fixed Save/Cancel, concise scope and optional details | User acceptance; this is a first revised layout |
| Short periods make calendar cells enormous | Fixed 18 dp cells, horizontal scrolling instead of weighted stretching | Physical visual acceptance |
| Photo search does not work | Clear server-only explanation, access failure/retry, picker remains available | Actual server trust/provider diagnosis and real recognition |
| Story Guide is hard to find | A visible card below Read/Listen, before download controls | Real guide generation/access and clearer future integration |
| Device registration fails despite HTTPS | Persistent stage/HTTP/bounded-code diagnostics; direct entry from AI settings | Exact physical-phone failure, server version and trust/hardware validation |
| Add a debug switch and downloadable logs | Opt-in structured private logging, bounded retention, explicit Android document export; 0.9.23 tests passed | Physical-device and user acceptance |

The user explicitly clarified that the headset problem concerns **streamed**, not
downloaded, audio and that the broken AI search means **photo search**. Do not ask
them to repeat these facts or substitute a different test scenario.

## 1. Editions, downloads and ownership

### Implemented

- Text edition / audiobook / both choices. All 75 tracks form one audiobook card;
  the persisted plan still carries every authoritative file ID and order.
- Existing downloaded files stay locked/retained when adding content. Partial audio
  is one partially available edition; confirmation requests only missing tracks.
- Exact text-file selection reaches native EPUB/PDF/comic and server readers.
  The main Read action reopens the last selected available edition; its arrow opens
  the chooser. Selection is server/account/book scoped, with distinct edition positions.
- Published content survives extension failures. Worker generations, transactional
  snapshot CAS and stable storage IDs prevent stale completion/deletion races.
- Acknowledgements clear only the exact queued write snapshot. PDF/comic page
  coordinates survive retries. Cache publication is atomic and owner scoped.
- Session/token-refresh leases prevent old-account responses from replacing a new
  token, signing out a new user or relabeling an open reading/listening snapshot.

### Remaining

- [ ] Show narrator, duration, total known size and track count without invented values.
- [ ] Re-test two identically named editions, 74/75 downloaded tracks, interrupted
  extension, revoked SAF permission and preservation of existing content/positions.
- [ ] Validate OIDC, upgrade/migrations, long transfers and process/network changes
  on hardware. Force stop, swiping Recents and an OS kill are different scenarios.
- [ ] Do not split multiple narrations by filename heuristics: the current model
  exposes one audio-metadata set per server book. Require explicit server edition IDs.

## 2. Readers, navigation and comic OCR

### Implemented slices

- Shared dark canvas/chapter roots rather than opaque backgrounds on every nested
  element. Theme changes must not invert illustrations or damage links/highlights.
- Reselecting a tab returns to that tab's root, clears nested detail and resets the
  Library subsection. Ordinary tab switching retains its own saved state; playback continues.
- Reader appearance: font family/size, line spacing, margins, width, columns,
  alignment, hyphenation, themes and paged/continuous mode, persisted and applied live.
- Reopenable **?** gesture-zone help in text/comics, including paged/scroll/RTL behavior.
- CBZ now uses a local native ZIP/pager path; old image CFIs map by image name.
  Text/comic zones use 30/40/30 with RTL mirroring and shared 300 ms/ease-out settings.
- English/Russian on-device Tesseract OCR, word geometry, tap/hold/range selection,
  copy/translation/dictionary actions, stale-result rejection and a six-page cache.
- OCR range uses spatial groups rather than contiguous global indices. Confidence
  threshold 65 and punctuation/degenerate-fragment filtering are heuristics, not a bubble detector.
- Pan uses screen-space finger displacement instead of multiplying scale twice.
- Server/Web CBZ routing supplies `format=cbz` and owner-bound native image auth;
  arbitrary origins never receive the BookOrbit bearer token.

### Remaining

- [ ] Validate selection placement outside the selected text at viewport edges,
  system insets, rotation and 140% fonts, keeping action targets at least 48 dp.
- [ ] Re-test native holding inside the real zoomable reader, complex artwork,
  short genuine words, adjacent bubbles, RTL/vertical text, handwriting and memory use.
- [ ] Finish consistent drag/fling behavior across EPUB, comics and PDF without
  breaking zoom or manga direction. Shared tap zones do not prove identical physics.
- [ ] Validate dark roots across actual books, SVG, columns and live theme changes.
- [ ] Validate bottom navigation, navigation rail, two-pane/configurable tabs and
  Back after reselection; no duplicate root or reopening the discarded nested book.
- [ ] Investigate a standalone-browser 404 separately if reproduced; the Android
  image-auth fix is not proof that every browser/server route works.
- [ ] Resolve offline CBR/CB7 or keep the explicit server-rendered-page limitation.

## 3. Streaming, read-along and audio compatibility

### Implemented

- Audiobooks use Media3 HTTP Range streaming, not a hidden permanent full download.
  Auth is live, refresh is single-flight, origins/routes are bounded and ownership
  is checked on open/read. A refresh retains the same Range and source clock.
- `APP_STREAMING` is opt-in. EPUB metadata/chapters/resources load through scoped
  exact-file native transport; JWT is not exposed to WebView. Cached-file mode stays
  the default and explicitly downloaded editions override either mode.
- Streaming failures do not silently download a whole archive. Other formats,
  including PDF, still need complete temporary files. Literal-percent resources
  account for the audited server's double-decoding behavior.
- Native SMIL uses the existing PlaybackService/ExoPlayer/MediaSession, not a second
  parallel player. Clips, highlighting, transitions and checkpoints are source scoped.
- Downloaded EPUBs use verified private snapshots and lazy seekable audio extraction.
  A local parse/resource/revision failure does not fall back to HTTP or WebView audio.
- **Listen from here** exists only in selection → ⋮ for a uniquely matched narrated
  SMIL fragment. It starts at the fragment boundary, never a guessed percentage or
  claimed word-accurate timestamp. The old chapter-start button was removed.
- A current narration queue seeks rather than duplicates itself; stale deferred
  starts are invalidated and playback rate is retained. Unnarrated/ambiguous selections
  and ordinary audiobooks do not expose the action.
- Source-built Media3 FFmpeg audio decoding ships for four ABIs, with pinned sources,
  licenses and 16 KiB alignment. Platform playback stays first; one supported decoder
  retry retains the item/clock/queue. Network/DRM failures are not codec incompatibility.

### Boundaries and remaining work

- [ ] Complete spoken-overlay, background/return, manual page navigation, end-of-book,
  unnarrated-section, Bluetooth/Auto/OEM/Doze and OS-kill acceptance on physical devices.
- [ ] Verify large local EPUB/SAF latency, temporary space and repeated extraction.
  Current safety bounds: snapshot 2 GiB; audio resource 1 GiB; XML 4 MiB/document,
  32 MiB total; 100,000 archive entries/clips. Do not present these as book quotas.
- [ ] Complete cross-device text/audio-coordinate compatibility and session arbitration
  acceptance. Never infer SMIL clocks from reading percentages.
- [ ] Investigate external-audio/text alignment only through a real alignment contract.
  A separate EPUB and arbitrary MP3s are not automatically synchronized narration.
- [ ] Complete adaptive playback/transcoding where a real contract exists. The audited
  stock server serves original audio with Range; its FFmpeg metadata tooling is not
  a playback-transcoding endpoint. Cast still needs a compatible encoded stream.
- [ ] A local encoder/cache/proxy, receiver capability negotiation and release shrinking
  remain separate work. Do not call decoder fallback full adaptive transcoding.

Streaming source fingerprints use the captured server revision; local checkpoints
use full EPUB SHA-256. KOReader partial MD5 is not a full-byte integrity hash. The
server lacks an atomic archive-revision precondition, so mid-session archive changes
cannot be promised byte-consistent. Loading a checkpoint never autoplays.

## 4. AI connections, photo search and Story Guide

### Implemented

- Explicit **server** or **direct** route, encrypted provider credentials scoped to
  server/account/provider, revision-bound consent and cancellation on ownership changes.
- Direct text adapters: OpenAI Chat Completions, Gemini generateContent, OpenRouter,
  Ollama native `/api/chat`, and configurable OpenAI-compatible Chat Completions.
- Custom base prefixes are retained; credentials/query/fragment in URLs are forbidden.
  HTTP needs explicit opt-in. Ollama/compatible tokens can be absent; endpoint changes
  do not carry the old token. Model namespace/tag syntax is preserved.
- Selected-text help is bounded to 12,000 characters and excludes BookOrbit IDs,
  tokens, the rest of the book and unseen chapters. Provider text responses are bounded;
  refused/truncated/blocked/tool-only output is not treated as a complete answer.
- Direct text uses one-shot requests with no redirects/automatic paid retries.
  OpenRouter requests disable provider fallbacks. Save/open never tests a paid model.
- Server Reading Help uses cancellable SSE. Photo search has cover/shelf modes,
  bounded orientation-aware JPEG preparation without EXIF/GPS, preview and confirmation.
  Returned library matches use authoritative IDs and do not alter metadata/history.
- Capability checks use empty invalid DTOs before any user input is sent. Permission,
  attestation, provider setup, budget and connectivity are distinct failure categories.
- Server Story Guide reads exact-edition metadata/content and offers separately
  confirmed build/update/retry/cancel on compatible deployments. Generation is a job,
  not a model call on every view; an open panel polls queued/running work every five seconds.
- Format-1 entities, joined identities, relationships and recaps are projected only
  through an explicitly completed chapter. Future canonical names are not fallback
  labels; invalid/invisible/ambiguous anchors fail closed. Search is local and inert.

### Remaining

- [ ] **Direct Story Guide/Graph:** design bounded chapter processing, capability and
  structured-response validation, context limits, cancellation and per-feature models.
- [ ] Define local result storage, revision binding and real BookOrbit import/sync
  compatibility. A provider connection is not an implemented server Story Graph contract.
- [ ] Define multimodal photo support separately; text API compatibility does not
  imply images/tools. Ollama model installation remains external to this app.
- [ ] Add automatic CFI-to-guide-anchor mapping and guide-fix editing only against
  audited contracts. Do not guess spoiler bounds from a percentage.
- [ ] Validate real provider availability/pricing/quality and inferred-spoiler risks.
  Mocks prove protocol/consent, not model quality or comprehensive spoiler prevention.
- [ ] Validate photo search and guide generation on the user's authorized deployment.
  Audited 3.9.0 generation limits include English text books, not PDF/comics/audiobooks.
- [ ] Do not claim Apple Intelligence on Android. General Q&A requires its own implementation.

## 5. Device trust and diagnostics

- Enrollment is optional and explicit: HTTPS, challenge-bound non-exportable Android
  Keystore P-256 key, leaf-first certificates, SEC1 key ID and production receipt.
- Assertions bind exact body bytes, method, encoded API path/query, clock and nonce.
  Failed replacement preserves prior registration; unsuccessful new keys are cleaned up.
  Key metadata is excluded from backup and no private device key leaves Keystore.
- Unknown keys do not trigger auto-enrollment or replay a paid request. A server
  restart can require explicit registration again.
- **Stock server trust is not changed by enrollment.** Its official package/signing
  policy can reject this fork. The [README guide](../README.md#allow-this-client-on-your-own-server)
  explains the separately approved exact-identity source change, not an unsigned bypass.
- The minimal v3.9.0 policy experiment passed 109 attestation tests, targeted lint and
  production compilation of 1,785 files. Synthetic roots are test-only; production
  roots, hardware/boot, ownership and replay checks remain unchanged.
- Persistent registration errors now show connection/challenge/key/registration/
  receipt/storage stage plus safe HTTP/code fields, not raw server messages or certificates.

### Diagnostic logging request (0.9.23)

- Default-off Settings switch with explicit consent, persistent device-local state,
  bounded private storage and explicit Android CreateDocument export.
- Only allowlisted structured fields: time, versions, request category/method/status,
  duration/failure category, attestation stage/code and numeric player error.
- Never collect URLs, account/book/file IDs, headers/cookies/tokens/passwords,
  bodies, certificates, books/photos, arbitrary exception messages or logcat.
- At most two 256 KiB files; bounded event queue. Turning off invalidates pending
  write tickets. Clear removes retained records and invalidates old queued events.
- No upload, automatic bug report, server development mode or trust-policy change.
- [x] Verify default-off, persistence, bounded rotation, disable/clear races, exported
  bytes and actual Settings/file-picker behavior. Five new JVM cases and two Android
  cases × three passed (`20261126T193327627718Z`). Storage failure does not alter
  the HTTP response or crash the caller. Manual Settings → export → Downloads and
  clear/off checks are recorded in `.local/contracts/diagnostic-export-ui-0923.json`.
- [ ] Complete physical-device acceptance. The logger is deliberately not full logcat
  or a crash recorder, and a device-wide redacted log is not per-account history.

## 6. Annotations and uncertain delivery

### Implemented

- Server/user/reader-file-scoped caches, owner-tagged requests and projection-aware
  CRUD; queues never coalesce/delete another user's rows. Legacy unowned/cache data
  is retained without assigning it to the next account. Room schema stays 11.
- Stale GETs do not resurrect deleted highlights; mutation responses must match the
  authoritative book/annotation identity. PATCH retains reader-projected CFI.
- New CREATE dispatch is durably marked `create-uncertain` before networking.
  Lost response/cancellation/reopen does not trigger automatic POST replay.
- Confirmed IDs permit subsequent edit/delete; a definite rejecting 401 can allow
  an explicit authorized retry. Deletion during uncertain delivery preserves intent
  (`create-uncertain-delete`) and the original text/note.
- Unconfirmed rows show retained text/note and a warning, without a fabricated CFI,
  jump target or destructive UI action. This favors avoiding duplicates over automatic recovery.

### Remaining

- [ ] Complete file-revision binding for cache, legacy rows and outbox operations.
- [ ] Design automatic/manual uncertain-delivery reconciliation and resolver UI.
  The user must verify the server before creating another copy. A process dying
  before actual dispatch also leaves a request that will not automatically resend.
- [ ] Do not claim retroactive delivery knowledge for pre-0.9.20 CREATEs or safe
  manual downgrade to a client that does not understand these states.
- [ ] Comic geometry synchronization needs image/page anchors, exact file/revision,
  text/note/color and confirmed web re-rendering. Stock PDF rectangles require a real
  PDF; `readerFileId` projects text CFI, not comic OCR geometry. This remains blocked.

## 7. Dictionaries, translation and statistics

### Dictionaries and translation

- Explicit official FreeDict metadata refresh/download with pinned size/SHA-512,
  bounded streaming XZ/TAR/TEI import and atomic private SQLite publication.
- Catalog metadata for 305 pairs is bundled, not dictionary data. Search uses
  localized language names/codes, installed-only filtering and a persisted direction.
- Source URLs stay on official HTTPS paths; versions such as `+fd1` encode `+` as
  `%2B`. Do not drop a valid language merely because its release syntax differs.
- Each package preserves its own source/header/COPYING/license: EN↔RU CC BY-SA
  does not make French/Spanish GPL packages CC BY-SA. No BookOrbit credentials or
  lookup words are sent by dictionary transport.
- Failed/cancelled updates preserve the old index and remove temporary files.
  Cancel the OkHttp call, not a body concurrently being read. External XML entities
  and unsafe archive paths are refused; limits are safety bounds, not a reading quota.
- Four real packages were imported/searched without network: EN→RU, RU→EN, FR→EN,
  ES→EN. This does not prove all 305 packages or morphology/fuzzy matching.
- Online Wiktionary and selected-text Google translation are separate explicit
  sources. Translation sends only panel text; the public transport's 5,000-character
  request limit is not Premium or a global reading limit.
- [ ] Offline translation and additional explicitly selected translation services.
- [ ] Real-package/language coverage, license presentation and physical acceptance.

### Statistics

- Implemented 7/30/90/365-day periods, previous equal-period comparison, source split,
  compact heatmap and lazy weekly session history capped at 3,000 records.
- Daily keys follow the account timezone from `auth/me`, with a bounded UTC query
  buffer; do not reinterpret them in the phone timezone. History uses UTC ISO weeks.
- The stock session-timeline contract does not distinguish READ/LISTEN. An M4B is
  not proof of listening and history durations are not added to daily totals.
- Account changes, ISO-year boundaries, malformed/duplicate rows and independent
  history failures are checked. Offline Refresh reports failure, not fabricated zero activity.
- [ ] Full server-chart parity, progress/dynamics and separate READ/LISTEN analytics
  only where reliable contracts and access exist. Guarded `activity-*` is not silently substituted.

## 8. Design and remaining acceptance

- [ ] Agree user-selectable visual styles before treating variants as approved.
  Share palette, typography, shape, spacing and state tokens across navigation,
  library/search, details, editions, player, downloads and settings.
- [ ] Persist style choice, preview and reset; do not reset navigation, downloads,
  reading positions, playback or independent book-reader preferences.
- [ ] Separate style from system/light/dark mode and book text appearance.
- [ ] Verify every supported style with Russian/English, long titles, large fonts,
  contrast, narrow/tablet/foldable layouts, RTL and accessibility.
- [ ] Finish concise consumer-facing wording. Keep consent at send time and technical
  detail in documentation or expandable sections, not every routine hint.
- [ ] Finish whole-app localization/visual review without suppressing MissingTranslation.
- [ ] Upgrade toolchain/dependencies in checked slices, not as an endless reason to
  postpone read-along or AI. The 0.9.4 migration is already completed, not future work.
- [ ] Physical acceptance: OIDC, migration/data retention, SAF/revocation, long downloads,
  network/process changes, audible output, Bluetooth/Auto/Cast and battery behavior.

## Milestone and evidence index

Counts below are per flavor where applicable, and describe those historical checkpoints.
Local `.local/` artifacts are intentionally ignored, not public repository files.

| Slice | Main result | JVM tests / evidence |
| --- | --- | --- |
| 0.9.0–0.9.1 | Independent identity, exact-file progress and additive downloads | 335 → 377; SQLite migration/guard checks |
| 0.9.3–0.9.4 | Edition grouping, tab/dark fixes, initial foreground SMIL/guide, toolchain migration | 394; retained emulator upgrade and 75-track choice |
| 0.9.5–0.9.6 | SSE/photo clients, Russian resources, exact local clocks, on-demand EPUB | 420 → 428; synthetic HTTP/WebView checks |
| 0.9.7–0.9.10 | Enrollment/signing, guarded reads, guide relationships, refresh/session ownership | 447 → 475; 102 companion attestation tests, no real hardware claim |
| 0.9.11 foundation | Native narration, selection/OCR/pan work | 540 → 542; silent media and synthetic OCR |
| 0.9.12 · `ca717c6` | Four-ABI source-built FFmpeg and one decoder retry | 544; `20261125T092622305464Z`, four runtime cases twice |
| 0.9.13 · `71c81b7` | Verified local native snapshots and offline background/resume | 551; `20261126T102520179598Z`, exact 849 ms force-stop restore |
| Insets · `3a73cbc` | Player navigation-bar padding | Gesture/three-button layouts at two resolutions |
| 0.9.14 · `4fb80a3` | Authenticated streaming seek and synthetic-command publication | 555; three tests × five, `20261126T105520838194Z` |
| 0.9.15 · `9831228` | Selection → ⋮ → Listen from here | 559; offline retained selection/native queue evidence |
| 0.9.16 · `ea7febc` | Statistics periods, timezone and bounded history | 571; `20261126T113809788266Z`, two cases twice |
| 0.9.17 · `e0eabd8` | Explicit downloadable dictionary indexes | 574; safety × three and real offline packages |
| 0.9.18 · `0fbe7d9` | Protected direct OpenAI/Gemini text help | 590; consent/Keystore UI, no real paid calls |
| 0.9.19 · `a0eae4d` | Owner/file annotation scope | 603; Room/HTTP × three, not process death |
| 0.9.20 · `e1c3436` | Durable uncertain CREATE states | 606; disk reopen after lost response, no repeated POST |
| 0.9.21 · `c0288a0` | 305 pairs and three more direct text providers | 615; four real packages offline in 18.379 s |
| Policy experiment · `f3e13b8` | Minimal exact-identity server policy test | 109 server tests/build; synthetic attestation, undeployed |
| 0.9.22 · `4598474` | Fullscreen AI setup, compact heatmap, guide entry, registration diagnostics | 622; portrait/landscape, mock access and safe-error UI |
| 0.9.23 · `4d6ff24` | User-requested diagnostic logging and English documentation refresh | 627 JVM/flavor; two diagnostic Android cases × three; actual document-picker export |

### Key evidence and honest limits

- Offline native narration: `.local/contracts/offline-native-narration-{background,
  saved-before-process,process-restore}.json`. Home transition and 849 ms force-stop
  restoration were checked; a low-memory OS kill, Bluetooth and audibility were not.
- Streaming seek: controlled Range + one injected 401 refresh + screen-off
  MediaSession, not the user's physical headset acceptance. Failed listener adapters
  were removed and failed runs were not counted as passes.
- Selection/OCR/pan: `.local/contracts/comic-pan-check.json` and
  `selection-native-narration-offline.json`; one 2.5× synthetic pan test is not all-scale acceptance.
- Dictionaries: `20261126T164814907358Z` for four real offline imports;
  metadata snapshot SHA-256 `08078a2c28bb06dac716ca4b0e785621ed8bfde7c0a4aac15236c3279e2ca060`.
- Annotation uncertainty: `20261126T131453476161Z`, two cases × three; disk Room
  reopened after the server received POST and disconnected, with no second POST.
- 0.9.22: `.local/contracts/{client-polish-0922,ai-settings-responsive-0922}.json`.
  Secure AI screenshots are intentionally black; hierarchy/tests establish geometry
  without disabling FLAG_SECURE. No real provider, phone registration or production mutation.

## Completion rules

1. Reproduce the reported scenario, add its regression, then build both flavors and
   run both lint tasks plus relevant JS/CSS/SQLite/HTTP/instrumentation checks.
2. Freeze source during final verification. Re-run affected checks after any code
   change so evidence matches the archived APK, not a previous intermediate build.
3. Keep previous APKs, increment versionCode and verify certificate/alignment for
   in-place upgrades. Preserve retained emulator login/downloads unless a test explicitly needs isolation.
4. Record unresolved limits and failed experiments. A mock, a successful build and
   a real-device provider/audio test are different evidence classes.
5. Commit checked slices; push only when requested. The current user request authorizes
   a source push after documentation/logging checks, not release publication or production deployment.
