#!/usr/bin/env python3
"""Generate the current upgrade models and recipes. Icons are authored in Blockbench."""
from pathlib import Path
import argparse
from generated_files import GeneratedFiles

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/rematter"
DATA = ROOT / "src/main/resources/data/rematter"


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
    for name in ("parallel_upgrade", "pressure_module"):
        out.json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"rematter:item/{name}"}})
        out.json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"rematter:item/{name}"}})
        if name == "parallel_upgrade":
            continue  # Its recipe belongs to the industrial workshop generator.
        recipe(out, name, ["RLR", "OCO", "RLR"], {
            "R": "rematter:rhevon_ingot", "L": "rematter:diamond_lattice",
            "O": "rematter:optical_circuit", "C": "rematter:control_circuit"})
    print(f"Verified {out.count} upgrade files" if check else f"Generated {out.count} upgrade files")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    generate(parser.parse_args().check)
