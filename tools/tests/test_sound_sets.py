"""Sound sets: the active selection resolves completely, earlier sets stay restorable, and mixing works."""
import hashlib
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

import generate_sound_assets as gsa  # noqa: E402


def all_sets():
    return {p.name: gsa.load_set(p.name) for p in sorted(gsa.SETS.iterdir()) if (p / "set.json").is_file()}


ARCHIVE_ROLES = (
    "sinter", "analyzer", "decomposer", "synthesizer", "processor", "chemical_reactor",
    "hand_preparator", "preparator", "preparation_reactor", "generator", "converter", "atmospheric",
)


class SoundSetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sets = all_sets()

    def test_both_sets_are_present_and_every_clip_matches_its_recorded_checksum(self):
        self.assertIn("studio-v10", self.sets)
        self.assertIn("workshop-v11", self.sets)
        self.assertIn("workshop-v12", self.sets)
        self.assertIn("workshop-v13", self.sets)
        self.assertIn("workshop-v14", self.sets)
        for set_id, data in self.sets.items():
            on_disk = {p.relative_to(gsa.SETS / set_id / "clips").with_suffix("").as_posix()
                       for p in (gsa.SETS / set_id / "clips").rglob("*.ogg")}
            self.assertEqual(on_disk, set(data["clips"]), f"{set_id}: clips on disk and in set.json differ")
            for clip, info in data["clips"].items():
                digest = hashlib.sha256((gsa.SETS / set_id / "clips" / f"{clip}.ogg").read_bytes()).hexdigest()
                self.assertEqual(digest, info["sha256"], f"{set_id}/{clip}")

    def test_the_archived_set_is_the_complete_earlier_set(self):
        archived = self.sets["studio-v10"]
        self.assertEqual(len(archived["clips"]), 71)
        self.assertEqual(len(archived["events"]), 35)
        for role in ARCHIVE_ROLES:
            self.assertIn(f"{role}_work", archived["events"])
            self.assertIn(f"{role}_accent", archived["events"])

    def test_every_clip_is_mono_44100(self):
        for set_id, data in self.sets.items():
            for clip in data["clips"]:
                self.assertEqual(gsa.ogg_format(gsa.SETS / set_id / "clips" / f"{clip}.ogg"), (1, 44100), f"{set_id}/{clip}")

    def test_active_selection_gives_every_played_event_a_sound_and_every_machine_a_profile(self):
        chosen, machines = gsa.resolve(gsa.ACTIVE_SET, gsa.ROLE_OVERRIDES, gsa.EVENT_OVERRIDES, self.sets)
        for event in gsa.INTERACTION_EVENTS:
            self.assertIn(event, chosen)
        self.assertEqual(set(machines), set(gsa.MACHINE_KINDS))
        for kind, profile in machines.items():
            self.assertIn(profile["loop"]["event"].removeprefix("rematter:"), chosen, kind)
            for cue in profile.get("cues", []):
                self.assertIn(cue["event"].removeprefix("rematter:"), chosen, kind)
                self.assertGreater(cue["period"], 0)
                self.assertTrue(0.0 <= cue["phase"] < 1.0)
        manifest = gsa.sounds_manifest(chosen)
        self.assertEqual(list(manifest), gsa.registered_events(), "every registered event is declared, in a fixed order")
        for event, entry in manifest.items():
            self.assertEqual(bool(entry["sounds"]), event in chosen, event)
            for sound in entry["sounds"]:
                self.assertRegex(sound["name"], r"^rematter:[a-z0-9.-]+/(machine|tool)/[a-z0-9_]+$", event)
        # an event that is declared silent is never named by a machine profile or played by an interaction
        silent = {event for event, entry in manifest.items() if not entry["sounds"]}
        self.assertFalse(silent & set(gsa.INTERACTION_EVENTS))
        for kind, profile in machines.items():
            used = [profile["loop"]["event"], *(cue["event"] for cue in profile.get("cues", []))]
            if "accent" in profile:
                used.append(profile["accent"]["event"])
            self.assertFalse(silent & {name.removeprefix("rematter:") for name in used}, kind)

    def test_going_back_restores_the_earlier_sounds_and_the_earlier_behaviour(self):
        chosen, machines = gsa.resolve("studio-v10", {}, {}, self.sets)
        for role in ARCHIVE_ROLES:
            self.assertEqual(chosen[f"{role}_work"][0], "studio-v10")
            self.assertEqual(chosen[f"{role}_accent"][0], "studio-v10")
            self.assertNotIn(f"{role}_cycle", chosen, "the earlier set has no motion cues")
        for kind, profile in machines.items():
            self.assertEqual(profile["cues"], [], kind)
            self.assertEqual(profile["accent"]["jitter"], 6, kind)
            self.assertEqual(profile["position"], "block", kind)
        self.assertEqual(machines["preparation_reactor_controller"]["loop"]["pitch"], 0.88)
        # interactions that had no sound of their own then play what their call site used at the time
        self.assertEqual(chosen["wrench_rotate"][1], self.sets["studio-v10"]["events"]["wrench_configure"])
        self.assertEqual(chosen["canister_empty"][1], self.sets["studio-v10"]["events"]["cell_insert"])
        # and the two that had none at all keep the newest one, so nothing is silent or vanilla
        self.assertEqual(chosen["structure_formed"][0], "workshop-v14")
        self.assertEqual(chosen["synthesizer_complete"][0], "workshop-v14")

    def test_one_machine_or_one_interaction_can_come_from_another_set(self):
        chosen, machines = gsa.resolve("workshop-v11", {"generator": "studio-v10"}, {"wrench_configure": "studio-v10"}, self.sets)
        self.assertEqual(chosen["generator_work"][0], "studio-v10")
        self.assertEqual(machines["crystal_generator"]["set"], "studio-v10")
        self.assertEqual(machines["industrial_crystal_generator"]["loop"]["pitch"], 0.9)
        self.assertEqual(machines["analyzer"]["set"], "workshop-v11")
        self.assertEqual(chosen["wrench_configure"][0], "studio-v10")
        self.assertEqual(chosen["wrench_rotate"][0], "workshop-v11")

    def test_unknown_override_keys_are_rejected(self):
        with self.assertRaises(ValueError):
            gsa.resolve("workshop-v11", {"toaster": "studio-v10"}, {}, self.sets)
        with self.assertRaises(ValueError):
            gsa.resolve("workshop-v11", {}, {"analyzer_work": "studio-v10"}, self.sets)

    def test_installed_files_match_the_record(self):
        record = json.loads(gsa.RECORD.read_text(encoding="utf-8"))
        installed = {p.relative_to(gsa.SOUNDS_ROOT).with_suffix("").as_posix() for p in gsa.SOUNDS_ROOT.rglob("*.ogg")}
        self.assertEqual(installed, set(record["clips"]))
        for name, info in record["clips"].items():
            digest = hashlib.sha256((gsa.SOUNDS_ROOT / f"{name}.ogg").read_bytes()).hexdigest()
            self.assertEqual(digest, info["sha256"], name)

    def test_loops_of_the_new_set_are_seamless_audible_and_free_of_whistles(self):
        for set_id in ("workshop-v11", "workshop-v12", "workshop-v13", "workshop-v14"):
            for clip, info in self.sets[set_id]["clips"].items():
                self.assertEqual(gsa.check_limits(set_id, clip, info), [], f"{set_id}/{clip}")


if __name__ == "__main__":
    unittest.main()
