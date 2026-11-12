#!/usr/bin/env python3
"""Generate the Primal Ore, Primal item and Entangled Link assets with their loot, recipes and world generation.

Primal Matter has its own colour signature, apart from the eight Matrix Crystal hues: a white-hot core in
crystals that wear a spectrum from teal over cyan-blue, ultraviolet and magenta to rose-gold, with ultraviolet
shadows. PRIMAL holds these colours and SPECTRUM their order. PRIMAL_LOOK selects how much of the spectrum
shows: "spectrum" gives every crystal of a cluster its own place in it, "aurora" keeps all crystals ultraviolet
with a shimmer between cyan-blue and magenta.

A Primal Ore is layered like the vanilla grass block. Three boxes of the same size carry the host stone, which
is the vanilla texture referenced at run time, a socket layer lit by the world, and an animated crystal layer at
full brightness. ORE_DESIGN selects the crystal drawing; the alternatives stay available in ORE_DESIGNS.

The Primal Shard, Primal Crystal and Discovery Lens icons are SVG drawings in artwork/primal/, rendered to
128-pixel sprites with CairoSVG (pip install cairosvg). artwork/primal/rendered.json records the checksum of
every drawing and of the sprite rendered from it, so --check and runs that leave the drawings unchanged do not
need CairoSVG.
"""
from pathlib import Path
import argparse
import functools
import hashlib
import json
import math
import struct

from check_assets import strip_ancillary_chunks
from generated_files import GeneratedFiles
from recipe_discovery import shaped_discovery
from family_geometry import Box, build, check_boxes
from family_sprites import Sprite, casing
from family_palette import (BASE, HIGHLIGHT, PORT_DARK, PORT_LIGHT, PORT_METAL, SEAM, SHADE, WELL,
                            PRIMAL_CORE, PRIMAL_CORE_SOFT, PRIMAL_LIGHT, PRIMAL_BODY, PRIMAL_DEEP,
                            PRIMAL_VOID, PRIMAL_INK, PRIMAL_TEAL, PRIMAL_COOL, PRIMAL_WARM, PRIMAL_GOLD)

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ART = RES / "assets/rematter"
DATA = RES / "data"
DRAWINGS = ROOT / "artwork/primal"
FACES = ("north", "east", "south", "west", "up", "down")

# The Primal signature. None of these is one of the eight Matrix Crystal hues.
PRIMAL = {name: "#" + bytes(colour).hex() for name, colour in {
    "core": PRIMAL_CORE, "core_soft": PRIMAL_CORE_SOFT, "light": PRIMAL_LIGHT, "body": PRIMAL_BODY,
    "deep": PRIMAL_DEEP, "void": PRIMAL_VOID, "ink": PRIMAL_INK, "teal": PRIMAL_TEAL,
    "cool": PRIMAL_COOL, "warm": PRIMAL_WARM, "gold": PRIMAL_GOLD,
}.items()}

# The spectrum from its cool to its warm end. A place in it is a number from 0 (teal) to 1 (rose-gold).
SPECTRUM = ("teal", "cool", "body", "warm", "gold")
# "spectrum": the crystals of a cluster stand at different places of the spectrum and drift along it.
# "aurora": every crystal is ultraviolet; only its prismatic edges turn between cyan-blue and magenta.
PRIMAL_LOOKS = ("spectrum", "aurora")
# The look that is written. Run the generator again after changing it; the item icons then need CairoSVG.
PRIMAL_LOOK = "spectrum"

# Dimension, replaced block, biome tag, height range, host textures and the socket colour that reads on the host.
ORES = {
    "deepslate_primal_ore": ("minecraft:overworld", "minecraft:deepslate", "#minecraft:is_overworld", -60, -24,
                             "minecraft:block/deepslate", "minecraft:block/deepslate_top", "#16131d"),
    "nether_primal_ore": ("minecraft:the_nether", "minecraft:netherrack", "#minecraft:is_nether", 16, 96,
                          "minecraft:block/netherrack", "minecraft:block/netherrack", "#1f0b14"),
    "end_primal_ore": ("minecraft:the_end", "minecraft:end_stone", "#minecraft:is_end", 0, 80,
                       "minecraft:block/end_stone", "minecraft:block/end_stone", "#4a4152"),
}

