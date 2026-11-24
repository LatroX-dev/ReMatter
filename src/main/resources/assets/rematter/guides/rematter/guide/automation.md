---
navigation:
  title: Drive and automation
  icon: rematter:matter_drive
  parent: index.md
  position: 4
item_ids:
  - rematter:cell_transfer_station
  - rematter:matter_drive
  - rematter:matrix_cluster
  - rematter:matter_cell_16m
  - rematter:matter_cell_64m
  - rematter:matter_cell_256m
  - rematter:void_matter_cell
  - rematter:pressurized_canister
  - rematter:matter_terminal
  - rematter:wireless_access_point
  - rematter:wireless_booster
  - rematter:wireless_matter_terminal
  - rematter:matter_level_emitter
  - rematter:configuration_card
  - rematter:security_card
  - rematter:matter_wrench
  - rematter:network_interface
  - rematter:automation_pattern
  - rematter:recovered_matter
---
# Grow the shared material network

Your [sand-to-glass starter line](copper.md) already uses a **Matter Drive**. Add item transport for continuous production, then more Drives when you need capacity. Each Drive holds up to six individually removable Cells in two columns of three. Its populated front bays match the Contents slots, and connected machines share all readable Cells.

Attach transport to the sides, back or bottom. Device fronts and open workbench tops never connect. A sealed device's top starts **Disabled**; enable it in **Settings → Sides** when needed. Disabled faces are plain casing, with no socket. The side diagram labels each face and shows its role: left-click cycles forward; right-click or Shift-click cycles backward. Energy Cubes are the exception with six configurable faces; a Matter Terminal connects only at its back. Industrial controllers use separate Machine Ports.

<RecipeFor id="rematter:matter_drive" />

## 1. Keep the Cell in storage

Place Decomposer, Drive and Synthesizer in a touching row. Put the Cell in any of the Drive's six **Contents** slots. Check the connected stock in **Settings** before starting work.

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

Enable **Automation** in both processors' **Settings** tabs. Hover its symbol to read the explanation; the small On/Off label shows the setting. Compact processors with physical outputs also offer an optional **Auto-Eject** toggle in **Settings → Access** (off by default): when enabled together with Automation, finished items and fluids push automatically into adjacent inventories or tanks on faces explicitly set to **Item Output** or **Fluid Output** — and the Configuration Card copies this toggle alongside side modes. Press **Start** on the Decomposer if it is paused.

On the Synthesizer, open **Choose recipe** or **Recipe**, select **Glass** and confirm with **Use recipe**. Then enable **Loop** beside **Make 1** on the main view. Put sand in the input chest. The line now produces glass while input, power and space remain available.

Hoppers do not select recipes or request synthesis jobs. **Loop** supplies the continuing requests. Toggle it off to stop continuous production. **Pause** keeps the current order and **Resume** continues it; changing the product cancels the old request.

## Sort and transfer Cells

The **Cell Transfer Station** holds **one Source Cell on the left and one Target Cell on the right**. Place both Cells in the dock (either in the menu or by right-clicking the left/right bay in the world) and press **Transfer** to move contents, or **Swap** to exchange both bays in one click.

By default, **Transfer** moves every compatible substance (including Primal Matter between Primal Cells). If you only want to transfer specific substances from a mixed Source Cell, click their badges in the Source Contents box below the dock; click **All** to return to transferring everything. Hover the status line for exact ordinary and Primal amounts and the last transfer. Only what fits moves, and the Source Cell keeps every remainder.

<RecipeFor id="rematter:cell_transfer_station" />

## Configure what a Cell stores

Open the **Cell Filter** tab on the left rail and choose **Source Cell** or **Target Cell** to edit any Matter Cell or Void Cell in place. Search the catalogue and click badges to whitelist the desired substance/phase pairs. These are ghost filters, so you do not need an inventory item for the substance and no resources are consumed. **Contents** copies exactly the types currently stored in that Cell; **All** removes the restriction; **Clear** empties the whitelist so nothing is accepted.

The partition is saved on the Cell and works after moving it into a **Matter Drive**, including connected processor outputs and Primal Converter credits. Filters affect future insertion. Existing quantities remain safe and extractable even if their types no longer match the whitelist. Unknown stored substances keep their IDs and quantities. Primal Cells have a fixed resource and need no substance whitelist.

