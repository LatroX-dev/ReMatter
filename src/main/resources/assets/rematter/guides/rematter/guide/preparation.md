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

## Build a 3 by 3 by 3 preparation chamber

The Industrial Preparator is a complete **3 wide × 3 deep × 3 high** chamber, including floor and roof. Put its controller in the **front wall's middle column, one block above the base**. Build the chamber behind the controller's front face.

Use **Reactor Casings on every edge and corner**. Fill the remaining face centers with Casings or Reactor Glass, and include **at least two separate Machine Ports**. Keep the one-block interior empty. A complete glass example uses **20 Casings, 3 Glass, 2 Ports and 1 controller**.

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
  <Block x="1" y="1" z="0" id="rematter:industrial_preparator" p:facing="north" />
  <Block x="1" y="1" z="2" id="rematter:machine_port" />
  <Block x="2" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="1" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="1" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="2" z="1" id="rematter:reactor_casing" />
  <Block x="2" y="2" z="2" id="rematter:reactor_casing" />
  <BlockAnnotation x="1" y="1" z="0" color="#63d8bf">Controller in the front wall, one block above the base. Open its menu here.</BlockAnnotation>
  <BlockAnnotation x="0" y="1" z="1" color="#f7bb56">Machine Port on the left face: connect external FE from outside.</BlockAnnotation>
  <LineAnnotation from="0 1.5 1.5" to="-1 1.5 1.5" color="#f7bb56" thickness="3">External FE reaches the controller through this Port.</LineAnnotation>
  <BoxAnnotation min="1 1 1" max="2 2 2" color="#8dcfff">Keep this interior empty. The guide shows the shell; processing mechanisms are seen in a formed in-world machine.</BoxAnnotation>
  <Block x="1" y="1" z="3" id="minecraft:hopper" p:facing="north" />
  <Block x="1" y="2" z="3" id="minecraft:chest" />
  <BlockAnnotation x="1" y="1" z="2" color="#e6b676">Second Port on the back wall: feed items through the outside hopper. Enable Automation in the controller.</BlockAnnotation>
</GameScene>

All Machine Ports have the same capabilities; connect only to their **outside face**. One can receive FE and another can handle items. Enable **Automation** for item or fluid transfers. The controller itself, Casings and Glass do not expose external transfer connections.

A missing shell block, obstructed interior or fewer than two Ports stops work before ingredients are consumed. Keep the complete structure loaded. A flat ring is not a valid chamber.

The machine runs preparation at **four times base speed and twice the base FE per batch**. It does not multiply the yield.

<RecipeFor id="rematter:industrial_preparator" />
<RecipeFor id="rematter:reactor_casing" />
<RecipeFor id="rematter:reactor_glass" />
<RecipeFor id="rematter:machine_port" />

Return to [Cell transport and automation](automation.md).
