# Visual style

ReMatter should look like one thing: every block, item, icon and screen follows the six rules below. Read them before you add or change a texture, a model, a recipe with a Matrix Crystal or a screen, and run the check at the end:

```sh
python3 tools/check_visual_style.py
```

The check reads the tables of this page that carry a `check:` mark, the recipes and both palettes. A colour is changed here, in the palette module and in the Java palette together, or not at all.

## The six rules

**S1. Blocks are machine-family pixel art.** Everything drawn in the world uses two texels per model pixel: a full block face is a 32 x 32 sprite, a small part a 16 x 16 one. Sprites are hard pixel clusters lit from the top left, without gradients, in the colours of `tools/family_palette.py`. Models are a few boxes on a half-pixel grid, written by a generator. One density and one palette are what make twenty machines read as a family.

**S2. Natural blocks sit in vanilla terrain.** A block that world generation places (the three Primal Ores) shows its vanilla host block from the vanilla texture, referenced at runtime and never copied, so resource packs keep working. The mod draws only an overlay on top, at the host's 16 px density. The overlay is animated and emissive.

**S3. Item icons are illustrated.** Materials, components and tools are 128 x 128 drawings with a clean dark outline, soft facet shading and a three-quarter view from the upper left. A 16 px sprite beside them looks like a different mod, so there are none.

**S4. Mounted hardware looks the same in the hand and in the machine.** Matter Cells, Primal Cells, the Cell Housing and the five industrial modules are block-style models built from the sprites the player sees when the part is mounted.

**S5. Colour means something, and the recipe decides it.** The casing is neutral graphite. Whatever is crafted with a coloured Matrix Crystal wears exactly that crystal's hue as an accent: every crystal of the recipe, and no crystal that is not in it. Status colours mean status only. Primal Matter has a signature of its own. Every colour comes from one source.

**S6. Screens use the same graphite and one icon set.** Every tab and every action has an icon; compact controls use the icon alone and a full tooltip. No displayed text is shortened or cropped. An empty slot shows what belongs in it, and a substance badge always shows the full symbol.

## Palette

`tools/family_palette.py` and `tools/machine_palette.py` are the source. `UiPalette.java` mirrors them for the screens. The generated, server-safe `PrimalVisualColors.java` gives Primal Cell item bars the same Body and fault colours without loading client code. The check compares the palettes with the tables below; the cell generator and tool tests also compare this item-bar mirror.

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
| Automatic port base | `PORT_AUTOMATIC` | `#4d5a64` | neutral seat and fallback of the seven-colour Automatic legend |
| Contact | `COPPER` | `#ae6f49` | electrical contacts, badges and other copper metal |
| Label | `IVORY` | `#e4decc` | type plates, tier bars, dashes |
| Cable jacket | `JACKET` | `#181c22` | cable body, a step below the casing |

### The eight crystal hues

A Matrix Crystal takes its hue from the material it is coloured with. Whatever is then crafted with the crystal carries the hue on.

<!-- check:crystal-hues -->
| Crystal | Hex | Coloured with | Stands for | Carried by |
| --- | --- | --- | --- | --- |
| Amber | `#e3a94b` | gold dust | preparation | `material_preparator`, `industrial_preparator`, `grinding_unit`, `separation_unit`, `mixing_basin` |
| Copper | `#dc894f` | copper dust | decomposition | `decomposer` |
| Mint | `#57cddb` | diamond powder | stored matter | `matter_drive`, `matter_terminal`, `cell_transfer_station` |
| Leaf | `#7dd27f` | emerald dust | synthesis | `material_synthesizer` |
| Ice | `#d7d1bc` | Silith | analysis | `analyzer` |
| Ember | `#ea4848` | redstone | heat | `sinter_furnace`, `crystal_generator`, `industrial_crystal_generator`, `energy_cube`, `reinforced_energy_cube`, `resonant_energy_cube` |
| Blue | `#6794f1` | lapis dust | network link | `network_interface` |
| Violet | `#b381e8` | amethyst dust | reaction | `chemical_reactor`, `industrial_reactor`, `field_coil`, `reaction_chamber`, `primal_converter`, `primal_cell_16k`, `primal_cell_64k` |

"Carried by" lists the result of every recipe that holds the crystal. The check reads the recipes and reports a row that is out of date.

**The recipe rule.**

