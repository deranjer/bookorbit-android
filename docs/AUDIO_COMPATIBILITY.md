# Audio compatibility without server changes

Audit date: 2026-11-24. Read-only source inspected at
`.local/server-compat-source`, commit `c1dd6b4f1947ff50843a155e12e70f1e7d15f0c7`.
This establishes the local stock source contract, not every deployed server version.
No production files, configuration, libraries or books were changed.

## Existing backend

- `server/src/modules/audiobook/audiobook.controller.ts:45-85` exposes the manifest
  and `GET /api/v1/audiobooks/:bookId/assets/:assetId/content`. The content handler
  verifies asset access, selects MIME from the stored format and sends the
  original file using `createReadStream`, optionally with byte Range/206.
  It has no codec, bitrate, start-time or output-profile query DTO and no encoder.
- `server/src/modules/book/book.controller.ts:401-416` deliberately refuses audio
  on the generic `books/files/:fileId/serve` route. It is not a transcode fallback.
- A source search for transcoding, HLS/m3u8 and conversion controller routes found
  no playback transcode endpoint. The FFmpeg runner under
  `modules/file-write/formats/audio/` belongs to metadata writing, not a temporary
  playback rendition. Triggering metadata edits is not an acceptable workaround.

Changing a URL extension or adding `codec=aac` cannot manufacture a new stream.
Range addresses original bytes, not the time positions of a converted file.

## Client-only path

1. Keep direct play first. Inspect the actual media track, not just `.m4b` or MIME:
   a container can contain different codecs. Retain original asset/file identity.
2. Let Media3 try another installed decoder when initialization fails. This is
   decoder fallback, not transcoding and not a guarantee for unsupported codecs.
3. For missing codecs, bundle a reviewed software decoder supporting those codecs
   and ABIs, subject to licensing/build-size/security review. Decode directly to
   PCM for local playback where possible. Merely enabling extension-renderer mode
   does not install a decoder library.
4. If a destination really needs a different encoded stream (notably Cast), encode
   on Android after decoding, to a bounded temporary cache or a correctly seekable
   local proxy. This needs a working input decoder; MediaCodec/Transformer alone
   cannot decode an otherwise unsupported input. Do not download/transcode an
   entire multi-track edition in response to browsing or synchronization.
5. Keep source time and track identity across retries. Cancel on account/output
   changes, retain owner-bound auth, bound cache/work/concurrency and avoid
   duplicating listening sessions. Do not treat HTTP/auth/network failures as
   decoder incompatibility. Avoid an infinite retry loop.

Local decoding does not establish Cast receiver compatibility. Actual on-device
transcoding is not implemented yet; installed-decoder fallback alone must not be
advertised as the requested full compatibility feature.

## Related annotation audit

`modules/annotation/dto/create-annotation.dto.ts` accepts exactly one of `cfi` or
`pdf`, with text, color, style, note and `bookFileId`. Its geometry DTO is explicitly
PDF. `annotation.service.ts:101-108` rejects `pdf` for a non-PDF file. There is no
dedicated comic word/image geometry in this contract. A comic cannot be submitted
as a PDF annotation. A CFI-based page anchor may allow a text quote to be stored,
but its validation, navigation and cross-client coordinate representation require
separate investigation; this is not evidence of synchronized OCR highlight support.
