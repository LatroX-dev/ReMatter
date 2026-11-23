#!/usr/bin/env python3
"""Generate the sealed-cube and alcove machine family.

Owns the sprites, block models, block states and item definitions of the
Matter Drive, Matter Terminal, Network Interface, Machine Port, the three
Matter Cables, Reactor Casing, Reactor Glass, both industrial controllers and
the Chemical Reactor and the six processing machines. Every model is a short list of boxes on a half-pixel
grid; only faces that can be seen are written, faces on the block boundary
carry a cullface, and sprites sit at two texels per pixel. Generation fails
when a model would flicker or break one of the family rules.
"""
from itertools import combinations, product
from pathlib import Path
import argparse

from family_geometry import (ROTATION, Box, GeometryError, build, check_boxes, check_composition, emitted,
                             mapped_uv, turned_parts)
from family_palette import CABLE_TIERS, PORT_MODES
from bench_showpieces import DEVICES as BENCHES, boxes as bench_boxes, work_boxes as bench_work, show_skin as bench_skin, work_skin as bench_work_skin, crank_boxes
import family_sprites
from family_sprites import ANIMATIONS, CABLE_ANIMS, CABLE_ANIM, CABLE_LOOKS, CABLE_LOOK, sprites
from hardware_sprites import OwnedFiles
import port_plates
from reactor_showpiece import (PREFIX as REACTOR_SPRITE_PREFIX, REACTOR_LIQUID, column_boxes, column_skin,
                              liquid_boxes, liquid_skin)

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/rematter'
MODEL = 'rematter:block/'
HORIZONTAL = ('north', 'east', 'south', 'west')
# Model directions in which a sprite's u and v grow on a face seen from outside.
NATURAL = {'north': ('-x', '-y'), 'south': ('+x', '-y'), 'west': ('+z', '-y'), 'east': ('-z', '-y'),
           'up': ('+x', '+z'), 'down': ('+x', '-z')}
# Sprites that only the block entity renderer draws: mounted cell faceplates and status lights.
RENDERER_SPRITES = ('drive_cell_4m', 'drive_cell_16m', 'drive_cell_64m', 'drive_cell_256m', 'drive_cell_unknown',
                    'led_blue', 'led_green', 'led_amber', 'led_red', 'chemical_processor_columns_shuttle', 'chemical_processor_columns_liquid', 'chemical_processor_columns_glass', 'drive_primal_cell_16k', 'drive_primal_cell_64k', 'drive_primal_cell_256k', 'drive_primal_cell_1m', 'drive_void_matter_cell')
# Most boxes a block model of each kind may hold.
BUDGET = {'bench_part': 4, 'network': 16, 'process': 24, 'part': 2, 'reactor_part': 3, 'cable': 4, 'cube': 1}


def region(box, face, origin, sprite=32):
    """A face mapped onto the sprite region whose top left texel is origin."""
    return mapped_uv(face, box.lo, box.hi, *NATURAL[face], origin, sprite)


class Family:
    """Collects the files of one run and the facts the final checks need."""

    def __init__(self):
        self.models = {}
        self.item_models = {}
        self.blockstates = {}
        self.items = {}
        self.compositions = []

    def model(self, name, kind, textures, boxes, skin, *, context=None, skip=(), particle=None, omit=()):
        """omit names the outer faces that the port plates of the device draw instead of this model."""
        check_boxes(name, boxes)
        if len(boxes) > BUDGET[kind]:
            raise GeometryError(f'{name}: {len(boxes)} boxes exceed the {kind} budget of {BUDGET[kind]}')
        elements = build(boxes, skin, context)
        for element, box in zip(elements, boxes):
            for face in [face for face in element['faces']
                         if (box.name, face) in skip or (face in omit and box.on_boundary(face))]:
                del element['faces'][face]
        # A box whose only visible faces belong to the plates is not written at all.
        kept = [(box, element) for box, element in zip(boxes, elements) if element['faces']]
        boxes, elements = [box for box, _ in kept], [element for _, element in kept]
        used = {face['texture'][1:] for element in elements for face in element['faces'].values()}
        if used != set(textures):
            raise GeometryError(f'{name}: texture keys {sorted(set(textures) ^ used)} are unused or undefined')
        table = {'particle': MODEL + (particle or next(iter(textures.values())))}
        table.update({key: MODEL + value for key, value in textures.items()})
        self.models[name] = {'parent': 'minecraft:block/block', 'textures': table, 'elements': elements}
        return emitted(boxes, elements)

    def child(self, name, parent, textures):
        table = {'particle': MODEL + next(iter(textures.values()))}
        table.update({key: MODEL + value for key, value in textures.items()})
        self.models[name] = {'parent': MODEL + parent, 'textures': table}

    def compose(self, label, parts, allowed_overlap=()):
        check_composition(label, parts, allowed_overlap)
        self.compositions.append(label)

    def machine(self, name, parts, ports=None):
        """Body by facing, plus the part model of the current activity and the port plates of the closed sides.

        The item shows the machine switched on and unconfigured: lit parts carry the device colour, which is
        what tells the icons of the family apart in an inventory, and every side shows its initial role."""
        states = [{'when': {'facing': facing}, 'apply': {'model': MODEL + name, **ROTATION[facing]}}
                  for facing in HORIZONTAL]
        for active, part in (('false', parts[0]), ('true', parts[1])):
            states += [{'when': {'facing': facing, 'active': active},
                        'apply': {'model': MODEL + part, **ROTATION[facing]}} for facing in HORIZONTAL]
        shown = [MODEL + name, MODEL + parts[1]]
        if ports:
            states += [{'when': {'facing': facing}, 'apply': {'type': ports['type'], **ROTATION[facing],
                                                              'default': ports['default'], 'sides': ports['sides']}}
                       for facing in HORIZONTAL]
            shown += port_plates.default_models(ports)
        self.blockstates[name] = {'multipart': states}
        self.items[name] = {'model': {'type': 'minecraft:composite', 'models': [
            {'type': 'minecraft:model', 'model': model} for model in shown]}}

    def cube(self, name):
        self.blockstates[name] = {'variants': {'': {'model': MODEL + name}}}
        self.items[name] = {'model': {'type': 'minecraft:model', 'model': MODEL + name}}


