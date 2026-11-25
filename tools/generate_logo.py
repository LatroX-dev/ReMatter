#!/usr/bin/env python3
"""Draw the ReMatter logo: the block mark, the wordmark and the two lockups.

The mark is a block built from cubes of different materials, seen like a block in the inventory. Some cubes
are missing and float around it. Leaving cubes (decomposition) are far out and tumbling, with a trail that
starts in their gap. Arriving cubes (synthesis) are close to their gap and nearly square to it, and their trail
runs through them into the gap. A leaving cube comes out of a vein of its own material; an arriving cube
completes one. Every material has one role: gold, diamond, copper and amethyst leave, redstone and emerald
arrive. The wordmark spells "ReMatter" by the same rules.

Faces carry a small pixel texture like a game block. Outlines run along the silhouette, along creases and,
thinner, where two materials meet; cubes of the same material join without a line, so the block reads as one
block with veins of material rather than as a grid of tiles.

Every colour comes from tools/family_palette.py and tools/machine_palette.py. The eight crystal hues appear
here as the raw materials they are named after (gold, copper, diamond, emerald, Silith, redstone, lapis,
amethyst): the logo shows matter, not a device. LOGO_LOOK selects one of the prepared alternatives.

  python3 tools/generate_logo.py            write the SVG files and render the PNG files (needs CairoSVG)
  python3 tools/generate_logo.py --all      also write the other looks under artwork/logo/alternatives/
  python3 tools/generate_logo.py --check    compare the SVG files and the recorded PNG checksums
"""
from __future__ import annotations

import hashlib
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

from family_palette import IVORY, SEAM, STEEL, TRIM, lighten, scale  # noqa: E402
from machine_palette import RGB  # noqa: E402

OUT = ROOT / "artwork" / "logo"
RECORD = OUT / "rendered.json"

# "veins" (recommended): a steel block with small veins of material; pieces leave and complete veins.
# "mosaic": every cube its own material, a colourful patchwork.
# "strata": the materials lie in layers like rock.
LOGO_LOOK = "veins"
LOOKS = ("veins", "mosaic", "strata")

MATERIAL = {
    "steel": STEEL,
    "trim": TRIM,
    "gold": RGB["amber"],
    "copper": RGB["copper"],
    "diamond": RGB["mint"],
    "emerald": RGB["leaf"],
    "quartz": RGB["ice"],
    "redstone": RGB["ember"],
    "lapis": RGB["blue"],
    "amethyst": RGB["violet"],
}
GEMS = {"diamond", "emerald", "redstone", "lapis", "amethyst", "quartz"}

# Inventory view of a block (gui rotation 30, 225): the top is a 2:1 rhombus, the sides a little taller.
C30 = math.cos(math.radians(30))
DEPTH = 0.433
HEIGHT = 1.0607
VIEW = (1.0, 2 * DEPTH / HEIGHT, 1.0)


def hexcolour(colour) -> str:
    return "#%02x%02x%02x" % tuple(colour)


def tone(colour, amount: float):
    """Positive amounts mix toward white, negative ones toward black."""
    return lighten(colour, amount) if amount >= 0 else scale(colour, 1 + amount)


def family(material: str) -> str:
    """Steel and its lighter trim are one material for outlines."""
    return "steel" if material in ("steel", "trim") else material


def noise(*key) -> int:
    return int.from_bytes(hashlib.sha256(repr(key).encode()).digest()[:4], "big")


def pixel_shift(material: str, *key, calm: float = 1.0) -> float:
    """Texture of one pixel: most are the plain face colour, some lighter or darker; gems sparkle, metal is calm."""
    n = noise(material in GEMS, *key) % 20
    if material in GEMS:
        value = (0, 0, 0, 0, 0, 0, 0, 0, -0.10, -0.10, -0.10, -0.16, 0.10, 0.10, 0.10, 0.10, 0.24, 0.24, 0.40, 0)[n]
    else:
        value = (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.06, -0.06, -0.10, 0.06, 0.06, 0.06, 0.10, 0)[n]
    return value * calm


