# Tools

These scripts generate and check ReMatter 0.7.0's data and assets. Run them from the repository root with Python 3.11 or newer. Apart from the two sprite renderers, they use only the Python standard library.

## Checks

Each check leaves the files untouched and exits with an error when something differs:

```sh
python3 tools/generate_material_data.py --check
python3 tools/generate_workshop_materials.py --check
python3 tools/generate_visual_assets.py --check
python3 tools/generate_family_assets.py --check
python3 tools/generate_gui_assets.py --check
python3 tools/generate_bench_shapes.py --check
python3 tools/generate_module_assets.py --check
python3 tools/generate_cell_models.py --check
python3 tools/generate_component_assets.py --check
python3 tools/generate_advanced_material_icons.py --check
python3 tools/generate_metallurgy_assets.py --check
python3 tools/generate_device_assets.py --check
python3 tools/generate_research_data.py --check
python3 tools/generate_primal_assets.py --check
python3 tools/generate_progression_assets.py --check
python3 tools/generate_player_guidance.py --check
python3 tools/check_assets.py
python3 tools/check_visual_style.py
python3 -m unittest discover -s integrations/reforging/tests -p 'test_*.py'
```

`./gradlew build` compiles the mod and runs the Java tests, including the data tests of the ReForging integration.

## Chemistry data

- `generate_material_data.py` writes every item profile, fluid profile, synthesis permission and gas permission under `src/main/resources/data/rematter/rematter/` from the reviewed catalogue in `data/material_catalogue.json`. It also writes `docs/material-coverage.csv` and fills `docs/material-coverage.md` from `data/material_coverage_template.md`. Generation removes chemistry files that the catalogue does not produce, and it rejects batch energies above the limit it reads from the machine sources. `--check` compares instead of writing; `--catalogue` reads another catalogue file.
- `generate_workshop_materials.py` writes the preparation and sintering recipes of the metal dusts, alloys, gem powders and advanced components, the smelting and blasting recipes of the dusts and alloy powders, the Sinter Furnace recipe, their recipe-book advancements, the dust and ingot tags, industrial direct and reclaim recipes, Precision-gated Primal Crystal and Discovery Lens preparation, the native empty-Hand-Preparator upgrade, and the optional Oritech, Mekanism and Ender IO recipes. The fraction, Matrix Blend and machine recipes are written by hand. `--check` compares instead of writing.
- `generate_research_data.py` writes exact material-research families from the reviewed catalogue and an explicit Discovery tier/dimension list. It never infers family membership or synthesis permission from tags, recipes or elemental similarity. `--check` compares without writing.
- `generate_player_guidance.py` writes ten optional advancement definitions. Their `minecraft:impossible` criteria are awarded only by matching server success hooks; an inventory pickup cannot stand in for a completed research or conversion operation. `--check` compares without writing.
- `inventory_materials.py` reads installed mod JARs from `--mods` and writes an evidence report to `--output` for manual review. It never creates profiles; keep its report outside the repository.

## Models and textures

