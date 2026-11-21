# Changelog

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
