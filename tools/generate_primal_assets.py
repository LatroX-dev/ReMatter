#!/usr/bin/env python3
"""Generate the Primal Ore, Primal item and Entangled Link assets with their loot, recipes and world generation.

Primal Matter has its own colour signature, apart from the eight Matrix Crystal hues: a white-hot core, an
ultraviolet body and a shimmer that travels between cyan-blue and magenta. PRIMAL holds these colours.

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

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ART = RES / "assets/rematter"
DATA = RES / "data"
DRAWINGS = ROOT / "artwork/primal"
FACES = ("north", "east", "south", "west", "up", "down")

# The Primal signature. None of these is one of the eight Matrix Crystal hues.
PRIMAL = {
    "core": "#ffffff",       # white-hot centre
    "core_soft": "#efe6ff",  # its halo
    "light": "#ae8eff",      # facet turned to the light
    "body": "#8a5cff",       # ultraviolet
    "deep": "#4c24b2",       # facet in shade
    "void": "#1c0e4a",       # inside of a hollow
    "cool": "#5cc8ff",       # the shimmer travels between this cyan-blue
    "warm": "#ff6ad5",       # and this magenta
    "ink": "#1e1546",        # contour of the item icons
}

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

VIOLET, WHITE = (153, 113, 208, 255), (235, 225, 255, 255)


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


def ore_layers(design=None):
    """Crystal texels with their role and the socket texels of a design."""
    return _ore_layers(design or ORE_DESIGN)


@functools.lru_cache(maxsize=None)
def _ore_layers(design):
    rows = ORE_DESIGNS[design]["rows"]
    if len(rows) != 16 or any(len(row) != 16 for row in rows):
        raise ValueError("An ore design is 16 rows of 16 texels")
    glow = {(x, y): role for y, row in enumerate(rows) for x, role in enumerate(row) if role in "wclbdve"}
    socket = {(x, y) for y, row in enumerate(rows) for x, role in enumerate(row) if role == "k"}
    unknown = {role for row in rows for role in row} - set("wclbdvek.")
    if unknown:
        raise ValueError(f"Unknown texel role in an ore design: {sorted(unknown)}")
    for x, y in glow:
        socket.update((x + dx, y + dy) for dx, dy in NEIGHBOURS
                      if 0 <= x + dx < 16 and 0 <= y + dy < 16 and (x + dx, y + dy) not in glow)
    return glow, socket


def glow_colour(design, x, y, frame):
    """Colour of one crystal texel in one frame, or None for a texel without crystal.

    A soft crest of light crosses the face diagonally once per loop. Its phase continues across the edge of the
    texture, so the light runs on through neighbouring ore blocks. Prismatic edges turn from cyan-blue to
    magenta and back as it passes, and each sparkle tip flashes once, half a loop away from the crest.
    """
    glow, _ = ore_layers(design)
    role = glow.get((x, y))
    if role is None:
        return None
    phase = ((x + y) / 16 - frame / ORE_FRAMES) % 1
    crest = max(0.0, (math.cos(2 * math.pi * phase) - .4) / .6) ** 2
    if role == "e":
        colour = mix(rgb(PRIMAL["cool"]), rgb(PRIMAL["warm"]), .5 - .5 * math.cos(2 * math.pi * (phase + .25)))
    else:
        colour = rgb(PRIMAL[ROLE_COLOURS[role]])
    colour = mix(colour, rgb(PRIMAL["core"]), (.18 if role == "v" else .45) * crest)
    flash = 0.0
    for tip_x, tip_y in ORE_DESIGNS[design or ORE_DESIGN]["sparkles"]:
        distance = abs(x - tip_x) + abs(y - tip_y)
        if distance <= 1:
            away = (frame - (tip_x + tip_y + ORE_FRAMES // 2)) % ORE_FRAMES
            strength = {0: 1.0, 1: .4, ORE_FRAMES - 1: .4}.get(away, 0.0)
            flash = max(flash, strength * (1.0 if distance == 0 else .2 if role == "v" else .6))
    return mix(colour, rgb(PRIMAL["core"]), flash)


def glow_strip(design=None):
    """Pixel function of the animated crystal layer: the frames below each other, alpha only 0 or 255."""
    def pixel(x, y):
        colour = glow_colour(design, x, y % 16, y // 16)
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


def svg(definitions, parts):
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">\n<defs>\n'
            + "\n".join(definitions) + "\n</defs>\n" + "\n".join(parts) + "\n</svg>\n")


def crystal_gradients():
    """Facet fills shared by the shard and the cut crystal, lit from the upper left."""
    p = PRIMAL
    return [
        gradient("glint", ((0, "#ffffff"), (.6, "#e7dcff"), (1, "#c9b6ff"))),
        gradient("lit", ((0, "#ddd0ff"), (.4, p["light"]), (1, "#9a72ff"))),
        gradient("body", ((0, "#a680ff"), (.5, p["body"]), (1, "#6f43e8"))),
        gradient("shade", ((0, "#6d40e2"), (.55, p["deep"]), (1, "#371a86"))),
        gradient("dark", ((0, p["deep"]), (1, "#2a1368"))),
        # The prismatic rim: cyan-blue where the light enters, magenta where it leaves.
        gradient("prism", ((0, p["cool"]), (.42, p["cool"], 0), (.58, p["warm"], 0), (1, p["warm"]))),
        radial("core", ((0, p["core"]), (.42, p["core"]), (.7, p["core_soft"], .85), (1, p["light"], 0))),
    ]


def rim(name, outline):
    """Dark contour below a shape and the prismatic line just inside it."""
    below = polygon(outline, PRIMAL["ink"], stroke=PRIMAL["ink"], stroke_width="10", stroke_linejoin="round")
    clip = f'<clipPath id="{name}">{polygon(outline, "#000")}</clipPath>'
    above = polygon(outline, "none", stroke="url(#prism)", stroke_width="9", stroke_linejoin="round",
                    clip_path=f"url(#{name})")
    return below, clip, above


def primal_crystal_svg():
    """The refined form: a cut, double-terminated prism with the white-hot core standing inside."""
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
    return svg(crystal_gradients() + [clip], parts)


def primal_shard_svg():
    """The raw form: a broken sliver of the ore's crystal with a splinter, the core laid open along its ridge."""
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
        polygon((splinter[0], splinter[1], seam, splinter[4]), "url(#body)"),
        polygon((splinter[1], splinter[2], splinter[3], splinter[4], seam), "url(#shade)"),
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
    return svg(crystal_gradients() + [clip, clip_splinter], parts)