# The crystal layer of the ore, one character per texel of a 16 x 16 face:
#   w core, c core halo, l lit facet, b body, d shaded facet, v hollow, e prismatic edge,
#   k socket without a crystal (a crack), . host stone.
# Every texel next to a crystal becomes socket by itself. "sparkles" are crystal tips that flash once per loop.
# "places" gives every crystal texel its home in the spectrum for the look "spectrum", one digit per texel:
#   0 teal, 2 cyan-blue, 4 ultraviolet, 6 magenta, 8 rose-gold, odd digits halfway between their neighbours.
ORE_DESIGNS = {
    "A": {"title": "Shard cluster", "sparkles": ((4, 4), (14, 3)), "rows": (
        "................",
        ".........w......",
        "........lc......",
        "........lbd...w.",
        "....w..elbd..cb.",
        "...lc...bbd.lbd.",
        "...lbd..bd..bd..",
        "..elbd..d.......",
        "..lcbd..........",
        "..lbbd.w........",
        ".elbd.cb........",
        "..bd.lbd....w...",
        "..d..bd....cbe..",
        "..........lbd...",
        "..........bd....",
        "................"), "places": (
        "................",
        ".........2......",
        "........22......",
        "........222...0.",
        "....4..2333..00.",
        "...44...333.000.",
        "...444..33..00..",
        "..4444..3.......",
        "..4444..........",
        "..4444.6........",
        ".4444.66........",
        "..45.666....7...",
        "..5..66....888..",
        "..........888...",
        "..........88....",
        "................")},
    "B": {"title": "Vein", "sparkles": ((10, 5), (5, 10)), "rows": (
        "................",
        ".............d..",
        "............b...",
        "...........l....",
        ".........ecb....",
        ".........lwb....",
        ".........lcd....",
        ".........cbd....",
        "........lbd.....",
        "....ec.lbd......",
        "...lcwbcbbl.....",
        "..lbddd..dbbl...",
        ".bd........dbce.",
        ".d............bd",
        "................",
        "................"), "places": (
        "................",
        ".............0..",
        "............0...",
        "...........1....",
        ".........122....",
        ".........222....",
        ".........233....",
        ".........334....",
        "........444.....",
        "....66.444......",
        "...66654455.....",
        "..66666..5566...",
        ".66........6778.",
        ".6............88",
        "................",
        "................")},
    "C": {"title": "Geode eye", "sparkles": ((6, 7), (8, 8)), "rows": (
        "................",
        "................",
        "..............k.",
        ".............k..",
        "......dddd..k...",
        "....ddlbvbdd....",
        "..ddvvcbvlvvdd..",
        ".dvvvvwvvcvvvvd.",
        ".dvvvvvvwvvvvvd.",
        "..ddvvlvcvvvdd..",
        "....ddbvbldd....",
        "...k..dddd......",
        "..k.............",
        ".k..............",
        "................",
        "................"), "places": (
        "................",
        "................",
        "................",
        "................",
        "......3445......",
        "....22664066....",
        "..114466404477..",
        ".04444644044448.",
        ".04444448444448.",
        "..114424844477..",
        "....22248066....",
        "......3445......",
        "................",
        "................",
        "................",
        "................")},
}
# The design that is written. "A", "B" or "C"; run the generator again after changing it.
ORE_DESIGN = "A"

# One loop of the crystal layer. The game blends neighbouring frames, the last one back into the first.
ORE_FRAMES = 16
ORE_FRAME_TICKS = 6
ORE_GLOW = "primal_ore_glow"
ORE_EMISSION = 15

ICONS = ("primal_shard", "primal_crystal", "discovery_lens")
CELLS = ("primal_cell_16k", "primal_cell_64k", "primal_cell_256k")
# Sprites of earlier versions that no model uses any more: the flat ore faces and the old Primal Cell cores.
# The Primal Cell items are cartridges of generate_cell_models.py; this generator keeps only their recipes.
RETIRED = tuple(f"textures/block/{name}.png" for name in ORES) + tuple(f"textures/block/{name}_core.png" for name in CELLS)


def rgb(value):
    return tuple(bytes.fromhex(value.lstrip("#")))


def mix(first, second, amount):
    return tuple(round(start + (end - start) * amount) for start, end in zip(first, second))


def noise(x, y):
    return ((x * 73 + y * 119 + x * y * 29) ^ (x * 11 + y * 3)) % 17 - 8


# ---------------------------------------------------------------------------------------------------------
# Primal Ore
# ---------------------------------------------------------------------------------------------------------

ROLE_COLOURS = {"w": "core", "c": "core_soft", "l": "light", "b": "body", "d": "deep", "v": "void"}
NEIGHBOURS = ((1, 0), (-1, 0), (0, 1), (0, -1))
# Look "spectrum", in shares of the whole spectrum: how far a crystal drifts from its home, how much later a
# crystal at the warm end swings than one at the cool end (in loops), and how far a lit facet leans toward the
# nearer end of the spectrum and a shaded one toward the ultraviolet middle.
ORE_DRIFT = .1
ORE_DRIFT_LAG = .5
ORE_FACET_LEAN = .05
# How far a lit facet is lifted toward white, how much of its crystal's colour the halo of a core takes, and
# how far the crest of light lifts a crystal toward white.
ORE_FACET_LIGHT, ORE_HALO_TINT, ORE_CREST = .2, .45, .32


def spectrum(place):
    """Colour at a place of the spectrum, 0 (teal) to 1 (rose-gold)."""
    scaled = min(1.0, max(0.0, place)) * (len(SPECTRUM) - 1)
    index = min(int(scaled), len(SPECTRUM) - 2)
    return mix(rgb(PRIMAL[SPECTRUM[index]]), rgb(PRIMAL[SPECTRUM[index + 1]]), scaled - index)


def in_shade(colour):
    """A facet turned away from the light: every Primal shadow is ultraviolet, as deep is to body."""
    return tuple(round(channel * deep / body)
                 for channel, deep, body in zip(colour, rgb(PRIMAL["deep"]), rgb(PRIMAL["body"])))


def ore_layers(design=None):
    """Crystal texels with their role and the socket texels of a design."""
    return _ore_layers(design or ORE_DESIGN)[:2]


def ore_places(design=None):
    """Home of every crystal texel in the spectrum, 0 to 1."""
    return _ore_layers(design or ORE_DESIGN)[2]