Primal Cells also retain their inventory gesture for loading Shards or ore and moving their separate reserve. Ordinary substance transfers use the Station.

## Larger Cells

A 4M Cell holds **4,000,000 units** (4,000 ingots or buckets) and uses a Matrix Crystal and a Control Circuit. A 16M Cell holds **16,000,000 units** and uses a Matrix Cluster, a Control Circuit and two Rhevon Ingots. A 64M Cell holds **64,000,000 units** and uses two Clusters, an Optical Circuit, a Diamond Lattice and two Ender Conductors. A 256M Cell holds **256,000,000 units** and combines a Mint Matrix Crystal, two Clusters, two Optical Circuits, two Ender Conductors and a Netherite Sheath. A **Void Matter Cell** accepts whitelisted substance overflow and safely discards excess amounts once full.

A Cluster combines four Crystals with Rhedon, Virel, Structural Bars and Matrix Blend. An Optical Circuit uses Lumivex, glass, a Control Circuit and Diamond Lattices. Each Cell also needs a separately crafted Cell Housing.

<RecipeFor id="rematter:matrix_cluster" />
<RecipeFor id="rematter:optical_circuit" />
<RecipeFor id="rematter:matter_cell_16m" />
<RecipeFor id="rematter:matter_cell_64m" />
<RecipeFor id="rematter:matter_cell_256m" />
<RecipeFor id="rematter:void_matter_cell" />

Every Cell supports up to **512 substance/phase pairs**. Solids, liquids and gases share its space. Water and water vapor are different types.

Crafting a new Cell gives you an empty Cell. Installed Cells keep their own contents, and the Drive pools their available stock. Connect a powered **Wireless Access Point** (optionally upgraded with **Wireless Boosters**) and sneak-use a charged **Wireless Matter Terminal** on it to browse your network within range. Assign a key in **Controls → ReMatter → Open Wireless Matter Terminal** to open a carried (or **Curios**-equipped) terminal from anywhere in range without switching hotbar slots.

<RecipeFor id="rematter:wireless_access_point" />
<RecipeFor id="rematter:wireless_booster" />
<RecipeFor id="rematter:wireless_matter_terminal" />

## Extend the network

Matter Cables carry matter-network traffic and forward FE that an external generator or energy store pushes into them. They store no FE, and nothing can pull FE out through a cable. The network supports up to **64 Drives** in loaded chunks. The lowest cable tier in the network sets its total node limit, counting machines and the cables on the routes between them: **256** for Matter Cable, **384** for Reinforced and **512** for Resonant. Cable branches that reach none of your machines do not count. If the network grows past that limit, cables of the weakest tier and the devices only they reach are left out; the stronger part keeps working. A cable system larger than 4,096 connected blocks is too large.

A network includes only your own machines; other players' machines on the same cables stay separate. Cables belong to their placer. Only the owner or an operator may remove them. Cable traversal and pushed FE remain within that owner's devices. On a shared server, protect your base with a claim mod.

Connect Matter Cables to eligible rear, side or bottom ports. The Matter Terminal connects at the rear. In the Terminal's network view, open connected device menus, queue a single recipe on a compatible worker, or open **Request Batch** to plan and dispatch multi-step reaction, atmospheric collection and approved raw-material synthesis chains across online workers with one-click cancellation. Choose **Pin lab goal** in the request screen to keep a target and quantity for this play session. Open the target tab on a Matter Drive or Chemical Processor to check the next step, energy, missing inputs and leftover products against its current network. Choose **Unpin goal** when finished. On any **Matter Drive**, click **Consolidate Cells** to pack scattered substances into partitioned Cells first and coalesce split stacks across all six bays. Use separate transport for physical items and ordinary fluids.

In **Request Batch**, open **Jobs** to see each order's number and completed batches. Scroll to the order and use the eject button on its row to cancel outstanding work. Batches already produced stay in storage or in the machine's output. A batch requested directly at a machine takes priority there. Terminal orders end when the server stops. After restarting, check your stock before placing the remaining order.

