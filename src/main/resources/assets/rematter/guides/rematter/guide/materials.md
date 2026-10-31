---
navigation:
  title: Materials and batches
  icon: minecraft:iron_nugget
  parent: index.md
  position: 4
---
# Materials and batches

Press **Select** in a processing machine to open its process list. Search by a material name or an exact identifier such as `minecraft:iron_ingot`. The list includes only processes available to that machine in the server's current catalogue.

## Whole batches

Nine supported metal nuggets contain the same stored quantity as one ingot. The Decomposer waits for all nine; the Synthesizer needs room for all nine outputs. Nothing is consumed while a complete operation cannot finish.

The Analyzer can inspect a single nugget. Its **Amounts per 9 items** label means the displayed quantities describe the processing batch, even though your single sample stays in the input slot. Check the recipe viewer for the exact batch of another material; some powders and storage blocks also have special ratios.

## Materials with different identities

Quartz crystals and ordinary silica are separate stocks. Diamond and graphite also remain separate, even though both contain carbon. An alloy's stored composition does not automatically provide a recipe for recovering each constituent.

Unrefined and refined materials may need different processes. For example, refined obsidian dust is a precursor; its stored material does not directly synthesize the finished ingot. Continue that step in the original machine.

Ore and raw-ore profiles recover a conservative amount of material. ReMatter does not synthesize ore blocks, and recovery does not reproduce Fortune, washing, or other ore-processing bonuses. Fuel, charged crystals, and special progression resources only work where an explicit profile and permission exist.

## Server changes

If a selected process becomes unavailable after a catalogue change, the machine pauses. Choose another process deliberately and press Run. Existing stored quantities retain their substance identities; similar names or formulas never convert a stock automatically.
