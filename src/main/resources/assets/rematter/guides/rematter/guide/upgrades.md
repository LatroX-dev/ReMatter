---
navigation:
  title: Upgrades
  icon: rematter:speed_upgrade
  parent: index.md
  position: 11
item_ids:
  - rematter:speed_upgrade
  - rematter:efficiency_upgrade
  - rematter:parallel_upgrade
---
# Upgrades

Open a processing machine's **Upgrades** side tab to reach its upgrade slot. Speed or Efficiency stacks up to **four of one type**. Parallel stacks up to **three**, only in industrial controllers. The Material Synthesizer accepts **one Pressure Module**. These choices share one slot. Hover the Speed and Efficiency symbols to compare their effects before inserting one.

## Speed

Each <ItemLink id="rematter:speed_upgrade" /> adds one unit of base throughput and increases total FE per operation by **25%**. With four, a compact machine processes at five times its unupgraded speed and uses twice the FE per operation. A very short recipe cannot finish faster than one operation per tick.

<RecipeFor id="rematter:speed_upgrade" />

## Efficiency

Each <ItemLink id="rematter:efficiency_upgrade" /> reduces FE per operation by **5%**, with at most **20%** saved. It does not increase speed. Integer rounding may slightly reduce the saving for small custom costs.

<RecipeFor id="rematter:efficiency_upgrade" />

Each Reaction Focus in an [Industrial Reactor](industrial.md) adds twice the Chemical Reactor's base speed. Upgrades scale from that base. They do not bypass its casing requirement.

Changing upgrade type or count restarts an unfinished process. Inputs and energy are consumed only when the entire result can be committed. Drive, Terminal and Network Interface do not process recipes and do not benefit from these upgrades.

## Parallel industrial batches

Each <ItemLink id="rematter:parallel_upgrade" /> adds one concurrent lane: one controller has **1 + N**, at most **four**. Every lane reserves a complete input, output room and its complete FE price. Lanes in the same cohort complete together, with each batch committed and paid separately. An expensive recipe may use fewer lanes because the 2,400,000-FE buffer cannot fund all four.

<RecipeFor id="rematter:parallel_upgrade" />

Efficiency and structure savings together stop at **25%** of the price before savings. The displayed batch FE uses that same capped calculation. Adding more saving modules cannot bypass the cap.
