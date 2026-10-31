#!/usr/bin/env python3
"""Read installed JAR evidence for manual chemistry curation; never create profiles.

Item assets and tags are candidates, not proof of runtime registry membership.
Optional tag entries may refer to absent mods. Inspect a live registry export
before promoting any candidate to a supported material.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import tomllib
import zipfile

MATERIAL_TAG = re.compile(r"data/([^/]+)/tags/item/(ingots|dusts|gems|raw_materials|nuggets|storage_blocks|ores)/(.+)\.json")
ITEM_ASSET = re.compile(r"assets/([^/]+)/items/(.+)\.json")


def read_json(archive, name):
    try:
        return json.loads(archive.read(name))
    except (json.JSONDecodeError, UnicodeDecodeError):
        return None


def inventory(mods: Path) -> dict:
    jars, assets, tags, errors = [], {}, [], []
    for path in sorted(mods.glob("*.jar")):
        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            metadata = []
            for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                if name in names:
                    try:
                        data = tomllib.loads(archive.read(name).decode())
                        metadata = [{"id": row["modId"], "name": row.get("displayName", row["modId"]),
                                     "version": row.get("version", "unknown")} for row in data.get("mods", [])]
                    except (tomllib.TOMLDecodeError, UnicodeDecodeError, KeyError) as error:
                        errors.append({"jar": path.name, "resource": name, "error": type(error).__name__})
                    break
            if not metadata and "fabric.mod.json" in names:
                data = read_json(archive, "fabric.mod.json") or {}
                if "id" in data:
                    metadata = [{"id": data["id"], "name": data.get("name", data["id"]),
                                 "version": data.get("version", "unknown")}]
            language = {}
            for name in sorted(names):
                if re.fullmatch(r"assets/[^/]+/lang/en_us.json", name):
                    language.update(read_json(archive, name) or {})
            own_assets = []
            for name in sorted(names):
                if match := ITEM_ASSET.fullmatch(name):
                    namespace, item_path = match.groups()
                    item = namespace + ":" + item_path
                    own_assets.append(item)
                    evidence = {"jar": path.name, "resource": name}
                    if item not in assets:
                        assets[item] = {"id": item, "display_name": language.get("item." + namespace + "." + item_path,
                            language.get("block." + namespace + "." + item_path, "")), "asset_evidence": [], "material_tags": []}
                    assets[item]["asset_evidence"].append(evidence)
                if match := MATERIAL_TAG.fullmatch(name):
                    namespace, form, material = match.groups()
                    for value in (read_json(archive, name) or {}).get("values", []):
                        optional = isinstance(value, dict) and not value.get("required", True)
                        value = value["id"] if isinstance(value, dict) else value
                        tags.append({"value": value, "optional": optional, "form": form, "material": material,
                                     "tag": namespace + ":" + form + "/" + material, "jar": path.name, "resource": name})
            with path.open("rb") as source:
                digest = hashlib.file_digest(source, "sha256").hexdigest()
            jars.append({"jar": path.name, "sha256": digest,
                         "mods": metadata, "item_asset_count": len(own_assets),
                         "status": "requires_runtime_classification"})
    for tag in tags:
        if tag["value"] in assets:
            assets[tag["value"]]["material_tags"].append(tag["tag"])
    for asset in assets.values():
        asset["material_tags"] = sorted(set(asset["material_tags"]))
    direct_ids = {row["value"] for row in tags if not row["value"].startswith("#")}
    return {"schema_version": 1, "evidence": "Installed JAR metadata, item assets and material tags; runtime verification required.",
            "summary": {"jar_count": len(jars), "item_asset_count": len(assets), "direct_tag_item_count": len(direct_ids),
                        "asset_backed_tag_item_count": len(direct_ids & assets.keys()),
                        "unverified_tag_item_count": len(direct_ids - assets.keys()),
                        "tag_forms": dict(sorted(Counter(row["form"] for row in tags).items()))},
            "jars": jars, "item_assets": dict(sorted(assets.items())), "material_tags": tags, "errors": errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mods", required=True, type=Path, help="Read-only directory containing installed mod JARs")
    parser.add_argument("--output", required=True, type=Path, help="Local evidence report; choose a private location")
    args = parser.parse_args()
    if not args.mods.is_dir():
        parser.error("--mods must be an existing directory")
    report = inventory(args.mods)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps(report["summary"], indent=2))
    if report["errors"]:
        print(f"Metadata parsing requires review for {len(report['errors'])} resources.")


if __name__ == "__main__":
    main()