@functools.lru_cache(maxsize=None)
def _ore_layers(design):
    rows, places = ORE_DESIGNS[design]["rows"], ORE_DESIGNS[design]["places"]
    if any(len(grid) != 16 or any(len(row) != 16 for row in grid) for grid in (rows, places)):
        raise ValueError("An ore design is 16 rows of 16 texels")
    glow = {(x, y): role for y, row in enumerate(rows) for x, role in enumerate(row) if role in "wclbdve"}
    socket = {(x, y) for y, row in enumerate(rows) for x, role in enumerate(row) if role == "k"}
    unknown = {role for row in rows for role in row} - set("wclbdvek.")
    if unknown:
        raise ValueError(f"Unknown texel role in an ore design: {sorted(unknown)}")
    home = {(x, y): int(place) / 8 for y, row in enumerate(places) for x, place in enumerate(row) if place in "012345678"}
    if set(home) != set(glow) or any(place not in "012345678." for row in places for place in row):
        raise ValueError(f"Ore design {design}: every crystal texel needs one place from 0 to 8, and no other texel")
    for x, y in glow:
        socket.update((x + dx, y + dy) for dx, dy in NEIGHBOURS
                      if 0 <= x + dx < 16 and 0 <= y + dy < 16 and (x + dx, y + dy) not in glow)
    return glow, socket, home


def drift(home, frame):
    """Place of a crystal in one frame: it swings once per loop around its home and never leaves the spectrum.

    Crystals with different homes swing out of step, so the colours of a cluster move like a slow wave.
    """
    low, high = max(0.0, home - ORE_DRIFT), min(1.0, home + ORE_DRIFT)
    return low + (high - low) * (.5 + .5 * math.sin(2 * math.pi * (frame / ORE_FRAMES - home * ORE_DRIFT_LAG)))


