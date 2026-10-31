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
    'graphite': (33, 41, 48), 'edge': (65, 77, 85), 'steel': (165, 181, 184),
    'copper': (160, 99, 65), 'copper_light': (211, 150, 98), 'rubber': (13, 19, 24),
    'cyan': (64, 193, 185), 'ice': (173, 230, 224), 'screen': (9, 23, 29),
    'vessel': (25, 55, 62),
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
        # Continuous, quiet material sheets: no repeated panel grid or speckled noise.
        d = 2 if y in (5, 21) else -1 if y in (6, 22) else 0
        if x == 0 or y == 31: d = -10
        if x == 1 or y == 1: d = 7
        if style == 'hazard':
            if y < 7 or y > 24: return rgba(PALETTE['graphite'])
            return rgba(PALETTE['copper_light'] if ((x + y) // 7) % 2 else PALETTE['rubber'])
        return rgba(c, d)
    png(f'textures/block/{name}.png', 32, 32, pixel)

for name, color in [('graphite', 'graphite'), ('brushed_steel', 'steel'), ('copper_trim', 'copper'),
                    ('frame', 'edge'), ('rubber', 'rubber'), ('vessel', 'vessel')]:
    material(name, color)
material('hazard', 'copper', 'hazard')

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
    png(f'textures/block/{name}.png', 32, 32, pixel)
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
    png(f'textures/block/{name}.png', 32, 32, pixel)
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
    png(f'textures/block/{name}.png', 32, 32 * frames, pixel)
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
    png(f'textures/block/{name}.png', 16, 16 * frames, pixel)
    if active: json_file(f'textures/block/{name}.png.mcmeta', {'animation': {'frametime': 3, 'interpolate': True}})
glow('indicator_idle', False)
glow('indicator_active', True)
# A quiet steel atlas with a restrained copper band, shared by the rigid mechanisms.
def mechanism_pixel(x, y):
    if y in (24, 25): return rgba(PALETTE['copper'])
    if y in (0, 31) or x in (0, 31): return rgba(PALETTE['edge'])
    return rgba(PALETTE['steel'], 5 if y < 10 else -10)
png('textures/entity/mechanism.png', 32, 32, mechanism_pixel)
png('textures/entity/light.png', 16, 16, lambda x, y: rgba(PALETTE['ice'] if 5 <= x <= 10 else PALETTE['cyan']))

TEXTURES = {name: f'rematter:block/{name}' for name in ('graphite', 'brushed_steel', 'copper_trim', 'frame', 'rubber', 'hazard', 'port_matter', 'port_energy', 'vent', 'panel', 'keyboard', 'vessel')}
TEXTURES |= {'screen': 'rematter:block/display_idle', 'status': 'rematter:block/indicator_idle', 'particle': 'rematter:block/graphite'}

def box(a, b, texture='graphite', face=None, rotation=None, shade=True):
    faces = {d: {'texture': '#' + texture} for d in FACES}
    for d, t in (face or {}).items(): faces[d] = {'texture': '#' + t, 'uv': [0, 0, 16, 16]}
    element = {'from': list(a), 'to': list(b), 'faces': faces}
    if rotation: element['rotation'] = {'origin': rotation[0], 'axis': rotation[1], 'angle': rotation[2], 'rescale': False}
    if not shade:
        element['shade'] = False
        element['light_emission'] = 3
    elif 'screen' in (face or {}).values():
        element['light_emission'] = 2
    return element

def bolt(e, x, y, z):
    e.append(box((x - .18, y - .18, z - .08), (x + .18, y + .18, z + .06), 'brushed_steel'))
    e.append(box((x - .11, y - .025, z - .09), (x + .11, y + .025, z - .08), 'rubber'))

def panel(e, a, b, texture='panel'):
    e.append(box(a, b, 'rubber', {'north': texture}))
    return e

def base():
    e = [box((1, 1, 1), (15, 2.2, 15), 'frame'), box((1.6, 2.2, 1.6), (14.4, 3.4, 14.4))]
    for x in (2, 11):
        for z in (2, 11): e.append(box((x, 0, z), (x + 3, 1, z + 3), 'rubber'))
    e += [box((2, 2.2, 1.5), (14, 2.4, 1.65), 'brushed_steel'),
          box((2, 1.3, .95), (4.5, 1.65, 1.1), 'copper_trim'), box((11.5, 1.3, .95), (14, 1.65, 1.1), 'copper_trim'),
          box((6, .6, 6), (10, 1, 10), 'frame', {'down': 'port_matter'})]
    for x in (2.6, 13.4): bolt(e, x, 2.8, 1.48)
    return e

def ports(e, top=True):
    e += [box((.5, 4, 6), (2, 9, 11), 'frame', {'west': 'port_matter'}),
          box((14, 4, 6), (15.5, 9, 11), 'frame', {'east': 'port_matter'}),
          box((5, 4, 14), (11, 10, 15.5), 'frame', {'south': 'port_energy'})]
    if top: e += [box((6, 15, 6), (10, 16, 10), 'frame', {'up': 'port_energy'})]
    return e

def display(e, a=(5, 5, 1.3), b=(11, 8.5, 2.0)):
    e.append(box((a[0] - .25, a[1] - .22, a[2] + .04), (b[0] + .25, b[1] + .22, b[2]), 'frame'))
    e.append(box(a, b, 'rubber', {'north': 'screen'}))
    e.append(box((a[0], a[1] - .55, a[2] - .02), (a[0] + 1.6, a[1] - .3, a[2] + .08), 'status', shade=False))
    return e

def idle_wheel(cx, cy, cz, radius, vertical=False, blades=6, phase=0):
    # Six stepped blades, expressed with valid vanilla cuboids (no unsupported 60-degree rotations).
    e = []
    for n in range(blades):
        angle = n * math.tau / blades + phase
        for r in (.95, 1.45, 1.95, 2.45, 2.95):
            if r + .3 > radius: continue
            u, v = r * math.cos(angle), r * math.sin(angle)
            if vertical:
                e.append(box((cx + u - .25, cy + v - .25, cz - .8), (cx + u + .25, cy + v + .25, cz + .8), 'brushed_steel'))
            else:
                e.append(box((cx + u - .4, cy - .3, cz + v - .4), (cx + u + .4, cy + .3, cz + v + .4), 'brushed_steel'))
    if vertical:
        e += [box((cx - .85, cy - .85, cz - .8), (cx + .85, cy + .85, cz + .8), 'copper_trim'),
              box((cx - .35, cy - .35, cz - .95), (cx + .35, cy + .35, cz - .8), 'rubber')]
    else:
        e += [box((cx - 1, cy - .5, cz - 1), (cx + 1, cy + .5, cz + 1), 'copper_trim'),
              box((cx - .45, cy + .5, cz - .45), (cx + .45, cy + .65, cz + .45), 'frame')]
    return e

models = {}
idle_parts = {}
# Analyzer: asymmetrical instrument housing around an open, steel-edged sample stage.
# Scanner travel remains x4..12, y10.7..11.3, z4.1..10.9.
e = base()
e += [box((3.6, 3.4, 2.8), (12.4, 4.5, 12), 'frame'), box((4, 4.5, 3.2), (12, 4.65, 11.7), 'brushed_steel'),
      box((4.45, 4.65, 3.65), (11.55, 4.69, 11.2), 'rubber'),
      box((2, 3.4, 9.5), (4, 14.7, 14)), box((12.2, 3.4, 11), (14, 12.8, 14), 'frame'),
      box((2, 13, 4), (4, 15, 14)), box((4, 13.7, 4.2), (14, 14.8, 7.1), 'frame'),
      box((4, 14.3, 7.1), (12.2, 15, 11.8)), box((4, 12.9, 4.2), (12, 13.15, 6.6), 'brushed_steel'),
      box((3.45, 5, 10.3), (3.8, 12.8, 10.8), 'brushed_steel'), box((12.2, 5, 11.1), (12.55, 12.8, 11.6), 'brushed_steel'),
      box((2.15, 12, 9.2), (3.6, 12.4, 9.5), 'copper_trim'),
      box((5, 4.69, 4.2), (5.15, 4.7, 10.6), 'frame'), box((10.85, 4.69, 4.2), (11, 4.7, 10.6), 'frame')]
display(e, (5.2, 8.2, 11.9), (10.8, 11.5, 12.4))
panel(e, (2.25, 6.2, 9.25), (3.7, 10.7, 9.5), 'vent')
for x in (2.7, 13.2): bolt(e, x, 14.25, 4)
models['analyzer'] = ports(e)
idle_parts['analyzer'] = [box((4, 10.7, 7.1), (12, 11.3, 7.9), 'brushed_steel'),
                          box((5.3, 10.58, 7.3), (10.7, 10.7, 7.65), 'rubber'),
                          box((4, 10.8, 7.06), (5, 11.15, 7.1), 'copper_trim')]
# Decomposer: wide open cutter throat, an angular feed hopper and a low output tray.
# The swept cutter bay x3.2..12.8/y4.5..9.5/z3.8..6.2 is kept completely unobstructed.
e = base()
e += [box((2.5, 3.4, 8), (13.5, 11.4, 14)), box((2, 4, 3), (3.1, 11.3, 9), 'frame'),
      box((12.9, 4, 3), (14, 11.3, 9), 'frame'), box((3.1, 4, 7.8), (12.9, 10, 8), 'rubber'),
      box((3.1, 10, 2.8), (12.9, 11.3, 8), 'graphite'), box((4, 10.2, 2.65), (12, 10.6, 2.85), 'hazard'),
      box((3.7, 3.2, .7), (12.3, 4, 6), 'frame'), box((4.1, 4, 1), (11.9, 4.15, 3.7), 'brushed_steel'),
      box((4.5, 4.15, 1.3), (11.5, 4.2, 3.6), 'rubber'),
      box((2, 13.5, 2), (14, 15.8, 3.1), 'frame'), box((2, 13.5, 12.9), (14, 15.8, 14), 'frame'),
      box((2, 13.5, 3.1), (3.1, 15.8, 12.9), 'frame'), box((12.9, 13.5, 3.1), (14, 15.8, 12.9), 'frame'),
      box((3.3, 10.8, 4), (4.1, 14, 12), 'brushed_steel', rotation=([4, 11, 8], 'z', -22.5)),
      box((11.9, 10.8, 4), (12.7, 14, 12), 'brushed_steel', rotation=([12, 11, 8], 'z', 22.5)),
      box((2, 15.8, 2), (14, 16, 3.1), 'brushed_steel'), box((2, 15.8, 12.9), (14, 16, 14), 'brushed_steel')]
for x in (2.55, 13.45):
    bolt(e, x, 5, 2.9); bolt(e, x, 9.4, 2.9)
panel(e, (4.6, 6, 13.9), (11.4, 9.7, 14.15), 'vent')
models['decomposer'] = ports(e, False)
idle_parts['decomposer'] = idle_wheel(5.5, 7, 5, 2.25, True) + idle_wheel(10.5, 7, 5, 2.25, True, phase=math.pi / 6)
# Synthesizer: deep press opening, machined bed and heavy pale-edged structural posts.
e = base()
e += [box((2, 3.4, 3.4), (3.75, 14, 13), 'frame'), box((12.25, 3.4, 3.4), (14, 14, 13), 'frame'),
      box((2.3, 4, 3.2), (2.65, 12.7, 3.5), 'brushed_steel'), box((13.35, 4, 3.2), (13.7, 12.7, 3.5), 'brushed_steel'),
      box((4, 4, 4), (12, 5.3, 12), 'brushed_steel'), box((4.5, 5.3, 4.5), (11.5, 5.5, 11.5), 'rubber'),
      box((2, 13, 4), (14, 15.5, 13)), box((3, 12.8, 3.7), (13, 13.2, 4), 'hazard'),
      box((4.1, 5, 11.7), (4.55, 13, 12.2), 'brushed_steel'), box((11.45, 5, 11.7), (11.9, 13, 12.2), 'brushed_steel'),
      box((5.3, 5.5, 5), (5.5, 5.55, 11), 'frame'), box((10.5, 5.5, 5), (10.7, 5.55, 11), 'frame')]
display(e, (5, 13.5, 3.3), (11, 15.2, 4))
for x in (2.9, 13.1): bolt(e, x, 11.8, 3.25); bolt(e, x, 6, 3.25)
models['material_synthesizer'] = ports(e)
idle_parts['material_synthesizer'] = [box((4.5, 9, 4.5), (11.5, 11, 11.5), 'frame'),
      box((4.9, 8.7, 4.9), (11.1, 9, 11.1), 'brushed_steel'), box((5, 10.4, 4.45), (6.5, 10.75, 4.5), 'copper_trim'),
      box((7.35, 11, 7.35), (8.65, 14.5, 8.65), 'brushed_steel')]
# Electrolyzer: two tall dark vessels with narrow luminous level indicators and front apertures.
e = base()
for x in (2.5, 9.5):
    e += [box((x, 3.5, 4.5), (x + 4, 4.9, 12), 'frame'), box((x + .4, 4.9, 7.1), (x + 3.6, 13.2, 11.5), 'vessel'),
          box((x, 13.2, 4.5), (x + 4, 14.8, 12), 'frame'), box((x + .25, 14.8, 4.75), (x + 3.75, 15, 5.05), 'brushed_steel'),
          box((x + .25, 14.8, 11.45), (x + 3.75, 15, 11.75), 'brushed_steel'),
          box((x + .25, 14.8, 5.05), (x + .55, 15, 11.45), 'brushed_steel'),
          box((x + 3.45, 14.8, 5.05), (x + 3.75, 15, 11.45), 'brushed_steel'),
          box((x + .05, 5, 4.8), (x + .45, 13.2, 5.2), 'brushed_steel'),
          box((x + 3.55, 5, 4.8), (x + 3.95, 13.2, 5.2), 'brushed_steel'),
          box((x + 2.7, 5.7, 6.98), (x + 3.1, 12.5, 7.1), 'status', shade=False),
          box((x + .4, 5.3, 7), (x + 3.6, 5.55, 7.15), 'copper_trim'),
          box((x + .4, 12.55, 7), (x + 3.6, 12.8, 7.15), 'copper_trim'),
          box((x, 8.4, 4.8), (x + .7, 8.75, 7.2), 'frame')]
    for y in (6.4, 7.4, 8.4, 9.4, 10.4, 11.4): e.append(box((x + 3.14, y, 6.96), (x + 3.4, y + .1, 7.12), 'brushed_steel'))
e += [box((6, 11, 9), (10, 12.8, 11.5)), box((6.5, 12.8, 9.5), (9.5, 13.15, 10.5), 'copper_trim')]
display(e, (5.2, 3.6, 1.25), (10.8, 5.1, 1.9))
models['electrolyzer'] = ports(e, False)
# Reactor: a stepped vessel with a front inspection opening and visible mechanical agitation.
e = base()
e += [box((4, 3.5, 3), (12, 4.5, 13), 'frame'), box((3, 4, 4), (13, 5, 12), 'frame'),
      box((4.2, 5, 11), (11.8, 12.7, 12.7), 'vessel'),
      box((3, 5, 4), (4, 13, 12), 'frame'), box((12, 5, 4), (13, 13, 12), 'frame'),
      box((4.2, 5, 4.2), (11.8, 6.8, 10.5), 'vessel'), box((4.5, 6.8, 4.5), (11.5, 6.9, 10.4), 'status', shade=False),
      box((4, 12.9, 3), (12, 14.1, 13), 'frame'), box((3, 12.9, 4), (13, 14.1, 12), 'frame'),
      box((4, 14.1, 3.3), (12, 14.35, 3.65), 'brushed_steel'),
      box((4, 14.1, 12.35), (12, 14.35, 12.7), 'brushed_steel'),
      box((4, 14.1, 3.65), (4.35, 14.35, 12.35), 'brushed_steel'),
      box((11.65, 14.1, 3.65), (12, 14.35, 12.35), 'brushed_steel'), box((6, 14.35, 6), (10, 15.7, 10)),
      box((3.1, 5.1, 3.8), (3.9, 12.4, 4), 'brushed_steel'), box((12.1, 5.1, 3.8), (12.9, 12.4, 4), 'brushed_steel'),
      box((3.2, 7.1, 3.7), (12.8, 7.35, 4), 'copper_trim')]
display(e, (5.3, 9.5, 12.6), (10.7, 12, 13.1))
for x in (3.5, 12.5): bolt(e, x, 5.7, 3.65); bolt(e, x, 11.8, 3.65)
models['chemical_reactor'] = ports(e, False)
idle_parts['chemical_reactor'] = [box((7.55, 8.5, 7.55), (8.45, 13.5, 8.45), 'brushed_steel')] + idle_wheel(8, 11, 8, 2.8, blades=3)

# Storage: three independent cartridges, dark protective shells and steel release handles.
e = base()
e += [box((2, 3.5, 11.8), (14, 14.5, 14)), box((2, 3.5, 3), (3.3, 15, 14), 'frame'),
      box((12.7, 3.5, 3), (14, 15, 14), 'frame'), box((2, 14, 3), (14, 16, 14))]
for x in (3.7, 7.1, 10.5):
    e += [box((x, 4, 4.3), (x + 2, 13.4, 10.7), 'frame'), box((x + .2, 5.3, 4.1), (x + 1.8, 12.3, 4.3), 'graphite'),
          box((x - .1, 4.2, 4), (x + 2.1, 4.65, 10.9), 'copper_trim'),
          box((x - .1, 12.95, 4), (x + 2.1, 13.4, 10.9), 'brushed_steel'),
          box((x + .7, 6.1, 3.98), (x + 1.1, 11.7, 4.16), 'status', shade=False),
          box((x + .4, 5, 3.65), (x + 1.6, 5.3, 4.3), 'brushed_steel'),
          box((x + .4, 11.9, 3.65), (x + 1.6, 12.2, 4.3), 'brushed_steel')]
display(e, (4, 14.35, 2.3), (12, 15.5, 3))
for x in (2.65, 13.35): bolt(e, x, 6.2, 2.9); bolt(e, x, 12.8, 2.9)
models['matter_storage'] = ports(e, False)
# Terminal: slim slanted screen, offset pedestal, a real keyboard and a separate touch pad.
e = base()
e += [box((6.2, 3.4, 7.7), (9.8, 9, 10.8), 'frame'), box((6.2, 3.7, 7.5), (6.55, 8.5, 7.8), 'copper_trim')]
pivot = [8, 10, 8]
e += [box((2, 8, 7), (14, 15, 8.7), 'frame', rotation=(pivot, 'x', -22.5)),
      box((2.7, 8.55, 6.75), (13.3, 14.45, 7.04), 'rubber', {'north': 'screen'}, rotation=(pivot, 'x', -22.5)),
      box((2, 14.8, 7), (14, 15, 8.7), 'brushed_steel', rotation=(pivot, 'x', -22.5)),
      box((2.6, 4.7, 2), (13.4, 5.7, 7.2), 'frame'), box((3.1, 5.7, 2.4), (10, 5.9, 6.7), 'rubber', {'up': 'keyboard'}),
      box((10.6, 5.71, 3.1), (12.6, 5.85, 5.9), 'rubber'), box((11, 5.86, 3.5), (12.2, 5.91, 3.7), 'status', shade=False)]
models['matter_terminal'] = e
# Industrial controller: open turbine well, layered dark reaction core and exposed load-bearing cage.
e = base()
e += [box((3, 3.4, 3), (13, 6, 13)), box((4, 6, 4), (12, 10, 12), 'vessel'),
      box((4.3, 6.5, 3.9), (4.65, 9.5, 4.1), 'status', shade=False),
      box((11.35, 6.5, 3.9), (11.7, 9.5, 4.1), 'status', shade=False),
      box((3, 10, 3), (13, 11.25, 13), 'frame'), box((4, 11.25, 4), (12, 11.4, 12), 'rubber'),
      box((3, 10.1, 2.9), (13, 10.4, 3.1), 'copper_trim')]
# Nothing covers x/z4..12 above y11.4: full-height neighboring casings cannot hide the rotor.
e += [box((3, 14.5, 3), (13, 15.65, 4), 'frame'), box((3, 14.5, 12), (13, 15.65, 13), 'frame'),
      box((3, 14.5, 4), (4, 15.65, 12), 'frame'), box((12, 14.5, 4), (13, 15.65, 12), 'frame'),
      box((3, 15.65, 3), (13, 16, 4), 'brushed_steel'), box((3, 15.65, 12), (13, 16, 13), 'brushed_steel'),
      box((3, 15.65, 4), (4, 16, 12), 'brushed_steel'), box((12, 15.65, 4), (13, 16, 12), 'brushed_steel')]
for x in (2, 12):
    for z in (2, 12):
        e += [box((x, 3, z), (x + 2, 15.5, z + 2), 'frame'),
              box((x + .25, 4, z - .03), (x + .5, 13.5, z + .1), 'brushed_steel'),
              box((x + .65, 12.5, z - .04), (x + 1.4, 14, z + .12), 'copper_trim')]
for x in (3, 13): bolt(e, x, 5.1, 1.9); bolt(e, x, 11.4, 1.9)
display(e, (5, 4, 1.5), (11, 6.7, 2.2))
models['industrial_reactor'] = ports(e, False)
idle_parts['industrial_reactor'] = idle_wheel(8, 13, 8, 3.6)

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

# Structural casing: inset maintenance panels, a continuous dark shell and slim reinforcement.
e = [box((.5, .5, .5), (15.5, 15.5, 15.5))]
for x in (0, 13.5):
    for z in (0, 13.5):
        e += [box((x, 0, z), (x + 2.5, 16, z + 2.5), 'frame'),
              box((x + .5, 15.9, z + .5), (x + 1, 16, z + 1), 'brushed_steel')]
e += [box((2.5, 1.2, .35), (13.5, 1.45, .55), 'brushed_steel'), box((2.5, 14.55, .35), (13.5, 14.8, .55), 'brushed_steel'),
      box((2.5, 1.2, 15.45), (13.5, 1.45, 15.65), 'brushed_steel'), box((2.5, 14.55, 15.45), (13.5, 14.8, 15.65), 'brushed_steel'),
      box((3, 4, .35), (13, 12, .55), 'rubber', {'north': 'vent'}),
      box((3, 4, 15.45), (13, 12, 15.65), 'rubber', {'south': 'vent'}),
      box((.35, 4, 3), (.55, 12, 13), 'rubber', {'west': 'panel'}),
      box((15.45, 4, 3), (15.65, 12, 13), 'rubber', {'east': 'panel'}),
      box((3, 15.5, 3), (13, 15.7, 13), 'rubber', {'up': 'panel'}),
      box((2.5, 2, .3), (4.5, 2.4, .55), 'copper_trim'), box((11.5, 2, .3), (13.5, 2.4, .55), 'copper_trim')]
json_file('models/block/reactor_casing.json', {'parent': 'minecraft:block/block', 'textures': TEXTURES, 'elements': e})

# Pipe manifold plus six independently selected arms. Dark conduit, steel couplings, slim copper collars.
core = [box((5, 5, 5), (11, 11, 11), 'frame'),
        box((6, 6, 4.8), (10, 10, 5.05), 'rubber', {'north': 'port_matter'}),
        box((6, 10.95, 6), (10, 11.15, 10), 'rubber', {'up': 'panel'})]
arm = [box((6, 6, 0), (10, 10, 6), 'graphite'), box((5.3, 5.3, 0), (10.7, 10.7, 1), 'frame'),
       box((5.65, 5.65, 1), (10.35, 10.35, 1.45), 'copper_trim'),
       box((5.3, 5.3, 0), (10.7, 5.55, 1), 'brushed_steel'),
       box((7.5, 9.95, 1.6), (8.5, 10.08, 4.8), 'status', shade=False)]
json_file('models/block/matter_pipe.json', {'parent': 'minecraft:block/block', 'textures': TEXTURES, 'elements': core})
json_file('models/block/matter_pipe_arm.json', {'parent': 'minecraft:block/block', 'textures': TEXTURES, 'elements': arm})
multipart = [{'apply': {'model': 'rematter:block/matter_pipe'}}]
for side, rotation in [('north', {}), ('east', {'y': 90}), ('south', {'y': 180}), ('west', {'y': 270}), ('up', {'x': 270}), ('down', {'x': 90})]:
    multipart.append({'when': {side: 'true'}, 'apply': {'model': 'rematter:block/matter_pipe_arm', **rotation}})
json_file('blockstates/matter_pipe.json', {'multipart': multipart})
rear_arm = copy.deepcopy(arm)
for element in rear_arm:
    element['from'][2], element['to'][2] = 16 - element['to'][2], 16 - element['from'][2]
json_file('models/item/matter_pipe.json', {'parent': 'minecraft:block/block', 'textures': TEXTURES, 'elements': core + arm + rear_arm})
json_file('items/matter_pipe.json', {'model': {'type': 'minecraft:model', 'model': 'rematter:item/matter_pipe'}})
print(f'Generated {len(models)} machine models, state variants, complete item models, pipe parts and original pixel materials.')
