#!/usr/bin/env python3
"""Generate chemistry JSON only from the reviewed exact-ID material catalogue.

This tool does not read installed mods, discover recipes, infer chemistry from
tags, or grant synthesis to new IDs. Edit the catalogue deliberately first.
Every file below the generated chemistry directories comes from the catalogue;
stray files fail the check and are removed by generation. Batch energies must
fit the machine limit, which is read from the Java machine sources.
"""
from __future__ import annotations

import argparse
from collections import Counter
import csv
from fractions import Fraction
import io
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ID = re.compile(r"[a-z0-9_.-]+:[a-z0-9_./-]+")
DATA = ROOT / "src/main/resources/data/rematter/rematter"
GENERATED_DIRECTORIES = ("substance", "material_profile", "fluid_profile", "synthesis", "environment_source", "process")
FORMS = {"ingot", "dust", "nugget", "material_block", "material", "gem", "natural", "raw", "ore"}
TEMPLATE = ROOT / "tools/data/material_coverage_template.md"
COVERAGE = ROOT / "docs/material-coverage.md"
COVERAGE_LIST = ROOT / "docs/material-coverage.csv"
MACHINE_SOURCES = ROOT / "src/main/java/dev/latrox/rematter/content"


def output(path, value, check, written):
    content = value if isinstance(value, str) else json.dumps(value, indent=2, ensure_ascii=False) + "\n"
    written.add(path)
    if check:
        if not path.is_file() or path.read_text() != content:
            raise ValueError(f"Generated data differs: {path.relative_to(ROOT)}")
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)


def java_constant(source, name):
    match = re.search(rf"static final (?:int|long) {name} = ([0-9_]+);", (MACHINE_SOURCES / source).read_text())
    if not match:
        raise ValueError(f"Cannot read {name} from {source}")
    return int(match.group(1).replace("_", ""))


def batch_energy_limit():
    """Return MachineEnergyPolicy.MAX_BATCH_ENERGY: the FE buffer divided by the full speed-upgrade factor.

    The machines reject a catalogue at load time when any batch exceeds it, whether or not synthesis is permitted.
    """
    policy = (MACHINE_SOURCES / "MachineEnergyPolicy.java").read_text()
    if re.search(r"MAX_BATCH_ENERGY = [0-9_]+;", policy):
        return java_constant("MachineEnergyPolicy.java", "MAX_BATCH_ENERGY")
    if "MAX_BATCH_ENERGY = MachineBlockEntity.ENERGY_CAPACITY / MAX_SPEED_FACTOR;" not in policy:
        raise ValueError("MachineEnergyPolicy.MAX_BATCH_ENERGY changed; update batch_energy_limit")
    if "ENERGY_CAPACITY = MatterLimits.MACHINE_ENERGY;" not in (MACHINE_SOURCES / "MachineBlockEntity.java").read_text():
        raise ValueError("Machine energy no longer uses MatterLimits")
    return java_constant("MatterLimits.java", "MACHINE_ENERGY") // java_constant("MachineEnergyPolicy.java", "MAX_SPEED_FACTOR")


def require_energy(target, row, limit):
    for field in ("decomposition_energy", "synthesis_energy"):
        if not isinstance(row[field], int) or not 0 <= row[field] <= limit:
            raise ValueError(f"{field} of {target} must be between 0 and the {limit} FE batch limit")


def resource_path(target):
    namespace, path = target.split(":", 1)
    return namespace, path


def guarded(target, row):
    """Content of another mod loads only with that mod; vanilla content always loads."""
    namespace, _ = resource_path(target)
    document = {"schema_version": 1}
    if namespace not in {"minecraft", "rematter"} and row.get("required_mod") in {None, "minecraft"}:
        raise ValueError(f"Optional content needs its owning mod: {target}")
    if row.get("required_mod", "minecraft") != "minecraft":
        document["required_mod"] = row["required_mod"]
    if "required_item" in row:
        document["required_item"] = row["required_item"]
    return document


def substances(catalogue, check, written, names):
    for substance_id, row in sorted(catalogue["substances"].items()):
        if not ID.fullmatch("rematter:" + substance_id):
            raise ValueError(f"Invalid substance ID: {substance_id}")
        key = "substance.rematter." + substance_id
        definition = {"schema_version": 1, "translation_key": key, "symbol": row["symbol"],
                      "kind": row["kind"], "composition": row["composition"], "phases": row["phases"]}
        output(DATA / "substance" / (substance_id + ".json"), definition, check, written)
        names[key] = row["name"]


