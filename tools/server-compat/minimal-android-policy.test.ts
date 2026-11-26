// Copy alongside upstream app-attest tests after adding the exact APK identity to policy.apps.
// Synthetic certificate roots are test-only; no physical device acceptance is claimed.
import { readFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';

import type { RequestUser } from '../../common/types/request-user';
import { AndroidKeyAttestService } from './android-key-attest.service';
import { verifyAndroidAttestation } from './android-key-verifier';
import { VERIFIED_BOOT_STATE } from './android-key-description';
import { androidKeyAttestPolicy } from './app-attest.policy';
import { AppAttestService, APP_ATTEST_HEADERS, androidAttestClientData, appAttestBodySha256 } from './app-attest.service';
import { AppAttestStore } from './app-attest.store';
import { AttestationRevocations } from './attestation-revocations';
import { androidChain, androidAssertion, type AndroidChainSpec } from './test-fixtures/android-attestation-test-helpers';

const packageName = 'io.github.monkecode.bookorbitclient.debug';
const digest = '9f8472c84b54b5033314fb3cd26ac949a2b7d818a78530263c3e8dc955c64f0d';
const user = { id: 7 } as RequestUser;

function fixture(spec: AndroidChainSpec = {}) {
  const chain = androidChain({ packageName, signatureDigest: Buffer.from(digest, 'hex'), ...spec });
  const input = { keyId: chain.keyId, chain: chain.chain, challenge: Buffer.from(chain.challenge) };
  const production = androidKeyAttestPolicy('production');
  const testPolicy = { ...production, trustedRoots: [chain.rootPem] };
  return { chain, input, production, testPolicy };
}

describe('minimal exact APK identity policy change', () => {
  it('contains the archived debug APK identity without allowing development', () => {
    const policy = androidKeyAttestPolicy('production');
    expect(policy.apps).toContainEqual({ packageName, signatureDigests: [digest], development: false });
    expect(policy.allowDevelopment).toBe(false);
    expect(policy.apps.every((app) => app.signatureDigests !== 'any' && !app.development)).toBe(true);
    const source = readFileSync('src/modules/app-attest/app-attest.policy.ts', 'utf8');
    expect(source).toContain('trustedRoots: GOOGLE_KEY_ATTESTATION_ROOTS');
  });

  it('rejects the same identity without the added entry and accepts it with the entry', () => {
    const { input, testPolicy } = fixture();
    expect(() => verifyAndroidAttestation(input, { ...testPolicy, apps: testPolicy.apps.filter((app) => app.packageName !== packageName) })).toThrow(
      'app id is not allowed',
    );
    expect(verifyAndroidAttestation(input, testPolicy)).toEqual(expect.objectContaining({ appId: packageName, environment: 'production' }));
  });

  it.each([
    ['wrong package', { packageName: 'io.github.someone.else' }, 'app id is not allowed'],
    ['wrong APK certificate', { signatureDigest: Buffer.alloc(32, 1) }, 'app id is not allowed'],
    ['software key', { securityLevel: 0, softwareOnly: true }, 'key is not hardware backed'],
    ['unverified boot', { verifiedBootState: VERIFIED_BOOT_STATE.unverified }, 'device boot is not verified'],
  ] as const)('still rejects %s', (_label, spec, reason) => {
    const { input, testPolicy } = fixture(spec);
    expect(() => verifyAndroidAttestation(input, testPolicy)).toThrow(reason);
  });

  it('does not trust the synthetic certificate root with the actual production roots', () => {
    const { input, production } = fixture();
    expect(() => verifyAndroidAttestation(input, production)).toThrow('certificate chain does not reach a trusted root');
  });

  it('still rejects a mismatched challenge', () => {
    const { input, testPolicy } = fixture();
    expect(() => verifyAndroidAttestation({ ...input, challenge: Buffer.from('wrong') }, testPolicy)).toThrow('challenge mismatch');
  });

  it('registers the allowed identity, verifies a signed vision POST and refuses replay and body changes', async () => {
    const { testPolicy } = fixture();
    const store = new AppAttestStore();
    const revocations = new AttestationRevocations();
    vi.spyOn(revocations, 'anyRevoked').mockResolvedValue(false);
    const verifier = new AppAttestService({ appIds: [], trustedRoots: [], allowDevelopment: false }, store);
    const challenge = verifier.issueChallenge(user).challenge;
    const issued = androidChain({ packageName, signatureDigest: Buffer.from(digest, 'hex'), challenge });
    const issuedPolicy = { ...testPolicy, trustedRoots: [issued.rootPem] };
    const issuedKeys = new AndroidKeyAttestService(issuedPolicy, store, revocations);
    await expect(
      issuedKeys.registerKey(user, {
        keyId: issued.keyId,
        certificateChain: issued.chain.map((der) => der.toString('base64')),
        challenge,
      }),
    ).resolves.toEqual({ keyId: issued.keyId, environment: 'production' });
    const body = Buffer.from('{"kind":"cover","image":"ZmFrZQ=="}');
    const timestamp = String(Date.now());
    const url = '/api/v1/mobile/vision-scan';
    function request() {
      const nonce = randomBytes(16).toString('base64url');
      const data = androidAttestClientData('POST', url, timestamp, appAttestBodySha256(body), nonce);
      return {
        method: 'POST',
        url,
        body,
        headers: {
          [APP_ATTEST_HEADERS.keyId]: issued.keyId,
          [APP_ATTEST_HEADERS.assertion]: androidAssertion(data, issued.leafKey).toString('base64'),
          [APP_ATTEST_HEADERS.timestamp]: timestamp,
          [APP_ATTEST_HEADERS.nonce]: nonce,
        },
      };
    }
    const signed = request();
    expect(() => verifier.verifyRequest(user, signed)).not.toThrow();
    expect(() => verifier.verifyRequest(user, signed)).toThrow();
    expect(() => verifier.verifyRequest(user, { ...request(), body: Buffer.from('changed') })).toThrow();
    expect(() => verifier.verifyRequest({ id: 8 } as RequestUser, request())).toThrow();
    expect(() => verifier.verifyRequest(user, { method: 'POST', url, body, headers: {} })).toThrow();
    expect(store.keysForUser(user.id)).toHaveLength(1);
  });
});
