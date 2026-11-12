"""Pixel art of the sealed-cube and alcove machine family.

Every sprite is drawn texel by texel at two texels per model pixel: 32 x 32
for a full block face, 16 x 16 for small parts. Shapes are hard clusters lit
from the top left; there are no gradients. Coordinates below are texels with
the origin at the top left, and ranges exclude their end.
"""
from family_palette import (ACTIVITY_OFF, ACTIVITY_ON, BADGE, BADGE_LIGHT, BASE, CABLE_TIERS, COPPER, COPPER_DARK,
                            COPPER_LIGHT, HIGHLIGHT, IVORY, JACKET, LED, LED_RED_DIM, PORT_MODES, PORT_UNFORMED,
                            PORT_AUTOMATIC, PORT_DARK, PORT_LIGHT, PORT_METAL,
                            SEAM, SHADE, STANDBY, STEEL, STEEL_DEEP, STEEL_SHADE, TRIM, WELL, accent, lighten, scale)
from machine_palette import RGB

CLEAR = (0, 0, 0, 0)


class Sprite:
    def __init__(self, width=32, height=None, fill=BASE):
        self.width = width
        self.height = width if height is None else height
        self.rows = [[self.color(fill)] * width for _ in range(self.height)]

    @staticmethod
    def color(value):
        return tuple(value) if len(value) == 4 else tuple(value) + (255,)

    def px(self, x, y, color):
        self.rows[y][x] = self.color(color)

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px(x, y, color)

    def row(self, y, x0, x1, color):
        self.rect(x0, y, x1, y + 1, color)

    def col(self, x, y0, y1, color):
        self.rect(x, y0, x + 1, y1, color)

    def ring(self, x0, y0, x1, y1, color):
        self.row(y0, x0, x1, color)
        self.row(y1 - 1, x0, x1, color)
        self.col(x0, y0, y1, color)
        self.col(x1 - 1, y0, y1, color)

    def bevel(self, x0, y0, x1, y1, light, dark):
        """A one-texel edge lit from the top left; the two mixed corners keep their colour."""
        self.row(y0, x0, x1 - 1, light)
        self.col(x0, y0, y1 - 1, light)
        self.row(y1 - 1, x0 + 1, x1, dark)
        self.col(x1 - 1, y0 + 1, y1, dark)

    def pixel(self, x, y):
        return self.rows[y][x]


def flat(color):
    return Sprite(16, fill=color)


def casing():
    """The shared block face: dark outline, lit inner edge, plain field."""
    sprite = Sprite(fill=BASE)
    sprite.ring(0, 0, 32, 32, SEAM)
    sprite.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    return sprite


def bolt(sprite, x, y):
    sprite.rect(x, y, x + 2, y + 2, SHADE)
    sprite.px(x, y, HIGHLIGHT)
    sprite.px(x + 1, y + 1, SEAM)


def port_plate(sprite, x=8, y=8, contact=True, shallow=False):
    """The seat of a cable plug: gunmetal ring, dark well and a copper contact.

    A shallow well is only shadowed along its top and left edge: on the cable sides of a machine an empty
    seat must not outweigh the front."""
    sprite.ring(x, y, x + 16, y + 16, SEAM)
    sprite.bevel(x + 1, y + 1, x + 15, y + 15, PORT_LIGHT, PORT_METAL)
    sprite.px(x + 14, y + 1, PORT_METAL)
    sprite.px(x + 1, y + 14, PORT_METAL)
    sprite.rect(x + 2, y + 2, x + 14, y + 14, SEAM if shallow else WELL)
    if shallow:
        sprite.row(y + 2, x + 2, x + 14, WELL)
        sprite.col(x + 2, y + 2, y + 14, WELL)
    if contact:
        sprite.rect(x + 6, y + 6, x + 10, y + 10, COPPER)
        sprite.bevel(x + 6, y + 6, x + 10, y + 10, COPPER_LIGHT, COPPER_DARK)


def casing_port():
    sprite = casing()
    for y in (4, 26):
        sprite.rect(12, y, 20, y + 2, SEAM)
    port_plate(sprite, shallow=True)
    return sprite


def casing_plain():
    sprite = casing()
    for x, y in ((4, 4), (26, 4), (4, 26), (26, 26)):
        bolt(sprite, x, y)
    return sprite


