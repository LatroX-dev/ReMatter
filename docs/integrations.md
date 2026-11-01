# Integrations

ReMatter targets Minecraft 26.1.2 and NeoForge 26.1.2.124. It is a standalone chemistry mod; ReForging supplies a reference collection of optional integrations and external energy sources. Support for another Minecraft version or loader requires a separate port.

A Hand Preparator first makes the dry matrix feedstock without FE. The first chemistry setup is then a Decomposer and Material Synthesizer with one Matter Cell moved between their local Cell slots. A Drive and network are optional later additions. Applied Energistics 2 and Refined Storage are not required for storage, recipe selection, or production. ReMatter uses its own matter network and standard item and fluid capabilities. Dedicated AE2/RS storage-network adapters are not implemented.

## Recipe viewers

The recipe catalogue comes from the server, including its data packs. Viewers display exact item and fluid batches, substance quantities, base FE costs, processing times, and explicit synthesis permissions. A nine-nugget batch appears as nine input or output items. Machine upgrades may change the effective cost or speed. Substance quantities are viewer ingredients, not an inventory item for every element.

- **JEI:** built against JEI 29.53.0.114 for 26.1.2. Displays decomposition, synthesis, electrolysis, and reactions.
- **EMI API:** built against **SEMI (Seasonal EMI) 1.1.24 for 26.1.2**, the continuation used in ReForging. The original EMI project currently has no 26.1.2 build. When SEMI and JEI are installed together, the native EMI integration supplies the ReMatter categories.

These integrations are optional. Installing an older viewer for a different Minecraft version is not supported.

The SEMI integration is compiled against its 26.1.2 release and uses that release's reload manager to refresh recipes after server chemistry changes; the EMI API does not expose a runtime recipe replacement method. An incompatible reload implementation logs a warning instead of interrupting gameplay. Reconnecting rebuilds the viewer catalogue. This does not claim compatibility with every EMI fork or future release.

## In-game guide

The **ReMatter Guide** is crafted with one book and one copper ingot. Its English tutorial starts with hand extraction of Silith and Argil, their dry mixture, furnace-grown Matrix Crystals and ordinary iron/copper parts, then follows sand into a local Cell and back out as glass. It continues with a Drive, hoppers and chests for automatic production. The basic book needs no framework. With **GuideME 26.1.17-beta**, the same item opens interactive 3D scenes of hand and powered preparation, the two-machine Cell setup, later Drive and hopper automation and the complete 3×3×3 preparation and 3×3×4 reactor chambers with Glass and separate Ports.

Patchouli and Ponder are not required. ReMatter does not add duplicate books to those frameworks.

## Jade

The optional Jade integration targets **Jade 26.1.12**. Its machine overlay reads server-authorized status. It respects machine ownership and does not expose another player's inventory through generic item, fluid, energy, or progress providers.

## Mekanism chemicals

The adapter targets **Mekanism Version Locked 26.1.2-2.11**, whose chemical capability uses NeoForge's transactional `ResourceHandler<ChemicalResource>` API. The upstream Mekanism version number alone does not identify that API. An incompatible chemical capability disables the adapter instead of guessing a conversion.

Enable automation on a **Matter Drive with a readable Cell** or an original **Matter Storage**, then use chemical automation against a block face. The shared storage capacity applies to solids, liquids, and gases together. Transfers use exact integer quantities and roll back aborted transactions. A missing or unreadable Cell refuses transfers; the Cell itself is not an automation slot.

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

The non-unit ratios preserve the targeted Mekanism recipes: one hydrogen and one chlorine produce one external hydrogen-chloride unit, 100 lithium units crystallize into one dust, and one osmium ingot produces 200 chemical units. Transfers only export whole chemical units; insufficient capacity and fractional remainders stay in storage. These mappings do not add new reactions between the stored substances.

Ten matching source fluids are supported through explicit profiles: hydrogen, oxygen, chlorine, sulfur dioxide, sulfur trioxide, sulfuric acid, hydrogen chloride, hydrofluoric acid, ethene, and lithium. Each conversion handles 1,000 mB and uses the ratios above. Their rotary recipes exchange one fluid mB for one chemical unit. Flowing variants are not separate inputs. Thermal steam, superheated sodium, radioactive chemicals, nuclear fuels, and antimatter are not mapped by these rules.

## Energy, fluids, and items

Processing machines accept FE from external energy sources. ReMatter has no survival or creative generator. Matter Pipes connect matter storage; they do not distribute power.

Item and fluid automation is off by default. Enable it in the processing machine's **Settings**. For an automated glass line, put the Cell in a Drive and leave the two local Cell slots empty. A hopper feeds sand into the Decomposer; another extracts glass from the Synthesizer. Start the Decomposer, select Glass and turn **Repeat** on to keep producing. Feeding input through a pipe does not request synthesis jobs on its own.

Input and output slots are separate. Upgrades and Matter Cells cannot be moved through these automation slots. Connected machines have the same owner and only traverse loaded chunks. Use one Matter Drive or original Matter Storage per network. An inserted local Cell takes priority over that network, including when it is full or unreadable; the screen identifies the active source. Cell changes pause processing.

The Drive holds one portable Cell with 64,000, 256,000 or 1,000,000 total units and at most 512 substance/phase pairs. Ordinary item storage can carry a Cell as an item; that does not make its internal substance inventory an AE2/RS inventory. Original Matter Storage blocks retain their built-in contents and require no Cell.

## Material coverage and pack authoring

The [material catalogue](material-coverage.md) lists the reviewed material families and remaining exclusions. Optional profiles activate only when their required mod is present; generated JAOPCA items additionally require the exact registered item. This is material support, not complete support for those mods' production systems.

Add or rebalance profiles with [chemistry data packs](data-packs.md). Decomposition does not grant synthesis permission. Every synthesized output needs an explicit allowed category; manufactured components, machines, and equipment remain excluded.

For ReForging's current coexistence with Replication and Equivox, see the separate [optional integration pack](../integrations/reforging/README.md). It preserves ReForging's existing crystalline-silica identity, requires that quartz feedstock for silicon electrolysis, corrects the quartz grinding bonus, and provides targeted exclusions for ReMatter machines. These additional pack rules are not activated by installing the standalone mod.
