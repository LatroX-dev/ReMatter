"""Pixel art of mounted hardware: the industrial modules and the cell cartridges.

Both are members of the machine family: two texels per model pixel, hard
clusters lit from the top left, no gradients, colours from family_palette
and the eight identity hues of machine_palette. A part does not get a sprite
of its own. Every visible face asks a Sheet for a tile of its exact size, and
the Sheet packs the tiles of one device into 32 x 32 sprites, so no face is
ever stretched and equal faces share one tile.
"""
from family_geometry import mapped_uv
from family_palette import (BADGE, BADGE_LIGHT, BASE, COPPER, COPPER_DARK, COPPER_LIGHT, HIGHLIGHT, IVORY, PORT_DARK, PORT_LIGHT,
                            PORT_METAL, SEAM, SHADE, STEEL, STEEL_DEEP, STEEL_SHADE, TRIM, WELL, lighten, rgb, scale)
from family_sprites import Sprite, bolt, casing, drive_cell
from machine_palette import RGB

# Model directions in which a sprite's u and v grow on a face seen from outside.
NATURAL = {'north': ('-x', '-y'), 'south': ('+x', '-y'), 'west': ('+z', '-y'), 'east': ('-z', '-y'),
           'up': ('+x', '+z'), 'down': ('+x', '-z')}
SIZE = 32

# ----------------------------------------------------------------------------- identity colours
# The crystal of a module has the hue of its job. Three assignments are drawn; switching is one line here,
# followed by a regeneration.
MODULE_HUE_PRESETS = {
    # The job as the controller names it: preparation is amber, reaction is violet. This is the Matrix Crystal
    # of the module's recipe, so the crystal a player crafts in is the crystal the module shows.
    'recipe': {'grinding_unit': 'amber', 'separation_unit': 'amber', 'mixing_basin': 'amber',
               'field_coil': 'violet', 'reaction_chamber': 'violet'},
    # The job as the workshop bench that does it by hand: Decomposer copper, Analyzer ice, Material Synthesizer
    # leaf, Network Interface blue, Chemical Reactor violet. Honest only when the recipes take these crystals.
    'job': {'grinding_unit': 'copper', 'separation_unit': 'ice', 'mixing_basin': 'leaf',
            'field_coil': 'blue', 'reaction_chamber': 'violet'},
    # As 'job', but mixing stays with the Preparator's amber.
    'job_amber': {'grinding_unit': 'copper', 'separation_unit': 'ice', 'mixing_basin': 'amber',
                  'field_coil': 'blue', 'reaction_chamber': 'violet'},
}
MODULE_HUE_PRESET = 'recipe'


def module_hue(name, preset=None):
    return RGB[MODULE_HUE_PRESETS[preset or MODULE_HUE_PRESET][name]]


# Primal Matter is none of the eight hues: a white-hot core in ultraviolet, with a shimmer that travels between
# cyan-blue and magenta. The pastel Violet crystal of the reactors is a different colour and never animated.
PRIMAL_CORE = rgb('#ffffff')
PRIMAL_HOT = rgb('#efe6ff')
PRIMAL_BODY = rgb('#8a5cff')
PRIMAL_CYAN = rgb('#5cc8ff')
PRIMAL_MAGENTA = rgb('#ff6ad5')
# The shaded step of the same ultraviolet, as the Primal Ore and the Primal items use it; not another violet.
PRIMAL_DEEP = rgb('#4c24b2')
PRIMAL_FRAMES = 8
PRIMAL_ANIMATION = {'animation': {'frametime': 3}}
# One prismatic glint, three texels long, that runs through the ultraviolet once per cycle.
PRIMAL_GLINT = (PRIMAL_CYAN, PRIMAL_HOT, PRIMAL_MAGENTA)


def primal_run(position, frame):
    """Colour of a texel on a Primal run: the glint enters at position 0 and leaves at the far end."""
    step = (frame - position) % PRIMAL_FRAMES
    return PRIMAL_GLINT[step] if step < len(PRIMAL_GLINT) else PRIMAL_BODY


