---
navigation:
  title: Water and gases
  icon: rematter:electrolyzer
  parent: index.md
  position: 4
item_ids:
  - rematter:electrolyzer
  - rematter:chemical_reactor
---
# Water and gases

Liquids and gases are stored as substance quantities, with their phases kept separate. The starter water cycle demonstrates both.

## 1. Store water

Use a water bucket on a Decomposer, or enable its Automation setting and fill it from a fluid pipe. Leave its item input empty so that it processes the tank. Each operation converts **1,000 mB of water into 1,000 stored water units**, using FE. The bucket is a container and is not decomposed with its contents.

## 2. Split water

Connect and power an <ItemLink id="rematter:electrolyzer" />. Select **Water electrolysis** with the recipe arrows.

**1,000 water units -> 1,000 hydrogen units + 500 oxygen units**

The gases enter the same Matter Storage. Their quantity ratio is **2 hydrogen to 1 oxygen**. Matter quantity is a chemical accounting unit; the sum of gas amounts is not a mass measurement. No element items or gas buckets are created.

<RecipeFor id="rematter:electrolyzer" />

## 3. Recombine the gases

In a powered <ItemLink id="rematter:chemical_reactor" />, select **Water synthesis**. It consumes 1,000 hydrogen units and 500 oxygen units to restore 1,000 water units. The reactor also offers supported phase changes and other non-electrolysis processes.

<RecipeFor id="rematter:chemical_reactor" />

## 4. Export liquid water

Select **Water** in the Material Synthesizer. It consumes 1,000 stored water units and FE, then adds **1,000 mB** to its internal tank. Use an empty bucket on it, or enable Automation to extract the water with a fluid pipe. Its tank holds 16,000 mB of one supported fluid at a time.

The basic fluid connection supports water and lava. Stored hydrogen and oxygen cannot travel through ordinary fluid pipes: they need an explicit chemical integration. Reversing the water cycle still consumes FE at each processing stage.

<OptionalSection modId="mekanism">

## Mekanism chemical pipes

Enable **Automation on Matter Storage** and connect a Mekanism **Pressurized Tube**. The adapter transfers hydrogen, oxygen and water vapor in both directions. One Mekanism chemical unit equals one matching stored matter unit. In ReMatter, water vapor is the gas phase of water.

Only these exact chemicals are mapped. Other gases and similarly named chemicals are not substituted automatically. Ordinary fluid pipes remain separate from this chemical connection.

</OptionalSection>
