"""Sixteen-frame, palette-only work strips. Direction follows the real flow of the device.

The painters are independent from their file writers. Static and animated art use the same tiles;
interpolation is atlas interpolation, never a gradient painted into a frame.
"""
import math
import family_palette as P
from family_palette import lighten,scale
from machine_palette import RGB
import family_sprites as FS
from family_sprites import Sprite
import hardware_sprites as HS
import reactor_showpiece as RS
N=16
BREATH=[0,0,1,1,2,2,3,3,3,3,2,2,1,1,0,0]

def strip(frames):
 frames=list(frames)
 out=Sprite(frames[0].width,frames[0].height*len(frames))
 for index,image in enumerate(frames):
  for y in range(image.height):
   for x in range(image.width):out.px(x,y+index*image.height,image.pixel(x,y))
 return out

def tones(c):
    return {'deep': scale(c, .38), 'dim': scale(c, .62), 'low': scale(c, .8), 'body': c,
            'light': lighten(c, .45), 'glint': lighten(c, .72)}

def reactor_liquid(active, frame=0, bubbles=True, level_bob=None):
    """The shipped sight-glass art; active frames add rising bubbles (and, for the baked variant, a bobbing level)."""
    sprite = Sprite(32, 32, fill=P.SEAM)
    violet = RGB['violet']
    body, light, shade = (violet, lighten(violet, .45), scale(violet, .64)) if active else (
        scale(violet, .62), scale(violet, .84), scale(violet, .42))
    glint = lighten(violet, .72)
    for x, y, w, h in set(RS.regions(RS.liquid_boxes()[0]).values()):
        sprite.rect(x, y, x + w, y + h, body)
        sprite.col(x, y, y + h, light)
        sprite.row(y + h - 1, x, x + w, shade)
        if h == 6:
            sprite.row(y, x, x + w, P.STEEL)
            sprite.row(y + h - 1, x, x + w, P.STEEL_DEEP)
            sprite.col(x + 1, y + 1, y + h - 1, light)
            if level_bob is not None:
                # Baked variant: the top one or two interior rows are dark glass above the meniscus.
                for row in range(level_bob):
                    sprite.row(y + 1 + row, x, x + w, P.WELL)
                sprite.row(y + 1 + level_bob, x + 2, x + w, light)
            if active and bubbles:
                tracks = ((3, 0), (4, 8)) if w == 6 else ((3, 0), (6, 5), (8, 11))
                for column, start in tracks:
                    step = (frame - start) % N
                    if step < 8:                      # four interior rows, two frames each, then eight frames rest
                        row = y + 4 - step // 2
                        if level_bob is None or row > y + 1 + level_bob:
                            sprite.px(x + column, row, glint)
    return sprite

def reactor_glass():
    sprite = Sprite(32, 32, fill=P.SEAM)
    for x, y, w, h in set(RS.regions(RS.liquid_boxes()[0]).values()):
        sprite.rect(x, y, x + w, y + h, P.WELL)
        sprite.col(x, y, y + h, P.PORT_DARK)
        if h == 6:
            sprite.row(y, x, x + w, P.STEEL)
            sprite.row(y + h - 1, x, x + w, P.STEEL_DEEP)
    return sprite

def reactor_shuttle():
    violet = RGB['violet']
    sprite = Sprite(16, 16, fill=violet)
    sprite.row(0, 0, 16, lighten(violet, .45))
    sprite.col(0, 0, 16, lighten(violet, .45))
    sprite.row(1, 1, 16, violet)
    return sprite

def flow_sheet(hue, frame, direction=1, period=16):
    """A lit plate over which one soft band travels (down for +1). Used on small work plates."""
    t = tones(hue)
    sprite = Sprite(32, 32, fill=t['body'])
    for y in range(32):
        step = (direction * (y - frame)) % period
        if step == 0:
            sprite.row(y, 0, 32, t['light'])
        elif step == period - 1:
            sprite.row(y, 0, 32, lighten(hue, .2))
    return sprite

