---
navigation:
  title: Energy Cubes
  icon: rematter:energy_cube
  parent: index.md
  position: 21
item_ids:
  - rematter:energy_cube
  - rematter:reinforced_energy_cube
  - rematter:resonant_energy_cube
  - rematter:portable_energy_cell
---
# Store power and charge items

An Energy Cube keeps FE available between generation and use. Its recessed core and segmented charge display show its reserve. The three tiers share the same Ember crystal identity; copper, turquoise and mauve bands match their Matter Cable tiers. Four identical horizontal ports accept the same centered cable connector on every side.

| Cube | Stored energy | Shared transfer limit | Charge + Discharge bays |
| --- | --- | --- | --- |
| Energy Cube | 4,000,000 FE | 2,048 FE/t | 1 + 1 |
| Reinforced Energy Cube | 32,000,000 FE | 16,384 FE/t | 2 + 2 |
| Resonant Energy Cube | 256,000,000 FE | 131,072 FE/t | 4 + 4 |

<RecipeFor id="rematter:energy_cube" />
<RecipeFor id="rematter:reinforced_energy_cube" />
<RecipeFor id="rematter:resonant_energy_cube" />

Connect a source to an **Input** face and a machine or Matter Cable to an **Output** face, or cycle a face to **Input/Output** for bidirectional transfer. The front starts as Output, with all other faces as Input. Open **Sides** to use the named face map: left-click steps forward, right-click or Shift-click steps backward. Directions rotate with the cube. A **Disabled** face has plain casing and refuses transport, including vanilla hoppers. Input faces accept FE items into **Charge** or **Discharge** bays; Output faces release charged items from Charge bays and drained items from Discharge bays.

The core's pulses move **up while charging** and **down while discharging**, then settle when the stored energy stops changing. The charge level still shows the stored reserve. Enabled horizontal faces have the same appearance; a Disabled face hides its socket and core window behind plain casing.

Insert a compatible FE item into a **Charge** slot to fill it from the cube, or into a **Discharge** slot to drain stored FE from the item into the cube. All outputs and Charge slots share the tier's output allowance, while external inputs and Discharge slots share its input limit.

Craft a **Portable Energy Cell** (8,000,000 FE, 16,384 FE/t) to power a Wireless Matter Terminal or charge FE tools in your inventory when toggled with **Sneak + Right-Click**.

<RecipeFor id="rematter:portable_energy_cell" />

Moving a cube preserves its stored energy, side settings and docked items. Unreadable contents remain protected. Each tier is crafted independently, so crafting a larger cube does not consume an energy-filled smaller cube.
