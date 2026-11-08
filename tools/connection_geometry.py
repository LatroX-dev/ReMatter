"""Shared ReMatter socket and cable assets."""
import copy
from itertools import combinations
from material_tiles import TILE, soft_light

FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def box(a, b, texture):
    return {'from': list(a), 'to': list(b), 'faces': {
        face: {'texture': '#' + texture, 'uv': [0, 0, 16, 16]} for face in FACES}}


def rotate(elements, direction):
    """Rotate a north-facing socket around the block centre onto a service face."""
    def point(p):
        x, y, z = p
        return {'north': (x, y, z), 'east': (16-z, y, x),
                'south': (16-x, y, 16-z), 'west': (z, y, 16-x),
                'up': (x, 16-z, y), 'down': (x, z, 16-y)}[direction]
    result = copy.deepcopy(elements)
    for element in result:
        a, b = point(element['from']), point(element['to'])
        element['from'] = [min(a[i], b[i]) for i in range(3)]
        element['to'] = [max(a[i], b[i]) for i in range(3)]
    return result


def socket_collar(outer, inner, front, back):
    far = 16 - outer
    opening = 16 - inner
    return [box((outer, outer, front), (inner, far, back), 'socket_rim'),
            box((opening, outer, front), (far, far, back), 'socket_rim'),
            box((inner, outer, front), (opening, inner, back), 'socket_rim'),
            box((inner, opening, front), (opening, far, back), 'socket_rim')]


def socket(direction='north', depth=3.75, *, indicator=True):
    # The square collar meets the cable at the block boundary on all four sides.
    elements = [box((6, 6, .86), (10, 10, depth), 'graphite'),
                box((5.2, 5.2, .64), (10.8, 10.8, .88), 'socket_well'),
                *socket_collar(5, 6.12, 0, .83)]
    if indicator:
        elements += [box((6.55, 4.94, .22), (9.45, 5.08, .64), 'teal'),
                     box((6.55, 10.92, .22), (9.45, 11.06, .64), 'teal')]
    return rotate(elements, direction)


def reactor_socket():
    # Matching mode strips sit opposite each other on the larger inset frame.
    return [box((2.8, 2.8, 1.04), (13.2, 13.2, 2.2), 'graphite'),
            *socket_collar(3.15, 4.6, .56, 1.16),
            box((5.2, 3.98, .46), (10.8, 4.35, .62), 'teal'),
            box((5.2, 11.65, .46), (10.8, 12.02, .62), 'teal'),
            *socket(depth=2.2, indicator=False)]


ROTATION = {
    'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270},
    'up': {'x': 270}, 'down': {'x': 90},
}
MODEL = 'rematter:block/'
SOCKET_TEXTURES = {
    'graphite': MODEL + 'graphite',
    'socket_rim': MODEL + 'socket_rim',
    'socket_well': MODEL + 'rubber',
}


def machine_state_assets(name, accent=None, *, rear_only=False, rear_trim=None):
    """Bake machine sockets only where the block state reports a Matter Cable arm."""
    horizontal = ('north', 'east', 'south', 'west')
    variants = {}
    for facing in horizontal:
        for active in ('false', 'true'):
            model = MODEL + name + ('_active' if active == 'true' else '')
            variants[(facing, active)] = {'model': model, **ROTATION[facing]}

    path = f'blockstates/{name}.json'
    if accent is None:
        yield path, {'variants': {f'facing={facing},active={active}': model
                                  for (facing, active), model in variants.items()}}
        return

    parts = [{'when': {'facing': facing, 'active': active}, 'apply': model}
             for (facing, active), model in variants.items()]
    # Local machine sides follow MachinePortMode.Side for a north-facing model.
    sides = {'back': 'south'} if rear_only else {'left': 'east', 'right': 'west', 'back': 'south', 'bottom': 'down'}
    for side, direction in sides.items():
        geometry = 'machine_socket_' + side
        elements = socket(direction, depth=1.8 if side == 'bottom' else 3.75)
        if side in ('left', 'right'):
            # The bracket carries the neck back to the housing on open machines.
            x = 12.3 if side == 'left' else 1.3
            elements.append(box((x, 6.05, 9.95), (x + 2.4, 9.95, 12.5), 'graphite'))
            elements.append(box((x + .55, 2.9, 10.45), (x + 1.75, 6.6, 11.65), 'graphite'))
        elif side == 'back':
            elements.append(box((7, 2.9, 12.85), (9, 7, 14.05), 'graphite'))
        textures = SOCKET_TEXTURES | {'particle': MODEL + 'graphite'}
        if side == 'back' and rear_trim is not None:
            geometry = name + '_socket_back'
            elements += rear_trim
            textures['energy'] = MODEL + 'port_energy'
        yield f'models/block/{geometry}.json', {'parent': 'minecraft:block/block', 'textures': textures,
                                                  'elements': elements}
        tinted = f'{geometry}_{accent}'
        yield f'models/block/{tinted}.json', {'parent': MODEL + geometry, 'textures': {'teal': MODEL + accent}}
        for facing in horizontal:
            parts.append({'when': {'facing': facing, f'{side}_cable': 'true'},
                          'apply': {'model': MODEL + tinted, **ROTATION[facing]}})
    yield path, {'multipart': parts}


