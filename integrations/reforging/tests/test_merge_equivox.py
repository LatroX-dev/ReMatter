import copy
import importlib.util
import unittest
from pathlib import Path


SPEC = importlib.util.spec_from_file_location("merge_equivox", Path(__file__).parents[1] / "merge_equivox.py")
MERGER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MERGER)


class EquivoxMergeTest(unittest.TestCase):
    def test_existing_values_and_unrelated_fields_are_preserved(self):
        document = {"comment": "Pack rules", "extra": {"revision": 7}, "entries": [
            {"id": "minecraft:sand", "emc": 1},
            {"id": "example:protected_goal", "emc": 0},
            {"id": "example:corrected_material", "emc": 47, "note": "retain"},
        ]}
        original = copy.deepcopy(document)
        result = MERGER.merge_exclusions(document)
        self.assertEqual(original, document)
        self.assertEqual(original["entries"], result["entries"][:3])
        self.assertEqual(original["comment"], result["comment"])
        self.assertEqual(original["extra"], result["extra"])

    def test_existing_machine_entries_are_zeroed_without_discarding_metadata(self):
        item = MERGER.protected_items()[0]
        result = MERGER.merge_exclusions({"entries": [{"id": item, "emc": 400, "note": "keep"}]})
        self.assertEqual({"id": item, "emc": 0, "note": "keep"}, result["entries"][0])
        self.assertEqual(18, len(result["entries"]))

    def test_only_exact_stateful_item_ids_are_added(self):
        result = MERGER.merge_exclusions({"entries": []})
        self.assertEqual(18, len(result["entries"]))
        self.assertEqual(set(MERGER.protected_items()), {entry["id"] for entry in result["entries"]})
        self.assertTrue(all(entry["emc"] == 0 for entry in result["entries"]))
        self.assertTrue({"rematter:hand_preparator", "rematter:industrial_preparator", "rematter:material_preparator", "rematter:matter_drive", "rematter:matter_cell_64k", "rematter:matter_cell_256k", "rematter:matter_cell_1m", "rematter:network_interface", "rematter:recovered_matter", "rematter:automation_pattern"}.issubset(MERGER.protected_items()))
        self.assertNotIn("rematter:matrix_crystal", MERGER.protected_items())
        self.assertNotIn("rematter:matter_pipe", MERGER.protected_items())
        self.assertNotIn("rematter:guide", MERGER.protected_items())

    def test_repeated_merge_is_idempotent_and_conflicting_duplicate_machine_values_are_zeroed(self):
        item = MERGER.protected_items()[0]
        result = MERGER.merge_exclusions({"entries": [{"id": item, "emc": 400}, {"id": item, "emc": 800}]})
        self.assertEqual(result, MERGER.merge_exclusions(result))
        self.assertEqual([0, 0], [entry["emc"] for entry in result["entries"] if entry["id"] == item])

    def test_malformed_documents_are_rejected_before_writing(self):
        for document in ([], {}, {"entries": {}}, {"entries": [{"id": "example:a", "emc": "12"}]},
                         {"entries": [{"id": "example:a", "emc": True}]}):
            with self.subTest(document=document), self.assertRaises(ValueError):
                MERGER.merge_exclusions(document)


if __name__ == "__main__":
    unittest.main()
