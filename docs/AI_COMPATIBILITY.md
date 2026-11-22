# BookOrbit 3.9.0 AI compatibility

Audited against the upstream `v3.9.0` source, not inferred from iOS screenshots.
All paths below are relative to `/api/v1`. Existing JWT and library permissions
still apply; an allowed metadata request does not imply access to AI content.

| Feature | Server contract | Independent client in production |
| --- | --- | --- |
| Story Guide metadata | `GET books/{bookId}/story-guide?fileId={fileId}` | Available subject to ordinary book permissions |
| Build / update | `POST books/{bookId}/story-guide` with explicit `fileId` and `kind` | Requires `library_generate_story_guide` and server configuration |
| Retry / cancel | `POST books/{bookId}/story-guide/{retry,cancel}` | Requires management permission |
| Guide content | `GET mobile/books/files/{fileId}/story-guide?format=1` | Requires app attestation |
| Reading help | `POST mobile/reading-help`, SSE `delta`/`done`/`error` | Requires app attestation; cancellation must close the stream |
| Photo scan | `POST mobile/vision-scan` | Requires app attestation; model call is billable |
| Server read-aloud jobs | `books/{bookId}/read-aloud[-jobs]` and `read-aloud-jobs/{jobId}` | Separate permission/configuration; not the same feature as EPUB playback |

The stock Android attestation policy explicitly permits only official package
names and signing digests. Renaming this client to the official package is not
a legitimate solution. Merely adding a signing digest also does not implement
key registration, nonce handling or signed requests.

## Implemented client slice

- The book's Story Guide panel reads the exact selected text edition's status.
- It probes the content route before enabling a potentially billable generation
  request. A production 403 is shown explicitly; generation/retry are disabled
  when the output cannot be read. Opening the panel never queues generation.
- Accessible deployments can read format 1, explicitly confirm generation/update,
  see job status, retry or cancel, and view chapter-bounded identity lines/recaps.
- No details are displayed before choosing a fully completed chapter. Future
  canonical names are not substituted for names already encountered. Relationships,
  joins and automatic CFI-to-guide-anchor mapping are not implemented yet.
- Provider disclosure and cost confirmation precede build/retry. The client does
  not configure providers, upload a photo or initiate a paid call automatically.

## What remains blocked or incomplete

- Production guide content, reading help and vision scan require an explicitly
  approved compatibility design. No production server or security policy has
  been changed. No unsigned-production bypass has been introduced.
- A companion server change must be opt-in, retain JWT/library authorization and
  protect provider budgets. Native enrollment/signing is implemented in 0.9.7 below;
  hardware acceptance and production deployment still need separate validation and
  approval. An allowlist edit alone is insufficient.
- The initial 0.9.3 panel did not include reading help or photo recognition.
  The 0.9.5 implementation below adds them. Relationship/anchor rendering and
  server read-aloud job controls remain separate work.
- Apple Intelligence is not an Android capability. Do not claim iOS free-form Q&A
  parity without a separately implemented provider/backend.

## Validation

The isolated test server remains in **production mode**, with real attestation
protection: metadata succeeds with JWT while unsigned content returns 403.
Unit tests verify that this denial prevents build/retry requests, wrong edition
IDs/text revisions are rejected, and default chapter bounds reveal no spoilers.
Tests use synthetic payloads; no paid model calls or user-book uploads were made.

Sources: upstream `server/src/modules/story-guides/*controller.ts`,
`server/src/modules/app-attest/app-attest.policy.ts`,
`server/src/modules/reading-help/reading-help.controller.ts`,
`server/src/modules/vision-scan/vision-scan.controller.ts`,
`server/src/modules/read-aloud/read-aloud.controller.ts`, and
`packages/types/src/story-guide*.ts` at tag `v3.9.0`.

## Reading help and photo recognition (0.9.5)

The reader selection toolbar opens an explicit excerpt preview. A user may request
a recap of that excerpt or enter a name/term from it; the app does not scrape and
upload unseen chapters. Requests retain the exact reader `fileId`, use the current
UI language, and optionally request the server's Careful model. Each request needs
provider/cost confirmation. Closing the sheet, leaving the app or pressing Cancel
cancels the live HTTP call. No automatic retry is performed.

The SSE parser handles UTF-8/CRLF, comments and multiline `data`, ignores unknown
event types, and requires an explicit `done` or `error` event. Partial text on a
failed/dropped/cancelled stream is not reported as a completed answer. Wire size,
event count and answer length are bounded to protect the device, not to impose a
paid-tier or download quota. Error codes distinguish provider/configuration,
budget, reader allowance/rate limit, auth and official-app attestation failures.

