/** Apply the same opaque surface to chapter roots and the paginator's outer canvas. */
export function chapterSurfaceCSS(mode) {
  return `
    html, body {
      color: ${mode.fg} !important;
      background: ${mode.bg} !important;
      border: 0 !important;
      outline: 0 !important;
      box-shadow: none !important;
    }
    body * {
      color: inherit !important;
      border-color: currentColor !important;
      background-color: transparent !important;
    }
    a:any-link { color: ${mode.link} !important; }
    svg, img, video, canvas { background-color: transparent !important; }
  `;
}

export function applyCanvasTheme(doc, container, view, renderer, mode, dark) {
  doc.documentElement.style.colorScheme = dark ? 'dark' : 'light';
  for (const element of [doc.documentElement, doc.body, container, view, renderer]) {
    if (element?.style) {
      element.style.backgroundColor = mode.bg;
      element.style.color = mode.fg;
    }
  }
}
