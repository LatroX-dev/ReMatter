#!/usr/bin/env python3
"""Check the shipped assets against the rules of docs/visual-style.md.

The check reads the resource tree, the palette modules, the Java UI palette and the marked tables of the guide.
It writes nothing. Every finding names the rule (S1 to S6 of the guide), the file and the fix.

    python3 tools/check_visual_style.py            check the repository
    python3 tools/check_visual_style.py --list     also print the texture inventory by asset class
    python3 tools/check_visual_style.py --selftest run the rules against synthetic cases
"""
from pathlib import Path
import argparse
import fnmatch
import json
import re
import struct
import sys
import tempfile
import zlib

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
NAMESPACE = 'rematter'
CRYSTALS = ('amber', 'copper', 'mint', 'leaf', 'ice', 'ember', 'blue', 'violet')
FACES = ('north', 'south', 'east', 'west', 'up', 'down')

# --------------------------------------------------------------------------------------------- asset classes
# First match wins. A texture that matches nothing is reported: every new folder needs a rule here and in the guide.
TEXTURE_CLASSES = (
    ('block/*primal_ore*', 'natural'),   # S2: overlay of a block that world generation places
    ('block/*', 'family'),               # S1: machine-family pixel art
    ('entity/*', 'family'),              # S1: parts a renderer draws in the world
    ('gui/sprites/*', 'gui'),            # S6: frames and icons of the screens
    ('item/*', 'icon'),                  # S3: illustrated item icons
)
FAMILY_SIZES = (16, 32)
NATURAL_SIZE = 16
ICON_SIZE = 128
GUI_MAX = 32
SPRITE_COLOURS = 16     # a pixel-art sprite with more colours than this is a gradient
ICON_MARGIN = 4         # clear pixels on every side of an illustrated icon
ICON_SPAN = 96          # the longer side of the drawing covers at least this many pixels
ICON_SOFT_PIXELS = 64   # an anti-aliased drawing has at least this many partly transparent pixels
OUTLINE_LUMINANCE = 96  # a silhouette pixel darker than this belongs to the dark outline
OUTLINE_SHARE = .85
TONE_SHARE = .2         # a tone keeps at least this share of its identity or status colour
NEAR_DUPLICATE = 16     # RGB distance below which an unlisted colour only imitates a palette colour
ICON_HUE_DISTANCE = 36  # RGB distance between the body of a crystal icon and its hue that still reads as the hue
TEXELS_PER_PIXEL = 2
MAX_ROTATED = 2
# Hardware a device shows mounted: the item is a block-style model (S4). Blocks are block-style anyway.
MOUNTED_HARDWARE = ('cell_housing', 'matter_cell_*', 'primal_cell_*', 'grinding_unit', 'separation_unit',
                    'mixing_basin', 'field_coil', 'reaction_chamber')
# Mounted parts that a renderer draws from sprites: the item model uses at least one of those sprites.
RENDERER_MOUNTED = ('matter_cell_*', 'primal_cell_*')
# The Primal signature spans cyan-blue over ultraviolet to magenta; this is its hue arc in degrees.
PRIMAL_ARC = (185, 335)

# ----------------------------------------------------------------------------------- documented exceptions
# Every entry carries its reason. An entry that no longer applies is reported, so these lists cannot rot.
EXCEPTIONS = {
    # texture -> reason it may keep a size outside its class
    'size': {},
    # texture -> reason it may hold partly transparent pixels
    'translucent': {
        'textures/block/reactor_glass_pane.png': 'the glint of the glass pane is translucent steel',
    },
    # texture -> {colour: reason} for colours outside the palette
    'colour': {
        'textures/block/terminal_screen_active.png': {
            '#102c32': 'glass of the lit screen: the well with mint mixed in, darker than a straight shade of mint'},
        'textures/block/machine_port_unformed.png': {
            '#5c5f63': 'bevel of the neutral frame, computed from SHADE like the bevel of a mode colour',
            '#14161b': 'bevel of the neutral frame, computed from SHADE like the bevel of a mode colour'},
        'textures/block/machine_port_disabled.png': {
            '#55575b': 'bevel of the neutral frame, computed from SEAM like the bevel of a mode colour',
            '#0d0f12': 'bevel of the neutral frame, computed from SEAM like the bevel of a mode colour'},
    },
    # icon -> reason its silhouette has no dark outline
    'outline': {
        'textures/item/matrix_crystal.png':
            'the uncoloured base crystal is clear glass with a soft grey rim; only a crystal with a hue is opaque',
    },
    # device -> reason its recipe holds no Matrix Crystal of its accent
    'key_crystal': {
        'hand_preparator': 'the crystal-free first device wears the accent of the Material Preparator it becomes',
    },
    # Java file -> {literal: reason} for colour literals outside the palette class
    'java_colour': {
        '*': {'0xffffff': 'mask that strips the alpha channel', '0xff000000': 'opaque alpha mask',
              '0xffffffff': 'untinted white vertex colour', '0xf000f0': 'packed full-bright light, not a colour'},
    },
}


class Problems:
    def __init__(self):
        self.items = []
        self.used = set()

    def add(self, rule, where, message, fix):
        self.items.append((rule, str(where), message, fix))

    def excepted(self, kind, key, sub=None):
        """The reason of a documented exception, or None. Marks the entry as used."""
        table = EXCEPTIONS[kind]
        if key in table and (sub is None or sub in table[key]):
            self.used.add((kind, key, sub))
            return table[key] if sub is None else table[key][sub]
        return None

    def rules(self):
        return [item[0] for item in self.items]

    def report(self, out=sys.stdout):
        for rule, where, message, fix in sorted(self.items):
            print(f'[{rule}] {where}: {message}\n    fix: {fix}', file=out)


# ------------------------------------------------------------------------------------------------- PNG files
class Image:
    """One PNG. The header is read at once, the pixels on first use."""
    SIGNATURE = b'\x89PNG\r\n\x1a\n'

    def __init__(self, path):
        self.path = Path(path)
        data = self.path.read_bytes()
        if not data.startswith(self.SIGNATURE):
            raise ValueError('not a PNG file')
        self._chunks = []
        offset = len(self.SIGNATURE)
        while offset + 8 <= len(data):
            length, kind = struct.unpack('>I4s', data[offset:offset + 8])
            self._chunks.append((kind, data[offset + 8:offset + 8 + length]))
            offset += 12 + length
            if kind == b'IEND':
                break
        if not self._chunks or self._chunks[0][0] != b'IHDR':
            raise ValueError('PNG without a header')
        (self.width, self.height, self.depth, self.colour_type, _, _, self.interlace) = struct.unpack(
            '>IIBBBBB', self._chunks[0][1])
        self._pixels = None

    @property
    def rgba8(self):
        return self.colour_type == 6 and self.depth == 8

    def pixels(self):
        """Rows of (r, g, b, a) tuples."""
        if self._pixels is None:
            self._pixels = self._decode()
        return self._pixels

    def flat(self):
        return [pixel for row in self.pixels() for pixel in row]

    def _decode(self):
        if self.interlace:
            raise ValueError('interlaced PNG files are not supported')
        channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[self.colour_type]
        bits = channels * self.depth
        stride = (self.width * bits + 7) // 8
        step = max(1, bits // 8)
        raw = zlib.decompress(b''.join(body for kind, body in self._chunks if kind == b'IDAT'))
        palette = next((body for kind, body in self._chunks if kind == b'PLTE'), b'')
        alpha = next((body for kind, body in self._chunks if kind == b'tRNS'), None)
        rows, previous, position = [], bytearray(stride), 0
        for _ in range(self.height):
            kind = raw[position]
            line = bytearray(raw[position + 1:position + 1 + stride])
            position += 1 + stride
            if kind == 1:
                for i in range(step, stride):
                    line[i] = (line[i] + line[i - step]) & 255
            elif kind == 2:
                for i in range(stride):
                    line[i] = (line[i] + previous[i]) & 255
            elif kind == 3:
                for i in range(stride):
                    left = line[i - step] if i >= step else 0
                    line[i] = (line[i] + ((left + previous[i]) >> 1)) & 255
            elif kind == 4:
                for i in range(stride):
                    a = line[i - step] if i >= step else 0
                    b = previous[i]
                    c = previous[i - step] if i >= step else 0
                    estimate = a + b - c
                    da, db, dc = abs(estimate - a), abs(estimate - b), abs(estimate - c)
                    line[i] = (line[i] + (a if da <= db and da <= dc else b if db <= dc else c)) & 255
            elif kind != 0:
                raise ValueError(f'unknown PNG filter {kind}')
            rows.append(self._row(line, channels, palette, alpha))
            previous = line
        return rows

    def _row(self, line, channels, palette, alpha):
        if self.depth == 16:
            samples = line[0::2]
        elif self.depth == 8:
            samples = line
        else:
            samples = []
            for value in line:
                for shift in range(8 - self.depth, -1, -self.depth):
                    samples.append((value >> shift) & ((1 << self.depth) - 1))
            samples = samples[:self.width * channels]
            if self.colour_type == 0:
                samples = [value * 255 // ((1 << self.depth) - 1) for value in samples]
        kind = self.colour_type
        if kind == 6:
            return list(zip(samples[0::4], samples[1::4], samples[2::4], samples[3::4]))
        if kind == 2:
            key = tuple(alpha[1:6:2]) if alpha else None
            return [(r, g, b, 0 if (r, g, b) == key else 255)
                    for r, g, b in zip(samples[0::3], samples[1::3], samples[2::3])]
        if kind == 4:
            return [(v, v, v, a) for v, a in zip(samples[0::2], samples[1::2])]
        if kind == 0:
            key = alpha[1] if alpha else None
            return [(v, v, v, 0 if v == key else 255) for v in samples]
        return [tuple(palette[3 * index:3 * index + 3]) + ((alpha[index] if alpha and index < len(alpha) else 255),)
                for index in samples]


def png_bytes(rows, filters=None):
    """Encode rows of RGBA tuples; filters optionally names a PNG filter type per row (used by the self-test)."""
    height, width = len(rows), len(rows[0])
    raw, previous = bytearray(), bytearray(width * 4)
    for y, row in enumerate(rows):
        line = bytearray(value for pixel in row for value in pixel)
        kind = filters[y % len(filters)] if filters else 0
        coded = bytearray(len(line))
        for i, value in enumerate(line):
            a = line[i - 4] if i >= 4 else 0
            b = previous[i]
            c = previous[i - 4] if i >= 4 else 0
            if kind == 0:
                estimate = 0
            elif kind == 1:
                estimate = a
            elif kind == 2:
                estimate = b
            elif kind == 3:
                estimate = (a + b) >> 1
            else:
                base = a + b - c
                da, db, dc = abs(base - a), abs(base - b), abs(base - c)
                estimate = a if da <= db and da <= dc else b if db <= dc else c
            coded[i] = (value - estimate) & 255
        raw += bytes([kind]) + coded
        previous = line

    def chunk(kind, body):
        return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body) & 0xffffffff)

    return (Image.SIGNATURE + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b''))


