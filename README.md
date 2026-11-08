# ReMatter

An industrial chemistry mod for Minecraft **26.1.2** and NeoForge. Recover substances from materials, carry them in a reusable Matter Cell, and assemble the materials you need.

Start with a **Hand Preparator** and an ordinary furnace to make the crystal medium. Build a powered **Material Preparator** for the crystal variants used in a **Decomposer**, **Matter Drive** and **Material Synthesizer**. Connect those three machines and put a **64k Matter Cell** in any Drive bay.

**Development build.** Build from source for testing; no mod-platform release is available yet. Processing needs an external FE source. ReMatter does not include a generator. Other Minecraft versions and loaders are not currently supported.

## What the crystal does

The Matrix Crystal is an artificial crystalline composite used as a reusable storage medium. Its inspiration is selective binding in ordered, porous materials; ReMatter's ability to hold its supported substances and phases in one portable matrix is fictional.

The crystal supplies neither energy nor new elements. The Decomposer separates supported feedstock into stored quantities; the Synthesizer consumes those quantities and FE to make an approved material. A Cell carries **actual stock**, not a learned recipe or an unlimited copy of an item. The crystal remains part of the Cell and is not spent for each batch.

The iron Machine Frame supports the machine, the copper Control Circuit controls its work, and the Cell's glass and metal housing contains the matrix. A Matrix Crystal also serves in the Synthesizer's assembly mechanism.

## Your first glass

1. **Prepare the matrix by hand.** Craft a Hand Preparator from two flint, five iron ingots and two copper ingots. Crank sand to extract Silith and a clay ball to extract Argil. Put Silith in the primary input and Argil in the second input to make four Matrix Blend. Smelt the Blend in an ordinary furnace to make Matrix Crystals.
2. **Build powered preparation.** Grind gold by hand to make Gold Dust and combine it with Matrix Crystals for the Material Preparator's two Amber Matrix Crystals. The ReMatter Guide shows the crafting grids for Structural Bars, Machine Frames and Control Circuits.
3. **Craft the starter kit.** Use Copper, Mint and Leaf Matrix Crystals for the Decomposer, Matter Drive and Material Synthesizer, respectively. The powered Material Preparator grinds diamond and emerald for the Mint and Leaf variants. Build a Cell Housing from Structural Bars and glass, then stack a Control Circuit, a plain Matrix Crystal and the Housing to make a **64k Matter Cell**.
4. **Connect the three blocks.** Place Decomposer → Drive → Synthesizer in a touching row. Supply external FE to both processors. The Drive needs no power.
5. **Store sand.** Insert the Cell in any Drive bay and put sand in the Decomposer's input. Press **Start** if it is paused. Open the Drive after processing to see the stored silica.
6. **Make glass.** Press **Choose recipe** (or **Recipe** to change an existing selection), select **Glass**, confirm with **Use recipe**, then press **Start**. Collect the glass from the output.

The default batch turns one sand into enough stored silica for one glass, using 24,000 FE across both machines. It does not multiply material. The same stored feedstock can later serve other explicitly supported products.

Craft the **ReMatter Guide** from a book and Silith for the recipes and instructions. Optional **GuideME** adds interactive 3D views. Sneak-use opens the basic book.

## Cells and connected storage

Cells hold **64,000**, **256,000** or **1,000,000 total units**, with up to **512 substance/phase pairs** per Cell. Solids, liquids and gases share capacity; water and water vapor are different types.

The 64k Cell uses one Matrix Crystal and a Control Circuit. The 256k Cell uses a Matrix Cluster, a Control Circuit and two Ender Conductors. The 1M Cell uses four Clusters, an Optical Circuit, a Diamond Lattice, an Ender Conductor and a Netherite Sheath. All tiers use a separately crafted Cell Housing and start empty.

A Matter Drive holds **six individually removable Cells**, arranged in two columns of three. Two three-bay Cell Racks form its assembly. The front shows the inserted Cells, and connected processors use their shared contents.

