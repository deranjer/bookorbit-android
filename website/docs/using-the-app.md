---
sidebar_position: 4
---

# Using the app

## Library, search, and collections

The library screen lists the books available on your server. From there you can:

- **Search** by title, author, or series.
- Filter with **smart scopes** — saved filters your server administrator or you have defined.
- Browse **collections**, **authors**, and **series** pages.
- Open a book's **detail page** for metadata, description, and available formats.

## Reading ebooks

BookOrbit's reader is built on [foliate.js](https://github.com/johnfactotum/foliate-js) and
supports EPUB. It syncs reading progress (via CFI) back to your server, so you can pick up where
you left off on another device or in the web client. The reader includes adjustable themes, font
size, and layout settings.

## Listening to audiobooks

Audiobook playback uses Media3/ExoPlayer, with:

- Background playback via a foreground service, so audio continues when you leave the app.
- Media-style notification controls (play/pause, skip, seek).
- Playback speed control and sleep timer.
- Progress sync back to the server, with a **Continue Listening** shortcut for resuming quickly.

## Downloads and offline use

Books and audiobooks can be **downloaded** for offline access. Downloaded files are stored
locally on your device; you can manage or remove them from the downloads screen. Deleting a
download only removes the local copy — it does not affect the book on your server.

## Account and data

See the [privacy policy](https://deranjer.github.io/bookorbit-android/privacy.html) for details
on what data the app handles and where it's stored. In short: everything goes to the server you
configure, and nothing is sent to the developer.
