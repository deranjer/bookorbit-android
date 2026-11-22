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
  protect provider budgets. For native attestation, implement client registration
  and per-request signing, not just an allowlist edit. Production deployment needs
  separate approval.
- Reading-help SSE UI, photo selection/upload, relationship/anchor rendering and
  server read-aloud job controls are not shipped by this initial panel.
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