CABLE_TIERS = (
    ('matter_pipe', 'copper_trim', 'cable_sleeve_silith', (220, 137, 79)),
    ('reinforced_matter_cable', 'reaction_chamber_crystal', 'cable_sleeve_rhevon', (84, 191, 172)),
    ('resonant_matter_cable', 'machine_amber', 'cable_sleeve_lumivex', (190, 143, 187)),
)


def cable_face(start, end, texture, faces=ROTATION):
    return {'from': list(start), 'to': list(end),
            'faces': {face: {'texture': '#' + texture} for face in faces}}


def cable_shaft(start, end):
    # Each exposed face uses the same trace tile along its length. Its seam
    # columns share one lane profile so rotated arms still align.
    shaft = cable_face((6, 6, start), (10, 10, end), 'trace', ('east', 'west', 'up', 'down'))
    for side, face in shaft['faces'].items():
        first, last = (16 - end, 16 - start) if side in ('east', 'down') else (start, end)
        face['uv'] = [first, 0, last, 16]
        if side in ('up', 'down'):
            face['rotation'] = 90
    return [shaft]


def cable_junction():
    return [box((5, 5, 5), (11, 11, 11), 'junction')]


def write_cable_textures(write_png):
    # Two staggered lanes exchange sides at one central crossing. Identical
    # terminal profiles let neighboring tiles meet in either orientation.
    upper_route = (
        (0, 16), (9, 16), (9, 10), (19, 10), (19, 16),
        (33, 16), (33, 47), (63, 47),
    )
    lower_route = (
        (0, 47), (23, 47), (23, 30), (43, 30), (43, 23),
        (52, 23), (52, 16), (63, 16),
    )

    def widen(points):
        return points | {(x + dx, y + dy) for x, y in points
                         for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))}

    def stroke(route):
        line = set()
        for (x0, y0), (x1, y1) in zip(route, route[1:]):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                for y in range(min(y0, y1), max(y0, y1) + 1):
                    line.add((x, y))
        core = widen(line)
        return core, widen(core)

    over, over_edge = stroke(upper_route)
    under, under_edge = stroke(lower_route)

    for tier, (name, _, _, accent) in enumerate(CABLE_TIERS):
        def toned(factor):
            return tuple(round(channel * factor) for channel in accent) + (255,)

        def trace(x, y):
            # The approved 64px route mask is sampled at twice its resolution.
            # Vertical lighting only keeps the terminal columns identical.
            column, row = x // 2, y // 2
            point = (column, row)
            if point in over:
                if column == 33 and 27 <= row <= 33:
                    return tuple(round(channel * .65 + 255 * .35) for channel in accent) + (255,)
                return toned(1)
            if point in over_edge:
                return toned(.63)
            # The shadow beside the upper path makes the underpass legible.
            if not (30 <= column <= 36 and 27 <= row <= 33):
                if point in under:
                    return toned(1)
                if point in under_edge:
                    return toned(.63)
            edge = min(y, TILE - 1 - y)
            if edge < 16:
                # Charcoal metal bevel around the inset circuit face.
                bevel = (21, 31, 42, 51, 44, 33, 24, 18, 18)
                band = edge // 2
                shade = (bevel[band] + bevel[band + edge % 2]) // 2
                return shade, shade + 4, shade + 7, 255
            shade = 16 + max(0, (25 - edge) // 2)
            return shade, shade + 4, shade + 7, 255

        def junction(x, y):
            light = round(.55 * soft_light(x, y))
            u, v = x // 4, y // 4
            u, v = min(u, 31 - u), min(v, 31 - v)
            edge, reach = min(u, v), max(u, v)
            if edge < 4:
                shade = (44, 105, 128, 77)[edge] + light - (12 if tier == 2 else 0)
                return shade, shade + 7, shade + 11, 255
            if edge in (5, 6) and 5 <= reach <= 10:
                return toned(.9 if edge == 5 else .66)
            if edge in (8, 9) and 12 <= reach <= 14:
                return toned(.82 if edge == 8 else .54)
            if edge in (11, 12):
                return 60, 67, 71, 255
            shade = (34 if edge < 11 else 23) + light
            return shade, shade + 5, shade + 8, 255

        write_png(f'textures/block/{name}_traces.png', TILE, TILE, trace)
        write_png(f'textures/block/{name}_contacts.png', TILE, TILE, junction)


def write_cable_models(write):
    # Straight segments, including their centre, use the same four-pixel cross section.
    geometry = {
        'arm': cable_shaft(0, 6),
        'straight': cable_shaft(6, 10),
        'core': [box((6, 6, 6), (10, 10, 10), 'junction')],
        'junction': cable_junction(),
    }
    for part, elements in geometry.items():
        write(f'models/block/matter_cable_geometry_{part}.json',
              {'parent': 'minecraft:block/block', 'elements': elements})

    axes = [('north', 'south'), ('east', 'west'), ('up', 'down')]
    def exact(connected):
        return {side: str(side in connected).lower() for side in ROTATION}

    small = (group for count in range(3) for group in combinations(ROTATION, count) if group not in axes)
    core = {'OR': [exact(group) for group in small]}
    junction = {'OR': [{side: 'true' for side in group} for group in combinations(ROTATION, 3)]}
    # Bake a placed north/south straight run. The exposed item ends use the
    # same caps as the placed core, while the three shaft pieces keep their UVs.
    north, = copy.deepcopy(geometry['arm'])
    south, = rotate(geometry['arm'], 'south')
    south_faces = south['faces']
    south_faces['east'], south_faces['west'] = south_faces['west'], south_faces['east']
    for face in ('up', 'down'):
        south_faces[face]['rotation'] = (south_faces[face]['rotation'] + 180) % 360
    caps = geometry['core'][0]['faces']
    north['faces']['north'] = caps['north']
    south_faces['south'] = caps['south']
    item = [north, *geometry['straight'], south]
    for name, conductor, shield, _ in CABLE_TIERS:
        textures = {key: MODEL + texture for key, texture in {
            'particle': 'graphite', 'conductor': conductor, 'shield': shield,
            'trace': name + '_traces', 'junction': name + '_contacts',
        }.items()}
        for part in geometry:
            model = name if part == 'core' else f'{name}_{part}'
            write(f'models/block/{model}.json',
                  {'parent': MODEL + f'matter_cable_geometry_{part}', 'textures': textures})
        states = [
            {'when': core, 'apply': {'model': MODEL + name}},
            {'when': junction, 'apply': {'model': MODEL + f'{name}_junction'}},
        ]
        states += [{'when': exact((side, other)),
                    'apply': {'model': MODEL + f'{name}_straight', **ROTATION[side]}} for side, other in axes]
        states += [{'when': {side: 'true'},
                    'apply': {'model': MODEL + f'{name}_arm', **rotation}} for side, rotation in ROTATION.items()]
        write(f'blockstates/{name}.json', {'multipart': states})
        write(f'models/item/{name}.json', {'parent': 'minecraft:block/block', 'textures': textures, 'elements': item})
        write(f'items/{name}.json', {'model': {'type': 'minecraft:model', 'model': 'rematter:item/' + name}})


def write_port_models(write):
    modes = {
        'automatic': None,
        'item_input': 'machine_copper',
        'item_output': 'machine_ember',
        'fluid_input': 'machine_blue',
        'fluid_output': 'machine_violet',
        'power': 'machine_amber',
        'matter': 'machine_mint',
        'chemical': 'machine_leaf',
        'disabled': 'graphite',
    }
    states = [{'when': {'formed': 'false', 'facing': side},
               'apply': {'model': MODEL + 'machine_port', **rotation}} for side, rotation in ROTATION.items()]
    for mode, accent in modes.items():
        name = 'machine_port_active' if accent is None else 'machine_port_' + mode
        if accent is not None:
            write(f'models/block/{name}.json',
                  {'parent': MODEL + 'machine_port_active', 'textures': {'teal': MODEL + accent}})
        for side, rotation in ROTATION.items():
            states.append({'when': {'formed': 'true', 'mode': mode, 'facing': side},
                           'apply': {'model': MODEL + name, **rotation}})
    write('blockstates/machine_port.json', {'multipart': states})