def glow_colour(design, x, y, frame, look=None):
    """Colour of one crystal texel in one frame, or None for a texel without crystal.

    A soft crest of light crosses the face diagonally once per loop. Its phase continues across the edge of the
    texture, so the light runs on through neighbouring ore blocks. Each sparkle tip flashes once, half a loop
    away from the crest. In the look "spectrum" every crystal wears the colour of its place in the spectrum,
    drifts slowly around it and keeps a white core and an ultraviolet shadow; its prismatic edges run through
    the whole spectrum. In the look "aurora" all crystals are ultraviolet and the edges turn from cyan-blue to
    magenta and back.
    """
    design, look = design or ORE_DESIGN, look or PRIMAL_LOOK
    glow, _ = ore_layers(design)
    role = glow.get((x, y))
    if role is None:
        return None
    phase = ((x + y) / 16 - frame / ORE_FRAMES) % 1
    crest = max(0.0, (math.cos(2 * math.pi * phase) - .4) / .6) ** 2
    lift = .45
    if look == "aurora":
        if role == "e":
            colour = mix(rgb(PRIMAL["cool"]), rgb(PRIMAL["warm"]), .5 - .5 * math.cos(2 * math.pi * (phase + .25)))
        else:
            colour = rgb(PRIMAL[ROLE_COLOURS[role]])
    elif look == "spectrum":
        lift = ORE_CREST
        home = ore_places(design)[(x, y)]
        place, lean = drift(home, frame), ORE_FACET_LEAN if home > .5 else -ORE_FACET_LEAN
        if role == "w":
            colour = rgb(PRIMAL["core"])
        elif role == "v":
            colour = rgb(PRIMAL["void"])
        elif role == "c":
            colour = mix(rgb(PRIMAL["core"]), spectrum(place + lean), ORE_HALO_TINT)
        elif role == "l":
            colour = mix(spectrum(place + lean), rgb(PRIMAL["core"]), ORE_FACET_LIGHT)
        elif role == "b":
            colour = spectrum(place)
        elif role == "d":
            colour = in_shade(spectrum(place - lean))
        else:
            colour = spectrum(.5 - .5 * math.cos(2 * math.pi * (phase + .25)))
    else:
        raise ValueError(f"Unknown Primal look: {look}")
    colour = mix(colour, rgb(PRIMAL["core"]), (.18 if role == "v" else lift) * crest)
    flash = 0.0
    for tip_x, tip_y in ORE_DESIGNS[design]["sparkles"]:
        distance = abs(x - tip_x) + abs(y - tip_y)
        if distance <= 1:
            away = (frame - (tip_x + tip_y + ORE_FRAMES // 2)) % ORE_FRAMES
            strength = {0: 1.0, 1: .4, ORE_FRAMES - 1: .4}.get(away, 0.0)
            flash = max(flash, strength * (1.0 if distance == 0 else .2 if role == "v" else .6))
    return mix(colour, rgb(PRIMAL["core"]), flash)


def glow_strip(design=None, look=None):
    """Pixel function of the animated crystal layer: the frames below each other, alpha only 0 or 255."""
    def pixel(x, y):
        colour = glow_colour(design, x, y % 16, y // 16, look)
        return (0, 0, 0, 0) if colour is None else colour + (255,)
    return pixel


def socket_pixel(design, colour):
    _, socket = ore_layers(design)
    return lambda x, y: rgb(colour) + (255,) if (x, y) in socket else (0, 0, 0, 0)


def layer(texture, **extra):
    return {"from": [0, 0, 0], "to": [16, 16, 16], **extra, "faces": {
        face: {"uv": [0, 0, 16, 16], "texture": texture(face), "cullface": face} for face in FACES}}


def ore_template():
    """Host stone, socket and crystals as three boxes of the same size, drawn in this order."""
    return {"parent": "minecraft:block/block", "elements": [
        layer(lambda face: "#host_end" if face in ("up", "down") else "#host_side"),
        layer(lambda face: "#socket"),
        # The crystals ignore the light of the world, the direction of their face and ambient occlusion.
        layer(lambda face: "#glow", shade=False, light_emission=ORE_EMISSION,
              neoforge_data={"ambient_occlusion": False})]}


def write_ores(out):
    out.png(ART / f"textures/block/{ORE_GLOW}.png", 16, 16 * ORE_FRAMES, glow_strip())
    out.json(ART / f"textures/block/{ORE_GLOW}.png.mcmeta",
             {"animation": {"frametime": ORE_FRAME_TICKS, "interpolate": True}})
    out.json(ART / "models/block/primal_ore.json", ore_template())
    for name, (dimension, host, biome, low, high, side, end, socket) in ORES.items():
        out.png(ART / f"textures/block/{name}_socket.png", 16, 16, socket_pixel(None, socket))
        out.json(ART / f"models/block/{name}.json", {"parent": "rematter:block/primal_ore", "textures": {
            "particle": side, "host_side": side, "host_end": end,
            "socket": f"rematter:block/{name}_socket", "glow": f"rematter:block/{ORE_GLOW}"}})
        out.json(ART / f"blockstates/{name}.json", {"variants": {"": {"model": f"rematter:block/{name}"}}})
        out.json(ART / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"rematter:block/{name}"}})
        silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
        out.json(DATA / f"rematter/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": "rematter:" + name, "conditions": [silk]},
            {"type": "minecraft:item", "name": "rematter:primal_shard", "conditions": [{"condition": "minecraft:survives_explosion"}]}]}]}]})
        out.json(DATA / f"rematter/worldgen/configured_feature/{name}.json", {"type": "minecraft:ore", "config": {"size": 3, "discard_chance_on_air_exposure": 0,
            "targets": [{"target": {"predicate_type": "minecraft:block_match", "block": host}, "state": {"Name": "rematter:" + name}}]}})
        out.json(DATA / f"rematter/worldgen/placed_feature/{name}.json", {"feature": "rematter:" + name, "placement": [
            ({"type": "minecraft:count", "count": 4} if dimension == "minecraft:the_end" else {"type": "minecraft:rarity_filter", "chance": 2}), {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": low}, "max_inclusive": {"absolute": high}}},
            # The largest vanilla size-3 ore vein fits within16 blocks. This margin protects every block, not only its anchor.
            {"type": "rematter:primal_dimension", "dimension": dimension, "minimum_radius": 1040 if dimension == "minecraft:the_end" else 0}, {"type": "minecraft:biome"}]})
        out.json(DATA / f"rematter/neoforge/biome_modifier/{name}.json", {"type": "neoforge:add_features", "biomes": biome, "features": ["rematter:" + name], "step": "underground_ores"})


# ---------------------------------------------------------------------------------------------------------
# Item icons
# ---------------------------------------------------------------------------------------------------------

def points(*pairs):
    return " ".join(f"{x:g},{y:g}" for x, y in pairs)


def polygon(pairs, fill, **attributes):
    extra = "".join(f' {key.replace("_", "-")}="{value}"' for key, value in attributes.items())
    return f'<polygon points="{points(*pairs)}" fill="{fill}"{extra}/>'


def line(pairs, stroke, width, **attributes):
    extra = "".join(f' {key.replace("_", "-")}="{value}"' for key, value in attributes.items())
    return (f'<polyline points="{points(*pairs)}" fill="none" stroke="{stroke}" stroke-width="{width:g}" '
            f'stroke-linecap="round" stroke-linejoin="round"{extra}/>')


def gradient(name, stops, x1=0, y1=0, x2=1, y2=1):
    body = "".join(f'<stop offset="{offset:g}" stop-color="{colour}"'
                   + (f' stop-opacity="{opacity:g}"' if opacity != 1 else "") + "/>"
                   for offset, colour, opacity in ((*stop, 1)[:3] for stop in stops))
    return f'<linearGradient id="{name}" x1="{x1:g}" y1="{y1:g}" x2="{x2:g}" y2="{y2:g}">{body}</linearGradient>'


def radial(name, stops, **attributes):
    extra = "".join(f' {key}="{value}"' for key, value in attributes.items())
    body = "".join(f'<stop offset="{offset:g}" stop-color="{colour}"'
                   + (f' stop-opacity="{opacity:g}"' if opacity != 1 else "") + "/>"
                   for offset, colour, opacity in ((*stop, 1)[:3] for stop in stops))
    return f'<radialGradient id="{name}"{extra}>{body}</radialGradient>'


def hexa(colour):
    return "#%02x%02x%02x" % tuple(colour)


# Tones of an illustrated facet: how far a spectrum colour is lifted toward white, or that it lies in shade.
FACET_TONES = {"glint": .74, "lit": .36, "body": 0, "shade": None, "dark": None}
# The spectrum laid across a drawing: the ultraviolet holds the middle, the ends stay short.
SWEEP = ((0, "teal"), (.2, "cool"), (.43, "body"), (.6, "body"), (.82, "warm"), (1, "gold"))
# A facet in shade leans this far toward the ultraviolet middle before it is darkened, so no shadow turns brown.
SHADE_LEAN = .125


def facet_tone(key, tone):
    place = SPECTRUM.index(key) / (len(SPECTRUM) - 1)
    lift = FACET_TONES[tone]
    if lift is None:
        shaded = in_shade(spectrum(place + (SHADE_LEAN if place < .5 else -SHADE_LEAN if place > .5 else 0)))
        return shaded if tone == "shade" else mix(shaded, rgb(PRIMAL["void"]), .42)
    return mix(spectrum(place), rgb(PRIMAL["core"]), lift)


def sweep(name, tone, start, end, stops=SWEEP):
    """One tone of the whole spectrum, laid from start to end in the coordinates of the drawing."""
    body = "".join(f'<stop offset="{offset:g}" stop-color="{hexa(facet_tone(key, tone))}"/>' for offset, key in stops)
    return (f'<linearGradient id="{name}" gradientUnits="userSpaceOnUse" x1="{start[0]:g}" y1="{start[1]:g}" '
            f'x2="{end[0]:g}" y2="{end[1]:g}">{body}</linearGradient>')


def svg(definitions, parts):
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">\n<defs>\n'
            + "\n".join(definitions) + "\n</defs>\n" + "\n".join(parts) + "\n</svg>\n")


