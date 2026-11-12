#!/usr/bin/env python3
"""Generate the five industrial modules as members of the machine family.

Owns their sprites, block models, block states and item definitions, and
their loot tables, recipes, advancements and block tag. A module is a short
list of boxes on the half-pixel grid: the common mount, a two-pixel socket
plate with a gunmetal flange, and a mechanism whose silhouette tells its job.
The crystal of a module carries the identity hue of that job; it and the
other working parts glow while the block state reports the module at work.
The ceiling variant is the same design mirrored, with its sprites laid on
again, so its light still comes from the top left. Generation fails when a
model leaves the grid, forms slivers, intersects, would z-fight in a state
the block can show, or exceeds twelve boxes.
"""
from pathlib import Path
import argparse

from family_geometry import ROTATION, Box, GeometryError, build, check_boxes, check_composition, emitted, mapped_uv
from generated_files import GeneratedFiles
from hardware_sprites import (DARK_FACE, GUNMETAL_FACE, NATURAL, STEEL_FACE, STEEL_PLAIN, Sheet, blade, coil,
                              collar_side, collar_top, crystal, cutting_edge, face_size, liquid, membrane,
                              mount_front, mount_side, mount_top, module_hue, well)

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/rematter'
MODEL = 'rematter:block/'
NAMES = ('grinding_unit', 'separation_unit', 'mixing_basin', 'field_coil', 'reaction_chamber')
HORIZONTAL = ('north', 'east', 'south', 'west')
# Most boxes a module may show at once: mount, mechanism and working parts together.
BUDGET = 12
# The mount is common to all five: the socket plate lies in the chamber floor, the flange carries the mechanism.
PLATE = ('mount_plate', (0, 0, 0), (16, 2, 16))
COLLAR = ('mount_collar', (3, 2, 3), (13, 3, 13))