# --------------------------------------------------------------------------------------------------- colours
def hex_rgb(value):
    return tuple(bytes.fromhex(value.lstrip('#')))


def rgb_hex(colour):
    return '#' + bytes(colour[:3]).hex()


def is_rgb(value):
    return (isinstance(value, (tuple, list)) and len(value) in (3, 4)
            and all(isinstance(channel, int) and 0 <= channel <= 255 for channel in value))


def luminance(colour):
    return .2126 * colour[0] + .7152 * colour[1] + .0722 * colour[2]


def hue_of(colour):
    """Hue in degrees, saturation and value of an RGB colour."""
    high, low = max(colour[:3]), min(colour[:3])
    spread = high - low
    if spread == 0:
        return 0.0, 0.0, high / 255
    r, g, b = colour[:3]
    if high == r:
        hue = ((g - b) / spread) % 6
    elif high == g:
        hue = (b - r) / spread + 2
    else:
        hue = (r - g) / spread + 4
    return hue * 60, spread / high, high / 255


def _window(windows):
    low = max(window[0] for window in windows)
    high = min(window[1] for window in windows)
    return low <= high + 1e-9 and high >= -1e-9 and low <= 1 + 1e-9


def on_ramp(colour, base):
    """Whether colour is base darkened toward black or lightened toward white, as the generators round it."""
    shade, tint = [], []
    for value, channel in zip(colour[:3], base[:3]):
        if channel == 0:
            shade.append((0, 1) if value == 0 else (2, -1))
        else:
            shade.append(((value - .5) / channel, (value + .5) / channel))
        if channel == 255:
            tint.append((0, 1) if value == 255 else (2, -1))
        else:
            tint.append(((value - .5 - channel) / (255 - channel), (value + .5 - channel) / (255 - channel)))
    return _window(shade) or _window(tint)


def on_tone(colour, base):
    """Whether colour is base mixed with black and white (shaded, then tinted) and still a fifth of base."""
    high, low = max(base[:3]), min(base[:3])
    if high == low:
        return False
    # Least squares for colour = share * base + white * 255.
    n, sx, sy = 3, sum(base[:3]), sum(colour[:3])
    sxx, sxy = sum(channel * channel for channel in base[:3]), sum(a * b for a, b in zip(base, colour))
    share = (n * sxy - sx * sy) / (n * sxx - sx * sx)
    white = (sy - share * sx) / n / 255
    if share < TONE_SHARE or white < -.01 or share + white > 1.01:
        return False
    return all(abs(value - share * channel - 255 * white) <= 1.5 for value, channel in zip(colour, base))


class Palette:
    """The colours every sprite may use, taken from the palette modules of the generators."""

    def __init__(self, crystals, family, named):
        self.crystals = dict(crystals)          # crystal name -> rgb
        self.family = dict(family)              # device id -> crystal name
        self.named = dict(named)                # constant -> rgb
        self.primal = {name: colour for name, colour in self.named.items() if name.upper().startswith('PRIMAL')}
        self.exact = set(self.named.values()) | set(self.crystals.values())
        # Identity and status colours may be shaded and tinted; the neutral ramps are used as they are.
        self.chromatic = {colour for colour in self.exact if max(colour) - min(colour) >= 40}
        self.chromatic |= set(self.crystals.values()) | set(self.primal.values())
        # The colours that carry meaning by themselves; an unlisted colour close to one of them is a copy gone astray.
        self.identity = set(self.crystals.values()) | set(self.primal.values()) | {
            colour for name, colour in self.named.items() if name.startswith('LED')}

    @classmethod
    def load(cls, tools):
        for name in ('family_palette', 'machine_palette', 'material_tiles'):
            sys.modules.pop(name, None)
        sys.path.insert(0, str(tools))
        try:
            import family_palette
            import machine_palette
        finally:
            sys.path.remove(str(tools))
        named = {}

        def collect(prefix, value):
            if isinstance(value, str) and re.fullmatch(r'#[0-9a-fA-F]{6}', value):
                named[prefix] = hex_rgb(value)
            elif is_rgb(value):
                named[prefix] = tuple(value[:3])
            elif isinstance(value, dict):
                for key, inner in value.items():
                    collect(f'{prefix}.{key}', inner)

        for name, value in vars(family_palette).items():
            if not name.startswith('_') and name != 'RGB':
                collect(name, value)
        return cls({name: tuple(colour) for name, colour in machine_palette.RGB.items()}, machine_palette.FAMILY, named)

    def verdict(self, colour, extra=()):
        """None when a sprite may use the colour, otherwise what is wrong with it."""
        colour = tuple(colour[:3])
        if colour in self.exact or colour in extra:
            return None
        for base in self.identity:
            if sum((a - b) ** 2 for a, b in zip(colour, base)) < NEAR_DUPLICATE ** 2:
                return 'imitates ' + self.nearest(base)
        if any(on_ramp(colour, base) or on_tone(colour, base) for base in self.chromatic):
            return None
        return 'nearest ' + self.nearest(colour)

    def allows(self, colour, extra=()):
        return self.verdict(colour, extra) is None

    def nearest(self, colour):
        names = dict(self.named)
        names.update({f'RGB.{name}': value for name, value in self.crystals.items()})
        name = min(names, key=lambda key: sum((a - b) ** 2 for a, b in zip(names[key], colour)))
        return f'{name} {rgb_hex(names[name])}'


# ----------------------------------------------------------------------------------------- guide and Java
MARK = re.compile(r'<!--\s*check:([a-z-]+)\s*-->')
HEX = re.compile(r'#([0-9a-fA-F]{6})\b')
IDS = re.compile(r'`([a-z0-9_]+)`')
IDS_ANY = re.compile(r'`([A-Za-z0-9_]+)`')


def guide_tables(text):
    """Tables of the guide that follow a <!-- check:name --> mark, as header cells and rows of cells."""
    tables, lines = {}, text.splitlines()
    for index, line in enumerate(lines):
        mark = MARK.search(line)
        if not mark:
            continue
        rows = []
        for following in lines[index + 1:]:
            if following.strip().startswith('|'):
                rows.append([cell.strip() for cell in following.strip().strip('|').split('|')])
            elif rows or (following.strip() and not following.strip().startswith('<!--')):
                break
        if len(rows) >= 2:
            tables[mark.group(1)] = (rows[0], [row for row in rows[2:] if len(row) == len(rows[0])])
    return tables


def column(header, word):
    for index, cell in enumerate(header):
        if word in cell.lower():
            return index
    return None


class Guide:
    def __init__(self, text):
        self.tables = guide_tables(text)
        self.crystals, self.constants, self.primal, self.primal_devices = {}, {}, {}, []
        self.errors = []
        header, rows = self.tables.get('crystal-hues', ([], []))
        hue, machines, modules = column(header, 'hex'), column(header, 'machine'), column(header, 'module')
        for row in rows:
            name, found = row[0].strip('* `').lower(), HEX.search(row[hue]) if hue is not None else None
            if name in CRYSTALS and found:
                self.crystals[name] = {'rgb': hex_rgb(found.group(1)),
                                       'machines': IDS.findall(row[machines]) if machines is not None else [],
                                       'modules': IDS.findall(row[modules]) if modules is not None else []}
        for mark, (header, rows) in self.tables.items():
            constant, hue = column(header, 'constant'), column(header, 'hex')
            for row in rows:
                name = re.search(r'`([^`]+)`', row[constant]) if constant is not None else None
                found = HEX.search(row[hue]) if hue is not None else None
                if name and found:
                    self.constants[re.sub(r'''\[['"]([^'"]+)['"]\]''', r'.\1', name.group(1))] = hex_rgb(found.group(1))
        header, rows = self.tables.get('gui-surfaces', ([], []))
        screen, source = column(header, 'uipalette'), column(header, 'palette constant')
        self.surfaces = {}
        for row in rows if screen is not None and source is not None else ():
            names = IDS_ANY.findall(row[screen]), IDS_ANY.findall(row[source])
            if names[0] and names[1]:
                self.surfaces[names[0][0]] = names[1][0]
        header, rows = self.tables.get('primal-signature', ([], []))
        hue, devices = column(header, 'hex'), column(header, 'device')
        for row in rows:
            for found in HEX.finditer(row[hue]) if hue is not None else ():
                self.primal[row[0].strip('* `').lower()] = hex_rgb(found.group(1))
            if devices is not None:
                self.primal_devices += IDS.findall(row[devices])
        for mark in ('crystal-hues', 'neutral-palette', 'status-colours', 'primal-signature', 'gui-surfaces'):
            if mark not in self.tables:
                self.errors.append(f'no table follows the mark <!-- check:{mark} -->')
        missing = [name for name in CRYSTALS if name not in self.crystals]
        if 'crystal-hues' in self.tables and missing:
            self.errors.append('the crystal table lacks ' + ', '.join(missing))


class JavaPalette:
    CONSTANT = re.compile(r'\bint\s+(\w+)\s*=\s*0x([0-9a-fA-F]{8})\b')
    CASE = re.compile(r'case\s+([A-Z0-9_,\s]+?)\s*->\s*(0x[0-9a-fA-F]{8}|[A-Z][A-Z0-9_]*)\s*;')
    KIND = re.compile(r'^\s*([A-Z][A-Z0-9_]*)\("([a-z0-9_]+)"\)', re.M)

    def __init__(self, palette_source, kinds_source):
        self.constants = {name: hex_rgb(value[2:]) for name, value in self.CONSTANT.findall(palette_source)}
        self.accents = {}
        for names, value in self.CASE.findall(palette_source):
            colour = hex_rgb(value[4:]) if value.startswith('0x') else self.constants.get(value)
            for name in names.replace(' ', '').replace('\n', '').split(','):
                if name:
                    self.accents[name] = colour
        self.kinds = dict(self.KIND.findall(kinds_source))   # enum constant -> registry id

    def accent(self, device):
        for kind, registered in self.kinds.items():
            if registered == device:
                return self.accents.get(kind)
        return None


