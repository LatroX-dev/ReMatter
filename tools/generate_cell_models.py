#!/usr/bin/env python3
"""Generate the cell cartridges: Cell Housing, Matter Cells and Primal Cells as items.

A cell in the hand is the cartridge that the Matter Drive shows in a bay.
Its front carries the very faceplate sprite the Drive draws, at the mounted
size of 6 x 3 pixels, and its status light is the Drive's light on the same
spot, driven by the rematter:cell_status item property. Behind the faceplate
a narrow neck leaves the handle recess, then follows the graphite body with
its copper contacts. The Cell Housing is the same cartridge without tier bar
and light. A Primal Cell shows its charge through a slit in the body.

The cartridge is a member of the machine family: boxes on the half-pixel
grid, two texels per pixel, checked against slivers, intersections and
z-fighting. The front faces north; the display transforms turn it to the
player.
"""
from __future__ import annotations
import argparse
from pathlib import Path

from family_geometry import Box, GeometryError, build, check_boxes, check_composition, emitted, mapped_uv
from generated_files import GeneratedFiles
from hardware_sprites import (GRAPHITE_FACE, NATURAL, PRIMAL_ANIMATION, PRIMAL_FRAMES, Sheet, cartridge_side,
                              cartridge_top, contacts, face_size, housing_face, neck, plate_back, plate_edge)

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/rematter'
BLOCK = 'rematter:block/'
ITEM = 'rematter:item/'
# Item id -> faceplate sprite. The sprites of filled cells belong to the Matter Drive, which mounts them.
FACES = {
    'cell_housing': 'cell_housing_face',
    'matter_cell_64k': 'drive_cell_64k',
    'matter_cell_256k': 'drive_cell_256k',
    'matter_cell_1m': 'drive_cell_1m',
    'primal_cell_16k': 'drive_primal_cell_16k',
    'primal_cell_64k': 'drive_primal_cell_64k',
    'primal_cell_256k': 'drive_primal_cell_256k',
}
MODEL_NAMES = tuple(FACES)
# Status of the item property -> light sprite, the ones the Drive shows over a bay.
STATUS = {'empty': 'led_blue', 'stored': 'led_green', 'full': 'led_amber', 'locked': 'led_red'}
UNLIT = 'status_off'
BUDGET = 8
# The cartridge at its mounted size. The faceplate is the bay opening of the Drive: 6 x 3 pixels.
FACEPLATE = Box('faceplate', (5, 6.5, 4.5), (11, 9.5, 5.5))
NECK = Box('neck', (6, 7, 5.5), (10, 8.5, 7))
BODY = Box('body', (5, 6.5, 7), (11, 9.5, 11.5))
CONTACTS = Box('contacts', (6, 7.5, 11.5), (10, 8.5, 12), step=True)
# The light stands on the faceplate where the Drive puts it: 4.5 pixels from the right edge of the bay, 1 up.
LIGHT = Box('status_light', (9.5, 7.5, 4), (10.5, 8.5, 4.5), emissive=True, step=True)
# The faceplate looks at the player in a slot, in the first-person hand and out of an item frame; a carried
# cartridge points it ahead. Hands, ground and frames show the cartridge at about its mounted size. The game
# mirrors the right hand for the left one.
DISPLAY = {
    'gui': {'rotation': [25, 200, 0], 'translation': [0, 0, 0], 'scale': [1.9, 1.9, 1.9]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [.9, .9, .9]},
    'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1.6, 1.6, 1.6]},
    'thirdperson_righthand': {'rotation': [75, 0, 0], 'translation': [0, 2.5, 0], 'scale': [.8, .8, .8]},
    'firstperson_righthand': {'rotation': [0, 165, 0], 'translation': [0, 4, 0], 'scale': [.8, .8, .8]},
}
PAINTERS = {
    ('faceplate', 'south'): ('plate_back', plate_back),
    ('faceplate', 'rim'): ('plate_edge', plate_edge),
    ('neck', 'any'): ('neck', neck),
    ('body', 'north'): ('body_end', GRAPHITE_FACE),
    ('body', 'south'): ('body_end', GRAPHITE_FACE),
    ('body', 'east'): ('body_side', cartridge_side),
    ('body', 'west'): ('body_side', cartridge_side),
    ('contacts', 'any'): ('contacts', contacts),
}


def is_primal(name):
    return name.startswith('primal_cell_')


def has_light(name):
    return name != 'cell_housing'


