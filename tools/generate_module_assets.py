#!/usr/bin/env python3
"""Derive ceiling and item variants from the Blockbench process modules."""
from pathlib import Path
import argparse
import copy
import json
import math
from material_tiles import TILE, crystal_pixel, write_png

RES = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = RES / 'assets/rematter'
NAMES = ('grinding_unit', 'separation_unit', 'mixing_basin', 'field_coil', 'reaction_chamber')
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
parser = argparse.ArgumentParser(description=__doc__)
mode = parser.add_mutually_exclusive_group()
mode.add_argument('--models-only', action='store_true', help='Write model/blockstate references only; preserve recipes and every texture byte')
mode.add_argument('--textures-only', action='store_true', help='Refresh the shared crystal materials only')
ARGS = parser.parse_args()
MODELS_ONLY = ARGS.models_only

def write(path, data):
    if ARGS.textures_only:
        return
    if MODELS_ONLY and not path.is_relative_to(ASSETS):
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')

def box(a, b, material='housing', rotation=None):
    item = {'from': list(a), 'to': list(b), 'faces': {f: {'texture': '#' + material} for f in FACES}}
    if rotation: item['rotation'] = rotation
    return item

def prism(x,y,z,width,height):
    """A fixed-proportion cut crystal, identical to the live renderer geometry."""
    h=width/2
    body=height*.34
    tip=width*.32
    e=[box((x-h,y-body,z-h),(x+h,y+body,z+h),'reaction_chamber_crystal'),
       box((x-h,y-body,z-h),(x+h,y+body,z+h),'reaction_chamber_crystal',
           {'origin':[x,y,z],'axis':'y','angle':45})]
    for sign in (-1,1):
        cy=y+sign*body
        e.append(box((x-tip,cy-tip,z-tip),(x+tip,cy+tip,z+tip),'reaction_chamber_crystal',
                     {'origin':[x,cy,z],'axis':'z','angle':45}))
    return e

def suspended(elements):
    """Mirror the complete socket into the ceiling; all scales remain unchanged."""
    result=copy.deepcopy(elements)
    for element in result:
        lo,hi=element['from'][1],element['to'][1]
        element['from'][1],element['to'][1]=16-hi,16-lo
        faces=element['faces'];faces['up'],faces['down']=faces['down'],faces['up']
        if 'rotation' in element:
            rotation=element['rotation'];rotation['origin'][1]=16-rotation['origin'][1]
            if rotation['axis'] in ('x','z'):rotation['angle']=-rotation['angle']
    return result

def crystal_halo(x,y,z,radius=4.1):
    result=[]
    reach=radius*.61; thickness=.24
    for a,b in (((x-reach,y-.12,z-radius),(x+reach,y+.12,z-radius+thickness)),
                ((x-reach,y-.12,z+radius-thickness),(x+reach,y+.12,z+radius)),
                ((x-radius,y-.12,z-reach),(x-radius+thickness,y+.12,z+reach)),
                ((x+radius-thickness,y-.12,z-reach),(x+radius,y+.12,z+reach))):
        result.append(box(a,b,'reaction_chamber_crystal'))
    for dx in (-1,1):
        for dz in (-1,1):
            cx=x+dx*radius*.80;cz=z+dz*radius*.80
            result.append(box((cx-1.05,y-.12,cz-.12),(cx+1.05,y+.12,cz+.12),
                          'reaction_chamber_crystal',{'origin':[cx,y,cz],'axis':'y','angle':dx*dz*45}))
    return result

def inclined_prism(x,y,z,width,height,axis,angle):
    # A complete inward-pointing crystal; its caps stay true cubes after the tilt.
    half=width/2;body=height*.34;tip=width*.32
    result=[box((x-half,y-body,z-half),(x+half,y+body,z+half),'reaction_chamber_crystal',
                {'origin':[x,y,z],'axis':axis,'angle':angle})]
    a=math.radians(angle)
    for sign in (-1,1):
        offset=sign*body
        cx=x-offset*math.sin(a) if axis=='z' else x
        cy=y+offset*math.cos(a)
        cz=z+offset*math.sin(a) if axis=='x' else z
        result.append(box((cx-tip,cy-tip,cz-tip),(cx+tip,cy+tip,cz+tip),'reaction_chamber_crystal'))
    return result

