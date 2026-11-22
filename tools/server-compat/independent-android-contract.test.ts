// Copy alongside the upstream app-attest tests. All keys/payloads here are synthetic.
import { createHash, createPublicKey } from 'node:crypto';
import { readFileSync } from 'node:fs';

import { verifyAndroidAssertion } from './android-key-verifier';
import { androidAttestClientData, appAttestBodySha256 } from './app-attest.service';
import { androidKeyAttestPolicy } from './app-attest.policy';

describe('independent Android compatibility contract', () => {
  it('adds only exact production identities without weakening roots or development policy', () => {
    const defaults = androidKeyAttestPolicy('production', '');
    const pinned = androidKeyAttestPolicy(
      'production',
      JSON.stringify([{ packageName: 'io.github.monkecode.bookorbitclient', sha256: ['a'.repeat(64)] }]),
    );
    expect(pinned.apps).toEqual([
      ...defaults.apps,
      {
        packageName: 'io.github.monkecode.bookorbitclient',
        signatureDigests: ['a'.repeat(64)],
        development: false,
      },
    ]);
    expect(pinned.trustedRoots).toEqual(defaults.trustedRoots);
    expect(pinned.allowDevelopment).toBe(false);
    expect(() => androidKeyAttestPolicy('production', '[{"packageName":"anything","sha256":["*"]}]')).toThrow();
  });

  it('verifies the Kotlin-produced canonical bytes, SEC1 key ID and DER signature', () => {
    const fixturePath = process.env.BOOKORBIT_ANDROID_CONTRACT_FILE;
    if (!fixturePath) throw new Error('Set BOOKORBIT_ANDROID_CONTRACT_FILE to the JVM-generated public fixture');
    const vector = JSON.parse(readFileSync(fixturePath, 'utf8')) as Record<string, string>;
    const body = Buffer.from(vector.bodyBase64, 'base64');
    const data = androidAttestClientData(vector.method, vector.url, vector.timestamp, appAttestBodySha256(body), vector.nonce);
    expect(data.toString('base64')).toBe(vector.clientDataBase64);
    const publicKey = createPublicKey({ key: Buffer.from(vector.spkiBase64, 'base64'), format: 'der', type: 'spki' });
    const jwk = publicKey.export({ format: 'jwk' });
    const point = Buffer.concat([Buffer.from([4]), Buffer.from(jwk.x!, 'base64url'), Buffer.from(jwk.y!, 'base64url')]);
    expect(createHash('sha256').update(point).digest('base64')).toBe(vector.keyId);
    const publicKeyPem = publicKey.export({ format: 'pem', type: 'spki' }).toString();
    const signature = Buffer.from(vector.signature, 'base64');
    expect(() => verifyAndroidAssertion({ signature, clientData: data, publicKeyPem })).not.toThrow();
    const changed = androidAttestClientData(
      vector.method,
      vector.url,
      vector.timestamp,
      appAttestBodySha256(Buffer.concat([body, Buffer.from(' ')])),
      vector.nonce,
    );
    expect(() => verifyAndroidAssertion({ signature, clientData: changed, publicKeyPem })).toThrow();
  });
});
