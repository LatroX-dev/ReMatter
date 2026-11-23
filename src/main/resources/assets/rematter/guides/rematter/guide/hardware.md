---
navigation:
  title: Hardware and upgrades
  icon: rematter:reaction_chamber
  parent: index.md
  position: 17
---
# Hardware grants capabilities; upgrades control work

| Hardware | Its job |
| --- | --- |
| Field Stabilizer / Field Coil | Adds Field; special Reactor procedures require 4 or 8 |
| Reaction Focus / Reaction Chamber | Adds 16,000 u room for simultaneous recipe inputs |
| Resonance Cutter / Grinding Unit | Adds capacity for grinding steps |
| Confluence Basin / Mixing Basin | Adds capacity for mixing steps |
| Phase Filter / Separation Unit | Adds Precision for separation and recipes that require finer control |

The crystal and the small tab on a module mount show the crystal used to craft it: **Amber** for Cutters, Filters and Basins, **Violet** for Stabilizers and Foci. The item in your hand uses the same mechanism and sprites as the installed module.

Hardware does not add another global Speed multiplier or an FE discount. Extra room never multiplies recipe output. The menu separates Field, Precision, room, speed, efficiency and active jobs.

Minimum outer height is 4 for Reactor, 5 for Preparator. Each additional layer permits another lane, up to 4. **Parallel Upgrades and enough hardware are also required.** Simultaneous Reactor inputs must fit its room; preparation jobs need capacity for every step they use.

All upgradable compact processors and industrial controllers (Chemical Reactor, Industrial Decomposer, Industrial Synthesizer and Preparation Reactor) have separate **Speed** and **Efficiency** slots that work simultaneously, plus **Parallel** on industrial controllers and a separate **Pressure Module** slot on the compact Material Synthesizer (a formed Industrial Synthesizer provides Tier 2 crystallization built in). **Sneak + Right-Click** a machine with an upgrade item to install it in-world. Analyzer payment and Discovery Lens have dedicated slots.

Four Speed Upgrades give ten times base work rate (2×, 4×, 7×, 10×) and +25% FE per unmatched upgrade. Each Efficiency Upgrade doubles the internal FE buffer (32k → 512k FE compact, 128k → 2,048k FE industrial), cancels one Speed Upgrade's +25% FE surcharge 1:1, and saves 5% base FE per excess upgrade (up to 20% saved; absolute policy limit 25%). Three Parallel Upgrades permit up to four lanes when height, room and step capacity allow. Every batch pays full inputs and FE, drawn tick by tick while working.

All lanes share [one transfer budget](throughput.md).

## Read the work

Bench mechanisms show their task: jaws close around a Decomposer sample, the Analyzer scans it with a ring, the Preparator presses or mixes it, and the Synthesizer sends matter toward its product. The Chemical Processor exchanges liquid between its two columns. Industrial fields turn continuously while modules send pulses into the chamber; Separation Units carry their pulses away from the focus.

A bench status lamp is dark when idle, uses the device colour while working, and breathes blue when a loaded job waits. Read the menu for its actual reason. Mechanisms ease into motion and slow to a stop; rapid recipe batches do not make them jump back every time. A short visual afterglow does not consume extra resources or mean that a paused machine is still producing.
