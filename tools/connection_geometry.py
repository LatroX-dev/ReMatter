"""Cable sockets of the authored bench machine models."""
import copy


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


ROTATION = {
    'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270},
    'up': {'x': 270}, 'down': {'x': 90},
}
MODEL = 'rematter:block/'


def machine_state_assets(name, accent, *, rear_trim=None):
    """Add a short hollow coupler to the housing's permanent recessed service frame."""
    horizontal = ('north', 'east', 'south', 'west')
    variants = {}
    for facing in horizontal:
        for active in ('false', 'true'):
            model = MODEL + name + ('_active' if active == 'true' else '')
            variants[(facing, active)] = {'model': model, **ROTATION[facing]}

    path = f'blockstates/{name}.json'
    parts = [{'when': {'facing': facing, 'active': active}, 'apply': model}
             for (facing, active), model in variants.items()]
    # Local machine sides follow MachinePortMode.Side for a north-facing model.
    sides = {'left': 'east', 'right': 'west', 'back': 'south', 'bottom': 'down'}
    for side, direction in sides.items():
        from machine_housings import authored
        geometry = 'machine_socket_' + side
        coupler = authored('cable_coupler')
        elements = rotate(coupler['elements'], direction)
        textures = coupler['textures'] | {'graphite': MODEL + 'graphite', 'particle': MODEL + 'graphite'}
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
