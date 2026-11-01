#!/usr/bin/env python3
"""Original Clean Lab multiblock modules. Writes only the five module assets and recipes."""
from pathlib import Path
import copy
import json
import math
import struct
import zlib

RES = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = RES / 'assets/rematter'
NAMES = ('grinding_unit', 'separation_unit', 'mixing_basin', 'field_coil', 'reaction_chamber')
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
TEXTURES = {name: 'rematter:block/' + name for name in ('ceramic', 'graphite', 'brushed_steel', 'copper_trim', 'rubber', 'reaction_chamber_crystal')}
TEXTURES['particle'] = TEXTURES['ceramic']

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')

def box(a, b, material='ceramic', rotation=None):
    item = {'from': list(a), 'to': list(b), 'faces': {f: {'texture': '#' + material} for f in FACES}}
    if rotation: item['rotation'] = rotation
    return item

def ring(e, x1, z1, x2, z2, y1, y2, thickness, material):
    e.extend([box((x1,y1,z1),(x2,y2,z1+thickness),material),
              box((x1,y1,z2-thickness),(x2,y2,z2),material),
              box((x1,y1,z1+thickness),(x1+thickness,y2,z2-thickness),material),
              box((x2-thickness,y1,z1+thickness),(x2,y2,z2-thickness),material)])

def frame():
    e=[]
    # Four pale load-bearing posts with graphite shoes. Align between stacked modules.
    for x in (1,13.5):
        for z in (1,13.5):
            e.extend([box((x,0,z),(x+1.5,1.1,z+1.5),'graphite'),
                      box((x,.9,z),(x+1.5,15.1,z+1.5),'ceramic'),
                      box((x,14.9,z),(x+1.5,16,z+1.5),'graphite')])
    # Rear braces leave the front and both sides open; hardware has no pretend external ports.
    e.extend([box((2.5,1,13.75),(13.5,2.1,14.75),'ceramic'),
              box((2.5,13.9,13.75),(13.5,15,14.75),'ceramic')])
    for x in (1.45,13.95):
        for y in (1.4,14.2):e.append(box((x,y,.93),(x+.6,y+.6,1.07),'copper_trim'))
    return e

def octagonal_roller(x,y,z):
    h=2.1/math.sqrt(2)
    out=[box((x-4,y-h,z-h),(x+4,y+h,z+h),'brushed_steel'),
         box((x-4,y-h,z-h),(x+4,y+h,z+h),'brushed_steel',{'origin':[x,y,z],'axis':'x','angle':45}),
         box((x-4.5,y-.35,z-.35),(x+4.5,y+.35,z+.35),'copper_trim')]
    for angle in (0,90,180,270):
        # Legal JSON rotations only: quarter-turn ribs are emitted directly.
        if angle==0:a,b=(x-3.7,y+1.49,z-.18),(x+3.7,y+1.74,z+.18)
        elif angle==180:a,b=(x-3.7,y-1.74,z-.18),(x+3.7,y-1.49,z+.18)
        elif angle==90:a,b=(x-3.7,y-.18,z+1.49),(x+3.7,y+.18,z+1.74)
        else:a,b=(x-3.7,y-.18,z-1.74),(x+3.7,y+.18,z-1.49)
        out.append(box(a,b,'copper_trim'))
    return out

def sieve_idle():
    e=[];ring(e,3,3,13,13,7.7,8.3,.5,'brushed_steel')
    for n in range(4,13):
        e.append(box((n-.06,7.94,3.5),(n+.06,8.06,12.5),'brushed_steel'))
        e.append(box((3.5,8.06,n-.06),(12.5,8.18,n+.06),'brushed_steel'))
    return e

def paddle_idle():
    e=[box((7.6,2.7,7.6),(8.4,12,8.4),'brushed_steel')]
    # Three real radial paddles, sampled into small cuboids for legal vanilla item geometry.
    for blade in range(3):
        angle=blade*math.tau/3
        for step in range(7):
            r=.65+step*.5;x=8+math.cos(angle)*r;z=8-math.sin(angle)*r
            e.append(box((x-.35,4.7,z-.35),(x+.35,5.7,z+.35),'copper_trim'))
        x=8+math.cos(angle)*3.85;z=8-math.sin(angle)*3.85
        e.append(box((x-.4,4.1,z-.4),(x+.4,5.7,z+.4),'brushed_steel'))
    return e

