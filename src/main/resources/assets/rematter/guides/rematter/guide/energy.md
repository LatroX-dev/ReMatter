---
navigation:
  title: Energy Cubes
  icon: rematter:energy_cube
  parent: index.md
  position: 20
item_ids:
  - rematter:energy_cube
  - rematter:reinforced_energy_cube
  - rematter:resonant_energy_cube
---
# Store power and charge items

An Energy Cube keeps FE available between generation and use. Its recessed core and segmented charge display show its reserve. The three tiers share the same Ember crystal identity; copper, turquoise and mauve bands match their Matter Cable tiers. Four identical horizontal ports accept the same centered cable connector on every side.

| Cube | Stored energy | Shared transfer limit | Charging slots |
| --- | --- | --- | --- |
| Energy Cube | 4,000,000 FE | 2,048 FE/t | 1 |
| Reinforced Energy Cube | 32,000,000 FE | 16,384 FE/t | 2 |
| Resonant Energy Cube | 256,000,000 FE | 131,072 FE/t | 4 |

<RecipeFor id="rematter:energy_cube" />
<RecipeFor id="rematter:reinforced_energy_cube" />
<RecipeFor id="rematter:resonant_energy_cube" />

Connect a source to an **Input** face and a machine or Matter Cable to an **Output** face. The front starts as Output, with all other faces as Input. Open **Sides** and hover the unfolded face map to identify and change a side. Directions rotate with the cube.

Insert a single compatible FE item into each charging slot. Charging moves energy from the cube into the item; it never empties the item back into the cube. All outputs and charging slots share the tier's output allowance, even when several faces or items work together. Input has its own shared limit.

Moving a cube preserves its stored energy, side settings and charging items. Unreadable contents remain protected. Each tier is crafted independently, so crafting a larger cube does not consume an energy-filled smaller cube.
