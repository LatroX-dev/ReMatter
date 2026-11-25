---
navigation:
  title: Energy Cubes & Matrix
  icon: rematter:energy_cube
  parent: index.md
  position: 21
item_ids:
  - rematter:energy_core
  - rematter:energy_cube
  - rematter:reinforced_energy_cube
  - rematter:resonant_energy_cube
  - rematter:primal_energy_cube
  - rematter:energy_matrix_controller
  - rematter:energy_matrix_casing
  - rematter:energy_matrix_glass
  - rematter:energy_matrix_port
  - rematter:energy_matrix_cell
  - rematter:reinforced_energy_matrix_cell
  - rematter:resonant_energy_matrix_cell
  - rematter:primal_energy_matrix_cell
  - rematter:energy_matrix_provider
  - rematter:reinforced_energy_matrix_provider
  - rematter:resonant_energy_matrix_provider
  - rematter:primal_energy_matrix_provider
  - rematter:energy_reactor_controller
  - rematter:energy_reactor_casing
  - rematter:energy_reactor_glass
  - rematter:energy_reactor_port
  - rematter:reactor_fuel_core
  - rematter:reactor_induction_coil
  - rematter:portable_energy_cell
---
# Store and generate power

An **Energy Core** is the shared Ember capacitor building block behind every Energy Cube tier, the Portable Energy Cell, the Wireless Matter Terminal, the Energy Matrix, and the Energy Reactor.

<RecipeFor id="rematter:energy_core" />

## Energy Cubes

An Energy Cube keeps FE available between generation and use. Its recessed core and segmented charge display show its reserve. The four tiers build directly on each other (**Energy Cube** → **Reinforced Energy Cube** → **Resonant Energy Cube** → **Primal Energy Cube**), sharing the same Ember crystal identity while copper, turquoise, mauve, and animated Primal bands match their progression tiers. Four identical horizontal ports accept the same centered cable connector on every side.

| Cube | Stored energy | Shared transfer limit | Charge + Discharge bays |
| --- | --- | --- | --- |
| Energy Cube | 8,000,000 FE | 4,096 FE/t | 1 + 1 |
| Reinforced Energy Cube | 64,000,000 FE | 65,536 FE/t | 2 + 2 |
| Resonant Energy Cube | 512,000,000 FE | 524,288 FE/t | 4 + 4 |
| Primal Energy Cube | 4,096,000,000 FE | 4,194,304 FE/t | 4 + 4 |

<RecipeFor id="rematter:energy_cube" />
<RecipeFor id="rematter:reinforced_energy_cube" />
<RecipeFor id="rematter:resonant_energy_cube" />
<RecipeFor id="rematter:primal_energy_cube" />

Connect a source to an **Input** face and a machine or Matter Cable to an **Output** face, or cycle a face to **Input/Output** for bidirectional transfer. The front starts as Output, with all other faces as Input. Open **Sides** to use the named face map: left-click steps forward, right-click or Shift-click steps backward. Directions rotate with the cube. A **Disabled** face has plain casing and refuses transport, including vanilla hoppers. Input faces accept FE items into **Charge** or **Discharge** bays; Output faces release charged items from Charge bays and drained items from Discharge bays.

The core's pulses move **up while charging** and **down while discharging**, then settle when the stored energy stops changing. The charge level still shows the stored reserve. Enabled horizontal faces have the same appearance; a Disabled face hides its socket and core window behind plain casing.

Insert a compatible FE item into a **Charge** slot to fill it from the cube, or into a **Discharge** slot to drain stored FE from the item into the cube. All outputs and Charge slots share the tier's output allowance, while external inputs and Discharge slots share its input limit.

## Energy Matrix

For silent multi-billion FE reserves, build a standalone **Energy Matrix** (**3×3×3** up to **9×9×9**, with an interior of at least 2 blocks) using its own dedicated shell — **Energy Matrix Casing**, **Energy Matrix Glass**, and **Energy Matrix Ports** — with an **Energy Matrix Controller** in the front wall. Energy Matrix Ports may be placed on any wall, floor, or roof panel.

