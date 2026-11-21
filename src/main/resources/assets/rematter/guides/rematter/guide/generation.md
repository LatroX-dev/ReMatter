---
navigation:
  title: Crystal power
  icon: rematter:crystal_generator
  parent: index.md
  position: 19
item_ids:
  - rematter:crystal_generator
  - rematter:industrial_crystal_generator
---
# Consume loose crystals for power

Build your first Crystal Generator before powering a machine. In the Hand Preparator, mix a plain Matrix Crystal with redstone to make the **Ember Matrix Crystal**. At the crafting table, put the Ember crystal at the top center, a Machine Frame between two copper blocks in the middle, and a Control Circuit between two Structural Bars at the bottom.

Place the side or rear of the generator against an enabled power input. Insert a loose Matrix Crystal as fuel. A plain crystal provides **768 FE**. The Ember crystal built into the generator stays in place. Use Matter Cables when you want to reach more machines.

<RecipeFor id="rematter:crystal_generator" />
<RecipeFor id="rematter:industrial_crystal_generator" />

Standalone and ReForging definitions allow the base Matrix Crystal and all eight coloured Matrix Crystals, yielding **768 to 2,816 FE** depending on the pigment tier (`matrix_crystal` 768 FE, Ice 896 FE, Copper 1,024 FE, Amber 1,280 FE, Ember 1,408 FE, Violet 1,792 FE, Blue 2,048 FE, Leaf 2,304 FE, Mint 2,816 FE). Every fuel yield stays strictly below its own synthesis and pigment processing cost.

| Generator | Generation | Energy per permitted crystal |
| --- | --- | --- |
| Crystal Generator | 128 FE/t | 768 to 2,816 FE |
| Industrial Crystal Generator | 512 FE/t | 768 to 2,816 FE |

Insert permitted fuel and connect an enabled power output. The industrial model buys power per tick, not better yield. Other FE sources remain usable.

Unspent crystal energy stays separate from the output buffer. Full output pauses and does not take another crystal. Moving or reloading a valid generator preserves that one balance. A fuel reload does not reprice consumed fuel. No spent crystal remains to reclaim in addition to its FE.

**Primal Cells and Primal Crystals are not fuel.** Packs may override or disable individual fuel entries via `data/rematter/rematter/crystal_fuel/*.json`.
