#!/usr/bin/env python3
"""Build ReMatter materials, connectors and variants of the authored machine models."""
from pathlib import Path
import argparse
import json
import math
from connection_geometry import (SOCKET_TEXTURES, machine_state_assets, reactor_socket,
                                 write_cable_models, write_cable_textures, write_port_models)
from machine_palette import CHASSIS, COPPER_CONTACT, MATTE_TRIM, RECESS, write_tiles
from generated_files import GeneratedFiles
from machine_housings import machine_housing
from material_tiles import TILE, soft_light

parser = argparse.ArgumentParser(description=__doc__)
mode = parser.add_mutually_exclusive_group()
mode.add_argument('--models-only', action='store_true', help='Preserve every authored texture and animation metadata file.')
mode.add_argument('--textures-only', action='store_true', help='Refresh materials without rewriting model or blockstate data.')
parser.add_argument('--refresh-glass', action='store_true', help='Rebuild only the native clear-glass texture, even with --models-only.')
parser.add_argument('--check', action='store_true', help='Compare the selected files instead of writing them.')
ARGS = parser.parse_args()
MODELS_ONLY = ARGS.models_only

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/rematter'
OUTPUT = GeneratedFiles(ROOT, ARGS.check)
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
PALETTE = {
    'graphite': CHASSIS, 'edge': (76, 83, 84), 'steel': (174, 183, 181), 'ceramic': (229, 222, 204),
    'copper': COPPER_CONTACT, 'copper_light': (218, 158, 110), 'rubber': RECESS,
    'cyan': (64, 193, 185), 'ice': (173, 230, 224), 'screen': (9, 23, 29),
    'vessel': (25, 55, 62), 'matte_trim': MATTE_TRIM,
}

def json_file(path, value):
    if MODELS_ONLY and path.startswith('textures/'): return
    if ARGS.textures_only and not path.startswith('textures/'): return
    OUTPUT.json(ROOT / path, value)

def png(path, width, height, pixel):
    if MODELS_ONLY and not (ARGS.refresh_glass and path == 'textures/block/reactor_glass.png'): return
    OUTPUT.png(ROOT / path, width, height, pixel)

def rgba(rgb, delta=0):
    return tuple(max(0, min(255, c + delta)) for c in rgb) + (255,)

