# Offline dictionary support

No dictionary data is bundled with the APK. Downloads require an explicit user action.
The bundled catalog.json is release metadata, not dictionary data. It was generated
from https://freedict.org/freedict-database.json on 2026-11-26, containing 305
language pairs with source archive URL, version, size and SHA-512. The SHA-256
of the original upstream metadata was:
08078a2c28bb06dac716ca4b0e785621ed8bfde7c0a4aac15236c3279e2ca060
Only validated HTTPS source releases within import safety bounds are offered.
Catalog refresh is explicit and its result is retained for offline use.

Each downloaded dictionary has its own authorship and license. For example,
English-Russian and Russian-English FreeDict/WikDict 2025.11.23 are maintained
by Karl Bartel and based on Wiktionary via DBnary. Their source packages identify
the data license as CC BY-SA 3.0 Unported:
https://creativecommons.org/licenses/by-sa/3.0/legalcode
French-English 0.4.1 and Spanish-English 0.3.1 instead identify GPL 2.0 or later.
Do not apply a single license or attribution to all catalog entries.

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
