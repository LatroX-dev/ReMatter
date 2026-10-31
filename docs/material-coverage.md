# Material coverage

ReMatter uses reviewed, exact item IDs. The catalogue is maintained in [material_catalogue.json](../tools/data/material_catalogue.json); [generate_material_data.py](../tools/generate_material_data.py) reproduces the shipped item profiles and their separate synthesis permissions. Tags help investigate candidates but never grant support at runtime.

The data catalogue contains **950 item profiles** across **33 namespaces**, with **629 explicit synthesis outputs**. The built-in starter definitions also provide the original stone, dirt, sand, water and lava routes. Installing a supported mod does not imply that every item from it is supported.

## Quantities and material states

- One ordinary ingot or full dust uses 1,000 formula units. Nine nuggets form one exact batch of 1,000 units; individual nuggets are never rounded to 111 or 112 units.
- Ender IO Ender Pearl Powder uses a nine-item batch, matching the nine fragments produced from one pearl. AE2 Ender Dust remains a full material unit.
- Obsidian dust contains 250 units: four dusts equal one natural obsidian block. Its JAOPCA powder block contains nine dusts (2,250 units). A JAOPCA Ender Pearl Powder block contains nine fragments (1,000 units).
- Compressed blocks use their reviewed recipe quantities. Quartz, Certus Quartz and uncharged Ember blocks use four crystals; ordinary metal storage blocks use nine ingots. JAOPCA powder blocks use their verified nine-powder recipe, not a ratio inferred from their name.
- Ores and raw metals are decomposition inputs only. Conservative recovery does not promise the yield of an external ore-processing chain.
- Crystalline silica remains distinct from sand and amorphous silica. Diamond and graphite are distinct carbon allotropes. No automatic conversion between these states is added.
- FTB Industrial Contraptions Crystalline Silicon remains distinct from ordinary silicon; no free purity upgrade is provided.
- Refined Obsidian precursor dust remains distinct from the osmium-compressed ingot. Mystical material essences, infused metals and gemstones retain separate identities for every tier.
- Uncharged Ember Crystal, corrupted Arcane Crystal, Stellarite, Spirit-Attuned Gem and Dimensional Shard retain separate identities. No charged crystals or manufactured attuned-crystal components are added.
- Burnt Otherrock, Burnt Otherstone and Otherworld Ashes retain their processed states; they are not aliases for unburnt rock, logs or living wood.
- Uncured AppFlux resin, hardened insulating resin, Oritech polymer resin and processed plastic are separate materials. The compacted plastic block is decomposition-only because unpacking it produces manufactured plastic sheets.
- Natural ice, packed ice and blue ice use three retained H₂O identities, separate from stored water and its phase-change recipes. Each block equals one corresponding crushed item; JAOPCA powder storage blocks contain nine. External mods disagree on ice compression yields, so these states have no automatic ReMatter conversion.
- Netherite scrap and scrap dust are equal unalloyed material units, separate from finished Netherite. Scrap storage blocks contain nine units; Ancient Debris has conservative one-scrap recovery and cannot be synthesized.
- Prismarine shards and crystals retain separate identities. Natural Prismarine contains four shards; the JAOPCA crystal block contains nine crystals. Ecologics Seashell Blocks contain nine shells; decorative shell tiles are outside this catalogue.
- Coal, charcoal, graphite, uraninite and combustible candidates remain decomposition-only until their complete energy cycles have been reviewed. Nuclear fuel rods and isotope mixtures are outside these generic material profiles.
- Nondefault item components are rejected. Profiles do not authorize consuming stored energy, inventories, patterns, enchantments or custom item data. Machines and manufactured components are not synthesis outputs.

## Retained compounds and mixtures

Named minerals, alloys and fantasy materials retain their own substance IDs. Their compositions document the material model; they do not automatically enable elemental separation. This matters when installed mods provide conflicting recipes for the same named alloy. Real-mineral formulas are simplified end members rather than measured purity. Fictional materials use explicitly fictional conserved constituents, shared across related material families.

The following alloy families are retained without an elemental separation recipe:

**Advanced Alloy**, **Brass**, **Bronze**, **Conductive Alloy**, **Constantan**, **Dark Steel**, **Electrum**, **End Steel**, **Enderium**, **Energetic Alloy**, **Invar**, **Lumium**, **Pulsating Alloy**, **Radiant Alloy**, **Redstone Alloy**, **Refined Glowstone**, **Refined Obsidian**, **Refined Obsidian Precursor**, **Riftmetal**, **Signalum**, **Soularium**, **Stainless Steel**, **Steel**, **Tough Alloy**, **Vibrant Alloy**.

Other retained crystal, magical and geological identities are listed with their compositions and design basis in the catalogue. Neither a common tag nor a shared atom formula makes two such identities interchangeable.

## Item profiles by namespace

Counts below describe the shipped exact-ID catalogue. Optional profiles load only with their owning mod; dynamically registered JAOPCA items also require their exact item to exist.

| Namespace | Profiles | Synthesis outputs |
| --- | ---: | ---: |
| `ae2` | 9 | 9 |
| `ae2omnicells` | 1 | 1 |
| `alltheores` | 205 | 100 |
| `appflux` | 4 | 4 |
| `appgen` | 6 | 6 |
| `aquaculture` | 3 | 3 |
| `colossal_reactors` | 36 | 18 |
| `ecologics` | 2 | 2 |
| `enderio` | 33 | 32 |
| `energizedpower` | 20 | 15 |
| `extendedae` | 4 | 4 |
| `forbidden_arcanus` | 17 | 14 |
| `ftbic` | 84 | 53 |
| `hextale` | 18 | 14 |
| `infinity_nexus_mod` | 78 | 51 |
| `jaopca` | 38 | 36 |
| `justdirethings` | 7 | 3 |
| `mekanism` | 67 | 46 |
| `mekanism_extras` | 8 | 4 |
| `mekmm` | 8 | 4 |
| `minecraft` | 76 | 48 |
| `mysticalagradditions` | 11 | 7 |
| `mysticalagriculture` | 61 | 56 |
| `nautec` | 4 | 4 |
| `occultism` | 38 | 31 |
| `oritech` | 52 | 39 |
| `powah` | 9 | 0 |
| `refinedstorage` | 1 | 1 |
| `regions_unexplored` | 1 | 0 |
| `replication` | 5 | 2 |
| `rftoolsbase` | 4 | 1 |
| `stellaris` | 19 | 7 |
| `thaumaturge` | 21 | 14 |

The complete per-item list is [material-coverage.csv](material-coverage.csv). A zero synthesis count still permits analysis and supported decomposition.

## Fluids and gases

Water and lava retain the original explicit conversions. Ten additional Mekanism fluid profiles cover hydrogen, oxygen, chlorine, sulfur dioxide, sulfur trioxide, sulfuric acid, hydrogen chloride, hydrofluoric acid, ethene and lithium. These use exact source-fluid IDs and explicit amount ratios. Flowing variants are not additional resources. See [Integrations](integrations.md) for chemical-tube support and the distinction between external fluids and gases.

## Boundaries and remaining work

This is a material catalogue, not complete chemical coverage of every item in a modpack. Unsupported items are rejected; they are never converted to a guessed fallback substance. In particular:

- Charged Certus Quartz and processed Redstone Crystal require explicit state-preserving treatment. They are not treated as ordinary uncharged crystals.
- FTB Industrial Contraptions plutonium blocks and raw plutonium blocks still need a verified compression quantity; their simpler supported forms do not establish a block ratio by themselves.
- Tools, armor, machines, inventory containers, storage cells, data patterns, artifacts and manufactured parts need their own safe recycling adapters and reviewed compositions. No generic recipe inversion is enabled.
- Foods, crops, wood species, biological products and decorative building products are not assigned a universal biomass or stone substitute. Their processing and quantity chains remain a separate coverage task.
- Other registered fluids, fuels, potion mixtures, experience fluids, molten magical materials and nuclear isotope chains need explicit identities, units and cycle reviews.
- Native integration checks and pack balancing are separate from data generation. A parsed profile alone is not evidence of a completed gameplay or balance test.

For a new material request, include its exact item or fluid ID, mod version, the expected material form, relevant manufacturing and recycling recipes, and any stored data. Report it through the repository material-coverage issue template.
