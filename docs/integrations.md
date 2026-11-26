# Integrations

ReMatter targets Minecraft 26.1.2 and NeoForge 26.1.2.126. It is a standalone chemistry mod; ReForging supplies a reference collection of optional integrations and external energy sources. Support for another Minecraft version or loader requires a separate port.

A Hand Preparator first makes the dry matrix feedstock without FE. Connect a Decomposer and Material Synthesizer to a Matter Drive holding a Cell. ReMatter provides its own storage network, recipe selection and production controls, with optional Applied Energistics 2 and Refined Storage integration.

## AE2 and Refined Storage

The mod metadata accepts AE2 from 26.1.13-beta up to, but not including, 26.2 and Refined Storage from 3.2.1 up to, but not including, 3.3.

Connect a **Network Interface** to the native grid and the ReMatter matter network. In its **Configuration**, enable **Automation** and select **Connect: AE2** or **Connect: Refined Storage**. On the connected processing machines, clear their work slots and tanks, then enable **Configuration → Network jobs: On**; upgrades may remain. Supply FE to the processing machines. AE2 also needs a crafting CPU and output storage; RS needs output storage. Both native networks need their normal power supply.

Eligible recipes from the connected machines appear in the native crafting catalogue: preparation, decomposition, permitted item/fluid synthesis and substance processes. Requests consume actual inputs, stored substances and FE. Finished outputs return to the network that requested them; synthesis permissions still apply.

Stored substances appear in AE2 and Refined Storage with their translated name, formula and phase, and RS grids find them by name. A substance whose definition is missing is not offered to either network and cannot be inserted or extracted through the interface; it stays in its Cell.

To use **AE2 and RS at the same time**, use two interfaces on the same ReMatter network, one selected for each. They share the connected substance stores and processing machines. Each machine handles one order at a time. Finish pending jobs before changing an interface's connection type. An interface that still holds jobs stays in charge of its connection type; a second interface of the same type waits until those jobs are done.

The interface belongs to the player who placed it. Substance transfers through it are checked: AE2 accepts the owner or a machine the owner placed on the same grid, and Refined Storage accepts a player only when the name belongs to the owner. AE2 and Refined Storage cables connect only on **Automatic** and **Matter Network** faces; set a face to **Disabled** to keep other networks out. AE2 cables and devices that another player placed directly against the interface do not connect, and only players who may open the interface, the owner and operators, can place Refined Storage blocks directly against it. Crafting jobs that reach the interface through its connected AE2 grid, or as automated Refined Storage sub-steps, cannot be attributed to a player: anyone who can use that native network can request them, so keep the network itself private.

If a data pack removes a recipe or changes its ingredients, queued jobs for it are cancelled after 10 seconds and their transferred inputs return to the native network. Changes that only affect FE cost or processing time continue the queued jobs with the new values. The interface status counts the outdated jobs while their inputs return.

If the native network does not confirm a finished delivery, the interface stops that order and keeps its resources. Its status then reads **Delivery stopped; resources retained** with the number of stopped orders. While a delivery is stopped, the configuration view offers **Release**. After a confirmation that lists the held resources, the owner or an operator receives matter as **Recovered Matter** parcels and items in their inventory, dropped at their feet when it is full; fluids go into the interface's tank. Whatever does not fit stays held, and a chat message reports the result. Use a parcel on a Drive or connected machine you own to return its substance to storage.

## Recipe viewers

The recipe catalogue comes from the server, including its data packs. Viewers display exact item and fluid batches, substance quantities, base FE costs with effective `FE/t` rates, processing times in seconds and ticks, and explicit synthesis permissions. A nine-nugget batch appears as nine input or output items. Machine upgrades may change the effective cost or speed. Substance quantities are viewer ingredients, not an inventory item for every element.

JEI and EMI show the same categories: decomposition (including the Industrial Decomposer), synthesis (including the Industrial Synthesizer), electrolysis, reaction, separation, phase change crystallization and renewable air separation, plus one preparation category each for the Hand Preparator, Material Preparator, Industrial Preparator and Sinter Furnace. Process entries name the process first; a data-pack process without a translation shows a readable form of its ID. Only substances that a shown recipe consumes or produces are listed as ingredients.