def crystal_gradients(look, start=None, end=None):
    """Facet fills shared by the shard and the cut crystal, lit from the upper left.

    In the look "spectrum" every tone is the whole spectrum laid from start (cool) to end (warm), so a facet
    takes its hue from where it lies and its tone from how it faces the light.
    """
    p = PRIMAL
    core = radial("core", ((0, p["core"]), (.42, p["core"]), (.7, p["core_soft"], .85), (1, p["light"], 0)))
    if look == "aurora":
        return [
            gradient("glint", ((0, "#ffffff"), (.6, "#e7dcff"), (1, "#c9b6ff"))),
            gradient("lit", ((0, "#ddd0ff"), (.4, p["light"]), (1, "#9a72ff"))),
            gradient("body", ((0, "#a680ff"), (.5, p["body"]), (1, "#6f43e8"))),
            gradient("shade", ((0, "#6d40e2"), (.55, p["deep"]), (1, "#371a86"))),
            gradient("dark", ((0, p["deep"]), (1, "#2a1368"))),
            # The prismatic rim: cyan-blue where the light enters, magenta where it leaves.
            gradient("prism", ((0, p["cool"]), (.42, p["cool"], 0), (.58, p["warm"], 0), (1, p["warm"]))),
            core,
        ]
    return [sweep(tone, tone, start, end) for tone in FACET_TONES] + [
        # The prismatic rim: teal where the light enters, rose-gold where it leaves.
        gradient("prism", ((0, p["teal"]), (.2, p["cool"]), (.42, p["cool"], 0), (.58, p["warm"], 0),
                           (.8, p["warm"]), (1, p["gold"]))),
        core,
    ]


def rim(name, outline):
    """Dark contour below a shape and the prismatic line just inside it."""
    below = polygon(outline, PRIMAL["ink"], stroke=PRIMAL["ink"], stroke_width="10", stroke_linejoin="round")
    clip = f'<clipPath id="{name}">{polygon(outline, "#000")}</clipPath>'
    above = polygon(outline, "none", stroke="url(#prism)", stroke_width="9", stroke_linejoin="round",
                    clip_path=f"url(#{name})")
    return below, clip, above


def primal_crystal_svg(look=None):
    """The refined form: a cut, double-terminated prism with the white-hot core standing inside."""
    look = look or PRIMAL_LOOK
    apex, base = (64, 12), (66, 116)
    left_top, ridge_top, edge_top, right_top = (31, 38), (50, 47), (85, 45), (97, 36)
    left_low, ridge_low, edge_low, right_low = (31, 90), (50, 99), (85, 97), (97, 88)
    outline = (apex, right_top, right_low, base, left_low, left_top)
    below, clip, above = rim("cut", outline)
    parts = [
        '<g transform="rotate(8 64 64)">', below,
        polygon((apex, left_top, ridge_top), "url(#glint)"),
        polygon((apex, ridge_top, edge_top), "url(#lit)"),
        polygon((apex, edge_top, right_top), "url(#body)"),
        polygon((left_top, ridge_top, ridge_low, left_low), "url(#lit)"),
        polygon((ridge_top, edge_top, edge_low, ridge_low), "url(#body)"),
        polygon((edge_top, right_top, right_low, edge_low), "url(#shade)"),
        polygon((left_low, ridge_low, base), "url(#body)"),
        polygon((ridge_low, edge_low, base), "url(#shade)"),
        polygon((edge_low, right_low, base), "url(#dark)"),
        # The core: a glow in the front face and a hard white sliver in it.
        '<ellipse cx="67.5" cy="72" rx="16" ry="32" fill="url(#core)"/>',
        polygon(((67.5, 46), (73, 59), (72.5, 85), (67.5, 99), (62.5, 85), (62, 59)), PRIMAL["core"]),
        above,
        # Ridges: light where two lit faces meet, dark toward the shaded side.
        line((apex, ridge_top, ridge_low, base), "#ffffff", 1.6, opacity=".7"),
        line((apex, edge_top, edge_low, base), "#2a1368", 1.4, opacity=".55"),
        line((left_top, ridge_top), "#ffffff", 1.2, opacity=".5"),
        line((ridge_top, edge_top, right_top), "#2a1368", 1.1, opacity=".35"),
        line((left_low, ridge_low), "#2a1368", 1.1, opacity=".3"),
        line((ridge_low, edge_low, right_low), "#2a1368", 1.1, opacity=".4"),
        # Reflections on the lit side.
        polygon(((36.5, 47), (42, 49.5), (42, 80), (36.5, 77.5)), "#ffffff", opacity=".6"),
        polygon(((60, 21), (47, 40), (52.5, 42.5)), "#ffffff", opacity=".75"),
        "</g>"]
    return svg(crystal_gradients(look, (38, 14), (86, 104)) + [clip], parts)


