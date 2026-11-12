#!/usr/bin/env python3
"""Generate the sealed-cube and alcove machine family.

Owns the sprites, block models, block states and item definitions of the
Matter Drive, Matter Terminal, Network Interface, Machine Port, the three
Matter Cables, Reactor Casing, Reactor Glass, both industrial controllers and
the Chemical Reactor and the six workshop benches. Every model is a short list of boxes on a half-pixel
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
from family_sprites import ANIMATIONS, sprites
from generated_files import GeneratedFiles
from reactor_showpiece import (PREFIX as REACTOR_SPRITE_PREFIX, column_boxes, column_skin,
                              liquid_boxes, liquid_skin)

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/rematter'
MODEL = 'rematter:block/'
HORIZONTAL = ('north', 'east', 'south', 'west')
# Model directions in which a sprite's u and v grow on a face seen from outside.
NATURAL = {'north': ('-x', '-y'), 'south': ('+x', '-y'), 'west': ('+z', '-y'), 'east': ('-z', '-y'),
           'up': ('+x', '+z'), 'down': ('+x', '-z')}
# Sprites that only the block entity renderer draws: mounted cell faceplates and status lights.
RENDERER_SPRITES = ('drive_cell_64k', 'drive_cell_256k', 'drive_cell_1m', 'drive_cell_unknown',
                    'led_blue', 'led_green', 'led_amber', 'led_red', 'drive_primal_cell_16k', 'drive_primal_cell_64k', 'drive_primal_cell_256k')
# Most boxes a block model of each kind may hold.
BUDGET = {'bench_part': 4, 'network': 16, 'process': 24, 'part': 2, 'reactor_part': 3, 'cable': 2, 'cube': 1}


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

    def model(self, name, kind, textures, boxes, skin, *, context=None, skip=(), particle=None):
        check_boxes(name, boxes)
        if len(boxes) > BUDGET[kind]:
            raise GeometryError(f'{name}: {len(boxes)} boxes exceed the {kind} budget of {BUDGET[kind]}')
        elements = build(boxes, skin, context)
        for element, box in zip(elements, boxes):
            for face in [face for face in element['faces'] if (box.name, face) in skip]:
                del element['faces'][face]
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

    def machine(self, name, parts):
        """Body by facing, plus the part model of the current activity.

        The item shows the machine switched on: lit parts carry the device colour, which is what tells the
        icons of the family apart in an inventory."""
        states = [{'when': {'facing': facing}, 'apply': {'model': MODEL + name, **ROTATION[facing]}}
                  for facing in HORIZONTAL]
        for active, part in (('false', parts[0]), ('true', parts[1])):
            states += [{'when': {'facing': facing, 'active': active},
                        'apply': {'model': MODEL + part, **ROTATION[facing]}} for facing in HORIZONTAL]
        self.blockstates[name] = {'multipart': states}
        self.items[name] = {'model': {'type': 'minecraft:composite', 'models': [
            {'type': 'minecraft:model', 'model': MODEL + name},
            {'type': 'minecraft:model', 'model': MODEL + parts[1]}]}}

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
               part_kind='part'):
    """A machine body and its idle and active part models, checked together in both states.

    Part boxes are screens and lights; they are lit flat in both states, so switching on changes their
    sprite and glow and nothing else."""
    drawn = family.model(name, kind, textures, body, skin, context=body + parts, particle=particle)
    names = []
    for state, emissive in (('idle', False), ('active', True)):
        boxes = [Box(box.name, box.lo, box.hi, emissive=emissive, step=box.step, flat=True) for box in parts]
        part_name = f'{name}_{state}_parts'
        shown = family.model(part_name, part_kind, part_textures[state], boxes, part_skin, context=body + boxes)
        family.compose(f'{name} {state}', drawn + shown)
        names.append(part_name)
    family.machine(name, names)


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
    sides = {'south': 'port', 'east': 'port', 'west': 'port', 'down': 'port', 'up': 'plain'}
    textures = {'port': 'casing_port', 'plain': 'casing_plain', 'front': 'drive_front', 'bay': 'drive_bay'}
    drawn = family.model('matter_drive', 'network', textures, body, sealed_skin(sides, 'bay'),
                         context=body + [bar], particle='casing_plain')
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
        family.compose('matter_drive ' + state, drawn + shown)
        family.compose('matter_drive full ' + state, drawn + shown + cells)
    family.machine('matter_drive', names)


# --------------------------------------------------------------------------- Matter Terminal
def matter_terminal(family):
    body = sealed_body(4)
    screen = [Box('screen', (1, 4, 1), (15, 15, 2))]
    sides = {'south': 'port', 'east': 'plain', 'west': 'plain', 'down': 'plain', 'up': 'plain'}
    textures = {'port': 'casing_port', 'plain': 'casing_plain', 'front': 'terminal_front', 'inner': 'casing_inner'}
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
    sides = {'south': 'port', 'east': 'port', 'west': 'port', 'down': 'port', 'up': 'plain'}
    textures = {'port': 'casing_port', 'plain': 'casing_plain', 'front': 'interface_front', 'inner': 'casing_inner'}
    with_parts(family, 'network_interface', 'network', textures, body, sealed_skin(sides), lights,
               {'idle': {'light': 'light_blue_standby'}, 'active': {'light': 'light_blue'}},
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
               lambda box, face: 'display' if face == 'north' else 'inner', 'reactor_casing')


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
# Texel origins on a cable sheet.
STRIP, CORE_CAP, HUB_FACE, FLANGE_FACE, FLANGE_SIDE = (0, 0), (0, 8), (8, 8), (20, 8), (0, 20)


def run_skin(box, face):
    """The stripe follows the run: u grows along z on all four sides."""
    across = {'east': '-y', 'west': '-y', 'up': '+x', 'down': '+x'}[face]
    uv, rotation = mapped_uv(face, box.lo, box.hi, '+z', across, (STRIP[0] + box.lo[2] * 2 % 16, STRIP[1]))
    return 'cable', uv, rotation


def cable_skin(box, face):
    if box.name in ('arm', 'shaft', 'centre', 'run') and face not in ('north', 'south'):
        return run_skin(box, face)
    if box.name == 'flange' and face not in ('north', 'south'):
        across = '+y' if face in ('east', 'west') else '+x'
        return ('cable', *mapped_uv(face, box.lo, box.hi, across, '+z', FLANGE_SIDE))
    origin = {'hub': HUB_FACE, 'flange': FLANGE_FACE}.get(box.name, CORE_CAP)
    return ('cable', *region(box, face, origin))


def matter_cables(family):
    core = Box('core', (6, 6, 6), (10, 10, 10))
    centre = Box('centre', (6, 6, 6), (10, 10, 10))
    hub = Box('hub', (5, 5, 5), (11, 11, 11))
    arm = Box('arm', (6, 6, 0), (10, 10, 6))
    flange = Box('flange', (5, 5, 0), (11, 11, 2))
    shaft = Box('shaft', (6, 6, 2), (10, 10, 6))
    north, south = Box('north', (6, 6, 0), (10, 10, 6)), Box('south', (6, 6, 10), (10, 10, 16))
    geometry = {
        'core': ([core], [core], ()),
        'straight': ([centre], [centre, north, south], ()),
        'junction': ([hub], [hub], ()),
        # An arm runs on into the next cable, so it has no end face.
        'arm': ([arm], [arm, core], {('arm', 'north')}),
        'arm_device': ([flange, shaft], [flange, shaft, core], ()),
    }
    drawn = {}
    for part, (boxes, context, skip) in geometry.items():
        drawn[part] = family.model('matter_cable_geometry_' + part, 'cable', {'cable': 'matter_cable'}, boxes,
                                   cable_skin, context=context, skip=skip)

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
                          allowed_overlap=(('hub', 'arm'), ('hub', 'shaft')))
    family.compositions.append('cable: 729 side combinations')

    def exact(connected):
        return {side: str(side in connected).lower() for side in ROTATION}

    small = [group for count in range(3) for group in combinations(ROTATION, count) if group not in CABLE_AXES]
    for name in CABLE_TIERS:
        for part in geometry:
            family.child(name if part == 'core' else f'{name}_{part}', 'matter_cable_geometry_' + part,
                         {'cable': name})
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
        # The item is one straight run with both end caps.
        run = [Box('run', (6, 6, 0), (10, 10, 16))]
        check_boxes(name + ' item', run)
        family.item_models[name] = {
            'parent': 'minecraft:block/block',
            'textures': {'particle': MODEL + name, 'cable': MODEL + name},
            'elements': build(run, cable_skin)}
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
        return {'north': 'front', 'up': 'top'}.get(face, 'port')
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


def chemical_reactor(family):
    # Two reaction columns replace the old stepped vessel. Their sight windows show the real active state.
    columns, liquids = column_boxes(), liquid_boxes()
    parts = [Box('status_light', (11, 1.5, .5), (13, 2.5, 1), step=True), *liquids]
    check_showpiece('chemical_reactor', columns + liquids)

    def skin(box, face):
        return chassis_skin(box, face) if box.name in CHASSIS else column_skin(box, face)

    def part_skin(box, face):
        return 'light' if box.name == 'status_light' else liquid_skin(box, face)

    textures = {'port': 'casing_port', 'top': 'chassis_top', 'front': 'chemical_reactor_front',
                'alcove': 'chassis_alcove', 'inner': 'casing_inner'}
    textures.update({material: REACTOR_SPRITE_PREFIX + material
                     for material in ('base', 'lower', 'upper', 'manifold')})
    with_parts(family, 'chemical_reactor', 'process', textures, chassis() + columns, skin, parts,
               {'idle': {'light': 'status_off', 'liquid': REACTOR_SPRITE_PREFIX + 'liquid'},
                'active': {'light': 'light_violet', 'liquid': REACTOR_SPRITE_PREFIX + 'liquid_active'}},
               part_skin, 'casing_port', part_kind='reactor_part')


# --------------------------------------------------------------------------- Six workshop benches
def bench(family,name):
    show=bench_boxes(name);work=bench_work(name)
    check_showpiece(name,show+work)
    body=chassis()+show
    materials={bench_skin(name,box,'north')[0] for box in show}
    textures={'port':'casing_port','top':'chassis_top','front':name+'_front','alcove':'chassis_alcove','inner':'casing_inner'}
    textures.update({material:'bench_'+name+'_plate' if material=='plate' else 'bench_'+material for material in sorted(materials)})
    def skin(box,face):return chassis_skin(box,face) if box.name in CHASSIS else bench_skin(name,box,face)
    light=Box('status_light',(11,1.5,.5),(13,2.5,1),step=True)
    parts=[light,*work]
    def part_skin(box,face):return 'light' if box.name=='status_light' else bench_work_skin(box,face)
    accent_name='amber' if name in ('hand_preparator','material_preparator') else {'analyzer':'ice','decomposer':'copper','material_synthesizer':'leaf','sinter_furnace':'ember'}[name]
    # Every light name is generated here, even when another old writer once supplied it.
    part_textures={state:{'light':'status_off' if state=='idle' or name=='hand_preparator' else 'light_'+accent_name} for state in ('idle','active')}
    if work:
        for state in ('idle','active'):part_textures[state]['work']='bench_'+name+('_work_active' if state=='active' else '_work')
    with_parts(family,name,'process',textures,body,skin,parts,part_textures,part_skin,'casing_port',part_kind='bench_part')
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
    matter_drive(result)
    matter_terminal(result)
    network_interface(result)
    controller(result, 'industrial_reactor')
    controller(result, 'industrial_preparator')
    machine_port(result)
    structure_cubes(result)
    matter_cables(result)
    chemical_reactor(result)
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
            if texture.removeprefix(MODEL) not in art:
                raise GeometryError(f'{name}: sprite {texture} is not generated')
        parent = model['parent']
        if parent.startswith('rematter:') and parent not in models:
            raise GeometryError(f'{name}: parent {parent} is not generated')
    for name, definition in {**result.blockstates, **result.items}.items():
        for model in referenced_models(definition):
            if model not in models:
                raise GeometryError(f'{name}: model {model} is not generated')
    drawn = {texture.removeprefix(MODEL) for model in {**result.models, **result.item_models}.values()
             for texture in model['textures'].values()}
    if set(art) != drawn | set(RENDERER_SPRITES):
        raise GeometryError(f'sprites without a user or a painter: {sorted(set(art) ^ (drawn | set(RENDERER_SPRITES)))}')
    for name, sprite in art.items():
        frames = sprite.height // sprite.width
        if sprite.width not in (16, 32) or sprite.height != sprite.width * frames:
            raise GeometryError(f'{name}: sprites are 16 or 32 texels wide with square frames')
        if (frames > 1) != (name in ANIMATIONS):
            raise GeometryError(f'{name}: only animated sprites hold several frames')
        clear = any(sprite.pixel(x, y)[3] < 255 for y in range(sprite.height) for x in range(sprite.width))
        if clear != (name == 'reactor_glass_pane'):
            raise GeometryError(f'{name}: only the glass pane may hold transparent texels')


def generate(check):
    result, art = family(), sprites()
    check_references(result, art)
    out = GeneratedFiles(ROOT, check)
    for name, sprite in art.items():
        out.png(ASSETS / f'textures/block/{name}.png', sprite.width, sprite.height, sprite.pixel)
        if name in ANIMATIONS:
            out.json(ASSETS / f'textures/block/{name}.png.mcmeta', ANIMATIONS[name])
    for folder, files in (('models/block', result.models), ('models/item', result.item_models),
                          ('blockstates', result.blockstates), ('items', result.items)):
        for name, value in files.items():
            out.json(ASSETS / folder / f'{name}.json', value)
    print(('Verified' if check else 'Generated') + f' {out.count} family files;'
          f' {len(result.models)} block models passed the geometry checks in {len(result.compositions)} compositions.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='compare the files instead of writing them')
    generate(parser.parse_args().check)