def reactor_casing():
    """Structure plating: the shared frame around one recessed, bolted plate."""
    sprite = casing()
    sprite.bevel(4, 4, 28, 28, SHADE, HIGHLIGHT)
    for x, y in ((6, 6), (24, 6), (6, 24), (24, 24)):
        bolt(sprite, x, y)
    return sprite


def reactor_glass_pane():
    sprite = Sprite(fill=CLEAR)
    sprite.ring(0, 0, 32, 32, SEAM)
    sprite.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    glint = TRIM + (112,)
    for step in range(7):
        sprite.rect(8 + step, 15 - step, 10 + step, 16 - step, glint)
    for step in range(4):
        sprite.rect(15 + step, 20 - step, 17 + step, 21 - step, glint)
    # Corner brackets keep the pane and its item readable on a dark background.
    for step in range(3):
        for y in (2 + step, 29 - step):
            sprite.row(y, 2, 5 - step, STEEL)
            sprite.row(y, 27 + step, 30, STEEL)
    return sprite


def sealed_frame(fill):
    """Front frame of a sealed cube: outline, lit cap and left post, shaded right post."""
    sprite = Sprite(fill=fill)
    sprite.ring(0, 0, 32, 32, SEAM)
    sprite.row(1, 1, 31, HIGHLIGHT)
    sprite.col(1, 1, 31, HIGHLIGHT)
    sprite.col(30, 2, 31, SHADE)
    return sprite


def plinth(sprite, top):
    sprite.rect(2, top, 30, 31, SHADE)
    sprite.row(top, 2, 30, BASE)
    sprite.rect(3, 29, 13, 31, BADGE)
    sprite.row(29, 3, 13, BADGE_LIGHT)


# Bay openings of the Matter Drive front as texel origins; each is 12 x 6.
DRIVE_BAYS = [(x, y) for x in (2, 18) for y in (6, 14, 22)]


def drive_front():
    sprite = sealed_frame(WELL)
    for x in (2, 18):
        sprite.rect(x, 2, x + 12, 6, SEAM)
    sprite.rect(4, 3, 12, 5, WELL)
    for x in (19, 21, 23, 25, 27):
        sprite.rect(x, 3, x + 1, 5, SHADE)
    sprite.rect(14, 2, 18, 28, BASE)
    sprite.col(14, 2, 28, HIGHLIGHT)
    sprite.col(17, 2, 28, SHADE)
    for y in (12, 20):
        for x in (2, 18):
            sprite.row(y, x, x + 12, BASE)
            sprite.row(y + 1, x, x + 12, SHADE)
    plinth(sprite, 28)
    return sprite


def drive_bay():
    sprite = Sprite(fill=SEAM)
    for x, y in DRIVE_BAYS:
        sprite.rect(x + 1, y + 1, x + 11, y + 5, WELL)
        sprite.rect(x + 4, y + 2, x + 8, y + 4, SHADE)
    return sprite


def drive_cell(bar):
    """Faceplate of a mounted cell in texels 0..11 x 0..5; bar is the tier bar length, None for an unreadable cell."""
    body, lip = (STEEL_DEEP, STEEL_SHADE) if bar is None else (STEEL, TRIM)
    sprite = Sprite(16, fill=body)
    sprite.ring(0, 0, 12, 6, WELL)
    sprite.row(1, 4, 11, lip)
    sprite.rect(0, 1, 4, 5, SEAM)
    sprite.row(2, 5, 11, SEAM)
    if bar is not None:
        sprite.row(4, 5, 11, STEEL_DEEP)
        sprite.row(4, 5, 5 + bar, IVORY)
    return sprite


def primal_drive_cell(bar):
    # The mounted front and its item use the same recipe-crystal window and animated Primal rail.
    from hardware_sprites import primal_cell_face
    return primal_cell_face(bar, crystal='violet' if bar in (2, 4) else None)


def led_red():
    sprite = Sprite(16, 32, fill=LED['red'])
    sprite.rect(0, 16, 16, 32, LED_RED_DIM)
    return sprite


def terminal_front():
    sprite = sealed_frame(WELL)
    sprite.rect(2, 24, 30, 31, SHADE)
    sprite.row(24, 2, 30, BASE)
    for y in (26, 29):
        for key in range(8):
            x = 3 + 3 * key
            sprite.row(y - 1, x, x + 2, TRIM)
            sprite.row(y, x, x + 2, STEEL)
    sprite.rect(27, 25, 29, 27, COPPER)
    sprite.row(25, 27, 29, COPPER_LIGHT)
    sprite.rect(27, 28, 29, 30, SEAM)
    return sprite