def batches(row, family_name, family):
    """Return how many exact family batches a row holds; fractional per-item shares are rejected."""
    item = row["item"]
    batch = family.get("batch")
    if not batch or not isinstance(batch.get("items"), int) or batch["items"] < 1 or not batch.get("matter"):
        raise ValueError(f"Family {family_name} needs an exact batch")
    expected = {(entry["substance"], entry["phase"]): entry["amount"] for entry in batch["matter"]}
    actual = {(entry["substance"], entry["phase"]): entry["amount"] for entry in row["matter"]}
    if len(actual) != len(row["matter"]) or set(actual) != set(expected):
        raise ValueError(f"Matter of {item} does not match the constituents of family {family_name}")
    multiples = {amount // expected[key] for key, amount in actual.items()}
    if any(amount % expected[key] for key, amount in actual.items()) or len(multiples) != 1:
        raise ValueError(f"Fractional per-item ratio for {item}: use item_count for a whole multiple of the {family_name} batch")
    count = multiples.pop()
    pieces = row.get("item_count", 1)
    per_base = {"ingot": 1, "nugget": 9}.get(row["form"])
    if per_base and pieces != count * batch["items"] * per_base:
        raise ValueError(f"Item count of {item} does not match its share of the {family_name} batch")
    return count


def items(catalogue, check, written, limit):
    families = catalogue["families"]
    seen = set()
    costs = {}
    for row in catalogue["items"]:
        item = row["item"]
        if item in seen or not ID.fullmatch(item):
            raise ValueError(f"Duplicate or invalid item ID: {item}")
        seen.add(item)
        if not row.get("reviewed"):
            raise ValueError(f"Item has not been reviewed: {item}")
        if row["family"] not in families:
            raise ValueError(f"Unknown material family for {item}: {row['family']}")
        if row["form"] not in FORMS:
            raise ValueError(f"Not a supported material form: {item}")
        if row["synthesis"] and row["form"] in {"raw", "ore"}:
            raise ValueError(f"Ore enrichment products cannot be synthesized: {item}")
        if row["synthesis"] and not families[row["family"]]["synthesis"]:
            raise ValueError(f"Synthesis is disabled for the family of {item}")
        pieces = row.get("item_count", 1)
        if not isinstance(pieces, int) or not 1 <= pieces <= 64:
            raise ValueError(f"Invalid item count: {item}")
        require_energy(item, row, limit)
        count = batches(row, row["family"], families[row["family"]])
        cost = tuple(Fraction(row[field], count) for field in ("decomposition_energy", "synthesis_energy", "ticks"))
        if costs.setdefault(row["family"], cost) != cost:
            raise ValueError(f"Energy and duration of {item} do not scale with its share of the {row['family']} batch")
        namespace, item_path = resource_path(item)
        relative = ("vanilla/" + item_path if namespace == "minecraft" else "compat/" + namespace + "/" + item_path) + ".json"
        profile = guarded(item, row)
        profile["item"] = item
        if pieces != 1:
            profile["item_count"] = pieces
        profile.update({"matter": row["matter"],
                        "decomposition_energy": row["decomposition_energy"],
                        "synthesis_energy": row["synthesis_energy"], "ticks": row["ticks"]})
        output(DATA / "material_profile" / relative, profile, check, written)
        allowance = guarded(item, row)
        allowance["item"] = item
        if row.get("unavailable_with_mods"):
            allowance["unavailable_with_mods"] = row["unavailable_with_mods"]
        if row["synthesis"]:
            family = families[row["family"]]
            for field in ("synthesis_machine", "crystallization_tier", "requires_advancement"):
                if field in family: allowance[field] = family[field]
            allowance["category"] = "natural_material" if row["form"] == "natural" else "raw_material"
        else:
            allowance["disabled"] = True
        output(DATA / "synthesis" / relative, allowance, check, written)
    return len(seen)


def fluids(catalogue, check, written, limit):
    seen = set()
    for row in catalogue["fluids"]:
        fluid = row["fluid"]
        if fluid in seen or not ID.fullmatch(fluid):
            raise ValueError(f"Duplicate or invalid fluid ID: {fluid}")
        seen.add(fluid)
        require_energy(fluid, row, limit)
        namespace, fluid_path = resource_path(fluid)
        relative = Path("fluids") / namespace / (fluid_path + ".json")
        profile = guarded(fluid, row)
        profile.update({"fluid": fluid, "amount": row["amount"], "matter": row["matter"],
                        "decomposition_energy": row["decomposition_energy"],
                        "synthesis_energy": row["synthesis_energy"], "ticks": row["ticks"]})
        output(DATA / "fluid_profile" / relative, profile, check, written)
        allowance = guarded(fluid, row)
        allowance["item"] = fluid
        if row["synthesis"]:
            allowance["category"] = "fluid"
        else:
            allowance["disabled"] = True
        output(DATA / "synthesis" / relative, allowance, check, written)
    return len(seen)


def gases(catalogue, check, written):
    seen = set()
    for row in catalogue["gases"]:
        substance = row["substance"]
        if substance in seen or not ID.fullmatch(substance):
            raise ValueError(f"Duplicate or invalid gas ID: {substance}")
        seen.add(substance)
        namespace, path = resource_path(substance)
        allowance = guarded(substance, row)
        allowance.update({"item": substance, "category": "gas"})
        output(DATA / "synthesis" / "gases" / namespace / (path + ".json"), allowance, check, written)
    return len(seen)


def stray_files(written, check):
    unexpected = sorted(path for directory in GENERATED_DIRECTORIES
                        for path in (DATA / directory).rglob("*.json") if path not in written)
    if check and unexpected:
        paths = ", ".join(str(path.relative_to(ROOT)) for path in unexpected)
        raise ValueError(f"Chemistry data outside the catalogue: {paths}")
    for path in unexpected:
        path.unlink()
    for directory in GENERATED_DIRECTORIES:
        for folder in sorted((DATA / directory).rglob("*"), reverse=True):
            if folder.is_dir() and not any(folder.iterdir()):
                folder.rmdir()


def matter_text(matter):
    return " + ".join(f"{entry['substance']}[{entry['phase']}]={entry['amount']}" for entry in matter)


def coverage_list(catalogue):
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(["id", "type", "family", "form", "batch", "matter", "synthesis",
                     "decomposition_energy_fe", "synthesis_energy_fe", "ticks", "required_mod", "unavailable_with_mods",
                     "synthesis_machine", "crystallization_tier", "requires_advancement"])
    for row in catalogue["items"]:
        writer.writerow([row["item"], "item", row["family"], row["form"], row.get("item_count", 1), matter_text(row["matter"]),
                         str(row["synthesis"]).lower(), row["decomposition_energy"], row["synthesis_energy"], row["ticks"],
                         guarded(row["item"], row).get("required_mod", ""), ";".join(row.get("unavailable_with_mods", [])),
                         catalogue["families"][row["family"]].get("synthesis_machine", "material_synthesizer") if row["synthesis"] else "",
                         catalogue["families"][row["family"]].get("crystallization_tier", 1) if row["synthesis"] else "",
                         catalogue["families"][row["family"]].get("requires_advancement", "")])
    for row in catalogue["fluids"]:
        writer.writerow([row["fluid"], "fluid", "", "fluid", row["amount"], matter_text(row["matter"]),
                         str(row["synthesis"]).lower(), row["decomposition_energy"], row["synthesis_energy"], row["ticks"],
                         guarded(row["fluid"], row).get("required_mod", ""), "", "material_synthesizer" if row["synthesis"] else "", 1, ""])
    for row in catalogue["gases"]:
        writer.writerow([row["substance"], "gas", "", "gas", "", "", "true", "", "", "",row.get("required_mod",""),"","drive export","",""])
    return buffer.getvalue()


def coverage_page(catalogue):
    rows = catalogue["items"]
    profiles = Counter(row["item"].split(":", 1)[0] for row in rows)
    outputs = Counter(row["item"].split(":", 1)[0] for row in rows if row["synthesis"])
    fluid_outputs = sum(row["synthesis"] for row in catalogue["fluids"])
    summary = (f"The data catalogue contains **{len(rows)} item profiles** across **{len(profiles)} namespaces**, "
               f"with **{sum(outputs.values())} explicit item synthesis outputs**. It also covers "
               f"**{len(catalogue['fluids'])} fluids** ({fluid_outputs} with synthesis) and **{len(catalogue['gases'])} gas outputs**, "
               f"and defines {len(catalogue['substances'])} substances in addition to the built-in core elements and compounds. "
               "Installing a supported mod does not imply that every item from it is supported. "
               "These are declared permissions before dependency filtering. Quartz gems and obsidian are disabled while Ender IO is loaded, "
               "unless a pack explicitly overrides them. Crystal-family outputs use the Material Synthesizer; every diamond form requires tier 2. "
               "The CSV includes dependency conditions, required machines, tiers and advancements.")
    table = ["| Namespace | Profiles | Synthesis outputs |", "| --- | ---: | ---: |"]
    table += [f"| `{namespace}` | {profiles[namespace]} | {outputs[namespace]} |" for namespace in sorted(profiles)]
    fluid_table = ["| Fluid | Batch | Matter | Synthesis |", "| --- | ---: | --- | --- |"]
    fluid_table += [f"| `{row['fluid']}` | {row['amount']} mB | {matter_text(row['matter'])} | {'yes' if row['synthesis'] else 'no'} |"
                    for row in catalogue["fluids"]]
    gas_lines = [f"- `{row['substance']}`" + (f" (with `{row['required_mod']}`)" if "required_mod" in row else "")
                 for row in catalogue["gases"]]
    fictional = []
    for element, row in sorted(catalogue["substances"].items(), key=lambda entry: entry[1]["name"]):
        if row["kind"] != "element" or not row["basis"].startswith("Fictional"):
            continue
        key = "rematter:" + element
        carriers = sorted(other["name"] for name, other in catalogue["substances"].items()
                          if name != element and key in other["composition"])
        direct = sum(any(entry["substance"] == key for entry in item["matter"]) for item in rows)
        fictional.append(f"- **{row['name']}** (`{row['symbol']}`): " + (", ".join(carriers) + "; " if carriers else "")
                         + f"held directly by {direct} item profiles.")
    page = TEMPLATE.read_text()
    for name, value in {"summary": summary, "namespace_table": "\n".join(table), "fictional": "\n".join(fictional),
                        "fluids": "\n".join(fluid_table) + "\n\nGases that may leave storage as gas:\n\n" + "\n".join(gas_lines)}.items():
        token = "{{" + name + "}}"
        if page.count(token) != 1:
            raise ValueError(f"Coverage template needs exactly one {token}")
        page = page.replace(token, value)
    if "{{" in page:
        raise ValueError("Unknown placeholder in the coverage template")
    return page


def coverage(catalogue, check):
    written = set()
    output(COVERAGE_LIST, coverage_list(catalogue), check, written)
    output(COVERAGE, coverage_page(catalogue), check, written)


def language(names, check):
    language_path = ROOT / "src/main/resources/assets/rematter/lang/en_us.json"
    current = json.loads(language_path.read_text())
    if check:
        if any(current.get(key) != value for key, value in names.items()):
            raise ValueError("Missing material translations")
    else:
        current.update(names)
        output(language_path, current, False, set())


def processes(catalogue, check, written, names):
    for row in catalogue.get("processes", []):
        namespace, path = resource_path(row["id"])
        if namespace != "rematter" or not ID.fullmatch(row["id"]):
            raise ValueError("Invalid industrial process id")
        document = {"schema_version": 1, **{key: value for key, value in row.items() if key not in {"id", "name"}}}
        output(DATA / "process" / (path + ".json"), document, check, written)
        # Every new industrial route is closed in the reference pack until its own integration audit.
        output(ROOT / "integrations/reforging/datapack/data/rematter/rematter/process" / (path + ".json"),
               {"schema_version": 1, "disabled": True}, check, written)
        names["process.rematter." + path] = row["name"]


def generate(catalogue, check=False):
    written = set()
    names = {}
    substances(catalogue, check, written, names)
    limit = batch_energy_limit()
    profile_count = items(catalogue, check, written, limit)
    fluid_count = fluids(catalogue, check, written, limit)
    gas_count = gases(catalogue, check, written)
    for source in catalogue.get("environment_sources", []):
        namespace, path = resource_path(source["id"])
        if namespace != "rematter" or not source.get("renewable"):
            raise ValueError("Invalid environment source")
        output(DATA / "environment_source" / (path + ".json"), {"schema_version": 1, **source}, check, written)
    processes(catalogue, check, written, names)
    stray_files(written, check)
    language(names, check)
    coverage(catalogue, check)
    return {"substances": len(catalogue["substances"]), "profiles": profile_count, "fluids": fluid_count,
            "synthesis_outputs": sum(row["synthesis"] for row in catalogue["items"]),
            "fluid_outputs": sum(row["synthesis"] for row in catalogue["fluids"]), "gas_outputs": gas_count}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalogue", type=Path, default=ROOT / "tools/data/material_catalogue.json")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    print(json.dumps(generate(json.loads(args.catalogue.read_text()), args.check), indent=2))


if __name__ == "__main__":
    main()
