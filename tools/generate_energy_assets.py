#!/usr/bin/env python3
"""Generate three energy cubes in the graphite machine family.

ENERGY_CUBE_LOOK selects the recessed energy core (recommended), a closed capacitor cabinet or a cartridge rack.
Every tier's independent recipe contains its matching Matter Cable and inherits that cable's case/port band;
an ivory mark also identifies the tier. Its Ember Matrix Crystal remains visible on the core and identity plate.
Charge0..8 swaps only a small part model;
empty inventory items show charge0. Side I/O behaviour belongs to the server energy cube implementation.
"""
from pathlib import Path
import argparse
from family_geometry import Box, mapped_uv
from family_palette import (BASE, CABLE_TIERS, COPPER, COPPER_DARK, COPPER_LIGHT, ENERGY_CABLE_TIERS, HIGHLIGHT, IVORY,
                            PORT_DARK, PORT_LIGHT, PORT_METAL, SEAM, SHADE, STEEL, STEEL_DEEP, STEEL_SHADE, TRIM, WELL, scale, lighten)
from family_sprites import Sprite, sealed_frame, plinth, bolt, port_plate
from generate_family_assets import Family, HORIZONTAL, ROTATION, MODEL, NATURAL
from hardware_sprites import OwnedFiles
from machine_palette import RGB
from recipe_discovery import shaped_discovery

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/rematter'
DATA = ROOT / 'src/main/resources/data/rematter'
ENERGY_CUBE_LOOKS = ('recessed_core', 'capacitor_cabinet', 'cartridge_rack')
ENERGY_CUBE_LOOK = 'recessed_core'
ENERGY_PORT_SIZES = {'compact': 6, 'trimmed': 7, 'balanced': 8}
ENERGY_PORT_STYLE = 'balanced'
TIERS = {'energy_cube': 1, 'reinforced_energy_cube': 2, 'resonant_energy_cube': 3}
CRYSTAL = 'ember'
RECIPES = {
    'energy_cube': (['RER', 'KFK', 'RVR'], {'R': 'rematter:rhevon_ingot', 'E': 'rematter:ember_matrix_crystal',
                    'K': 'rematter:matter_cable', 'F': 'rematter:machine_frame', 'V': 'rematter:virelite_ceramic'}),
    'reinforced_energy_cube': (['LEL', 'OFO', 'VKV'], {'L': 'rematter:lumivex_ingot', 'E': 'rematter:ember_matrix_crystal',
                    'O': 'rematter:optical_circuit', 'F': 'rematter:machine_frame', 'V': 'rematter:ender_conductor', 'K': 'rematter:reinforced_matter_cable'}),
    'resonant_energy_cube': (['NEN', 'DFD', 'KGK'], {'N': 'rematter:netherite_sheath', 'E': 'rematter:ember_matrix_crystal',
                    'D': 'rematter:diamond_lattice', 'F': 'rematter:machine_frame', 'K': 'rematter:resonant_matter_cable', 'G': 'rematter:matrix_cluster'}),
}


def side(tier, look, cable):
    image = sealed_frame(BASE)
    plinth(image, 26)
    stripe = CABLE_TIERS[cable]['stripe']
    image.row(2, 4, 28, stripe)
    image.row(3, 4, 28, scale(stripe, .6))
    # Recipe identity remains independent from the inherited cable-tier band, even at zero charge.
    image.rect(13, 28, 19, 30, RGB[CRYSTAL])
    for mark in range(tier):
        image.rect(21 + mark * 3, 28, 23 + mark * 3, 30, IVORY)
    for x in (2, 28): bolt(image, x, 5)
    return image