class Canvas:
    def __init__(self, width: float, height: float):
        self.width, self.height = width, height
        self.layers: list[str] = []

    def poly(self, points, fill, opacity: float = 1.0, stroke=None, width: float = 0.0) -> None:
        coords = " ".join(f"{x:.2f},{y:.2f}" for x, y in points)
        attrs = [f'points="{coords}"', f'fill="{hexcolour(fill)}"']
        if opacity < 1.0:
            attrs.append(f'fill-opacity="{opacity:.3f}"')
        if stroke is not None and width > 0:
            attrs += [f'stroke="{hexcolour(stroke)}"', f'stroke-width="{width:.2f}"', 'stroke-linejoin="round"']
            if opacity < 1.0:
                attrs.append(f'stroke-opacity="{opacity:.3f}"')
        self.layers.append(f"<polygon {' '.join(attrs)}/>")

    def line(self, a, b, colour, width: float) -> None:
        self.layers.append(f'<line x1="{a[0]:.2f}" y1="{a[1]:.2f}" x2="{b[0]:.2f}" y2="{b[1]:.2f}" '
                           f'stroke="{hexcolour(colour)}" stroke-width="{width:.2f}" stroke-linecap="round"/>')

    def svg(self, label: str, background=None) -> str:
        head = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width:.0f}" height="{self.height:.0f}" '
                f'viewBox="0 0 {self.width:.0f} {self.height:.0f}" role="img" aria-label="{label}">')
        body = []
        if background is not None:
            body.append(f'<rect width="{self.width:.0f}" height="{self.height:.0f}" fill="{hexcolour(background)}"/>')
        return "\n".join([head, *body, *self.layers, "</svg>"]) + "\n"

    def embed(self, other: "Canvas", dx: float, dy: float, factor: float) -> None:
        self.layers.append(f'<g transform="translate({dx:.2f},{dy:.2f}) scale({factor:.4f})">')
        self.layers.extend(other.layers)
        self.layers.append("</g>")


def hull(points) -> list[tuple[float, float]]:
    pts = sorted(set((round(x, 3), round(y, 3)) for x, y in points))

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def bezier(start, end, ctrl, t: float):
    (sx, sy), (ex, ey), (cx, cy) = start, end, ctrl
    return ((1 - t) ** 2 * sx + 2 * (1 - t) * t * cx + t * t * ex,
            (1 - t) ** 2 * sy + 2 * (1 - t) * t * cy + t * t * ey)


def ribbon(start, end, ctrl, width: float):
    steps = 28
    left, right = [], []
    for s in range(steps + 1):
        t = s / steps
        x, y = bezier(start, end, ctrl, t)
        x1, y1 = bezier(start, end, ctrl, max(0.0, t - 0.01))
        x2, y2 = bezier(start, end, ctrl, min(1.0, t + 0.01))
        length = math.hypot(x2 - x1, y2 - y1) or 1.0
        nx, ny = -(y2 - y1) / length, (x2 - x1) / length
        w = width * (0.05 + 0.95 * t ** 1.2) / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    return left + right[::-1]


def particle(canvas: Canvas, x: float, y: float, half: float, colour, opacity: float, outline: float):
    canvas.poly([(x - half, y - half), (x + half, y - half), (x + half, y + half), (x - half, y + half)],
                tone(colour, 0.22), opacity=opacity, stroke=SEAM, width=outline)


def trail(canvas: Canvas, start, end, ctrl, colour, unit: float, width: float, key: str, into=None,
          count: int = 8, faint: bool = True, grain: float = 0.0):
    """Matter in motion: square particles from start (small, faint) to end, over a faint ribbon.

    `into` is the gap of an arriving cube: the returned step draws two more particles from the cube into it,
    after the cube itself. `grain` rounds particle sizes to the texture's pixel.
    """
    if faint:
        canvas.poly(ribbon(start, end, ctrl, unit * width), tone(colour, 0.12), opacity=0.13)
    for n in range(count):
        t = 0.08 + 0.80 * n / max(1, count - 1)
        x, y = bezier(start, end, ctrl, t)
        x2, y2 = bezier(start, end, ctrl, t + 0.01)
        length = math.hypot(x2 - x, y2 - y) or 1.0
        side = (noise(key, n) % 200 - 100) / 100.0 * unit * width * 0.36 * t
        x, y = x - (y2 - y) / length * side, y + (x2 - x) / length * side
        half = unit * (0.05 + 0.12 * t)
        if grain:
            half = max(grain, round(half / grain) * grain)
        particle(canvas, x, y, half, colour, 0.40 + 0.60 * t, unit * 0.025)
    if into is None:
        return None

    def enter():
        for f, s in ((0.66, 0.085), (0.84, 0.065)):
            particle(canvas, end[0] + (into[0] - end[0]) * f, end[1] + (into[1] - end[1]) * f, unit * s, colour,
                     0.95, unit * 0.025)
    return enter