def terminal_screen(active):
    mint = ACTIVITY_ON
    if active:
        back, head, line, mark, value = (16, 44, 50), mint, scale(mint, .72), IVORY, lighten(mint, .45)
    else:
        # Standby: the layout stays readable against the dark glass and the cursor alone is lit.
        back, head, line, mark, value = WELL, scale(mint, .72), ACTIVITY_OFF, ACTIVITY_OFF, ACTIVITY_OFF
    sprite = Sprite(fill=WELL)
    sprite.rect(2, 2, 30, 24, SEAM)
    sprite.rect(3, 3, 29, 23, back)
    sprite.rect(4, 4, 28, 6, head)
    for index, (length, amount) in enumerate(((12, 4), (8, 6), (13, 2), (6, 5))):
        y = 8 + 3 * index
        sprite.rect(4, y, 6, y + 2, mark)
        sprite.rect(7, y, 7 + length, y + 2, line)
        sprite.rect(28 - amount, y, 28, y + 2, value)
    sprite.rect(4, 20, 6, 22, mint)
    return sprite


def interface_front():
    sprite = sealed_frame(SHADE)
    plinth(sprite, 28)
    for x, y in ((3, 3), (28, 3), (3, 26), (28, 26)):
        sprite.px(x, y, SEAM)
    # Antenna bar with the seat of its signal strip.
    sprite.rect(6, 4, 26, 8, BASE)
    sprite.bevel(6, 4, 26, 8, HIGHLIGHT, SEAM)
    sprite.rect(8, 5, 24, 7, WELL)
    # Port boss: a large plug seat around the status lens.
    sprite.rect(8, 10, 24, 26, BASE)
    sprite.bevel(8, 10, 24, 26, HIGHLIGHT, SEAM)
    sprite.ring(10, 12, 22, 24, SEAM)
    sprite.bevel(11, 13, 21, 23, PORT_LIGHT, PORT_METAL)
    sprite.rect(12, 14, 20, 22, WELL)
    # The contacts carry the device colour; copper stays on the badge.
    for x, y in ((15, 14), (16, 14), (15, 21), (16, 21), (12, 17), (12, 18), (19, 17), (19, 18)):
        sprite.px(x, y, accent('network_interface'))
    return sprite


def machine_port(color, contact=True):
    sprite = casing()
    light, dark = ((PORT_LIGHT, PORT_DARK) if color == PORT_AUTOMATIC
                   else (lighten(color, .28), scale(color, .68)))
    sprite.rect(4, 4, 28, 28, color)
    sprite.bevel(4, 4, 28, 28, light, dark)
    sprite.rect(6, 6, 26, 26, BASE)
    for x, y, width, height in ((14, 6, 4, 2), (14, 24, 4, 2), (6, 14, 2, 4), (24, 14, 2, 4)):
        sprite.rect(x, y, x + width, y + height, color)
    port_plate(sprite, contact=contact)
    return sprite


