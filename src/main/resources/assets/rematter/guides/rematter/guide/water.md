---
navigation:
  title: Water and gases
  icon: rematter:electrolyzer
  parent: index.md
  position: 7
item_ids:
  - rematter:electrolyzer
  - rematter:chemical_reactor
---
# Water and gases

Add a Matter Drive with a Cell and connect the Electrolyzer and Chemical Reactor to it. For network input and output, leave the Decomposer and Synthesizer’s local Cell slots empty. Supply FE separately to each processing machine. Liquids and gases remain distinct stored quantities, without chemical inventory items.

## 1. Store water

Use a water bucket on the Decomposer. For a pipe, open **Settings** and enable **Automation**. Leave its item input empty to process the tank.

Each default operation converts **1,000 mB of water into 1,000 stored water units**, using FE. After changing a Cell or source, press **Start** on the Decomposer. It then processes supplied water continuously. The bucket itself is not decomposed.

## 2. Split water

In a powered <ItemLink id="rematter:electrolyzer" />, press **Choose process** and find **Water electrolysis**. Check the requirements, confirm with **Use recipe**, then press **Run once**.

**1,000 water units -> 1,000 hydrogen units + 500 oxygen units**

The gases enter the same Cell. Their quantity ratio is **2 hydrogen to 1 oxygen**. These units describe chemical quantities, not equal masses.

<RecipeFor id="rematter:electrolyzer" />

## 3. Recombine the gases

In a powered <ItemLink id="rematter:chemical_reactor" />, choose **Water synthesis**, confirm with **Use recipe**, then **Run once**. It consumes 1,000 hydrogen units and 500 oxygen units to restore 1,000 water units.

<RecipeFor id="rematter:chemical_reactor" />

## 4. Export water

In the Material Synthesizer, press **Choose product** and select **Water**. Confirm the recipe and press **Make once**. One default batch consumes 1,000 stored water units and FE, then produces **1,000 mB** in its tank.

Use an empty bucket to collect the water. A fluid pipe needs **Automation** enabled in **Settings**. The tank holds 16,000 mB of one fluid at a time.

Keep **Repeat: off** for this experiment. Use Repeat only for continuing production. Each stage consumes FE; the complete loop does not generate free power.

## Other fluids

Water and lava are available in the base catalogue. Optional mod integrations add explicitly supported fluids. Check the product list and recipe preview for the exact substance quantities and batch volume; a fluid name alone never grants a conversion.

<OptionalSection modId="mekanism">

## Mekanism chemical pipes

Enable **Automation** in the Drive’s **Settings** (or an original Matter Storage), then connect a compatible Mekanism **Pressurized Tube**. Supported chemicals transfer in both directions through explicit mappings.

Hydrogen, oxygen and water vapor use one stored unit per chemical unit. Water vapor is the gas phase of water. Other mapped chemicals can have different ratios; they are not interchangeable by name or formula. Ordinary fluid pipes do not transport the stored gas quantities.

</OptionalSection>
