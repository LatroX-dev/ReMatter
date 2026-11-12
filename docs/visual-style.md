# Visual style

ReMatter should look like one thing: every block, item, icon and screen follows the six rules below. Read them before you add or change a texture, a model or a screen, and run the check at the end:

```sh
python3 tools/check_visual_style.py
```

The check reads the tables of this page that carry a `check:` mark, so a colour is changed here, in the palette module and in the Java palette together, or not at all.

## The six rules

**S1. Blocks are machine-family pixel art.** Everything drawn in the world uses two texels per model pixel: a full block face is a 32 x 32 sprite, a small part a 16 x 16 one. Sprites are hard pixel clusters lit from the top left, without gradients, in the colours of `tools/family_palette.py`. Models are a few boxes on a half-pixel grid, written by a generator. One density and one palette are what make twenty machines read as a family.

**S2. Natural blocks sit in vanilla terrain.** A block that world generation places (the three Primal Ores) shows its vanilla host block from the vanilla texture, referenced at runtime and never copied, so resource packs keep working. The mod draws only an overlay on top, at the host's 16 px density. The overlay is animated and emissive.

**S3. Item icons are illustrated.** Materials, components and tools are 128 x 128 drawings with a clean dark outline, soft facet shading and a three-quarter view from the upper left. A 16 px sprite beside them looks like a different mod, so there are none.

**S4. Mounted hardware looks the same in the hand and in the machine.** Matter Cells, Primal Cells, the Cell Housing and the five industrial modules are block-style models built from the sprites the player sees when the part is mounted.

**S5. Colour means something.** The casing is neutral graphite. The eight Matrix Crystal hues are identity colours: a machine wears the hue of the crystal in its recipe, a module the hue of its job. Status colours mean status only. Primal Matter has a signature of its own. Every colour comes from one source.

**S6. Screens use the same graphite and one icon set.** Every tab and every action has an icon, compact buttons have a tooltip, an empty slot shows what belongs in it, and a substance badge always shows the full symbol.

## Palette

`tools/family_palette.py` and `tools/machine_palette.py` are the source. `UiPalette.java` mirrors them for the screens. The check compares both with the tables below.

A neutral is used exactly as listed. An identity or status colour may also be mixed with black or white for lit, dim and shaded states (`scale` and `lighten` in the palette module), as long as at least a fifth of the colour remains. A colour that is merely close to a listed one is a mistake: take the listed value.

### Casing, steel and connectors

<!-- check:neutral-palette -->
| Role | Constant | Hex | Use |
| --- | --- | --- | --- |
| Casing highlight | `HIGHLIGHT` | `#495059` | lit top and left edge of a face |
| Casing base | `BASE` | `#2b2f35` | field of a face, GUI panels |
| Casing shade | `SHADE` | `#1d2127` | shaded edge, plinth, recessed plate |
| Seam | `SEAM` | `#13161b` | outline of a face, joints, inner steps |
| Well | `WELL` | `#0a0d11` | recesses, screen glass, empty bays and slots |
| Steel light | `TRIM` | `#c3ccce` | top edge of steel hardware |
| Steel | `STEEL` | `#9aa6ab` | keys, knobs, process hardware, cell faceplates |
| Steel shade | `STEEL_SHADE` | `#6b777d` | shaded side of steel |
| Steel deep | `STEEL_DEEP` | `#4a545a` | steel in shadow, unreadable cell |
| Connector light | `PORT_LIGHT` | `#64727e` | lit edge of a plug seat, hub or flange |
| Connector | `PORT_METAL` | `#46515b` | gunmetal body of connectors |
| Connector dark | `PORT_DARK` | `#2d363f` | shaded edge of connectors |
| Automatic port | `PORT_AUTOMATIC` | `#4d5a64` | ring of a port without a fixed mode |
| Contact | `COPPER` | `#ae6f49` | electrical contacts |
| Label | `IVORY` | `#e4decc` | type plates, tier bars, dashes |
| Cable jacket | `JACKET` | `#181c22` | cable body, a step below the casing |

