"""Contracts shared by asset generators and the style gate (standard library only)."""
from collections import Counter
import colorsys
import hashlib
import json
import re
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest

sys.dont_write_bytecode = True
TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
import check_visual_style as style
import generate_primal_assets as primal
from hardware_sprites import OwnedFiles, Sheet, SIZE
from material_tiles import png_bytes


class SheetTests(unittest.TestCase):
    TILES = [(str(i), w, h) for i, (w, h) in enumerate(
        ((32, 20), (20, 12), (12, 12), (7, 18), (25, 7), (3, 32), (16, 16), (10, 6), (2, 2)))]

    def sheet(self, tiles):
        sheet = Sheet('parts', 'parts')
        for key, width, height in tiles:
            sheet.want(key, width, height, lambda *args: None)
        sheet.pack()
        return sheet

    def test_pack_is_deterministic_and_bounded_without_overlap(self):
        first, second = self.sheet(self.TILES), self.sheet(reversed(self.TILES))
        self.assertEqual(first.places, second.places)
        self.assertGreater(first.count, 1)
        occupied = set()
        for key, (page, x, y) in first.places.items():
            width, height, _ = first.tiles[key]
            self.assertTrue(0 <= x <= SIZE - width and 0 <= y <= SIZE - height)
            area = {(page, tx, ty) for tx in range(x, x + width) for ty in range(y, y + height)}
            self.assertFalse(occupied & area, key)
            occupied |= area

    def test_repeated_key_shares_tile_but_conflicting_dimensions_fail(self):
        sheet = self.sheet([('same', 4, 8), ('same', 4, 8)])
        self.assertEqual(len(sheet.places), 1)
        with self.assertRaises(ValueError):
            sheet.want('same', 8, 4, None)

    def test_invalid_tile_dimensions_fail(self):
        for width, height in ((0, 4), (4, -1), (33, 3)):
            with self.subTest(size=(width, height)), self.assertRaises(ValueError):
                Sheet('parts', 'parts').want('invalid', width, height, None)


