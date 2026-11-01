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
    'graphite': (57, 63, 65), 'edge': (76, 83, 84), 'steel': (174, 183, 181), 'ceramic': (229, 222, 204),
    'copper': (174, 111, 73), 'copper_light': (218, 158, 110), 'rubber': (30, 37, 39),
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
        d = 2 if y < 3 else -3 if y > 28 else 0
        if x == 0 or y == 31: d = -6
        if x == 1 or y == 1: d = 4
        if base == 'ceramic':
            # Broad fired-ceramic pixel clusters, not random noise or a tiled panel grid.
            d = 5 if x < 3 or y < 3 else -7 if x > 28 or y > 28 else 0
            if 5 <= x <= 17 and 5 <= y <= 11: d += 3
            if 17 <= x <= 27 and 17 <= y <= 25: d -= 4
        if style == 'hazard':
            if y < 7 or y > 24: return rgba(PALETTE['graphite'])
            return rgba(PALETTE['copper_light'] if ((x + y) // 7) % 2 else PALETTE['rubber'])
        return rgba(c, d)
    png(f'textures/block/{name}.png', 32, 32, pixel)

for name, color in [('ceramic', 'ceramic'), ('graphite', 'graphite'), ('brushed_steel', 'steel'), ('copper_trim', 'copper'),
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
# Original oiled timber for the non-electronic hand bench; long restrained grain.
def wood_pixel(x, y):
    delta = -13 if x in (0, 31) else 14 if x in (1, 2) else 5 if x in (6, 18, 27) else -7 if x in (5, 17, 26) else 0
    if 7 <= x <= 13 and 8 <= y <= 21: delta += 4
    if 20 <= x <= 23 and 12 <= y <= 27: delta -= 4
    if y in (0, 31): delta -= 8
    return rgba((192, 161, 113), delta)
png('textures/block/bench_wood.png', 32, 32, wood_pixel)

TEXTURES = {name: f'rematter:block/{name}' for name in ('ceramic', 'graphite', 'brushed_steel', 'copper_trim', 'frame', 'rubber', 'hazard', 'port_matter', 'port_energy', 'vent', 'panel', 'keyboard', 'vessel')}
TEXTURES |= {'screen': 'rematter:block/display_idle', 'status': 'rematter:block/indicator_idle', 'wood': 'rematter:block/bench_wood', 'particle': 'rematter:block/ceramic'}

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

def pin(e, x, y, z):
    """A single readable copper fastener, never a busy repeating texture."""
    e.append(box((x-.26,y-.26,z-.06),(x+.26,y+.26,z+.12),'copper_trim'))

def base(width=14):
    x=(16-width)/2
    e=[box((x,.7,1),(16-x,2.1,15),'graphite'),
       box((x+.3,2.1,1.3),(15.7-x,3.2,14.7),'ceramic')]
    for xx in (x+.5,12.5-x):
        for z in (2,11.5):e.append(box((xx,0,z),(xx+2.5,.7,z+2.5),'rubber'))
    return e

def ports(e, top=False):
    # Recessed, readable matter and FE connectors; frame never covers the working window.
    e.extend([box((.85,4.7,7),(2,8.3,10.6),'graphite',{'west':'port_matter'}),
              box((14,4.7,7),(15.15,8.3,10.6),'graphite',{'east':'port_matter'}),
              box((5.8,4.5,14),(10.2,8.9,15.1),'graphite',{'south':'port_energy'})])
    if top:e.append(box((6,14.8,6),(10,15.15,10),'graphite',{'up':'port_energy'}))
    return e

def status(e,x=12.8,y=12.2,z=1.7):
    e.extend([box((x-.14,y-.14,z+.04),(x+1.14,y+1.14,z+.3),'graphite'),
              box((x,y,z),(x+1,y+1,z+.12),'status',shade=False)])

def display(e,a=(4,4,1.7),b=(12,7.5,2)):
    e.append(box(a,b,'rubber',{'north':'screen'}))


def shell(e, opening_bottom=3.5, opening_top=12, roof=14.8):
    # Three large ceramic panels surround the open chamber. Dark corner guards are short.
    e.extend([box((1.2,3.2,2),(3.1,roof,14.8),'ceramic'),
              box((12.9,3.2,2),(14.8,roof,14.8),'ceramic'),
              box((3.1,3.2,12.6),(12.9,roof,14.8),'ceramic'),
              box((3.1,opening_top,2),(12.9,roof,12.6),'ceramic'),
              box((3.1,3.2,2),(12.9,opening_bottom,12.6),'graphite'),
              box((3.1,opening_bottom,12.35),(12.9,opening_top,12.6),'rubber'),
              box((2.9,opening_bottom,1.8),(3.25,opening_top,2.3),'graphite'),
              box((12.75,opening_bottom,1.8),(13.1,opening_top,2.3),'graphite'),
              box((3.25,opening_top-.15,1.8),(12.75,opening_top+.15,2.3),'graphite')])
    for x in (1,13.5):
        e.append(box((x,roof-1.1,1.8),(x+1.5,roof+.12,4.3),'graphite'))
        pin(e,x+.75,roof-.45,1.74)
    ports(e)

def tray(e,y=3.3,z=.6,width=10):
    x=(16-width)/2
    e.extend([box((x,y,z),(16-x,y+.35,z+5),'brushed_steel'),
              box((x,y+.35,z),(x+.3,y+1,z+5),'graphite'),
              box((15.7-x,y+.35,z),(16-x,y+1,z+5),'graphite'),
              box((x,y+.35,z+4.7),(16-x,y+1,z+5),'graphite'),
              box((x-.1,y-.2,z-.15),(16.1-x,y+.4,z+.15),'graphite'),
              box((5.5,y-.08,z-.5),(10.5,y+.27,z-.15),'ceramic')])

def feed(e,y=14.8,x1=4,x2=12,z1=5,z2=12,mat='graphite'):
    e.extend([box((x1,y,z1),(x2,y+1,z1+.65),mat),
              box((x1,y,z2-.65),(x2,y+1,z2),mat),
              box((x1,y,z1+.65),(x1+.65,y+1,z2-.65),mat),
              box((x2-.65,y,z1+.65),(x2,y+1,z2-.65),mat)])


def idle_wheel(cx,cy,cz,radius,vertical=False,blades=6,phase=0):
    e=[]
    for n in range(blades):
        a=n*math.tau/blades+phase
        for r in (.8,1.3,1.8,2.3,2.8,3.2):
            if r+.3>radius:continue
            u,v=r*math.cos(a),r*math.sin(a)
            if vertical:e.append(box((cx+u-.28,cy+v-.28,cz-.8),(cx+u+.28,cy+v+.28,cz+.8),'brushed_steel'))
            else:e.append(box((cx+u-.35,cy-.2,cz+v-.35),(cx+u+.35,cy+.2,cz+v+.35),'brushed_steel'))
    if vertical:e.append(box((cx-.8,cy-.8,cz-1.05),(cx+.8,cy+.8,cz+1.05),'frame'))
    else:e.append(box((cx-.7,cy-.4,cz-.7),(cx+.7,cy+.4,cz+.7),'frame'))
    return e

def idle_roller(cx,cy,cz,radius):
    # Octagonal solid roller, axis X. The renderer uses the exact same two crossed cuboids.
    t=radius/math.sqrt(2)
    return [box((cx-4,cy-t,cz-t),(cx+4,cy+t,cz+t),'brushed_steel'),
            box((cx-4,cy-t,cz-t),(cx+4,cy+t,cz+t),'brushed_steel',rotation=([cx,cy,cz],'x',45)),
            box((cx-4.3,cy-.3,cz-.3),(cx+4.3,cy+.3,cz+.3),'frame')]

def idle_sieve(cx,cy,cz,width):
    a=width/2;e=[box((cx-a,cy-.15,cz-3),(cx+a,cy+.15,cz-2.7),'frame'),
      box((cx-a,cy-.15,cz+2.7),(cx+a,cy+.15,cz+3),'frame'),
      box((cx-a,cy-.15,cz-2.7),(cx-a+.3,cy+.15,cz+2.7),'frame'),
      box((cx+a-.3,cy-.15,cz-2.7),(cx+a,cy+.15,cz+2.7),'frame')]
    for k in range(-3,4):
        e.append(box((cx+k,cy-.04,cz-2.7),(cx+k+.12,cy+.06,cz+2.7),'brushed_steel'))
    for k in range(-2,3):e.append(box((cx-a+.3,cy-.04,cz+k),(cx+a-.3,cy+.06,cz+k+.12),'brushed_steel'))
    return e

models={};idle_parts={};model_textures={}
# Hand Preparator: an open pale-oak bench, two full-width rollers, sieve and collecting drawer.
e=[]
for x in (1.4,12.6):
    for z in (3,11.5):
        e.extend([box((x,.5,z),(x+2,8.1,z+2),'wood'),
                  box((x-.1,0,z-.1),(x+2.1,.65,z+2.1),'graphite')])
e.extend([box((1,7.5,2.8),(3.5,9,14),'wood'),box((12.5,7.5,2.8),(15,9,14),'wood'),
          box((3.5,7.5,12),(12.5,9,14),'wood'),
          box((1.3,3,3.5),(3.3,4,13),'wood'),box((12.7,3,3.5),(14.7,4,13),'wood'),
          box((3.3,3,11.8),(12.7,4,13),'wood'),
          box((2.6,8.8,3.8),(4,13.1,12.2),'wood'),box((12,8.8,3.8),(13.4,13.1,12.2),'wood'),
          box((2.4,10,4.8),(4.03,12,7.2),'graphite'),box((11.97,10,4.8),(14.8,12,7.2),'graphite'),
          box((2.4,10,8.8),(4.03,12,11.2),'graphite'),box((11.97,10,8.8),(13.6,12,11.2),'graphite')])
# A wide open hopper, no opaque plate over the rolls.
feed(e,13.2,2.5,13.5,3.5,12.8,'wood')
e.extend([box((2.4,13.15,3.4),(13.6,13.45,4.2),'graphite'),
          box((2.4,13.15,12.1),(13.6,13.45,12.9),'graphite')])
tray(e,2.2,.7)
for x in (2.2,13.8):pin(e,x,8.2,2.7)
models['hand_preparator']=e
idle_parts['hand_preparator']=idle_roller(8,11,6,1.6)+idle_roller(8,11,10,1.6)+idle_sieve(8,7.7,4.8,9)
idle_parts['hand_preparator'] += [box((13.4,10.8,5.8),(15.4,11.2,6.2),'frame'),
    box((14.95,10.78,5.78),(15.25,12.5,6.22),'copper_trim'),box((15.25,12.15,5.75),(15.95,12.55,6.25),'wood')]
# Electric Preparator: one calm shell; visible stacked rollers, mesh screen and a lower mixing well.
e=base();shell(e,3.5,12.7,14.8)
# Roof is open beneath the feeding throat, unlike a decorative hopper on a solid lid.
# Replace the single ceiling with four broad strips around x4..12,z3.8..11.6.
e=[v for v in e if not (v['from']==[3.1,12.7,2] and v['to']==[12.9,14.8,12.6])]
e.extend([box((3.1,12.7,2),(12.9,14.8,3.8),'ceramic'),box((3.1,12.7,11.6),(12.9,14.8,12.6),'ceramic'),
          box((3.1,12.7,3.8),(4,14.8,11.6),'ceramic'),box((12,12.7,3.8),(12.9,14.8,11.6),'ceramic'),
          box((3.3,8.1,3.2),(4,9.7,10),'frame'),box((12,8.1,3.2),(12.7,9.7,10),'frame'),
          box((5.3,3.4,4.3),(10.7,3.65,9.7),'rubber')])
e.extend([box((14.8,11.5,5.5),(15.02,13,12.4),'rubber'),
          box((15.02,11.85,5.8),(15.08,12.05,12.1),'frame'),box((15.02,12.45,5.8),(15.08,12.65,12.1),'frame')])
feed(e,14.8,3.5,12.5,3.3,12.1);tray(e,3.2,.7);status(e,12.95,10.9,1.72)
models['material_preparator']=e
idle_parts['material_preparator']=idle_roller(8,8.9,4.7,1.35)+idle_roller(8,8.9,8.3,1.35)+idle_sieve(8,6.3,6.4,8.5)
idle_parts['material_preparator'] += idle_wheel(8,4.7,7,1.8,blades=3)+[box((7.78,3.7,6.78),(8.22,5.95,7.22),'brushed_steel')]
# Analyzer: an asymmetric instrument bridge over a broad exposed sample stage.
e=base();e.extend([box((2,3.2,10.8),(4,14.7,14.5),'ceramic'),
 box((12.2,3.2,10.8),(14,12.2,14.5),'ceramic'),box((4,12.7,3.5),(14,14.8,14.5),'ceramic'),
 box((3.5,4.1,3),(12.5,4.5,12),'frame'),box((4,4.5,3.5),(12,4.68,11.5),'brushed_steel'),
 box((4.8,4.68,4.3),(11.2,4.7,10.7),'rubber'),box((3.5,5,10.5),(3.8,12.7,10.85),'brushed_steel'),
 box((12.15,5,10.5),(12.5,12.7,10.85),'brushed_steel'),box((4.1,12.4,3.5),(11.9,12.7,4),'graphite'),
 box((4,4.5,12.2),(12,12.7,12.6),'rubber')]);ports(e);status(e,12.6,13.2,3.2)
display(e,(5.5,7,12),(10.5,9.5,12.2));models['analyzer']=e
idle_parts['analyzer']=[box((4,10.7,7.1),(12,11.3,7.9),'brushed_steel'),box((4.4,10.5,7.25),(11.6,10.7,7.75),'frame')]
# Decomposer: low broad crushing window, a generous top inlet and an open collecting shelf.
e=base();shell(e,4.25,10.1,13.5);tray(e,3.5,.7)
feed(e,13.5,4,12,4.5,11.5);e.append(box((4.6,13.49,5.15),(11.4,13.51,10.85),'rubber'))
status(e,12.95,11.5,1.7);models['decomposer']=e
idle_parts['decomposer']=idle_wheel(5.5,7,5,2.22,True)+idle_wheel(10.5,7,5,2.22,True,phase=math.pi/6)
# Synthesizer: a large white press arch, matte guide columns and a recessed die bed.
e=base();e.extend([box((1.3,3.2,3),(3.7,14.8,14.8),'ceramic'),box((12.3,3.2,3),(14.7,14.8,14.8),'ceramic'),
 box((3.7,13,3),(12.3,15,14.8),'ceramic'),box((3.7,3.2,12.3),(12.3,13,14.8),'ceramic'),
 box((4.1,4.2,4.1),(11.9,5.3,11.9),'frame'),box((4.5,5.3,4.5),(11.5,5.55,11.5),'brushed_steel'),
 box((3.9,5.5,9.5),(4.3,13,10),'brushed_steel'),box((11.7,5.5,9.5),(12.1,13,10),'brushed_steel'),
 box((3.7,12.6,3),(12.3,13,3.5),'graphite')]);ports(e);status(e,12.7,13.1,2.8)
for x in (2.5,13.5):pin(e,x,4.2,2.94)
models['material_synthesizer']=e
idle_parts['material_synthesizer']=[box((4.5,9,4.5),(11.5,11,11.5),'brushed_steel'),box((4.9,8.7,4.9),(11.1,9,11.1),'frame'),box((7.35,11,7.35),(8.65,14.5,8.65),'brushed_steel')]
# Electrolyzer: two clearly separated cells against a pale rear spine.
e=base();e.extend([box((1.5,3.2,11.5),(14.5,15,14.8),'ceramic'),box((7.2,3.2,4.8),(8.8,14.5,11.5),'ceramic')])
for cx in (4.5,11.5):
 e.extend([box((cx-2.6,3.4,4.2),(cx+2.6,5.1,10.5),'ceramic'),box((cx-2.4,12.4,4.2),(cx+2.4,14.7,10.5),'ceramic'),
           box((cx-2,5.1,8.4),(cx+2,12.4,9),'vessel'),box((cx-2.25,5.1,5.5),(cx-1.9,12.4,9.1),'frame'),
           box((cx+1.9,5.1,5.5),(cx+2.25,12.4,9.1),'frame'),box((cx-.25,5.1,7.2),(cx+.25,12.4,7.7),'brushed_steel')])
ports(e);status(e,7.5,10,4.55);models['electrolyzer']=e;idle_parts['electrolyzer']=[]
# Reactor: open cylindrical-looking vessel assembled from broad stepped panels.
e=base();e.extend([box((3,3.2,3),(13,7.4,13),'ceramic'),box((2,4,5),(3,7.4,11),'ceramic'),box((13,4,5),(14,7.4,11),'ceramic'),
 box((3,7.4,10.9),(13,13.8,13),'ceramic'),box((3,7.4,4),(4.8,13.8,10.9),'ceramic'),box((11.2,7.4,4),(13,13.8,10.9),'ceramic'),
 box((4.8,7.4,4),(11.2,7.7,10.9),'rubber'),box((3,13.8,4),(13,15,6.1),'graphite'),
 box((3,13.8,10.9),(13,15,13),'graphite'),box((3,13.8,6.1),(4.8,15,10.9),'graphite'),box((11.2,13.8,6.1),(13,15,10.9),'graphite')]);ports(e);status(e,10.7,4.5,2.8)
models['chemical_reactor']=e;idle_parts['chemical_reactor']=idle_wheel(8,11,8,2.8,blades=3)+[box((7.55,8.5,7.55),(8.45,13.5,8.45),'brushed_steel')]
# Drive: one empty horizontal bay; actual cartridge is rendered only from the server-synced flag.
e=base();shell(e,5.2,10.8,14.4)
e.extend([box((3.4,5.2,2.9),(12.6,5.65,11),'graphite'),box((3.5,5.65,3),(3.85,10.4,11),'brushed_steel'),
 box((12.15,5.65,3),(12.5,10.4,11),'brushed_steel'),box((3.4,10.4,10.7),(12.6,10.8,11),'frame'),
 box((3.2,11.2,1.85),(11,13.8,2.2),'ceramic')]);status(e,12.95,12,1.7)
models['matter_drive']=e;idle_parts['matter_drive']=[]
# Legacy storage: a tall closed three-column vault, distinct from the one-cell open Drive.
e=base();e.extend([box((1.2,3.2,2),(14.8,15.5,14.8),'ceramic'),box((3,4.1,1.6),(13,13.9,2),'graphite')])
for x in (3.5,6.8,10.1):
 e.extend([box((x,4.6,1.2),(x+2.4,13.4,1.7),'frame'),box((x+.35,5.1,1),(x+2.05,12.9,1.25),'ceramic'),
           box((x+.85,8, .85),(x+1.55,11.5,1.02),'vessel'),box((x+.85,11.7,.83),(x+1.55,12.3,1.02),'status',shade=False)])
ports(e);models['matter_storage']=e;idle_parts['matter_storage']=[]
# Terminal: a short pedestal and a single large angled face, with one quiet keyboard shelf.
e=base();e.extend([box((4,3.2,7),(12,8,13.5),'ceramic'),box((2,7.8,5.5),(14,14.7,13.5),'ceramic'),
 box((2.6,8.3,5.2),(13.4,14.2,5.6),'graphite',rotation=([8,8,5.5],'x',-22.5)),
 box((3.2,8.9,5.1),(12.8,13.6,5.25),'rubber',{'north':'screen'},rotation=([8,8,5.5],'x',-22.5)),
 box((2,6.6,1.7),(14,7.5,7),'ceramic'),box((3.2,7.5,2.5),(12.8,7.65,5.5),'rubber',{'up':'keyboard'})]);ports(e)
models['matter_terminal']=e;idle_parts['matter_terminal']=[]
# Industrial controller items are front panels, not miniature complete machines.
# Their working mechanisms are rendered only inside the formed 3-D shell behind this block.
for name in ('industrial_reactor','industrial_preparator'):
 e=[box((0,0,0),(1.4,16,3),'graphite'),box((14.6,0,0),(16,16,3),'graphite'),
    box((1.4,0,.2),(14.6,1.4,3),'ceramic'),box((1.4,14.6,.2),(14.6,16,3),'ceramic'),
    box((1.4,1.4,.2),(14.6,7.8,3),'ceramic'),
    box((2.5,2.1,.08),(13.5,7.2,.25),'graphite'),
    box((3.1,2.7,.02),(11.9,6.6,.1),'rubber',{'north':'screen'}),
    box((1.4,7.8,.55),(14.6,14.6,.7),'glass'),
    box((1.4,7.65,.15),(14.6,8,.5),'graphite')]
 for x in (.7,15.3):
  for y in (.7,15.3):pin(e,x,y,.08)
 status(e,12.3,3.9,.04)
 models[name]=e
 model_textures[name]={'glass':'rematter:block/reactor_glass'}
 idle_parts[name]=[]

for name, elements in models.items():
    model = {'parent': 'minecraft:block/block', 'textures': TEXTURES | model_textures.get(name, {}), 'elements': elements}
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

# Large clean casing panels stay readable as a complete multiblock ring.
e=[box((.35,.35,.35),(15.65,15.65,15.65),'ceramic')]
for x in (0,14.5):
 for z in (0,14.5):e.append(box((x,0,z),(x+1.5,16,z+1.5),'graphite'))
for z in (.28,15.65):
 e.append(box((2.5,2.5,z),(13.5,13.5,z+.07),'ceramic'))
for x in (2.1,13.9):
 for y in (2.1,13.9):pin(e,x,y,.22)
json_file('models/block/reactor_casing.json',{'parent':'minecraft:block/block','textures':TEXTURES,'elements':e})
# Connected matter conduit: ceramic collars around a dark line; six state-driven arms remain unchanged.
core=[box((5,5,5),(11,11,11),'ceramic'),box((6,6,4.8),(10,10,5.05),'graphite',{'north':'port_matter'})]
arm=[box((6.3,6.3,1.3),(9.7,9.7,6),'graphite'),box((5.4,5.4,0),(10.6,10.6,1.3),'ceramic'),
     box((6.1,6.1,1.3),(9.9,9.9,1.6),'copper_trim')]
json_file('models/block/matter_pipe.json',{'parent':'minecraft:block/block','textures':TEXTURES,'elements':core})
json_file('models/block/matter_pipe_arm.json',{'parent':'minecraft:block/block','textures':TEXTURES,'elements':arm})
multipart=[{'apply':{'model':'rematter:block/matter_pipe'}}]
for side,rotation in [('north',{}),('east',{'y':90}),('south',{'y':180}),('west',{'y':270}),('up',{'x':270}),('down',{'x':90})]:
 multipart.append({'when':{side:'true'},'apply':{'model':'rematter:block/matter_pipe_arm',**rotation}})
json_file('blockstates/matter_pipe.json',{'multipart':multipart})
rear_arm=copy.deepcopy(arm)
for element in rear_arm:element['from'][2],element['to'][2]=16-element['to'][2],16-element['from'][2]
json_file('models/item/matter_pipe.json',{'parent':'minecraft:block/block','textures':TEXTURES,'elements':core+arm+rear_arm})
json_file('items/matter_pipe.json',{'model':{'type':'minecraft:model','model':'rematter:item/matter_pipe'}})
print(f'Generated {len(models)} Clean Lab machine models, state variants, complete item models, pipe parts and original materials.')

# Clear laboratory glass: cutout highlights leave the chamber genuinely visible.
def glass_pixel(x,y):
    if x in (0,31) or y in (0,31): return rgba(PALETTE['steel'])
    if x in (1,30) or y in (1,30): return rgba((199,220,215))
    if 5 <= x <= 11 and y == 26-x or 21 <= x <= 25 and y == 29-x:
        return rgba((205,229,224))
    return (0,0,0,0)
png('textures/block/reactor_glass.png',32,32,glass_pixel)
glass_textures=TEXTURES|{'glass':'rematter:block/reactor_glass'}
e=[box((.55,.55,.55),(15.45,15.45,15.45),'glass')]
for x in (0,15.2):
 for z in (0,15.2):e.append(box((x,0,z),(x+.8,16,z+.8),'ceramic'))
for y in (0,15.2):
 for z in (0,15.2):e.append(box((.8,y,z),(15.2,y+.8,z+.8),'ceramic'))
 for x in (0,15.2):e.append(box((x,y,.8),(x+.8,y+.8,15.2),'ceramic'))
json_file('models/block/reactor_glass.json',{'parent':'minecraft:block/block','textures':glass_textures,'elements':e})
json_file('blockstates/reactor_glass.json',{'variants':{'':{'model':'rematter:block/reactor_glass'}}})
json_file('items/reactor_glass.json',{'model':{'type':'minecraft:model','model':'rematter:block/reactor_glass'}})
# A single combined connector is deliberately the same on every service face.
def combined_port(x,y):
    if min(x,y,31-x,31-y)<3:return rgba(PALETTE['ceramic'],4 if x<2 or y<2 else 0)
    if (x in (5,26) and y in (5,26)):return rgba(PALETTE['copper'])
    if min(x,y,31-x,31-y)<7:return rgba(PALETTE['graphite'])
    if x in (7,24) or y in (7,24):return rgba(PALETTE['steel'], -25)
    if (10 <= x <= 12 and 11 <= y <= 20) or (11 <= x <= 15 and y in (14,15)):
        return rgba(PALETTE['copper_light'])
    if (x in (19,20) and 12 <= y <= 20) or (17 <= x <= 22 and y in (12,19,20)):
        return rgba(PALETTE['cyan'])
    return rgba(PALETTE['rubber'])
png('textures/block/port_combined.png',32,32,combined_port)
json_file('models/block/machine_port.json',{'parent':'minecraft:block/cube_all','textures':{'all':'rematter:block/port_combined'}})
json_file('blockstates/machine_port.json',{'variants':{'':{'model':'rematter:block/machine_port'}}})
json_file('items/machine_port.json',{'model':{'type':'minecraft:model','model':'rematter:block/machine_port'}})
