# ReMatter

An industrial chemistry mod for Minecraft **26.1.2** and NeoForge. Recover substances from materials, carry them in a reusable Matter Cell, and assemble the materials you need.

Start with a **Hand Preparator** and an ordinary furnace to make the crystal medium. Then connect **a Decomposer, a Matter Storage and a Material Synthesizer**, with one **64k Matter Cell** in the storage. A Matter Drive can hold the Cell instead. An Analyzer, AE2 and Refined Storage are not prerequisites.

**Development build.** Build from source for testing; no mod-platform release is available yet. Processing needs an external FE source. ReMatter does not include a generator. Other Minecraft versions and loaders are not currently supported.

## What the crystal does

The Matrix Crystal is an artificial crystalline composite used as a reusable storage medium. Its inspiration is selective binding in ordered, porous materials; ReMatter's ability to hold its supported substances and phases in one portable matrix is fictional.

The crystal supplies neither energy nor new elements. The Decomposer separates supported feedstock into stored quantities; the Synthesizer consumes those quantities and FE to make an approved material. A Cell carries **actual stock**, not a learned recipe or an unlimited copy of an item. The crystal remains part of the Cell and is not spent for each batch.

The iron Machine Frame supports the machine, the copper Control Circuit controls its work, and the Cell's glass and metal housing contains the matrix. A Matrix Crystal also serves in the Synthesizer's assembly mechanism.

## Your first glass

1. **Prepare the matrix by hand.** Craft a Hand Preparator from two flint, one iron bars item, three planks and one stick. Put sand in its primary input and press **Crank** to extract Silith; repeat with a clay ball to extract Argil. Then put Silith in the primary input and Argil in the second input and crank one mixing batch to get four Matrix Blend. No FE, added water or fluid connection is needed. Smelt each Blend in an ordinary furnace to get a Matrix Crystal.
2. **Craft the starter kit.** Four iron ingots make a Machine Frame; copper, redstone and paper make a Control Circuit. Craft a Decomposer, Matter Storage (or Matter Drive), Material Synthesizer and **64k Matter Cell**. The Cell uses two iron ingots, two glass, one Circuit and one Crystal. The Hand Preparator needs no Circuit or Crystal, so this path has no circular ingredient requirement.
3. **Connect the three blocks.** Place Decomposer → storage → Synthesizer next to one another, with accessible fronts. Supply external FE to both processors; the storage needs no FE.
4. **Store sand.** Insert the Cell in the storage bay and put sand in the Decomposer's input. The Decomposer processes it automatically when running; resume it if paused. The completed batch stores silica in the Cell.
5. **Check the stock.** Open the storage to see the silica. Leave the Cell there: both connected processors use the same stock.
6. **Make glass.** Press **Choose recipe** (or **Recipe** to change an existing selection), select **Glass**, confirm with **Use recipe**, then press **Start**. Collect the glass from the output.

The default batch turns one sand into enough stored silica for one glass, using 24,000 FE across both machines. It does not multiply material. The same stored feedstock can later serve other explicitly supported products.

Craft the **ReMatter Guide** from a book and a copper ingot for the recipes and instructions. Optional **GuideME** adds interactive 3D views. Sneak-use opens the basic book.

## Cells and connected storage

Cells hold **64,000**, **256,000** or **1,000,000 total units**, with up to **512 substance/phase pairs** per Cell. Solids, liquids and gases share capacity; water and water vapor are different types.

The 64k Cell uses one Matrix Crystal. A Matrix Cluster combines four Crystals, Rhedon and Virel; one Cluster goes into a 256k Cell. A 1M Cell uses four Clusters and an Optical Circuit, made from a Control Circuit, Lumin and glass. All Cells also need their iron/glass housing. Larger Cells are crafted empty and do not consume your old Cell.

Matter Storage and Matter Drive each hold one Cell. Processors use the connected storage network. Cells left inside Decomposers or Synthesizers by an older build can be removed through **Configuration** and placed in a storage bay; those old slots no longer accept Cells or supply processing stock.

For continuous production, use hoppers and chests to feed input and collect output. Enable **Automation** under **Configuration** on both processors and press **Run nonstop** under the Synthesizer's **Configuration**.

Multiple Matter Storages and Drives can share one network; their available contents and capacity are combined. Matter Pipes bridge gaps but do not carry FE. A Matter Terminal gives another view of the connected stock. Older Matter Storages retain their built-in contents without a Cell. To move that stock into an empty Cell, insert one with enough capacity and use **Configuration → Move to cell**.

