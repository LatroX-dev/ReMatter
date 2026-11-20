#!/usr/bin/env python3
"""Generate small palette-controlled sprites for the machine renderers' moving effects.

The baked family owns all static hardware. Dynamic optics use a hard-faceted 32-pixel sprite in their recipe
crystal's exact hue. The 16-pixel white mask is multiplied by a UiPalette colour at draw time; white here is
neutral ink, not a Primal identity. No retired material tile or old machine model is regenerated.
"""
from pathlib import Path
import argparse
from family_palette import PRIMAL_CORE, lighten, scale
from family_sprites import Sprite
from hardware_sprites import OwnedFiles
from machine_palette import RGB

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'src/main/resources/assets/rematter'
# These belonged to the former shared-material generator. Their replacements are owned by the family or the
# module generator; exact names keep cleanup away from material/item art and future unrelated assets.
RETIRED_BLOCK = ('bench_wood', 'brushed_steel', 'ceramic', 'copper_trim', 'display_active', 'display_idle',
                 'frame', 'graphite', 'hazard', 'indicator_active', 'indicator_idle', 'keyboard', 'panel',
                 'port_energy', 'port_matter', 'reactor_glass', 'rubber', 'socket_rim', 'vent', 'vessel',
                 'cell_led_amber', 'cell_led_blue', 'cell_led_green', 'cell_led_red',
                 'matter_cell_core_64k', 'matter_cell_core_1m', 'matrix_cluster_core', 'reaction_chamber_crystal')


def effect(hue):
    image = Sprite(32, fill=hue)
    image.row(0, 0, 32, lighten(hue, .45))
    image.col(0, 0, 32, lighten(hue, .45))
    image.row(31, 0, 32, scale(hue, .66))
    image.col(31, 1, 32, scale(hue, .66))
    return image


def generate(check=False):
    out = OwnedFiles(ROOT, check)
    image = Sprite(16, fill=PRIMAL_CORE)
    out.png(ART / 'textures/entity/effect_light.png', image.width, image.height, image.pixel)
    out.sweep(ART / 'textures/block', [pattern for name in RETIRED_BLOCK for pattern in (name + '.png', name + '.png.mcmeta')]
              + [pattern for name in RGB for pattern in (f'machine_{name}.png', f'machine_{name}.png.mcmeta')])
    out.sweep(ART / 'textures/entity', ['light.png', 'machine_*.png'])
    out.sweep(ART / 'textures/block',[f'effect_{name}.png' for name in ('ice','copper','leaf','violet','amber','ember')])
    out.sweep(ART / 'models/block', ['machine_socket_*.json'])
    print(f"{'Verified' if check else 'Generated'} {out.count} renderer effect sprites")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    generate(parser.parse_args().check)
