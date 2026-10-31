# ReMatter

An industrial chemistry mod for Minecraft **26.1.2** and NeoForge. Use it in your own pack, with **LatroX's Xtreme - Tech ReForging** as its reference integration.

Recover substances from materials, store them in a shared matter inventory, and synthesize raw materials. Solids, liquids, and gases are part of the same production system. Manufacturing components and equipment remains with your existing production chains.

**Development build.** Build from source for testing; no mod-platform release is available yet. Other Minecraft versions and loaders are not currently supported.

## Machines and materials

- **Analyzer** — inspect the substances in a supported sample.
- **Decomposer** — recover substances from supported items and fluids in exact batches.
- **Matter Storage and Terminal** — store solids, liquids, and gases as quantities, with search and paging.
- **Material Synthesizer** — turn stored substances into approved raw materials and natural materials.
- **Electrolyzer and Chemical Reactor** — separate and recombine substances, including water, hydrogen, and oxygen.
- **Industrial Reactor** — a reactor controller with an eight-block casing ring.
- **Matter Pipes** — connect machines to one shared storage. Networks operate within loaded chunks.
- **Speed and Efficiency Upgrades** — bounded upgrades with different energy costs.

Each machine has its own industrial silhouette, with graphite housings, copper fittings, visible ports, and cyan displays. Working analyzers scan their sample bed; decomposers turn twin cutters; synthesizers cycle their press; electrolyzers show rising bubbles; and both reactor types rotate their drive. Motion and active displays follow processing and stop when a machine pauses or cannot proceed. Matter Pipes connect visually in all six directions.

Copper, iron, gold, stone, dirt, sand, quartz, water, and lava form the starting catalogue. Optional definitions expand it with reviewed metals, alloys, minerals, crystals, and mod-specific raw materials. Each definition identifies an exact item or fluid; shared tags do not automatically grant conversions. Materials with different processing requirements keep separate identities.

Small forms use whole batches: nine supported nuggets represent exactly one ingot, with no rounded fractions. The Analyzer labels batch quantities, and recipe viewers show the required item count. A searchable process picker handles large catalogues.

See the [material coverage table](docs/material-coverage.md) for supported families, exact item profiles, and exclusions.

Synthesis uses an explicit allowlist. Machines, weapons, tools, armor, plates, wires, gears, and circuits remain outside that list.

## Getting started

1. Place a Matter Storage and connect a Decomposer and Material Synthesizer using Matter Pipes. Use one storage per connected network.
2. Supply FE to each processing machine from a compatible energy source. ReMatter does not include a generator; Matter Pipes carry the matter network, not FE.
3. Insert a copper ingot in the Decomposer. Its copper becomes available in the shared storage.
4. Press **Select** in the Material Synthesizer, search for Copper Ingot, and choose it to recover an ingot from that stored copper.
5. Import water through a Decomposer's tank, select Water Electrolysis in an Electrolyzer, and use Water Synthesis in a Chemical Reactor for the reverse process. Select Water in the Material Synthesizer to export it to a tank.

Machines belong to the player who places them. Item and fluid automation is off by default and can be enabled from the machine screen. Filled machines retain their contents when picked up.

For the Industrial Reactor, place eight Reactor Casings around the controller in a flat 3×3 ring, with the controller at the center. A broken ring pauses processing.

## Tutorial and integrations

Craft the **ReMatter Guide** from a book and a copper ingot. It explains the first copper loop, water processing, automation, upgrades, and the reactor casing ring. The book works on its own. With **GuideME** installed, it opens an extended guide with interactive 3D construction scenes.

Optional recipe-viewer, HUD, and chemical-transfer support is described in [Integrations](docs/integrations.md). ReMatter does not require ReForging, JEI, EMI, Jade, GuideME, or Mekanism to load. An external FE source is needed to operate processing machines.

The separate [ReForging integration](integrations/reforging/README.md) supplies quartz, silicon progression, and machine-copying rules for packs that also use Replication and Equivox. It is not activated by installing the mod.

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
