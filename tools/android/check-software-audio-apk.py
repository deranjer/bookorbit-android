#!/usr/bin/env python3
"""Check that a built APK includes real software decoder libraries and source/license notices."""
import argparse
import json
from pathlib import Path
import struct
import zipfile

ARCHITECTURES = {'armeabi-v7a': (1, 40), 'arm64-v8a': (2, 183), 'x86': (1, 3), 'x86_64': (2, 62)}


def elf_load_alignments(data, expected):
    if data[:4] != b'\x7fELF' or data[5] != 1:
        raise ValueError('Expected a little-endian ELF library')
    if (data[4], struct.unpack_from('<H', data, 18)[0]) != expected:
        raise ValueError('Wrong ELF architecture')
    if data[4] == 2:
        offset = struct.unpack_from('<Q', data, 32)[0]
        entry_size, count = struct.unpack_from('<HH', data, 54)
        alignment_offset, alignment_format = 48, '<Q'
    else:
        offset = struct.unpack_from('<I', data, 28)[0]
        entry_size, count = struct.unpack_from('<HH', data, 42)
        alignment_offset, alignment_format = 28, '<I'
    alignments = []
    for index in range(count):
        position = offset + index * entry_size
        if struct.unpack_from('<I', data, position)[0] == 1:
            alignments.append(struct.unpack_from(alignment_format, data, position + alignment_offset)[0])
    if not alignments or any(value < 16384 for value in alignments):
        raise ValueError('JNI LOAD segments do not support 16 KiB pages')
    return alignments


def check(apk):
    result = {'apk': str(apk), 'libraries': []}
    with zipfile.ZipFile(apk) as archive, apk.open('rb') as raw:
        for abi, expected in ARCHITECTURES.items():
            info = archive.getinfo(f'lib/{abi}/libffmpegJNI.so')
            if not 100_000 < info.file_size < 30 * 1024 * 1024:
                raise ValueError('Missing or unexpectedly sized native decoder')
            alignments = elf_load_alignments(archive.read(info), expected)
            raw.seek(info.header_offset)
            header = raw.read(30)
            filename, extra = struct.unpack_from('<HH', header, 26)
            data_offset = info.header_offset + 30 + filename + extra
            if info.compress_type != zipfile.ZIP_STORED or data_offset % 16384:
                raise ValueError('JNI APK entry is not stored with 16 KiB ZIP alignment')
            result['libraries'].append({'abi': abi, 'bytes': info.file_size, 'loadAlignments': alignments, 'zipOffset': data_offset})
        for notice in ['COPYING.LGPLv2.1', 'MEDIA3-LICENSE', 'NOTICE.md', 'sources.lock.json']:
            if not archive.read('assets/software-audio/' + notice):
                raise ValueError('Missing software decoder license/source notice')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    print(json.dumps(check(parser.parse_args().apk), indent=2))
