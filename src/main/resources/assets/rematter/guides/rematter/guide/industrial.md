---
navigation:
  title: Industrial Reactor
  icon: rematter:industrial_reactor
  parent: index.md
  position: 7
item_ids:
  - rematter:industrial_reactor
  - rematter:field_coil
  - rematter:reaction_chamber
  - rematter:reactor_casing
  - rematter:reactor_glass
  - rematter:machine_port
---
# Build a working reactor

An Industrial Reactor surrounds real reaction machinery with a complete shell. Its controller manages one selected process; its internal columns supply the processing capacity.

## Put two modules on the floor

Build each column **bottom to top**: a **Field Coil** directly on the inside floor, then a **Reaction Chamber** directly above it. The Coil controls the reaction field; the Chamber holds the working region. These are machine components, not extra stored substances.

This cutaway shows the two modules and one floor block. Add the full shell before connecting or running it.

<GameScene zoom="4" interactive={true}>
  <Block x="0" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="0" id="rematter:field_coil" />
  <BlockAnnotation x="0" y="1" z="0" color="#63d8bf">1. Field Coil rests directly on the inside floor.</BlockAnnotation>
  <Block x="0" y="2" z="0" id="rematter:reaction_chamber" />
  <BlockAnnotation x="0" y="2" z="0" color="#63d8bf">2. Reaction Chamber sits directly above the Coil.</BlockAnnotation>
</GameScene>

## Close the shell

Outer width and depth can each be **3–9 blocks**; height can be **4–9**. Put the controller in the front wall, one block above the base, away from the edge columns. Build the rectangular chamber behind its front face.

Use Casings on all edges and corners, Casings or Reactor Glass on the other shell panels, and **at least two Machine Ports** away from edges. Leave every interior position outside the complete columns empty. Columns cannot float or stack on one another.

This minimum **3 wide × 3 deep × 4 high** glass example uses **24 Casings, 7 Glass, 2 Ports, 1 controller, 1 Field Coil and 1 Reaction Chamber**.

<GameScene zoom="2.6" interactive={true}>
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
  <Block x="1" y="1" z="1" id="rematter:field_coil" />
  <Block x="1" y="2" z="1" id="rematter:reaction_chamber" />
  <BlockAnnotation x="1" y="1" z="0" color="#63d8bf">Controller: front wall, one block above the base, away from the edges.</BlockAnnotation>
  <BlockAnnotation x="0" y="1" z="1" color="#f7bb56">Connect external FE to the outside of this Port.</BlockAnnotation>
  <LineAnnotation from="0 1.5 1.5" to="-1 1.5 1.5" color="#f7bb56" thickness="3">Connections enter through outward Port faces.</LineAnnotation>
  <BlockAnnotation x="1" y="2" z="0" color="#e6b676">Connect one Matter Drive or Matter Pipe outside this Port.</BlockAnnotation>
</GameScene>

## Connect and run one process

A Port connects only on its **outside face**. In the example, the left Port receives external FE. The upper front Port connects one owned Matter Drive with a Cell, or an original Matter Storage; a Matter Pipe can bridge the gap. The controller, Casings, Glass and internal modules are not transfer connections.

Ports are general-purpose connections and hold no separate stock. Item and fluid transfers require **Automation** in the controller's **Details**. Reaction ingredients and outputs remain quantities in the connected storage.

Press **Choose recipe** (or **Recipe** to change the selection), inspect a recipe, confirm with **Use recipe**, then press **Start** for one batch. Open **Details** for upgrades and **Run nonstop**. **Stop repeat** stops continuous production; **Pause** and **Resume** retain the current order.

## Fit another complete column

Each full Coil-and-Chamber column contributes **2× ordinary reaction speed** before upgrades. One column processes a 200-tick recipe in 100 ticks; two columns take 50 ticks. Inputs, outputs and **FE per batch stay unchanged**. The machine still runs one batch at a time, and the controller shows its effective duration.

A larger empty shell gives **no speed bonus**. This **4 wide × 3 deep × 4 high** example holds two columns and uses **28 Casings, 13 Glass, 2 Ports, 1 controller, 2 Field Coils and 2 Reaction Chambers**.

<GameScene zoom="2.6" interactive={true}>
  <Block x="0" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="0" z="1" id="rematter:reactor_glass" />
  <Block x="1" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="1" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="1" id="rematter:machine_port" />
  <Block x="0" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="1" z="0" id="rematter:industrial_reactor" p:facing="north" />
  <Block x="1" y="1" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="1" z="0" id="rematter:reactor_glass" />
  <Block x="2" y="1" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="1" z="1" id="rematter:reactor_glass" />
  <Block x="3" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="0" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="2" z="0" id="rematter:machine_port" />
  <Block x="1" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="2" z="0" id="rematter:reactor_glass" />
  <Block x="2" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="3" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="3" z="1" id="rematter:reactor_glass" />
  <Block x="1" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="3" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="3" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="3" z="1" id="rematter:reactor_casing" />
  <Block x="3" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="1" z="1" id="rematter:field_coil" />
  <Block x="1" y="2" z="1" id="rematter:reaction_chamber" />
  <Block x="2" y="1" z="1" id="rematter:field_coil" />
  <Block x="2" y="2" z="1" id="rematter:reaction_chamber" />
  <BlockAnnotation x="1" y="1" z="0" color="#63d8bf">Controller: front wall, one block above the base, away from the edges.</BlockAnnotation>
  <BlockAnnotation x="0" y="1" z="1" color="#f7bb56">Connect external FE to the outside of this Port.</BlockAnnotation>
  <LineAnnotation from="0 1.5 1.5" to="-1 1.5 1.5" color="#f7bb56" thickness="3">Connections enter through outward Port faces.</LineAnnotation>
  <BlockAnnotation x="1" y="2" z="0" color="#e6b676">Connect one Matter Drive or Matter Pipe outside this Port.</BlockAnnotation>
</GameScene>

Pause before rebuilding. The status distinguishes shell, Port and module faults. An incomplete or misplaced column stops processing; keep all other interior positions clear. Old empty chambers need the new modules before they can work again. Retrofit the existing controller and keep its connected stock.

<RecipeFor id="rematter:industrial_reactor" />
<RecipeFor id="rematter:field_coil" />
<RecipeFor id="rematter:reaction_chamber" />
<RecipeFor id="rematter:reactor_casing" />
<RecipeFor id="rematter:reactor_glass" />
<RecipeFor id="rematter:machine_port" />

See [Upgrades](upgrades.md), [When a machine waits](help.md) and [Cell transport and automation](automation.md).
