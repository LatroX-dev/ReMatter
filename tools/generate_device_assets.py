#!/usr/bin/env python3
"""Generate upgrade, handheld tool and wireless network models, sprites, recipes and item tags."""
from pathlib import Path
import argparse
from family_geometry import Box, build, check_boxes, mapped_uv
from family_palette import (BADGE, BADGE_LIGHT, BASE, CABLE_TIERS, COPPER, COPPER_LIGHT, HIGHLIGHT, IVORY, JACKET, LED,
                            LED_RED_DIM, PORT_DARK, PORT_ENERGY_INPUT, PORT_ENERGY_OUTPUT, PORT_LIGHT, PORT_METAL,
                            PRIMAL_BODY, PRIMAL_COOL, PRIMAL_CORE,
                            PRIMAL_CORE_SOFT, PRIMAL_DEEP, PRIMAL_GOLD, PRIMAL_LIGHT, PRIMAL_TEAL, PRIMAL_VOID,
                            PRIMAL_WARM, SEAM, SHADE, STANDBY, STEEL, STEEL_DEEP, STEEL_SHADE, TRIM, WELL,
                            lighten, scale)
from family_sprites import Sprite, bolt
from work_sprites import strip
from generate_family_assets import Family, ROTATION
from generated_files import GeneratedFiles
from machine_palette import RGB
import port_plates
from recipe_discovery import shaped_discovery

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/rematter"
DATA = ROOT / "src/main/resources/data/rematter"
COMMON_TAGS = ROOT / "src/main/resources/data/c/tags/item"
ACCESS_POINT_LOOKS = ("beacon_mast", "dual_array", "dome_transceiver")
ACCESS_POINT_LOOK = "beacon_mast"
RELAY_LOOKS = ("inline_gate", "valve_drum", "micro_switch")
RELAY_LOOK = "inline_gate"


def recipe(out, name, pattern, keys, *, category="misc", unlock=None, count=1):
    out.json(DATA / "recipe" / (name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
        "key": keys, "result": {"id": "rematter:" + name, "count": count}})
    if unlock is None:
        out.json(DATA / "advancement/recipes" / (name + ".json"), shaped_discovery(name, pattern, keys))
    else:
        out.json(DATA / "advancement/recipes" / (name + ".json"), {
            "criteria": {"has_material": {"trigger": "minecraft:inventory_changed", "conditions": {
                "items": [{"items": unlock}]}}},
            "requirements": [["has_material"]], "rewards": {"recipes": ["rematter:" + name]}})


