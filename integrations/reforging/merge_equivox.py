#!/usr/bin/env python3
"""Prepare targeted Equivox exclusions in a new file; never modify the input."""

import argparse
import copy
import json
from pathlib import Path


MACHINE_TAG = Path(__file__).parent / "datapack/data/reforging/tags/item/rematter/stateful_machines.json"


def protected_items():
    with MACHINE_TAG.open(encoding="utf-8") as stream:
        tag = json.load(stream)
    return tuple(entry["id"] for entry in tag["values"])


def merge_exclusions(document):
    if not isinstance(document, dict) or not isinstance(document.get("entries"), list):
        raise ValueError("Expected an Equivox object with an entries array")
    for entry in document["entries"]:
        if (not isinstance(entry, dict) or not isinstance(entry.get("id"), str)
                or not isinstance(entry.get("emc"), int) or isinstance(entry.get("emc"), bool)):
            raise ValueError("Every EMC entry must contain a string id and an integer emc value")
    result = copy.deepcopy(document)
    protected = protected_items()
    found = set()
    for entry in result["entries"]:
        if entry["id"] in protected:
            entry["emc"] = 0
            found.add(entry["id"])
    result["entries"].extend({"id": item, "emc": 0} for item in protected if item not in found)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Existing config/Equivox/custom_emc.json")
    parser.add_argument("destination", type=Path, help="New file to review before installation")
    args = parser.parse_args()
    if args.source.resolve() == args.destination.resolve():
        parser.error("Choose a different destination; the input is never modified")
    if args.destination.exists():
        parser.error("The destination already exists; choose a new file")
    try:
        with args.source.open(encoding="utf-8") as stream:
            result = merge_exclusions(json.load(stream))
        with args.destination.open("x", encoding="utf-8") as stream:
            json.dump(result, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print("Prepared exclusions for eight ReMatter machines; review the new file before installation.")


if __name__ == "__main__":
    main()
