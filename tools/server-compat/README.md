# Proposed opt-in Android attestation policy (BookOrbit 3.9.0)

For the administrator walkthrough, including APK identity verification, source
changes, rebuilding and phone registration, see
[Allow this client on your own server](../../README.md#allow-this-client-on-your-own-server).

## Minimal hardcoded policy experiment (2026-11-26)

The user requested testing the smaller alternative: add one exact identity directly
to `androidKeyAttestPolicy().apps`, with no environment parser or verifier changes.
`minimal-debug-apk-policy.patch` does this for the archived 0.9.21 debug APK only:
package `io.github.monkecode.bookorbitclient.debug`, signing certificate SHA-256
`9f8472c84b54b5033314fb3cd26ac949a2b7d818a78530263c3e8dc955c64f0d`.
This is a public certificate fingerprint, not a phone key or provider token.
Do not add the digest to the official package's list or set development/unsigned
acceptance. A different build/signing certificate requires checking its own pair.

Tested in a fresh isolated checkout of v3.9.0 at
`c1dd6b4f1947ff50843a155e12e70f1e7d15f0c7`. The initial upstream policy test
failed because it asserts exactly one allowed app; its expected array was extended
with the same exact identity, without removing existing assertions.
All **109 tests** then passed (100 upstream plus 9 in
`minimal-android-policy.test.ts`). Targeted ESLint and `nest build` passed
(1785 files compiled, zero type-check issues).

Tests prove: absent entry rejects the identity; the added entry permits synthetic
attestation and registration, followed by verification of a signed request with the
vision-scan path. Wrong package/digest, software keys, unverified boot, wrong challenge,
untrusted roots, request replay, changed body and another user remain rejected.
The test trusts a synthetic certificate root only inside its fixture; production
roots and hardware checks are not changed. This is **not** an HTTP photo scan,
real Google-rooted hardware enrollment, or successful inference test. No live
server, database, books, provider or physical phone was used or changed.

Evidence: `.local/attestation-minimal-test-20261126/{attestation-results.json,evidence.json,minimal-policy.patch}`.
The code path works under tested prerequisites. Actual phone acceptance still
requires separately approved deployment on the matching server version and
explicit device registration. No deployment was performed.

## Configurable companion alternative

**Not deployed. No production server was changed.** This is a companion patch,
not a way to impersonate the official app or make unsigned production requests.
It must be reviewed and separately approved before any production deployment.

The stock policy trusts only official Android packages/certificates. This patch
adds explicitly configured additional package + signing-certificate SHA-256 pairs.
Default configuration adds none. Invalid configuration fails closed at startup.
It does **not** change trusted roots, key verification, verified boot, challenge
consumption, nonce replay protection, library/JWT authorization or provider budgets.
Production still refuses software/emulator/self-signed attestation chains.

On a separate upstream `v3.9.0` checkout (never directly inside a live container):

1. Copy `independent-android-apps.ts` into `server/src/modules/app-attest/`.
2. Review and apply `android-app-policy.patch` with `git apply --check` first.
3. Run the server's attestation/controller tests and production build.
4. Only after separate deployment approval, configure an exact pair, for example:

   ```text
   BOOKORBIT_INDEPENDENT_ANDROID_APPS=[{"packageName":"io.github.monkecode.bookorbitclient","sha256":["<64 hex characters: actual APK signing certificate SHA-256>"]}]
   ```

   The placeholder is deliberately invalid. Do not use `any`, `*`, a public-key
   fingerprint instead of a signing-certificate digest, or an unrelated APK's pin.
   Include a second exact digest during an intentional signing-key rotation only.

5. The client still must explicitly obtain a server challenge, generate an Android
   Keystore P-256 key with that challenge, register its DER certificate chain, and
   sign every guarded request with fresh timestamp/nonce and exact body hash.
   The signing key stays in the device Keystore, not the server or this repository.

Parser regression checks (Node with native TypeScript stripping):

```sh
node --test tools/server-compat/test-policy.mjs
```

Native client 0.9.7 implements explicit Settings enrollment and guarded request
signing. This does not mean a device or stock server accepts it. An unlocked or
unsupported phone may legitimately remain unable to attest. No insecure fallback
is proposed; provider calls and production deployment still require approval.

### Isolated companion validation

Use a disposable v3.9.0 checkout (upstream commit
`c1dd6b4f1947ff50843a155e12e70f1e7d15f0c7`), not a live installation. Copy the
parser and `independent-android-contract.test.ts` alongside the upstream app-attest
sources, then apply the policy patch. Install the pinned pnpm lockfile. Commands:

```sh
pnpm --filter server exec vitest run src/modules/app-attest
pnpm --filter server build
pnpm --filter server exec eslint src/modules/app-attest/independent-android-apps.ts src/modules/app-attest/independent-android-contract.test.ts src/modules/app-attest/app-attest.policy.ts
```

The additional contract test requires `BOOKORBIT_ANDROID_CONTRACT_FILE` pointing
to the absolute JVM-generated `app/build/reports/android-attestation-contract.json`
in this client checkout. That fixture contains only a synthetic public key, payload
and signature, never private material or a real attestation chain. It cross-checks
Kotlin canonical bytes, SEC1 key ID and DER assertion against the upstream verifier,
including rejection after a body mutation. It is not a hardware enrollment test.

Recorded 0.9.7 checks on the disposable checkout: production server build passes,
102 attestation/contract tests pass (100 upstream plus 2 compatibility tests), and
targeted ESLint/Prettier pass. This patch has not been deployed to either the
running fixture server or production. Real hardware acceptance remains unverified.
