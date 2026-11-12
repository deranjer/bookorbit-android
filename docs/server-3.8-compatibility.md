# BookOrbit server 3.8.0 compatibility

Reviewed on 2026-11-11 against the [3.8.0 release](https://github.com/bookorbit/bookorbit/releases/tag/v3.8.0), comparing the 3.7.0 and 3.8.0 source trees.

## Findings and fixes

Two existing incompatibilities, already present in server 3.7.0, were found during this review:

| Android feature | Problem | Client change |
| --- | --- | --- |
| Dashboard and statistics streak/goal | Individual widget GET routes require App Attest; failures were silently hidden. | Use the session-authenticated `POST /dashboard/widgets/batch`, requesting both widgets together. Decode each result independently so a failed widget does not hide the other. |
| Smart-scope book lists | `GET /smart-scopes/:id/books` requires App Attest. | Use the session-authenticated `POST /smart-scopes/:id/books/query`, preserving the scope ID and pagination. |

Both changes use the server's standard query APIs. On 404/405 only, retain the legacy routes for older servers. Authentication, authorization, rate-limit, and server failures do not trigger fallback.

Server 3.8 adds Android key attestation, but its production policy accepts `app.bookorbit.reader` and pinned official signing certificates. This project uses `com.bookorbit`; adding attestation requests would not make it eligible. Do not change the application ID or signing identity to attempt compatibility.

## Other release changes checked

- **API keys and authentication:** the Android app uses interactive JWT sessions from password/OIDC login and refresh cookies. The API-key policy guard leaves session requests unchanged; JWT bearer extraction and session validation remain supported. No API-key migration is needed.
- **Reading progress:** 3.8 explicitly removes App Attest requirements from file-progress routes. Reading sessions, annotations, ebook bookmarks, audiobook playback state/bookmarks, manifest, and asset streaming remain accessible through ordinary authenticated requests.
- **Full original guard overlap:** commit `b44d902` guarded five calls made by this native app: file-progress GET/POST, reading-streak GET, reading-goal GET, and smart-scope books GET. Upstream removes the progress guards in 3.8; this client change replaces the other three calls. The native app does not call the other guarded routes: achievement celebrations, notebook views, global `/search` (it uses `/books/search`), advanced activity statistics (it uses summary/daily/source routes), chunked `/uploads` (it uses `/book-dock/upload`), annotation-color preferences, captioned TTS, watch downloads, or the other guarded dashboard widgets. This inventory covers native requests; embedded server-reader behavior remains part of the live smoke-test boundary below.
- **Library access:** the release centralizes library scoping for sessions and API keys. Android continues using the signed-in user's permissions and library access.
- **Models:** the release's new authentication-method string and app-info feature do not require Android model changes; unmodeled fields are ignored. Existing audiobook response contracts are unchanged.
- **Reader assets:** no foliate, bridge, or reader-theme files changed between these tags; no asset copy is required.
- **Scanner/metadata, duplicate scans, TorBox, translations:** no Android request or response contract change was identified. TorBox is a server-side request-download integration, separate from Android's own downloads.

## Validation boundaries

Regression tests cover widget batch decoding, partial/malformed results, legacy fallback, cancellation, preservation of permission errors, and smart-scope pagination/fallback.

Local checks passed for both `fullDebug` and `fdroidDebug`: all unit tests (including eight new regression tests per flavor), Android lint, and debug APK assembly. `git diff --check` also passed.

This is a source-contract review and local Android validation, not a live end-to-end test against a configured 3.8.0 server. A device smoke test should cover password/OIDC login and refresh, the dashboard and stats widgets, smart-scope pagination, ebook progress/highlights/bookmarks, audiobook seek/resume, and offline download/reconnection sync.

## Pinned server sources

- [Dashboard routes](https://github.com/bookorbit/bookorbit/blob/v3.8.0/server/src/modules/dashboard/dashboard.controller.ts) and [batch types](https://github.com/bookorbit/bookorbit/blob/v3.8.0/packages/types/src/dashboard.ts)
- [Smart-scope routes](https://github.com/bookorbit/bookorbit/blob/v3.8.0/server/src/modules/smart-scope/smart-scope.controller.ts)
- [Android attestation policy](https://github.com/bookorbit/bookorbit/blob/v3.8.0/server/src/modules/app-attest/app-attest.policy.ts)
- [API-key policy guard](https://github.com/bookorbit/bookorbit/blob/v3.8.0/server/src/common/guards/api-key-policy.guard.ts)
- [Progress-route change](https://github.com/bookorbit/bookorbit/commit/2ddae7c385c3e1a819f5a54db0d938c713c66f3d)
