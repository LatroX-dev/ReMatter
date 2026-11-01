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

The Industrial Reactor runs the Chemical Reactor's non-electrolysis processes at **twice base speed**, with the **same total FE per operation**.

## Build a complete 3 by 3 by 4 chamber

Build a **3 wide × 3 deep × 4 high** shell, including its floor and roof. The controller goes in the **front wall's middle column, one block above the base**. The chamber extends behind its front face.

Use **Reactor Casings on every edge and corner**. Fill all other face centers with Casings or Reactor Glass, with **at least two separate Machine Ports**. Keep both vertically stacked interior blocks empty. A glass example uses **24 Casings, 7 Glass, 2 Ports and 1 controller**.

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

## Connect through the Ports

A Port connects only on its outside face. In this example, the left Port receives external FE. The front upper Port connects one owned Matter Drive with a Cell; a Matter Pipe can bridge the gap instead. Casings, Glass and the controller itself are not external transfer ports.

Both Ports are general-purpose connections. Item and fluid transfers require **Automation** in the controller's Settings. The reactor's ingredients and outputs remain substance quantities in the connected storage; the Ports hold no duplicate inventory.

## Run one process

Press **Choose process**, inspect a recipe, confirm with **Use recipe**, then press **Run once**. Enable **Repeat** separately for continuing work. Open **Settings** for upgrades.

An incomplete shell, blocked interior, wrong height or fewer than two Ports stops work safely. Restore the required shape and intended storage before resuming. Existing materials and FE remain stored. Former flat casing rings must be rebuilt into this chamber; they are no longer a working structure.

<RecipeFor id="rematter:industrial_reactor" />
<RecipeFor id="rematter:reactor_casing" />
<RecipeFor id="rematter:reactor_glass" />
<RecipeFor id="rematter:machine_port" />

See [Upgrades](upgrades.md) and [Cell transport and automation](automation.md).
