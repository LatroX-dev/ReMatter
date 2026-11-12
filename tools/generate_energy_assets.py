#!/usr/bin/env python3
"""Generate three energy cubes in the graphite machine family.

ENERGY_CUBE_LOOK selects the recessed energy core (recommended), a closed capacitor cabinet or a cartridge rack.
The storage tier is a neutral ivory mark, not a different colour. Every tier's independent recipe contains an
Ember Matrix Crystal, whose exact hue appears on its casing and core. Charge0..8 swaps only a small part model;
empty inventory items show charge0. Side I/O behaviour belongs to the server energy cube implementation.
"""
from pathlib import Path
import argparse
from family_geometry import Box
from family_palette import BASE, HIGHLIGHT, IVORY, SEAM, SHADE, STEEL, STEEL_DEEP, STEEL_SHADE, TRIM, WELL, scale, lighten
from family_sprites import Sprite, sealed_frame, plinth, bolt
from generate_family_assets import Family, HORIZONTAL, ROTATION, MODEL, sealed_body, sealed_skin
from hardware_sprites import OwnedFiles
from machine_palette import RGB
from recipe_discovery import shaped_discovery

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/rematter'
DATA = ROOT / 'src/main/resources/data/rematter'
ENERGY_CUBE_LOOKS = ('recessed_core', 'capacitor_cabinet', 'cartridge_rack')
ENERGY_CUBE_LOOK = 'recessed_core'
TIERS = {'energy_cube': 1, 'reinforced_energy_cube': 2, 'resonant_energy_cube': 3}
CRYSTAL = 'ember'
RECIPES = {
    'energy_cube': (['RER', 'CFC', 'RVR'], {'R': 'rematter:rhevon_ingot', 'E': 'rematter:ember_matrix_crystal',
                    'C': 'minecraft:copper_ingot', 'F': 'rematter:machine_frame', 'V': 'rematter:virelite_ceramic'}),
    'reinforced_energy_cube': (['LEL', 'OFO', 'VEV'], {'L': 'rematter:lumivex_ingot', 'E': 'rematter:ember_matrix_crystal',
                    'O': 'rematter:optical_circuit', 'F': 'rematter:machine_frame', 'V': 'rematter:ender_conductor'}),
    'resonant_energy_cube': (['NEN', 'DFD', 'OGO'], {'N': 'rematter:netherite_sheath', 'E': 'rematter:ember_matrix_crystal',
                    'D': 'rematter:diamond_lattice', 'F': 'rematter:machine_frame', 'O': 'rematter:optical_circuit', 'G': 'rematter:matrix_cluster'}),
}


def front(tier, look):
    image = sealed_frame(BASE)
    plinth(image, 26)
    # Exact recipe hue on the top cap remains visible even when the empty core is dark.
    image.row(2, 5, 27, RGB[CRYSTAL])
    image.row(3, 5, 27, scale(RGB[CRYSTAL], .6))
    for mark in range(tier):
        image.rect(20 + mark * 3, 28, 22 + mark * 3, 30, IVORY)
    for x in (2, 28):
        bolt(image, x, 5)
    if look == 'capacitor_cabinet':
        image.rect(4, 6, 28, 25, SEAM)
        image.bevel(4, 6, 28, 25, STEEL_SHADE, SHADE)
    elif look == 'cartridge_rack':
        for y in (7, 13, 19):
            image.rect(4, y, 28, y + 5, SEAM)
            image.row(y, 4, 28, STEEL_SHADE)
    return image


def core(charge):
    image = Sprite(fill=STEEL_DEEP)
    image.ring(6, 6, 26, 26, SEAM)
    image.rect(7, 7, 25, 25, STEEL_SHADE)
    # The recessed cube maps texels8..24; keep a neutral frame inside that region so an empty core remains
    # a visible piece of hardware instead of looking like a hole. The cabinet alternatives show its outer rim.
    image.ring(8, 8, 24, 24, STEEL_SHADE)
    image.rect(9, 9, 23, 23, WELL)
    height = round(14 * charge / 8)
    if height:
        hue = RGB[CRYSTAL]
        image.rect(9, 23 - height, 23, 23, hue)
        image.col(9, 23 - height, 23, lighten(hue, .4))
        image.col(22, 23 - height, 23, scale(hue, .64))
        image.row(23 - height, 9, 23, lighten(hue, .4))
        # Eight physical bands show the coarse synchronized amount, independent of the ivory tier marks.
        for y in range(11, 23, 2):
            if y >= 23 - height:
                image.row(y, 11, 21, scale(hue, .64))
    return image