class Cartridge:
    """Tiles and models of the Matter or the Primal cartridge body."""

    def __init__(self, primal):
        self.primal = primal
        self.sheet = Sheet('cell_cartridge_primal' if primal else 'cell_cartridge', 'body')
        # All tiles are asked for before the first model is laid out.
        build(self.boxes(True), self.skin)

    @staticmethod
    def boxes(light):
        return [FACEPLATE, NECK, BODY, CONTACTS] + ([LIGHT] if light else [])

    def skin(self, box, face):
        if box.name == 'faceplate' and face == 'north':
            return 'face', *mapped_uv(face, box.lo, box.hi, *NATURAL[face], (0, 0), 16)
        if box.name == 'status_light':
            # The light sprites are one flat colour; the centre texels stay clear of their atlas neighbours.
            return 'light', *mapped_uv(face, box.lo, box.hi, *NATURAL[face], (7, 7), 16)
        if box.name == 'body' and face in ('up', 'down'):
            material, painter = 'body_top', cartridge_top(self.primal and face == 'up')
            material += '_slit' if self.primal and face == 'up' else ''
        else:
            for role in (face, 'rim', 'any'):
                if (box.name, role) in PAINTERS:
                    material, painter = PAINTERS[box.name, role]
                    break
        key = (material, *face_size(box, face))
        self.sheet.want(key, *face_size(box, face), painter)
        return self.sheet.face(key, box, face)

    def model(self, name):
        boxes = self.boxes(has_light(name))
        if len(boxes) > BUDGET:
            raise GeometryError(f'{name}: {len(boxes)} boxes exceed the budget of {BUDGET}')
        check_boxes(name, boxes)
        elements = build(boxes, self.skin)
        check_composition(name, emitted(boxes, elements))
        textures = {'particle': BLOCK + self.sheet.page(0), 'face': BLOCK + FACES[name]}
        textures.update({key: BLOCK + page for key, page in self.sheet.textures().items()})
        if has_light(name):
            textures['light'] = BLOCK + UNLIT
        return {'gui_light': 'side', 'textures': textures, 'elements': elements, 'display': DISPLAY}

    def sprites(self):
        frames = PRIMAL_FRAMES if self.primal else 1
        return self.sheet.sprites(frames=frames), PRIMAL_ANIMATION if self.primal else None


def outputs():
    """Models and item definitions by path. Other generators check them through this function."""
    cartridges = {False: Cartridge(False), True: Cartridge(True)}
    for name in MODEL_NAMES:
        yield ASSETS / f'models/item/{name}.json', cartridges[is_primal(name)].model(name)
        if not has_light(name):
            yield ASSETS / f'items/{name}.json', {'model': {'type': 'minecraft:model', 'model': ITEM + name}}
            continue
        for status, sprite in STATUS.items():
            yield ASSETS / f'models/item/{name}_{status}.json', {
                'parent': ITEM + name, 'textures': {'light': BLOCK + sprite}}
        yield ASSETS / f'items/{name}.json', {'model': {
            'type': 'minecraft:select', 'property': 'rematter:cell_status',
            'cases': [{'when': status, 'model': {'type': 'minecraft:model', 'model': f'{ITEM}{name}_{status}'}}
                      for status in STATUS],
            'fallback': {'type': 'minecraft:model', 'model': ITEM + name}}}


def sprites():
    """Sprites this generator owns, by name, with the animation of those that have one."""
    result = {'cell_housing_face': (housing_face(), None)}
    for primal in (False, True):
        art, animation = Cartridge(primal).sprites()
        result.update({name: (sprite, animation) for name, sprite in art.items()})
    return result


def check_references(art):
    """Every sprite a model names is written here or by the family generator, which owns the Drive."""
    for path, value in outputs():
        for texture in value.get('textures', {}).values():
            name = texture.removeprefix(BLOCK)
            if name not in art and not (ASSETS / f'textures/block/{name}.png').is_file():
                raise GeometryError(f'{path.name}: sprite {texture} is neither generated here nor present')


def generate(check):
    art = sprites()
    check_references(art)
    out = GeneratedFiles(ROOT, check)
    for name, (sprite, animation) in art.items():
        out.png(ASSETS / f'textures/block/{name}.png', sprite.width, sprite.height, sprite.pixel)
        if animation:
            out.json(ASSETS / f'textures/block/{name}.png.mcmeta', animation)
    for path, value in outputs():
        out.json(path, value)
    return out.count


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='compare the files instead of writing them')
    arguments = parser.parse_args()
    print(('Verified' if arguments.check else 'Generated') + f' {generate(arguments.check)} cell files;'
          f' {len(MODEL_NAMES)} cartridges of at most {BUDGET} boxes passed the geometry checks.')