- If a coloured Matrix Crystal is an ingredient, the result shows that crystal's hue in exactly the value of the table: at least four texels (one model pixel) of a sprite the block or its item shows, or 64 pixels of an illustrated icon. The lit state counts. A darker shade alone does not: a dim part may be a shade, but somewhere the hue itself appears.
- A recipe with two different crystals gives a result with both hues.
- Something crafted from a part that already carries a hue may keep that hue; it does not have to.
- A recipe that takes a crystal apart again returns the clear crystal and its pigment. Nothing keeps the hue.
- No crystal in the recipe chain means no crystal hue: the part is drawn in neutrals. Decoration never borrows one of the eight values.
- The screen of a machine uses the hue of its crystal as its accent. A machine without a crystal has a neutral accent.
- Choose the crystal of a new recipe by what it stands for. Then the hue follows from the recipe, and a player who sees a green part knows a Leaf crystal went into it.

**When a hue is not a crystal.** These are the only other meanings, and each has its own values or its own place:

- *Status* has the four LED colours below: separate values, used only for small lamps and status icons.
- *Port mode.* The ring of a port uses seven of the hues as the legend of its mode (`PORT_MODES`): on the Machine Port and on a configured side of a machine, and only there. Automatic shows all seven roles in a static segmented ring, with no Primal hues or travelling spectrum. The same functional exception gives Energy Cube ports Blue for input and Ember for output, accompanied by arrows toward or away from the contact. These colours describe direction, never tier or charge.
- *Primal Matter* has the signature below.
- *A material's own colour.* An illustrated icon shows the material as it is: copper dust is copper-coloured without a crystal. In pixel art the copper of contacts and badges is `COPPER`, not the Copper crystal hue. The Matter Cable's copper-conductor stripe and the matching band on the Energy Cube crafted with that cable are the documented uses of the crystal value for the metal.

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
| Matter Cable | `CABLE_TIERS['matter_cable']['stripe']` | `#dc894f` | 2 texels, solid: the copper conductor |
| Reinforced Matter Cable | `CABLE_TIERS['reinforced_matter_cable']['stripe']` | `#54bfac` | 4 texels, solid |
| Resonant Matter Cable | `CABLE_TIERS['resonant_matter_cable']['stripe']` | `#be8fbb` | 4 texels, dashed |

Each Energy Cube contains the matching cable in its independent recipe and inherits that cable's exact stripe on its four identical casing sides and connector seats. This is a material tier band, separate from status colours. The Basic Cube's copper stripe is the documented exception to the rule that a crystal hue requires that crystal: its cable supplies the copper conductor. Every Cube still wears its own recipe's Ember hue on the energy core and a casing identity plate, including at zero charge; the cable band never replaces that identity.

<!-- check:energy-cable-tiers -->
| Energy Cube | Cable ingredient | Hex |
| --- | --- | --- |
| `energy_cube` | `matter_cable` | `#dc894f` |
| `reinforced_energy_cube` | `reinforced_matter_cable` | `#54bfac` |
| `resonant_energy_cube` | `resonant_matter_cable` | `#be8fbb` |

`ENERGY_PORT_STYLE` selects a compact 6 px, trimmed 7 px or recommended balanced 8 px square seat. All are centered at the block-face center and accept the native cable's 6 px flange. `ENERGY_CUBE_LOOK` independently selects the recommended recessed core, closed capacitor cabinet or cartridge rack. All three retain the same four-way casing and port layout within the 16-box network casing budget.

The cable-tier band stays on the seat's outer rim and the casing. Inside it the functional direction ring uses the following shared values; the arrow points into the copper contact for input and away from it for output. Identically configured horizontal faces remain identical. A disabled face is still unbroken casing.

<!-- check:energy-port-directions -->
| Direction | Constant | Hex | Shape |
| --- | --- | --- | --- |
| Input | `PORT_ENERGY_INPUT` | `#6794f1` | arrow toward the contact |
| Output | `PORT_ENERGY_OUTPUT` | `#ea4848` | arrow away from the contact |

`PORT_AUTOMATIC_STYLE` selects the recommended broad `segments`, compact `checker`, or `paired_bands` legend. Every alternative uses the same seven existing role colours; the static segmented ring is easiest to read at a distance. It changes no port permission.

### The Primal signature

Primal Matter has the mod's animated prismatic signature. It is ultraviolet: a white-hot core in an ultraviolet body, and a spectrum of five stops that shows in the crystals and travels through them. Static functional port legends remain separate from this signature.

