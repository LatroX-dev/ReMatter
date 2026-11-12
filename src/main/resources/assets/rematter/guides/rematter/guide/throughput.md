---
navigation:
  title: Cable and controller budgets
  icon: rematter:resonant_matter_cable
  parent: index.md
  position: 17
item_ids:
  - rematter:matter_cable
  - rematter:reinforced_matter_cable
  - rematter:resonant_matter_cable
---
# One controller, one budget

| Cable tier | Ordinary matter work | Native FE input |
| --- | --- | --- |
| Basic | 128 u/t | 2,048 FE/t |
| Reinforced | 1,024 u/t | 16,384 FE/t |
| Resonant | 8,192 u/t | 131,072 FE/t |

The **best usable route**, including its weakest cable, determines the tier. Direct contact is Basic. Equal cables and extra ports do not add their rates. A better path can replace a bottleneck without changing yield, Field, Precision or efficiency.

All active manual and native crafting jobs share the controller's matter budget.1,000 u in plus 1,000 u out owes **2,000 u transfer work**. Progress pays this work; the complete material change still commits atomically. Waiting or failed batches do not leak matter.

No idle transfer credit is saved for future batches. Route changes affect the remaining work of the same job. The controller shows the effective rate and cable-limited status.

Native FE input shares one best-route budget across ports. External cables remain supported with a shared absolute controller cap of **131,072 FE/t**. Native and foreign input together stay below that total; neither receives a new allowance per side.

Foreign owners' networks remain separate. [Entangled Links](links.md) provide one short Basic connection, not chunk loading or free bandwidth.