def primal_shard_svg(look=None):
    """The raw form: a broken sliver of the ore's crystal with a splinter, the core laid open along its ridge.

    In the look "spectrum" the sliver runs from a cool tip to a warm break, and the splinter is rose-gold,
    like the small crystals of the ore.
    """
    look = look or PRIMAL_LOOK
    chip_body, chip_shade = ("body", "shade") if look == "aurora" else ("chip", "chip_shade")
    tip, shoulder, flank, heel = (103, 14), (112, 37), (91, 78), (77, 104)
    notch, spike, chip, jag = (63, 96), (53, 115), (37, 103), (24, 111)
    low, side, brow = (21, 88), (45, 52), (79, 23)
    ridge, crack = (57, 85), (39, 95)
    outline = (tip, shoulder, flank, heel, notch, spike, chip, jag, low, side, brow)
    splinter = ((90, 91), (109, 76), (115, 94), (101, 114), (85, 110))
    seam = (99, 97)
    below, clip, above = rim("raw", outline)
    below_splinter, clip_splinter, above_splinter = rim("chip", splinter)
    parts = [
        below_splinter,
        polygon((splinter[0], splinter[1], seam, splinter[4]), f"url(#{chip_body})"),
        polygon((splinter[1], splinter[2], splinter[3], splinter[4], seam), f"url(#{chip_shade})"),
        above_splinter,
        line((splinter[1], seam, splinter[4]), "#ffffff", 1.3, opacity=".6"),
        below,
        polygon((tip, brow, side, low, crack, ridge), "url(#lit)"),
        polygon((tip, shoulder, flank, heel, notch, ridge), "url(#body)"),
        polygon((shoulder, flank, heel, (88, 70)), "url(#shade)"),
        # The break: dark facets with the open core between them.
        polygon((low, jag, chip, crack), "url(#shade)"),
        polygon((crack, chip, spike, notch, ridge), "url(#dark)"),
        # The core runs along the ridge and widens where the piece broke off.
        '<ellipse cx="78" cy="55" rx="9" ry="44" transform="rotate(38 78 55)" fill="url(#core)"/>',
        polygon(((98, 24), (101, 30), (64, 84), (57, 91), (53, 86), (59, 76)), PRIMAL["core"]),
        above,
        line((tip, ridge, crack), "#ffffff", 1.5, opacity=".7"),
        line((ridge, notch), "#2a1368", 1.2, opacity=".5"),
        line((tip, (88, 70), heel), "#2a1368", 1.2, opacity=".4"),
        line((crack, chip), "#2a1368", 1.1, opacity=".4"),
        polygon(((74, 33), (79, 35), (52, 69), (48, 65)), "#ffffff", opacity=".55"),
        polygon(((40, 71), (44, 74), (33, 88), (30, 85)), "#ffffff", opacity=".4"),
    ]
    definitions = crystal_gradients(look, (104, 20), (30, 106))
    if look != "aurora":
        # The splinter is a rose-gold crystal of its own: a pale gold face to the light, rose on the far side.
        definitions += [sweep("chip", "lit", (92, 80), (100, 112), ((0, "gold"), (1, "gold"))),
                        sweep("chip_shade", "body", (108, 78), (98, 114), ((0, "gold"), (.35, "gold"), (1, "warm")))]
    return svg(definitions + [clip, clip_splinter], parts)