<!-- check:primal-signature -->
| Role | Name | Hex | Stop | Use |
| --- | --- | --- | --- | --- |
| Core | `PRIMAL_CORE` | `#ffffff` | | white-hot centre of every Primal crystal |
| Glow | `PRIMAL_CORE_SOFT` | `#efe6ff` | | halo around the core |
| Light | `PRIMAL_LIGHT` | `#ae8eff` | | facet turned to the light; Primal text on screens |
| Body | `PRIMAL_BODY` | `#8a5cff` | 3 | ultraviolet body; the flat Primal accent of screens |
| Deep | `PRIMAL_DEEP` | `#4c24b2` | | facet in shade |
| Void | `PRIMAL_VOID` | `#1c0e4a` | | inside of a hollow |
| Ink | `PRIMAL_INK` | `#1e1546` | | contour of the illustrated Primal icons |
| Teal | `PRIMAL_TEAL` | `#46e0c8` | 1 | spectrum |
| Cool | `PRIMAL_COOL` | `#5cc8ff` | 2 | spectrum |
| Warm | `PRIMAL_WARM` | `#ff6ad5` | 4 | spectrum |
| Gold | `PRIMAL_GOLD` | `#ffb37a` | 5 | spectrum |

- The spectrum runs teal, cool, body, warm, gold and back the same way. It never jumps from gold to teal.
- In the world Primal Matter is never one flat colour. An ore shows at least three stops in every single frame, each crystal with its white core, and the animation moves the hues along the spectrum.
- A single stop is never "the Primal colour". Where a screen needs one flat value it is Body for fills, edges and icon tints and Light for text.
- The signature belongs to Primal Matter alone. A reactor never uses it, and a Primal part uses Violet `#b381e8` only when a Violet crystal is in its recipe; then the crystal is a flat, static part beside the animated signature, so the two cannot be mistaken for each other.

<!-- check:primal-devices -->
| Primal device | Shows the signature in |
| --- | --- |
| `deepslate_primal_ore`, `nether_primal_ore`, `end_primal_ore` | the crystals of the overlay |
| `primal_cell_16k`, `primal_cell_64k`, `primal_cell_256k` | the rail of the cartridge and the faceplate in the Drive |
| `primal_converter` | the window of the front; Body is its screen accent |
| `entangled_link` | the lens on each side |

## Blocks and models (S1)

- **Density.** Two texels per model pixel. A sprite is 32 x 32 for a block face or 16 x 16 for a small part. Faces are mapped by position, so a sprite never stretches.
- **Grid.** Every box corner is a multiple of 0.5 px. A feature that stands alone is at least 1 px in two of its dimensions; 0.5 px is only the depth of a step or an inset. Boxes stay inside the block; the ring of a port plate alone stands half a pixel proud of it.
- **Geometry or texture?** Geometry carries the silhouette and real depth: the casing, an alcove, a bay, a showpiece, a light that stands proud. Everything else is drawn into the sprite: bolts, seams, vents, labels, keys. If a detail would need a box thinner than 1 px, it is a texture detail.
- **Faces.** Only visible faces are written. No two faces lie in the same plane, face the same way and overlap. An outer face on the block boundary carries its `cullface`. A showpiece holds at most two rotated boxes; housings hold none.
- **Sprites.** Palette values only, at most 24 colours in a frame and at most six shades of one hue; alpha 0 or 255. Light comes from the top left: a lit edge on top and left, a shaded edge on the right and bottom, one texel wide.
- **Lights and screens.** `textures/entity/effect_light.png` is a white tint mask for renderer effects; its white is technical ink, not a Primal identity accent. A lit part has `light_emission` 15 and no shading, so it keeps its brightness between other blocks. Switching a machine on swaps the sprite of its lit parts and nothing else.
- **Animation.** A vertical strip of square frames with a `.png.mcmeta` beside it. Working glows, liquids and energy use at least 16 frames with `interpolate: true`; every frame keeps the same transparent pixels and palette. The red fault blink keeps its deliberate hard switch. Existing Primal signature strips retain their own timing and interpolation.
- **Never by hand.** A generator writes the sprites, models, block states and item definitions. To change one, change the generator and run it.

### Working motion

Recipe progress measures completed work; it is never the clock of a moving mechanism. A monotonic client frame clock, measured at twenty ticks per real second, drives movement through a shared eased work envelope. It pauses with the game and stays independent of the server's tick rate. Server progress updates estimate only a modest speed change and the size of slow-growing items. Fast batches show a steady process item rather than making it disappear every few frames.