def shaded_mask(path, mask, size=32, frames=1, depth=1):
    """Retain instrument markings and animation frames over continuous lighting."""
    step = TILE // size

    def pixel(x, y):
        color = mask(x // step, y // step)
        lit = rgba(color[:3], round(depth * soft_light(x, y % TILE)))
        return lit[:3] + (color[3],)

    png(path, TILE, TILE * frames, pixel)


def material(name, base):
    def pixel(x, y):
        near = min(x, y)
        far = TILE - 1 - max(x, y)
        d = soft_light(x, y)
        # Sharp silhouette, then a narrow lit chamfer around the wide face.
        if min(near, far) < 3:
            d -= 50 if base == PALETTE['ceramic'] else 29
        elif near < 9 and far >= 9:
            d += (9 - near) * 3.1
        elif far < 10:
            d -= (10 - far) * 1.8
        return rgba(base, round(d))

    png(f'textures/block/{name}.png', TILE, TILE, pixel)

for name, color in [('ceramic', 'ceramic'), ('graphite', 'graphite'), ('brushed_steel', 'steel'), ('copper_trim', 'copper'),
                    ('frame', 'edge'), ('rubber', 'rubber'), ('vessel', 'vessel')]:
    material(name, PALETTE[color])
material('socket_rim', PALETTE['matte_trim'])
for color, rgb in {'blue': (64,166,255), 'green': (59,224,122),
                   'amber': (255,183,53), 'red': (242,68,78)}.items():
    png(f'textures/block/cell_led_{color}.png', TILE, TILE,
        lambda x,y,rgb=rgb: rgba(rgb, round(5 * soft_light(x,y))))
material('cable_sleeve_silith', (187, 181, 161))
material('cable_sleeve_rhevon', (100, 85, 86))
material('cable_sleeve_lumivex', (170, 124, 69))


def hazard_pixel(x, y):
    if y < 7 or y > 24: return rgba(PALETTE['graphite'])
    return rgba(PALETTE['copper_light'] if ((x + y) // 7) % 2 else PALETTE['rubber'])


shaded_mask('textures/block/hazard.png', hazard_pixel, depth=.55)

def detail(name, style):
    def pixel(x, y):
        if max(abs(x - 15.5), abs(y - 15.5)) > 14: return rgba(PALETTE['rubber'])
        if x == 2 or y == 2: return rgba(PALETTE['edge'], 8)
        if (x in (4, 27) and y in (4, 27)): return rgba(PALETTE['steel'])
        if style == 'vent':
            if 7 <= x <= 24 and y in (8, 12, 16, 20, 24): return rgba(PALETTE['rubber'])
            if 7 <= x <= 24 and y in (9, 13, 17, 21, 25): return rgba(PALETTE['edge'], 5)
        if style == 'panel':
            if x in (5, 26) and 7 <= y <= 25 or y in (7, 25) and 5 <= x <= 26: return rgba(PALETTE['rubber'])
            if 9 <= x <= 22 and y in (11, 12): return rgba(PALETTE['steel'], -20)
            if 9 <= x <= 18 and y in (16, 19): return rgba(PALETTE['edge'], 10)
            if 22 <= x <= 24 and 21 <= y <= 23: return rgba(PALETTE['copper'])
        if style == 'keyboard':
            if 4 <= x <= 27 and 5 <= y <= 23 and x % 4 != 0 and y % 5 in (1, 2, 3): return rgba(PALETTE['steel'], -45)
            if 7 <= x <= 21 and 26 <= y <= 28: return rgba(PALETTE['edge'])
        return rgba(PALETTE['graphite'])
    shaded_mask(f'textures/block/{name}.png', pixel, depth=.78)
for name in ('vent', 'panel', 'keyboard'): detail(name, name)

def port(name, color, energy=False):
    def pixel(x, y):
        dx, dy = abs(x - 15.5), abs(y - 15.5)
        # Chamfered flange, four bolts and a deeply recessed connector.
        if dx + dy > 25 or max(dx, dy) > 14: return rgba(PALETTE['graphite'])
        if (x in (5, 26) and y in (5, 26)): return rgba(PALETTE['steel'])
        if max(dx, dy) > 12: return rgba(PALETTE['steel'], -65)
        if max(dx, dy) > 10: return rgba(PALETTE['rubber'])
        if max(dx, dy) > 9: return rgba(PALETTE[color], -30)
        if energy:
            if (12 <= y <= 15 and 13 <= x <= 20) or (8 <= y <= 11 and 16 <= x <= 19) or (16 <= y <= 23 and 12 <= x <= 15):
                return rgba(PALETTE['copper_light'])
        else:
            nodes = ((15, 10), (10, 19), (21, 19))
            if any(abs(x - nx) <= 1 and abs(y - ny) <= 1 for nx, ny in nodes): return rgba(PALETTE['ice'])
            if (x in (15, 16) and 12 <= y <= 17) or (y in (17, 18) and 10 <= x <= 21): return rgba(PALETTE['cyan'], -25)
        return rgba(PALETTE['rubber'])
    shaded_mask(f'textures/block/{name}.png', pixel, depth=.72)
port('port_matter', 'cyan')
port('port_energy', 'copper', True)

def screen(name, active):
    frames = 8 if active else 1
    def pixel(x, y):
        frame, yy = divmod(y, 32)
        if x in (0, 31) or yy in (0, 31): return rgba(PALETTE['rubber'])
        if yy == 3 and 4 <= x <= 20: return rgba(PALETTE['steel'], -60)
        if 5 <= yy <= 7 and 5 <= x <= 9: return rgba(PALETTE['cyan'], -35 if active else -95)
        if 6 <= yy <= 24 and 23 <= x <= 26 and yy >= 24 - (frame + 7 if active else 6): return rgba(PALETTE['copper'], 5)
        if yy in (11, 17, 23) and 5 <= x <= 19: return rgba(PALETTE['edge'], -15)
        if 5 <= x <= 19 and yy == 17 + int(math.sin((x + frame * 2) * .55) * 4): return rgba(PALETTE['cyan'], -20 if active else -105)
        if active and yy == 27 and 4 <= x <= 7 + frame * 2: return rgba(PALETTE['ice'], -25)
        return rgba(PALETTE['screen'])
    shaded_mask(f'textures/block/{name}.png', pixel, frames=frames, depth=.43)
    if active: json_file(f'textures/block/{name}.png.mcmeta', {'animation': {'frametime': 3, 'interpolate': True}})
screen('display_idle', False)
screen('display_active', True)

def glow(name, active):
    frames = 8 if active else 1
    def pixel(x, y):
        frame, yy = divmod(y, 16)
        if x in (0, 15) or yy in (0, 15): return rgba(PALETTE['rubber'])
        d = int(math.sin(frame * math.pi / 4) * 12) - 15 if active else -105
        if x in (1, 14) or yy in (1, 14): d -= 25
        return rgba(PALETTE['cyan'], d)
    shaded_mask(f'textures/block/{name}.png', pixel, size=16, frames=frames, depth=.52)
    if active: json_file(f'textures/block/{name}.png.mcmeta', {'animation': {'frametime': 3, 'interpolate': True}})
glow('indicator_idle', False)
glow('indicator_active', True)
shaded_mask('textures/entity/light.png', lambda x, y: rgba(PALETTE['ice'] if 5 <= x <= 10 else PALETTE['cyan']),
            size=16, depth=.32)
# Original oiled timber for the non-electronic hand bench; long restrained grain.
def wood_pixel(x, y):
    delta = -13 if x in (0, 31) else 14 if x in (1, 2) else 5 if x in (6, 18, 27) else -7 if x in (5, 17, 26) else 0
    if 7 <= x <= 13 and 8 <= y <= 21: delta += 4
    if 20 <= x <= 23 and 12 <= y <= 27: delta -= 4
    if y in (0, 31): delta -= 8
    return rgba((192, 161, 113), delta)
shaded_mask('textures/block/bench_wood.png', wood_pixel, depth=.66)

TEXTURES = {name: f'rematter:block/{name}' for name in ('ceramic', 'graphite', 'brushed_steel', 'copper_trim', 'frame', 'rubber', 'hazard', 'port_matter', 'port_energy', 'vent', 'panel', 'keyboard', 'vessel')}
TEXTURES |= {'screen': 'rematter:block/display_idle', 'status': 'rematter:block/indicator_idle', 'wood': 'rematter:block/bench_wood', 'particle': 'rematter:block/ceramic'}
TEXTURES['teal'] = 'rematter:block/reaction_chamber_crystal'
TEXTURES['housing'] = 'rematter:block/frame'
TEXTURES['particle'] = 'rematter:block/graphite'
CASING_TEXTURE = 'rematter:block/reactor_casing'

def box(a, b, texture='graphite', face=None, rotation=None, shade=True):
    faces = {d: {'texture': '#' + texture, 'uv': [0, 0, 16, 16]} for d in FACES}
    for d, t in (face or {}).items(): faces[d] = {'texture': '#' + t, 'uv': [0, 0, 16, 16]}
    element = {'from': list(a), 'to': list(b), 'faces': faces}
    if rotation: element['rotation'] = {'origin': rotation[0], 'axis': rotation[1], 'angle': rotation[2], 'rescale': False}
    if not shade:
        element['shade'] = False
        element['light_emission'] = 3
    elif 'screen' in (face or {}).values():
        element['light_emission'] = 2
    return element


def recessed_casing(inset, depth):
    """A sealed casing with a flush perimeter and a shallow north-facing recess."""
    far = 16 - inset
    boundary = {'west': (0, 0), 'east': (0, 16), 'down': (1, 0),
                'up': (1, 16), 'north': (2, 0), 'south': (2, 16)}
    pieces = (
        ((0, 0, depth), (16, 16, 16), FACES),
        ((0, 0, 0), (inset, 16, depth), ('north', 'west', 'east', 'up', 'down')),
        ((far, 0, 0), (16, 16, depth), ('north', 'west', 'east', 'up', 'down')),
        ((inset, 0, 0), (far, inset, depth), ('north', 'up', 'down')),
        ((inset, far, 0), (far, 16, depth), ('north', 'up', 'down')),
    )
    result = []
    for start, end, sides in pieces:
        # Implicit UVs project each panel onto the same casing texture.
        faces = {side: {'texture': '#casing'} for side in sides}
        for side, face in faces.items():
            axis, edge = boundary[side]
            if (start if edge == 0 else end)[axis] == edge:
                face['cullface'] = side
        result.append({'from': list(start), 'to': list(end), 'faces': faces})
    return result

def facet(e, x, y, z, width=1.5, height=4, material='teal'):
    """A deliberately cut, stepped crystal; no stretched image mapped onto a box."""
    half=width/2
    e.extend([box((x-half,y+height*.2,z-half),(x+half,y+height*.8,z+half),material),
              box((x-half*.72,y+height*.12,z-half*.72),(x+half*.72,y+height*.88,z+half*.72),material,
                  rotation=([x,y+height*.5,z],'y',45)),
              box((x-half*.4,y,z-half*.4),(x+half*.4,y+height,z+half*.4),material)])


def hand_preparator_idle():
    parts=[]
    for z in (5.8,9.6):
        for angle in (0,45):
            parts.append(box((4,10.01,z-.99),(12,11.99,z+.99),'brushed_steel',
                rotation=([8,11,z],'x',angle) if angle else None))
        parts.append(box((3.4,10.75,z-.25),(12.6,11.25,z+.25),'frame'))
    # Mesh and mixer have the exact same rigid, unpowered resting poses as the renderer.
    for x in (3.6,12):parts.append(box((x,6.85,4.7),(x+.4,7.15,10.7),'frame'))
    for z in (4.7,10.3):parts.append(box((4,6.85,z),(12,7.15,z+.4),'frame'))
    for x in range(4,13):parts.append(box((x,6.94,5.1),(x+.1,7.06,10.3),'brushed_steel'))
    for z in range(5,11):parts.append(box((4,6.94,z),(12,7.06,z+.1),'brushed_steel'))
    parts += [box((7.8,3.7,7.6),(8.2,6.3,8),'wood'),
        box((5,4.5,7.5),(11,4.9,8.1),'wood'),box((7.7,4.5,4.8),(8.3,4.9,10.8),'wood'),
        box((13.4,13.3,5.8),(15.4,13.7,6.2),'frame'),box((14.95,13.28,5.78),(15.25,15,6.22),'copper_trim'),
        box((15.25,14.65,5.75),(15.95,15.05,6.25),'wood')]
    return parts


# Static housings and service frames come from the Blockbench exports in artwork/machines.
MACHINES = (
    'hand_preparator', 'material_preparator', 'analyzer', 'decomposer',
    'material_synthesizer', 'electrolyzer', 'chemical_reactor', 'matter_drive',
    'matter_terminal', 'network_interface',
    'industrial_reactor', 'industrial_preparator',
)
reactor_idle = []
facet(reactor_idle, 8, 6, 8, 2.6, 5)
idle_parts = {'hand_preparator': hand_preparator_idle(), 'chemical_reactor': reactor_idle}

for name in MACHINES:
    model = (json.loads((ROOT / f'models/block/{name}.json').read_text())
             if name.startswith('industrial_') else machine_housing(name))
    if not name.startswith('industrial_'):
        json_file(f'models/block/{name}.json', model)
    json_file(f'models/block/{name}_active.json', {
        'parent': f'rematter:block/{name}',
        'textures': {'screen': 'rematter:block/display_active', 'status': 'rematter:block/indicator_active'},
    })
    accent = None if name.startswith('industrial_') else model['textures']['teal'].split('/')[-1]
    for path, asset in machine_state_assets(name, accent, rear_only=name == 'matter_terminal'):
        json_file(path, asset)
    item_model = f'rematter:block/{name}'
    if name in idle_parts:
        item = model | {'elements': model['elements'] + idle_parts[name]}
        json_file(f'models/item/{name}.json', item)
        item_model = f'rematter:item/{name}'
    json_file(f'items/{name}.json', {'model': {'type': 'minecraft:model', 'model': item_model}})
if not MODELS_ONLY:
    write_tiles(ROOT, OUTPUT.png)

# One broad recessed plate, with the bevel lit on the inside of the rim.
def reactor_casing_pixel(x, y):
    left_top = min(x, y)
    right_bottom = TILE - 1 - max(x, y)
    edge = min(left_top, right_bottom)
    corner = min(x, TILE - 1 - x) + min(y, TILE - 1 - y)
    inset = min(edge - 11, (corner - 29) / math.sqrt(2))

    if edge < 2:
        shade = -13
    elif inset < 0:
        shade = 6 + .24 * soft_light(x, y)
    elif inset < 7:
        bevel_light = -20 if left_top <= right_bottom else 16
        shade = bevel_light + (-8 - bevel_light) * (inset / 7)
    else:
        shade = -8 + .42 * soft_light(x, y)
    return rgba(PALETTE['graphite'], round(shade))


png('textures/block/reactor_casing.png', TILE, TILE, reactor_casing_pixel)
casing = box((0, 0, 0), (16, 16, 16), 'casing')
for side, face in casing['faces'].items():
    face['cullface'] = side
json_file('models/block/reactor_casing.json', {
    'parent': 'minecraft:block/block',
    'textures': {'casing': CASING_TEXTURE, 'particle': CASING_TEXTURE},
    'elements': [casing],
})
write_cable_models(json_file)
write_cable_textures(png)

# The Casing supplies the structure. Glass shares its clear model with the inventory.
def glass_pixel(x, y, rim_width, reflection_alpha):
    edge = min(x, y, TILE - 1 - x, TILE - 1 - y)
    if edge < rim_width:
        return rgba(PALETTE['graphite'], -11 if edge < 2 else 3)
    distance = abs(x + y - 72)
    if 29 <= x <= 51 and distance <= 2:
        strength = math.sin(math.pi * (x - 29) / 22) * (1 - distance / 3)
        return (157, 174, 175, round(reflection_alpha * strength))
    return (0, 0, 0, 0)


png('textures/block/reactor_glass.png', TILE, TILE, lambda x, y: glass_pixel(x, y, 3, 100))
glass_textures=TEXTURES|{'glass':'rematter:block/reactor_glass'}
e=[box((0,0,0),(16,16,16),'glass')]
for side,face in e[0]['faces'].items():face['cullface']=side
json_file('models/block/reactor_glass.json',{'parent':'minecraft:block/block','textures':glass_textures,'elements':e})
json_file('blockstates/reactor_glass.json',{'variants':{'':{'model':'rematter:block/reactor_glass'}}})

json_file('items/reactor_glass.json',{'model':{'type':'minecraft:model','model':'rematter:block/reactor_glass'}})
# Configured connectors share a single socket geometry.
json_file('models/block/machine_port.json', {
    'parent': 'rematter:block/machine_port_active', 'textures': {'teal': 'rematter:block/socket_rim'},
})
json_file('models/block/machine_port_active.json', {
    'parent': 'minecraft:block/block',
    'textures': TEXTURES | SOCKET_TEXTURES | {'casing': CASING_TEXTURE, 'particle': CASING_TEXTURE},
    'elements': [*recessed_casing(2.25, 2.2), *reactor_socket()],
})
write_port_models(json_file)
json_file('items/machine_port.json',{'model':{'type':'minecraft:model','model':'rematter:block/machine_port'}})
if ARGS.check:
    print(f'Verified {OUTPUT.count} visual files.')
elif ARGS.textures_only:
    print('Generated 128px machine, connector, cable and glass materials.')
else:
    print(f'Generated variants for {len(MACHINES)} machine models, and connector models.')
