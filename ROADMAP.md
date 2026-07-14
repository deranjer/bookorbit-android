# BookOrbit Android — Roadmap

A read of the current client against its own docs and code, turned into a priority list.
Tiers are "orbits" — P0 sits closest to today, P3 furthest out. Originally drafted 10 Jul 2026,
updated 11 Jul 2026 after Chromecast support shipped.

## Already in orbit (shipped)

- **Auth** — username/password + OIDC, self-hosted server URL setup
- **Library** — browse, search, smart scopes, collections, authors, series
- **Dashboard** — continue reading, continue listening, recently added
- **Book detail** — metadata, rating, read status, collection assignment
- **EPUB reader** — foliate.js, CFI progress sync, themes, font settings
- **PDF reader** — separate renderer with its own layout/zoom settings
- **Audiobook player** — Media3/ExoPlayer, background playback, notification controls,
  sleep timer (5–60 min presets + "end of chapter"), Chromecast support, Android Auto browse tree
- **Downloads** — offline files via WorkManager, cached book detail fallback
- **Book Drop** — upload, server-side metadata fetch, review-and-finalize into library
- **Offline write queue** — ratings, read-status, and reading/listening progress made offline are
  queued in Room and auto-flushed by a WorkManager `SyncWorker` on reconnect

## P1 — Next up

No screen exists for these yet, and the gap already shows.

### A Settings screen

There is currently no app-level preferences surface at all. `Theme.kt` already takes a
`darkTheme` parameter but suppresses it and forces dark always — that's a manual
light/dark/system toggle waiting to be wired up. Bundle in: Wi-Fi-only downloads,
cache/storage management (clear image cache, bulk-remove downloads), default playback speed,
and a configurable **download save location** (see below).

**Why:** every one of these is a one-line change blocked only by not having anywhere to put a toggle.

### Surface the update check that's already wired server-side

`AppInfo.updateAvailable` / `latestVersion` are modeled and fetched but nothing in the UI reads
them. A drawer badge or dashboard banner is most of the remaining work.

**Why:** the server already tells the client an update exists; that signal is currently thrown away.

### Save downloads to a user-chosen local storage folder

Today, downloads are **app-private only** — `DownloadWorker` writes every file under
`Context.filesDir/downloads/$bookId` (`feature/downloads/DownloadWorker.kt`), and `DownloadEntity`
persists those as plain absolute path strings (`filesJson`). There's no SAF or MediaStore usage
anywhere in the app. That storage vanishes on uninstall and is invisible to other apps (file
managers, backup tools, other reader apps) — hence the request: let users pick a folder (SD card,
shared `Downloads/`, etc.) via Android's Storage Access Framework
(`ACTION_OPEN_DOCUMENT_TREE` + persisted `DocumentFile`/`Uri` permissions) instead.

**Why:** requested directly by a user.

**Scope (medium lift, ~few days):**
- **New UI**: a folder-picker launcher (SAF `ACTION_OPEN_DOCUMENT_TREE`) plus somewhere to put it
  — either the new Settings screen above, or a control on the existing Downloads screen. No new
  runtime permission is needed for SAF itself.
- **New preference store**: a `DownloadLocationStore` following the existing per-feature
  `preferencesDataStore` pattern (`ReaderSettingsStore.kt`, `PdfReaderSettingsStore.kt`,
  `AudioSettings.kt`), holding the picked tree `Uri` and calling
  `takePersistableUriPermission`.
- **`DownloadWorker` rewrite**: swap `File` I/O for `DocumentFile` / `ContentResolver.openOutputStream(uri)`
  when a folder is configured, falling back to internal storage otherwise.
- **Schema/model change**: `DownloadedFile.localPath` / `DownloadEntity` assume plain file paths
  today — needs a Room migration to store a `Uri` (or a polymorphic path type), plus updates to
  every downstream consumer of `localPath`: `DownloadsRepository` (`localFiles`, `coverPath`,
  `delete`), the reader (`ReaderSource.kt`), the player (`PlayerRepository.kt`,
  `AutoBrowseTree.kt`), and the Chromecast proxy (`CastProxyServer.kt`) — all need to accept
  content `Uri`s, not just `File` paths.
- **Edge cases**: handle a revoked folder permission or an ejected SD card gracefully (fall back
  to internal storage / re-prompt rather than silently failing downloads).

Best scoped as its own item, paired with the Settings screen work above so there's a place to
configure it.

## P2 — Medium-term

Real value, larger lift — worth scoping once P0/P1 land.

- **Reading & listening statistics** — time read/listened, books finished, streaks — if the
  server's progress data supports aggregation, this is a natural dashboard companion.
- **App lock (PIN / biometric)** — relevant given this is a self-hosted personal library that may
  include shared devices.
- **Tablet / foldable layout** — two-pane list + detail for the library, authors, series, and
  collections screens.
- **Barcode / ISBN scan-to-search** — fits naturally next to the existing search screen and Book
  Drop's metadata fetch.

## P3 — Later / exploratory

Real requests, but further out — sequence after the inner orbits settle.

- **A real light theme** — both system modes currently resolve to the same hardcoded dark
  palette; a proper light `ColorScheme` is a design pass, not just a toggle.
- **Home-screen widget** — Continue Reading / Continue Listening as a glanceable widget.
- **Push notifications** — new books added, downloads complete, Book Drop finished processing.
- **Wear OS companion** — playback controls on the wrist for audiobook listeners.
- **Multi-server / account switching** — swap between servers without a full sign-out.
- **Localization** — UI strings aren't currently externalized for translation.

---

Sources: README.md, website/docs/using-the-app.md, and a pass over
`app/src/main/java/com/bookorbit` (`feature/`, `core/model/`).
