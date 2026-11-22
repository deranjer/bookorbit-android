import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { test } from 'node:test';

const root = new URL('../../app/src/main/assets/reader/', import.meta.url);
const controllerSource = await readFile(new URL('read-along.js', root), 'utf8');
const { ReadAlongController } = await import(`data:text/javascript;base64,${Buffer.from(controllerSource).toString('base64')}`);
class DetailEvent extends Event { constructor(type, detail) { super(type); this.detail = detail; } }
class Overlay extends EventTarget {
  calls = [];
  state(value) { this.dispatchEvent(new DetailEvent('state', value)); }
  stop() { this.calls.push('stop'); this.state('stopped'); }
  pause() { this.calls.push('pause'); this.state('paused'); }
  resume() { this.calls.push('resume'); this.state('playing'); }
  next() { this.calls.push('next'); }
  prev() { this.calls.push('prev'); }
  setRate(value) { this.calls.push(['rate', value]); }
}

test('overlay detection never autoplays and absent overlays remain unavailable', () => {
  const events = [];
  const ctl = new ReadAlongController(event => events.push(event));
  const overlay = new Overlay();
  ctl.attach({ mediaOverlay: overlay });
  assert.equal(events.at(-1).state, 'ready');
  assert.equal(overlay.calls.length, 0);
  ctl.attach({});
  assert.equal(events.at(-1).available, false);
});

test('explicit start, pause, resume, rate and ended state use one overlay', async () => {
  const events = [], overlay = new Overlay();
  const ctl = new ReadAlongController(event => events.push(event));
  let starts = 0;
  ctl.attach({ mediaOverlay: overlay, startMediaOverlay: async () => { starts++; overlay.state('playing'); } });
  await ctl.command('pause');
  assert.equal(events.at(-1).state, 'ready');
  await ctl.command('start');
  assert.equal(starts, 1);
  assert.equal(events.at(-1).state, 'playing');
  await ctl.command('pause');
  assert.equal(events.at(-1).state, 'paused');
  await ctl.command('resume');
  await ctl.command('rate', 1.5);
  assert.equal(events.at(-1).rate, 1.5);
  await ctl.command('rate', NaN);
  await ctl.command('rate', 99);
  assert.equal(events.at(-1).rate, 1.5);
  overlay.state('ended');
  assert.equal(events.at(-1).state, 'ended');
});

test('audio errors are nonfatal reader events and old overlay listeners detach', async () => {
  const events = [], overlay = new Overlay();
  const ctl = new ReadAlongController(event => events.push(event));
  ctl.attach({ mediaOverlay: overlay, startMediaOverlay: async () => { throw Error('codec'); } });
  await ctl.command('start');
  assert.equal(events.at(-1).type, 'readAlong');
  assert.equal(events.at(-1).state, 'error');
  assert.equal(events.at(-1).error, 'audio');
  ctl.detach();
  const count = events.length;
  overlay.state('playing');
  assert.equal(events.length, count);
});

// Exercise the actual vendored engine without Android, network, or an audio decoder.
const epubSource = await readFile(new URL('foliate/epub.js', root), 'utf8');
const overlayClass = epubSource.slice(epubSource.indexOf('class MediaOverlay extends EventTarget'), epubSource.indexOf('\nconst isUUID'));
function engine(loadBlob = async () => new Blob(['synthetic'])) {
  const audios = [], loaded = [];
  class Audio extends EventTarget {
    paused = true; currentTime = 0;
    constructor(src) { super(); this.src = src; audios.push(this); }
    pause() { this.paused = true; }
    async play() { this.paused = false; this.dispatchEvent(new Event('playing')); }
  }
  const attrs = obj => ({ getAttribute: key => obj[key] });
  const par = (text, audio, begin, end) => ({ text: attrs({ src: text }), audio: attrs({ src: audio, clipBegin: begin, clipEnd: end }) });
  const smil = { pars: [par('chapter.xhtml#one', 'one.mp3', '0', '1'), par('chapter.xhtml#two', 'one.mp3', '1', '2'), par('chapter.xhtml#three', 'two.mp3', '0', '1')] };
  const MediaOverlay = new Function('EventTarget', 'CustomEvent', 'resolveURL', 'parseClock', 'childGetter', 'NS', 'URL', 'Audio', `return ${overlayClass}`)(
    EventTarget, class extends DetailEvent { constructor(type, options) { super(type, options.detail); } },
    (href, base) => new URL(href, `https://fixture/${base}`).pathname.slice(1) + new URL(href, `https://fixture/${base}`).hash,
    Number, doc => ({ $: (node, name) => node[name], $$$: () => doc.pars }), { SMIL: 'smil' },
    { createObjectURL: () => `blob:${audios.length}`, revokeObjectURL() {} }, Audio,
  );
  const book = { sections: [{ id: 'OEBPS/chapter.xhtml', mediaOverlay: { href: 'OEBPS/overlay.smil' } }], loadBlob: async src => { loaded.push(src); return loadBlob(src); } };
  return { overlay: new MediaOverlay(book, async () => smil), audios, loaded };
}

const settle = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };
test('actual SMIL engine advances to next audio asset rather than skipping its clips', async () => {
  const { overlay, audios, loaded } = engine();
  await overlay.start(0);
  audios.at(-1).dispatchEvent(new Event('canplaythrough'));
  overlay.next(); await settle();
  audios.at(-1).dispatchEvent(new Event('canplaythrough'));
  assert.equal(audios.at(-1).currentTime, 1);
  overlay.next(); await settle();
  assert.equal(loaded.at(-1), 'OEBPS/two.mp3');
});

test('stop during asynchronous audio loading cannot create or start late audio', async () => {
  let resolve;
  const pending = new Promise(done => { resolve = done; });
  const { overlay, audios } = engine(() => pending);
  const start = overlay.start(0);
  await settle();
  overlay.stop(); resolve(new Blob(['synthetic']));
  await start;
  assert.equal(audios.length, 0);
});

test('late events from a stopped audio instance cannot resume playback', async () => {
  const { overlay, audios } = engine();
  await overlay.start(0);
  const stale = audios[0];
  overlay.stop();
  stale.dispatchEvent(new Event('canplaythrough'));
  stale.dispatchEvent(new Event('ended'));
  await settle();
  assert.equal(stale.paused, true);
  assert.equal(audios.length, 1);
});

test('end of book reports ended without creating another audio object', async () => {
  const { overlay, audios } = engine();
  const states = [];
  overlay.addEventListener('state', event => states.push(event.detail));
  await overlay.start(99);
  assert.equal(states.at(-1), 'ended');
  assert.equal(audios.length, 0);
});
