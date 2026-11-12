#!/usr/bin/env python3
"""Draw the opaque Ice crystal, the Matrix Cluster and two engineered upgrade icons from reproducible SVG geometry.

The SVGs are the sources; rendered.json ties each PNG to its source and allows --check without CairoSVG.
Changed drawings need CairoSVG to render, as with the Primal drawings. UPGRADE_LOOK switches the highlighted
material inserts (recommended) to neutral graphite without changing the silhouette or the recipe.
MATRIX_CLUSTER_LOOK selects how the four clear crystals of the Matrix Cluster are joined: grown from one root
(recommended), bound by an iron band, or fused two by two around an iron node.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
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
MATRIX_CLUSTER_LOOKS = ('grown', 'bound', 'lattice')
MATRIX_CLUSTER_LOOK = 'grown'
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


# One Matrix Crystal as the painted crystal icons show it, in the coordinates of the 128 px canvas: the body
# inside the contour, the foot it stands on, and its facets. A crystal of the cluster is drawn at about half the
# width, so the facets are wider here than on the single crystal: bevels and reflections keep their pixel weight.
GEM_FOOT = (64, 113)
GEM_BODY = ((58.5, 14), (78, 19), (96, 36), (98, 79), (91, 98), (67, 113), (48, 111.5), (33.5, 97), (30, 49), (40, 30))
GEM_FACETS = {
    'crown': ((30, 48.5), (40, 30), (58.5, 14), (78, 19), (96, 36), (96.5, 38), (72, 42), (62, 40), (54, 40), (50, 45)),
    'left': ((30, 48.5), (50, 45), (51, 60), (52, 80), (54, 96), (55, 105), (48, 111.5), (33.5, 97)),
    'table': ((50, 45), (54, 40), (62, 40), (72, 42), (74, 58), (77, 80), (75, 89), (69, 101), (55, 105), (54, 96),
              (52, 80), (51, 60)),
    'right': ((72, 42), (96.5, 38), (98, 79), (77, 80), (74, 58)),
    'lower': ((77, 80), (98, 79), (91, 98), (67, 113), (69, 101), (75, 89)),
}
GEM_GLINT = ((48, 102), (55, 106.5), (66, 106), (78, 100), (69, 112.6), (48, 111.5))
GEM_WEDGE = ((39.5, 32), (57, 15.5), (67, 15.5), (70, 19), (50, 32), (45, 35.5))
GEM_STREAK = ((34, 50.5), (42.5, 48.5), (43.5, 62), (41.5, 75), (37.5, 75), (35, 61))
# Where the shading of each facet starts and ends: light at the upper left, the darkest facet at the lower right.
GEM_SHADING = {'crown': ((36, 42), (96, 34)), 'left': ((36, 45), (40, 110)), 'table': ((46, 40), (80, 102)),
               'right': ((88, 39), (90, 80)), 'lower': ((90, 80), (76, 108))}
# The colours of the clear Matrix Crystal, read from its painted icon. That icon is translucent glass with a soft
# rim; the cluster shows the same greys opaque inside the dark contour every other crystal has. No recipe of the
# cluster holds a coloured crystal, so it shows no crystal hue.
CLEAR_CRYSTAL = {
    'ink': '#28333e', 'crown': ('#cdd2d6', '#c0c6cb', '#9ea4a9'), 'left': ('#cacfd2', '#d3d9dc', '#cfd5d8'),
    'table': ('#b8bdc2', '#a0a7ad', '#7b8289'), 'right': ('#adb3b8', '#8e959b', '#798188'),
    'lower': ('#727981', '#6b727a'), 'glint': '#d3d7db', 'white': '#f0f4f6',
}
CONTOUR = 4.5   # around the silhouette, as on the painted crystals
JOINT = 2.75    # between the parts of one drawing


def spots(points):
    return ' '.join(f'{x:.2f},{y:.2f}' for x, y in points)


def stand(x, y, width=1, height=1, turn=0, stretch=0, point=0):
    """Places the gem with its foot at (x, y): the prism between crown and base lengthened by stretch, the crown
    raised by the share point, then scaled and turned clockwise by turn degrees."""
    cos, sin = math.cos(math.radians(turn)), math.sin(math.radians(turn))

    def place(spot):
        longer = stretch * min(1, max(0, (spot[1] - 44) / 36)) - point * max(0, 44 - spot[1])
        across, down = (spot[0] - GEM_FOOT[0]) * width, (spot[1] + longer - GEM_FOOT[1] - stretch) * height
        return x + across * cos - down * sin, y + across * sin + down * cos
    return place


class Composition:
    """Shapes drawn back to front inside one common contour: heavy around the silhouette, lighter between the
    parts. A composition is laid out freely, then fitted to the canvas: scaled about its middle and moved."""

    def __init__(self, size=1, move=(0, 0)):
        self.size, self.move = size, move
        self.definitions, self.silhouette, self.parts = [], [], []

    def fit(self, points):
        return [(64 + (x - 64) * self.size + self.move[0], 64 + (y - 64) * self.size + self.move[1])
                for x, y in points]

    def gradient(self, name, start, end, colours):
        (x1, y1), (x2, y2) = self.fit((start, end))
        stops = ''.join(f'<stop offset="{index / (len(colours) - 1):g}" stop-color="{colour}"/>'
                        for index, colour in enumerate(colours))
        self.definitions.append(f'<linearGradient id="{name}" gradientUnits="userSpaceOnUse" x1="{x1:.2f}" '
                                f'y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}">{stops}</linearGradient>')
        return f'url(#{name})'

    def fill(self, points, fill):
        self.parts.append(f'<polygon points="{spots(self.fit(points))}" fill="{fill}"/>')

    def stroke(self, points, colour, width):
        self.parts.append(f'<polyline points="{spots(self.fit(points))}" fill="none" stroke="{colour}" '
                          f'stroke-width="{width:g}" stroke-linejoin="round" stroke-linecap="round"/>')

    def shape(self, points, fill, joint=JOINT):
        """A part with its contour: the common silhouette below everything, the lighter joint line around it."""
        ink, points = CLEAR_CRYSTAL['ink'], spots(self.fit(points))
        self.silhouette.append(f'<polygon points="{points}" fill="{ink}" stroke="{ink}" '
                               f'stroke-width="{2 * CONTOUR:g}" stroke-linejoin="round"/>')
        self.parts.append(f'<polygon points="{points}" fill="{fill}" stroke="{ink}" stroke-width="{2 * joint:g}" '
                          'stroke-linejoin="round"/>')
        self.parts.append(f'<polygon points="{points}" fill="{fill}"/>')

    def gem(self, name, place, glint=True, streak=True):
        """One clear Matrix Crystal: crown, lit left bevel, table, shaded right bevel and its reflections."""
        fills = {facet: self.gradient(f'{name}-{facet}', place(start), place(end), CLEAR_CRYSTAL[facet])
                 for facet, (start, end) in GEM_SHADING.items()}
        self.shape([place(spot) for spot in GEM_BODY], fills['table'])
        for facet, points in GEM_FACETS.items():
            self.fill([place(spot) for spot in points], fills[facet])
        if glint:
            self.fill([place(spot) for spot in GEM_GLINT], CLEAR_CRYSTAL['glint'])
        self.fill([place(spot) for spot in GEM_WEDGE], CLEAR_CRYSTAL['white'])
        if streak:
            self.fill([place(spot) for spot in GEM_STREAK], CLEAR_CRYSTAL['white'])

    def svg(self):
        return ('<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">\n<defs>\n'
                + '\n'.join(self.definitions) + '\n</defs>\n' + '\n'.join(self.silhouette + self.parts)
                + '\n</svg>\n')


def cluster(look=None):
    """The Matrix Cluster: the four clear Matrix Crystals of its recipe joined into one part. The iron of the bound
    and lattice looks is the steel of the Structural Bars in the recipe."""
    look = look or MATRIX_CLUSTER_LOOK
    light, steel, shade, deep = (hex_color(colour) for colour in (TRIM, STEEL, STEEL_SHADE, STEEL_DEEP))
    dark = hex_color(scale(STEEL_DEEP, .78))
    if look == 'grown':
        # Grown from one root: a tall crystal, two leaning out and a short one in front of the root.
        drawing = Composition(.92, (1, -1))
        drawing.gem('main', stand(58, 108, .64, .8, turn=-6, stretch=16, point=.3), glint=False)
        drawing.gem('right', stand(74, 110, .52, .64, turn=32, stretch=8, point=.3), glint=False)
        drawing.gem('left', stand(51, 112, .5, .58, turn=-39, stretch=6, point=.3), glint=False, streak=False)
        drawing.gem('front', stand(67, 120, .62, .46, turn=7), streak=False)
    elif look == 'bound':
        # A pack of four held by an iron band with a clasp.
        drawing = Composition(.88, (0, 1))
        drawing.gem('back-left', stand(53, 101, .5, .72, turn=-6, stretch=12, point=.3), glint=False)
        drawing.gem('back-right', stand(83, 99, .5, .78, turn=6, stretch=14, point=.3), glint=False)
        drawing.gem('front-left', stand(45, 118, .54, .72, turn=-4, stretch=6, point=.3))
        drawing.gem('front-right', stand(79, 119, .54, .62, turn=4, stretch=6, point=.3))
        drawing.shape([(16, 78), (40, 85), (62, 87), (86, 85), (110, 77), (111, 90), (86, 98), (62, 100), (40, 98),
                       (17, 91)], drawing.gradient('band', (20, 78), (106, 100), (shade, deep, dark)))
        drawing.stroke([(19, 81.5), (40, 88), (62, 90), (86, 88), (108, 81)], steel, 1.6)
        drawing.shape([(53, 84), (72, 84), (72, 103), (53, 103)],
                      drawing.gradient('clasp', (54, 84), (72, 103), (light, steel, shade)), joint=2)
        drawing.fill([(57.5, 89), (67.5, 89), (67.5, 98), (57.5, 98)], deep)
        drawing.fill([(59, 90.5), (67.5, 90.5), (67.5, 98), (59, 98)], shade)
    elif look == 'lattice':
        # Fused two by two, like the crafting grid, and pinned by one iron node.
        drawing = Composition(.9)
        drawing.gem('top-left', stand(48, 66, .66, .58, turn=-2), glint=False)
        drawing.gem('top-right', stand(82, 65, .66, .58, turn=2), glint=False)
        drawing.gem('low-left', stand(48, 120, .66, .58, turn=-2))
        drawing.gem('low-right', stand(82, 119, .66, .58, turn=2))
        drawing.shape([(65, 51), (78, 64), (65, 77), (52, 64)],
                      drawing.gradient('node', (54, 52), (76, 76), (light, steel, shade)), joint=2.5)
        drawing.fill([(65, 58.5), (70.5, 64), (65, 69.5), (59.5, 64)], deep)
    else:
        raise ValueError(f'Unknown Matrix Cluster look: {look}')
    return drawing.svg()


def digest(content):
    return hashlib.sha256(content).hexdigest()


def generate(check=False):
    out = GeneratedFiles(ROOT, check)
    record = json.loads(RECORD.read_text()) if RECORD.is_file() else {}
    expected = {}
    for name, source in (('ice_matrix_crystal', ice()), ('pressure_module', pressure()), ('parallel_upgrade', parallel()),
                         ('matrix_cluster', cluster())):
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
    print(f"{'Verified' if check else 'Generated'} {len(expected)} illustrated icons ({UPGRADE_LOOK} upgrade "
          f"inserts, {MATRIX_CLUSTER_LOOK} Matrix Cluster)")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    generate(parser.parse_args().check)
