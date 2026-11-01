---
navigation:
  title: Upgrades
  icon: rematter:speed_upgrade
  parent: index.md
  position: 9
item_ids:
  - rematter:speed_upgrade
  - rematter:efficiency_upgrade
---
# Upgrades

Open a processing machine's **Details** to reach its upgrade slot. It holds up to **four upgrades of one type**.

## Speed

Each <ItemLink id="rematter:speed_upgrade" /> adds one unit of base throughput and increases total FE per operation by **25%**. With four, a compact machine processes at five times its unupgraded speed and uses twice the FE per operation. A very short recipe cannot finish faster than one operation per tick.

<RecipeFor id="rematter:speed_upgrade" />

## Efficiency

Each <ItemLink id="rematter:efficiency_upgrade" /> reduces FE per operation by **5%**, with at most **20%** saved. It does not increase speed. Integer rounding may slightly reduce the saving for small custom costs.

<RecipeFor id="rematter:efficiency_upgrade" />

The Industrial Reactor's base throughput is twice that of the Chemical Reactor. Upgrades scale from that base. They do not bypass its casing requirement.

Changing upgrade type or count restarts an unfinished process. Inputs and energy are consumed only when the entire result can be committed. Drive, Storage and Terminal do not process recipes and do not benefit from these upgrades.
