---
navigation:
  title: Upgrades
  icon: rematter:speed_upgrade
  parent: index.md
  position: 13
item_ids:
  - rematter:speed_upgrade
  - rematter:efficiency_upgrade
  - rematter:parallel_upgrade
---
# Upgrades

Open the dedicated **Upgrades** tab on the left rail of any upgradable compact processor or industrial controller to manage upgrades and inspect live scaling. Every upgradable machine has dedicated **Speed** and **Efficiency** slots (up to four each) that work together at the same time. **Sneak + Right-Click** a machine while holding an upgrade to install it directly, or Shift-click it from your inventory. The compact Material Synthesizer adds a dedicated **Pressure Module** slot in its Upgrades tab so diamond synthesis can run with full upgrades; a formed Industrial Synthesizer includes Tier 2 crystallization built in. Industrial Reactor, Decomposer, Synthesizer and Preparation controllers add a **Parallel** slot (up to three). Analyzer Primal payment and Discovery Lens remain in the main machine view.

## Speed

Each <ItemLink id="rematter:speed_upgrade" /> raises throughput (**2×, 4×, 7×, 10×** for one to four upgrades) and increases total FE per operation by **25%** when not matched by an Efficiency Upgrade. With four Speed Upgrades alone, a compact machine processes at ten times its base speed and uses twice the FE per operation. A very short recipe cannot finish faster than one operation per tick.

<RecipeFor id="rematter:speed_upgrade" />

## Efficiency

Each <ItemLink id="rematter:efficiency_upgrade" /> **doubles** the machine's internal FE buffer (**×2, ×4, ×8, ×16** — up to **512,000 FE** on compact processors and **2,048,000 FE** on industrial controllers), cancels one Speed Upgrade's **+25%** FE surcharge **1:1**, and reduces base FE by **5%** per excess Efficiency Upgrade (up to **20%** saved with 0 Speed and 4 Efficiency). Pairing 4 Speed with 4 Efficiency delivers **10× speed at 100% base FE** and a **16×** larger buffer. Integer rounding may slightly reduce the saving for small custom costs.

<RecipeFor id="rematter:efficiency_upgrade" />

Industrial hardware provides Field, Precision, process room or step capacity. It does not add another global speed multiplier or FE discount. Upgrades never replace the formed-shell, hardware or research requirements.

Changing upgrade type or count restarts an unfinished process, and removing Efficiency Upgrades clamps stored FE to the lower buffer capacity. Inputs and energy are consumed only when the entire result can be committed. Drive, Terminal, Network Interface, Crystal Generators and the Primal Converter do not accept these upgrades.

## Parallel industrial batches

Each <ItemLink id="rematter:parallel_upgrade" /> permits another concurrent lane, up to four. Height, input room and step capacity may limit useful lanes. Every batch needs complete inputs, output room and its full FE price. All active jobs share one transfer budget; extra lanes do not create extra cable throughput.

<RecipeFor id="rematter:parallel_upgrade" />

Four Efficiency Upgrades save at most 20% below base cost before integer rounding. The absolute policy ceiling remains 25% of the price before savings; hardware adds no discount. Hover the lightning bar to see the live speed multiplier, FE multiplier and expanded buffer capacity.