Each mechanism shows its job: the Chemical Reactor exchanges liquid between its two columns, the Decomposer closes its jaws, the Analyzer passes a ring over its sample, the Material Preparator presses or mixes, and the Synthesizer sends pulses from its focus to the product. Industrial rings and module beams use continuous motion with bounded speed. They slow when work waits and resume from their stopped pose. Reciprocating mechanisms return to rest when their job is gone; rotary parts retain their stopped angle.

The small status lamp is off while idle, uses the device hue while working, and breathes status blue over 60 ticks while a loaded job waits. `WAITING_LIGHT` retains the alternative with no waiting lamp. The menu gives the actual waiting reason. A brief visual hold bridges processing gaps: eight server ticks for processors and twenty for Crystal Generators. That hold never authorizes another operation, energy payment or item movement.

Moving boxes sample the same block atlas and face lighting as the hardware around them. Their rest and stroke-end coordinates come from `tools/renderer_parts.py`, which writes `client/anim/RendererParts.java` and checks their composition with the baked model. Endpoints stay on the half-pixel grid; moving edges are drawn each frame. A new moving part needs a rest pose, both stroke endpoints, a speed bound and a native time sequence. A shader or full-pack rendering path must be checked separately from a unit test.

Energy Cubes keep the same four horizontal faces. Core pulses move up for net charging, down for net discharge, and stop when storage stays steady. Flow is sampled coarsely with hysteresis so item charging or a balanced cable network cannot flicker the block state every tick.

## Connections

Where a cable, a pipe, a hopper or a neighbouring device can attach is one table, `PortSides.java`. Cables, every capability, direct links between devices, the side configuration, saved data, the block and the screen read that table and nothing else.

- **The front is never a connection.** Nothing attaches to it, nothing is exposed on it and it cannot be configured. The three Energy Cubes are the one exception: their four sides are identical by design, the front included.
- **Only a closed face can be a connection.** The top of a bench is its open working area, so it never is. On a sealed device the top is a connection that starts Disabled: the owner enables it where a hopper or a cable from above is wanted. `PortSides.SEALED_TOP` is the one switch for this: `OPT_IN` (recommended), `CONNECTION` (the top starts Automatic like the other sides) or `CLOSED`. The generators read it.
- **A connection face shows a port plate only while enabled.** Disabled connection faces are plain casing: no seat, ring, well or Cube charge window. Permanently unavailable faces retain their device's front or open working area, without connector hardware. An enabled connection shows the plate: the seat on the casing and a ring that stands half a pixel proud of it. Automatic wears all seven role colours in static segments; a specific role wears only its own colour. Energy Cubes have Blue input and Ember output rings with opposite direction arrows, separate from their cable-tier bands.
- **The plate is a block model.** The body of a device leaves its closed sides out; a block state entry of the type `rematter:port_plates` lists, per side and role, the model that draws the side, and the game picks one by the roles the device publishes. Plates are part of the chunk mesh: they do not pop in with distance, and no renderer draws them. The ring is the only geometry of the family outside the block; a solid neighbour hides it.
- **The screen shows the block.** The side diagram of a settings screen names the six sides around the front. A side that can never be a connection is sunk, cannot be clicked and says why. A connection is a control: a left click steps its role forward, a right click or Shift steps it back, and its glyph is the plate of the block in the same ring colour.

<!-- check:port-sides -->
| Device | Connections | Never a connection |
| --- | --- | --- |
| `analyzer`, `decomposer`, `material_synthesizer`, `chemical_reactor`, `material_preparator`, `hand_preparator`, `sinter_furnace` | left, right, back, bottom | front; top (open working area) |
| `matter_drive`, `network_interface`, `primal_converter`, `crystal_generator`, `industrial_crystal_generator` | left, right, back, bottom, top (starts Disabled) | front |
| `matter_terminal` | back (fixed, not configurable) | every other side |
| `energy_cube`, `reinforced_energy_cube`, `resonant_energy_cube` | front, left, right, back, top, bottom | none |
| `industrial_reactor`, `industrial_preparator` | none: a structure connects through its Machine Ports | all six |
| `cell_transfer_station` | none: it is operated by hand | all six |

The check compares this table with the block states, the ring colours of `MachinePortMode.java` with `PORT_MODES`, the seven-colour Automatic legend and Cube direction colours with both palettes, and reports a body model that still draws a face of its port plates. `PortAppearance` is a rendering discriminator only: saved roles and transfer permissions remain separate.

## Natural blocks (S2)

