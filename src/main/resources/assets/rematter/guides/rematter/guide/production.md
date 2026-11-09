---
navigation:
  title: Useful production
  icon: rematter:electrolyzer
  parent: index.md
  position: 3
---
# Build for something you need

Glass teaches storage and recipe selection. A furnace already makes glass cheaply. Use ReMatter's next steps to recover useful materials, handle byproducts and choose between competing products.

## Silicon for your storage network

ReMatter adds no silicon item. This route needs a supported mod such as AE2, Refined Storage, Oritech or Energized Power.

In **ReForging**, decompose one Nether Quartz into **1,000 crystalline silica**, then run **Silica electrolysis** in the Electrolyzer. You receive **1,000 silicon and 1,000 oxygen units**. Select the installed mod's silicon item in the Synthesizer: each costs **250 silicon units**, so the original quartz makes **four items**.

The complete route costs **160,000 base FE**: 32,000 for quartz decomposition, 64,000 for electrolysis and 4 × 16,000 for synthesis. Refined Storage can smelt quartz directly, and Mekanism can crush sand into silicon. These routes use less equipment. The chemical route offers four items per quartz, shared material stock and an oxygen byproduct; it is not automatically the cheapest silicon source. The Electrolyzer requires advanced preparation and additional diamonds. Check its recipe before investing.

Without ReForging's progression rules, sand-derived silica can feed the standalone process where silicon item profiles exist: **136,000 base FE** for four silicon. ReForging keeps crystalline and ordinary silica separate, so sand cannot replace quartz in this chain.

<OptionalSection modId="mekanism">

Keep the oxygen for Mekanism's **Purification Chamber**. Enable Automation on the Matter Drive and use a Pressurized Tube; the exact oxygen transfer ratio is **1:1**. This is a chemical connection, not an ordinary fluid pipe.

</OptionalSection>

If you have no oxygen consumer, select **Vent oxygen: On** under the Electrolyzer's **Production settings** and confirm. Only newly produced oxygen is discarded. Previously stored oxygen stays in its Cell. Venting begins off; network crafting always retains its contracted byproducts.

## Bronze from available metal

Supported bronze items use the conservative common composition from their interchangeable recipes: **3,000 copper + 125 tin units make four bronze ingots**. Recover tin from supported tin materials and copper from ordinary copper. Choose an installed mod's bronze output in the Synthesizer. Check the exact batch rather than assuming a universal one-ingot quantity.

## Steel from soil and iron

Decompose dirt, then run **Soil separation** in a Separator. A batch yields carbon, silica and water. Use **1,000 iron + 10 carbon units** per supported steel ingot in the Synthesizer. This gives soil a useful role while the silica can make glass and the water can be exported as fluid.

Byproducts need storage room. An incomplete result never consumes part of the batch. Water remains a stored liquid until the Synthesizer exports it; the Separator itself has no water output pipe.

## Lava without a Nether trip

Use the complete chain:

**Cobblestone → Decomposer → Mineral blending in Reactor → Mineral melting in Thermal Processor → Lava in Synthesizer.**

One full route to **1,000 mB** costs **192,000 base FE**: 8,000 + 32,000 + 128,000 + 24,000. Supply the final Synthesizer, select Lava, and collect it from its tank or enable fluid automation. The Thermal Processor creates stored liquid; it does not skip the final export cost. This is a material supply route, not a power generator.

## Keep a reserve

Select a recipe and open **Configuration → Production settings**. Set a target divisible by its yield. Zero disables target-driven production. A process counts its selected substance and phase across the connected ReMatter network; a physical producer counts only its own item or fluid output buffer.

The target is a hard ceiling. A 1,000-unit batch will not fit under a target of 1,500 after the first batch. Several producers share planned outputs and inputs, so they cannot each promise the same stock. Manual requests and native crafting jobs have priority. AE2, RS, chests and external tanks are outside this stock count.
