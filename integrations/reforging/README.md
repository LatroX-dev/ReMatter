# ReForging coexistence integration

This optional integration applies specific rules while ReMatter, Replication, and Equivox coexist. It is separate from the standalone mod and is not bundled or activated by the ReMatter JAR. It targets Minecraft 26.1.2 data pack format 101.1, Replication 1.2.8, and Equivox 1.4.0.

## Quartz

Quartz decomposes into 1,000 units of `reforging:crystalline_silica` and synthesizes from the same quantity. Its SiO₂ composition is unchanged. Sand continues to use `rematter:silica`; the two substances have separate identities. The integration keeps its existing crystalline-silica ID so previously stored quartz feedstock remains usable.

Quartz dusts use the same 1,000 units. A quartz block contains 4,000 units; nine quartz nuggets form one 1,000-unit batch. Quartz ores can be decomposed but cannot be synthesized. These rules use exact item profiles, not a synthesis permission inferred from a tag. The profiles override the standalone files at their original resource IDs; installing a second profile for the same item under another namespace is invalid.

The integration adds no crystallization reaction. Sand, separated minerals, silicon, and oxygen therefore cannot become crystalline silica through the included chemistry recipes. This keeps the quartz recovery cycle available without turning sand into an inexpensive quartz source. A future crystallization recipe requires its own progression and balance review.

Silica electrolysis uses this crystalline quartz feedstock in ReForging. Its silicon and oxygen outputs, 64,000 FE cost and 400-tick duration are unchanged. Ordinary sand and separated amorphous silica cannot feed this process. This prevents a sand → silicon → EMC → sand loop when silicon material recovery is available; silicon obtained through the pack's normal routes can still be recovered.

Existing stored `rematter:silica` remains that substance; it is not converted into crystalline silica. Existing quartz items can enter the new recovery cycle. An operation in progress may restart when the catalogue changes.

The data pack also makes Ender IO's **quartz-gem grinding** recipe produce exactly one quartz dust, with grinding-ball output bonuses disabled. Its energy cost remains 2,400 FE. This closes a pre-existing gem → extra dust → gem loop that also works through other mods. Quartz-ore processing and its bonuses are unchanged. KubeJS can still unify the output to the pack's canonical quartz dust.

## Material and energy boundaries

Recovery preserves the approved material identity and quantity. Nine nuggets, one ingot or full dust, and one ninth of a normal metal block represent the same amount. Four-gem quartz and amethyst blocks use their actual four-item recipes. Refined powders, charged crystals, fuels and distinct mineral grades must not become interchangeable merely because their names or tags are similar.

Obsidian is a specific exception to the full-dust rule: one block mills into four dusts, each worth 250 units. The integration disables only Occultism's one-dust-to-one-obsidian crystallization recipe, which would otherwise multiply the material fourfold. Its recipe type cannot consume a four-item input batch. Four dusts can instead be recovered and synthesized through ReMatter. Other Occultism crystallization recipes are unchanged. Ender IO's small ender-pearl powder is another explicit exception: nine powders recover one pearl; AE2's full ender dust represents one pearl per item.

The standalone catalogue keeps ambiguous alloys as their own mixtures and gives decomposition and synthesis separate permissions. Raw ores, enriched intermediates, fuel families and manufactured components do not gain synthesis from this integration. In particular, coal dust must not provide a cheaper route to coal, and refined-obsidian powder must not bypass the osmium used to make its ingot.

The data tests retain conservative energy floors for the included water-to-hydrogen, water-to-steam and stone-to-lava routes, including the maximum 20% ReMatter efficiency saving. These floors depend on the target pack's generator configuration; changing fuel values, turbine efficiencies, chemical conversion ratios or adding new feedstock routes requires another balance review. They do not imply that every generator, resource farm or recipe combination has been exercised in gameplay. Intentional ore enrichment, crystal growth, seeds and bees are not disabled by these rules.

## Machines and Cells

These twelve machines and three Matter Cells are excluded from Replication scanning, disintegration, and automatic value calculation, and receive an EMC value of zero through the separate Equivox configuration merge:

- `rematter:analyzer`
- `rematter:decomposer`
- `rematter:material_synthesizer`
- `rematter:electrolyzer`
- `rematter:chemical_reactor`
- `rematter:matter_storage`
- `rematter:matter_terminal`
- `rematter:industrial_reactor`
- `rematter:matter_drive`
- `rematter:material_preparator`
- `rematter:hand_preparator`
- `rematter:industrial_preparator`
- `rematter:matter_cell_64k`
- `rematter:matter_cell_256k`
- `rematter:matter_cell_1m`

The exclusions cover empty and filled variants of those exact items. The existing `reforging:rematter/stateful_machines` tag ID is retained and now also includes the portable Cells. Foreign machines, pipes, casings, upgrades, and the guide are unchanged. Existing tag entries and unrelated custom EMC values are preserved.

Replication's `skip_calculation` tag does not override an explicit matter-value recipe. Existing patterns, previously learned knowledge, and later third-party overrides must be checked after activation. Do not add these machines or Cells to Equivox's data-component whitelist: copying a machine's identity must not copy stored substances, energy, fluids, inventory, or ownership.

## Installation

1. Back up the target world and its configuration. Review the complete combination of mods and data packs before activating it in an existing world.
2. Copy the contents of `datapack/` into a new data pack directory. With Paxi, use `config/paxi/datapacks/reforging-rematter-compat/`; otherwise use the world's `datapacks/reforging-rematter-compat/` directory. `pack.mcmeta` must be at that directory's root. When upgrading, replace the old integration directory rather than merging files: the former `data/reforging/rematter/material_profile/quartz.json` must be removed to avoid a duplicate item target.
3. Prepare the Equivox configuration in a separate file, using Python 3:

   ```sh
   python3 integrations/reforging/merge_equivox.py \
     config/Equivox/custom_emc.json \
     custom_emc.rematter.json
   ```

4. Review the generated file, then replace `config/Equivox/custom_emc.json` after backing up the original. The helper never edits the input or overwrites an existing destination. Install both parts; the data pack alone does not change EMC values.
5. Restart the test environment and verify the final chemistry, tag, EMC, and Replication state. Check quartz recovery, the absence of a sand-to-quartz route, and all twelve machines and three Cell types with stored contents. Automated data checks do not replace this gameplay and balance check.

Removing this integration while crystalline silica is stored is not a migration strategy. Keep the data definitions available until those stores have been handled deliberately. No conversion of Replication patterns, Equivox knowledge, or EMC balances is provided.

## Validation

`./gradlew test` includes the data and core chemistry checks for this integration. The configuration helper has separate standard-library tests:

```sh
python3 -B -m unittest discover -s integrations/reforging/tests -p 'test_*.py'
```