# ---------------------------------------------------------------------------------------------------------------
# 3D: cubes in the inventory view, light from the upper left (top face lit, left face mid, right face shaded).

def rotate(v, rx: float, ry: float, rz: float):
    x, y, z = v
    a, b, c = (math.radians(t) for t in (rx, ry, rz))
    y, z = y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a)
    x, z = x * math.cos(b) + z * math.sin(b), -x * math.sin(b) + z * math.cos(b)
    x, y = x * math.cos(c) - y * math.sin(c), x * math.sin(c) + y * math.cos(c)
    return x, y, z


def face_light(normal) -> float:
    nx, ny, _ = normal
    return 0.22 * ny - 0.34 * nx


class Iso:
    def __init__(self, unit: float, ox: float, oy: float):
        self.u, self.ox, self.oy = unit, ox, oy

    def p(self, v):
        x, y, z = v
        return self.ox + (x - z) * C30 * self.u, self.oy + (x + z) * DEPTH * self.u - y * HEIGHT * self.u

    def at(self, sx: float, sy: float, y: float):
        """The world point at height y that appears at (sx, sy)."""
        a = (sx - self.ox) / (C30 * self.u)
        b = (sy - self.oy + y * HEIGHT * self.u) / (DEPTH * self.u)
        return (a + b) / 2, y, (b - a) / 2


def textured_face(canvas: Canvas, iso: Iso, corners3d, material: str, base, key, calm: float, pixels: int = 4):
    a, b, _, d = corners3d
    e1 = [(b[i] - a[i]) / pixels for i in range(3)]
    e2 = [(d[i] - a[i]) / pixels for i in range(3)]
    canvas.poly([iso.p(v) for v in corners3d], base, stroke=base, width=0.9)
    for p in range(pixels):
        for q in range(pixels):
            shift = pixel_shift(material, *key, p, q, calm=calm)
            if not shift:
                continue
            corner = [a[i] + p * e1[i] + q * e2[i] for i in range(3)]
            quad = [corner, [corner[i] + e1[i] for i in range(3)], [corner[i] + e1[i] + e2[i] for i in range(3)],
                    [corner[i] + e2[i] for i in range(3)]]
            canvas.poly([iso.p(v) for v in quad], tone(base, shift))


CUBE_FACES = (
    ((1, 0, 0), ((1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0))),   # +x right
    ((0, 1, 0), ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1))),   # +y top
    ((0, 0, 1), ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1))),   # +z left
    ((-1, 0, 0), ((0, 1, 1), (0, 1, 0), (0, 0, 0), (0, 0, 1))),
    ((0, -1, 0), ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0))),
    ((0, 0, -1), ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0))),
)


def draw_cube(canvas: Canvas, iso: Iso, centre, rot, material: str, outline: float, key: str, calm: float):
    faces = []
    for normal, corners in CUBE_FACES:
        n = rotate(normal, *rot)
        if sum(n[i] * VIEW[i] for i in range(3)) <= 1e-6:
            continue
        pts = []
        for corner in corners:
            r = rotate(tuple(c - 0.5 for c in corner), *rot)
            pts.append(tuple(centre[i] + r[i] for i in range(3)))
        faces.append((n, pts))
    canvas.poly(hull([iso.p(v) for _, pts in faces for v in pts]), SEAM, stroke=SEAM, width=outline * 3.4)
    for index, (n, pts) in enumerate(faces):
        textured_face(canvas, iso, pts, material, tone(MATERIAL[material], face_light(n)), (key, index), calm)
    for _, pts in faces:
        p2 = [iso.p(v) for v in pts]
        for i in range(4):
            canvas.line(p2[i], p2[(i + 1) % 4], SEAM, outline)


# ---------------------------------------------------------------------------------------------------------------
# The mark