class OwnedFilesTests(unittest.TestCase):
    def test_check_reports_stale_files_without_writing_or_deleting(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            kept, stale, foreign = (root / name for name in ('kept.png', 'stale.png', 'foreign.txt'))
            kept.write_bytes(b'kept'); stale.write_bytes(b'stale'); foreign.write_bytes(b'foreign')
            writer = OwnedFiles(root, check=True)
            writer.bytes(kept, b'kept')
            with self.assertRaisesRegex(ValueError, 'Stale generated file: stale.png'):
                writer.sweep(root, ('*.png',))
            self.assertEqual({path.name: path.read_bytes() for path in root.iterdir()},
                             {'kept.png': b'kept', 'stale.png': b'stale', 'foreign.txt': b'foreign'})

    def test_generation_sweeps_once_even_when_patterns_overlap(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            stale, kept, foreign = (root / name for name in ('old.png', 'kept.png', 'foreign.txt'))
            stale.write_bytes(b'old'); foreign.write_bytes(b'foreign')
            writer = OwnedFiles(root, check=False)
            writer.bytes(kept, b'new')
            self.assertEqual(writer.sweep(root, ('*.png', 'old.*')), [stale])
            self.assertEqual(kept.read_bytes(), b'new')
            self.assertEqual(foreign.read_bytes(), b'foreign')
            self.assertFalse(stale.exists())


class PrimalVariantTests(unittest.TestCase):
    # These are the original alternate look's immutable drawings/strip. Choosing spectrum must not rewrite aurora.
    AURORA = {
        'primal_shard': '7b9d7a4323220edff9cf142dcec2c0d2d783acb20b46886713f660c6fe2929cb',
        'primal_crystal': '5713cb08b3b33e22a2cd65f1f01205362654688ff8251b7c40d5c89c81ee62ad',
        'discovery_lens': '006900603a63256734ad8babac87dc59ab32cd6bb350904be771438e68912f7d',
        'ore_A': 'befac4b20511b264614294c60511610e8470010059ee5a6b1517645586c57b54',
    }

    def test_all_design_look_combinations_keep_square_frames_and_alpha_masks(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'strip.png'
            for design in primal.ORE_DESIGNS:
                expected = {(x, y) for x, y in primal.ore_layers(design)[0]}
                for look in primal.PRIMAL_LOOKS:
                    with self.subTest(design=design, look=look):
                        content = png_bytes(16, 16 * primal.ORE_FRAMES, primal.glow_strip(design, look))
                        path.write_bytes(content)
                        image = style.Image(path)
                        self.assertEqual((image.width, image.height), (16, 256))
                        for frame in range(primal.ORE_FRAMES):
                            mask = {(x, y) for y in range(16) for x in range(16)
                                    if image.pixels()[16 * frame + y][x][3]}
                            self.assertEqual(mask, expected)
                            self.assertLessEqual({p[3] for row in image.pixels() for p in row}, {0, 255})

    def test_spectrum_bodies_show_multiple_hues_in_every_frame(self):
        bands = ((150, 200), (200, 245), (245, 285), (285, 345), (345, 400))
        for design in primal.ORE_DESIGNS:
            for frame in range(primal.ORE_FRAMES):
                shown = set()
                for (x, y), role in primal.ore_layers(design)[0].items():
                    if role not in 'lb':
                        continue
                    colour = primal.glow_colour(design, x, y, frame, 'spectrum')
                    hue, saturation, _ = colorsys.rgb_to_hsv(*(channel / 255 for channel in colour))
                    hue *= 360
                    hue += 360 if hue < 60 else 0
                    if saturation >= .25:
                        shown |= {i for i, (low, high) in enumerate(bands) if low <= hue < high}
                with self.subTest(design=design, frame=frame):
                    self.assertGreaterEqual(len(shown), 3)

    def test_spectrum_loop_has_no_seam_jump(self):
        for design in primal.ORE_DESIGNS:
            steps = [max(max(abs(a - b) for a, b in zip(
                primal.glow_colour(design, x, y, frame, 'spectrum'),
                primal.glow_colour(design, x, y, (frame + 1) % primal.ORE_FRAMES, 'spectrum')))
                for x, y in primal.ore_layers(design)[0]) for frame in range(primal.ORE_FRAMES)]
            with self.subTest(design=design):
                self.assertLessEqual(max(steps), 255 * .5)
                self.assertLessEqual(steps[-1], max(steps[:-1]))

    def test_aurora_alternative_is_unchanged(self):
        for name in primal.ICONS:
            self.assertEqual(hashlib.sha256(primal.ICON_DRAWINGS[name]('aurora').encode()).hexdigest(), self.AURORA[name])
        strip = png_bytes(16, 16 * primal.ORE_FRAMES, primal.glow_strip('A', 'aurora'))
        self.assertEqual(hashlib.sha256(strip).hexdigest(), self.AURORA['ore_A'])


class StyleSamplingTests(unittest.TestCase):
    AMBER = (227, 169, 75)

    def make_tree(self, directory, texels, faces):
        root = Path(directory)
        assets = root / 'src/main/resources/assets/rematter'
        files = {
            'textures/block/accent.png': style.png_bytes([
                [self.AMBER + (255,) if (x, y) in texels else (43, 47, 53, 255)
                 for x in range(32)] for y in range(32)]),
            'models/block/part.json': {'textures': {'accent': 'rematter:block/accent'},
                                      'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces}]},
            'items/part.json': {'model': {'type': 'minecraft:model', 'model': 'rematter:block/part'}},
        }
        for name, value in files.items():
            path = assets / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(value if isinstance(value, bytes) else json.dumps(value).encode())
        recipe = root / 'src/main/resources/data/rematter/recipe/part.json'
        recipe.parent.mkdir(parents=True, exist_ok=True)
        recipe.write_text(json.dumps({'type': 'minecraft:crafting_shapeless',
                                     'ingredients': ['rematter:amber_matrix_crystal'],
                                     'result': {'id': 'rematter:part'}}))
        return style.Tree(root)

    def test_repeating_the_same_uv_cannot_multiply_an_accent(self):
        with tempfile.TemporaryDirectory() as directory:
            tree = self.make_tree(directory, {(0, 0)}, {face: {'texture': '#accent', 'uv': [0, 0, .5, .5]}
                                                      for face in style.FACES})
            self.assertEqual(tree.shown('part')[0][self.AMBER], 1)
            problems = style.Problems()
            guide = SimpleNamespace(crystals={'amber': {'rgb': self.AMBER}}, primal={}, primal_devices={})
            style.check_recipe_hues(tree, problems, None, guide)
            self.assertEqual(problems.rules(), ['S5 recipe hue'])

    def test_overlapping_uvs_count_unique_texels_and_direct_texture_ids(self):
        with tempfile.TemporaryDirectory() as directory:
            tree = self.make_tree(directory, {(0, 0), (1, 0), (2, 0)}, {
                'north': {'texture': '#accent', 'uv': [0, 0, 1, .5]},
                'south': {'texture': 'rematter:block/accent', 'uv': [.5, 0, 1.5, .5]}})
            self.assertEqual(tree.shown('part')[0][self.AMBER], 3)

    def test_four_distinct_texels_pass_the_recipe_accent_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            tree = self.make_tree(directory, {(0, 0), (1, 0), (0, 1), (1, 1)}, {
                'north': {'texture': '#accent', 'uv': [0, 0, 1, 1]}})
            problems = style.Problems()
            guide = SimpleNamespace(crystals={'amber': {'rgb': self.AMBER}}, primal={}, primal_devices={})
            style.check_recipe_hues(tree, problems, None, guide)
            self.assertEqual(problems.rules(), [])


class IconSourceTests(unittest.TestCase):
    def tree(self, root, content=b'original'):
        path = root / 'src/main/resources/assets/rematter/textures/item/argil.png'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return style.Tree(root), path

    def registry(self, root, path, content=b'original'):
        registry = root / 'artwork/painted-icon-sources.json'
        registry.parent.mkdir(parents=True, exist_ok=True)
        registry.write_text(json.dumps({'sha256': {str(path.relative_to(root)):
                                                  hashlib.sha256(content).hexdigest()}}))

    def test_original_canonical_png_is_accepted_but_a_changed_png_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tree, path = self.tree(root)
            self.registry(root, path)
            problems = style.Problems()
            style.check_icon_sources(tree, problems)
            self.assertEqual(problems.rules(), [])
            path.write_bytes(b'changed')
            style.check_icon_sources(tree, problems)
            self.assertEqual(problems.rules(), ['S3 source'])

    def test_a_reproducible_replacement_requires_retiring_its_canonical_entry(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tree, path = self.tree(root)
            self.registry(root, path)
            (root / 'artwork/argil.svg').write_text('<svg/>')
            problems = style.Problems()
            style.check_icon_sources(tree, problems)
            self.assertEqual(problems.rules(), ['S3 source'])


class RecipeReaderTests(unittest.TestCase):
    def test_unused_shaped_key_is_not_an_ingredient(self):
        with tempfile.TemporaryDirectory() as directory:
            data = Path(directory)
            folder = data / 'recipe'
            folder.mkdir()
            (folder / 'part.json').write_text(json.dumps({
                'type': 'minecraft:crafting_shaped', 'pattern': ['P'],
                'key': {'P': 'minecraft:iron_ingot', 'A': 'rematter:amber_matrix_crystal'},
                'result': {'id': 'rematter:part'}}))
            self.assertEqual(style.Recipes(data).direct('rematter:part'), {})


class SharedPrimalPaletteTests(unittest.TestCase):
    def test_server_safe_item_bar_and_ui_share_the_generator_palette(self):
        from family_palette import PRIMAL_BODY, LED
        root = TOOLS.parent
        source = (root / 'src/main/java/dev/latrox/rematter/primal/PrimalVisualColors.java').read_text()
        generated = {name: int(value, 16) for name, value in re.findall(
            r'int\s+(BODY|FAULT)\s*=\s*0x([0-9a-fA-F]+)', source)}
        ui = (root / 'src/main/java/dev/latrox/rematter/client/UiPalette.java').read_text()
        mirrored = int(re.search(r'int\s+PRIMAL\s*=\s*0x([0-9a-fA-F]+)', ui).group(1), 16) & 0xffffff
        self.assertEqual(generated['BODY'], int.from_bytes(bytes(PRIMAL_BODY), 'big'))
        self.assertEqual(generated['FAULT'], int.from_bytes(bytes(LED['red']), 'big'))
        self.assertEqual(generated['BODY'], mirrored)
        self.assertNotIn('dev.latrox.rematter.client', source)


if __name__ == '__main__':
    unittest.main()