def breath_sheet(hue, frame, levels=None):
    levels = levels or (scale(hue, .8), hue, lighten(hue, .22), lighten(hue, .45))
    tone = levels[BREATH[frame]]
    sprite = Sprite(32, 32, fill=tone)
    for y in range(1, 32, 4):
        sprite.row(y, 0, 32, lighten(tone, .22) if BREATH[frame] < 3 else lighten(hue, .6))
    return sprite

def heat_sheet(frame):
    """Sinter heat: ember cells rise one texel per frame over a hot floor and a darker crown."""
    e = RGB['ember']
    t = {'crown': scale(e, .62), 'body': e, 'warm': lighten(e, .2), 'hot': lighten(e, .45), 'white': lighten(e, .72)}
    sprite = Sprite(32, 32, fill=t['body'])
    for y in range(32):
        local = y % 12                       # the visible plate is twelve texels tall; rows repeat below it
        if local < 2:
            sprite.row(y, 0, 32, t['crown'])
        elif local >= 10:
            sprite.row(y, 0, 32, t['warm'])
    for k in range(16):
        x = k * 2
        base = (k * 5 + 3) % 16
        for y in range(32):
            step = (y + frame - base) % 16     # cells move up: a larger frame shows the cell at a smaller y
            if step == 0:
                sprite.rect(x, y, x + 2, y + 1, t['hot'])
            elif step == 1:
                sprite.rect(x, y, x + 2, y + 1, t['white'] if k % 3 == 0 else t['hot'])
            elif step == 2:
                sprite.rect(x, y, x + 2, y + 1, t['warm'])
    return sprite

