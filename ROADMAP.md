# BookOrbit Android — Roadmap

A read of the current client against its own docs and code, turned into a priority list.
Tiers are "orbits" — P0 sits closest to today, P3 furthest out. Originally drafted 10 Jul 2026,
updated 11 Jul 2026 after Chromecast support shipped, updated 29 Jul 2026 after the
download-location and update-check items shipped.

## Already in orbit (shipped)

- **Auth** — username/password + OIDC, self-hosted server URL setup
- **Library** — browse, search, smart scopes, collections, authors, series
- **Dashboard** — continue reading, continue listening, recently added
- **Book detail** — metadata, rating, read status, collection assignment
- **EPUB reader** — foliate.js, CFI progress sync, themes, font settings
- **PDF reader** — separate renderer with its own layout/zoom settings
- **Audiobook player** — Media3/ExoPlayer, background playback, notification controls,
  sleep timer (5–60 min presets + "end of chapter"), Chromecast support, Android Auto browse tree
- **Downloads** — offline files via WorkManager, cached book detail fallback, user-chosen local
  storage folder via Storage Access Framework (falls back to app-private storage)
- **Book Drop** — upload, server-side metadata fetch, review-and-finalize into library
- **Offline write queue** — ratings, read-status, and reading/listening progress made offline are
  queued in Room and auto-flushed by a WorkManager `SyncWorker` on reconnect
- **Settings screen** — appearance (system/light/dark, with a real light `ColorScheme`, not just
  system-dark repeated), Wi-Fi-only downloads, image cache / bulk downloads clearing, default
  playback speed, and an About section showing app/server version and update availability
- **Update check** — `AppInfo.updateAvailable`/`latestVersion` surfaced via a drawer badge on
  Settings, a drawer footer line, and the Settings About section; refreshed on every app
  foreground while signed in

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

- **Home-screen widget** — Continue Reading / Continue Listening as a glanceable widget.
- **Push notifications** — new books added, downloads complete, Book Drop finished processing.
- **Wear OS companion** — playback controls on the wrist for audiobook listeners.
- **Multi-server / account switching** — swap between servers without a full sign-out.
- **Localization** — UI strings aren't currently externalized for translation.

---

Sources: README.md, website/docs/using-the-app.md, and a pass over
`app/src/main/java/com/bookorbit` (`feature/`, `core/model/`).