# ----------------------------------------------------------------------------------------------- the tree
class Tree:
    """Everything the rules read. The self-test builds small trees of its own."""

    def __init__(self, root):
        self.root = Path(root)
        self.assets = self.root / 'src/main/resources/assets' / NAMESPACE
        self.data = self.root / 'src/main/resources/data' / NAMESPACE
        self.java = self.root / 'src/main/java'
        self.guide = self.root / 'docs/visual-style.md'
        self.tools = self.root / 'tools'
        self.textures, self.models, self.items, self.blockstates, self.broken = {}, {}, {}, {}, []
        for path in sorted((self.assets / 'textures').rglob('*.png')):
            self.textures[path.relative_to(self.assets / 'textures').with_suffix('').as_posix()] = path
        for folder, table in (('models', self.models), ('items', self.items), ('blockstates', self.blockstates)):
            for path in sorted((self.assets / folder).rglob('*.json')):
                try:
                    table[path.relative_to(self.assets / folder).with_suffix('').as_posix()] = json.loads(
                        path.read_text())
                except ValueError as error:
                    self.broken.append((path, str(error)))
        self._images, self._flat = {}, {}
        self.literals = {}
        for path in sorted(self.java.rglob('*.java')) if self.java.is_dir() else ():
            self.literals[path] = set(re.findall(r'"((?:[^"\\\n]|\\.)*)"', path.read_text()))
        self.all_literals = set().union(*self.literals.values()) if self.literals else set()

    def rel(self, path):
        return Path(path).relative_to(self.root).as_posix()

    def texture_path(self, name):
        return 'textures/' + name + '.png'

    def image(self, name):
        if name not in self._images:
            self._images[name] = Image(self.textures[name])
        return self._images[name]

    def animation(self, name):
        """Parsed animation section of the texture's metadata, None without one, or an error text."""
        meta = Path(str(self.textures[name]) + '.mcmeta')
        if not meta.is_file():
            return None
        try:
            value = json.loads(meta.read_text())
        except ValueError as error:
            return f'not valid JSON ({error})'
        return value.get('animation') if isinstance(value, dict) else 'not a JSON object'

    def frame(self, name):
        """Frame size of a texture: an animated strip stacks frames of the sprite's width."""
        image = self.image(name)
        animation = self.animation(name)
        if isinstance(animation, dict):
            return animation.get('width', image.width), animation.get('height', image.width)
        if image.height > image.width and image.height % image.width == 0:
            return image.width, image.width   # a strip; the animation rule reports its missing metadata
        return image.width, image.height

    def is_flat(self, name):
        if name not in self._flat:
            self._flat[name] = len(set(self.image(name).flat())) == 1
        return self._flat[name]

    def texture_class(self, name):
        for pattern, kind in TEXTURE_CLASSES:
            if fnmatch.fnmatchcase(name, pattern):
                return kind
        return None

    # ---- models
    def chain(self, name):
        """The model and its ancestors inside the mod, nearest first, and the first parent outside it."""
        found, seen = [], set()
        while name is not None and name not in seen:
            seen.add(name)
            model = self.models.get(name)
            if model is None:
                return found, 'missing:' + name
            found.append((name, model))
            parent = model.get('parent')
            if parent is None:
                return found, None
            namespace, _, path = parent.rpartition(':')
            if (namespace or 'minecraft') != NAMESPACE:
                return found, (namespace or 'minecraft') + ':' + path
            name = path
        return found, None

    def model_textures(self, name):
        """Texture variables of a model with its parents applied, each resolved to its final reference."""
        merged = {}
        for _, model in reversed(self.chain(name)[0]):
            merged.update(model.get('textures', {}))
        resolved = {}
        for key, value in merged.items():
            for _ in range(8):
                if isinstance(value, str) and value.startswith('#') and value[1:] in merged:
                    value = merged[value[1:]]
            resolved[key] = value
        return resolved

    def elements(self, name):
        for _, model in self.chain(name)[0]:
            if 'elements' in model:
                return model['elements']
        return None

    def own_texture(self, reference):
        """Texture name of a reference into this mod, or None for another namespace or a variable."""
        if not isinstance(reference, str) or reference.startswith('#'):
            return None
        namespace, _, path = reference.rpartition(':')
        return path if (namespace or 'minecraft') == NAMESPACE else None

    @staticmethod
    def model_references(value):
        """Every model id below a block state or item definition."""
        if isinstance(value, dict):
            for key, inner in value.items():
                if key == 'model' and isinstance(inner, str):
                    yield inner
                else:
                    yield from Tree.model_references(inner)
        elif isinstance(value, list):
            for inner in value:
                yield from Tree.model_references(inner)

    def own_model(self, reference):
        namespace, _, path = reference.rpartition(':')
        return path if (namespace or 'minecraft') == NAMESPACE else None

    def in_code(self, name):
        """Whether Java names this sprite: as a whole literal, as a path, or put together from two literals."""
        short = name[len('gui/sprites/'):] if name.startswith('gui/sprites/') else name.split('/', 1)[-1]
        wanted = {name, short, short + '.png', f'{NAMESPACE}:{short}', f'{NAMESPACE}:{name}', f'textures/{name}.png'}
        if not name.startswith('gui/sprites/') and ('gui/sprites/' + short in self.textures or short in self.items
                                                    or short in self.blockstates):
            # The bare name means the GUI sprite or the registered block or item, not this texture.
            wanted -= {short, short + '.png', f'{NAMESPACE}:{short}'}
        if self.all_literals & wanted:
            return True
        for literals in self.literals.values():
            for cut in range(3, len(short)):
                head, tail = short[:cut], short[cut:]
                if (tail in literals or tail + '.png' in literals) and any(
                        literal.endswith(head) and (len(literal) == len(head) or literal[-len(head) - 1] == '/')
                        for literal in literals):
                    return True
        return False


# ------------------------------------------------------------------------------------------------- rules
def check_broken(tree, problems):
    for path, error in tree.broken:
        problems.add('refs', tree.rel(path), f'not valid JSON ({error})', 'regenerate the file with its generator')


def distinct(image):
    colours = {}
    for pixel in image.flat():
        colours[pixel] = colours.get(pixel, 0) + 1
    return colours


def check_pixel_art(tree, problems, palette, name, rule, extra=(), ink=False):
    """Shared colour rules of family and GUI sprites."""
    where = tree.texture_path(name)
    colours = distinct(tree.image(name))
    visible = {pixel: count for pixel, count in colours.items() if pixel[3] > 0}
    soft = sum(count for pixel, count in visible.items() if pixel[3] < 255
               and not (ink and pixel[:3] == (255, 255, 255)))
    if soft and not problems.excepted('translucent', where):
        problems.add(rule + ' alpha', where, f'{soft} partly transparent pixels in a pixel-art sprite',
                     'use alpha 0 or 255 only' + ('; only white ink may carry a second alpha level' if ink else ''))
    if len({pixel[:3] for pixel in visible}) > SPRITE_COLOURS:
        problems.add(rule + ' gradient', where, f'{len({pixel[:3] for pixel in visible})} colours; a sprite is hard pixel clusters, not a gradient',
                     f'draw it with at most {SPRITE_COLOURS} palette colours')
        return
    wrong = {}
    for pixel, count in visible.items():
        if ink and pixel[:3] == (255, 255, 255):
            continue
        if not palette.allows(pixel, extra) and not problems.excepted('colour', where, rgb_hex(pixel)):
            wrong[pixel[:3]] = wrong.get(pixel[:3], 0) + count
    if wrong:
        total = sum(wrong.values())
        shown = ', '.join(f'{rgb_hex(colour)} x{count} ({palette.verdict(colour, extra)})'
                          for colour, count in sorted(wrong.items(), key=lambda item: -item[1])[:4])
        problems.add(rule + ' palette', where, f'{total} pixels in {len(wrong)} colours outside the palette: {shown}',
                     'take the colour from tools/family_palette.py (a neutral as it is, an identity or status '
                     'colour also mixed with black or white) and regenerate')


def check_textures(tree, problems, palette, gui_colours=()):
    for name in tree.textures:
        where = tree.texture_path(name)
        kind = tree.texture_class(name)
        try:
            image = tree.image(name)
        except (ValueError, zlib.error, struct.error) as error:
            problems.add('refs', where, f'unreadable PNG ({error})', 'write the file again with its generator')
            continue
        if kind is None:
            problems.add('class', where, 'texture outside every asset class',
                         'move it into block/, entity/, gui/sprites/ or item/, or add its class to the guide and '
                         'to TEXTURE_CLASSES')
            continue
        width, height = tree.frame(name)
        size = f'{image.width}x{image.height}'
        if kind == 'family':
            if width not in FAMILY_SIZES or height != width:
                if not problems.excepted('size', where):
                    problems.add('S1 size', where, f'{size}; family sprites are 32x32 for a block face and 16x16 for '
                                 'small parts (2 texels per model pixel)',
                                 'redraw it as a family sprite in the generator, or delete it when nothing shows it')
                continue
            check_pixel_art(tree, problems, palette, name, 'S1')
        elif kind == 'natural':
            if width != NATURAL_SIZE or height != NATURAL_SIZE:
                problems.add('S2 size', where, f'{size}; an overlay of a world-generated block uses the 16 px density '
                             'of its vanilla host', 'draw the overlay 16 px wide, one 16 px frame per animation step')
                continue
            check_natural(tree, problems, palette, name)
        elif kind == 'gui':
            if image.width > GUI_MAX or image.height > GUI_MAX:
                problems.add('S6 size', where, f'{size}; GUI sprites are pixel art at GUI scale, at most {GUI_MAX} px',
                             'draw icons 16x16 and frames as nine-slice sprites')
                continue
            check_pixel_art(tree, problems, palette, name, 'S6', extra=gui_colours, ink=True)
        elif kind == 'icon':
            check_icon(tree, problems, name)


def check_natural(tree, problems, palette, name):
    """S2: whatever glows on a world-generated block wears the Primal signature, never the hue of a crystal."""
    where = tree.texture_path(name)
    outside = 0
    for pixel in tree.image(name).flat():
        hue, saturation, value = hue_of(pixel)
        if pixel[3] and saturation >= .25 and value >= .5 and not PRIMAL_ARC[0] <= hue <= PRIMAL_ARC[1]:
            outside += 1
    if outside:
        problems.add('S2 palette', where, f'{outside} bright pixels outside the Primal signature (cyan-blue over '
                     'ultraviolet to magenta, white core)', 'draw only the socket shadow and the crystal: the host '
                     'stone comes from the vanilla texture, the crystal from the PRIMAL colours of the palette')