def sealed_body(plinth):
    """Shell of a sealed cube: a core behind a two-pixel front zone of posts, plinth and cap."""
    return [Box('core', (0, 0, 2), (16, 16, 16)),
            Box('post_left', (15, 0, 0), (16, 16, 2)),
            Box('post_right', (0, 0, 0), (1, 16, 2)),
            Box('plinth', (1, 0, 0), (15, plinth, 2)),
            Box('cap', (1, 15, 0), (15, 16, 2))]


def sealed_skin(sides, recess='inner'):
    """Front art on every front plane, a casing sprite per outer side, a dark sprite inside steps."""
    def skin(box, face):
        if face == 'north':
            return 'front' if box.plane('north') <= 1 else recess
        return sides[face] if box.on_boundary(face) else recess
    return skin


def with_parts(family, name, kind, textures, body, skin, parts, part_textures, part_skin, particle,
               part_kind='part', casing='plain', plates=True, renderer=()):
    """A machine body, its idle and active part models and its port plates, checked together.

    Part boxes are screens and lights; they are lit flat in both states, so switching on changes their
    sprite and glow and nothing else. The closed sides belong to the port plates: the body leaves those faces
    out, and the checks run with every side blank and with every side connected. A structure controller has
    no connections of its own and keeps its casing faces (plates=False)."""
    ports = port_plates.definition(name, casing) if plates else None
    drawn = family.model(name, kind, textures, body, skin, context=body + parts, particle=particle,
                         omit=port_plates.omitted_faces(name) if plates else ())
    names = []
    for state, emissive in (('idle', False), ('active', True)):
        boxes = [Box(box.name, box.lo, box.hi, emissive=emissive, step=box.step, flat=True) for box in parts]
        part_name = f'{name}_{state}_parts'
        shown = family.model(part_name, part_kind, part_textures[state], boxes, part_skin, context=body + boxes)
        if plates:
            compose_with_plates(family, f'{name} {state}', name, drawn + shown)
        else:
            family.compose(f'{name} {state}', drawn + shown)
        if renderer:
            visible=[box for box in boxes if box.name not in renderer]
            used={part_skin(box,face) if isinstance(part_skin(box,face),str) else part_skin(box,face)[0]
                  for box in visible for face in ('north','south','east','west','up','down')}
            world_name=part_name.replace('_parts','_world_parts')
            family.model(world_name,part_kind,{key:value for key,value in part_textures[state].items() if key in used},
                         visible,part_skin,context=body+visible,particle=particle)
            names.append(world_name)
            if state=='idle':family.models.pop(part_name)
        else:names.append(part_name)
    family.machine(name, names, ports)
    if renderer:
        for child in family.items[name]['model']['models']:
            if child['model']==MODEL+names[1]:child['model']=MODEL+f'{name}_active_parts'


def compose_with_plates(family, label, device, parts):
    for connected in (False, True):
        family.compose(f'{label} {"connected" if connected else "blank"}',
                       parts + port_plates.parts(device, connected), port_plates.OVERLAP)


# --------------------------------------------------------------------------- Matter Drive
# Bay b sits in column b // 3 (0 is the viewer's left, at high x) and row b % 3 (0 is the top).
DRIVE_BAY_ORIGINS = [(x, y) for x in (9, 1) for y in (10, 6, 2)]


def drive_cell_box(bay):
    x, y = DRIVE_BAY_ORIGINS[bay]
    return Box(f'bay{bay}_cell', (x, y, .5), (x + 6, y + 3, 2))


def drive_led_box(bay):
    x, y = DRIVE_BAY_ORIGINS[bay]
    return Box(f'bay{bay}_led', (x + 4.5, y + 1, 0), (x + 5.5, y + 2, .5), step=True)


