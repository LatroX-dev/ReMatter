"""Presentation-only connection legends preserve role identity, neutral hardware and complete direction choices."""
from collections import Counter
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest

sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
import check_visual_style as style
import family_sprites
import generate_energy_assets as energy
from family_palette import (CABLE_TIERS, COPPER, ENERGY_PORT_MODES, PORT_AUTOMATIC, PORT_AUTOMATIC_COLORS,
                            PORT_ENERGY_INPUT, PORT_ENERGY_OUTPUT, PRIMAL_BODY, WELL)


class PortLegendTests(unittest.TestCase):
    def test_every_automatic_alternative_shows_all_seven_exact_roles_in_the_ring(self):
        for look in family_sprites.PORT_AUTOMATIC_STYLES:
            sprite = family_sprites.machine_port(PORT_AUTOMATIC, automatic_style=look)
            ring = Counter(tuple(sprite.pixel(x, y)[:3]) for y in range(4, 28) for x in range(4, 28)
                           if x < 6 or x >= 26 or y < 6 or y >= 26)
            with self.subTest(look=look):
                self.assertEqual(set(ring), set(PORT_AUTOMATIC_COLORS))
                self.assertGreaterEqual(min(ring.values()), 4)
                self.assertLessEqual(len({pixel for row in sprite.rows for pixel in row}), 24)
                self.assertEqual(sprite.pixel(15, 15)[:3], COPPER)

    def test_cube_direction_keeps_the_tier_rim_and_contact_and_opposes_its_arrow(self):
        for cable in CABLE_TIERS:
            for width in energy.ENERGY_PORT_SIZES.values():
                input_ = energy.socket(cable, width, 'energy_input')
                output = energy.socket(cable, width, 'energy_output')
                inset = (16 - width * 2) // 2
                with self.subTest(cable=cable, width=width):
                    for sprite, colour in ((input_, PORT_ENERGY_INPUT), (output, PORT_ENERGY_OUTPUT)):
                        self.assertEqual(sprite.pixel(inset, inset)[:3], CABLE_TIERS[cable]['stripe'])
                        self.assertEqual(sprite.pixel(8, 8)[:3], COPPER)
                        self.assertGreaterEqual(sum(pixel[:3] == colour for row in sprite.rows for pixel in row), 4)
                    self.assertEqual(input_.pixel(6, 4)[:3], PORT_ENERGY_INPUT)
                    self.assertEqual(output.pixel(6, 4)[:3], WELL)
                    self.assertEqual(output.pixel(6, 3)[:3], PORT_ENERGY_OUTPUT)
                    self.assertEqual(input_.pixel(6, 3)[:3], WELL)

    def automatic_findings(self, mutate=None, mutate_java=None):
        sprite = family_sprites.machine_port(PORT_AUTOMATIC)
        if mutate:
            mutate(sprite)
        image = SimpleNamespace(width=32, height=32, pixels=lambda: sprite.rows)
        tree = SimpleNamespace(textures={'block/machine_port_automatic': None}, blockstates={},
                               image=lambda _: image, texture_path=lambda name: 'textures/' + name + '.png')
        java = style.JavaPalette((TOOLS.parent / 'src/main/java/dev/latrox/rematter/client/UiPalette.java').read_text(), '')
        if mutate_java:
            mutate_java(java)
        problems = style.Problems()
        style.check_port_legends(tree, problems, style.Palette.load(TOOLS), SimpleNamespace(), java)
        return problems.rules()

    def test_checker_accepts_the_exact_shared_automatic_legend(self):
        self.assertEqual(self.automatic_findings(), [])

    def test_checker_rejects_primal_colour_in_an_automatic_ring(self):
        self.assertEqual(self.automatic_findings(lambda sprite: sprite.px(4, 4, PRIMAL_BODY)), ['ports automatic'])

    def test_checker_rejects_an_incomplete_ui_legend(self):
        self.assertEqual(self.automatic_findings(mutate_java=lambda java: java.arrays.update(PORT_AUTOMATIC_COLORS=())),
                         ['ports colour'])

    def test_generated_cube_defaults_and_both_rendering_appearances_match_the_six_server_modes(self):
        family, _ = energy.assets()
        for cube in energy.TIERS:
            entry = family.blockstates[cube]['multipart'][4]['apply']
            for side, models in entry['sides'].items():
                self.assertEqual(set(models), {'disabled', *ENERGY_PORT_MODES})
                self.assertEqual(entry['default'][side], 'energy_output' if side == 'front' else 'energy_input')
                self.assertNotEqual(models['energy_input'], models['energy_output'])


if __name__ == '__main__':
    unittest.main()
