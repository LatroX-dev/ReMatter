# ReMatter

An industrial chemistry mod for **LatroX's Xtreme - Tech ReForging**, built for Minecraft **26.1.2** and NeoForge.

Recover substances from materials, store them in a shared matter inventory, and synthesize raw materials. Solids, liquids, and gases are part of the same production system. Manufacturing components and equipment remains with the modpack's existing production chains.

**Development build.** No release is available yet.

## Machines and materials

- **Analyzer** — inspect the substances in a supported sample.
- **Decomposer** — recover substances from materials, water, and lava.
- **Matter Storage and Terminal** — store solids, liquids, and gases as quantities, with search and paging.
- **Material Synthesizer** — turn stored substances into approved raw materials and natural materials.
- **Electrolyzer and Chemical Reactor** — separate and recombine substances, including water, hydrogen, and oxygen.
- **Industrial Reactor** — a reactor controller with an eight-block casing ring.
- **Matter Pipes** — connect machines to one shared storage. Networks operate within loaded chunks.
- **Speed and Efficiency Upgrades** — bounded upgrades with different energy costs.

Copper, iron, gold, stone, dirt, sand, quartz, water, and lava form the starting catalogue. Optional profiles cover selected metal ingots and dusts from AllTheOres, Mekanism, Occultism, Ender IO, FTB Industrial Contraptions, Energized Power, and Oritech. These profiles are not a claim of complete support for every material or production chain in those mods.

Synthesis uses an explicit allowlist. Machines, weapons, tools, armor, plates, wires, gears, and circuits remain outside that list.

## Getting started

1. Place a Matter Storage and connect a Decomposer and Material Synthesizer using Matter Pipes. Use one storage per connected network.
2. Supply FE to each processing machine from a compatible energy source.
3. Insert a copper ingot in the Decomposer. Its copper becomes available in the shared storage.
4. Choose Copper Ingot in the Material Synthesizer to recover an ingot from that stored copper.
5. Import water through a Decomposer's tank, select Water Electrolysis in an Electrolyzer, and use Water Synthesis in a Chemical Reactor for the reverse process. Select Water in the Material Synthesizer to export it to a tank.

Machines belong to the player who places them. Item and fluid automation is off by default and can be enabled from the machine screen. Filled machines retain their contents when picked up.

For the Industrial Reactor, place eight Reactor Casings around the controller in a flat 3×3 ring, with the controller at the center. A broken ring pauses processing.

## Build

Install JDK **25**, then run:

```sh
./gradlew build
```

The mod JAR is written to `build/libs/`. On Windows, use `gradlew.bat build`.

Material profiles and processes can be configured using [chemistry data packs](docs/data-packs.md).

## Feedback

Report bugs and suggest improvements through [Issues](https://github.com/LatroX-dev/ReMatter/issues/new/choose). English and German are welcome. Include the mod version or commit, Minecraft and NeoForge versions, reproduction steps, and relevant logs with personal information removed.

## License

A license has not been selected yet.
