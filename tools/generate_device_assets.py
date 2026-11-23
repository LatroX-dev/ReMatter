#!/usr/bin/env python3
"""Generate upgrade, handheld tool and wireless network models, sprites, recipes and item tags."""
from pathlib import Path
import argparse
from family_geometry import Box
from family_palette import (BADGE, BADGE_LIGHT, BASE, HIGHLIGHT, IVORY, LED, LED_RED_DIM, PORT_LIGHT, PORT_METAL,
                            SEAM, SHADE, STANDBY, STEEL, STEEL_DEEP, STEEL_SHADE, TRIM, WELL, lighten, scale)
from family_sprites import Sprite, bolt
from generate_family_assets import Family
from generated_files import GeneratedFiles
from machine_palette import RGB
from recipe_discovery import shaped_discovery

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/rematter"
DATA = ROOT / "src/main/resources/data/rematter"
COMMON_TAGS = ROOT / "src/main/resources/data/c/tags/item"
ACCESS_POINT_LOOKS = ("beacon_mast", "dual_array", "dome_transceiver")
ACCESS_POINT_LOOK = "beacon_mast"


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
    blue = RGB["blue"]
    # Front/side transceiver housing sprite (32x32, 2 texels per model pixel).
    front = Sprite(fill=BASE)
    front.ring(0, 0, 32, 32, SEAM)
    front.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    # Plinth (y_model = 0..3 -> v_texel = 26..32): shaded base with Copper maker's badge.
    front.rect(4, 26, 28, 32, SHADE)
    front.row(26, 4, 28, HIGHLIGHT)
    front.rect(11, 28, 21, 31, BADGE)
    front.row(28, 11, 21, BADGE_LIGHT)
    # Pedestal (x_model = 4..12 -> u = 8..24, y_model = 3..8 -> v = 16..26): recessed Blue link window.
    front.rect(8, 16, 24, 26, BASE)
    front.bevel(8, 16, 24, 26, HIGHLIGHT, SEAM)
    front.rect(10, 18, 22, 24, WELL)
    front.rect(12, 19, 20, 21, blue)
    front.rect(12, 22, 17, 23, IVORY)
    # Steel antenna mast (y_model = 8..12 -> v = 8..16) and emitter collar (y_model = 12..13 -> v = 6..8).
    front.rect(10, 8, 22, 16, STEEL_SHADE)
    front.col(13, 8, 16, TRIM)
    front.col(14, 8, 16, STEEL)
    front.col(18, 8, 16, STEEL_DEEP)
    front.rect(10, 6, 22, 8, STEEL)
    front.row(6, 10, 22, TRIM)

    side = Sprite(fill=BASE)
    side.ring(0, 0, 32, 32, SEAM)
    side.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    side.rect(4, 26, 28, 32, SHADE)
    side.row(26, 4, 28, HIGHLIGHT)
    side.rect(8, 16, 24, 26, PORT_METAL)
    side.bevel(8, 16, 24, 26, PORT_LIGHT, SEAM)
    for bx in (10, 20):
        bolt(side, bx, 19)
    side.rect(10, 8, 22, 16, STEEL_SHADE)
    side.col(13, 8, 16, STEEL)
    side.rect(10, 6, 22, 8, STEEL)
    side.row(6, 10, 22, TRIM)

    top = Sprite(fill=BASE)
    top.ring(4, 4, 28, 28, SEAM)
    top.bevel(5, 5, 27, 27, HIGHLIGHT, SHADE)
    # Front pointer at north edge (y = 5..7) + collar ring (10..22).
    top.rect(13, 5, 19, 7, IVORY)
    top.ring(10, 10, 22, 22, SEAM)
    top.bevel(11, 11, 21, 21, TRIM, STEEL_DEEP)
    top.rect(12, 12, 20, 20, STEEL)

    idle = Sprite(fill=scale(blue, STANDBY))
    idle.ring(11, 11, 21, 21, SEAM)
    idle.rect(14, 14, 18, 18, blue)

    active = Sprite(fill=blue)
    active.bevel(11, 11, 21, 21, lighten(blue, .38), scale(blue, .72))
    active.rect(14, 14, 18, 18, lighten(blue, .45))
    active.rect(13, 2, 19, 4, lighten(blue, .38))

    return {
        "wireless_access_point_front": front,
        "wireless_access_point_side": side,
        "wireless_access_point_top": top,
        "wireless_access_point_beacon_idle": idle,
        "wireless_access_point_beacon_active": active,
    }


