#!/usr/bin/env python3
"""Audit Crystal Burner fuel definitions and core-producing recipes across standalone data and datapack overlays."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE_DATA = ROOT / "src/main/resources/data"
REFORGING_PACK = ROOT / "integrations/reforging/datapack"
AUDIT_DOC = ROOT / "docs/crystal-fuel-audit.md"

ALLOWED_FUELS = (
    "rematter:matrix_crystal",
    "rematter:ice_matrix_crystal",
    "rematter:copper_matrix_crystal",
    "rematter:amber_matrix_crystal",
    "rematter:ember_matrix_crystal",
    "rematter:violet_matrix_crystal",
    "rematter:blue_matrix_crystal",
    "rematter:leaf_matrix_crystal",
    "rematter:mint_matrix_crystal",
)
ALLOWED_SET = frozenset(ALLOWED_FUELS)
TIER_ZERO_CORES = frozenset({
    "matrix_crystal",
    "ice_matrix_crystal",
    "copper_matrix_crystal",
    "amber_matrix_crystal",
    "ember_matrix_crystal",
})


def discounted_floor(energy: int) -> int:
    """Return the conservative lower bound under MachineEnergyPolicy's 25% maximum combined discount."""
    if energy < 0:
        raise ValueError(f"Negative recipe energy: {energy}")
    return energy - energy // 4


def merged_json_tree(roots: list[Path], subpaths: tuple[str, ...]) -> dict[str, dict]:
    """Merge JSON resources from base data and optional datapack overlays using standard datapack replacement."""
    merged: dict[str, dict] = {}
    for root in roots:
        data_root = root / "data" if (root / "data").is_dir() else root
        if not data_root.is_dir():
            raise ValueError(f"Missing data directory: {root}")
        for namespace_dir in sorted(p for p in data_root.iterdir() if p.is_dir()):
            for sub in subpaths:
                target = namespace_dir / sub
                if not target.is_dir():
                    continue
                for file in sorted(target.rglob("*.json")):
                    rel = f"{namespace_dir.name}/{file.relative_to(namespace_dir).as_posix()}"
                    merged[rel] = json.loads(file.read_text())
    return merged


def resolve_tag_values(roots: list[Path], tag_id: str) -> set[str]:
    namespace, path = tag_id.split(":", 1)
    values: set[str] = set()
    for root in roots:
        data_root = root / "data" if (root / "data").is_dir() else root
        tag_file = data_root / namespace / "tags/item" / f"{path}.json"
        if not tag_file.is_file():
            continue
        doc = json.loads(tag_file.read_text())
        if doc.get("replace"):
            values.clear()
        for entry in doc.get("values", []):
            if isinstance(entry, str) and not entry.startswith("#"):
                values.add(entry)
            elif isinstance(entry, dict) and isinstance(entry.get("id"), str) and not entry["id"].startswith("#"):
                values.add(entry["id"])
    return values


def parse_fuel_doc(rel_path: str, doc: dict) -> dict:
    if doc.get("schema_version") != 1:
        raise ValueError(f"Unsupported crystal_fuel schema_version in {rel_path}")
    item = doc.get("item")
    if item not in ALLOWED_SET:
        raise ValueError(f"Unapproved crystal fuel item in {rel_path}: {item}")
    count = doc.get("count", 1)
    energy = doc.get("energy")
    if not isinstance(count, int) or not 1 <= count <= 64:
        raise ValueError(f"Invalid crystal fuel count in {rel_path}: {count}")
    if not isinstance(energy, int) or not 1 <= energy <= 1_000_000_000:
        raise ValueError(f"Invalid crystal fuel energy in {rel_path}: {energy}")
    enabled = True
    if doc.get("disabled") is True or doc.get("enabled") is False:
        enabled = False
    return {
        "item": item,
        "count": count,
        "energy": energy,
        "energy_per_item": energy / count,
        "enabled": enabled,
        "source": rel_path,
    }


def recipe_outputs(doc: dict) -> list[tuple[str, int]]:
    outputs: list[tuple[str, int]] = []
    result = doc.get("result")
    if isinstance(result, dict) and "id" in result:
        outputs.append((result["id"], int(result.get("count", 1))))
    elif isinstance(result, str):
        outputs.append((result, int(doc.get("count", 1))))
    for entry in doc.get("results", []):
        if isinstance(entry, dict) and "id" in entry:
            outputs.append((entry["id"], int(entry.get("count", 1))))
    return outputs