def matter_drive(family):
    body = sealed_body(2) + [
        Box('band_left', (9, 13, .5), (15, 15, 2)),
        Box('band_right', (1, 13, .5), (7, 15, 2)),
        Box('mullion', (7, 2, 0), (9, 15, 2)),
        Box('rail_left_upper', (9, 9, 0), (15, 10, 2)),
        Box('rail_left_lower', (9, 5, 0), (15, 6, 2)),
        Box('rail_right_upper', (1, 9, 0), (7, 10, 2)),
        Box('rail_right_lower', (1, 5, 0), (7, 6, 2)),
    ]
    bar = Box('activity_bar', (10, 13.5, 0), (14, 14.5, .5), step=True)
    sides = dict.fromkeys(('south', 'east', 'west', 'down', 'up'), 'plain')
    textures = {'front': 'drive_front', 'bay': 'drive_bay'}
    ports = port_plates.definition('matter_drive')
    drawn = family.model('matter_drive', 'network', textures, body, sealed_skin(sides, 'bay'),
                         context=body + [bar], particle='casing_plain', omit=port_plates.omitted_faces('matter_drive'))
    names = []
    for state, emissive in (('off', False), ('on', True)):
        lit = Box(bar.name, bar.lo, bar.hi, emissive=emissive, step=True, flat=True)
        name = 'matter_drive_activity_' + state
        shown = family.model(name, 'part', {'bar': 'activity_' + state}, [lit], lambda box, face: 'bar',
                             context=body + [lit])
        names.append(name)
        # The renderer adds a faceplate and a status light to each occupied bay.
        cells = [(box, faces) for bay in range(6)
                 for box, faces in ((drive_cell_box(bay), ('north',)),
                                    (drive_led_box(bay), ('north', 'east', 'west', 'up', 'down')))]
        check_boxes('matter_drive bays', [box for box, _ in cells])
        compose_with_plates(family, 'matter_drive ' + state, 'matter_drive', drawn + shown)
        compose_with_plates(family, 'matter_drive full ' + state, 'matter_drive', drawn + shown + cells)
    family.machine('matter_drive', names, ports)


# --------------------------------------------------------------------------- Matter Terminal
def matter_terminal(family):
    body = sealed_body(4)
    screen = [Box('screen', (1, 4, 1), (15, 15, 2))]
    sides = dict.fromkeys(('south', 'east', 'west', 'down', 'up'), 'plain')
    textures = {'front': 'terminal_front', 'inner': 'casing_inner'}
    with_parts(family, 'matter_terminal', 'network', textures, body, sealed_skin(sides), screen,
               {'idle': {'screen': 'terminal_screen'}, 'active': {'screen': 'terminal_screen_active'}},
               lambda box, face: 'screen', 'casing_plain')


# --------------------------------------------------------------------------- Network Interface
def network_interface(family):
    body = sealed_body(2) + [
        Box('panel', (1, 2, 1), (15, 15, 2)),
        Box('port_boss', (4, 3, .5), (12, 11, 1), step=True),
        Box('antenna', (3, 12, .5), (13, 14, 1), step=True),
    ]
    lights = [Box('lens', (7, 6, 0), (9, 8, .5), step=True),
              Box('signal', (4, 12.5, 0), (12, 13.5, .5), step=True)]
    sides = dict.fromkeys(('south', 'east', 'west', 'down', 'up'), 'plain')
    textures = {'front': 'interface_front', 'inner': 'casing_inner'}
    with_parts(family, 'network_interface', 'network', textures, body, sealed_skin(sides), lights,
               {'idle': {'light': 'light_blue_standby'}, 'active': {'light': 'interface_light_active'}},
               lambda box, face: 'light', 'casing_plain')


# --------------------------------------------------------------------------- industrial controllers
def controller(family, name):
    body = sealed_body(2) + [Box('panel', (1, 2, 1), (15, 15, 2))]
    display = [Box('display', (3, 8, .5), (13, 13, 1), step=True)]
    sides = dict.fromkeys(('south', 'east', 'west', 'down', 'up'), 'casing')
    textures = {'casing': 'reactor_casing', 'front': name + '_front', 'inner': 'casing_inner'}
    with_parts(family, name, 'network', textures, body, sealed_skin(sides), display,
               {'idle': {'display': name + '_display', 'inner': 'casing_inner'},
                'active': {'display': name + '_display_active', 'inner': 'casing_inner'}},
               lambda box, face: 'display' if face == 'north' else 'inner', 'reactor_casing', plates=False)


# --------------------------------------------------------------------------- Machine Port, Casing, Glass
def machine_port(family):
    body = [Box('body', (0, 0, 0), (16, 16, 16))]
    family.model('machine_port', 'cube', {'casing': 'reactor_casing', 'face': 'machine_port_unformed'}, body,
                 lambda box, face: 'face' if face == 'north' else 'casing')
    states = [{'when': {'formed': 'false', 'facing': side}, 'apply': {'model': MODEL + 'machine_port', **rotation}}
              for side, rotation in ROTATION.items()]
    for mode in PORT_MODES:
        name = 'machine_port_' + mode
        family.child(name, 'machine_port', {'face': name})
        states += [{'when': {'formed': 'true', 'mode': mode, 'facing': side},
                    'apply': {'model': MODEL + name, **rotation}} for side, rotation in ROTATION.items()]
    family.blockstates['machine_port'] = {'multipart': states}
    family.items['machine_port'] = {'model': {'type': 'minecraft:model', 'model': MODEL + 'machine_port'}}


def structure_cubes(family):
    for name, texture in (('reactor_casing', 'reactor_casing'), ('reactor_glass', 'reactor_glass_pane')):
        family.model(name, 'cube', {'all': texture}, [Box('body', (0, 0, 0), (16, 16, 16))],
                     lambda box, face: 'all')
        family.cube(name)


