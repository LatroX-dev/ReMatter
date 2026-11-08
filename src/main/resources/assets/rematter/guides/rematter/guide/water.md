---
navigation:
  title: Water and gases
  icon: rematter:electrolyzer
  parent: index.md
  position: 10
item_ids:
  - rematter:electrolyzer
  - rematter:chemical_reactor
---
# Water and gases

Add a Matter Drive with a Cell and connect the Electrolyzer and Chemical Reactor to it. Keep the Cell in the Drive; processors use its shared stock. Supply FE separately to each processing machine. Liquids and gases remain distinct stored quantities, without chemical inventory items.

## 1. Store water

Use a water bucket on the Decomposer. For a pipe, open **Configuration** and enable **Automation**. Leave its item input empty to process the tank.

Each default operation converts **1,000 mB of water into 1,000 stored water units**, using FE. Press **Start** on the Decomposer if it is paused. It then processes supplied water continuously. The bucket itself is not decomposed.

## 2. Split water

In a powered <ItemLink id="rematter:electrolyzer" />, press **Choose recipe** (or **Recipe** to change the selection) and find **Water electrolysis**. Check the requirements, confirm with **Use recipe**, then press **Start** for one batch.

**1,000 water units -> 1,000 hydrogen units + 500 oxygen units**

The gases enter the same Cell. Their quantity ratio is **2 hydrogen to 1 oxygen**. These units describe chemical quantities, not equal masses.

<RecipeFor id="rematter:electrolyzer" />

## 3. Recombine the gases

In a powered <ItemLink id="rematter:chemical_reactor" />, open **Choose recipe** or **Recipe**, choose **Water synthesis**, confirm with **Use recipe**, then press **Start**. It consumes 1,000 hydrogen units and 500 oxygen units to restore 1,000 water units.

<RecipeFor id="rematter:chemical_reactor" />

## 4. Export water

In the Material Synthesizer, open **Choose recipe** or **Recipe** and select **Water**. Confirm with **Use recipe** and press **Start**. One default batch consumes 1,000 stored water units and FE, then produces **1,000 mB** in its tank.

Use an empty bucket to collect the water. A fluid pipe needs **Automation** enabled in **Configuration**. The tank holds 16,000 mB of one fluid at a time.

Use one **Start** request at each stage for this experiment. **Configuration → Run nonstop** is for continuing production; **Stop repeat** stops it. Each stage consumes FE; the complete loop does not generate free power.

## Other fluids

Water and lava are available in the base catalogue. Optional mod integrations add explicitly supported fluids. Check the product list and recipe preview for the exact substance quantities and batch volume; a fluid name alone never grants a conversion.

<OptionalSection modId="mekanism">

## Mekanism chemical pipes

Enable **Automation** in the Drive’s **Configuration**, then connect a compatible Mekanism **Pressurized Tube**. Supported chemicals transfer in both directions through explicit mappings.

Hydrogen, oxygen and water vapor use one stored unit per chemical unit. Water vapor is the gas phase of water. Other mapped chemicals can have different ratios; they are not interchangeable by name or formula. Ordinary fluid pipes do not transport the stored gas quantities.

A chemical leaves the Drive only when the server permits its export. The twelve mapped chemicals, including lithium and osmium, are permitted by default. ReMatter stores every Mekanism chemical in the gas phase, even lithium and osmium; that phase marks the chemical form and keeps it apart from solid dusts and ingots.

</OptionalSection>
