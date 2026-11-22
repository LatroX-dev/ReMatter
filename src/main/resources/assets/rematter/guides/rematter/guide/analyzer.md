---
navigation:
  title: Research and analysis
  icon: rematter:analyzer
  parent: index.md
  position: 5
item_ids:
  - rematter:analyzer
  - rematter:spectrometer_lens
---
# Learn a material, keep the sample

There are two inexpensive targeted ways to learn a supported material family. **Decomposition consumes its sample and teaches the family. Analysis keeps its sample and spends FE instead.** Learning can cover several item forms; it does not supply stored substance.

1. Place the Analyzer and connect external FE.
2. Put a plain supported sample, such as Sand, in its input.
3. Complete the analysis and inspect its composition.
4. Take the same sample back. The earned research notice confirms the newly learned family.

A new Sand analysis has a base cost of **16,000 FE and 800 ticks** before upgrades. Other profiles use twice their decomposition FE and four times their profile duration. An already known material needs no paid research again. Ordinary sample analysis needs no Drive or Matter Cell.

<RecipeFor id="rematter:analyzer" />

<GameScene zoom="5" interactive={true}>
  <Block x="0" y="1" id="rematter:analyzer" />
  <BlockAnnotation x="0" y="1" color="#8dcfff">Analyze a supported sample, learn its family, keep the same item.</BlockAnnotation>
  <LineAnnotation from="0.5 1.5 1" to="0.5 1.5 2" color="#f7bb56" thickness="3">Connect external FE to an exposed face.</LineAnnotation>
</GameScene>

## Read the batch size

You can scan a single nugget, but its composition is reported for the complete recipe batch. For a nine-nugget profile, the quantities describe **nine nuggets**, not the single sample. Your sample is still kept; the Decomposer needs all nine to process that batch.

Unsupported or modified items cannot provide usable research. A missing profile never becomes arbitrary replacement material.

## What research permits

Knowledge is an **additional condition** for synthesis. The output must still be on the explicit material allowlist. Machines, weapons, tools, armour, wires and circuits do not become synthesizable because they were analysed. Diamond item forms keep their separate Pressure Module requirement.

The machine checks its real owner's effective personal/team knowledge. Leaving a team does not copy its shared knowledge into personal knowledge. Trading a sample and analysing it remains a deliberate learning path. Imported cells, external network imports and Recovered Matter never teach a material.

## Field spectrometry

Craft an early **Spectrometer Lens** with an Ice Matrix Crystal, Glass Pane, Structural Bars and a Control Circuit. Carry it in either hand, or wear it in a **Curios** `head`, `charm`, or `curio` slot (right-click in air to equip), to project a live **Field Spectrometer** HUD over targeted blocks, source fluids, Primal Ore veins, and harvestable ambient gases. While an active lens is worn in Curios or carried in your inventory, hold **Shift** on any supported item to view its complete substance badges and exact quantities even before researching it. Bind **Controls → ReMatter → Toggle Spectrometer Lens** to switch the HUD and tooltips on or off at any time. Synthesizing materials still requires researching them in the Analyzer or Decomposer.

<RecipeFor id="rematter:spectrometer_lens" />

The Analyzer's **Research** tab offers [Derive and Discovery](discovery.md). Derive targets a material whose required substances are already known. Discovery is a much later, paid random find that upgrades your Spectrometer Lens into a Discovery Lens. Both use an explicitly inserted [Primal Cell](primal.md), separate from ordinary stock.

Return to [Your first glass](copper.md) when ready.
