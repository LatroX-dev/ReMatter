# Material coverage

ReMatter uses reviewed, exact item IDs. The catalogue is maintained in [material_catalogue.json](../tools/data/material_catalogue.json); [generate_material_data.py](../tools/generate_material_data.py) reproduces the shipped item and fluid profiles, their separate synthesis permissions, the gas permissions, this page and its list. Tags help investigate candidates but never grant support at runtime.

{{summary}}

## Quantities and material states

- One ordinary ingot or full dust uses a material batch of 1,000 units. Nine nuggets form one exact batch; individual nuggets are never rounded to 111 or 112 units. Every material family declares one exact batch, and each profile must hold a whole multiple of it. Where a recipe divides a constituent unevenly, the profile covers several items instead of rounding.
- Nine Ender IO Ender Pearl Powder fragments contain the same constituents as one pearl. ReMatter's full powder and AE2 Ender Dust each contain a whole pearl's constituents. A JAOPCA pearl powder block can be made from nine tag ingredients of either size. ReMatter recovers one pearl equivalent and leaves its synthesis disabled.
- Four obsidian dusts contain the same silica and alumina as one natural obsidian block. A JAOPCA obsidian powder block contains nine dusts.
- Compressed blocks use their reviewed recipe quantities. Quartz, Certus Quartz and uncharged Ember blocks use four crystals; ordinary metal storage blocks use nine ingots. JAOPCA powder blocks use their verified nine-powder recipe, not a ratio inferred from their name.
- Ores and raw metals are decomposition inputs only. Conservative recovery does not promise the yield of an external ore-processing chain.
- Crystalline silica remains distinct from sand and amorphous silica. Diamond and graphite are distinct carbon allotropes. No automatic conversion between these states is added.
- FTB Industrial Contraptions separates one Nether Quartz into three Silicon Gems. Its gems share the silicon ingredient tag with other mods. Oritech yields four silicon per sand with Silicon Wash, and ExtendedAE makes 24 from a blend batch containing six sand and additional quartz. Recovery uses 250 elemental silicon per interchangeable piece and 2,250 per nine-piece block. FTBIC's gem still requires its native Separator.
- Refined Obsidian dust retains the obsidian and diamond constituents of its infusion recipe. Refined Glowstone and Refined Obsidian ingots still require Mekanism compression. Decomposition recovers the constituents of the corresponding dust without recovering the machine's osmium reagent. Mystical essences and gemstones retain their tier identities.
- Uncharged Ember Crystal, corrupted Arcane Crystal, Stellarite and Dimensional Shard retain separate identities. Occultism's Gambler can produce Spirit-Attuned Gems from ordinary gems, so attuned gems and blocks are excluded from diamond recovery.
- Burnt Otherrock, Burnt Otherstone and Otherworld Ashes retain their processed states; they are not aliases for unburnt rock, logs or living wood.
- AppFlux insulating resin recovers the Glowstone constituents common to the uncured and hardened forms. ReMatter models Oritech's finished plastic as polyethylene, `(C₂H₄)ₙ`. Each sheet recovers carbon and molecular hydrogen. The block recovers nine sheets' worth. Resin recovers the plastic fraction of its native one-sheet furnace route. These profiles are for decomposition.
- Natural ice, packed ice and blue ice recover solid water. Each block equals one corresponding crushed item, and JAOPCA powder storage blocks contain nine. FTBIC and Refurbished Furniture compress both grades at 1:1, so recovery follows that lower bound. Native recipes produce packed ice and blue ice.
- ReMatter's diamond, emerald, lapis, amethyst and netherite powders recover the same amounts as their one-to-one grinding inputs.
- Netherite scrap and scrap dust both contain Netherium (`Nt`), a fictional constituent. Native routes make a Netherite Ingot from as little as one gold ingot and one scrap or one eighth of a gold dust and four scrap, so an ingot or dust recovers the smallest share of each, 500 gold and 1,000 Netherium, and none of them can be synthesized. Scrap storage blocks contain nine scrap batches. Ancient Debris provides one scrap batch through decomposition.
- Prismarine shards and crystals retain separate identities. Natural Prismarine contains four shards; the JAOPCA crystal block contains nine crystals. Ecologics Seashell Blocks contain nine shells; decorative shell tiles are outside this catalogue.
- Coal, charcoal, graphite, uraninite and combustible candidates remain decomposition-only until their complete energy cycles have been reviewed. Nuclear fuel rods and isotope mixtures are outside these generic material profiles.
- Nondefault item components are rejected. Profiles do not authorize consuming stored energy, inventories, patterns, enchantments or custom item data. Machines and manufactured components are not synthesis outputs.

