# BookOrbit Android — Roadmap

A read of the current client against its own docs and code, turned into a priority list.
Tiers are "orbits" — P0 sits closest to today, P3 furthest out. Originally drafted 10 Jul 2026,
updated 11 Jul 2026 after Chromecast support shipped, updated 29 Jul 2026 after the
download-location and update-check items shipped, re-prioritised 8 Oct 2026 after a feature-parity
review against the web client (demo.bookorbit.app) and the server API.

## Already in orbit (shipped)

- **Auth** — username/password + OIDC, self-hosted server URL setup
- **Library** — browse, search, smart scopes, collections, authors, series
- **Dashboard** — continue reading, continue listening, recently added
- **Book detail** — metadata, rating, read status, collection assignment
- **EPUB reader** — foliate.js, CFI progress sync, themes, font settings
- **PDF reader** — separate renderer with its own layout/zoom settings
- **Audiobook player** — Media3/ExoPlayer, background playback, notification controls,
  sleep timer (5–60 min presets + "end of chapter"), Chromecast support, Android Auto browse tree, chapter list and
  chapter progress bar, reopens the last audiobook on launch
- **Downloads** — offline files via WorkManager, cached book detail fallback, user-chosen local
  storage folder via Storage Access Framework (falls back to app-private storage)
- **Book Drop** — upload, server-side metadata fetch, review-and-finalize into library
- **Offline write queue** — ratings, read-status, and reading/listening progress made offline are
  queued in Room and auto-flushed by a WorkManager `SyncWorker` on reconnect
- **Settings screen** — appearance (system/light/dark, with a real light `ColorScheme`, not just
  system-dark repeated), Wi-Fi-only downloads, image cache / bulk downloads clearing, default
  playback speed, and an About section showing app/server version and update availability
- **In-book search** — search the open book, results by chapter with matches outlined in the page
- **Highlights and notes (EPUB, MOBI, AZW3, FB2)** — select text to highlight in five colours, add notes, tap a
  highlight to recolour/delete, browse all highlights from the reader; synced with the web annotations hub; work offline and sync when back online
- **Reading sessions** — reading and listening time reported to the server (`source: android`) so mobile
  activity counts toward web streaks, goals and statistics; queued offline in Room and flushed by `SyncWorker`
- **Update check** — `AppInfo.updateAvailable`/`latestVersion` surfaced via a drawer badge on
  Settings, a drawer footer line, and the Settings About section; refreshed on every app
  foreground while signed in

## Parity review (8 Oct 2026)

The remaining items below were originally chosen from this app's own docs. Comparing against the web
client and the server (`bookorbit/bookorbit`) shows the largest gaps are not listed there. The server
already exposes everything the P0/P1 items need, and `android` is an accepted reading-session source
(`CLIENT_READING_SESSION_SOURCES = ios | watchos | android`).

Server endpoints available but unused by this app:

- `books/{bookId}/annotations` (GET/POST/PATCH/DELETE; CFI or PDF rect) — highlights and notes,
  three-way synced with Kobo, KOReader and web.
- `books/{bookId}/bookmarks` and `audiobooks/{bookId}/bookmarks`.
- `user-statistics/*` and `dashboard/widgets/*` (streak, goal, highlight of the day, Reading DNA,
  neglected gems, monthly challenge, …).
- `books/{id}/series-books` (declared in `ApiService` but not called).

## P0 — Parity core

- **Reader bookmarks and PDF highlights** — EPUB-family highlights/notes shipped (see below); PDF annotations
  (page rectangles) and bookmarks are still missing.
- **Book detail depth** — files/editions with per-format progress, narrators, tappable author and
  series, review, external ratings.
- **Fix: synopsis renders raw HTML** (`<br />`, `<i>`), double status-bar inset on detail top bar,
  no retry UI on paging errors, 3-column grid hard-coded.

## P1 — Targeted refresh

- Dashboard: streak, goal and a Continue card shipped; highlight of the day and discover still to do.
- Reading stats: shipped under You (totals, streak/goal, 30-day chart, 12-week heatmap, source split). Completion timeline and pace charts still to do.
- Navigation: shipped (Home/Library/Search/Notes/You, no drawer). Statistics screen under You still to do.
- Theme: shipped (AA-contrast blue palette in light and dark, tonal surfaces, optional Material You in Settings).
- Tablet / foldable: navigation rail and readable-width content shipped; a true two-pane list + detail (library next to the open book) is still to do.
- Player: chapter list, chapter progress, custom and per-book speed and bookmarks shipped (bookmarks are online-only).
- Comics: CBR/CB7 now open in a native comic reader (shipped); CBZ still uses the foliate reader without comic controls, and CBR/CB7 need a server connection (no offline).

## P2 — Medium-term

Real value, larger lift — worth scoping once P0/P1 land.

- **TTS and dictionary in the reader** — `tts.js` / `dict.js` are vendored but unwired.
- **App lock (PIN / biometric)** — relevant given this is a self-hosted personal library that may
  include shared devices.
- **Barcode / ISBN scan-to-search** — fits naturally next to the existing search screen and Book
  Drop's metadata fetch.

## P3 — Later / exploratory

Real requests, but further out — sequence after the inner orbits settle.

- **Home-screen widget** — Continue Reading / Continue Listening as a glanceable widget.
- **Push notifications** — new books added, downloads complete, Book Drop finished processing.
- **Wear OS companion** — playback controls on the wrist for audiobook listeners.
- **Multi-server / account switching** — swap between servers without a full sign-out.
- **Localization** — UI strings aren't externalized (no `stringResource` use); the web client is
  already translated via Crowdin. Externalizing strings early is cheap.

---

Sources: README.md, website/docs/using-the-app.md, and a pass over
`app/src/main/java/com/bookorbit` (`feature/`, `core/model/`).