def audit_roots(label: str, roots: list[Path]) -> dict:
    fuel_docs = merged_json_tree(roots, ("rematter/crystal_fuel",))
    if len(fuel_docs) > 64:
        raise ValueError("Too many crystal_fuel documents")
    fuels_by_item: dict[str, dict] = {}
    for rel_path, doc in sorted(fuel_docs.items()):
        parsed = parse_fuel_doc(rel_path, doc)
        if parsed["item"] in fuels_by_item:
            raise ValueError(f"Duplicate crystal_fuel target: {parsed['item']}")
        fuels_by_item[parsed["item"]] = parsed
    missing = sorted(ALLOWED_SET - fuels_by_item.keys())
    if missing:
        raise ValueError(f"Missing crystal_fuel definitions in {label}: {missing}")

    profiles = merged_json_tree(roots, ("rematter/material_profile",))
    sand_profile = profiles.get("rematter/rematter/material_profile/vanilla/sand.json")
    if not sand_profile:
        raise ValueError("Missing vanilla sand material profile")
    sand_synthesis_energy = int(sand_profile["synthesis_energy"])
    sand_floor = discounted_floor(sand_synthesis_energy)

    recipes = merged_json_tree(roots, ("recipe",))
    silith = recipes.get("rematter/recipe/silith.json")
    blend = recipes.get("rematter/recipe/matrix_blend.json")
    if not silith or silith.get("primary") != "minecraft:sand" or silith.get("result", {}).get("count", 1) != 1:
        raise ValueError("Unexpected rematter:silith feedstock recipe")
    if not blend or blend.get("primary") != "rematter:silith" or blend.get("secondary") != "rematter:argil":
        raise ValueError("Unexpected rematter:matrix_blend feedstock recipe")
    blend_outputs = int(blend.get("result", {}).get("count", 0))
    if blend_outputs < 1:
        raise ValueError("Invalid rematter:matrix_blend output count")
    sand_floor_per_core = sand_floor // blend_outputs

    producers: dict[str, set[str]] = {fuel: set() for fuel in ALLOWED_FUELS}
    for rel_path, doc in sorted(recipes.items()):
        if doc.get("neoforge:conditions") == [{"type": "neoforge:false"}]:
            continue
        for item_id, _count in recipe_outputs(doc):
            if item_id in ALLOWED_SET:
                producers[item_id].add(rel_path)

    expected_matrix_producers = {"rematter/recipe/matrix_crystal.json"}
    rows: list[dict] = []
    for item_id in ALLOWED_FUELS:
        short = item_id.split(":", 1)[1]
        direct_rel = f"rematter/recipe/{short}.json"
        direct = recipes.get(direct_rel)
        if not direct or direct.get("type") != "rematter:preparation":
            raise ValueError(f"Missing direct preparation recipe for {item_id}")
        if direct.get("tertiary") is not None or direct.get("primary_count", 1) != 1:
            raise ValueError(f"Unexpected extra inputs on {direct_rel}")
        result = direct.get("result", {})
        if result.get("id") != item_id or int(result.get("count", 1)) != 1:
            raise ValueError(f"Direct preparation of {item_id} must yield exactly 1 item")
        prep_energy = int(direct["energy"])
        prep_ticks = int(direct["ticks"])
        expected_tier = 0 if short in TIER_ZERO_CORES else 1
        if int(direct.get("minimum_tier", -1)) != expected_tier:
            raise ValueError(f"Unexpected minimum_tier on {direct_rel}")
        prep_floor = discounted_floor(prep_energy)

        if item_id == "rematter:matrix_crystal":
            if direct.get("primary") != "rematter:matrix_blend":
                raise ValueError("matrix_crystal must prepare from rematter:matrix_blend")
        else:
            reclaim_rel = f"rematter/recipe/{short}_reclaim.json"
            expected_matrix_producers.add(reclaim_rel)
            if direct.get("primary") != "rematter:matrix_crystal":
                raise ValueError(f"{direct_rel} must consume rematter:matrix_crystal")
            if producers[item_id] != {direct_rel}:
                raise ValueError(f"Unauthorized producer(s) for {item_id}: {sorted(producers[item_id])}")
            reclaim = recipes.get(reclaim_rel)
            if not reclaim or reclaim.get("type") != "rematter:preparation" or reclaim.get("primary") != item_id:
                raise ValueError(f"Missing or invalid reclaim recipe {reclaim_rel}")
            if int(reclaim.get("minimum_tier", 0)) < 2:
                raise ValueError(f"Reclaim recipe {reclaim_rel} must require minimum_tier >= 2")
            reclaim_outputs = recipe_outputs(reclaim)
            if len(reclaim_outputs) != 2 or reclaim_outputs[0] != ("rematter:matrix_crystal", 1) or reclaim_outputs[1][1] != 1:
                raise ValueError(f"Reclaim recipe {reclaim_rel} must return 1 matrix_crystal and 1 additive")
            additive = str(direct.get("secondary", ""))
            returned = reclaim_outputs[1][0]
            if additive.startswith("#"):
                allowed_additives = resolve_tag_values(roots, additive[1:])
                if returned not in allowed_additives:
                    raise ValueError(f"Reclaim {reclaim_rel} returns {returned}, outside {additive}")
            elif additive != returned:
                raise ValueError(f"Reclaim {reclaim_rel} returns {returned}, expected {additive}")

        fuel = fuels_by_item[item_id]
        full_synthesis_floor = sand_floor_per_core + prep_floor
        mandatory_synthesis_floor = sand_floor_per_core if expected_tier == 0 else full_synthesis_floor
        if fuel["enabled"]:
            if fuel["energy_per_item"] >= prep_floor:
                raise ValueError(
                    f"Enabled fuel {item_id} yields {fuel['energy_per_item']} FE >= powered preparation floor {prep_floor} FE"
                )
            if fuel["energy_per_item"] > mandatory_synthesis_floor // 2:
                raise ValueError(
                    f"Enabled fuel {item_id} yields {fuel['energy_per_item']} FE > half the mandatory synthesis floor ({mandatory_synthesis_floor // 2} FE)"
                )

        rows.append({
            "item": item_id,
            "enabled": fuel["enabled"],
            "count": fuel["count"],
            "fuel_energy": fuel["energy"],
            "minimum_tier": expected_tier,
            "prep_energy": prep_energy,
            "prep_ticks": prep_ticks,
            "prep_floor_fe": prep_floor,
            "sand_floor_fe": sand_floor_per_core,
            "combined_floor_fe": full_synthesis_floor,
            "net_after_prep_fe": fuel["energy"] - prep_floor * fuel["count"],
            "net_after_synthesis_fe": fuel["energy"] - full_synthesis_floor * fuel["count"],
        })

    if producers["rematter:matrix_crystal"] != expected_matrix_producers:
        extra = sorted(producers["rematter:matrix_crystal"] - expected_matrix_producers)
        missing_prod = sorted(expected_matrix_producers - producers["rematter:matrix_crystal"])
        raise ValueError(f"Matrix crystal producer mismatch in {label}: extra={extra}, missing={missing_prod}")

    return {
        "label": label,
        "enabled_count": sum(1 for r in rows if r["enabled"]),
        "disabled_count": sum(1 for r in rows if not r["enabled"]),
        "sand_synthesis_energy_fe": sand_synthesis_energy,
        "sand_discounted_floor_fe": sand_floor,
        "blend_outputs_per_sand": blend_outputs,
        "sand_floor_per_core_fe": sand_floor_per_core,
        "fuels": rows,
    }


