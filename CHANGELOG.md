# Changelog

## 0.51.0

Cross-mod interoperability, Synthesizer filtering, and ReForging material expansion release for **Minecraft 26.1.2**, **NeoForge 26.1.2.126**, and **Java 25**.

### Energy, Cables & Relays (External-Mod Interoperability)
- **Energized Matter Cable Border Glow (`POWERED`) with External FE Sources:** Matter Cables now keep their glowing outer border rails lit whenever fed by a live external FE buffer (such as Mekanism Universal Cables, Powah, Flux Points, or Oritech) — even when downstream machines are full or cables branch — and on any real committed FE transfer from stateless conduits, while empty `0 FE` probes (both aborted and committed) stay dark.
- **Matter Cable Relay Full External-Mod Compatibility:** `MatterCableRelayBlock` now exposes `Capabilities.Energy.BLOCK` on its two axial faces, auto-aligns to external FE blocks on placement, supports unowned `PUBLIC` relays, invalidates capabilities and chunk topology on external rotation as well as Matter Wrench rotation, treats relays as conduits on `AUTOMATIC` Energy Matrix ports, and enforces Redstone gates, Power/Matter channel separation, One-Way diode direction, and exact `FE/t` caps on external FE.
- **Bidirectional External FE Routing, Loop Safety & Security Hardening:** Matter Cables forward FE to external `Capabilities.Energy.BLOCK` sinks as well as ReMatter devices, with nested-transaction backflow detection (`Transaction.open`) for buffered external sources and a strict non-forwarding boundary for opaque zero-reporting pass-through proxies (e.g. Ender IO conduits) to prevent mixed-mod cable rings from voiding FE in Mekanism `EnergyNetwork`. Re-entrancy is scoped per active cable component (allowing separate ReMatter networks bridged externally), late-activated external FE ports are observed via `BlockCapabilityCache` and neighbor updates, incoming source authorization covers all ReMatter generators/matrices/reactors/cubes (including cable energize), edge-by-edge security prevents scan-order identity leakage, and `ChunkRevisions.trustEpoch()` invalidates cached cable components immediately on trust revocation.

### Cross-Mod Tags, Wrenches & Machine Recipes
- **Universal Wrench Passthrough:** `WrenchInteractions` now recognizes `#c:wrenches` alongside `#c:tools/wrench`, exposes `rematter:matter_wrench` in both tags, and passes Shift+Right-Click on non-ReMatter blocks cleanly through both `useOn` and `use` fallback so other mods' dismantle/rotate and off-hand interactions work untouched while Shift+Right-Click in air or on ReMatter blocks keeps cycling wrench mode.
- **Routing, P2P & Relocation/Dupe Protection Tags:** Added `#c:books` & `#minecraft:bookshelf_books` for the ReMatter Guide, `#pipe_connector:pipe_block` & `#pipe_connector:placeable_items` for all three Matter Cable tiers, `#ae2:p2p_attunements/fe_p2p_tunnel` & `#appmek:p2p_attunements/chemical_p2p_tunnel`, `#entangled:invalid_targets` for routing/anchor conduits, and `#rematter:relocation_not_supported` wired into `#c:relocation_not_supported`, `#c:relocation_not_permitted`, Mekanism Cardboard Box, Building Gadgets 2, Just Dire Things, Oritech Black Hole, Productive Bees, Thaumaturge Portable Hole, and Carry On blacklists.
- **Cross-Mod Dust Routes (`neoforge:mod_loaded` guarded):** Added bonus-free Ender IO SAG Mill (`rhevon`, `lumivex`, `amethyst`, `emerald`), Oritech Pulverizer (`amethyst`), and Occultism Crusher (`rhevon`, `lumivex`, `ignore_crushing_multiplier: true`) recipes.

### Material Catalogue & Synthesizer Picker UX
- **71 New ReForging `0.70.1` Material Profiles (+6 Families):** Added verified coverage for **Biomes O' Plenty** (25), **Regions Unexplored** (33), **Integrated Dynamics** (5), **Flux Networks** (`flux_dust`, 1), and **Nautec** (7), bringing the catalogue to **2,349 profiles**, **387 research families**, and **900 explicit synthesis outputs** (with strict conservation lower bounds for Menril sawmill/squeezer chains, Regions Unexplored Ash/Ashen Dirt, and Nautec Cast Iron/Aquarine Steel).
- **Synthesizer Recipe Picker Categories & Live `In stock only` Filter:** Added 6 responsive category tabs (**All**, **Metals**, **Crystals**, **Stone**, **Organic**, **Fluids**) that wrap cleanly in narrow windows without overlapping the detail panel, plus a server-authoritative **In stock only** filter (`synthesisStock` in `MachineExtraPayload`) immune to the 512-row stock list cap and search truncation, with clear syncing and partial-stock indicators.