# ----------------------------------------------------------------------------- tiles
class Sheet:
    """Face tiles of one device, packed into 32 x 32 sprites.

    A tile is asked for by a key and its size in texels; equal keys share one tile. A painter draws a tile at
    its place: painter(sprite, x, y, w, h, look), where look carries the state and the animation frame.
    """

    def __init__(self, name, texture):
        self.name = name
        self.texture = texture
        self.tiles = {}
        self.places = None

    def want(self, key, width, height, painter):
        width, height = round(width), round(height)
        if width > SIZE or height > SIZE:
            raise ValueError(f'{self.name}: tile {key} of {width} x {height} leaves the sprite')
        known = self.tiles.get(key)
        if known and known[:2] != (width, height):
            raise ValueError(f'{self.name}: tile {key} is asked for in two sizes')
        self.tiles[key] = (width, height, painter)
        self.places = None

    def pack(self):
        """Shelves, tallest tiles first. The order depends on nothing but the tiles."""
        pages, places = [], {}
        for key in sorted(self.tiles, key=lambda item: (-self.tiles[item][1], -self.tiles[item][0], repr(item))):
            width, height, _ = self.tiles[key]
            for index, shelves in enumerate(pages):
                spot = self._fit(shelves, width, height)
                if spot:
                    places[key] = (index, *spot)
                    break
            else:
                pages.append([[0, height, width]])
                places[key] = (len(pages) - 1, 0, 0)
        self.places, self.count = places, len(pages)

    @staticmethod
    def _fit(shelves, width, height):
        for shelf in shelves:
            top, tall, used = shelf
            if height <= tall and used + width <= SIZE:
                shelf[2] += width
                return used, top
        top = shelves[-1][0] + shelves[-1][1]
        if top + height <= SIZE:
            shelves.append([top, height, width])
            return 0, top
        return None

    def spot(self, key):
        if self.places is None:
            self.pack()
        return self.places[key]

    def page(self, index):
        return self.name if index == 0 else f'{self.name}_{index + 1}'

    def key(self, index):
        return self.texture if index == 0 else f'{self.texture}{index + 1}'

    def textures(self, suffix=''):
        if self.places is None:
            self.pack()
        return {self.key(index): self.page(index) + suffix for index in range(self.count)}

    def face(self, key, box, face):
        """Texture key, uv and rotation that lay the tile onto a face of a box."""
        index, x, y = self.spot(key)
        return (self.key(index), *mapped_uv(face, box.lo, box.hi, *NATURAL[face], (x, y)))

    def sprites(self, suffix='', frames=1, **look):
        """The pages by sprite name. Several frames stand below each other, as an animated sprite needs them."""
        if self.places is None:
            self.pack()
        result = {self.page(index) + suffix: Sprite(SIZE, SIZE * frames, fill=SEAM) for index in range(self.count)}
        for key, (index, x, y) in self.places.items():
            width, height, painter = self.tiles[key]
            for frame in range(frames):
                painter(result[self.page(index) + suffix], x, y + SIZE * frame, width, height,
                        dict(look, frame=frame))
        return result


def face_size(box, face):
    """Width and height of a face in texels, in the orientation the sprite is laid on it."""
    width, height, depth = (round(value * 2) for value in box.size())
    return {'north': (width, height), 'south': (width, height), 'east': (depth, height), 'west': (depth, height),
            'up': (width, depth), 'down': (width, depth)}[face]


# ----------------------------------------------------------------------------- painters
def edges(sprite, x, y, w, h, light, dark):
    """Lit top and left edge, shaded bottom and right edge; a tile too small for both keeps the lit one."""
    if h >= 2:
        sprite.row(y, x, x + w, light)
        sprite.row(y + h - 1, x, x + w, dark)
    if w >= 3 and h >= 3:
        sprite.col(x, y, y + h - 1, light)
        sprite.col(x + w - 1, y + 1, y + h, dark)
    elif w == 2 and h >= 3:
        sprite.col(x, y, y + h, light)
        sprite.col(x + 1, y + 1, y + h, dark)


