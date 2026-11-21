---
navigation:
  title: Chemical Reactor
  icon: rematter:chemical_reactor_controller
  parent: index.md
  position: 10
item_ids:
  - rematter:chemical_reactor_controller
  - rematter:industrial_decomposer_controller
  - rematter:industrial_synthesizer_controller
  - rematter:field_coil
  - rematter:reaction_chamber
  - rematter:reactor_casing
  - rematter:reactor_glass
  - rematter:machine_port
---
# Build a working reactor

A **Chemical Reactor**, **Industrial Decomposer**, or **Industrial Synthesizer** joins its crystal fittings into one shared reaction chamber. Fit at least one **Field Stabilizer** and one **Reaction Focus**.

## Fit the floor and crown

Both types can mount on either the inside floor or under the roof. Click a supporting block's top face or underside: the socket turns automatically and the crystal faces into the chamber. Sideways and unsupported placement are rejected.

Stabilizers use a crystal ring; Foci use one large focusing crystal. The same fitting looks mirrored at the ceiling. The field holds and transfers working material, so neither function relies on gravity. Keep the middle clear.

<RecipeFor id="rematter:chemical_reactor_controller" />
<RecipeFor id="rematter:industrial_decomposer_controller" />
<RecipeFor id="rematter:industrial_synthesizer_controller" />
<RecipeFor id="rematter:field_coil" />
<RecipeFor id="rematter:reaction_chamber" />

## Complete the shell

Outer width and depth are **3–9 blocks**. Height is **4–9**, including floor and roof. Build the entire floor, roof, edges and corners from Reactor Casing. Fill the remaining wall panels with Reactor Glass or Reactor Casing and place at least two Machine Ports in those panels. The front controller sits one block above the base.

<RecipeFor id="rematter:reactor_casing" />
<RecipeFor id="rematter:reactor_glass" />
<RecipeFor id="rematter:machine_port" />

This **5 wide × 3 deep × 6 high** example uses **46 Casings, 29 Glass, 2 Ports, 1 controller, 1 Stabilizer and 1 Focus**. The Stabilizer is on the floor and the Focus is on the ceiling. Either type can use either mounting plane.

<GameScene zoom="2.7" interactive={true}>
  <Block x="0" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="1" id="rematter:machine_port" p:formed="true" p:facing="west" p:mode="power" />
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
  <Block x="1" y="0" z="1" id="rematter:reactor_casing" />
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
  <Block x="1" y="5" z="1" id="rematter:reactor_casing" />
  <Block x="1" y="5" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="1" id="rematter:reactor_casing" />
  <Block x="2" y="0" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="1" z="0" id="rematter:chemical_reactor_controller" p:facing="north" />
  <Block x="2" y="1" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="2" z="0" id="rematter:machine_port" p:formed="true" p:facing="north" p:mode="matter" />
  <Block x="2" y="2" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="3" z="0" id="rematter:reactor_glass" />
  <Block x="2" y="3" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="4" z="0" id="rematter:reactor_glass" />
  <Block x="2" y="4" z="2" id="rematter:reactor_glass" />
  <Block x="2" y="5" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="5" z="1" id="rematter:reactor_casing" />
  <Block x="2" y="5" z="2" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="0" id="rematter:reactor_casing" />
  <Block x="3" y="0" z="1" id="rematter:reactor_casing" />
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
  <Block x="3" y="5" z="1" id="rematter:reactor_casing" />
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
  <Block x="1" y="1" z="1" id="rematter:field_coil" />
  <Block x="3" y="4" z="1" id="rematter:reaction_chamber" p:suspended="true" />
  <BlockAnnotation x="2" y="1" z="0" color="#63d8bf">Controller: front wall, one block above the floor.</BlockAnnotation>
  <BlockAnnotation x="0" y="1" z="1" color="#f7bb56">Power Port. Supply FE on the outside face.</BlockAnnotation>
  <BlockAnnotation x="2" y="2" z="0" color="#63d8bf">Matter Port. Connect the Matter Drive network here.</BlockAnnotation>
</GameScene>

After formation, sneak-use the outside face of each Machine Port with an empty hand (or use a Matter Wrench in **Ports** mode) to set its mode. Supply FE at the **Power** Port and connect the **Matter** Port to a Matter Drive network. Keep at least one readable Cell in the Drive.

Select a permitted reaction and request a batch. Missing stock or FE waits safely. Inspect the controller's Field, input process room, speed, efficiency and active-job values.

## Expand the working field

Each **Reaction Focus adds 16,000 u of simultaneous input room**. Each **Field Stabilizer adds Field**; advanced procedures require Field 4 or 8. Neither fitting adds a global speed bonus or an FE discount. See [Hardware and upgrades](hardware.md).

Inputs and outputs stay exact; each controller runs one batch, or up to four with Parallel Upgrades. Empty chamber space adds no throughput. Pause before moving parts: a broken shell restarts the unfinished batch once the chamber forms again.

The formed chamber's Casing, Glass, Ports and fittings can only be broken by players who may use its controller. A chamber never takes over a Machine Port inside another owner's chamber.

## Find a construction error or Auto-Build

Open the controller and toggle the chamber blueprint preview in **Settings → Structure** to highlight missing, misplaced or obstructing blocks in the world. Press **Auto-Build** in **Settings → Structure** — or right-click an unformed controller with a **Matter Wrench** in **Ports** mode — to place missing Reactor Casing, Reactor Glass, Machine Ports and interior fittings directly from your inventory. **Auto-Build** verifies the entire chamber volume beforehand: if any foreign block, fluid or entity obstructs full construction, no blocks are placed, a warning names the obstruction and the world preview turns on automatically.

Up to three **Parallel Upgrades** permit four fully paid jobs when chamber height and process room allow them. Speed, Efficiency and Parallel have separate industrial slots. All jobs share [one controller transfer budget](throughput.md); extra cables and ports do not add rates.

## Industrial Decomposer and Industrial Synthesizer

Swap the front controller for an **Industrial Decomposer Controller** (Copper crystal) or **Industrial Synthesizer Controller** (Leaf crystal) using the same 3–9 × 4–9 × 3–9 reactor chamber with at least one Field Stabilizer and one Reaction Focus. Both run at **2× base speed** and accept up to **4× Speed**, **4× Efficiency**, and **3× Parallel** upgrades (up to **80×** total throughput across 4 concurrent lanes).

- **Machine Ports** on an Industrial Decomposer or Industrial Synthesizer transfer **items, fluids, FE, and matter** according to each port's mode (configure modes with a Matter Wrench or Shift-right-click).
- The **Industrial Synthesizer** includes a built-in Pressure Module when formed, enabling Tier 2 crystallization (such as diamonds and diamond blocks) across all parallel lanes without occupying an upgrade slot.
