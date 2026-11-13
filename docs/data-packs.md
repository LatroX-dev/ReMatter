# Chemistry data packs

Pack authors can add ReMatter chemistry for existing items and fluids with a standard Minecraft data pack. ReMatter reads it on server startup and `/reload`. Material profiles, substance definitions, chemical processes, synthesis permissions and material research families are separate resources. This documents ReMatter 0.7.0 for Minecraft 26.1.2; ReMatter versions are independent of pack versions.

## Start from the example

1. Copy the [example pack](../examples/chemistry-datapack/) into your world's `datapacks` directory. Keep `pack.mcmeta` and `data/` at the root of the copied folder. The example targets Minecraft Java 26.1.2 and uses data pack format `101.1`.
2. Start the world or run `/reload`. Use `/datapack list` to check that the example is enabled. If it is available but disabled, run `/datapack enable "file/chemistry-datapack"`. For a pack-wide installation, use your pack's data pack loader, such as [the Paxi layout in the ReForging integration](../integrations/reforging/README.md#installation).
3. Put four snowballs or one snow block into the Decomposer. Both profiles provide 1,000 units of solid water. The separate synthesis entry permits snow-block output. Analyse a snowball without consuming it, or decompose a complete batch, to learn the example Snow family; the Synthesizer can then make a snow block from that amount. This matches the vanilla four-to-one crafting ratio.
4. Copy the profile files for your own materials and replace `rematter_example` with your pack's namespace. Use exact item IDs and match any crafting, grinding or compression ratios. Add synthesis entries only for products you want to make in ReMatter, and exact research membership for those forms when research is enabled.

Use your own namespace for new profiles. To replace an existing profile or permission, use its original resource path and give your pack higher priority. For an optional mod, put the same dependency fields on the profile and its synthesis entry so they are skipped together.

Place JSON files in the following directories of a Minecraft data pack:

| Directory | Purpose |
| --- | --- |
| `data/<namespace>/rematter/substance/` | Elements, allotropes, compounds, mixtures, and supported phases |
| `data/<namespace>/rematter/material_profile/` | The substances contained in an explicit whole item batch |
| `data/<namespace>/rematter/fluid_profile/` | An explicit canonical fluid batch and its stored substances |
| `data/<namespace>/rematter/process/` | Balanced transformations between stored substances |
| `data/<namespace>/rematter/environment_source/` | Explicit renewable, gas-only environmental sources |
| `data/<namespace>/rematter/synthesis/` | Explicit permission to synthesize a particular output |
| `data/<namespace>/rematter/material_research/` | Exact, curated research-family membership and Discovery eligibility |
| `data/<namespace>/rematter/crystal_fuel/` | Prices or disables the nine permitted loose Matrix Crystal fuels |

Ordinary chemistry documents require `"schema_version": 1`; any other version rejects their reload. Research families instead use `"schema": 1`. Crystal-fuel entries use `"schema_version": 1` in a separate fuel reload catalogue. Standard crafting and `rematter:preparation` recipes belong under `data/<namespace>/recipe/` and use their recipe codecs rather than either chemistry schema. Substance and process IDs come from the namespace and filename, including any nested path. Files with the same path use data-pack priority among the entries whose dependencies are available. Two profile or synthesis files at different paths that target the same item are rejected, even when one of them is disabled. Every shipped item profile has a synthesis entry, disabled for recovery-only items, at `data/rematter/rematter/synthesis/vanilla/<item path>.json` for Minecraft items or `data/rematter/rematter/synthesis/compat/<namespace>/<item path>.json` for other mods. To change such a permission, override that exact path with higher pack priority instead of adding a file in your own namespace.

The whole catalogue is validated before a reload takes effect. Unknown substances, invalid phases, fractional or overflowing quantities, batches above the energy limit, unbalanced reactions, and forbidden synthesis categories reject the reload.

Stored quantities keep their substance ID and amount when a definition disappears. Machines, Cells and tooltips show them as **Unknown substance (ID, phase)**; they cannot be processed or offered to AE2 or Refined Storage until a definition with that ID returns.

## Material profiles

