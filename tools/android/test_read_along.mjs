import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { test } from 'node:test';

const root = new URL('../../app/src/main/assets/reader/', import.meta.url);
const controllerSource = await readFile(new URL('read-along.js', root), 'utf8');
const { ReadAlongController } = await import(`data:text/javascript;base64,${Buffer.from(controllerSource).toString('base64')}`);
class DetailEvent extends Event { constructor(type, detail) { super(type); this.detail = detail; } }
globalThis.CustomEvent ??= class extends DetailEvent { constructor(type, options) { super(type, options?.detail); } };
class Overlay extends EventTarget {
  calls = [];
  state(value) { this.dispatchEvent(new DetailEvent('state', value)); }
  stop() { this.calls.push('stop'); this.state('stopped'); }
  pause() { this.calls.push('pause'); this.state('paused'); }
  resume() { this.calls.push('resume'); this.state('playing'); }
  next() { this.calls.push('next'); }
  prev() { this.calls.push('prev'); }
  setRate(value) { this.calls.push(['rate', value]); }
  async restore(position) { this.calls.push(['restore', position]); this.state('playing'); }
}

test('native following highlights exact fragments without starting web audio or repeating navigation', () => {
  const overlay = new Overlay(), highlights = [];
  overlay.addEventListener('highlight', e => highlights.push(e.detail.text));
  const ctl = new ReadAlongController(() => {});
  ctl.attach({ mediaOverlay: overlay });
  ctl.followNative(null);
  assert.deepEqual(overlay.calls, []);
  ctl.followNative({ fragment: 'OPS/chapter.xhtml#one' });
  ctl.followNative({ fragment: 'OPS/chapter.xhtml#one', audioSeconds: 2 });
  assert.deepEqual(highlights, ['OPS/chapter.xhtml#one']);
  assert.ok(overlay.calls.every(value => value === 'pause'));
  ctl.followNative({ fragment: 'OPS/chapter.xhtml#two' });
  assert.equal(highlights.at(-1), 'OPS/chapter.xhtml#two');
});

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
function engine(loadBlob = async () => new Blob(['synthetic']), sections) {
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
  const book = { sections: sections ?? [{ id: 'OEBPS/chapter.xhtml', mediaOverlay: { href: 'OEBPS/overlay.smil' } }], loadBlob: async src => { loaded.push(src); return loadBlob(src); } };
  const loadXML = async href => href.endsWith('second.smil')
    ? { pars: [par('second.xhtml#one', 'second.mp3', '0', '1'), par('second.xhtml#two', 'second.mp3', '1', '2')] }
    : smil;
  return { overlay: new MediaOverlay(book, loadXML), audios, loaded };
}

function selectionRange(elements, id = 'one') {
  const word = { nodeType: 3 };
  const selected = { contains: node => node === word };
  const doc = { getElementById: key => elements?.(key, word, selected) ?? (key === id ? selected : null) };
  word.ownerDocument = doc;
  return { startContainer: word, startOffset: 2, collapsed: false };
}

test('selection resolves exact SMIL fragment start without loading or playing audio', async () => {
  const { overlay, loaded, audios } = engine();
  const position = await overlay.positionForSelection(0, selectionRange(null, 'two'));
  assert.deepEqual(position, { sectionIndex: 0, fragment: 'OEBPS/chapter.xhtml#two', audioSrc: 'OEBPS/one.mp3', audioSeconds: 1 });
  assert.deepEqual(loaded, []);
  assert.deepEqual(audios, []);
  assert.equal(overlay.getPosition(), null);
});

test('unnarrated or collapsed selections never offer listening from a guessed text percentage', async () => {
  const { overlay } = engine();
  assert.equal(await overlay.positionForSelection(0, selectionRange(null, 'silent')), null);
  assert.equal(await overlay.positionForSelection(0, { ...selectionRange(), collapsed: true }), null);
  assert.equal(await overlay.positionForSelection(1, selectionRange()), null);
});

test('nested narration selects the most specific fragment and ambiguous anchors fail closed', async () => {
  const { overlay } = engine();
  let parent;
  const nested = selectionRange((id, word, selected) => {
    parent ??= { contains: node => node === word || node === selected };
    return id === 'one' ? parent : id === 'two' ? selected : null;
  }, 'silent');
  assert.equal((await overlay.positionForSelection(0, nested)).fragment, 'OEBPS/chapter.xhtml#two');
  const ambiguous = selectionRange((id, _word, selected) => id === 'one' || id === 'two' ? selected : null, 'silent');
  assert.equal(await overlay.positionForSelection(0, ambiguous), null);
});

test('selection metadata lookup failure is nonfatal and does not start foreground audio', async () => {
  const overlay = new Overlay();
  overlay.positionForSelection = async () => { throw new Error('missing SMIL'); };
  const ctl = new ReadAlongController(() => {});
  ctl.attach({ mediaOverlay: overlay });
  assert.equal(await ctl.positionForSelection(0, selectionRange()), null);
  assert.deepEqual(overlay.calls, []);
});

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

test('exact checkpoint restores its audio asset and clock, not the start of the sentence', async () => {
  const { overlay, audios } = engine();
  const position = { sectionIndex: 0, fragment: 'OEBPS/chapter.xhtml#two', audioSrc: 'OEBPS/one.mp3', audioSeconds: 1.375 };
  await overlay.restore(position);
  audios.at(-1).dispatchEvent(new Event('canplaythrough'));
  assert.equal(audios.at(-1).currentTime, 1.375);
  assert.deepEqual(overlay.getPosition(), position);
});

