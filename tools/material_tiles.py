"""Shared texture tiles for ReMatter's 3D material surfaces."""

import math
import struct
import zlib


TILE = 128


def png_bytes(width, height, pixel):
    def chunk(kind, data):
        return (struct.pack('>I', len(data)) + kind + data
                + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff))

    data = b''.join(b'\0' + bytes(value for x in range(width) for value in pixel(x, y))
                    for y in range(height))
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(data, 9)) + chunk(b'IEND', b''))


def write_png(path, width, height, pixel):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png_bytes(width, height, pixel))


def soft_light(x, y):
    """A broad upper-left reflection with a darker lower rim."""
    u, v = x / (TILE - 1), y / (TILE - 1)
    reflection = math.exp(-((u - .28) / .46) ** 2 - ((v - .27) / .35) ** 2)
    return 19 * reflection + 11 - 17 * v - 10 * u


def crystal_pixel(rgb, x, y, *, emitting=False):
    """Shade the faces separately so a soft reflection preserves the cut."""
    u, v = x / (TILE - 1), y / (TILE - 1)
    seam = u + .18 * v
    facet = .88 if seam < .28 else 1.05 if seam < .64 else .80 if seam < .94 else .66
    reflection = max(0, 1 - abs(seam - .41) / .38) * (1 - v * .54)
    light = (13 if emitting else 5) + reflection * (48 if emitting else 40)
    if emitting:
        facet = max(facet, .95)
    if x < 3 or y < 3:
        light += 16
    elif x >= TILE - 4 or y >= TILE - 4:
        light -= 14
    return tuple(max(0, min(255, round(channel * facet + light))) for channel in rgb) + (255,)
