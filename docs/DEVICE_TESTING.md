# Verification and device acceptance

**Independent BookOrbit Client · November 26, 2026**

## Real-server screenshot session: November 27, 2026

- 0.9.23 full debug, Android API 36 emulator, isolated app data, English app locale.
- With explicit owner authorization, signed in to the owner's self-hosted server
  and browsed library/book metadata. Published only the library and book-detail
  screens, with system bars cropped; no account name, endpoint or credentials shown.
- Opened Dungeon Crawler Carl → Story Guide. The actual result was **Server access
  restricted. Check device registration in Settings.** No graph content, character
  relationships or spoiler-bound rendering was accepted on this deployment.
- Did not open the reader, start playback, download a book, enroll a device key,
  generate/update a guide, submit a photo/model request or modify server policy.
  Authentication and ordinary metadata reads were the only server interactions.
- Source inspection confirms the panel has no persistent offline graph cache.
  Real-server graph access and offline graph acceptance remain open; do not count
  the screenshot of the entry card as evidence that graphs or AI inference work.
- Raw UI evidence remains ignored under `.local/device-screenshots/`; only reviewed
  English screenshots are published under `docs/images/`.

### Requested follow-up: statistics and an opened book

- Captured Reading stats in English before opening the book. The screenshot account
  had zero activity; the image preserves those values without seeded or fabricated data.
- At the owner's subsequent request, opened Dungeon Crawler Carl in the native EPUB
  reader, dismissed the first-run system/tap-zone hints, and showed the reader toolbar
  on the cover. Did not turn pages, select text, annotate or invoke AI; exited the reader
  after capture. Opening/closing the book may record a position or reading session for
  this account; this follow-up is not covered by the initial metadata-only boundary above.
- Published reviewed statistics and reader images without account/endpoint labels or
  image EXIF. The reader screenshot demonstrates an opened EPUB cover and controls,
  not spoken narration, offline acceptance or successful Story Guide access.

### Requested follow-up: interior EPUB page and manga

- Replaced the README cover-reader reference with an interior Dungeon Crawler Carl
  screenshot, counting ten reflowed screen pages from the cover at the current settings.
  This is not a print-edition page number. Edge taps stopped advancing at the opening
  chapter; horizontal swipes advanced the text. The failed tap attempts are not counted
  as successful navigation. This tap behavior needs a separate regression investigation.
- Opened JoJo's Bizarre Adventure: Part 1, Phantom Blood, Vol. 3 in the native CBZ
  reader and captured its displayed page **6 / 324**, including the reader controls.
  The archive loaded through the normal temporary-cache path; no permanent offline
  download was requested and no OCR/AI operation was started.
- These requested reading actions can update this screenshot account's positions and
  sessions. Reviewed images omit account/server details and contain no EXIF. No server
  trust-policy changes or protected AI acceptance are implied by these screenshots.

This is the current English acceptance checklist. The complete dated test journal
is preserved in [the source archive](history/DEVICE_TESTING.source-2026-11-26.md).
Use the [plan](CLIENT_PLAN.md) for implementation status and the [README](../README.md)
for building, server trust and log export.

## Evidence levels

| Evidence | What it establishes | What it does not establish |
| --- | --- | --- |
| Build / lint / JVM / Node / SQLite | Compilation, contracts and targeted regressions | A correct physical UI, Android permissions or actual audio |
| Isolated Android instrumentation | Actual framework/Room/Keystore/Media3 behavior in the stated synthetic scenario | Every OEM, phone, codec, Bluetooth accessory or real provider |
| Retained-emulator UI | Navigation/layout/state with existing synthetic downloads and login | User-book quality, audible output or production acceptance |
| Physical-device acceptance | Only the exact recorded phone, Android, APK, server and scenario | Untested configurations or broader feature parity |

Record the phone model, Android version, application version/flavor, server version,
steps, expected/actual behavior, network state and a safe error code. Do not send
passwords, tokens, private books/photos, full raw logcat or unrestricted server logs.

## Latest completed application checkpoint: 0.9.22

- Both debug APKs and lint tasks passed; **622 JVM tests per flavor**, **31 Node**
  checks and **14 Python** checks passed. The signing certificate was unchanged,
  16 KiB ZIP alignment was verified and Room schema stayed 11.
- AI security/server-route UI: two cases × five,
  `20261126T184935467749Z`. Custom Ollama/compatible mock HTTP: two × two,
  `20261126T185017700172Z`. No real paid provider/model call.
- Both groups also passed twice at **1080×1920** and **1920×1080**; original
  **1080×2400** restored. Tests explicitly close IME before coordinate-sensitive
  route/HTTP-checkbox actions, rather than clicking during keyboard motion.
- Heatmap/statistics: three cases × two, `20261126T185037358610Z`; 18 dp cells,
  seven rows totaling 150 dp, 7/30/84 days, widths 180/320 dp and final-day scrolling.