The model of an ore stacks three boxes of the full block, like the vanilla grass block: the host, a socket and the glow. The host names the texture of the vanilla block (`minecraft:block/deepslate`, `minecraft:block/netherrack`, `minecraft:block/end_stone`). The socket and the glow are the mod's overlays: 16 px wide, named `*primal_ore*`, never a copy of the stone. The socket is lit by the world, so its dark outline does not glow. The glow layer carries the Primal signature, is animated and has `light_emission`. This is the one place where faces share a plane on purpose.

## Item icons (S3)

- **Canvas.** 128 x 128, 8-bit RGBA, transparent background. The longer side of the drawing covers 96 to 120 px; at least 4 px stay clear on every side.
- **Outline.** A closed outline 3 to 5 px wide in a dark shade of the object's own colour, anti-aliased against the background.
- **Shading.** Flat facets in three to five steps of one colour, a soft highlight toward the upper left, the darkest facet at the lower right. No texture noise, no photographic gradients.
- **View.** Three-quarter view from the upper left, as if the object lay on a table in front of you.
- **Hue.** An item crafted with a coloured crystal has one flat facet or inlay in exactly the crystal's value.
- **Source.** New and changed icons use an SVG under `artwork/` rendered by its tool, or a deterministic painter. The original painted PNGs are canonical artwork sources recorded by SHA-256 in `artwork/painted-icon-sources.json`; this closed set is preserved byte for byte until an icon gets a reproducible replacement. The exception concerns provenance only: canvas, outline, colour and shading rules still apply. A new or changed hand-edited PNG without a source cannot be reproduced.
- **References.** Crystals: `amber_matrix_crystal`. Powders: `gold_dust`. Ingots: `rhevon_ingot`. Circuits: `control_circuit`. Upgrades: `speed_upgrade`. Frames: `machine_frame`.

A crystal with a hue is opaque and its body is the hue of the table above. Only the uncoloured Matrix Crystal is clear.

## Mounted hardware (S4)

The item of a cell or a module is built from the same boxes and sprites as the part in the machine, at the same density. A cell item shows the faceplate, tier bar and status light the Matter Drive draws in its bay. Blocks follow the same rule by construction: the item of a machine is its block model, switched on.

## Screens (S6)

- **Surfaces.** Panels, buttons, insets and slots are nine-slice sprites in the casing colours of the table below. Device colour appears in the title strip and in accents.
- **Icons.** 16 x 16 sprites in `textures/gui/sprites/icon/`, drawn in white ink with one lighter tone and tinted when drawn, so one sprite serves every state. Status lamps are the only icons with baked colours, and those are the status colours.
- **Buttons.** A primary action may pair an icon with a full label. Compact controls, tabs and secondary actions use the icon alone with a complete tooltip naming the action and explaining it. Accessible names remain complete.
- **Slots.** An empty slot shows a ghost icon of what belongs in it.
- **Text.** Show every name, formula and hint without truncation. Quantities may use standard compact units such as k and M; their exact values remain available on hover. Reflow or wrap text, scroll an overflowing list, or scale a bounded single-line readout without dropping characters. Never append an ellipsis or clip displayed information. The same rule covers JEI and EMI views.
- **Substance badges.** A badge shows the complete symbol of the substance. A long formula breaks into two lines at an element; it is never cut with an ellipsis.
- **Colours in code.** Every colour of a screen or renderer is a constant of `UiPalette.java`. Other client classes hold no colour literals.

The side controls use a compact unfolded face net around the front, with complete side and mode names on hover and in narration. `SideDiagram.LAYOUT` selects recommended `FACE_NET`, the two-row `COMPACT_GRID`, or a `STRIP`; all retain the same reserved screen area and the same forward/backward role controls. Automatic repeats the block's seven-colour ring, and Cube input/output use the same Blue/Ember direction colours and opposite arrows.

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
| `port_plates.py` (shared by the three generators around it) | the port plates of every device: plain face and plate per side and role, and the `rematter:port_plates` block state entry |
| `renderer_parts.py` (shared by the family and module generators) | moving hardware coordinates, generated Java constants and composition checks at rest and stroke ends |
| `generate_progression_assets.py` | Primal Converter and both Crystal Generators |
| `generate_energy_assets.py` | the three Energy Cube tiers, charge0–8 core models, independent recipes, loot and recipe-book advancements |
| `generate_module_assets.py` | the five industrial modules |
| `generate_cell_models.py` | Cell Housing, Matter Cells and Primal Cells |
| `generate_cell_transfer_assets.py` | unpowered Cell Transfer Station, three empty source and three target bays, independent recipe, loot and recipe-book advancement |
| `generate_primal_assets.py` | Primal Ores, Primal Shard, Primal Crystal, Discovery Lens, Entangled Link |
| `generate_gui_assets.py` | GUI frames and icons |
| `generate_technical_sprites.py`, `generate_storage_sprites.py` | component, upgrade and guide icons from `artwork/components/` |
| `generate_illustrated_icons.py` | Ice Matrix Crystal, Matrix Cluster, Pressure Module and Parallel Upgrade icons, with their checksums in `artwork/components/illustrated-rendered.json` |
| `generate_component_assets.py`, `generate_advanced_material_icons.py`, `generate_metallurgy_assets.py`, `generate_device_assets.py` | item models and definitions of the illustrated icons |