# --------------------------------------------------------------------------- Matter Cables
CABLE_AXES = (('north', 'south'), ('east', 'west'), ('up', 'down'))
# Texel origins on the static housing sheet ({tier}.png) and emissive conduit sheet ({tier}_glow.png).
STRIP, CORE_CAP, HUB_FACE, FLANGE_FACE, FLANGE_SIDE, HUB_CAP_SIDE = (0, 0), (0, 8), (8, 8), (20, 8), (0, 20), (12, 20)
GLOW_STRIP_RECESSED, GLOW_STRIP_FULL, GLOW_HUB_FACE, GLOW_HUB_CHAMBER, GLOW_CAP = (0, 1), (0, 0), (8, 8), (0, 12), (20, 12)


def run_skin(box, face, key='cable', origin=STRIP):
    """The conduit wave or clamp strip follows the run: u grows along z on all four sides."""
    across = {'east': '-y', 'west': '-y', 'up': '+x', 'down': '+x'}[face]
    uv, rotation = mapped_uv(face, box.lo, box.hi, '+z', across, (origin[0] + int(box.lo[2] * 2) % 16, origin[1]))
    return key, uv, rotation


def cable_skin(box, face):
    name = box.name
    if name in ('arm_glass', 'centre_glass', 'shaft_glass', 'arm_bundle', 'centre_bundle', 'shaft_bundle', 'item_glass'):
        if face not in ('north', 'south'):
            return run_skin(box, face, 'glow', GLOW_STRIP_RECESSED)
        return ('glow', *region(box, face, GLOW_CAP))
    if name == 'core_glow':
        return ('glow', *region(box, face, CORE_CAP))
    if name in ('arm', 'shaft', 'centre', 'run') and box.emissive:
        if face not in ('north', 'south'):
            return run_skin(box, face, 'glow', GLOW_STRIP_FULL)
        if family_sprites.CABLE_LOOK == 'pcb_traces':
            return ('glow', *region(box, face, CORE_CAP))
        return ('cable', *region(box, face, CORE_CAP))
    if name in ('arm_clamp', 'centre_clamp', 'shaft_clamp', 'item_clamp_a', 'item_clamp_b'):
        if face not in ('north', 'south'):
            return run_skin(box, face, 'cable', STRIP)
        return ('cable', *region(box, face, CORE_CAP))
    if name == 'hub_glow':
        return ('glow', *region(box, face, GLOW_HUB_FACE))
    if name == 'hub_chamber':
        return ('glow', *region(box, face, GLOW_HUB_CHAMBER))
    if name in ('hub_bot', 'hub_top'):
        origin = HUB_FACE if face in ('up', 'down') else HUB_CAP_SIDE
        return ('cable', *region(box, face, origin))
    if name == 'flange' and face not in ('north', 'south'):
        across = '+y' if face in ('east', 'west') else '+x'
        return ('cable', *mapped_uv(face, box.lo, box.hi, across, '+z', FLANGE_SIDE))
    origin = {'hub': HUB_FACE, 'flange': FLANGE_FACE}.get(name, CORE_CAP)
    return ('cable', *region(box, face, origin))