Search includes a photo-recognition sheet with cover/shelf selection. Android's
photo picker grants access only to the selected image; no broad media or camera
permission is needed. A bounded on-device decoder resizes to at most 1600 pixels,
respects EXIF orientation and produces a fresh JPEG without EXIF/GPS metadata.
The prepared photo is previewed and uploaded only after explicit provider/cost
confirmation. Returned library matches open their authoritative book IDs; this
does not edit metadata, add books or change reading positions.

Before either function can offer a billable request, it checks `/app-info` and
probes its mobile route with **an empty, invalid DTO**, containing no excerpt or
photo. The pinned server's guard runs before validation: production returns 403;
a compatible route reaches DTO validation and returns 400, before the provider
service. Full valid requests recheck access. This is a compatibility check, not
attestation or a bypass. An unexpected response never enables generation.

Source contracts additionally audited: `packages/types/src/{app-info,reading-help,
vision-scan,visual-match}.ts` and the corresponding DTO/controller files at
`v3.9.0`. Unit HTTP tests use MockWebServer synthetic answers and verify real
request cancellation; **no model generated an answer or recognized a real photo**
during these tests. Production-mode local smoke retains both 403 protections.

AI POST transport additionally uses one-shot request bodies with connection retries
and redirects disabled. MockWebServer verifies a 503 `Retry-After: 0`, a disconnect
after receiving a request and a cross-origin redirect do not replay a paid request.
This also applies to Story Guide build/update/retry/cancel, not only SSE/photo calls.

An undeployed opt-in package/certificate policy is prepared in
`tools/server-compat/`; it does not enable unsigned production traffic. Native
Android hardware validation is still required before claiming that this independent
client can use a patched production server. Enrollment/signing implementation follows.

## Explicit Android key enrollment and signing (0.9.7)

Settings includes an optional **Device verification for AI** section. Registration
requires confirmation that the current HTTPS server receives the attestation chain,
including application/device security information. It does not submit books, photos
or provider prompts. No automatic registration or server policy changes occur.

The client obtains a challenge, generates a non-exportable Android Keystore EC P-256
signing key using the challenge's UTF-8 string bytes, rejects software-only keys or
missing certificate chains, and registers leaf-first base64 DER certificates. The
key ID hashes the SEC1 uncompressed public point, not SPKI. Only a matching key ID
with a production receipt activates signing for that exact server/account. Failed
replacement enrollment keeps the previous active key and removes the new key.
Local signing can be disabled without contacting or altering the server. Metadata
is excluded from backup/device transfer; private keys never leave Android Keystore.

The dedicated AI HTTP client materializes bounded immutable JSON bytes once and
signs at the network boundary, after URL rewriting and bearer authorization. ECDSA
SHA-256 covers the protocol version, uppercase method, encoded path/query from the
first `/api/v1/`, millisecond timestamp, lowercase exact-body SHA-256, and a fresh
URL-safe nonce. Empty GET bodies hash zero bytes. The four attestation headers do
not contain provider credentials or private keys. Through 0.9.7 only this AI
transport is signed; the non-AI guarded GET additions in 0.9.8 follow below.

Redirects, connection retries and authenticators are disabled on this AI client;
billable POST bodies remain one-shot. A server restart can lose its in-memory key
registration: `APP_ATTEST_KEY_UNKNOWN` does not trigger enrollment or replay. The
user must register again explicitly and decide whether to retry the original action.
An unlocked, unsupported or untrusted device can legitimately remain unable to use
these routes. No software/emulator trust fallback is added.

Validation distinguishes protocol/transport checks from hardware acceptance. JVM
tests use synthetic EC keys and local HTTPS, including exact UTF-8 wire signatures,
503/disconnect/redirect/401 no-replay and unknown-key behavior. Companion server
checks run on an isolated upstream v3.9.0 checkout, not the production installation.
They do not prove actual Keystore attestation acceptance or provider generation.

### Guarded non-AI reads (0.9.8)

The server also guards `GET dashboard/widgets/reading-streak`,
`GET dashboard/widgets/reading-goal` and `GET smart-scopes/{id}/books`, all used by
this client. These exact main-client GET routes now use the registered key too.
URL/auth preparation precedes signing and each network attempt gets a fresh nonce.
Enrollment uses the separate refresh transport, avoiding a MainClient dependency
cycle. There is still no automatic enrollment.

Unrelated auth, batch dashboard, file delivery, progress and session routes are
untouched, as are image/audio transports. A real local HTTPS regression checks the
three signed reads and that adjacent unguarded routes carry no attestation header.
This does not assert support for every guarded route the server exposes; future
client endpoints need their own contract audit.
