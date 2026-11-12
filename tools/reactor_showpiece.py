"""Twin reaction columns and their two-texels-per-pixel surface art.

Sprites keep opposite faces on the same small material tile. Each metal
part has its own 32-pixel sheet, so sharing never changes texture density.
The active sight windows animate flow; geometry is fixed in every frame.
"""
from family_geometry import Box, mapped_uv
from family_palette import (SEAM, STEEL, STEEL_DEEP, STEEL_SHADE, TRIM, accent, lighten, scale)

NATURAL = {'north': ('-x', '-y'), 'south': ('+x', '-y'), 'west': ('+z', '-y'), 'east': ('-z', '-y'),
           'up': ('+x', '+z'), 'down': ('+x', '-z')}
PREFIX = 'chemical_reactor_columns_'
REACTOR_LIQUID='renderer'  # recommended; 'baked' keeps the smaller texture-only alternative
REACTOR_ANIMATIONS = {PREFIX + 'liquid_active': {'animation': {'frametime': 3,'interpolate': True}}}

# (name, from, to, material); the two columns share corresponding material sheets.
PARTS = (
    ('column_base', (3, 4, 3), (13, 5, 8), 'base'),
    ('left_lower', (3, 5, 3), (6, 7, 8), 'lower'),
    ('left_upper', (3, 10, 3), (6, 13, 8), 'upper'),
    ('right_lower', (10, 5, 3), (13, 7, 8), 'lower'),
    ('right_upper', (10, 10, 3), (13, 13, 8), 'upper'),
    ('manifold', (3, 13, 4), (13, 15, 7), 'manifold'),
)
LIQUIDS = (
    ('left_sight', (3, 7, 3), (6, 10, 8)),
    ('right_sight', (10, 7, 3), (13, 10, 8)),
)
MATERIALS = {name: material for name, _, _, material in PARTS}


def column_boxes():
    return [Box(name, lo, hi, flat=True) for name, lo, hi, _ in PARTS]


def liquid_boxes():
    return [Box(name, lo, hi, flat=True) for name, lo, hi in LIQUIDS]


def regions(box):
    """Front, side and top tiles; opposite faces intentionally share a finish."""
    w, h, d = (round(value * 2) for value in box.size())
    if w + d > 32 or h + d > 32:
        raise ValueError(f'{box.name}: face tiles leave the 32-pixel sprite')
    return {'north': (0, 0, w, h), 'south': (0, 0, w, h),
            'east': (w, 0, d, h), 'west': (w, 0, d, h),
            'up': (0, h, w, d), 'down': (0, h, w, d)}


def skin(box, face, key):
    x, y, _, _ = regions(box)[face]
    return key, *mapped_uv(face, box.lo, box.hi, *NATURAL[face], (x, y))


def column_skin(box, face):
    return skin(box, face, MATERIALS[box.name])


def liquid_skin(box, face):
    return skin(box, face, 'liquid')


def _metal_sprite(Sprite, box, dark):
    sprite = Sprite(fill=SEAM)
    base, light, shade = (STEEL_DEEP, STEEL_SHADE, SEAM) if dark else (STEEL, TRIM, STEEL_SHADE)
    for x, y, w, h in set(regions(box).values()):
        sprite.rect(x, y, x + w, y + h, base)
        sprite.row(y, x, x + w, light)
        sprite.col(x, y, y + h, light)
        sprite.row(y + h - 1, x, x + w, shade)
        sprite.col(x + w - 1, y, y + h, shade)
        if w >= 6 and h >= 4:
            sprite.row(y + h // 2, x + 2, x + w - 2, shade)
            if h >= 6:
                sprite.rect(x + 1, y + 1, x + 3, y + 3, light)
    return sprite


def _liquid_sprite(Sprite, active):
    if active:
        from work_sprites import strip,reactor_liquid
        return strip(reactor_liquid(True,f,level_bob=(0,0,0,0,1,1,1,1,2,2,2,2,1,1,1,1)[f] if REACTOR_LIQUID=='baked' else None) for f in range(16))
    frames = 1
    sprite = Sprite(32, 32 * frames, fill=SEAM)
    violet = accent('chemical_reactor')
    body, light, shade = (violet, lighten(violet, .45), scale(violet, .64)) if active else (
        scale(violet, .62), scale(violet, .84), scale(violet, .42))
    for frame in range(frames):
        for x, y, w, h in set(regions(liquid_boxes()[0]).values()):
            y += frame * 32
            sprite.rect(x, y, x + w, y + h, body)
            sprite.col(x, y, y + h, light)
            sprite.row(y + h - 1, x, x + w, shade)
            if h == 6:  # side sight glass: narrow steel lips enclose the coloured window.
                sprite.row(y, x, x + w, STEEL)
                sprite.row(y + h - 1, x, x + w, STEEL_DEEP)
                sprite.col(x + 1, y + 1, y + h - 1, light)
                if active:
                    sprite.px(x + w - 2, y + 1 + frame, lighten(violet, .72))
            elif active:
                sprite.row(y + 1 + frame, x + 1, x + w - 1, light)
    return sprite


def reactor_sprites(Sprite):
    """Called by family_sprites after Sprite has been defined; no circular import."""
    result = {}
    for box in column_boxes():
        material = MATERIALS[box.name]
        result[PREFIX + material] = _metal_sprite(Sprite, box, material == 'base')
    from work_sprites import reactor_glass,reactor_shuttle
    result[PREFIX+'glass']=reactor_glass()
    result[PREFIX+'shuttle']=reactor_shuttle()
    result[PREFIX + 'liquid'] = _liquid_sprite(Sprite, False)
    result[PREFIX + 'liquid_active'] = _liquid_sprite(Sprite, True)
    return result
