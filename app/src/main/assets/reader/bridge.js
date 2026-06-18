/*
 * In-WebView bridge between the Android host and foliate.js.
 *
 * This is the Android analog of the web client's useFoliate.ts + useReaderState.ts.
 * The host app never lets the WebView touch the network: the book bytes are streamed
 * in from the host as base64 chunks (begin -> chunk* -> commit), assembled into a File, and
 * handed to <foliate-view>. All commands arrive through globals invoked via
 * evaluateJavascript; all events leave through window.ReactNativeWebView.postMessage
 * (an Android @JavascriptInterface shimmed onto that global in index.html).
 *
 * Keep this file dependency-free and in plain ES module JS — it is shipped as a static
 * asset served by WebViewAssetLoader.
 */
import './foliate/view.js';

const container = document.getElementById('reader');

/** Latest applied render settings, re-applied on every section 'load'. */
let currentSettings = null;
/** The <foliate-view> element, once created. */
let view = null;
/** Base64 transfer state for the book currently being opened. */
let pending = null;

function post(message) {
  try {
    window.ReactNativeWebView.postMessage(JSON.stringify(message));
  } catch {
    /* host bridge not ready — nothing we can do from here. */
  }
}

function postError(message) {
  post({ type: 'error', message: String(message ?? 'Reader error') });
}

/* ------------------------------------------------------------------ themes */
// Ported verbatim from client/src/features/reader/epub/constants/themes.ts.
const THEMES = [
  { name: 'default', light: { fg: '#000000', bg: '#ffffff', link: '#0066cc' }, dark: { fg: '#e0e0e0', bg: '#222222', link: '#77bbee' } },
  { name: 'gray', light: { fg: '#222222', bg: '#e0e0e0', link: '#4488cc' }, dark: { fg: '#c6c6c6', bg: '#444444', link: '#88ccee' } },
  { name: 'sepia', light: { fg: '#5b4636', bg: '#f1e8d0', link: '#008b8b' }, dark: { fg: '#ffd595', bg: '#342e25', link: '#48d1cc' } },
  { name: 'crimson', light: { fg: '#2f1f25', bg: '#fdf1f4', link: '#dd0031' }, dark: { fg: '#f3dbe2', bg: '#3a252d', link: '#ff5a86' } },
  { name: 'meadow', light: { fg: '#232c16', bg: '#d7dbbd', link: '#177b4d' }, dark: { fg: '#d8deba', bg: '#333627', link: '#a6d608' } },
  { name: 'rosewood', light: { fg: '#4e1609', bg: '#f0d1d5', link: '#de3838' }, dark: { fg: '#e5c4c8', bg: '#462f32', link: '#ff646e' } },
  { name: 'azure', light: { fg: '#262d48', bg: '#cedef5', link: '#2d53e5' }, dark: { fg: '#babee1', bg: '#282e47', link: '#ff646e' } },
  { name: 'dawnlight', light: { fg: '#586e75', bg: '#fdf6e3', link: '#268bd2' }, dark: { fg: '#93a1a1', bg: '#002b36', link: '#268bd2' } },
  { name: 'ember', light: { fg: '#3c3836', bg: '#fbf1c7', link: '#076678' }, dark: { fg: '#ebdbb2', bg: '#282828', link: '#83a598' } },
  { name: 'aurora', light: { fg: '#2e3440', bg: '#eceff4', link: '#5e81ac' }, dark: { fg: '#d8dee9', bg: '#2e3440', link: '#88c0d0' } },
  { name: 'ocean', light: { fg: '#0a4d4d', bg: '#e0f7fa', link: '#00838f' }, dark: { fg: '#b2dfdb', bg: '#263238', link: '#4dd0e1' } },
  { name: 'mist', light: { fg: '#4a148c', bg: '#f3e5f5', link: '#7b1fa2' }, dark: { fg: '#c7b6dd', bg: '#3a3150', link: '#b39ddb' } },
  { name: 'amoled', light: { fg: '#000000', bg: '#ffffff', link: '#0066cc' }, dark: { fg: '#ffffff', bg: '#000000', link: '#77bbee' } },
];

