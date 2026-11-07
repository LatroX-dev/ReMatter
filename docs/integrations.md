# Integrations

ReMatter targets Minecraft 26.1.2 and NeoForge 26.1.2.124. It is a standalone chemistry mod; ReForging supplies a reference collection of optional integrations and external energy sources. Support for another Minecraft version or loader requires a separate port.

A Hand Preparator first makes the dry matrix feedstock without FE. Connect a Decomposer and Material Synthesizer to a Matter Drive holding a Cell. ReMatter provides its own storage network, recipe selection and production controls, with optional Applied Energistics 2 and Refined Storage integration.

## AE2 and Refined Storage

Connect a **Network Interface** to the native grid and the ReMatter matter network. In its **Configuration**, enable **Automation** and select **Connect: AE2** or **Connect: Refined Storage**. On the connected processing machines, clear their work slots and tanks, then enable **Configuration → Network jobs: On**; upgrades may remain. Supply FE to the processing machines. AE2 also needs a crafting CPU and output storage; RS needs output storage. Both native networks need their normal power supply.

Eligible recipes from the connected machines appear in the native crafting catalogue: preparation, decomposition, permitted item/fluid synthesis and substance processes. Requests consume actual inputs, stored substances and FE. Finished outputs return to the network that requested them; synthesis permissions still apply.

To use **AE2 and RS at the same time**, use two interfaces on the same ReMatter network, one selected for each. They share the connected substance stores and processing machines. Each machine handles one order at a time. Finish pending jobs before changing an interface's connection type.

## Recipe viewers

The recipe catalogue comes from the server, including its data packs. Viewers display exact item and fluid batches, substance quantities, base FE costs, processing times, and explicit synthesis permissions. A nine-nugget batch appears as nine input or output items. Machine upgrades may change the effective cost or speed. Substance quantities are viewer ingredients, not an inventory item for every element.

- **JEI:** built against JEI 29.53.0.114 for 26.1.2. Displays decomposition, synthesis, electrolysis, and reactions.
- **EMI API:** built against **SEMI (Seasonal EMI) 1.1.24 for 26.1.2**, the continuation used in ReForging. The original EMI project currently has no 26.1.2 build. When SEMI and JEI are installed together, the native EMI integration supplies the ReMatter categories.

These integrations are optional. Installing an older viewer for a different Minecraft version is not supported.

The SEMI integration is compiled against its 26.1.2 release and uses that release's reload manager to refresh recipes after server chemistry changes; the EMI API does not expose a runtime recipe replacement method. An incompatible reload implementation logs a warning instead of interrupting gameplay. Reconnecting rebuilds the viewer catalogue. This does not claim compatibility with every EMI fork or future release.

## In-game guide

Craft the **ReMatter Guide** from a book and Silith. Its English tutorial covers the preparation chain, crystal variants, Matter Drives and material processing. The basic book needs no framework. With **GuideME 26.1.17-beta**, the same item opens interactive 3D scenes of preparation, hopper automation and industrial chambers with separate Machine Ports and fittings on the floor and ceiling.

Patchouli and Ponder are not required. ReMatter does not add duplicate books to those frameworks.

## Jade

The optional Jade integration targets **Jade 26.1.12**. Its machine overlay reads server-authorized status. It respects machine ownership and does not expose another player's inventory through generic item, fluid, energy, or progress providers.

## Mekanism chemicals

The adapter targets **Mekanism Version Locked 26.1.2-2.11**, whose chemical capability uses NeoForge's transactional `ResourceHandler<ChemicalResource>` API. The upstream Mekanism version number alone does not identify that API. An incompatible chemical capability disables the adapter instead of guessing a conversion.

Enable automation on a **Matter Drive** with a readable Cell, then use chemical automation against a block face. Solids, liquids and gases share the same capacity. Transfers use exact integer quantities and roll back aborted transactions. The Drive accepts transfers when it contains at least one readable Cell and all installed Cells are readable.

