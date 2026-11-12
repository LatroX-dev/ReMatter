#!/usr/bin/env python3
"""Draw the opaque Ice crystal and two engineered upgrade icons from reproducible SVG geometry.

The SVGs are the sources; rendered.json ties each PNG to its source and allows --check without CairoSVG.
Changed drawings need CairoSVG to render, as with the Primal drawings. UPGRADE_LOOK switches the highlighted
material inserts (recommended) to neutral graphite without changing the silhouette or the recipe.
"""
from pathlib import Path
import argparse
import hashlib
import json
from family_palette import (BASE, COPPER, COPPER_DARK, COPPER_LIGHT, HIGHLIGHT, IVORY, PORT_METAL,
                            SEAM, SHADE, STEEL, STEEL_DEEP, STEEL_SHADE, TRIM, lighten, scale)
from machine_palette import RGB, hex_color
from check_assets import strip_ancillary_chunks
from generated_files import GeneratedFiles

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'artwork/components'
TEX = ROOT / 'src/main/resources/assets/rematter/textures/item'
RECORD = ART / 'illustrated-rendered.json'
UPGRADE_LOOKS = ('material', 'graphite')
UPGRADE_LOOK = 'material'
INK = SEAM


def poly(points, fill, stroke=None, width=0):
    edge = f' stroke="{hex_color(stroke)}" stroke-width="{width}" stroke-linejoin="round"' if stroke else ''
    return f'<polygon points="{points}" fill="{hex_color(fill)}"{edge}/>'


def line(points, colour, width):
    return f'<polyline points="{points}" fill="none" stroke="{hex_color(colour)}" stroke-width="{width}" stroke-linejoin="round" stroke-linecap="round"/>'


def svg(parts):
    return '<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">\n' + '\n'.join(parts) + '\n</svg>\n'


def ice():
    hue = RGB['ice']
    # The original rough shard silhouette, now opaque: large middle facets retain the exact Ice hue.
    edge = scale(hue, .23)
    parts = [poly('17,54 34,25 56,12 78,15 105,31 112,68 107,94 87,114 48,116 24,96', hue, edge, 5),
             poly('20,54 37,28 57,16 78,19 101,33 61,39', lighten(hue, .36)),
             poly('20,54 61,39 47,112 28,94', scale(hue, .82)),
             poly('61,39 101,33 108,68 103,92 86,110 71,83', hue),
             poly('61,39 71,83 86,110 49,112', hue),
             poly('101,33 108,68 103,92 86,110 91,75', scale(hue, .7)),
             poly('30,52 42,42 46,88 38,78', lighten(hue, .58)),
             line('17,54 34,25 56,12 78,15 105,31 112,68 107,94 87,114 48,116 24,96 17,54', edge, 5)]
    return svg(parts)


def board():
    parts = [poly('11,61 60,33 116,64 116,76 69,104 11,74', BASE, INK, 4),
             poly('12,62 60,35 114,65 68,92', PORT_METAL),
             poly('12,63 68,93 68,101 12,73', STEEL_DEEP),
             poly('68,93 114,66 114,75 68,101', SHADE),
             line('18,61 60,38 108,65', STEEL_SHADE, 2.3),
             line('18,67 67,94', STEEL_SHADE, 1.4)]
    # Copper contacts are material, independent of a recipe-crystal identity.
    for x, y in ((80, 87), (94, 79), (108, 71)):
        parts += [poly(f'{x},{y} {x+8},{y-4} {x+8},{y+1} {x},{y+5}', COPPER, INK, 2.7),
                  line(f'{x+1},{y} {x+7},{y-3}', COPPER_LIGHT, 1.6)]
    return parts


def pressure():
    parts = board()
    # A raised pressure cap in a metal yoke; no unrelated crystal is inserted into its recipe.
    parts += [poly('32,57 56,43 89,61 65,75', STEEL, INK, 2),
              poly('32,57 65,75 65,82 32,64', STEEL_SHADE, INK, 1.5),
              poly('65,75 89,61 89,68 65,82', STEEL_DEEP, INK, 1.5),
              poly('49,48 61,41 75,49 63,56', TRIM, INK, 2.5),
              poly('49,48 63,56 63,69 49,61', COPPER if UPGRADE_LOOK == 'material' else HIGHLIGHT, INK, 2),
              poly('63,56 75,49 75,62 63,69', COPPER_DARK if UPGRADE_LOOK == 'material' else STEEL_DEEP, INK, 2),
              line('54,47 61,44 69,48', IVORY, 1.5)]
    return svg(parts)


def parallel():
    parts = board()
    # Three separate material channels indicate parallel work at inventory scale.
    for x, y in ((28, 63), (43, 54), (58, 45)):
        hue = TRIM if UPGRADE_LOOK == 'material' else STEEL_SHADE
        parts += [poly(f'{x},{y} {x+9},{y-5} {x+39},{y+12} {x+30},{y+17}', hue, INK, 2.7),
                  poly(f'{x},{y} {x+30},{y+17} {x+30},{y+22} {x},{y+5}', STEEL_DEEP, INK, 1.5),
                  line(f'{x+4},{y} {x+31},{y+15}', STEEL, 1.6)]
    return svg(parts)


def digest(content):
    return hashlib.sha256(content).hexdigest()


def generate(check=False):
    out = GeneratedFiles(ROOT, check)
    record = json.loads(RECORD.read_text()) if RECORD.is_file() else {}
    expected = {}
    for name, source in (('ice_matrix_crystal', ice()), ('pressure_module', pressure()), ('parallel_upgrade', parallel())):
        source = source.encode()
        out.bytes(ART / (name + '.svg'), source)
        path = TEX / (name + '.png')
        old = record.get(name, {})
        if old.get('svg') != digest(source) or not path.is_file() or old.get('png') != digest(path.read_bytes()):
            if check:
                raise ValueError(f'Illustrated source or rendered PNG differs: {name}')
            import cairosvg
            cairosvg.svg2png(bytestring=source, write_to=str(path), output_width=128, output_height=128)
            strip_ancillary_chunks(path)
        expected[name] = {'svg': digest(source), 'png': digest(path.read_bytes())}
    out.json(RECORD, expected)
    print(f"{'Verified' if check else 'Generated'} 3 illustrated icons ({UPGRADE_LOOK} upgrade inserts)")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    generate(parser.parse_args().check)