const DEFAULT_SETTINGS = {
  fontSize: 16,
  lineHeight: 1.5,
  fontFamily: null,
  maxColumnCount: 2,
  gap: 0.05,
  maxInlineSize: 720,
  maxBlockSize: 1440,
  justify: true,
  hyphenate: true,
  isDark: false,
  themeName: 'default',
  flow: 'paginated',
};

function themeFor(name) {
  return THEMES.find((t) => t.name === name) ?? THEMES[0];
}

// Ported from useReaderState.generateCSS — keep changes in sync with the web client.
function generateCSS(s) {
  const theme = themeFor(s.themeName);
  const mode = s.isDark ? theme.dark : theme.light;
  const dark = s.isDark;
  const forceBg = dark || theme.light.bg !== '#ffffff';
  const fontFamilyRule = s.fontFamily
    ? `body { font-family: ${s.fontFamily} !important; } body * { font-family: inherit !important; }`
    : '';
  return `
    @namespace epub "http://www.idpf.org/2007/ops";
    @media print { html { column-width: auto !important; height: auto !important; width: auto !important; } }
    @media screen {
      html { color-scheme: ${dark ? 'dark' : 'light'}; color: ${mode.fg}; font-size: ${s.fontSize}px; }
      ${fontFamilyRule}
      a:any-link { color: ${mode.link}; text-underline-offset: .1em; }
      aside[epub|type~="footnote"] { display: none; }
    }
    html { line-height: ${s.lineHeight}; hanging-punctuation: allow-end last; orphans: 2; widows: 2; }
    [align="left"] { text-align: left; } [align="right"] { text-align: right; }
    [align="center"] { text-align: center; } [align="justify"] { text-align: justify; }
    :is(hgroup, header) p { text-align: unset; hyphens: unset; }
    h1, h2, h3, h4, h5, h6, hgroup, th { text-wrap: balance; }
    pre { white-space: pre-wrap !important; tab-size: 2; }
    ${
      forceBg
        ? `html, body { color: ${mode.fg} !important; background: none !important; }
           body * { color: inherit !important; border-color: currentColor !important; background-color: ${mode.bg} !important; }
           a:any-link { color: ${mode.link} !important; }
           svg, img { background-color: transparent !important; ${!dark ? 'mix-blend-mode: multiply;' : ''} }`
        : ''
    }
    p, li, blockquote, dd { line-height: ${s.lineHeight}; text-align: ${s.justify ? 'justify' : 'start'} !important; hyphens: ${s.hyphenate ? 'auto' : 'none'}; }
    ::selection { background-color: rgba(128, 128, 128, 0.3); }
  `;
}

// Ported from useReaderState.applyToRenderer.
function applyStyles(settings) {
  if (!view) return;
  currentSettings = { ...DEFAULT_SETTINGS, ...(settings ?? currentSettings ?? {}) };
  const s = currentSettings;
  const r = view.renderer;
  if (!r) return;
  r.setAttribute('max-column-count', String(s.maxColumnCount));
  r.setAttribute('gap', `${s.gap * 100}%`);
  r.setAttribute('max-inline-size', `${s.maxInlineSize}px`);
  r.setAttribute('max-block-size', `${s.maxBlockSize}px`);
  if (s.flow === 'paginated') r.setAttribute('margin', '40px');
  else r.removeAttribute('margin');
  r.setAttribute('flow', s.flow);
  if (typeof r.setStyles === 'function') r.setStyles(generateCSS(s));
}

/* --------------------------------------------------------------- toc/meta */
function serializeToc(items) {
  if (!Array.isArray(items)) return [];
  return items.map((it) => ({
    label: typeof it?.label === 'string' ? it.label.trim() : '',
    href: it?.href ?? null,
    subitems: serializeToc(it?.subitems),
  }));
}