# Each function has a distinct three-dimensional core, fitted to the shared socket.
# These exact dimensions and resting poses are also used by the live renderer.
idle={
    'grinding_unit':prism(5.2,8.1,8,2.4,7.6)+prism(10.8,8.1,8,2.4,7.6),
    'separation_unit':sum((prism(x,8,8,1.4,7.2) for x in (4.9,8,11.1)),[]),
    'mixing_basin':inclined_prism(4.9,7.4,8,2.2,5.8,'z',-45)
                   +inclined_prism(11.1,7.4,8,2.2,5.8,'z',45)
                   +inclined_prism(8,7.4,11.1,2.2,5.8,'x',-45),
    'field_coil':sum((prism(8+3.25*math.cos(i*math.pi/4),7.6,8+3.25*math.sin(i*math.pi/4),1.35,4.5)
                      for i in range(8)),[]),
    'reaction_chamber':prism(8,8.5,8,4.2,8.8)+crystal_halo(8,8.5,8),
}

DISPLAY={'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.625,.625,.625]},
         'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.25,.25,.25]},
         'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.5,.5,.5]},
         'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]},
         'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.4,.4,.4]}}
for name in NAMES:
    model = json.loads((ASSETS / f'models/block/{name}.json').read_text())
    write(ASSETS / f'models/block/{name}_suspended.json', model | {'elements': suspended(model['elements'])})
    variants={}
    for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]:
        for active in ('false','true'):
            for ceiling in ('false','true'):
                suffix='_suspended' if ceiling=='true' else ''
                variants[f'active={active},facing={facing},suspended={ceiling}']={'model':f'rematter:block/{name}{suffix}','y':rotation}
    write(ASSETS/f'blockstates/{name}.json',{'variants':variants})
    item = model | {'elements': model['elements'] + idle[name], 'display': DISPLAY}
    write(ASSETS/f'models/item/{name}.json',item)
    write(ASSETS/f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'rematter:item/{name}'}})
    write(RES/f'data/rematter/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'rematter:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

recipes={
'grinding_unit': (['DMD','RBR','VFV'], {'D':'rematter:diamond_lattice','M':'rematter:matrix_crystal','R':'rematter:rhevon_ingot','B':'rematter:structural_bars','V':'rematter:virelite_ceramic','F':'rematter:machine_frame'}),
'separation_unit': (['VMV','DOD','RFR'], {'V':'rematter:virelite_ceramic','M':'rematter:matrix_crystal','D':'rematter:diamond_lattice','O':'rematter:optical_circuit','R':'rematter:rhevon_ingot','F':'rematter:machine_frame'}),
'mixing_basin': (['M M','VMV','RFR'], {'M':'rematter:matrix_crystal','V':'rematter:virelite_ceramic','R':'rematter:rhevon_ingot','F':'rematter:machine_frame'}),
'field_coil': (['CLC','CMC','FQF'], {'C':'rematter:copper_dust','L':'rematter:lumivex_ingot','M':'rematter:matrix_crystal','Q':'rematter:control_circuit','F':'rematter:machine_frame'}),
'reaction_chamber': (['GDG','VMV','FOF'], {'G':'rematter:reactor_glass','D':'rematter:diamond_lattice','V':'rematter:virelite_ceramic','M':'rematter:matrix_cluster','F':'rematter:machine_frame','O':'rematter:optical_circuit'}),
}
for name,(pattern,key) in recipes.items():
    write(RES/f'data/rematter/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':key,'result':{'id':'rematter:'+name,'count':1}})
    write(RES/f'data/rematter/advancement/recipes/{name}.json',{'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['rematter:machine_frame']}]}},'has_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'rematter:'+name}}},'requirements':[['has_material','has_recipe']],'rewards':{'recipes':['rematter:'+name]}})
# The vanilla mineable tag references this private-to-the-mod grouping once.
write(RES/'data/rematter/tags/block/industrial_modules.json',{'replace':False,'values':['rematter:'+name for name in NAMES]})

def cluster_core(x, y):
    # Silith and quartz brighten the cut. Rhedon warms the broad lower face.
    blush = max(0, 1 - math.hypot((x / (TILE - 1) - .66) / .64, (y / (TILE - 1) - .75) / .62))
    ivory = (205, 198, 184)
    rhedon = (176, 98, 96)
    color = tuple(round(base + (accent - base) * .58 * blush) for base, accent in zip(ivory, rhedon))
    return crystal_pixel(color, x, y)


if not MODELS_ONLY:
    write_png(ASSETS/'textures/block/reaction_chamber_crystal.png', TILE, TILE,
              lambda x, y: crystal_pixel((83, 185, 181), x, y))
    write_png(ASSETS/'textures/block/matrix_cluster_core.png', TILE, TILE, cluster_core)
if ARGS.textures_only:
    print('Generated 128px crystal materials.')
elif MODELS_ONLY:
    print('Generated five module block/item models; recipes and textures preserved.')
else:
    print('Generated five original module models, complete idle items, recipes and discovery advancements.')
