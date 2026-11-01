---
navigation:
  title: Industrial Reactor
  icon: rematter:industrial_reactor
  parent: index.md
  position: 8
item_ids:
  - rematter:industrial_reactor
  - rematter:reactor_casing
  - rematter:reactor_glass
  - rematter:machine_port
---
# Industrial Reactor

The Industrial Reactor runs the Chemical Reactor's non-electrolysis processes. Its smallest chamber runs at **twice base speed**. Larger interiors increase throughput with diminishing gains, while the **total FE and material quantities per operation remain unchanged**.

## Build a complete reactor chamber

Build a rectangular shell **3–9 blocks wide, 3–9 deep and 4–9 high**, including its floor and roof. The controller goes in the **front wall, one block above the base**, in any column except the edge columns. The chamber extends behind its front face. The smallest example below is **3 wide × 3 deep × 4 high**.

Use **Reactor Casings on every edge and corner**. Fill all other wall panels with Casings or Reactor Glass, with **at least two separate Machine Ports**. Keep the entire interior empty. This smallest glass example uses **24 Casings, 7 Glass, 2 Ports and 1 controller**.

<GameScene zoom="3" interactive={true}>
  <Block x="0" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="0" z="1" id="rematter:reactor_glass" />
  <Block x="1" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="1" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="1" id="rematter:machine_port" />
  <Block x="0" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="1" z="0" id="rematter:industrial_reactor" p:facing="north" />
  <Block x="1" y="1" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="1" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="0" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="2" z="0" id="rematter:machine_port" />
  <Block x="1" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="3" z="1" id="rematter:reactor_glass" />
  <Block x="1" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="3" z="1" id="rematter:reactor_casing" />
  <Block x="2" y="3" z="2" id="rematter:reactor_casing" />
  <BlockAnnotation x="1" y="1" z="0" color="#63d8bf">Controller in the front wall, one block above the base. Open its menu here.</BlockAnnotation>
  <BlockAnnotation x="0" y="1" z="1" color="#f7bb56">Machine Port on the left face: connect external FE from outside.</BlockAnnotation>
  <LineAnnotation from="0 1.5 1.5" to="-1 1.5 1.5" color="#f7bb56" thickness="3">External FE reaches the controller through this Port.</LineAnnotation>
  <BoxAnnotation min="1 1 1" max="2 3 2" color="#8dcfff">Keep this interior empty. The guide shows the shell; processing mechanisms are seen in a formed in-world machine.</BoxAnnotation>
  <Block x="1" y="2" z="-1" id="rematter:matter_drive" />
  <BlockAnnotation x="1" y="2" z="0" color="#e6b676">Second Port above the controller: connect one owned Drive or Matter Pipe from outside.</BlockAnnotation>
  <BlockAnnotation x="1" y="2" z="-1" color="#8dcfff">The Drive holds the Cell. Its stock reaches the reactor through the formed Port.</BlockAnnotation>
</GameScene>

## A larger rectangular reactor

This example is **4 wide × 6 deep × 5 high**, with the controller in the right-hand inner column. Its 24 empty interior blocks give **6× Chemical Reactor speed** before upgrades. A 200-tick process completes in 34 ticks. The machine performs one batch at a time and keeps the same recipe quantities and FE cost.

The glass example uses **44 Casings, 49 Glass, 2 Ports and 1 controller**. The menu shows the actual duration after upgrades. Pause processing before changing the chamber's shape, then check its formation and intended storage connection.

