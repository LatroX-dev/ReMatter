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

Upgrade slots stay in the main machine view. Compact processors choose Speed or Efficiency in their shared slot, up to four. The Material Synthesizer may choose one Pressure Module in that slot instead. Industrial Reactor and Preparator controllers have separate Speed, Efficiency and Parallel slots; Parallel stacks to three. Analyzer Primal payment and Discovery Lens have dedicated slots.

## Speed

Each <ItemLink id="rematter:speed_upgrade" /> adds one unit of base throughput and increases total FE per operation by **25%**. With four, a compact machine processes at five times its unupgraded speed and uses twice the FE per operation. A very short recipe cannot finish faster than one operation per tick.

<RecipeFor id="rematter:speed_upgrade" />

## Efficiency

Each <ItemLink id="rematter:efficiency_upgrade" /> reduces FE per operation by **5%**, with at most **20%** saved. It does not increase speed. Integer rounding may slightly reduce the saving for small custom costs.

<RecipeFor id="rematter:efficiency_upgrade" />

Industrial hardware provides Field, Precision, process room or step capacity. It does not add another global speed multiplier or FE discount. Upgrades never replace the formed-shell, hardware or research requirements.

Changing upgrade type or count restarts an unfinished process. Inputs and energy are consumed only when the entire result can be committed. Drive, Terminal and Network Interface do not process recipes and do not benefit from these upgrades.

## Parallel industrial batches

Each <ItemLink id="rematter:parallel_upgrade" /> permits another concurrent lane, up to four. Height, input room and step capacity may limit useful lanes. Every batch needs complete inputs, output room and its full FE price. All active jobs share one transfer budget; extra lanes do not create extra cable throughput.

<RecipeFor id="rematter:parallel_upgrade" />

Four Efficiency Upgrades save at most 20% before integer rounding. The absolute policy ceiling remains 25% of the price before savings; hardware adds no discount. Read the actual displayed FE rather than assuming that more modules make a recipe cheaper.
