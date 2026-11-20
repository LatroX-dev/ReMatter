#!/usr/bin/env python3
"""Generate inventory models for the authored workshop and crystal sprites."""

import argparse
import json
from pathlib import Path
import struct

from machine_palette import RGB


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/rematter"
MODELED_ITEMS = (
    "diamond_powder", "lapis_dust", "amethyst_dust", "emerald_dust",
    "ender_pearl_powder", "netherite_powder",
    "diamond_matrix_blend", "ender_conductor_blend", "netherite_sheath_blend",
    "diamond_lattice", "ender_conductor", "netherite_sheath",
    *(f"{family}_matrix_crystal" for family in RGB),
)
OTHER_SPRITES = (
    "silith", "argil", "rhedon", "virel", "lumin", "matrix_blend",
    "iron_dust", "copper_dust", "gold_dust", "rhevon_dust", "lumivex_dust", "virelite_blend",
    "rhevon_ingot", "lumivex_ingot", "virelite_ceramic", "matrix_crystal", "matrix_cluster",
)


def outputs():
    for name in MODELED_ITEMS:
        reference = f"rematter:item/{name}"
        yield ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": reference}}
        yield ASSETS / f"models/item/{name}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": reference},
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for path, model in outputs():
        content = json.dumps(model, indent=2) + "\n"
        if args.check:
            if path.read_text() != content:
                raise ValueError(f"Generated model differs: {path.relative_to(ROOT)}")
        else:
            path.write_text(content)
    for name in (*MODELED_ITEMS, *OTHER_SPRITES):
        path = ASSETS / f"textures/item/{name}.png"
        header = path.read_bytes()[:26]
        if (header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR"
                or struct.unpack(">IIBB", header[16:26]) != (128, 128, 8, 6)):
            raise ValueError(f"Expected an authored 128px RGBA sprite: {name}")


if __name__ == "__main__":
    main()
