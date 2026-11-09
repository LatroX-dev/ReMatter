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
  - rematter:matter_terminal
  - rematter:matter_cable
  - rematter:reinforced_matter_cable
  - rematter:resonant_matter_cable
  - rematter:network_interface
  - rematter:automation_pattern
  - rematter:recovered_matter
---
# Grow the shared material network

Your [sand-to-glass starter line](copper.md) already uses a **Matter Drive**. Add item transport for continuous production, then more Drives when you need capacity. Each Drive holds up to six individually removable Cells in two columns of three. Its populated front bays match the Contents slots, and connected machines share all readable Cells.

<RecipeFor id="rematter:matter_drive" />

## 1. Keep the Cell in storage

Place Decomposer, Drive and Synthesizer in a touching row. Put the Cell in any of the Drive's six **Contents** slots. Check the connected stock in **Configuration** before starting work.

The Drive needs no FE. Both processors still need their own FE connections.

<GameScene zoom="4" interactive={true}>
  <Block x="0" y="1" id="rematter:decomposer" />
  <Block x="1" y="1" id="rematter:matter_drive" />
  <Block x="2" y="1" id="rematter:material_synthesizer" />
  <BlockAnnotation x="0" y="1" color="#63d8bf">Decomposer: its input accepts sand; the connected Drive stores the recovered material.</BlockAnnotation>
  <BlockAnnotation x="1" y="1" color="#63d8bf">Insert your Cell here. It serves both processors.</BlockAnnotation>
  <BlockAnnotation x="2" y="1" color="#8dcfff">Synthesizer: choose Glass from the shared stock.</BlockAnnotation>
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

Enable **Automation** in both processors' **Configuration** tabs. Hover its symbol to read the explanation; the small On/Off label shows the setting. Press **Start** on the Decomposer if it is paused.

On the Synthesizer, open **Choose recipe** or **Recipe**, select **Glass** and confirm with **Use recipe**. Then open **Configuration** and use **Run nonstop**. Hover the circular-arrow symbol to find it. Put sand in the input chest. The line now produces glass while input, power and space remain available.

Hoppers do not select recipes or request synthesis jobs. **Run nonstop** supplies the continuing requests. **Stop repeat** stops continuous production. **Pause** keeps the current order and **Resume** continues it; changing the product cancels the old request.

## Larger Cells

A 64k Cell holds **64,000 units** and uses a Matrix Crystal and a Control Circuit. A 256k Cell holds **256,000 units** and uses a Matrix Cluster, a Control Circuit and two Ender Conductors. A 1M Cell holds **1,000,000 units** and uses four Clusters, an Optical Circuit, a Diamond Lattice, an Ender Conductor and a Netherite Sheath.

A Cluster combines four Crystals with Rhedon, Virel, Structural Bars and Matrix Blend. An Optical Circuit uses Lumivex, glass, a Control Circuit and Diamond Lattices. Each Cell also needs a separately crafted Cell Housing.

<RecipeFor id="rematter:matrix_cluster" />
<RecipeFor id="rematter:optical_circuit" />
<RecipeFor id="rematter:matter_cell_256k" />
<RecipeFor id="rematter:matter_cell_1m" />

Every Cell supports up to **512 substance/phase pairs**. Solids, liquids and gases share its space. Water and water vapor are different types.

Crafting a new Cell gives you an empty Cell. Installed Cells keep their own contents, and the Drive pools their available stock.

## Extend the network

Matter Cables carry matter-network traffic and forward FE that an external generator or energy store pushes into them. They store no FE, and nothing can pull FE out through a cable. The network supports up to **64 Drives** in loaded chunks. The lowest cable tier in the network sets its total node limit, counting machines and the cables on the routes between them: **256** for Matter Cable, **384** for Reinforced and **512** for Resonant. Cable branches that reach none of your machines do not count. If the network grows past that limit, cables of the weakest tier and the devices only they reach are left out; the stronger part keeps working. A cable system larger than 4,096 connected blocks is too large.

A network includes only your own machines; other players' machines on the same cables stay separate. Cables belong to their placer. Only the owner or an operator may remove them. Cable traversal and pushed FE remain within that owner's devices. On a shared server, protect your base with a claim mod.

Connect Matter Cables to eligible rear, side or bottom ports. The Matter Terminal connects at the rear. In the Terminal's network view, open connected device menus or queue a selected recipe on a compatible worker. Use separate transport for physical items and ordinary fluids.

Stock views in the Terminal, Drive and Network Interface have a search field and **Solids**, **Liquids**, **Gases** and **Favorites** filters. Mark a substance as a favorite in its details; favorites are listed first. A view lists up to 512 types, matches and favorites first, and shows how many of the stored types it lists.

<RecipeFor id="rematter:matter_cable" />
<RecipeFor id="rematter:reinforced_matter_cable" />
<RecipeFor id="rematter:resonant_matter_cable" />
<RecipeFor id="rematter:matter_terminal" />

Item and fluid automation cannot move Cells or upgrades. Owners can use buckets manually. Enable Automation only for trusted external connections; it does not share private menu access.

## AE2 and Refined Storage requests

Connect a **ReMatter Network Interface** to the material network and to the chosen external system. In its **Configuration**, select the backend and turn **Automation** on. Then enable **Network jobs** on the powered workers that should accept requests. Hover each network symbol for its full label. Use a separate Interface for AE2 and Refined Storage when both share the same ReMatter installation.

The connected systems can request supported recipes from available workers. They still need the complete ingredients, power and output space. Substance transfers through it are limited to its owner. Every player who can use the connected AE2 or Refined Storage network can request its recipes, so keep that network private. AE2 and Refined Storage cables connect only to its **Automatic** and **Matter Network** faces, and AE2 cables placed against it by another player do not connect. The automatic alloy catalogue offers powder manufacture and ingot production; it omits reverse alloy grinding so that a request for powder does not depend on first making its own ingot. Reverse grinding remains usable in physical machines and manually encoded processing patterns.

If a recipe changes, outdated jobs return their inputs to the external network. If the external network does not confirm a delivery, the Interface stops it and keeps its resources. Press **Release** in **Configuration** to receive them after a confirmation: matter arrives as <ItemLink id="rematter:recovered_matter" />, which you use on your Drive to store it again; fluids go into the Interface's tank.

<RecipeFor id="rematter:network_interface" />

## Ownership and teams

Place every machine and Matter Cable for one stock network as the same owner. Foreign and unowned cables do not connect. FE travels only to the cable owner's devices. Current FTB Teams members may operate machines, but removal, disposal settings and held-delivery recovery remain owner/operator actions. Team membership does not merge stock networks or share advancements.

Use **Production settings** for a hard stock target. Matter processes count the selected substance and phase across the connected ReMatter network. Physical outputs count only the local output buffer. Set a multiple of the complete batch yield. AE2 and RS stock is outside this target.