/* ----------------------------------------------------------- create view */
function createView() {
  if (view) return view;
  view = document.createElement('foliate-view');
  view.setAttribute('id', 'foliate');
  view.style.cssText = 'width:100%;height:100%;display:block;';
  container.appendChild(view);

  view.addEventListener('load', () => {
    // The paginator swaps its internal view in a microtask after 'load'; defer so
    // setStyles targets the freshly loaded section (mirrors useFoliate.ts).
    setTimeout(() => applyStyles(currentSettings), 0);
  });

  view.addEventListener('relocate', (e) => {
    const d = e.detail ?? {};
    post({
      type: 'relocate',
      cfi: d.cfi ?? null,
      fraction: typeof d.fraction === 'number' ? d.fraction : null,
      chapterTitle: d.tocItem?.label ?? null,
      location: d.location ?? null,
    });
  });

  view.addEventListener('error', (e) => {
    postError(e.detail?.message ?? 'Reader error');
  });

  return view;
}

/* ----------------------------------------------------- base64 -> bytes */
function b64ToBytes(b64) {
  const bin = atob(b64);
  const len = bin.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

const MIME = {
  epub: 'application/epub+zip',
  mobi: 'application/x-mobipocket-ebook',
  azw3: 'application/vnd.amazon.ebook',
  azw: 'application/vnd.amazon.ebook',
  fb2: 'application/x-fictionbook+xml',
  cbz: 'application/vnd.comicbook+zip',
  cbr: 'application/vnd.comicbook-rar',
};

async function openBook(meta, parts) {
  try {
    createView();
    const format = (meta.format ?? 'epub').toLowerCase();
    const blob = new Blob(parts, { type: MIME[format] ?? 'application/octet-stream' });
    const file = new File([blob], `book.${format}`, { type: blob.type });

    await view.open(file);

    applyStyles(meta.settings ?? currentSettings);

    post({
      type: 'loaded',
      toc: serializeToc(view.book?.toc),
      metadata: {
        title: view.book?.metadata?.title ?? null,
        language: view.book?.metadata?.language ?? null,
      },
    });

    let navigated = false;
    if (meta.cfi) {
      try {
        await view.goTo(meta.cfi);
        navigated = true;
      } catch {
        navigated = false;
      }
    }
    if (!navigated && typeof meta.fraction === 'number' && meta.fraction > 0 && typeof view.goToFraction === 'function') {
      try {
        view.goToFraction(meta.fraction);
        navigated = true;
      } catch {
        navigated = false;
      }
    }
    if (!navigated) await view.goTo(0).catch(() => {});
  } catch (e) {
    postError(e?.message ?? 'Failed to open book');
  }
}

/* -------------------------------------------------- host -> WebView API */
// Begin a fresh book transfer. meta = { format, cfi, fraction, settings }.
window.__readerBegin = (metaJson) => {
  try {
    pending = { meta: JSON.parse(metaJson), parts: [] };
    if (pending.meta.settings) currentSettings = { ...DEFAULT_SETTINGS, ...pending.meta.settings };
  } catch (e) {
    postError(e?.message ?? 'Bad open payload');
  }
};

// Push one base64 chunk (already aligned to a 4-char boundary by the host).
window.__readerChunk = (b64) => {
  if (!pending) return;
  try {
    pending.parts.push(b64ToBytes(b64));
  } catch (e) {
    postError(e?.message ?? 'Bad chunk');
  }
};

// Finish the transfer and open the assembled file.
window.__readerCommit = () => {
  if (!pending) return;
  const { meta, parts } = pending;
  pending = null;
  void openBook(meta, parts);
};

// Imperative commands: { type: 'goTo'|'goToFraction'|'prev'|'next'|'applyStyles', ... }.
window.__readerCommand = (json) => {
  if (!view) return;
  let cmd;
  try {
    cmd = JSON.parse(json);
  } catch {
    return;
  }
  switch (cmd.type) {
    case 'goTo':
      void view.goTo(cmd.target).catch(() => {});
      break;
    case 'goToFraction':
      if (typeof view.goToFraction === 'function') view.goToFraction(cmd.value);
      break;
    case 'prev':
      view.prev?.();
      break;
    case 'next':
      view.next?.();
      break;
    case 'applyStyles':
      applyStyles(cmd.settings);
      break;
    default:
      break;
  }
};

post({ type: 'ready' });