# Mechanism of each module as (name, from, to, material, faces with another material). The front faces north.
BODY = {
    # Resonance Cutter: a blade bridge. Two posts carry the bridge, the blade hangs over the anvil.
    'grinding_unit': (
        ('anvil', (5, 3, 6), (11, 5, 10), 'dark', {}),
        ('post_left', (11, 3, 6), (13, 11, 10), 'dark', {}),
        ('post_right', (3, 3, 6), (5, 11, 10), 'dark', {}),
        ('bridge', (3, 11, 5), (13, 13, 11), 'steel', {}),
        ('blade', (5, 7, 7.5), (11, 11, 8.5), 'blade', {}),
    ),
    # Phase Filter: three membrane plates threaded on a crystal rod.
    'separation_unit': (
        ('foot', (6, 3, 6), (10, 4, 10), 'dark', {}),
        ('membrane_low', (3, 4, 3), (13, 5, 13), 'membrane', {}),
        ('membrane_mid', (3, 8, 3), (13, 9, 13), 'membrane', {}),
        ('membrane_high', (3, 12, 3), (13, 13, 13), 'membrane', {}),
    ),
    # Confluence Basin: an open basin with a stirrer.
    'mixing_basin': (
        ('basin_floor', (2, 3, 2), (14, 4, 14), 'dark', {}),
        ('wall_front', (2, 4, 2), (14, 8, 3), 'plain', {'south': 'well'}),
        ('wall_back', (2, 4, 13), (14, 8, 14), 'plain', {'north': 'well'}),
        ('wall_left', (13, 4, 3), (14, 8, 13), 'plain', {'west': 'well'}),
        ('wall_right', (2, 4, 3), (3, 8, 13), 'plain', {'east': 'well'}),
        ('paddle', (4, 6, 7.5), (12, 7, 8.5), 'plain', {}),
        ('shaft', (7, 7, 7), (9, 11, 9), 'plain', {}),
    ),
    # Field Stabilizer: one wound coil around a crystal core.
    'field_coil': (
        ('core_foot', (6, 3, 6), (10, 4, 10), 'dark', {}),
        ('pylon_left', (12, 3, 6), (13, 7, 10), 'dark', {}),
        ('pylon_right', (3, 3, 6), (4, 7, 10), 'dark', {}),
        ('coil_front', (2, 7, 2), (14, 10, 4), 'coil', {}),
        ('coil_back', (2, 7, 12), (14, 10, 14), 'coil', {}),
        ('coil_left', (12, 7, 4), (14, 10, 12), 'coil', {}),
        ('coil_right', (2, 7, 4), (4, 10, 12), 'coil', {}),
    ),
    # Reaction Focus: a fork that cradles the lens crystal.
    'reaction_chamber': (
        ('stem', (7, 3, 7), (9, 5, 9), 'plain', {}),
        ('yoke', (3, 5, 6.5), (13, 6, 9.5), 'plain', {}),
        ('arm_left', (12, 6, 6.5), (13, 11, 9.5), 'plain', {}),
        ('arm_right', (3, 6, 6.5), (4, 11, 9.5), 'plain', {}),
    ),
}
# Working parts as (name, from, to, material). They change sprite and glow with the block state.
WORK = {
    'grinding_unit': (
        ('cutting_edge', (5, 6, 7.5), (11, 7, 8.5), 'edge'),
        ('resonator', (6, 13, 6), (10, 15, 10), 'crystal'),
    ),
    'separation_unit': (
        ('rod_low', (6, 5, 6), (10, 8, 10), 'crystal'),
        ('rod_high', (6, 9, 6), (10, 12, 10), 'crystal'),
        ('finial', (6, 13, 6), (10, 15, 10), 'crystal'),
    ),
    'mixing_basin': (
        ('charge', (3, 4, 3), (13, 6, 13), 'liquid'),
        ('stirrer_head', (6.5, 11, 6.5), (9.5, 13, 9.5), 'crystal'),
    ),
    'field_coil': (
        ('core', (6.5, 4, 6.5), (9.5, 14, 9.5), 'crystal'),
    ),
    'reaction_chamber': (
        ('lens_low', (6, 6, 7), (10, 7, 9), 'crystal'),
        ('lens', (4, 7, 7), (12, 12, 9), 'crystal'),
        ('lens_high', (6, 12, 7), (10, 13, 9), 'crystal'),
        ('lens_front', (6, 8, 6.5), (10, 11, 7), 'crystal'),
        ('lens_back', (6, 8, 9), (10, 11, 9.5), 'crystal'),
    ),
}
# Sprites of working parts that move while the module works.
ANIMATED = {'mixing_basin': (4, {'animation': {'frametime': 4}})}


def painters(name):
    """Painter of each material by whether the module hangs; only the blade is drawn differently upside down."""
    hue = module_hue(name)
    fixed = {'steel': STEEL_FACE, 'plain': STEEL_PLAIN, 'dark': DARK_FACE, 'gunmetal': GUNMETAL_FACE, 'well': well,
             'membrane': membrane, 'coil': coil, 'edge': cutting_edge(hue), 'crystal': crystal(hue),
             'liquid': liquid(hue)}
    oriented = {'blade': blade}

    def painter(material, suspended):
        if material in oriented:
            return (material, suspended), oriented[material](suspended)
        return (material,), fixed[material]
    return painter


def mirrored(lo, hi):
    """A box under the ceiling: the floor design turned upside down."""
    return (lo[0], 16 - hi[1], lo[2]), (hi[0], 16 - lo[1], hi[2])


