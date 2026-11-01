---
navigation:
  title: Craft the starter kit
  icon: rematter:matrix_crystal
  parent: index.md
  position: 1
item_ids:
  - rematter:hand_preparator
  - rematter:matrix_blend
  - rematter:matrix_crystal
  - rematter:machine_frame
  - rematter:control_circuit
  - rematter:matter_cell_64k
---
# Make the matrix by hand

The first Hand Preparator needs no FE, Circuit, Crystal, Cell, Drive or Analyzer. A furnace completes its crystal feedstock.

## 1. Build the Hand Preparator

Craft a **Hand Preparator** from **two flint, one iron bars item, three planks and one stick**.

<RecipeFor id="rematter:hand_preparator" />

<GameScene zoom="4" interactive={true}>
  <Block x="0" y="1" id="rematter:hand_preparator" />
  <Block x="2" y="1" id="minecraft:furnace" />
  <BlockAnnotation x="0" y="1" color="#e6b676">Hand Preparator: insert feedstock and press Crank. No FE required.</BlockAnnotation>
  <BlockAnnotation x="2" y="1" color="#f7bb56">Furnace: smelt Matrix Blend into Matrix Crystal with ordinary fuel.</BlockAnnotation>
</GameScene>

## 2. Extract, mix and heat

Put **one sand** in the primary input, leave the second input empty and press **Crank** until the batch yields **one Silith**. Collect it. Repeat with **one clay ball** to obtain **one Argil**.

Now put **one Silith in the primary input and one Argil in the second input**. Crank the mixing batch to make **four Matrix Blend**.

**Sand → Silith · Clay Ball → Argil · Silith + Argil → 4 Matrix Blend**

No added water or fluid connection is needed. Grinding and sieving prepare the grains; they do not turn the feedstock into chemically pure elements.

Smelt each <ItemLink id="rematter:matrix_blend" /> in a normal furnace to make one <ItemLink id="rematter:matrix_crystal" />. You need one Crystal for the first Cell and another for the Synthesizer.

<RecipeFor id="rematter:matrix_crystal" />

The Crystal stays in its device. It is not fuel and is not consumed each time you process material.

Four iron ingots make a <ItemLink id="rematter:machine_frame" />. Copper, redstone and paper make a <ItemLink id="rematter:control_circuit" />.

<RecipeFor id="rematter:machine_frame" />
<RecipeFor id="rematter:control_circuit" />

## 3. Craft the machines and Cell

The Decomposer uses a piston, iron, a Frame and a Circuit. The Synthesizer uses a piston, glass, a Crystal, a Frame and a Circuit.

The 64k Cell uses **two iron ingots, two glass, one Circuit and one Crystal**. Its housing and matrix hold the recovered ingredients together.

<RecipeFor id="rematter:decomposer" />
<RecipeFor id="rematter:material_synthesizer" />
<RecipeFor id="rematter:matter_cell_64k" />

## 4. Place and power both machines

The machines do not need to touch for this manual setup. You carry the Cell between them.

<GameScene zoom="4" interactive={true}>
  <Block x="0" y="1" id="rematter:decomposer" />
  <Block x="2" y="1" id="rematter:material_synthesizer" />
  <BlockAnnotation x="0" y="1" color="#63d8bf">Decomposer: Cell in the dedicated Cell slot, sand in the material input, then Start.</BlockAnnotation>
  <BlockAnnotation x="2" y="1" color="#8dcfff">Synthesizer: insert that same Cell, choose Glass, then Make once.</BlockAnnotation>
  <LineAnnotation from="0.7 1.7 0" to="2.3 1.7 0" color="#e6b676" thickness="3">Move the filled Cell by hand. This line is an instruction, not a pipe.</LineAnnotation>
  <LineAnnotation from="0.5 1.5 1" to="0.5 1.5 2" color="#f7bb56" thickness="3">External FE connection for the Decomposer.</LineAnnotation>
  <LineAnnotation from="2.5 1.5 1" to="2.5 1.5 2" color="#f7bb56" thickness="3">External FE connection for the Synthesizer.</LineAnnotation>
</GameScene>

Connect an external generator or charged energy storage through compatible FE cables. ReMatter does not include a generator. The orange lines show example connections, not new ReMatter blocks.

Any exposed machine face accepts FE. The back leaves the front accessible. No Drive or Matter Pipe is required yet.

**Next: [Make your first glass](copper.md).**
