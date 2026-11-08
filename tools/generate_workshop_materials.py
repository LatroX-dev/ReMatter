#!/usr/bin/env python3
"""Generate physical workshop recipes and explicit optional machine integrations."""
from pathlib import Path
import argparse
from generated_files import GeneratedFiles

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data"
OUTPUT = GeneratedFiles(ROOT, False)


def write(path, data):
    OUTPUT.json(path, data)


def recipe(name, data):
    write(DATA / "rematter/recipe" / (name + ".json"), data)


def preparation(name, inputs, output, count=1, energy=4000, ticks=100, process="preparation", network_recipe=True, minimum_tier=1):
    data = {"type": "rematter:preparation"}
    for role, (ingredient, amount) in zip(("primary", "secondary", "tertiary"), inputs):
        data[role] = ingredient
        if amount != 1:
            data[role + "_count"] = amount
    data.update(result={"id": "rematter:" + output, "count": count},
                energy=energy, ticks=ticks, minimum_tier=minimum_tier)
    if process != "preparation":
        data["process"] = process
    if not network_recipe:
        data["network_recipe"] = False
    recipe(name, data)


def advancement(name, ingredient):
    write(DATA / "rematter/advancement/recipes" / (name + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_material": {"trigger": "minecraft:inventory_changed", "conditions": {
                "items": [{"items": [ingredient]}]}},
            "has_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {
                "recipe": "rematter:" + name}}},
        "requirements": [["has_material", "has_recipe"]],
        "rewards": {"recipes": ["rematter:" + name]}})


def tag(namespace, name, values):
    write(DATA / namespace / "tags/item" / (name + ".json"), {"replace": False, "values": values})


def mod_loaded(mod):
    return {"type": "neoforge:mod_loaded", "modid": mod}


def registered(registry, value):
    return {"type": "neoforge:registered", "registry": "minecraft:" + registry, "value": value}


def missing_item(item):
    return {"type": "neoforge:not", "value": registered("item", item)}


def optional(mod, data, *conditions):
    required = [mod_loaded(mod)]
    if data["type"].startswith(mod + ":"):
        required.append(registered("recipe_serializer", data["type"]))
    return {"neoforge:conditions": [*required, *conditions], **data}


def advanced_materials():
    materials = (
        ("diamond_powder", "minecraft:diamond", "#c:dusts/diamond", "diamond_matrix_blend",
         "matrix_blend", "gold_dust", "diamond_lattice", 6000, 8000, 12000, 120, 160, 200),
        ("ender_pearl_powder", "minecraft:ender_pearl", "#rematter:full_ender_pearl_powders", "ender_conductor_blend",
         "lumivex_dust", "copper_dust", "ender_conductor", 8000, 10000, 16000, 140, 180, 240),
        ("netherite_powder", "minecraft:netherite_ingot", "#c:dusts/netherite", "netherite_sheath_blend",
         "rhevon_dust", "virelite_blend", "netherite_sheath", 12000, 14000, 24000, 180, 220, 280),
    )
    for powder, raw, shared, blend, binder, additive, result, grind_fe, mix_fe, sinter_fe, grind_t, mix_t, sinter_t in materials:
        binder, additive = "rematter:" + binder, "rematter:" + additive
        preparation(powder, [(raw, 1)], powder, energy=grind_fe, ticks=grind_t)
        preparation(blend, [(shared, 1), (binder, 2), (additive, 1)], blend, energy=mix_fe, ticks=mix_t)
        preparation(result, [("rematter:" + blend, 1)], result,
                    energy=sinter_fe, ticks=sinter_t, process="sintering")
        recipe("compat/oritech/assembler/" + blend, optional("oritech", {
            "type": "oritech:assembler", "itemInputs": [shared, binder, binder, additive],
            "itemResults": [{"id": "rematter:" + blend, "count": 1}], "time": mix_t}))
        recipe("compat/oritech/foundry/" + result, optional("oritech", {
            "type": "oritech:foundry", "itemInputs": ["rematter:" + blend] * 2,
            "itemResults": [{"id": "rematter:" + result, "count": 2}], "time": sinter_t}))

    tag("c", "dusts/diamond", ["rematter:diamond_powder"])
    tag("c", "dusts/netherite", ["rematter:netherite_powder"])
    # The common ender tag mixes one-per-pearl AE2 dust and nine-per-pearl Ender IO dust.
    tag("rematter", "full_ender_pearl_powders", ["rematter:ender_pearl_powder", {"id": "ae2:ender_dust", "required": False}])
    recipe("compat/enderio/ender_conductor_blend_from_fragments", optional("enderio", {
        "type": "rematter:preparation", "primary": "enderio:powdered_ender_pearl", "primary_count": 9,
        "secondary": "rematter:lumivex_dust", "secondary_count": 2, "tertiary": "rematter:copper_dust",
        "result": {"id": "rematter:ender_conductor_blend", "count": 1}, "energy": 10000, "ticks": 180, "minimum_tier": 1,
    }, registered("item", "enderio:powdered_ender_pearl")))

    # Mekanism already crushes diamond/netherite. Its pearl route uses AE2 dust when available.
    recipe("compat/mekanism/crushing/ender_pearl", optional("mekanism", {
        "type": "mekanism:crushing", "input": {"count": 1, "ingredient": "minecraft:ender_pearl"},
        "output": {"id": "rematter:ender_pearl_powder", "count": 1}},
        missing_item("ae2:ender_dust")))
    for name, raw in (("diamond", "minecraft:diamond"), ("netherite", "minecraft:netherite_ingot")):
        skip_existing = (missing_item("alltheores:diamond_dust"),
                         missing_item("ftbmaterials:diamond_dust")) if name == "diamond" else ()
        recipe("compat/oritech/pulverizer/" + name, optional("oritech", {
            "type": "oritech:pulverizer", "itemInputs": [raw],
            "itemResults": [{"id": "rematter:" + name + "_powder", "count": 1}], "time": 100}, *skip_existing))
        recipe("compat/enderio/sag_milling/" + name, optional("enderio", {
            "type": "enderio:sag_milling", "bonus": "none", "energy": 4800, "input": raw,
            "outputs": [{"item": {"id": "rematter:" + name + "_powder", "count": 1}}]}))