- **JEI:** built against JEI 29.53.0.115 for 26.1.2, with full substance hover lookups (`R`/`U`), Pressurized Canister hover support, non-container screen handlers (`ProcessSelectionScreen`, `PrimalTargetScreen`, `SubstanceDetailsScreen`), phase-distinct `MatterIngredient` identifiers, and GUI exclusion areas across all ReMatter screens.
- **EMI API:** built against **SEMI (Seasonal EMI) 1.1.24 for 26.1.2** and compatible with **EMI - Port Forward (PF-EMI)** for 26.1.2, with matching exclusion areas and substance hover lookups. The original EMI project currently has no 26.1.2 build. When an EMI implementation and JEI are installed together, the native EMI integration supplies the ReMatter categories.

These integrations are optional. Viewer builds for other Minecraft versions are not supported.

The EMI integration is compiled against SEMI 1.1.24 for 26.1.2 and uses the EMI reload manager to refresh recipes after server chemistry changes; the EMI API does not expose a runtime recipe replacement method. An incompatible reload implementation logs a warning instead of interrupting gameplay. Reconnecting rebuilds the viewer catalogue. This does not claim compatibility with every EMI fork or future release.

## In-game guide

Craft the **ReMatter Guide** from a book and Silith. Its English tutorial covers the preparation chain, crystal variants, advanced components, Matter Drives and material processing. The basic book needs no framework. With **GuideME 26.1.17-beta**, the same item opens interactive 3D scenes of preparation, hopper automation and industrial chambers with separate Machine Ports and fittings on the floor and ceiling.

Patchouli and Ponder are not required. ReMatter does not add duplicate books to those frameworks.

## Jade

The optional Jade integration targets **Jade 26.1.12**. Its server-authorized overlays cover processing machines (including live FE/t draw, active speed/efficiency/parallel upgrades, Pressure Module status, industrial Field/Room/Precision, Energy Matrix capacity/rate, Energy Reactor flux/coolant, and outdated/stopped Network Interface orders), Energy Cubes (charge level, net flow, docked charge/discharge counts), Wireless Access Points (online state, active range, boosters/Dimensional Card, Anchor Upgrade, power), Matter Level Emitters (configured substance/phase, threshold, emitted signal), Matter Cable Relays (gate, channel, direction, filter, rate caps), Spatial Anchors (active chunks, quota, power), Entangled and Dimensional Entangled Links (pairing state, dimension/range, Anchor Upgrade), Matter Cables (disconnected faces), and Cell Transfer Stations (transfer readiness and docked cells). Every provider respects machine ownership and does not expose another player's inventory, energy, fluid, or status details. The Jade settings list each ReMatter provider by name.

## Curios and Hotkeys

The optional Curios integration targets **Curios 15.0.0+26.1.2**. The **Wireless Matter Terminal** and **Portable Energy Cell** accept `curio`, `charm`, and `belt` slots, the **Spectrometer Lens** and **Discovery Lens** accept `head`, `charm`, and `curio` slots via `data/curios/tags/item/{head,curio,charm,belt}.json`, and `data/rematter/curios/entities/rematter.json` ensures `head`, `charm`, and `belt` slots are available on players when Curios is installed.

- **Wireless Matter Terminal:** assign the client keybind **Controls → ReMatter → Open Wireless Matter Terminal** (unbound by default) to open a bound, charged terminal carried in your inventory or equipped in a Curios slot without switching hotbar slots. Right-clicking the terminal in hand opens it normally rather than auto-equipping into Curios.
- **Portable Energy Cell:** when toggled on, recharges compatible FE items across both your player inventory and equipped Curios slots, and ticks continuously while equipped in a Curios slot.
- **Spectrometer Lens & Discovery Lens:** craft an early **Spectrometer Lens** (upgradable into a **Discovery Lens** for Analyzer Discovery), wear either lens in a Curios `head`, `charm`, or `curio` slot (or right-click in air to equip) to project the Field Spectrometer HUD and inspect full item compositions with `SubstanceIcon` badges on Shift tooltips even before research. Assign **Controls → ReMatter → Toggle Spectrometer Lens** (unbound by default) to toggle the spectrometer HUD and tooltips on or off without unequipping. Synthesizing materials still requires research.