class Module:
    """Boxes, tiles and models of one module, standing and suspended."""

    def __init__(self, name, mount):
        self.name = name
        self.mount = mount
        self.sheet = Sheet('module_' + name, 'body')
        self.work = Sheet('module_' + name + '_work', 'work')
        self.models = {}
        self.compositions = 0
        self.painter = painters(name)
        self.materials = {part: (material, other) for part, _, _, material, other in BODY[name]}
        self.materials.update({part: (material, {}) for part, _, _, material in WORK[name]})
        # Both variants ask for their tiles before the first model is laid out: a later tile moves earlier ones.
        for suspended in (False, True):
            mount_boxes, body, work = self.boxes(suspended)
            build(mount_boxes + body + work, self.skin(suspended))
        for suspended in (False, True):
            self.variant(suspended)

    def boxes(self, suspended):
        place = mirrored if suspended else lambda lo, hi: (lo, hi)

        def box(part, lo, hi):
            return Box(part, *place(lo, hi), step=min(b - a for a, b in zip(lo, hi)) < 1, flat=True)
        mount = [Box(PLATE[0], *place(*PLATE[1:])), Box(COLLAR[0], *place(*COLLAR[1:]), flat=True)]
        return (mount, [box(part, lo, hi) for part, lo, hi, _, _ in BODY[self.name]],
                [box(part, lo, hi) for part, lo, hi, _ in WORK[self.name]])

    def skin(self, suspended):
        # A mirrored face keeps the role it has on the floor: what points at the support carries the plug.
        role = {'up': 'down', 'down': 'up'} if suspended else {}
        work = {part for part, _, _, _ in WORK[self.name]}

        def skin(box, face):
            floor_face = role.get(face, face)
            if box.name == 'mount_plate':
                if floor_face in ('up', 'down'):
                    return ('seat' if floor_face == 'up' else 'port',
                            *mapped_uv(face, box.lo, box.hi, *NATURAL[face], (0, 0)))
                return self.mount.face('plate_front' if face == 'north' else 'plate_side', box, face)
            if box.name == 'mount_collar':
                return self.mount.face('collar_top' if floor_face in ('up', 'down') else 'collar_side', box, face)
            material, other = self.materials[box.name]
            variant, paint = self.painter(other.get(floor_face, material), suspended)
            sheet = self.work if box.name in work else self.sheet
            key = (*variant, *face_size(box, face))
            sheet.want(key, *face_size(box, face), paint)
            return sheet.face(key, box, face)
        return skin

    def variant(self, suspended):
        name = self.name + ('_suspended' if suspended else '')
        mount, body, work = self.boxes(suspended)
        if len(mount + body + work) > BUDGET:
            raise GeometryError(f'{name}: {len(mount + body + work)} boxes exceed the budget of {BUDGET}')
        check_boxes(name, mount + body + work)
        skin = self.skin(suspended)
        drawn = self.model(name, mount + body, skin, mount + body + work)
        for state, glowing in (('idle', False), ('active', True)):
            parts = [Box(box.name, box.lo, box.hi, emissive=glowing, step=box.step, flat=True) for box in work]
            shown = self.model(f'{name}_{state}_parts', parts, skin, mount + body + parts,
                               '_active' if glowing else '')
            check_composition(f'{name} {state}', drawn + shown)
            self.compositions += 1

    def model(self, name, boxes, skin, context, suffix=''):
        elements = build(boxes, skin, context)
        used = {face['texture'][1:] for element in elements for face in element['faces'].values()}
        table = {'seat': 'module_mount_top', 'port': 'casing_port', **self.mount.textures(),
                 **self.sheet.textures(), **self.work.textures(suffix)}
        textures = {'particle': MODEL + 'module_mount_top'}
        textures.update({key: MODEL + table[key] for key in sorted(used)})
        self.models[name] = {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements}
        return emitted(boxes, elements)

    def blockstate(self):
        states = []
        for suspended in ('false', 'true'):
            body = self.name + ('_suspended' if suspended == 'true' else '')
            for facing in HORIZONTAL:
                states.append({'when': {'facing': facing, 'suspended': suspended},
                               'apply': {'model': MODEL + body, **ROTATION[facing]}})
                for active, state in (('false', 'idle'), ('true', 'active')):
                    states.append({'when': {'facing': facing, 'suspended': suspended, 'active': active},
                                   'apply': {'model': f'{MODEL}{body}_{state}_parts', **ROTATION[facing]}})
        return {'multipart': states}

    def item(self):
        """The item is the standing module at work, so the five icons differ by the hue of their job."""
        return {'model': {'type': 'minecraft:composite', 'models': [
            {'type': 'minecraft:model', 'model': MODEL + self.name},
            {'type': 'minecraft:model', 'model': f'{MODEL}{self.name}_active_parts'}]}}

    def sprites(self):
        """Sprites by name, with the animation of those that have one."""
        result = {sprite: (art, None) for sprite, art in self.sheet.sprites().items()}
        result.update({sprite: (art, None) for sprite, art in self.work.sprites().items()})
        frames, animation = ANIMATED.get(self.name, (1, None))
        result.update({sprite: (art, animation)
                       for sprite, art in self.work.sprites('_active', frames, active=True).items()})
        return result


