---
navigation:
  title: Your first setup
  icon: rematter:matter_storage
  parent: index.md
  position: 1
item_ids:
  - rematter:matter_storage
  - rematter:matter_terminal
  - rematter:matter_pipe
---
# Your first setup

Start with one <ItemLink id="rematter:matter_storage" />, an <ItemLink id="rematter:analyzer" />, a <ItemLink id="rematter:decomposer" /> and a <ItemLink id="rematter:material_synthesizer" />. Add a <ItemLink id="rematter:matter_terminal" /> if you want to view the storage from another point on the network.

## Connect the matter network

Machines can touch face-to-face. Use <ItemLink id="rematter:matter_pipe" /> to bridge a gap. Every connected network needs **exactly one storage** and supports up to **64 connected machines and pipes**. All machines must belong to the same player, and the connection must stay in loaded chunks.

<GameScene zoom="3" interactive={true}>
  <Block x="0" id="rematter:decomposer" />
  <Block x="1" id="rematter:matter_pipe" p:west="true" p:east="true" />
  <Block x="2" id="rematter:matter_storage" />
  <Block x="3" id="rematter:matter_pipe" p:west="true" p:east="true" />
  <Block x="4" id="rematter:material_synthesizer" />
  <Block x="2" z="-1" id="rematter:analyzer" />
  <Block x="2" z="1" id="rematter:matter_terminal" />
  <BlockAnnotation x="0" color="#63d8bf">The Decomposer puts substances into storage.</BlockAnnotation>
  <BlockAnnotation x="2" color="#e6b676">One Matter Storage holds the shared stock.</BlockAnnotation>
  <BlockAnnotation x="4" color="#63d8bf">The Material Synthesizer consumes stored substances.</BlockAnnotation>
  <BlockAnnotation x="2" z="-1" color="#8dcfff">Analyze a sample here; the sample is kept.</BlockAnnotation>
  <BlockAnnotation x="2" z="1" color="#d8b6ef">A Terminal lets you inspect the linked storage.</BlockAnnotation>
</GameScene>

## Supply power separately

Connect an external **FE generator or charged energy storage** to each processing machine. Use compatible energy cables or a direct energy connection. Matter Pipes carry matter; they do not distribute FE.

Each processing machine accepts FE from any side, stores up to 1,000,000 FE and accepts up to 16,000 FE per transfer. Matter Storage and Matter Terminal do not need FE to hold or show materials. ReMatter does not add a survival generator.

## Craft the network

<RecipeFor id="rematter:matter_storage" />
<RecipeFor id="rematter:matter_pipe" />
<RecipeFor id="rematter:matter_terminal" />

Next: [Analyze a sample](analyzer.md).