def discovery_lens_svg(look=None):
    """A precision optic: a Primal core behind glass in a steel bezel and a graphite mount with one lug.

    In the look "spectrum" the glass splits the light of the core: a cool fringe on the side of the light, a
    warm one on the far side, and the whole spectrum around the rim of the pane.
    """
    look = look or PRIMAL_LOOK
    p = PRIMAL
    ink = "#161b22"
    tilt = 'transform="rotate(-18 60 58)"'
    definitions = [
        gradient("graphite", ((0, "#98a2ab"), (.32, "#59626c"), (.68, "#373d45"), (1, "#2b2f35"))),
        gradient("wall", ((0, "#49515a"), (1, "#22262c"))),
        gradient("steel", ((0, "#f1f3f0"), (.45, "#9aa6ab"), (1, "#56626a"))),
        radial("glass", ((0, p["core"]), (.2, p["core_soft"]), (.46, p["light"]), (.74, p["body"]),
                         (1, p["deep"])), cx=".46", cy=".44", r=".62"),
        gradient("prism", ((0, p["cool"]), (.38, p["cool"], 0), (.62, p["warm"], 0), (1, p["warm"]))
                 if look == "aurora" else
                 ((0, p["teal"]), (.2, p["cool"]), (.4, p["cool"], 0), (.6, p["warm"], 0), (.8, p["warm"]), (1, p["gold"]))),
        '<clipPath id="pane"><ellipse cx="60" cy="58" rx="28" ry="26.5" ' + tilt + "/></clipPath>",
    ]
    core = ((58, 44), (64, 50), (64.5, 63), (60.5, 72), (55, 65), (54.5, 53))
    wide = ((58, 39), (67, 47), (68, 64), (61, 77), (52, 68), (51, 51))
    if look == "aurora":
        halo = [polygon(wide, p["core_soft"], opacity=".55")]
    else:
        # Thin fringes beside the halo: teal and cyan-blue toward the light, magenta and rose-gold away from it.
        halo = [polygon([(x - 4, y - 2.5) for x, y in wide], p["teal"]),
                polygon([(x + 4, y + 2.5) for x, y in wide], p["gold"]),
                polygon([(x - 2, y - 1.25) for x, y in wide], p["cool"]),
                polygon([(x + 2, y + 1.25) for x, y in wide], p["warm"]),
                polygon(wide, p["core_soft"])]
    clamps = "".join(
        f'<rect x="-5" y="-39.5" width="10" height="11" rx="2" transform="translate(60 58) rotate({angle})" '
        f'fill="url(#steel)" stroke="{ink}" stroke-width="2"/>' for angle in (-58, 62, 182))
    ticks = "".join(
        f'<line x1="60" y1="17" x2="60" y2="21" transform="rotate({angle} 60 58)" stroke="#c3ccce" '
        f'stroke-width="1.5" stroke-linecap="round" opacity=".75"/>' for angle in range(-128, 40, 14))
    parts = [
        # The lug that holds the optic in its instrument.
        f'<rect x="-9" y="36" width="18" height="30" rx="5" transform="translate(64 62) rotate(-45)" '
        f'fill="url(#steel)" stroke="{ink}" stroke-width="5"/>',
        f'<circle cx="104.5" cy="102.5" r="3.6" fill="#0a0d11" stroke="{ink}" stroke-width="1.2"/>',
        # The depth of the mount, seen from the upper left.
        f'<ellipse cx="67" cy="67" rx="44" ry="42" {tilt} fill="url(#wall)" stroke="{ink}" stroke-width="5"/>',
        f'<ellipse cx="60" cy="58" rx="44" ry="42" {tilt} fill="url(#graphite)" stroke="{ink}" stroke-width="5"/>',
        f'<ellipse cx="60" cy="58" rx="40.6" ry="38.6" {tilt} fill="none" stroke="#c3ccce" stroke-width="1.6" '
        'stroke-dasharray="118 400" stroke-dashoffset="-128" stroke-linecap="round" opacity=".8"/>',
        f"<g>{ticks}</g>",
        f'<ellipse cx="60" cy="58" rx="34.5" ry="32.5" {tilt} fill="url(#steel)" stroke="{ink}" stroke-width="2.6"/>',
        f'<ellipse cx="60" cy="58" rx="29.5" ry="28" {tilt} fill="#0a0d11" stroke="{ink}" stroke-width="1.6"/>',
        f'<ellipse cx="60" cy="58" rx="28" ry="26.5" {tilt} fill="url(#glass)"/>',
        '<g clip-path="url(#pane)">',
        # The Primal core behind the glass.
        *halo,
        polygon(core, p["core"]),
        f'<ellipse cx="60" cy="58" rx="26" ry="24.5" {tilt} fill="none" stroke="url(#prism)" stroke-width="5"/>',
        # The reflection of the glass.
        '<path d="M38 48 Q44 33 60 31 Q48 37 43 51 Z" fill="#ffffff" opacity=".8"/>',
        '<path d="M74 82 Q82 77 85 67 Q84 78 77 84 Z" fill="#ffffff" opacity=".35"/>',
        "</g>",
        f"<g>{clamps}</g>",
    ]
    return svg(definitions, parts)


ICON_DRAWINGS = {"primal_shard": primal_shard_svg, "primal_crystal": primal_crystal_svg,
                 "discovery_lens": discovery_lens_svg}


def digest(content):
    return hashlib.sha256(content).hexdigest()


def render(drawing, target):
    try:
        import cairosvg
    except (ImportError, OSError) as error:
        raise SystemExit("A Primal item drawing changed. Rendering it needs CairoSVG (pip install cairosvg) "
                         f"and the Cairo library: {error}") from error
    target.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring=drawing, write_to=str(target), output_width=128, output_height=128)
    strip_ancillary_chunks(target)


def write_icons(out):
    """Write the drawings, render the sprites whose drawing changed and tie both together by checksum."""
    record_path = DRAWINGS / "rendered.json"
    recorded = json.loads(record_path.read_text()) if record_path.is_file() else {}
    record = {}
    for name in ICONS:
        drawing = ICON_DRAWINGS[name]().encode()
        sprite = ART / f"textures/item/{name}.png"
        out.bytes(DRAWINGS / f"{name}.svg", drawing)
        current = recorded.get(name, {}).get("drawing") == digest(drawing) and sprite.is_file() \
            and recorded[name].get("sprite") == digest(sprite.read_bytes())
        if not current:
            if out.check:
                raise ValueError(f"Generated file differs: {sprite.relative_to(ROOT)} was not rendered from its drawing")
            render(drawing, sprite)
        data = sprite.read_bytes()
        if struct.unpack(">IIBB", data[16:26]) != (128, 128, 8, 6):
            raise ValueError(f"{sprite.relative_to(ROOT)} is not a 128-pixel RGBA sprite")
        out.count += 1
        record[name] = {"drawing": digest(drawing), "sprite": digest(data)}
        out.json(ART / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"rematter:item/{name}"}})
        out.json(ART / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"rematter:item/{name}"}})
    out.json(record_path, record)


# ---------------------------------------------------------------------------------------------------------
# Entangled Link, recipes and tags
# ---------------------------------------------------------------------------------------------------------