Stock views in the Terminal, Drive and Network Interface have a search field and **Solids**, **Liquids**, **Gases** and **Favorites** filters. Click a substance row to toggle its world-saved favorite mark (or Shift-click a badge in the Cell Filter and Level Emitter pickers to pin it to the top across all menus); hover for exact quantities and details. Open the Terminal's **Monitor** tab to inspect live rolling net substance rates (`u/min`), machine target stock caps, **Matter Level Emitter** states and **Void Matter Cell** overflow coverage. A view lists up to 512 types, matches and favorites first. See [Cable and controller budgets](throughput.md) for actual rates: equal extra cables do not add throughput.

A **Pressurized Canister** holds up to **16,000 units** of a single liquid or gas substance (or 16 buckets of an ordinary fluid) without mixing both modes. Shift-click a liquid or gas row in the Terminal to fill a canister in your inventory, click the drain button next to the filters to return stored canisters to the network, click directly into **AE2 ME Terminals** and **Refined Storage Grids** to insert or extract stored matter, or right-click / sneak-right-click ReMatter machines, multiblock **Machine Ports**, fluid tanks, and **Mekanism** chemical tanks (and vice versa with Mekanism Gauge Droppers or Chemical Tanks on ReMatter machines). EMI and JEI also inspect the substance or fluid stored inside a filled canister. A **Configuration Card** can copy and stamp Cell Filter whitelists across Cells held in your offhand, docked in a Cell Transfer Station, or mounted in a Matter Drive's front bays, while holding a **Matter Wrench** or **Configuration Card** shows a live diagnostic HUD over targeted devices.

<RecipeFor id="rematter:matter_cable" />
<RecipeFor id="rematter:reinforced_matter_cable" />
<RecipeFor id="rematter:resonant_matter_cable" />
<RecipeFor id="rematter:matter_terminal" />

Item and fluid automation cannot move Cells or upgrades. Owners can use buckets manually. Enable Automation only for trusted external connections; it does not share private menu access.

## AE2 and Refined Storage requests

Connect a **ReMatter Network Interface** to the material network and to the chosen external system. In its **Settings**, select the backend and turn **Automation** on. Then enable **Network jobs** on the powered workers that should accept requests. Hover each network symbol for its full label. Use a separate Interface for AE2 and Refined Storage when both share the same ReMatter installation.

The connected systems can request supported recipes from available workers. They still need the complete ingredients, power and output space. Substance transfers through it are limited to its owner. Every player who can use the connected AE2 or Refined Storage network can request its recipes, so keep that network private. AE2 and Refined Storage cables connect only to its **Automatic** and **Matter Network** faces, and AE2 cables placed against it by another player do not connect. The automatic alloy catalogue offers powder manufacture and ingot production; it omits reverse alloy grinding so that a request for powder does not depend on first making its own ingot. Reverse grinding remains usable in physical machines and manually encoded processing patterns.

If a recipe changes, outdated jobs return their inputs to the external network. If the external network does not confirm a delivery, the Interface stops it and keeps its resources. Press **Release** in **Settings** to receive them after a confirmation: matter arrives as <ItemLink id="rematter:recovered_matter" />, which you use on your Drive to store it again; fluids go into the Interface's tank.

<RecipeFor id="rematter:network_interface" />

## Ownership and security

Devices default to **Public** so co-op players can share machines and cable networks right away. Only a device's owner (or a server operator) can change its security mode between **Public**, **Trusted** (owner, current FTB Teams teammates, and players added via `/rematter trust`), and **Private** (owner only; isolates from other players' networks). You can also set your personal default for newly placed devices with `/rematter security default public|trusted|private`.

Craft a **Security Card** to stamp a security mode quickly: right-click in the air to select **Public**, **Trusted**, or **Private**, right-click a device to update that block, or **Sneak + Right-Click** any device, Machine Port, or Matter Cable to apply the mode across all of your own devices on the connected network in one click.

<RecipeFor id="rematter:security_card" />

Use **Production settings** for a hard stock target. Matter processes count the selected substance and phase across the connected ReMatter network. Physical outputs count only the local output buffer. Set a multiple of the complete batch yield. AE2 and RS stock is outside this target.