## Cross-mod compatibility recipes and tags

Optional mod-loaded crafting recipes let pack tech lines participate in ReMatter's early/mid-game material preparation without bypassing ReMatter's core chemistry:

- **Ender IO Alloy Smelter & SAG Mill:** Rhevon Ingot, Lumivex Ingot, Virelite Ceramic, Diamond Lattice, Ender Conductor, and Netherite Sheath in the Alloy Smelter; bonus-free Diamond, Netherite, Rhevon, Lumivex, Amethyst, and Emerald dust routes in the SAG Mill.
- **Oritech Foundry, Assembler & Pulverizer:** alloy sintering, Diamond Matrix / Ender Conductor blending, and Diamond, Netherite, Rhevon, Lumivex, and Amethyst pulverizing.
- **FTB Industrial Contraptions Macerator:** Rhevon Dust, Lumivex Dust, Lapis Dust, Amethyst Dust, and Emerald Dust.
- **Energized Power Crusher:** Rhevon Dust, Lumivex Dust, Diamond Powder, Netherite Powder, Lapis Dust, Amethyst Dust, and Emerald Dust.
- **Mekanism Crusher:** Amethyst Dust, Ender Pearl Powder (when AE2 dust is absent), Rhevon Dust, and Lumivex Dust.
- **Occultism Crusher Spirit:** bonus-free 1:1 Rhevon Dust and Lumivex Dust routes.

Each recipe is guarded by `neoforge:mod_loaded` (and `neoforge:registered` for third-party recipe serializers) so it loads only when the target mod is installed.

ReMatter also ships standard cross-mod interoperability and safety tags:

- **Wrenches (`#c:tools/wrench`, `#c:wrenches`):** ReMatter recognizes wrenches from other mods for rotating, configuring, and dismantling ReMatter blocks, and exposes `rematter:matter_wrench` in both tags while letting Shift+Right-Click pass through on non-ReMatter blocks.
- **Pipe Connector (`#pipe_connector:pipe_block`, `#pipe_connector:placeable_items`):** all three Matter Cable tiers work with Pipe Connector auto-routing.
- **AE2 & Applied Mekanistics P2P (`#ae2:p2p_attunements/fe_p2p_tunnel`, `#appmek:p2p_attunements/chemical_p2p_tunnel`):** Matter Cables, Relays, and Energy Cubes attune FE P2P Tunnels; Pressurized Canisters attune Chemical P2P Tunnels.
- **Bookshelves (`#c:books`, `#minecraft:bookshelf_books`):** the ReMatter Guide fits in chiseled bookshelves and mod book racks.
- **Relocation and duplication protection (`#rematter:relocation_not_supported`):** stateful machines, multiblock controllers, Machine Ports, Energy Cubes, cables, Relays, Spatial Anchors, and Entangled Links are registered in `#c:relocation_not_supported`, `#c:relocation_not_permitted`, `#mekanism:cardboard_blacklist`, `#buildinggadgets2:deny`, `#justdirethings:swapper_deny`, `#justdirethings:paradox_absorb_deny`, `#oritech:blackhole_blacklist`, `#thaumaturge:portable_hole_blacklist`, and `#carryon:block_blacklist`; Primal Ores are also protected against `#justdirethings:paradox_absorb_deny`, `#oritech:blackhole_blacklist`, and `#productivebees:dupe_blacklist`. Routing/anchor conduits are listed in `#entangled:invalid_targets` so Entangled Blocks cannot bind to them.

## Mekanism chemicals

The adapter targets **Mekanism Version Locked 26.1.2-2.11**, whose chemical capability uses NeoForge's transactional `ResourceHandler<ChemicalResource>` API. The upstream Mekanism version number alone does not identify that API. An incompatible chemical capability disables the adapter instead of guessing a conversion.

