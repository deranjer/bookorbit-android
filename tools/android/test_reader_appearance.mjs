import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

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

const bridge = await readFile(new URL('../../app/src/main/assets/reader/bridge.js', import.meta.url), 'utf8');
function rendererFixture() {
  const attrs = new Map();
  let css;
  const renderer = { setAttribute: (key, value) => attrs.set(key, value), removeAttribute: key => attrs.delete(key), setStyles: value => { css = value; } };
  const defaultsStart = bridge.indexOf('const DEFAULT_SETTINGS = {');
  const defaultsEnd = bridge.indexOf('\n};', defaultsStart) + 3;
  const stylesStart = bridge.indexOf('function generateCSS(s)');
  const stylesEnd = bridge.indexOf('/* --------------------------------------------------------------- toc/meta */');
  assert.ok(defaultsStart >= 0 && defaultsEnd > defaultsStart && stylesEnd > stylesStart);
  const context = { view: { renderer }, currentSettings: null, document: {}, container: {},
    themeFor: () => ({ dark: { bg: '#222', fg: '#eee' }, light: { bg: '#fff', fg: '#111' } }),
    applyCanvasTheme: () => {}, chapterSurfaceCSS: () => '', CSS: { escape: value => value } };
  runInNewContext(`${bridge.slice(defaultsStart, defaultsEnd)}\n${bridge.slice(stylesStart, stylesEnd)}\nglobalThis.update = applyStyles;`, context);
  return { update: context.update, attrs, css: () => css };
}

test('live reading controls reach the renderer with exact font layout and spacing values', () => {
  const renderer = rendererFixture();
  renderer.update({ fontSize: 23, lineHeight: 1.8, pageMargin: 24, gap: 0.12, maxColumnCount: 3,
    maxInlineSize: 600, justify: false, hyphenate: false, flow: 'paginated' });
  assert.equal(renderer.attrs.get('margin'), '24px');
  assert.equal(renderer.attrs.get('gap'), '12%');
  assert.equal(renderer.attrs.get('max-column-count'), '3');
  assert.equal(renderer.attrs.get('max-inline-size'), '600px');
  assert.equal(renderer.attrs.has('animated'), true);
  assert.match(renderer.css(), /font-size: 23px/);
  assert.match(renderer.css(), /line-height: 1.8/);
  assert.match(renderer.css(), /text-align: start !important; hyphens: none/);
});

test('switching to continuous scroll removes page margins and defaults restore the original layout', () => {
  const renderer = rendererFixture();
  renderer.update({ pageMargin: 72 });
  assert.equal(renderer.attrs.get('margin'), '72px');
  renderer.update({ flow: 'scrolled' });
  assert.equal(renderer.attrs.get('flow'), 'scrolled');
  assert.equal(renderer.attrs.has('margin'), false);
  renderer.update({ animatePageTurns: false });
  assert.equal(renderer.attrs.has('animated'), false);
  renderer.update({});
  assert.equal(renderer.attrs.get('flow'), 'paginated');
  assert.equal(renderer.attrs.get('margin'), '40px');
});
