# Proposed opt-in Android attestation policy (BookOrbit 3.9.0)

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

These parser checks do not replace full server tests, real hardware attestation
or an end-to-end provider test. Native client enrollment/signing is the next work
unit; merely applying this policy patch does not enable the currently unsigned
AI requests. An unlocked/unsupported phone may legitimately remain unable to
attest. No insecure fallback is proposed.