Enable automation on a **Matter Drive** with a readable Cell, then use chemical automation against a block face. Solids, liquids and gases share the same capacity. Transfers use exact integer quantities and roll back aborted transactions. The Drive accepts transfers when it contains at least one readable Cell and all installed Cells are readable.

| Mekanism chemical | ReMatter substance | Conversion |
| --- | --- | --- |
| `mekanism:hydrogen` | Hydrogen, gas | 1 chemical unit = 1 matter unit |
| `mekanism:oxygen` | Oxygen, gas | 1 chemical unit = 1 matter unit |
| `mekanism:water_vapor` | Water, gas | 1 chemical unit = 1 matter unit |
| `mekanism:chlorine` | Chlorine, gas | 1 chemical unit = 1 matter unit |
| `mekanism:sulfur_dioxide` | Sulfur dioxide, gas | 1 chemical unit = 1 matter unit |
| `mekanism:sulfur_trioxide` | Sulfur trioxide, gas | 1 chemical unit = 1 matter unit |
| `mekanism:sulfuric_acid` | Sulfuric acid, gas | 1 chemical unit = 1 matter unit |
| `mekanism:hydrogen_chloride` | Hydrogen chloride, gas | 1 chemical unit = 2 matter units |
| `mekanism:hydrofluoric_acid` | Hydrogen fluoride, gas | 1 chemical unit = 1 matter unit |
| `mekanism:ethene` | Ethene, gas | 1 chemical unit = 1 matter unit |
| `mekanism:lithium` | Lithium, gas | 1 chemical unit = 10 matter units |
| `mekanism:osmium` | Osmium, gas | 1 chemical unit = 5 matter units |
| `mekanism:brine` | Brine, gas | 1 chemical unit = 1 matter unit |
| `mekanism:sodium` | Sodium, gas | 1 chemical unit = 1 matter unit |

Mekanism moves all of these through one chemical capability, so ReMatter stores each of them in the gas phase of its substance. For sulfuric acid, lithium, osmium, brine and sodium the gas phase is a transfer identity, not a claim about the physical state: it keeps the Mekanism chemical apart from liquid acid, lithium dust, osmium ingots, solid salt or solid sodium, and no ReMatter process converts between those phases.

Water vapor is not an alias for every mod's steam fluid. Unknown gases are not accepted automatically. ReMatter's base water electrolysis uses 1,000 water to produce 1,000 hydrogen and 500 oxygen; the reverse reaction consumes those exact quantities. Packs that change hydrogen fuel values should review their complete energy balance.

The non-unit ratios preserve the targeted Mekanism recipes: one hydrogen and one chlorine produce one external hydrogen-chloride unit, 100 lithium units crystallize into one dust, and one osmium ingot produces 200 chemical units. Transfers only export whole chemical units; insufficient capacity and fractional remainders stay in storage. These mappings do not add new reactions between the stored substances.

Importing a mapped chemical needs only a known substance definition. Exporting one needs an explicit release: a `gas` synthesis permission for the substance or a `fluid` permission for the chemical's own fluid ID. All fourteen mapped substances ship with a gas permission, so each can return to Mekanism.

Twelve matching source fluids are supported through explicit profiles: hydrogen, oxygen, chlorine, sulfur dioxide, sulfur trioxide, sulfuric acid, hydrogen chloride, hydrofluoric acid, ethene, lithium, brine, and sodium. Each conversion handles 1,000 mB and uses the ratios above. Their rotary recipes exchange one fluid mB for one chemical unit. Flowing variants are not separate inputs. Thermal steam, superheated sodium, radioactive chemicals, nuclear fuels, and antimatter are not mapped by these rules.

## Energy, fluids, and items

