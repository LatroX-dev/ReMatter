---
navigation:
  title: Drive and automation
  icon: rematter:matter_drive
  parent: index.md
  position: 3
item_ids:
  - rematter:matter_drive
  - rematter:matrix_cluster
  - rematter:matter_cell_256k
  - rematter:matter_cell_1m
  - rematter:matter_storage
  - rematter:matter_terminal
  - rematter:matter_pipe
---
# Stop carrying the Cell by hand

Once [sand-to-glass](copper.md) works, add a **Matter Drive** for continuous production. It holds the same Cell and makes its stock available to connected machines.

<RecipeFor id="rematter:matter_drive" />

## 1. Move the Cell into the Drive

Place Decomposer, Drive and Synthesizer in a touching row. Put the Cell in the Drive. **Leave the two processors' local Cell slots empty.**

An inserted local Cell always takes priority over a network, even when empty, full or unreadable. Removing it selects the connected storage instead. Check the source under **Details** before starting work.

The Drive needs no FE. Both processors still need their own FE connections.

<GameScene zoom="4" interactive={true}>
  <Block x="0" y="1" id="rematter:decomposer" />
  <Block x="1" y="1" id="rematter:matter_drive" />
  <Block x="2" y="1" id="rematter:material_synthesizer" />
  <BlockAnnotation x="0" y="1" color="#63d8bf">Decomposer: local Cell slot empty; use the connected Drive.</BlockAnnotation>
  <BlockAnnotation x="1" y="1" color="#e6b676">Insert your Cell here. The Drive replaces manual Cell transfers.</BlockAnnotation>
  <BlockAnnotation x="2" y="1" color="#8dcfff">Synthesizer: local Cell slot empty; choose Glass from the shared stock.</BlockAnnotation>
  <LineAnnotation from="0.5 1.5 1" to="0.5 1.5 2" color="#f7bb56" thickness="3">Keep external FE connected.</LineAnnotation>
  <LineAnnotation from="2.5 1.5 1" to="2.5 1.5 2" color="#f7bb56" thickness="3">Power the Synthesizer separately.</LineAnnotation>
</GameScene>

## 2. Add hoppers and chests

Put a downward hopper above the Decomposer and a chest above that hopper. Put another downward hopper below the Synthesizer and an output chest below it.

<GameScene zoom="3" interactive={true}>
  <Block x="0" y="2" id="rematter:decomposer" />
  <Block x="1" y="2" id="rematter:matter_drive" />
  <Block x="2" y="2" id="rematter:material_synthesizer" />
  <Block x="0" y="3" id="minecraft:hopper" p:facing="down" />
  <Block x="0" y="4" id="minecraft:chest" />
  <Block x="2" y="1" id="minecraft:hopper" p:facing="down" />
  <Block x="2" y="0" id="minecraft:chest" />
  <BlockAnnotation x="0" y="4" color="#63d8bf">Put sand in the input chest.</BlockAnnotation>
  <BlockAnnotation x="1" y="2" color="#e6b676">The Cell stays in the Drive.</BlockAnnotation>
  <BlockAnnotation x="2" y="0" color="#8dcfff">Collect glass here.</BlockAnnotation>
</GameScene>

You can dig space below the Synthesizer for its hopper and chest.

## 3. Start continuous production

Enable **Automation** in both processors' **Details**. Press **Start** on the Decomposer if it is paused.

On the Synthesizer, open **Choose recipe** or **Recipe**, select **Glass** and confirm with **Use recipe**. Then open **Details** and press **Run nonstop**. Put sand in the input chest. The line now produces glass without further menu clicks while input, power and space remain available.

Hoppers do not select recipes or request synthesis jobs. **Run nonstop** supplies the continuing requests. **Stop repeat** stops continuous production. **Pause** keeps the current order and **Resume** continues it; changing the product cancels the old request.

## Larger Cells

A 64k Cell holds **64,000 units** and uses one Matrix Crystal. A 256k Cell holds **256,000 units** and uses one Matrix Cluster. A 1M Cell holds **1,000,000 units** and uses four Clusters and an Optical Circuit.

A Cluster combines four Crystals, Rhedon and Virel. The Optical Circuit combines a Control Circuit, Lumin and glass. [Powered preparation](preparation.md) provides these additional fractions.

<RecipeFor id="rematter:matrix_cluster" />
<RecipeFor id="rematter:matter_cell_256k" />
<RecipeFor id="rematter:matter_cell_1m" />

Every Cell supports up to **512 substance/phase pairs**. Solids, liquids and gases share its space. Water and water vapor are different types.

Larger Cells are crafted empty. They do not consume your old filled Cell; swapping Cells does not combine their contents.

## Extend the network

Matter Pipes bridge gaps. A Terminal gives another view of the stock. Use **one Drive or original Matter Storage** per connected network, with at most 64 machines and pipes in loaded chunks. All connected machines must have the same owner.

<RecipeFor id="rematter:matter_pipe" />
<RecipeFor id="rematter:matter_terminal" />

Original **Matter Storage** blocks keep their built-in contents and need no Cell. They remain an alternative for existing installations.

Item and fluid automation cannot move Cells or upgrades. Owners can use buckets manually. Enable Automation only for trusted external connections; it does not share private menu access.