Pack the hollow 3D cavity with any mix of **Energy Matrix Cells** (capacity) and **Energy Matrix Providers** (shared I/O transfer rate) across four tiers:

| Tier | Matrix Cell Capacity | Matrix Provider Transfer |
| --- | --- | --- |
| Basic | 64,000,000 FE | 262,144 FE/t |
| Reinforced | 512,000,000 FE | 1,048,576 FE/t |
| Resonant | 4,096,000,000 FE | 4,194,304 FE/t |
| Primal | 32,768,000,000 FE | 16,777,216 FE/t |

The Energy Matrix operates completely silently, displays live 64-bit storage and In/Out telemetry, supports toggleable Auto-Output on Power ports, and provides built-in Charge (+) and Discharge (-) item bays.

<RecipeFor id="rematter:energy_matrix_controller" />
<RecipeFor id="rematter:energy_matrix_casing" />
<RecipeFor id="rematter:energy_matrix_glass" />
<RecipeFor id="rematter:energy_matrix_port" />
<RecipeFor id="rematter:energy_matrix_cell" />
<RecipeFor id="rematter:reinforced_energy_matrix_cell" />
<RecipeFor id="rematter:resonant_energy_matrix_cell" />
<RecipeFor id="rematter:primal_energy_matrix_cell" />
<RecipeFor id="rematter:energy_matrix_provider" />
<RecipeFor id="rematter:reinforced_energy_matrix_provider" />
<RecipeFor id="rematter:resonant_energy_matrix_provider" />
<RecipeFor id="rematter:primal_energy_matrix_provider" />

## Energy Reactor

For endgame power generation, build an **Energy Reactor** (**5×5×5** up to **9×9×9**) using **Energy Reactor Casing**, **Energy Reactor Glass**, and **Energy Reactor Ports**, with an **Energy Reactor Controller** in the front wall. Mount **Reactor Fuel Cores** (+512M FE buffer and +1× generation per core) and **Reactor Induction Coils** (+1,048,576 FE/t output bandwidth and +5% efficiency, capped at +25%) on the inside floor or ceiling.

Select one of three fuel modes directly in the controller:
- **Crystal** (8,192 FE/t per Fuel Core): burns multiple crystal fuels per tick from the left fuel slot or an Item Input port.
- **Matter** (24,576–98,304 FE/t per Fuel Core): draws Uranium (98.3k), Void Condensate (49.2k), or Lithium + Hydrogen (24.6k) from a connected Matter port.
- **Primal** (196,608 FE/t per Fuel Core): draws 320 Primal Matter/t per core from the right Primal Cell slot or the connected Matter Network (high-density peak discharge for mined Primal Shards and stored Primal Cells).

Supplying liquid **Water** or **Nitrogen** gas over the Matter Network enables **Coolant Overdrive** (+25% FE output and -20% fuel consumption). Running dry is completely safe — the Energy Reactor auto-throttles whenever its buffer is full or paused and never explodes.

<RecipeFor id="rematter:energy_reactor_controller" />
<RecipeFor id="rematter:energy_reactor_casing" />
<RecipeFor id="rematter:energy_reactor_glass" />
<RecipeFor id="rematter:energy_reactor_port" />
<RecipeFor id="rematter:reactor_fuel_core" />
<RecipeFor id="rematter:reactor_induction_coil" />

## Portable Energy Cell

Craft a **Portable Energy Cell** (16,000,000 FE, 65,536 FE/t) from two **Energy Cores** to power a Wireless Matter Terminal or charge FE tools in your inventory and equipped **Curios** slots when toggled with **Sneak + Right-Click**. With Curios installed, the Portable Energy Cell can also sit in a Curio, Charm, or Belt slot and recharge your gear continuously.

<RecipeFor id="rematter:portable_energy_cell" />

Moving **or upgrading** an Energy Cube to the next tier preserves its stored FE, side configuration and docked Charge/Discharge items. Cubes with unreadable or unknown-schema data are safely refused by upgrade recipes so protected contents are never lost.
