# Changelog

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
