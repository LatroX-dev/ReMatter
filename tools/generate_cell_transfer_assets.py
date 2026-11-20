#!/usr/bin/env python3
"""Generate the unpowered two-bay benchtop Cell Transfer Station and its independent crafting recipe.

CONSOLE_LOOK selects a stepped benchtop dock (recommended), a recessed bridge dock or a split cradle dock.
All three keep the same eight family-grid housing boxes and the same two honest empty bays: Source on the viewer's
left (slot 0) and Target on the viewer's right (slot 1), separated by a central transfer bridge. The Mint recipe
crystal sets the identity; there is no energy/network connector or active state, and no stored cell is painted into
the item model.
"""
from pathlib import Path
import argparse
from family_geometry import Box, check_boxes
from family_palette import (BASE, COPPER, COPPER_LIGHT, HIGHLIGHT, IVORY, PORT_DARK, PORT_LIGHT, PORT_METAL,
                            SEAM, SHADE, STEEL, STEEL_SHADE, TRIM, WELL)
from family_sprites import Sprite, bolt
from generate_family_assets import Family, HORIZONTAL, ROTATION, MODEL
from generated_files import GeneratedFiles
from machine_palette import RGB
from recipe_discovery import shaped_discovery

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/rematter'
DATA = ROOT / 'src/main/resources/data/rematter'
NAME = 'cell_transfer_station'
CONSOLE_LOOKS = ('benchtop_dock', 'recessed_bridge', 'split_cradle')
CONSOLE_LOOK = 'benchtop_dock'
PATTERN = ['SMS', 'CFC', 'SBS']
KEYS = {
    'S': 'rematter:structural_bars',
    'M': 'rematter:mint_matrix_crystal',
    'C': 'rematter:control_circuit',
    'F': 'rematter:machine_frame',
    'B': 'rematter:cell_housing',
}

# Viewer-left (north face: +x -> u=2..14) is Bay 0 (Source); viewer-right (-x -> u=18..30) is Bay 1 (Target).
STATION_BAYS = ((2, 20), (18, 20))


def station_cell_box(bay):
    x0 = 9 if bay == 0 else 1
    return Box(f'station_cell_{bay}', (x0, 3, 0.5), (x0 + 6, 6, 2))


def station_led_box(bay):
    x0 = (9 if bay == 0 else 1) + 4.5
    return Box(f'station_led_{bay}', (x0, 4, 0), (x0 + 1, 5, 0.5), step=True)


