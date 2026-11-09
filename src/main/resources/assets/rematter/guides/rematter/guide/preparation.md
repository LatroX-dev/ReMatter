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
  - rematter:grinding_unit
  - rematter:separation_unit
  - rematter:mixing_basin
---
# Five fractions, three preparation stages

The Hand Preparator starts the matrix chain. Powered machines take over repeated work and supply the additional fractions for larger Cells.

## Extract one material at a time

Leave the middle and right inputs empty for extraction. Each batch uses one input and produces one fraction.

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

Craft a **Material Preparator** with two Amber Matrix Crystals, Structural Bars, Machine Frames and Control Circuits. Supply external FE. It starts when the input, power and output space are ready. Press **Start** to resume it if paused.

At base speed, an extraction takes **2,000 FE and 4.5 seconds**. Mixing takes **4,000 FE and 6 seconds**. Grinding, sieving and mixing are stages of one batch; they are not separate machines.

The three ingredient slots are arranged **left → middle → right**. Matrix Blend uses Silith on the left and Argil in the middle; the right slot stays empty. [Alloy and ceramic powders](metallurgy.md) use all three slots and have their own costs and durations.

<RecipeFor id="rematter:material_preparator" />

<GameScene zoom="4" interactive={true}>
  <Block x="0" y="1" id="rematter:material_preparator" />
  <BlockAnnotation x="0" y="1" color="#63d8bf">Ingredients run left to right. Use only the left slot for extraction; fill the other slots as the mixture requires.</BlockAnnotation>
  <LineAnnotation from="0.5 1.5 1" to="0.5 1.5 2" color="#f7bb56" thickness="3">External FE replaces hand cranking. No Cell or matter network is needed.</LineAnnotation>
</GameScene>

## Larger matrix assemblies

A **Matrix Cluster** combines four Matrix Crystals with Rhedon, Virel, Structural Bars and Matrix Blend. An **Optical Circuit** combines Lumivex Ingots, glass, Diamond Lattices and a Control Circuit. Make Lumivex through the [powder and sintering chain](metallurgy.md). The [larger Cell recipes](automation.md) show where both assemblies are used.

<RecipeFor id="rematter:matrix_cluster" />
<RecipeFor id="rematter:optical_circuit" />

The fractions remain physical ingredients in these assemblies. Learning the recipes does not provide them or fill a new Cell.

## Mount the working parts

Fit at least one **Resonance Cutter**, **Phase Filter** and **Confluence Basin**. Every type can mount on either the inside floor or the underside of the roof. The field carries material between parts; their function does not depend on gravity.

Click a supporting block's **top face** for a floor fitting or **underside** for a ceiling fitting. The socket turns automatically so the crystal points into the chamber. Sideways and unsupported placement are rejected. Keep the middle of the chamber empty.

Each type has a different crystal core and a small symbol. Its item and mounted appearance identify the same part; ceiling fittings are mirrored floor fittings.

<RecipeFor id="rematter:industrial_preparator" />
<RecipeFor id="rematter:grinding_unit" />
<RecipeFor id="rematter:separation_unit" />
<RecipeFor id="rematter:mixing_basin" />

## Close the shell

Outer width and depth can each be **3–9 blocks**. Use a footprint of at least **3×4 or 4×3** so the floor and ceiling have room for all three fittings. Build **5–9 blocks high**, including floor and roof. Use Reactor Casings on every edge and corner, Glass or Casing panels elsewhere and at least two Ports away from edges. Put the controller in the front wall, one block above the base.

This **5 wide × 3 deep × 6 high** example uses **40 Casings, 35 Glass, 2 Ports, 1 controller and one of each preparation module**. The Basin and Filter are on the floor; the Cutter is on the ceiling. Their positions on the two mounting planes are your choice.

