#!/usr/bin/env python3
"""Generate the cell cartridges: Cell Housing, Matter Cells and Primal Cells as items.

A cell in the hand is the cartridge that the Matter Drive shows in a bay.
Its front carries the very faceplate sprite the Drive draws, at the mounted
size of 6 x 3 pixels, and its status light is the Drive's light on the same
spot, driven by the rematter:cell_status item property. Behind the faceplate
a narrow neck leaves the handle recess, then follows the graphite body with
its copper contacts. The Cell Housing is the same cartridge without tier bar
and light.

A window in the top of the body shows the storage crystal the cell was
crafted around, read from its recipe: the socket of a Cell Housing is empty,
a Matter Cell holds the clear Matrix Crystal, a cell with a coloured Matrix
Crystal in its recipe shows exactly that hue, and a cell made of Primal
Crystals shows the Primal signature. The guide rail of every Primal Cell is
a Primal run.

The cartridge is a member of the machine family: boxes on the half-pixel
grid, two texels per pixel, checked against slivers, intersections and
z-fighting. The front faces north; the display transforms turn it to the
player.
"""
from __future__ import annotations
import argparse
import json
import re
from pathlib import Path

from family_geometry import Box, GeometryError, build, check_boxes, check_composition, emitted, mapped_uv
from hardware_sprites import (GRAPHITE_FACE, NATURAL, PRIMAL_ANIMATION, PRIMAL_FRAMES, OwnedFiles, Sheet,
                              cartridge_bottom,
                              cartridge_side, cartridge_top, contacts, face_size, housing_face, neck, plate_back,
                              plate_edge)
from machine_palette import RGB
from family_palette import PRIMAL_BODY, LED

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/rematter'
RECIPES = ROOT / 'src/main/resources/data/rematter/recipe'
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
# cartridge points it ahead. A slot shows the faceplate and, above it, the window over the storage crystal; the
# icon is 15 of the 16 slot pixels wide. Hands, ground and frames show the cartridge at about its mounted size.
# The game mirrors the right hand for the left one.
DISPLAY = {
    'gui': {'rotation': [30, 205, 0], 'translation': [0, 0, 0], 'scale': [1.8, 1.8, 1.8]},
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
    ('body', 'down'): ('body_bottom', cartridge_bottom),
    ('contacts', 'any'): ('contacts', contacts),
}
# Recipe ingredients that are a storage crystal without one of the eight hues.
CLEAR_CRYSTALS = {'rematter:matrix_crystal': 'matrix', 'rematter:matrix_cluster': 'matrix',
                  'rematter:primal_crystal': 'primal'}


def is_primal(name):
    return name.startswith('primal_cell_')


def has_light(name):
    return name != 'cell_housing'


def core(name):
    """The storage crystal of a cell, read from its crafting recipe: a hue name, 'matrix', 'primal' or None.

    A coloured Matrix Crystal in the recipe is what the window shows, in exactly its hue. The window holds one
    crystal; a recipe with two different coloured crystals needs a second window before it can be drawn.
    """
    key = json.loads((RECIPES / f'{name}.json').read_text())['key']
    items = set(key.values())
    hues = {found.group(1) for found in (re.fullmatch(r'rematter:([a-z]+)_matrix_crystal', item) for item in items)
            if found and found.group(1) in RGB}
    if len(hues) > 1:
        raise GeometryError(f'{name}: its recipe holds the crystals {sorted(hues)}; the cartridge shows one')
    if hues:
        return hues.pop()
    clear = {CLEAR_CRYSTALS[item] for item in items if item in CLEAR_CRYSTALS}
    if len(clear) > 1:
        raise GeometryError(f'{name}: its recipe holds both a Matrix and a Primal crystal')
    return clear.pop() if clear else None