- Photo-access refusal/retry: one × two, `20261126T185050332559Z`, without uploading
  an image. A picker remains available during denial; submit remains gated.
- Persistent safe registration error: one × two, `20261126T185056859140Z`, with stage,
  HTTP 403 and `APP_ATTEST_INVALID`, not original exception/certificate/server text.
- Story Guide entry: one × two, `20261126T185102259994Z`, plus server/mock regression.
  Actual stock fixture panels still show access restrictions, not successful generation.
- Retained settings/reading/downloads were not cleared. No real token entered,
  AI configuration saved, device registration submitted or photo/model call made.
- Evidence: ignored `.local/apks/working-0.9.22-20261126/build-info.json`,
  `.local/contracts/client-polish-0922.json` and `ai-settings-responsive-0922.json`.
  FLAG_SECURE makes AI screenshots black; hierarchy/tests establish geometry without
  disabling protection. Library/book/calendar screenshots use synthetic fixtures.

## Latest checkpoint: diagnostic logging in 0.9.23

The user requested a Settings switch, reproducible logging and a downloadable file.
This slice was checked separately from 0.9.22:

- [x] Default off; opening Settings/export does not enable it.
- [x] Confirmed switch persists only on this device; the off preference is persisted too.
- [x] Record safe HTTP outcome/category, attestation stage/code and player error fields.
  Verify no URL/query/header/token/password/cookie/certificate/book/photo/body or
  arbitrary exception text reaches exported bytes.
- [x] Logging does not consume/change response bodies, replay requests, weaken TLS,
  alter attestation or send extra network calls.
- [x] Two rotating files stay within 512 KiB total; event queue is bounded (256 slots).
- [x] Turning off and clearing invalidate queued old write tickets; clear removes
  existing records without changing downloads, account state or reading history.
- [x] Explicit export through Android CreateDocument writes a readable text file.
  The picker callback ignores cancellation and has no network/upload action.
- [x] IO/storage problems set a visible failure state without breaking the HTTP
  response. Queue saturation uses the same indicator by code inspection.
- [x] Logs use noBackupFilesDir; cloud/transfer rules exclude the opt-in preference.
  This is a configuration audit, not a real cloud-backup restoration test.

Verification: both APKs/lints, **627 JVM tests per flavor**, **34 Node checks** and
**14 Python checks** passed. New diagnostic instrumentation: **two cases × three**,
`20261126T193327627718Z`, covering opt-in/reopen/privacy/export bytes/disable/clear
and storage failure without changed HTTP bodies or extra requests.

Manual retained-emulator workflow used the real Settings switch/consent and Android
CreateDocument picker to save a readable file in Downloads. An HTTP-only fixture
enrollment was refused before networking and logged only CONNECTION/HTTPS. Logging
was turned off before export; Clear removed only internal records, not the exported
copy. Evidence: `.local/contracts/diagnostic-export-ui-0923.json` and the private
`diagnostic-export-0923.txt`. No real key enrollment, photo or provider request occurred.

Final regressions passed: custom AI (two cases twice), Keystore/server route,
dictionary safety, statistics, registration panel, streaming seek, local native
narration and Auto. Exact paths and hashes are in the private
`.local/apks/working-0.9.23-20261126/build-info.json`. Physical/OEM/SAF-provider
acceptance and a full crash/logcat recorder are not claimed.

## Physical acceptance checklist

### Installation, login and ownership

- [ ] Install an update signed with the same certificate without clearing data;
  verify login, settings, downloaded editions and Room migration behavior.
- [ ] Test local-password and OIDC login using this fork's callback. Exercise
  expiry, logout/relogin as the same user, another account and another server.
- [ ] Late requests/refreshes must not affect the new owner or expose another user's cache.

### Editions and offline transfers

- [ ] Two text editions keep separate file identities and positions; Read reopens
  the selected available edition and its arrow still opens the chooser.
- [ ] Text + 75 audio tracks gives two edition choices. Selecting audio includes
  all exact ordered IDs; 74 downloaded tracks leaves only one missing track.
- [ ] Add audio/another text edition without removing existing files or positions.
  Existing text remains readable while an extension fails or is interrupted.
- [ ] Retry retains selected files/partial bytes. A deleted or superseded worker
  must not recreate its row or overwrite a new plan.
- [ ] Repeat with SAF storage, revoked permissions, low free space and long transfers.
- [ ] Browsing/sync/chooser opening never starts a permanent download.

### Reading, selection and OCR

- [ ] Test dark/light/system modes, publisher CSS, SVG/illustrations, live appearance
  changes, columns, paged/continuous mode and rotation on real WebViews.
- [ ] Compact selection actions stay outside the selection and inside safe bounds,
  with usable targets at 140% fonts, narrow/tablet widths and RTL.