<GameScene zoom="2" interactive={true}>
  <Block x="0" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="3" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="4" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="5" id="rematter:reactor_casing" />
  <Block x="1" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="0" z="1" id="rematter:reactor_glass" />
  <Block x="1" y="0" z="2" id="rematter:reactor_glass" />
  <Block x="1" y="0" z="3" id="rematter:reactor_glass" />
  <Block x="1" y="0" z="4" id="rematter:reactor_glass" />
  <Block x="1" y="0" z="5" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="0" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="0" z="3" id="rematter:reactor_glass" />
  <Block x="2" y="0" z="4" id="rematter:reactor_glass" />
  <Block x="2" y="0" z="5" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="1" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="3" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="4" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="5" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="1" id="rematter:machine_port" />
  <Block x="0" y="1" z="2" id="rematter:reactor_glass" />
  <Block x="0" y="1" z="3" id="rematter:reactor_glass" />
  <Block x="0" y="1" z="4" id="rematter:reactor_glass" />
  <Block x="0" y="1" z="5" id="rematter:reactor_casing" />
  <Block x="1" y="1" z="0" id="rematter:reactor_glass" />
  <Block x="1" y="1" z="5" id="rematter:reactor_glass" />
  <Block x="2" y="1" z="0" id="rematter:industrial_reactor" p:facing="north" />
  <Block x="2" y="1" z="5" id="rematter:reactor_glass" />
  <Block x="3" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="1" z="1" id="rematter:reactor_glass" />
  <Block x="3" y="1" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="1" z="3" id="rematter:reactor_glass" />
  <Block x="3" y="1" z="4" id="rematter:reactor_glass" />
  <Block x="3" y="1" z="5" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="0" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="0" y="2" z="3" id="rematter:reactor_glass" />
  <Block x="0" y="2" z="4" id="rematter:reactor_glass" />
  <Block x="0" y="2" z="5" id="rematter:reactor_casing" />
  <Block x="1" y="2" z="0" id="rematter:reactor_glass" />
  <Block x="1" y="2" z="5" id="rematter:reactor_glass" />
  <Block x="2" y="2" z="0" id="rematter:machine_port" />
  <Block x="2" y="2" z="5" id="rematter:reactor_glass" />
  <Block x="3" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="3" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="2" z="3" id="rematter:reactor_glass" />
  <Block x="3" y="2" z="4" id="rematter:reactor_glass" />
  <Block x="3" y="2" z="5" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="1" id="rematter:reactor_glass" />
  <Block x="0" y="3" z="2" id="rematter:reactor_glass" />
  <Block x="0" y="3" z="3" id="rematter:reactor_glass" />
  <Block x="0" y="3" z="4" id="rematter:reactor_glass" />
  <Block x="0" y="3" z="5" id="rematter:reactor_casing" />
  <Block x="1" y="3" z="0" id="rematter:reactor_glass" />
  <Block x="1" y="3" z="5" id="rematter:reactor_glass" />
  <Block x="2" y="3" z="0" id="rematter:reactor_glass" />
  <Block x="2" y="3" z="5" id="rematter:reactor_glass" />
  <Block x="3" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="3" z="1" id="rematter:reactor_glass" />
  <Block x="3" y="3" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="3" z="3" id="rematter:reactor_glass" />
  <Block x="3" y="3" z="4" id="rematter:reactor_glass" />
  <Block x="3" y="3" z="5" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="3" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="4" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="5" id="rematter:reactor_casing" />
  <Block x="1" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="4" z="1" id="rematter:reactor_glass" />
  <Block x="1" y="4" z="2" id="rematter:reactor_glass" />
  <Block x="1" y="4" z="3" id="rematter:reactor_glass" />
  <Block x="1" y="4" z="4" id="rematter:reactor_glass" />
  <Block x="1" y="4" z="5" id="rematter:reactor_casing" />
  <Block x="2" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="4" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="4" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="4" z="3" id="rematter:reactor_glass" />
  <Block x="2" y="4" z="4" id="rematter:reactor_glass" />
  <Block x="2" y="4" z="5" id="rematter:reactor_casing" />
  <Block x="3" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="4" z="1" id="rematter:reactor_casing" />
  <Block x="3" y="4" z="2" id="rematter:reactor_casing" />
  <Block x="3" y="4" z="3" id="rematter:reactor_casing" />
  <Block x="3" y="4" z="4" id="rematter:reactor_casing" />
  <Block x="3" y="4" z="5" id="rematter:reactor_casing" />
  <BlockAnnotation x="2" y="1" z="0" color="#63d8bf">Controller: one block above the base, away from either front edge. This offset position is valid.</BlockAnnotation>
  <BoxAnnotation min="1 1 1" max="3 4 5" color="#8dcfff">Keep the whole interior empty. More interior space raises throughput with diminishing gains.</BoxAnnotation>
  <BlockAnnotation x="0" y="1" z="1" color="#f7bb56">External FE connects to this Port from the left.</BlockAnnotation>
  <LineAnnotation from="0 1.5 1.5" to="-1 1.5 1.5" color="#f7bb56" thickness="3">Connect outside the shell.</LineAnnotation>
  <BlockAnnotation x="2" y="2" z="0" color="#e6b676">Matter network Port: connect a Drive or Matter Pipe outside the front wall.</BlockAnnotation>
</GameScene>

## Connect through the Ports

A Port connects only on its outside face. In the smallest example, the left Port receives external FE. The front upper Port connects one owned Matter Drive with a Cell; a Matter Pipe can bridge the gap instead. Casings, Glass and the controller itself are not external transfer ports.

Both Ports are general-purpose connections. Item and fluid transfers require **Automation** in the controller's Settings. The reactor's ingredients and outputs remain substance quantities in the connected storage; the Ports hold no duplicate inventory.

## Run one process

Press **Choose process**, inspect a recipe, confirm with **Use recipe**, then press **Run once**. Enable **Repeat** separately for continuing work. Open **Settings** for upgrades.

An incomplete shell, blocked interior, dimensions outside the allowed range or fewer than two Ports stops work safely. Restore the required shape and intended storage before resuming. Existing materials and FE remain stored. Former flat casing rings must be rebuilt into this chamber; they are no longer a working structure.

<RecipeFor id="rematter:industrial_reactor" />
<RecipeFor id="rematter:reactor_casing" />
<RecipeFor id="rematter:reactor_glass" />
<RecipeFor id="rematter:machine_port" />

See [Upgrades](upgrades.md) and [Cell transport and automation](automation.md).