### The eight crystal hues

<!-- check:crystal-hues -->
| Crystal | Hex | Stands for | Machines | Modules |
| --- | --- | --- | --- | --- |
| Amber | `#e3a94b` | preparation | `material_preparator`, `hand_preparator`, `industrial_preparator` | `grinding_unit`, `separation_unit`, `mixing_basin` |
| Copper | `#dc894f` | decomposition | `decomposer` | |
| Mint | `#57cddb` | stored matter | `matter_drive`, `matter_terminal` | |
| Leaf | `#7dd27f` | synthesis | `material_synthesizer` | |
| Ice | `#d7d1bc` | analysis | `analyzer` | |
| Ember | `#ea4848` | heat | `sinter_furnace`, `crystal_generator`, `industrial_crystal_generator` | |
| Blue | `#6794f1` | network link | `network_interface` | |
| Violet | `#b381e8` | reaction | `chemical_reactor`, `industrial_reactor` | `field_coil`, `reaction_chamber` |

The hue of a device is the hue of the Matrix Crystal in its crafting recipe, and at least one of its sprites shows it. The check reads the recipes, the sprites and `machineAccent` in `UiPalette.java` against this table. The item icon of a crystal has this hue as its body colour. On a Machine Port the crystal hues double as the legend of the port mode, and only there.

### Status colours

<!-- check:status-colours -->
| Status | Constant | Hex | Meaning |
| --- | --- | --- | --- |
| Blue | `LED['blue']` | `#54b8f1` | empty, waiting |
| Green | `LED['green']` | `#4fe18e` | holding matter, working |
| Amber | `LED['amber']` | `#f1c54a` | full, needs attention |
| Red | `LED['red']` | `#eb5862` | unreadable, fault; blinks |
| Red, dark phase | `LED_RED_DIM` | `#a33a42` | second frame of the red blink |

A status colour never decorates and never tells tiers apart. A tier is a bar length, a stripe width or a dash.

### Cable tiers

<!-- check:cable-tiers -->
| Cable | Constant | Hex | Stripe |
| --- | --- | --- | --- |
| Matter Cable | `CABLE_TIERS['matter_cable']['stripe']` | `#dc894f` | 2 texels, solid |
| Reinforced Matter Cable | `CABLE_TIERS['reinforced_matter_cable']['stripe']` | `#54bfac` | 4 texels, solid |
| Resonant Matter Cable | `CABLE_TIERS['resonant_matter_cable']['stripe']` | `#be8fbb` | 4 texels, dashed |

### The Primal signature

<!-- check:primal-signature -->
| Role | Hex | Use | Devices |
| --- | --- | --- | --- |
| Core | `#ffffff` | white-hot centre | |
| Glow | `#efe6ff` | halo around the core | |
| Body | `#8a5cff` | ultraviolet body, accent of Primal screens | `primal_converter` |
| Cool | `#5cc8ff` | one end of the shimmer, cyan-blue | |
| Warm | `#ff6ad5` | other end of the shimmer, magenta | |

Primal Matter is prismatic ultraviolet: a white core in an ultraviolet body, with a shimmer that travels between the cool and the warm end. It is animated wherever it is lit. It is none of the eight hues and must not be mistaken for the static, pastel Violet crystal of the reactors, so Primal parts never use `#b381e8` and reactors never use the signature.

## Blocks and models (S1)