N = 4
MARK_SIZE = 512.0
# Floating cubes on the 512 px canvas: the gap they belong to, where they float, how they turn, the direction of
# their matter and the control point of their trail (an arriving cube's trail begins at `source`).
MARK = {
    "unit": 46.0, "origin": (256.0, 266.0), "outline": 0.05, "calm": 1.0, "particles": 8, "ribbon": True,
    "pieces": (
        {"gap": (3, 3, 0), "material": "gold", "at": (446, 94), "rot": (14, -24, 12), "dir": "out",
         "ctrl": (384, 112)},
        {"gap": (0, 3, 1), "material": "diamond", "at": (90, 88), "rot": (-14, 20, -16), "dir": "out",
         "ctrl": (200, 86)},
        {"gap": (3, 0, 0), "material": "redstone", "at": (452, 382), "rot": (4, -5, 3), "dir": "in",
         "source": (480, 488), "ctrl": (494, 414)},
        {"gap": (0, 1, 3), "material": "emerald", "at": (56, 352), "rot": (-4, 5, -3), "dir": "in",
         "source": (34, 484), "ctrl": (16, 384)},
    ),
}
# The icon sizes (64 and 128 px): a larger block, two pieces, heavier outlines, particles without a ribbon.
MARK_SMALL = {
    "unit": 52.0, "origin": (250.0, 270.0), "outline": 0.095, "calm": 0.6, "particles": 4, "ribbon": False,
    "pieces": (
        {"gap": (0, 3, 1), "material": "diamond", "at": (84, 84), "rot": (-14, 20, -16), "dir": "out",
         "ctrl": (190, 70)},
        {"gap": (3, 0, 0), "material": "redstone", "at": (446, 392), "rot": (4, -5, 3), "dir": "in",
         "source": (488, 488), "ctrl": (490, 420)},
    ),
}


def block_materials(look: str) -> dict[tuple[int, int, int], str]:
    voxels = {(i, j, k): "steel" for i in range(N) for j in range(N) for k in range(N)}
    if look == "strata":
        layers = ("copper", "steel", "lapis", "trim")
        for key in voxels:
            voxels[key] = layers[key[1]]
        voxels.update({(2, 3, 0): "gold", (3, 2, 0): "gold", (0, 3, 0): "diamond", (3, 0, 1): "redstone",
                       (0, 0, 3): "emerald", (3, 2, 2): "amethyst", (1, 3, 2): "quartz"})
        return voxels
    if look == "mosaic":
        order = ("copper", "steel", "amethyst", "trim", "diamond", "steel", "gold", "emerald", "steel", "lapis",
                 "redstone", "trim", "quartz")
        for key in voxels:
            voxels[key] = order[noise("m", key) % len(order)]
        return voxels
    voxels.update({
        # gold under and beside the right corner: the leaving gold cube was its top
        (3, 2, 0): "gold", (2, 3, 0): "gold",
        # diamond at the back of the top: the leaving diamond cube was part of it
        (0, 3, 0): "diamond", (1, 3, 1): "diamond",
        # redstone on the right face and emerald on the left face: the arriving cubes complete them
        (3, 0, 1): "redstone",
        (0, 0, 3): "emerald",
        # small veins of the other materials
        (1, 1, 3): "copper", (2, 1, 3): "copper",
        (3, 2, 2): "amethyst",
        (1, 3, 2): "quartz",
        (3, 3, 2): "lapis",
    })
    return voxels