Processing machines accept FE from external energy sources, up to 131,072 FE/t per controller. Compact processors and the Crystal Generator have a **32,000 FE** base internal buffer; industrial multiblock controllers, the Industrial Crystal Generator, and the Primal Converter have a **128,000 FE** base buffer. Every upgradable compact or industrial processor provides dedicated simultaneous **Speed** (up to 4) and **Efficiency** (up to 4) upgrade slots (plus in-world Sneak + Right-Click installation): each Speed Upgrade accelerates work (2×, 4×, 7×, 10×) with +25% batch FE when unmatched, while each Efficiency Upgrade doubles the internal FE buffer (×2, ×4, ×8, ×16 — up to **512,000 FE** compact / **2,048,000 FE** industrial), cancels Speed FE surcharges 1:1, and saves 5% base FE per excess upgrade (max 20% saved; hard policy floor 75% of base). Active production batches, Analyzer Derive/Discovery sessions, and Primal Converter conversions draw FE continuously tick by tick, pause safely if power drops, and preserve stationary paid progress across chunk reloads; cancelling or picking up a machine refunds uncommitted FE up to buffer capacity. Catalogue batches stay within 1,200,000 base FE. Matter Cables, Matter Cable Relays, and Entangled Links forward pushed FE along permitted conduits (`Public`, `Trusted`, or `Private`) to ReMatter devices and external `Capabilities.Energy.BLOCK` sinks. ReMatter sources (Energy Cubes, Crystal Generators, Energy Matrices, Energy Reactors) can feed any external FE sink through cables or Relays; buffered external sources reporting a positive FE level can also feed non-looping external sinks using nested-transaction backflow detection (`Transaction.open`). Opaque zero-reporting pass-through proxies (such as Ender IO energy conduits) and anonymous senders still power all ReMatter devices normally, but are not automatically forwarded onward to other external blocks so mixed-mod cable rings cannot void FE. Cables store no FE and expose `extract == 0`, so passive pull-only extractors cannot pull FE out through a cable (use a pushing source or a ReMatter Energy Cube / Matrix port). Matter Cable Relays expose `Capabilities.Energy.BLOCK` on their two axial faces, auto-align to external FE blocks on placement, and enforce their Redstone gate, Power/Matter channel separation, One-Way diode direction, and exact `FE/t` cap on external FE as well as ReMatter power. Matter Cables keep their energized outer border glow active whenever FE transfers or while a connected external source reports a non-zero FE buffer (even when downstream machines are full); empty `0 FE` probes stay dark, and stateless zero-reporting conduits only light the border while FE is actively flowing. Foreign `Private` ReMatter devices remain strictly isolated across every conduit edge.

Item and fluid automation is off by default. Enable it under **Settings → Access** on each processor. Compact machines with physical outputs also offer an optional **Auto-Eject** toggle (off by default, copied by the Configuration Card) that pushes finished items and fluids into adjacent inventories or tanks only on faces explicitly configured to **Item Output** or **Fluid Output** while Automation is on. For an automated glass line, put a Cell in a connected Matter Drive. One hopper feeds sand into the Decomposer and another extracts glass from the Synthesizer. Keep the Decomposer running, select Glass with **Choose recipe** or **Recipe** (the Synthesizer recipe picker also supports **Known** / **Locked**, live **In stock only**, and category filters across **All**, **Metals**, **Crystals**, **Stone**, **Organic**, and **Fluids** alongside search), confirm with **Use recipe**, and enable **Loop** beside **Make 1** on the Synthesizer's main view. Toggle it off to stop continuous production. **Pause** and **Resume** retain the current order. Feeding input through a pipe does not request synthesis jobs on its own.

Input and output slots are separate. Upgrades and Matter Cells cannot be moved through these automation slots. A matter network respects each device's and cable's security mode (`Public`, `Trusted`, or `Private`) and only traverses loaded chunks. Multiple Matter Drives contribute to the same network stock.

Each Matter Drive holds up to six portable Cells. An ordinary Cell can hold 4,000,000, 16,000,000, 64,000,000 or 256,000,000 total units and up to 512 substance and phase pairs. You can carry a Cell as an item. To access its stock, install it in a Matter Drive and connect a Network Interface for AE2 or Refined Storage access.

## Material coverage and pack authoring

The [material catalogue](material-coverage.md) lists the reviewed material families and remaining exclusions. Optional profiles activate only when their required mod is present; generated JAOPCA items additionally require the exact registered item. This is material support, not complete support for those mods' production systems.

