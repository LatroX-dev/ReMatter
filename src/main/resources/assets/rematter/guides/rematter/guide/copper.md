---
navigation:
  title: Your first glass
  icon: minecraft:glass
  parent: index.md
  position: 2
item_ids:
  - rematter:decomposer
  - rematter:material_synthesizer
---
# Sand, a Cell, then glass

Use your [two-machine starter kit](setup.md), its **64k Matter Cell**, external power and **one sand**.

## 1. Load the Decomposer

Open the Decomposer. Put the Cell in its **Cell slot**, separate from the material input. Put the sand in the material input.

Inserting a Cell pauses the machine so you can check its source before processing. Press **Start**.

Wait for the cutter to finish. The sand is consumed only when its full result can be stored. Open **Details** to inspect the local stock: it now contains **Silica**.

## 2. Carry the Cell to the Synthesizer

Remove the Cell from the Decomposer and put it in the Synthesizer's **Cell slot**. Its silica travels with it.

The Cell does not stay active in both machines. Removing or inserting it pauses work. Open **Details** to check that the Synthesizer is using its local Cell.

## 3. Choose and make glass

1. Press **Choose recipe**, or **Recipe** if another product is already selected.
2. Search for **Glass**, then select its icon.
3. Inspect the required ingredients and output.
4. Press **Use recipe**.
5. Press **Start** to request one complete batch.

Take **one glass** from the output when the press finishes. Use a single **Start** request for this experiment; leave **Run nonstop** under **Details** unused.

**The same sand-derived silica has become a useful building material.** The Cell remains usable; the Crystal is not spent. It is not a free-copy recipe, and does not multiply the amount of material.

## What the numbers mean

With default recipes and no upgrades, one sand produces **1,000 solid silica units** using **8,000 FE** over **100 ticks**. One glass consumes those **1,000 units**, plus **16,000 FE** over **100 ticks**.

The Cell stores these quantities without separate silica inventory items. One batch of nuggets or fluid can have a different item count or volume; **Start requests the displayed complete batch**.

Server recipes can differ. Always use the machine's displayed requirements.

## Try another material later

The product list contains only approved outputs. Stored ingredients can be used for another listed recipe when its exact requirements are met. Knowing that recipe does not add missing stock.

For continuing production without moving the Cell by hand, see [Drive and automation](automation.md). If the first batch waits, check [the machine's status](help.md).
