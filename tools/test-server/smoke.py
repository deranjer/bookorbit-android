"""Authenticated integration checks against only our loopback Docker fixture server."""
import json
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = 'http://127.0.0.1:6264/api/v1'
env = dict(line.split('=', 1) for line in (ROOT / '.local/test-server.env').read_text().splitlines() if '=' in line)
account_file = ROOT / '.local/test-account.json'
if not account_file.exists():
    with account_file.open('x') as stream:
        account_file.chmod(0o600)
        json.dump(dict(username='client-dev', password='Dev-' + secrets.token_urlsafe(24) + 'aA7'), stream)
account = json.loads(account_file.read_text())


def call(method, path, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(BASE + path, data=data, method=method,
        headers={'Content-Type': 'application/json', **(headers or {})})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def main():
    status, setup = call('GET', '/auth/setup-status')
    assert status == 200, f'Server not ready: {status}'
    status, login = call('POST', '/auth/setup', dict(account, name='Client Development', email='client@example.test'),
                         {'x-setup-token': env['SETUP_BOOTSTRAP_TOKEN']})
    if status == 409:
        status, login = call('POST', '/auth/login', account)
    assert status in (200, 201) and login.get('accessToken'), f'Local authentication failed: HTTP {status}'
    headers = {'Authorization': 'Bearer ' + login['accessToken']}
    status, libraries = call('GET', '/libraries', headers=headers)
    assert status == 200
    library = next((item for item in libraries if item['name'] == 'Client fixtures'), None)
    if library is None:
        status, library = call('POST', '/libraries', dict(name='Client fixtures', icon='BookOpen', folders=['/books'],
            watch=False, organizationMode='book_per_folder', fileWriteEnabled=False), headers)
        assert status == 201, f'Fixture library creation failed: HTTP {status}'
    status, scan = call('POST', f"/scanner/libraries/{library['id']}/scan", headers=headers)
    assert status == 202 or (status == 409 and 'scan is already running' in scan.get('message', '')), f'Fixture scan failed: HTTP {status}'
    deadline = time.monotonic() + 90
    while True:
        status, history = call('GET', f"/scanner/libraries/{library['id']}/scan-history", headers=headers)
        assert status == 200
        scan_finished = bool(history) and history[0]['status'] == 'completed'
        status, books = call('POST', '/books/query', dict(page=0, size=20), headers)
        assert status == 201
        items = books.get('items', [])
        detail_rows = []
        for item in items:
            status, detail = call('GET', f"/books/{item['id']}", headers=headers)
            assert status == 200, 'Book detail contract failed'
            detail_rows.append(detail)
        paired = next((book for book in detail_rows if book['title'] == 'First Orbit'), None)
        many_tracks = next((book for book in detail_rows if book['title'] == '75 Track Orbit'), None)
        if (scan_finished and len(items) >= 5 and paired
                and many_tracks and len([file for file in many_tracks['files'] if file['format'] == 'mp3']) == 75
                and len([file for file in paired['files'] if file['format'] == 'mp3']) == 2
                and len([file for file in paired['files'] if file['format'] == 'epub']) == 2):
            break
        assert time.monotonic() < deadline, 'Fixture scan did not finish within 90 seconds'
        time.sleep(1)
    formats = {file['format'] for file in paired['files']}
    comic_contracts = []
    for book in detail_rows:
        for file in book['files']:
            if file['format'] != 'cbz':
                continue
            status, pages = call('GET', f"/cbz/files/{file['id']}/pages", headers=headers)
            assert status == 200 and pages['pageCount'] > 0, 'Selected CBZ page list failed'
            request = urllib.request.Request(BASE + f"/cbz/files/{file['id']}/pages/0", headers=headers)
            with urllib.request.urlopen(request, timeout=30) as response:
                assert response.status == 200 and response.headers['Content-Type'].startswith('image/'), 'Selected CBZ image failed'
                assert response.read(8) == b'\x89PNG\r\n\x1a\n', 'Expected synthetic PNG comic page'
            comic_contracts.append(dict(bookId=book['id'], fileId=file['id'], pageCount=pages['pageCount'],
                readerPath=f"/read/{book['id']}/{file['id']}?format=cbz"))
    assert comic_contracts, 'Missing synthetic CBZ'
    contract_dir = ROOT / '.local/contracts'
    contract_dir.mkdir(parents=True, exist_ok=True)
    (contract_dir / 'cbz-reader.json').write_text(json.dumps(comic_contracts, indent=2) + '\n')
    overlay_book = next(book for book in detail_rows if book['title'] == 'Read Along Orbit')
    overlay_file = next(file for file in overlay_book['files'] if file['format'] == 'epub')
    assert overlay_file.get('mediaOverlay', {}).get('available'), 'Server did not recognize the SMIL fixture'
    assert overlay_file['sizeBytes'] == (ROOT / '.local/fixtures/Read Along Orbit/Read Along Orbit.epub').stat().st_size
    assert overlay_file['mediaOverlay']['durationSeconds'] == 20, 'Stale overlay metadata after scan'
    chapter_book = next(book for book in detail_rows if book['title'] == 'Read Along Chapters')
    chapter_file = next(file for file in chapter_book['files'] if file['format'] == 'epub')
    assert chapter_file['mediaOverlay']['available'] and chapter_file['mediaOverlay']['durationSeconds'] == 40
    percent_asset = urllib.request.Request(BASE + f"/epub/{chapter_book['id']}/file/OEBPS/art%20100%2525.svg?fileId={chapter_file['id']}", headers=headers)
    with urllib.request.urlopen(percent_asset, timeout=30) as response:
        with zipfile.ZipFile(ROOT / '.local/fixtures/Read Along Chapters/Read Along Chapters.epub') as original:
            assert response.read() == original.read('OEBPS/art 100%.svg')
    many_tracks = next(book for book in detail_rows if book['title'] == '75 Track Orbit')
    assert len([file for file in many_tracks['files'] if file['format'] == 'mp3']) == 75
    assert {'epub', 'mp3'} <= formats, f'Paired fixture lost a format: {formats}'
    assert paired.get('description') and paired.get('authors'), 'Card description/author missing'
    epub = next(file for file in paired['files'] if file['format'] == 'epub')
    for edition in (file for file in paired['files'] if file['format'] == 'epub'):
        status, info = call('GET', f"/epub/{paired['id']}/info?fileId={edition['id']}", headers=headers)
        assert status == 200 and info['manifest'] and info['containerPath']
        asset = 'OEBPS/chapter.xhtml'
        request = urllib.request.Request(BASE + f"/epub/{paired['id']}/file/{asset}?fileId={edition['id']}", headers=headers)
        with urllib.request.urlopen(request, timeout=30) as response:
            chapter_bytes = response.read()
        with zipfile.ZipFile(ROOT / '.local/fixtures/First Orbit' / edition['filename']) as original:
            assert chapter_bytes == original.read(asset), 'EPUB stream crossed edition identity'
    progress = dict(cfi='epubcfi(/6/2!/4/2)', percentage=10)
    status, _ = call('POST', f"/books/files/{epub['id']}/progress", progress, headers)
    assert status in (200, 201, 204), f'Progress route rejected independent client: HTTP {status}'
    status, saved = call('GET', f"/books/files/{epub['id']}/progress", headers=headers)
    assert status == 200 and saved['percentage'] == 10
    status, manifest = call('GET', f"/audiobooks/{paired['id']}/manifest", headers=headers)
    assert status == 200 and manifest.get('revision'), 'Audiobook manifest missing'
    audio_ids = {file['id'] for file in paired['files'] if file['format'] == 'mp3'}
    assert len(audio_ids) == 2 and {asset['fileId'] for asset in manifest['assets']} == audio_ids
    def check_range(path, expected):
        request = urllib.request.Request(BASE + path, headers={**headers, 'Range': 'bytes=0-31'})
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read()
            assert response.status == 206 and len(data) == 32
            assert response.headers['Content-Range'].startswith('bytes 0-31/')
            assert data == expected[:32], 'Server returned bytes from the wrong file'
    status, overlay_playlist = call('GET', f"/epub/{chapter_book['id']}/media-overlay?fileId={chapter_file['id']}", headers=headers)
    assert status == 200 and overlay_playlist['bookId'] == chapter_book['id'] and overlay_playlist['fileId'] == chapter_file['id']
    assert len(overlay_playlist['items']) == 4 and {item['sectionIndex'] for item in overlay_playlist['items']} == {0, 2}
    assert {item['textFragment'] for item in overlay_playlist['items']} == {'one', 'two'}
    with zipfile.ZipFile(ROOT / '.local/fixtures/Read Along Chapters/Read Along Chapters.epub') as original:
        for resource in overlay_playlist['resources']:
            asset_path = urllib.parse.quote(resource['href'], safe='/')
            check_range(f"/epub/{chapter_book['id']}/media-overlay/file/{asset_path}?fileId={chapter_file['id']}", original.read(resource['href']))
    fixture_root = ROOT / '.local/fixtures/First Orbit'
    for text_file in (file for file in paired['files'] if file['format'] == 'epub'):
        check_range(f"/books/files/{text_file['id']}/serve", (fixture_root / text_file['filename']).read_bytes())
    other_epub = next(file for file in paired['files'] if file['format'] == 'epub' and file['id'] != epub['id'])
    status, _ = call('POST', f"/books/files/{other_epub['id']}/progress", dict(cfi='epubcfi(/6/2!/4/4)', percentage=35), headers)
    assert status in (200, 201, 204)
    status, other_saved = call('GET', f"/books/files/{other_epub['id']}/progress", headers=headers)
    assert status == 200 and other_saved['percentage'] == 35
    status, first_saved = call('GET', f"/books/files/{epub['id']}/progress", headers=headers)
    assert status == 200 and first_saved['percentage'] == 10, 'Edition progress was conflated'
    status, guide_status = call('GET', f"/books/{paired['id']}/story-guide?fileId={epub['id']}", headers=headers)
    assert status == 200 and guide_status['fileId'] == epub['id'], 'Story Guide status contract failed'
    assert 'allowed' in guide_status['build'] and 'canManage' in guide_status
    status, denial = call('GET', f"/mobile/books/files/{epub['id']}/story-guide?format=1", headers=headers)
    assert status == 403, 'Production-mode test server must retain attestation protection'
    # Empty DTOs cannot invoke a provider. Confirm mobile AI gates without any book text/photo.
    status, app_info = call('GET', '/app-info', headers=headers)
    assert status == 200 and {'reading-help', 'vision-scan'} <= set(app_info.get('features', []))
    for route in ('reading-help', 'vision-scan'):
        status, _ = call('POST', f'/mobile/{route}', {}, headers)
        assert status == 403, f'Production {route} gate unexpectedly accepted independent client'
    for asset in manifest['assets']:
        file = next(file for file in paired['files'] if file['id'] == asset['fileId'])
        check_range(f"/audiobooks/{paired['id']}/assets/{asset['assetId']}/content", (fixture_root / file['filename']).read_bytes())
    contracts = ROOT / '.local/contracts'
    contracts.mkdir(exist_ok=True)
    (contracts / 'book-detail.json').write_text(json.dumps(paired, indent=2))
    (contracts / 'audiobook-manifest.json').write_text(json.dumps(manifest, indent=2))
    (contracts / 'story-guide-status.json').write_text(json.dumps(guide_status, indent=2))
    (contracts / 'epub-overlay-playlist.json').write_text(json.dumps(overlay_playlist, indent=2))
    print('PASS: local login, library, descriptions, two EPUB editions with isolated progress, exact audio IDs and HTTP Range bytes.')
    print('Test credentials: .local/test-account.json (not printed). No production server was used.')


if __name__ == '__main__':
    main()
