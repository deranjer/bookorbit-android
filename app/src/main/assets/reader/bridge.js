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

/* ------------------------------------------------------- annotations */
// Highlight drawing mirrors client/src/features/reader/epub/composables/useFoliateAnnotations.ts.
/** cfi -> { color, style } for every highlight currently drawn. */
const annotationStyles = new Map();
/** When foliate last reported a tap on an existing highlight, so the generic tap handler can skip it. */
let lastAnnotationHitAt = 0;

function createSVG(tag) {
  return document.createElementNS('http://www.w3.org/2000/svg', tag);
}

function getDrawFunction(style) {
  switch (style) {
    case 'underline':
      return (rects, { color = 'red' } = {}) => {
        const g = createSVG('g');
        g.setAttribute('fill', color);
        for (const { left, bottom, width } of Array.from(rects)) {
          const el = createSVG('rect');
          el.setAttribute('x', String(left));
          el.setAttribute('y', String(bottom - 2));
          el.setAttribute('height', '2');
          el.setAttribute('width', String(width));
          g.append(el);
        }
        return g;
      };
    case 'strikethrough':
      return (rects, { color = 'red' } = {}) => {
        const g = createSVG('g');
        g.setAttribute('fill', color);
        for (const { left, top, bottom, width } of Array.from(rects)) {
          const el = createSVG('rect');
          el.setAttribute('x', String(left));
          el.setAttribute('y', String((top + bottom) / 2));
          el.setAttribute('height', '2');
          el.setAttribute('width', String(width));
          g.append(el);
        }
        return g;
      };
    case 'invert':
      return (rects, { color = '#FFFFFF' } = {}) => {
        const g = createSVG('g');
        g.setAttribute('fill', color);
        g.style.mixBlendMode = 'difference';
        for (const { left, top, height, width } of Array.from(rects)) {
          const el = createSVG('rect');
          el.setAttribute('x', String(left));
          el.setAttribute('y', String(top));
          el.setAttribute('height', String(height));
          el.setAttribute('width', String(width));
          g.append(el);
        }
        return g;
      };
    case 'squiggly':
      return (rects, { color = 'red' } = {}) => {
        const g = createSVG('g');
        g.setAttribute('fill', 'none');
        g.setAttribute('stroke', color);
        g.setAttribute('stroke-width', '2');
        const block = 3;
        for (const { left, bottom, width } of Array.from(rects)) {
          const el = createSVG('path');
          const n = Math.max(1, Math.round(width / block / 1.5));
          const inline = width / n;
          const ls = Array.from({ length: n }, (_, i) => `l${inline} ${i % 2 ? block : -block}`).join('');
          el.setAttribute('d', `M${left} ${bottom}${ls}`);
          g.append(el);
        }
        return g;
      };
    default:
      return (rects, { color = 'yellow' } = {}) => {
        const g = createSVG('g');
        g.setAttribute('fill', color);
        g.style.opacity = '0.3';
        g.style.mixBlendMode = 'multiply';
        for (const { left, top, height, width } of Array.from(rects)) {
          const el = createSVG('rect');
          el.setAttribute('x', String(left));
          el.setAttribute('y', String(top));
          el.setAttribute('height', String(height));
          el.setAttribute('width', String(width));
          g.append(el);
        }
        return g;
      };
  }
}

function drawAnnotation(cfi) {
  return view?.addAnnotation?.({ value: cfi })?.catch?.(() => {});
}

function eraseAnnotation(cfi) {
  return view?.deleteAnnotation?.({ value: cfi })?.catch?.(() => {});
}

/** Replace the full set of drawn highlights: erase removed ones, (re)draw new or changed ones. */
function setAnnotations(items) {
  const next = new Map();
  for (const it of Array.isArray(items) ? items : []) {
    if (it?.cfi) next.set(it.cfi, { color: it.color, style: it.style });
  }
  for (const cfi of Array.from(annotationStyles.keys())) {
    if (!next.has(cfi)) {
      annotationStyles.delete(cfi);
      void eraseAnnotation(cfi);
    }
  }
  for (const [cfi, st] of next) {
    const prev = annotationStyles.get(cfi);
    annotationStyles.set(cfi, st);
    if (!prev || prev.color !== st.color || prev.style !== st.style) void drawAnnotation(cfi);
  }
}

