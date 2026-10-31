#!/usr/bin/env python3
"""Generate chemistry JSON only from the reviewed exact-ID material catalogue.

This tool does not read installed mods, discover recipes, infer chemistry from
tags, or grant synthesis to new IDs. Edit the catalogue deliberately first.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ID = re.compile(r"[a-z0-9_.-]+:[a-z0-9_./-]+")


def output(path, value, check):
    content = json.dumps(value, indent=2, ensure_ascii=False) + "\n"
    if check:
        if not path.is_file() or path.read_text() != content:
            raise ValueError(f"Generated data differs: {path.relative_to(ROOT)}")
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)


def generate(catalogue, check=False):
    resources = ROOT / "src/main/resources"
    data = resources / "data/rematter/rematter"
    names = {}
    substances = catalogue["substances"]
    for substance_id, row in sorted(substances.items()):
        if not ID.fullmatch("rematter:" + substance_id):
            raise ValueError(f"Invalid substance ID: {substance_id}")
        key = "substance.rematter." + substance_id
        definition = {"schema_version": 1, "translation_key": key, "symbol": row["symbol"],
                      "kind": row["kind"], "composition": row["composition"], "phases": row["phases"]}
        output(data / "substance" / (substance_id + ".json"), definition, check)
        names[key] = row["name"]
    seen = set()
    for row in catalogue["items"]:
        item = row["item"]
        if item in seen or not ID.fullmatch(item):
            raise ValueError(f"Duplicate or invalid item ID: {item}")
        seen.add(item)
        if not row.get("reviewed"):
            raise ValueError(f"Item has not been reviewed: {item}")
        if row["form"] not in {"ingot", "dust", "nugget", "material_block", "material", "gem", "natural", "raw", "ore"}:
            raise ValueError(f"Not a supported material form: {item}")
        if row["synthesis"] and row["form"] in {"raw", "ore"}:
            raise ValueError(f"Ore enrichment products cannot be synthesized: {item}")
        count = row.get("item_count", 1)
        if not isinstance(count, int) or not 1 <= count <= 64:
            raise ValueError(f"Invalid item count: {item}")
        namespace, item_path = item.split(":", 1)
        relative = ("vanilla/" + item_path if namespace == "minecraft" else "compat/" + namespace + "/" + item_path) + ".json"
        profile = {"schema_version": 1}
        if namespace != "minecraft":
            profile["required_mod"] = row["required_mod"]
        if "required_item" in row:
            profile["required_item"] = row["required_item"]
        profile["item"] = item
        if count != 1:
            profile["item_count"] = count
        profile.update({"matter": row["matter"], "decomposition_energy": row["decomposition_energy"],
                        "synthesis_energy": row["synthesis_energy"], "ticks": row["ticks"]})
        output(data / "material_profile" / relative, profile, check)
        allowance = {"schema_version": 1}
        if namespace != "minecraft":
            allowance["required_mod"] = row["required_mod"]
        if "required_item" in row:
            allowance["required_item"] = row["required_item"]
        allowance["item"] = item
        if row["synthesis"]:
            allowance["category"] = "natural_material" if row["form"] == "natural" else "raw_material"
        else:
            allowance["disabled"] = True
        output(data / "synthesis" / relative, allowance, check)
    language_path = resources / "assets/rematter/lang/en_us.json"
    language = json.loads(language_path.read_text())
    language.update(names)
    if check:
        actual = json.loads(language_path.read_text())
        if any(actual.get(k) != v for k, v in names.items()):
            raise ValueError("Missing material translations")
    else:
        output(language_path, language, False)
    return {"substances": len(substances), "profiles": len(seen),
            "synthesis_outputs": sum(row["synthesis"] for row in catalogue["items"])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalogue", type=Path, default=ROOT / "tools/data/material_catalogue.json")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    print(json.dumps(generate(json.loads(args.catalogue.read_text()), args.check), indent=2))


if __name__ == "__main__":
    main()
