#!/usr/bin/env python3
"""Fetch hash-pinned upstream decoder sources, never an opaque third-party AAR."""
import argparse
import hashlib
import json
import re
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def verified_download(url, expected, destination, maximum=64 * 1024 * 1024):
    if destination.exists() and hashlib.sha256(destination.read_bytes()).hexdigest() == expected:
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_suffix(destination.suffix + '.part')
    try:
        with urllib.request.urlopen(url, timeout=90) as response, partial.open('wb') as output:
            size = 0
            digest = hashlib.sha256()
            while True:
                chunk = response.read(64 * 1024)
                if not chunk:
                    break
                size += len(chunk)
                if size > maximum:
                    raise ValueError('Source download exceeds size bound')
                digest.update(chunk)
                output.write(chunk)
        if digest.hexdigest() != expected:
            raise ValueError(f'Source checksum mismatch: {destination.name}')
        partial.replace(destination)
    finally:
        partial.unlink(missing_ok=True)
    return destination


def extract_sources(archive, destination, prefix):
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as source:
        members = source.getmembers()
        if sum(member.size for member in members) > 256 * 1024 * 1024:
            raise ValueError('Source archive exceeds extraction bound')
        for member in members:
            path = PurePosixPath(member.name)
            if path.is_absolute() or '..' in path.parts or not path.parts or path.parts[0] != prefix:
                raise ValueError('Invalid source archive path')
            if not (member.isfile() or member.isdir()):
                raise ValueError('Source archive contains a link or special file')
            target = destination.joinpath(*path.parts[1:])
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.extractfile(member) as input_file, target.open('wb') as output:
                    shutil.copyfileobj(input_file, output)
                target.chmod(member.mode & 0o777)


def prepare(output):
    lock_path = ROOT / 'software-audio/sources.lock.json'
    lock = json.loads(lock_path.read_text())
    catalog = (ROOT / 'gradle/libs.versions.toml').read_text()
    version = re.search(r'^media3\s*=\s*"([^"]+)"', catalog, re.MULTILINE)
    if not version or version.group(1) != lock['media3Version']:
        raise ValueError('Update the pinned Media3 decoder sources together with the app Media3 version')
    cache = ROOT / '.local/native-audio-sources'
    for relative, checksum in lock['media3Files'].items():
        target = 'src/' + relative.split('/src/', 1)[1] if '/src/' in relative else relative
        verified_download(lock['media3Base'] + relative, checksum, output / 'media3' / target, 1024 * 1024)
    archive = verified_download(lock['ffmpegArchive'], lock['ffmpegSha256'], cache / f"ffmpeg-{lock['ffmpegVersion']}.tar.xz")
    signature = verified_download(lock['ffmpegArchive'] + '.asc', lock['signatureSha256'], archive.with_suffix('.xz.asc'))
    key = verified_download(lock['signingKey'], lock['signingKeySha256'], cache / 'ffmpeg-devel.asc')
    with tempfile.TemporaryDirectory(prefix='gpg-', dir=cache) as home:
        subprocess.run(['gpg', '--batch', '--homedir', home, '--import', str(key)], check=True, capture_output=True)
        result = subprocess.run(['gpg', '--batch', '--homedir', home, '--status-fd', '1', '--verify', str(signature), str(archive)],
                                check=True, capture_output=True, text=True)
        if '[GNUPG:] VALIDSIG ' + lock['signingFingerprint'] + ' ' not in result.stdout:
            raise ValueError('Unexpected FFmpeg signing fingerprint')
    source = output / 'ffmpeg'
    stamp = output / 'ffmpeg-source.sha256'
    if not stamp.exists() or stamp.read_text().strip() != lock['ffmpegSha256'] or not (source / 'configure').exists():
        extract_sources(archive, source, f"ffmpeg-{lock['ffmpegVersion']}")
        stamp.write_text(lock['ffmpegSha256'] + '\n')
    notices = output / 'assets/software-audio'
    notices.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source / 'COPYING.LGPLv2.1', notices / 'COPYING.LGPLv2.1')
    shutil.copyfile(output / 'media3/LICENSE', notices / 'MEDIA3-LICENSE')
    shutil.copyfile(lock_path, notices / 'sources.lock.json')
    shutil.copyfile(ROOT / 'software-audio/NOTICE.md', notices / 'NOTICE.md')
    print(f"Verified Media3 {lock['media3Version']} + FFmpeg {lock['ffmpegVersion']} sources")


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    prepare(parser.parse_args().output.resolve())
