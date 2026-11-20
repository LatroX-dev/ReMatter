# Crystal fuel closed-cycle audit

`tools/audit_crystal_fuel.py` verifies the closed nine-crystal fuel set (`CrystalFuelData.ALLOWED`), every recipe producing a crystal core, and the conservative energy lower bounds enforced by `MachineEnergyPolicy` (capped at a 25% combined efficiency discount).

## Closed-cycle energy bounds (standalone)

- **Sand synthesis cost:** `16,000 FE` nominal (`12,000 FE` floor at 25% max discount) for `4` `rematter:matrix_blend` outputs = **`3,000 FE` per core** (counting clay, preparation of Silith/Argil, and coloured additives as `0 FE`).
- **Powered crystal preparation floor:** `4,000 FE` nominal = **`3,000 FE` floor** per core at maximum machine discount.
- **Differentiated closed-cycle burner yields:** **`768 FE` .. `2,816 FE` per crystal** — Tier-0 hand-crankable cores stay `<= 1,500 FE` (`50%` of the `3,000 FE` sand floor), and Tier-1 mandatory-powered cores stay strictly `< 3,000 FE` (powered preparation floor) and `<= 3,000 FE` (`50%` of the `6,000 FE` combined synthesis + preparation floor).

| Crystal fuel | Tier | Enabled | Burn yield | Prep floor | Sand + prep floor | Net vs. prep only |
| --- | ---: | :---: | ---: | ---: | ---: | ---: |
| `rematter:matrix_crystal` | 0 | yes | 768 FE | 3,000 FE | 6,000 FE | -2,232 FE |
| `rematter:ice_matrix_crystal` | 0 | yes | 896 FE | 3,000 FE | 6,000 FE | -2,104 FE |
| `rematter:copper_matrix_crystal` | 0 | yes | 1,024 FE | 3,000 FE | 6,000 FE | -1,976 FE |
| `rematter:amber_matrix_crystal` | 0 | yes | 1,280 FE | 3,000 FE | 6,000 FE | -1,720 FE |
| `rematter:ember_matrix_crystal` | 0 | yes | 1,408 FE | 3,000 FE | 6,000 FE | -1,592 FE |
| `rematter:violet_matrix_crystal` | 1 | yes | 1,792 FE | 3,000 FE | 6,000 FE | -1,208 FE |
| `rematter:blue_matrix_crystal` | 1 | yes | 2,048 FE | 3,000 FE | 6,000 FE | -952 FE |
| `rematter:leaf_matrix_crystal` | 1 | yes | 2,304 FE | 3,000 FE | 6,000 FE | -696 FE |
| `rematter:mint_matrix_crystal` | 1 | yes | 2,816 FE | 3,000 FE | 6,000 FE | -184 FE |

## Pack overlay status (`integrations/reforging/datapack`)

- **Enabled fuels:** `9` / `9` (`0` disabled).
- **External duplication safeguards:** All nine `rematter:*matrix_crystal` items are grouped in `#reforging:rematter/crystal_fuels`, excluded from Replication (`cant_be_scanned`, `cant_be_disintegrated`, `skip_calculation`), and assigned `emc: 0` by `integrations/reforging/merge_equivox.py` so external pack mods cannot duplicate crystal fuel for free FE.

## Auditing a custom modpack overlay

Run the audit script against any datapack directory before enabling or repricing crystal fuels:

```sh
python3 tools/audit_crystal_fuel.py --pack path/to/datapack
```

In addition to passing `--pack`, pack authors enabling crystal fuels alongside Replication, Equivox, Productive Bees, or Mystical Agriculture should verify that none of the nine `rematter:*matrix_crystal` items have an EMC value, Replication matter value, or zero-energy duplication route cheaper than their burner output.
