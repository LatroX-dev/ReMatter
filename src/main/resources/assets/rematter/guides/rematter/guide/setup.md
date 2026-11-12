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
  - rematter:structural_bars
  - rematter:machine_frame
  - rematter:control_circuit
  - rematter:cell_rack
  - rematter:cell_housing
  - rematter:matter_cell_64k
---
# Before you start

Plan an early midgame workshop with iron, copper, redstone, gold, sand, clay, glass and an external FE source. The complete starter storage line needs **one diamond for the Drive** and **three emeralds for the Synthesizer**. Glass teaches the controls; silicon, bronze and steel are later practical goals. The first Hand Preparator itself needs no power.

# Make the matrix by hand

The first Hand Preparator needs no FE, Circuit, Crystal, Cell, Drive or Analyzer. A furnace completes its crystal feedstock.

## 1. Build the Hand Preparator

Craft a **Hand Preparator** from **two flint, five iron ingots and two copper ingots**. It runs by hand without an FE connection.

<RecipeFor id="rematter:hand_preparator" />

<GameScene zoom="4" interactive={true}>
  <Block x="0" y="1" id="rematter:hand_preparator" />
  <Block x="2" y="1" id="minecraft:furnace" />
  <BlockAnnotation x="0" y="1" color="#e6b676">Hand Preparator: insert feedstock and press Crank. No FE required.</BlockAnnotation>
  <BlockAnnotation x="2" y="1" color="#f7bb56">Furnace: smelt Matrix Blend into Matrix Crystal with ordinary fuel.</BlockAnnotation>
</GameScene>

## 2. Extract, mix and heat

Put **one sand** in the left input, leave the other ingredient slots empty and press the **Crank** symbol until the batch yields **one Silith**. Collect it. Repeat with **one clay ball** to obtain **one Argil**.

Now put **one Silith in the primary input and one Argil in the second input**. Crank the mixing batch to make **four Matrix Blend**.

**Sand → Silith · Clay Ball → Argil · Silith + Argil → 4 Matrix Blend**

No added water or fluid connection is needed. Grinding and sieving prepare the grains; they do not turn the feedstock into chemically pure elements.

Smelt each <ItemLink id="rematter:matrix_blend" /> in a normal furnace to make one <ItemLink id="rematter:matrix_crystal" />. Make nine Crystals for the starter line. The Cell uses one plain Crystal, and the four machines use eight colored variants.

<RecipeFor id="rematter:matrix_crystal" />

The installed Crystal stays reusable and is not consumed per operation. A loose Crystal is consumed only when deliberately used as permitted generator fuel.

Combine iron and Silith for <ItemLink id="rematter:structural_bars" />. Make <ItemLink id="rematter:machine_frame" /> from those Bars, iron and Matrix Blend. The <ItemLink id="rematter:control_circuit" /> uses copper, redstone, Argil and Matrix Blend. Follow the displayed grids.

<RecipeFor id="rematter:structural_bars" />
<RecipeFor id="rematter:machine_frame" />
<RecipeFor id="rematter:control_circuit" />

## 3. Craft the machines and Cell

Grind gold ingots in the Hand Preparator and combine the Gold Dust with two plain Crystals to make two **Amber Matrix Crystals**. Use them to craft a **Material Preparator**, then connect it to external FE. This powered machine can grind diamond and emerald.

Make the colored Crystals for the other machines by combining a plain Matrix Crystal with the corresponding dust. Use **Copper Dust** for the Decomposer's two Copper Crystals, **Diamond Powder** for the Matter Drive's Mint Crystal, and **Emerald Dust** for the Synthesizer's three Leaf Crystals. Copper ingots can also be ground in the Hand Preparator. [Crystals and advanced components](advanced.md) lists every crystal color and the machines that use it.

Make a **Cell Housing** from Structural Bars and glass. Stack **Control Circuit → Matrix Crystal → Cell Housing**, top to bottom, to make the 64k Cell. Make **Cell Racks** from Structural Bars, Control Circuits and Silith. Each Rack has three bays, and the Drive uses two Racks.

<RecipeFor id="rematter:amber_matrix_crystal" />
<RecipeFor id="rematter:material_preparator" />
<RecipeFor id="rematter:copper_matrix_crystal" />
<RecipeFor id="rematter:mint_matrix_crystal" />
<RecipeFor id="rematter:leaf_matrix_crystal" />
<RecipeFor id="rematter:decomposer" />
<RecipeFor id="rematter:material_synthesizer" />
<RecipeFor id="rematter:cell_housing" />
<RecipeFor id="rematter:matter_cell_64k" />
<RecipeFor id="rematter:cell_rack" />
<RecipeFor id="rematter:matter_drive" />

## 4. Connect the starter line

Place the **Decomposer, Drive and Synthesizer in a touching row**. Insert the Cell into any of the Drive's six Contents slots. Start with one; add more Cells when needed. The processors use its shared material stock; their own input and output slots remain available for ordinary items.

<GameScene zoom="4" interactive={true}>
  <Block x="0" y="1" id="rematter:decomposer" />
  <Block x="1" y="1" id="rematter:matter_drive" />
  <Block x="2" y="1" id="rematter:material_synthesizer" />
  <BlockAnnotation x="0" y="1" color="#63d8bf">Decomposer: put sand in the input, check the connected storage, then Start.</BlockAnnotation>
  <BlockAnnotation x="1" y="1" color="#63d8bf">Drive: the Cell stays here and serves both touching machines. No FE needed.</BlockAnnotation>
  <BlockAnnotation x="2" y="1" color="#8dcfff">Synthesizer: choose Glass, confirm Use recipe, then Start. It uses the Drive's stock.</BlockAnnotation>
  <LineAnnotation from="0.7 1.7 0" to="2.3 1.7 0" color="#63d8bf" thickness="3">Touching machines share the Drive. The line marks the connection.</LineAnnotation>
  <LineAnnotation from="0.5 1.5 1" to="0.5 1.5 2" color="#f7bb56" thickness="3">External FE connection for the Decomposer.</LineAnnotation>
  <LineAnnotation from="2.5 1.5 1" to="2.5 1.5 2" color="#f7bb56" thickness="3">External FE connection for the Synthesizer.</LineAnnotation>
</GameScene>

Connect an external generator or charged energy store to the powered processors through Matter Cables or compatible FE transport. The orange lines show example power connections. Use the rear or another eligible port for Matter Cables to leave the front clear. The touching row shares matter stock without any cable between the machines.

**Next: [Make your first glass](copper.md).**