def crystal_idle():
    e=[box((6.4,5.3,6.4),(9.6,10.7,9.6),'reaction_chamber_crystal')]
    e.append(box((6.4,5.3,6.4),(9.6,10.7,9.6),'reaction_chamber_crystal',{'origin':[8,8,8],'axis':'y','angle':45}))
    for y in (5.3,10.7):
        e.append(box((6.9,y-1.1,6.9),(9.1,y+1.1,9.1),'reaction_chamber_crystal',{'origin':[8,y,8],'axis':'z','angle':45}))
    return e

models={};idle={}
e=frame()
# Hopper rim and two narrow bearing pedestals keep both working rollers visible.
ring(e,2.6,2.8,13.4,13.2,14.25,15.3,.7,'ceramic')
for x in (2.7,12):
    e.append(box((x,8.7,3.3),(x+1.3,12.4,12.7),'ceramic'))
    for z in (5.5,10.5):
        e.append(box((x-.08,9.65,z-.85),(x+1.38,11.35,z+.85),'graphite'))
        e.append(box((x-.13,10.15,z-.35),(x+1.43,10.85,z+.35),'copper_trim'))
ring(e,4,4,12,12,1.2,2.2,.6,'brushed_steel')
models['grinding_unit']=e;idle['grinding_unit']=octagonal_roller(8,10.5,5.5)+octagonal_roller(8,10.5,10.5)

e=frame()
# Open sieve carriers, four copper spring stacks and a shallow collection throat.
for x in (2.6,12.8):
    e.append(box((x,4.7,2.8),(x+.6,6,13.2),'ceramic'))
    for z in (4,11.5):
        for y in (6,6.5,7):e.append(box((x-.12,y,z),(x+.72,y+.18,z+.5),'copper_trim'))
ring(e,4,4,12,12,2,3,.6,'brushed_steel')
for z in (3.4,12):e.append(box((3.4,3, z),(12.6,4,z+.6),'ceramic'))
models['separation_unit']=e;idle['separation_unit']=sieve_idle()

e=frame()
e.extend([box((2.8,1,2.8),(13.2,2.5,13.2),'ceramic'),
          box((2.8,2.5,2.8),(3.5,7.4,13.2),'ceramic'),box((12.5,2.5,2.8),(13.2,7.4,13.2),'ceramic'),
          box((3.5,2.5,12.5),(12.5,7.4,13.2),'ceramic'),box((3.5,2.5,2.8),(12.5,4.1,3.5),'ceramic'),
          box((3.5,2.5,3.5),(12.5,2.7,12.5),'brushed_steel'),
          box((6.8,12,6.8),(9.2,14,9.2),'graphite'),
          box((7.6,12.8,2.5),(8.4,13.5,13.5),'ceramic'),
          box((6.6,13.8,6.6),(9.4,14.5,9.4),'ceramic')])
models['mixing_basin']=e;idle['mixing_basin']=paddle_idle()

e=frame()
e.extend([box((4,1,4),(12,2.4,12),'ceramic'),box((6,2.4,6),(10,3,10),'graphite'),
          box((6,13,6),(10,13.6,10),'graphite'),box((4,13.6,4),(12,14.8,12),'ceramic')])
for y in (3.5,6.2,8.9,11.6):
    # Real hollow copper loops with clipped corners, never a glowing solid tank.
    e.extend([box((4.6,y,2.9),(11.4,y+.65,3.8),'copper_trim'),box((4.6,y,12.2),(11.4,y+.65,13.1),'copper_trim'),
              box((2.9,y,4.6),(3.8,y+.65,11.4),'copper_trim'),box((12.2,y,4.6),(13.1,y+.65,11.4),'copper_trim')])
    for x,z,angle in ((4.15,4.15,-45),(11.85,4.15,45),(4.15,11.85,45),(11.85,11.85,-45)):
        e.append(box((x-1.25,y,z-.45),(x+1.25,y+.65,z+.45),'copper_trim',{'origin':[x,y,z],'axis':'y','angle':angle}))