def mount_sheet():
    """Tiles of the common mount. Every module asks for the same four, so they share one sprite."""
    sheet = Sheet('module_mount', 'mount')
    sheet.want('plate_side', 32, 4, mount_side)
    sheet.want('plate_front', 32, 4, mount_front)
    sheet.want('collar_side', 20, 2, collar_side)
    sheet.want('collar_top', 20, 20, collar_top)
    return sheet


def check_references(modules, art):
    """Every sprite a model names is written here or belongs to the family; every written sprite has a user."""
    named = {texture.removeprefix(MODEL) for module in modules for model in module.models.values()
             for texture in model['textures'].values()}
    borrowed = {'casing_port'}
    if named != set(art) | borrowed:
        raise GeometryError(f'sprites without a user or a painter: {sorted(named ^ (set(art) | borrowed))}')
    for name in borrowed:
        if not (ASSETS / f'textures/block/{name}.png').is_file():
            raise GeometryError(f'family sprite {name} is missing')
    for name, (sprite, animation) in art.items():
        frames = sprite.height // sprite.width
        if sprite.width != 32 or sprite.height != 32 * frames or (frames > 1) != bool(animation):
            raise GeometryError(f'{name}: a module sprite is 32 texels wide; only animated ones hold several frames')
        if any(sprite.pixel(x, y)[3] < 255 for y in range(sprite.height) for x in range(sprite.width)):
            raise GeometryError(f'{name}: module sprites are opaque')


RECIPES = {
    'grinding_unit': (['DMD', 'RBR', 'VFV'], {'D': 'rematter:diamond_lattice', 'M': 'rematter:amber_matrix_crystal', 'R': 'rematter:rhevon_ingot', 'B': 'rematter:structural_bars', 'V': 'rematter:virelite_ceramic', 'F': 'rematter:machine_frame'}),
    'separation_unit': (['VMV', 'DOD', 'RFR'], {'V': 'rematter:virelite_ceramic', 'M': 'rematter:amber_matrix_crystal', 'D': 'rematter:diamond_lattice', 'O': 'rematter:optical_circuit', 'R': 'rematter:rhevon_ingot', 'F': 'rematter:machine_frame'}),
    'mixing_basin': (['M M', 'VMV', 'RFR'], {'M': 'rematter:amber_matrix_crystal', 'V': 'rematter:virelite_ceramic', 'R': 'rematter:rhevon_ingot', 'F': 'rematter:machine_frame'}),
    'field_coil': (['CLC', 'CMC', 'FQF'], {'C': 'rematter:copper_dust', 'L': 'rematter:lumivex_ingot', 'M': 'rematter:violet_matrix_crystal', 'Q': 'rematter:control_circuit', 'F': 'rematter:machine_frame'}),
    'reaction_chamber': (['GDG', 'VMV', 'FOF'], {'G': 'rematter:reactor_glass', 'D': 'rematter:diamond_lattice', 'V': 'rematter:virelite_ceramic', 'M': 'rematter:violet_matrix_crystal', 'F': 'rematter:machine_frame', 'O': 'rematter:optical_circuit'}),
}