def geometry(look):
    if look == 'recessed_core':
        # An open graphite frame around the energy core. Full top/bottom plates and corner posts preserve a
        # cube silhouette; the rear panel seats the power connection. No Mekanism geometry or art is reused.
        body = [Box('plinth', (0, 0, 0), (16, 3, 16)), Box('cap', (0, 14, 0), (16, 16, 16)),
                Box('rear_panel', (2, 3, 14), (14, 14, 16)),
                Box('core_foot', (7, 3, 7), (9, 4, 9)), Box('core_head', (7, 12, 7), (9, 14, 9))]
        body += [Box(f'post_{x}_{z}', (x, 3, z), (x + 2, 14, z + 2)) for x in (0, 14) for z in (0, 14)]
        return body, [Box('energy_core', (4, 4, 4), (12, 12, 12), flat=True)]
    body = sealed_body(2) + [Box('panel', (1, 2, 1), (15, 15, 2))]
    if look == 'capacitor_cabinet':
        return body, [Box('meter', (3, 4, .5), (13, 12, 1), step=True, flat=True)]
    return body, [Box(f'cartridge_{index}', (3, y, .5), (13, y + 2, 1), step=True, flat=True)
                  for index, y in enumerate((4, 7, 10))]


def check_recipes():
    # These are independent crafts, so stored FE or charging-slot contents cannot disappear into a tier upgrade.
    for name, (_, keys) in RECIPES.items():
        if any(ingredient.removeprefix('rematter:') in TIERS for ingredient in keys.values()):
            raise ValueError(f'{name}: do not consume a previous energy cube')
        if f'rematter:{CRYSTAL}_matrix_crystal' not in keys.values():
            raise ValueError(f'{name}: recipe hue differs from its sprite')
        for ingredient in keys.values():
            if ingredient.startswith('rematter:') and not (ART / 'items' / (ingredient.split(':')[1] + '.json')).is_file():
                raise ValueError(f'{name}: unregistered ingredient asset {ingredient}')


def assets(look=ENERGY_CUBE_LOOK):
    if look not in ENERGY_CUBE_LOOKS:
        raise ValueError(f'Unknown energy cube look: {look}')
    family = Family()
    art = {f'energy_core_charge_{charge}': core(charge) for charge in range(9)}
    body, source_parts = geometry(look)
    for name, tier in TIERS.items():
        art[name + '_front'] = front(tier, look)
        textures = {'front': name + '_front', 'plain': 'casing_plain', 'port': 'casing_port', 'inner': 'casing_inner'}
        skin = sealed_skin({'south': 'port', 'down': 'port', 'east': 'plain', 'west': 'plain', 'up': 'plain'})
        drawn = family.model(name, 'network', textures, body, skin, context=body + source_parts, particle='casing_plain')
        state = [{'when': {'facing': facing}, 'apply': {'model': MODEL + name, **ROTATION[facing]}} for facing in HORIZONTAL]
        for charge in range(9):
            parts = [Box(part.name, part.lo, part.hi, emissive=charge > 0, step=part.step, flat=True) for part in source_parts]
            part_name = f'{name}_charge_{charge}'
            table = {'core': f'energy_core_charge_{charge}'}
            if look != 'recessed_core':
                table['inner'] = 'casing_inner'
            shown = family.model(part_name, 'network', table, parts,
                                  lambda box, face: 'core' if look == 'recessed_core' or face == 'north' else 'inner',
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
    out.sweep(ART / 'textures/block', ['energy_core_charge_*.png'] + [f'{name}_front.png' for name in TIERS])
    out.sweep(ART / 'models/block', [f'{name}_charge_*.json' for name in TIERS])
    print(f"{'Verified' if check else 'Generated'} {out.count} energy cube files; {len(family.compositions)} charge compositions checked ({ENERGY_CUBE_LOOK})")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    generate(parser.parse_args().check)
