"""Generate tiny original fixtures, never copies of the user's library."""
from pathlib import Path
import io
import os
import struct
import subprocess
import wave
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[2] / '.local' / 'fixtures'


def epub(path, title):
    with zipfile.ZipFile(path, 'w') as book:
        book.writestr('mimetype', 'application/epub+zip', compress_type=zipfile.ZIP_STORED)
        book.writestr('META-INF/container.xml', '''<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
</rootfiles></container>''')
        book.writestr('OEBPS/content.opf', f'''<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="id">urn:bookorbit-client:fixture:{path.stem}</dc:identifier>
<dc:title>{title}</dc:title><dc:creator>Client Test Author</dc:creator><dc:language>en</dc:language>
<dc:description>&lt;p&gt;An original &lt;strong&gt;offline fixture&lt;/strong&gt; for our client.&lt;/p&gt;</dc:description>
<meta property="dcterms:modified">2026-11-22T00:00:00Z</meta></metadata>
<manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest>
<spine><itemref idref="chapter"/></spine></package>''')
        book.writestr('OEBPS/chapter.xhtml', f'''<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>{title}</title></head><body>
<h1 id="first">First chapter</h1><p id="one">A small original book for testing offline reading.</p>
<p id="two">Page position and audio position are separate coordinates.</p></body></html>''')
        book.writestr('OEBPS/nav.xhtml', '''<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Contents</title></head><body><nav epub:type="toc"><ol>
<li><a href="chapter.xhtml#first">First chapter</a></li></ol></nav></body></html>''')


def png(width=600, height=800):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    pixels = b''.join(b'\0' + bytes((240, 230, 210)) * width for _ in range(height))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b'')


