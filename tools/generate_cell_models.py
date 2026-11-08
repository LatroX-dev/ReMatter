#!/usr/bin/env python3
"""Generate the physical cartridges shared by item views and populated Drive bays.

FIXED is deliberately identity: the Drive mounts the same complete 3D item with
one uniform scale. The north face (minimum Z) is its front; depth points into a bay.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/rematter'
MODEL_NAMES = ('cell_housing', 'matter_cell_64k', 'matter_cell_256k', 'matter_cell_1m')
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
TEXTURES = {
    'shell': 'rematter:block/graphite',
    'edge': 'rematter:block/socket_rim',
    'dark': 'rematter:block/rubber',
    'frame': 'rematter:block/frame',
    'copper': 'rematter:block/copper_trim',
    'particle': 'rematter:block/graphite',
}
STATUS_TEXTURES = {
    'matter_cell_64k': 'rematter:block/matter_cell_core_64k',
    'matter_cell_256k': 'rematter:block/machine_leaf',
    'matter_cell_1m': 'rematter:block/matter_cell_core_1m',
}
DISPLAY = {
    'gui': {'rotation': [25, 225, 0], 'translation': [0, 0, 0], 'scale': [.72, .72, .72]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [.35, .35, .35]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2, 0], 'scale': [.45, .45, .45]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [.5, .5, .5]},
}


def box(a, b, material):
    return {'from': list(a), 'to': list(b),
            'faces': {face: {'texture': '#' + material, 'uv': [0, 0, 16, 16]} for face in FACES}}


def cell_model(name):
    level = MODEL_NAMES.index(name)
    textures = dict(TEXTURES)
    if level:
        textures['status'] = STATUS_TEXTURES[name]
    # Exact shared bounds: x=3..13, y=5..11, z=0..16 (10 x 6 x 16).
    parts = [
        box((3.45, 5.25, 1.32), (12.55, 10.5, 15.35), 'shell'),
        box((4.3, 10.5, 2.0), (11.7, 10.72, 14.65), 'frame'),
        box((3.32, 5.65, 1.62), (3.79, 6.35, 14.95), 'edge'),
        box((12.21, 5.65, 1.62), (12.68, 6.35, 14.95), 'edge'),
        box((3.52, 10.48, 1.58), (4.10, 10.86, 14.92), 'edge'),
        box((11.90, 10.48, 1.58), (12.48, 10.86, 14.92), 'edge'),
        # End cap and recessed copper contacts at the insertion end.
        box((3.75, 5.65, 15.18), (12.25, 10.05, 15.73), 'edge'),
        box((4.4, 6.2, 15.70), (11.6, 9.50, 15.84), 'dark'),
        box((4.4, 6.2, 15.82), (11.6, 6.76, 16), 'edge'),
        box((4.4, 8.54, 15.82), (11.6, 9.50, 16), 'edge'),
        box((4.9, 7.1, 15.82), (6.55, 8.2, 15.98), 'copper'),
        box((7.17, 7.1, 15.82), (8.83, 8.2, 15.98), 'copper'),
        box((9.45, 7.1, 15.82), (11.10, 8.2, 15.98), 'copper'),
        # Reinforced faceplate with stepped corners and a recessed finger pull.
        box((3, 5.45, .22), (4.05, 10.55, 1.61), 'edge'),
        box((11.95, 5.45, .22), (13, 10.55, 1.61), 'edge'),
        box((3.58, 5, .38), (12.42, 5.45, 1.57), 'shell'),
        box((3.58, 10.55, .38), (12.42, 11, 1.57), 'shell'),
        box((4.05, 5.45, .42), (11.95, 6.26, 1.52), 'frame'),
        box((4.05, 8.71, .36), (11.95, 10.55, 1.52), 'frame'),
        box((4.05, 6.26, .42), (4.65, 8.71, 1.52), 'frame'),
        box((11.35, 6.26, .42), (11.95, 8.71, 1.52), 'frame'),
        box((4.65, 6.26, .98), (11.35, 8.71, 1.45), 'dark'),
        box((4.96, 6.67, .06), (5.77, 7.29, 1.02), 'edge'),
        box((10.23, 6.67, .06), (11.04, 7.29, 1.02), 'edge'),
        box((5.57, 6.62, 0), (10.43, 7.34, .35), 'edge'),
        # One narrow readout in the face and another visible above the handle.
        box((4.75, 9.10, .19), (11.25, 10.20, .46), 'dark'),
        box((4.9, 10.68, 2.62), (11.1, 10.83, 6.85), 'dark'),
    ]
    first = 8 - (level * 1.59 + (level - 1) * .44) / 2
    for index in range(level):
        x = round(first + 2.03 * index, 3)
        parts.append(box((x, 9.28, .10), (x + 1.59, 10.02, .23), 'status'))
        parts.append(box((x, 10.80, 3.04), (x + 1.59, 10.91, 6.43), 'status'))
    return {'ambientocclusion': False, 'gui_light': 'side', 'textures': textures,
            'elements': parts, 'display': DISPLAY}


def outputs():
    for name in MODEL_NAMES:
        model = cell_model(name)
        validate(model)
        yield ASSETS / f'models/item/{name}.json', model
        yield ASSETS / f'items/{name}.json', {
            'model': {'type': 'minecraft:model', 'model': f'rematter:item/{name}'}}


def validate(model):
    low = [min(e['from'][axis] for e in model['elements']) for axis in range(3)]
    high = [max(e['to'][axis] for e in model['elements']) for axis in range(3)]
    assert low == [3, 5, 0] and high == [13, 11, 16], (low, high)
    assert model['display']['fixed'] == DISPLAY['fixed']
    for element in model['elements']:
        assert all(a < b for a, b in zip(element['from'], element['to']))
        assert all(0 <= v <= 16 for v in element['from'] + element['to'])
    for texture in set(model['textures'].values()):
        ns, path = texture.split(':')
        assert (ASSETS.parent / ns / 'textures' / (path + '.png')).is_file(), texture


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for path, data in outputs():
        content = json.dumps(data, indent=2) + '\n'
        if args.check:
            assert path.read_text() == content, str(path)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
    print(('Verified' if args.check else 'Generated') + ' four physical cartridge models; FIXED identity, bounds 10 x 6 x 16.')


if __name__ == '__main__':
    main()
