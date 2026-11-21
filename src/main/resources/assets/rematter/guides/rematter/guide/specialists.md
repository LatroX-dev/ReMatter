---
navigation:
  title: Chemical processes
  icon: rematter:chemical_processor
  parent: index.md
  position: 11
item_ids:
  - rematter:atmospheric_collector
  - rematter:chemical_processor
  - rematter:pressure_module
---
# One processor, several processes

Connect a **Chemical Processor** to your Matter Drive, install a readable Cell and provide external FE. Choose electrolysis, reactions, separation, heating, cooling or Air Separation in its recipe list. The **Chemical Reactor** runs the same chemical processes in an expandable chamber. The list shows every input, output, FE and duration before you start.

<GameScene zoom="3" interactive={true}>
  <Block x="0" y="1" id="rematter:chemical_processor" />
  <Block x="1" y="1" id="rematter:matter_drive" />
  <Block x="2" y="1" id="rematter:material_synthesizer" />
  <BlockAnnotation x="0" y="1" color="#bba4f2">Chemical Processor: choose a chemical process or Air Separation.</BlockAnnotation>
  <BlockAnnotation x="2" y="1" color="#bba4f2">Synthesizer: approved physical outputs; diamonds require a Pressure Module.</BlockAnnotation>
</GameScene>

## Recover useful byproducts

**Soil separation** supplies carbon for steel, silica for glass and water. **Water electrolysis** supplies hydrogen and oxygen; **Water synthesis** recombines them. Every byproduct requires storage room. A full store pauses the whole batch before partial consumption.

## Heat and cool

Choose evaporation, condensation, mineral melting or solidification in the same reactor. The displayed phases distinguish heating from cooling. Both directions require FE. Follow [Useful production](production.md) through the final Synthesizer: melting stored material does not create a filled lava bucket.

<RecipeFor id="rematter:chemical_processor" />

## Crystal outputs

Use the **Material Synthesizer** for explicitly approved crystal-family products. Install **one Pressure Module** in its dedicated Pressure slot beside the upgrade slot for tier 2, required for **every diamond form**, including native AE2/RS crafting patterns. Speed or Efficiency Upgrades stay available in the main upgrade slot at the same time. Ender Pearls and other permitted materials remain Synthesizer products.

<RecipeFor id="rematter:pressure_module" />

## Atmospheric collection

Choose an environmental source on a Chemical Processor or Chemical Reactor. The **Atmospheric Collector** runs the same sources at twice the speed and half the base FE. Connect a Matter Drive with room in a Cell for every output. For a source marked **Open sky**, keep the intake above the machine clear and exposed to the sky.

### Default collection sources

| Source | Dimension | Intake | Gas units per batch |
| --- | --- | --- | --- |
| Air Separation | minecraft:overworld | Open sky | 200 Oxygen |
| Atmospheric CO2 capture | minecraft:overworld | Open sky | 150 Oxygen, 50 Carbon Dioxide |
| Subterranean vapor condensation | minecraft:overworld | Covered or open | 250 Water Vapor, 50 Carbon Dioxide |
| Void atmosphere intake | minecraft:the_end | Open sky | 150 Hydrogen, 50 Oxygen |
| Nether fume extraction | minecraft:the_nether | Covered or open | 150 Sulfur Dioxide, 100 Carbon Dioxide |

### Using the collected gas

<RecipeFor id="rematter:atmospheric_collector" />

Oxygen and other non-reserved gases can feed Mekanism machines through a Drive with Automation enabled and a Pressurized Tube, or via a **Pressurized Canister**. Renewable atmosphere collection remains an explicit environmental source with power and storage costs.

Gas venting starts off. An owner may explicitly discard selected **new** gas outputs in Production settings. Existing stored gas remains untouched. A stock target cannot count a gas that this device is venting.