- [ ] OCR tap/hold/range grouping works inside the real zoomable reader. Test adjacent
  bubbles, short words, handwriting, complex/blank artwork and long sessions/memory.
- [ ] Panning tracks screen-space finger movement at multiple zoom levels; double
  tap and manga RTL remain correct. No false promise of perfect bubble detection.
- [ ] Local CBZ opens offline. CBR/CB7 server-rendered limits are visible, not a hidden
  failure after claiming the downloaded archive can be read offline.

### Audio and read-along

- [ ] Stream actual user-supported formats with play/pause/seek, track boundaries,
  rate, sleep timer and audio-focus changes. Separate decode errors from network errors.
- [ ] Reproduce **streaming headset skip-back with screen off** on the original
  phone/server/headset. Test actual Bluetooth, OEM power settings and Doze.
- [ ] Verify platform-first decoding and one fallback with the same source/item/clock;
  then re-check the next track. Do not call this transcoding or universal codec support.
- [ ] Use spoken SMIL with multiple clips/assets/chapters and an unnarrated interlude.
  Check highlighting, manual navigation, natural transitions and end-of-book.
- [ ] Selection → ⋮ → Listen from here starts only at a uniquely validated narrated
  fragment; no action for unnarrated text, ordinary audio or ambiguous anchors.
- [ ] Download the edition explicitly, disable network, background/return and reopen
  after force stop. Play must remain inert until explicit action and restore exact clocks.
- [ ] Test an OS low-memory kill separately from Force stop/Recents removal, plus SAF,
  large EPUB extraction latency/space, Android Auto and Cast hardware.
- [ ] Verify actual audible output, headphone routing and text/spoken synchronization.
  A silent fixture and a PLAYING MediaSession are not evidence of these.

### AI and server trust

- [ ] Keep stock server behavior as a control. HTTPS/account login alone does not
  grant app trust. Record exact enrollment stage/status/code before proposing a fix.
- [ ] Apply a reviewed exact package/certificate policy only with separate deployment
  approval and a rollback plan. Keep production security/hardware/ownership checks.
- [ ] Register a physical phone, including failure/unsupported cases, and test a real
  protected route. Synthetic roots or a successful source build are insufficient.
- [ ] Confirm server provider/model/permissions/budget separately. Photo upload and
  guide build/update require explicit data/cost confirmation, not opening a panel.
- [ ] Test actual photo recognition and authoritative library matches; verify no
  metadata, books or history are modified by the search.
- [ ] Test direct selected-text help, endpoint changes, optional tokens, HTTP opt-in,
  cancellation and no automatic route fallback/replay with an approved provider.
- [ ] Test Story Guide identity joins/relationships/recaps at explicit completed
  chapter bounds, searching and moving the bound backward. No future-name fallback.
- [ ] Direct Story Guide/photo capabilities remain unimplemented; do not infer them
  from the presence of OpenAI-compatible text settings.

### Annotation and statistics integrity

- [ ] Check account/file/revision isolation, stale GETs, offline edits/deletes and
  delayed acknowledgements against actual supported annotations.
- [ ] An uncertain CREATE retains its text/note and does not automatically POST again
  after lost responses/reopen. Do not recreate it until server delivery is reconciled.
- [ ] Do not test unsupported comic OCR geometry by pretending it is PDF or EPUB CFI.
- [ ] Verify account-timezone daily totals and UTC ISO-week history around boundaries.
  Failures must not become zero activity. No inferred READ/LISTEN or double-counted totals.

## Selected historical evidence

| Scenario | Established evidence | Boundary |
| --- | --- | --- |
| Offline native narration | `20261126T102520179598Z`; retained background transition and **849 ms** force-stop restore | Not low-memory kill, audibility or Bluetooth |
| Screen-off stream seek | `20261126T105520838194Z`; three cases × five with Range and one injected 401 refresh | Not the physical user's headset/OEM |
| Four-ABI software audio | `20261125T092622305464Z`; four cases twice with actual JNI seek/queue and injected decoder failure | Not all codecs, receiver compatibility or battery acceptance |
| Four real dictionaries | `20261126T164814907358Z`; EN↔RU, FR→EN, ES→EN imported/searched offline in 18.379 s | Not every one of 305 catalog entries |
| Uncertain annotation CREATE | `20261126T131453476161Z`; disk Room reopen after POST/lost response, no duplicate flush | Not server idempotency or historical delivery reconciliation |
| Minimal server trust patch | v3.9.0: 109 attestation tests, lint and production build | Synthetic test roots, no deployed hardware enrollment |

Evidence paths are local/private unless a sanitized synthetic screenshot is explicitly
published. Full logs, tokens, signing keys, test-account credentials and `.local/`
are never part of a source push.