## 0.50.1

Hotfix release for **Minecraft 26.1.2**, **NeoForge 26.1.2.126**, and **Java 25**, fixing world upgrades from `0.8.x` / `0.9.0` / `0.10.0`, unowned/command-placed Matter Cables, and a recipe-book advancement log error.

### Fixes
- **Lossless 14-Slot Machine Migration (`0.8.x` / `0.9.0` / `0.10.0` → `0.50.1`):** Fixed `MachineSchema.rejection(...)` (and empty-device salvage checks in `ToolSalvage`) rejecting `14`-slot saved machines after slots `14` (`Mass Compression`) and `15` (`Anchor Upgrade`) were added in `0.50.0`. Existing machines now migrate directly to 16 slots with all stored items, cells, and upgrades intact, immediately restoring normal operation, Matter networks, and external-mod FE injection over Matter Cables.
- **Unowned & Command-Placed Matter Cables (`PUBLIC` Compatibility):** Fixed unowned Matter Cables (from pre-`0.8.0` worlds, `/setblock`, `/fill`, WorldEdit, or schematics) collapsing into isolated cubes on chunk load or neighbor updates and refusing to route Matter or FE. Unowned `PUBLIC` cables now connect visually and functionally with each other, with `PUBLIC` devices/cables, and with external-mod FE sources while strictly preserving `PRIVATE` and `TRUSTED` isolation and unclaimed-machine claim behavior.
- **Recipe-Book Advancement Predicate (`wireless_booster.json`):** Fixed `shaped_discovery` emitting `"items": ["#rematter:full_ender_pearl_powders"]` instead of `"items": "#rematter:full_ender_pearl_powders"`, eliminating the datapack load `DataResult.Error` log entry, with a permanent regression test across all shipped advancements.
- **Documentation:** Documented **EMI - Port Forward (PF-EMI)** alongside JEI (including JEI++) and SEMI in `README.md` and `docs/integrations.md`.

## 0.50.0 — The Endgame, Power & Logistics Expansion

ReMatter `0.50.0` is the largest feature expansion in the mod's history, turning ReMatter from a compact workshop chemistry mod into a complete end-to-end industrial, power, and multi-dimensional logistics platform for **Minecraft 26.1.2**, **NeoForge 26.1.2.126**, and **Java 25**.

### Endgame Multiblocks, Power Generation & Deep Extraction
- **Energy Reactor (`rematter:energy_reactor_controller`):** High-output multiblock power generator (`5×5×5` to `9×9×9`) built with **Reactor Fuel Cores** and **Reactor Induction Coils**. Supports **Crystal**, **Matter** (Uranium, Lithium, Hydrogen, Methane, Ethene), and **Primal** fuel modes, optional **Water** and **Cryogenic Nitrogen** coolant overdrive (`+30%`), Flux ramp-up (`0–100%`), genuine operating light (`lightLevel = 12` on active controllers and Fuel Cores), dedicated `POWER_OUTPUT` and `REDSTONE` ports, automatic throttling when full, and lossless energy overhang preservation if the chamber is reshaped smaller.
- **Energy Matrix (`rematter:energy_matrix_controller`):** Silent 64-bit multiblock energy vault (`3×3×3` to `9×9×9`) supporting four freely mixable 3D tiers of **Energy Matrix Cells** and **Energy Matrix Providers** (`Basic`, `Reinforced`, `Resonant`, `Primal`), scaling up to tens of billions of FE and millions of `FE/t` with configurable `POWER` / `POWER_OUTPUT` ports and bidirectional Charge/Discharge item docks.
- **Transmutation Reactor (`rematter:transmutation_reactor_controller`):** Endgame multiblock (`5×5×5` to `9×9×9`) powered by **Particle Emitters**, **Field Coils**, and catalyst cores (**Particle Resonator** / **Primal Matrix Core**) unlocking 13 balanced industrial transmutation routes.
- **Primal Reactor (`rematter:primal_reactor_controller`):** Endgame multiblock (`5×5×5` to `9×9×9`) with **Primal Condenser Cores**, **Particle Emitters**, and optional **Mass Compression Upgrade**, delivering atomic Primal Condensation and Manifestation with Stability scaling and a new **4M Primal Cell** (`rematter:primal_cell_4m`).
- **Deep-Core Extractor (`rematter:deep_extractor`):** Bedrock- and deepslate-probing extraction machine with **Bore Core** and **Extraction Focus Upgrade**, drilling concentrated geological filtrates for industrial refining.
- **Primal Energy Cube (`rematter:primal_energy_cube`):** Tier 4 Energy Cube (`4.096G FE`) upgrading cleanly from the Resonant Energy Cube while preserving exact stored FE, side configuration, and docked items.

