"""Regression tests for recipe-book advancement generation and shipped advancement JSONs."""
from __future__ import annotations

import json
from pathlib import Path
import sys
import unittest

TOOLS = Path(__file__).resolve().parents[1]
ROOT = TOOLS.parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

from recipe_discovery import shaped_discovery


class RecipeDiscoveryTest(unittest.TestCase):
    def test_shaped_discovery_emits_tag_string_not_tag_array(self):
        advancement = shaped_discovery(
            "wireless_booster",
            [" E ", "ECE", " B "],
            {
                "E": "#rematter:full_ender_pearl_powders",
                "C": "minecraft:copper_ingot",
                "B": "rematter:blue_matrix_crystal",
            },
        )
        items = advancement["criteria"]["has_material"]["conditions"]["items"]
        by_items = {entry["items"] if isinstance(entry["items"], str) else tuple(entry["items"]): entry["count"]["min"] for entry in items}
        self.assertEqual(by_items["#rematter:full_ender_pearl_powders"], 3)
        self.assertEqual(by_items[("minecraft:copper_ingot",)], 1)
        self.assertEqual(by_items[("rematter:blue_matrix_crystal",)], 1)

    def test_no_shipped_advancement_wraps_tag_in_items_array(self):
        advancements_root = ROOT / "src/main/resources/data/rematter/advancement"
        files = sorted(advancements_root.rglob("*.json"))
        self.assertGreater(len(files), 0)
        offenders = []
        for path in files:
            data = json.loads(path.read_text(encoding="utf-8"))

            def walk(node, loc=""):
                if isinstance(node, dict):
                    val = node.get("items")
                    if isinstance(val, list) and any(isinstance(x, str) and x.startswith("#") for x in val):
                        offenders.append(f"{path.relative_to(ROOT)}:{loc}.items={val!r}")
                    for k, v in node.items():
                        walk(v, f"{loc}.{k}")
                elif isinstance(node, list):
                    for i, v in enumerate(node):
                        walk(v, f"{loc}[{i}]")

            walk(data)
        self.assertEqual(offenders, [], "Tag strings inside an items array fail Minecraft HolderSetCodec parsing")


if __name__ == "__main__":
    unittest.main()
