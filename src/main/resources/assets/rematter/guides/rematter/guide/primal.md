---
navigation:
  title: Primal Matter and cells
  icon: rematter:primal_cell_64k
  parent: index.md
  position: 14
item_ids:
  - rematter:primal_shard
  - rematter:deepslate_primal_ore
  - rematter:nether_primal_ore
  - rematter:end_primal_ore
  - rematter:primal_cell_16k
  - rematter:primal_cell_64k
  - rematter:primal_cell_256k
  - rematter:primal_converter
  - rematter:primal_crystal
---
# A deliberately separate reserve

Primal Matter is a rare one-way resource, never a replacement silently pulled into ordinary manufacturing. Ordinary Matter Cells reject it; **Primal Cells hold only Primal Matter**. A Drive can hold both types, with separate totals. Ordinary processors see only ordinary stock.

| Source | Where it appears |
| --- | --- |
| Deepslate Primal Ore | New Overworld terrain, Deepslate at y −60 to −24 |
| Nether Primal Ore | New Nether terrain, Netherrack at y 16 to 96 |
| End Primal Ore | New outer End islands; the central island is excluded |

Mine with iron tools or better. One ore drops **one Shard**; Fortune adds none. Silk Touch keeps one ore block. Either source loads exactly **1,000 u**, with no ore-processing bonus. Existing chunks are not refilled. Search time and actual find rates depend on terrain; different dimensions are not promised identical yields.

## Load and transfer

<RecipeFor id="rematter:primal_cell_16k" />
<RecipeFor id="rematter:primal_cell_64k" />
<RecipeFor id="rematter:primal_cell_256k" />

The cells hold 16,000, 64,000 or 256,000 u. In the inventory, **right-click a Primal Cell with Shards, Silk-Touch ore or another Primal Cell**. Whole source items load only when they fit; cell transfer preserves the exact quantity and retains the empty source cell. New cell recipes use fresh housings and never consume a filled cell.

Analyzer and Converter pay only from their own payment slot. Nearby Drive reserves are not automatically spent. Ordinary Decomposer profiles, AE2/RS crafting and chemical recipes cannot refill Primal from other matter.

## Explicit conversion

<RecipeFor id="rematter:primal_converter" />

Choose a **known, useful, explicitly allowed solid element**, a finite quantity, and Start. Connect ordinary output storage and provide FE. Work waits for stock, energy, transfer work and output room. It creates no research and no finished machine or component.

| Target class | Primal for 1,000 u result | Base FE / duration |
| --- | --- | --- |
| Common | 1,000 u | 64,000 FE /20 seconds |
| Scarce | 2,000 u | 96,000 FE /25 seconds |
| Invented | 4,000 u | 128,000 FE /30 seconds |

Choose multiples of 250 u. Read exact output and price in the window; splitting does not improve the rate. There is no Repeat mode, external crafting pattern or automatic missing-ingredient replacement. Ordinary material never converts back into Primal.

## Optional workshop

An Industrial Preparator with **Precision 4** stabilizes 4 Shards and 1 Matrix Crystal into 1 Primal Crystal. Read actual FE and duration in its preview. Primal Crystals support the [Discovery Lens](discovery.md), 256k reserve and [Entangled Link](links.md). They are not fuel or reclaimable Primal. Ordinary progression does not require them.
