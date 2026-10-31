#!/usr/bin/env python3
"""Build original ReMatter pixel materials and cuboid models. No external art or libraries."""
from pathlib import Path
import copy
import json
import math
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/rematter'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
PALETTE = {
    'graphite': (36, 46, 52), 'edge': (61, 76, 82), 'steel': (120, 144, 148),
    'copper': (180, 105, 63), 'copper_light': (232, 163, 96), 'rubber': (15, 22, 28),
    'cyan': (73, 221, 205), 'ice': (169, 249, 235), 'screen': (10, 27, 34),
}

def json_file(path, value):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(value, indent=2) + '\n')

def png(path, width, height, pixel):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(v for x in range(width) for v in pixel(x, y)) for y in range(height))
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
                 + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

def rgba(rgb, delta=0):
    return tuple(max(0, min(255, c + delta)) for c in rgb) + (255,)

def material(name, base, style='metal'):
    def pixel(x, y):
        c = PALETTE[base]
        if style == 'metal':
            d = 5 if y % 8 == 0 else -3 if y % 8 == 7 else ((x * 3 + y * 5) % 5 - 2)
            if x in (0, 31) or y in (0, 31): d = -12
            if x == 1 or y == 1: d = 10
            return rgba(c, d)
        if style == 'hazard': return rgba(PALETTE['copper_light'] if ((x + y) // 5) % 2 else PALETTE['rubber'])
        return rgba(c)
    png(f'textures/block/{name}.png', 32, 32, pixel)

for name, color in [('graphite', 'graphite'), ('brushed_steel', 'steel'), ('copper_trim', 'copper'), ('frame', 'edge'), ('rubber', 'rubber')]:
    material(name, color)
material('hazard', 'copper', 'hazard')

def port(name, color, energy=False):
    def pixel(x, y):
        dx, dy = abs(x - 15.5), abs(y - 15.5)
        d = max(dx, dy)
        if d > 13: return rgba(PALETTE['graphite'])
        if d > 11: return rgba(PALETTE['steel'])
        if d > 8: return rgba(PALETTE[color], 8 if x + y < 32 else -15)
        if energy and (12 <= x <= 17 and 7 <= y <= 16 or 15 <= x <= 20 and 14 <= y <= 23): return rgba(PALETTE['copper_light'])
        if not energy and (12 <= x <= 19 and 11 <= y <= 20): return rgba(PALETTE[color], -75)
        return rgba(PALETTE['rubber'])
    png(f'textures/block/{name}.png', 32, 32, pixel)
port('port_matter', 'cyan')
port('port_energy', 'copper', True)

def screen(name, active):
    frames = 8 if active else 1
    def pixel(x, y):
        frame, yy = divmod(y, 32)
        if x in (0, 31) or yy in (0, 31): return rgba(PALETTE['edge'])
        if x in (1, 30) or yy in (1, 30): return rgba(PALETTE['rubber'])
        if yy == 5 and 5 <= x <= 25: return rgba(PALETTE['cyan'], -45 if active else -100)
        if yy in (11, 17, 23) and 5 <= x < (24 - (yy % 5)): return rgba(PALETTE['cyan'], -80 if active else -140)
        if active and 5 <= x <= 25 and yy == 7 + frame * 2: return rgba(PALETTE['ice'], -30)
        if active and x in (24, 25) and 20 - frame % 4 <= yy <= 26: return rgba(PALETTE['copper_light'])
        return rgba(PALETTE['screen'], 3 if x % 5 == 0 and yy % 5 == 0 else 0)
    png(f'textures/block/{name}.png', 32, 32 * frames, pixel)
    if active: json_file(f'textures/block/{name}.png.mcmeta', {'animation': {'frametime': 3, 'interpolate': True}})
screen('display_idle', False)
screen('display_active', True)

def glow(name, active):
    frames = 8 if active else 1
    def pixel(x, y):
        frame, yy = divmod(y, 16)
        c = PALETTE['cyan']
        d = int(math.sin(frame * math.pi / 4) * 22) if active else -95
        if x in (0, 15) or yy in (0, 15): return rgba(PALETTE['rubber'])
        if x in (1, 14) or yy in (1, 14): d -= 35
        return rgba(c, d)
    png(f'textures/block/{name}.png', 16, 16 * frames, pixel)
    if active: json_file(f'textures/block/{name}.png.mcmeta', {'animation': {'frametime': 3, 'interpolate': True}})
glow('indicator_idle', False)
glow('indicator_active', True)
# Dedicated small entity textures keep mechanism rendering on standard vanilla render types.
png('textures/entity/mechanism.png', 32, 32, lambda x, y: rgba(PALETTE['copper_light'] if y % 8 == 0 else PALETTE['copper'] if y % 8 in (1, 2, 3) else PALETTE['steel'], -10 if x % 8 == 7 else 0))
png('textures/entity/light.png', 16, 16, lambda x, y: rgba(PALETTE['ice'] if (x + y) % 7 == 0 else PALETTE['cyan']))

TEXTURES = {name: f'rematter:block/{name}' for name in ('graphite', 'brushed_steel', 'copper_trim', 'frame', 'rubber', 'hazard', 'port_matter', 'port_energy')}
TEXTURES |= {'screen': 'rematter:block/display_idle', 'status': 'rematter:block/indicator_idle', 'particle': 'rematter:block/graphite'}

def box(a, b, texture='graphite', face=None, rotation=None, shade=True):
    faces = {d: {'texture': '#' + texture} for d in FACES}
    for d, t in (face or {}).items(): faces[d] = {'texture': '#' + t, 'uv': [0, 0, 16, 16]}
    element = {'from': list(a), 'to': list(b), 'faces': faces}
    if rotation: element['rotation'] = {'origin': rotation[0], 'axis': rotation[1], 'angle': rotation[2], 'rescale': False}
    if not shade:
        element['shade'] = False
        element['light_emission'] = 8
    elif 'screen' in (face or {}).values():
        element['light_emission'] = 5
    return element

def base():
    e = [box((1, 1, 1), (15, 2.5, 15), 'frame'), box((2, 2.5, 2), (14, 3.5, 14))]
    for x in (2, 11):
        for z in (2, 11): e.append(box((x, 0, z), (x + 3, 1, z + 3), 'rubber'))
    e += [box((2, 2.5, 1.8), (14, 3.1, 2.1), 'copper_trim'), box((6, 0.6, 6), (10, 1, 10), 'frame', {'down': 'port_matter'})]
    return e

def ports(e, top=True):
    e += [box((0.5, 4, 6), (2, 9, 11), 'frame', {'west': 'port_matter'}),
          box((14, 4, 6), (15.5, 9, 11), 'frame', {'east': 'port_matter'}),
          box((5, 4, 14), (11, 10, 15.5), 'frame', {'south': 'port_energy'})]
    if top: e += [box((6, 15, 6), (10, 16, 10), 'frame', {'up': 'port_energy'})]
    return e

def display(e, a=(5, 5, 1.3), b=(11, 8.5, 2.0)):
    e.append(box(a, b, 'rubber', {'north': 'screen'}))
    e.append(box((a[0], a[1] - 0.65, a[2] - 0.02), (b[0], a[1] - 0.25, a[2] + 0.08), 'status', shade=False))
    return e

models = {}
idle_parts = {}
# Analyzer: open sample stage and a rear-mounted scanning gantry.
e = base()
e += [box((4, 3.5, 3), (12, 4.4, 12), 'brushed_steel'), box((5, 4.4, 4), (11, 4.7, 11), 'rubber'),
      box((2, 3.5, 10), (4, 13, 14)), box((12, 3.5, 10), (14, 13, 14)),
      box((2, 13, 4), (14, 15, 14)), box((4, 12.5, 4), (12, 13, 13), 'copper_trim'),
      box((3, 6, 11), (3.7, 12.5, 12), 'brushed_steel'), box((12.3, 6, 11), (13, 12.5, 12), 'brushed_steel')]
display(e, (5, 7.8, 11.7), (11, 11.5, 12.5))
models['analyzer'] = ports(e)
idle_parts['analyzer'] = [box((4, 10.7, 7.1), (12, 11.3, 7.9), 'copper_trim'), box((4, 4.75, 7.35), (12, 4.8, 7.65), 'status', shade=False)]
# Decomposer: twin exposed cutters, deep intake, sloped hopper and output chute.
e = base()
e += [box((3, 3.5, 8), (13, 11, 14)), box((3, 4, 3), (4, 11, 9), 'frame'), box((12, 4, 3), (13, 11, 9), 'frame'),
      box((4, 4, 7.8), (12, 10, 8), 'rubber'), box((4, 10, 3), (12, 11, 8), 'hazard'),
      box((4, 3, 0.7), (12, 4, 6), 'brushed_steel'), box((5, 4, 1), (11, 4.2, 4), 'rubber'),
      box((2, 12, 2), (14, 16, 3.5), 'frame'), box((2, 12, 12.5), (14, 16, 14), 'frame'),
      box((2, 12, 3.5), (3.5, 16, 12.5), 'frame'), box((12.5, 12, 3.5), (14, 16, 12.5), 'frame'),
      box((3.3, 10.5, 4), (4.3, 14, 12), 'brushed_steel', rotation=([4, 11, 8], 'z', -22.5)),
      box((11.7, 10.5, 4), (12.7, 14, 12), 'brushed_steel', rotation=([12, 11, 8], 'z', 22.5))]
display(e, (5, 5, 13.5), (11, 8, 14.3))
models['decomposer'] = ports(e, False)
idle_parts['decomposer'] = [box((x - 2.4, 6.5, 4.3), (x + 2.4, 7.5, 5.7), 'copper_trim') for x in (5.5, 10.5)] + [box((x - .5, 4.6, 4.3), (x + .5, 9.4, 5.7), 'brushed_steel') for x in (5.5, 10.5)]
# Synthesizer: open press bed, stout guides and moving tool head.
e = base()
e += [box((2, 3.5, 4), (4, 14, 13), 'frame'), box((12, 3.5, 4), (14, 14, 13), 'frame'),
      box((4, 4, 4), (12, 5.3, 12), 'brushed_steel'), box((5, 5.3, 5), (11, 5.55, 11), 'rubber'),
      box((2, 13, 4), (14, 15.5, 13)), box((3, 12.8, 3.7), (13, 13.3, 4), 'hazard'),
      box((4.1, 5, 9), (4.7, 13, 10), 'brushed_steel'), box((11.3, 5, 9), (11.9, 13, 10), 'brushed_steel')]
display(e, (5, 13.5, 3.3), (11, 15.2, 4))
models['material_synthesizer'] = ports(e)
idle_parts['material_synthesizer'] = [box((4.5, 9, 4.5), (11.5, 11, 11.5), 'copper_trim'), box((7.35, 11, 7.35), (8.65, 14.5, 8.65), 'brushed_steel')]
# Electrolyzer: two separate electrode columns and a shared insulated bridge.
e = base()
for x in (2.5, 9.5):
    e += [box((x, 3.5, 4.5), (x + 4, 5, 12), 'copper_trim'), box((x, 13, 4.5), (x + 4, 15, 12), 'frame'),
          box((x + .5, 5, 10.5), (x + 3.5, 13, 12), 'rubber'),
          box((x, 5, 5), (x + .5, 13, 6), 'brushed_steel'), box((x + 3.5, 5, 5), (x + 4, 13, 6), 'brushed_steel'),
          box((x + 1, 5, 7), (x + 3, 11.5, 10.5), 'status', shade=False),
          box((x + .3, 8.1, 4.8), (x + 3.7, 8.5, 5.2), 'frame')]
e += [box((6, 11, 9), (10, 13, 12)), box((7, 12.7, 10), (9, 13.4, 11), 'copper_trim')]
display(e, (5, 3.6, 1.3), (11, 5.2, 2))
models['electrolyzer'] = ports(e, False)
# Reactor: a framed vessel with a visible top-driven agitator.
e = base()
e += [box((4, 3.5, 3), (12, 5, 13), 'copper_trim'), box((3, 4, 4), (13, 5, 12), 'copper_trim'),
      box((4, 5, 10), (12, 12.5, 13)), box((3, 5, 4), (4, 13, 12), 'frame'), box((12, 5, 4), (13, 13, 12), 'frame'),
      box((4.4, 5, 4.4), (11.6, 8, 10.5), 'status', shade=False),
      box((4, 13, 3), (12, 14.5, 13), 'copper_trim'), box((3, 13, 4), (13, 14.5, 12), 'copper_trim'),
      box((6, 14.5, 6), (10, 16, 10)), box((3.3, 8.5, 3.5), (12.7, 9, 4), 'brushed_steel')]
display(e, (5, 10, 11.7), (11, 12.5, 13.2))
models['chemical_reactor'] = ports(e, False)
idle_parts['chemical_reactor'] = [box((7.4, 8.5, 7.4), (8.6, 13.5, 8.6), 'brushed_steel'), box((5, 10.7, 7.4), (11, 11.3, 8.6), 'copper_trim')]
# Storage: three removable-looking cartridges held in a rigid protective rack.
e = base()
e += [box((2, 3.5, 11.5), (14, 14.5, 14)), box((2, 3.5, 3), (3.3, 15, 14), 'frame'), box((12.7, 3.5, 3), (14, 15, 14), 'frame'), box((2, 14, 3), (14, 16, 14))]
for x in (3.7, 7.1, 10.5):
    e += [box((x, 4, 4), (x + 2, 13.5, 10.5), 'brushed_steel'), box((x - .2, 4, 3.8), (x + 2.2, 5.2, 11), 'copper_trim'),
          box((x - .2, 12.5, 3.8), (x + 2.2, 13.5, 11), 'copper_trim'), box((x + .55, 6, 3.85), (x + 1.45, 11.5, 4.05), 'status', shade=False)]
display(e, (4, 14.35, 2.3), (12, 15.5, 3))
models['matter_storage'] = ports(e, False)
# Terminal: a freestanding slanted monitor and keyboard instead of a box.
e = base()
e += [box((6, 3.5, 7), (10, 9, 11), 'frame'), box((7.5, 4, 7.5), (8.5, 8.5, 8), 'copper_trim')]
pivot = [8, 10, 8]
e += [box((2, 8, 7), (14, 15, 10), 'frame', rotation=(pivot, 'x', -22.5)),
      box((2.8, 8.6, 6.75), (13.2, 14.4, 7.05), 'rubber', {'north': 'screen'}, rotation=(pivot, 'x', -22.5)),
      box((3, 5, 2), (13, 6, 7.5), 'frame', {'up': 'screen'}), box((3.5, 5.8, 2.2), (5, 6.1, 3), 'status', shade=False)]
models['matter_terminal'] = e
# Industrial controller: reinforced corners, two visible levels and a top turbine.
e = base()
e += [box((3, 3.5, 3), (13, 6, 13)), box((4, 6, 4), (12, 10, 12), 'status', shade=False),
      box((3, 10, 3), (13, 11.3, 13), 'copper_trim')]
# An open top guard keeps the turbine visible inside the full-height casing ring.
e += [box((3, 14.5, 3), (13, 16, 4), 'frame'), box((3, 14.5, 12), (13, 16, 13), 'frame'),
      box((3, 14.5, 4), (4, 16, 12), 'frame'), box((12, 14.5, 4), (13, 16, 12), 'frame')]
for x in (2, 12):
    for z in (2, 12): e += [box((x, 3, z), (x + 2, 15.5, z + 2), 'frame'), box((x + .6, 4, z - .03), (x + 1.4, 12, z + .1), 'copper_trim')]
display(e, (5, 4, 1.5), (11, 6.8, 2.2))
models['industrial_reactor'] = ports(e, False)
idle_parts['industrial_reactor'] = [box((4, 12.5, 7.3), (12, 13.5, 8.7), 'copper_trim'), box((7.3, 12.5, 4), (8.7, 13.5, 12), 'brushed_steel')]

for name, elements in models.items():
    model = {'parent': 'minecraft:block/block', 'textures': TEXTURES, 'elements': elements}
    json_file(f'models/block/{name}.json', model)
    json_file(f'models/block/{name}_active.json', {'parent': f'rematter:block/{name}', 'textures': {'screen': 'rematter:block/display_active', 'status': 'rematter:block/indicator_active'}})
    variants = {}
    for direction, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
        for active in (False, True):
            value = {'model': f'rematter:block/{name}' + ('_active' if active else '')}
            if angle: value['y'] = angle
            variants[f'facing={direction},active={str(active).lower()}'] = value
    json_file(f'blockstates/{name}.json', {'variants': variants})
    item = copy.deepcopy(model)
    item['elements'] += idle_parts.get(name, [])
    json_file(f'models/item/{name}.json', item)
    json_file(f'items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'rematter:item/{name}'}})

# Structural casing stays a convincing load-bearing full block with raised reinforcement.
e = [box((.5, .5, .5), (15.5, 15.5, 15.5))]
for x in (0, 13.5):
    for z in (0, 13.5): e += [box((x, 0, z), (x + 2.5, 16, z + 2.5), 'frame')]
e += [box((2.5, 1, 0), (13.5, 2, 1), 'copper_trim'), box((2.5, 14, 0), (13.5, 15, 1), 'copper_trim'),
      box((2.5, 1, 15), (13.5, 2, 16), 'copper_trim'), box((2.5, 14, 15), (13.5, 15, 16), 'copper_trim')]
json_file('models/block/reactor_casing.json', {'parent': 'minecraft:block/block', 'textures': TEXTURES, 'elements': e})

# Pipe manifold plus six independently selected arms. No fake connections to adjacent air.
core = [box((5, 5, 5), (11, 11, 11), 'frame'), box((6, 6, 4.8), (10, 10, 5.05), 'port_matter', {'north': 'port_matter'})]
arm = [box((6, 6, 0), (10, 10, 6), 'copper_trim'), box((5.3, 5.3, 0), (10.7, 10.7, 1.3), 'frame'),
       box((6.8, 9.85, 1.3), (9.2, 10.15, 4.8), 'status', shade=False)]
json_file('models/block/matter_pipe.json', {'parent': 'minecraft:block/block', 'textures': TEXTURES, 'elements': core})
json_file('models/block/matter_pipe_arm.json', {'parent': 'minecraft:block/block', 'textures': TEXTURES, 'elements': arm})
multipart = [{'apply': {'model': 'rematter:block/matter_pipe'}}]
for side, rotation in [('north', {}), ('east', {'y': 90}), ('south', {'y': 180}), ('west', {'y': 270}), ('up', {'x': 270}), ('down', {'x': 90})]:
    multipart.append({'when': {side: 'true'}, 'apply': {'model': 'rematter:block/matter_pipe_arm', **rotation}})
json_file('blockstates/matter_pipe.json', {'multipart': multipart})
json_file('models/item/matter_pipe.json', {'parent': 'minecraft:block/block', 'textures': TEXTURES, 'elements': core + arm + [box((6, 6, 10), (10, 10, 16), 'copper_trim'), box((5.3, 5.3, 14.7), (10.7, 10.7, 16), 'frame')]})
json_file('items/matter_pipe.json', {'model': {'type': 'minecraft:model', 'model': 'rematter:item/matter_pipe'}})
print(f'Generated {len(models)} machine models, state variants, complete item models, pipe parts and original pixel materials.')
