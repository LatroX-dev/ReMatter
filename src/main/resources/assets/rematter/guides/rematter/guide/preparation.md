---
navigation:
  title: Powered preparation
  icon: rematter:material_preparator
  parent: index.md
  position: 5
item_ids:
  - rematter:material_preparator
  - rematter:industrial_preparator
  - rematter:silith
  - rematter:argil
  - rematter:rhedon
  - rematter:virel
  - rematter:lumin
  - rematter:optical_circuit
---
# Five fractions, three preparation stages

The Hand Preparator starts the matrix chain. Powered machines take over repeated work and supply the additional fractions for larger Cells.

## Extract one material at a time

Leave the second input empty for extraction. Each batch uses one input and produces one fraction.

| Primary input | Output | First available machine |
| --- | --- | --- |
| Sand | Silith | Hand Preparator |
| Clay Ball | Argil | Hand Preparator |
| Redstone Dust | Rhedon | Material Preparator |
| Nether Quartz | Virel | Material Preparator |
| Glowstone Dust | Lumin | Material Preparator |

To make **four Matrix Blend**, put **one Silith in the primary input and one Argil in the second input**. A raw sand/clay pair is not the mixing recipe.

These are named fractions for the matrix technology, not purified elements. No added water or fluid connection is needed.

## Let FE do the work

Craft a **Material Preparator** from a Frame, Circuit, piston, flint and iron bars. Supply external FE. It starts when the input, power and output space are ready; **Start** resumes it if paused.

At base speed, an extraction takes **2,000 FE and 4.5 seconds**. Mixing takes **4,000 FE and 6 seconds**. Grinding, sieving and mixing are stages of one batch; they are not separate machines.

<RecipeFor id="rematter:material_preparator" />

<GameScene zoom="4" interactive={true}>
  <Block x="0" y="1" id="rematter:material_preparator" />
  <BlockAnnotation x="0" y="1" color="#e6b676">Primary feedstock on the left. Use the second input only for a mixture.</BlockAnnotation>
  <LineAnnotation from="0.5 1.5 1" to="0.5 1.5 2" color="#f7bb56" thickness="3">External FE replaces hand cranking. No Cell or matter network is needed.</LineAnnotation>
</GameScene>

## Larger matrix assemblies

A **Matrix Cluster** combines **four Matrix Crystals, one Rhedon and one Virel**. A 256k Cell uses one Cluster with its ordinary iron/glass housing and Control Circuit.

An **Optical Circuit** combines **one Control Circuit, one Lumin and one glass**. The 1M Cell uses this Circuit and four Clusters with its iron/glass housing.

<RecipeFor id="rematter:matrix_cluster" />
<RecipeFor id="rematter:optical_circuit" />

The fractions remain physical ingredients in these assemblies. Learning the recipes does not provide them or fill a new Cell.

## Build the work column first

Industrial preparation uses physical machinery inside its shell. Build each vertical column directly on the inside floor, **bottom to top**:

1. **Mixing Basin** receives and mixes the prepared fractions.
2. **Separation Unit** separates the grain fractions.
3. **Grinding Unit** works the feedstock at the top.

This cutaway shows the column and one floor block; it still needs its complete shell to operate. Keep the order and leave every other interior position empty. Do not float a column or stack another column above it.

<GameScene zoom="4" interactive={true}>
  <Block x="0" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="0" id="rematter:mixing_basin" />
  <BlockAnnotation x="0" y="1" z="0" color="#63d8bf">1. Mixing Basin rests directly on the floor.</BlockAnnotation>
  <Block x="0" y="2" z="0" id="rematter:separation_unit" />
  <BlockAnnotation x="0" y="2" z="0" color="#63d8bf">2. Separation Unit sits directly above the Basin.</BlockAnnotation>
  <Block x="0" y="3" z="0" id="rematter:grinding_unit" />
  <BlockAnnotation x="0" y="3" z="0" color="#63d8bf">3. Grinding Unit completes this preparation column.</BlockAnnotation>
</GameScene>

## Enclose a minimum chamber

The outer dimensions are **3–9 wide, 3–9 deep and 5–9 high**, including floor and roof. The controller belongs in the front wall, one block above the base, in any column away from the edges. Build behind the controller's front face.

Use **Reactor Casings at all edges and corners**. Other shell panels can be Casings or Reactor Glass. Include **at least two Machine Ports** in wall, floor or roof panels, never on an edge. Every interior block outside the work columns must be air.