Add or rebalance profiles with [chemistry data packs](data-packs.md). Decomposition does not grant synthesis permission. Every synthesized output needs an explicit allowed category; manufactured components, machines, and equipment remain excluded.

For ReForging's pack rules and optional third-party coexistence safeguards, see the separate [optional integration pack](../integrations/reforging/README.md). It preserves ReMatter's standard sand-to-silicon electrolysis while keeping crystalline quartz separate, removes Ender IO grinding-ball bonuses from quartz-gem and obsidian milling, disables native recipes that would turn a single dust or cheap ingredients into a whole block or ingot, and retains optional Replication/Equivox exclusions for packs that install those mods. These additional pack rules are not activated by installing the standalone mod.

## Specialist machines and production rules

The compact Chemical Processor and the multiblock Chemical Reactor run chemical processes, including electrolysis, separation and phase changes. The multiblock **Industrial Decomposer**, **Industrial Synthesizer**, **Transmutation Reactor**, **Primal Reactor**, **Energy Reactor**, and **Energy Matrix** share ReMatter's modular multiblock framework (`3×3×4` to `9×9×9`, with `3×3×3` minimum on the Energy Matrix and `5×5×5` minimum on late-game reactors, dedicated Machine Ports carrying items, fluids, FE, Redstone, and matter, plus 3D blueprint preview and Auto-Build). Processing chambers run at 2× base speed with separate Speed, Efficiency, and Parallel slots (up to 80× throughput across 4 parallel lanes). JEI/EMI workstations, manual choices and native network jobs use the same routing. The Material Synthesizer handles approved crystal-family outputs; every diamond form requires tier 2 with a Pressure Module in the compact machine, while a formed Industrial Synthesizer provides tier 2 crystallization built in. This also applies to AE2 and RS patterns. Owner advancements gate execution; revocation prevents new batches and refunds already owned automation inputs.

The default Air Separation recipe produces 200 stored oxygen units for 16,000 base FE over 400 work ticks in the Overworld, with air directly above and open sky, alongside an Atmospheric Nitrogen separation route. The Drive exports oxygen to Mekanism at 1:1 for uses such as its Purification Chamber. Gas venting applies only to explicitly selected newly produced gas outputs; it starts off and does not alter stored stock or native crafting contracts.

Production targets count either a substance/phase in the connected ReMatter network or the producer's own item/fluid output buffer. AE2 and RS inventories are outside this scope. All producers share reservations for inputs and planned substance outputs; only complete batches fit below the hard target. Manual requests have priority. Industrial controllers accept up to three Parallel Upgrades for four concurrent, fully paid batches in their dedicated Parallel slot alongside Speed and Efficiency Upgrades. Combined structural and Efficiency savings never exceed 25%.

Every ReMatter device and Matter Cable supports **Public** *(default)*, **Trusted**, and **Private** security modes (configurable in device settings, via the dedicated **Security Card** for single blocks or connected networks, or per-player placement defaults via `/rematter security default`). Under **Trusted** mode, both **FTB Teams 26.1.2.5** teammates (checked live, failing closed when the backend is unavailable) and players added with `/rematter trust` can operate devices and share connected networks. Only the owner (or a server operator) can change a device's security mode; advancement checks remain tied to the device's owner.

## Reference versions

The compile-only APIs and metadata target Minecraft 26.1.2, NeoForge 26.1.2.126, AE2 26.1.13-beta, Refined Storage 3.2.1, Jade 26.1.12, GuideME 26.1.17-beta, JEI 29.53.0.115, SEMI 1.1.24, Mekanism Version Locked 26.1.2-2.11 (mod version 10.8.0), Curios 15.0.0+26.1.2, and FTB Teams 26.1.2.5. Material catalogue IDs, compatibility tags, and optional cross-mod recipe schemas were audited against **ReForging 0.70.1** (building on the original ReForging 0.11.1 reference pack baseline). Exact API compilation and simulated GameTests are not a claim that every optional third-party runtime path has been exercised live.