def cable_geometry(look=None):
    """Part boxes, neighbor contexts, skipped faces, allowed hub overlaps and item run for all 4 CABLE_LOOKS."""
    look = family_sprites.CABLE_LOOK if look is None else look
    if look not in CABLE_LOOKS:
        raise GeometryError(f'Unknown CABLE_LOOK: {look}')
    core = Box('core', (6, 6, 6), (10, 10, 10))
    flange = Box('flange', (5, 5, 0), (11, 11, 1.5))

    if look == 'pcb_traces':
        # Dual PCB traces with bends & bridge crossovers on a dark graphite shaft, seamless glowing corners & PCB hubs.
        core_glow = Box('core_glow', (6, 6, 6), (10, 10, 10), emissive=True)
        centre = Box('centre', (6, 6, 6), (10, 10, 10), emissive=True)
        hub = Box('hub_glow', (5, 5, 5), (11, 11, 11), emissive=True)
        arm = Box('arm', (6, 6, 0), (10, 10, 6), emissive=True)
        shaft = Box('shaft', (6, 6, 1.5), (10, 10, 6), emissive=True)
        north_full, south_full = Box('north', (6, 6, 0), (10, 10, 6)), Box('south', (6, 6, 10), (10, 10, 16))
        parts = {
            'core': ([core_glow], [core_glow], ()),
            'straight': ([centre], [centre, north_full, south_full], ()),
            'junction': ([hub], [hub], ()),
            'arm': ([arm], [arm, core_glow], {('arm', 'north')}),
            'arm_device': ([flange, shaft], [flange, shaft, core_glow], ()),
        }
        overlaps = (('hub_glow', 'arm'), ('hub_glow', 'shaft'))
        item_boxes = [Box('run', (6, 6, 0), (10, 10, 16), emissive=True)]
        return parts, overlaps, item_boxes

    if look == 'fiber_trace':
        # 1. Sleek continuous dark graphite 4 x 4 conduit with razor-sharp uninterrupted 2-texel glowing fibre.
        centre = Box('centre', (6, 6, 6), (10, 10, 10), emissive=True)
        hub = Box('hub_glow', (5, 5, 5), (11, 11, 11), emissive=True)
        arm = Box('arm', (6, 6, 0), (10, 10, 6), emissive=True)
        shaft = Box('shaft', (6, 6, 1.5), (10, 10, 6), emissive=True)
        north_full, south_full = Box('north', (6, 6, 0), (10, 10, 6)), Box('south', (6, 6, 10), (10, 10, 16))
        parts = {
            'core': ([core], [core], ()),
            'straight': ([centre], [centre, north_full, south_full], ()),
            'junction': ([hub], [hub], ()),
            'arm': ([arm], [arm, core], {('arm', 'north')}),
            'arm_device': ([flange, shaft], [flange, shaft, core], ()),
        }
        overlaps = (('hub_glow', 'arm'), ('hub_glow', 'shaft'))
        item_boxes = [Box('run', (6, 6, 0), (10, 10, 16), emissive=True)]
        return parts, overlaps, item_boxes

    if look == 'glass_conduit':
        # 2. Mekanism-style smoked-glass 3 x 3 tube with metal end-clamps only at block joints & hubs.
        hub_bot = Box('hub_bot', (5, 5, 5), (11, 6, 11))
        hub_mid = Box('hub_chamber', (5.5, 6, 5.5), (10.5, 10, 10.5), emissive=True)
        hub_top = Box('hub_top', (5, 10, 5), (11, 11, 11))
        arm_clamp = Box('arm_clamp', (6, 6, 0), (10, 10, 1))
        arm_glass = Box('arm_glass', (6.5, 6.5, 1), (9.5, 9.5, 6), emissive=True)
        centre_glass = Box('centre_glass', (6.5, 6.5, 6), (9.5, 9.5, 10), emissive=True)
        shaft_glass = Box('shaft_glass', (6.5, 6.5, 1.5), (9.5, 9.5, 6), emissive=True)
        north_rec, south_rec = Box('north', (6.5, 6.5, 0), (9.5, 9.5, 6)), Box('south', (6.5, 6.5, 10), (9.5, 9.5, 16))
        parts = {
            'core': ([core], [core], ()),
            'straight': ([centre_glass], [centre_glass, north_rec, south_rec], ()),
            'junction': ([hub_bot, hub_mid, hub_top], [hub_bot, hub_mid, hub_top], ()),
            'arm': ([arm_clamp, arm_glass], [arm_clamp, arm_glass, core], {('arm_clamp', 'north')}),
            'arm_device': ([flange, shaft_glass], [flange, shaft_glass, core], ()),
        }
        overlaps = tuple((h, a) for h in ('hub_bot', 'hub_chamber', 'hub_top') for a in ('arm_glass', 'shaft_glass'))
        item_boxes = [
            Box('item_clamp_a', (6, 6, 0), (10, 10, 2)),
            Box('item_glass', (6.5, 6.5, 2), (9.5, 9.5, 14), emissive=True),
            Box('item_clamp_b', (6, 6, 14), (10, 10, 16)),
        ]
        return parts, overlaps, item_boxes

    if look == 'conduit_cage':
        # 3. Ender-IO-style compact armored 3 x 3 conduit bundle with heavy centre clamp & tight 5 x 5 node.
        hub_bot = Box('hub_bot', (5, 5, 5), (11, 6, 11))
        hub_mid = Box('hub_chamber', (5.5, 6, 5.5), (10.5, 10, 10.5), emissive=True)
        hub_top = Box('hub_top', (5, 10, 5), (11, 11, 11))
        arm_bundle = Box('arm_bundle', (6.5, 6.5, 0), (9.5, 9.5, 6), emissive=True)
        centre_clamp = Box('centre_clamp', (6, 6, 7), (10, 10, 9))
        centre_n = Box('centre_bundle', (6.5, 6.5, 6), (9.5, 9.5, 7), emissive=True)
        centre_s = Box('centre_bundle', (6.5, 6.5, 9), (9.5, 9.5, 10), emissive=True)
        shaft_clamp = Box('shaft_clamp', (6, 6, 1.5), (10, 10, 3))
        shaft_bundle = Box('shaft_bundle', (6.5, 6.5, 3), (9.5, 9.5, 6), emissive=True)
        north_rec, south_rec = Box('north', (6.5, 6.5, 0), (9.5, 9.5, 6)), Box('south', (6.5, 6.5, 10), (9.5, 9.5, 16))
        parts = {
            'core': ([core], [core], ()),
            'straight': ([centre_n, centre_clamp, centre_s], [centre_n, centre_clamp, centre_s, north_rec, south_rec], ()),
            'junction': ([hub_bot, hub_mid, hub_top], [hub_bot, hub_mid, hub_top], ()),
            'arm': ([arm_bundle], [arm_bundle, core], {('arm_bundle', 'north')}),
            'arm_device': ([flange, shaft_clamp, shaft_bundle], [flange, shaft_clamp, shaft_bundle, core], ()),
        }
        overlaps = tuple((h, a) for h in ('hub_bot', 'hub_chamber', 'hub_top') for a in ('arm_bundle', 'shaft_bundle'))
        item_boxes = [
            Box('item_clamp_a', (6, 6, 0), (10, 10, 2)),
            Box('item_glass', (6.5, 6.5, 2), (9.5, 9.5, 14), emissive=True),
            Box('item_clamp_b', (6, 6, 14), (10, 10, 16)),
        ]
        return parts, overlaps, item_boxes

    # 4. classic_sleek: clean smooth 4 x 4 silhouette without ribs, upgraded with dark refined graphite & emissive trace.
    centre = Box('centre', (6, 6, 6), (10, 10, 10), emissive=True)
    hub = Box('hub', (5, 5, 5), (11, 11, 11))
    arm = Box('arm', (6, 6, 0), (10, 10, 6), emissive=True)
    flange2 = Box('flange', (5, 5, 0), (11, 11, 2))
    shaft = Box('shaft', (6, 6, 2), (10, 10, 6), emissive=True)
    north_full, south_full = Box('north', (6, 6, 0), (10, 10, 6)), Box('south', (6, 6, 10), (10, 10, 16))
    parts = {
        'core': ([core], [core], ()),
        'straight': ([centre], [centre, north_full, south_full], ()),
        'junction': ([hub], [hub], ()),
        'arm': ([arm], [arm, core], {('arm', 'north')}),
        'arm_device': ([flange2, shaft], [flange2, shaft, core], ()),
    }
    overlaps = (('hub', 'arm'), ('hub', 'shaft'))
    item_boxes = [Box('run', (6, 6, 0), (10, 10, 16), emissive=True)]
    return parts, overlaps, item_boxes