def front(look=CONSOLE_LOOK):
    if look not in CONSOLE_LOOKS:
        raise ValueError(f'Unknown console look: {look}')
    image = Sprite(fill=SEAM)

    # Rear stepped riser front (y=9..13 -> v=6..14, u=0..32)
    image.rect(1, 7, 31, 14, BASE)
    image.row(6, 0, 32, SEAM)
    image.row(7, 1, 31, HIGHLIGHT)
    image.col(1, 7, 14, HIGHLIGHT)
    image.col(30, 7, 14, SHADE)
    # Recessed Mint telemetry bar across the rear riser
    image.rect(3, 9, 29, 12, WELL)
    image.row(8, 3, 29, PORT_DARK)
    image.row(12, 3, 29, PORT_LIGHT)
    image.row(10, 4, 13, RGB['mint'])
    image.row(10, 19, 28, RGB['mint'])
    # Central directional chevrons on the riser screen (Source left -> Target right)
    image.px(15, 9, IVORY)
    image.px(16, 10, IVORY)
    image.px(15, 11, IVORY)
    image.px(17, 10, RGB['mint'])

    # Desk bridge front nose (x=6.5..9.5 -> u=13..19, y=9..10 -> v=12..14)
    image.rect(13, 12, 19, 14, PORT_METAL)
    image.row(12, 13, 19, PORT_LIGHT)
    image.row(13, 14, 18, RGB['mint'])

    # Front docking fascia (y=0..9 -> v=14..32, u=0..32)
    image.rect(1, 15, 31, 31, BASE)
    image.row(14, 0, 32, SEAM)
    image.row(15, 1, 31, HIGHLIGHT)
    image.col(1, 15, 31, HIGHLIGHT)
    image.col(30, 15, 31, SHADE)
    # Plinth (y=0..3 -> v=26..32)
    image.rect(1, 28, 31, 31, SHADE)
    image.row(27, 1, 31, SEAM)
    bolt(image, 2, 16)
    bolt(image, 28, 16)

    if look == 'recessed_bridge':
        image.rect(2, 17, 30, 27, PORT_DARK)
    elif look == 'split_cradle':
        for bx in (2, 18):
            image.rect(bx, 17, bx + 12, 27, SHADE)
            image.row(17, bx, bx + 12, PORT_METAL)

    # Source header bar (left, Mint) and Target header bar (right, Ivory + Mint)
    image.rect(4, 17, 12, 19, WELL)
    image.row(17, 4, 12, RGB['mint'])
    image.rect(20, 17, 28, 19, WELL)
    image.row(17, 20, 24, IVORY)
    image.row(17, 24, 28, RGB['mint'])

    # Two cartridge bay openings (v=20..26)
    for bx, by in STATION_BAYS:
        image.ring(bx, by, bx + 12, by + 6, PORT_METAL)
        image.row(by, bx, bx + 12, PORT_LIGHT)
        image.row(by + 5, bx, bx + 12, SEAM)
        image.rect(bx + 1, by + 1, bx + 11, by + 5, WELL)

    # Central mullion (u=14..18, v=20..26) with clear left-to-right transfer arrow
    image.rect(14, 20, 18, 26, BASE)
    image.col(14, 20, 26, HIGHLIGHT)
    image.col(17, 20, 26, SHADE)
    image.row(23, 14, 17, IVORY)
    image.px(16, 22, IVORY)
    image.px(17, 23, IVORY)
    image.px(16, 24, IVORY)

    # Copper maker's badge on the lower plinth center
    image.rect(14, 29, 18, 31, COPPER)
    image.row(29, 14, 18, COPPER_LIGHT)
    return image


def top(look=CONSOLE_LOOK):
    if look not in CONSOLE_LOOKS:
        raise ValueError(f'Unknown console look: {look}')
    # On 'up', u grows +x (viewer-left is u=18..30, viewer-right is u=2..14) and v grows +z (front z=0 is v=0).
    image = Sprite(fill=SEAM)

    # Front desk top surface (z=0..9 -> v=0..18)
    image.rect(1, 1, 31, 18, BASE)
    image.row(1, 1, 31, HIGHLIGHT)
    image.col(1, 1, 18, HIGHLIGHT)
    image.col(30, 1, 18, SHADE)
    image.row(17, 1, 31, SHADE)

    # Two top inspection/cradle pads above the Target (u=3..13) and Source (u=19..29) docks
    for ux in (3, 19):
        image.ring(ux, 4, ux + 10, 15, PORT_METAL)
        image.rect(ux + 1, 5, ux + 9, 14, SHADE if look != 'recessed_bridge' else WELL)
        image.row(5, ux + 1, ux + 9, PORT_DARK)
        # Subtle steel guide rails inside each dock pad
        image.col(ux + 2, 6, 13, STEEL_SHADE)
        image.col(ux + 7, 6, 13, STEEL_SHADE)
    # Source pad (u=19..29) Mint marker, Target pad (u=3..13) Ivory marker
    image.row(7, 21, 27, RGB['mint'])
    image.row(7, 5, 11, IVORY)

    # Central raised transfer bridge top (x=6.5..9.5 -> u=13..19, z=3..9 -> v=6..18)
    image.rect(13, 6, 19, 18, PORT_METAL)
    image.row(6, 13, 19, PORT_LIGHT)
    image.col(13, 6, 18, PORT_LIGHT)
    image.col(18, 6, 18, PORT_DARK)
    image.rect(15, 8, 17, 16, RGB['mint'])
    # Arrow on bridge pointing from Source (+x, u=18) toward Target (-x, u=13)
    image.row(11, 14, 18, IVORY)
    image.px(15, 10, IVORY)
    image.px(14, 11, IVORY)
    image.px(15, 12, IVORY)

    # Rear stepped riser top plate (z=9..16 -> v=18..32)
    image.row(18, 0, 32, SEAM)
    image.rect(1, 19, 31, 31, BASE)
    image.row(19, 1, 31, HIGHLIGHT)
    image.col(1, 19, 31, HIGHLIGHT)
    image.col(30, 19, 31, SHADE)
    image.row(30, 1, 31, SHADE)
    image.rect(5, 22, 27, 26, SHADE)
    image.row(23, 7, 25, STEEL)
    image.row(24, 7, 25, TRIM)
    bolt(image, 2, 20)
    bolt(image, 28, 20)
    bolt(image, 2, 28)
    bolt(image, 28, 28)
    return image