def render_markdown(standalone: dict, reforging: dict) -> str:
    min_yield = min(r["fuel_energy"] for r in standalone["fuels"])
    max_yield = max(r["fuel_energy"] for r in standalone["fuels"])
    lines = [
        "# Crystal fuel closed-cycle audit",
        "",
        "`tools/audit_crystal_fuel.py` verifies the closed nine-crystal fuel set (`CrystalFuelData.ALLOWED`), every recipe producing a crystal core, and the conservative energy lower bounds enforced by `MachineEnergyPolicy` (capped at a 25% combined efficiency discount).",
        "",
        "## Closed-cycle energy bounds (standalone)",
        "",
        f"- **Sand synthesis cost:** `{standalone['sand_synthesis_energy_fe']:,} FE` nominal (`{standalone['sand_discounted_floor_fe']:,} FE` floor at 25% max discount) for `{standalone['blend_outputs_per_sand']}` `rematter:matrix_blend` outputs = **`{standalone['sand_floor_per_core_fe']:,} FE` per core** (counting clay, preparation of Silith/Argil, and coloured additives as `0 FE`).",
        "- **Powered crystal preparation floor:** `4,000 FE` nominal = **`3,000 FE` floor** per core at maximum machine discount.",
        f"- **Differentiated closed-cycle burner yields:** **`{min_yield:,} FE` .. `{max_yield:,} FE` per crystal** — Tier-0 hand-crankable cores stay `<= 1,500 FE` (`50%` of the `3,000 FE` sand floor), and Tier-1 mandatory-powered cores stay strictly `< 3,000 FE` (powered preparation floor) and `<= 3,000 FE` (`50%` of the `6,000 FE` combined synthesis + preparation floor).",
        "",
        "| Crystal fuel | Tier | Enabled | Burn yield | Prep floor | Sand + prep floor | Net vs. prep only |",
        "| --- | ---: | :---: | ---: | ---: | ---: | ---: |",
    ]
    for row in standalone["fuels"]:
        lines.append(
            f"| `{row['item']}` | {row['minimum_tier']} | {'yes' if row['enabled'] else 'no'} "
            f"| {row['fuel_energy']:,} FE | {row['prep_floor_fe']:,} FE | {row['combined_floor_fe']:,} FE "
            f"| {row['net_after_prep_fe']:,} FE |"
        )
    lines += [
        "",
        "## Pack overlay status (`integrations/reforging/datapack`)",
        "",
        f"- **Enabled fuels:** `{reforging['enabled_count']}` / `{len(reforging['fuels'])}` (`{reforging['disabled_count']}` disabled).",
        "- **External duplication safeguards:** All nine `rematter:*matrix_crystal` items are grouped in `#reforging:rematter/crystal_fuels`, excluded from Replication (`cant_be_scanned`, `cant_be_disintegrated`, `skip_calculation`), and assigned `emc: 0` by `integrations/reforging/merge_equivox.py` so external pack mods cannot duplicate crystal fuel for free FE.",
        "",
        "## Auditing a custom modpack overlay",
        "",
        "Run the audit script against any datapack directory before enabling or repricing crystal fuels:",
        "",
        "```sh",
        "python3 tools/audit_crystal_fuel.py --pack path/to/datapack",
        "```",
        "",
        "In addition to passing `--pack`, pack authors enabling crystal fuels alongside Replication, Equivox, Productive Bees, or Mystical Agriculture should verify that none of the nine `rematter:*matrix_crystal` items have an EMC value, Replication matter value, or zero-energy duplication route cheaper than their burner output.",
        "",
    ]
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", type=Path, help="Audit an external datapack overlay on top of standalone ReMatter data.")
    parser.add_argument("--check", action="store_true", help="Verify standalone, ReForging, and docs/crystal-fuel-audit.md without writing.")
    args = parser.parse_args(argv)

    if args.pack is not None:
        report = audit_roots(str(args.pack), [BASE_DATA, args.pack])
        print(json.dumps(report, indent=2))
        return 0

    standalone = audit_roots("standalone", [BASE_DATA])
    reforging = audit_roots("reforging", [BASE_DATA, REFORGING_PACK])
    rendered = render_markdown(standalone, reforging)
    if args.check:
        if not AUDIT_DOC.is_file() or AUDIT_DOC.read_text() != rendered:
            raise ValueError(f"Outdated audit documentation: {AUDIT_DOC.relative_to(ROOT)}")
    else:
        AUDIT_DOC.parent.mkdir(parents=True, exist_ok=True)
        AUDIT_DOC.write_text(rendered)
    print(
        f"Audited {len(standalone['fuels'])} standalone fuels ({standalone['enabled_count']} enabled) "
        f"and {len(reforging['fuels'])} ReForging overlay fuels ({reforging['enabled_count']} enabled)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
