# Offline dictionary support

No dictionary data is bundled with the APK. Downloads require an explicit user action.
The catalog initially offers English-Russian and Russian-English FreeDict/WikDict
2025.11.23 dictionaries maintained by Karl Bartel, based on Wiktionary via DBnary.
Their source packages identify the data license as CC BY-SA 3.0 Unported:
https://creativecommons.org/licenses/by-sa/3.0/legalcode

The source TEI header and complete COPYING file are stored with each installed
dictionary and viewable in the app. The app creates a normalized SQLite lookup
index; the upstream archive URL, release and SHA-512 remain attached to that index.
No BookOrbit account credentials or lookup words are sent to dictionary servers.

Archive support uses Apache Commons Compress 1.28.0 (Apache License 2.0) and
XZ for Java 1.10 (Zero-Clause BSD). These libraries are separate from dictionary data.
https://commons.apache.org/proper/commons-compress/
https://tukaani.org/xz/java.html

XZ for Java 1.10 license:

Copyright (C) The XZ for Java authors and contributors

Permission to use, copy, modify, and/or distribute this
software for any purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL
WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL
THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR
CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
