#!/usr/bin/env python3
"""Metallurgy sprite model references and the compact sintering furnace.

The furnace housing is the Blockbench export in models/block.
Item textures are independently authored.
Run with --check to verify deterministic outputs without rewriting them.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
from connection_geometry import machine_state_assets
from machine_housings import machine_housing

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/rematter'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
ITEM_NAMES = (
    'iron_dust', 'copper_dust', 'gold_dust',
    'rhevon_ingot', 'rhevon_dust',
    'lumivex_ingot', 'lumivex_dust',
    'virelite_blend', 'virelite_ceramic',
)


def box(a, b, texture, rotation=None, faces=None):
    values = {'from': list(a), 'to': list(b), 'faces': {
        face: {'texture': '#' + (faces or {}).get(face, texture)} for face in FACES}}
    if rotation:
        values['rotation'] = rotation
    # The gold texture's centre is a calm metal surface, without the block border.
    for value in values['faces'].values():
        if value['texture'] == '#gold': value['uv'] = [4, 4, 12, 12]
    return values


def idle_prisms():
    # The inventory model includes the same fixed-proportion dormant focusing
    # prisms drawn by the block entity renderer, not a fictitious loaded product.
    e=[]
    for x in (4.4,11.6):
        e.extend([box((x-.585,7,10.115),(x+.585,9.4,11.285),'copper'),
                  box((x-.234,6.2,10.466),(x+.234,10.2,10.934),'copper'),
                  box((x-.4212,6.68,10.2788),(x+.4212,9.72,11.1212),'copper',
                      rotation={'origin':[x,8.2,10.7],'axis':'y','angle':45,'rescale':False})])
    return e


def outputs():
    # Standard generated-item transforms keep the illustrated face readable in
    # inventories, in the hand and when laid on a machine's work surface.
    for name in ITEM_NAMES:
        yield ASSETS/f'models/item/{name}.json',{
            'parent':'minecraft:item/generated',
            'textures':{'layer0':f'rematter:item/{name}'},
        }
        yield ASSETS/f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'rematter:item/{name}'}}
    furnace = machine_housing('sinter_furnace')
    yield ASSETS / 'models/block/sinter_furnace.json', furnace
    yield ASSETS / 'models/block/sinter_furnace_active.json', {
        'parent': 'rematter:block/sinter_furnace',
        'textures': {'status': 'rematter:block/machine_ember'},
    }
    yield ASSETS / 'models/item/sinter_furnace.json', furnace | {'elements': furnace['elements'] + idle_prisms()}
    yield ASSETS/'items/sinter_furnace.json',{'model':{'type':'minecraft:model','model':'rematter:item/sinter_furnace'}}
    energy_trim = box((6.4,11.5,14.6),(9.6,13.7,14.66),'graphite',faces={'south':'energy'})
    for path, asset in machine_state_assets('sinter_furnace', 'machine_ember', rear_trim=[energy_trim]):
        yield ASSETS / path, asset


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    count=0
    for path,data in outputs():
        content=json.dumps(data,indent=2)+'\n'
        if args.check:
            if not path.is_file() or path.read_text()!=content:
                raise ValueError(f'Generated asset differs: {path.relative_to(ROOT)}')
        else:
            path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
        count+=1
    print(f'{"Verified" if args.check else "Generated"} {count} metallurgy assets.')


if __name__=='__main__':main()
