---
navigation:
  title: Analyze a sample
  icon: rematter:analyzer
  parent: index.md
  position: 2
item_ids:
  - rematter:analyzer
---
# Analyze a sample

The <ItemLink id="rematter:analyzer" /> reveals the stored composition of a supported item. It keeps the sample and does not consume or create its materials.

<RecipeFor id="rematter:analyzer" />

1. Connect the Analyzer to exactly one Matter Storage and supply FE.
2. Put a **copper ingot** in the input slot on the left.
3. Press **Run** if the machine is paused.
4. After the scan, the right panel shows **1,000 units of solid copper**. The Analyzer pauses automatically.
5. Remove the ingot and continue with [The copper loop](copper.md).

An unupgraded scan costs 500 FE and takes 40 ticks. The composition appears only after the scan completes. To analyze a new sample, insert it and press Run again.

Some materials use a batch to keep quantities exact. You can scan a single nugget, but **Amounts per 9 items** means the displayed composition describes nine nuggets together. The Decomposer needs the complete batch before it starts; the Analyzer still keeps your single sample.

If the machine stays at **Insert sample**, that item has no usable profile or carries unsupported custom data. An unknown item is not converted to an arbitrary replacement material. Analysis is information, not permission to synthesize the original item.
