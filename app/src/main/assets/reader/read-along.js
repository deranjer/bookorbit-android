/** Foreground EPUB/SMIL read-along. No fuzzy matching against unrelated external audio. */
export class ReadAlongController {
  #view;
  #overlay;
  #emit;
  #state = 'unavailable';
  #listeners = [];
  #rate = 1;
  constructor(emit) { this.#emit = emit; }
  #publish(error = null) {
    this.#emit({ type: 'readAlong', available: !!this.#overlay, state: this.#state, rate: this.#rate, error });
  }
  attach(view) {
    this.detach();
    this.#view = view;
    this.#overlay = view?.mediaOverlay ?? null;
    this.#state = this.#overlay ? 'ready' : 'unavailable';
    if (this.#overlay) {
      const listen = (name, fn) => {
        this.#overlay.addEventListener(name, fn);
        this.#listeners.push([name, fn]);
      };
      listen('state', event => { this.#state = event.detail; this.#publish(); });
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
  }
  async command(action, rate) {
    if (!this.#overlay) return;
    try {
      switch (action) {
        case 'start':
          this.#overlay.stop();
          this.#state = 'loading'; this.#publish();
          this.#overlay.setRate(this.#rate);
          await this.#view.startMediaOverlay();
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
