import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { test } from 'node:test';

const source = await readFile(new URL('../../app/src/main/assets/reader/paging.js', import.meta.url), 'utf8');
const { PAGE_TURN_MS, pageTurnEase, pageTurnDelta } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
const contract = JSON.parse(await readFile(new URL('../../app/src/test/resources/reader-paging-contract.json', import.meta.url), 'utf8'));

test('EPUB and native share dp/CSS velocity projection and a one-page gesture cap', () => {
  for (const sample of contract.snap) assert.equal(pageTurnDelta(sample.drag, sample.velocity), sample.delta, JSON.stringify(sample));
});

test('EPUB and native share the page-turn duration and easing curve', () => {
  assert.equal(PAGE_TURN_MS, contract.durationMs);
  for (const sample of contract.ease) assert.equal(pageTurnEase(sample.progress), sample.position);
});

test('missing velocity or displacement never yields an unbounded page jump', () => {
  assert.equal(pageTurnDelta(Number.NaN, Number.POSITIVE_INFINITY), 0);
  assert.equal(pageTurnDelta(0, Number.MAX_VALUE), 1);
  assert.equal(pageTurnDelta(0, -Number.MAX_VALUE), -1);
});