### Smart Multiblock Blueprints, Connected Glass, 3D Preview & Auto-Build
- **Interactive Blueprint Planner & 3D Wireframe Hologram:** Expanded industrial chamber sizing up to `9×9×9` with configurable dimensions, port count, interior module counts, live bill of materials, and a compact **Shell Style** selector (`Sides Glass`, `All Glass`, `Full Casing`) synchronizing the BOM, in-world 3D wireframe hologram, and Auto-Build simultaneously.
- **Seamless Connected Reactor Glass & Mixed Shells:** All five reactor and matrix glass tiers now render seamlessly with borderless connected textures when formed (`FORMED = true`). Floors and roofs freely mix Glass and Casing wherever no interior module or Matrix core anchors directly against them, with live `FORMED` updates on in-place edits.
- **Smart Layouts, Partial-Build Completion & Ground Lift:** Bottom-first symmetric port placement, concentric perimeter-ring module distribution for reactors, outward same-type column stacking for 3D Energy Matrix cores, automatic detection and completion proposals when placing a controller into a half-to-three-quarter built chamber, and automatic `+1` ground lift when Auto-Building directly from a solid floor.
- **Atomic Full-Structure Dismantle & Claim-Compatible Auto-Build:** Using the **Matter Wrench** in `Dismantle` mode on a formed industrial controller verifies ownership and NeoForge `BreakBlockEvent`, removes interior → shell → controller safely, saves the exact dismantled layout (including Energy Matrix Cell/Provider tiers, mixed glass, and port locations) onto the controller item, and returns stacked drops strictly for blocks actually removed. Auto-Build authorizes NeoForge `BlockEvent.EntityPlaceEvent` on every target block with full transactional rollback on claim veto.

### Network Logistics, Native Chunkloading & Wireless Overhaul
- **Matter Cable Relay (`rematter:matter_cable_relay`):** Inline auto-aligning cable valve with Redstone gating (`Always Open`, `High`, `Low`), channel separation (`Power + Matter`, `Power Only`, `Matter Only`), animated **One-Way Diode** mode (`Green In → Red Out`), aggregate-state & 8-slot substance whitelist/blacklist filters, and shared per-tick `FE/t` and `Matter/t` throughput caps across all controllers using the relay.
- **Dimensional Entangled Link (`rematter:dimensional_entangled_link`):** Cross-dimension, unlimited-distance Resonant Matter + FE link with zero BlockEntity ticks, configurable port faces, custom naming, and optional Anchor Upgrade support.
- **Native Chunkloading (`Spatial Anchor` & `Anchor Upgrade`):** Built on NeoForge's `TicketController` with per-player quotas and optional online-only mode (`rematter-chunkloading.toml`), an interactive clickable **5×5 chunk coverage map** (plus `1×1`, `3×3`, and `5×5` presets), FE upkeep, Redstone gating, and automatic login reactivation. Install **Anchor Upgrades** directly into machines, multiblocks, Dimensional Entangled Links, or powered Wireless Access Points.
- **Wireless Access Point Rebalance & Dimensional Card (`rematter:dimensional_card`):** Unified Range Upgrade slot accepting either up to `16× Wireless Boosters` (`104 FE/t` max) or `1× Dimensional Card` (`256 FE/t`, unlimited cross-dimension reach), power- and Redstone-gated Anchor Upgrade (`+16 FE/t`), and lossless Schema 1 → 2 migration for both `0.10.0` and multi-slot development worlds.
- **Global & Per-Machine Network Reserves:** Configure global minimum stock floors (including Primal Matter) directly from the **Matter Terminal** (owner/operator managed, synced read-only to guests) alongside per-machine stock reserves and Energy Reactor fuel/coolant filters.
- **Custom Device Names, Per-Side Cable Disconnect & Energized Cable Glow:** Rename any device in-UI or via Anvil (searchable in **Matter Terminal → Devices** with dimension tags), toggle individual Matter Cable faces with the Matter Wrench to run adjacent cables side-by-side without merging, and see live energized PCB trace glow whenever cables carry power.