def crystal_powders():
    gems = (("lapis", "minecraft:lapis_lazuli"), ("amethyst", "minecraft:amethyst_shard"),
            ("emerald", "minecraft:emerald"))
    for name, raw in gems:
        powder = name + "_dust"
        preparation(powder, [(raw, 1)], powder)
        tag("c", "dusts/" + name, ["rematter:" + powder])


def workshop_dust_tag():
    materials = ("iron", "copper", "gold", "rhevon", "lumivex", "diamond", "netherite", "lapis", "amethyst", "emerald")
    tag("c", "dusts", ["#c:dusts/" + name for name in materials] + ["rematter:ender_pearl_powder"])


def main():
    global OUTPUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Compare the generated files instead of writing them.")
    args = parser.parse_args()
    OUTPUT = GeneratedFiles(ROOT, args.check)
    generate()
    print(f'{"Verified" if args.check else "Generated"} {OUTPUT.count} workshop files.')


def generate():
    for metal in ("iron", "copper", "gold"):
        # Copper and gold crystal additives must be available before the first powered machine.
        preparation(metal + "_dust", [("#c:ingots/" + metal, 1)], metal + "_dust",
                    minimum_tier=1 if metal == "iron" else 0)
        for method, time in (("smelting", 200), ("blasting", 100)):
            name = metal + "_ingot_from_rematter_dust_" + method
            recipe(name, {"type": "minecraft:" + method, "category": "misc",
                          "ingredient": "rematter:" + metal + "_dust",
                          "result": {"id": "minecraft:" + metal + "_ingot", "count": 1},
                          "experience": 0.0, "cookingtime": time})
            advancement(name, "rematter:" + metal + "_dust")
        tag("c", "dusts/" + metal, ["rematter:" + metal + "_dust"])

    batches = {
        "rhevon": [("#c:dusts/iron", 3), ("#c:dusts/copper", 1), ("rematter:rhedon", 1)],
        "lumivex": [("#c:dusts/copper", 3), ("#c:dusts/gold", 1), ("rematter:lumin", 1)],
        "virelite": [("rematter:silith", 1), ("rematter:argil", 1), ("rematter:virel", 1)],
    }
    for material, inputs in batches.items():
        product = material + ("_ceramic" if material == "virelite" else "_ingot")
        powder = material + ("_blend" if material == "virelite" else "_dust")
        preparation(powder, inputs, powder, 4, 8000, 160)
        heating_input = "rematter:" + powder if material == "virelite" else "#c:dusts/" + material
        preparation(product, [(heating_input, 1)], product,
                    energy=12000, ticks=200, process="sintering")
        recipe("compat/oritech/foundry/" + material, optional("oritech", {
            "type": "oritech:foundry",
            "itemInputs": ["rematter:" + powder] * 2,
            "itemResults": [{"id": "rematter:" + product, "count": 2}], "time": 200}))

    for material in ("rhevon", "lumivex"):
        ingot, dust = "rematter:" + material + "_ingot", "rematter:" + material + "_dust"
        # Auto-export only the forward production route, so network planners cannot
        # choose ingot recycling as a cyclic alternative to making alloy powder.
        preparation(material + "_dust_from_ingot", [("#c:ingots/" + material, 1)], material + "_dust",
                    network_recipe=False)
        # The same powder can be heated with fuel or in the electric Sinter Furnace.
        for method, time in (("smelting", 200), ("blasting", 100)):
            name = material + "_ingot_from_dust_" + method
            recipe(name, {"type": "minecraft:" + method, "category": "misc",
                          "ingredient": "#c:dusts/" + material,
                          "result": {"id": ingot, "count": 1}, "experience": 0.0, "cookingtime": time})
            advancement(name, dust)
        recipe("compat/mekanism/crushing/" + material, optional("mekanism", {
            "type": "mekanism:crushing", "input": {"count": 1, "ingredient": "#c:ingots/" + material},
            "output": {"id": dust, "count": 1}}))
        recipe("compat/oritech/pulverizer/" + material, optional("oritech", {
            "type": "oritech:pulverizer", "itemInputs": ["#c:ingots/" + material],
            "itemResults": [{"id": dust, "count": 1}], "time": 100}))
        tag("c", "ingots/" + material, [ingot])
        tag("c", "dusts/" + material, [dust])

    tag("c", "ingots", ["#c:ingots/rhevon", "#c:ingots/lumivex"])
    advanced_materials()
    crystal_powders()
    workshop_dust_tag()
    tag("rematter", "workshop_powders", ["rematter:rhevon_dust", "rematter:lumivex_dust", "rematter:virelite_blend"])
    tag("rematter", "technical_ceramics", ["rematter:virelite_ceramic"])
    recipe("sinter_furnace", {"type": "minecraft:crafting_shaped", "category": "misc",
                             "pattern": ["BXB", "KCK", "BFB"],
                             "key": {"B": "rematter:structural_bars", "K": "minecraft:brick",
                                     "C": "rematter:control_circuit", "F": "rematter:machine_frame",
                                     "X": "rematter:ember_matrix_crystal"},
                             "result": {"id": "rematter:sinter_furnace", "count": 1}})
    advancement("sinter_furnace", "rematter:machine_frame")


if __name__ == "__main__":
    main()
