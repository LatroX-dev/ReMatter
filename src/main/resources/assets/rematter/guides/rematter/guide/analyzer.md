---
navigation:
  title: Optional material analysis
  icon: rematter:analyzer
  parent: index.md
  position: 4
item_ids:
  - rematter:analyzer
---
# Optional material analysis

The <ItemLink id="rematter:analyzer" /> shows a supported sample's composition and keeps the item. **It needs FE, but no Drive, Cell or pipe network.** Analysis is information, not a prerequisite for decomposition or synthesis.

1. Place the Analyzer and connect external FE.
2. Put a copper ingot in its input.
3. The new sample starts a scan automatically.
4. After the scan, read **1,000 units of solid copper** and take your ingot back.

An unupgraded scan costs **500 FE** and takes **40 ticks**. Results appear after completion. The scanner stops when finished. Its completed result is kept when you reopen the world, provided the sample and recipe are unchanged. Inserting a new sample starts the next scan.

<RecipeFor id="rematter:analyzer" />

<GameScene zoom="5" interactive={true}>
  <Block x="0" y="1" id="rematter:analyzer" />
  <BlockAnnotation x="0" y="1" color="#8dcfff">Optional Analyzer: sample in, composition shown, same sample kept. No storage required.</BlockAnnotation>
  <LineAnnotation from="0.5 1.5 1" to="0.5 1.5 2" color="#f7bb56" thickness="3">Connect external FE to an exposed face.</LineAnnotation>
</GameScene>

## Read the batch size

You can scan a single nugget. **Amounts per 9 items** means the displayed composition describes nine nuggets; your sample is still kept. The Decomposer needs all nine to process that batch.

Unsupported or modified items cannot provide a usable analysis. A missing profile never becomes arbitrary replacement material. Analysis does not grant permission to synthesize the original item.

Return to [Your first glass](copper.md) when ready.