## Alloys, compounds and mixtures

Reviewed alloys use the ingredients and output counts of the reference mod recipes. Where shared tags allow brands to be exchanged, recovery uses the smallest verified common quantities. Four Bronze Ingots recover exactly 3,000 copper and 125 tin, and three Invar recover 1,500 iron and 1,000 nickel; such alloys decompose in whole batches. Where routes disagree element by element, as for Invar and Netherite, recovery keeps the smaller amount of each element and synthesis stays disabled. Oritech's lowest-cost Adamant recipe uses one nickel ingot and one diamond. Recovery-only profiles keep native production required when routes consume different ingredients or when the full composition cannot be verified. The catalogue records the recipe basis or identifies a native mineral's composition as a ReMatter model.

The explicit synthesis list accounts for progression and equivalent constituent recipes. Finished Rhevon, Lumivex and Virelite require firing. Mystical Agriculture alloys and gemstones recover their actual base and same-tier ingredients. Their distinct essence grades preserve native upgrading. Thaumaturge Void Metal stays outside material recovery until its seed and aspect chemistry has a supported model.

Certus Quartz uses silica chemistry and retains its crystal structure. Fluix recovery yields 250 units of Certus per crystal or dust. AdvancedAE and Applied Generators produce batches of 64 from 16 charged Certus, and their duplication routes omit redstone and Nether Quartz.

Stone, cobblestone, deepslate, andesite, diorite, granite, tuff, basalt and blackstone share silica, alumina and calcium oxide. Their proportions are simplified gameplay compositions. Sandstone uses the same amount of silica as its four sand inputs. The mineral blend process supplies the solid feedstock for lava from the same three ingredients.

End Stone adds a small amount of Enderion (`Edr`) to silica and calcium oxide. Ten blocks provide the Enderion required by one Ender Pearl. Netherrack adds Cinderium (`Cdr`) to silica and alumina. Fifty blocks supply the Cinderium for one Glowstone Dust. Redstone contains silica and Arcane Essence. Calcite yields calcium oxide and carbon dioxide.

Lapis Lazuli uses a simplified lazurite model with sodium, calcium, aluminum, silicon, oxygen and sulfur. Real lapis lazuli contains several minerals with variable proportions.

A common tag or atom formula alone does not grant synthesis or make distinct compounds, allotropes or crystals interchangeable.

## Item profiles by namespace

Counts below describe the shipped exact-ID catalogue. Optional profiles load only with their owning mod; dynamically registered JAOPCA items also require their exact item to exist.

{{namespace_table}}

The complete list of item, fluid and gas entries is [material-coverage.csv](material-coverage.csv). Its `batch` column counts items for item profiles and millibuckets for fluid profiles. A zero synthesis count still permits analysis and supported decomposition.

## Fluids and gases

Fluid profiles use exact source-fluid IDs and explicit amount ratios. Flowing variants are not additional resources. Gas permissions allow a stored gas to leave through a chemical adapter; see [Integrations](integrations.md) for chemical-tube support and the distinction between external fluids and gases.

{{fluids}}

## Boundaries and remaining work

This is a material catalogue, not complete chemical coverage of every item in a modpack. Unsupported items are rejected; they are never converted to a guessed fallback substance. In particular:

- Charged Certus Quartz and processed Redstone Crystal require explicit state-preserving treatment. They are not treated as ordinary uncharged crystals.
- FTB Industrial Contraptions plutonium blocks and raw plutonium blocks still need a verified compression quantity; their simpler supported forms do not establish a block ratio by themselves.
- Tools, armor, machines, inventory containers, storage cells, data patterns, artifacts and manufactured parts need their own safe recycling adapters and reviewed compositions. No generic recipe inversion is enabled.
- Foods, crops, wood species, biological products and decorative building products are not assigned a universal biomass or stone substitute. Their processing and quantity chains remain a separate coverage task.
- Other registered fluids, fuels, potion mixtures, experience fluids, molten magical materials and nuclear isotope chains need explicit identities, units and cycle reviews.
- Native integration checks and pack balancing are separate from data generation. A parsed profile alone is not evidence of a completed gameplay or balance test.

For a new material request, include its exact item or fluid ID, mod version, the expected material form, relevant manufacturing and recycling recipes, and any stored data. Report it through the repository's "Material coverage gap" issue template.
