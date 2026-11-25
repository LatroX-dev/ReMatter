---
navigation:
  title: Entangled Links
  icon: rematter:entangled_link
  parent: index.md
  position: 20
item_ids:
  - rematter:entangled_link
  - rematter:dimensional_entangled_link
---
# Link your own loaded networks

<RecipeFor id="rematter:entangled_link" />

Place two **Entangled Links** and connect their local sides to your Matter Cable networks. Hold an Entangled Link item or a **Configuration Card** and **shift-right-click the first Link, then right-click (or shift-right-click with a Link item) the second**. The on-screen message confirms selection and pairing.

Both basic endpoints must have compatible ownership/security, be in the **same dimension**, remain in already loaded chunks and stay within **64 blocks**. They must refer to each other. The pair never forces chunk loading on its own.

A basic Entangled Link counts as **one Basic cable connection** with `128 u/t` matter work and `4,096 FE/t` native routing. Controller and source budgets still apply. It creates no new allowance per parallel lane, side or second route.

## Cross-dimension links

<RecipeFor id="rematter:dimensional_entangled_link" />

The **Dimensional Entangled Link** connects two of your Matter Cable networks across **unlimited distance and across any dimensions** (for example Overworld ↔ Nether ↔ End) at **Resonant** throughput (`8,192 u/t` matter and `524,288 FE/t` FE). Pair two Dimensional Entangled Links with a **Configuration Card** (Sneak+Right-Click the first, travel to the second in any dimension, Right-Click to pair).

Right-click a Dimensional Entangled Link to inspect its paired dimension and coordinates, assign a custom label, unpair it, or install an **Anchor Upgrade** to keep its chunk loaded. If the remote dimension's chunk is unloaded, your local base network stays online and simply pauses the remote bridge until the far chunk is loaded again.
