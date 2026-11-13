"""Energy Cube ports remain physically identical around the four horizontal sides.

Each side owns its complete outward face: a socket and frame while enabled, an unbroken casing face while
disabled. The charge core remains behind those faces. Check all tiers, charge/flow states and alternatives."""
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
from family_palette import COPPER, COPPER_LIGHT, COPPER_DARK, ENERGY_PORT_MODES
import port_plates
from generate_family_assets import Family

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


COMMON=Family()
port_plates.add_family_models(COMMON)


def resolve_model(family, name):
    table={};elements=None
    while name:
        value=family.models.get(name,COMMON.models.get(name))
        if value is None:break
        table={**value.get('textures',{}),**table}
        if elements is None and 'elements' in value:elements=value['elements']
        parent=value.get('parent','')
        name=parent.removeprefix('rematter:block/') if parent.startswith('rematter:block/') else ''
    if elements is None:raise AssertionError('Missing model geometry')
    return {'textures':table,'elements':elements}


def side_models(family, name, disabled=(), mode='energy_input'):
    entry=family.blockstates[name]['multipart'][4]['apply']
    return [resolve_model(family,entry['sides'][side]['disabled' if side in disabled else mode].removeprefix('rematter:block/'))
            for side in energy.SOCKET_SIDES]


def seats(family, name):
    return side_models(family,name)


def charge_model(family,name,charge,flow):
    suffix='' if charge==0 or flow=='none' else '_'+flow
    return family.models[f'{name}_charge_{charge}'+suffix]


class EnergyCubeGeometryTests(unittest.TestCase):
    def test_every_tier_charge_and_alternative_has_identical_horizontal_geometry_and_appearance(self):
        for look in energy.ENERGY_CUBE_LOOKS:
            for port_style in energy.ENERGY_PORT_SIZES:
                family, _ = energy.assets(look, port_style)
                for name in energy.TIERS:
                    for charge,flow,mode in ((c,f,m) for c in range(9) for f in ('none','in','out') for m in ('energy_input','energy_output','disabled')):
                        with self.subTest(look=look,port=port_style,tier=name,charge=charge,flow=flow,mode=mode):
                            models=[family.models[name],charge_model(family,name,charge,flow)]
                            models+=side_models(family,name,energy.SOCKET_SIDES if mode=='disabled' else (),mode)
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
                models = [family.models[name], family.models[f'{name}_charge_0']] + seats(family, name)
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


    def test_disabled_horizontal_faces_are_unbroken_plain_casing_including_the_front(self):
        family,_=energy.assets()
        for name in energy.TIERS:
            entry=family.blockstates[name]['multipart'][4]['apply']
            self.assertEqual(set(entry['sides']),{'front','left','back','right','top','bottom'})
            for local in energy.SOCKET_SIDES:
                face=port_plates.FACE[local]
                model=resolve_model(family,entry['sides'][local]['disabled'].removeprefix('rematter:block/'))
                quads=side_quads([model],face)
                self.assertEqual(len(quads),1)
                rectangle,depth,texture,_,emission=quads[0]
                self.assertEqual(rectangle,(0,0,16,16),'disabled side leaves a window or cutout')
                self.assertEqual(depth,0,'disabled casing must lie on the block boundary')
                self.assertEqual(texture,'rematter:block/casing_plain')
                self.assertEqual(emission,0,'disabled face must not expose the emissive charge window')
                self.assertEqual(set(model['elements'][0]['faces']),{face})
                self.assertEqual(model['elements'][0]['faces'][face]['cullface'],face)
            for face in HORIZONTAL:
                self.assertTrue(all(quad[1]>0 for quad in side_quads([family.models[name]],face)),
                                'common housing retains an outer face that would overlap a blank panel')
            for local in ('top','bottom'):
                self.assertEqual(entry['sides'][local]['disabled'],f'rematter:block/port_plain_{local}_disabled')
                for mode in ENERGY_PORT_MODES:
                    self.assertEqual(entry['sides'][local][mode],f'rematter:block/{name}_port_{local}_{mode}')

    def test_mixed_blank_and_connected_compositions_cover_every_direction_charge_and_flow(self):
        family,_=energy.assets()
        # Charge0 uses the same geometry for all flows; the other eight charges use three independent models.
        states=[(0,'none')]+[(c,f) for c in range(1,9) for f in ('none','in','out')]
        for name in energy.TIERS:
            for charge,flow in states:
                for mode in ('blank','connected',*('disabled-'+s for s in energy.SOCKET_SIDES)):
                    for facing in HORIZONTAL:
                        self.assertIn(f'{name} charge {charge} {flow} {mode} {facing}',family.compositions)
            for disabled in energy.SOCKET_SIDES:
                models=[family.models[name],charge_model(family,name,8,'in')]+side_models(family,name,{disabled})
                face=port_plates.FACE[disabled]
                outer=[q for q in side_quads(models,face) if q[1]==0]
                self.assertEqual(len(outer),1,'mixed configuration still overlays housing/socket on its blank face')
                self.assertEqual(outer[0][0],(0,0,16,16))
                self.assertEqual(outer[0][2],'rematter:block/casing_plain')


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