def data_files():
    """Loot tables, recipes, discovery advancements and the block tag; none of them depends on the look."""
    for name in NAMES:
        yield RES / f'data/rematter/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'rematter:' + name}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}
    for name, (pattern, key) in RECIPES.items():
        yield RES / f'data/rematter/recipe/{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': key, 'result': {'id': 'rematter:' + name, 'count': 1}}
        yield RES / f'data/rematter/advancement/recipes/{name}.json', {'parent': 'minecraft:recipes/root', 'criteria': {'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': ['rematter:machine_frame']}]}}, 'has_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': 'rematter:' + name}}}, 'requirements': [['has_material', 'has_recipe']], 'rewards': {'recipes': ['rematter:' + name]}}
    # The vanilla mineable tag references this private-to-the-mod grouping once.
    yield RES / 'data/rematter/tags/block/industrial_modules.json', {'replace': False, 'values': ['rematter:' + name for name in NAMES]}


# Two 128-pixel textures of the old look still have a reader each: the block entity renderer draws its runtime
# crystals with matrix_cluster_core, and four retired socket models name reaction_chamber_crystal. They are
# written byte for byte until those readers are gone, so nothing shows a missing texture; then both lines go.
def legacy_textures(out):
    import math
    from material_tiles import TILE, crystal_pixel

    def cluster_core(x, y):
        blush = max(0, 1 - math.hypot((x / (TILE - 1) - .66) / .64, (y / (TILE - 1) - .75) / .62))
        ivory = (205, 198, 184)
        rhedon = (176, 98, 96)
        color = tuple(round(base + (accent - base) * .58 * blush) for base, accent in zip(ivory, rhedon))
        return crystal_pixel(color, x, y)
    out.png(ASSETS / 'textures/block/reaction_chamber_crystal.png', TILE, TILE,
            lambda x, y: crystal_pixel((83, 185, 181), x, y))
    out.png(ASSETS / 'textures/block/matrix_cluster_core.png', TILE, TILE, cluster_core)


def generate(check, models=True, textures=True, data=True):
    mount = mount_sheet()
    modules = [Module(name, mount) for name in NAMES]
    art = {'module_mount_top': (mount_top(), None)}
    art.update({name: (sprite, None) for name, sprite in mount.sprites().items()})
    for module in modules:
        art.update(module.sprites())
    check_references(modules, art)
    out = GeneratedFiles(ROOT, check)
    if textures:
        for name, (sprite, animation) in art.items():
            out.png(ASSETS / f'textures/block/{name}.png', sprite.width, sprite.height, sprite.pixel)
            if animation:
                out.json(ASSETS / f'textures/block/{name}.png.mcmeta', animation)
        legacy_textures(out)
    if models:
        for module in modules:
            for name, model in module.models.items():
                out.json(ASSETS / f'models/block/{name}.json', model)
            out.json(ASSETS / f'blockstates/{module.name}.json', module.blockstate())
            out.json(ASSETS / f'items/{module.name}.json', module.item())
    if data:
        for path, value in data_files():
            out.json(path, value)
    return out.count, modules, art


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--models-only', action='store_true',
                      help='write models, block states and item definitions only')
    mode.add_argument('--textures-only', action='store_true', help='write the sprites only')
    parser.add_argument('--check', action='store_true', help='compare the selected files instead of writing them')
    args = parser.parse_args()
    count, modules, art = generate(args.check, models=not args.textures_only, textures=not args.models_only,
                                   data=not (args.models_only or args.textures_only))
    print(('Verified' if args.check else 'Generated') + f' {count} module files; {len(modules) * 6} block models'
          f' of at most {BUDGET} boxes passed the geometry checks in {sum(m.compositions for m in modules)} compositions.')