For continuous production, use hoppers and chests to feed input and collect output. Enable **Automation** under **Configuration** on both processors and press **Run nonstop** under the Synthesizer's **Configuration**.

Multiple Drives can share one network and pool their capacity. Matter Cables carry both matter-network traffic and FE. The connected machines and energy stores provide the FE buffers. Connect a Matter Terminal at its back to inspect stock, open connected device menus and queue supported production batches.

## Expand preparation

The five named fractions are physical intermediate materials for the matrix technology:

| Feedstock | Fraction | Use |
| --- | --- | --- |
| Sand | Silith | Mix with Argil to make Matrix Blend |
| Clay ball | Argil | Mix with Silith to make Matrix Blend |
| Redstone dust | Rhedon | Matrix Clusters and Rhevon alloy |
| Nether quartz | Virel | Matrix Clusters and Virelite ceramic |
| Glowstone dust | Lumin | Lumivex alloy for Optical Circuits |

The Hand Preparator supports Silith, Argil and their mixture. The powered **Material Preparator** also extracts the other three fractions. Give it external FE; it performs the grinding, sieving and mixing stages automatically. The sieve sorts grains; these fictional fractions are not claims of chemically pure elements.

Powered preparation also grinds iron, copper and gold into Dusts and mixes **three counted inputs** into Rhevon Powder, Lumivex Powder or Virelite Powder. The **Sinter Furnace** presses and heats each powder into its finished ingot or ceramic piece. Rhevon reinforces the preparation modules, Lumivex conducts the field in Stabilizers and Optical Circuits, and Virelite insulates Reaction Foci and Resonance Cutters. Alloy powders can also use an ordinary furnace or blast furnace; grinding an ingot returns the same powder, one for one. Optional Mekanism Crusher and Oritech Pulverizer/Foundry recipes provide matching processing routes. The guide and recipe viewers show the exact inputs, quantities and FE costs.

For greater throughput, build a rectangular industrial chamber with **floor and ceiling parts forming one working field**. Outer width and depth each range from **3 to 9 blocks**. The Industrial Preparator needs a footprint of at least **3×4 or 4×3** and a height of **5–9**. The Industrial Reactor starts at **3×3** with a height of **4–9**. Heights include the floor and roof.

Build the edges and corners from Reactor Casing. The Industrial Reactor also uses Casing across its entire floor and roof. Use Glass or Casing on the remaining panels, with at least two Machine Ports away from the edges. Place the controller in the front wall, one block above the base, in any non-edge column. External connections use the Ports' outward faces.

| Machine | Floor / ceiling | Speed before upgrades |
| --- | --- | --- |
| Industrial Preparator | Cutter, Filter and Basin on either mounting plane | 2× per Basin and Cutter |
| Industrial Reactor | Stabilizer and Focus on either mounting plane | 2× per Focus |

Fit at least one of every required part directly on the inside floor or under the ceiling; all matching types work on either mounting plane. Clicking a support's top or bottom face automatically orients the fitting; sideways and unsupported placement are rejected. Keep the middle clear. **Empty space adds no throughput.** Extra Filters or Stabilizers save FE with diminishing returns: two give 6%, three give 10%. Preparation starts at twice the ordinary recipe FE, reactions at the original cost, before this saving and upgrades. Inputs and outputs stay exact; each controller runs one batch at a time and shows the effective duration.

Pause before rebuilding and follow the displayed reason if a shell or module is invalid.

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

Pack authors can add material profiles, synthesis permissions and reactions for their mods using [chemistry data packs](docs/data-packs.md). Start with the [copyable example pack](examples/chemistry-datapack/).

## Feedback

Report bugs and suggest improvements through [Issues](https://github.com/LatroX-dev/ReMatter/issues/new/choose). English and German are welcome. Include the mod version or commit, Minecraft and NeoForge versions, reproduction steps, and relevant logs with personal information removed.

## License

A license has not been selected yet.