def matter_cables(family):
    look = family_sprites.CABLE_LOOK
    geometry, overlaps, item_boxes = cable_geometry(look)
    drawn = {}
    part_textures = {}
    for part, (boxes, context, skip) in geometry.items():
        has_cable = any(not b.emissive for b in boxes)
        has_glow = any(b.emissive for b in boxes)
        textures = {}
        if has_cable:
            textures['cable'] = 'matter_cable'
        if has_glow:
            textures['glow'] = 'matter_cable_glow'
        part_textures[part] = tuple(textures)
        drawn[part] = family.model('matter_cable_geometry_' + part, 'cable', textures, boxes,
                                   cable_skin, context=context, skip=skip, particle='matter_cable')

    # Every combination of free sides, cable arms and device arms that the block state can show.
    for sides in product((None, 'arm', 'arm_device'), repeat=6):
        used = [(side, kind) for side, kind in zip(ROTATION, sides) if kind]
        connected = tuple(side for side, _ in used)
        centre_part = 'junction' if len(used) > 2 else 'straight' if connected in CABLE_AXES else 'core'
        parts = list(drawn[centre_part])
        if centre_part == 'straight' and connected != CABLE_AXES[0]:
            parts = turned_parts(parts, connected[0])
        for side, kind in used:
            parts += turned_parts(drawn[kind], side)
        check_composition('cable ' + ' '.join(f'{side}:{kind}' for side, kind in used), parts,
                          allowed_overlap=overlaps)
    family.compositions.append(f'cable ({look}): 729 side combinations')

    def exact(connected):
        return {side: str(side in connected).lower() for side in ROTATION}

    small = [group for count in range(3) for group in combinations(ROTATION, count) if group not in CABLE_AXES]
    for name in CABLE_TIERS:
        for part in geometry:
            child_tex = {'particle': name}
            if 'cable' in part_textures[part]:
                child_tex['cable'] = name
            if 'glow' in part_textures[part]:
                child_tex['glow'] = name + '_glow'
            family.child(name if part == 'core' else f'{name}_{part}', 'matter_cable_geometry_' + part, child_tex)
        states = [
            {'when': {'OR': [exact(group) for group in small]}, 'apply': {'model': MODEL + name}},
            {'when': {'OR': [dict.fromkeys(group, 'true') for group in combinations(ROTATION, 3)]},
             'apply': {'model': MODEL + name + '_junction'}},
        ]
        states += [{'when': exact(axis), 'apply': {'model': MODEL + name + '_straight', **ROTATION[axis[0]]}}
                   for axis in CABLE_AXES]
        for side, rotation in ROTATION.items():
            states.append({'when': {side: 'true', side + '_device': 'false'},
                           'apply': {'model': MODEL + name + '_arm', **rotation}})
            states.append({'when': {side: 'true', side + '_device': 'true'},
                           'apply': {'model': MODEL + name + '_arm_device', **rotation}})
        family.blockstates[name] = {'multipart': states}
        # The item shows one full straight run in the selected cable look.
        check_boxes(name + ' item', item_boxes)
        item_elements = build(item_boxes, cable_skin)
        item_tex = {'particle': MODEL + name}
        if any(not b.emissive for b in item_boxes) or look in ('fiber_trace', 'classic_sleek'):
            item_tex['cable'] = MODEL + name
        if any(b.emissive for b in item_boxes):
            item_tex['glow'] = MODEL + name + '_glow'
        family.item_models[name] = {
            'parent': 'minecraft:block/block',
            'textures': item_tex,
            'elements': item_elements}
        family.items[name] = {'model': {'type': 'minecraft:model', 'model': 'rematter:item/' + name}}


# --------------------------------------------------------------------------- Chemical Reactor
ALCOVE = ((2, 4, 1), (14, 16, 10))
CHASSIS = ('rear', 'cheek_left', 'cheek_right', 'base', 'skirt', 'ledge')


def chassis():
    """Closed left, right, back and bottom around an alcove that opens to the front and the top."""
    return [Box('rear', (0, 0, 10), (16, 16, 16)),
            Box('cheek_left', (14, 0, 0), (16, 16, 10)),
            Box('cheek_right', (0, 0, 0), (2, 16, 10)),
            Box('base', (2, 0, 1), (14, 4, 10)),
            Box('skirt', (2, 0, 0), (14, 1, 1)),
            # The ledge carries the accent stripe; lit flat, the stripe keeps its colour just above a floor.
            Box('ledge', (2, 3, 0), (14, 4, 1), flat=True)]


