---
navigation:
  title: Crystal power
  icon: rematter:crystal_generator
  parent: index.md
  position: 18
item_ids:
  - rematter:crystal_generator
  - rematter:industrial_crystal_generator
---
# Consume loose crystals for power

After powered preparation is available, a Crystal Generator turns spare Matrix Crystals into a small FE supply. The crystal is **consumed completely**. It is not the reusable crystal installed inside a machine or cell.

<RecipeFor id="rematter:crystal_generator" />
<RecipeFor id="rematter:industrial_crystal_generator" />

Standalone definitions allow the base Matrix Crystal and eight coloured Matrix Crystals at **1,024 FE each**. Colour stays primarily a functional device key. Rarity does not increase fuel yield.

| Generator | Generation | Energy per permitted crystal |
| --- | --- | --- |
| Crystal Generator | 64 FE/t | 1,024 FE |
| Industrial Crystal Generator | 256 FE/t | 1,024 FE |

Insert permitted fuel and connect an enabled power output. The industrial model buys power per tick, not better yield. Other FE sources remain usable.

Unspent crystal energy stays separate from the output buffer. Full output pauses and does not take another crystal. Moving or reloading a valid generator preserves that one balance. A fuel reload does not reprice consumed fuel. No spent crystal remains to reclaim in addition to its FE.

**Primal Cells and Primal Crystals are not fuel.** Packs may disable the explicit fuel entries. ReForging leaves all nine disabled until its actual production and fuel paths receive a separate audit. The standalone own-recipe calculation does not prove every foreign renewable source or recipe loop safe.