### Catalogue, Audio, Integrations, Migration & Hardening
- **Expanded Material Catalogue & Conservation Audits:** Expanded to **2,278 reviewed item profiles**, **95 substances**, **14 fluid profiles**, **846 explicit synthesis outputs**, **14 fluid outputs**, and **14 gas outputs**, plus closed conservative decomposition floors on `rematter:anchor_upgrade` and `rematter:dimensional_card` and balanced `minecraft:cobweb` (`1,250` protein with `unavailable_with_mods: ["mekanism"]` on synthesis) to prevent both Botania `5 string → cobweb` and Mekanism `cobweb → 9 string` protein loops.
- **Sound Set `workshop-v14`:** Added dedicated deep endgame machine audio roles (click-free Smooth Rotary Bore for the Deep-Core Extractor, Energy Reactor, Transmutation Reactor, and Primal Reactor; Energy Matrix remains silent by design).
- **Automatic Controller Registry Aliases:** NeoForge block and item aliases migrate `rematter:industrial_decomposer_controller` → `rematter:decomposer_reactor_controller` and `rematter:industrial_synthesizer_controller` → `rematter:synthesizer_reactor_controller` transparently in existing worlds.
- **Reference Pack (`integrations/reforging/`):** Explicitly documents and disables the 19 new `0.50.0` industrial, transmutation, and refining processes in the optional ReForging datapack pending its pack-specific integration audit; standalone ReMatter enables all of them by default.
- **Multiplayer Security & Reliability Hardening:** Unified `MachineProtection` break and explosion safeguards across all 10 owned device types (including `Wireless Access Point`, `Matter Level Emitter`, `Matter Cable Relay`, `Spatial Anchor`, and `Dimensional Entangled Link`), replaced recursive relay-zone search with exact linear `O(V + E)` reachability, enforced fail-closed Redstone gate evaluation on relay chunk loads, and expanded Jade overlays across every new device.

## 0.10.0

### Multiplayer Security & Security Card
- **Mekanism-Style Security Modes (`Public` / `Trusted` / `Private`):** Every ReMatter machine, Energy Cube, Cell Transfer Station, Wireless Access Point, Matter Level Emitter, Entangled Link, and Matter Cable now supports per-device security modes:
  - **`Public`** *(default)*: Anyone can operate, configure, connect coop cables/networks, and wrench/break devices, while only the owner (or a server operator) can change security modes.
  - **`Trusted`**: Accessible to the owner, live **FTB Teams** teammates, and players added via `/rematter trust` — with shared coop Matter/FE networks and Entangled Links across trusted players.
  - **`Private`**: Strictly isolated to the owner (and server operators); foreign cables, networks, FE routing, Entangled Links, Wrench diagnostics, and Jade details are blocked.
- **Dedicated Security Card (`rematter:security_card`):** Craft a dedicated Security Card (matching the Configuration Card chassis with an isometric padlock icon and live emerald/amber/rose LED status for `Public`/`Trusted`/`Private`):
  - **Right-Click air** (or **Sneak + Right-Click air**) to cycle the card's target security mode.
  - **Right-Click** any owned device or formed Machine Port to stamp that device's mode.
  - **Sneak + Right-Click** any device, Machine Port, or Matter Cable to apply the mode across your entire connected loaded network (including Matter Cables and paired Entangled Links), automatically skipping other players' devices.
  - The **Configuration Card** remains dedicated purely to technical setups (ports, Redstone, targets, filters, Entangled Link pairing) and never overwrites security modes.
- **Player Commands (no operator permission required):**
  - `/rematter trust add|remove|list|clear` manages your persistent Overworld-saved trusted player list for `Trusted` mode (complementing optional FTB Teams).
  - `/rematter security default [public|trusted|private]` sets your personal default security mode for newly placed ReMatter devices and cables (global default remains `Public`).
- **In-Device Security Controls:** Added cycling `Security` controls (left-click forward, right-click or Shift+left-click backward, locked for non-owners) directly in **Settings → Access** and on Energy Cubes, Cell Transfer Stations, Wireless Access Points, and Matter Level Emitters.

