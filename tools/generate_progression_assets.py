#!/usr/bin/env python3
"""Generate three functional progression fronts with the existing half-pixel family geometry."""
from pathlib import Path
import argparse
from family_geometry import Box
from family_palette import BASE, SHADE, SEAM, WELL, HIGHLIGHT, STEEL, STEEL_SHADE, TRIM, IVORY, COPPER, lighten, scale
from family_sprites import Sprite, sealed_frame, plinth, bolt
from generate_family_assets import Family, sealed_body, sealed_skin, with_parts
from generated_files import GeneratedFiles
from machine_palette import RGB
from hardware_sprites import PRIMAL_BODY, PRIMAL_CORE, PRIMAL_DEEP, PRIMAL_LIGHT, PRIMAL_FRAMES, PRIMAL_ANIMATION, primal_run
from recipe_discovery import shaped_discovery

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/rematter'
DATA = ROOT / 'src/main/resources/data'
DEVICES = ('primal_converter', 'crystal_generator', 'industrial_crystal_generator')
VIOLET = RGB['violet']
EMBER = RGB['ember']


def front(name):
    image = sealed_frame(BASE)
    plinth(image, 28)
    image.row(3, 5, 14, IVORY)
    if name == 'primal_converter':
        # One deliberately separate payment cartridge, a target arrow and a single output socket.
        image.rect(4, 6, 18, 25, SEAM)
        image.bevel(4, 6, 18, 25, STEEL_SHADE, SHADE)
        image.rect(6, 8, 16, 23, WELL)
        image.rect(20, 16, 28, 24, SEAM)
        image.bevel(20, 16, 28, 24, STEEL_SHADE, SHADE)
        image.rect(22, 18, 26, 22, COPPER)
        image.rect(20, 9, 25, 11, VIOLET)
        image.px(25, 8, VIOLET); image.px(26, 9, VIOLET); image.px(25, 10, VIOLET)
    else:
        # The chamber is an actual recessed part; extra burners buy power, not extra crystal yield.
        image.rect(4, 6, 28, 23, SEAM)
        image.bevel(4, 6, 28, 23, STEEL_SHADE, SHADE)
        for x in (5, 25):
            for y in (24, 26):
                image.rect(x, y, x + 2, y + 1, STEEL_SHADE)
        if name.startswith('industrial'):
            for x in (6, 12, 18, 24):
                image.rect(x, 25, x + 2, 27, COPPER)
        else:
            image.rect(13, 25, 19, 27, COPPER)
    for x in (3, 27):
        bolt(image, x, 3)
    return image


