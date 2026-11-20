#!/usr/bin/env python3
"""Generate model references for independently authored component sprites.

Material illustrations are source assets; technical components have separate
vector drawing tools. Keeping both separate prevents a model refresh from replacing artwork. Machine
BlockItems retain their own models; Cells share full 3D cartridge geometry with inserted Drive bays.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import struct
from generate_cell_models import MODEL_NAMES, outputs as cell_outputs

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/rematter'
ITEM_NAMES = (
    'silith', 'argil', 'rhedon', 'virel', 'lumin',
    'matrix_blend', 'matrix_crystal', 'matrix_cluster',
    'machine_frame', 'control_circuit', 'optical_circuit',
    'speed_upgrade', 'efficiency_upgrade', 'automation_pattern',
    'cell_rack',
    'recovered_matter', 'guide',
)


def outputs():
    for name in ITEM_NAMES:
        yield ASSETS / f'models/item/{name}.json', {
            'parent': 'minecraft:item/generated',
            'textures': {'layer0': f'rematter:item/{name}'},
        }
        yield ASSETS / f'items/{name}.json', {
            'model': {'type': 'minecraft:model', 'model': f'rematter:item/{name}'},
        }
    yield from cell_outputs()


def check_sprites():
    for name in ITEM_NAMES:
        path = ASSETS / f'textures/item/{name}.png'
        data = path.read_bytes()
        if (data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR'
                or struct.unpack('>IIBB', data[16:26]) != (128, 128, 8, 6)):
            raise ValueError(f'Expected an authored 128px RGBA sprite: {name}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for path, data in outputs():
        content = json.dumps(data, indent=2) + '\n'
        if args.check:
            if not path.is_file() or path.read_text() != content:
                raise ValueError(f'Generated asset differs: {path.relative_to(ROOT)}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
    if args.check:
        check_sprites()
    print(f'{"Verified" if args.check else "Generated"} models for {len(ITEM_NAMES) + len(MODEL_NAMES)} component items.')


if __name__ == '__main__':
    main()