def mark(look: str = LOGO_LOOK, small: bool = False) -> Canvas:
    spec = MARK_SMALL if small else MARK
    canvas = Canvas(MARK_SIZE, MARK_SIZE)
    unit = spec["unit"]
    iso = Iso(unit, *spec["origin"])
    outline = unit * spec["outline"]
    grain = unit / 8 if small else 0.0

    def world(v):
        return tuple(v[i] - N / 2 for i in range(3))

    voxels = block_materials(look)
    for piece in spec["pieces"]:
        voxels.pop(piece["gap"], None)

    faces = []
    for (i, j, k), material in voxels.items():
        for normal, corners in CUBE_FACES[:3]:
            ni, nj, nk = i + normal[0], j + normal[1], k + normal[2]
            if (ni, nj, nk) in voxels:
                continue
            interior = 0 <= ni < N and 0 <= nj < N and 0 <= nk < N
            faces.append(((i, j, k), normal, corners, material, interior))
    faces.sort(key=lambda f: (sum(f[0]), f[1]))
    # the outline follows the faces, so a notch in the silhouette stays open
    for v, _, corners, _, _ in faces:
        canvas.poly([iso.p(world(tuple(v[a] + c[a] for a in range(3)))) for c in corners], SEAM, stroke=SEAM,
                    width=outline * 3.4)

    def visible(v, normal):
        return v in voxels and tuple(v[a] + normal[a] for a in range(3)) not in voxels

    for v, normal, corners, material, interior in faces:
        pts = [world(tuple(v[a] + c[a] for a in range(3))) for c in corners]
        base = tone(MATERIAL[material], face_light(normal) + (-0.30 if interior else 0.0))
        textured_face(canvas, iso, pts, material, base, ("b",) + v + tuple(normal), spec["calm"])
        p2 = [iso.p(p) for p in pts]
        centre = [sum(p[a] for p in pts) / 4 for a in range(3)]
        for e in range(4):
            mid = [(pts[e][a] + pts[(e + 1) % 4][a]) / 2 - centre[a] for a in range(3)]
            step = tuple(int(round(2 * m)) for m in mid)
            neighbour = tuple(v[a] + step[a] for a in range(3))
            if visible(neighbour, normal):
                if family(voxels[neighbour]) == family(material):
                    continue
                # a vein is an inlay: its border is thinner than an edge of the block
                canvas.line(p2[e], p2[(e + 1) % 4], SEAM, outline * 0.6)
            else:
                canvas.line(p2[e], p2[(e + 1) % 4], SEAM, outline)

    finish = []
    for piece in spec["pieces"]:
        gap_centre = world(tuple(c + 0.5 for c in piece["gap"]))
        centre = iso.at(*piece["at"], gap_centre[1])
        colour = MATERIAL[piece["material"]]
        if piece["dir"] == "out":
            trail(canvas, iso.p(gap_centre), iso.p(centre), piece["ctrl"], colour, unit, 1.0, piece["material"],
                  count=spec["particles"], faint=spec["ribbon"], grain=grain)
        else:
            finish.append(trail(canvas, piece["source"], iso.p(centre), piece["ctrl"], colour, unit, 1.0,
                                piece["material"], into=iso.p(gap_centre), count=spec["particles"],
                                faint=spec["ribbon"], grain=grain))
        draw_cube(canvas, iso, centre, piece["rot"], piece["material"], outline, piece["material"], spec["calm"])
    for step in finish:
        step()
    return canvas


# ---------------------------------------------------------------------------------------------------------------
# The wordmark: blocky letters, strokes two cells wide and two cells tall, extruded toward the lower right.

GLYPHS = {
    "R": ("#######.",
          "########",
          "##....##",
          "##....##",
          "########",
          "#######.",
          "##..###.",
          "##...###",
          "##....##"),
    "e": (".#####.",
          "#######",
          "##...##",
          "#######",
          "##.....",
          "#######",
          ".######"),
    "M": ("##......##",
          "###....###",
          "####..####",
          "##.####.##",
          "##..##..##",
          "##......##",
          "##......##",
          "##......##",
          "##......##"),
    "a": (".#####.",
          ".######",
          ".....##",
          ".######",
          "##...##",
          "#######",
          ".######"),
    "t": (".##...",
          ".##...",
          "######",
          "######",
          ".##...",
          ".##...",
          ".##...",
          ".#####",
          "..####"),
    "r": ("##.####",
          "#######",
          "###....",
          "##.....",
          "##.....",
          "##.....",
          "##....."),
}
WORD = "ReMatter"
CAP = 9
SPACING = 2