| Mekanism chemical | ReMatter substance | Conversion |
| --- | --- | --- |
| `mekanism:hydrogen` | Hydrogen, gas | 1 chemical unit = 1 matter unit |
| `mekanism:oxygen` | Oxygen, gas | 1 chemical unit = 1 matter unit |
| `mekanism:water_vapor` | Steam, gas | 1 chemical unit = 1 matter unit |
| `mekanism:chlorine` | Chlorine, gas | 1 chemical unit = 1 matter unit |
| `mekanism:sulfur_dioxide` | Sulfur dioxide, gas | 1 chemical unit = 1 matter unit |
| `mekanism:sulfur_trioxide` | Sulfur trioxide, gas | 1 chemical unit = 1 matter unit |
| `mekanism:sulfuric_acid` | Sulfuric acid, gas | 1 chemical unit = 1 matter unit |
| `mekanism:hydrogen_chloride` | Hydrogen chloride, gas | 1 chemical unit = 2 matter units |
| `mekanism:hydrofluoric_acid` | Hydrogen fluoride, gas | 1 chemical unit = 1 matter unit |
| `mekanism:ethene` | Ethene, gas | 1 chemical unit = 1 matter unit |
| `mekanism:lithium` | Lithium, gas | 1 chemical unit = 10 matter units |
| `mekanism:osmium` | Osmium, gas | 1 chemical unit = 5 matter units |

Water vapor is not an alias for every mod's steam fluid. Unknown gases are not accepted automatically. ReMatter's base water electrolysis uses 1,000 water to produce 1,000 hydrogen and 500 oxygen; the reverse reaction consumes those exact quantities. Packs that change hydrogen fuel values should review their complete energy balance.

The non-unit ratios preserve the targeted Mekanism recipes: one hydrogen and one chlorine produce one external hydrogen-chloride unit, 100 lithium units crystallize into one dust, and one osmium ingot produces 200 chemical units. Transfers only export whole chemical units; insufficient capacity and fractional remainders stay in storage. These mappings do not add new reactions between the stored substances. Importing a mapped chemical needs only a known substance definition. Exporting one needs an explicit release: a `gas` synthesis permission for the substance or a `fluid` permission for the chemical's own fluid ID.

Ten matching source fluids are supported through explicit profiles: hydrogen, oxygen, chlorine, sulfur dioxide, sulfur trioxide, sulfuric acid, hydrogen chloride, hydrofluoric acid, ethene, and lithium. Each conversion handles 1,000 mB and uses the ratios above. Their rotary recipes exchange one fluid mB for one chemical unit. Flowing variants are not separate inputs. Thermal steam, superheated sodium, radioactive chemicals, nuclear fuels, and antimatter are not mapped by these rules.

## Energy, fluids, and items

Processing machines accept FE from external energy sources. Matter Cables connect devices to shared matter stock and forward FE to eligible power ports.

Item and fluid automation is off by default. Enable it in the processing machine's **Configuration**. For an automated glass line, put a Cell in a connected Matter Drive. One hopper feeds sand into the Decomposer and another extracts glass from the Synthesizer. Keep the Decomposer running, select Glass with **Choose recipe** or **Recipe**, confirm with **Use recipe**, and press **Configuration → Run nonstop** on the Synthesizer to keep producing. **Stop repeat** stops continuous production. **Pause** and **Resume** retain the current order. Feeding input through a pipe does not request synthesis jobs on its own.

Input and output slots are separate. Upgrades and Matter Cells cannot be moved through these automation slots. Connected machines have the same owner and only traverse loaded chunks. Multiple Matter Drives contribute to the same network stock.

Each Matter Drive holds up to six portable Cells. A Cell can hold 64,000, 256,000 or 1,000,000 total units and up to 512 substance and phase pairs. You can carry a Cell as an item. To access its stock, install it in a Matter Drive and connect a Network Interface for AE2 or Refined Storage access.

## Material coverage and pack authoring

The [material catalogue](material-coverage.md) lists the reviewed material families and remaining exclusions. Optional profiles activate only when their required mod is present; generated JAOPCA items additionally require the exact registered item. This is material support, not complete support for those mods' production systems.

Add or rebalance profiles with [chemistry data packs](data-packs.md). Decomposition does not grant synthesis permission. Every synthesized output needs an explicit allowed category; manufactured components, machines, and equipment remain excluded.

For ReForging's current coexistence with Replication and Equivox, see the separate [optional integration pack](../integrations/reforging/README.md). It preserves ReForging's existing crystalline-silica identity, requires that quartz feedstock for silicon electrolysis, corrects the quartz grinding bonus, and provides targeted exclusions for ReMatter machines. These additional pack rules are not activated by installing the standalone mod.