- `generate_gui_assets.py` writes the six pixel-aligned console frames and their GUI nine-slice metadata from `UiPalette.java`. Their neutral surfaces match the machine casing palette; device colours and status indicators are drawn separately. `--check` compares instead of writing.
- `generate_family_assets.py` writes everything the Matter Drive, Matter Terminal, Network Interface, Machine Port, the three Matter Cables, Reactor Casing, Reactor Glass, both industrial controllers, the Chemical Reactor and the six workshop benches (Analyzer, Decomposer, Material Synthesizer, Material Preparator, Hand Preparator and Sinter Furnace) show: their sprites, block models, block states and item definitions. A model is a short list of boxes on a half-pixel grid. The generator writes only the faces that can be seen, gives faces on the block boundary a cullface, and maps every sprite at two texels per pixel: 32 x 32 for a block face, 16 x 16 for small parts. Lights, screens and showpieces are lit without ambient occlusion, so they keep their brightness when the machine stands between other blocks. The item of a machine shows it switched on, so the icons of the family differ by their device colour. It fails when boxes leave the grid, form slivers, intersect or would z-fight in any state the block can show, when a model exceeds its box budget, and when a sprite or model is referenced but not written. The faceplates and status lights of mounted cells are sprites only; the block entity renderer draws them. The Chemical Reactor has two reaction columns with animated active sight windows, generated from `reactor_showpiece.py`. The six benches share its open alcove chassis; `bench_showpieces.py` defines the scan plate, crusher, growth tray, grinding/mixing station, hand crank and oven. The inventory crank has the same resting bounds as its renderer, and a conservative swept envelope is checked against the complete housing. Connector seats, hubs and flanges use a separate dark metal palette; coloured rings continue to show side roles. `--check` compares instead of writing.
- `generate_visual_assets.py` maintains the remaining shared material/effect textures and legacy window pane. It writes no bench models, blockstates or item definitions; those belong exclusively to `generate_family_assets.py`. `--models-only` keeps every texture, `--textures-only` keeps every model and blockstate, and `--refresh-glass` lets `--models-only` redraw the window pane. `--check` compares the files of the selected mode instead of writing them.
- `generate_module_assets.py` derives the floor, ceiling and item models of the five industrial modules from their authored models, draws their crystal textures and writes their loot tables, recipes and advancements. Preparation modules require Amber functional crystals; reaction modules use Violet. `--models-only` and `--textures-only` limit it to one part, and `--check` compares instead of writing.
- `generate_device_assets.py` writes the Pressure Module and Parallel Upgrade item definitions, plus the Pressure Module crafting recipe and advancement. The Parallel Upgrade recipe belongs exclusively to `generate_workshop_materials.py` and requires Industrial Preparation. Removed specialist devices are not generated or registered.
- `generate_primal_assets.py` writes the three explicit-dimension Primal Ores, their equal-value Silk Touch/shard loot, separate Primal Cells, Primal Crystal, Discovery Lens and Entangled Link assets. The three Cell recipes and Link recipe include vanilla recipe-book discovery advancements. Cell recipes start with fresh housings and never consume an existing filled cell. End placement excludes a 1,040-block radius, including a margin beyond the central island. `--check` compares without writing.
- `generate_progression_assets.py` builds the Primal Converter and both Crystal Generator fronts with the shared 32-pixel family palette and geometry checks. It also writes their crafting recipes and recipe-book advancements, plus the nine exact crystal-fuel definitions: 1,024 FE each standalone, disabled in the ReForging integration. The conservative own-recipe audit gives clay, furnace fuel and coloured additives zero cost; only the sand contribution and the full 25% FE-policy discount count toward the 50% energy-return ceiling. This does not establish the safety of external renewable-material or fuel paths. `--check` compares without writing.
- `generate_metallurgy_assets.py` writes only the nine metallurgy item model/definition pairs. The Sinter Furnace model and states belong exclusively to `generate_family_assets.py`.
- `generate_cell_models.py` writes the 3D cartridge models of the Cell Housing and the Matter Cells. A mounted cell is not drawn with them: the Matter Drive shows the faceplate and status light sprites of the machine family in its bays.
- `generate_component_assets.py` writes the item models of the component sprites and the Cell models, and checks that the sprites are 128-pixel RGBA images.
- `generate_advanced_material_icons.py` writes the item models of the crystal, powder, blend and advanced component sprites and checks their size.
- `generate_technical_sprites.py` and `generate_storage_sprites.py` draw the component, upgrade, pattern, rack, recovery parcel and guide icons as SVG files in `artwork/components/` and render them to `textures/item/`. They need [CairoSVG](https://cairosvg.org/) (`pip install cairosvg`), which needs the Cairo library.
- `check_assets.py` fails when a PNG under `src/main/resources/assets` carries chunks that are not needed to decode it, such as color profiles, resolution, background or text metadata. `--strip` removes them in place without touching the pixel data. The renderers above strip these chunks themselves.
- `check_visual_style.py` checks the shipped assets against the [visual style guide](../docs/visual-style.md): the size of every texture by asset class, the palette of block and GUI sprites, the form of item icons, block-style items for mounted hardware, the half-pixel grid and texel density of models, animation metadata, the crystal hues in the palette module, `UiPalette.java` and the guide, colour literals in client code, and textures or models that nothing uses or that are missing. It reads the marked tables of the guide, uses only the standard library and writes nothing. Each finding names the rule and the fix; `--list` prints the texture inventory, `--json FILE` writes the findings, and `--selftest` runs the rules against synthetic cases. Its `EXCEPTIONS` table holds the documented exceptions, each with a reason.

`component_drawing.py`, `connection_geometry.py`, `generated_files.py`, `machine_housings.py`, `machine_palette.py`, `recipe_discovery.py` and `material_tiles.py` are shared helpers of these generators. `recipe_discovery.py` builds exact ingredient-count discovery and recipe rewards for generated vanilla crafting recipes. `family_palette.py` holds the colours, `family_sprites.py` the pixel art and `family_geometry.py` the box, texture mapping and z-fighting checks of `generate_family_assets.py`. The older bench housings in `artwork/machines/` remain artwork references; shipping bench geometry is defined by `bench_showpieces.py`. The module models in `models/block/`, the crystal, powder and material sprites, and `data/material_catalogue.json` are authored sources read by their corresponding generators.