def access_point_sprites():
    """Freestanding sculpted transceiver tower: bottom-only connection, 4 steel stabilizer fins, animated Blue beacon."""
    blue = RGB["blue"]
    blue_hi = lighten(blue, .42)
    blue_mid = lighten(blue, .20)
    blue_lo = scale(blue, .58)
    blue_dim = scale(blue, .32)

    # Front/side tower housing sprite (32x32, 2 texels per model pixel)
    front = Sprite(fill=BASE)
    front.ring(0, 0, 32, 32, SEAM)
    front.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    # Plinth (y=0..2.5 -> v=27..32) with Copper maker's badge & intake slots
    front.rect(4, 27, 28, 32, SHADE)
    front.row(27, 4, 28, HIGHLIGHT)
    front.rect(11, 28, 21, 31, BADGE)
    front.row(28, 11, 21, BADGE_LIGHT)
    for vx in (6, 24):
        front.rect(vx, 28, vx + 2, 31, WELL)
    # Central transceiver spine (x=4.5..11.5 -> u=9..23, y=2.5..8.5 -> v=15..27)
    front.rect(9, 15, 23, 27, PORT_METAL)
    front.bevel(9, 15, 23, 27, PORT_LIGHT, PORT_DARK)
    front.rect(11, 17, 21, 25, WELL)
    front.rect(12, 18, 20, 21, blue)
    front.row(18, 12, 20, blue_hi)
    front.rect(12, 22, 17, 24, IVORY)
    # Upper emitter collar (y=8.5..10 -> v=12..15) & slim steel mast (y=10..12 -> v=8..12)
    front.rect(10, 12, 22, 15, STEEL_SHADE)
    front.bevel(10, 12, 22, 15, TRIM, STEEL_DEEP)
    front.rect(13, 13, 19, 14, blue)
    front.rect(13, 8, 19, 12, STEEL)
    front.col(13, 8, 12, TRIM)
    front.col(18, 8, 12, STEEL_DEEP)

    side = Sprite(fill=BASE)
    side.ring(0, 0, 32, 32, SEAM)
    side.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    side.rect(4, 27, 28, 32, SHADE)
    side.row(27, 4, 28, HIGHLIGHT)
    side.rect(9, 15, 23, 27, PORT_METAL)
    side.bevel(9, 15, 23, 27, PORT_LIGHT, PORT_DARK)
    # Steel stabilizer fins (u=6..9 & 23..26, v=16..27) + recessed Blue side waveguide
    for u0, u1 in ((6, 9), (23, 26)):
        side.rect(u0, 16, u1, 27, STEEL_SHADE)
        side.bevel(u0, 16, u1, 27, TRIM, STEEL_DEEP)
    side.rect(14, 17, 18, 25, WELL)
    side.rect(15, 18, 17, 24, blue)
    side.rect(10, 12, 22, 15, STEEL_SHADE)
    side.bevel(10, 12, 22, 15, TRIM, STEEL_DEEP)
    side.rect(13, 8, 19, 12, STEEL)
    side.col(13, 8, 12, TRIM)

    top = Sprite(fill=BASE)
    top.ring(4, 4, 28, 28, SEAM)
    top.bevel(5, 5, 27, 27, HIGHLIGHT, SHADE)
    top.rect(13, 5, 19, 7, IVORY)
    top.rect(10, 10, 22, 22, STEEL_SHADE)
    top.bevel(10, 10, 22, 22, TRIM, STEEL_DEEP)
    top.rect(12, 12, 20, 20, WELL)
    top.rect(14, 14, 18, 18, blue)

    # Idle transceiver spire & front link window (static 32x32)
    idle = Sprite(fill=STEEL_DEEP)
    idle.ring(0, 0, 32, 32, SEAM)
    idle.rect(10, 1, 22, 10, blue_dim)
    idle.rect(13, 3, 19, 8, blue_lo)
    idle.rect(11, 18, 21, 24, WELL)
    idle.rect(12, 19, 20, 23, blue_dim)
    idle.rect(14, 20, 18, 22, blue_lo)

    # Active 16-frame animated glowing Blue transceiver beacon & pulsing antenna ring!
    def active_frame(f):
        s = Sprite(fill=blue_lo)
        s.ring(0, 0, 32, 32, SEAM)
        # Rising energy wave across spire & collar (v=1..10, u=10..22)
        wave = 9 - ((f * 9) // 16)
        for y in range(1, 10):
            dist = min(abs(y - wave), 9 - abs(y - wave))
            col = blue_hi if dist == 0 else blue_mid if dist == 1 else blue
            s.row(y, 10, 22, col)
        s.col(15, 1, 10, blue_hi)
        s.col(16, 1, 10, blue_hi)
        # Top cap center (u=10..22, v=10..22)
        s.rect(10, 10, 22, 22, blue)
        s.ring(11, 11, 21, 21, blue_mid if f % 8 < 4 else blue_hi)
        s.rect(13, 13, 19, 19, blue_hi if f % 4 < 2 else blue_mid)
        s.rect(15, 15, 17, 17, IVORY)
        # Front pulse bar window (v=18..24)
        s.rect(11, 18, 21, 24, WELL)
        s.rect(12, 19, 20, 23, blue)
        px = 12 + ((f * 8) // 16)
        s.rect(px, 19, min(20, px + 2), 23, blue_hi)
        return s

    active = strip(active_frame(f) for f in range(16))

    return (
        {
            "wireless_access_point_front": front,
            "wireless_access_point_side": side,
            "wireless_access_point_top": top,
            "wireless_access_point_beacon_idle": idle,
            "wireless_access_point_beacon_active": active,
        },
        {"wireless_access_point_beacon_active": {"animation": {"frametime": 3, "interpolate": True}}},
    )


def access_point_models(look=ACCESS_POINT_LOOK):
    if look not in ACCESS_POINT_LOOKS:
        raise ValueError(f"Unknown Wireless Access Point look: {look}")
    family = Family()
    # Bottom-only connection: freestanding sculpted transceiver tower with 4 stabilizer fins (no bulky rear port!).
    body = [
        Box("plinth", (2, 0, 2), (14, 2.5, 14)),
        Box("spine", (4.5, 2.5, 4.5), (11.5, 8.5, 11.5)),
        Box("fin_west", (3, 2.5, 7), (4.5, 8, 9)),
        Box("fin_east", (11.5, 2.5, 7), (13, 8, 9)),
        Box("fin_south", (7, 2.5, 11.5), (9, 8, 13)),
        Box("collar", (5, 8.5, 5), (11, 10, 11)),
        Box("mast", (6.5, 10, 6.5), (9.5, 11.5, 9.5)),
    ]
    parts = [
        Box("beacon_ring", (5, 11.5, 5), (11, 13.5, 11), flat=True),
        Box("beacon_tip", (6.5, 13.5, 6.5), (9.5, 15.5, 9.5), flat=True),
    ]

    def body_skin(box, face):
        if box.name == "plinth" and face == "down":
            return "port"
        if face == "up":
            return "top"
        if face == "north":
            return "front"
        return "side"

    textures = {
        "front": "wireless_access_point_front",
        "side": "wireless_access_point_side",
        "top": "wireless_access_point_top",
        "port": "casing_port",
    }
    drawn = family.model("wireless_access_point", "network", textures, body, body_skin,
                         context=body + parts, particle="wireless_access_point_side")
    part_names = []
    for state, emissive in (("idle", False), ("active", True)):
        lit = [Box(box.name, box.lo, box.hi, emissive=emissive, step=box.step, flat=True) for box in parts]
        part_name = f"wireless_access_point_{state}_parts"
        shown = family.model(part_name, "part", {"beacon": f"wireless_access_point_beacon_{state}"},
                             lit, lambda box, face: "beacon", context=body + lit)
        family.compose(f"wireless_access_point {state}", drawn + shown)
        part_names.append(part_name)
    family.machine("wireless_access_point", part_names)
    sprites, mcmeta = access_point_sprites()
    return family, sprites, mcmeta


def level_emitter_sprites():
    mint = RGB["mint"]
    red = LED["red"]
    # Front sensor housing sprite (32x32, 2 texels per model pixel).
    front = Sprite(fill=BASE)
    front.ring(0, 0, 32, 32, SEAM)
    front.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    # Plinth (y_model = 0..3 -> v = 26..32) with Copper maker's badge.
    front.rect(4, 26, 28, 32, SHADE)
    front.row(26, 4, 28, HIGHLIGHT)
    front.rect(11, 28, 21, 31, BADGE)
    front.row(28, 11, 21, BADGE_LIGHT)
    # Housing front (x = 4..12 -> u = 8..24, y = 3..11 -> v = 10..26) & collar (x = 5..11 -> u = 10..22, y = 5..11 -> v = 10..22).
    front.rect(8, 10, 24, 26, BASE)
    front.bevel(8, 10, 24, 26, HIGHLIGHT, SEAM)
    front.rect(10, 23, 22, 25, mint)
    front.rect(10, 10, 22, 22, STEEL)
    front.bevel(10, 10, 22, 22, TRIM, STEEL_DEEP)

    side = Sprite(fill=BASE)
    side.ring(0, 0, 32, 32, SEAM)
    side.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    side.rect(4, 26, 28, 32, SHADE)
    side.row(26, 4, 28, HIGHLIGHT)
    # Side housing (z = 4..11 -> u = 8..22, y = 3..11 -> v = 10..26) with recessed Mint stock-sensor bar.
    side.rect(8, 10, 24, 26, PORT_METAL)
    side.bevel(8, 10, 24, 26, PORT_LIGHT, SEAM)
    side.rect(10, 14, 20, 20, WELL)
    side.rect(11, 15, 19, 18, mint)
    side.rect(11, 18, 16, 19, IVORY)
    for bx in (10, 20):
        bolt(side, bx, 22)

    top = Sprite(fill=BASE)
    top.ring(4, 4, 28, 28, SEAM)
    top.bevel(5, 5, 27, 27, HIGHLIGHT, SHADE)
    # Front directional arrow/pointer toward north (z = 2.5..4 -> v = 5..8) + recessed Mint Matrix Crystal window.
    top.rect(13, 5, 19, 7, IVORY)
    top.ring(9, 9, 23, 21, SEAM)
    top.rect(10, 10, 22, 20, WELL)
    top.rect(11, 11, 21, 15, lighten(mint, .32))
    top.rect(11, 15, 21, 19, mint)

    idle = Sprite(fill=STEEL_DEEP)
    idle.ring(11, 11, 21, 21, SEAM)
    idle.rect(12, 12, 20, 20, LED_RED_DIM)

    active = Sprite(fill=red)
    active.bevel(11, 11, 21, 21, lighten(red, .38), LED_RED_DIM)
    active.rect(13, 13, 19, 19, lighten(red, .46))

    return {
        "matter_level_emitter_front": front,
        "matter_level_emitter_side": side,
        "matter_level_emitter_top": top,
        "matter_level_emitter_tip_idle": idle,
        "matter_level_emitter_tip_active": active,
    }


def level_emitter_models():
    family = Family()
    body = [
        Box("plinth", (2, 0, 2), (14, 3, 14)),
        Box("rear_port", (5, 5, 11), (11, 11, 16)),
        Box("housing", (4, 3, 4), (12, 11, 11)),
        Box("collar", (5, 5, 2.5), (11, 11, 4)),
    ]
    parts = [Box("emitter", (5.5, 5.5, 0.5), (10.5, 10.5, 2.5), flat=True)]

    def body_skin(box, face):
        if face == "down" or (box.name == "rear_port" and face == "south"):
            return "port"
        if face == "up":
            return "top"
        if face == "north":
            return "front"
        return "side"

    textures = {
        "front": "matter_level_emitter_front",
        "side": "matter_level_emitter_side",
        "top": "matter_level_emitter_top",
        "port": "casing_port",
    }
    drawn = family.model("matter_level_emitter", "network", textures, body, body_skin,
                         context=body + parts, particle="matter_level_emitter_side")
    part_names = []
    for state, emissive in (("idle", False), ("active", True)):
        lit = [Box(box.name, box.lo, box.hi, emissive=emissive, flat=True) for box in parts]
        part_name = f"matter_level_emitter_{state}_parts"
        shown = family.model(part_name, "part", {"tip": f"matter_level_emitter_tip_{state}"},
                             lit, lambda box, face: "tip", context=body + lit)
        family.compose(f"matter_level_emitter {state}", drawn + shown)
        part_names.append(part_name)
    family.machine("matter_level_emitter", part_names)
    return family, level_emitter_sprites()


def _relay_mapped_longitudinal(box, face, key):
    """Map u along +z (u=0 -> output/north z=0, u=32 -> input/south z=16), v centered at 16."""
    if face in ("north", "south"):
        u_axis = "-x" if face == "north" else "+x"
        v_axis = "-y"
        origin = (int(round((16 - box.hi[0] if face == "north" else box.lo[0]) * 2)),
                  int(round((16 - box.hi[1]) * 2)))
        return (key, *mapped_uv(face, box.lo, box.hi, u_axis, v_axis, origin))
    across = {"east": "-y", "west": "-y", "up": "+x", "down": "+x"}[face]
    v_origin = int(round((16 - box.hi[1]) * 2)) if face in ("east", "west") else int(round(box.lo[0] * 2))
    u_origin = int(round(box.lo[2] * 2))
    return (key, *mapped_uv(face, box.lo, box.hi, "+z", across, (u_origin, v_origin)))


def _relay_end_plate(inner_col, hi_col, ring_col=None):
    end = Sprite(fill=BASE)
    end.ring(0, 0, 32, 32, SEAM)
    end.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    end.rect(7, 7, 25, 25, BASE)
    end.ring(7, 7, 25, 25, SEAM)
    end.bevel(8, 8, 24, 24, HIGHLIGHT, SHADE)
    for bx, by in ((9, 9), (21, 9), (9, 21), (21, 21)):
        bolt(end, bx, by)
    end.rect(10, 10, 22, 22, PORT_METAL)
    end.ring(10, 10, 22, 22, SEAM)
    end.bevel(11, 11, 21, 21, TRIM, PORT_DARK)
    if ring_col is not None:
        end.ring(11, 11, 21, 21, ring_col)
    end.rect(12, 12, 20, 20, WELL)
    end.rect(14, 14, 18, 18, inner_col)
    end.rect(15, 15, 17, 17, hi_col)
    return end


def _relay_side_sprite(oneway):
    blue = RGB["blue"]
    green = PORT_ENERGY_INPUT
    red = PORT_ENERGY_OUTPUT
    side = Sprite(fill=BASE)
    side.ring(0, 0, 32, 32, SEAM)
    for u0, u1, is_out in ((0, 4, True), (28, 32, False)):
        side.rect(u0, 10, u1, 22, PORT_METAL)
        side.ring(u0, 10, u1, 22, SEAM)
        side.bevel(u0 + 1, 11, u1 - 1, 21, PORT_LIGHT, PORT_DARK)
        if oneway:
            col = red if is_out else green
            cx = u0 + 1 if is_out else u1 - 2
            side.rect(cx, 12, cx + 1, 20, col)
    for u0, u1, is_out in ((4, 7, True), (25, 28, False)):
        side.rect(u0, 8, u1, 24, BASE)
        side.ring(u0, 8, u1, 24, SEAM)
        side.bevel(u0 + 1, 9, u1 - 1, 23, HIGHLIGHT, SHADE)
        if oneway:
            col = red if is_out else green
            side.rect(u0 + 1, 10, u0 + 2, 12, col)
            side.rect(u0 + 1, 20, u0 + 2, 22, col)
        else:
            side.px(u0 + 1, 10, blue)
            side.px(u0 + 1, 21, blue)
    for v0 in (8, 22):
        side.rect(7, v0, 25, v0 + 2, STEEL_SHADE)
        side.row(v0, 7, 25, TRIM if v0 == 8 else STEEL)
        side.row(v0 + 1, 7, 25, STEEL_DEEP)
        for nx in (9, 15, 16, 22):
            side.px(nx, v0 + 1, SEAM)
    return side


def _relay_disp_frame(oneway, open_state, frame=None):
    blue = RGB["blue"]
    blue_hi = lighten(blue, .34)
    blue_bright = lighten(blue, .58)
    blue_dim = scale(blue, .38)
    green = PORT_ENERGY_INPUT
    green_hi = lighten(green, .36)
    green_dim = scale(green, .38)
    red = PORT_ENERGY_OUTPUT
    red_hi = lighten(red, .36)
    red_dim = scale(red, .38)

    disp = Sprite(fill=WELL)
    disp.ring(7, 10, 25, 22, SEAM)
    disp.rect(8, 11, 24, 21, WELL)
    disp.row(11, 8, 24, JACKET)
    disp.row(20, 8, 24, JACKET)

    if not open_state:
        disp.rect(14, 11, 18, 21, red_dim)
        disp.rect(15, 11, 17, 21, red)
        disp.rect(15, 13, 17, 19, red_hi)
        if oneway:
            disp.rect(19, 15, 23, 17, green_dim)
            disp.rect(9, 15, 13, 17, red_dim)
            disp.rect(10, 14, 12, 18, red_dim)
            disp.rect(11, 13, 12, 19, red_dim)
        else:
            disp.rect(9, 15, 13, 17, blue_dim)
            disp.rect(19, 15, 23, 17, blue_dim)
        return disp

    if oneway:
        wave_u = None if frame is None else (23 - frame)

        def pulse_boost(u, base_col, hi_col):
            if wave_u is None:
                return base_col
            d = (u - wave_u) % 16
            d = min(d, 16 - d)
            if d == 0:
                return IVORY
            if d == 1:
                return hi_col
            return base_col

        for u in range(15, 23):
            disp.px(u, 15, pulse_boost(u, green_hi, IVORY))
            disp.px(u, 16, pulse_boost(u, green, green_hi))
        disp.px(14, 15, pulse_boost(14, IVORY, IVORY))
        disp.px(14, 16, pulse_boost(14, green_hi, IVORY))
        head_cols = (
            (13, 12, 20, red, red_hi),
            (12, 12, 20, red_hi, IVORY),
            (11, 13, 19, red_hi, IVORY),
            (10, 14, 18, red_hi, IVORY),
            (9, 15, 17, red_hi, IVORY),
            (8, 15, 17, red, red_hi),
        )
        for u, v0, v1, base_c, hi_c in head_cols:
            for v in range(v0, v1):
                c0 = IVORY if (frame is None and v in (15, 16) and u in (10, 11, 12)) else base_c
                disp.px(u, v, pulse_boost(u, c0, hi_c))
        return disp

    out_dist = None if frame is None else (frame % 8)

    def bidi_col(u, base_c, hi_c):
        if out_dist is None:
            return base_c
        dc = (15 - u) if u <= 15 else (u - 16)
        delta = (dc - out_dist) % 8
        delta = min(delta, 8 - delta)
        if delta == 0:
            return IVORY
        if delta == 1:
            return hi_c
        return base_c

    for u in range(12, 20):
        disp.px(u, 15, bidi_col(u, blue_hi, blue_bright))
        disp.px(u, 16, bidi_col(u, blue, blue_hi))
    for u, v0, v1, base_c, hi_c in ((9, 15, 17, blue_hi, IVORY), (10, 14, 18, blue_hi, blue_bright), (11, 13, 19, blue, blue_hi)):
        for v in range(v0, v1):
            disp.px(u, v, bidi_col(u, blue_hi if v in (15, 16) else base_c, hi_c))
    for u, v0, v1, base_c, hi_c in ((20, 13, 19, blue, blue_hi), (21, 14, 18, blue_hi, blue_bright), (22, 15, 17, blue_hi, IVORY)):
        for v in range(v0, v1):
            disp.px(u, v, bidi_col(u, blue_hi if v in (15, 16) else base_c, hi_c))
    return disp


def matter_cable_relay_sprites():
    blue = RGB["blue"]
    blue_hi = lighten(blue, .34)
    green = PORT_ENERGY_INPUT
    green_hi = lighten(green, .36)
    red = PORT_ENERGY_OUTPUT
    red_hi = lighten(red, .36)

    sprites = {
        "matter_cable_relay_end": _relay_end_plate(blue, blue_hi),
        "matter_cable_relay_end_out": _relay_end_plate(red, red_hi, red),
        "matter_cable_relay_end_in": _relay_end_plate(green, green_hi, green),
        "matter_cable_relay_side": _relay_side_sprite(False),
        "matter_cable_relay_oneway_side": _relay_side_sprite(True),
        "matter_cable_relay_disp_bidi_closed": _relay_disp_frame(False, False),
        "matter_cable_relay_disp_bidi_open": strip(_relay_disp_frame(False, True, f) for f in range(16)),
        "matter_cable_relay_disp_oneway_closed": _relay_disp_frame(True, False),
        "matter_cable_relay_disp_oneway_open": strip(_relay_disp_frame(True, True, f) for f in range(16)),
    }
    mcmeta = {
        "matter_cable_relay_disp_bidi_open": {"animation": {"frametime": 2, "interpolate": True}},
        "matter_cable_relay_disp_oneway_open": {"animation": {"frametime": 2, "interpolate": True}},
    }
    return sprites, mcmeta


def matter_cable_relay_models(look=RELAY_LOOK):
    if look not in RELAY_LOOKS:
        raise ValueError(f"Unknown Matter Cable Relay look: {look}")
    family = Family()
    body = [
        Box("collar_out", (5, 5, 0), (11, 11, 2)),
        Box("collar_in", (5, 5, 14), (11, 11, 16)),
        Box("frame_out", (4, 4, 2), (12, 12, 3.5)),
        Box("frame_in", (4, 4, 12.5), (12, 12, 14)),
        Box("rail_tl", (4, 11, 3.5), (5, 12, 12.5)),
        Box("rail_tr", (11, 11, 3.5), (12, 12, 12.5)),
        Box("rail_bl", (4, 4, 3.5), (5, 5, 12.5)),
        Box("rail_br", (11, 4, 3.5), (12, 5, 12.5)),
    ]
    parts = [Box("display_core", (5, 5, 3.5), (11, 11, 12.5), flat=True)]

    def bidi_skin(box, face):
        if face in ("north", "south"):
            return _relay_mapped_longitudinal(box, face, "end")
        return _relay_mapped_longitudinal(box, face, "side")

    def oneway_skin(box, face):
        if face == "north":
            return _relay_mapped_longitudinal(box, face, "end_out")
        if face == "south":
            return _relay_mapped_longitudinal(box, face, "end_in")
        return _relay_mapped_longitudinal(box, face, "side")

    drawn_bidi = family.model("matter_cable_relay", "network",
                              {"end": "matter_cable_relay_end", "side": "matter_cable_relay_side"},
                              body, bidi_skin, context=body + parts, particle="matter_cable_relay_side")
    drawn_oneway = family.model("matter_cable_relay_oneway", "network",
                                {"end_out": "matter_cable_relay_end_out", "end_in": "matter_cable_relay_end_in",
                                 "side": "matter_cable_relay_oneway_side"},
                                body, oneway_skin, context=body + parts, particle="matter_cable_relay_oneway_side")

    part_names = {}
    for mode, drawn in (("bidi", drawn_bidi), ("oneway", drawn_oneway)):
        for state, emissive in (("closed", False), ("open", True)):
            lit = [Box(box.name, box.lo, box.hi, emissive=emissive, flat=True) for box in parts]
            part_name = f"matter_cable_relay_{mode}_{state}_parts"
            tex_name = f"matter_cable_relay_disp_{mode}_{state}"
            shown = family.model(part_name, "part", {"disp": tex_name}, lit,
                                 lambda box, face: _relay_mapped_longitudinal(box, face, "disp"),
                                 context=body + lit)
            family.compose(f"matter_cable_relay {mode} {state}", drawn + shown)
            part_names[(mode, state)] = part_name

    states = []
    for side, rot in ROTATION.items():
        states.append({"when": {"facing": side, "directional": "false"},
                       "apply": {"model": "rematter:block/matter_cable_relay", **rot}})
        states.append({"when": {"facing": side, "directional": "true"},
                       "apply": {"model": "rematter:block/matter_cable_relay_oneway", **rot}})
        for directional, mode in (("false", "bidi"), ("true", "oneway")):
            for is_open, state in (("false", "closed"), ("true", "open")):
                states.append({"when": {"facing": side, "directional": directional, "open": is_open},
                               "apply": {"model": "rematter:block/" + part_names[(mode, state)], **rot}})
    family.blockstates["matter_cable_relay"] = {"multipart": states}
    family.items["matter_cable_relay"] = {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": "rematter:block/matter_cable_relay"},
        {"type": "minecraft:model", "model": "rematter:block/" + part_names[("bidi", "open")]},
    ]}}
    sprites, mcmeta = matter_cable_relay_sprites()
    return family, sprites, mcmeta


