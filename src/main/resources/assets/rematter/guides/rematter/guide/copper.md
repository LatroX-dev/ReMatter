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

Use your [starter line](setup.md), its **64k Matter Cell in the Drive**, external power and **one sand**.

## 1. Load the Decomposer

Open the Decomposer. Put sand in its material input. The adjacent Drive supplies storage through the Cell installed there.

Check the storage source in **Configuration**, then return to **Process** and press the triangle whose tooltip reads **Start**.

Wait for the cutter to finish. The sand is consumed only when its full result can be stored. Open the Drive's **Contents** tab: it now contains **Silica**.

## 2. Keep the Cell in the Drive

Leave the Cell installed. The touching Synthesizer can use the same silica without moving an item or Cell between machines.

Open the Synthesizer's **Configuration** tab to check its connected storage, then return to **Process**. If you change the Cell or network, check the new source before resuming.

## 3. Choose and make glass

1. Press **Choose recipe**, or **Recipe** if another product is already selected.
2. Search for **Glass**, then select its icon.
3. Inspect the required ingredients and output.
4. Press **Use recipe**.
5. Press **Start** to request one complete batch.

Take **one glass** from the output when the press finishes. Use a single **Start** request for this experiment; leave **Run nonstop** under **Configuration** unused. Hover the recipe and action symbols to see their full labels.

**The same sand-derived silica has become a useful building material.** The Cell remains usable; the Crystal is not spent. It is not a free-copy recipe, and does not multiply the amount of material.

## What the numbers mean

With default recipes and no upgrades, one sand produces **1,000 solid silica units** using **8,000 FE** over **100 ticks**. One glass consumes those **1,000 units**, plus **16,000 FE** over **100 ticks**.

The Cell stores these quantities without separate silica inventory items. One batch of nuggets or fluid can have a different item count or volume; **Start requests the displayed complete batch**.

Server recipes can differ. Always use the machine's displayed requirements.

## Try another material later

The product list contains only approved outputs. Stored ingredients can be used for another listed recipe when its exact requirements are met. Knowing that recipe does not add missing stock.

For continuing production with hoppers or a larger network, see [Drive and automation](automation.md). If the first batch waits, check [the machine's status](help.md).
