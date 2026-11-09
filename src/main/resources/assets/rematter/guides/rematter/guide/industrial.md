---
navigation:
  title: Industrial Reactor
  icon: rematter:industrial_reactor
  parent: index.md
  position: 9
item_ids:
  - rematter:industrial_reactor
  - rematter:field_coil
  - rematter:reaction_chamber
  - rematter:reactor_casing
  - rematter:reactor_glass
  - rematter:machine_port
---
# Build a working reactor

An Industrial Reactor joins its crystal fittings into one shared reaction field. Fit at least one **Field Stabilizer** and one **Reaction Focus**.

## Fit the floor and crown

Both types can mount on either the inside floor or under the roof. Click a supporting block's top face or underside: the socket turns automatically and the crystal faces into the chamber. Sideways and unsupported placement are rejected.

Stabilizers use a crystal ring; Foci use one large focusing crystal. The same fitting looks mirrored at the ceiling. The field holds and transfers working material, so neither function relies on gravity. Keep the middle clear.

<RecipeFor id="rematter:industrial_reactor" />
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
  <Block x="2" y="1" z="0" id="rematter:industrial_reactor" p:facing="north" />
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

After formation, sneak-use the outside face of each Machine Port with an empty hand to set its mode. Supply FE at the **Power** Port and connect the **Matter** Port to a Matter Drive network. Keep at least one readable Cell in the Drive.

Select a permitted reaction and request a batch. Missing stock or FE waits safely. Use the controller's structure symbol for module counts, speed and energy savings.

## Expand the working field

Each **Reaction Focus adds 2× ordinary reaction speed** before upgrades. A 200-tick recipe takes 100 ticks with one Focus, 50 with two. Extra **Field Stabilizers** save FE with diminishing returns: two give 6%, three give 10%.

Inputs and outputs stay exact; each controller runs one batch, or up to four with Parallel Upgrades. Empty chamber space adds no throughput. Pause before moving parts: a broken shell restarts the unfinished batch once the chamber forms again.

The formed chamber's Casing, Glass, Ports and fittings can only be broken by players who may use its controller. A chamber never takes over a Machine Port inside another owner's chamber.

## Find a construction error

Open the controller and use **Locate missing part** when a diagnostic offers a position. Close the menu to see the marked outline in the world; it remains for five seconds. The tooltip names the expected part, coordinates and floor/ceiling mounting. Correct that block and reopen the controller to find the next error. Full ghost-block previews are not included.

Use up to three **Parallel Upgrades** for four concurrent, fully paid batches. Speed, Efficiency and Parallel compete for the same slot. All energy savings together stop at 25%.