def metal(base, light, dark, groove=None):
    """A plain metal face. Large faces carry one engraved line, so a broad plate is not an empty rectangle."""
    def paint(sprite, x, y, w, h, look):
        sprite.rect(x, y, x + w, y + h, base)
        edges(sprite, x, y, w, h, light, dark)
        if groove and w >= 8 and h >= 6:
            sprite.row(y + h // 2, x + 2, x + w - 2, groove)
    return paint


STEEL_FACE = metal(STEEL, TRIM, STEEL_SHADE, STEEL_SHADE)
STEEL_PLAIN = metal(STEEL, TRIM, STEEL_SHADE)
DARK_FACE = metal(STEEL_DEEP, STEEL_SHADE, SEAM)
GUNMETAL_FACE = metal(PORT_METAL, PORT_LIGHT, PORT_DARK)
GRAPHITE_FACE = metal(BASE, HIGHLIGHT, SHADE)


def well(sprite, x, y, w, h, look):
    """The inside of a vessel or a recess: dark, with the shadow of the rim along its top."""
    sprite.rect(x, y, x + w, y + h, SEAM)
    sprite.row(y, x, x + w, WELL)
    if w >= 3:
        sprite.col(x, y, y + h, WELL)


def tones(hue, active):
    """Body, lit facet, shaded facet and glint of a crystal. At rest it keeps its hue without shining."""
    if active:
        return hue, lighten(hue, .45), scale(hue, .66), lighten(hue, .8)
    return scale(hue, .66), scale(hue, .86), scale(hue, .44), hue


def crystal(hue):
    """A cut crystal face: three flat facets that step down a diagonal, and one glint on the lit facet."""
    def paint(sprite, x, y, w, h, look):
        body, light, dark, glint = tones(hue, look.get('active', False))
        for v in range(h):
            for u in range(w):
                # Position along the diagonal from the lit top left to the shaded bottom right, 0 to 1.
                along = ((u + .5) / w + (v + .5) / h) / 2
                sprite.px(x + u, y + v, light if along < .36 else body if along < .7 else dark)
        if w >= 2 and h >= 2:
            sprite.row(y + h - 1, x, x + w, dark)
        if w >= 3 and h >= 3:
            sprite.col(x + w - 1, y + 1, y + h, dark)
            sprite.px(x + 1, y + 1, glint)
        if w >= 6 and h >= 6:
            sprite.px(x + 2, y + 1, glint)
            sprite.px(x + 1, y + 2, glint)
    return paint


def coil(sprite, x, y, w, h, look):
    """Windings of a coil: ribs across the short side of the face, under one lit edge."""
    sprite.rect(x, y, x + w, y + h, STEEL)
    if w >= h:
        for u in range(w):
            if u % 4 >= 2:
                sprite.col(x + u, y, y + h, STEEL_SHADE)
        sprite.row(y, x, x + w, TRIM)
        sprite.row(y + h - 1, x, x + w, STEEL_DEEP)
    else:
        for v in range(h):
            if v % 4 >= 2:
                sprite.row(y + v, x, x + w, STEEL_SHADE)
        sprite.col(x, y, y + h, TRIM)
        sprite.col(x + w - 1, y, y + h, STEEL_DEEP)


def membrane(sprite, x, y, w, h, look):
    """A membrane plate: steel with rows of pores on its broad faces, a plain lit rim on its edge."""
    sprite.rect(x, y, x + w, y + h, STEEL)
    edges(sprite, x, y, w, h, TRIM, STEEL_SHADE)
    if w >= 8 and h >= 8:
        for v in range(3, h - 2, 3):
            for u in range(3, w - 2, 3):
                sprite.px(x + u, y + v, STEEL_DEEP)


def blade(inverted):
    """A steel blade. Its ground bevel points at the work: down on the floor, up under the ceiling."""
    def paint(sprite, x, y, w, h, look):
        sprite.rect(x, y, x + w, y + h, STEEL)
        if h < 4 or w < 4:
            edges(sprite, x, y, w, h, TRIM, STEEL_SHADE)
            return
        rows = list(range(y, y + h))
        if inverted:
            rows.reverse()
        # From the back of the blade to its edge: a dark spine, the flat, and the bright ground bevel.
        sprite.row(rows[0], x, x + w, STEEL_DEEP)
        sprite.row(rows[1], x, x + w, STEEL_SHADE)
        for row in rows[-3:]:
            sprite.row(row, x, x + w, TRIM)
        sprite.row(rows[-3], x, x + w, STEEL_SHADE)
        for u in (2, w - 4):
            sprite.rect(x + u, min(rows[2], rows[3]), x + u + 2, max(rows[2], rows[3]) + 1, STEEL_DEEP)
    return paint


def cutting_edge(hue):
    """The ground edge of the blade. At work it carries the hue of the resonator that drives it."""
    def paint(sprite, x, y, w, h, look):
        active = look.get('active', False)
        body, light, dark, _ = tones(hue, True) if active else (TRIM, IVORY, STEEL, None)
        sprite.rect(x, y, x + w, y + h, body)
        if w >= h:
            for u in range(w):
                if u % 4 == 0:
                    sprite.col(x + u, y, y + h, dark)
            if h >= 2:
                sprite.row(y, x, x + w, light)
        else:
            sprite.col(x, y, y + h, light)
    return paint


# One arm of the swirl in a basin, as steps from the centre of the face; the other three are quarter turns.
SWIRL_ARM = ((.5, -2.5), (.5, -3.5), (1.5, -4.5), (2.5, -5.5), (3.5, -5.5), (4.5, -6.5), (5.5, -6.5), (6.5, -5.5),
             (7.5, -4.5))


def liquid(hue):
    """What a basin holds: a dark charge at rest, and at work a swirl that runs outward in the hue of its crystal."""
    def paint(sprite, x, y, w, h, look):
        active, frame = look.get('active', False), look.get('frame', 0)
        body, light, dark, glint = tones(hue, active)
        if not active:
            body, light, dark = scale(hue, .3), scale(hue, .44), scale(hue, .22)
        sprite.rect(x, y, x + w, y + h, body)
        if w < 12 or h < 12:
            sprite.row(y, x, x + w, light)
            return
        for turn in range(4):
            for index, (u, v) in enumerate(SWIRL_ARM):
                for _ in range(turn):
                    u, v = -v, u
                u, v = int(x + w / 2 - .5 + u), int(y + h / 2 - .5 + v)
                if x < u < x + w - 1 and y < v < y + h - 1:
                    lit = not active or (index - frame) % 4 < 2
                    sprite.px(u, v, (glint if active else light) if lit else light)
        sprite.ring(x, y, x + w, y + h, dark)
    return paint


# ----------------------------------------------------------------------------- mount of a module
def mount_top():
    """Top of the socket plate: the casing face with the gunmetal seat that the module is plugged into."""
    sprite = casing()
    for x, y in ((2, 2), (28, 2), (2, 28), (28, 28)):
        bolt(sprite, x, y)
    sprite.ring(4, 4, 28, 28, SEAM)
    sprite.rect(5, 5, 27, 27, PORT_METAL)
    sprite.bevel(5, 5, 27, 27, PORT_LIGHT, PORT_DARK)
    sprite.rect(6, 6, 26, 26, WELL)
    return sprite


def mount_side(sprite, x, y, w, h, look):
    """Edge of the socket plate: a casing strip with the lugs of the seat."""
    sprite.rect(x, y, x + w, y + h, BASE)
    sprite.row(y, x, x + w, HIGHLIGHT)
    sprite.row(y + h - 1, x, x + w, SEAM)
    sprite.col(x, y, y + h, SEAM)
    sprite.col(x + w - 1, y, y + h, SEAM)
    if w >= 24:
        for left in (x + 6, x + w - 10):
            sprite.rect(left, y + 1, left + 4, y + h - 1, PORT_METAL)
            sprite.row(y + 1, left, left + 4, PORT_LIGHT)


def mount_front(sprite, x, y, w, h, look):
    """The front edge of the plate carries the maker's badge of every machine front."""
    mount_side(sprite, x, y, w, h, look)
    sprite.rect(x + 13, y + 1, x + 19, y + h - 1, BADGE)
    sprite.row(y + 1, x + 13, x + 19, BADGE_LIGHT)


def collar_side(sprite, x, y, w, h, look):
    """The flange between plate and module, in the gunmetal of every connector."""
    sprite.rect(x, y, x + w, y + h, PORT_METAL)
    sprite.row(y, x, x + w, PORT_LIGHT)
    sprite.row(y + h - 1, x, x + w, PORT_DARK)
    sprite.col(x, y, y + h, SEAM)
    sprite.col(x + w - 1, y, y + h, SEAM)


def collar_top(sprite, x, y, w, h, look):
    """Top of the flange: a shallow seat, shadowed along its top and left edge like an empty cable port."""
    sprite.rect(x, y, x + w, y + h, PORT_METAL)
    sprite.bevel(x, y, x + w, y + h, PORT_LIGHT, PORT_DARK)
    if w >= 8 and h >= 8:
        sprite.rect(x + 2, y + 2, x + w - 2, y + h - 2, SEAM)
        sprite.row(y + 2, x + 2, x + w - 2, WELL)
        sprite.col(x + 2, y + 2, y + h - 2, WELL)


# ----------------------------------------------------------------------------- cell cartridges
def cell_face(bar):
    """Faceplate of a Matter Cell, exactly as the Matter Drive shows it in a bay."""
    return drive_cell(bar)


def housing_face():
    """Faceplate of an empty Cell Housing: the same plate with an empty track and no light."""
    return drive_cell(0)


# How a Primal Cell tells its tier. The bar keeps the lengths of the Matter Cells.
PRIMAL_FACE_STYLES = ('slot', 'bar', 'pastel')
PRIMAL_FACE_STYLE = 'slot'


def primal_cell_face(bar, style=None):
    """Faceplate of a Primal Cell in a bay: the cell faceplate with the Primal signature in place of the ivory bar.

    'slot' also fills the grip slot with deep ultraviolet, so the smallest cell is recognised without its bar;
    'bar' changes nothing but the bar; 'pastel' is the faceplate of 0.7.0 and uses the Violet crystal hue.
    """
    style = style or PRIMAL_FACE_STYLE
    if style == 'pastel':
        sprite = drive_cell(bar)
        violet = RGB['violet']
        sprite.rect(0, 1, 4, 5, scale(violet, .48))
        sprite.row(1, 1, 3, IVORY)
        sprite.col(1, 1, 4, IVORY)
        sprite.row(3, 1, 3, IVORY)
        sprite.row(1, 4, 11, lighten(violet, .5))
        return sprite
    plate = drive_cell(0)
    sprite = Sprite(16, 16 * PRIMAL_FRAMES, fill=STEEL)
    for frame in range(PRIMAL_FRAMES):
        for y in range(16):
            for x in range(16):
                sprite.px(x, y + 16 * frame, plate.pixel(x, y))
        if style == 'slot':
            sprite.row(2 + 16 * frame, 5, 11, PRIMAL_DEEP)
        for position in range(bar):
            sprite.px(5 + position, 4 + 16 * frame, primal_run(position, frame))
    return sprite


def cartridge_top(primal):
    """Top and bottom of the cartridge body. A Primal Cell shows its charge through a slit."""
    def paint(sprite, x, y, w, h, look):
        GRAPHITE_FACE(sprite, x, y, w, h, look)
        if w >= 6 and h >= 6:
            sprite.rect(x + 2, y + 2, x + w - 2, y + h - 2, SHADE)
            sprite.row(y + 2, x + 2, x + w - 2, SEAM)
            if primal:
                for position in range(h - 4):
                    colour = primal_run(position, look.get('frame', 0))
                    sprite.rect(x + w // 2 - 1, y + 2 + position, x + w // 2 + 1, y + 3 + position, colour)
            else:
                sprite.rect(x + w // 2 - 1, y + 3, x + w // 2 + 1, y + h - 3, STEEL_DEEP)
    return paint


def cartridge_side(sprite, x, y, w, h, look):
    """Flank of the cartridge body with the guide rail that runs into the bay."""
    GRAPHITE_FACE(sprite, x, y, w, h, look)
    if w >= 6 and h >= 4:
        sprite.row(y + h // 2 - 1, x + 1, x + w - 1, STEEL_SHADE)
        sprite.row(y + h // 2, x + 1, x + w - 1, SEAM)


def plate_edge(sprite, x, y, w, h, look):
    """Rim of the steel faceplate."""
    sprite.rect(x, y, x + w, y + h, STEEL)
    if h >= w:
        sprite.col(x, y, y + h, TRIM)
        sprite.col(x + w - 1, y, y + h, STEEL_SHADE)
    else:
        sprite.row(y, x, x + w, TRIM)
        sprite.row(y + h - 1, x, x + w, STEEL_SHADE)


def plate_back(sprite, x, y, w, h, look):
    """Back of the faceplate around the neck: the shadow in which a finger takes hold."""
    sprite.rect(x, y, x + w, y + h, STEEL_DEEP)
    sprite.ring(x, y, x + w, y + h, STEEL_SHADE)


def neck(sprite, x, y, w, h, look):
    """The waist behind the faceplate: the handle recess."""
    sprite.rect(x, y, x + w, y + h, WELL)
    sprite.row(y + h - 1, x, x + w, SEAM)


def contacts(sprite, x, y, w, h, look):
    """Copper contact strip at the insertion end."""
    sprite.rect(x, y, x + w, y + h, COPPER)
    if h >= 2:
        sprite.row(y, x, x + w, COPPER_LIGHT)
        sprite.row(y + h - 1, x, x + w, COPPER_DARK)
    if w >= 6:
        for u in range(2, w - 1, 3):
            sprite.col(x + u, y, y + h, SEAM)
