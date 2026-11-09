---
navigation:
  title: Reactor processes
  icon: rematter:chemical_reactor
  parent: index.md
  position: 10
item_ids:
  - rematter:chemical_reactor
  - rematter:industrial_reactor
  - rematter:pressure_module
---
# One reactor, several processes

Connect a **Chemical Reactor** to your Matter Drive, install a readable Cell and provide external FE. Choose electrolysis, reactions, separation, heating, cooling or air oxygen in its recipe list. The **Industrial Reactor** runs the same chemical processes in an expandable chamber. The list shows every input, output, FE and duration before you start.

<GameScene zoom="3" interactive={true}>
  <Block x="0" y="1" id="rematter:chemical_reactor" />
  <Block x="1" y="1" id="rematter:matter_drive" />
  <Block x="2" y="1" id="rematter:material_synthesizer" />
  <BlockAnnotation x="0" y="1" color="#bba4f2">Reactor: choose the chemical process or air oxygen.</BlockAnnotation>
  <BlockAnnotation x="2" y="1" color="#bba4f2">Synthesizer: approved physical outputs; diamonds require a Pressure Module.</BlockAnnotation>
</GameScene>

## Recover useful byproducts

**Soil separation** supplies carbon for steel, silica for glass and water. **Water electrolysis** supplies hydrogen and oxygen; **Water synthesis** recombines them. Every byproduct requires storage room. A full store pauses the whole batch before partial consumption.

## Heat and cool

Choose evaporation, condensation, mineral melting or solidification in the same reactor. The displayed phases distinguish heating from cooling. Both directions require FE. Follow [Useful production](production.md) through the final Synthesizer: melting stored material does not create a filled lava bucket.

<RecipeFor id="rematter:chemical_reactor" />

## Crystal outputs

Use the **Material Synthesizer** for explicitly approved crystal-family products. Install **one Pressure Module** in its upgrade slot for tier 2, required for **every diamond form**, including native AE2/RS crafting patterns. The module occupies the same slot as Speed or Efficiency Upgrades. Ender Pearls and other permitted materials remain Synthesizer products.

<RecipeFor id="rematter:pressure_module" />

## Air oxygen

Choose **Air oxygen** in either reactor. A batch extracts **200 stored oxygen units** for **16,000 base FE** over **200 ticks** in the **Overworld**. Compact reactors require an air block directly above and an unobstructed sky. An industrial chamber takes air above the front edge of its roof. Blocking that inlet pauses production without spending FE. Connected Cell storage is required.

Oxygen can feed Mekanism's Purification Chamber through a Drive with Automation enabled and a Pressurized Tube. Nitrogen waits for a useful supported consumer. Renewable air remains an explicit environmental source with power and storage costs.

Gas venting starts off. An owner may explicitly discard selected **new** gas outputs in Production settings. Existing stored gas remains untouched. A stock target cannot count a gas that this device is venting.
