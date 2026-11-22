#!/usr/bin/env python3
"""Draw the opaque Ice crystal, the Matrix Cluster, the Matter Wrench and two engineered upgrade icons from SVG geometry.

The SVGs are the sources; rendered.json ties each PNG to its source and allows --check without CairoSVG.
Changed drawings need CairoSVG to render, as with the Primal drawings. UPGRADE_LOOK switches the highlighted
material inserts (recommended) to neutral graphite without changing the silhouette or the recipe.
MATRIX_CLUSTER_LOOK selects how the four clear crystals of the Matrix Cluster are joined: grown from one root
(recommended), bound by an iron band, or fused two by two around an iron node.
WRENCH_LOOK selects the head geometry of the Matter Wrench: precision port caliper (recommended), classic open
spanner jaw, or hex port-ring socket.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
from family_palette import (BASE, COPPER, COPPER_DARK, COPPER_LIGHT, HIGHLIGHT, IVORY, LED, PORT_LIGHT, PORT_METAL,
                            SEAM, SHADE, STEEL, STEEL_DEEP, STEEL_SHADE, TRIM, WELL, lighten, scale)
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
WRENCH_LOOKS = ('caliper', 'open_jaw', 'socket')
WRENCH_LOOK = 'caliper'
CONFIGURATION_CARD_LOOKS = ('smart_module', 'punched_bus')
CONFIGURATION_CARD_LOOK = 'smart_module'
WIRELESS_TERMINAL_LOOKS = ('field_slate', 'transceiver_deck')
WIRELESS_TERMINAL_LOOK = 'field_slate'
CANISTER_LOOKS = ('valved_cylinder', 'twin_gauge', 'ribbed_flask')
CANISTER_LOOK = 'valved_cylinder'
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


def wrench(look=None, mode='ports'):
    """The Matter Wrench: Structural Bars steel and casing graphite with a mode-coloured status collar & grip ribs.
    No coloured Matrix Crystal enters its recipe, so the icon wears neutral steel, graphite and status LED hues only."""
    look = look or WRENCH_LOOK
    ivory = hex_color(IVORY)
    light, steel, shade, deep = (hex_color(c) for c in (TRIM, STEEL, STEEL_SHADE, STEEL_DEEP))
    casing_hi, casing, casing_lo, well = (hex_color(c) for c in (HIGHLIGHT, BASE, SHADE, WELL))
    port_hi, port = hex_color(PORT_LIGHT), hex_color(PORT_METAL)
    mode_rgb = {'ports': LED['blue'], 'rotate': LED['amber'], 'dismantle': LED['red']}[mode]
    acc_hi = hex_color(lighten(mode_rgb, .28))
    acc = hex_color(mode_rgb)
    acc_lo = hex_color(scale(mode_rgb, .68))

    drawing = Composition(.96, (0, 0))
    steel_grad = drawing.gradient('wr-steel', (68, 18), (110, 56), (light, steel, shade, deep))
    pommel_grad = drawing.gradient('wr-pommel', (15, 96), (34, 113), (light, steel, shade))
    grip_grad = drawing.gradient('wr-grip', (24, 72), (56, 102), (casing_hi, casing, casing_lo))
    neck_grad = drawing.gradient('wr-neck', (58, 44), (84, 66), (port_hi, port, casing_lo))
    accent_grad = drawing.gradient('wr-accent', (52, 54), (74, 74), (acc_hi, acc, acc_lo))

    # Shared handle: steel pommel with mode LED window, graphite grip with mode-coloured ribs, and status collar.
    drawing.shape([(14, 101), (23, 92), (36, 105), (27, 114)], pommel_grad, joint=2.2)
    drawing.fill([(15.5, 100.5), (23, 93), (27, 97), (19.5, 104.5)], light)
    drawing.fill([(20.5, 102), (24.5, 98), (29, 102.5), (25, 106.5)], acc)

    drawing.shape([(21, 94), (54, 61), (67, 74), (34, 107)], grip_grad, joint=2.4)
    drawing.fill([(22.5, 93.5), (54, 62), (58, 66), (26.5, 97.5)], casing_hi)
    drawing.fill([(30, 102), (62, 70), (66, 74), (34, 106)], casing_lo)
    drawing.fill([(29, 91), (50, 70), (57, 77), (36, 98)], well)
    for step in (0, 7, 14):
        x0, y0 = 32 + step, 89 - step
        drawing.fill([(x0, y0), (x0 + 4, y0 - 4), (x0 + 9.5, y0 + 1.5), (x0 + 5.5, y0 + 5.5)], acc)
        drawing.stroke([(x0 + 1, y0 - .5), (x0 + 4, y0 - 3.5)], acc_hi, 1.5)

    drawing.shape([(59, 56), (75, 40), (88, 53), (72, 69)], neck_grad, joint=2.3)
    drawing.stroke([(61, 56), (75, 42)], steel, 1.8)

    drawing.shape([(51, 62), (61, 52), (76, 67), (66, 77)], accent_grad, joint=2.4)
    for step in (0, 4.5, 9):
        drawing.stroke([(54.5 + step, 62.5 - step), (65.5 + step, 73.5 - step)], acc_lo, 1.6)
    drawing.stroke([(53, 62), (61, 54)], acc_hi, 2.0)
    drawing.stroke([(61, 62), (65, 58)], ivory, 2.0)

    if look == 'caliper':
        # Precision twin-jaw port caliper: upper fixed jaw, lower sliding jaw and mode-coloured vernier pin.
        drawing.shape([(66, 43), (81, 28), (100, 47), (85, 62)], steel_grad, joint=2.5)
        drawing.fill([(74, 43), (83, 34), (93, 44), (84, 53)], well)
        # Upper fixed jaw (lit top-left bevel).
        drawing.shape([(65, 37), (76, 20), (95, 13), (103, 19), (94, 28), (83, 29), (78, 36), (72, 43)],
                      steel_grad, joint=2.5)
        drawing.fill([(67, 36), (77, 21.5), (94.5, 14.8), (99, 18), (82, 25), (72, 38)], light)
        drawing.stroke([(77, 21), (94, 15)], ivory, 1.6)
        # Lower sliding jaw (stepped throat and shaded outer cheek).
        drawing.shape([(85, 56), (92, 50), (99, 45), (100, 34), (109, 25), (115, 33), (108, 52), (91, 63)],
                      steel_grad, joint=2.5)
        drawing.fill([(91, 61.5), (107, 51), (113.5, 33.5), (110, 29), (102, 46), (88, 58)], deep)
        drawing.fill([(87, 53), (95, 45), (101, 51), (93, 59)], acc)
        drawing.stroke([(88.5, 53), (95, 46.5)], acc_hi, 1.6)
    elif look == 'open_jaw':
        # Classic heavy open-end spanner head with 15-degree offset horns and recessed hex throat.
        head = [(66, 38), (73, 22), (91, 13), (102, 17), (92, 28), (84, 31), (82, 39), (89, 46),
                (97, 44), (108, 33), (114, 41), (105, 57), (89, 63), (76, 52)]
        drawing.shape(head, steel_grad, joint=2.6)
        drawing.fill([(68, 37), (74.5, 23.5), (91, 15), (98.5, 17.8), (90.5, 26.5), (81, 29.5), (74, 41)], light)
        drawing.fill([(89, 61), (104, 55.5), (112, 41.5), (108, 36), (99, 46), (86, 51), (79, 52)], deep)
        drawing.stroke([(75, 23), (91, 15.5)], ivory, 1.6)
    elif look == 'socket':
        # Closed octagonal port-ring box wrench with recessed hex bore and status ratchet ring.
        octagon = [(71, 29), (85, 15), (101, 15), (113, 27), (113, 43), (99, 57), (83, 57), (71, 45)]
        drawing.shape(octagon, steel_grad, joint=2.6)
        drawing.fill([(73, 29), (85.5, 16.5), (100, 16.5), (105, 21.5), (87, 23), (76, 34)], light)
        drawing.fill([(98, 55.5), (111.5, 42), (111.5, 28), (106, 26), (105, 40), (94, 51)], deep)
        ring = [(79, 31), (87, 23), (97, 23), (105, 31), (105, 41), (97, 49), (87, 49), (79, 41)]
        drawing.shape(ring, accent_grad, joint=1.8)
        bore = [(83, 32.5), (88.5, 27), (95.5, 27), (101, 32.5), (101, 39.5), (95.5, 45), (88.5, 45), (83, 39.5)]
        drawing.shape(bore, well, joint=1.6)
        drawing.stroke([(85, 32), (89, 28), (95, 28)], shade, 1.5)
    else:
        raise ValueError(f'Unknown Matter Wrench look: {look}')
    return drawing.svg()


def configuration_card(look=None):
    """Universal Configuration Card: graphite & gunmetal data cartridge with copper edge pins and ivory port map.
    No coloured Matrix Crystal enters its recipe, so only neutrals and material copper appear."""
    look = look or CONFIGURATION_CARD_LOOK
    ivory = hex_color(IVORY)
    light, steel, shade, deep = (hex_color(c) for c in (TRIM, STEEL, STEEL_SHADE, STEEL_DEEP))
    casing_hi, casing, casing_lo, well = (hex_color(c) for c in (HIGHLIGHT, BASE, SHADE, WELL))
    port_hi, port = hex_color(PORT_LIGHT), hex_color(PORT_METAL)
    cu_hi, cu, cu_lo = (hex_color(c) for c in (COPPER_LIGHT, COPPER, COPPER_DARK))

    drawing = Composition(.95, (0, 0))
    body_grad = drawing.gradient('cc-body', (22, 24), (104, 98), (port_hi, port, casing, casing_lo))
    bezel_grad = drawing.gradient('cc-bezel', (32, 30), (90, 78), (light, steel, shade, deep))
    pin_grad = drawing.gradient('cc-pin', (66, 80), (108, 108), (cu_hi, cu, cu_lo))

    # Isometric three-quarter data card with notched corner key and recessed connector tongue.
    card_top = [(14, 58), (58, 22), (112, 52), (112, 62), (72, 98), (22, 70)]
    drawing.shape(card_top, body_grad, joint=2.5)
    drawing.fill([(17, 57), (58, 24.5), (109, 53), (70, 86)], casing)
    drawing.fill([(17, 58), (70, 87), (70, 95.5), (22, 69)], shade)
    drawing.fill([(70, 87), (110, 53), (110, 61), (70, 95.5)], casing_lo)
    drawing.stroke([(19, 56.5), (58, 25.5), (107, 52.5)], casing_hi, 2.0)

    # Copper edge-connector bus along the lower-right insertion lip.
    tongue = [(64, 90), (106, 56), (115, 62), (115, 70), (74, 104), (64, 98)]
    drawing.shape(tongue, casing_lo, joint=2.2)
    for step in range(5):
        px, py = 69 + step * 8.2, 91 - step * 6.7
        drawing.fill([(px, py), (px + 5.4, py - 4.4), (px + 9.2, py - 1.8), (px + 3.8, py + 2.6)], pin_grad)
        drawing.stroke([(px + .6, py - .4), (px + 5.0, py - 4.0)], cu_hi, 1.4)

    # Upper-left steel grip handle / write-lock tab.
    drawing.shape([(20, 51), (48, 28), (58, 34), (30, 57)], bezel_grad, joint=2.0)
    drawing.stroke([(23, 50), (48, 30)], light, 1.8)

    if look == 'smart_module':
        # Recessed memory window showing the unfolded six-side port cross in ivory & steel.
        drawing.shape([(36, 56), (62, 35), (92, 52), (66, 73)], bezel_grad, joint=2.0)
        drawing.fill([(40, 56), (62, 38.5), (88, 52.5), (66, 70)], well)
        # Central front node + four orthogonal side nodes + copper bus trace.
        for cx, cy, col in ((64, 54, ivory), (55, 49, steel), (73, 59, steel), (56, 61, cu_hi), (72, 47, port_hi)):
            drawing.fill([(cx - 4, cy), (cx, cy - 3.2), (cx + 4, cy), (cx, cy + 3.2)], col)
        drawing.stroke([(55, 49), (73, 59)], ivory, 1.5)
        drawing.stroke([(56, 61), (72, 47)], steel, 1.5)
    elif look == 'punched_bus':
        # Parallel memory rails with copper address jumpers.
        for idx in range(3):
            ox, oy = 38 + idx * 9, 57 - idx * 7.2
            drawing.fill([(ox, oy), (ox + 6, oy - 4.8), (ox + 34, oy + 10.8), (ox + 28, oy + 15.6)], well)
            drawing.stroke([(ox + 3, oy), (ox + 29, oy + 14.5)], ivory if idx == 1 else steel, 1.8)
    else:
        raise ValueError(f'Unknown Configuration Card look: {look}')
    return drawing.svg()


def wireless_booster():
    """Wireless Booster card: plug-in RF range-amplifier cartridge with twin dipole fins, copper bus pins,
    and a central Blue Matrix Crystal resonator (#6794f1)."""
    blue = hex_color(RGB['blue'])
    blue_hi = hex_color(lighten(RGB['blue'], .38))
    blue_lo = hex_color(scale(RGB['blue'], .66))
    ivory = hex_color(IVORY)
    light, steel, shade, deep = (hex_color(c) for c in (TRIM, STEEL, STEEL_SHADE, STEEL_DEEP))
    casing_hi, casing, casing_lo, well = (hex_color(c) for c in (HIGHLIGHT, BASE, SHADE, WELL))
    port_hi, port = hex_color(PORT_LIGHT), hex_color(PORT_METAL)
    cu_hi, cu, cu_lo = (hex_color(c) for c in (COPPER_LIGHT, COPPER, COPPER_DARK))

    drawing = Composition(.95, (0, 0))
    chassis_grad = drawing.gradient('wb-chassis', (18, 30), (106, 104), (port_hi, port, casing, casing_lo))
    steel_grad = drawing.gradient('wb-steel', (52, 12), (112, 48), (light, steel, shade, deep))
    cu_grad = drawing.gradient('wb-cu', (22, 82), (62, 112), (cu_hi, cu, cu_lo))

    # Twin angled steel dipole antenna fins at the upper-right head.
    drawing.shape([(62, 31), (86, 12), (96, 17), (72, 38)], steel_grad, joint=2.2)
    drawing.stroke([(65, 29), (86.5, 13.5)], light, 1.8)
    drawing.shape([(80, 41), (104, 22), (114, 27), (90, 48)], steel_grad, joint=2.2)
    drawing.stroke([(83, 39), (104.5, 23.5)], light, 1.8)

    # Angled amplifier module cartridge body.
    body = [(16, 70), (58, 30), (108, 56), (108, 68), (66, 108), (16, 82)]
    drawing.shape(body, chassis_grad, joint=2.5)
    drawing.fill([(19, 69), (58, 32.5), (105, 57), (65, 95)], casing)
    drawing.fill([(18, 71), (65, 96), (65, 106), (18, 81)], shade)
    drawing.fill([(65, 96), (106, 57.5), (106, 67), (65, 106)], casing_lo)
    drawing.stroke([(20, 68.5), (58, 33), (103, 56.5)], casing_hi, 1.8)

    # Copper insertion edge-connector pins at the lower-left foot.
    foot = [(15, 76), (54, 96), (46, 106), (13, 88)]
    drawing.shape(foot, casing_lo, joint=2.1)
    for step in range(4):
        px, py = 20 + step * 8.0, 82 + step * 4.2
        drawing.fill([(px, py), (px + 5.2, py + 2.7), (px + 1.4, py + 7.2), (px - 3.8, py + 4.5)], cu_grad)
        drawing.stroke([(px - .5, py + 1.0), (px + 4.0, py + 3.4)], cu_hi, 1.4)

    # Central faceted Blue Matrix Crystal resonator core (exact #6794f1 middle table >= 320 px).
    drawing.shape([(38, 62), (62, 40), (88, 54), (64, 76)], well, joint=2.0)
    gem = [(43, 60), (62, 43), (83, 54), (80, 66), (63, 74), (45, 66)]
    drawing.shape(gem, blue, joint=2.2)
    drawing.fill([(45, 59.5), (62, 45), (79, 54), (63, 58)], blue_hi)
    drawing.fill([(45, 60), (63, 58), (79, 65), (63, 72.5), (46, 65)], blue)
    drawing.fill([(63, 58), (81, 54.5), (79, 65)], blue_lo)
    drawing.stroke([(58, 49), (69, 55)], ivory, 1.8)
    return drawing.svg()


def wireless_terminal(look=None):
    """Handheld Wireless Matter Terminal: ruggedized graphite tablet, Blue Matrix Crystal transceiver core (#6794f1),
    illuminated Mint substance screen (inherited from Matter Terminal) and tactile steel key row."""
    look = look or WIRELESS_TERMINAL_LOOK
    blue = hex_color(RGB['blue'])
    blue_hi = hex_color(lighten(RGB['blue'], .36))
    blue_lo = hex_color(scale(RGB['blue'], .68))
    mint = hex_color(RGB['mint'])
    mint_hi = hex_color(lighten(RGB['mint'], .4))
    mint_lo = hex_color(scale(RGB['mint'], .55))
    ivory = hex_color(IVORY)
    light, steel, shade, deep = (hex_color(c) for c in (TRIM, STEEL, STEEL_SHADE, STEEL_DEEP))
    casing_hi, casing, casing_lo, well = (hex_color(c) for c in (HIGHLIGHT, BASE, SHADE, WELL))
    port_hi, port = hex_color(PORT_LIGHT), hex_color(PORT_METAL)
    cu_hi, cu, cu_lo = (hex_color(c) for c in (COPPER_LIGHT, COPPER, COPPER_DARK))

    drawing = Composition(.95, (0, 1))
    chassis_grad = drawing.gradient('wt-chassis', (18, 28), (108, 108), (casing_hi, casing, casing_lo))
    steel_grad = drawing.gradient('wt-steel', (70, 14), (108, 44), (light, steel, shade, deep))
    cu_grad = drawing.gradient('wt-cu', (20, 82), (48, 106), (cu_hi, cu, cu_lo))

    # Antenna mast + Blue Matrix Crystal transceiver module at the upper right.
    if look == 'field_slate':
        drawing.shape([(72, 27), (86, 12), (96, 17), (82, 33)], steel_grad, joint=2.2)
        drawing.stroke([(75, 25), (86.5, 13.5)], light, 1.8)
        # Faceted Blue Matrix Crystal beacon housing (exact #6794f1 middle table >= 250 px).
        crystal_pts = [(82, 24), (96, 12), (112, 20), (110, 36), (95, 44), (80, 36)]
        drawing.shape(crystal_pts, blue, joint=2.4)
        drawing.fill([(83, 24), (96, 13.5), (109, 20), (96, 25)], blue_hi)
        drawing.fill([(81.5, 24.5), (96, 25), (108.5, 35), (95, 42.5), (81.5, 35)], blue)
        drawing.fill([(96, 25), (110.5, 20.5), (108.5, 35)], blue_lo)
    elif look == 'transceiver_deck':
        drawing.shape([(64, 24), (92, 11), (112, 22), (86, 38)], steel_grad, joint=2.3)
        drawing.shape([(72, 24), (92, 14), (108, 23), (88, 34)], blue, joint=2.0)
        drawing.fill([(74, 23.5), (92, 15.5), (100, 19.5), (82, 28)], blue_hi)
        drawing.fill([(74, 24.5), (105, 23), (88, 33)], blue)
    else:
        raise ValueError(f'Unknown Wireless Terminal look: {look}')

    # Main angled field-tablet chassis.
    slate = [(14, 66), (58, 24), (110, 52), (110, 66), (66, 110), (14, 80)]
    drawing.shape(slate, chassis_grad, joint=2.6)
    drawing.fill([(17, 65), (58, 26.5), (107, 53), (65, 95)], port)
    drawing.fill([(16, 67), (65, 96), (65, 108), (16, 79)], shade)
    drawing.fill([(65, 96), (108, 53.5), (108, 65), (65, 108)], casing_lo)
    drawing.stroke([(18, 64.5), (58, 27), (105, 52.5)], port_hi, 1.8)

    # Illuminated Mint terminal screen in a dark glass bezel.
    drawing.shape([(28, 60), (58, 32), (96, 52), (66, 81)], well, joint=2.0)
    drawing.fill([(32, 59.5), (58.5, 35), (92, 53), (65.5, 78)], hex_color(scale(RGB['mint'], .22)))
    # Header bar + stock readout bars on screen.
    drawing.stroke([(55, 40), (85, 56)], mint, 3.2)
    for step, length in ((0, 18), (6, 13), (12, 20)):
        sx, sy = 48 - step * .95, 46 + step * .92
        drawing.stroke([(sx, sy), (sx + length, sy + length * .54)], mint_hi if step == 0 else mint_lo, 2.2)
    # Blue wireless link indicator bar on the screen corner.
    drawing.fill([(36, 58), (44, 50.5), (51, 54.5), (43, 62)], blue)

    # Tactile steel key strip + copper charging contact bumper along the bottom edge.
    for k in range(4):
        kx, ky = 27 + k * 9.5, 70 + k * 5.4
        drawing.fill([(kx, ky), (kx + 5.5, ky - 4.8), (kx + 10.5, ky - 2.0), (kx + 5.0, ky + 2.8)], steel)
        drawing.stroke([(kx + .8, ky - .5), (kx + 5.2, ky - 4.3)], light, 1.3)
    drawing.shape([(20, 80), (28, 74), (48, 86), (40, 93)], cu_grad, joint=1.8)
    drawing.stroke([(22, 80), (39, 90)], ivory, 1.4)
    return drawing.svg()


def portable_energy_cell():
    """Portable Energy Cell: ruggedized pocket 4M FE battery pack with twin Copper terminal posts,
    charge indicator pips, and a faceted Ember Matrix Crystal capacitor core (#ea4848)."""
    ember = hex_color(RGB['ember'])
    ember_hi = hex_color(lighten(RGB['ember'], .36))
    ember_lo = hex_color(scale(RGB['ember'], .68))
    green = hex_color(LED['green'])
    ivory = hex_color(IVORY)
    light, steel, shade, deep = (hex_color(c) for c in (TRIM, STEEL, STEEL_SHADE, STEEL_DEEP))
    casing_hi, casing, casing_lo, well = (hex_color(c) for c in (HIGHLIGHT, BASE, SHADE, WELL))
    port_hi, port = hex_color(PORT_LIGHT), hex_color(PORT_METAL)
    cu_hi, cu, cu_lo = (hex_color(c) for c in (COPPER_LIGHT, COPPER, COPPER_DARK))

    drawing = Composition(.95, (0, 0))
    chassis_grad = drawing.gradient('pec-chassis', (16, 26), (110, 110), (port_hi, port, casing, casing_lo))
    steel_grad = drawing.gradient('pec-steel', (56, 12), (108, 48), (light, steel, shade, deep))
    cu_grad = drawing.gradient('pec-cu', (62, 10), (108, 44), (cu_hi, cu, cu_lo))

    # Twin heavy Copper terminal posts with steel isolator collars at the upper-right head.
    for ox, oy in ((62, 28), (82, 39)):
        drawing.shape([(ox, oy), (ox + 12, oy - 11), (ox + 22, oy - 5), (ox + 10, oy + 6)], steel_grad, joint=2.0)
        drawing.shape([(ox + 5, oy - 6), (ox + 15, oy - 15), (ox + 23, oy - 10), (ox + 13, oy - 1)], cu_grad, joint=2.0)
        drawing.stroke([(ox + 7, oy - 7), (ox + 15.5, oy - 13.5)], cu_hi, 1.8)

    # Armored graphite-and-gunmetal pocket battery housing.
    body = [(14, 68), (58, 28), (110, 56), (110, 72), (66, 112), (14, 84)]
    drawing.shape(body, chassis_grad, joint=2.6)
    drawing.fill([(17, 67), (58, 30.5), (107, 57), (65, 96)], casing)
    drawing.fill([(16, 69), (65, 97), (65, 110), (16, 83)], shade)
    drawing.fill([(65, 97), (108, 57.5), (108, 71), (65, 110)], casing_lo)
    drawing.stroke([(18, 66.5), (58, 31), (105, 56.5)], casing_hi, 1.8)

    # Steel clamp straps framing the cell core.
    for sx, sy in ((26, 59), (74, 85)):
        drawing.fill([(sx, sy), (sx + 8, sy - 7), (sx + 14, sy - 3.5), (sx + 6, sy + 3.5)], steel)
        drawing.stroke([(sx + 1, sy - .5), (sx + 7.5, sy - 6.2)], light, 1.4)

    # Recessed dark capacitor window with faceted Ember Matrix Crystal core (exact #ea4848 >= 350 px).
    drawing.shape([(34, 61), (60, 37), (92, 55), (66, 79)], well, joint=2.1)
    gem = [(40, 59), (60, 41), (86, 55), (83, 68), (65, 76), (43, 66)]
    drawing.shape(gem, ember, joint=2.2)
    drawing.fill([(42, 58.5), (60, 43), (82, 55), (64, 59.5)], ember_hi)
    drawing.fill([(42, 59), (64, 59.5), (81, 67), (65, 74.5), (44, 65.5)], ember)
    drawing.fill([(64, 59.5), (84, 55.5), (81, 67)], ember_lo)
    drawing.stroke([(56, 48), (70, 56)], ivory, 1.8)

    # Three status-green charge pips along the lower side rail + Copper base bus contact.
    for p in range(3):
        px, py = 26 + p * 10.5, 76 + p * 5.8
        drawing.fill([(px, py), (px + 6.5, py + 3.6), (px + 6.5, py + 7.2), (px, py + 3.6)], green)
    drawing.shape([(50, 92), (59, 97), (59, 104), (50, 99)], cu_grad, joint=1.6)
    return drawing.svg()


def pressurized_canister(look=None):
    """Pressurized Chemical Canister: portable Virelite-and-Rhevon reinforced containment vessel with a copper
    shutoff valve assembly and a recessed Mint Matrix Crystal sight-gauge core (#57cddb)."""
    look = look or CANISTER_LOOK
    if look not in CANISTER_LOOKS:
        raise ValueError(f'Unknown Pressurized Canister look: {look}')
    mint = hex_color(RGB['mint'])
    mint_hi = hex_color(lighten(RGB['mint'], .38))
    mint_lo = hex_color(scale(RGB['mint'], .66))
    green = hex_color(LED['green'])
    light, steel, shade, deep = (hex_color(c) for c in (TRIM, STEEL, STEEL_SHADE, STEEL_DEEP))
    casing_hi, casing, casing_lo, well = (hex_color(c) for c in (HIGHLIGHT, BASE, SHADE, WELL))
    port_hi, port = hex_color(PORT_LIGHT), hex_color(PORT_METAL)
    cu_hi, cu, cu_lo = (hex_color(c) for c in (COPPER_LIGHT, COPPER, COPPER_DARK))

    drawing = Composition(.95, (0, 0))
    chassis_grad = drawing.gradient('pc-chassis', (18, 24), (104, 108), (port_hi, port, casing, casing_lo))
    steel_grad = drawing.gradient('pc-steel', (22, 20), (102, 100), (light, steel, shade, deep))
    cu_grad = drawing.gradient('pc-cu', (70, 12), (112, 46), (cu_hi, cu, cu_lo))

    if look == 'valved_cylinder':
        # Heavy lower-left Rhevon base foot & docking skirt.
        base_cap = [(13, 76), (22, 67), (54, 99), (45, 108), (29, 109), (14, 94)]
        drawing.shape(base_cap, steel_grad, joint=2.3)
        drawing.fill([(15, 76), (22, 69), (35, 82), (26, 89)], light)
        drawing.fill([(36, 95), (52, 99), (44.5, 106.5), (29.5, 107.5)], deep)

        # Upper-right steel valve boss, brass discharge nozzle & Copper shutoff handwheel.
        drawing.shape([(94, 34), (105, 27), (113, 35), (102, 44)], cu_grad, joint=2.0)
        drawing.fill([(104, 31), (110, 35), (105, 40), (99, 36)], well)
        drawing.shape([(71, 27), (82, 16), (103, 37), (92, 48)], steel_grad, joint=2.2)
        wheel = [(75, 16), (92, 11), (111, 24), (112, 34), (96, 39), (77, 26)]
        drawing.shape(wheel, cu_grad, joint=2.3)
        drawing.fill([(82, 18.5), (91, 15.5), (96, 19.5), (87, 22.5)], well)
        drawing.fill([(94, 25.5), (104, 28.5), (99, 33.5), (89, 30.5)], well)
        drawing.stroke([(78, 16.5), (92, 12.5), (108, 23.5)], cu_hi, 2.0)
        drawing.fill([(89, 21.5), (94, 19.5), (98, 23.5), (93, 26.5)], light)

        # Angled three-quarter graphite & ceramic pressure cylinder body.
        cylinder = [(20, 69), (65, 24), (98, 57), (53, 102)]
        drawing.shape(cylinder, chassis_grad, joint=2.6)
        drawing.fill([(22.5, 68), (65, 25.5), (76, 36.5), (33.5, 79)], port_hi)
        drawing.fill([(33.5, 79), (76, 36.5), (88, 48.5), (45.5, 91)], casing)
        drawing.fill([(45.5, 91), (88, 48.5), (96.5, 57), (54, 99.5)], casing_lo)
        drawing.stroke([(24, 67.5), (65, 26.5)], light, 1.8)

        # Two Rhevon steel reinforcement collars wrapping across the vessel.
        for x0, y0 in ((25, 62), (55, 32)):
            collar = [(x0, y0), (x0 + 8, y0 - 8), (x0 + 43, y0 + 27), (x0 + 35, y0 + 35)]
            drawing.shape(collar, steel_grad, joint=2.1)
            drawing.fill([(x0 + 1.5, y0 - .5), (x0 + 8, y0 - 7), (x0 + 19, y0 + 4), (x0 + 12.5, y0 + 10.5)], light)
            drawing.fill([(x0 + 29, y0 + 14), (x0 + 41.5, y0 + 26.5), (x0 + 35, y0 + 33), (x0 + 22.5, y0 + 20.5)], deep)
        drawing.fill([(66, 35), (71, 30), (76, 35), (71, 40)], green)

        # Recessed Mint Matrix Crystal sight-gauge core (exact #57cddb >= 300 px).
        drawing.shape([(36, 57), (55, 38), (83, 66), (64, 85)], well, joint=2.1)
        gem = [(40, 57), (55, 42), (79, 66), (64, 81)]
        drawing.shape(gem, mint, joint=2.0)
        drawing.fill([(41.5, 56.5), (55, 43), (61, 49), (47.5, 62.5)], mint_hi)
        drawing.fill([(47.5, 62.5), (61, 49), (75, 63), (61.5, 76.5)], mint)
        drawing.fill([(61.5, 76.5), (75, 63), (77.5, 66), (64, 79.5)], mint_lo)
        for step in (0, 6, 12):
            tx, ty = 43 + step, 68 + step
            drawing.stroke([(tx, ty), (tx + 3.5, ty - 3.5)], steel, 1.5)

    elif look == 'twin_gauge':
        # Dual-chamber industrial gas/liquid manifold flask with copper bridge valve.
        drawing.shape([(14, 68), (58, 24), (76, 42), (32, 86)], chassis_grad, joint=2.4)
        drawing.fill([(16.5, 67.5), (58, 26), (66, 34), (24.5, 75.5)], port_hi)
        drawing.fill([(24.5, 75.5), (66, 34), (74, 42), (32.5, 83.5)], casing)

        drawing.shape([(35, 89), (79, 45), (97, 63), (53, 107)], chassis_grad, joint=2.4)
        drawing.fill([(37.5, 88.5), (79, 47), (88, 56), (46.5, 97.5)], casing)
        drawing.fill([(46.5, 97.5), (88, 56), (95.5, 63.5), (54, 105)], casing_lo)

        # Bottom-left & upper-right steel manifold yokes joining both chambers.
        drawing.shape([(13, 75), (22, 66), (62, 106), (51, 113), (32, 108), (15, 91)], steel_grad, joint=2.3)
        drawing.fill([(15, 75), (22, 68), (38, 84), (30, 91)], light)
        drawing.shape([(55, 25), (64, 16), (104, 56), (95, 65)], steel_grad, joint=2.3)
        drawing.stroke([(58, 24.5), (64, 18.5), (101, 55.5)], light, 1.8)

        # Copper cross-manifold bridge pipe & T-valve regulator cap.
        drawing.shape([(68, 22), (78, 13), (112, 47), (102, 56)], cu_grad, joint=2.2)
        drawing.stroke([(71, 21.5), (78, 15), (109, 46)], cu_hi, 1.9)
        drawing.shape([(84, 15), (98, 12), (114, 28), (106, 37), (90, 29)], cu_grad, joint=2.2)
        drawing.stroke([(86, 15.5), (97.5, 13.5), (111, 27)], cu_hi, 1.8)
        drawing.fill([(94, 18), (99, 17), (104, 22), (99, 24)], light)

        # Central recessed Mint sight window bridging the manifold frame (exact #57cddb >= 300 px).
        drawing.shape([(33, 60), (54, 39), (85, 70), (64, 91)], well, joint=2.2)
        gem = [(37, 60), (54, 43), (81, 70), (64, 87)]
        drawing.shape(gem, mint, joint=2.0)
        drawing.fill([(39, 59.5), (54, 44.5), (60.5, 51), (45.5, 66)], mint_hi)
        drawing.fill([(45.5, 66), (60.5, 51), (76.5, 67), (61.5, 82)], mint)
        drawing.fill([(61.5, 82), (76.5, 67), (79.5, 70), (64, 85.5)], mint_lo)
        drawing.stroke([(49, 73), (68, 54)], steel, 2.0)

    else:
        # 'ribbed_flask': Virelite-ceramic ribbed pressure vessel with heavy collar clamp & Mint core window.
        flask = [(16, 76), (58, 22), (104, 62), (62, 112), (32, 108), (15, 92)]
        drawing.shape(flask, chassis_grad, joint=2.5)
        drawing.fill([(18.5, 75.5), (58, 24.5), (72, 36.5), (31, 86.5)], port_hi)
        drawing.fill([(31, 86.5), (72, 36.5), (89, 51), (47, 100)], casing)
        drawing.fill([(47, 100), (89, 51), (102, 62.5), (61.5, 110)], casing_lo)

        # External Virelite-ceramic / steel armor ribs wrapping the flask.
        for rx0, ry0, dx, dy in ((19, 75, 45, 39), (49, 36, 44, 39)):
            rib = [(rx0, ry0), (rx0 + 8, ry0 - 9), (rx0 + 8 + dx, ry0 - 9 + dy), (rx0 + dx, ry0 + dy)]
            drawing.shape(rib, steel_grad, joint=2.1)
            drawing.fill([(rx0 + 1.5, ry0 - .5), (rx0 + 8, ry0 - 7.5), (rx0 + 21, ry0 + 3.5), (rx0 + 14.5, ry0 + 10.5)], light)
            drawing.fill([(rx0 + dx - 11, ry0 + dy - 16), (rx0 + dx + 6.5, ry0 + dy - 8.5), (rx0 + dx, ry0 + dy - 1.5), (rx0 + dx - 16, ry0 + dy - 10)], deep)

        # Heavy bolted neck collar clamp & Copper pressure-relief valve cap.
        drawing.shape([(62, 24), (76, 13), (110, 43), (97, 56)], steel_grad, joint=2.3)
        drawing.stroke([(65, 23.5), (76, 15), (107, 42.5)], light, 1.9)
        drawing.shape([(79, 15), (95, 12), (113, 28), (104, 39), (86, 26)], cu_grad, joint=2.2)
        drawing.stroke([(81, 15.5), (94.5, 13.5), (110, 27.5)], cu_hi, 1.9)
        drawing.fill([(90, 18.5), (96, 17.5), (101, 22), (95, 24.5)], light)

        # Central armored viewport with Mint Matrix Crystal core window (exact #57cddb >= 300 px).
        drawing.shape([(31, 63), (54, 40), (85, 67), (62, 90)], steel_grad, joint=2.2)
        drawing.fill([(35, 63), (54, 44), (81, 67.5), (62, 86.5)], well)
        gem = [(38, 63), (54, 47), (78, 68), (62, 84)]
        drawing.shape(gem, mint, joint=1.9)
        drawing.fill([(39.5, 62.5), (54, 48), (60.5, 53.5), (46, 68)], mint_hi)
        drawing.fill([(46, 68), (60.5, 53.5), (74.5, 65.5), (60, 80)], mint)
        drawing.fill([(60, 80), (74.5, 65.5), (76.5, 68), (62, 82.5)], mint_lo)
    return drawing.svg()


def spectrometer_lens():
    """Early field spectrometer optic: graphite & steel bezel with a faceted Ice Matrix Crystal prism core.
    Matches the Discovery Lens silhouette so its later upgrade path reads clearly, while carrying the exact
    Ice crystal hue (#d7d1bc) from its recipe and no Primal hues."""
    ice_rgb = RGB['ice']
    ice_hi = hex_color(lighten(ice_rgb, .38))
    ice = hex_color(ice_rgb)
    ice_mid = hex_color(scale(ice_rgb, .80))
    ice_lo = hex_color(scale(ice_rgb, .52))
    ice_deep = hex_color(scale(ice_rgb, .28))
    ink = hex_color(SEAM)
    well = hex_color(WELL)
    trim = hex_color(TRIM)
    steel = hex_color(STEEL)
    steel_sh = hex_color(STEEL_SHADE)
    steel_dp = hex_color(STEEL_DEEP)
    base = hex_color(BASE)
    hi = hex_color(HIGHLIGHT)
    shade = hex_color(SHADE)
    port_lt = hex_color(PORT_LIGHT)
    port_mt = hex_color(PORT_METAL)
    cu = hex_color(COPPER)
    cu_hi = hex_color(COPPER_LIGHT)
    tilt = 'transform="rotate(-18 60 58)"'

    defs = [
        f'<linearGradient id="sl-graphite" x1="0%" y1="0%" x2="100%" y2="100%">'
        f'<stop offset="0%" stop-color="{port_lt}"/>'
        f'<stop offset="32%" stop-color="{port_mt}"/>'
        f'<stop offset="68%" stop-color="{hi}"/>'
        f'<stop offset="100%" stop-color="{base}"/></linearGradient>',
        f'<linearGradient id="sl-wall" x1="0%" y1="0%" x2="100%" y2="100%">'
        f'<stop offset="0%" stop-color="{hi}"/>'
        f'<stop offset="100%" stop-color="{shade}"/></linearGradient>',
        f'<linearGradient id="sl-steel" x1="0%" y1="0%" x2="100%" y2="100%">'
        f'<stop offset="0%" stop-color="{trim}"/>'
        f'<stop offset="48%" stop-color="{steel}"/>'
        f'<stop offset="100%" stop-color="{steel_sh}"/></linearGradient>',
        f'<radialGradient id="sl-glass" cx=".44" cy=".42" r=".64">'
        f'<stop offset="0%" stop-color="{ice_hi}"/>'
        f'<stop offset="38%" stop-color="{ice_mid}"/>'
        f'<stop offset="76%" stop-color="{steel_dp}"/>'
        f'<stop offset="100%" stop-color="{well}"/></radialGradient>',
        f'<clipPath id="sl-pane"><ellipse cx="60" cy="58" rx="28" ry="26.5" {tilt}/></clipPath>',
    ]
    clamps = ''.join(
        f'<rect x="-5" y="-39.5" width="10" height="11" rx="2" transform="translate(60 58) rotate({angle})" '
        f'fill="url(#sl-steel)" stroke="{ink}" stroke-width="2"/>' for angle in (-58, 62, 182))
    ticks = ''.join(
        f'<line x1="60" y1="17" x2="60" y2="21" transform="rotate({angle} 60 58)" stroke="{trim}" '
        f'stroke-width="1.5" stroke-linecap="round" opacity=".78"/>' for angle in range(-128, 40, 14))
    parts = [
        # Steel mounting lug with copper contact collar (from its Control Circuit) and bore hole.
        f'<rect x="-9" y="36" width="18" height="30" rx="5" transform="translate(64 62) rotate(-45)" '
        f'fill="url(#sl-steel)" stroke="{ink}" stroke-width="5"/>',
        f'<rect x="-8" y="38" width="16" height="6" rx="1.5" transform="translate(64 62) rotate(-45)" '
        f'fill="{cu}" stroke="{ink}" stroke-width="1.6"/>',
        f'<line x1="-6" y1="39.5" x2="6" y2="39.5" transform="translate(64 62) rotate(-45)" '
        f'stroke="{cu_hi}" stroke-width="1.4" stroke-linecap="round"/>',
        f'<circle cx="104.5" cy="102.5" r="3.6" fill="{well}" stroke="{ink}" stroke-width="1.2"/>',
        # Three-quarter graphite housing depth and face.
        f'<ellipse cx="67" cy="67" rx="44" ry="42" {tilt} fill="url(#sl-wall)" stroke="{ink}" stroke-width="5"/>',
        f'<ellipse cx="60" cy="58" rx="44" ry="42" {tilt} fill="url(#sl-graphite)" stroke="{ink}" stroke-width="5"/>',
        f'<ellipse cx="60" cy="58" rx="40.6" ry="38.6" {tilt} fill="none" stroke="{trim}" stroke-width="1.6" '
        'stroke-dasharray="118 400" stroke-dashoffset="-128" stroke-linecap="round" opacity=".8"/>',
        f'<g>{ticks}</g>',
        f'<ellipse cx="60" cy="58" rx="34.5" ry="32.5" {tilt} fill="url(#sl-steel)" stroke="{ink}" stroke-width="2.6"/>',
        f'<ellipse cx="60" cy="58" rx="29.5" ry="28" {tilt} fill="{well}" stroke="{ink}" stroke-width="1.6"/>',
        f'<ellipse cx="60" cy="58" rx="28" ry="26.5" {tilt} fill="url(#sl-glass)"/>',
        '<g clip-path="url(#sl-pane)">',
        # Analytical spectrometer reticle ring and axis marks inside the lens chamber.
        f'<circle cx="60" cy="58" r="21" fill="none" stroke="{trim}" stroke-width="1.2" opacity=".45"/>',
        f'<line x1="33" y1="58" x2="43" y2="58" stroke="{trim}" stroke-width="1.4" opacity=".55"/>',
        f'<line x1="77" y1="58" x2="87" y2="58" stroke="{trim}" stroke-width="1.4" opacity=".55"/>',
        f'<line x1="60" y1="32" x2="60" y2="41" stroke="{trim}" stroke-width="1.4" opacity=".55"/>',
        f'<line x1="60" y1="75" x2="60" y2="84" stroke="{trim}" stroke-width="1.4" opacity=".55"/>',
        # Faceted Ice Matrix Crystal prism core carrying large flat facets in the exact #d7d1bc Ice hue.
        f'<polygon points="60,38 75,51 73,70 59,79 45,67 46,49" fill="{ice}" stroke="{ice_deep}" stroke-width="2.2" stroke-linejoin="round"/>',
        f'<polygon points="60,38 75,51 60,55 46,49" fill="{ice_hi}"/>',
        f'<polygon points="46,49 60,55 59,79 45,67" fill="{ice}"/>',
        f'<polygon points="60,55 75,51 73,70 59,79" fill="{ice}"/>',
        f'<polygon points="75,51 73,70 67,67 68,54" fill="{ice_lo}"/>',
        f'<polygon points="52,50 60,53 59,66 51,62" fill="{ice}"/>',
        # Glass specular reflection arcs.
        '<path d="M38 48 Q44 33 60 31 Q48 37 43 51 Z" fill="#ffffff" opacity=".75"/>',
        '<path d="M74 82 Q82 77 85 67 Q84 78 77 84 Z" fill="#ffffff" opacity=".32"/>',
        '</g>',
        f'<g>{clamps}</g>',
    ]
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">\n<defs>\n'
            + '\n'.join(defs) + '\n</defs>\n' + '\n'.join(parts) + '\n</svg>\n')