### Unified Synthesis & Expanded Material Catalogue
- **Cross-Mod Synthesis & Viewer Unification:** Cross-mod duplicate synthesis outputs now declare explicit `synthesis_group` keys (69 groups, e.g. `dust:copper`, `ingot:steel`, `dust:salt`). With `synthesis.unifyOutputs = true` (default in `rematter-common.toml`), the server collapses each group to a single canonical output following `synthesis.modPriority` across the Material Synthesizer, Industrial Synthesizer, Network Interface (AE2/RS), and JEI/EMI recipe viewers — while the **Decomposer** and **Analyzer** continue accepting every mod variant seamlessly.
- **Massive Material Catalogue Expansion:** Expanded the reviewed exact-ID catalogue from 1,043 to **1,989 item profiles across 41 namespaces** with **747 explicit item synthesis outputs**, **14 fluids**, **14 gas outputs**, **90 substances**, and **326 research families**:
  - Expanded vanilla natural blocks, woods, stones, dyes, crops, and food/salt chemistry (`bakeries:salt`, `croptopia:salt`, `croptopia:salt_ore`, `refurbished_furniture:sea_salt`, `alltheores:salt_dust`, `mekanism:salt`, `nautec:salt` unified under `dust:salt` with standalone fallback).
  - Added **Atmospheric Nitrogen** collection in the Atmospheric Collector / Industrial Reactor.
  - Added conservative recovery-only decomposition profiles for all **97 non-Primal ReMatter items and empty devices** (including empty machines, Energy Cubes, Cell Transfer Stations, emptied unpartitioned Matter Cells, Matter Wrench, Configuration Card, and Security Card — strictly rejecting filled, powered, partitioned, linked, or unreadable items).
  - Broadened optional exact-ID mod coverage across major tech, storage, and worldgen mods while keeping manufactured components, tools, armor, weapons, and machines strictly outside synthesis.

### Recipe Viewers (JEI / JEI++ & EMI / SEMI) & UI Polish
- **Full JEI / JEI++ Parity:** Implemented native JEI `SubstanceDetails.Viewer` opening (`R`/`U` recipe & usage lookup with live JEI key bindings), generic `AbstractContainerScreen` hover support for `SubstanceHoverProvider` screens and Pressurized Canisters, `IScreenHandler` bounds & hover support for `ProcessSelectionScreen`, `PrimalTargetScreen`, and `SubstanceDetailsScreen`, phase-distinct `MatterIngredient` identifiers (`<ns>:<path>/<phase>`), and complete GUI exclusion areas across all ReMatter device screens in both JEI and EMI.
- **UI Fixes:** Tinted all Cancel buttons across machine, terminal, process selection, and Primal target screens with the active device accent colour, and fixed text/button clearance on the Decomposer, Synthesizer, and Preparator **Settings → Access** panel.

## 0.9.0

### Breaking & Migration Notes
- **Matter Cell Tier Rename & 64× Storage Rebalance:** Ordinary Matter Cells now scale to match Applied Energistics 2 (`1k`/`4k`/`16k`/`64k`) and Refined Storage (`4k`/`16k`/`64k`/`256k`) storage ratios while preserving exact `1,000 u = 1 ingot / 1 bucket` chemistry conservation:
  - `rematter:matter_cell_64k` (`64,000 u`) → **`rematter:matter_cell_4m`** (`4,000,000 u` = 4,000 ingots/buckets)
  - `rematter:matter_cell_256k` (`256,000 u`) → **`rematter:matter_cell_16m`** (`16,000,000 u` = 16,000 ingots/buckets)
  - `rematter:matter_cell_1m` (`1,000,000 u`) → **`rematter:matter_cell_64m`** (`64,000,000 u` = 64,000 ingots/buckets)
  - New Tier 4 endgame cell: **`rematter:matter_cell_256m`** (`256,000,000 u` = 256,000 ingots/buckets; a full 6-bay Matter Drive holds `1,536,000,000 u`)
  - **Automatic world migration:** NeoForge item registry aliases automatically migrate existing `matter_cell_64k`, `matter_cell_256k` and `matter_cell_1m` cells to `matter_cell_4m`, `matter_cell_16m` and `matter_cell_64m` without losing any stored substances or partitions. Previously crafted `rematter:matter_cell_4m` cells remain valid at `4,000,000 u` as the new Tier 1 cell (all stored contents remain intact; craft `matter_cell_256m` for the new Tier 4 tier). Pack authors and datapacks referencing old Matter Cell IDs (quests, tags, scripts) should update to `matter_cell_4m`, `matter_cell_16m`, `matter_cell_64m` and `matter_cell_256m`.