For example, `data/example/rematter/material_profile/copper_dust.json` could add a copper dust item from an installed mod:

```json
{
  "schema_version": 1,
  "required_mod": "example",
  "item": "example:copper_dust",
  "matter": [
    {"substance": "rematter:copper", "phase": "solid", "amount": 1000}
  ],
  "decomposition_energy": 8000,
  "synthesis_energy": 16000,
  "ticks": 100
}
```

`example:copper_dust` is a placeholder for a registered item ID. `required_mod` skips the file if that mod is absent. For foreign targets in that mod's namespace, ReMatter also checks whether the item or fluid is registered. Use `required_item` or `required_fluid` to check a specific dependency in any namespace. Unguarded unknown targets are data errors. Energy is measured in FE. All quantities, energy costs, and durations must be positive integers. One batch may cost at most 1,200,000 FE, so that it still fits a machine's 2,400,000 FE buffer after four Speed Upgrades double its price. Modified or data-bearing stacks are not implicitly safe to recycle.

The optional `item_count` defaults to 1. It specifies the number of items consumed or produced by one complete operation; all listed matter and energy values belong to that batch. The count must be between 1 and 64 and fit the item's actual stack limit. For example, a profile for nine copper nuggets uses `"item_count": 9` and 1000 copper units. Eight nuggets cannot start that operation, and synthesis requires room for all nine outputs. Never round a ninth of an ingot to 111 or 112 units. Changing a profile never rescales quantities that are already stored.

The Analyzer retains a single sample, displays composition for the profile's stated batch, and learns that item's explicitly defined research family after successful analysis. Analysis does not consume or manufacture the rest of the batch. The Industrial Preparator's Assay mode likewise returns one unchanged sample after successful research; it is separate from multi-output reclaim recipes.

A profile alone permits decomposition. Synthesis requires an explicit permission and, with the default research setting, the owner's knowledge of the exact material family. Add the permission in a separate file in `rematter/synthesis/`:

```json
{
  "schema_version": 1,
  "required_mod": "example",
  "item": "example:copper_dust",
  "category": "raw_material"
}
```

Use `raw_material` or `natural_material` for approved item outputs. Machines, equipment, plates, wires, gears, circuits, and other manufactured components are outside ReMatter's synthesis rules. Classifying an item as a raw material is a deliberate pack-author decision; do not derive it automatically from recipes.

The catalogue also distinguishes `fluid` and `gas` export permissions. These require an implemented adapter; an allowlist entry by itself does not make a substance compatible with an arbitrary tank or pipe. A gas permission names the stored substance in its `item` field. This entry lets osmium leave a Drive as a Mekanism chemical:

```json
{
  "schema_version": 1,
  "required_mod": "mekanism",
  "item": "rematter:osmium",
  "category": "gas"
}
```

## Fluid profiles

Fluid profiles use an exact registered source fluid ID, a whole batch measured in millibuckets, and an explicit matter vector. Flowing variants and fluid stacks with modified components are not converted. For example, the default water profile is equivalent to:

```json
{
  "schema_version": 1,
  "fluid": "minecraft:water",
  "amount": 1000,
  "matter": [
    {"substance": "rematter:water", "phase": "liquid", "amount": 1000}
  ],
  "decomposition_energy": 2000,
  "synthesis_energy": 2000,
  "ticks": 80
}
```

`amount` must fit the 16000 mB machine tank. Matter, energy, and duration apply to the complete batch; no per-millibucket rounding occurs. A foreign fluid may use a different explicit ratio after its actual recipes have been checked. The whole input and output must fit before anything is consumed. A fluid profile permits import; export additionally requires a separate synthesis entry with `"item": "minecraft:water"` and `"category": "fluid"`. Fluid permission never grants item synthesis for an item sharing that ID. A gas requires its own chemical adapter and is not accepted merely because a fluid profile exists.

## Processes

This is the built-in water electrolysis recipe, expressed as data. Saving it as `data/rematter/rematter/process/water_electrolysis.json` overrides its values:

```json
{
  "schema_version": 1,
  "type": "electrolysis",
  "inputs": [
    {"substance": "rematter:water", "phase": "liquid", "amount": 1000}
  ],
  "outputs": [
    {"substance": "rematter:hydrogen", "phase": "gas", "amount": 1000},
    {"substance": "rematter:oxygen", "phase": "gas", "amount": 500}
  ],
  "energy": 32000,
  "ticks": 200
}
```

Hydrogen and oxygen quantities represent H₂ and O₂ respectively. The reaction conserves elements; the number of stored molecular units increases, so the storage needs additional free capacity. Storage capacity counts units, not mass. Water and lava adapters use one internal unit per millibucket; this is not a universal conversion for foreign gas systems.

Process types are `electrolysis`, `reaction`, `separation`, `phase_change`, and `crystallization`. The Chemical Reactor and Industrial Reactor run all five ordinary process types. Both inputs and outputs must be nonempty, and their elemental compositions must match exactly. An optional `industrial` object restricts a process to an Industrial Reactor with the declared Field and input room:

```json
{
  "industrial": {
    "min_field": 8,
    "requires_known_outputs": true,
    "product": "rematter:diamond"
  }
}
```

This is a fragment inside a complete process document. `min_field` is 1–98; `product` must name an actual output substance. With `requires_known_outputs`, the owner must already know the named product substance. These requirements grant no new knowledge or synthesis permission. Every simultaneous input consumes room, at 16,000 u per Reaction Focus. Extra Field hardware does not multiply speed or discount FE. The shipped Field 4/8 procedures remain exactly balanced; growth never creates elemental matter from a retained visual crystal.

Machines, recipe viewers and the Terminal name a process by the translation key `process.<namespace>.<path>`, with slashes in the path replaced by dots. Without a translation they show a readable form of the process ID.

## Substances

Each definition specifies a stable ID through its file path, a translation key, a display symbol, a kind (`element`, `allotrope`, `compound`, or `mixture`), an elemental composition, and its supported phases (`solid`, `liquid`, `gas`). For example, the water definition is:

```json
{
  "schema_version": 1,
  "translation_key": "substance.rematter.water",
  "symbol": "H₂O",
  "kind": "compound",
  "composition": {"rematter:hydrogen": 2, "rematter:oxygen": 1},
  "phases": ["solid", "liquid", "gas"]
}
```

Composition coefficients count atoms of each element per stored unit. Element definitions may contain only their own element ID; molecular gases can use a coefficient greater than one. Translation keys and names never replace saved substance IDs.

Supply custom display names in a client resource pack under `assets/<namespace>/lang/`, using the exact `translation_key` from the definition. The server data pack provides the chemistry and symbol.

An `allotrope` has its own stable substance ID and exactly one underlying element. Diamond and graphite can both contain carbon while remaining different stored resources. Equal elemental composition does not grant a conversion: every such process must be explicitly defined and balanced.

## Disabling entries

Override a substance or process file with `{"schema_version": 1, "disabled": true}`. Material profiles and synthesis entries also need their `item` field to identify the target. When disabling a shipped material or fluid profile, disable its separate synthesis entry at its original path as well. An active synthesis entry still requires an active profile. When removing a substance, update or disable every profile and process that uses it.

Fluid profile disabling uses its `fluid` field. Catalogues are bounded to 32768 item profiles, 4096 fluid profiles, 8192 substances, and 8192 processes. The complete encoded catalogue must also fit the validated network size limits. Oversized data is rejected before it replaces the active registry.

Disabling a definition does not delete stored quantities; they stay marked as unknown until the definition returns. Keep substance IDs stable when updating a pack.

## Quantities and specialist requirements

1,000 units is a standard convention for defined basic forms, not a universal mass scale. The complete profile of a batch is authoritative. Existing ratios and documented exceptions remain: silicon uses 250 units per item; water and lava use one unit per mB. Derive block and nugget amounts from actual crafting and return routes. Cells hold 64,000, 256,000 or 1,000,000 units and at most 512 substance-ID/phase pairs.