def display_frame(name, active, frame=0):
    image = Sprite(fill=WELL)
    accent = PRIMAL_BODY if name == 'primal_converter' else EMBER
    ink = accent if active else scale(accent, .45)
    bright = lighten(accent, .6) if active else scale(accent, .65)
    if name == 'primal_converter':
        # The Primal rail is separate from the static Violet recipe crystal on the front.
        image.rect(7, 8, 15, 23, STEEL_SHADE)
        image.bevel(7, 8, 15, 23, STEEL, SHADE)
        image.rect(9, 10, 13, 21, PRIMAL_BODY)
        image.col(9, 10, 21, PRIMAL_LIGHT)
        image.col(12, 10, 21, PRIMAL_DEEP)
        for y in range(10, 21):
            image.px(10, y, primal_run(y - 10, frame))
        image.px(10, 10 + frame % 11, PRIMAL_CORE)
        image.row(22, 8, 14, TRIM)
    else:
        centers = (10, 21) if name.startswith('industrial') else (16,)
        for center in centers:
            for y in range(8, 21):
                half = max(0, 4 - abs(y - 14) // 2)
                image.rect(center - half, y, center + half + 1, y + 1, ink)
                if half > 0:
                    image.px(center - 1, y, bright)
            image.rect(center - 4, 21, center + 5, 23, STEEL_SHADE)
            image.row(21, center - 4, center + 5, STEEL)
        image.row(24, 6, 26, ink)
    return image


def display(name, active):
    if name != 'primal_converter':
        return display_frame(name, active)
    strip = Sprite(32, 32 * PRIMAL_FRAMES)
    for frame in range(PRIMAL_FRAMES):
        image = display_frame(name, active, frame)
        for y in range(32):
            for x in range(32):
                strip.px(x, y + frame * 32, image.pixel(x, y))
    return strip


def generate(check=False):
    family = Family(); art = {}
    for name in DEVICES:
        art[name + '_front'] = front(name)
        for active in (False, True):
            art[name + '_window' + ('_active' if active else '')] = display(name, active)
        body = sealed_body(2) + [Box('panel', (1, 2, 1), (15, 15, 2))]
        window = [Box('window', (8, 4.5, .5), (13, 12, 1), step=True)] if name == 'primal_converter' else [Box('chamber', (2.5, 4.5, .5), (13.5, 12.5, 1), step=True)]
        casing = 'reactor_casing' if name.startswith('industrial') else 'casing_plain'
        sides = {'south': 'port', 'east': 'plain', 'west': 'plain', 'down': 'plain', 'up': 'plain'}
        with_parts(family, name, 'network', {'port': 'casing_port', 'plain': casing, 'front': name + '_front', 'inner': 'casing_inner'},
                   body, sealed_skin(sides), window,
                   {'idle': {'window': name + '_window', 'inner': 'casing_inner'},
                    'active': {'window': name + '_window_active', 'inner': 'casing_inner'}},
                   lambda box, face: 'window' if face == 'north' else 'inner', casing)
    out = GeneratedFiles(ROOT, check)
    for name, sprite in art.items():
        out.png(ART / f'textures/block/{name}.png', sprite.width, sprite.height, sprite.pixel)
        if name.startswith('primal_converter_window'):
            out.json(ART / f'textures/block/{name}.png.mcmeta', PRIMAL_ANIMATION)
    for folder, files in (('models/block', family.models), ('blockstates', family.blockstates), ('items', family.items)):
        for name, value in files.items():
            out.json(ART / folder / f'{name}.json', value)
    recipes = {
        'primal_converter': (['VOV', 'EFE', 'DND'], {'V': 'rematter:violet_matrix_crystal', 'O': 'rematter:optical_circuit', 'E': 'rematter:ender_conductor',
                             'F': 'rematter:machine_frame', 'D': 'rematter:diamond_lattice', 'N': 'rematter:netherite_sheath'}),
        'crystal_generator': ([' E ', 'RFR', 'BCB'], {'E': 'rematter:ember_matrix_crystal', 'R': 'rematter:rhevon_ingot', 'F': 'rematter:machine_frame',
                              'B': 'rematter:structural_bars', 'C': 'rematter:control_circuit'}),
        'industrial_crystal_generator': (['ENE', 'DFD', 'EGE'], {'E': 'rematter:ember_matrix_crystal', 'N': 'rematter:netherite_sheath',
                                         'D': 'rematter:diamond_lattice', 'F': 'rematter:machine_frame', 'G': 'rematter:matrix_cluster'})
    }
    for name, (pattern, keys) in recipes.items():
        out.json(DATA / f'rematter/recipe/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': keys,
                                                     'result': {'id': 'rematter:' + name, 'count': 1}})
        out.json(DATA / f'rematter/advancement/recipes/{name}.json', shaped_discovery(name, pattern, keys))
    # The audited own-recipe floor counts Sand alone, gives Clay and smelting fuel zero cost,
    # and allows the full25% FE-policy discount:16000*.75/4=3000FE/crystal;1024<50%.
    # World harvest/trade/farming supplies external matter. Pack sources require their separate audit.
    for name in ('matrix_crystal', 'ice_matrix_crystal', 'copper_matrix_crystal', 'leaf_matrix_crystal', 'blue_matrix_crystal',
                 'violet_matrix_crystal', 'amber_matrix_crystal', 'ember_matrix_crystal', 'mint_matrix_crystal'):
        fuel = {'schema_version': 1, 'item': 'rematter:' + name, 'count': 1, 'energy': 1024, 'enabled': True}
        out.json(DATA / f'rematter/rematter/crystal_fuel/{name}.json', fuel)
        out.json(ROOT / f'integrations/reforging/datapack/data/rematter/rematter/crystal_fuel/{name}.json', dict(fuel, enabled=False))
    print(f"{'Checked' if check else 'Generated'} {out.count} progression files; {len(family.compositions)} active/idle compositions passed geometry checks")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    generate(parser.parse_args().check)
