import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { test } from 'node:test';

const source = await readFile(new URL('../../app/src/main/assets/reader/appearance.js', import.meta.url), 'utf8');
const { chapterSurfaceCSS, applyCanvasTheme } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);

test('dark chapter roots are opaque and descendants do not create background rectangles', () => {
  const css = chapterSurfaceCSS({ fg: '#e0e0e0', bg: '#222222', link: '#77bbee' });
  assert.match(css, /background: #222222 !important/);
  assert.match(css, /background-color: transparent !important/);
  assert.match(css, /box-shadow: none !important/);
  assert.doesNotMatch(css, /invert\(|mix-blend-mode/);
});

test('canvas, margins and chapter use one theme, including on live theme change', () => {
  const nodes = Array.from({ length: 5 }, () => ({ style: {} }));
  const doc = { documentElement: nodes[0], body: nodes[1] };
  applyCanvasTheme(doc, ...nodes.slice(2), { fg: '#eee', bg: '#222' }, true);
  nodes.forEach(node => assert.equal(node.style.backgroundColor, '#222'));
  assert.equal(doc.documentElement.style.colorScheme, 'dark');
  applyCanvasTheme(doc, ...nodes.slice(2), { fg: '#111', bg: '#fff' }, false);
  nodes.forEach(node => assert.equal(node.style.backgroundColor, '#fff'));
  assert.equal(doc.documentElement.style.colorScheme, 'light');
});