Synthesis entries may declare `"synthesis_machine": "material_synthesizer"` and `"crystallization_tier": 1` or `2`. Tier 1 is the default; tier 2 requires a Pressure Module in the Synthesizer. Every approved form of a crystal family must carry the same tier; alternate dusts or blocks cannot bypass it. These fields restrict an existing explicit permission; they never grant permission by tags or recipe inference. Only `material_synthesizer` is a valid synthesis machine.

A process or synthesis entry may declare `"requires_advancement": "example:milestone"`. The server checks the machine owner's progress. A missing definition denies work. Acquired progress is mirrored for offline use; revocation updates that mirror. Datapack changes are authoritative on reload and automation jobs are cancelled/refunded when their required permission disappears.

Use `"unavailable_with_mods": ["enderio"]` to disable an otherwise permitted document while any named mod is loaded. Required/excluded mod IDs must be valid and the list is bounded. A higher-priority override at the original path may deliberately remove that condition. The shipped quartz-gem and obsidian permissions use it; ReForging restores both with matching native milling fixes.

All ordinary chemical process types run in the Chemical Reactor and Industrial Reactor; `industrial` requirements restrict advanced procedures to a formed industrial chamber. Renewable environmental sources are explicit recipes in the same two devices. Physical crystal products use the Material Synthesizer with its explicit permission and pressure requirement. No included process turns ordinary silica into crystalline silica.

## Renewable environmental sources

A source has no material inputs and therefore is not a balanced chemical process. Put it in its own directory and explicitly mark it renewable. Example:

```json
{
  "schema_version": 1,
  "renewable": true,
  "outputs": [{"substance": "rematter:oxygen", "phase": "gas", "amount": 200}],
  "energy": 16000,
  "ticks": 200,
  "dimensions": ["minecraft:overworld"],
  "requires_open_air": true
}
```

Only known gas phases are accepted, with positive bounded quantities, a nonempty dimension allowlist, positive ticks and an affordable complete FE price. Both reactor tiers enforce the dimension, air and sky rules on the server. The compact inlet is directly above the device; the industrial inlet is above the front edge of its roof. Review complete energy cycles whenever adding renewable gases. Gas disposal is a separate owner-selected machine setting and never deletes existing stored quantities.

## Material research families

Research is enabled by default and adds a knowledge requirement to the ordinary synthesis allowlist. A family document names exact item IDs, not tags, recipes or every material sharing an element. For the copper-dust example, `data/example/rematter/material_research/copper_dust.json` could contain:

```json
{
  "schema": 1,
  "name": "Copper Dust",
  "items": ["example:copper_dust"],
  "substances": [{"substance": "rematter:copper", "phase": "solid"}],
  "derive": true,
  "starting_knowledge": false,
  "discovery_tier": 0,
  "requires_dimension": ""
}
```

Only exact members with active material profiles participate. Missing optional members are filtered; an empty family is skipped. Every participating item's profile must have exactly the declared substance-ID/phase set, although whole-batch amounts may differ. An item may belong to only one active family. To add a form to an existing family, override that family's original path and retain its other intended members; do not create overlapping membership in a second family.

Successful decomposition, retained-sample analysis and industrial Assay learn the family and its retained substances. Imports, Cell transfers and administrative grants do not count as earned discovery progress. A decomposition-only family remains decomposition-only after research. Fluids require their ordinary export permission plus knowledge of all exact substances in their profile. Ordinary reactions can teach output substances only when every input substance was already known.

`derive` offers sample-free research only where at least one family form has synthesis permission. The server calculates its Primal price from the smallest allowed whole-batch matter sum: four times that sum, clamped to 4,000–16,000 u. Base FE is 256,000 and duration is 1,800 ticks, subject to normal upgrades. `starting_knowledge` deliberately grants initial knowledge, never synthesis permission.

`discovery_tier` is 0 to exclude the family, or 1–3 for an explicitly reviewed random Discovery pool. Keep machines, equipment, manufactured components, boss rewards and other progression-only materials outside those pools. `requires_dimension` is empty or an exact dimension ID the owner must have visited. The default server config requires a Lens, both industrial milestones and 32 earned families. `research.discoveryMinimumEarned` changes the earned threshold; `research.sharing` selects `PERSONAL_AND_TEAM` or `SERVER`. Disabling `research.enabled` removes knowledge checks while retaining the synthesis allowlist.