def check_icon(tree, problems, name):
    where = tree.texture_path(name)
    image = tree.image(name)
    if (image.width, image.height) != (ICON_SIZE, ICON_SIZE) or not image.rgba8:
        small = ' (a 16 px sprite does not sit beside the illustrated icons)' if image.width <= 32 else ''
        problems.add('S3 size', where, f'{image.width}x{image.height}, colour type {image.colour_type}, '
                     f'{image.depth} bit; item icons are {ICON_SIZE}x{ICON_SIZE} 8-bit RGBA' + small,
                     'draw an illustrated icon (SVG under artwork/ rendered by its tool, or a deterministic painter)')
        return
    rows = image.pixels()
    if all(rows[y][x] == rows[y - y % 2][x - x % 2] for y in range(ICON_SIZE) for x in range(ICON_SIZE)):
        problems.add('S3 illustrated', where, 'the image is an enlarged low-resolution sprite',
                     'draw the icon at 128 px with an outline and facet shading')
        return
    solid = [[pixel[3] >= 128 for pixel in row] for row in rows]
    xs = [x for row in solid for x, inside in enumerate(row) if inside]
    ys = [y for y, row in enumerate(solid) if any(row)]
    if not xs:
        problems.add('S3 illustrated', where, 'the icon is empty', 'draw the icon')
        return
    if any(rows[y][x][3] for y in (0, ICON_SIZE - 1) for x in (0, ICON_SIZE - 1)):
        problems.add('S3 background', where, 'the corners are not transparent', 'keep the background transparent')
    margin = min(min(xs), min(ys), ICON_SIZE - 1 - max(xs), ICON_SIZE - 1 - max(ys))
    span = max(max(xs) - min(xs), max(ys) - min(ys)) + 1
    if margin < ICON_MARGIN or span < ICON_SPAN:
        problems.add('S3 canvas', where, f'the drawing spans {span} px and leaves {margin} px at its tightest side',
                     f'let the longer side cover at least {ICON_SPAN} px and keep {ICON_MARGIN} px clear all round')
    soft = sum(1 for row in rows for pixel in row if 0 < pixel[3] < 255)
    if soft < ICON_SOFT_PIXELS:
        problems.add('S3 illustrated', where, f'only {soft} partly transparent pixels: the edge is not anti-aliased',
                     'render the icon with anti-aliasing')
    edge = [rows[y][x] for y in range(ICON_SIZE) for x in range(ICON_SIZE) if solid[y][x] and not (
        0 < y < ICON_SIZE - 1 and 0 < x < ICON_SIZE - 1
        and solid[y - 1][x] and solid[y + 1][x] and solid[y][x - 1] and solid[y][x + 1])]
    dark = sum(1 for pixel in edge if luminance(pixel) < OUTLINE_LUMINANCE) / len(edge)
    if dark < OUTLINE_SHARE and not problems.excepted('outline', where):
        problems.add('S3 outline', where, f'{dark:.0%} of the silhouette edge is dark; the other icons carry a '
                     'closed dark outline', 'outline the drawing in a dark shade of its own colour')


