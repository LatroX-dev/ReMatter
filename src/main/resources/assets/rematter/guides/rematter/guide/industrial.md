---
navigation:
  title: Industrial Reactor
  icon: rematter:industrial_reactor
  parent: index.md
  position: 5
item_ids:
  - rematter:industrial_reactor
  - rematter:reactor_casing
---
# Industrial Reactor

The <ItemLink id="rematter:industrial_reactor" /> runs the Chemical Reactor's non-electrolysis processes at **twice the base speed**, with the **same total FE cost** per operation.

## Build one complete layer

Place the controller in the middle of a **3 by 3 square**. Fill all eight surrounding positions, including the corners, with <ItemLink id="rematter:reactor_casing" />. All nine blocks must be at the same height.

<GameScene zoom="4" interactive={true}>
  <Block x="0" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="1" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="2" y="1" z="0" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="1" id="rematter:reactor_casing" />
  <Block x="1" y="1" z="1" id="rematter:industrial_reactor" />
  <Block x="2" y="1" z="1" id="rematter:reactor_casing" />
  <Block x="0" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="2" y="1" z="2" id="rematter:reactor_casing" />
  <Block x="1" y="0" z="1" id="rematter:matter_pipe" p:up="true" p:north="true" />
  <Block x="1" y="0" z="0" id="rematter:matter_storage" />
  <BlockAnnotation x="1" y="1" z="1" color="#63d8bf">Controller: connect FE directly above it. The pipe underneath links its matter storage.</BlockAnnotation>
  <BlockAnnotation x="1" y="0" z="1" color="#8dcfff">Matter Pipe below the controller. Casings do not transfer matter or energy.</BlockAnnotation>
  <BlockAnnotation x="1" y="0" z="0" color="#e6b676">One owned Matter Storage, connected through the lower pipe.</BlockAnnotation>
  <BoxAnnotation min="0 1 0" max="3 2 3" color="#d8b6ef">The complete 3 by 3 layer: eight casings around one controller.</BoxAnnotation>
</GameScene>

Rotate the scene to inspect the lower connection. **Casings are not ports.** Connect matter below the controller and FE above it, or swap those two connections. A casing ring around the controller leaves these two faces accessible.

Supply FE, select a process and press Run. If a casing is missing or replaced, the controller shows **Needs casing** and safely waits. Restore the ring to continue. Stored resources remain in the controller or linked Matter Storage when the ring is incomplete.

<RecipeFor id="rematter:industrial_reactor" />
<RecipeFor id="rematter:reactor_casing" />

Speed or efficiency upgrades fit in the controller's upgrade slot. See [Upgrades](upgrades.md).
