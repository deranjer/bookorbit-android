# Independent Android client

This is an AGPL-3.0 fork of `deranjer/bookorbit-android`, not the official
BookOrbit application. Keep upstream attribution and compatible engine licenses.
Do not import production databases, secrets or books into development fixtures.

## First working increment

1. Reproducible Android build, separate installation identity and OIDC redirect.
2. Browse libraries, search, series/authors, and book details with formatted descriptions.
3. Explicit offline download choices: ebook, audiobook, or both. Opening or syncing
   the library must never start a permanent download.
4. Streaming/background audio, native reader, and retained offline content.
5. Contract checks against an isolated BookOrbit 3.9.0 instance and JVM regressions.

## Following increments

- Preserve exact edition/file identity from the book page into the reader and download queue.
- Versioned offline writes: acknowledging an older write must not clear a newer one.
- Read-along engine spike on synthetic EPUB/SMIL fixtures, online and offline.
  A text locator and a media position are separate coordinates of one activity.
- Server capabilities and clear attestation errors, followed by an opt-in companion
  server patch trusting our release signing identity. Never impersonate the official app.
- Story Guides, spoiler-bounded entries and recaps, streamed reading help and photo scans.
  BookOrbit 3.9.0 currently limits Story Guide generation to English text books.
- No claim of Apple Intelligence support on Android. General question answering
  needs a separately designed Android/server implementation.

## Validation boundaries

An inherited feature is not automatically a verified feature. Track build, unit,
HTTP contract and physical-device validation separately. Native codecs, process
death, long downloads, background playback and offline conflicts need device tests.

Production TrueNAS, BookBridge and the user's reading history are outside this project.

## First increment status (2026-11-22)

- Upstream baseline: 335 JVM tests passed; fdroid debug APK built in Docker.
- Independent package `io.github.monkecode.bookorbitclient` (debug suffix `.debug`)
  and callback `bookorbitclient://oauth2-callback`; local Compose allowlist matches.
- Explicit per-file offline choices, persisted independently of the book metadata;
  retries retain the exact selected IDs and legacy plans remain readable.
- Audio manifest mapping uses authoritative `fileId`; no ambiguous order-based
  substitution. Legacy manifests support unique format/size matches only.
- Local HTTP smoke passed on 3.9.0: password setup/login, list, descriptions,
  paired EPUB/two MP3s, file progress, audio manifest file IDs, Range responses
  and byte comparison against original fixtures.
- The initial 0.9.0 increment required removing an offline download to change its
  selection; the additive 0.9.1 follow-up below removes that limitation.
- Toolchain/dependency upgrade is intentionally separate from the baseline and file
  selection change. Current successful builds still use inherited versions.
- No emulator/device, OIDC provider, long-running audio or process-death validation yet.

## Identity and progress follow-up

- Book detail and the Files tab open an explicit file ID. Multi-edition books get
  a chooser, with offline files identified and listed first. The selected ID reaches
  native EPUB/PDF/comic engines and the server reader; a missing file fails closed.
- Reader and audio writes have unique persisted write IDs. Acknowledgements clear
  only the exact snapshot sent, and network pushes are serialized within each
  repository to prevent local requests overtaking one another.
- PDF/comic page numbers survive queued retries. Database migrations add nullable
  page coordinates and defaulted write IDs without deleting existing rows.
- Reader cache publishes files atomically only after successful transfer/length
  checks; the new cache namespace is separated by server data scope and account.
- Regression coverage includes delayed acknowledgements, same-millisecond writes,
  secondary edition selection, mismatched reading engine, failed partial cache
  writes and offline page-number retries. Device/Room migration execution remains
  a separate validation step.
- CBR/CB7 still use server-rendered pages; downloading their archive does not make
  that inherited reader work offline. CBZ uses the local Foliate path.

Validation of this increment: both fdroid/full debug APKs assembled; 359 JVM tests
passed in each flavor; fdroid debug lint passed (127 warnings, no errors); local
3.9.0 smoke verified independent progress for two EPUB editions, exact manifest
IDs for two MP3 tracks, and byte-range serving for all four files.

## Additive offline downloads (0.9.1)

- Completed downloads offer **Add files**. Already-published files are locked in
  the picker; confirmation only adds explicitly selected content, never removes it.
- The persisted journal, cover, file IDs and local paths survive an extension and
  a failed extension. Each completed file is published immediately, not only after
  the whole book finishes. Readers/player can still use the existing local files.
- Published audiobooks remain in Android Auto's offline catalog during extensions.
- Scheduling awaits WorkManager's acknowledgement. Scheduling errors leave a
  retryable failed plan and an inline UI message, rather than crashing the detail
  screen. Short DB/WorkManager changes finish off the UI thread even if that screen
  closes immediately after confirmation; media transfer itself remains cancellable.
- Each work generation has a token. Progress/checkpoint/failure/completion writes
  are conditional updates, so an obsolete worker cannot overwrite a newer plan or
  recreate a deleted row. Plan replacement uses a Room transaction and snapshot CAS.
- Stable storage IDs retain partial bytes for retries while separate WorkManager
  names prevent a finishing older worker swallowing a new request. New installations
  of a deleted book use a fresh storage ID. Filenames include exact file IDs so two
  editions called `book.epub` cannot overwrite one another.
- SQL tests execute the production migration and guarded update statements against
  SQLite, including stale workers, deleted rows and retained published content.
  This is not a substitute for Android Room/WorkManager device testing.
- The 0.9.0 APKs are archived locally in `.local/apks/43b7b9d/`. The 0.9.1 build
  uses versionCode 901 and the same app identity/debug signing key for an update.
- No production services, media or reading history were used for this increment.

Validation: fdroid/full debug APKs built; 377 JVM tests passed per flavor; both
lint tasks passed with no errors (127 warnings each); all 5 SQLite migration/guard
tests passed. APK signatures verified and matched the archived 0.9.0 certificate.
Android device validation of additions, SAF and interruption remains outstanding.
