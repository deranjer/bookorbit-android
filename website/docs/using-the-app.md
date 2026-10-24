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
supports EPUB, MOBI, AZW3, FB2 and CBZ ebooks, plus a separate native PDF reader. It syncs reading progress (via CFI) back to your server, so you can pick up where
you left off on another device or in the web client. The reader includes adjustable themes, font
size, and layout settings.

In **Settings → Reading**, **Reader interface** defaults to **App**. You can opt into
**Server web reader** to use your server's familiar reading interface for ebooks, PDFs, and comics.
Web mode requires a connection to the server and uses the server reader's settings and progress
saving behavior. Downloaded files remain available through App mode. The floating web-reader
options button lets you reload, return to the app, or choose **Open in App** for the current book
without changing your saved preference.

**Full-screen reading** hides Android's status and navigation bars in either interface. It is
enabled by default; swipe from a screen edge to reveal the system bars temporarily, or disable it
in Settings → Reading. In App mode, tap the center of an ebook page to show or hide the reader
controls in either paginated or scrolling layouts.

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