def link_sprites():
    """Neutral family casing and a separate animated Primal lens, both at two texels per pixel."""
    shell = casing()
    shell.row(5, 2, 30, SHADE)
    shell.row(26, 2, 30, HIGHLIGHT)
    for x in (4, 26):
        shell.rect(x, 3, x + 2, 5, PORT_LIGHT)
        shell.rect(x, 27, x + 2, 29, PORT_DARK)
    lens = Sprite(32, 32 * ORE_FRAMES, fill=(0, 0, 0, 0))
    for frame in range(ORE_FRAMES):
        for y in range(8, 24):
            for x in range(8, 24):
                distance = (x - 15.5) ** 2 + (y - 15.5) ** 2
                colour = WELL if distance > 56 else PORT_METAL if distance > 42 else PRIMAL_VOID
                if distance <= 30:
                    colour = (PRIMAL_CORE if distance < 4 else PRIMAL_CORE_SOFT if distance < 9
                              else PRIMAL_LIGHT if x + y < 29 else PRIMAL_BODY if x + y < 36 else PRIMAL_DEEP)
                    if 14 <= distance <= 30 and (x + y - frame) % ORE_FRAMES < 3:
                        colour = rgb(PRIMAL[SPECTRUM[round((frame / 7 if frame < 8 else (15 - frame) / 7) * 4)]])
                lens.px(x, y + frame * 32, colour)
    return shell, lens


def write_devices(out):
    shell, lens = link_sprites()
    out.png(ART / "textures/block/entangled_link_casing.png", shell.width, shell.height, shell.pixel)
    out.png(ART / "textures/block/entangled_link.png", lens.width, lens.height, lens.pixel)
    out.json(ART / "textures/block/entangled_link.png.mcmeta",
             {"animation": {"frametime": ORE_FRAME_TICKS}})
    boxes = [Box("lower rim", [0, 0, 0], [16, 3, 16]),
             Box("body", [1, 3, 1], [15, 13, 15]),
             Box("upper rim", [0, 13, 0], [16, 16, 16])]
    check_boxes("entangled_link", boxes)
    elements = build(boxes, lambda box, face: "shell")
    # The four half-pixel lens steps stand in front of the casing, never coplanar with it.
    for face, lo, hi in (("north", [4, 4, .5], [12, 12, 1]),
                         ("south", [4, 4, 15], [12, 12, 15.5]),
                         ("west", [.5, 4, 4], [1, 12, 12]),
                         ("east", [15, 4, 4], [15.5, 12, 12])):
        elements.append({"name": face + " lens", "from": lo, "to": hi, "shade": False,
                         "light_emission": 15, "faces": {face: {"uv": [4, 4, 12, 12], "texture": "#eye"}}})
    out.json(ART / "models/block/entangled_link.json", {"parent": "minecraft:block/block",
             "textures": {"particle": "rematter:block/entangled_link_casing",
                          "shell": "rematter:block/entangled_link_casing", "eye": "rematter:block/entangled_link"},
             "elements": elements})
    out.json(ART / "blockstates/entangled_link.json", {"variants": {"": {"model": "rematter:block/entangled_link"}}})
    out.json(ART / "items/entangled_link.json", {"model": {"type": "minecraft:model", "model": "rematter:block/entangled_link"}})
    out.json(DATA / "rematter/loot_table/blocks/entangled_link.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
        "entries": [{"type": "minecraft:item", "name": "rematter:entangled_link"}]}]})
    for name, keys, pattern in (
        ("primal_cell_16k", {"H": "rematter:cell_housing", "C": "rematter:control_circuit", "V": "rematter:violet_matrix_crystal"}, [" C ", " V ", " H "]),
        ("primal_cell_64k", {"H": "rematter:cell_housing", "C": "rematter:optical_circuit", "V": "rematter:violet_matrix_crystal", "E": "rematter:ender_conductor"}, [" C ", "EVE", " H "]),
        ("primal_cell_256k", {"H": "rematter:cell_housing", "C": "rematter:primal_crystal", "D": "rematter:diamond_lattice", "N": "rematter:netherite_sheath", "E": "rematter:ender_conductor"}, ["CDC", "EHE", "CNC"]),
        ("entangled_link", {"C": "rematter:primal_crystal", "D": "rematter:diamond_lattice", "N": "rematter:netherite_sheath", "E": "rematter:ender_conductor", "F": "rematter:machine_frame"}, ["CEC", "DFD", "CNC"])):
        out.json(DATA / f"rematter/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": keys, "result": {"id": "rematter:" + name, "count": 2 if name == "entangled_link" else 1}})
        out.json(DATA / f"rematter/advancement/recipes/{name}.json", shaped_discovery(name, pattern, keys))
    sources = ["rematter:" + name for name in ORES] + ["rematter:primal_shard"]
    out.json(DATA / "rematter/tags/item/primal_sources.json", {"replace": False, "values": sources})
    out.json(DATA / "rematter/tags/block/primal_ores.json", {"replace": False, "values": ["rematter:" + name for name in ORES]})
    out.json(DATA / "minecraft/tags/block/needs_iron_tool.json", {"replace": False, "values": ["#rematter:primal_ores"]})


def retire(out):
    for name in RETIRED:
        path = ART / name
        if path.exists():
            if out.check:
                raise ValueError(f"Retired file still exists: {path.relative_to(ROOT)}")
            path.unlink()


def generate(check=False):
    out = GeneratedFiles(ROOT, check)
    write_ores(out)
    write_icons(out)
    write_devices(out)
    retire(out)
    print(f"{'Checked' if check else 'Generated'} {out.count} Primal files")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    generate(parser.parse_args().check)