/* ------------------------------------------------- taps and selection */
/** A range's bounding box in the WebView's own viewport coordinates (CSS px == dp). */
function viewportRect(range) {
  const r = range.getBoundingClientRect();
  const frame = range.startContainer?.ownerDocument?.defaultView?.frameElement;
  const off = frame ? frame.getBoundingClientRect() : { left: 0, top: 0 };
  return { left: r.left + off.left, top: r.top + off.top, right: r.right + off.left, bottom: r.bottom + off.top };
}

function clearSelection() {
  for (const c of view?.renderer?.getContents?.() ?? []) c.doc?.getSelection?.()?.removeAllRanges();
}

/**
 * Per-section-document listeners. The host no longer overlays tap zones on the WebView (that
 * blocked text selection), so taps are detected here and reported with their horizontal position.
 */
function attachDocHandlers(doc, index) {
  let selectionAtPointerDown = false;
  let selectionTimer = null;

  doc.addEventListener(
    'pointerdown',
    () => {
      const sel = doc.getSelection();
      selectionAtPointerDown = !!sel && !sel.isCollapsed;
    },
    true,
  );

  doc.addEventListener('click', (e) => {
    // A tap that merely dismissed an existing selection must not also turn the page.
    if (selectionAtPointerDown) {
      selectionAtPointerDown = false;
      return;
    }
    if (e.target?.closest?.('a[href]')) return;
    const sel = doc.getSelection();
    if (sel && !sel.isCollapsed) return;
    const frame = doc.defaultView?.frameElement;
    const left = frame ? frame.getBoundingClientRect().left : 0;
    const x = (left + e.clientX) / window.innerWidth;
    // Foliate's own click listener reports taps on highlights; give it a tick to run first.
    setTimeout(() => {
      if (performance.now() - lastAnnotationHitAt < 50) return;
      post({ type: 'tap', x });
    }, 0);
  });

  doc.addEventListener('selectionchange', () => {
    clearTimeout(selectionTimer);
    selectionTimer = setTimeout(() => {
      const sel = doc.getSelection();
      if (!sel || sel.isCollapsed || sel.rangeCount === 0) {
        post({ type: 'selectionCleared' });
        return;
      }
      const range = sel.getRangeAt(0);
      const text = range.toString().trim();
      if (!text) return;
      let cfi = null;
      try {
        cfi = view.getCFI(index, range);
      } catch {
        cfi = null;
      }
      post({ type: 'selection', text, cfi, rect: viewportRect(range) });
    }, 300);
  });
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

  view.addEventListener('load', (e) => {
    const { doc, index } = e.detail ?? {};
    if (doc) attachDocHandlers(doc, index);
  });

  view.addEventListener('draw-annotation', (e) => {
    const { draw, annotation } = e.detail ?? {};
    const st = annotationStyles.get(annotation?.value);
    if (!draw || !st) return;
    draw(getDrawFunction(st.style), { color: st.color });
  });

  // A section's overlay is rebuilt each time it loads, so redraw everything that belongs to it.
  view.addEventListener('create-overlay', () => {
    setTimeout(() => {
      for (const cfi of annotationStyles.keys()) void drawAnnotation(cfi);
    }, 100);
  });

  view.addEventListener('show-annotation', (e) => {
    lastAnnotationHitAt = performance.now();
    const range = e.detail?.range;
    post({ type: 'annotationTap', cfi: e.detail?.value ?? null, rect: range ? viewportRect(range) : null });
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

// Imperative commands: { type: 'goTo'|'goToFraction'|'prev'|'next'|'applyStyles'|'setAnnotations'|'clearSelection', ... }.
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
    case 'setAnnotations':
      setAnnotations(cmd.items);
      break;
    case 'clearSelection':
      clearSelection();
      break;
    default:
      break;
  }
};

post({ type: 'ready' });