def chassis_skin(box, face):
    if box.on_boundary(face):
        # The four closed sides are left to the port plates; the key only names them until they are omitted.
        return {'north': 'front', 'up': 'top'}.get(face, 'plate')
    if face == 'north':
        return 'alcove' if box.name == 'rear' else 'front'
    if box.name == 'skirt' or (box.name == 'ledge' and face == 'down'):
        return 'inner'
    return 'alcove'


def check_showpiece(name, boxes):
    if len(boxes) > 12:
        raise GeometryError(f'{name}: a showpiece holds at most 12 boxes')
    for box in boxes:
        if any(box.lo[axis] < ALCOVE[0][axis] or box.hi[axis] > ALCOVE[1][axis] for axis in range(3)):
            raise GeometryError(f'{name}: {box.name} leaves the alcove')
        if sum(value >= 2 for value in box.size()) < 2:
            raise GeometryError(f'{name}: {box.name} needs two pixels in two dimensions')


def chemical_processor(family):
    # Two reaction columns replace the old stepped vessel. Their sight windows show the real active state.
    columns, liquids = column_boxes(), liquid_boxes()
    parts = [Box('status_light', (11, 1.5, .5), (13, 2.5, 1), step=True), *liquids]
    check_showpiece('chemical_processor', columns + liquids)

    def skin(box, face):
        return chassis_skin(box, face) if box.name in CHASSIS else column_skin(box, face)

    def part_skin(box, face):
        return 'light' if box.name == 'status_light' else liquid_skin(box, face)

    textures = {'top': 'chassis_top', 'front': 'chemical_processor_front',
                'alcove': 'chassis_alcove', 'inner': 'casing_inner'}
    textures.update({material: REACTOR_SPRITE_PREFIX + material
                     for material in ('base', 'lower', 'upper', 'manifold')})
    with_parts(family, 'chemical_processor', 'process', textures, chassis() + columns, skin, parts,
               {'idle': {'light': 'status_off', 'liquid': REACTOR_SPRITE_PREFIX + 'liquid'},
                'active': {'light': 'light_violet', 'liquid': REACTOR_SPRITE_PREFIX + 'liquid_active'}},
               part_skin, 'casing_plain', part_kind='reactor_part',renderer=tuple(box.name for box in parts) if REACTOR_LIQUID=='renderer' else ('status_light',))
    # A dark inset keeps the sight glass closed outside the 64-block renderer range.
    cores=[Box('glass_core_right',(3.5,7,3.5),(5.5,10,7.5),flat=True),Box('glass_core_left',(10.5,7,3.5),(12.5,10,7.5),flat=True)]
    if REACTOR_LIQUID=='renderer':family.model('chemical_processor_glass_cores','reactor_part',{'glass':REACTOR_SPRITE_PREFIX+'glass'},cores,lambda box,face:'glass')
    if REACTOR_LIQUID=='renderer':family.blockstates['chemical_processor']['multipart'] += [{'when':{'facing':f},'apply':{'model':MODEL+'chemical_processor_glass_cores',**ROTATION[f]}} for f in HORIZONTAL]


# --------------------------------------------------------------------------- Six processing machines
def bench(family,name):
    show=bench_boxes(name);work=bench_work(name)
    check_showpiece(name,show+work)
    body=chassis()+show
    materials={bench_skin(name,box,'north')[0] for box in show}
    textures={'top':'chassis_top','front':name+'_front','alcove':'chassis_alcove','inner':'casing_inner'}
    textures.update({material:'bench_'+name+'_plate' if material=='plate' else 'bench_'+material for material in sorted(materials)})
    def skin(box,face):return chassis_skin(box,face) if box.name in CHASSIS else bench_skin(name,box,face)
    light=Box('status_light',(11,1.5,.5),(13,2.5,1),step=True)
    parts=[light,*work]
    def part_skin(box,face):return 'light' if box.name=='status_light' else bench_work_skin(box,face)
    accent_name='amber' if name in ('hand_preparator','material_preparator') else {'analyzer':'ice','decomposer':'copper','material_synthesizer':'leaf','sinter_furnace':'ember','atmospheric_collector':'mint'}[name]
    # Every light name is generated here, even when another old writer once supplied it.
    part_textures={state:{'light':'status_off' if state=='idle' or name=='hand_preparator' else 'light_'+accent_name} for state in ('idle','active')}
    if work:
        for state in ('idle','active'):part_textures[state]['work']='bench_'+name+('_work_active' if state=='active' else '_work')
    with_parts(family,name,'process',textures,body,skin,parts,part_textures,part_skin,'casing_plain',part_kind='bench_part',renderer=('status_light',))
    if name=='hand_preparator':
        crank=crank_boxes();shown=family.model('hand_preparator_crank_item','bench_part',{'metal':'bench_metal'},crank,lambda box,face:('metal',*mapped_uv(face,box.lo,box.hi,*NATURAL[face],(0,0))),context=body+crank)
        family.items[name]['model']['models'].append({'type':'minecraft:model','model':MODEL+'hand_preparator_crank_item'})
        family.compose('hand_preparator item crank',emitted(body,build(body,skin,body+crank))+shown)
        # Conservative swept envelope proves the rotating crank cannot enter housing or mechanism.
        envelope=Box('crank_swept',(7,8,1.5),(14,15,3),flat=True)
        family.compose('hand_preparator crank sweep',emitted(body,build(body,skin,body))+[(envelope,('north','south','east','west','up','down'))])


