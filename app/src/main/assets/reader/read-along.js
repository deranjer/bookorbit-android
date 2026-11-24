/** Foreground EPUB/SMIL read-along. No fuzzy matching against unrelated external audio. */
export class ReadAlongController {
  #view;
  #overlay;
  #emit;
  #state = 'unavailable';
  #listeners = [];
  #rate = 1;
  #checkpoint = null;
  #lastPositionAt = 0;
  #nativeFragment = null;
  constructor(emit) { this.#emit = emit; }
  #publish(error = null) {
    this.#emit({ type: 'readAlong', available: !!this.#overlay, state: this.#state, rate: this.#rate,
      position: this.#checkpoint ? { ...this.#checkpoint, rate: this.#rate,
        completed: this.#state === 'ended' || this.#checkpoint.completed === true } : null, error });
  }
  attach(view, checkpoint = null) {
    this.detach();
    this.#view = view;
    this.#overlay = view?.mediaOverlay ?? null;
    this.#checkpoint = checkpoint;
    this.#rate = checkpoint?.rate >= 0.5 && checkpoint.rate <= 2 ? checkpoint.rate : 1;
    this.#state = this.#overlay ? 'ready' : 'unavailable';
    if (this.#overlay) {
      const listen = (name, fn) => {
        this.#overlay.addEventListener(name, fn);
        this.#listeners.push([name, fn]);
      };
      listen('state', event => { this.#state = event.detail; this.#publish(); });
      listen('position', event => {
        this.#checkpoint = event.detail;
        const now = Date.now();
        if (this.#state !== 'playing' || now - this.#lastPositionAt >= 1000) {
          this.#lastPositionAt = now; this.#publish();
        }
      });
      listen('error', () => { this.#overlay.pause(); this.#state = 'error'; this.#publish('audio'); });
    }
    this.#publish();
  }
  detach() {
    for (const [name, fn] of this.#listeners) this.#overlay?.removeEventListener(name, fn);
    this.#listeners = [];
    this.#overlay?.stop();
    this.#overlay = null;
    this.#view = null;
    this.#state = 'unavailable';
    this.#checkpoint = null;
    this.#nativeFragment = null;
  }
  followNative(position) {
    if (!this.#overlay) return;
    if (!position && this.#nativeFragment === null) return;
    this.#overlay.pause();
    const fragment = position?.fragment;
    if (fragment === this.#nativeFragment) return;
    this.#nativeFragment = fragment ?? null;
    if (typeof fragment === 'string' && fragment.length <= 8192)
      this.#overlay.dispatchEvent(new CustomEvent('highlight', { detail: { text: fragment } }));
    else this.#overlay.dispatchEvent(new CustomEvent('unhighlight'));
  }
  async command(action, rate) {
    if (!this.#overlay) return;
    try {
      switch (action) {
        case 'restart':
          this.#checkpoint = null;
          this.#state = 'ready';
          return this.command('start');
        case 'start':
          { const checkpoint = this.#state === 'ended' ? null : this.#checkpoint;
          this.#overlay.stop();
          this.#state = 'loading'; this.#publish();
          this.#overlay.setRate(this.#rate);
          if (checkpoint && !checkpoint.completed) await this.#overlay.restore(checkpoint);
          else await this.#view.startMediaOverlay();
          }
          break;
        case 'pause': if (this.#state === 'playing' || this.#state === 'loading') this.#overlay.pause(); break;
        case 'resume': this.#overlay.resume(); break;
        case 'stop': this.#overlay.stop(); break;
        case 'next': this.#overlay.next(); break;
        case 'prev': this.#overlay.prev(); break;
        case 'rate':
          if (!Number.isFinite(rate) || rate < 0.5 || rate > 2) return;
          this.#rate = rate; this.#overlay.setRate(rate); this.#publish();
          break;
      }
    } catch {
      this.#overlay.pause(); this.#state = 'error'; this.#publish('audio');
    }
  }
}