## Expand preparation

The five named fractions are physical intermediate materials for the matrix technology:

| Feedstock | Fraction | Use |
| --- | --- | --- |
| Sand | Silith | Mix with Argil to make Matrix Blend |
| Clay ball | Argil | Mix with Silith to make Matrix Blend |
| Redstone dust | Rhedon | Matrix Clusters |
| Nether quartz | Virel | Matrix Clusters |
| Glowstone dust | Lumin | Optical Circuits |

The Hand Preparator supports Silith, Argil and their mixture. The powered **Material Preparator** also extracts the other three fractions. Give it external FE; it performs the grinding, sieving and mixing stages automatically. The sieve sorts grains; these fictional fractions are not claims of chemically pure elements.

For greater throughput, build a rectangular industrial chamber with **physical work columns inside**. Both machines accept an outer width and depth of **3–9 blocks**. The Industrial Preparator needs **5–9 blocks of height**; the Industrial Reactor needs **4–9**, including floor and roof.

Use Casing edges and corners, Glass or Casing panels and at least two Machine Ports away from the edges. Place the controller in the front wall, one block above the base, in any non-edge column. External connections use the Ports' outward faces, including the reactor's matter-network connection.

| Machine | Each column, bottom to top | Speed before upgrades |
| --- | --- | --- |
| Industrial Preparator | Mixing Basin → Separation Unit → Grinding Unit | 4× per complete column |
| Industrial Reactor | Field Coil → Reaction Chamber | 2× per complete column |

Every column rests directly on the inside floor; leave all other interior blocks empty. A bigger shell creates room for more columns, but **empty space adds no throughput**. Two preparation columns give 8× speed; two reactor columns give 4×. Each controller still runs one batch at a time. Recipe quantities and FE per batch do not scale with the number of columns: industrial preparation uses twice the ordinary recipe FE, and reactions retain their original FE cost. The controller shows the effective duration.

Pause before rebuilding and follow the displayed reason if a shell or module is invalid. Older empty chambers need these modules before work can continue. Preparators shorter than five blocks must also grow taller. Retrofit the existing controller; the upgrade does not require discarding it or emptying the connected storage. Back up worlds before updating a development build.

## Build beyond the first batch

- **Analyzer:** inspect a sample without consuming it. It needs FE, but no Cell or storage network.
- **Electrolyzer and Chemical Reactor:** split and recombine stored substances, including water, hydrogen and oxygen.
- **Industrial Reactor:** run reactor processes faster in a complete chamber, starting at 3×3×4.
- **Speed and Efficiency Upgrades:** trade energy for throughput or reduce the energy cost of each batch.

The [material coverage table](docs/material-coverage.md) lists supported profiles and exclusions. Small forms use complete batches: nine supported nuggets represent one ingot without rounded fractions. Synthesized outputs require an explicit material permission. Frames, Circuits, Cells, machines, tools, armor, plates, wires and gears keep their normal crafting paths. Use the preparation chain for the five fractions and Matrix Blend, smelt Crystals and assemble Clusters; these intermediates do not gain chemistry conversion or synthesis permissions.

Machines belong to their placer. Connected machines need the same owner and loaded chunks. On the Synthesizer, Electrolyzer and reactors, **Start** requests one displayed recipe batch. **Configuration → Run nonstop** starts continuing production; **Stop repeat** stops it. **Pause** keeps the current order, and **Resume** continues it. Selecting another recipe cancels the old request and waits for a new instruction. Changing storage during a job stops work until its source is valid again; unfinished work does not spend its uncommitted resources.

Filled machines and Cells retain their contents when carried. Mechanisms animate while processing and stop when work waits; the menu explains the reason.

## Optional integrations

ReMatter remains standalone. Its optional [integrations](docs/integrations.md) add recipe displays, a machine HUD, illustrated guides, chemical transfers, and AE2/Refined Storage crafting requests through a **Network Interface**. To use both crafting networks at once, connect two interfaces to the same ReMatter network: one set to AE2 and the other to Refined Storage.

[LatroX's Xtreme - Tech ReForging](integrations/reforging/README.md) is the reference pack integration. Its separate data pack supplies quartz/silicon progression and machine-copying rules for coexistence with Replication and Equivox. Those rules are not activated by installing ReMatter.

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