# --------------------------------------------------------------------------- output
def family():
    result = Family()
    port_plates.add_family_models(result)
    matter_drive(result)
    matter_terminal(result)
    network_interface(result)
    controller(result, 'chemical_reactor_controller')
    controller(result, 'preparation_reactor_controller')
    controller(result, 'industrial_decomposer_controller')
    controller(result, 'industrial_synthesizer_controller')
    machine_port(result)
    structure_cubes(result)
    matter_cables(result)
    chemical_processor(result)
    for name in BENCHES:bench(result,name)
    return result


def referenced_models(node):
    if isinstance(node, dict):
        if isinstance(node.get('model'), str):
            yield node['model']
        for value in node.values():
            yield from referenced_models(value)
    elif isinstance(node, list):
        for value in node:
            yield from referenced_models(value)


def check_references(result, art):
    """Every sprite and model that a file names must be written by this run."""
    models = {MODEL + name for name in result.models} | {'rematter:item/' + name for name in result.item_models}
    for name, model in {**result.models, **result.item_models}.items():
        for key, texture in model['textures'].items():
            # A parent may leave a texture variable to its children.
            if not texture.startswith('#') and texture.removeprefix(MODEL) not in art:
                raise GeometryError(f'{name}: sprite {texture} is not generated')
        parent = model['parent']
        if parent.startswith('rematter:') and parent not in models:
            raise GeometryError(f'{name}: parent {parent} is not generated')
    for name, definition in {**result.blockstates, **result.items}.items():
        for model in list(referenced_models(definition)) + list(port_plates.referenced(definition)):
            if model not in models:
                raise GeometryError(f'{name}: model {model} is not generated')
    drawn = {texture.removeprefix(MODEL) for model in {**result.models, **result.item_models}.values()
             for texture in model['textures'].values() if not texture.startswith('#')}
    if set(art) != drawn | set(RENDERER_SPRITES):
        raise GeometryError(f'sprites without a user or a painter: {sorted(set(art) ^ (drawn | set(RENDERER_SPRITES)))}')
    animations = family_sprites.build_animations()
    for name, sprite in art.items():
        frames = sprite.height // sprite.width
        if sprite.width not in (16, 32) or sprite.height != sprite.width * frames:
            raise GeometryError(f'{name}: sprites are 16 or 32 texels wide with square frames')
        if (frames > 1) != (name in animations):
            raise GeometryError(f'{name}: only animated sprites hold several frames')
        clear = any(sprite.pixel(x, y)[3] < 255 for y in range(sprite.height) for x in range(sprite.width))
        if clear != (name == 'reactor_glass_pane'):
            raise GeometryError(f'{name}: only the glass pane may hold transparent texels')


def generate(check, cable_look=None, cable_anim=None):
    if cable_look is not None:
        family_sprites.CABLE_LOOK = cable_look
    if cable_anim is not None:
        family_sprites.CABLE_ANIM = cable_anim
    import renderer_parts
    renderer_parts.generate(check)
    animations = family_sprites.build_animations()
    result, art = family(), sprites()
    check_references(result, art)
    out = OwnedFiles(ROOT, check)
    # Existing white renderer mask is technical ink; expose it on the block atlas too.
    out.json(ASSETS.parent / 'minecraft/atlases/blocks.json',{'sources':[{'type':'minecraft:single','resource':'rematter:entity/effect_light'}]})
    for name, sprite in art.items():
        out.png(ASSETS / f'textures/block/{name}.png', sprite.width, sprite.height, sprite.pixel)
        if name in animations:
            out.json(ASSETS / f'textures/block/{name}.png.mcmeta', animations[name])
    for folder, files in (('models/block', result.models), ('models/item', result.item_models),
                          ('blockstates', result.blockstates), ('items', result.items)):
        for name, value in files.items():
            out.json(ASSETS / folder / f'{name}.json', value)
    # Plate models that a changed PortSides.SEALED_TOP no longer needs are removed, or reported by --check.
    out.sweep(ASSETS / 'models/block',list(port_plates.OWNED)+['chemical_processor_glass_cores.json']+[name+suffix+'.json' for name in (*BENCHES,'chemical_processor') for suffix in ('_idle_parts','_active_parts','_idle_world_parts','_active_world_parts')])
    out.sweep(ASSETS / 'textures/block',['light_blue.png', 'drive_cell_64k.png', 'drive_cell_256k.png', 'drive_cell_1m.png',
                                         'matter_cable_glow.png.mcmeta', 'reinforced_matter_cable_glow.png.mcmeta', 'resonant_matter_cable_glow.png.mcmeta'])
    print(('Verified' if check else 'Generated') + f' {out.count} family files;'
          f' {len(result.models)} block models passed the geometry checks in {len(result.compositions)} compositions.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='compare the files instead of writing them')
    parser.add_argument('--cable-look', choices=CABLE_LOOKS, default=None, help='override CABLE_LOOK for this run')
    parser.add_argument('--cable-anim', choices=CABLE_ANIMS, default=None, help='override CABLE_ANIM for this run')
    args = parser.parse_args()
    generate(args.check, cable_look=args.cable_look, cable_anim=args.cable_anim)