def side():
    # Stepped side profile: desk y=0..9 (v=14..32), rear riser y=9..13 (v=6..14).
    image = Sprite(fill=SEAM)
    # Riser area (v=6..14)
    image.rect(1, 7, 31, 14, BASE)
    image.row(7, 1, 31, HIGHLIGHT)
    image.col(1, 7, 14, HIGHLIGHT)
    image.col(30, 7, 14, SHADE)
    image.row(13, 1, 31, SHADE)
    # Desk area (v=14..32)
    image.rect(1, 15, 31, 31, BASE)
    image.row(15, 1, 31, HIGHLIGHT)
    image.col(1, 15, 31, HIGHLIGHT)
    image.col(30, 15, 31, SHADE)
    # Lower plinth band (v=27..31)
    image.row(27, 1, 31, SEAM)
    image.rect(1, 28, 31, 31, SHADE)
    # Side louvres
    for vy in (19, 22):
        image.row(vy, 6, 26, SEAM)
        image.row(vy + 1, 6, 26, SHADE)
    return image


def back():
    # Rear face (y=0..13 -> v=6..32)
    image = Sprite(fill=SEAM)
    image.rect(1, 7, 31, 31, BASE)
    image.row(7, 1, 31, HIGHLIGHT)
    image.col(1, 7, 31, HIGHLIGHT)
    image.col(30, 7, 31, SHADE)
    image.row(14, 1, 31, SEAM)
    image.row(15, 1, 31, HIGHLIGHT)
    image.row(27, 1, 31, SEAM)
    image.rect(1, 28, 31, 31, SHADE)
    image.rect(10, 19, 22, 24, SHADE)
    image.ring(10, 19, 22, 24, PORT_METAL)
    bolt(image, 2, 8)
    bolt(image, 28, 8)
    bolt(image, 2, 24)
    bolt(image, 28, 24)
    return image


def inner():
    image = Sprite(fill=SHADE)
    for bx, by in STATION_BAYS:
        image.rect(bx, by, bx + 12, by + 6, WELL)
        image.row(by, bx + 1, bx + 11, PORT_DARK)
        # Fixed copper contact fingers at the back of each empty bay, never an inserted Cell faceplate.
        for finger in (bx + 3, bx + 7):
            image.rect(finger, by + 4, finger + 2, by + 6, COPPER)
            image.row(by + 4, finger, finger + 2, COPPER_LIGHT)
    return image