def spatial_anchor_sprites():
    """Full 16x16 Spatial Anchor: flush full-height ports on 4 sides, recessed 5x5 chunk-radar console on the front,
    and a sculpted recessed 3D Stepped Transmitter Dome rising flush to y=16 on top."""
    blue = RGB["blue"]
    blue_hi = lighten(blue, .40)
    blue_mid = lighten(blue, .20)
    blue_lo = scale(blue, .58)
    blue_dim = scale(blue, .32)

    # Full-height 32x32 front armored console frame (v=0..32 -> y=0..16):
    # upper brow (v=0..9) + recessed 5x5 radar bay (u=5..27, v=10..27) + Copper badge plinth (v=28..31)
    front = Sprite(fill=BASE)
    front.ring(0, 0, 32, 32, SEAM)
    front.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    # Upper brow telemetry strip & steel transceiver vents (v=2..8)
    front.rect(5, 3, 27, 7, STEEL_SHADE)
    front.bevel(5, 3, 27, 7, TRIM, STEEL_DEEP)
    for vx in (7, 11, 19, 23):
        front.rect(vx, 4, vx + 2, 6, blue)
    front.rect(14, 4, 18, 6, blue_hi)
    # Lower plinth with Copper maker's badge
    front.rect(2, 28, 30, 31, SHADE)
    front.row(28, 2, 30, BASE)
    front.rect(10, 29, 22, 31, BADGE)
    front.row(29, 10, 22, BADGE_LIGHT)
    # Recessed screen bay frame (u=3..29, v=9..27)
    front.rect(3, 9, 29, 27, WELL)
    front.bevel(3, 9, 29, 27, PORT_DARK, HIGHLIGHT)
    for cx, cy in ((2, 8), (26, 8), (2, 24), (26, 24)):
        front.rect(cx, cy, cx + 4, cy + 4, PORT_METAL)
        front.bevel(cx, cy, cx + 4, cy + 4, TRIM, PORT_DARK)
        front.rect(cx + 1, cy + 1, cx + 3, cy + 3, blue)

    # Top full-height rim + recessed 3D Transmitter Dome bay (u,v=3..29):
    # outer armored rim (0..3 & 29..32) -> dark bay floor -> dome_base (5..27) -> dome_mid (7..25) + 4 cardinal antenna struts!
    top = Sprite(fill=BASE)
    top.ring(0, 0, 32, 32, SEAM)
    top.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    top.rect(3, 3, 29, 29, WELL)
    top.bevel(2, 2, 30, 30, PORT_DARK, HIGHLIGHT)
    # North Copper orientation tab on rim
    top.rect(12, 1, 20, 3, COPPER)
    top.row(1, 12, 20, COPPER_LIGHT)
    # Four steel corner transmitter clamps + cardinal feed rails
    for cx, cy in ((1, 1), (27, 1), (1, 27), (27, 27)):
        top.rect(cx, cy, cx + 4, cy + 4, STEEL_SHADE)
        top.bevel(cx, cy, cx + 4, cy + 4, TRIM, STEEL_DEEP)
        top.rect(cx + 1, cy + 1, cx + 3, cy + 3, blue)
    for x0, y0, x1, y1 in ((14, 3, 18, 7), (14, 25, 18, 29), (3, 14, 7, 18), (25, 14, 29, 18)):
        top.rect(x0, y0, x1, y1, STEEL)
        top.bevel(x0, y0, x1, y1, TRIM, STEEL_DEEP)
    # Stepped dome_base (u,v=5..27) & dome_mid (u,v=7..25) steel collars
    top.rect(5, 5, 27, 27, STEEL_SHADE)
    top.bevel(5, 5, 27, 27, TRIM, STEEL_DEEP)
    for cx, cy in ((6, 6), (24, 6), (6, 24), (24, 24)):
        top.rect(cx, cy, cx + 2, cy + 2, blue)
    top.rect(7, 7, 25, 25, STEEL)
    top.bevel(7, 7, 25, 25, TRIM, STEEL_DEEP)
    top.rect(9, 9, 23, 23, WELL)
    top.ring(9, 9, 23, 23, blue_lo)

    # Inner stepped dome collar vertical wall texture (samples v=3..6 for dome_mid & dome_base inside top bay)
    dome_wall = Sprite(fill=STEEL_SHADE)
    dome_wall.rect(0, 0, 32, 32, STEEL_DEEP)
    dome_wall.rect(0, 3, 32, 5, STEEL)
    dome_wall.row(3, 0, 32, TRIM)
    dome_wall.rect(13, 3, 19, 5, blue)
    dome_wall.rect(0, 5, 32, 7, STEEL_SHADE)
    dome_wall.row(5, 0, 32, TRIM)
    for vx in (7, 11, 20, 24):
        dome_wall.px(vx, 5, blue)

    # Front Screen: crisp 5x5 chunk-grid radar (front_screen x=2.5..13.5, y=2.5..11 -> u=5..27, v=10..27)
    screen_idle = Sprite(fill=WELL)
    screen_idle.ring(5, 10, 27, 27, SEAM)
    screen_idle.bevel(6, 11, 26, 26, PORT_LIGHT, PORT_DARK)
    screen_idle.rect(7, 12, 25, 25, WELL)
    for gy in range(5):
        for gx in range(5):
            ux = 9 + gx * 3
            uy = 12 + gy * 2 + (1 if gy >= 2 else 0)
            col = blue_lo if (gx == 2 and gy == 2) else blue_dim
            screen_idle.rect(ux, uy, ux + 2, uy + 2, col)

    def screen_active_frame(f):
        s = Sprite(fill=WELL)
        s.ring(5, 10, 27, 27, SEAM)
        s.bevel(6, 11, 26, 26, blue_mid, blue_lo)
        s.rect(7, 12, 25, 25, WELL)
        ring_r = (f * 4.0) / 16.0
        for gy in range(5):
            for gx in range(5):
                ux = 9 + gx * 3
                uy = 12 + gy * 2 + (1 if gy >= 2 else 0)
                d = max(abs(gx - 2), abs(gy - 2))
                delta = min(abs(d - ring_r), 4.0 - abs(d - ring_r))
                if gx == 2 and gy == 2:
                    col = IVORY if f % 8 < 3 else blue_hi
                elif delta < 0.7:
                    col = blue_hi
                elif delta < 1.3:
                    col = blue_mid
                else:
                    col = blue
                s.rect(ux, uy, ux + 2, uy + 2, col)
        s.rect(7, 13, 8, 24, blue_lo)
        s.px(7, 13 + (f // 2) % 10, blue_hi)
        s.rect(24, 13, 25, 24, blue_lo)
        s.px(24, 22 - (f // 2) % 10, blue_hi)
        return s

    screen_active = strip(screen_active_frame(f) for f in range(16))

    # Top 3D Transmitter Dome Cap ("Kuppel / Sender", x,z=5..11 -> u,v=10..22; y=14.5..16 -> side v=0..3)
    dome_idle = Sprite(fill=STEEL_DEEP)
    dome_idle.rect(10, 10, 22, 22, STEEL_SHADE)
    dome_idle.bevel(10, 10, 22, 22, TRIM, STEEL_DEEP)
    dome_idle.rect(11, 11, 21, 21, WELL)
    dome_idle.ring(11, 11, 21, 21, blue_dim)
    dome_idle.rect(13, 13, 19, 19, blue_dim)
    dome_idle.rect(15, 15, 17, 17, blue_lo)
    for d in (10, 11, 20, 21):
        dome_idle.px(d, d, STEEL)
        dome_idle.px(d, 31 - d, STEEL)
    dome_idle.rect(10, 0, 22, 3, STEEL_SHADE)
    dome_idle.rect(11, 0, 21, 3, blue_dim)
    dome_idle.rect(13, 0, 19, 2, blue_lo)
    for sx in (10, 21):
        dome_idle.col(sx, 0, 3, STEEL)
        dome_idle.px(sx, 0, STEEL_DEEP)

    def dome_active_frame(f):
        s = Sprite(fill=STEEL_DEEP)
        pulse = (f * 5.0) / 16.0
        for y in range(10, 22):
            for x in range(10, 22):
                dx = x - 15.5
                dy = y - 15.5
                r = (dx * dx + dy * dy) ** 0.5
                if r > 6.6:
                    col = STEEL_DEEP
                elif r > 5.4:
                    col = TRIM if (dx + dy < 0) else STEEL_SHADE
                elif r < 1.3:
                    col = IVORY if f % 8 < 4 else blue_hi
                else:
                    delta = min(abs(r - pulse), 5.0 - abs(r - pulse))
                    if delta < 0.75:
                        col = blue_hi
                    elif delta < 1.55:
                        col = blue_mid
                    elif r < 3.6:
                        col = blue
                    else:
                        col = blue_lo
                s.px(x, y, col)
        for d in (10, 11, 20, 21):
            s.px(d, d, TRIM if d < 16 else STEEL_SHADE)
            s.px(d, 31 - d, STEEL)
        crest_v = 2 - ((f * 3) // 16)
        for y in range(0, 3):
            dist = min(abs(y - crest_v), 3 - abs(y - crest_v))
            band_col = IVORY if (y == 0 and dist == 0) else blue_hi if dist == 0 else blue_mid if dist == 1 else blue
            for x in range(10, 22):
                if x in (10, 21):
                    col = STEEL_DEEP if y == 0 else (TRIM if x == 10 else STEEL_SHADE)
                elif y == 0 and x in (11, 20):
                    col = STEEL
                else:
                    col = band_col
                s.px(x, y, col)
        return s

    dome_active = strip(dome_active_frame(f) for f in range(16))

    return (
        {
            "spatial_anchor_front": front,
            "spatial_anchor_top": top,
            "spatial_anchor_dome_wall": dome_wall,
            "spatial_anchor_screen_idle": screen_idle,
            "spatial_anchor_screen_active": screen_active,
            "spatial_anchor_dome_idle": dome_idle,
            "spatial_anchor_dome_active": dome_active,
        },
        {
            "spatial_anchor_screen_active": {"animation": {"frametime": 3, "interpolate": True}},
            "spatial_anchor_dome_active": {"animation": {"frametime": 3, "interpolate": True}},
        },
    )


def spatial_anchor_models():
    family = Family()
    # Full 16x16 flush LEFT/RIGHT/BACK/DOWN ports, recessed FRONT 5x5 radar screen, recessed TOP 3D Transmitter Dome!
    body = [
        Box("core", (0, 0, 1.5), (16, 13, 16)),
        Box("wall_south", (0, 13, 14.5), (16, 16, 16)),
        Box("wall_west", (0, 13, 0), (1.5, 16, 14.5)),
        Box("wall_east", (14.5, 13, 0), (16, 16, 14.5)),
        Box("wall_north", (1.5, 13, 0), (14.5, 16, 1.5)),
        Box("post_left", (14.5, 0, 0), (16, 13, 1.5)),
        Box("post_right", (0, 0, 0), (1.5, 13, 1.5)),
        Box("plinth", (1.5, 0, 0), (14.5, 2, 1.5)),
        Box("cap_front", (1.5, 11.5, 0), (14.5, 13, 1.5)),
        Box("dome_base", (2.5, 13, 2.5), (13.5, 13.5, 13.5), step=True),
        Box("dome_mid", (3.5, 13.5, 3.5), (12.5, 14.5, 12.5)),
    ]
    parts = [
        Box("front_screen", (2.5, 2.5, 1), (13.5, 11, 1.5), step=True, flat=True),
        Box("top_dome", (5, 14.5, 5), (11, 16, 11), flat=True),
    ]

    def body_skin(box, face):
        if face == "up":
            return "top"
        if box.name in ("dome_base", "dome_mid") and face in ("north", "south", "east", "west"):
            return "dome_wall"
        if face == "north" and box.on_boundary(face):
            return "front"
        return "inner"

    textures = {
        "front": "spatial_anchor_front",
        "top": "spatial_anchor_top",
        "dome_wall": "spatial_anchor_dome_wall",
        "inner": "casing_inner",
    }
    ports = port_plates.definition("spatial_anchor")
    drawn = family.model("spatial_anchor", "network", textures, body, body_skin,
                         context=body + parts, particle="spatial_anchor_front",
                         omit=port_plates.omitted_faces("spatial_anchor"))
    part_names = {}
    for state, emissive in (("idle", False), ("active", True)):
        lit = [Box(b.name, b.lo, b.hi, emissive=emissive, step=b.step, flat=True) for b in parts]
        part_name = f"spatial_anchor_{state}_parts"
        shown = family.model(
            part_name,
            "part",
            {
                "screen": f"spatial_anchor_screen_{state}",
                "dome": f"spatial_anchor_dome_{state}",
            },
            lit,
            lambda b, f: "screen" if b.name == "front_screen" else "dome",
            context=body + lit,
        )
        family.compose(f"spatial_anchor {state}", drawn + shown + port_plates.parts("spatial_anchor", True), port_plates.OVERLAP)
        part_names[state] = part_name
    states = []
    for facing in ("north", "east", "south", "west"):
        rot = ROTATION[facing]
        states.append({"when": {"facing": facing}, "apply": {"model": "rematter:block/spatial_anchor", **rot}})
        states.append({"when": {"facing": facing, "active": "false"}, "apply": {"model": "rematter:block/" + part_names["idle"], **rot}})
        states.append({"when": {"facing": facing, "active": "true"}, "apply": {"model": "rematter:block/" + part_names["active"], **rot}})
        states.append({"when": {"facing": facing}, "apply": {
            "type": ports["type"], **rot, "default": ports["default"], "sides": ports["sides"],
        }})
    family.blockstates["spatial_anchor"] = {"multipart": states}
    item_models = ["rematter:block/spatial_anchor", "rematter:block/" + part_names["active"]] + port_plates.default_models(ports)
    family.items["spatial_anchor"] = {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": m} for m in item_models
    ]}}
    sprites, mcmeta = spatial_anchor_sprites()
    return family, sprites, mcmeta


def dimensional_link_sprites():
    """Endgame Dimensional Entangled Link: full 16x16 dark obsidian-graphite monolith with sleek thin steel+Blue+Resonant
    armored gateway frame, HUGE animated Front Interdimensional Rift Console, 4 glowing inward corner Resonance Prongs,
    and a GIANT blazing 3D Blue+Primal Dimensional Orb rising flush to y=16 — unmistakably more epic than regular Link."""
    import math
    blue = RGB["blue"]
    blue_hi = lighten(blue, .40)
    blue_lo = scale(blue, .58)
    blue_dim = scale(blue, .32)
    resonant = CABLE_TIERS["resonant_matter_cable"]["stripe"]
    spec = (PRIMAL_TEAL, PRIMAL_COOL, PRIMAL_BODY, PRIMAL_WARM, PRIMAL_GOLD)

    # Full-height 32x32 rich dark obsidian-graphite front frame + glowing vertical Blue conduit rails + Resonant brow!
    front = Sprite(fill=BASE)
    front.ring(0, 0, 32, 32, SEAM)
    front.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    # Machined top armored brow with gleaming Steel edge & Resonant + Blue emitter jewels (v=1..6)
    front.rect(2, 1, 30, 6, SHADE)
    front.row(1, 2, 30, TRIM)
    front.row(5, 2, 30, SEAM)
    for x in (5, 9, 21, 25):
        front.rect(x, 2, x + 2, 5, resonant)
    front.rect(13, 2, 19, 5, blue)
    front.row(2, 13, 19, blue_hi)
    # Sleek vertical glowing Lapis-Blue conduit rails along both front corner posts (u=1..3 & u=29..31)
    for u0 in (1, 29):
        front.rect(u0, 6, u0 + 2, 27, blue)
        front.col(u0, 6, 27, blue_hi)
        for ry in (8, 16, 24):
            front.row(ry, u0, u0 + 2, TRIM)
    # Lower plinth with Copper maker's badge flanked by Blue + Resonant crystal inlays
    front.rect(2, 28, 30, 31, SHADE)
    front.row(28, 2, 30, STEEL_SHADE)
    front.rect(10, 29, 22, 31, BADGE)
    front.row(29, 10, 22, BADGE_LIGHT)
    front.rect(4, 29, 8, 31, blue)
    front.rect(24, 29, 28, 31, blue)
    front.px(9, 29, resonant); front.px(22, 29, resonant)
    # Huge recessed gateway viewport bay (u=3..29, v=6..27)
    front.rect(3, 6, 29, 27, WELL)
    front.bevel(3, 6, 29, 27, TRIM, STEEL_DEEP)

    # Top sleek full-height rim (u,v=0..3 & 29..32) + 4 inward glowing Resonance Focus Prongs (3..7 & 25..29) cradling giant orb!
    top = Sprite(fill=BASE)
    top.ring(0, 0, 32, 32, SEAM)
    top.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    top.rect(3, 3, 29, 29, WELL)
    top.bevel(2, 2, 30, 30, TRIM, STEEL_DEEP)
    # Resonant energy inlays along the 4 rim midpoints + Copper north pointer
    for x in (9, 21):
        top.rect(x, 1, x + 2, 3, resonant)
        top.rect(x, 29, x + 2, 31, resonant)
        top.rect(1, x, 3, x + 2, resonant)
        top.rect(29, x, 31, x + 2, resonant)
    top.rect(13, 1, 19, 3, COPPER)
    top.row(1, 13, 19, COPPER_LIGHT)
    # Four corner Steel+Blue Focus Prong crowns (u,v=3..7 & 25..29) pointing inward toward the blazing orb!
    for cx, cy in ((3, 3), (25, 3), (3, 25), (25, 25)):
        top.rect(cx, cy, cx + 4, cy + 4, STEEL)
        top.bevel(cx, cy, cx + 4, cy + 4, TRIM, STEEL_DEEP)
        top.rect(cx + 1, cy + 1, cx + 3, cy + 3, blue)
    top.px(5, 5, blue_hi); top.px(26, 5, blue_hi); top.px(5, 26, blue_hi); top.px(26, 26, blue_hi)
    # Dark resonance floor with subtle Blue containment conduits radiating under the giant orb
    for i in (15, 16):
        top.col(i, 3, 29, blue_dim)
        top.row(i, 3, 29, blue_dim)

    # Vertical faces of the 4 inward Resonance Prongs (y=13..15.5 -> v=1..6, u=3..7 & 25..29)
    prong_wall = Sprite(fill=STEEL_SHADE)
    prong_wall.rect(0, 0, 32, 32, STEEL_DEEP)
    for u0 in (3, 25):
        prong_wall.rect(u0, 1, u0 + 4, 6, STEEL)
        prong_wall.bevel(u0, 1, u0 + 4, 6, TRIM, STEEL_DEEP)
        prong_wall.rect(u0 + 1, 2, u0 + 3, 5, blue)
        prong_wall.row(1, u0 + 1, u0 + 3, resonant)

    # HUGE Front Interdimensional Rift Console Screen (front_screen x=2..14, y=2.5..12.5 -> u=4..28, v=7..27 = 24x20 texels!)
    screen_idle = Sprite(fill=WELL)
    screen_idle.ring(4, 7, 28, 27, SEAM)
    screen_idle.bevel(5, 8, 27, 26, PORT_DARK, SEAM)
    screen_idle.rect(6, 9, 26, 25, WELL)
    for y in range(10, 24):
        for x in range(7, 25):
            dx, dy = x - 15.5, y - 16.5
            d2 = dx * dx + dy * dy
            if 28 < d2 <= 42:
                screen_idle.px(x, y, blue_dim)
            elif d2 <= 16:
                screen_idle.px(x, y, PRIMAL_VOID)
    screen_idle.row(17, 7, 25, blue_dim)
    screen_idle.rect(14, 15, 18, 19, blue_lo)

    def screen_linked_frame(frame):
        s = Sprite(fill=WELL)
        s.ring(4, 7, 28, 27, SEAM)
        s.bevel(5, 8, 27, 26, blue_lo, SEAM)
        s.rect(6, 9, 26, 25, WELL)
        # Dramatic swirling Interdimensional Rift Portal + horizontal energy horizon + flanking Blue power telemetry bars!
        for y in range(9, 25):
            for x in range(6, 26):
                dx = (x - 15.5) * 0.9
                dy = (y - 16.5) * 1.1
                d2 = dx * dx + dy * dy
                if d2 > 46:
                    # Deep starlit coordinate matrix with subtle Blue corner markers
                    col = PRIMAL_VOID if (x % 4 == 0 and y % 4 == 0) else WELL
                elif d2 > 34:
                    # Rotating Electric-Blue outer dimensional gateway ring
                    theta = math.atan2(dy, dx)
                    seg = (theta * 3.0 / (2.0 * math.pi) + frame / 16.0) % 1.0
                    col = blue_hi if seg < 0.28 else blue if seg < 0.72 else blue_lo
                elif d2 < 4.5:
                    col = PRIMAL_CORE
                elif d2 < 11.5:
                    col = PRIMAL_CORE_SOFT
                else:
                    # 5-stop Primal dimensional wormhole spiral!
                    theta = math.atan2(dy, dx)
                    r = math.sqrt(d2)
                    swirl = (theta * 2.0 / (2.0 * math.pi) + r * 0.16 - frame / 16.0) % 1.0
                    band = int(swirl * 8) % 8
                    if band == 0:
                        col = spec[(frame // 3) % 5]
                    elif band in (1, 2):
                        col = blue_hi
                    elif band in (3, 4):
                        col = PRIMAL_LIGHT
                    elif band == 5:
                        col = PRIMAL_BODY
                    elif band == 6:
                        col = spec[((frame // 3) + 2) % 5]
                    else:
                        col = PRIMAL_DEEP
                s.px(x, y, col)
        # Glowing dimensional energy horizon beam piercing the portal
        pkt = 7 + (frame % 18)
        s.px(pkt, 16, PRIMAL_CORE)
        s.px(31 - pkt, 17, blue_hi)
        # Flanking animated telemetry bars (left u=6, right u=25)
        bar_y = 10 + (frame % 14)
        s.col(6, 10, 24, blue_dim)
        s.rect(6, bar_y, 7, min(24, bar_y + 3), blue_hi)
        s.col(25, 10, 24, blue_dim)
        r_y = 23 - (frame % 14)
        s.rect(25, max(10, r_y - 2), 26, r_y + 1, blue_hi)
        return s

    screen_linked = strip(screen_linked_frame(f) for f in range(16))

    # GIANT Elevated 3D Blue+Primal Dimensional Orb (top_orb x,z=4..12 -> u,v=8..24; y=13..16 -> side v=0..6!)
    orb_idle = Sprite(fill=WELL)
    for y in range(8, 24):
        for x in range(8, 24):
            dx, dy = x - 15.5, y - 15.5
            d2 = dx * dx + dy * dy
            if d2 > 60:
                col = PRIMAL_VOID
            elif d2 > 42:
                col = blue_lo if (dx + dy < 0) else blue_dim
            elif d2 > 18:
                col = PRIMAL_DEEP
            else:
                col = blue_lo
            orb_idle.px(x, y, col)
    for y in range(0, 6):
        for x in range(8, 24):
            dx = abs(x - 15.5)
            if (y in (0, 5) and dx > 6.0) or dx > 7.2:
                col = PRIMAL_VOID
            elif y == 3:
                col = blue_lo
            else:
                col = PRIMAL_DEEP if dx < 4.5 else blue_dim
            orb_idle.px(x, y, col)

    def orb_linked_frame(frame):
        s = Sprite(fill=WELL)
        # Giant 16x16 blazing top view of 3D Dimensional Orb (u=8..24, v=8..24)
        for y in range(8, 24):
            for x in range(8, 24):
                dx, dy = x - 15.5, y - 15.5
                d2 = dx * dx + dy * dy
                sx, sy = x - 13.2, y - 13.2
                s2 = sx * sx + sy * sy
                if d2 > 60:
                    col = PRIMAL_VOID
                elif d2 > 44:
                    # Rotating blazing Electric-Blue dimensional containment aura
                    theta = math.atan2(dy, dx)
                    seg = (theta * 4.0 / (2.0 * math.pi) + frame / 16.0) % 1.0
                    col = PRIMAL_CORE if seg < 0.12 else blue_hi if seg < 0.45 else blue
                elif s2 < 4.5:
                    col = PRIMAL_CORE
                elif s2 < 12.0:
                    col = PRIMAL_CORE_SOFT
                else:
                    theta = math.atan2(dy, dx)
                    r = math.sqrt(d2)
                    swirl = (theta * 2.0 / (2.0 * math.pi) + r * 0.14 - frame / 16.0) % 1.0
                    band = int(swirl * 8) % 8
                    if band == 0:
                        col = spec[(frame // 3) % 5]
                    elif band in (1, 2):
                        col = blue_hi
                    elif band in (3, 4):
                        col = PRIMAL_LIGHT
                    elif band == 5:
                        col = PRIMAL_BODY
                    elif band == 6:
                        col = spec[((frame // 3) + 2) % 5]
                    else:
                        col = PRIMAL_DEEP
                s.px(x, y, col)
        # Tall glowing 16x6 side wall of the giant 3D Dimensional Orb (u=8..24, v=0..6)
        for y in range(0, 6):
            for x in range(8, 24):
                dx = abs(x - 15.5)
                if (y in (0, 5) and dx > 5.8) or (y in (1, 4) and dx > 7.0):
                    col = PRIMAL_VOID
                elif y == 3:
                    col = PRIMAL_CORE if ((x - frame) % 6 == 0) else (blue_hi if dx < 4.5 else blue)
                elif y in (1, 2) and dx < 2.6:
                    col = PRIMAL_CORE if y == 1 else PRIMAL_CORE_SOFT
                elif dx > 6.0:
                    col = blue
                else:
                    wave = ((x - 8) / 16.0 - frame / 16.0 + y * 0.14) % 1.0
                    band = int(wave * 6) % 6
                    if band == 0:
                        col = spec[(frame // 3) % 5]
                    elif band in (1, 2):
                        col = PRIMAL_LIGHT
                    elif band in (3, 4):
                        col = PRIMAL_BODY
                    else:
                        col = blue_lo
                s.px(x, y, col)
        return s

    orb_linked = strip(orb_linked_frame(f) for f in range(16))

    return (
        {
            "dimensional_entangled_link_cap": top,
            "dimensional_entangled_link_casing": front,
            "dimensional_entangled_link_prong": prong_wall,
            "dimensional_entangled_link_screen_idle": screen_idle,
            "dimensional_entangled_link_screen_linked": screen_linked,
            "dimensional_entangled_link_orb_idle": orb_idle,
            "dimensional_entangled_link_orb_linked": orb_linked,
        },
        {
            "dimensional_entangled_link_screen_linked": {"animation": {"frametime": 2, "interpolate": True}},
            "dimensional_entangled_link_orb_linked": {"animation": {"frametime": 2, "interpolate": True}},
        },
    )


def dimensional_link_models():
    """Full 16x16 flush ports on 4 sides, EPIC dark-obsidian + Blue/Resonant gateway front screen, 4 glowing inward
    corner Resonance Prongs, and a GIANT blazing 3D Blue+Primal Orb rising flush to y=16 on top."""
    family = Family()
    body = [
        Box("core", (0, 0, 1.5), (16, 13, 16)),
        Box("wall_south", (0, 13, 14.5), (16, 16, 16)),
        Box("wall_west", (0, 13, 0), (1.5, 16, 14.5)),
        Box("wall_east", (14.5, 13, 0), (16, 16, 14.5)),
        Box("wall_north", (1.5, 13, 0), (14.5, 16, 1.5)),
        Box("post_left", (14.5, 0, 0), (16, 13, 1.5)),
        Box("post_right", (0, 0, 0), (1.5, 13, 1.5)),
        Box("plinth", (1.5, 0, 0), (14.5, 2, 1.5)),
        Box("cap_front", (1.5, 12.5, 0), (14.5, 13, 1.5), step=True),
        Box("prong_nw", (1.5, 13, 1.5), (3.5, 15.5, 3.5)),
        Box("prong_ne", (12.5, 13, 1.5), (14.5, 15.5, 3.5)),
        Box("prong_sw", (1.5, 13, 12.5), (3.5, 15.5, 14.5)),
        Box("prong_se", (12.5, 13, 12.5), (14.5, 15.5, 14.5)),
    ]
    parts = [
        Box("front_screen", (2, 2.5, 1), (14, 12.5, 1.5), step=True, flat=True),
        Box("top_orb", (4, 13, 4), (12, 16, 12), flat=True),
    ]

    def shell_skin(box, face):
        if face == "up":
            return "cap"
        if box.name.startswith("prong_") and face in ("north", "south", "east", "west"):
            return "prong"
        if face == "north" and box.on_boundary(face):
            return "shell"
        return "inner"

    textures = {
        "shell": "dimensional_entangled_link_casing",
        "cap": "dimensional_entangled_link_cap",
        "prong": "dimensional_entangled_link_prong",
        "inner": "casing_inner",
    }
    ports = port_plates.definition("dimensional_entangled_link")
    drawn = family.model("dimensional_entangled_link", "network", textures, body, shell_skin,
                         context=body + parts, particle="dimensional_entangled_link_casing",
                         omit=port_plates.omitted_faces("dimensional_entangled_link"))
    part_names = {}
    for state, emissive in (("idle", False), ("linked", True)):
        lit = [Box(b.name, b.lo, b.hi, emissive=emissive, step=b.step, flat=True) for b in parts]
        part_name = f"dimensional_entangled_link_{state}_parts"
        shown = family.model(
            part_name,
            "part",
            {
                "screen": f"dimensional_entangled_link_screen_{state}",
                "orb": f"dimensional_entangled_link_orb_{state}",
            },
            lit,
            lambda b, f: "screen" if b.name == "front_screen" else "orb",
            context=body + lit,
        )
        family.compose(f"dimensional_entangled_link {state}", drawn + shown + port_plates.parts("dimensional_entangled_link", True), port_plates.OVERLAP)
        part_names[state] = part_name
    states = []
    for facing in ("north", "east", "south", "west"):
        rot = ROTATION[facing]
        states.append({"when": {"facing": facing}, "apply": {"model": "rematter:block/dimensional_entangled_link", **rot}})
        states.append({"when": {"facing": facing, "linked": "false"}, "apply": {"model": "rematter:block/" + part_names["idle"], **rot}})
        states.append({"when": {"facing": facing, "linked": "true"}, "apply": {"model": "rematter:block/" + part_names["linked"], **rot}})
        states.append({"when": {"facing": facing}, "apply": {
            "type": ports["type"], **rot, "default": ports["default"], "sides": ports["sides"],
        }})
    family.blockstates["dimensional_entangled_link"] = {"multipart": states}
    item_models = ["rematter:block/dimensional_entangled_link", "rematter:block/" + part_names["linked"]] + port_plates.default_models(ports)
    family.items["dimensional_entangled_link"] = {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": m} for m in item_models
    ]}}
    sprites, mcmeta = dimensional_link_sprites()
    return family, sprites, mcmeta


def generate(check):
    out = GeneratedFiles(ROOT, check)
    for name in ("parallel_upgrade", "pressure_module", "configuration_card",
                 "wireless_booster", "dimensional_card", "anchor_upgrade",
                 "wireless_matter_terminal", "energy_core", "portable_energy_cell",
                 "pressurized_canister", "spectrometer_lens",
                 "bore_core", "extraction_focus_upgrade", "particle_resonator",
                 "primal_matrix_core", "mass_compression_upgrade"):
        out.json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"rematter:item/{name}"}})
        out.json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"rematter:item/{name}"}})
        if name in ("parallel_upgrade", "primal_matrix_core"):
            continue  # Recipes belong to the industrial / primal generators.
        if name == "pressure_module":
            recipe(out, name, ["RLR", "OCO", "RLR"], {
                "R": "rematter:rhevon_ingot", "L": "rematter:diamond_lattice",
                "O": "rematter:optical_circuit", "C": "rematter:control_circuit"}, unlock="rematter:machine_frame")
        elif name == "configuration_card":
            recipe(out, name, [" C ", "RSR", " P "], {
                "C": "rematter:control_circuit", "S": "rematter:structural_bars",
                "R": "rematter:rhevon_ingot", "P": "minecraft:copper_ingot"}, category="equipment")
        elif name == "wireless_booster":
            recipe(out, name, [" B ", "ECE", " P "], {
                "B": "rematter:blue_matrix_crystal", "E": "#rematter:full_ender_pearl_powders",
                "C": "rematter:control_circuit", "P": "minecraft:copper_ingot"}, count=2)
        elif name == "dimensional_card":
            recipe(out, name, ["EPE", "ONO", "WBW"], {
                "E": "rematter:ender_conductor", "P": "rematter:primal_crystal",
                "O": "rematter:optical_circuit", "N": "rematter:netherite_sheath",
                "W": "rematter:wireless_booster", "B": "rematter:blue_matrix_crystal"},
                unlock="rematter:primal_crystal")
        elif name == "anchor_upgrade":
            recipe(out, name, ["SBS", "OCO", "EBE"], {
                "S": "rematter:structural_bars", "B": "rematter:blue_matrix_crystal",
                "O": "rematter:optical_circuit", "C": "rematter:control_circuit",
                "E": "rematter:ender_conductor"}, unlock="rematter:ender_conductor")
        elif name == "wireless_matter_terminal":
            recipe(out, name, [" B ", "ETE", "OKO"], {
                "B": "rematter:blue_matrix_crystal", "E": "rematter:ender_conductor",
                "T": "rematter:matter_terminal", "O": "rematter:optical_circuit",
                "K": "rematter:energy_core"}, category="equipment")
        elif name == "energy_core":
            recipe(out, name, ["CRC", "VEV", "CRC"], {
                "C": "minecraft:copper_ingot", "R": "rematter:rhevon_ingot",
                "V": "rematter:virelite_ceramic", "E": "rematter:ember_matrix_crystal"},
                unlock="rematter:ember_matrix_crystal")
        elif name == "portable_energy_cell":
            recipe(out, name, ["KCK", "LHL", "O O"], {
                "K": "rematter:energy_core", "C": "rematter:reinforced_matter_cable",
                "L": "rematter:lumivex_ingot", "H": "rematter:cell_housing",
                "O": "rematter:optical_circuit"}, category="equipment")
        elif name == "pressurized_canister":
            recipe(out, name, [" C ", "VMV", "RHR"], {
                "C": "minecraft:copper_ingot", "V": "rematter:virelite_ceramic",
                "M": "rematter:mint_matrix_crystal", "R": "rematter:rhevon_ingot",
                "H": "rematter:cell_housing"}, category="equipment")
        elif name == "spectrometer_lens":
            recipe(out, name, [" G ", "BIB", " C "], {
                "G": "minecraft:glass_pane", "B": "rematter:structural_bars",
                "I": "rematter:ice_matrix_crystal", "C": "rematter:control_circuit"},
                category="equipment", unlock="rematter:ice_matrix_crystal")
        elif name == "bore_core":
            recipe(out, name, ["RDR", "DSD", "C C"], {
                "R": "rematter:rhevon_ingot", "D": "rematter:diamond_lattice",
                "S": "rematter:structural_bars", "C": "minecraft:copper_ingot"},
                unlock="rematter:diamond_lattice")
        elif name == "extraction_focus_upgrade":
            recipe(out, name, ["RMR", "OCO", "RDR"], {
                "R": "rematter:rhevon_ingot", "M": "rematter:mint_matrix_crystal",
                "O": "rematter:optical_circuit", "C": "rematter:control_circuit",
                "D": "rematter:diamond_lattice"}, unlock="rematter:mint_matrix_crystal")
        elif name == "particle_resonator":
            recipe(out, name, ["ENE", "VOV", "CEC"], {
                "E": "rematter:ender_conductor", "N": "rematter:netherite_sheath",
                "V": "rematter:violet_matrix_crystal", "O": "rematter:optical_circuit",
                "C": "rematter:ember_matrix_crystal"}, unlock="rematter:netherite_sheath")
        elif name == "mass_compression_upgrade":
            recipe(out, name, ["NDN", "OPO", "NBN"], {
                "N": "rematter:netherite_sheath", "D": "rematter:diamond_lattice",
                "O": "rematter:optical_circuit", "P": "rematter:pressure_module",
                "B": "rematter:structural_bars"}, unlock="rematter:netherite_sheath")

    for block_name, builder, pattern, keys, result_count in (
            ("wireless_access_point", access_point_models, [" B ", "OFO", "ECE"], {
                "B": "rematter:blue_matrix_crystal", "O": "rematter:optical_circuit",
                "F": "rematter:machine_frame", "E": "rematter:ender_conductor",
                "C": "rematter:reinforced_matter_cable"}, 1),
            ("matter_level_emitter", level_emitter_models, [" R ", "CFC", "AMA"], {
                "R": "minecraft:redstone_torch", "C": "rematter:control_circuit",
                "F": "rematter:machine_frame", "A": "rematter:matter_cable",
                "M": "rematter:mint_matrix_crystal"}, 1),
            ("matter_cable_relay", matter_cable_relay_models, [" R ", "CBC", "AMA"], {
                "R": "minecraft:redstone", "C": "rematter:control_circuit",
                "B": "rematter:blue_matrix_crystal", "A": "rematter:matter_cable",
                "M": "rematter:machine_frame"}, 1),
            ("spatial_anchor", spatial_anchor_models, ["EBE", "FAF", "RKR"], {
                "E": "rematter:ender_conductor", "B": "rematter:blue_matrix_crystal",
                "F": "rematter:machine_frame", "A": "rematter:anchor_upgrade",
                "R": "rematter:rhevon_ingot", "K": "rematter:energy_core"}, 1),
            ("dimensional_entangled_link", dimensional_link_models, ["EBE", "RLR", "ONO"], {
                "E": "rematter:ender_conductor", "B": "rematter:blue_matrix_crystal",
                "R": "rematter:resonant_matter_cable", "L": "rematter:entangled_link",
                "O": "rematter:optical_circuit", "N": "rematter:netherite_sheath"}, 2),
    ):
        built = builder()
        if len(built) == 3:
            family, sprites, mcmeta = built
        else:
            family, sprites = built
            mcmeta = {}
        for name, sprite in sprites.items():
            out.png(ASSETS / f"textures/block/{name}.png", sprite.width, sprite.height, sprite.pixel)
        for name, meta in mcmeta.items():
            out.json(ASSETS / f"textures/block/{name}.png.mcmeta", meta)
        for folder, files in (("models/block", family.models), ("blockstates", family.blockstates), ("items", family.items)):
            for name, value in files.items():
                out.json(ASSETS / folder / f"{name}.json", value)
        recipe(out, block_name, pattern, keys, count=result_count)
        out.json(DATA / f"loot_table/blocks/{block_name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"rematter:{block_name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

    for suffix in ("", "_trusted", "_private"):
        stem = "security_card" + suffix
        out.json(ASSETS / f"models/item/{stem}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"rematter:item/{stem}"}})
    out.json(ASSETS / "items/security_card.json", {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:component",
            "component": "rematter:security_card_mode",
            "cases": [
                {"when": "public", "model": {"type": "minecraft:model", "model": "rematter:item/security_card"}},
                {"when": "trusted", "model": {"type": "minecraft:model", "model": "rematter:item/security_card_trusted"}},
                {"when": "private", "model": {"type": "minecraft:model", "model": "rematter:item/security_card_private"}},
            ],
            "fallback": {"type": "minecraft:model", "model": "rematter:item/security_card"},
        }
    })
    recipe(out, "security_card", [" S ", "RCR", " I "], {
        "S": "rematter:structural_bars", "R": "rematter:rhevon_ingot",
        "C": "rematter:control_circuit", "I": "minecraft:iron_ingot"},
        category="equipment")

    for suffix in ("", "_rotate", "_dismantle"):
        stem = "matter_wrench" + suffix
        out.json(ASSETS / f"models/item/{stem}.json", {
            "parent": "minecraft:item/handheld",
            "textures": {"layer0": f"rematter:item/{stem}"}})
    out.json(ASSETS / "items/matter_wrench.json", {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:component",
            "component": "rematter:wrench_mode",
            "cases": [
                {"when": "ports", "model": {"type": "minecraft:model", "model": "rematter:item/matter_wrench"}},
                {"when": "rotate", "model": {"type": "minecraft:model", "model": "rematter:item/matter_wrench_rotate"}},
                {"when": "dismantle", "model": {"type": "minecraft:model", "model": "rematter:item/matter_wrench_dismantle"}},
            ],
            "fallback": {"type": "minecraft:model", "model": "rematter:item/matter_wrench"},
        }
    })
    recipe(out, "matter_wrench", ["B B", " C ", " B "], {
        "B": "rematter:structural_bars", "C": "minecraft:copper_ingot"},
        category="equipment", unlock="rematter:structural_bars")
    out.json(COMMON_TAGS / "tools/wrench.json", {"replace": False, "values": ["rematter:matter_wrench"]})
    out.json(COMMON_TAGS / "tools.json", {"replace": False, "values": ["#c:tools/wrench"]})
    print(f"Verified {out.count} device/tool files" if check else f"Generated {out.count} device/tool files")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    generate(parser.parse_args().check)