- **Density.** Two texels per model pixel. A sprite is 32 x 32 for a block face or 16 x 16 for a small part. Faces are mapped by position, so a sprite never stretches.
- **Grid.** Every box corner is a multiple of 0.5 px. A feature that stands alone is at least 1 px in two of its dimensions; 0.5 px is only the depth of a step or an inset.
- **Geometry or texture?** Geometry carries the silhouette and real depth: the casing, an alcove, a bay, a showpiece, a light that stands proud. Everything else is drawn into the sprite: bolts, seams, vents, labels, keys. If a detail would need a box thinner than 1 px, it is a texture detail.
- **Faces.** Only visible faces are written. No two faces lie in the same plane, face the same way and overlap. An outer face on the block boundary carries its `cullface`. A showpiece holds at most two rotated boxes; housings hold none.
- **Sprites.** At most 16 colours, alpha 0 or 255. Light comes from the top left: a lit edge on top and left, a shaded edge on the right and bottom, one texel wide.
- **Lights and screens.** A lit part has `light_emission` 15 and no shading, so it keeps its brightness between other blocks. Switching a machine on swaps the sprite of its lit parts and nothing else.
- **Animation.** A vertical strip of square frames with a `.png.mcmeta` beside it. Hard steps for pixel art (the red blink, the reactor liquid); `interpolate` only for glows. Every frame keeps the same transparent pixels.
- **Never by hand.** A generator writes the sprites, models, block states and item definitions. To change one, change the generator and run it.

## Natural blocks (S2)

The model of an ore names the host texture of the vanilla block (`minecraft:block/deepslate`, `minecraft:block/netherrack`, `minecraft:block/end_stone`) and puts the mod's overlay on the same faces. Overlay textures are 16 px wide and named `*primal_ore*`. They hold the socket shadow and the crystal, never a copy of the stone. The crystal uses the Primal signature, at least one overlay layer is animated, and the lit layer has `light_emission`.

## Item icons (S3)

- **Canvas.** 128 x 128, 8-bit RGBA, transparent background. The longer side of the drawing covers 96 to 120 px; at least 4 px stay clear on every side.
- **Outline.** A closed outline 3 to 5 px wide in a dark shade of the object's own colour, anti-aliased against the background.
- **Shading.** Flat facets in three to five steps of one colour, a soft highlight toward the upper left, the darkest facet at the lower right. No texture noise, no photographic gradients.
- **View.** Three-quarter view from the upper left, as if the object lay on a table in front of you.
- **Source.** An SVG under `artwork/` rendered by its tool, or a deterministic painter. A hand-edited PNG without a source cannot be reproduced.
- **References.** Crystals: `amber_matrix_crystal`. Powders: `gold_dust`. Ingots: `rhevon_ingot`. Circuits: `control_circuit`. Upgrades: `speed_upgrade`. Frames: `machine_frame`.

A crystal with a hue is opaque and its body is the hue of the table above. Only the uncoloured Matrix Crystal is clear.

## Mounted hardware (S4)

The item of a cell or a module is built from the same boxes and sprites as the part in the machine, at the same density. A cell item shows the faceplate, tier bar and status light the Matter Drive draws in its bay. Blocks follow the same rule by construction: the item of a machine is its block model, switched on.

## Screens (S6)

- **Surfaces.** Panels, buttons, insets and slots are nine-slice sprites in the casing colours of the table below. Device colour appears in the title strip and in accents.
- **Icons.** 16 x 16 sprites in `textures/gui/sprites/icon/`, drawn in white ink with one lighter tone and tinted when drawn, so one sprite serves every state. Status lamps are the only icons with baked colours, and those are the status colours.
- **Buttons.** A primary action is an icon and a label. A compact button is an icon alone and must have a tooltip. A tab has an icon.
- **Slots.** An empty slot shows a ghost icon of what belongs in it.
- **Substance badges.** A badge shows the complete symbol of the substance. A long formula breaks into two lines at an element; it is never cut with an ellipsis.
- **Colours in code.** Every colour of a screen or renderer is a constant of `UiPalette.java`. Other client classes hold no colour literals.

<!-- check:gui-surfaces -->
| Screen surface | `UiPalette` | Palette constant |
| --- | --- | --- |
| Window background | `BACKGROUND` | `BASE` |
| Panel | `PANEL` | `SHADE` |
| Control surface | `SURFACE` | `BASE` |
| Recess, slot floor | `RECESS` | `WELL` |
| Border | `BORDER` | `PORT_METAL` |
| Lit edge | `HIGHLIGHT` | `HIGHLIGHT` |
| Slot edge | `SLOT_EDGE` | `PORT_LIGHT` |