def socket(cable, width):
    image = Sprite(16, fill=PORT_DARK)
    inset = (16 - width * 2) // 2
    # A port face samples precisely its physical footprint at two texels per model pixel.
    end = 16 - inset
    if width == 8:
        port_plate(image, 0, 0)
    else:
        image.ring(inset, inset, end, end, SEAM)
        image.bevel(inset + 1, inset + 1, end - 1, end - 1, PORT_LIGHT, PORT_METAL)
        image.rect(inset + 2, inset + 2, end - 2, end - 2, WELL)
    stripe = CABLE_TIERS[cable]['stripe']
    image.row(inset + 2, inset + 2, end - 2, stripe)
    image.col(inset + 2, inset + 2, end - 2, stripe)
    image.rect(6, 6, 10, 10, COPPER)
    image.bevel(6, 6, 10, 10, COPPER_LIGHT, COPPER_DARK)
    return image


def core(charge, look):
    image = Sprite(fill=STEEL_DEEP)
    image.ring(4, 4, 28, 28, SEAM)
    image.rect(5, 5, 27, 27, STEEL_SHADE)
    image.rect(6, 6, 26, 26, WELL)
    height = round(20 * charge / 8)
    if height:
        hue = RGB[CRYSTAL]
        image.rect(6, 26 - height, 26, 26, hue)
        image.col(6, 26 - height, 26, lighten(hue, .4))
        image.col(25, 26 - height, 26, scale(hue, .64))
        image.row(26 - height, 6, 26, lighten(hue, .4))
        for y in range(8, 26, 2):
            if y >= 26 - height: image.row(y, 8, 24, scale(hue, .64))
    if look == 'capacitor_cabinet':
        for x in (8, 22): image.col(x, 6, 26, STEEL_DEEP)
    elif look == 'cartridge_rack':
        for y in (10, 16, 22): image.row(y, 6, 26, SEAM)
    return image


def rotated_box(name, lo, hi, turns):
    points = [tuple((lo, hi)[pick][axis] for axis, pick in enumerate((x, y, z)))
              for x in (0, 1) for y in (0, 1) for z in (0, 1)]
    for _ in range(turns): points = [(16-z, y, x) for x, y, z in points]
    return Box(name, tuple(min(p[i] for p in points) for i in range(3)),
               tuple(max(p[i] for p in points) for i in range(3)))


def geometry(look, port_style=ENERGY_PORT_STYLE):
    size = ENERGY_PORT_SIZES[port_style]
    low, high = 8 - size / 2, 8 + size / 2
    body = [Box('plinth', (0, 0, 0), (16, 2, 16)), Box('cap', (0, 14, 0), (16, 16, 16)),
            Box('core_foot', (7, 2, 7), (9, 3, 9)), Box('core_head', (7, 13, 7), (9, 14, 9))]
    body += [Box(f'post_{x}_{z}', (x, 2, z), (x + 2, 14, z + 2)) for x in (0, 14) for z in (0, 14)]
    for turns, direction in enumerate(HORIZONTAL):
        body.append(rotated_box('socket_' + direction, (low, low, 0), (high, high, 1), turns))
        body.append(rotated_box('rail_' + direction, (2, 7, 1), (14, 9, 2), turns))
    if look == 'recessed_core':
        parts = [Box('energy_core', (2.5, 3, 2.5), (13.5, 13, 13.5), flat=True)]
    elif look == 'capacitor_cabinet':
        parts = [Box('capacitor', (2, 3, 2), (14, 13, 14), flat=True)]
    else:
        parts = [Box(f'cartridge_{i}', (2.5, y, 2.5), (13.5, y + 2, 13.5), flat=True)
                 for i, y in enumerate((3, 6, 9))]
    return body, parts


def body_skin(box, face, port_style=ENERGY_PORT_STYLE):
    if box.name.startswith('socket_'):
        if face == box.name.removeprefix('socket_'):
            inset = (16 - ENERGY_PORT_SIZES[port_style] * 2) // 2
            uv, rotation = mapped_uv(face, box.lo, box.hi, *NATURAL[face], (inset, inset), 16)
            return 'socket', uv, rotation
        return 'inner'
    return 'side' if face in HORIZONTAL and box.on_boundary(face) else 'plain' if face == 'up' else 'inner'