def access_point_models(look=ACCESS_POINT_LOOK):
    if look not in ACCESS_POINT_LOOKS:
        raise ValueError(f"Unknown Wireless Access Point look: {look}")
    family = Family()
    body = [
        Box("plinth", (2, 0, 2), (14, 3, 14)),
        Box("rear_port", (5, 5, 11), (11, 11, 16)),
        Box("pedestal", (4, 3, 4), (12, 8, 11)),
    ]
    if look == "beacon_mast":
        body += [Box("mast", (6.5, 8, 6.5), (9.5, 12, 9.5)), Box("collar", (5, 12, 5), (11, 13, 11))]
        parts = [Box("beacon", (5.5, 13, 5.5), (10.5, 15.5, 10.5), flat=True)]
    elif look == "dual_array":
        body += [Box("mast_left", (5, 8, 6.5), (7, 12, 9.5)), Box("mast_right", (9, 8, 6.5), (11, 12, 9.5)),
                 Box("collar", (4.5, 12, 5.5), (11.5, 13, 10.5))]
        parts = [Box("beacon", (5.5, 13, 6), (10.5, 15, 10), flat=True)]
    else:
        body += [Box("collar", (4.5, 8, 4.5), (11.5, 9.5, 10.5))]
        parts = [Box("beacon", (5.5, 9.5, 5.5), (10.5, 13.5, 9.5), flat=True)]

    def body_skin(box, face):
        if face == "down" or (box.name == "rear_port" and face == "south"):
            return "port"
        if face in ("up",):
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
        lit = [Box(box.name, box.lo, box.hi, emissive=emissive, flat=True) for box in parts]
        part_name = f"wireless_access_point_{state}_parts"
        shown = family.model(part_name, "part", {"beacon": f"wireless_access_point_beacon_{state}"},
                             lit, lambda box, face: "beacon", context=body + lit)
        family.compose(f"wireless_access_point {state}", drawn + shown)
        part_names.append(part_name)
    family.machine("wireless_access_point", part_names)
    return family, access_point_sprites()


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


def generate(check):
    out = GeneratedFiles(ROOT, check)
    for name in ("parallel_upgrade", "pressure_module", "configuration_card",
                 "wireless_booster", "wireless_matter_terminal", "energy_core", "portable_energy_cell",
                 "pressurized_canister", "spectrometer_lens"):
        out.json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"rematter:item/{name}"}})
        out.json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"rematter:item/{name}"}})
        if name == "parallel_upgrade":
            continue  # Its recipe belongs to the industrial recipe generator.
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
                "B": "rematter:blue_matrix_crystal", "E": "rematter:ender_pearl_powder",
                "C": "rematter:control_circuit", "P": "minecraft:copper_ingot"}, count=2)
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

    for block_name, builder, pattern, keys in (
            ("wireless_access_point", access_point_models, [" B ", "OFO", "ECE"], {
                "B": "rematter:blue_matrix_crystal", "O": "rematter:optical_circuit",
                "F": "rematter:machine_frame", "E": "rematter:ender_conductor",
                "C": "rematter:reinforced_matter_cable"}),
            ("matter_level_emitter", level_emitter_models, [" R ", "CFC", "AMA"], {
                "R": "minecraft:redstone_torch", "C": "rematter:control_circuit",
                "F": "rematter:machine_frame", "A": "rematter:matter_cable",
                "M": "rematter:mint_matrix_crystal"}),
    ):
        family, sprites = builder()
        for name, sprite in sprites.items():
            out.png(ASSETS / f"textures/block/{name}.png", sprite.width, sprite.height, sprite.pixel)
        for folder, files in (("models/block", family.models), ("blockstates", family.blockstates), ("items", family.items)):
            for name, value in files.items():
                out.json(ASSETS / folder / f"{name}.json", value)
        recipe(out, block_name, pattern, keys)
        out.json(DATA / f"loot_table/blocks/{block_name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"rematter:{block_name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

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
