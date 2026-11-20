import json
from pathlib import Path
import tempfile
import unittest

from tools import audit_crystal_fuel


class CrystalFuelAuditToolTest(unittest.TestCase):
    def test_standalone_and_reforging_overlays_pass(self):
        standalone = audit_crystal_fuel.audit_roots("standalone", [audit_crystal_fuel.BASE_DATA])
        self.assertEqual(9, standalone["enabled_count"])
        self.assertEqual(0, standalone["disabled_count"])
        self.assertEqual(9, len({row["fuel_energy"] for row in standalone["fuels"]}))
        reforging = audit_crystal_fuel.audit_roots(
            "reforging",
            [audit_crystal_fuel.BASE_DATA, audit_crystal_fuel.REFORGING_PACK],
        )
        self.assertEqual(9, reforging["enabled_count"])
        self.assertEqual(0, reforging["disabled_count"])
        self.assertEqual([row["fuel_energy"] for row in standalone["fuels"]], [row["fuel_energy"] for row in reforging["fuels"]])

    def test_overlay_rejects_excessive_fuel_yield(self):
        with tempfile.TemporaryDirectory() as tmp:
            overlay = Path(tmp)
            fuel_file = overlay / "data/rematter/rematter/crystal_fuel/matrix_crystal.json"
            fuel_file.parent.mkdir(parents=True)
            fuel_file.write_text(json.dumps({
                "schema_version": 1,
                "item": "rematter:matrix_crystal",
                "count": 1,
                "energy": 3000,
                "enabled": True,
            }))
            with self.assertRaisesRegex(ValueError, "powered preparation floor"):
                audit_crystal_fuel.audit_roots("unsafe_price", [audit_crystal_fuel.BASE_DATA, overlay])

    def test_overlay_rejects_unauthorized_core_recipe(self):
        with tempfile.TemporaryDirectory() as tmp:
            overlay = Path(tmp)
            recipe_file = overlay / "data/custom/recipe/free_core.json"
            recipe_file.parent.mkdir(parents=True)
            recipe_file.write_text(json.dumps({
                "type": "minecraft:crafting_shapeless",
                "ingredients": ["minecraft:cobblestone"],
                "result": {"id": "rematter:matrix_crystal", "count": 2},
            }))
            with self.assertRaisesRegex(ValueError, "Matrix crystal producer mismatch"):
                audit_crystal_fuel.audit_roots("unsafe_recipe", [audit_crystal_fuel.BASE_DATA, overlay])

    def test_overlay_rejects_unapproved_fuel_item(self):
        with tempfile.TemporaryDirectory() as tmp:
            overlay = Path(tmp)
            fuel_file = overlay / "data/rematter/rematter/crystal_fuel/coal.json"
            fuel_file.parent.mkdir(parents=True)
            fuel_file.write_text(json.dumps({
                "schema_version": 1,
                "item": "minecraft:coal",
                "count": 1,
                "energy": 512,
                "enabled": True,
            }))
            with self.assertRaisesRegex(ValueError, "Unapproved crystal fuel item"):
                audit_crystal_fuel.audit_roots("unapproved_fuel", [audit_crystal_fuel.BASE_DATA, overlay])


if __name__ == "__main__":
    unittest.main()
