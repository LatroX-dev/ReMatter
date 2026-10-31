# Chemistry data packs

ReMatter reads chemistry data on server startup and `/reload`. Material profiles, substance definitions, chemical processes, and synthesis permissions are separate resources. No recipe scanning or tag expansion grants synthesis permission.

Place JSON files in the following directories of a Minecraft data pack:

| Directory | Purpose |
| --- | --- |
| `data/<namespace>/rematter/substance/` | Elements, compounds, mixtures, and supported phases |
| `data/<namespace>/rematter/material_profile/` | The substances contained in one ordinary item |
| `data/<namespace>/rematter/process/` | Balanced transformations between stored substances |
| `data/<namespace>/rematter/synthesis/` | Explicit permission to synthesize a particular output |

Every document requires `"schema_version": 1`. Substance and process IDs come from the namespace and filename, including any nested path. Standard data-pack priority applies to files with the same path. Two different profile or synthesis files targeting the same item are rejected.

The whole catalogue is validated before a reload takes effect. Unknown substances, invalid phases, fractional or overflowing quantities, unbalanced reactions, and forbidden synthesis categories reject the reload. Existing stored quantities retain their IDs when a definition is temporarily unavailable.

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

`example:copper_dust` is a placeholder: use an actual registered item ID. `required_mod` skips the file if that mod is absent; otherwise an unknown item is an error. Energy is measured in FE. All quantities, energy costs, and durations must be positive integers. Profiles apply to one ordinary item; modified or data-bearing stacks are not implicitly safe to recycle.

A profile alone permits decomposition. To permit synthesis, add a separate file in `rematter/synthesis/`:

```json
{
  "schema_version": 1,
  "required_mod": "example",
  "item": "example:copper_dust",
  "category": "raw_material"
}
```

Use `raw_material` or `natural_material` for approved item outputs. Machines, equipment, plates, wires, gears, circuits, and other manufactured components are outside ReMatter's synthesis rules. Classifying an item as a raw material is a deliberate pack-author decision; do not derive it automatically from recipes.

The catalogue also distinguishes `fluid` and `gas` export permissions. These require an implemented adapter; an allowlist entry by itself does not make a substance compatible with an arbitrary tank or pipe.

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

Process types are `electrolysis`, `reaction`, `separation`, `phase_change`, and `crystallization`. A process only becomes usable when a machine supports that type. Both inputs and outputs must be nonempty, and their elemental compositions must match exactly.

## Substances

Each definition specifies a stable ID through its file path, a translation key, a display symbol, a kind (`element`, `compound`, or `mixture`), an elemental composition, and its supported phases (`solid`, `liquid`, `gas`). For example, the water definition is:

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

## Disabling entries

Override a substance or process file with `{"schema_version": 1, "disabled": true}`. Material profiles and synthesis entries also need their `item` field to identify the target. Removing a material profile also removes its existing synthesis permission. Removing a substance still referenced by a profile or process rejects the reload; remove or update those references together.

Disabling a definition does not delete stored quantities. Keep existing substance IDs stable when updating a pack.