# Pieces: letter index, glyph cells (column, row) they take, and where they float. Offsets, sources and control
# points are in cells relative to the centre of the gap.
WORD_PIECES = (
    {"letter": 0, "cells": ((0, 0), (1, 0), (0, 1), (1, 1)), "material": "copper", "offset": (-2.4, -3.9),
     "ctrl": (0.2, -3.0), "rot": -16, "dir": "out"},
    {"letter": 3, "cells": ((1, 0), (2, 0), (1, 1), (2, 1)), "material": "diamond", "offset": (-1.6, -4.0),
     "ctrl": (0.2, -3.0), "rot": -14, "dir": "out"},
    {"letter": 4, "cells": ((4, 2), (5, 2), (4, 3), (5, 3)), "material": "gold", "offset": (1.6, -4.4),
     "ctrl": (0.2, -2.8), "rot": 12, "dir": "out"},
    {"letter": 7, "cells": ((5, 0), (6, 0), (5, 1), (6, 1)), "material": "amethyst", "offset": (2.2, -3.8),
     "ctrl": (0.6, -2.8), "rot": 18, "dir": "out"},
    {"letter": 1, "cells": ((5, 5), (6, 5), (5, 6), (6, 6)), "material": "emerald", "offset": (0.4, 2.8),
     "source": (1.1, 5.1), "ctrl": (0.7, 4.0), "rot": 4, "dir": "in"},
    {"letter": 5, "cells": ((4, 7), (5, 7), (4, 8), (5, 8)), "material": "redstone", "offset": (-0.3, 2.8),
     "source": (-1.0, 5.1), "ctrl": (-0.6, 4.0), "rot": -4, "dir": "in"},
)
# Veins: letter index, material, glyph cells. Each touches the gap of the piece of its material.
WORD_VEINS = (
    (0, "copper", ((2, 0), (2, 1))),
    (3, "diamond", ((3, 0), (3, 1))),
    (4, "gold", ((3, 2), (3, 3))),
    (7, "amethyst", ((3, 0), (4, 0))),
    (1, "emerald", ((4, 5), (4, 6))),
    (5, "redstone", ((3, 7), (3, 8))),
)
ALL_PIECES = tuple(range(len(WORD_PIECES)))


