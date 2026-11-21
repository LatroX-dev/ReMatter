# ReMatter

**ReMatter is an industrial chemistry mod for Minecraft, developed for [LatroX's Xtreme – Tech ReForging](https://www.curseforge.com/minecraft/modpacks/latroxs-xtreme-tech-reforging).** It also runs on its own, with its own power, storage and in-game guide. Break down familiar materials, keep the substances you recover and use them in another recipe. Silica recovered from sand can become glass for your base. Minerals recovered from cobblestone can supply a tank of lava.

![Three Atmospheric Collectors connected by a green Matter Cable beneath the illuminated observatory arch at dusk](artwork/listing/screenshots/observatory-at-dusk.jpg)

## Your first glass

<img align="right" width="144" src="artwork/listing/chemical-processor.png" alt="Chemical Processor with two violet reaction columns">

1. **Start by hand.** Crank sand in the Hand Preparator to get Silith, then use a clay ball to get Argil. Mix one of each into four Matrix Blend and form your first Matrix Crystals. Place Silith above a book in the crafting grid to make the **ReMatter Guide**.
2. **Power a small line.** Craft a Crystal Generator with an Ember Matrix Crystal and two blocks of copper. Fuel it with spare crystals, or connect another FE source. Place a Matter Drive between a powered Decomposer and a powered Material Synthesizer and install a 64k Matter Cell in the Drive. Touching machines can share its stock.
3. **Decompose one sand block.** With the default recipes, the Decomposer spends 8,000 FE, stores **1,000 units of solid silica** and teaches you the Sand & Glass family. Open the Drive to see the silica. Leave the cell installed for the next step.
4. **Make one glass block.** On the Material Synthesizer, choose **Glass**, confirm with **Use recipe**, then press **Make 1**. It draws the same 1,000 units from the Drive and spends 16,000 FE to produce one glass block.

For a rare sample you want to keep, use the **Analyzer**. It spends energy to identify the material family and returns the sample. Research unlocks eligible raw materials, including metals, glass and natural blocks. Turn those materials into wires, gears, tools and machines through their usual crafting or processing recipes.

## Storage and production orders

Each Matter Drive holds up to six removable cells. A cell records quantities by substance and phase, so liquid water and water vapor have separate balances. Add **Matter Cables** to reach more machines and carry power from a generator or **Energy Cube**. A **Pressurized Canister** lets you carry a single liquid or gas between installations.

![The Matter Terminal lists the formulas and quantities available across six connected cells](artwork/listing/screenshots/matter-terminal-stock.jpg)

At the **Matter Terminal**, search the stock by name or filter it by phase. Open **Request Batch** to plan a target across connected machines. Pin the goal to see the next step, missing inputs, energy cost and leftover products as you work. For a recurring job, use **Loop** on the machine or set a stock target in its production settings. The Terminal's **Monitor** tab shows whether a substance is accumulating or being used up.

## What to build next

- **Make gas from water.** Use a water bucket on the Decomposer to store 1,000 units of liquid water. One **Water electrolysis** batch in a Chemical Processor converts that stock into 1,000 units of hydrogen and 500 of oxygen. Both gases return to the network.
- **Supply lava from cobblestone.** Decompose the stone, then run **Mineral blending** and **Mineral melting** in a Chemical Processor. Select **Lava** in the Material Synthesizer to deliver 1,000 mB to its tank, ready for a bucket or fluid pipe.
- **Build an industrial reactor.** Fit a Chemical Reactor with a Field Stabilizer, a Reaction Focus and separate ports for power and matter. Stabilizers provide the field strength for advanced processes. Foci increase the amount of input the chamber can hold at once. Parallel Upgrades let a suitably sized reactor process multiple batches at once.
- **Collect atmospheric gases.** Leave the intake of a powered Atmospheric Collector open to the Overworld sky and select **Air Separation** to collect 200 units of oxygen per batch. Other sources have different intake conditions and yields, including sulfur dioxide from Nether fumes.
- **Explore Primal research.** Mine Primal Ore and load its shards into a dedicated Primal Cell. At the Analyzer, spend that reserve to derive an eligible material from known substances. Later, a Discovery Lens opens research into unknown families.

![Field Coils at work behind the reactor glass during silica electrolysis](artwork/listing/screenshots/chemical-reactor-detail.jpg)

## Optional integrations

| What you want to do | Mods and setup |
| --- | --- |
| Order chemistry from your storage grid | Connect a ReMatter **Network Interface** to [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) or [Refined Storage](https://www.curseforge.com/minecraft/mc-mods/refined-storage). Select your storage system in the interface settings, enable **Automation**, then enable **Network jobs** on the machines that should accept orders. |
| Look up a recipe | [Just Enough Items](https://www.curseforge.com/minecraft/mc-mods/jei) and [SEMI (Seasonal EMI)](https://www.curseforge.com/minecraft/mc-mods/semi-seasonal-emi) show exact batch inputs, yields, energy costs and processing times. |
| Read machines and layouts in-game | [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) shows machine status when you look at a block. [GuideME](https://www.curseforge.com/minecraft/mc-mods/guideme) adds interactive 3D layouts to the ReMatter Guide. |
| Supply a chemical line | With [Mekanism: Version Locked](https://www.curseforge.com/minecraft/mc-mods/mekanism-version-locked), enable **Automation** on the Matter Drive and connect a Pressurized Tube to move supported chemicals such as hydrogen and oxygen. |
| Process ReMatter alloys | Use an [Oritech](https://www.curseforge.com/minecraft/mc-mods/oritech) Pulverizer to grind Rhevon or Lumivex ingots. The Foundry can process their powders into ingots. |
| Run a shared facility | With [FTB Teams](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge), teammates can operate your machines. The player who placed a machine keeps ownership. |

See the [integration guide](docs/integrations.md) for supported API versions and details on connecting external storage and chemical networks.

## Install, build and get help

ReMatter 0.7.x targets **Minecraft 26.1.2, NeoForge 26.1.2.126 and Java 25**. For multiplayer, put the same ReMatter JAR in the server's and each client's `mods` folder.

To build from source, run `./gradlew build` on macOS or Linux, or `gradlew.bat build` on Windows. The JAR is written to `build/libs/`. Pack authors can define new material profiles, permitted synthesis outputs and processes with [chemistry data packs](docs/data-packs.md).

[Read the ReMatter Wiki](https://github.com/LatroX-dev/ReMatter/wiki) for illustrated recipes, reactor layouts and troubleshooting.

[Report a bug, request a material or suggest a feature](https://github.com/LatroX-dev/ReMatter/issues/new/choose). For a recipe issue, include the item or fluid ID, your ReMatter version and the result you expected.

Code, data and documentation use [LGPL-3.0-only](COPYING.LESSER). Textures, models, sounds and other artwork are covered by the [asset license](LICENSE-ASSETS.md).

Inspired by [Alchemistry](https://www.curseforge.com/minecraft/mc-mods/alchemistry).

I build ReMatter from my own ideas and development experience, with AI as one of the tools I use along the way. I want the mod to be fun to play, and your feedback helps me decide what to improve next.

— LatroX