def cable(tier):
    stripe, width, dashed = tier['stripe'], tier['width'], tier['dashed']
    dark = scale(stripe, .7)
    first = 4 - width // 2
    sprite = Sprite(fill=JACKET)
    # Run strip, 32 x 8, repeating every eight texels. Its rows mirror around the centre line: the block state
    # turns one arm model to all six sides, so any one-sided row would swap sides between opposite arms.
    sprite.row(0, 0, 32, SEAM)
    sprite.row(7, 0, 32, SEAM)
    sprite.rect(0, first, 32, first + width, stripe)
    if dashed:
        for x in range(32):
            if x % 8 in (7, 0):
                sprite.rect(x, 3, x + 1, 5, IVORY)
    # End cap of the 4 x 4 core.
    sprite.bevel(0, 8, 8, 16, SHADE, SEAM)
    sprite.rect(2, 10, 6, 14, stripe)
    sprite.row(13, 2, 6, dark)
    # Face of the 6 x 6 junction hub.
    sprite.ring(8, 8, 20, 20, SEAM)
    sprite.rect(10, 10, 18, 18, PORT_METAL)
    sprite.bevel(10, 10, 18, 18, PORT_LIGHT, PORT_DARK)
    sprite.rect(12, 12, 16, 16, stripe)
    sprite.row(15, 12, 16, dark)
    # Face of the device flange; the shaft hides its centre.
    sprite.rect(20, 8, 32, 20, PORT_METAL)
    sprite.ring(20, 8, 32, 20, SEAM)
    sprite.bevel(21, 9, 31, 19, PORT_LIGHT, PORT_DARK)
    sprite.rect(23, 11, 29, 17, JACKET)
    sprite.ring(22, 10, 30, 18, SEAM)
    # Side of the flange: gunmetal toward the device, jacket and stripe toward the cable.
    sprite.rect(0, 20, 12, 22, PORT_METAL)
    sprite.row(20, 0, 12, PORT_LIGHT)
    sprite.rect(0, 22, 12, 24, SHADE)
    sprite.rect(6 - width // 2, 22, 6 + width // 2, 24, stripe)
    sprite.col(0, 20, 24, SEAM)
    sprite.col(11, 20, 24, SEAM)
    return sprite


def controller_front(device):
    sprite = sealed_frame(SHADE)
    plinth(sprite, 28)
    sprite.rect(5, 5, 27, 17, SEAM)
    sprite.row(3, 6, 12, IVORY)
    if device == 'industrial_reactor':
        for x in (5, 11, 17):
            sprite.rect(x, 19, x + 4, 23, STEEL)
            sprite.bevel(x, 19, x + 4, 23, TRIM, STEEL_SHADE)
            sprite.rect(x + 1, 20, x + 3, 22, WELL)
        sprite.px(12, 20, COPPER)
        sprite.rect(24, 18, 26, 26, SEAM)
        sprite.rect(23, 19, 27, 21, STEEL)
        sprite.row(19, 23, 27, TRIM)
    else:
        for y, knob in ((19, 9), (23, 15)):
            sprite.rect(5, y, 21, y + 2, SEAM)
            sprite.rect(knob, y - 1, knob + 2, y + 3, STEEL)
            sprite.row(y - 1, knob, knob + 2, TRIM)
        sprite.rect(24, 18, 26, 20, COPPER)
        sprite.row(18, 24, 26, COPPER_LIGHT)
        sprite.rect(24, 23, 26, 25, STEEL)
        sprite.row(23, 24, 26, TRIM)
    return sprite


def controller_display(device, active):
    color = accent(device)
    if active:
        back, ink, glow = scale(color, .22), color, lighten(color, .5)
    else:
        back, ink, glow = WELL, scale(color, .57), scale(color, .57)
    sprite = Sprite(fill=SEAM)
    sprite.rect(6, 6, 26, 16, back)
    if device == 'industrial_reactor':
        sprite.rect(7, 8, 13, 14, ink)
        sprite.rect(8, 9, 12, 13, back)
        sprite.rect(9, 10, 11, 12, glow)
        for y, length in ((7, 10), (10, 6), (13, 8)):
            sprite.rect(15, y, 15 + length, y + 2, ink)
        sprite.rect(23, 7, 25, 9, glow)
    else:
        for y, shift in ((8, 0), (12, 1)):
            sprite.rect(7, y, 13, y + 2, ink)
            for x in (7 + shift, 9 + shift, 11 + shift):
                sprite.px(x, y, glow)
        for x, height in ((15, 4), (18, 7), (21, 5), (24, 3)):
            sprite.rect(x, 15 - height, x + 2, 15, ink)
            sprite.row(15 - height, x, x + 2, glow)
    return sprite


def chassis_top():
    """Top of an alcove machine: the rear tower and both cheeks around the open alcove."""
    sprite = casing()
    sprite.rect(4, 0, 28, 20, WELL)
    sprite.col(3, 2, 20, SHADE)
    sprite.col(28, 2, 20, HIGHLIGHT)
    sprite.row(20, 4, 28, HIGHLIGHT)
    sprite.px(3, 20, BASE)
    for x in (6, 24):
        bolt(sprite, x, 25)
    return sprite


def chassis_front(device):
    """Front planes of an alcove machine: cheeks, accent ledge, control strip and skirt."""
    # The hand-operated bench has no Matrix Crystal in its recipe.
    color = STEEL if device == 'hand_preparator' else accent(device)
    sprite = Sprite(fill=WELL)
    sprite.ring(0, 0, 32, 32, SEAM)
    for x, edge in ((1, (HIGHLIGHT, BASE, SHADE)), (28, (HIGHLIGHT, BASE, SHADE))):
        for offset, tone in enumerate(edge):
            sprite.col(x + offset, 1, 31, tone)
    sprite.row(1, 1, 4, HIGHLIGHT)
    sprite.row(1, 28, 31, HIGHLIGHT)
    sprite.rect(4, 24, 28, 26, color)
    sprite.row(24, 4, 28, TRIM if device == 'hand_preparator' else lighten(color, .3))
    sprite.rect(4, 26, 28, 30, SHADE)
    sprite.rect(5, 26, 11, 30, WELL)
    for x in (13, 16):
        sprite.rect(x, 27, x + 2, 29, STEEL)
        sprite.row(27, x, x + 2, TRIM)
    # The type plate is ivory: a copper one would read as a second status light beside warm accents.
    sprite.rect(22, 27, 26, 29, TRIM)
    sprite.row(27, 22, 26, IVORY)
    sprite.row(30, 4, 28, BASE)
    return sprite


def chassis_alcove():
    """Lining of the alcove: back wall, cheek insides and floor share these plates."""
    sprite = Sprite(fill=SHADE)
    sprite.row(0, 0, 32, SEAM)
    for y in (7, 15):
        sprite.row(y, 0, 32, SEAM)
        sprite.row(y + 1, 0, 32, BASE)
    sprite.rect(0, 23, 32, 32, SEAM)
    return sprite



def sprites():
    """Every family sprite by its path below textures/block, without the extension."""
    result = {
        'casing_port': casing_port(),
        'casing_plain': casing_plain(),
        'casing_inner': flat(SEAM),
        'status_off': flat(SEAM),
        'reactor_casing': reactor_casing(),
        'reactor_glass_pane': reactor_glass_pane(),
        'drive_front': drive_front(),
        'drive_bay': drive_bay(),
        'drive_cell_64k': drive_cell(2),
        'drive_cell_256k': drive_cell(4),
        'drive_cell_1m': drive_cell(6),
        'drive_cell_unknown': drive_cell(None),
        'drive_primal_cell_16k': primal_drive_cell(2),
        'drive_primal_cell_64k': primal_drive_cell(4),
        'drive_primal_cell_256k': primal_drive_cell(6),
        'led_red': led_red(),
        'activity_on': flat(ACTIVITY_ON),
        'activity_off': flat(ACTIVITY_OFF),
        'terminal_front': terminal_front(),
        'terminal_screen': terminal_screen(False),
        'terminal_screen_active': terminal_screen(True),
        'interface_front': interface_front(),
        'machine_port_unformed': machine_port(PORT_UNFORMED),
        'chassis_top': chassis_top(),
        'chassis_alcove': chassis_alcove(),
        'chemical_reactor_front': chassis_front('chemical_reactor'),
    }
    from bench_showpieces import DEVICES, bench_sprites
    result.update(bench_sprites(Sprite))
    for device in DEVICES:result[device+'_front']=chassis_front(device)
    from reactor_showpiece import reactor_sprites
    result.update(reactor_sprites(Sprite))
    for name in ('blue', 'green', 'amber'):
        result['led_' + name] = flat(LED[name])
    for name in ('violet', 'blue', 'ice', 'copper', 'leaf', 'amber', 'ember'):
        result['light_' + name] = flat(RGB[name])
    result['light_blue_standby'] = flat(scale(RGB['blue'], STANDBY))
    for mode, color in PORT_MODES.items():
        result['machine_port_' + mode] = machine_port(color, contact=mode != 'disabled')
    for name, tier in CABLE_TIERS.items():
        result[name] = cable(tier)
    for device in ('industrial_reactor', 'industrial_preparator'):
        result[device + '_front'] = controller_front(device)
        result[device + '_display'] = controller_display(device, False)
        result[device + '_display_active'] = controller_display(device, True)
    return result


# Animation metadata by sprite name.
ANIMATIONS = {'led_red': {'animation': {'frametime': 10}}}
for name in ('drive_primal_cell_16k', 'drive_primal_cell_64k', 'drive_primal_cell_256k'):
    ANIMATIONS[name] = {'animation': {'frametime': 3}}

# Active sight windows are animated only in the active model.
from reactor_showpiece import REACTOR_ANIMATIONS
ANIMATIONS.update(REACTOR_ANIMATIONS)