def discovery_lens_svg():
    """A precision optic: a Primal core behind glass in a steel bezel and a graphite mount with one lug."""
    p = PRIMAL
    ink = "#161b22"
    tilt = 'transform="rotate(-18 60 58)"'
    definitions = [
        gradient("graphite", ((0, "#98a2ab"), (.32, "#59626c"), (.68, "#373d45"), (1, "#2b2f35"))),
        gradient("wall", ((0, "#49515a"), (1, "#22262c"))),
        gradient("steel", ((0, "#f1f3f0"), (.45, "#9aa6ab"), (1, "#56626a"))),
        radial("glass", ((0, p["core"]), (.2, p["core_soft"]), (.46, p["light"]), (.74, p["body"]),
                         (1, p["deep"])), cx=".46", cy=".44", r=".62"),
        gradient("prism", ((0, p["cool"]), (.38, p["cool"], 0), (.62, p["warm"], 0), (1, p["warm"]))),
        '<clipPath id="pane"><ellipse cx="60" cy="58" rx="28" ry="26.5" ' + tilt + "/></clipPath>",
    ]
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
        polygon(((58, 39), (67, 47), (68, 64), (61, 77), (52, 68), (51, 51)), p["core_soft"], opacity=".55"),
        polygon(((58, 44), (64, 50), (64.5, 63), (60.5, 72), (55, 65), (54.5, 53)), p["core"]),
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

def cuboid(low, high, texture, faces=FACES):
    return {"from": low, "to": high, "faces": {face: {"uv": [0, 0, 16, 16], "texture": "#" + texture} for face in faces}}


def write_devices(out):
    out.png(ART / "textures/block/entangled_link.png", 16, 16,
            lambda x, y: WHITE if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 5 else VIOLET if 13 <= (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 27 else (43 + noise(x, y), 46 + noise(x, y), 54 + noise(x, y), 255))
    out.json(ART / "models/block/entangled_link.json", {"ambientocclusion": False, "textures": {"particle": "rematter:block/graphite", "shell": "rematter:block/graphite",
        "edge": "rematter:block/socket_rim", "eye": "rematter:block/entangled_link"}, "elements": [
        cuboid([0, 0, 0], [16, 3, 16], "edge"), cuboid([0, 13, 0], [16, 16, 16], "edge"),
        cuboid([1, 3, 1], [15, 13, 15], "shell"), cuboid([3, 4, .95], [13, 12, 15.05], "eye", ("north", "south")),
        cuboid([.9, 4, 3], [15.1, 12, 13], "eye", ("east", "west"))]})
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
