---
navigation:
  title: Specialist machines
  icon: rematter:separator
  parent: index.md
  position: 10
item_ids:
  - rematter:separator
  - rematter:thermal_processor
  - rematter:crystallizer
  - rematter:air_separator
  - rematter:pressure_module
---
# Give each process a clear job

Connect each processor to your Matter Drive, keep a readable Cell installed and provide external FE. The recipe list shows the complete input, every output, FE and duration. Stored liquids and gases remain quantities until exported through their supported adapters.

<GameScene zoom="3" interactive={true}>
  <Block x="0" y="1" id="rematter:separator" />
  <Block x="1" y="1" id="rematter:matter_drive" />
  <Block x="2" y="1" id="rematter:thermal_processor" />
  <Block x="3" y="1" id="rematter:crystallizer" />
  <BlockAnnotation x="0" y="1" color="#f0b957">Separator: recover carbon, silica and water from soil.</BlockAnnotation>
  <BlockAnnotation x="2" y="1" color="#ef785b">Thermal Processor: heat or cool stored substances.</BlockAnnotation>
  <BlockAnnotation x="3" y="1" color="#bba4f2">Crystallizer: approved crystal outputs; diamonds need a Pressure Module.</BlockAnnotation>
</GameScene>

## Separator

Runs **Mineral separation** and **Soil separation**. Soil provides carbon for steel, silica for glass and water for fluid export through a Synthesizer. Read the byproducts before starting; a full store safely pauses the whole batch.

<RecipeFor id="rematter:separator" />

## Thermal Processor

Runs **phase changes**, including evaporation, condensation, mineral melting and solidification. The process name and input/output phase show whether it heats or cools. It uses FE in either direction. An Industrial Reactor can also run these processes.

For lava, follow [Useful production](production.md) through the final Synthesizer. Heating alone does not produce a filled bucket.

<RecipeFor id="rematter:thermal_processor" />

## Crystallizer

Makes explicitly approved crystal-family outputs, including their interchangeable gem, dust, nugget and block forms. The Synthesizer cannot bypass this requirement, including through AE2 or RS patterns. Ender Pearls remain Synthesizer products.

Tier 1 uses an Amethyst-based Violet Matrix Crystal and no additional diamond in the device recipe. Your storage Drive still needs its own diamond. Install **one Pressure Module** in the upgrade slot for **tier 2**, required for **every diamond form**. It replaces Speed or Efficiency in that slot. The module's components require advanced preparation.

<RecipeFor id="rematter:crystallizer" />
<RecipeFor id="rematter:pressure_module" />

## Air Separator

Extracts **200 stored oxygen units** for **16,000 base FE** over **200 ticks**. Place it in the **Overworld**, with an air block directly above and an unobstructed view of the sky. It requires connected Cell storage. Blocking the inlet or moving to an unsupported dimension pauses production without spending FE.

Oxygen can feed supported chemical consumers such as Mekanism's Purification Chamber. There is no nitrogen output without a supported useful consumer. Renewable air is explicitly separate from balanced reactions; it still needs power and output space.

<RecipeFor id="rematter:air_separator" />

Gas venting is off by default. An owner may explicitly discard selected **new** gas outputs under Production settings. Existing stored gas remains untouched. A stock target cannot use a gas that this device is venting.
