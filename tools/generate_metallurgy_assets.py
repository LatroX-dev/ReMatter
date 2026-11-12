#!/usr/bin/env python3
"""Model/definition references for the nine independently authored metallurgy sprites.

The Sinter Furnace body and states are owned by generate_family_assets.py.
Run with --check to verify deterministic outputs without rewriting them.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path

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


def outputs():
    # Standard generated-item transforms keep the illustrated face readable in
    # inventories, in the hand and when laid on a machine's work surface.
    for name in ITEM_NAMES:
        yield ASSETS/f'models/item/{name}.json',{
            'parent':'minecraft:item/generated',
            'textures':{'layer0':f'rematter:item/{name}'},
        }
        yield ASSETS/f'items/{name}.json',{'model':{'type':'minecraft:model','model':f'rematter:item/{name}'}}
    # Sinter Furnace housing/state/item models belong exclusively to generate_family_assets.py.


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
