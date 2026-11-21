#!/usr/bin/env python3
"""Install the active ReMatter sound set and verify what is installed.

Sounds are kept as sets under artwork/sound-sets/<set>/ (clips/**.ogg plus set.json with the events, the machine
audio profile and a per-clip record). This tool copies the clips the game needs into the resource folder and
writes the three files that describe them:

  src/main/resources/assets/rematter/sounds/<set>/**.ogg   the clips in use
  src/main/resources/assets/rematter/sounds.json           sound events -> clips
  src/main/resources/assets/rematter/machine_audio.json    how each machine plays its loop, motion cues and accents
  artwork/sound-rendered.json                              what was installed, with checksums

Going back to an earlier set, or mixing sets, is a change of the three constants below followed by a run:

  ACTIVE_SET      the set everything comes from
  ROLE_OVERRIDES  machine role -> set (loop, motion cues, accents and engine behaviour of that machine together)
  EVENT_OVERRIDES tool or interaction event -> set

  python3 tools/generate_sound_assets.py            install
  python3 tools/generate_sound_assets.py --check    verify, change nothing (plain Python, no audio libraries)
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import struct
from pathlib import Path

from generated_files import GeneratedFiles

ROOT = Path(__file__).resolve().parents[1]
SETS = ROOT / "artwork/sound-sets"
ASSETS = ROOT / "src/main/resources/assets/rematter"
SOUNDS_ROOT = ASSETS / "sounds"
SOUNDS_JSON = ASSETS / "sounds.json"
MACHINE_AUDIO_JSON = ASSETS / "machine_audio.json"
RECORD = ROOT / "artwork/sound-rendered.json"
MOD_SOUNDS = ROOT / "src/main/java/dev/latrox/rematter/content/ModSounds.java"

# ---------------------------------------------------------------------------------------------------------------
# The switch. "studio-v10" is the set that shipped until 2026-11-17; "workshop-v11" follows the visible mechanisms.
ACTIVE_SET = "workshop-v11"
ROLE_OVERRIDES: dict[str, str] = {}      # for example {"generator": "studio-v10"}
EVENT_OVERRIDES: dict[str, str] = {}     # for example {"wrench_configure": "studio-v10"}
# ---------------------------------------------------------------------------------------------------------------

# Newest first: an interaction event that the chosen set neither defines nor aliases comes from the first set here
# that has it, so no registered event is ever without a sound.
FALLBACK_ORDER = ("workshop-v11", "studio-v10")

MACHINE_ROLES = ("sinter", "analyzer", "decomposer", "synthesizer", "processor", "chemical_reactor", "hand_preparator",
                 "preparator", "preparation_reactor", "generator", "converter", "atmospheric")
ROLE_EVENT_SUFFIXES = ("work", "accent", "cycle")
INTERACTION_EVENTS = (
    "wrench_configure", "wrench_rotate", "wrench_mode", "wrench_dismantle", "port_cycle",
    "config_card_read", "config_card_write", "config_card_clear",
    "cell_insert", "cell_extract", "canister_fill", "canister_empty", "cell_transfer",
    "hand_crank", "analyzer_complete", "synthesizer_complete",
    "entangled_pair", "wireless_link", "terminal_order", "energy_cell_on", "energy_cell_off",
    "structure_formed", "action_denied",
)
MACHINE_KINDS = {
    "analyzer": "analyzer", "decomposer": "decomposer", "material_synthesizer": "synthesizer",
    "chemical_processor": "processor", "chemical_reactor_controller": "chemical_reactor",
    "industrial_decomposer_controller": "chemical_reactor",
    "industrial_synthesizer_controller": "chemical_reactor",
    "hand_preparator": "hand_preparator", "material_preparator": "preparator",
    "preparation_reactor_controller": "preparation_reactor", "sinter_furnace": "sinter",
    "crystal_generator": "generator", "industrial_crystal_generator": "generator",
    "primal_converter": "converter", "atmospheric_collector": "atmospheric",
}
PROFILE_ALIASES = {
    "industrial_decomposer_controller": "chemical_reactor_controller",
    "industrial_synthesizer_controller": "chemical_reactor_controller",
}

# Limits for clips that carry measurements in their set record (the earlier set carries only a few numbers).
LOOP_LIMITS = {"seam_jump_ratio": (None, 2.5), "env_dip_db": (-12.0, None), "whine_db": (None, 12.0),
               "lufs": (-34.0, -24.0), "a_weight_deficit_db": (None, 13.0)}
ALL_LIMITS = {"peak": (None, 0.95)}


def registered_events() -> list[str]:
    """Every sound event the game registers, in a fixed order."""
    names = [f"{role}_{suffix}" for role in MACHINE_ROLES for suffix in ROLE_EVENT_SUFFIXES]
    return names + list(INTERACTION_EVENTS)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_set(set_id: str, sets_root: Path = SETS) -> dict:
    path = sets_root / set_id / "set.json"
    if not path.is_file():
        raise ValueError(f"Unknown sound set '{set_id}' (no {path.relative_to(sets_root.parent.parent) if sets_root == SETS else path})")
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("id") != set_id:
        raise ValueError(f"Sound set folder '{set_id}' declares id '{data.get('id')}'")
    return data


def ogg_format(path: Path) -> tuple[int, int]:
    """(channels, sample rate) from the Vorbis identification header of an Ogg file."""
    head = path.read_bytes()[:128]
    if head[:4] != b"OggS":
        raise ValueError(f"Not an Ogg file: {path.name}")
    at = head.find(b"\x01vorbis")
    if at < 0:
        raise ValueError(f"No Vorbis header in {path.name}")
    channels = head[at + 11]
    (rate,) = struct.unpack_from("<I", head, at + 12)
    return channels, rate


def role_of_event(event: str) -> str | None:
    for role in MACHINE_ROLES:
        for suffix in ROLE_EVENT_SUFFIXES:
            if event == f"{role}_{suffix}":
                return role
    return None


def resolve(active: str, role_overrides: dict[str, str], event_overrides: dict[str, str],
            sets: dict[str, dict]) -> tuple[dict[str, tuple[str, dict]], dict[str, dict]]:
    """Decide for every registered event which set defines it, and for every machine kind which profile applies.
    Returns ({event: (set id, event spec)}, {kind: profile})."""
    unknown_roles = set(role_overrides) - set(MACHINE_ROLES)
    unknown_events = set(event_overrides) - set(INTERACTION_EVENTS)
    if unknown_roles or unknown_events:
        raise ValueError(f"Unknown override keys: {sorted(unknown_roles | unknown_events)}")
    chosen: dict[str, tuple[str, dict]] = {}
    for event in registered_events():
        role = role_of_event(event)
        if role is not None:
            set_id = role_overrides.get(role, active)
            spec = sets[set_id]["events"].get(event)
            if spec is not None:        # a machine event that its set does not use is simply not played
                chosen[event] = (set_id, spec)
            continue
        set_id = event_overrides.get(event, active)
        data = sets[set_id]
        if event in data["events"]:
            chosen[event] = (set_id, data["events"][event])
        elif event in data.get("aliases", {}):
            target = data["aliases"][event]
            chosen[event] = (set_id, data["events"][target])
        else:
            for fallback in FALLBACK_ORDER:
                if event in sets[fallback]["events"]:
                    chosen[event] = (fallback, sets[fallback]["events"][event])
                    break
            else:
                raise ValueError(f"No sound set defines the event '{event}'")
    machines: dict[str, dict] = {}
    for kind, role in MACHINE_KINDS.items():
        set_id = role_overrides.get(role, active)
        table = sets[set_id]["machine_audio"]
        profile = dict(table.get(kind) or table[PROFILE_ALIASES[kind]])
        profile["set"] = set_id
        for block in [profile.get("loop"), profile.get("accent"), *profile.get("cues", [])]:
            if block and block["event"].removeprefix("rematter:") not in chosen:
                raise ValueError(f"Machine '{kind}' in set '{set_id}' uses the event {block['event']}, which has no sounds")
        machines[kind] = profile
    return chosen, machines


def sounds_manifest(chosen: dict[str, tuple[str, dict]]) -> dict[str, object]:
    """sounds.json for the selection. Every registered event gets an entry: the game logs a warning at start for
    a registered event that sounds.json does not mention, so events the selection does not use (an earlier set's
    accents, another set's motion cues) are declared with an empty list. No machine profile names such an event."""
    manifest: dict[str, object] = {}
    for event in registered_events():
        if event not in chosen:
            manifest[event] = {"sounds": []}
            continue
        set_id, spec = chosen[event]
        entry: dict[str, object] = {}
        if "subtitle" in spec:
            entry["subtitle"] = spec["subtitle"]
        sounds = []
        for sound in spec["sounds"]:
            item: dict[str, object] = {
                "name": f"rematter:{set_id}/{sound['clip']}",
                "volume": sound.get("volume", 1.0),
                "pitch": sound.get("pitch", 1.0),
                "attenuation_distance": sound.get("attenuation_distance", 16),
            }
            if sound.get("weight", 1) != 1:
                item["weight"] = sound["weight"]
            sounds.append(item)
        if not sounds:
            raise ValueError(f"Event '{event}' in set '{set_id}' has no sounds")
        entry["sounds"] = sounds
        manifest[event] = entry
    return manifest


def needed_clips(chosen: dict[str, tuple[str, dict]]) -> list[tuple[str, str]]:
    return sorted({(set_id, sound["clip"]) for set_id, spec in chosen.values() for sound in spec["sounds"]})


def check_limits(set_id: str, clip: str, record: dict) -> list[str]:
    metrics = record.get("metrics", {})
    problems = []
    limits = dict(ALL_LIMITS)
    if record.get("kind") == "loop":
        limits.update(LOOP_LIMITS)
    for key, (low, high) in limits.items():
        if key not in metrics:
            continue
        value = metrics[key]
        if low is not None and value < low or high is not None and value > high:
            problems.append(f"{set_id}/{clip}: {key} = {value} outside [{low}, {high}]")
    return problems


def java_registered_events() -> set[str] | None:
    if not MOD_SOUNDS.is_file():
        return None
    return set(re.findall(r'register\("([a-z0-9_]+)"\)', MOD_SOUNDS.read_text(encoding="utf-8")))


def generate(check: bool = False) -> None:
    set_ids = sorted(p.name for p in SETS.iterdir() if (p / "set.json").is_file())
    sets = {set_id: load_set(set_id) for set_id in set_ids}
    for required in {ACTIVE_SET, *ROLE_OVERRIDES.values(), *EVENT_OVERRIDES.values(), *FALLBACK_ORDER}:
        if required not in sets:
            raise ValueError(f"Sound set '{required}' is not in artwork/sound-sets")
    chosen, machines = resolve(ACTIVE_SET, ROLE_OVERRIDES, EVENT_OVERRIDES, sets)

    java_events = java_registered_events()
    if java_events is not None and java_events != set(registered_events()):
        only_java = sorted(java_events - set(registered_events()))
        only_tool = sorted(set(registered_events()) - java_events)
        raise ValueError(f"ModSounds.java and this tool disagree on the sound events: only in Java {only_java}, only here {only_tool}")

    clips = needed_clips(chosen)
    problems: list[str] = []
    record_clips: dict[str, object] = {}
    for set_id, clip in clips:
        source = SETS / set_id / "clips" / f"{clip}.ogg"
        info = sets[set_id]["clips"].get(clip)
        if info is None or not source.is_file():
            raise ValueError(f"Set '{set_id}' references the clip '{clip}' but does not contain it")
        digest = sha256(source)
        if digest != info["sha256"]:
            raise ValueError(f"Clip {set_id}/{clip} does not match the checksum in its set record")
        channels, rate = ogg_format(source)
        if channels != 1 or rate != 44100:
            problems.append(f"{set_id}/{clip}: {channels} channel(s) at {rate} Hz; positional sounds must be mono 44100 Hz")
        problems.extend(check_limits(set_id, clip, info))
        record_clips[f"{set_id}/{clip}"] = {"sha256": digest, "bytes": source.stat().st_size}
    if problems:
        raise ValueError("Sound clips outside their limits:\n  " + "\n  ".join(problems))

    expected_files = {SOUNDS_ROOT / set_id / f"{clip}.ogg": SETS / set_id / "clips" / f"{clip}.ogg" for set_id, clip in clips}
    present = set(SOUNDS_ROOT.rglob("*.ogg")) if SOUNDS_ROOT.exists() else set()
    if check:
        missing = sorted(str(p.relative_to(ROOT)) for p in set(expected_files) - present)
        extra = sorted(str(p.relative_to(ROOT)) for p in present - set(expected_files))
        if missing or extra:
            raise ValueError(f"Installed sound clips differ from the active selection; run generate_sound_assets.py. "
                             f"Missing: {missing[:5]} ({len(missing)}), unexpected: {extra[:5]} ({len(extra)})")
        for target, source in expected_files.items():
            if target.read_bytes() != source.read_bytes():
                raise ValueError(f"Installed clip differs from its set: {target.relative_to(ROOT)}")
    else:
        for stale in present - set(expected_files):
            stale.unlink()
        for target, source in expected_files.items():
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.is_file() or target.read_bytes() != source.read_bytes():
                shutil.copyfile(source, target)
        for folder in sorted((p for p in SOUNDS_ROOT.rglob("*") if p.is_dir()), reverse=True):
            if not any(folder.iterdir()):
                folder.rmdir()

    out = GeneratedFiles(ROOT, check)
    out.json(SOUNDS_JSON, sounds_manifest(chosen))
    out.json(MACHINE_AUDIO_JSON, {"format": 1, "active_set": ACTIVE_SET, "machines": machines})
    out.json(RECORD, {
        "active_set": ACTIVE_SET,
        "role_overrides": ROLE_OVERRIDES,
        "event_overrides": EVENT_OVERRIDES,
        "events": {event: set_id for event, (set_id, _) in chosen.items()},
        "clips": record_clips,
    })
    used_sets = sorted({set_id for set_id, _ in clips})
    print(f"{'Verified' if check else 'Installed'} {len(chosen)} sound events and {len(clips)} clips from {', '.join(used_sets)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="verify the installed files without changing anything")
    generate(check=parser.parse_args().check)