### Gameplay & Balance
- **Shared Energy Core & Tiered Energy Cube Upgrades:** Add `rematter:energy_core` as the shared building block across the Portable Energy Cell, Wireless Matter Terminal and all three Energy Cubes. Higher Energy Cube tiers now upgrade the previous tier (`energy_cube` → `reinforced_energy_cube` → `resonant_energy_cube`) via `rematter:energy_upgrade_shaped`, transferring exact stored FE, configured side modes and Charge/Discharge bay items safely (with JEI & EMI support).
- **Dedicated Machine Upgrades Tab:** Move Speed, Efficiency, Pressure Module and Parallel upgrade slots into a dedicated Mekanism-style **Upgrades** tab on the machine screen rail with live effect readouts, automatic migration of previously installed upgrades, and full player inventory access (`Shift+Click` and `Sneak+Right-Click` installation preserved).
- **Recipe Rebalancing:**
  - Fix the Matter Cell cost progression across all four tiers (`16M` uses Rhevon instead of Ender Conductors; `64M` uses 2 Clusters and drops Netherite; `256M` scales strictly above `64M`).
  - Make **Speed** and **Efficiency Upgrades** accessible in Tier 1 metallurgy (Structural Bars, Rhevon, Control Circuit and Rhedon/Virelite Ceramic — no Diamonds or Lumivex).
  - Remove Ender Conductors from the basic wired **Matter Terminal** and Diamond Lattice from the **Analyzer**.
  - Upgrade **Reinforced** and **Resonant Matter Cables** to Mekanism-style 8-cable rings (`8×` cables around `1× Ender Conductor` / `1× Diamond Lattice` → `8` cables, removing Netherite from bulk cabling).
  - Lower **Configuration Card** to Control Circuit + Structural Bars and **Atmospheric Collector** to `1× Mint Matrix Crystal`.
- **AE2 Byte Scaling Fix:** Set `MatterKey.TYPE.getAmountPerByte()` to `8,000` (matching AE2 fluids) so `1,000 u` consumes the same byte budget as 1 item or 1 bucket of fluid in AE2 storage/crafting.
- Format Matter Cell tooltip totals with thousands separators for readability.

