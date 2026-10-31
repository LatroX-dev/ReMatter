---
navigation:
  title: The copper loop
  icon: rematter:decomposer
  parent: index.md
  position: 3
item_ids:
  - rematter:decomposer
  - rematter:material_synthesizer
---
# The copper loop

This first experiment recovers one copper ingot from its own material. Both processing steps consume FE; the loop creates no extra copper.

## 1. Decompose the ingot

Connect and power a <ItemLink id="rematter:decomposer" />. Place the ingot from your Analyzer in its input slot. When the process finishes, the ingot is removed and the connected storage gains **1,000 copper units**. Open the storage or terminal to check the amount.

<RecipeFor id="rematter:decomposer" />

Pause after one item while learning. A running machine repeats whenever its inputs, energy and output space allow another operation.

## 2. Synthesize copper

Open a connected, powered <ItemLink id="rematter:material_synthesizer" />. Press **Select**, search for **Copper Ingot**, and choose the result. Then press Run. The process consumes 1,000 copper units and produces one ingot in the right-hand output slot. The arrows also step through the available processes.

<RecipeFor id="rematter:material_synthesizer" />

An ordinary copper block represents 9,000 copper units. Raw copper can be decomposed, but it is not a synthesis output. Use the selected material's live recipe for the current FE and time costs; servers may customize profiles.

Small forms use whole batches: nine supported copper nuggets represent the same 1,000 units as one ingot. Synthesis needs room for all nine output items. Partial batches wait without consuming items, substances or FE.

## 3. Continue normal manufacturing

Turn the recovered ingot into wires, plates or components using the crafting and machine recipes that normally make those products. ReMatter does not synthesize finished components or machines.

The machine waits when storage is full, ingredients are missing, energy is insufficient, or its output is blocked. It does not consume the input before the whole operation can complete.