## Who writes what

| Generator | Owns |
| --- | --- |
| `generate_family_assets.py` | sprites, models, block states and items of the Matter Drive, Terminal, Network Interface, Machine Port, cables, Reactor Casing and Glass, both industrial controllers, the Chemical Reactor and the six benches |
| `generate_progression_assets.py` | Primal Converter and both Crystal Generators |
| `generate_module_assets.py` | the five industrial modules |
| `generate_cell_models.py` | Cell Housing, Matter Cells and Primal Cells |
| `generate_primal_assets.py` | Primal Ores, Primal Shard, Primal Crystal, Discovery Lens, Entangled Link |
| `generate_gui_assets.py` | GUI frames and icons |
| `generate_technical_sprites.py`, `generate_storage_sprites.py` | component, upgrade and guide icons from `artwork/components/` |
| `generate_component_assets.py`, `generate_advanced_material_icons.py`, `generate_metallurgy_assets.py`, `generate_device_assets.py` | item models and definitions of the illustrated icons |

Run a generator without arguments to write its files and with `--check` to compare them. `tools/README.md` lists every command. After any visual change run:

```sh
python3 tools/check_visual_style.py
python3 tools/check_assets.py
```

`check_visual_style.py` holds a short list of documented exceptions, each with its reason. Add one only for a deliberate design decision, never to silence a finding.

## Adding something new

**A block.** 1. Choose its crystal: the hue of the Matrix Crystal in its recipe. Add the block to the crystal table above, to `FAMILY` in `tools/machine_palette.py` and to `machineAccent`. 2. Describe its boxes and sprites in the generator of its family; reuse the casing sprites and the sealed or alcove body. 3. Give it an idle and an active state that differ only in lit parts. 4. Run the generator, the generator's `--check` and the style check.

**A material, component or tool item.** 1. Draw the icon as an SVG in `artwork/` in the style of the reference icons, or extend a painter. 2. Render it to `textures/item/` at 128 x 128. 3. Add the item to the generator that writes item models and definitions. 4. Run the style check; it verifies size, canvas use, anti-aliasing and outline.

**A module or another mounted part.** 1. Decide what the player sees when it is mounted and draw those sprites first. 2. Build the item from the same boxes and sprites. 3. Give its crystal the hue of its job and list the module in the crystal table. 4. Put the same crystal into its recipe.

**A GUI icon.** 1. Draw it in the icon generator as a 16 x 16 white-ink glyph on the pixel grid, with strokes of the same weight as the existing set. 2. Reference it from the screen with a tint from `UiPalette`. 3. Add a tooltip if the button has no label.

**A substance.** 1. Give its definition the real symbol or formula. A material without a formula gets a short code of two or three capitals. 2. Check its badge in a picker: the full symbol is readable, on one line or two. 3. Do not add a colour for it in code; badge colours are derived from the substance.

**A colour.** Do not. If a new meaning needs one, add it to the palette module, to the Java palette and to the tables above in the same change, and say what it means.

## Review checklist

- [ ] The style check and every generator `--check` pass.
- [ ] No generated file was edited by hand; the generator or the SVG changed with it.
- [ ] New block sprites are 32 x 32 or 16 x 16, in palette colours, lit from the top left.
- [ ] New geometry is on the half-pixel grid and small detail is in the sprite.
- [ ] A new item is either an illustrated 128 px icon or a block-style model, not a small sprite.
- [ ] Colour carries its meaning: crystal hue for identity, status colours for status, the Primal signature for Primal, and nothing else.
- [ ] Lit parts are emissive, animations loop, and the item of a machine shows it switched on.
- [ ] Every new button and tab has an icon; an icon-only button has a tooltip; empty slots have a ghost.
- [ ] Substance badges show full symbols.
- [ ] Seen in the game next to an older block and an older icon, the new one belongs to the same set.
- [ ] Where the choice is a matter of taste, the change shows two or three alternatives with a recommendation, and switching between them is one constant.