<GameScene zoom="2.7" interactive={true}>
  <Block x="0" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="1" id="rematter:machine_port" p:formed="true" p:facing="west" />
  <Block x="0" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="0" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="3" z="1" id="rematter:reactor_glass" />
  <Block x="0" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="4" z="1" id="rematter:reactor_glass" />
  <Block x="0" y="4" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="5" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="5" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="5" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="0" z="1" id="rematter:reactor_glass" />
  <Block x="1" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="1" z="0" id="rematter:reactor_glass" />
  <Block x="1" y="1" z="2" id="rematter:reactor_glass" />
  <Block x="1" y="2" z="0" id="rematter:reactor_glass" />
  <Block x="1" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="1" y="3" z="0" id="rematter:reactor_glass" />
  <Block x="1" y="3" z="2" id="rematter:reactor_glass" />
  <Block x="1" y="4" z="0" id="rematter:reactor_glass" />
  <Block x="1" y="4" z="2" id="rematter:reactor_glass" />
  <Block x="1" y="5" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="5" z="1" id="rematter:reactor_glass" />
  <Block x="1" y="5" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="1" z="0" id="rematter:industrial_preparator" p:facing="north" />
  <Block x="2" y="1" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="2" z="0" id="rematter:machine_port" p:formed="true" p:facing="north" />
  <Block x="2" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="3" z="0" id="rematter:reactor_glass" />
  <Block x="2" y="3" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="4" z="0" id="rematter:reactor_glass" />
  <Block x="2" y="4" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="5" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="5" z="1" id="rematter:reactor_glass" />
  <Block x="2" y="5" z="2" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="1" id="rematter:reactor_glass" />
  <Block x="3" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="3" y="1" z="0" id="rematter:reactor_glass" />
  <Block x="3" y="1" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="2" z="0" id="rematter:reactor_glass" />
  <Block x="3" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="3" z="0" id="rematter:reactor_glass" />
  <Block x="3" y="3" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="4" z="0" id="rematter:reactor_glass" />
  <Block x="3" y="4" z="2" id="rematter:reactor_glass" />
  <Block x="3" y="5" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="5" z="1" id="rematter:reactor_glass" />
  <Block x="3" y="5" z="2" id="rematter:reactor_casing" />
  <Block x="4" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="4" y="0" z="1" id="rematter:reactor_casing" />
  <Block x="4" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="4" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="4" y="1" z="1" id="rematter:reactor_glass" />
  <Block x="4" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="4" y="2" z="0" id="rematter:reactor_casing" />
  <Block x="4" y="2" z="1" id="rematter:reactor_glass" />
  <Block x="4" y="2" z="2" id="rematter:reactor_casing" />
  <Block x="4" y="3" z="0" id="rematter:reactor_casing" />
  <Block x="4" y="3" z="1" id="rematter:reactor_glass" />
  <Block x="4" y="3" z="2" id="rematter:reactor_casing" />
  <Block x="4" y="4" z="0" id="rematter:reactor_casing" />
  <Block x="4" y="4" z="1" id="rematter:reactor_glass" />
  <Block x="4" y="4" z="2" id="rematter:reactor_casing" />
  <Block x="4" y="5" z="0" id="rematter:reactor_casing" />
  <Block x="4" y="5" z="1" id="rematter:reactor_casing" />
  <Block x="4" y="5" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="1" z="1" id="rematter:mixing_basin" />
  <Block x="3" y="1" z="1" id="rematter:separation_unit" />
  <Block x="2" y="4" z="1" id="rematter:grinding_unit" p:suspended="true" />
  <BlockAnnotation x="2" y="1" z="0" color="#63d8bf">Controller: front wall, one block above the floor.</BlockAnnotation>
  <BlockAnnotation x="0" y="1" z="1" color="#f7bb56">Connect external FE to this Port from outside.</BlockAnnotation>
  <BlockAnnotation x="2" y="2" z="0" color="#63d8bf">Use this outside Port for item transport or the matter network.</BlockAnnotation>
</GameScene>

Ports work only after the complete chamber forms. Connect external FE to an outward Port face. Enable **Automation** for item transport. Casing, Glass, internal modules and the controller are not external transfer connections.

## Add more working parts

Each **Cutter and Basin adds 2× ordinary preparation speed** before upgrades. One of each gives 4×: 23 ticks for extraction or 30 for Matrix Blend. Two of each give 8×: 12 or 15 ticks. Each controller runs one batch, or up to four with Parallel Upgrades; input and output quantities stay exact.

Industrial preparation starts at twice the ordinary recipe FE. Extra **Phase Filters** reduce that cost with diminishing returns: two give 6% savings, three give 10%. Hover the structure symbol for counts, actual speed and energy savings.

A larger empty shell adds no throughput. Pause before moving modules.