test('a changed fragment or audio asset is rejected instead of resuming unrelated audio', async () => {
  for (const change of [{ fragment: 'other.xhtml#two' }, { audioSrc: 'OEBPS/two.mp3' }, { sectionIndex: 99 }]) {
    const { overlay, audios } = engine();
    await assert.rejects(overlay.restore({ sectionIndex: 0, fragment: 'OEBPS/chapter.xhtml#two', audioSrc: 'OEBPS/one.mp3', audioSeconds: 1.375, ...change }));
    assert.equal(audios.length, 0);
  }
});

test('a checkpoint outside its SMIL clip safely resumes at that exact fragment start', async () => {
  const { overlay, audios } = engine();
  await overlay.restore({ sectionIndex: 0, fragment: 'OEBPS/chapter.xhtml#two', audioSrc: 'OEBPS/one.mp3', audioSeconds: 999 });
  audios.at(-1).dispatchEvent(new Event('canplaythrough'));
  assert.equal(audios.at(-1).currentTime, 1);
});

test('pause while audio bytes load cannot autoplay when canplaythrough arrives', async () => {
  let resolve;
  const { overlay, audios } = engine(() => new Promise(done => { resolve = done; }));
  const pending = overlay.start(0);
  await settle();
  overlay.pause();
  resolve(new Blob(['audio']));
  await pending;
  audios.at(-1).dispatchEvent(new Event('canplaythrough'));
  assert.equal(audios.at(-1).paused, true);
  assert.equal(overlay.getPosition().audioSeconds, 0);
});

test('loading a saved checkpoint is inert until explicit Play and retains its rate', async () => {
  const overlay = new Overlay(), events = [];
  const checkpoint = { sectionIndex: 0, fragment: 'chapter#two', audioSrc: 'a.mp3', audioSeconds: 4.25, rate: 1.5 };
  const ctl = new ReadAlongController(event => events.push(event));
  ctl.attach({ mediaOverlay: overlay }, checkpoint);
  assert.equal(overlay.calls.length, 0);
  assert.equal(events.at(-1).rate, 1.5);
  await ctl.command('start');
  assert.deepEqual(overlay.calls.at(-1), ['restore', checkpoint]);
});

test('position events publish a separate audio coordinate without text progress or session events', () => {
  const overlay = new Overlay(), events = [];
  const ctl = new ReadAlongController(event => events.push(event));
  ctl.attach({ mediaOverlay: overlay });
  overlay.state('playing');
  const position = { sectionIndex: 0, fragment: 'chapter#two', audioSrc: 'a.mp3', audioSeconds: 4.25 };
  overlay.dispatchEvent(new DetailEvent('position', position));
  overlay.pause();
  assert.equal(events.at(-1).position.audioSeconds, 4.25);
  assert.ok(events.every(event => event.type === 'readAlong'));
});

test('explicit chapter restart discards the saved audio clock instead of replaying its checkpoint', async () => {
  const overlay = new Overlay();
  const ctl = new ReadAlongController(() => {});
  let starts = 0;
  ctl.attach({ mediaOverlay: overlay, startMediaOverlay: async () => { starts++; overlay.state('playing'); } },
    { sectionIndex: 0, fragment: 'c#two', audioSrc: 'a.mp3', audioSeconds: 4.25, rate: 1.25 });
  await ctl.command('restart');
  assert.equal(starts, 1);
  assert.equal(overlay.calls.some(call => call?.[0] === 'restore'), false);
});

const chapters = [
  { id: 'OEBPS/chapter.xhtml', mediaOverlay: { href: 'OEBPS/overlay.smil' } },
  { id: 'OEBPS/interlude.xhtml' },
  { id: 'OEBPS/second.xhtml', mediaOverlay: { href: 'OEBPS/second.smil' } },
];

test('previous chapter crosses un-narrated spine sections backwards rather than returning to the current chapter', async () => {
  const { overlay, audios } = engine(undefined, chapters);
  await overlay.start(2);
  audios.at(-1).dispatchEvent(new Event('canplaythrough'));
  assert.equal(overlay.getPosition().sectionIndex, 2);
  overlay.prev(); await settle();
  audios.at(-1).dispatchEvent(new Event('canplaythrough'));
  assert.equal(overlay.getPosition().sectionIndex, 0);
  assert.equal(overlay.getPosition().fragment, 'OEBPS/chapter.xhtml#three');
});

test('start in an un-narrated interlude advances to the next narrated chapter', async () => {
  const { overlay, audios } = engine(undefined, chapters);
  await overlay.start(1);
  audios.at(-1).dispatchEvent(new Event('canplaythrough'));
  assert.equal(overlay.getPosition().fragment, 'OEBPS/second.xhtml#one');
  assert.equal(overlay.getPosition().sectionIndex, 2);
});

test('ending an audio asset advances across an un-narrated chapter and end-of-book stays terminal', async () => {
  const { overlay, audios } = engine(undefined, chapters);
  await overlay.start(0);
  overlay.next(); await settle(); overlay.next(); await settle();
  audios.at(-1).dispatchEvent(new Event('ended')); await settle();
  audios.at(-1).dispatchEvent(new Event('canplaythrough'));
  assert.equal(overlay.getPosition().sectionIndex, 2);
  const states = [];
  overlay.addEventListener('state', event => states.push(event.detail));
  audios.at(-1).dispatchEvent(new Event('ended')); await settle();
  assert.equal(states.at(-1), 'ended');
});