def digest(content):
    return hashlib.sha256(content).hexdigest()


def generate(check=False):
    out = GeneratedFiles(ROOT, check)
    record = json.loads(RECORD.read_text()) if RECORD.is_file() else {}
    expected = {}
    for name, source in (('ice_matrix_crystal', ice()), ('pressure_module', pressure()), ('parallel_upgrade', parallel()),
                         ('matrix_cluster', cluster()),
                         ('matter_wrench', wrench(mode='ports')),
                         ('matter_wrench_rotate', wrench(mode='rotate')),
                         ('matter_wrench_dismantle', wrench(mode='dismantle')),
                         ('configuration_card', configuration_card()),
                         ('wireless_booster', wireless_booster()),
                         ('wireless_matter_terminal', wireless_terminal()),
                         ('portable_energy_cell', portable_energy_cell()),
                         ('pressurized_canister', pressurized_canister()),
                         ('spectrometer_lens', spectrometer_lens())):
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
          f"inserts, {MATRIX_CLUSTER_LOOK} Matrix Cluster, {WRENCH_LOOK} Matter Wrench, "
          f"{CONFIGURATION_CARD_LOOK} Configuration Card, {WIRELESS_TERMINAL_LOOK} Wireless Terminal, "
          f"{CANISTER_LOOK} Pressurized Canister)")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    generate(parser.parse_args().check)
