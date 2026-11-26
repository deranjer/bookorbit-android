# Roadmap

**Independent BookOrbit Client · updated November 26, 2026**

This is the fork's active roadmap, not a promise that every inherited feature has
been accepted on physical devices. The [detailed plan](docs/CLIENT_PLAN.md) contains
requirements and validation boundaries; [device testing](docs/DEVICE_TESTING.md)
contains the acceptance checklist.

## Current checkpoints

| Version | Delivered slice | Still not implied |
| --- | --- | --- |
| 0.9.12–0.9.15 | Source-built software audio, shared-service online/offline SMIL, exact resume, selection-based listening, authenticated streaming seek | All codecs/devices, audible output, OEM/Bluetooth/Doze acceptance or transcoding |
| 0.9.16–0.9.18 | Statistics periods/history, explicit offline dictionaries, protected server/direct AI connections | Full statistics parity or direct Story Guide |
| 0.9.19–0.9.20 | Owner/file-scoped annotations and durable uncertain-CREATE handling | Server idempotency, reconciliation UI or comic geometry sync |
| 0.9.21 | 305 dictionary catalog pairs; Ollama, OpenRouter and OpenAI-compatible text adapters | Every dictionary package, live provider/model acceptance or multimodal support |
| 0.9.22 | Fullscreen AI setup, compact calendar, visible Story Guide entry and registration diagnostics | Removal of stock server restrictions or user acceptance of the new UI |
| 0.9.23 | Opt-in bounded diagnostic logging and explicit file export | Full logcat/crash collection or a server security bypass |

## Next priorities

### 1. Resolve real-device failures

- [ ] Obtain and interpret the physical phone's registration stage/status/code.
  Keep app trust, hardware attestation, TLS, authentication and provider configuration separate.
- [ ] With separate deployment approval, validate the exact package/certificate
  policy on the user's server version and register the physical phone.
- [ ] Validate photo recognition and Story Guide end to end with explicit consent
  and a configured provider. Do not count mocks or a visible button as acceptance.
- [ ] Re-test streaming headset skip-back with the screen off, including real
  Bluetooth/OEM/Doze behavior. Controlled Range/401/MediaSession tests already pass.

### 2. Complete reading and synchronization

- [ ] Finish narration acceptance: spoken overlays, large EPUB/SAF, OS-kill recovery,
  chapter navigation, device switching and non-overlapping reading/listening activity.
- [ ] Finish annotation file-revision binding and safe uncertain-delivery reconciliation.
- [ ] Resolve comic annotation geometry with a real supported server contract;
  never disguise OCR coordinates as PDF rectangles or EPUB CFI.
- [ ] Validate OCR selection/grouping/filtering and zoom/pan on complex real pages.
- [ ] Finish offline translation and additional explicitly selected translation services.

### 3. Expand AI deliberately

- [ ] Design direct Story Guide/Graph generation: bounded chapter processing,
  structured validation, spoiler boundaries, cancellation, storage and import/sync compatibility.
- [ ] Design direct photo support with actual multimodal capability checks and
  exact library matching. Text API compatibility alone is insufficient.
- [ ] Add per-feature model choices only for implemented tasks; no silent route fallback.
- [ ] Complete statistics parity only where the server provides reliable contracts.

### 4. Finish UX and compatibility acceptance

- [ ] Obtain user acceptance of AI setup, calendar and Story Guide discoverability.
- [ ] Agree visual style variants, then implement persistent preview/reset and
  consistent shared design tokens without overwriting reader preferences.
- [ ] Complete large-font, narrow/tablet/foldable, RTL and accessibility checks.
- [ ] Finish edition metadata presentation, full UI localization review, OIDC,
  non-destructive migrations, download interruption and SAF permission-revocation checks.
- [ ] Resolve offline CBR/CB7 or retain an explicit server-rendering limitation.

## Boundaries that do not change

No production server edits without separate approval. No Premium gates or book
quotas. No permanent downloads caused by browsing/sync. Preserve exact edition/file
identity and source clocks. Do not duplicate queues, sessions, events or annotation
CREATEs after ambiguous delivery. Never replace missing contracts with guessed data.

The [historical upstream roadmap](docs/history/UPSTREAM_ROADMAP.md) is retained for
attribution and context; its old status and exploratory ideas are not current commitments.