def generator_window(name, frame):
    import generate_progression_assets as GPA
    image = GPA.display_frame(name, True)
    e = RGB['ember']
    hot, white = lighten(e, .45), lighten(e, .72)
    centers = (10, 21) if name.startswith('industrial') else (16,)
    for index, center in enumerate(centers):
        f = (frame + 8 * index) % N
        # Energy leaves the crystal: a band sinks from the tip (row 8) to the foot (row 20), then the holder takes it.
        for y in range(8, 21):
            half = max(0, 4 - abs(y - 14) // 2)
            step = (y - 8) - f
            if step in (0, -1):
                tone = white if step == 0 else hot
                if half > 0:
                    image.rect(center - half, y, center + half + 1, y + 1, tone)
                else:
                    image.px(center, y, tone)
        image.row(21, center - 4, center + 5, white if f in (13, 14) else hot if f in (12, 15) else P.STEEL)
    return image

def controller_display(device, frame):
    sprite = FS.controller_display(device, True)
    color = P.accent(device)
    back, ink, glow = scale(color, .22), color, lighten(color, .5)
    wave = lambda phase: math.sin(2 * math.pi * (frame / N + phase))
    if device == 'industrial_reactor':
        sprite.rect(15, 7, 26, 15, back)
        for index, (y, length) in enumerate(((7, 8), (10, 6), (13, 7))):
            n = length + round(2 * wave(index / 3))
            sprite.rect(15, y, 15 + n, y + 2, ink)
            sprite.px(15 + n - 1, y, glow); sprite.px(15 + n - 1, y + 1, glow)
        sprite.rect(9, 10, 11, 12, glow if wave(0) > -.3 else ink)
        sprite.rect(23, 7, 25, 9, back)
    else:
        sprite.rect(15, 7, 26, 16, back)
        for index, (x, height) in enumerate(((15, 4), (18, 6), (21, 5), (24, 3))):
            hgt = height + round(1.6 * wave(index / 4))
            sprite.rect(x, 15 - hgt, x + 2, 15, ink)
            sprite.row(15 - hgt, x, x + 2, glow)
    return sprite

def terminal_screen(frame):
    sprite = FS.terminal_screen(True)
    mint = P.ACTIVITY_ON
    line, bright = scale(mint, .72), lighten(mint, .3)
    row = frame // 4
    for index, (length, amount) in enumerate(((12, 4), (8, 6), (13, 2), (6, 5))):
        if index == row:
            y = 8 + 3 * index
            sprite.rect(7, y, 7 + length, y + 2, mint)
            sprite.rect(28 - amount, y, 28, y + 2, bright)
    sprite.rect(4, 20, 6, 22, mint if frame % 8 < 4 else scale(mint, .72))
    return sprite

def interface_light(frame):
    blue = RGB['blue']
    t = tones(blue)
    sprite = Sprite(32, 32, fill=blue)
    sprite.rect(8, 5, 24, 7, t['dim'])
    d = frame // 2                                   # 0..7 texels from the centre outward
    for offset, tone in ((0, 'glint'), (-1, 'light'), (-2, 'body')):
        x = d + offset
        if 0 <= x <= 7:
            sprite.rect(16 + x, 5, 17 + x, 7, t[tone]); sprite.rect(15 - x, 5, 16 - x, 7, t[tone])
    level = (blue, lighten(blue, .15), lighten(blue, .3), lighten(blue, .45))[BREATH[frame]]
    sprite.rect(14, 16, 18, 20, level)
    return sprite

def activity_bar(frame):
    mint = P.ACTIVITY_ON
    return Sprite(32, 32, fill=(scale(mint, .86), mint, lighten(mint, .1), lighten(mint, .2))[BREATH[frame]])

def energy_core(charge, frame, direction):
    import generate_energy_assets as GEA
    image = GEA.core(charge, GEA.ENERGY_CUBE_LOOK)
    hue = RGB[GEA.CRYSTAL]
    height = round(20 * charge / 8)
    top = 26 - height
    for y in range(top + 1, 26):
        step = (direction * (frame - (26 - y))) % 16 if direction == 1 else ((26 - y) + frame) % 16
        # in: the band climbs from the floor to the surface; out: it sinks from the surface to the floor
        if step == 0:
            image.row(y, 7, 25, lighten(hue, .4))
        elif step == (15 if direction == 1 else 1):
            image.row(y, 7, 25, lighten(hue, .2))
    return image

def animated_crystal(hue, style):
    base = HS.crystal(hue)

    def paint(sprite, x, y, w, h, look):
        frame, active = look.get('frame', 0), look.get('active', False)
        body, light, dark, glint = HS.tones(hue, active)
        if not active:
            return base(sprite, x, y, w, h, look)
        if style == 'breath':
            shift = .1 * math.sin(2 * math.pi * frame / N)
            for v in range(h):
                for u in range(w):
                    along = ((u + .5) / w + (v + .5) / h) / 2
                    sprite.px(x + u, y + v, light if along < .36 + shift else body if along < .7 + shift else dark)
            if w >= 2 and h >= 2:
                sprite.row(y + h - 1, x, x + w, dark)
            if w >= 3 and h >= 3:
                sprite.col(x + w - 1, y + 1, y + h, dark)
                sprite.px(x + 1, y + 1, glint)
            return
        base(sprite, x, y, w, h, look)
        if style == 'rise' and h < 12 or style == 'fall' and h != 6:
            return
        period = 16 if style == 'rise' else 8
        for v in range(h):
            step = (v + frame) % period if style == 'rise' else (v - frame) % period
            if step == 0:
                sprite.row(y + v, x, x + w - 1, glint)
            elif step == 1 and style == 'rise' or step == period - 1 and style == 'fall':
                sprite.row(y + v, x, x + w - 1, light)
    return paint

def animated_liquid(hue):
    def paint(sprite, x, y, w, h, look):
        active, frame = look.get('active', False), look.get('frame', 0)
        body, light, dark, glint = HS.tones(hue, active)
        if not active:
            body, light, dark = scale(hue, .3), scale(hue, .44), scale(hue, .22)
        sprite.rect(x, y, x + w, y + h, body)
        if w < 12 or h < 12:
            sprite.row(y, x, x + w, light)
            return
        for turn in range(4):
            for index, (u, v) in enumerate(HS.SWIRL_ARM):
                for _ in range(turn):
                    u, v = -v, u
                u, v = int(x + w / 2 - .5 + u), int(y + h / 2 - .5 + v)
                if x < u < x + w - 1 and y < v < y + h - 1:
                    step = (2 * index - frame) % 16
                    sprite.px(u, v, (glint if step < 3 else light if step < 6 else lighten(body, .2)) if active else light)
        sprite.ring(x, y, x + w, y + h, dark)
    return paint
