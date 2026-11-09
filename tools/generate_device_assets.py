#!/usr/bin/env python3
"""Generate specialist machines, their recipes and the two industrial/crystal upgrades."""
from pathlib import Path
import argparse
import copy
import json
from generated_files import GeneratedFiles
from connection_geometry import machine_state_assets

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/rematter"
DATA = ROOT / "src/main/resources/data/rematter"
DEVICES = {
    "separator": ("chemical_reactor", "amber", "amber_matrix_crystal"),
    "thermal_processor": ("electrolyzer", "ember", "ember_matrix_crystal"),
    "crystallizer": ("material_synthesizer", "violet", "violet_matrix_crystal"),
    "air_separator": ("electrolyzer", "ice", "ice_matrix_crystal"),
}


def cube(start, end, texture):
    return {"from": list(start), "to": list(end), "faces": {
        face: {"texture": "#" + texture, "uv": [0, 0, 16, 16]}
        for face in ("north", "south", "east", "west", "up", "down")}}


def recipe(out, name, pattern, keys):
    out.json(DATA / "recipe" / (name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
        "key": keys, "result": {"id": "rematter:" + name, "count": 1}})
    out.json(DATA / "advancement/recipes" / (name + ".json"), {
        "criteria": {"has_material": {"trigger": "minecraft:inventory_changed", "conditions": {
            "items": [{"items": "rematter:machine_frame"}]}}},
        "requirements": [["has_material"]], "rewards": {"recipes": ["rematter:" + name]}})


def generate(check):
    out = GeneratedFiles(ROOT, check)
    for name, (template, color, crystal) in DEVICES.items():
        model = copy.deepcopy(json.loads((ASSETS / f"models/block/{template}.json").read_text()))
        model["textures"]["teal"] = "rematter:block/machine_" + color
        if name == "separator":
            model["elements"] += [cube((4, 10.4, 4), (12, 11, 12), "brushed_steel"),
                                  cube((7.5, 9, 3), (8.5, 13, 13), "teal")]
        elif name == "thermal_processor":
            model["elements"] += [cube((3, 3, 6), (13, 3.5, 10), "teal"),
                                  cube((4, 10, 5), (12, 10.5, 11), "copper_trim")]
        elif name == "crystallizer":
            model["elements"] += [cube((5, 4, 5), (11, 4.5, 11), "teal"),
                                  cube((7, 5, 7), (9, 10, 9), "teal")]
        else:
            model["elements"] += [cube((4, 14.5, 4), (12, 15, 12), "vent"),
                                  cube((6, 13, 6), (10, 14.5, 10), "brushed_steel")]
        out.json(ASSETS / f"models/block/{name}.json", model)
        out.json(ASSETS / f"models/block/{name}_active.json", {
            "parent": f"rematter:block/{name}",
            "textures": {"screen": "rematter:block/display_active", "status": "rematter:block/indicator_active"}})
        for path, asset in machine_state_assets(name, "machine_" + color):
            out.json(ASSETS / path, asset)
        out.json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"rematter:block/{name}"}})
        recipe(out, name, ["IMI", "GFG", "ICI"], {
            "I": "minecraft:iron_ingot", "M": "rematter:" + crystal, "G": "minecraft:glass",
            "F": "rematter:machine_frame", "C": "rematter:control_circuit"})
    for name, accent in [("parallel_upgrade", (106, 186, 208)), ("pressure_module", (186, 133, 220))]:
        def pixel(x, y, accent=accent, parallel=name == "parallel_upgrade"):
            if not (3 <= x < 29 and 3 <= y < 29): return (0, 0, 0, 0)
            if x in (3, 28) or y in (3, 28): return (143, 153, 157, 255)
            lit = (x // 6) % 2 == 0 and 8 <= y <= 23 if parallel else abs(x - 16) + abs(y - 16) < 11
            return (*accent, 255) if lit else (37, 44, 49, 255)
        out.png(ASSETS / f"textures/item/{name}.png", 32, 32, pixel)
        out.json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"rematter:item/{name}"}})
        out.json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"rematter:item/{name}"}})
        recipe(out, name, ["RLR", "OCO", "RLR"], {
            "R": "rematter:rhevon_ingot", "L": "rematter:diamond_lattice",
            "O": "rematter:optical_circuit", "C": "rematter:control_circuit"})
    print(f"Verified {out.count} specialist device files" if check else f"Generated {out.count} specialist device files")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    generate(parser.parse_args().check)
