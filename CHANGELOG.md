# Changelog

## 0.7.0

- Add a Cell Transfer Station with three source and three target slots, searchable substance selection and a separate Primal option. Move only what fits while preserving exact quantities, phases, unknown substances and Cell identities.
- Add persistent ordinary Cell partitions, configurable by substance and phase or copied from current contents. Drives, processors, converter credits and transfers respect insertion filters; previously stored contents remain extractable.
- Give Energy Cubes four identical horizontal sockets and charge displays, with tier accents matching Basic, Reinforced and Resonant cables.

- Add Energy Cubes with 4M/32M/256M FE buffers, configurable input/output sides and 1/2/4 item-charging slots. Every output and charging slot shares the tier transfer budget. Carried cubes preserve their energy and inventory.
- Use compact icon controls with complete hover descriptions; display full text and substance formulas without shortened labels.

- Add server-authoritative material research through decomposition, retained-sample analysis, industrial Assay and paid Derive. Knowledge complements the explicit synthesis allowlist; imports grant no knowledge.
- Add optional endgame Discovery with tiered hidden candidates, saved payment steps, pause/resume and claimable Primal refunds. JEI and EMI display research status without hiding recipes.
- Add dimension-specific Primal Ores in new terrain, separate 16k/64k/256k Primal Cells, Primal Crystal, Discovery Lens and a finite one-way Primal Converter with curated known-element targets.
- Add same-owner Entangled Links within 32 loaded blocks in one dimension, using Basic bandwidth without chunk loading.
- Add Crystal Generators at 64 and 256 FE/t with identical 1,024-FE standalone fuel yields and saved unspent energy. ReForging disables the nine fuels pending its own audit.
- Give cables real matter and FE budgets. Separate industrial Field, Precision and working capacity from Speed, Efficiency and Parallel Upgrades; add balanced Field 4/8 procedures and explicit multi-output reclaim.
- Add a native crafting upgrade from an empty Hand Preparator, rejecting filled, modified or unreadable machine contents.
- Rebuild machine controls around Machine, Settings and Research tabs, embedded Terminal device access, scrollable stock, finite orders and Loop controls. Keep Preview separate from finished output.
- Match industrial module crystals and their mount tabs to their recipes: Amber for preparation, Violet for reaction. Give Cell items the mounted cartridge faceplate, a recipe-crystal window and a live status light, with a separate animated Primal rail on Primal Cells.
- Refresh the graphite machine family, twin-column Chemical Reactor, recessed connections, functional bench mechanisms and resource-pack GUI skins. Remove the five retired specialist devices and route their supported chemistry through reactors and the Synthesizer.
- Expand both guides and add ten optional advancements awarded by completed actions. Update the reference integration to ReForging 0.10.0; ReMatter keeps its own version numbering.

This development version has no mod-platform release. Older pre-release machine and Cell schemas are not migrated; unknown stored substances remain preserved. Primal Ores do not refill existing terrain.

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