# Rear bus connects the turns; foreground remains open to the reaction axis.
e.append(box((7.55,3.5,12.7),(8.45,12.25,13.4),'copper_trim'))
models['field_coil']=e;idle['field_coil']=[]

e=frame()
e.extend([box((3.3,1.1,3.3),(12.7,2.3,12.7),'ceramic'),box((5.8,2.3,5.8),(10.2,3.2,10.2),'graphite'),
          box((6.5,3.2,6.5),(9.5,3.45,9.5),'copper_trim'),
          box((3.3,13.7,3.3),(12.7,14.9,12.7),'ceramic'),box((5.8,12.8,5.8),(10.2,13.7,10.2),'graphite'),
          box((6.5,12.55,6.5),(9.5,12.8,9.5),'copper_trim')])
# Four slim isolated chamber struts frame, rather than hide, the faceted core.
for x in (4,11.5):
    for z in (4,11.5):e.append(box((x,2.3,z),(x+.5,13.7,z+.5),'brushed_steel'))
models['reaction_chamber']=e;idle['reaction_chamber']=crystal_idle()

DISPLAY={'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.625,.625,.625]},
         'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.25,.25,.25]},
         'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.5,.5,.5]},
         'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]},
         'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.4,.4,.4]}}
for name in NAMES:
    model={'textures':TEXTURES,'elements':models[name]}
    write(ASSETS/f'models/block/{name}.json',model)
    variants={}
    for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]:
        for active in ('false','true'):
            variants[f'active={active},facing={facing}']={'model':f'rematter:block/{name}','y':rotation}
    write(ASSETS/f'blockstates/{name}.json',{'variants':variants})
    item=copy.deepcopy(model);item['elements']+=idle[name];item['display']=DISPLAY
    write(ASSETS/f'models/item/{name}.json',item)
    write(ASSETS/f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'rematter:item/{name}'}})
    write(RES/f'data/rematter/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'rematter:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

recipes={
'grinding_unit':['rematter:machine_frame','minecraft:piston','minecraft:piston','minecraft:flint','minecraft:flint','minecraft:iron_ingot','minecraft:iron_ingot'],
'separation_unit':['rematter:machine_frame','minecraft:iron_bars','minecraft:iron_bars','minecraft:string','minecraft:string','rematter:control_circuit'],
'mixing_basin':['rematter:machine_frame','minecraft:cauldron','minecraft:piston','minecraft:copper_ingot'],
'field_coil':['rematter:machine_frame','minecraft:copper_ingot','minecraft:copper_ingot','minecraft:copper_ingot','minecraft:copper_ingot','rematter:control_circuit','rematter:rhedon'],
'reaction_chamber':['rematter:machine_frame','minecraft:glass','minecraft:glass','rematter:matrix_cluster','rematter:optical_circuit'],
}
for name,ingredients in recipes.items():
    write(RES/f'data/rematter/recipe/{name}.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':ingredients,'result':{'id':'rematter:'+name,'count':1}})
    write(RES/f'data/rematter/advancement/recipes/{name}.json',{'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['rematter:machine_frame']}]}},'has_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'rematter:'+name}}},'requirements':[['has_material','has_recipe']],'rewards':{'recipes':['rematter:'+name]}})
# The vanilla mineable tag references this private-to-the-mod grouping once.
write(RES/'data/rematter/tags/block/industrial_modules.json',{'replace':False,'values':['rematter:'+name for name in NAMES]})

# Original restrained facet sheet; no third-party texture or concept-art extraction.
def pixel(x,y):
    base=(101,196,191) if x < 15 else (64,150,158)
    if x<3 or y<2:base=(193,234,219)
    elif 3<=x<9 and 3<=y<18:base=(151,219,204)
    elif x>24 and y>9:base=(46,116,132)
    if 10<=x<13 and y<11:base=(211,239,224)
    return bytes((*base,255))
def chunk(k,v):return struct.pack('>I',len(v))+k+v+struct.pack('>I',zlib.crc32(k+v)&0xffffffff)
raw=b''.join(b'\0'+b''.join(pixel(x,y) for x in range(32)) for y in range(32))
p=ASSETS/'textures/block/reaction_chamber_crystal.png';p.parent.mkdir(parents=True,exist_ok=True)
p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
print('Generated five original module models, complete idle items, recipes and discovery advancements.')
