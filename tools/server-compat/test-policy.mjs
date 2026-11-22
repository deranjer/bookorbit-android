import assert from 'node:assert/strict';
import { test } from 'node:test';
import { independentAndroidApps } from './independent-android-apps.ts';

const packageName = 'io.github.monkecode.bookorbitclient';
const digest = 'ab'.repeat(32);
test('default policy adds no independent application', () => {
  for (const value of [undefined, '', ' ', '[]']) assert.deepEqual(independentAndroidApps(value), []);
});
test('explicit certificate pin produces a non-development identity', () => {
  assert.deepEqual(independentAndroidApps(JSON.stringify([{ packageName, sha256: [digest.toUpperCase(), digest] }])),
    [{ packageName, signatureDigests: [digest], development: false }]);
});
test('wildcards, debug/official overrides and attempts to weaken verification are refused', () => {
  for (const value of [
    [{ packageName, sha256: 'any' }], [{ packageName, sha256: ['*'] }], [{ packageName, sha256: [] }],
    [{ packageName: '*', sha256: [digest] }], [{ packageName, sha256: [digest], development: true }],
    [{ packageName, sha256: [digest], allowDevelopment: true }], [{ packageName, sha256: ['a'.repeat(63)] }],
    [{ packageName: 'app.bookorbit.reader', sha256: [digest] }],
    [{ packageName: 'app.bookorbit.reader.debug', sha256: [digest] }],
    [{ packageName, sha256: [digest] }, { packageName, sha256: [digest] }],
    {}, null, [null], [{ packageName: 'broken/package', sha256: [digest] }],
  ]) assert.throws(() => independentAndroidApps(JSON.stringify(value)));
  assert.throws(() => independentAndroidApps('{invalid json'));
});