def check_recipes():
    # These are independent crafts, so stored FE or charging-slot contents cannot disappear into a tier upgrade.
    for name, (_, keys) in RECIPES.items():
        if any(ingredient.removeprefix('rematter:') in TIERS for ingredient in keys.values()):
            raise ValueError(f'{name}: do not consume a previous energy cube')
        if 'rematter:' + ENERGY_CABLE_TIERS[name] not in keys.values():
            raise ValueError(f'{name}: its tier band requires its matching cable ingredient')
        if f'rematter:{CRYSTAL}_matrix_crystal' not in keys.values():
            raise ValueError(f'{name}: recipe hue differs from its sprite')
        for ingredient in keys.values():
            if ingredient.startswith('rematter:') and not (ART / 'items' / (ingredient.split(':')[1] + '.json')).is_file():
                raise ValueError(f'{name}: unregistered ingredient asset {ingredient}')


def assets(look=ENERGY_CUBE_LOOK, port_style=ENERGY_PORT_STYLE):
    if look not in ENERGY_CUBE_LOOKS:
        raise ValueError(f'Unknown energy cube look: {look}')
    family = Family()
    art = {f'energy_core_charge_{charge}': core(charge, look) for charge in range(9)}
    body, source_parts = geometry(look, port_style)
    for name, tier in TIERS.items():
        cable = ENERGY_CABLE_TIERS[name]
        art[name + '_side'] = side(tier, look, cable)
        art[name + '_socket'] = socket(cable, ENERGY_PORT_SIZES[port_style])
        textures = {'side': name + '_side', 'plain': 'casing_plain', 'socket': name + '_socket', 'inner': 'casing_inner'}
        drawn = family.model(name, 'network', textures, body, lambda box, face: body_skin(box, face, port_style),
                             context=body + source_parts, particle='casing_plain')
        state = [{'when': {'facing': facing}, 'apply': {'model': MODEL + name, **ROTATION[facing]}} for facing in HORIZONTAL]
        for charge in range(9):
            parts = [Box(part.name, part.lo, part.hi, emissive=charge > 0, step=part.step, flat=True) for part in source_parts]
            part_name = f'{name}_charge_{charge}'
            table = {'core': f'energy_core_charge_{charge}'}
            shown = family.model(part_name, 'network', table, parts,
                                  lambda box, face: 'core',
                                  context=body + parts, particle='casing_plain')
            family.compose(f'{name} charge {charge}', drawn + shown)
            state += [{'when': {'facing': facing, 'charge': str(charge)},
                       'apply': {'model': MODEL + part_name, **ROTATION[facing]}} for facing in HORIZONTAL]
        family.blockstates[name] = {'multipart': state}
        family.items[name] = {'model': {'type': 'minecraft:composite', 'models': [
            {'type': 'minecraft:model', 'model': MODEL + name},
            {'type': 'minecraft:model', 'model': MODEL + name + '_charge_0'}]}}
    return family, art


def generate(check=False):
    check_recipes()
    family, art = assets()
    out = OwnedFiles(ROOT, check)
    for name, sprite in art.items():
        out.png(ART / f'textures/block/{name}.png', sprite.width, sprite.height, sprite.pixel)
    for folder, files in (('models/block', family.models), ('blockstates', family.blockstates), ('items', family.items)):
        for name, value in files.items():
            out.json(ART / folder / f'{name}.json', value)
    for name, (pattern, keys) in RECIPES.items():
        out.json(DATA / f'recipe/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
                 'key': keys, 'result': {'id': 'rematter:' + name, 'count': 1}})
        out.json(DATA / f'advancement/recipes/{name}.json', shaped_discovery(name, pattern, keys))
        out.json(DATA / f'loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
                 'entries': [{'type': 'minecraft:item', 'name': 'rematter:' + name}],
                 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    out.sweep(ART / 'textures/block', ['energy_core_charge_*.png'] + [f'{name}{suffix}.png' for name in TIERS for suffix in ('_front', '_side', '_socket')])
    out.sweep(ART / 'models/block', [f'{name}_charge_*.json' for name in TIERS])
    print(f"{'Verified' if check else 'Generated'} {out.count} energy cube files; {len(family.compositions)} charge compositions checked ({ENERGY_CUBE_LOOK})")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    generate(parser.parse_args().check)