### Visuals & Animations
- **PCB-Trace Matter Cables & Calm Signal Pulse:** Redesign all three Matter Cable tiers around dark graphite 4 × 4 shafts carrying dual routed PCB traces with bends and over/under bridge crossovers, seamless glowing 90° corner pads and matching PCB junction hubs (`{tier}_glow.png`). A calm 32-frame GPU-interpolated signal sweep (`single_pulse`) glides smoothly along the traces with a generous resting pause so dense cable halls stay legible and calm without flickering.
- **Zero-Particle 3D Matter Transfer Wave:** Replace floating transfer particle dots with a single batched additive 3D light sleeve (`RenderTypes.lightning`) hugging the cable shaft and hubs during active **Matter** transfers (continuous FE supply stays quiet on the PCB traces, eliminating server FE path-lookup and network pulse traffic; respects Minecraft's `ParticleStatus`).
- **Icon Redesigns:** Refresh illustrated icons for Structural Bars (forged square bar bundle), Netherite Sheath, Ender Conductor, unified cartridge-chassis Upgrades & Modules, microchip-style Control & Optical Circuits, upright Portable Energy Cell and the new Energy Core.

## 0.8.1

- Add the early-game **Spectrometer Lens** (`rematter:spectrometer_lens`, crafted with an Ice Matrix Crystal, Glass Pane, Structural Bars and Control Circuit) as the optical precursor to the endgame **Discovery Lens**, which now upgrades directly from a Spectrometer Lens in the Industrial Preparation Reactor.
- Equip either the **Spectrometer Lens** or **Discovery Lens** in **Curios** (`head`, `charm`, or `curio` slots, including right-click in air to equip) and toggle the live spectrometer HUD and Shift tooltips on or off via **Controls → ReMatter → Toggle Spectrometer Lens** (unbound by default).
- Reveal full chemical compositions, exact quantities, and harvestable ambient gases through both lenses even before researching a material, rendered with ReMatter's 16×16 **substance badges** in both Shift item tooltips and the in-world Field Spectrometer HUD (deduplicating overlapping atmospheric sources). Material synthesis remains strictly research-gated and clearly displays its unlock status.
- Update the optional **ReForging** integration pack to restore standard amorphous-silica (sand) electrolysis for elemental silicon now that Replication and Equivox are removed from ReForging, while preserving full standalone and optional third-party modpack compatibility (including Replication tags and the Equivox merge helper).

## 0.8.0

- Add two endgame multiblocks: **Industrial Decomposer** (Copper accent) and **Industrial Synthesizer** (Leaf accent). Both share the modular `3×3×5` to `7×7×7` chamber shell, Field Stabilizers, Reaction Foci and separate Machine Ports with the Chemical Reactor, run at **2× base speed** and accept up to **4× Speed**, **4× Efficiency** and **3× Parallel** upgrades (up to 4 parallel lanes / 80× throughput). The formed Industrial Synthesizer includes **Tier 2 crystallization pressure built in** without requiring a Pressure Module.
- Rebalance processing speed and energy: compact processors now scale up to **10× speed** (`1× / 2× / 4× / 7× / 10×`) across 0–4 Speed Upgrades, Crystal Generators output **128 FE/t** and **512 FE/t**, and alloy/component preparation costs and durations are tuned to match contemporary tech lines.
- Give the compact **Material Synthesizer** a dedicated **Pressure Module** slot beside its upgrade slot so diamond crystallization can still use Speed or Efficiency Upgrades.
- Add optional **Auto-eject** under **Settings → Access** (off by default, copied by the Configuration Card) to push finished item outputs into adjacent storage on enabled output faces.
- Add optional **Curios 15.0.0** support and a **Wireless Matter Terminal hotkey** (**Controls → ReMatter → Open Wireless Matter Terminal**, unbound by default). Both the Wireless Matter Terminal (`charm`/`curio`) and Portable Energy Cell (`belt`/`curio`) can be worn in Curios slots (with standalone player `charm`/`belt` slot registration), and the Portable Energy Cell recharges compatible FE gear across both inventory and Curios slots.
- Enhance **JEI / SEMI (EMI)** and **Jade** integrations: recipe viewers now display effective `FE/t` rates, durations in seconds alongside ticks and both new industrial workstations. Server-authorized Jade overlays show live `FE/t` draw on active machines and now cover **Energy Cubes**, **Wireless Access Points**, **Matter Level Emitters**, **Entangled Links** and **Cell Transfer Stations**.
- Expand the curated material catalogue to **1,043 item profiles** (455 explicit synthesis outputs), **14 fluids**, **14 gas outputs** and **83 substances**, including vanilla wood/cellulose, honeycomb, sugar cane/paper, cactus, Mekanism **brine** and **sodium** (fluid and chemical), and reviewed materials across AdvancedAE, Powah, Just Dire Things, Productive Bees and FTBIC.
- Add 19 optional mod-loaded cross-mod compatibility recipes for **Ender IO** (Alloy Smelter), **FTB Industrial Contraptions** (Macerator), **Energized Power** (Crusher) and **Mekanism** (Crusher).

## 0.7.2

- Fix a rendering crash with Iris shaders when viewing installed Matter Cells or moving machine parts.

## 0.7.1

- Clarify the setup, machine upgrade and Primal Crystal instructions in the guide and tooltips.

## 0.7.0

- Power your first machines with a hand-built Crystal Generator. Its two copper blocks, Ember Matrix Crystal, frame, bars and circuit are obtainable before the first powered batch.
- Scroll through all eight active Terminal orders and cancel an individual order. Automatic work is attributed to its own order, so cancelling preserves other orders and manual batches.
- Select a locked material to see a suitable research sample. Pin a lab goal to check the next step, shortages and surplus at the Matter Drive or Chemical Processor. Atmospheric plans take dimension and sky access into account and can choose an alternative source.
- Store up to 4M units in an ordinary Cell. Use a filtered Void Matter Cell for overflow and see the amounts stored and discarded when depositing a Pressurized Canister.
- Add distinct sounds for machines, tools and transfers. Timed motion cues follow the Decomposer jaws, Preparator stroke, Analyzer sweep and reactor motion. The four-step machine volume setting also applies to the new sounds.
- Unify device connection rules across cables, hoppers, capabilities and side settings. Fronts and open workbench tops stay closed; sealed tops start Disabled. Disabled faces show plain casing, with connection plates baked into the world model. Name the side controls and support backward cycling.
- Replace the Primal Converter's cycling target control with a searchable list showing symbols, prices and research requirements. Show its receiving Drive and Cell bay, and require room for a complete output batch before taking payment.
- Smooth work motion independently of recipe update packets. Add mechanism-specific bench motion, continuous industrial fields and module pulses, eased waiting states and charging/discharging Cube flow.
- Redraw the Matrix Cluster as four clear Matrix Crystals grown from one root, matching the illustrated component icons.
- Respect Energy Cube sides in vanilla hopper transfers, reject stale generator power handles after side changes, and close AE2 node queries and sided Refined Storage providers on unavailable faces.
- Save research payments on Windows through supported file handles, and keep energy rollback safe while a transaction is closing. Preserve paid progress, Cell receipts and hidden Discovery outcomes across recovery.
- Keep Energy Cubes and Cell Transfer Stations usable after chunk and world reloads. Separate Minecraft's chunk metadata from device data while retaining protection for genuinely unknown payloads.
- Add a Cell Transfer Station with three source and three target slots, searchable substance selection and a separate Primal option. Move only what fits while preserving exact quantities, phases, unknown substances and Cell identities.
- Add persistent ordinary Cell partitions, configurable by substance and phase or copied from current contents. Drives, processors, converter credits and transfers respect insertion filters; previously stored contents remain extractable.
- Give Energy Cubes four identical horizontal sockets and charge displays, with tier accents matching Basic, Reinforced and Resonant cables.

- Add Energy Cubes with 4M/32M/256M FE buffers, configurable input/output sides and 1/2/4 item-charging slots. Every output and charging slot shares the tier transfer budget. Carried cubes preserve their energy and inventory.
- Use compact icon controls with complete hover descriptions; display full text and substance formulas without shortened labels.

- Add server-authoritative material research through decomposition, retained-sample analysis, industrial Assay and paid Derive. Knowledge complements the explicit synthesis allowlist; imports grant no knowledge.
- Add optional endgame Discovery with tiered hidden candidates, saved payment steps, pause/resume and claimable Primal refunds. JEI and EMI display research status without hiding recipes.
- Add dimension-specific Primal Ores in new terrain, separate 16k/64k/256k/1M Primal Cells, Primal Crystal, Discovery Lens and a finite one-way Primal Converter with curated known-element targets.
- Add same-owner Entangled Links within 32 loaded blocks in one dimension, using Basic bandwidth without chunk loading.
- Add Crystal Generators at 64 and 256 FE/t with saved unspent energy. Both use the same nine fuels, yielding 768–2,816 FE per crystal depending on its colour. ReForging enables all nine.
- Give cables real matter and FE budgets. Separate industrial Field, Precision and working capacity from Speed, Efficiency and Parallel Upgrades; add balanced Field 4/8 procedures and explicit multi-output reclaim.
- Add a native crafting upgrade from an empty Hand Preparator, rejecting filled, modified or unreadable machine contents.
- Rebuild machine controls around Machine, Settings and Research tabs, embedded Terminal device access, scrollable stock, finite orders and Loop controls. Keep Preview separate from finished output.
- Match industrial module crystals and their mount tabs to their recipes: Amber for preparation, Violet for reaction. Give Cell items the mounted cartridge faceplate, a recipe-crystal window and a live status light, with a separate animated Primal rail on Primal Cells.
- Refresh the graphite machine family, twin-column Chemical Reactor, recessed connections, functional bench mechanisms and resource-pack GUI skins. Remove the five retired specialist devices and route their supported chemistry through reactors and the Synthesizer.
- Expand both guides and add advancement milestones for completed actions. The separate ReForging 0.11.1 integration includes an optional quest for actually synthesizing 1,000 mB of lava.

Start a fresh world when coming from an older development schema. Cells preserve unknown stored substances, and Primal Ores generate in new terrain.

## 0.6.4

- Close the Drive's front around its six insertion slots and add vent, fastener and badge details.
- Add live Cell status lights: blue for empty, green for stored matter, amber for full quantity or type capacity, and red for unreadable contents. Lights also follow Cells held in the hand or viewed in inventory.

## 0.6.3

- Fit Matter Cable sockets into permanent machine frames, with smaller recessed connections on the sides, back and underside.
- Resize the Matter Drive to a full-block rack so adjacent Drives sit flush. Enlarge all six bays and fit the mounted cells to them.

## 0.6.2

- Keep the separate result preview labelled **Preview**, with its explanatory hover tooltip.

## 0.6.1

- Consolidate chemical processing and air collection into the Chemical and Industrial Reactors. Make approved crystals in the Material Synthesizer.
- Repair owner-bound cable connections and reject invalid ingredients before slot prediction.
- Separate expected results from finished output, fix overlapping production labels and redraw the Parallel Upgrade and Pressure Module icons.

## 0.6.0

- Add Separator, Thermal Processor, Crystallizer and Air Separator with dedicated recipes, models and process routing.
- Require a Pressure Module for every permitted diamond form.
- Add hard stock targets, coordinated batch reservations and up to four parallel industrial batches. Disabling a target stops its automatic production.
- Add explicit venting of newly produced gas outputs, with confirmation and retained stored gas.
- Cap combined energy savings at 25% and use the same pricing for work and previews.
- Add owner-bound cables, optional FTB Teams operation and advancement-based recipe requirements with an offline progress mirror.
- Add construction error outlines, clearer stock counts and refreshed recipe names after language reloads.
- Standardize fictional substance names and document quantity exceptions.
- Add nine optional ReForging journal lessons and expand both guide formats; the basic book now fits its text on 49 pages.
- Align the reference integration with ReForging 0.9.0 and NeoForge 26.1.2.126.

This is a development test build. Additional unreviewed material families remain safely unsupported; their follow-up reviews are linked in the material coverage documentation.