def geometry():
    # Low-profile stepped tabletop dock: 9 px front desk, 13 px rear riser, central bridge, and two side-by-side bays.
    return [
        Box('desk_core', (0, 0, 2), (16, 9, 16)),
        Box('rear_riser', (0, 9, 9), (16, 13, 16)),
        Box('desk_bridge', (6.5, 9, 3), (9.5, 10, 9)),
        Box('post_left', (15, 0, 0), (16, 9, 2)),
        Box('post_right', (0, 0, 0), (1, 9, 2)),
        Box('plinth', (1, 0, 0), (15, 3, 2)),
        Box('mullion', (7, 3, 0), (9, 6, 2)),
        Box('cap', (1, 6, 0), (15, 9, 2)),
    ]


def station_skin(box, face):
    if face == 'north':
        if box.name == 'desk_core':
            return 'inner'
        return 'front'
    if face == 'up':
        if box.name in ('plinth', 'mullion'):
            return 'inner'
        return 'top'
    if face == 'down':
        if box.on_boundary('down'):
            return 'plain'
        return 'inner'
    if face == 'south':
        return 'back' if box.on_boundary('south') else 'inner'
    if face in ('east', 'west'):
        if box.on_boundary(face):
            return 'side'
        if box.name == 'desk_bridge':
            return 'top'
        return 'inner'
    return 'plain'


def assets(look=CONSOLE_LOOK):
    family = Family()
    body = geometry()
    textures = {
        'front': NAME + '_front',
        'top': NAME + '_top',
        'side': NAME + '_side',
        'back': NAME + '_back',
        'plain': 'casing_plain',
        'inner': NAME + '_inner',
    }
    shown = family.model(NAME, 'network', textures, body, station_skin, particle='casing_plain')
    family.compose(NAME, shown)
    mounted = [
        (box, faces)
        for bay in range(2)
        for box, faces in (
            (station_cell_box(bay), ('north',)),
            (station_led_box(bay), ('north', 'east', 'west', 'up', 'down')),
        )
    ]
    check_boxes(NAME + ' mounted cells', [box for box, _ in mounted])
    family.compose(NAME + ' full', shown + mounted)
    family.blockstates[NAME] = {
        'variants': {
            f'facing={facing}': {'model': MODEL + NAME, **ROTATION[facing]}
            for facing in HORIZONTAL
        }
    }
    family.items[NAME] = {'model': {'type': 'minecraft:model', 'model': MODEL + NAME}}
    sprites_out = {
        NAME + '_front': front(look),
        NAME + '_top': top(look),
        NAME + '_side': side(),
        NAME + '_back': back(),
        NAME + '_inner': inner(),
    }
    return family, sprites_out


def generate(check=False):
    for item in KEYS.values():
        if not (ART / f"items/{item.split(':')[1]}.json").is_file():
            raise ValueError(f'Unknown ingredient asset: {item}')
    family, art = assets()
    out = GeneratedFiles(ROOT, check)
    for name, sprite in art.items():
        out.png(ART / f'textures/block/{name}.png', sprite.width, sprite.height, sprite.pixel)
    for folder, files in (('models/block', family.models), ('blockstates', family.blockstates), ('items', family.items)):
        for name, value in files.items():
            out.json(ART / folder / f'{name}.json', value)
    out.json(DATA / f'recipe/{NAME}.json', {
        'type': 'minecraft:crafting_shaped',
        'category': 'misc',
        'pattern': PATTERN,
        'key': KEYS,
        'result': {'id': 'rematter:' + NAME, 'count': 1},
    })
    out.json(DATA / f'advancement/recipes/{NAME}.json', shaped_discovery(NAME, PATTERN, KEYS))
    out.json(DATA / f'loot_table/blocks/{NAME}.json', {
        'type': 'minecraft:block',
        'pools': [{
            'rolls': 1,
            'entries': [{'type': 'minecraft:item', 'name': 'rematter:' + NAME}],
            'conditions': [{'condition': 'minecraft:survives_explosion'}],
        }],
    })
    print(f"{'Verified' if check else 'Generated'} {out.count} Cell Transfer Station files; eight boxes, two bays, four facings ({CONSOLE_LOOK})")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    generate(parser.parse_args().check)
