---
navigation:
  title: Crystals and advanced components
  icon: rematter:diamond_lattice
  parent: index.md
  position: 7
item_ids:
  - rematter:amber_matrix_crystal
  - rematter:copper_matrix_crystal
  - rematter:mint_matrix_crystal
  - rematter:leaf_matrix_crystal
  - rematter:ice_matrix_crystal
  - rematter:ember_matrix_crystal
  - rematter:blue_matrix_crystal
  - rematter:violet_matrix_crystal
  - rematter:emerald_dust
  - rematter:lapis_dust
  - rematter:amethyst_dust
  - rematter:diamond_powder
  - rematter:diamond_matrix_blend
  - rematter:diamond_lattice
  - rematter:ender_pearl_powder
  - rematter:ender_conductor_blend
  - rematter:ender_conductor
  - rematter:netherite_powder
  - rematter:netherite_sheath_blend
  - rematter:netherite_sheath
---
# Crystals and advanced components

Each machine uses a colored Matrix Crystal. Later machines, larger Cells and faster cables also need three sintered components: the **Diamond Lattice**, the **Ender Conductor** and the **Netherite Sheath**. All of them need the powered Material Preparator; the components also need the [Sinter Furnace](metallurgy.md).

## Color a crystal

Combine one plain Matrix Crystal with one ingredient in a crafting grid.

| Ingredient | Crystal | Used in |
| --- | --- | --- |
| Gold Dust | <ItemLink id="rematter:amber_matrix_crystal" /> | Material Preparator, Industrial Preparator |
| Copper Dust | <ItemLink id="rematter:copper_matrix_crystal" /> | Decomposer |
| Diamond Powder | <ItemLink id="rematter:mint_matrix_crystal" /> | Matter Drive, Matter Terminal |
| Emerald Dust | <ItemLink id="rematter:leaf_matrix_crystal" /> | Material Synthesizer |
| Silith | <ItemLink id="rematter:ice_matrix_crystal" /> | Analyzer |
| Redstone Dust | <ItemLink id="rematter:ember_matrix_crystal" /> | Sinter Furnace |
| Lapis Lazuli Dust | <ItemLink id="rematter:blue_matrix_crystal" /> | Chemical Reactor, Network Interface |
| Amethyst Dust | <ItemLink id="rematter:violet_matrix_crystal" /> | Chemical Reactor, Industrial Reactor |

The Hand Preparator grinds gold and copper ingots. Gems need the powered Material Preparator. Dusts from other mods with the same common tag work as well.

<RecipeFor id="rematter:ice_matrix_crystal" />
<RecipeFor id="rematter:ember_matrix_crystal" />
<RecipeFor id="rematter:blue_matrix_crystal" />
<RecipeFor id="rematter:violet_matrix_crystal" />

## Grind the powders

Put the material in the Material Preparator's **left input** and leave the other inputs empty.

| Input | Output | Base cost |
| --- | --- | --- |
| Emerald | <ItemLink id="rematter:emerald_dust" /> | 4,000 FE, 5 seconds |
| Lapis Lazuli | <ItemLink id="rematter:lapis_dust" /> | 4,000 FE, 5 seconds |
| Amethyst Shard | <ItemLink id="rematter:amethyst_dust" /> | 4,000 FE, 5 seconds |
| Diamond | <ItemLink id="rematter:diamond_powder" /> | 6,000 FE, 6 seconds |
| Ender Pearl | <ItemLink id="rematter:ender_pearl_powder" /> | 8,000 FE, 7 seconds |
| Netherite Ingot | <ItemLink id="rematter:netherite_powder" /> | 12,000 FE, 9 seconds |

## Mix the blends

Fill the three ingredient slots from left to right. Each batch makes one blend.

| Left input | Middle input | Right input | Output | Base cost |
| --- | --- | --- | --- | --- |
| 1 Diamond Powder | 2 Matrix Blend | 1 Gold Dust | <ItemLink id="rematter:diamond_matrix_blend" /> | 8,000 FE, 8 seconds |
| 1 Ender Pearl Powder | 2 Lumivex Powder | 1 Copper Dust | <ItemLink id="rematter:ender_conductor_blend" /> | 10,000 FE, 9 seconds |
| 1 Netherite Powder | 2 Rhevon Powder | 1 Virelite Powder | <ItemLink id="rematter:netherite_sheath_blend" /> | 14,000 FE, 11 seconds |

AE2's Ender Dust can replace the Ender Pearl Powder. Lumivex, Rhevon and Virelite Powder come from [Powders, alloys and ceramic](metallurgy.md).

## Sinter the components

Put one blend in the Sinter Furnace's input.

| Blend | Component | Base cost | Used in |
| --- | --- | --- | --- |
| Diamond Matrix Blend | <ItemLink id="rematter:diamond_lattice" /> | 12,000 FE, 10 seconds | Analyzer, Chemical Reactor, Network Interface, Industrial Preparator, Optical Circuits, upgrades, Cutters, Filters, Foci, 1M Cell |
| Ender Conductor Blend | <ItemLink id="rematter:ender_conductor" /> | 16,000 FE, 12 seconds | 256k and 1M Cells, Matter Terminal, Network Interface, Machine Ports, Reinforced Matter Cable |
| Netherite Sheath Blend | <ItemLink id="rematter:netherite_sheath" /> | 24,000 FE, 14 seconds | 1M Cell, Industrial Reactor, Resonant Matter Cable |

Inputs are consumed only when the complete output fits. The Lattice, Conductor and Sheath stay physical parts; they never enter a Cell as stored matter.

## Other machines

When installed, Ender IO's SAG Mill and Oritech's Pulverizer grind diamonds and netherite ingots into these powders, and Mekanism's Crusher grinds ender pearls. Oritech's Assembler mixes the three blends, and its Foundry turns two matching blends into two components. With Ender IO, nine Powdered Ender Pearl fragments replace one Ender Pearl Powder in the Material Preparator. Check EMI or JEI for the exact recipes and costs.
