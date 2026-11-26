import type { AndroidAppIdentity } from './android-key-verifier';

/** Explicit additional package/certificate pairs. This never changes attestation verification. */
export function independentAndroidApps(raw: string | undefined): AndroidAppIdentity[] {
  if (raw === undefined || raw.trim() === '') return [];
  const reject = (): never => {
    throw new Error('Invalid BOOKORBIT_INDEPENDENT_ANDROID_APPS: expected exact packageName and SHA-256 certificate pairs');
  };
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return reject();
  }
  if (!Array.isArray(parsed) || parsed.length > 10) return reject();
  const seen = new Set<string>();
  return parsed.map((entry: unknown) => {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) return reject();
    const value = entry as Record<string, unknown>;
    if (Object.keys(value).some((key) => key !== 'packageName' && key !== 'sha256')) return reject();
    const { packageName, sha256 } = value;
    if (
      typeof packageName !== 'string' ||
      packageName.length > 255 ||
      !/^(?:[a-zA-Z][a-zA-Z0-9_]*\.)+[a-zA-Z][a-zA-Z0-9_]*$/.test(packageName) ||
      packageName === 'app.bookorbit.reader' ||
      packageName === 'app.bookorbit.reader.debug' ||
      seen.has(packageName)
    )
      return reject();
    if (
      !Array.isArray(sha256) ||
      sha256.length < 1 ||
      sha256.length > 10 ||
      sha256.some((digest) => typeof digest !== 'string' || !/^[a-fA-F0-9]{64}$/.test(digest))
    )
      return reject();
    seen.add(packageName);
    return { packageName, signatureDigests: [...new Set((sha256 as string[]).map((digest) => digest.toLowerCase()))], development: false };
  });
}