def create():
    paired = ROOT / 'First Orbit'
    paired.mkdir(parents=True, exist_ok=True)
    epub(paired / 'First Orbit.epub', 'First Orbit')
    epub(paired / 'Second edition.epub', 'First Orbit — alternate edition')
    wav = io.BytesIO()
    with wave.open(wav, 'wb') as audio:
        audio.setparams((1, 2, 22050, 0, 'NONE', 'not compressed'))
        audio.writeframes(b'\0\0' * 22050 * 20)
    # Use the same pinned test image's encoder; no host multimedia tools or network access.
    image = 'ghcr.io/bookorbit/bookorbit:3.9.0@sha256:79cd9c66c990a82944514e3e82d5be5987f21eda96cb37a378ed1484043716fc'
    encoded = subprocess.run([
        'docker', 'run', '--rm', '-i', '--network=none', '--read-only', '--cap-drop=ALL',
        '--user', f'{os.getuid()}:{os.getgid()}', '--entrypoint', 'ffmpeg', image,
        '-hide_banner', '-loglevel', 'error', '-i', 'pipe:0', '-f', 'mp3', 'pipe:1',
    ], input=wav.getvalue(), stdout=subprocess.PIPE, check=True).stdout
    # Equal-sized tracks deliberately exercise exact file identity, not size heuristics.
    for name in ('01 First Orbit.mp3', '02 First Orbit.mp3'):
        (paired / name).write_bytes(encoded)
    read_along = ROOT / 'Read Along Orbit'
    read_along.mkdir(exist_ok=True)
    overlay_book = read_along / 'Read Along Orbit.epub'
    epub(overlay_book, 'Read Along Orbit')
    with zipfile.ZipFile(overlay_book) as book:
        payload = {name: book.read(name) for name in book.namelist()}
    opf = payload['OEBPS/content.opf'].decode()
    opf = opf.replace('<metadata ', '<metadata ').replace('</metadata>',
        '<meta property="media:duration">00:00:20.000</meta><meta property="media:active-class">read-along-active</meta></metadata>')
    opf = opf.replace('id="chapter" href=', 'id="chapter" media-overlay="overlay" href=')
    opf = opf.replace('</manifest>', '<item id="overlay" href="overlay.smil" media-type="application/smil+xml"/>'
        '<item id="audio" href="audio.mp3" media-type="audio/mpeg"/></manifest>')
    payload['OEBPS/content.opf'] = opf.encode()
    payload['OEBPS/audio.mp3'] = encoded
    payload['OEBPS/overlay.smil'] = b'''<?xml version="1.0" encoding="UTF-8"?>
<smil xmlns="http://www.w3.org/ns/SMIL" version="3.0"><body><seq>
<par><text src="chapter.xhtml#one"/><audio src="audio.mp3" clipBegin="0s" clipEnd="10s"/></par>
<par><text src="chapter.xhtml#two"/><audio src="audio.mp3" clipBegin="10s" clipEnd="20s"/></par>
</seq></body></smil>'''
    with zipfile.ZipFile(overlay_book, 'w') as book:
        for name, data in payload.items():
            book.writestr(name, data, compress_type=zipfile.ZIP_STORED)
    chapters = ROOT / 'Read Along Chapters'
    chapters.mkdir(exist_ok=True)
    chapter_payload = dict(payload)
    opf = opf.replace('Read Along Orbit', 'Read Along Chapters').replace('00:00:20.000', '00:00:40.000')
    opf = opf.replace('</manifest>',
        '<item id="art" href="art%20100%25.svg" media-type="image/svg+xml"/>'
        '<item id="interlude" href="interlude.xhtml" media-type="application/xhtml+xml"/>'
        '<item id="second" href="second.xhtml" media-type="application/xhtml+xml" media-overlay="second-overlay"/>'
        '<item id="second-overlay" href="second.smil" media-type="application/smil+xml"/>'
        '<item id="second-audio" href="second.mp3" media-type="audio/mpeg"/></manifest>')
    opf = opf.replace('<itemref idref="chapter"/>', '<itemref idref="chapter"/><itemref idref="interlude"/><itemref idref="second"/>')
    chapter_payload['OEBPS/content.opf'] = opf.encode()
    chapter_payload['OEBPS/art 100%.svg'] = b'<svg xmlns="http://www.w3.org/2000/svg" width="220" height="80"><rect width="220" height="80" fill="#2468ab"/><text x="15" y="48" fill="white" font-size="24">Original blue 100%</text></svg>'
    chapter_payload['OEBPS/chapter.xhtml'] = payload['OEBPS/chapter.xhtml'].replace(b'</head>',
        b'<style>html,body{background:white;border:4px solid red;box-shadow:0 0 15px #888}p{background:white}</style></head>').replace(b'</body>',
        b'<img src="art%20100%25.svg" alt="Original blue illustration"/></body>')
    chapter_payload['OEBPS/interlude.xhtml'] = b'<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Silent interlude</title></head><body><h1>Silent interlude</h1><p>This section deliberately has no narration.</p></body></html>'
    chapter_payload['OEBPS/second.xhtml'] = b'<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Second chapter</title></head><body><h1>Second chapter</h1><p id="one">A new chapter uses another audio asset.</p><p id="two">The saved clock belongs to this exact resource, not a percentage.</p></body></html>'
    chapter_payload['OEBPS/second.mp3'] = encoded
    chapter_payload['OEBPS/second.smil'] = payload['OEBPS/overlay.smil'].replace(b'chapter.xhtml', b'second.xhtml').replace(b'audio.mp3', b'second.mp3')
    chapter_payload['OEBPS/nav.xhtml'] = payload['OEBPS/nav.xhtml'].replace(b'</ol>', b'<li><a href="interlude.xhtml">Silent interlude</a></li><li><a href="second.xhtml">Second chapter</a></li></ol>')
    with zipfile.ZipFile(chapters / 'Read Along Chapters.epub', 'w') as book:
        for name, data in chapter_payload.items():
            book.writestr(name, data, compress_type=zipfile.ZIP_STORED)
    many_tracks = ROOT / '75 Track Orbit'
    many_tracks.mkdir(exist_ok=True)
    epub(many_tracks / '75 Track Orbit.epub', '75 Track Orbit')
    for index in range(1, 76):
        (many_tracks / f'{index:02d} Orbit.mp3').write_bytes(encoded)
    legacy = paired / 'First Orbit.wav'
    if legacy.exists():
        legacy.unlink()  # Remove only our obsolete generated fixture, never user media.
    comic = ROOT / 'Orbit Comic'
    comic.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(comic / 'Orbit Comic.cbz', 'w') as book:
        book.writestr('001.png', png())
        book.writestr('ComicInfo.xml', '<ComicInfo><Title>Orbit Comic</Title><Writer>Client Test Author</Writer><Summary>Synthetic comic fixture.</Summary></ComicInfo>')
    # The scanner skips unchanged directory mtimes; overwriting an existing file
    # alone does not update its parent directory on Linux.
    for directory in (paired, read_along, chapters, many_tracks, comic):
        os.utime(directory, None)


if __name__ == '__main__':
    create()
    print('Created text/audio editions, a 75-track book, EPUB/SMIL read-along and CBZ fixtures in .local/fixtures/.')