def word_cells(look: str, pieces):
    cells = {}
    starts = []
    x = 0
    for letter in WORD:
        rows = GLYPHS[letter]
        top = CAP - len(rows)
        starts.append((x, top))
        for r, line in enumerate(rows):
            for c, ch in enumerate(line):
                if ch == "#":
                    cells[(x + c, top + r)] = "trim"
        x += len(rows[0]) + SPACING
    width = x - SPACING
    if look == "strata":
        bands = ("trim", "trim", "steel", "copper", "steel", "trim", "lapis", "steel", "steel")
        for key in cells:
            cells[key] = bands[key[1]]
    elif look == "mosaic":
        order = ("copper", "amethyst", "emerald", "quartz", "gold", "lapis", "redstone", "diamond", "trim", "steel")
        for key in cells:
            cells[key] = order[noise("wm", key[0] // 2, key[1] // 2) % len(order)]
    else:
        for letter, material, keys in WORD_VEINS:
            sx, top = starts[letter]
            for c, r in keys:
                key = (sx + c, top + r)
                assert key in cells, (letter, material, (c, r))
                cells[key] = material
    gaps = {}
    for index in pieces:
        piece = WORD_PIECES[index]
        sx, top = starts[piece["letter"]]
        for c, r in piece["cells"]:
            key = (sx + c, top + r)
            assert key in cells, (piece, (c, r))
            cells.pop(key)
            gaps[key] = index
    return cells, gaps, width


def wordmark(look: str = LOGO_LOOK, pieces=ALL_PIECES) -> Canvas:
    c = 22.0
    depth = 0.45 * c
    cells, gaps, width = word_cells(look, pieces)
    margin_x, margin_top, margin_bottom = 5.0 * c, 6.0 * c, 5.8 * c
    canvas = Canvas(width * c + 2 * margin_x, CAP * c + margin_top + margin_bottom)
    outline = c * 0.08

    def draw_group(group: dict, ox: float, oy: float, rot: float, key: str, holes=frozenset()):
        cols = [k[0] for k in group]
        rows = [k[1] for k in group]
        pivot = ((min(cols) + max(cols) + 1) / 2 * c + ox, (min(rows) + max(rows) + 1) / 2 * c + oy)
        a = math.radians(rot)

        def P(x, y):
            dx, dy = x - pivot[0], y - pivot[1]
            return pivot[0] + dx * math.cos(a) - dy * math.sin(a), pivot[1] + dx * math.sin(a) + dy * math.cos(a)

        def sq(col, row, x0f=0.0, y0f=0.0, x1f=1.0, y1f=1.0):
            x0, y0 = ox + (col + x0f) * c, oy + (row + y0f) * c
            x1, y1 = ox + (col + x1f) * c, oy + (row + y1f) * c
            return [P(x0, y0), P(x1, y0), P(x1, y1), P(x0, y1)]

        def ext(pt):
            return pt[0] + depth, pt[1] + depth

        for (col, row) in group:
            q = sq(col, row)
            canvas.poly(hull(q + [ext(p) for p in q]), SEAM, stroke=SEAM, width=outline * 4.2)
        for (col, row) in sorted(group, key=lambda k: (k[0] + k[1], k[1])):
            colour = MATERIAL[group[(col, row)]]
            q = sq(col, row)
            if (col + 1, row) not in group:
                dim = -0.52 if (col + 1, row) in holes else -0.40
                canvas.poly([q[1], ext(q[1]), ext(q[2]), q[2]], tone(colour, dim), stroke=SEAM, width=outline)
            if (col, row + 1) not in group:
                dim = -0.64 if (col, row + 1) in holes else -0.54
                canvas.poly([q[3], q[2], ext(q[2]), ext(q[3])], tone(colour, dim), stroke=SEAM, width=outline)
        for (col, row), material in sorted(group.items()):
            base = tone(MATERIAL[material], 0.05)
            canvas.poly(sq(col, row), base, stroke=base, width=0.9)
            for p in range(2):
                for qq in range(2):
                    shift = pixel_shift(material, key, col, row, p, qq, calm=0.45)
                    if shift:
                        canvas.poly(sq(col, row, p / 2, qq / 2, (p + 1) / 2, (qq + 1) / 2), tone(base, shift))
        for (col, row), material in group.items():
            q = sq(col, row)
            for e, (dc, dr) in enumerate(((0, -1), (1, 0), (0, 1), (-1, 0))):
                other = group.get((col + dc, row + dr))
                if other is not None and family(other) == family(material):
                    continue
                # a vein is an inlay: its border is thinner than the outline of the letter
                canvas.line(q[e], q[(e + 1) % 4], SEAM, outline * (0.65 if other is not None else 1.0))

    ox, oy = margin_x, margin_top
    draw_group(cells, ox, oy, 0.0, "word", frozenset(gaps))

    finish = []
    for index in pieces:
        piece = WORD_PIECES[index]
        keys = [k for k, owner in gaps.items() if owner == index]
        cols = [k[0] for k in keys]
        rows = [k[1] for k in keys]
        gx = ox + (min(cols) + max(cols) + 1) / 2 * c
        gy = oy + (min(rows) + max(rows) + 1) / 2 * c
        dx, dy = piece["offset"][0] * c, piece["offset"][1] * c
        colour = MATERIAL[piece["material"]]
        ctrl = (gx + piece["ctrl"][0] * c, gy + piece["ctrl"][1] * c)
        if piece["dir"] == "out":
            trail(canvas, (gx, gy), (gx + dx, gy + dy), ctrl, colour, c, 2.0, piece["material"], count=6,
                  grain=c / 4)
        else:
            source = (gx + piece["source"][0] * c, gy + piece["source"][1] * c)
            finish.append(trail(canvas, source, (gx + dx, gy + dy), ctrl, colour, c, 2.0, piece["material"],
                                into=(gx, gy + c), count=6, grain=c / 4))
        draw_group({k: piece["material"] for k in keys}, ox + dx, oy + dy, piece["rot"], piece["material"])
    for step in finish:
        step()
    return canvas


# ---------------------------------------------------------------------------------------------------------------
# Lockups: the word drops the pieces that would crowd the mark; their veins stay.

LOCKUP_PIECES = (1, 2, 3, 4, 5)
STACKED_PIECES = (0, 3, 4, 5)


def lockup(look: str, stacked: bool) -> Canvas:
    m = mark(look)
    if stacked:
        w = wordmark(look, STACKED_PIECES)
        factor = 1.6
        width = max(w.width, m.width * factor)
        canvas = Canvas(width, m.height * factor + w.height - 110)
        canvas.embed(m, (width - m.width * factor) / 2, 0, factor)
        canvas.embed(w, (width - w.width) / 2, m.height * factor - 110, 1.0)
        return canvas
    w = wordmark(look, LOCKUP_PIECES)
    factor = (w.height * 1.3) / m.height
    canvas = Canvas(m.width * factor + w.width - 20, w.height * 1.3)
    canvas.embed(m, 0, 0, factor)
    canvas.embed(w, m.width * factor - 20, (canvas.height - w.height) / 2, 1.0)
    return canvas


# name: (drawing, label, PNG widths, background of the drawing itself)
def outputs(look: str):
    return {
        "rematter-mark": (lambda: mark(look), "ReMatter block mark", (400, 1024), None),
        "rematter-mark-small": (lambda: mark(look, small=True), "ReMatter block mark", (64, 128), None),
        "rematter-avatar": (lambda: mark(look), "ReMatter", (400,), SEAM),
        "rematter-avatar-small": (lambda: mark(look, small=True), "ReMatter", (64, 128), SEAM),
        "rematter-wordmark": (lambda: wordmark(look), "ReMatter", (1600,), None),
        "rematter-logo": (lambda: lockup(look, stacked=False), "ReMatter", (800, 2000), None),
        "rematter-logo-stacked": (lambda: lockup(look, stacked=True), "ReMatter", (1200,), None),
    }


PREVIEW_BACKGROUNDS = {"dark": SEAM, "light": lighten(IVORY, 0.55)}
PREVIEWS = ("rematter-mark", "rematter-mark-small", "rematter-wordmark", "rematter-logo", "rematter-logo-stacked")


def png(canvas: Canvas, svg: str, target: Path, width: int) -> str:
    import cairosvg
    target.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring=svg.encode(), write_to=str(target), output_width=width,
                     output_height=round(canvas.height * width / canvas.width))
    return hashlib.sha256(target.read_bytes()).hexdigest()


def main() -> int:
    args = sys.argv[1:]
    check = "--check" in args
    # --check covers the active look and every alternative that was written
    looks = list(LOOKS) if "--all" in args or check else [LOGO_LOOK]
    if not check:
        try:
            import cairosvg  # noqa: F401
        except ImportError as error:  # depends on the local machine
            raise SystemExit("Rendering the logo needs CairoSVG (pip install cairosvg).") from error
    problems: list[str] = []
    rendered: dict[str, str] = {}
    for look in looks:
        folder = OUT if look == LOGO_LOOK else OUT / "alternatives" / look
        if check and look != LOGO_LOOK and not folder.is_dir():
            continue
        for name, (draw, label, widths, background) in outputs(look).items():
            canvas = draw()
            svg = canvas.svg(label, background)
            path = folder / f"{name}.svg"
            if check:
                if not path.is_file() or path.read_text(encoding="utf-8") != svg:
                    problems.append(f"{path.relative_to(ROOT)} differs from the generator")
                continue
            folder.mkdir(parents=True, exist_ok=True)
            path.write_text(svg, encoding="utf-8")
            if look == LOGO_LOOK:
                for width in widths:
                    target = folder / f"{name}-{width}.png"
                    rendered[target.relative_to(OUT).as_posix()] = png(canvas, svg, target, width)
            if name in PREVIEWS:
                for mode, colour in PREVIEW_BACKGROUNDS.items():
                    target = folder / "preview" / f"{name}-on-{mode}.png"
                    digest = png(canvas, canvas.svg(label, colour), target, min(1200, max(widths) * 2))
                    if look == LOGO_LOOK:
                        rendered[target.relative_to(OUT).as_posix()] = digest
    if check:
        expected_pngs: set[str] = set()
        for name, (_, _, widths, _) in outputs(LOGO_LOOK).items():
            for width in widths:
                expected_pngs.add(f"{name}-{width}.png")
            if name in PREVIEWS:
                for mode in PREVIEW_BACKGROUNDS:
                    expected_pngs.add(f"preview/{name}-on-{mode}.png")
        record = json.loads(RECORD.read_text(encoding="utf-8")) if RECORD.is_file() else {"png": {}}
        if record.get("look") != LOGO_LOOK:
            problems.append(f"{RECORD.relative_to(ROOT)} records another look")
        recorded_pngs = record.get("png", {})
        for missing in sorted(expected_pngs - set(recorded_pngs)):
            problems.append(f"artwork/logo/{missing} is missing from {RECORD.relative_to(ROOT)}")
        for extra in sorted(set(recorded_pngs) - expected_pngs):
            problems.append(f"artwork/logo/{extra} in {RECORD.relative_to(ROOT)} is not produced by the generator")
        for name, digest in recorded_pngs.items():
            path = OUT / name
            if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
                problems.append(f"artwork/logo/{name} does not match {RECORD.relative_to(ROOT)}")
        for problem in problems:
            print(problem)
        print("logo up to date" if not problems else f"{len(problems)} logo problems")
        return 1 if problems else 0
    RECORD.write_text(json.dumps({"look": LOGO_LOOK, "png": dict(sorted(rendered.items()))}, indent=2) + "\n",
                      encoding="utf-8")
    print(f"wrote the logo ({LOGO_LOOK}) to {OUT.relative_to(ROOT)}: {len(rendered)} PNG files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