Discovery charges saved steps of 4,000 u Primal and 600,000 base FE over at least 1,500 ticks each. Tiers contain 4/8/16 steps. Candidate order and paid progress persist; pausing or reloading does not reroll. Changed incompatible research data pauses an existing project rather than silently repricing it. Exhausted paid projects retain a Primal refund claim; FE and elapsed time are not refunded. JEI and EMI retain recipes and display the current research status; the server authorizes every operation.

## Industrial preparation and reclaim

Preparation recipes use `"type": "rematter:preparation"`. `ticks` is the final work duration: it is not multiplied again at load time. The shipped production balance is four times the earlier durations; one hand crank supplies 80 work units, preserving the original manual click counts. Plain and coloured Matrix Crystals use this recipe type exclusively, with hand-accessible Amber and Ember keeping the powered workshop reachable. Besides the counted `primary`, optional `secondary` and `tertiary` ingredients, they declare `energy`, `ticks`, `minimum_tier` (0 hand, 1 powered, 2 industrial), `min_precision` (0–98), and distinct `steps` from `grind`, `mix`, `separate`. Separation, positive Precision and multiple outputs require tier 2. Hardware supplies capacity for every declared step; it supplies no global speed or FE discount. Industrial preparation runs at a fixed 4× base work rate with twice the declared recipe energy, before upgrades.

Use exactly one `result` or a `results` array of one to three plain outputs. Multi-output reclaim uses only the primary ingredient; the complete input and all outputs must fit before commitment. `"network_recipe": false` excludes a manual reclaim or research-component recipe from AE2/RS crafting patterns. The shipped reclaim recipes are explicit, limited return routes rather than universal machine disassembly. Crafting upgrades from a portable Hand Preparator use the native `rematter:empty_machine` custom ingredient, which rejects filled, component-modified, foreign or unreadable machine data.

## Crystal fuels and Primal boundaries

The fuel catalogue accepts only plain Matrix Crystal plus its eight coloured variants. For example, override `data/rematter/rematter/crystal_fuel/matrix_crystal.json`:

```json
{
  "schema_version": 1,
  "item": "rematter:matrix_crystal",
  "count": 1,
  "energy": 1024,
  "enabled": true
}
```

`count` is 1–64 and `energy` is 1–1,000,000,000 FE per complete consumed count. `disabled: true` or `enabled: false` disables an entry. Duplicate targets at different paths reject the fuel reload; override the original path. Fuels cannot add arbitrary items or Primal resources. Both generators get the same total FE, at 64 or 256 FE/t; unspent energy from consumed fuel is saved independently and is never repriced by a fuel reload. Full output pauses intake.

All nine standalone defaults yield 1,024 FE per crystal. The supplied ReForging overrides disable them pending a pack-specific audit. The standalone bound uses ReMatter's own production and reclaim graph, counts the sand input to four crystal cores, and conservatively assigns no cost to clay, furnace fuel or coloured additives. Even the absolute 25% FE discount leaves at least 3,000 FE of sand-synthesis cost per core; 1,024 FE stays below its 50% return ceiling. Fresh world matter is an external source. This is not a guarantee against foreign renewable-material or recipe loops; re-audit those paths before enabling pack fuels.

`rematter:primal_matter` and the Primal source items are reserved. Ordinary profiles, processes, synthesis, fluid adapters and Matter Cells cannot create, spend or export them. Existing unknown or unreadable stored contents remain preserved rather than being reinterpreted. Primal Cells have their own versioned state, payment slot, exact transfers and 16k/64k/256k capacities. Converter targets and 1:1/2:1/4:1 prices are an explicit implementation allowlist in 0.7.0; there is no inferred or data-pack-defined converter target category.

Primal Ores use dimension-explicit world generation for Overworld Deepslate, Nether Netherrack and outer End islands, excluding the central island. They appear in newly generated terrain only; existing chunks are not refilled. ReMatter 0.7.0 does not migrate older pre-release machine or Cell schemas. A removed ordinary substance definition still leaves its stored ID, phase and exact amount preserved.