class Cartridge:
    """Tiles and models of the cartridges that share one sprite: the same body and the same storage crystal."""

    def __init__(self, primal, crystal):
        self.primal = primal
        self.crystal = crystal
        # Matter Cells and the Cell Housing share a sprite; a coloured crystal gets its own, so no cell names a
        # sprite that holds a hue from another recipe.
        name = 'cell_cartridge' + ('_primal' if primal else '')
        self.sheet = Sheet(name if crystal in (None, 'matrix', 'primal') else f'{name}_{crystal}', 'body')
        self.cores = set()

    @staticmethod
    def boxes(light):
        return [FACEPLATE, NECK, BODY, CONTACTS] + ([LIGHT] if light else [])

    def skin(self, shown):
        def skin(box, face):
            if box.name == 'faceplate' and face == 'north':
                return 'face', *mapped_uv(face, box.lo, box.hi, *NATURAL[face], (0, 0), 16)
            if box.name == 'status_light':
                # The light sprites are one flat colour; the centre texels stay clear of their atlas neighbours.
                return 'light', *mapped_uv(face, box.lo, box.hi, *NATURAL[face], (7, 7), 16)
            if box.name == 'body' and face == 'up':
                material, painter = f'body_top_{shown or "empty"}', cartridge_top(shown)
            elif box.name == 'body' and face in ('east', 'west'):
                material, painter = 'body_side', cartridge_side(self.primal)
            else:
                for role in (face, 'rim', 'any'):
                    if (box.name, role) in PAINTERS:
                        material, painter = PAINTERS[box.name, role]
                        break
            key = (material, *face_size(box, face))
            self.sheet.want(key, *face_size(box, face), painter)
            return self.sheet.face(key, box, face)
        return skin

    def reserve(self, name):
        """Ask for the tiles of a cell. All cells of a sprite do so before the first model is laid out."""
        self.cores.add(core(name))
        build(self.boxes(has_light(name)), self.skin(core(name)))

    def model(self, name):
        boxes = self.boxes(has_light(name))
        if len(boxes) > BUDGET:
            raise GeometryError(f'{name}: {len(boxes)} boxes exceed the budget of {BUDGET}')
        check_boxes(name, boxes)
        elements = build(boxes, self.skin(core(name)))
        check_composition(name, emitted(boxes, elements))
        textures = {'particle': BLOCK + self.sheet.page(0), 'face': BLOCK + FACES[name]}
        textures.update({key: BLOCK + page for key, page in self.sheet.textures().items()})
        if has_light(name):
            textures['light'] = BLOCK + UNLIT
        return {'gui_light': 'side', 'textures': textures, 'elements': elements, 'display': DISPLAY}

    def sprites(self):
        frames = PRIMAL_FRAMES if self.primal else 1
        return self.sheet.sprites(frames=frames), PRIMAL_ANIMATION if self.primal else None


def cartridges():
    """The cartridge of every cell, with all tiles asked for."""
    shared, by_name = {}, {}
    for name in MODEL_NAMES:
        crystal = core(name)
        # The empty socket of the housing lies on the sprite of the Matter Cells.
        kind = (is_primal(name), 'matrix' if crystal is None else crystal)
        by_name[name] = shared.setdefault(kind, Cartridge(*kind))
        by_name[name].reserve(name)
    return by_name


def outputs():
    """Models and item definitions by path. Other generators check them through this function."""
    cartridge = cartridges()
    for name in MODEL_NAMES:
        yield ASSETS / f'models/item/{name}.json', cartridge[name].model(name)
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
    for cartridge in cartridges().values():
        art, animation = cartridge.sprites()
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
    out = OwnedFiles(ROOT, check)
    # Item bar colours are used on both sides, so content classes must not import the client palette.
    body = int.from_bytes(bytes(PRIMAL_BODY), 'big')
    fault = int.from_bytes(bytes(LED['red']), 'big')
    java = f"""package dev.latrox.rematter.primal;

/** Shared palette for Primal Cell item bars. Generated by tools/generate_cell_models.py. */
public final class PrimalVisualColors {{
    public static final int BODY = 0x{body:06x};
    public static final int FAULT = 0x{fault:06x};

    private PrimalVisualColors() {{}}
}}
"""
    out.bytes(ROOT / 'src/main/java/dev/latrox/rematter/primal/PrimalVisualColors.java', java.encode())
    for name, (sprite, animation) in art.items():
        out.png(ASSETS / f'textures/block/{name}.png', sprite.width, sprite.height, sprite.pixel)
        if animation:
            out.json(ASSETS / f'textures/block/{name}.png.mcmeta', animation)
    for path, value in outputs():
        out.json(path, value)
    out.sweep(ASSETS / 'textures/block', ['cell_cartridge*.png', 'cell_cartridge*.png.mcmeta', 'cell_housing_face.png*'])
    out.sweep(ASSETS / 'models/item', [f'{name}{rest}.json' for name in MODEL_NAMES for rest in ('', '_*')])
    return out.count


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='compare the files instead of writing them')
    arguments = parser.parse_args()
    print(('Verified' if arguments.check else 'Generated') + f' {generate(arguments.check)} cell files;'
          f' {len(MODEL_NAMES)} cartridges of at most {BUDGET} boxes passed the geometry checks.')
