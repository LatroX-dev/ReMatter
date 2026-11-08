# Tools

These scripts generate and check ReMatter's data and assets. Run them from the repository root with Python 3.11 or newer. Apart from the two sprite renderers, they use only the Python standard library.

## Checks

Each check leaves the files untouched and exits with an error when something differs:

```sh
python3 tools/generate_material_data.py --check
python3 tools/generate_workshop_materials.py --check
python3 tools/generate_visual_assets.py --check
python3 tools/generate_module_assets.py --check
python3 tools/generate_cell_models.py --check
python3 tools/generate_component_assets.py --check
python3 tools/generate_advanced_material_icons.py --check
python3 tools/generate_metallurgy_assets.py --check
python3 tools/check_assets.py
python3 -m unittest discover -s integrations/reforging/tests -p 'test_*.py'
```

`./gradlew build` compiles the mod and runs the Java tests, including the data tests of the ReForging integration.

## Chemistry data

- `generate_material_data.py` writes every item profile, fluid profile, synthesis permission and gas permission under `src/main/resources/data/rematter/rematter/` from the reviewed catalogue in `data/material_catalogue.json`. It also writes `docs/material-coverage.csv` and fills `docs/material-coverage.md` from `data/material_coverage_template.md`. Generation removes chemistry files that the catalogue does not produce, and it rejects batch energies above the limit it reads from the machine sources. `--check` compares instead of writing; `--catalogue` reads another catalogue file.
- `generate_workshop_materials.py` writes the preparation and sintering recipes of the metal dusts, alloys, gem powders and advanced components, the smelting and blasting recipes of the dusts and alloy powders, the Sinter Furnace recipe, their recipe-book advancements, the dust and ingot tags, and the optional Oritech, Mekanism and Ender IO recipes. The fraction, Matrix Blend and machine recipes are written by hand. `--check` compares instead of writing.
- `inventory_materials.py` reads installed mod JARs from `--mods` and writes an evidence report to `--output` for manual review. It never creates profiles; keep its report outside the repository.

## Models and textures

- `generate_visual_assets.py` draws the block material textures, the cable and port models and textures, and the Reactor Casing and Glass textures, and derives the state variants of the authored machine models. `--models-only` keeps every texture, `--textures-only` keeps every model and blockstate, and `--refresh-glass` lets `--models-only` redraw the glass texture. `--check` compares the files of the selected mode instead of writing them.
- `generate_module_assets.py` derives the floor, ceiling and item models of the five industrial modules from their authored models, draws their crystal textures and writes their loot tables, recipes and advancements. `--models-only` and `--textures-only` limit it to one part, and `--check` compares instead of writing.
- `generate_metallurgy_assets.py` writes the Sinter Furnace states and the item models of the metallurgy sprites.
- `generate_cell_models.py` writes the 3D cartridge models that the Cell Housing and Matter Cells share with populated Drive bays.
- `generate_component_assets.py` writes the item models of the component sprites and the Cell models, and checks that the sprites are 128-pixel RGBA images.
- `generate_advanced_material_icons.py` writes the item models of the crystal, powder, blend and advanced component sprites and checks their size.
- `generate_technical_sprites.py` and `generate_storage_sprites.py` draw the component, upgrade, pattern, rack, recovery parcel and guide icons as SVG files in `artwork/components/` and render them to `textures/item/`. They need [CairoSVG](https://cairosvg.org/) (`pip install cairosvg`), which needs the Cairo library.
- `check_assets.py` fails when a PNG under `src/main/resources/assets` carries chunks that are not needed to decode it, such as color profiles, resolution, background or text metadata. `--strip` removes them in place without touching the pixel data. The renderers above strip these chunks themselves.

`component_drawing.py`, `connection_geometry.py`, `generated_files.py`, `machine_palette.py` and `material_tiles.py` are shared helpers of these generators. The machine models in `models/block/`, the crystal, powder and material sprites, and `data/material_catalogue.json` are authored by hand; the generators read them but do not replace them.
