#!/usr/bin/env python3
"""Check that shipped PNG textures carry only the chunks needed to decode them.

Image editors and Cairo add color profiles, resolution, background and text
metadata that Minecraft ignores. Without arguments this lists every PNG under
the asset directory with such chunks and fails; --strip removes them in place.
Pixel data is never re-encoded: IHDR, PLTE, tRNS and IDAT stay byte for byte.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import struct
import sys
import zlib

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets'
SIGNATURE = b'\x89PNG\r\n\x1a\n'
KEPT = (b'IHDR', b'PLTE', b'tRNS', b'IDAT', b'IEND')


def chunks(data):
    if not data.startswith(SIGNATURE):
        raise ValueError('not a PNG file')
    offset = len(SIGNATURE)
    while offset < len(data):
        length, = struct.unpack('>I', data[offset:offset + 4])
        kind = data[offset + 4:offset + 8]
        body = data[offset + 8:offset + 8 + length]
        crc, = struct.unpack('>I', data[offset + 8 + length:offset + 12 + length])
        if len(body) != length or zlib.crc32(kind + body) & 0xffffffff != crc:
            raise ValueError(f'damaged {kind.decode("latin-1")} chunk')
        yield kind, data[offset:offset + 12 + length]
        offset += 12 + length
        if kind == b'IEND':
            break
    if offset != len(data):
        raise ValueError('data after IEND')


def extra_chunks(path):
    return sorted({kind.decode('latin-1') for kind, _ in chunks(path.read_bytes()) if kind not in KEPT})


def strip_ancillary_chunks(path):
    """Rewrite a PNG without the chunks that do not affect its decoded pixels."""
    data = path.read_bytes()
    kept = b''.join(raw for kind, raw in chunks(data) if kind in KEPT)
    if SIGNATURE + kept != data:
        path.write_bytes(SIGNATURE + kept)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--strip', action='store_true', help='remove the extra chunks in place')
    args = parser.parse_args()
    images = sorted(ASSETS.rglob('*.png'))
    problems = []
    for path in images:
        try:
            extra = extra_chunks(path)
        except ValueError as error:
            problems.append(f'{path.relative_to(ROOT)}: {error}')
            continue
        if not extra:
            continue
        if args.strip:
            strip_ancillary_chunks(path)
        else:
            problems.append(f'{path.relative_to(ROOT)}: {", ".join(extra)}')
    if problems:
        print('PNG files with chunks that do not belong in shipped textures:', file=sys.stderr)
        print('\n'.join(problems), file=sys.stderr)
        print('Run python3 tools/check_assets.py --strip to remove them.', file=sys.stderr)
        sys.exit(1)
    print(('Stripped' if args.strip else 'Verified') + f' {len(images)} PNG textures; only decoding chunks remain.')


if __name__ == '__main__':
    main()
