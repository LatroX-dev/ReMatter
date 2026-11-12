"""Original crystal accent tiles for ReMatter's distinct machine families."""

from pathlib import Path
import json
from material_tiles import TILE, crystal_pixel, write_png


FAMILY = {
    "analyzer": "ice",
    "decomposer": "copper",
    "material_synthesizer": "leaf",
    "chemical_reactor": "violet",
    "industrial_reactor": "violet",
    "material_preparator": "amber",
    "industrial_preparator": "amber",
    "sinter_furnace": "ember",
    "matter_drive": "mint",
    "matter_terminal": "mint",
    "network_interface": "blue",
}

RGB = {
    "ice": (215, 209, 188),  # Silith
    "copper": (220, 137, 79),
    "leaf": (125, 210, 127),
    "blue": (103, 148, 241),
    "violet": (179, 129, 232),
    "amber": (227, 169, 75),
    "ember": (234, 72, 72),
    "mint": (87, 205, 219),  # Diamond powder
}

# Hardware surfaces stay neutral. Family colors belong to the functional
# insets and contacts, never to the entire machine or component silhouette.
CHASSIS = (57, 63, 65)
RECESS = (30, 37, 39)
MATTE_TRIM = (90, 103, 108)
COPPER_CONTACT = (174, 111, 73)


def hex_color(rgb):
    return "#" + bytes(rgb).hex()


def write_tiles(root, write=write_png):
    for family, rgb in RGB.items():
        for folder, emitting in (("block", False), ("entity", True)):
            path = root / "textures" / folder / f"machine_{family}.png"
            write(path, TILE, TILE, lambda x, y: crystal_pixel(rgb, x, y, emitting=emitting))


def apply_models(root):
    for name in (*FAMILY, "hand_preparator"):
        paths = [root / "models/block" / f"{name}.json"]
        item = root / "models/item" / f"{name}.json"
        if item.is_file():
            paths.append(item)
        if name == "sinter_furnace":
            paths.append(root / "models/block/sinter_furnace_active.json")
        for path in paths:
            model = json.loads(path.read_text())
            textures = model["textures"]
            if name in FAMILY and "teal" in textures:
                textures["teal"] = "rematter:block/machine_" + FAMILY[name]
            if name == "hand_preparator" and "wood" in textures:
                textures["wood"] = "rematter:block/brushed_steel"
            path.write_text(json.dumps(model, indent=2) + "\n")


if __name__ == "__main__":
    assets = Path(__file__).resolve().parents[1] / "src/main/resources/assets/rematter"
    write_tiles(assets)
    apply_models(assets)
