import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { test } from 'node:test';

const source = await readFile(new URL('../../app/src/main/assets/reader/foliate/streaming-loader.js', import.meta.url), 'utf8');
const { makeStreamingLoader } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
const info = { manifest: [{ href: 'OPS/ch1.xhtml', size: 19 }, { href: 'OPS/ch2.xhtml', size: 99 }, { href: 'OPS/art 100%.svg' }], optionalFiles: [] };

test('loader requests only selected resources through the exact edition bridge without bearer credentials', async () => {
  const previous = globalThis.fetch, requests = [];
  globalThis.fetch = async (url, options) => { requests.push({ url, options }); return new Response('<h1>One</h1>'); };
  try {
    const loader = makeStreamingLoader(4, 'https://appassets.androidplatform.net/reader-stream/lease', info, null, null, 71);
    assert.equal(requests.length, 0);
    await loader.loadText('META-INF/encryption.xml');
    assert.equal(requests.length, 0);
    assert.equal(await loader.loadText('OPS/ch1.xhtml'), '<h1>One</h1>');
    assert.equal(requests.length, 1);
    assert.equal(requests[0].url, 'https://appassets.androidplatform.net/reader-stream/lease/4/file/OPS/ch1.xhtml?fileId=71');
    assert.deepEqual(requests[0].options, {});
    assert.equal(loader.getSize('OPS/ch2.xhtml'), 99);
    assert.equal(requests.length, 1); // Size lookup does not prefetch another chapter.
  } finally { globalThis.fetch = previous; }
});

test('resource names with literal percent and spaces are encoded without changing their identity', async () => {
  const previous = globalThis.fetch, requests = [];
  globalThis.fetch = async url => { requests.push(url); return new Response('<svg/>'); };
  try {
    const loader = makeStreamingLoader(4, '/reader-stream/lease', info, null, null, 71);
    assert.equal(await (await loader.loadBlob('OPS/art 100%.svg', 'image/svg+xml')).text(), '<svg/>');
    assert.equal(requests[0], '/reader-stream/lease/4/file/OPS/art%20100%25.svg?fileId=71');
  } finally { globalThis.fetch = previous; }
});