def check_animations(tree, problems):
    for meta in sorted((tree.assets / 'textures').rglob('*.mcmeta')):
        where = tree.rel(meta).split(f'assets/{NAMESPACE}/', 1)[-1]
        texture = meta.with_name(meta.name[:-len('.mcmeta')])
        if not texture.is_file():
            problems.add('anim', where, 'metadata without its texture', 'delete it, or write the texture')
            continue
        try:
            value = json.loads(meta.read_text())
        except ValueError as error:
            problems.add('anim', where, f'not valid JSON ({error})', 'regenerate the metadata')
            continue
        if not isinstance(value, dict) or not set(value) & {'animation', 'gui', 'texture'}:
            problems.add('anim', where, 'no animation, gui or texture section', 'regenerate the metadata')
            continue
        image = Image(texture)
        animation = value.get('animation')
        if animation is not None:
            if not isinstance(animation, dict):
                problems.add('anim', where, 'the animation section is not an object', 'regenerate the metadata')
                continue
            width, height = animation.get('width', image.width), animation.get('height', image.width)
            ints = all(isinstance(number, int) and not isinstance(number, bool) and number > 0
                       for number in (width, height, animation.get('frametime', 1)))
            if not ints:
                problems.add('anim', where, 'width, height and frametime must be positive whole numbers',
                             'regenerate the metadata')
                continue
            if image.width % width or image.height % height:
                problems.add('anim', where, f'a {image.width}x{image.height} strip does not divide into '
                             f'{width}x{height} frames', 'stack whole frames of the sprite width, top to bottom')
                continue
            count = (image.width // width) * (image.height // height)
            if count < 2:
                problems.add('anim', where, 'an animation with a single frame', 'add frames, or drop the metadata')
            for entry in animation.get('frames', []):
                index = entry.get('index') if isinstance(entry, dict) else entry
                time = entry.get('time', 1) if isinstance(entry, dict) else 1
                if not isinstance(index, int) or not 0 <= index < count or not isinstance(time, int) or time < 1:
                    problems.add('anim', where, f'frame entry {entry!r} does not fit the {count} frames of the strip',
                                 'list frame indices below the frame count, with positive times')
                    break
        elif image.height != image.width and 'gui' not in value:
            problems.add('anim', where, f'{image.width}x{image.height} texture without an animation section',
                         'add the animation, or make the sprite square')
        scaling = value.get('gui', {}).get('scaling') if isinstance(value.get('gui'), dict) else None
        if 'gui' in value:
            if not isinstance(scaling, dict) or scaling.get('type') not in ('stretch', 'tile', 'nine_slice'):
                problems.add('anim', where, 'gui metadata without a valid scaling type', 'regenerate the metadata')
            elif scaling['type'] != 'stretch':
                border = scaling.get('border', 0)
                edges = list(border.values()) if isinstance(border, dict) else [border]
                if (scaling.get('width'), scaling.get('height')) != (image.width, image.height):
                    problems.add('anim', where, f'scaling size {scaling.get("width")}x{scaling.get("height")} is not '
                                 f'the sprite size {image.width}x{image.height}', 'regenerate the metadata')
                elif scaling['type'] == 'nine_slice' and not all(
                        isinstance(edge, int) and 0 <= edge * 2 < min(image.width, image.height) for edge in edges):
                    problems.add('anim', where, 'a nine-slice border leaves no centre', 'regenerate the metadata')
    for name in tree.textures:
        image = tree.image(name)
        if image.height != image.width and tree.animation(name) is None and tree.texture_class(name) != 'gui':
            problems.add('anim', tree.texture_path(name), f'{image.width}x{image.height} strip without animation '
                         'metadata', 'write the .png.mcmeta beside it, or make the sprite square')


def check_references(tree, problems):
    used_textures, used_models = {}, {}
    for kind, table in (('items', tree.items), ('blockstates', tree.blockstates)):
        for name, value in table.items():
            for reference in tree.model_references(value):
                model = tree.own_model(reference)
                if model is None:
                    continue
                used_models.setdefault(model, []).append(f'{kind}/{name}.json')
                if model not in tree.models:
                    problems.add('refs', f'{kind}/{name}.json', f'model {reference} does not exist',
                                 'write the model with its generator, or correct the reference')
    # Other resource files may name models as well, the guide book for example.
    for path in sorted(tree.assets.rglob('*.json')):
        if not {'models', 'items', 'blockstates', 'textures', 'lang'} & set(path.relative_to(tree.assets).parts[:1]):
            for model in re.findall(rf'"{NAMESPACE}:((?:block|item)/[a-z0-9_/]+)"', path.read_text()):
                used_models.setdefault(model, []).append(path.relative_to(tree.assets).as_posix())
    direct = set(used_models)
    for name, model in tree.models.items():
        parent = model.get('parent')
        if isinstance(parent, str) and tree.own_model(parent) is not None:
            used_models.setdefault(tree.own_model(parent), []).append(f'models/{name}.json')
            if tree.own_model(parent) not in tree.models:
                problems.add('refs', f'models/{name}.json', f'parent {parent} does not exist',
                             'write the parent model, or correct the reference')
        textures = tree.model_textures(name)
        for key, reference in model.get('textures', {}).items():
            texture = tree.own_texture(reference)
            if texture is not None:
                used_textures.setdefault(texture, []).append(f'models/{name}.json')
                if texture not in tree.textures:
                    problems.add('refs', f'models/{name}.json', f'texture {reference} ({key}) does not exist',
                                 'draw the texture with its generator, or correct the reference')
        if name in direct:   # a parent that only children use may leave its variables to them
            missing = sorted({face.get('texture', '') for element in tree.elements(name) or []
                              for face in element.get('faces', {}).values()
                              if face.get('texture', '').startswith('#') and face['texture'][1:] not in textures})
            if missing:
                problems.add('refs', f'models/{name}.json', f'face textures {", ".join(missing)} are not defined',
                             'define the texture variables in the model or its parent')
    for path, literals in tree.literals.items():
        for literal in sorted(literals):
            if re.fullmatch(r'textures/[a-z0-9_/]+\.png', literal) and literal[len('textures/'):-len('.png')] not in tree.textures:
                problems.add('refs', tree.rel(path), f'the source names {literal}, which does not exist',
                             'keep the texture, or point the renderer at the sprite that replaced it')
    reachable, queue = set(), [name for name in direct if name in tree.models]
    while queue:
        name = queue.pop()
        if name in reachable or name not in tree.models:
            continue
        reachable.add(name)
        parent = tree.models[name].get('parent')
        if isinstance(parent, str) and tree.own_model(parent):
            queue.append(tree.own_model(parent))
    for name in tree.models:
        if name not in reachable:
            problems.add('refs unused', f'models/{name}.json', 'no item, block state, model or source file uses this '
                         'model', 'delete it in its generator, or reference it')
    for name in tree.textures:
        users = [user for user in used_textures.get(name, []) if user[len('models/'):-len('.json')] in reachable]
        if not users and not tree.in_code(name):
            problems.add('refs unused', tree.texture_path(name), 'no reachable model and no source file uses this '
                         'texture', 'delete it in its generator, or reference it')
    return used_textures, reachable


def close(value, target=None):
    target = round(value) if target is None else target
    return abs(value - target) < 1e-6


def check_geometry(tree, problems, reachable):
    placed = {tree.own_model(reference) for value in tree.blockstates.values()
              for reference in tree.model_references(value)}
    for name, model in tree.models.items():
        elements = model.get('elements')
        if not elements or name not in reachable:
            continue
        textures = tree.model_textures(name)
        where = f'models/{name}.json'
        classes = {tree.texture_class(tree.own_texture(value)) for value in textures.values() if tree.own_texture(value)}
        if not classes & {'family', 'natural'}:
            continue
        off_grid = slivers = uncalled = 0
        rotated = sum(1 for element in elements if 'rotation' in element)
        planes = {}
        dense = []
        for element in elements:
            low, high = element.get('from', [0, 0, 0]), element.get('to', [16, 16, 16])
            size = [b - a for a, b in zip(low, high)]
            off_grid += any(not close(value * 2) for value in low + high)
            slivers += min(size) < .5 - 1e-6 or sum(1 for edge in size if edge < 1 - 1e-6) > 1
            for face, entry in element.get('faces', {}).items():
                axis = {'north': 2, 'south': 2, 'west': 0, 'east': 0, 'down': 1, 'up': 1}[face]
                at = low[axis] if face in ('north', 'west', 'down') else high[axis]
                rest = [index for index in range(3) if index != axis]
                box = (low[rest[0]], low[rest[1]], high[rest[0]], high[rest[1]])
                if name in placed and close(at, 0 if face in ('north', 'west', 'down') else 16) \
                        and entry.get('cullface') != face:
                    uncalled += 1
                texture = tree.own_texture(textures.get(entry.get('texture', '')[1:]))
                kind = tree.texture_class(texture) if texture in tree.textures else None
                if 'rotation' not in element:
                    planes.setdefault((face, round(at * 2)), []).append((box, kind, element.get('name', '?')))
                if kind != 'family' or tree.frame(texture)[0] not in FAMILY_SIZES:
                    continue
                if tree.is_flat(texture):
                    continue   # a flat colour has no texels to stretch
                frame = tree.frame(texture)[0]
                wide, tall = ((size[0], size[2]) if axis == 1 else (size[2 if axis == 0 else 0], size[1]))
                if 'uv' in entry:
                    u, v = abs(entry['uv'][2] - entry['uv'][0]) * frame / 16, abs(entry['uv'][3] - entry['uv'][1]) * frame / 16
                    if entry.get('rotation', 0) in (90, 270):
                        u, v = v, u
                else:
                    u, v = wide * frame / 16, tall * frame / 16
                if wide > 1e-6 and tall > 1e-6 and not (close(u / wide, TEXELS_PER_PIXEL) and close(v / tall, TEXELS_PER_PIXEL)):
                    dense.append(f'{element.get("name", "?")}.{face} {u / wide:.2f}x{v / tall:.2f}')
        if off_grid:
            problems.add('S1 grid', where, f'{off_grid} of {len(elements)} boxes leave the half-pixel grid',
                         'build the model in its generator from boxes on multiples of 0.5')
        if slivers:
            problems.add('S1 detail', where, f'{slivers} boxes are thinner than a feature may be (two sides below '
                         '1 px, or one below 0.5 px)', 'put small detail into the sprite; 0.5 px is only the depth '
                         'of a step')
        if rotated > MAX_ROTATED:
            problems.add('S1 rotation', where, f'{rotated} rotated boxes; a showpiece may hold {MAX_ROTATED}',
                         'build the shape from axis-aligned boxes')
        if uncalled:
            problems.add('S1 cullface', where, f'{uncalled} faces on the block boundary carry no cullface',
                         'let the generator set the cullface of every outer face')
        if dense:
            problems.add('S1 density', where, f'{len(dense)} faces are not mapped at {TEXELS_PER_PIXEL} texels per '
                         f'pixel, for example {", ".join(dense[:3])}', 'map every sprite at two texels per model pixel')
        clashes = []
        for (face, _), faces in planes.items():
            for first in range(len(faces)):
                for second in range(first + 1, len(faces)):
                    (a, kind_a, name_a), (b, kind_b, name_b) = faces[first], faces[second]
                    if min(a[2], b[2]) - max(a[0], b[0]) > 1e-6 and min(a[3], b[3]) - max(a[1], b[1]) > 1e-6:
                        if a == b and 'natural' in (kind_a, kind_b):
                            continue   # an overlay drawn on the very face of its host
                        clashes.append(f'{name_a}/{name_b} {face}')
        if clashes:
            problems.add('S1 z-fighting', where, f'{len(clashes)} pairs of faces share a plane and overlap, for example '
                         f'{", ".join(clashes[:3])}', 'write only the visible face, or step one box by 0.5 px')


def matches(name, patterns):
    return any(fnmatch.fnmatchcase(name, pattern) for pattern in patterns)


def check_items(tree, problems):
    for name, definition in tree.items.items():
        where = f'items/{name}.json'
        wanted = 'block' if name in tree.blockstates or matches(name, MOUNTED_HARDWARE) else 'icon'
        kinds, textures = set(), set()
        for reference in tree.model_references(definition):
            model = tree.own_model(reference)
            if model is None or model not in tree.models:
                continue
            chain, outside = tree.chain(model)
            resolved = tree.model_textures(model)
            textures |= {tree.own_texture(value) for value in resolved.values()} - {None}
            if any('elements' in entry for _, entry in chain) or (outside or '').startswith('minecraft:block/'):
                kinds.add('block')
            elif outside in ('minecraft:item/generated', 'minecraft:item/handheld'):
                layer = tree.own_texture(resolved.get('layer0'))
                kinds.add('icon' if layer and tree.texture_class(layer) == 'icon' else 'sprite')
            else:
                kinds.add('other')
        if not kinds:
            problems.add('S3 item', where, 'the item shows no model of this mod',
                         'give it an illustrated icon or a block-style model')
        elif wanted == 'icon' and kinds != {'icon'}:
            problems.add('S3 item', where, 'a material, component or tool item without an illustrated icon',
                         f'write models/item/{name}.json with parent minecraft:item/generated and layer0 '
                         f'{NAMESPACE}:item/{name}, a 128 px illustrated icon')
        elif wanted == 'block' and kinds != {'block'}:
            what = 'a block' if name in tree.blockstates else 'hardware that a device shows mounted'
            problems.add('S4 item', where, f'{what} whose item is not a block-style model',
                         'build the item from the same boxes and sprites as the part in the world')
        if wanted == 'block' and kinds == {'block'}:
            legacy = sorted(texture for texture in textures if texture in tree.textures
                            and tree.texture_class(texture) == 'family'
                            and tree.frame(texture)[0] not in FAMILY_SIZES)
            if legacy:
                problems.add('S4 item', where, f'{len(legacy)} of its textures are not family sprites: '
                             + ', '.join(legacy[:5]), 'rebuild the model with family sprites in its generator')
            if matches(name, RENDERER_MOUNTED) and tree.literals and not any(
                    tree.in_code(texture) for texture in textures):
                problems.add('S4 item', where, 'the item shares no sprite with the mounted part the renderer draws',
                             'use the faceplate and status-light sprites of the mounted cell in the item model')
    lang = tree.assets / 'lang/en_us.json'
    if lang.is_file():
        for key in json.loads(lang.read_text()):
            found = re.fullmatch(rf'(?:item|block)\.{NAMESPACE}\.([a-z0-9_]+)', key)
            if found and found.group(1) not in tree.items:
                problems.add('S3 item', 'lang/en_us.json', f'{key} names an item without an item definition',
                             f'write items/{found.group(1)}.json with its generator, or remove the name')


def check_natural_blocks(tree, problems, signature=()):
    """S2: a block that shows a natural overlay shows its vanilla host below it and lights the overlay."""
    for name, state in tree.blockstates.items():
        models = [tree.own_model(reference) for reference in tree.model_references(state)]
        textures, emissive = set(), False
        for model in models:
            if model not in tree.models:
                continue
            textures |= set(tree.model_textures(model).values())
            for _, entry in tree.chain(model)[0]:
                text = json.dumps(entry)
                emissive = emissive or bool(re.search(r'"light_emission": *[1-9]|"block_light": *[1-9]', text))
        own = {tree.own_texture(texture) for texture in textures} - {None}
        if not any(tree.texture_class(texture) == 'natural' for texture in own):
            continue
        where = f'blockstates/{name}.json'
        if not any(isinstance(texture, str) and texture.startswith('minecraft:block/') for texture in textures):
            problems.add('S2 host', where, 'the ore does not show a vanilla host texture',
                         'reference the host from minecraft:block/... in the model and draw only the overlay')
        if not emissive:
            problems.add('S2 emissive', where, 'the overlay is not emissive',
                         'give the overlay boxes "light_emission" in the generator')
        if not any(isinstance(tree.animation(texture), dict) for texture in own if texture in tree.textures):
            problems.add('S2 animation', where, 'no texture of the ore is animated',
                         'animate the overlay with a frame strip and a .png.mcmeta')
        if signature and not any(pixel[3] and pixel[:3] in signature for texture in own if texture in tree.textures
                                 and tree.texture_class(texture) == 'natural' for pixel in set(tree.image(texture).flat())):
            problems.add('S2 palette', where, 'none of the Primal signature colours appears on the ore',
                         'draw the crystal with the PRIMAL colours of the palette')


def check_hues(tree, problems, palette, guide, java):
    where_guide, where_tools = 'docs/visual-style.md', 'tools/machine_palette.py'
    where_java = 'UiPalette.java'
    for error in guide.errors:
        problems.add('S5 guide', where_guide, error, 'restore the marked palette tables of the guide')
    assigned = {}
    for name in CRYSTALS:
        entry = guide.crystals.get(name)
        if entry is None:
            continue
        wanted = entry['rgb']
        if palette.crystals.get(name) != wanted:
            problems.add('S5 hue', where_tools, f'RGB[{name!r}] is {rgb_hex(palette.crystals.get(name, (0, 0, 0)))}, the '
                         f'guide says {rgb_hex(wanted)}', 'make the tools palette and the guide agree, then regenerate')
        constant = java.constants.get('CRYSTAL_' + name.upper())
        if constant is not None and constant != wanted:
            problems.add('S5 hue', where_java, f'CRYSTAL_{name.upper()} is {rgb_hex(constant)}, the guide says '
                         f'{rgb_hex(wanted)}', 'mirror the tools palette in the Java palette')
        for device in entry['machines']:
            assigned[device] = name
            accent = java.accent(device)
            if device not in java.kinds.values():
                problems.add('S5 guide', where_guide, f'{device} in the {name} row is not a machine kind',
                             'list the registry ids of MachineKind in the crystal table')
            elif accent != wanted:
                problems.add('S5 hue', where_java, f'the accent of {device} is '
                             f'{rgb_hex(accent) if accent else "missing"}, its crystal {name} is {rgb_hex(wanted)}',
                             'return the crystal hue of the guide from machineAccent')
            if device in palette.family and palette.family[device] != name:
                problems.add('S5 hue', where_tools, f'FAMILY[{device!r}] is {palette.family[device]}, the guide says '
                             f'{name}', 'make FAMILY and the crystal table of the guide agree')
        for device in entry['machines'] + entry['modules']:
            recipe = tree.data / f'recipe/{device}.json'
            if not recipe.is_file():
                problems.add('S5 guide', where_guide, f'{device} in the {name} row has no crafting recipe',
                             'list registry ids in the crystal table')
            elif f'{NAMESPACE}:{name}_matrix_crystal' not in recipe.read_text() \
                    and not problems.excepted('key_crystal', device):
                problems.add('S5 key crystal', f'data/{NAMESPACE}/recipe/{device}.json',
                             f'{device} wears {name} but its recipe holds no {name.title()} Matrix Crystal',
                             'give the device the hue of the crystal in its recipe, or the recipe the crystal of its '
                             'hue')
    for device, name in palette.family.items():
        if device not in assigned and device not in guide.primal_devices and guide.crystals:
            problems.add('S5 guide', where_guide, f'{device} ({name} in FAMILY) is in no row of the crystal table',
                         'list every machine in the crystal table')
    for kind, device in java.kinds.items():
        if device not in assigned and device not in guide.primal_devices and guide.crystals:
            problems.add('S5 guide', where_guide, f'machine kind {device} is in no row of the crystal or Primal table',
                         'give every machine a crystal, or the Primal signature')
    for constant, wanted in guide.constants.items():
        if palette.named.get(constant) != wanted:
            actual = palette.named.get(constant)
            problems.add('S5 palette', 'tools/family_palette.py', f'{constant} is '
                         f'{rgb_hex(actual) if actual else "not defined"}, the guide says {rgb_hex(wanted)}',
                         'make the palette module and the guide agree, then regenerate')
    for screen, source in guide.surfaces.items():
        if java.constants.get(screen) is None or java.constants.get(screen) != palette.named.get(source):
            actual = java.constants.get(screen)
            problems.add('S6 surface', where_java, f'{screen} is {rgb_hex(actual) if actual else "not defined"}, the '
                         f'casing colour {source} is {rgb_hex(palette.named.get(source, (0, 0, 0)))}',
                         'keep the screen surfaces on the casing colours of the palette module')
    if guide.primal:
        body = guide.primal.get('body')
        if not palette.primal:
            elsewhere = sorted(path.name for path in tree.tools.glob('*.py') if path.name != Path(__file__).name
                               and re.search(r'^PRIMAL\w*\s*=', path.read_text(), re.M))
            found = f'; it is defined in {", ".join(elsewhere)}' if elsewhere else ''
            problems.add('S5 primal', 'tools/family_palette.py', 'the Primal signature is not part of the palette '
                         'module' + found, 'define the PRIMAL colours once in tools/family_palette.py and import them '
                         'where they are drawn')
        else:
            for role, wanted in guide.primal.items():
                if wanted not in palette.primal.values():
                    problems.add('S5 primal', 'tools/family_palette.py', f'Primal {role} {rgb_hex(wanted)} of the guide '
                                 'is not among the PRIMAL colours', 'make the palette module and the guide agree')
        for device in guide.primal_devices:
            accent = java.accent(device)
            if body and accent != body:
                problems.add('S5 primal', where_java, f'the accent of {device} is '
                             f'{rgb_hex(accent) if accent else "missing"}, the Primal body colour is {rgb_hex(body)}',
                             'return the Primal body colour from machineAccent')
        for name, colour in palette.crystals.items():
            if colour in guide.primal.values():
                problems.add('S5 primal', where_tools, f'crystal {name} uses a Primal signature colour',
                             'keep the Primal signature apart from the eight crystal hues')


def shows(tree, device):
    """Visible colours of every sprite the block and the item of a device show."""
    models = [tree.own_model(reference) for value in (tree.blockstates.get(device), tree.items.get(device))
              for reference in tree.model_references(value)]
    colours = set()
    for model in models:
        for reference in tree.model_textures(model).values() if model in tree.models else ():
            texture = tree.own_texture(reference)
            if texture in tree.textures and tree.texture_class(texture) in ('family', 'natural'):
                colours |= {pixel[:3] for pixel in tree.image(texture).flat() if pixel[3]}
    return colours


def check_accents(tree, problems, guide):
    """S5: a device shows the hue of its crystal; a crystal icon has the hue as its body colour."""
    wanted = {device: (name, entry['rgb']) for name, entry in guide.crystals.items()
              for device in entry['machines'] + entry['modules']}
    if 'body' in guide.primal:
        wanted.update({device: ('Primal', guide.primal['body']) for device in guide.primal_devices})
    for device, (name, hue) in sorted(wanted.items()):
        colours = shows(tree, device)
        if colours and not any(colour == hue or on_ramp(colour, hue) or on_tone(colour, hue) for colour in colours):
            problems.add('S5 accent', f'blockstates/{device}.json', f'no sprite of {device} shows its {name} hue '
                         f'{rgb_hex(hue)}', 'light its accent parts in the hue of the crystal table, from the palette')
    for name, entry in guide.crystals.items():
        texture = f'item/{name}_matrix_crystal'
        if texture not in tree.textures or not tree.image(texture).rgba8:
            continue
        # The body colour: the median of the middle facets, without the dark outline and the highlights.
        body = sorted((pixel for pixel in tree.image(texture).flat() if pixel[3] >= 128 and luminance(pixel) >= 64),
                      key=luminance)
        body = body[len(body) // 4:len(body) * 85 // 100]
        if not body:
            continue
        middle = tuple(sorted(pixel[channel] for pixel in body)[len(body) // 2] for channel in range(4))
        distance = sum((a - b) ** 2 for a, b in zip(middle, entry['rgb'])) ** .5
        if distance > ICON_HUE_DISTANCE or middle[3] < 255:
            clear = '' if middle[3] == 255 else f' at alpha {middle[3]}'
            problems.add('S5 crystal icon', tree.texture_path(texture), f'the body of the icon is {rgb_hex(middle)}{clear}, '
                         f'the {name} hue is {rgb_hex(entry["rgb"])}', 'paint the crystal opaque, with the hue as the '
                         'colour of its middle facets')


JAVA_COLOUR = re.compile(r'\b0[xX](?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6})\b')


def check_java(tree, problems, palette_file='UiPalette.java'):
    """S5: colours live in the palette class. S6: a substance badge is never abbreviated."""
    for path in tree.literals:
        if 'client' not in path.parts or path.name == palette_file:
            continue
        text = path.read_text()
        where = tree.rel(path)
        found = {}
        for number, line in enumerate(text.splitlines(), 1):
            code = line.split('//', 1)[0]
            for literal in JAVA_COLOUR.findall(code):
                key = literal.lower()
                if problems.excepted('java_colour', '*', key) or problems.excepted('java_colour', path.name, key):
                    continue
                found.setdefault(key, []).append(number)
        if found:
            shown = ', '.join(f'{literal} (line {lines[0]})' for literal, lines in sorted(found.items()))
            problems.add('S5 source', where, f'{len(found)} colour literals outside the palette class: {shown}',
                         f'name the colour in {palette_file} and use the constant')
        if path.name == 'SubstanceIcon.java' and re.search(r'ScreenChrome\.fit\(|…|\\u2026|"\.\.\."', text):
            problems.add('S6 badge', where, 'the substance badge shortens its symbol with an ellipsis',
                         'draw the full symbol: break it into two lines or scale it, never cut it')


def check_exceptions(tree, problems):
    for kind, table in EXCEPTIONS.items():
        for key, value in table.items():
            subs = list(value) if isinstance(value, dict) else [None]
            for sub in subs:
                if kind == 'java_colour' and key == '*':
                    continue
                if (kind, key, sub) not in problems.used:
                    entry = key if sub is None else f'{key} {sub}'
                    problems.add('exceptions', 'tools/check_visual_style.py', f'the {kind} exception for {entry} no '
                                 'longer applies', 'remove the entry from EXCEPTIONS')


def gui_palette(java):
    return set(java.constants.values())


def run(tree, palette, guide, java):
    problems = Problems()
    check_broken(tree, problems)
    check_textures(tree, problems, palette, gui_palette(java))
    check_animations(tree, problems)
    _, reachable = check_references(tree, problems)
    check_geometry(tree, problems, reachable)
    check_items(tree, problems)
    check_natural_blocks(tree, problems, set(palette.primal.values()) or set(guide.primal.values()))
    check_hues(tree, problems, palette, guide, java)
    check_accents(tree, problems, guide)
    check_java(tree, problems)
    return problems


def load(root):
    tree = Tree(root)
    palette = Palette.load(tree.tools)
    guide = Guide(tree.guide.read_text() if tree.guide.is_file() else '')
    client = tree.java / 'dev/latrox/rematter'
    read = lambda path: path.read_text() if path.is_file() else ''
    java = JavaPalette(read(client / 'client/UiPalette.java'), read(client / 'content/MachineKind.java'))
    return tree, palette, guide, java


def inventory(tree):
    groups = {}
    for name in tree.textures:
        image = tree.image(name)
        key = (tree.texture_class(name) or 'unclassified', name.rsplit('/', 1)[0], f'{image.width}x{image.height}')
        groups.setdefault(key, []).append(name.rsplit('/', 1)[-1])
    for (kind, folder, size), names in sorted(groups.items()):
        print(f'{kind:8} {folder:12} {size:9} {len(names):3}  {" ".join(names[:6])}{" ..." if len(names) > 6 else ""}')


# --------------------------------------------------------------------------------------------- self-test
def selftest():
    """Synthetic trees: each case breaks one rule and must be reported under exactly that rule."""
    real = Palette.load(ROOT / 'tools')
    primal = {'PRIMAL.core': (255, 255, 255), 'PRIMAL.body': (138, 92, 255), 'PRIMAL.cool': (92, 200, 255)}
    palette = Palette(real.crystals, {'press': 'amber'}, {**real.named, **primal})
    base, shade, seam = palette.named['BASE'], palette.named['SHADE'], palette.named['SEAM']
    amber = palette.crystals['amber']
    clear = (0, 0, 0, 0)

    def solid(colour, width=32, height=None):
        return [[tuple(colour[:3]) + (255,)] * width for _ in range(height or width)]

    def framed(colour, size=32):
        return [[tuple(seam if 0 in (x, y) or size - 1 in (x, y) else colour) + (255,) for x in range(size)]
                for y in range(size)]

    def icon(dark=(20, 20, 24), soft=True, size=ICON_SIZE, body=None):
        rows = [[clear] * size for _ in range(size)]
        for y in range(8, size - 8):
            for x in range(8, size - 8):
                rim = min(x - 8, y - 8, size - 9 - x, size - 9 - y)
                colour = dark if rim < 3 else body or (min(255, 120 + x), 90 + y // 2, 60)
                rows[y][x] = tuple(colour) + ((128 + 40 * rim) if soft and rim < 1 else 255,)
        if soft:
            for x in range(8, size - 8):
                rows[7][x] = rows[size - 8][x] = tuple(dark) + (90,)
        return rows

    guide_text = '\n'.join([
        '<!-- check:crystal-hues -->', '| Crystal | Hex | Machines | Modules |', '| --- | --- | --- | --- |',
        *(f'| {name.title()} | `{rgb_hex(palette.crystals[name])}` | {"`press`" if name == "amber" else ""} | '
          f'{"`grinder`" if name == "amber" else ""} |' for name in CRYSTALS),
        '<!-- check:neutral-palette -->', '| Role | Constant | Hex |', '| --- | --- | --- |',
        f'| base | `BASE` | {rgb_hex(base)} |',
        '<!-- check:status-colours -->', '| Status | Constant | Hex |', '| --- | --- | --- |',
        f"| blue | `LED['blue']` | {rgb_hex(palette.named['LED.blue'])} |",
        '<!-- check:gui-surfaces -->', '| Role | UiPalette | Palette constant |', '| --- | --- | --- |',
        '| panel | `PANEL` | `SHADE` |',
        '<!-- check:primal-signature -->', '| Role | Hex | Devices |', '| --- | --- | --- |',
        *(f'| {role.split(".")[1]} | {rgb_hex(colour)} | |' for role, colour in palette.primal.items())])
    kinds_text = 'public enum MachineKind {\n    PRESS("press");\n}'

    def java_text(colour=amber):
        return ('final class UiPalette {\n    static final int PANEL = 0xff1d2127;\n'
                f'    static int machineAccent(MachineKind kind) {{ return switch(kind) {{ case PRESS -> 0xff{bytes(colour).hex()}; }}; }}\n}}')

    def build(folder, change=None):
        """A small valid tree: one block, one module item, one cell-like item, one icon item, one GUI frame."""
        root = Path(folder)
        assets = root / 'src/main/resources/assets' / NAMESPACE
        files = {
            'textures/block/press_front.png': framed(base),
            'textures/block/plate.png': solid(shade),
            'textures/block/lamp.png': [[tuple(amber) + (255,)] * 16 for _ in range(16)] + solid(shade, 16),
            'textures/block/lamp.png.mcmeta': {'animation': {'frametime': 4}},
            'textures/item/dust.png': icon(),
            'textures/gui/sprites/panel.png': solid(shade, 16),
            'textures/gui/sprites/panel.png.mcmeta': {'gui': {'scaling': {'type': 'nine_slice', 'width': 16,
                                                                           'height': 16, 'border': 3}}},
            'textures/gui/sprites/icon/play.png': [[(255, 255, 255, 255 if x > y else 102 if x == y else 0)
                                                    for x in range(16)] for y in range(16)],
            'models/block/press.json': {'parent': 'minecraft:block/block', 'textures': {
                'particle': f'{NAMESPACE}:block/plate', 'front': f'{NAMESPACE}:block/press_front',
                'side': f'{NAMESPACE}:block/plate', 'lamp': f'{NAMESPACE}:block/lamp'}, 'elements': [
                {'name': 'body', 'from': [0, 0, 1], 'to': [16, 16, 16], 'faces': {
                    'north': {'texture': '#front'}, 'south': {'texture': '#side', 'cullface': 'south'},
                    'east': {'texture': '#side', 'cullface': 'east'}, 'west': {'texture': '#side', 'cullface': 'west'},
                    'up': {'texture': '#side', 'cullface': 'up'}, 'down': {'texture': '#side', 'cullface': 'down'}}},
                {'name': 'lamp', 'from': [7, 7, .5], 'to': [9, 9, 1], 'light_emission': 15, 'faces': {
                    'north': {'texture': '#lamp', 'uv': [0, 0, 4, 4]}}}]},
            'models/item/dust.json': {'parent': 'minecraft:item/generated',
                                      'textures': {'layer0': f'{NAMESPACE}:item/dust'}},
            'blockstates/press.json': {'variants': {'': {'model': f'{NAMESPACE}:block/press'}}},
            'items/press.json': {'model': {'type': 'minecraft:model', 'model': f'{NAMESPACE}:block/press'}},
            'items/dust.json': {'model': {'type': 'minecraft:model', 'model': f'{NAMESPACE}:item/dust'}},
            'lang/en_us.json': {f'block.{NAMESPACE}.press': 'Press', f'item.{NAMESPACE}.dust': 'Dust'},
        }
        extra = {
            'src/main/resources/data/%s/recipe/press.json' % NAMESPACE: {'key': {'A': f'{NAMESPACE}:amber_matrix_crystal'}},
            'src/main/resources/data/%s/recipe/grinder.json' % NAMESPACE: {'key': {'A': f'{NAMESPACE}:amber_matrix_crystal'}},
            'docs/visual-style.md': guide_text,
            'src/main/java/dev/latrox/rematter/client/UiPalette.java': java_text(),
            'src/main/java/dev/latrox/rematter/content/MachineKind.java': kinds_text,
            'src/main/java/dev/latrox/rematter/client/Screen.java':
                'class Screen { String a = "panel", b = "icon/play"; int mask = 0xffffff; }',
        }
        if change:
            change(files, extra)
        for table, target in ((files, assets), (extra, root)):
            for name, value in table.items():
                path = target / name
                path.parent.mkdir(parents=True, exist_ok=True)
                if value is None:
                    continue
                if isinstance(value, bytes):
                    path.write_bytes(value)
                elif isinstance(value, str):
                    path.write_text(value)
                elif name.endswith('.png'):
                    path.write_bytes(png_bytes(value))
                else:
                    path.write_text(json.dumps(value))
        tree = Tree(root)
        java = JavaPalette((root / 'src/main/java/dev/latrox/rematter/client/UiPalette.java').read_text(),
                           (root / 'src/main/java/dev/latrox/rematter/content/MachineKind.java').read_text())
        saved = {kind: dict(table) for kind, table in EXCEPTIONS.items()}
        for kind in ('size', 'translucent', 'colour', 'outline', 'key_crystal'):
            EXCEPTIONS[kind].clear()
        try:
            return run(tree, palette, Guide((root / 'docs/visual-style.md').read_text()), java)
        finally:
            for kind, table in saved.items():
                EXCEPTIONS[kind].clear()
                EXCEPTIONS[kind].update(table)

    def put(name, value, where='files'):
        def change(files, extra):
            (files if where == 'files' else extra)[name] = value
        return change

    def model(edit):
        def change(files, extra):
            edit(files['models/block/press.json'])
        return change

    def both(*changes):
        def change(files, extra):
            for one in changes:
                one(files, extra)
        return change

    gradient = [[(40 + x, 44 + x, 50 + y, 255) for x in range(32)] for y in range(32)]
    small = [[(138, 92, 255, 255) if 4 <= x < 12 and 4 <= y < 12 else clear for x in range(16)] for y in range(16)]
    enlarged = [[small[y // 8][x // 8] for x in range(128)] for y in range(128)]
    ore = {'parent': 'minecraft:block/block', 'textures': {'particle': 'minecraft:block/deepslate',
                                                             'host': 'minecraft:block/deepslate',
                                                             'overlay': f'{NAMESPACE}:block/deep_primal_ore'},
           'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
               face: {'texture': '#host', 'cullface': face} for face in FACES}},
               {'from': [0, 0, 0], 'to': [16, 16, 16], 'light_emission': 15, 'faces': {
                   face: {'texture': '#overlay', 'cullface': face} for face in FACES}}]}
    violet = (138, 92, 255, 255)
    strip = [[violet if (x + y + frame) % 5 == 0 else clear for x in range(16)] for frame in range(4) for y in range(16)]

    def natural(files, extra):
        files['textures/block/deep_primal_ore.png'] = strip
        files['textures/block/deep_primal_ore.png.mcmeta'] = {'animation': {'frametime': 3, 'interpolate': True}}
        files['models/block/deep_primal_ore.json'] = json.loads(json.dumps(ore))
        files['blockstates/deep_primal_ore.json'] = {'variants': {'': {'model': f'{NAMESPACE}:block/deep_primal_ore'}}}
        files['items/deep_primal_ore.json'] = {'model': {'type': 'minecraft:model',
                                                         'model': f'{NAMESPACE}:block/deep_primal_ore'}}

    def cell(texture):
        def change(files, extra):
            files['textures/block/drive_cell.png'] = framed(shade, 16)
            files[f'textures/block/{texture}.png'] = framed(shade, 16)
            files['models/item/matter_cell_1k.json'] = {'textures': {'face': f'{NAMESPACE}:block/{texture}'}, 'elements': [
                {'from': [5, 6, 2], 'to': [11, 9, 14], 'faces': {'north': {'texture': '#face', 'uv': [0, 0, 12, 6]}}}]}
            files['items/matter_cell_1k.json'] = {'model': {'type': 'minecraft:model',
                                                            'model': f'{NAMESPACE}:item/matter_cell_1k'}}
            extra['src/main/java/dev/latrox/rematter/client/Bays.java'] = 'class Bays { String[] S = {"drive_cell"}; }'
        return change

    cases = [
        ('a valid tree', None, []),
        ('a valid ore with host, animated overlay and light', natural, []),
        ('a valid cell item with the mounted sprite', cell('drive_cell'), []),
        ('128 px block texture', put('textures/block/plate.png', solid(shade, 128)), ['S1 size', 'S4 item']),
        ('colour outside the palette', put('textures/block/plate.png', solid((200, 30, 190))), ['S1 palette']),
        ('shade of a crystal hue is allowed', put('textures/block/plate.png', solid(tuple(round(c * .57) for c in amber))), []),
        ('tone of a crystal hue is allowed', put('textures/block/plate.png', solid(tuple(
            round(round(c * .38) + (255 - round(c * .38)) * .22) for c in amber))), []),
        ('colour that imitates a crystal hue', put('textures/block/plate.png', solid((231, 176, 86))), ['S1 palette']),
        ('tint of a neutral is not allowed', put('textures/block/plate.png', solid(tuple(c + 9 for c in base))), ['S1 palette']),
        ('gradient sprite', put('textures/block/plate.png', gradient), ['S1 gradient']),
        ('soft alpha in a block sprite', put('textures/block/plate.png', [[tuple(shade) + (128,)] * 32] * 32), ['S1 alpha']),
        ('16 px item sprite', put('textures/item/dust.png', small), ['S3 size']),
        ('enlarged 16 px item sprite', put('textures/item/dust.png', enlarged), ['S3 illustrated']),
        ('icon without anti-aliasing', put('textures/item/dust.png', icon(soft=False)), ['S3 illustrated']),
        ('icon without a dark outline', put('textures/item/dust.png', icon(dark=(200, 200, 190))), ['S3 outline']),
        ('icon that fills the canvas', put('textures/item/dust.png', [[(20 + (x * 7 + y * 3) % 20, 20, 24, 255)
                                                                      for x in range(128)] for y in range(128)]),
         ['S3 background', 'S3 canvas', 'S3 illustrated']),
        ('item that shows a block sprite as its icon', put('models/item/dust.json', {
            'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NAMESPACE}:block/plate'}}),
         ['S3 item', 'refs unused']),
        ('block whose item is a flat icon', put('items/press.json', {'model': {
            'type': 'minecraft:model', 'model': f'{NAMESPACE}:item/dust'}}), ['S4 item']),
        ('cell item without the mounted sprite', cell('cell_body'), ['S4 item']),
        ('name without an item definition', put('lang/en_us.json', {f'item.{NAMESPACE}.ghost': 'Ghost'}), ['S3 item']),
        ('animation metadata that is not JSON', put('textures/block/lamp.png.mcmeta', '{"animation": '), ['anim']),
        ('strip that does not divide into frames', put('textures/block/lamp.png', solid(amber, 16, 40)), ['anim']),
        ('frame index beyond the strip', put('textures/block/lamp.png.mcmeta', {'animation': {'frames': [0, 1, 2]}}), ['anim']),
        ('strip without metadata', put('textures/block/lamp.png.mcmeta', None), ['anim']),
        ('nine-slice size that is not the sprite size', put('textures/gui/sprites/panel.png.mcmeta', {'gui': {'scaling': {
            'type': 'nine_slice', 'width': 18, 'height': 18, 'border': 3}}}), ['anim']),
        ('texture nothing uses', put('textures/block/orphan.png', solid(base)), ['refs unused']),
        ('model family nothing uses', both(
            put('models/block/old.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NAMESPACE}:block/plate'}}),
            put('models/block/old_red.json', {'parent': f'{NAMESPACE}:block/old'})), ['refs unused', 'refs unused']),
        ('renderer that names a missing texture', put('src/main/java/dev/latrox/rematter/client/Screen.java',
                                                     'class Screen { String a = "panel", b = "icon/play", c = "textures/entity/gone.png"; }',
                                                     'extra'), ['refs']),
        ('model that names a missing texture', model(lambda value: value['textures'].update(
            side=f'{NAMESPACE}:block/gone')), ['refs']),
        ('item that names a missing model', put('items/dust.json', {'model': {
            'type': 'minecraft:model', 'model': f'{NAMESPACE}:item/gone'}}), ['S3 item', 'refs', 'refs unused', 'refs unused']),
        ('box off the half-pixel grid', model(lambda value: value['elements'][0]['from'].__setitem__(2, 1.3)), ['S1 grid']),
        ('sliver box', model(lambda value: value['elements'][1].update({
            'from': [7, 7, .5], 'to': [7.5, 9, 1], 'faces': {'north': {'texture': '#lamp', 'uv': [0, 0, 1, 4]}}})),
         ['S1 detail']),
        ('outer face without cullface', model(lambda value: value['elements'][0]['faces']['up'].pop('cullface')),
         ['S1 cullface']),
        ('stretched sprite', model(lambda value: value['elements'][0]['faces']['north'].update(uv=[0, 0, 8, 8])),
         ['S1 density']),
        ('two faces in one plane', model(lambda value: value['elements'].append({
            'name': 'patch', 'from': [2, 2, 1], 'to': [6, 6, 3], 'faces': {'north': {'texture': '#side'}}})),
         ['S1 z-fighting']),
        ('GUI sprite in a colour outside the palette', put('textures/gui/sprites/panel.png', solid((90, 200, 20), 16)),
         ['S6 palette']),
        ('GUI icon with soft coloured pixels', put('textures/gui/sprites/icon/play.png', [[tuple(shade) + (99,)] * 16] * 16),
         ['S6 alpha']),
        ('GUI sprite nothing draws', put('textures/gui/sprites/icon/stop.png', [[(255, 255, 255, 255)] * 16] * 16),
         ['refs unused']),
        ('texture outside every class', put('textures/misc/thing.png', solid(base)), ['class', 'refs unused']),
        ('block that does not show its crystal hue', put('textures/block/lamp.png', solid(shade, 16, 16) + solid(seam, 16, 16)),
         ['S5 accent']),
        ('crystal icon in its hue', both(
            put('textures/item/amber_matrix_crystal.png', icon(body=tuple(c - 6 for c in amber))),
            put('models/item/amber_matrix_crystal.json', {'parent': 'minecraft:item/generated', 'textures': {
                'layer0': f'{NAMESPACE}:item/amber_matrix_crystal'}}),
            put('items/amber_matrix_crystal.json', {'model': {'type': 'minecraft:model',
                                                              'model': f'{NAMESPACE}:item/amber_matrix_crystal'}})), []),
        ('crystal icon that is not its hue', both(
            put('textures/item/amber_matrix_crystal.png', icon()),
            put('models/item/amber_matrix_crystal.json', {'parent': 'minecraft:item/generated', 'textures': {
                'layer0': f'{NAMESPACE}:item/amber_matrix_crystal'}}),
            put('items/amber_matrix_crystal.json', {'model': {'type': 'minecraft:model',
                                                              'model': f'{NAMESPACE}:item/amber_matrix_crystal'}})),
         ['S5 crystal icon']),
        ('Java accent that is not the crystal hue', put('src/main/java/dev/latrox/rematter/client/UiPalette.java',
                                                         java_text((1, 2, 3)), 'extra'), ['S5 hue']),
        ('guide hue that differs from the tools palette', put('docs/visual-style.md', guide_text.replace(
            rgb_hex(palette.crystals['leaf']), '#123456'), 'extra'), ['S5 hue']),
        ('guide constant that differs from the palette module', put('docs/visual-style.md', guide_text.replace(
            f'`BASE` | {rgb_hex(base)}', '`BASE` | #123456'), 'extra'), ['S5 palette']),
        ('guide without its marked tables', put('docs/visual-style.md', '# Visual style\n', 'extra'), ['S5 guide'] * 5),
        ('screen surface off the casing colours', put('src/main/java/dev/latrox/rematter/client/UiPalette.java',
                                                       java_text().replace('0xff1d2127', '0xff202428'), 'extra'), ['S6 surface']),
        ('recipe without the crystal of the accent', put('src/main/resources/data/%s/recipe/press.json' % NAMESPACE,
                                                          {'key': {'A': f'{NAMESPACE}:leaf_matrix_crystal'}}, 'extra'),
         ['S5 key crystal']),
        ('colour literal outside the palette class', put('src/main/java/dev/latrox/rematter/client/Screen.java',
                                                          'class Screen { String a = "panel", b = "icon/play"; int c = 0xff56bcb3; }',
                                                          'extra'), ['S5 source']),
        ('abbreviated substance badge', put('src/main/java/dev/latrox/rematter/client/SubstanceIcon.java',
                                            'class SubstanceIcon { String s = ScreenChrome.fit(font, label, 14); }', 'extra'),
         ['S6 badge']),
        ('ore overlay that is not animated', both(natural, put('textures/block/deep_primal_ore.png', strip[:16]),
                                                  put('textures/block/deep_primal_ore.png.mcmeta', None)),
         ['S2 animation']),
        ('ore that copies its host stone', both(natural, model(lambda value: None), lambda files, extra: files[
            'models/block/deep_primal_ore.json']['textures'].update(host=f'{NAMESPACE}:block/plate',
                                                                    particle=f'{NAMESPACE}:block/plate')), ['S2 host']),
        ('ore overlay without light', both(natural, lambda files, extra: files['models/block/deep_primal_ore.json'][
            'elements'][1].pop('light_emission')), ['S2 emissive']),
        ('ore overlay in the hue of the host stone', both(natural, put('textures/block/deep_primal_ore.png', [
            [(150, 110, 60, 255) if (x + y) % 5 == 0 else clear for x in range(16)] for y in range(64)])),
         ['S2 palette', 'S2 palette']),
    ]
    failed = 0
    for title, change, expected in cases:
        with tempfile.TemporaryDirectory() as folder:
            found = sorted(build(folder, change).rules())
        if found != sorted(expected):
            failed += 1
            print(f'self-test failed: {title}: expected {sorted(expected)}, found {found}')
    # The PNG reader against every filter type, and the ramp rule against the helpers of the palette module.
    rows = [[((x * 37 + y * 11) % 256, (x * 5 + y * 91) % 256, (x * y) % 256, 255 - (x + y) % 200) for x in range(19)]
            for y in range(23)]
    with tempfile.TemporaryDirectory() as folder:
        for filters in ([0], [1], [2], [3], [4], [0, 1, 2, 3, 4]):
            path = Path(folder) / 'filter.png'
            path.write_bytes(png_bytes(rows, filters))
            if Image(path).pixels() != rows:
                failed += 1
                print(f'self-test failed: PNG filter {filters} does not decode to its pixels')
    for factor in (.22, .45, .57, .68, .7, .72):
        for colour in palette.crystals.values():
            dark = tuple(max(0, min(255, round(channel * factor))) for channel in colour)
            light = tuple(round(channel + (255 - channel) * factor) for channel in colour)
            if not (on_ramp(dark, colour) and on_ramp(light, colour)):
                failed += 1
                print(f'self-test failed: {factor} of {colour} is not on its ramp')
    if on_ramp((200, 30, 190), palette.crystals['amber']) or on_ramp((60, 60, 60), palette.crystals['ember']):
        failed += 1
        print('self-test failed: a foreign colour lies on a crystal ramp')
    print(f'Self-test: {len(cases)} tree cases, 6 PNG filter cases, ramp cases; {failed} failed')
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true', help='check without writing (the default; nothing is written)')
    parser.add_argument('--list', action='store_true', help='print the texture inventory by asset class')
    parser.add_argument('--json', metavar='FILE', help='also write the findings as JSON')
    parser.add_argument('--selftest', action='store_true', help='run the rules against synthetic cases')
    parser.add_argument('--root', type=Path, default=ROOT, help='check another checkout of the repository')
    arguments = parser.parse_args()
    if arguments.selftest:
        return selftest()
    tree, palette, guide, java = load(arguments.root.resolve())
    if arguments.list:
        inventory(tree)
    problems = run(tree, palette, guide, java)
    check_exceptions(tree, problems)
    if arguments.json:
        Path(arguments.json).write_text(json.dumps([dict(zip(('rule', 'file', 'message', 'fix'), item))
                                                    for item in sorted(problems.items)], indent=1) + '\n')
    problems.report()
    counts = {}
    for rule in problems.rules():
        counts[rule] = counts.get(rule, 0) + 1
    if problems.items:
        print(f'{len(problems.items)} findings: ' + ', '.join(f'{rule} {count}' for rule, count in sorted(counts.items())))
        print('The rules are explained in docs/visual-style.md.')
        return 1
    print(f'Checked {len(tree.textures)} textures, {len(tree.models)} models and {len(tree.items)} items against '
          'docs/visual-style.md')
    return 0


if __name__ == '__main__':
    sys.exit(main())
