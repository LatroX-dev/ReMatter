---
navigation:
  title: Powders, alloys and ceramic
  icon: rematter:sinter_furnace
  parent: index.md
  position: 6
item_ids:
  - rematter:sinter_furnace
  - rematter:iron_dust
  - rematter:copper_dust
  - rematter:gold_dust
  - rematter:rhevon_dust
  - rematter:rhevon_ingot
  - rematter:lumivex_dust
  - rematter:lumivex_ingot
  - rematter:virelite_blend
  - rematter:virelite_ceramic
---
# Mix a powder, make a material

The powered Material Preparator grinds and mixes physical ingredients. The **Sinter Furnace** presses, heats and cools that powder into a usable material. Both need external FE; neither needs a Matter Cell or material-network stock for these recipes.

## 1. Grind the base metals

Put one iron, copper or gold ingot in the Preparator's **left input** and leave the other inputs empty. It makes one matching <ItemLink id="rematter:iron_dust" />, <ItemLink id="rematter:copper_dust" /> or <ItemLink id="rematter:gold_dust" />. Each base-speed batch takes 4,000 FE and five seconds.

## 2. Use all three ingredient slots

The three slots under **Ingredients** run left to right. Their positions matter: primary material first, second material in the middle, refining fraction on the right. The result appears in the separate output slot.

| Left input | Middle input | Right input | Complete output |
| --- | --- | --- | --- |
| 3 Iron Dust | 1 Copper Dust | 1 Rhedon | 4 Rhevon Powder |
| 3 Copper Dust | 1 Gold Dust | 1 Lumin | 4 Lumivex Powder |
| 1 Silith | 1 Argil | 1 Virel | 4 Virelite Powder |

Hover the slot markers or recipe-viewer icons for ingredient counts and process details. These mixtures need the powered Material Preparator or a complete Industrial Preparator. At base speed each mixture takes 8,000 FE and eight seconds. The small refining fraction is part of the finished composite; there is no separate alloy-blend item.

## 3. Press, heat and cool

<RecipeFor id="rematter:sinter_furnace" />

Put one powder in the Sinter Furnace's single input. Leave room for one complete output. The machine shows the real input on its internal tray while the press and heating cycle run.

| Input | Output | Purpose | Base cost |
| --- | --- | --- | --- |
| <ItemLink id="rematter:rhevon_dust" /> | <ItemLink id="rematter:rhevon_ingot" /> | Rigid alloy for Cutters, Filters and Basins | 12,000 FE, 10 seconds |
| <ItemLink id="rematter:lumivex_dust" /> | <ItemLink id="rematter:lumivex_ingot" /> | Signal alloy for Optical Circuits | 12,000 FE, 10 seconds |
| <ItemLink id="rematter:virelite_blend" /> | <ItemLink id="rematter:virelite_ceramic" /> | Technical ceramic for insulated reaction machinery | 12,000 FE, 10 seconds |
| <ItemLink id="rematter:diamond_matrix_blend" /> | <ItemLink id="rematter:diamond_lattice" /> | Optical Circuits, upgrades and advanced machines | 12,000 FE, 10 seconds |
| <ItemLink id="rematter:ender_conductor_blend" /> | <ItemLink id="rematter:ender_conductor" /> | Larger Cells, Terminals, Interfaces and Ports | 16,000 FE, 12 seconds |
| <ItemLink id="rematter:netherite_sheath_blend" /> | <ItemLink id="rematter:netherite_sheath" /> | 1M Cell, Industrial Reactor and Resonant Cable | 24,000 FE, 14 seconds |

The last three blends are mixed in the Material Preparator; see [Crystals and advanced components](advanced.md). Sintering takes inspiration from powder metallurgy and ceramic firing: compacted grains bond under heat. ReMatter's field and signal properties are fictional. No added water or fluid connection is required.

An ordinary furnace or blast furnace can also turn Rhevon and Lumivex Powder into their ingots. Virelite uses the Sinter Furnace, or its optional Oritech route.

## Regrind the same alloy

One Rhevon or Lumivex Ingot can be ground back into **one powder of that same alloy** in the powered Preparator. It does not separate into the original ingredients or multiply the metal.

**Mix → alloy powder → ingot → the same alloy powder.** There is one powder per alloy, shared by preparation, heating and recycling. Virelite Ceramic has no equivalent regrinding recipe.

## Connect other machines

When installed, **Mekanism's Crusher** and **Oritech's Pulverizer** can regrind either alloy ingot. Oritech's Foundry accepts two matching powders and produces two matching ingots or ceramic pieces. Check EMI or JEI for the available machine recipes and their costs.

Hoppers and item pipes can supply ingredients and remove results when **Automation** is on. For AE2 or Refined Storage, connect a ReMatter Network Interface and enable **Network jobs** on the workers. The automatic recipe list uses the manufacturing route; reverse alloy-grinding recipes remain available for manually encoded processing patterns. See [Drive and automation](automation.md).
