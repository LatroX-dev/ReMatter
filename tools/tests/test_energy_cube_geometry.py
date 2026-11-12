"""Energy Cube ports remain physically identical around the four horizontal sides."""
from collections import Counter
import json
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest

sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
import generate_energy_assets as energy
import check_visual_style as style
from family_palette import COPPER, COPPER_LIGHT, COPPER_DARK
from family_sprites import sprites as family_sprites

HORIZONTAL = ('north', 'east', 'south', 'west')
# Minecraft's face vertices, ordered top-left, bottom-left, bottom-right, top-right when viewed from outside.
CORNERS = {
    'north': ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    'east': ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
    'south': ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    'west': ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
}


def turn(point):
    x, y, z = point
    return 16 - z, y, x


def bounds(element, rotation=0):
    lo, hi = element['from'], element['to']
    corners = [tuple((lo, hi)[pick][axis] for axis, pick in enumerate((x, y, z)))
               for x in (0, 1) for y in (0, 1) for z in (0, 1)]
    for _ in range(rotation):
        corners = [turn(point) for point in corners]
    return tuple(min(point[axis] for point in corners) for axis in range(3)) + tuple(
        max(point[axis] for point in corners) for axis in range(3))


def geometry(elements, rotation=0):
    return Counter((bounds(element, rotation), element.get('shade', True), element.get('light_emission', 0),
                    json.dumps(element.get('neoforge_data', {}), sort_keys=True)) for element in elements)


def default_uv(face, lo, hi):
    if face == 'north': return 16 - hi[0], 16 - hi[1], 16 - lo[0], 16 - lo[1]
    if face == 'east': return 16 - hi[2], 16 - hi[1], 16 - lo[2], 16 - lo[1]
    if face == 'south': return lo[0], 16 - hi[1], hi[0], 16 - lo[1]
    return lo[2], 16 - hi[1], hi[2], 16 - lo[1]


def outside(point, face):
    x, y, z = point
    if face == 'north': return x, y, z
    if face == 'east': return z, y, 16 - x
    if face == 'south': return 16 - x, y, 16 - z
    return 16 - z, y, x


def side_quads(models, side):
    result = []
    for model in models:
        for element in model['elements']:
            face = element.get('faces', {}).get(side)
            if face is None: continue
            lo, hi = element['from'], element['to']
            points = [outside(tuple((lo, hi)[pick][axis] for axis, pick in enumerate(corner)), side)
                      for corner in CORNERS[side]]
            rectangle = (min(p[0] for p in points), min(p[1] for p in points),
                         max(p[0] for p in points), max(p[1] for p in points))
            depth = points[0][2]
            texture = face['texture']
            while texture.startswith('#'): texture = model['textures'][texture[1:]]
            uv = face.get('uv', default_uv(side, lo, hi))
            uvs = [(uv[0] if i in (0, 1) else uv[2], uv[1] if i in (0, 3) else uv[3]) for i in range(4)]
            shift = face.get('rotation', 0) // 90
            uvs = uvs[shift:] + uvs[:shift]
            result.append((rectangle, depth, texture, tuple(uvs), element.get('light_emission', 0)))
    return result


class EnergyCubeGeometryTests(unittest.TestCase):
    def test_every_tier_charge_and_alternative_has_identical_horizontal_geometry_and_appearance(self):
        for look in energy.ENERGY_CUBE_LOOKS:
            for port_style in energy.ENERGY_PORT_SIZES:
                family, _ = energy.assets(look, port_style)
                for name in energy.TIERS:
                    for charge in range(9):
                        with self.subTest(look=look, port=port_style, tier=name, charge=charge):
                            models = [family.models[name], family.models[f'{name}_charge_{charge}']]
                            elements = [element for model in models for element in model['elements']]
                            self.assertEqual(geometry(elements), geometry(elements, 1), 'quarter-turn changes the physical shape')
                            expected = Counter(side_quads(models, 'north'))
                            for side in HORIZONTAL[1:]:
                                self.assertEqual(expected, Counter(side_quads(models, side)), f'{side} has a different surface or texture mapping')

    def test_each_horizontal_face_has_a_centered_contact_that_fits_the_native_cable_plug(self):
        shared = family_sprites()
        contacts = {tuple(colour) for colour in (COPPER, COPPER_LIGHT, COPPER_DARK)}
        for look in energy.ENERGY_CUBE_LOOKS:
            family, art = energy.assets(look)
            for name in energy.TIERS:
                models = [family.models[name], family.models[f'{name}_charge_0']]
                for side in HORIZONTAL:
                    with self.subTest(look=look, tier=name, side=side):
                        found = False
                        for rectangle, depth, texture, uvs, _ in side_quads(models, side):
                            x0, y0, x1, y1 = rectangle
                            # The real native flange is 6x6. A whole side panel is not a raised/recessed seat.
                            if not (0 <= depth <= 2 and 6 <= x1 - x0 <= 10 and 6 <= y1 - y0 <= 10
                                    and x0 + x1 == 16 and y0 + y1 == 16):
                                continue
                            key = texture.removeprefix('rematter:block/')
                            sprite = art.get(key, shared.get(key))
                            self.assertIsNotNone(sprite, texture)
                            u, v = (sum(c[axis] for c in uvs) / 4 for axis in (0, 1))
                            tx = min(sprite.width - 1, max(0, int(u * sprite.width / 16)))
                            ty = min(sprite.height - 1, max(0, int(v * sprite.height / 16)))
                            found |= tuple(sprite.rows[ty][tx][:3]) in contacts
                        self.assertTrue(found, 'no centered physical seat with a copper contact for a cable')


class EnergyTierProvenanceTests(unittest.TestCase):
    def setUp(self):
        self.palette = style.Palette.load(TOOLS)
        self.stripe = self.palette.named['CABLE_TIERS.matter_cable.stripe']
        self.recipes = [('recipe/energy_cube.json', [(['rematter:matter_cable'], 1)], ['rematter:energy_cube'], False)]
        self.band = self.stripe
        self.table = ('<!-- check:energy-cable-tiers -->\n| Energy Cube | Cable ingredient | Hex |\n'
                      '| --- | --- | --- |\n| `energy_cube` | `matter_cable` | `#dc894f` |')

    def findings(self):
        tree = SimpleNamespace(items={'energy_cube': {}}, blockstates={},
                               recipes=SimpleNamespace(entries=self.recipes), shown=lambda _: (Counter({self.band: 8}), Counter()))
        problems = style.Problems()
        style.check_energy_tier_bands(tree, problems, self.palette, style.Guide(self.table))
        return problems.items

    def test_cable_ingredient_exact_band_and_documented_relationship_agree(self):
        self.assertEqual(self.findings(), [])

    def test_optional_or_wrong_cable_cannot_claim_its_tier_band(self):
        self.recipes[0][1][0] = (['rematter:matter_cable', 'minecraft:copper_ingot'], 1)
        self.assertTrue(any('ingredient' in item[2] for item in self.findings()))

    def test_different_tier_colour_is_rejected(self):
        self.band = self.palette.named['CABLE_TIERS.reinforced_matter_cable.stripe']
        self.assertTrue(any('exact' in item[2] for item in self.findings()))

    def test_undocumented_material_role_is_rejected(self):
        self.table = self.table.replace('`matter_cable`', '`resonant_matter_cable`')
        self.assertTrue(any(item[1] == 'docs/visual-style.md' for item in self.findings()))


if __name__ == '__main__': unittest.main()