This **3 wide × 3 deep × 5 high** example uses **28 Casings, 11 Glass, 2 Ports, 1 controller, 1 Mixing Basin, 1 Separation Unit and 1 Grinding Unit**. Glass panels may be replaced with Casings.

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
  <Block x="1" y="1" z="0" id="rematter:industrial_preparator" p:facing="north" />
  <Block x="1" y="1" z="2" id="rematter:machine_port" />
  <Block x="2" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="1" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="0" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="2" z="0" id="rematter:reactor_glass" />
  <Block x="1" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="1" id="rematter:reactor_glass" />
  <Block x="0" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="3" z="0" id="rematter:reactor_glass" />
  <Block x="1" y="3" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="3" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="4" z="1" id="rematter:reactor_glass" />
  <Block x="1" y="4" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="4" z="1" id="rematter:reactor_casing" />
  <Block x="2" y="4" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="1" z="1" id="rematter:mixing_basin" />
  <Block x="1" y="2" z="1" id="rematter:separation_unit" />
  <Block x="1" y="3" z="1" id="rematter:grinding_unit" />
  <BlockAnnotation x="1" y="1" z="0" color="#63d8bf">Controller: front wall, one block above the base, away from the edges.</BlockAnnotation>
  <BlockAnnotation x="0" y="1" z="1" color="#f7bb56">Connect external FE to the outside of this Port.</BlockAnnotation>
  <LineAnnotation from="0 1.5 1.5" to="-1 1.5 1.5" color="#f7bb56" thickness="3">Connections enter through outward Port faces.</LineAnnotation>
  <BlockAnnotation x="1" y="1" z="2" color="#e6b676">Second Port for item transport. Enable Automation in the controller.</BlockAnnotation>
</GameScene>

Connect external FE to an outward Port face. Use another Port for item transport and enable **Automation**. Ports have the same capabilities; they do not need a special energy or item variant. Casings, Glass, modules and the controller itself are not external transfer connections.

## Add machinery, not empty space

Each complete column contributes **4× ordinary preparation speed** before upgrades. One column takes **23 ticks for an extraction** or **30 ticks for Matrix Blend**. Two columns contribute **8×**, taking **12 or 15 ticks** respectively. A machine still completes one batch at a time.

A wider shell makes room for more columns. **Empty space alone adds no speed.** Every column must start on the floor, use all three correct modules and fit entirely inside the shell. The recipe's input and output quantities stay the same; industrial preparation costs **twice the ordinary recipe FE per batch**, regardless of column count. Read the controller for the effective duration after upgrades.

This two-column example is **4 wide × 3 deep × 5 high**: **32 Casings, 19 Glass, 2 Ports, 1 controller and 2 of each preparation module**.

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
  <Block x="1" y="1" z="0" id="rematter:industrial_preparator" p:facing="north" />
  <Block x="1" y="1" z="2" id="rematter:machine_port" />
  <Block x="2" y="1" z="0" id="rematter:reactor_glass" />
  <Block x="2" y="1" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="1" z="1" id="rematter:reactor_glass" />
  <Block x="3" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="0" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="2" z="0" id="rematter:reactor_glass" />
  <Block x="1" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="2" z="0" id="rematter:reactor_glass" />
  <Block x="2" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="3" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="1" id="rematter:reactor_glass" />
  <Block x="0" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="3" z="0" id="rematter:reactor_glass" />
  <Block x="1" y="3" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="3" z="0" id="rematter:reactor_glass" />
  <Block x="2" y="3" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="3" z="1" id="rematter:reactor_glass" />
  <Block x="3" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="4" z="1" id="rematter:reactor_glass" />
  <Block x="1" y="4" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="4" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="4" z="2" id="rematter:reactor_casing" />
  <Block x="3" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="4" z="1" id="rematter:reactor_casing" />
  <Block x="3" y="4" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="1" z="1" id="rematter:mixing_basin" />
  <Block x="1" y="2" z="1" id="rematter:separation_unit" />
  <Block x="1" y="3" z="1" id="rematter:grinding_unit" />
  <Block x="2" y="1" z="1" id="rematter:mixing_basin" />
  <Block x="2" y="2" z="1" id="rematter:separation_unit" />
  <Block x="2" y="3" z="1" id="rematter:grinding_unit" />
  <BlockAnnotation x="1" y="1" z="0" color="#63d8bf">Controller: front wall, one block above the base, away from the edges.</BlockAnnotation>
  <BlockAnnotation x="0" y="1" z="1" color="#f7bb56">Connect external FE to the outside of this Port.</BlockAnnotation>
  <LineAnnotation from="0 1.5 1.5" to="-1 1.5 1.5" color="#f7bb56" thickness="3">Connections enter through outward Port faces.</LineAnnotation>
  <BlockAnnotation x="1" y="1" z="2" color="#e6b676">Second Port for item transport. Enable Automation in the controller.</BlockAnnotation>
</GameScene>

Pause before rebuilding. The status identifies missing, misplaced or incomplete modules as well as shell faults. A damaged structure cannot process another batch. Old empty chambers need the new column; a Preparator shorter than five blocks must also be enlarged. Retrofit the existing controller instead of discarding it or its contents.

<RecipeFor id="rematter:industrial_preparator" />
<RecipeFor id="rematter:mixing_basin" />
<RecipeFor id="rematter:separation_unit" />
<RecipeFor id="rematter:grinding_unit" />
<RecipeFor id="rematter:reactor_casing" />
<RecipeFor id="rematter:reactor_glass" />
<RecipeFor id="rematter:machine_port" />

See [When a machine waits](help.md) and [Cell transport and automation](automation.md).
