# ReForging coexistence integration

This optional integration applies specific rules while ReMatter, Replication, and Equivox coexist. It is separate from the standalone mod and is not bundled or activated by the ReMatter JAR. It targets Minecraft 26.1.2 data pack format 101.1, Replication 1.2.8, and Equivox 1.4.0.

## Quartz

Quartz decomposes into 1,000 units of `reforging:crystalline_silica` and synthesizes from the same quantity. Its SiO₂ composition is unchanged. Sand continues to use `rematter:silica`; the two substances have separate identities.

The integration adds no crystallization reaction. Sand, separated minerals, silicon, and oxygen therefore cannot become crystalline silica through the included chemistry recipes. This keeps the quartz recovery cycle available without turning sand into an inexpensive quartz source. A future crystallization recipe requires its own progression and balance review.

Existing stored `rematter:silica` remains that substance; it is not converted into crystalline silica. Existing quartz items can enter the new recovery cycle. An operation in progress may restart when the catalogue changes.

## Machines

These eight machines are excluded from Replication scanning, disintegration, and automatic value calculation, and receive an EMC value of zero through the separate Equivox configuration merge:

- `rematter:analyzer`
- `rematter:decomposer`
- `rematter:material_synthesizer`
- `rematter:electrolyzer`
- `rematter:chemical_reactor`
- `rematter:matter_storage`
- `rematter:matter_terminal`
- `rematter:industrial_reactor`

The rules cover empty and filled variants of those exact items. Material profiles, foreign machines, pipes, casings, upgrades, and the guide are unchanged. Existing tag entries and unrelated custom EMC values are preserved.

Replication's `skip_calculation` tag does not override an explicit matter-value recipe. Existing patterns, previously learned knowledge, and later third-party overrides must be checked after activation. Do not add these machines to Equivox's data-component whitelist: copying a machine's identity must not copy stored substances, energy, fluids, inventory, or ownership.

## Installation

1. Back up the target world and its configuration. Review the complete combination of mods and data packs before activating it in an existing world.
2. Copy the contents of `datapack/` into a new data pack directory. With Paxi, use `config/paxi/datapacks/reforging-rematter-compat/`; otherwise use the world's `datapacks/reforging-rematter-compat/` directory. `pack.mcmeta` must be at that directory's root.
3. Prepare the Equivox configuration in a separate file, using Python 3:

   ```sh
   python3 integrations/reforging/merge_equivox.py \
     config/Equivox/custom_emc.json \
     custom_emc.rematter.json
   ```

4. Review the generated file, then replace `config/Equivox/custom_emc.json` after backing up the original. The helper never edits the input or overwrites an existing destination. Install both parts; the data pack alone does not change EMC values.
5. Restart the test environment and verify the final chemistry, tag, EMC, and Replication state. Check quartz recovery, the absence of a sand-to-quartz route, and all eight machines with stored contents. Automated data checks do not replace this gameplay and balance check.

Removing this integration while crystalline silica is stored is not a migration strategy. Keep the data definitions available until those stores have been handled deliberately. No conversion of Replication patterns, Equivox knowledge, or EMC balances is provided.

## Validation

`./gradlew test` includes the data and core chemistry checks for this integration. The configuration helper has separate standard-library tests:

```sh
python3 -B -m unittest discover -s integrations/reforging/tests -p 'test_*.py'
```
