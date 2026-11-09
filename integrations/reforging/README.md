# ReForging coexistence integration

This optional integration applies specific rules while ReMatter, Replication, and Equivox coexist. It is separate from the standalone mod and is not bundled or activated by the ReMatter JAR. It targets Minecraft 26.1.2 data pack format 101.1, Replication 1.2.8, and Equivox 1.4.0.

## Quartz

Quartz decomposes into 1,000 units of `rematter:crystalline_silica`, exactly as in the standalone mod. Its dusts, nuggets and blocks synthesize from the same quantities. The gem is synthesizable in the Crystallizer. The standalone mod disables it while Ender IO is loaded; this integration explicitly restores it together with the bonus-free milling override below. Sand uses `rematter:silica`; the two substances share the SiO₂ composition but keep separate identities. The integration adds no second crystalline-silica substance and overrides no material profile.

Quartz dusts use the same 1,000 units. A quartz block contains 4,000 units; nine quartz nuggets form one 1,000-unit batch. Quartz ores can be decomposed but cannot be synthesized. Virelite keeps the quartz share of its preparation batch as crystalline silica and its sand and clay shares as amorphous silica and clay, so sand and clay add no electrolysis feedstock.

The integration adds no crystallization reaction. Sand, separated minerals, silicon, and oxygen therefore cannot become crystalline silica through the included chemistry recipes. This keeps the quartz recovery cycle available without turning sand into an inexpensive quartz source. A future crystallization recipe requires its own progression and balance review.

Silica electrolysis uses this crystalline quartz feedstock in ReForging instead of amorphous silica. Its silicon and oxygen outputs, 64,000 FE cost and 400-tick duration are unchanged. Ordinary sand and separated amorphous silica cannot feed this process. This prevents a sand → silicon → EMC → sand loop when silicon material recovery is available; silicon obtained through the pack's normal routes can still be recovered.

The data pack also makes Ender IO's **quartz-gem grinding** recipe produce exactly one quartz dust, with grinding-ball output bonuses disabled. Its energy cost remains 2,400 FE. This closes a pre-existing gem → extra dust → gem loop that also works through other mods. Quartz-ore processing and its bonuses are unchanged. KubeJS can still unify the output to the pack's canonical quartz dust.

## Material and energy boundaries

Recovery preserves the approved material identity and quantity. Nine nuggets, one ingot or full dust, and one ninth of a normal metal block represent the same amount. Four-gem quartz and amethyst blocks use their actual four-item recipes. Refined powders, charged crystals, fuels and distinct mineral grades must not become interchangeable merely because their names or tags are similar.

One obsidian block mills into four dusts. The integration disables Occultism's one-dust obsidian crystallization and its gray paste crafting recipe because each turns a single dust into a whole block. Ender IO's obsidian grinding keeps its four dusts without grinding-ball bonuses, which would otherwise yield up to eight. Four dusts recover one block. The obsidian block is explicitly synthesizable in this integration; its four-dust quantity is preserved. Standalone disables the block while Ender IO is loaded because it cannot correct native grinding on its own. Ender IO mills one pearl into nine fragments. Occultism crushes one pearl into one full ReMatter powder and crystallizes a pearl from one full ReMatter or AE2 powder. The JAOPCA storage block packs and unpacks exactly nine Ender IO fragments.

The standalone catalogue recovers ambiguous alloys only as the constituents common to all interchangeable recipes, for example copper from brass, and gives decomposition and synthesis separate permissions. Netherite ingots, blocks and dusts recover the smallest share of gold and Netherium any native route uses and cannot be synthesized. The integration disables Easy Netherite's recipe, which crafts a Netherite Ingot from an iron ingot and an ender pearl and would turn both into gold and Netherium. Raw ores, enriched intermediates, fuel families and manufactured components do not gain synthesis from this integration. In particular, coal dust must not provide a cheaper route to coal, and refined-obsidian powder must not bypass the osmium used to make its ingot.

The energy regression tests use the production pricing function across legal compact/industrial routing, structure savings and upgrades. All savings combined are capped at 25%. The tested floors exceed the reference hydrogen, steam and lava generator yields. Parallel lanes pay independently. Generator changes still require a new review; the tests do not claim coverage of every farm, fuel or recipe combination. Intentional ore enrichment, crystal growth, seeds and bees remain available.

## Machines and Cells

The optional `quests/learn_rematter/` chapter adds nine lessons in the pack's native Embers Questing format. Copy it to `config/embersquesting/books/main/chapters/learn_rematter/` alongside this integration. It uses existing Power Systems grouping and does not replace the book or other chapters. Tasks detect the listed items; the descriptions guide practical process checks, byproduct handling and comparing native production alternatives.

These machines, Matter Cells, recovery items and automation patterns are excluded from Replication scanning, disintegration, and automatic value calculation, and receive an EMC value of zero through the separate Equivox configuration merge:

- `rematter:analyzer`
- `rematter:decomposer`
- `rematter:material_synthesizer`
- `rematter:separator`
- `rematter:thermal_processor`
- `rematter:crystallizer`
- `rematter:air_separator`
- `rematter:electrolyzer`
- `rematter:chemical_reactor`
- `rematter:matter_terminal`
- `rematter:industrial_reactor`
- `rematter:matter_drive`
- `rematter:material_preparator`
- `rematter:hand_preparator`
- `rematter:industrial_preparator`
- `rematter:sinter_furnace`
- `rematter:network_interface`
- `rematter:matter_cell_64k`
- `rematter:matter_cell_256k`
- `rematter:matter_cell_1m`
- `rematter:recovered_matter`
- `rematter:automation_pattern`

The exclusions cover empty and filled variants of those exact items. The `reforging:rematter/stateful_machines` tag lists them. Foreign machines, pipes, casings, upgrades, and the guide are unchanged. Existing tag entries and unrelated custom EMC values are preserved.

Replication's `skip_calculation` tag does not override an explicit matter-value recipe, so third-party matter-value overrides must be checked separately. Do not add these machines or Cells to Equivox's data-component whitelist: copying a machine's identity must not copy stored substances, energy, fluids, inventory, or ownership.

## Installation

1. Copy the contents of `datapack/` into a new data pack directory. With Paxi, use `config/paxi/datapacks/reforging-rematter-compat/`; otherwise use the world's `datapacks/reforging-rematter-compat/` directory. `pack.mcmeta` must be at that directory's root.
2. Prepare the Equivox configuration in a separate file, using Python 3:

   ```sh
   python3 integrations/reforging/merge_equivox.py \
     config/Equivox/custom_emc.json \
     custom_emc.rematter.json
   ```

3. Review the generated file, then use it as `config/Equivox/custom_emc.json`. The helper never edits its input or overwrites an existing destination. Install both parts; the data pack alone does not change EMC values.
4. Restart the game or server and verify the final chemistry, tag, EMC, and Replication state. Check quartz recovery, the absence of a sand-to-quartz route, and the protected machines and three Cell types with stored contents. Automated data checks do not replace this gameplay and balance check.

## Validation

`./gradlew test` includes the data and core chemistry checks for this integration. The configuration helper has separate standard-library tests:

```sh
python3 -B -m unittest discover -s integrations/reforging/tests -p 'test_*.py'
```
