# ReMatter

An industrial chemistry mod for Minecraft **26.1.2** and NeoForge. Recover substances from materials, carry them in a reusable Matter Cell, and assemble the materials you need.

**[Alchemistry](https://www.curseforge.com/minecraft/mc-mods/alchemistry) inspired me to create ReMatter.** I wanted to bring that chemistry-workshop idea to newer Minecraft versions and give it my own twist. [Alchemistry's official file list](https://www.curseforge.com/minecraft/mc-mods/alchemistry/files/all) currently reaches Minecraft 1.21.5 and lists no build for my **Minecraft 26.1.2 / NeoForge** target (checked 12 November 2026).

I built ReMatter around exact quantities of solids, liquids and gases, reusable Matter Cells, and explicitly permitted material outputs. In the 0.7 development line, I am adding material research, hidden endgame Discovery, modular industry and internal power generation with Crystal Generators. I am also adding tiered Energy Cubes to buffer FE and charge equipment, plus a Cell Transfer Station to sort substances and configure what each Cell should store. I want learning what can be made, collecting the matter, and building the equipment to be separate parts of progression.

Start with a **Hand Preparator** and an ordinary furnace to make the crystal medium. Build a powered **Material Preparator** for the crystal variants used in a **Decomposer**, **Matter Drive** and **Material Synthesizer**. Connect those three machines and put a **64k Matter Cell** in any Drive bay.

**Development version 0.7.0.** Build from source for testing; no mod-platform release is available yet. Powered preparation needs a starting FE source; later Crystal Generators can consume spare crystals. Other Minecraft versions and loaders are not currently supported. ReMatter uses its own numeric versions, independent of any reference pack. This first-release development line does not provide migration from earlier development schemas; unknown stored substances remain preserved.

## What the crystal does

The Matrix Crystal is an artificial crystalline composite used as a reusable storage medium. Its inspiration is selective binding in ordered, porous materials; ReMatter's ability to hold its supported substances and phases in one portable matrix is fictional.

A crystal installed in a machine or Cell is a reusable functional part. The Decomposer separates supported feedstock into stored quantities; the Synthesizer consumes those quantities and FE to make an explicitly approved, researched material. Loose crystals can instead be consumed completely by a Crystal Generator. A Cell carries **actual stock**, not a learned recipe or an unlimited copy of an item. The crystal remains part of the Cell and is not spent for each batch.

The iron Machine Frame supports the machine, the copper Control Circuit controls its work, and the Cell's glass and metal housing contains the matrix. A Matrix Crystal also serves in the Synthesizer's assembly mechanism.

## Your first glass

1. **Prepare the matrix by hand.** Craft a Hand Preparator from two flint, five iron ingots and two copper ingots. Crank sand to extract Silith and a clay ball to extract Argil. Put Silith in the primary input and Argil in the second input to make four Matrix Blend. Smelt the Blend in an ordinary furnace to make Matrix Crystals.
2. **Build powered preparation.** Grind gold by hand to make Gold Dust and combine it with Matrix Crystals for the Material Preparator's two Amber Matrix Crystals. The ReMatter Guide shows the crafting grids for Structural Bars, Machine Frames and Control Circuits.
3. **Craft the starter kit.** Use Copper, Mint and Leaf Matrix Crystals for the Decomposer, Matter Drive and Material Synthesizer, respectively. The powered Material Preparator grinds diamond and emerald for the Mint and Leaf variants. Build a Cell Housing from Structural Bars and glass, then stack a Control Circuit, a plain Matrix Crystal and the Housing to make a **64k Matter Cell**.
4. **Connect the three blocks.** Place Decomposer → Drive → Synthesizer in a touching row. Supply FE to both processors. The Drive needs no power.
5. **Store sand.** Insert the Cell in any Drive bay and put sand in the Decomposer's input. Press **Start** if it is paused. Open the Drive after processing to see the stored silica.
6. **Make glass.** Press **Choose recipe** (or the current recipe name), select **Glass**, confirm with **Use recipe**, then press **Make 1**. Decomposing the sand teaches the Sand & Glass family. Collect the glass from the output.

The default batch turns one sand into enough stored silica for one glass, using 24,000 FE across both machines. It does not multiply material. The same stored feedstock can later serve other explicitly supported products.

Craft the **ReMatter Guide** from a book and Silith for recipes and instructions, including research, Primal Matter and optional progression milestones. Optional **GuideME** adds interactive 3D views. Sneak-use opens the basic book.

## Before you start

Plan this as an **early midgame material workshop**. You need iron, copper, redstone, gold, sand, clay, glass and an external FE source. The starter Drive uses one diamond for its Mint Crystal; the Synthesizer uses three emeralds for its Leaf Crystals. The Hand Preparator starts without FE, but the complete storage line needs powered preparation. Glass teaches the controls; it is not a cheaper replacement for a furnace.

Your next practical goal is **silicon for a storage network**, if a supported silicon-producing mod is installed. In ReForging, one Nether Quartz yields 1,000 crystalline silica; electrolysis turns it into 1,000 silicon and 1,000 oxygen units. The Synthesizer can make **four silicon items**, at 250 units each. This costs **160,000 base FE** across the complete chain. The Chemical Reactor and its components add a diamond investment. Refined Storage can smelt quartz directly and Mekanism can crush sand into silicon; these routes use less equipment. ReMatter trades equipment and FE for flexible recovery and a choice of outputs, rather than replacing every native route. Standalone sand can supply the alternative 136,000-FE route where silicon item profiles exist; ReMatter itself adds no silicon item.

Keep the oxygen for Mekanism processing or explicitly vent new oxygen in **Settings → Production**. Existing gas stock is never vented. [Practical production](src/main/resources/assets/rematter/guides/rematter/guide/production.md) also explains bronze, steel and lava.

## Cells and connected storage

Cells hold **64,000**, **256,000** or **1,000,000 total units**, with up to **512 substance/phase pairs** per Cell. Solids, liquids and gases share capacity; water and water vapor are different types.

The 64k Cell uses one Matrix Crystal and a Control Circuit. The 256k Cell uses a Matrix Cluster, a Control Circuit and two Ender Conductors. The 1M Cell uses four Clusters, an Optical Circuit, a Diamond Lattice, an Ender Conductor and a Netherite Sheath. All tiers use a separately crafted Cell Housing and start empty.

A Matter Drive holds **six individually removable Cells**, arranged in two columns of three. Two three-bay Cell Racks form its assembly. The full-block cabinet sits flush beside adjacent Drives. The front shows the inserted Cells, and connected processors use their shared contents.

The **Cell Transfer Station** has **three source and three target slots**. Insert your Cells, choose **All substances** or select substance icons with the search field, then press **Transfer**. Partitioned targets are filled before unrestricted targets, with the narrowest whitelist first. Only quantities that fit move; sources keep their leftovers. The selection distinguishes substances and phases, with a separate Primal Matter toggle. Ordinary and Primal Cells never mix, and transferring stock grants no research.

Select a numbered Cell button to configure an **AE2-style partition**: accept any ordinary substance, select a whitelist of substance/phase pairs, or partition to the Cell's current contents. An empty whitelist accepts nothing. The policy stays on the Cell and applies in Matter Drives, connected processors and the Station. Existing contents remain preserved and extractable even if you remove them from the whitelist. Unknown stored substances remain intact, and unreadable data stays protected.

Each Cell has a status light: **blue** means empty, **green** means it contains matter with room available, **amber** means its quantity or substance-type capacity is full, and **red** means its contents cannot be read safely. Unreadable contents stay preserved. The same lights appear on mounted Cells, held Cells and inventory items.

For continuous production, use hoppers and chests to feed input and collect output. Allow **Pipes & hoppers** under **Settings → Access** on both processors and enable **Loop On** on the Synthesizer's **Machine** tab.

Multiple Drives can share one network and pool their capacity, up to **64 Drives**. Matter Cables carry the matter network and forward FE that a generator or energy store pushes into them to ReMatter machines. They store no FE, and nothing can pull FE out through a cable. Each powered machine buffers up to **2,400,000 FE**. Native and external input together share an absolute **131,072 FE/t** controller cap. FE routing and matter work have real per-tick limits; repeated calls, extra sides and parallel batches do not create additional allowance. A batch commits only when its complete FE price is available.

| Cable tier | Matter work per active controller | FE routing |
| --- | --- | --- |
| Matter Cable / direct contact | 128 u/t | 2,048 FE/t |
| Reinforced Matter Cable | 1,024 u/t | 16,384 FE/t |
| Resonant Matter Cable | 8,192 u/t | 131,072 FE/t |

Matter work counts the full input and output quantities of each committed batch. The weakest cable on the best usable route determines the tier. Equal routes, extra ports and parallel lanes do not add rates; idle controllers save no credit for later jobs.

The lowest cable tier in a network sets its node limit, counting machines and the cables on the routes between them: **256** for Matter Cable, **384** for Reinforced and **512** for Resonant. Cables belong to their placer. Foreign and unowned cables do not connect to your network. Your cable branches that reach none of your machines do not count toward the routed node limit. A network that outgrows its weakest cable leaves that tier, and whatever only it reaches, out of the network; the stronger part keeps working. A cable system larger than the 4,096-block exploration budget reports that the network is too large. Work waits while part of a network is in an unloaded chunk.

Connect a Matter Terminal at its back to inspect **Storage**, open connected devices from **Machines**, and queue supported production batches. Processing devices use **Machine** and **Settings** tabs; the Analyzer also has **Research**. Expected results appear in a separate **Preview**, beside the real output slot. Stock views in the Terminal, Drives and Network Interfaces have a search field, **Solids**, **Liquids** and **Gases** filters, and **Favorites**. Favorites are stored on each player's client in `config/rematter/stock_favorites.json`, up to 256 per player. A view lists up to 512 types: matches and favorites come first, and a notice shows how many of the stored types are listed.

A stored substance whose definition is missing, for example after a data pack is removed, stays in its Cell with its ID and amount. It is shown as **Unknown substance (ID, phase)** and cannot be processed until its definition returns.

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

Later machines need three advanced components, each mixed as a blend in the Material Preparator and sintered in the Sinter Furnace:

| Component | Blend ingredients | Used in |
| --- | --- | --- |
| Diamond Lattice | Diamond Powder, two Matrix Blend, Gold Dust | Analyzer, Chemical Reactor, upgrades, Optical Circuits |
| Ender Conductor | Ender Pearl Powder, two Lumivex Powder, Copper Dust | Larger Cells, Terminal, Network Interface, Machine Ports |
| Netherite Sheath | Netherite Powder, two Rhevon Powder, Virelite Powder | 1M Cell, Industrial Reactor, Resonant Matter Cable |

The eight coloured Matrix Crystals identify functional roles: Ice for Research, Copper for Breakdown, Leaf for Synthesis, Violet for Reaction, Amber for Preparation, Ember for Heat, Blue for Link and Mint for Storage. Reactors and their modules use Violet; preparation modules use Amber. Installed keys are reusable. Crystal Generator fuel is a separate, consumptive use.

For greater throughput, build a rectangular industrial chamber with **floor and ceiling parts forming one working field**. Outer width and depth each range from **3 to 9 blocks**. The Industrial Preparator needs a footprint of at least **3×4 or 4×3** and a height of **5–9**. The Industrial Reactor starts at **3×3** with a height of **4–9**. Heights include the floor and roof.

Build the edges and corners from Reactor Casing. The Industrial Reactor also uses Casing across its entire floor and roof. Use Glass or Casing on the remaining panels, with at least two Machine Ports away from the edges. Place the controller in the front wall, one block above the base, in any non-edge column. External connections use the Ports' outward faces.

| Machine | Installed hardware | Capability |
| --- | --- | --- |
| Industrial Preparator | Resonance Cutters, Phase Filters and Mixing Basins | Grinding and mixing job capacity; Filters provide Precision |
| Industrial Reactor | Field Stabilizers and Reaction Foci | Stabilizers provide Field; each Focus provides 16,000 u of simultaneous input room |

Fit at least one of every required part directly on the inside floor or under the ceiling; all matching types work on either mounting plane. Clicking a support's top or bottom face automatically orients the fitting; sideways and unsupported placement are rejected. Keep the middle clear. **Empty space adds no capacity.** Special procedures require Field 4 or 8; fine preparation and reclamation require their stated Precision.

Hardware does not multiply global speed or discount FE. Industrial Preparation has a fixed 4× base work rate and twice the declared recipe FE; the Industrial Reactor has a fixed 2× work rate at the original recipe FE. Both controllers have separate **Speed, Efficiency and Parallel** slots. Four Speed Upgrades give five times the base work rate and double base FE. Four Efficiency Upgrades save 20% of base FE before rounding; the absolute saving cap is 25%. Up to three Parallel Upgrades allow four concurrent batches when chamber height, step capacity and input room support them. Each batch retains its exact inputs, outputs and full effective FE charge.

If a shell or module becomes invalid, the controller shows the reason and restarts its unfinished batch once the chamber forms again.

Open an invalid controller and use **Missing part**. Its outline marks the first diagnosed position and remains briefly after closing the menu. The tooltip names the part, coordinates and mounting direction. Repair it and check the next diagnostic.

## Build beyond the first batch

- **Analyzer:** analyse a supported sample without consuming it and learn its exact material family. Sample analysis needs FE but no storage Cell or matter network. Its Research tab also offers paid Derive and Discovery projects.
- **Chemical Reactor:** choose electrolysis, reactions, separation, phase changes or renewable oxygen in one device. Soil supplies carbon for steel; water can be split, recombined, evaporated and condensed. Each complete batch pays its stated FE and retains every byproduct unless explicitly vented.
- **Material Synthesizer:** assemble approved material and crystal outputs. Every diamond form requires a **Pressure Module** in its upgrade slot. Stored liquids also leave chemistry through this machine's tank.
- **Air oxygen:** choose this recipe in either reactor tier, in the Overworld with an unobstructed air inlet and open sky. Compact reactors use the block above; industrial reactors use the space above the front edge of their roof. No nitrogen is produced without a useful consumer.
- **Industrial Reactor:** ordinary chemical processes plus Field-gated industrial procedures in a complete chamber, starting at 3×3×4. Dedicated upgrade slots control speed, efficiency and concurrent jobs. Known-output crystal growth requires prior knowledge and pays the declared matter and FE.

The [material coverage table](docs/material-coverage.md) lists supported profiles and exclusions. **1,000 units is a convention for defined basic forms; the complete batch profile is authoritative.** Silicon uses 250 units per item; water and lava use one unit per mB. Block and nugget ratios follow reviewed recipes rather than a universal mass scale. Small forms use complete batches without rounded fractions. Capacity counts units, not mass.

Synthesized outputs require an explicit material permission. Frames, Circuits, Cells, machines, tools, armor, plates, wires and gears keep their normal crafting paths. Use the preparation chain for the five fractions and Matrix Blend, smelt Crystals and assemble Clusters; these intermediates do not gain chemistry conversion or synthesis permissions. Nether Quartz gems and obsidian are recovery-only **while Ender IO is loaded**. The ReForging integration restores them together with bonus-free native grinding recipes.

## Research and Primal Matter

Research is required by default **in addition to** the synthesis allowlist. Successful decomposition, retained-sample analysis and Industrial Preparator **Assay** teach exact curated families. Assay returns the unchanged sample; multi-item reclamation is a separate preparation operation. Importing a Cell or stored matter does not teach knowledge. Known substances do not automatically unlock every family containing them. JEI and EMI show research status without hiding recipes or expanding permissions.

Knowledge is personal with current-team sharing by default; servers can select a server-wide knowledge view or disable research requirements. These choices never merge ownership, stock or matter networks. The Analyzer can **Derive** an eligible unknown family once all its constituent substances are known, spending FE and 4,000–16,000 u from its explicitly inserted Primal Cell.

**Discovery** is optional endgame research with a hidden random result from the selected tier's eligible unknown families. It requires a Discovery Lens, 32 earned families by default, both industrial milestones and applicable dimension visits. Its three tiers cost 16k/32k/64k Primal with base 2.4M/4.8M/9.6M FE and minimum durations of 5/10/20 minutes. Saved payment steps, pauses and reloads preserve the same hidden candidate order. Exhaustion leaves a claimable Primal refund; spent FE and time are not refunded. Research creates knowledge rather than an item or matter.

Three rare Primal Ores generate only in **new terrain**: deep Overworld Deepslate, Nether Netherrack and outer End islands, outside the central island. One Shard or Silk-Touch ore loads exactly **1,000 u**; Fortune adds none. Separate **16k, 64k and 256k Primal Cells** hold only Primal Matter. Load whole source items or transfer between cells in the inventory. Drives may store both Cell kinds, with separate totals, but Analyzer and Converter payments use only their own payment slot.

The **Primal Converter** spends a finite Primal amount and FE to create known, useful, explicitly curated solid elements in ordinary storage. Search its target list by name or symbol; locked targets explain the required research. The flow display identifies the receiving Drive, Cell bay and free space before starting. A complete output batch must fit one compatible ordinary Cell, including its partition. Common, scarce and invented targets cost 1:1, 2:1 and 4:1 respectively, in output multiples of 250 u. It grants no knowledge, offers no external crafting pattern and never silently fills missing ingredients. Ordinary matter cannot become Primal Matter. Primal Crystal and Discovery Lens preparation require Precision 4.

Two **Entangled Links** can join loaded networks of the same owner within **32 blocks in the same dimension**. Pair them by shift-right-clicking both with a Link item. The pair provides one Basic connection, respects existing budgets and loads no chunks.

## Reuse parts and make power

The Industrial Preparator offers explicit multi-output **reclaim** recipes for coloured crystals and selected workshop parts. Inputs and all outputs must fit as a complete operation. It is not general machine recycling. An empty Hand Preparator can also be crafted into a powered Material Preparator; filled, modified or unreadable portable machines are refused.

The **Crystal Generator** produces up to **64 FE/t** and the **Industrial Crystal Generator** up to **256 FE/t**. Both consume a permitted loose Matrix Crystal for exactly **1,024 FE** in the standalone defaults: plain plus all eight colours have the same yield. Full output pauses intake, and unspent fuel energy remains separate from the buffer across reloads. Primal Cells and Primal Crystals are not fuel. The ReForging data pack disables all nine crystal fuels pending a pack-specific recipe audit; the standalone energy bound covers ReMatter's own recipes only.

## Buffer power and charge equipment

The 0.7 development line includes Energy Cubes inspired by the familiar [Mekanism cube](https://wiki.aidancbrady.com/wiki/Energy_Cube) layout, drawn in ReMatter's graphite machine family.

| Energy Cube | Capacity | Shared input / output rate | Charging slots |
| --- | --- | --- | --- |
| Basic | 4,000,000 FE | 2,048 FE/t | 1 |
| Reinforced | 32,000,000 FE | 16,384 FE/t | 2 |
| Resonant | 256,000,000 FE | 131,072 FE/t | 4 |

Enabled horizontal faces have the same socket and charge display. A Disabled face covers both with plain casing. Copper, turquoise and mauve accents match the corresponding cable tiers; the Ember core keeps its energy identity. Configure each face as **Input**, **Output** or **Disabled**. The front starts as Output; the other faces start as Input. Compatible FE items charge in the dedicated slots. All outputs and charging slots share the tier's output allowance; inputs have their own shared allowance. Energy, side settings and charging items remain with the cube when it is moved. Each tier is crafted independently.

Compact controls use icons with complete mouseover text. Names and formulas remain complete, while exact stored amounts are available on hover.

Device fronts stay clear of connections; Energy Cubes accept connections on all six faces. An open workbench top is also closed to transport. The top of a sealed device starts **Disabled** and can be enabled in **Settings → Sides**. Disabled sides show plain casing. The named side diagram uses the same roles as cables, hoppers and external systems: left-click cycles forward, right-click or Shift-click cycles backward.

## Keep a useful reserve

Select a recipe, then set **Settings → Production → Stock target**. Use zero to disable it and stop target-driven production. Matter-producing processes count the selected substance and phase across the connected ReMatter network. Item and fluid production counts only the local output buffer, never a chest, AE2 or RS stock. The server reports the scope and exact current count, including in Jade.

The target is a hard ceiling: a complete batch that would exceed it waits. Choose a target divisible by the batch yield. Producers on the same stock share reservations for inputs and planned outputs. Manual and network crafting requests take priority. Gas byproducts need room unless their newly produced quantities are explicitly vented; gas venting starts off and requires confirmation. Network crafting returns all contracted outputs and does not vent them.

On selectable processors, **Make 1** requests one displayed batch; **Queue +1** adds a finite batch. **Loop Off / Loop On** toggles continuous production on the Machine tab. **Pause** keeps the current order, and **Resume** continues it. Selecting another recipe cancels the old request and waits for a new instruction.

Inputs, stock and FE are consumed only when a complete batch commits. If a Cell or Drive changes, a device disconnects or a chamber breaks during a batch, the machine restarts that unfinished batch and continues on its own once its network is valid again; requested and repeating batches are kept. Cable changes that leave every device connected do not interrupt work.

Filled machines and Cells retain their contents when carried. Working mechanisms ease into motion and slow to a stop independently of recipe progress updates. Bench status lights use the device hue while working, breathe blue while a loaded job waits, and go dark when idle; the menu explains the waiting reason. Energy Cube core pulses travel upward while charging and downward while discharging.

## Multiplayer

- The player who places a machine owns it. A mined machine keeps its contents but no owner; whoever places it next owns it.
- Operators (permission level 2 or higher) can open and break any machine.
- Automated fake players act on a machine only when they carry its owner's UUID. A machine that a fake player places belongs to the UUID it carries. A machine placed without any player, for example by a command, belongs to the first real player who uses it.
- A matter network includes only one owner's machines. Other players' machines on the same cables are not part of it and never share its stock.
- An industrial chamber never forms over any block of a chamber that another owner formed first, and a port that two owners' chambers could use stays with the chamber that already claims it. Only a formed chamber or the claim it leaves protects its parts.

Casing, Glass, Ports and modules of a formed chamber can only be broken by the controller's owner or an operator. Cables follow the same ownership rule. Pistons cannot move protected parts or cables, and explosions spare owned machines, owned cables and claimed chamber parts. FE pushed into a cable reaches only machines and Machine Ports belonging to that cable's owner; other mods' blocks receive none. Unowned cables connect nothing.

With a supported **FTB Teams** installation, current team members can operate devices and configure ordinary production. Ownership remains with the placer: removal, gas-disposal changes and recovery of held deliveries require the owner or an operator. Leaving a team immediately removes shared access. Team membership does not merge matter networks or grant the owner's advancements. Pack authors can gate a synthesis permission or process with `requires_advancement`; offline operation uses a server-side mirror of the owner's completed advancements.

## Optional integrations

ReMatter remains standalone. Its optional [integrations](docs/integrations.md) add recipe displays with one JEI or EMI category per process type, a machine HUD, illustrated guides, chemical transfers, and AE2/Refined Storage crafting requests through a **Network Interface**. To use both crafting networks at once, connect two interfaces to the same ReMatter network: one set to AE2 and the other to Refined Storage.

[LatroX's Xtreme - Tech ReForging 0.10.0](integrations/reforging/README.md) is the reference pack integration. Its separate data pack supplies quartz/silicon progression and machine-copying rules for coexistence with Replication and Equivox. Those rules are not activated by installing ReMatter.

## Build

Install JDK **25**, then run:

```sh
./gradlew build
```

The mod JAR is written to `build/libs/`. On Windows, use `gradlew.bat build`. The [tools](tools/README.md) regenerate the material data and textures and check them.

Pack authors can add material profiles, synthesis permissions and reactions for their mods using [chemistry data packs](docs/data-packs.md). Start with the [copyable example pack](examples/chemistry-datapack/).

Blocks, items, textures and screens follow the [visual style guide](docs/visual-style.md); `python3 tools/check_visual_style.py` checks them against it.

## Feedback

Report bugs and suggest improvements through [Issues](https://github.com/LatroX-dev/ReMatter/issues/new/choose). English and German are welcome. Include the mod version or commit, Minecraft and NeoForge versions, reproduction steps, and relevant logs with personal information removed.

## License

Code, data and documentation are **LGPL-3.0-only**; see [COPYING.LESSER](COPYING.LESSER) and [COPYING](COPYING). Textures, models, blockstates, item model definitions and artwork are **All Rights Reserved**, © LatroX; see [LICENSE-ASSETS.md](LICENSE-ASSETS.md).