Run a generator without arguments to write its files and with `--check` to compare them. `tools/README.md` lists every command. After any visual change run:

```sh
python3 tools/check_visual_style.py
python3 tools/check_assets.py
```

`check_visual_style.py` holds a short list of documented exceptions, each with its reason. Add one only for a deliberate design decision, never to silence a finding.

## Adding something new

**A block.** 1. Write its recipe first. The Matrix Crystal in it is its hue; choose the crystal by what it stands for. 2. Add the block to "Carried by" in the crystal table, to `FAMILY` in `tools/machine_palette.py` and, for a machine, to `machineAccent`. 3. Describe its boxes and sprites in the generator of its family; reuse the casing sprites and the sealed or alcove body, and take its closed sides from `port_plates.py`. Decide in `PortSides.java` which sides can be a connection and add the device to the port table. Draw the accent parts in the crystal's value. 4. Give it an idle and an active state that differ only in lit parts. 5. Run the generator, the generator's `--check` and the style check.

**A material, component or tool item.** 1. Draw the icon as an SVG in `artwork/` in the style of the reference icons, or extend a painter. 2. Render it to `textures/item/` at 128 x 128. 3. Add the item to the generator that writes item models and definitions. 4. If its recipe holds a coloured crystal, give the drawing a facet in the crystal's value and add the item to the crystal table. 5. Run the style check; it verifies size, canvas use, anti-aliasing, outline and hue.

**A module or another mounted part.** 1. Decide what the player sees when it is mounted and draw those sprites first. 2. Build the item from the same boxes and sprites. 3. Put the crystal of its job into its recipe, show that crystal on the part and list the part in the crystal table.

**A recipe change.** Adding, removing or swapping a coloured crystal changes how the result looks. Change the sprites and the crystal table in the same change.

**A GUI icon.** 1. Draw it in the icon generator as a 16 x 16 white-ink glyph on the pixel grid, with strokes of the same weight as the existing set. 2. Reference it from the screen with a tint from `UiPalette`. 3. Add a tooltip if the button has no label.

**A substance.** 1. Give its definition the real symbol or formula. A material without a formula gets a short code of two or three capitals. 2. Check its badge in a picker: the full symbol is readable, on one line or two. 3. Do not add a colour for it in code; badge colours are derived from the substance.

**A colour.** Do not. If a new meaning needs one, add it to the palette module, to the Java palette and to the tables above in the same change, and say what it means.

## Review checklist

- [ ] The style check and every generator `--check` pass.
- [ ] No generated file was edited by hand; the generator or the SVG changed with it.
- [ ] New block sprites are 32 x 32 or 16 x 16, in palette colours, lit from the top left.
- [ ] New geometry is on the half-pixel grid and small detail is in the sprite.
- [ ] A new device is in the port table: its front is closed, a disabled side is plain casing, and its body draws no face that belongs to a port plate.
- [ ] A new item is either an illustrated 128 px icon or a block-style model, not a small sprite.
- [ ] Every coloured crystal of a recipe shows on its result in the exact value, and nothing wears a crystal hue without the crystal.
- [ ] Colour carries its meaning: crystal hue for what the recipe holds, status colours for status, the Primal signature for Primal, and nothing else.
- [ ] Lit parts are emissive, animations loop, and the item of a machine shows it switched on.
- [ ] Every new button and tab has an icon; an icon-only button has a tooltip; empty slots have a ghost.
- [ ] Compact controls use icons with full tooltips; all displayed text and substance symbols remain complete.
- [ ] Seen in the game next to an older block and an older icon, the new one belongs to the same set.
- [ ] Where the choice is a matter of taste, the change shows two or three alternatives with a recommendation, and switching between them is one constant.
