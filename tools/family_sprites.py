"""Pixel art of the sealed-cube and alcove machine family.

Every sprite is drawn texel by texel at two texels per model pixel: 32 x 32
for a full block face, 16 x 16 for small parts. Shapes are hard clusters lit
from the top left; there are no gradients. Coordinates below are texels with
the origin at the top left, and ranges exclude their end.
"""
from family_palette import (ACTIVITY_OFF, ACTIVITY_ON, BADGE, BADGE_LIGHT, BASE, CABLE_TIERS, COPPER, COPPER_DARK,
                            COPPER_LIGHT, HIGHLIGHT, IVORY, JACKET, LED, LED_RED_DIM, PORT_MODES, PORT_UNFORMED,
                            PORT_AUTOMATIC, PORT_DARK, PORT_LIGHT, PORT_METAL,
                            PRIMAL_BODY, PRIMAL_COOL, PRIMAL_CORE, PRIMAL_CORE_SOFT, PRIMAL_GOLD, PRIMAL_TEAL, PRIMAL_WARM,
                            SEAM, SHADE, STANDBY, STEEL, STEEL_DEEP, STEEL_SHADE, TRIM, WELL, accent, lighten, scale)
from machine_palette import RGB

CLEAR = (0, 0, 0, 0)
# Automatic wears a neutral light-graphite ring, clearly brighter than the casing and without rainbow segments.
PORT_AUTOMATIC_STYLES = ('solid_bevel', 'notched_bevel')
PORT_AUTOMATIC_STYLE = 'solid_bevel'
# One-line switch for Matter Cable texture style & animation (synced with generate_family_assets.py).
CABLE_LOOKS = ('pcb_traces', 'fiber_trace', 'glass_conduit', 'conduit_cage', 'classic_sleek')
CABLE_LOOK = 'pcb_traces'
CABLE_ANIMS = ('single_pulse', 'idle_quiet', 'steady_flow')
CABLE_ANIM = 'single_pulse'
# One-line switch for powered vs unpowered Matter Cable visual contrast (all keep pure tier hue, never grey inside):
#   rail_stripes (recommended): both powered and unpowered share the exact same vivid inner PCB traces & animation,
#                               while outer rail stripes are neutral grey (PORT_LIGHT, light 4) without FE and
#                               glowing tier colour (light 15) while FE flows!
#   hollow_sleep:               unpowered = dormant dashed dark-hue segments & unlit hub cores; powered = continuous vivid traces
#   deep_to_vivid:              same slim trace geometry; deep dark hue unpowered vs vivid blazing powered
CABLE_POWER_CONTRASTS = ('rail_stripes', 'hollow_sleep', 'deep_to_vivid')
CABLE_POWER_CONTRAST = 'rail_stripes'
# One-line switch for PCB trace layout inside the dark WELL channel (all seamless across straight_full runs):
#   classic_basic (recommended): original crisp 90° dual traces alternating over/under bridges at x=4 and x=12
#   stepped_bus:                 two unbroken parallel motherboard bus lanes stepping inward/outward with corner vias
#   woven_helix:                 two unbroken interwoven 90° zigzag lanes meeting at glowing crossover nodes at x=4 & x=12
CABLE_TRACE_PATTERNS = ('classic_basic', 'stepped_bus', 'woven_helix')
CABLE_TRACE_PATTERN = 'stepped_bus'


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


def reactor_casing_port():
    """The structure plating with the seat of a port plate, for a connected side of a device in that casing."""
    sprite = reactor_casing()
    port_plate(sprite, shallow=True)
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


PRIMAL_RING_CYCLE = (
    PRIMAL_TEAL, PRIMAL_COOL, PRIMAL_BODY, PRIMAL_WARM,
    PRIMAL_GOLD, PRIMAL_WARM, PRIMAL_BODY, PRIMAL_COOL,
)


def _perimeter_coords(lo, hi):
    """Clockwise perimeter pixel list around rectangle [lo, hi) x [lo, hi)."""
    pts = []
    for x in range(lo, hi):
        pts.append((x, lo))
    for y in range(lo + 1, hi):
        pts.append((hi - 1, y))
    for x in range(hi - 2, lo - 1, -1):
        pts.append((x, hi - 1))
    for y in range(hi - 2, lo, -1):
        pts.append((lo, y))
    return pts


def _armored_corners(sprite, high=TRIM, mid=STEEL, shade=STEEL_SHADE, deep=STEEL_DEEP):
    """Heavy sculpted steel corner brackets on a 32 x 32 high-tier reactor face."""
    # Top-left L-bracket
    sprite.rect(1, 1, 6, 3, mid)
    sprite.rect(1, 3, 3, 6, mid)
    sprite.row(1, 1, 6, high)
    sprite.col(1, 1, 6, high)
    # Top-right L-bracket
    sprite.rect(26, 1, 31, 3, mid)
    sprite.rect(29, 3, 31, 6, shade)
    sprite.row(1, 26, 31, high)
    # Bottom-left L-bracket
    sprite.rect(1, 26, 3, 31, mid)
    sprite.rect(3, 29, 6, 31, shade)
    sprite.col(1, 26, 31, high)
    # Bottom-right L-bracket
    sprite.rect(26, 29, 31, 31, deep)
    sprite.rect(29, 26, 31, 29, shade)


def transmutation_reactor_casing_frame(frame):
    """Late-game Transmutation Reactor armored plating with Violet & Ember energy channels."""
    sprite = casing()
    _armored_corners(sprite)
    # Recessed energy conduit bed (4..28)
    sprite.rect(4, 4, 28, 28, WELL)
    v_mid, v_hi, v_dim = RGB['violet'], lighten(RGB['violet'], .38), scale(RGB['violet'], .45)
    e_mid, e_hi, e_dim = RGB['ember'], lighten(RGB['ember'], .38), scale(RGB['ember'], .45)
    # Top & left conduit channels carry Violet; bottom & right carry Ember, with smooth traveling pulse.
    sprite.row(4, 5, 27, v_dim)
    sprite.col(4, 5, 27, v_dim)
    sprite.row(27, 5, 27, e_dim)
    sprite.col(27, 5, 27, e_dim)
    pos = (frame * 22) // 16
    for offset in range(7):
        t = (pos + offset) % 22
        col_v = v_hi if offset in (2, 3, 4) else v_mid
        col_e = e_hi if offset in (2, 3, 4) else e_mid
        sprite.px(5 + t, 4, col_v)
        sprite.px(4, 26 - t, col_v)
        sprite.px(26 - t, 27, col_e)
        sprite.px(27, 5 + t, col_e)
    # Solid static exact-hue corner anchor pips (guarantee >= 8 exact Violet & Ember texels every frame)
    sprite.rect(4, 4, 6, 6, v_mid)
    sprite.rect(26, 4, 28, 6, v_mid)
    sprite.rect(4, 26, 6, 28, e_mid)
    sprite.rect(26, 26, 28, 28, e_mid)
    # Clean blank inner steel plate matching reactor_casing() (no concentric rings)
    sprite.rect(6, 6, 26, 26, BASE)
    sprite.bevel(6, 6, 26, 26, SHADE, HIGHLIGHT)
    for x, y in ((8, 8), (22, 8), (8, 22), (22, 22)):
        bolt(sprite, x, y)
    return sprite


def transmutation_reactor_glass_pane():
    """Armored containment glass for the Transmutation Reactor with Violet & Ember corner clamps."""
    sprite = Sprite(fill=CLEAR)
    sprite.ring(0, 0, 32, 32, SEAM)
    sprite.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    sprite.ring(2, 2, 30, 30, STEEL_DEEP)
    _armored_corners(sprite)
    # Static exact-hue crystal clamp pins in the corners
    sprite.rect(2, 2, 4, 4, RGB['violet'])
    sprite.rect(2, 28, 4, 30, RGB['violet'])
    sprite.rect(28, 2, 30, 4, RGB['ember'])
    sprite.rect(28, 28, 30, 30, RGB['ember'])
    glint = TRIM + (112,)
    for step in range(7):
        sprite.rect(8 + step, 15 - step, 10 + step, 16 - step, glint)
    for step in range(4):
        sprite.rect(15 + step, 20 - step, 17 + step, 21 - step, glint)
    return sprite


def primal_reactor_casing_frame(frame):
    """Endgame Primal Reactor casing: animated 5-stop Primal border + clean blank inner plate."""
    sprite = casing()
    sprite.rect(2, 2, 30, 30, SHADE)
    _armored_corners(sprite)
    # Recessed Primal conduit track (4..28)
    sprite.rect(4, 4, 28, 28, WELL)
    ring = _perimeter_coords(4, 28)
    n = len(ring)
    shift = (frame * n) // 16
    for i, (px, py) in enumerate(ring):
        phase = ((i - shift) * len(PRIMAL_RING_CYCLE)) // n
        c = PRIMAL_RING_CYCLE[phase % len(PRIMAL_RING_CYCLE)]
        sprite.px(px, py, c)
    # Two opposing white-hot Primal core sparks orbiting the conduit ring
    for spark_idx in (shift % n, (shift + n // 2) % n):
        sx, sy = ring[spark_idx]
        sprite.px(sx, sy, PRIMAL_CORE)
        px_prev, py_prev = ring[(spark_idx - 1) % n]
        px_next, py_next = ring[(spark_idx + 1) % n]
        sprite.px(px_prev, py_prev, PRIMAL_CORE_SOFT)
        sprite.px(px_next, py_next, PRIMAL_CORE_SOFT)
    # Clean blank inner graphite plate matching reactor_casing() (no center dot, no concentric rings)
    sprite.rect(6, 6, 26, 26, BASE)
    sprite.bevel(6, 6, 26, 26, SHADE, HIGHLIGHT)
    for x, y in ((8, 8), (22, 8), (8, 22), (22, 22)):
        bolt(sprite, x, y)
    # Four static flat Violet crystal studs in the steel corner clasps (marks violet_matrix_crystal in recipe)
    for cx, cy in ((2, 2), (28, 2), (2, 28), (28, 28)):
        sprite.rect(cx, cy, cx + 2, cy + 2, RGB['violet'])
    return sprite


def primal_reactor_glass_pane_frame(frame):
    """Endgame Primal containment glass: animated 5-stop Primal inner filament + static Violet corner pins."""
    sprite = Sprite(fill=CLEAR)
    sprite.ring(0, 0, 32, 32, SEAM)
    sprite.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    sprite.ring(2, 2, 30, 30, STEEL_DEEP)
    _armored_corners(sprite)
    # Inner 1-texel animated Primal filament on frame edge (3..29, fully opaque so alpha stays identical across frames)
    ring = _perimeter_coords(3, 29)
    n = len(ring)
    shift = (frame * n) // 16
    for i, (px, py) in enumerate(ring):
        phase = ((i - shift) * len(PRIMAL_RING_CYCLE)) // n
        sprite.px(px, py, PRIMAL_RING_CYCLE[phase % len(PRIMAL_RING_CYCLE)])
    for spark_idx in (shift % n, (shift + n // 2) % n):
        sx, sy = ring[spark_idx]
        sprite.px(sx, sy, PRIMAL_CORE)
    # Static flat Violet crystal pins in the 4 corner clasps
    for cx, cy in ((2, 2), (28, 2), (2, 28), (28, 28)):
        sprite.rect(cx, cy, cx + 2, cy + 2, RGB['violet'])
    glint = TRIM + (112,)
    for step in range(7):
        sprite.rect(8 + step, 15 - step, 10 + step, 16 - step, glint)
    for step in range(4):
        sprite.rect(15 + step, 20 - step, 17 + step, 21 - step, glint)
    return sprite


def energy_matrix_casing():
    """Energy Matrix dedicated structural casing: dark gunmetal corners, Ember conductor rail, blank inner plate."""
    sprite = casing()
    # Sleek gunmetal corner clasps (distinct from heavy armored reactor brackets)
    for cx, cy in ((1, 1), (27, 1), (1, 27), (27, 27)):
        sprite.rect(cx, cy, cx + 4, cy + 4, PORT_METAL)
        sprite.bevel(cx, cy, cx + 4, cy + 4, PORT_LIGHT, PORT_DARK)
    # Recessed conductor channel (4..28) with static Ember perimeter rail and copper contact studs
    sprite.rect(4, 4, 28, 28, WELL)
    e_mid, e_dim = RGB['ember'], scale(RGB['ember'], .55)
    sprite.ring(4, 4, 28, 28, e_dim)
    sprite.row(4, 8, 24, e_mid)
    sprite.row(27, 8, 24, e_mid)
    sprite.col(4, 8, 24, e_mid)
    sprite.col(27, 8, 24, e_mid)
    for cx, cy in ((4, 4), (26, 4), (4, 26), (26, 26)):
        sprite.rect(cx, cy, cx + 2, cy + 2, COPPER)
        sprite.px(cx, cy, COPPER_LIGHT)
    # Clean blank inner graphite plate
    sprite.rect(6, 6, 26, 26, BASE)
    sprite.bevel(6, 6, 26, 26, SHADE, HIGHLIGHT)
    for x, y in ((8, 8), (22, 8), (8, 22), (22, 22)):
        bolt(sprite, x, y)
    return sprite


def energy_matrix_glass_pane():
    """Energy Matrix structural glass: gunmetal corner brackets + Ember accent pins + translucent glint."""
    sprite = Sprite(fill=CLEAR)
    sprite.ring(0, 0, 32, 32, SEAM)
    sprite.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    sprite.ring(2, 2, 30, 30, PORT_DARK)
    for cx, cy in ((1, 1), (27, 1), (1, 27), (27, 27)):
        sprite.rect(cx, cy, cx + 4, cy + 4, PORT_METAL)
        sprite.bevel(cx, cy, cx + 4, cy + 4, PORT_LIGHT, PORT_DARK)
    for cx, cy in ((2, 2), (28, 2), (2, 28), (28, 28)):
        sprite.rect(cx, cy, cx + 2, cy + 2, RGB['ember'])
    glint = TRIM + (112,)
    for step in range(7):
        sprite.rect(8 + step, 15 - step, 10 + step, 16 - step, glint)
    for step in range(4):
        sprite.rect(15 + step, 20 - step, 17 + step, 21 - step, glint)
    return sprite


def energy_reactor_casing_frame(frame):
    """Endgame Energy Reactor armored plating with animated Amber & Ember thermal conduit channels and blank plate."""
    sprite = casing()
    _armored_corners(sprite)
    sprite.rect(4, 4, 28, 28, WELL)
    a_mid, a_hi, a_dim = RGB['amber'], lighten(RGB['amber'], .38), scale(RGB['amber'], .45)
    e_mid, e_hi, e_dim = RGB['ember'], lighten(RGB['ember'], .38), scale(RGB['ember'], .45)
    sprite.row(4, 5, 27, a_dim)
    sprite.col(4, 5, 27, a_dim)
    sprite.row(27, 5, 27, e_dim)
    sprite.col(27, 5, 27, e_dim)
    pos = (frame * 22) // 16
    for offset in range(7):
        t = (pos + offset) % 22
        col_a = a_hi if offset in (2, 3, 4) else a_mid
        col_e = e_hi if offset in (2, 3, 4) else e_mid
        sprite.px(5 + t, 4, col_a)
        sprite.px(4, 26 - t, col_a)
        sprite.px(26 - t, 27, col_e)
        sprite.px(27, 5 + t, col_e)
    sprite.rect(4, 4, 6, 6, a_mid)
    sprite.rect(26, 4, 28, 6, a_mid)
    sprite.rect(4, 26, 6, 28, e_mid)
    sprite.rect(26, 26, 28, 28, e_mid)
    sprite.rect(6, 6, 26, 26, BASE)
    sprite.bevel(6, 6, 26, 26, SHADE, HIGHLIGHT)
    for x, y in ((8, 8), (22, 8), (8, 22), (22, 22)):
        bolt(sprite, x, y)
    return sprite


def energy_reactor_glass_pane():
    """Armored containment glass for the Energy Reactor with Amber & Ember corner clamps."""
    sprite = Sprite(fill=CLEAR)
    sprite.ring(0, 0, 32, 32, SEAM)
    sprite.bevel(1, 1, 31, 31, HIGHLIGHT, SHADE)
    sprite.ring(2, 2, 30, 30, STEEL_DEEP)
    _armored_corners(sprite)
    sprite.rect(2, 2, 4, 4, RGB['amber'])
    sprite.rect(2, 28, 4, 30, RGB['amber'])
    sprite.rect(28, 2, 30, 4, RGB['ember'])
    sprite.rect(28, 28, 30, 30, RGB['ember'])
    glint = TRIM + (112,)
    for step in range(7):
        sprite.rect(8 + step, 15 - step, 10 + step, 16 - step, glint)
    for step in range(4):
        sprite.rect(15 + step, 20 - step, 17 + step, 21 - step, glint)
    return sprite


def _primal_edge_color(index, total, frame):
    """Synchronized 5-stop Primal spectrum color along a connected glass perimeter edge."""
    shift = (frame * total) // 16
    pos = (index - shift) % total
    if pos == 0:
        return PRIMAL_CORE
    if pos in (1, total - 1):
        return PRIMAL_CORE_SOFT
    phase = (pos * len(PRIMAL_RING_CYCLE)) // total
    return PRIMAL_RING_CYCLE[phase % len(PRIMAL_RING_CYCLE)]


def connected_glass_pane(tier, mask, frame=0):
    """Connected reactor glass face for bitmask (1=top, 2=right, 4=bottom, 8=left connected).
    Connected edges disappear completely so formed reactor windows merge into one unified pane
    keeping colored borders and corner accents only along the outer perimeter."""
    c_top = bool(mask & 1)
    c_right = bool(mask & 2)
    c_bottom = bool(mask & 4)
    c_left = bool(mask & 8)
    sprite = Sprite(fill=CLEAR)

    # Outer border edges (only where NOT connected to a same-tier formed glass neighbor)
    if not c_top:
        sprite.row(0, 0, 32, SEAM)
        sprite.row(1, 0 if c_left else 1, 32 if c_right else 31, HIGHLIGHT)
        if tier in ('transmutation', 'primal', 'energy_reactor'):
            sprite.row(2, 0 if c_left else 2, 32 if c_right else 30, STEEL_DEEP)
        elif tier == 'energy_matrix':
            sprite.row(2, 0 if c_left else 2, 32 if c_right else 30, PORT_DARK)
    if not c_bottom:
        sprite.row(31, 0, 32, SEAM)
        sprite.row(30, 0 if c_left else 1, 32 if c_right else 31, SHADE)
        if tier in ('transmutation', 'primal', 'energy_reactor'):
            sprite.row(29, 0 if c_left else 2, 32 if c_right else 30, STEEL_DEEP)
        elif tier == 'energy_matrix':
            sprite.row(29, 0 if c_left else 2, 32 if c_right else 30, PORT_DARK)
    if not c_left:
        sprite.col(0, 0, 32, SEAM)
        sprite.col(1, 0 if c_top else 1, 32 if c_bottom else 31, HIGHLIGHT)
        if tier in ('transmutation', 'primal', 'energy_reactor'):
            sprite.col(2, 0 if c_top else 2, 32 if c_bottom else 30, STEEL_DEEP)
        elif tier == 'energy_matrix':
            sprite.col(2, 0 if c_top else 2, 32 if c_bottom else 30, PORT_DARK)
    if not c_right:
        sprite.col(31, 0, 32, SEAM)
        sprite.col(30, 0 if c_top else 1, 32 if c_bottom else 31, SHADE)
        if tier in ('transmutation', 'primal', 'energy_reactor'):
            sprite.col(29, 0 if c_top else 2, 32 if c_bottom else 30, STEEL_DEEP)
        elif tier == 'energy_matrix':
            sprite.col(29, 0 if c_top else 2, 32 if c_bottom else 30, PORT_DARK)

    # Tier accent rails along outer perimeter edges
    if tier == 'transmutation':
        v_col, e_col = RGB['violet'], RGB['ember']
        if not c_top:
            sprite.row(3, 0 if c_left else 3, 32 if c_right else 29, v_col)
        if not c_left:
            sprite.col(3, 0 if c_top else 3, 32 if c_bottom else 29, v_col)
        if not c_bottom:
            sprite.row(28, 0 if c_left else 3, 32 if c_right else 29, e_col)
        if not c_right:
            sprite.col(28, 0 if c_top else 3, 32 if c_bottom else 29, e_col)
    elif tier == 'energy_matrix':
        e_col = RGB['ember']
        if not c_top:
            sprite.row(3, 0 if c_left else 3, 32 if c_right else 29, e_col)
        if not c_left:
            sprite.col(3, 0 if c_top else 3, 32 if c_bottom else 29, e_col)
        if not c_bottom:
            sprite.row(28, 0 if c_left else 3, 32 if c_right else 29, e_col)
        if not c_right:
            sprite.col(28, 0 if c_top else 3, 32 if c_bottom else 29, e_col)
    elif tier == 'energy_reactor':
        a_col, e_col = RGB['amber'], RGB['ember']
        if not c_top:
            sprite.row(3, 0 if c_left else 3, 32 if c_right else 29, a_col)
        if not c_left:
            sprite.col(3, 0 if c_top else 3, 32 if c_bottom else 29, a_col)
        if not c_bottom:
            sprite.row(28, 0 if c_left else 3, 32 if c_right else 29, e_col)
        if not c_right:
            sprite.col(28, 0 if c_top else 3, 32 if c_bottom else 29, e_col)
    elif tier == 'primal':
        if not c_top:
            x0, x1 = (0 if c_left else 3), (32 if c_right else 29)
            for x in range(x0, x1):
                sprite.px(x, 3, _primal_edge_color(x, 32, frame))
        if not c_right:
            y0, y1 = (0 if c_top else 3), (32 if c_bottom else 29)
            for y in range(y0, y1):
                sprite.px(28, y, _primal_edge_color(y, 32, frame))
        if not c_bottom:
            x0, x1 = (0 if c_left else 3), (32 if c_right else 29)
            for x in range(x0, x1):
                sprite.px(x, 28, _primal_edge_color(31 - x, 32, frame))
        if not c_left:
            y0, y1 = (0 if c_top else 3), (32 if c_bottom else 29)
            for y in range(y0, y1):
                sprite.px(3, y, _primal_edge_color(31 - y, 32, frame))

    # Outer corner brackets ONLY where both adjacent edges are outer boundaries
    tl_pin = RGB['ember'] if tier == 'energy_matrix' else RGB['amber'] if tier == 'energy_reactor' else RGB['violet']
    tr_pin = RGB['ember'] if tier in ('transmutation', 'energy_matrix', 'energy_reactor') else RGB['violet']
    bl_pin = RGB['ember'] if tier == 'energy_matrix' else RGB['amber'] if tier == 'energy_reactor' else RGB['violet']
    br_pin = RGB['ember'] if tier in ('transmutation', 'energy_matrix', 'energy_reactor') else RGB['violet']
    if not c_top and not c_left:
        if tier == 'standard':
            for step in range(3):
                sprite.row(2 + step, 2, 5 - step, STEEL)
        elif tier == 'energy_matrix':
            sprite.rect(1, 1, 5, 5, PORT_METAL)
            sprite.bevel(1, 1, 5, 5, PORT_LIGHT, PORT_DARK)
            sprite.rect(2, 2, 4, 4, tl_pin)
        else:
            sprite.rect(1, 1, 6, 3, STEEL)
            sprite.rect(1, 3, 3, 6, STEEL)
            sprite.row(1, 1, 6, TRIM)
            sprite.col(1, 1, 6, TRIM)
            sprite.rect(2, 2, 4, 4, tl_pin)
    if not c_top and not c_right:
        if tier == 'standard':
            for step in range(3):
                sprite.row(2 + step, 27 + step, 30, STEEL)
        elif tier == 'energy_matrix':
            sprite.rect(27, 1, 31, 5, PORT_METAL)
            sprite.bevel(27, 1, 31, 5, PORT_LIGHT, PORT_DARK)
            sprite.rect(28, 2, 30, 4, tr_pin)
        else:
            sprite.rect(26, 1, 31, 3, STEEL)
            sprite.rect(29, 3, 31, 6, STEEL_SHADE)
            sprite.row(1, 26, 31, TRIM)
            sprite.rect(28, 2, 30, 4, tr_pin)
    if not c_bottom and not c_left:
        if tier == 'standard':
            for step in range(3):
                sprite.row(29 - step, 2, 5 - step, STEEL)
        elif tier == 'energy_matrix':
            sprite.rect(1, 27, 5, 31, PORT_METAL)
            sprite.bevel(1, 27, 5, 31, PORT_LIGHT, PORT_DARK)
            sprite.rect(2, 28, 4, 30, bl_pin)
        else:
            sprite.rect(1, 26, 3, 31, STEEL)
            sprite.rect(3, 29, 6, 31, STEEL_SHADE)
            sprite.col(1, 26, 31, TRIM)
            sprite.rect(2, 28, 4, 30, bl_pin)
    if not c_bottom and not c_right:
        if tier == 'standard':
            for step in range(3):
                sprite.row(29 - step, 27 + step, 30, STEEL)
        elif tier == 'energy_matrix':
            sprite.rect(27, 27, 31, 31, PORT_METAL)
            sprite.bevel(27, 27, 31, 31, PORT_LIGHT, PORT_DARK)
            sprite.rect(28, 28, 30, 30, br_pin)
        else:
            sprite.rect(26, 29, 31, 31, STEEL_DEEP)
            sprite.rect(29, 26, 31, 29, STEEL_SHADE)
            sprite.rect(28, 28, 30, 30, br_pin)
    return sprite


def connected_glass_corner(tier, corner_idx):
    """Tiny concave inner-corner patch (0=TR, 1=BR, 2=BL, 3=TL) closing the window frame around an inset controller or port."""
    sprite = Sprite(fill=CLEAR)
    if corner_idx == 0:  # Top-Right (u=28..31, v=0..3)
        sprite.rect(29, 0, 32, 3, PORT_DARK if tier == 'energy_matrix' else STEEL_DEEP if tier != 'standard' else SHADE)
        sprite.px(31, 0, SEAM)
        sprite.px(30, 0, SEAM)
        sprite.px(31, 1, SEAM)
        if tier in ('transmutation', 'energy_reactor'):
            sprite.px(28, 3, RGB['ember'])
            sprite.rect(29, 1, 31, 3, STEEL)
        elif tier == 'energy_matrix':
            sprite.px(28, 3, RGB['ember'])
            sprite.rect(29, 1, 31, 3, PORT_METAL)
        elif tier == 'primal':
            sprite.px(28, 3, PRIMAL_BODY)
            sprite.rect(29, 1, 31, 3, STEEL)
    elif corner_idx == 1:  # Bottom-Right (u=28..31, v=28..31)
        sprite.rect(29, 29, 32, 32, PORT_DARK if tier == 'energy_matrix' else STEEL_DEEP if tier != 'standard' else SHADE)
        sprite.px(31, 31, SEAM)
        sprite.px(30, 31, SEAM)
        sprite.px(31, 30, SEAM)
        if tier in ('transmutation', 'energy_reactor'):
            sprite.px(28, 28, RGB['ember'])
            sprite.rect(29, 29, 31, 31, STEEL_SHADE)
        elif tier == 'energy_matrix':
            sprite.px(28, 28, RGB['ember'])
            sprite.rect(29, 29, 31, 31, PORT_METAL)
        elif tier == 'primal':
            sprite.px(28, 28, PRIMAL_BODY)
            sprite.rect(29, 29, 31, 31, STEEL_SHADE)
    elif corner_idx == 2:  # Bottom-Left (u=0..3, v=28..31)
        sprite.rect(0, 29, 3, 32, PORT_DARK if tier == 'energy_matrix' else STEEL_DEEP if tier != 'standard' else HIGHLIGHT)
        sprite.px(0, 31, SEAM)
        sprite.px(1, 31, SEAM)
        sprite.px(0, 30, SEAM)
        if tier == 'transmutation':
            sprite.px(3, 28, RGB['violet'])
            sprite.rect(1, 29, 3, 31, STEEL)
        elif tier == 'energy_reactor':
            sprite.px(3, 28, RGB['amber'])
            sprite.rect(1, 29, 3, 31, STEEL)
        elif tier == 'energy_matrix':
            sprite.px(3, 28, RGB['ember'])
            sprite.rect(1, 29, 3, 31, PORT_METAL)
        elif tier == 'primal':
            sprite.px(3, 28, PRIMAL_BODY)
            sprite.rect(1, 29, 3, 31, STEEL)
    else:  # Top-Left (u=0..3, v=0..3)
        sprite.rect(0, 0, 3, 3, PORT_DARK if tier == 'energy_matrix' else STEEL_DEEP if tier != 'standard' else HIGHLIGHT)
        sprite.px(0, 0, SEAM)
        sprite.px(1, 0, SEAM)
        sprite.px(0, 1, SEAM)
        if tier == 'transmutation':
            sprite.px(3, 3, RGB['violet'])
            sprite.rect(1, 1, 3, 3, TRIM)
        elif tier == 'energy_reactor':
            sprite.px(3, 3, RGB['amber'])
            sprite.rect(1, 1, 3, 3, TRIM)
        elif tier == 'energy_matrix':
            sprite.px(3, 3, RGB['ember'])
            sprite.rect(1, 1, 3, 3, PORT_LIGHT)
        elif tier == 'primal':
            sprite.px(3, 3, PRIMAL_BODY)
            sprite.rect(1, 1, 3, 3, TRIM)
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


def drive_cell(bar, crystal=None):
    """Faceplate of a mounted cell in texels 0..11 x 0..5; bar is the tier bar length, None for an unreadable cell."""
    body, lip = (STEEL_DEEP, STEEL_SHADE) if bar is None else (STEEL, TRIM)
    sprite = Sprite(16, fill=body)
    sprite.ring(0, 0, 12, 6, WELL)
    sprite.row(1, 4, 11, lip)
    sprite.rect(0, 1, 4, 5, SEAM)
    sprite.row(2, 5, 11, RGB[crystal] if crystal else SEAM)
    if bar is not None:
        sprite.row(4, 5, 11, STEEL_DEEP)
        sprite.row(4, 5, 5 + bar, IVORY)
    return sprite


def void_drive_cell():
    """Faceplate of a mounted Void Matter Cell: dark steel housing with Ember overflow slot and segmented sink ticks."""
    sprite = Sprite(16, fill=STEEL_DEEP)
    sprite.ring(0, 0, 12, 6, WELL)
    sprite.row(1, 4, 11, STEEL_SHADE)
    sprite.rect(0, 1, 4, 5, SEAM)
    sprite.row(2, 5, 11, RGB['ember'])
    sprite.row(4, 5, 11, WELL)
    for x in (5, 7, 9):
        sprite.px(x, 4, RGB['ember'])
    return sprite


def primal_drive_cell(bar, crown=False, apex=False):
    # The mounted front and its item use the same recipe-crystal window and animated Primal rail.
    from hardware_sprites import primal_cell_face
    return primal_cell_face(bar, crystal='violet' if bar in (2, 4) else None, crown=crown, apex=apex)


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


def _redstone_port_socket(sprite, color):
    """Dedicated Redstone Signal Receiver face: clean unbroken Deep-Crimson outer ring (so compact plates look crisp
    and stay unmistakable vs Ember-Red Power Output), dark recessed receiver housing, 4 copper contact pins, steel
    collar, and glowing bright Ember-Red redstone diode core."""
    ember = RGB['ember']
    light = lighten(ember, .32)
    # Recessed dark gunmetal receiver housing (8..24) instead of an ordinary pipe/cable plug well
    sprite.ring(8, 8, 24, 24, SEAM)
    sprite.bevel(9, 9, 23, 23, PORT_LIGHT, PORT_DARK)
    sprite.rect(10, 10, 22, 22, WELL)
    # Four corner Copper signal contact pins
    for cx, cy in ((10, 10), (20, 10), (10, 20), (20, 20)):
        sprite.rect(cx, cy, cx + 2, cy + 2, COPPER)
        sprite.px(cx, cy, COPPER_LIGHT)
    # Central steel collar & glowing Redstone crystal core
    sprite.ring(12, 12, 20, 20, STEEL_SHADE)
    sprite.bevel(12, 12, 20, 20, TRIM, STEEL_DEEP)
    sprite.rect(13, 13, 19, 19, color)
    sprite.rect(14, 14, 18, 18, ember)
    sprite.rect(15, 15, 17, 17, light)
    sprite.px(15, 15, IVORY)


def _port_direction_arrows(sprite, color, mode):
    """Subtle inward (input) or outward (output) directional chevrons on the top & bottom of the mode ring."""
    if mode in ('item_input', 'fluid_input', 'power'):
        # Arrows pointing INWARD toward the center socket (top points down, bottom points up)
        light = lighten(color, .28)
        sprite.rect(13, 6, 19, 8, WELL)
        sprite.row(6, 14, 18, light)
        sprite.row(7, 15, 17, color)
        sprite.rect(13, 24, 19, 26, WELL)
        sprite.row(24, 15, 17, color)
        sprite.row(25, 14, 18, light)
    elif mode in ('item_output', 'fluid_output', 'power_output'):
        # Arrows pointing OUTWARD away from the center socket (top points up, bottom points down)
        light = lighten(color, .28)
        sprite.rect(13, 6, 19, 8, WELL)
        sprite.row(6, 15, 17, light)
        sprite.row(7, 14, 18, color)
        sprite.rect(13, 24, 19, 26, WELL)
        sprite.row(24, 14, 18, color)
        sprite.row(25, 15, 17, light)


def machine_port(color, contact=True, automatic_style=PORT_AUTOMATIC_STYLE, mode=None):
    if automatic_style not in PORT_AUTOMATIC_STYLES:
        raise ValueError('Unknown Automatic ring style: ' + automatic_style)
    sprite = casing()
    light, dark = _port_ring_bevel(color)
    sprite.rect(4, 4, 28, 28, color)
    sprite.bevel(4, 4, 28, 28, light, dark)
    if color == PORT_AUTOMATIC and automatic_style == 'notched_bevel':
        for x, y in ((4, 4), (26, 4), (4, 26), (26, 26)):
            sprite.rect(x, y, x + 2, y + 2, PORT_DARK)
    sprite.rect(6, 6, 26, 26, BASE)
    for x, y, width, height in ((14, 6, 4, 2), (14, 24, 4, 2), (6, 14, 2, 4), (24, 14, 2, 4)):
        sprite.rect(x, y, x + width, y + height, color)
    if mode == 'redstone':
        _redstone_port_socket(sprite, color)
    else:
        _port_direction_arrows(sprite, color, mode)
        port_plate(sprite, contact=contact)
    return sprite


def _port_ring_bevel(color):
    if color == PORT_AUTOMATIC:
        return STEEL, PORT_METAL
    if color == PORT_UNFORMED:
        return HIGHLIGHT, SEAM
    if color == SEAM:
        return BASE, WELL
    if color == LED['red']:
        return lighten(color, .28), LED_RED_DIM
    return lighten(color, .28), scale(color, .68)


def transmutation_machine_port(color, contact=True, mode=None):
    """Armored Transmutation Machine Port: steel corner clasps + Violet/Ember edge bars around functional port ring."""
    sprite = casing()
    _armored_corners(sprite)
    # Static exact-hue Violet (top & left) and Ember (bottom & right) recessed edge bars outside the 4..28 ring
    sprite.row(2, 10, 22, RGB['violet'])
    sprite.col(2, 10, 22, RGB['violet'])
    sprite.row(29, 10, 22, RGB['ember'])
    sprite.col(29, 10, 22, RGB['ember'])
    light, dark = _port_ring_bevel(color)
    sprite.rect(4, 4, 28, 28, color)
    sprite.bevel(4, 4, 28, 28, light, dark)
    sprite.rect(6, 6, 26, 26, BASE)
    for x, y, width, height in ((14, 6, 4, 2), (14, 24, 4, 2), (6, 14, 2, 4), (24, 14, 2, 4)):
        sprite.rect(x, y, x + width, y + height, color)
    if mode == 'redstone':
        _redstone_port_socket(sprite, color)
    else:
        _port_direction_arrows(sprite, color, mode)
        port_plate(sprite, contact=contact)
    return sprite


def primal_machine_port_frame(color, frame, contact=True, mode=None):
    """Endgame Primal Machine Port: animated 5-stop Primal edge bars + static Violet corner pins around functional port ring."""
    sprite = casing()
    sprite.rect(2, 2, 30, 30, SHADE)
    _armored_corners(sprite)
    # Animated 5-stop Primal conduit bars along the 4 outer edges (indices 9..23, strictly outside the 4..28 mode ring)
    n = len(PRIMAL_RING_CYCLE)
    for i in range(12):
        c = PRIMAL_RING_CYCLE[(i - frame // 2) % n]
        sprite.px(10 + i, 2, c)
        sprite.px(21 - i, 29, c)
        sprite.px(2, 21 - i, c)
        sprite.px(29, 10 + i, c)
    # Single orbiting white-hot spark on top/bottom edges
    sp = (frame * 12) // 16
    sprite.px(10 + sp, 2, PRIMAL_CORE)
    sprite.px(21 - sp, 29, PRIMAL_CORE)
    # Four static flat Violet crystal pins in the steel corner clasps
    for cx, cy in ((2, 2), (28, 2), (2, 28), (28, 28)):
        sprite.rect(cx, cy, cx + 2, cy + 2, RGB['violet'])
    light, dark = _port_ring_bevel(color)
    sprite.rect(4, 4, 28, 28, color)
    sprite.bevel(4, 4, 28, 28, light, dark)
    sprite.rect(6, 6, 26, 26, BASE)
    for x, y, width, height in ((14, 6, 4, 2), (14, 24, 4, 2), (6, 14, 2, 4), (24, 14, 2, 4)):
        sprite.rect(x, y, x + width, y + height, color)
    if mode == 'redstone':
        # Compact receiver staying within the 24-colour frame budget alongside the Primal spectrum
        sprite.ring(8, 8, 24, 24, SEAM)
        sprite.bevel(9, 9, 23, 23, STEEL, STEEL_DEEP)
        sprite.rect(10, 10, 22, 22, WELL)
        for cx, cy in ((10, 10), (20, 10), (10, 20), (20, 20)):
            sprite.rect(cx, cy, cx + 2, cy + 2, COPPER)
        sprite.rect(13, 13, 19, 19, dark)
        sprite.rect(14, 14, 18, 18, color)
        sprite.rect(15, 15, 17, 17, light)
    else:
        _port_direction_arrows(sprite, color, mode)
        port_plate(sprite, contact=contact)
    return sprite


def energy_matrix_port(color, contact=True, mode=None):
    """Energy Matrix Port: gunmetal corner clasps + Ember (top/bottom) & Blue (left/right) outer bars around mode ring."""
    sprite = casing()
    for cx, cy in ((1, 1), (27, 1), (1, 27), (27, 27)):
        sprite.rect(cx, cy, cx + 4, cy + 4, PORT_METAL)
        sprite.bevel(cx, cy, cx + 4, cy + 4, PORT_LIGHT, PORT_DARK)
    sprite.row(2, 10, 22, RGB['ember'])
    sprite.row(29, 10, 22, RGB['ember'])
    sprite.col(2, 10, 22, RGB['blue'])
    sprite.col(29, 10, 22, RGB['blue'])
    light, dark = _port_ring_bevel(color)
    sprite.rect(4, 4, 28, 28, color)
    sprite.bevel(4, 4, 28, 28, light, dark)
    sprite.rect(6, 6, 26, 26, BASE)
    for x, y, width, height in ((14, 6, 4, 2), (14, 24, 4, 2), (6, 14, 2, 4), (24, 14, 2, 4)):
        sprite.rect(x, y, x + width, y + height, color)
    if mode == 'redstone':
        _redstone_port_socket(sprite, color)
    else:
        _port_direction_arrows(sprite, color, mode)
        port_plate(sprite, contact=contact)
    return sprite


def energy_reactor_port(color, contact=True, mode=None):
    """Armored Energy Reactor Port: steel corner clasps + Amber (top/left) & Ember (bottom/right) edge bars."""
    sprite = casing()
    _armored_corners(sprite)
    sprite.row(2, 10, 22, RGB['amber'])
    sprite.col(2, 10, 22, RGB['amber'])
    sprite.row(29, 10, 22, RGB['ember'])
    sprite.col(29, 10, 22, RGB['ember'])
    light, dark = _port_ring_bevel(color)
    sprite.rect(4, 4, 28, 28, color)
    sprite.bevel(4, 4, 28, 28, light, dark)
    sprite.rect(6, 6, 26, 26, BASE)
    for x, y, width, height in ((14, 6, 4, 2), (14, 24, 4, 2), (6, 14, 2, 4), (24, 14, 2, 4)):
        sprite.rect(x, y, x + width, y + height, color)
    if mode == 'redstone':
        _redstone_port_socket(sprite, color)
    else:
        _port_direction_arrows(sprite, color, mode)
        port_plate(sprite, contact=contact)
    return sprite


def cable(tier, look=None):
    """Static shaded dark-graphite housing of a Matter Cable: clamps, elbows, manifold hubs and bolted flanges."""
    look = CABLE_LOOK if look is None else look
    stripe, width, dashed = tier['stripe'], tier['width'], tier['dashed']
    deep = scale(stripe, .36)
    dark = scale(stripe, .66)
    sprite = Sprite(fill=JACKET)
    # Clamping collar / outer jacket strip (32 x 8), mirrored vertically around centre line.
    sprite.row(0, 0, 32, SEAM)
    sprite.row(7, 0, 32, SEAM)
    if look == 'conduit_cage':
        # Dark armored steel-graphite cable clamp with subtle woven recesses.
        sprite.row(1, 0, 32, PORT_DARK)
        sprite.row(6, 0, 32, PORT_DARK)
        sprite.rect(0, 2, 32, 6, SHADE)
        for x in range(32):
            if x % 4 in (1, 2):
                sprite.px(x, 1, PORT_METAL)
                sprite.px(x, 6, PORT_METAL)
                sprite.rect(x, 2, x + 1, 6, BASE)
            if x % 8 in (2, 3):
                sprite.rect(x, 3, x + 1, 5, stripe)
    elif look == 'glass_conduit':
        # Sleek forged gunmetal end-clamp bands for Mekanism-style glass tubes.
        sprite.row(1, 0, 32, PORT_LIGHT)
        sprite.row(6, 0, 32, PORT_DARK)
        sprite.rect(0, 2, 32, 6, PORT_METAL)
        for x in range(32):
            if x % 4 == 0:
                sprite.rect(x, 1, x + 1, 7, SHADE)
            elif x % 4 == 2:
                sprite.rect(x, 3, x + 1, 5, stripe)
    else:
        # fiber_trace & classic_sleek: deep sleek graphite jacket with crisp dark channel + tier trace.
        sprite.row(1, 0, 32, SHADE)
        sprite.row(6, 0, 32, SHADE)
        sprite.rect(0, 2, 32, 6, WELL)
        sprite.rect(0, 3, 32, 5, stripe)
        if dashed:
            for x in range(32):
                if x % 8 == 0:
                    sprite.rect(x, 3, x + 1, 5, dark)
    # End cap of the 4 x 4 elbow/core & clamp ends (0..8, 8..16): dark graphite bezel + subtle tier core.
    sprite.rect(0, 8, 8, 16, SHADE)
    sprite.ring(0, 8, 8, 16, SEAM)
    sprite.bevel(1, 9, 7, 15, PORT_DARK, SEAM)
    sprite.rect(2, 10, 6, 14, WELL)
    sprite.rect(3, 11, 5, 13, stripe)
    sprite.px(4, 12, dark)
    for rx, ry in ((1, 9), (6, 9), (1, 14), (6, 14)):
        sprite.px(rx, ry, PORT_METAL)
    # Face of the 6 x 6 hub / manifold cap (8..20, 8..20): sleek dark graphite plate + centered cross/core.
    sprite.rect(8, 8, 20, 20, BASE)
    sprite.ring(8, 8, 20, 20, SEAM)
    sprite.bevel(9, 9, 19, 19, HIGHLIGHT, SHADE)
    sprite.rect(10, 10, 18, 18, JACKET)
    if look == 'fiber_trace':
        # Recessed dark channels meeting at a neat 2 x 2 tier node.
        sprite.rect(13, 9, 15, 19, WELL)
        sprite.rect(9, 13, 19, 15, WELL)
        sprite.rect(13, 12, 15, 16, deep)
        sprite.rect(12, 13, 16, 15, deep)
        sprite.rect(13, 13, 15, 15, stripe)
    elif look == 'conduit_cage':
        # Dark armored 5 x 5 / 6 x 6 node with four bolted steel corners and a tight core lens.
        sprite.rect(11, 11, 17, 17, PORT_DARK)
        sprite.rect(12, 12, 16, 16, WELL)
        sprite.rect(13, 13, 15, 15, stripe)
    else:
        sprite.rect(11, 11, 17, 17, WELL)
        sprite.rect(12, 12, 16, 16, dark)
        sprite.rect(13, 13, 15, 15, stripe)
    for bx, by in ((10, 10), (17, 10), (10, 17), (17, 17)):
        sprite.px(bx, by, PORT_LIGHT)
    # Face of the 6 x 6 device flange (20..32, 8..20): dark gunmetal socket with subtle corner bolts.
    sprite.rect(20, 8, 32, 20, PORT_DARK)
    sprite.ring(20, 8, 32, 20, SEAM)
    sprite.bevel(21, 9, 31, 19, PORT_METAL, SEAM)
    sprite.rect(23, 11, 29, 17, JACKET)
    sprite.ring(22, 10, 30, 18, SEAM)
    for bx, by in ((22, 10), (29, 10), (22, 17), (29, 17)):
        sprite.px(bx, by, STEEL_SHADE)
    # Side of the device flange (0..12, 20..24): sleek dark gunmetal connector with fine tier accent.
    sprite.rect(0, 20, 12, 24, PORT_DARK)
    sprite.row(20, 0, 12, PORT_METAL)
    sprite.row(23, 0, 12, SEAM)
    sprite.rect(0, 22, 12, 23, JACKET)
    sprite.rect(5, 21, 7, 23, stripe)
    sprite.col(0, 20, 24, SEAM)
    sprite.col(11, 20, 24, SEAM)
    # Side rim of the glass_conduit hub caps (12..24, 20..24): slim forged metal bracket.
    sprite.rect(12, 20, 24, 24, PORT_DARK)
    sprite.row(20, 12, 24, PORT_METAL)
    sprite.row(23, 12, 24, SEAM)
    sprite.rect(14, 21, 22, 23, JACKET)
    sprite.rect(17, 21, 19, 23, stripe)
    sprite.col(12, 20, 24, SEAM)
    sprite.col(23, 20, 24, SEAM)
    return sprite


def _pulse_tone(dist, anim, deep, dim, mid, bright, dashed, core_pin=False):
    """Saturated, non-washed-out glow tone along a 16-texel conduit cycle."""
    if anim == 'idle_quiet':
        return mid
    if anim == 'single_pulse':
        # One calm, sleek data/energy pulse gliding through a dark sleeping fibre (period 16).
        if dist == 0:
            return IVORY if (dashed and core_pin) else bright
        if dist in (1, 15):
            return mid
        if dist in (2, 14):
            return dim
        return deep
    # steady_flow: continuous smooth dual-wave current (period 8), rich & saturated.
    p = dist % 8
    if p == 0:
        return bright
    if p in (1, 7):
        return mid
    if p in (2, 6):
        return dim
    return deep


def _pcb_paths(complex_arches):
    """Two non-touching 1-texel PCB trace paths over a 16-texel period (entry/exit symmetric at y=2 and y=5).

    Path A bridges vertically OVER Path B at x=4; Path B bridges vertically OVER Path A at x=12.
    Dark 1-texel gaps flank each underpass so both crossings read crisply on a 32x32 block texture.
    """
    if complex_arches:
        path_a = [
            (0, 2), (1, 2), (1, 1), (2, 1), (2, 2), (3, 2), (4, 2),
            (4, 3), (4, 4), (4, 5), (5, 5), (6, 5), (6, 6), (7, 6),
            (7, 5), (8, 5), (9, 5), (10, 5), (10, 4), (10, 3),
            None,  # underpass under Path B at x=12
            (14, 3), (14, 2), (15, 2),
        ]
        path_b = [
            (0, 5), (1, 5), (2, 5), (2, 4), (2, 3),
            None,  # underpass under Path A at x=4
            (6, 3), (7, 3), (7, 2), (8, 2), (9, 2), (9, 1), (10, 1),
            (10, 2), (11, 2), (12, 2), (12, 3), (12, 4), (12, 5),
            (13, 5), (14, 5), (15, 5),
        ]
    else:
        # Simplified crisp 90-degree dual traces without outer edge arches (Basic tier).
        path_a = [
            (0, 2), (1, 2), (2, 2), (3, 2), (4, 2),
            (4, 3), (4, 4), (4, 5), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5),
            (10, 5), (10, 4), (10, 3),
            None,
            (14, 3), (14, 2), (15, 2),
        ]
        path_b = [
            (0, 5), (1, 5), (2, 5), (2, 4), (2, 3),
            None,
            (6, 3), (7, 3), (7, 2), (8, 2), (9, 2), (10, 2), (11, 2), (12, 2),
            (12, 3), (12, 4), (12, 5), (13, 5), (14, 5), (15, 5),
        ]
    return path_a, path_b


def _trace_tone(stripe, intensity):
    """Quantize 0..1 powered intensity onto vivid high-saturation stops + IVORY flash (4 stripe shades max)."""
    if intensity >= .90:
        return IVORY
    if intensity >= .58:
        return lighten(stripe, .50)
    if intensity >= .22:
        return lighten(stripe, .24)
    return stripe


def _calm_envelope(frame):
    """32-frame calm pulse envelope: smooth fade-in (1..4), gentle sweep (5..14), fade-out (15..18), quiet rest (19..31)."""
    f = frame % 32
    if f in (1, 17):
        return .28
    if f in (2, 16):
        return .58
    if f in (3, 15):
        return .84
    if 4 <= f <= 14:
        return 1.0
    return 0.0


def _trace_pattern_paths(pattern=None):
    """Return (path_a, path_b, nodes) over a 16-texel period (entry/exit symmetric at x=0 and x=15 on y=2 and y=5).

    Because pure straight cable runs use a single 16-px (32-texel) block model without 180° mirroring,
    every 16-texel pattern below tiles seamlessly end-to-end across arbitrarily long straight cable lines.
    """
    pattern = CABLE_TRACE_PATTERN if pattern is None else pattern
    if pattern == 'stepped_bus':
        # Two unbroken parallel motherboard bus lanes stepping inward with clean 90° chicanes & corner vias.
        path_a = [
            (0, 2), (1, 2), (2, 2), (3, 2), (3, 3), (4, 3), (5, 3), (6, 3),
            (6, 2), (7, 2), (8, 2), (9, 2), (10, 2), (11, 2), (12, 2),
            (13, 2), (14, 2), (15, 2),
        ]
        path_b = [
            (0, 5), (1, 5), (2, 5), (3, 5), (4, 5), (5, 5), (6, 5), (7, 5),
            (8, 5), (9, 5), (10, 5), (11, 5), (11, 4), (12, 4), (13, 4), (14, 4),
            (14, 5), (15, 5),
        ]
        nodes = {(3, 3), (6, 2), (11, 4), (14, 5)}
        return path_a, path_b, nodes
    if pattern == 'woven_helix':
        # Two unbroken interwoven 90° lanes crossing directly through bright shared coupling nodes at x=4 and x=12.
        path_a = [
            (0, 2), (1, 2), (2, 2), (3, 2), (3, 3), (4, 3), (4, 4), (5, 4), (5, 5),
            (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5), (11, 4), (12, 4), (12, 3),
            (13, 3), (13, 2), (14, 2), (15, 2),
        ]
        path_b = [
            (0, 5), (1, 5), (2, 5), (3, 5), (3, 4), (4, 4), (4, 3), (5, 3), (5, 2),
            (6, 2), (7, 2), (8, 2), (9, 2), (10, 2), (11, 2), (11, 3), (12, 3), (12, 4),
            (13, 4), (13, 5), (14, 5), (15, 5),
        ]
        nodes = {(4, 3), (4, 4), (12, 3), (12, 4)}
        return path_a, path_b, nodes
    # classic_basic (default): original crisp 90° dual PCB traces alternating bridges at x=4 and x=12.
    path_a, path_b = _pcb_paths(complex_arches=False)
    return path_a, path_b, {(4, 3), (12, 3)}


def _paint_pcb_traces(sprite, name, tier, frame, anim, energized=True):
    """Draw Basic-layout PCB traces (32x8), corner core (8x8), and PCB junction hub (12x12).

    In the recommended 'rail_stripes' mode both powered and unpowered cables share the exact same crisp, vivid
    inner PCB traces and animation; outer rail stripes are neutral grey (PORT_LIGHT) without FE and glowing
    tier colour while FE flows!
    """
    stripe, width, dashed = tier['stripe'], tier['width'], tier['dashed']
    base = stripe
    mid = lighten(stripe, .24)
    bright = lighten(stripe, .50)
    rails = CABLE_POWER_CONTRAST == 'rail_stripes'
    lit_rails = rails and energized
    grey_rails = rails and not energized
    env = _calm_envelope(frame) if anim == 'single_pulse' else 1.0

    # 1. Cable shaft (32 x 8 at y=0..8):
    #    y=0 & y=7: continuous outer rail stripes (glowing tier colour with FE; neutral grey PORT_LIGHT without FE)
    #    y=1 & y=6: 1-texel dark JACKET isolation gap so rails never bleed into the inner traces
    #    y=2..5:    pitch-black WELL bed carrying the selected 16-texel PCB trace pattern (tiled x=0..15 & 16..31)
    if lit_rails:
        for x in range(32):
            if anim == 'idle_quiet':
                rc = base
            elif anim == 'single_pulse':
                dist = (x - frame) % 16
                rc = mid if (env >= .35 and dist in (0, 1, 15)) else base
            else:
                rc = mid if ((x - frame) % 8 == 0) else base
            sprite.px(x, 0, rc)
            sprite.px(x, 7, rc)
    elif grey_rails:
        sprite.row(0, 0, 32, PORT_LIGHT)
        sprite.row(7, 0, 32, PORT_LIGHT)
    else:
        sprite.row(0, 0, 32, SEAM)
        sprite.row(7, 0, 32, SEAM)
    sprite.row(1, 0, 32, JACKET)
    sprite.row(6, 0, 32, JACKET)
    sprite.rect(0, 2, 32, 6, WELL)

    path_a, path_b, bridges = _trace_pattern_paths()

    def draw_path(path, phase_shift, reverse=False):
        n = len(path)
        for idx, pt in enumerate(path):
            if pt is None:
                continue
            lx, ly = pt
            pos = int(round((n - 1 - idx if reverse else idx) * 16 / n)) % 16
            if anim == 'idle_quiet':
                col = bright if pt in bridges else mid
            elif anim == 'single_pulse':
                ahead = (pos - (frame + phase_shift)) % 16
                if ahead == 0:
                    wave = 1.0
                elif ahead == 15:
                    wave = .82
                elif ahead == 1:
                    wave = .66
                elif ahead == 14:
                    wave = .48
                elif ahead == 13:
                    wave = .24
                else:
                    wave = 0.0
                floor = .16 if rails else .08
                lift = .24 if pt in bridges else 0.0
                col = _trace_tone(stripe, min(1.0, floor + lift + env * wave))
            else:  # steady_flow
                p = (pos - frame - phase_shift) % 8
                if p == 0:
                    col = IVORY if pt in bridges else bright
                elif p in (1, 7):
                    col = bright
                elif p in (2, 6):
                    col = mid
                else:
                    col = base
            for tile in (0, 16):
                sprite.px(tile + lx, ly, col)

    if anim == 'single_pulse':
        draw_path(path_a, phase_shift=0, reverse=False)
        draw_path(path_b, phase_shift=4, reverse=False)
    else:
        draw_path(path_a, phase_shift=0, reverse=False)
        draw_path(path_b, phase_shift=8, reverse=dashed)
    if anim == 'idle_quiet':
        for tile in (0, 16):
            for bx, by in bridges:
                sprite.px(tile + bx, by, IVORY if dashed else bright)

    # 2. Glowing PCB corner/elbow cap (8 x 8 at x=0..8, y=8..16):
    #    Inner trace entries (p=2, 5), inner ring and core stay identical in both states;
    #    outer rail perimeter is neutral grey (PORT_LIGHT) without FE and tier colour with FE!
    breath = abs((frame % 16) - 8)
    if anim == 'single_pulse':
        ring_col = mid if rails else base
        hub_ring = bright if rails else mid
        pad_core = IVORY if (rails or env >= .75) else _trace_tone(stripe, .55 + .40 * env)
        corner_core = IVORY if (rails and env >= .45) else bright if rails else _trace_tone(stripe, .40 + .55 * env)
    elif anim == 'idle_quiet':
        ring_col = mid if rails else base
        hub_ring = bright if rails else mid
        pad_core = IVORY if rails else bright
        corner_core = bright
    else:
        ring_col = bright if breath <= 3 else mid
        hub_ring = IVORY if (rails and breath <= 1) else bright
        pad_core = IVORY if breath <= 2 else bright
        corner_core = bright if breath <= 2 else mid
    sprite.rect(0, 8, 8, 16, WELL)
    sprite.ring(0, 8, 8, 16, base if lit_rails else (PORT_LIGHT if grey_rails else SEAM))
    sprite.ring(1, 9, 7, 15, JACKET)
    # Entry traces at positions 2 and 5 on all four sides, meeting the inner square trace ring
    for p in (2, 5):
        sprite.px(p, 8, ring_col); sprite.px(p, 9, ring_col)
        sprite.px(p, 14, ring_col); sprite.px(p, 15, ring_col)
        sprite.px(0, 8 + p, ring_col); sprite.px(1, 8 + p, ring_col)
        sprite.px(6, 8 + p, ring_col); sprite.px(7, 8 + p, ring_col)
    sprite.ring(2, 10, 6, 14, hub_ring)
    sprite.rect(3, 11, 5, 13, corner_core)

    # 3. PCB Junction Hub face (12 x 12 at x=8..20, y=8..20):
    #    Inner PCB entries, ring and chip core stay identical in both states;
    #    outer rail collar (10..18) is neutral grey (PORT_LIGHT) without FE and tier colour with FE!
    sprite.rect(8, 8, 20, 20, JACKET)
    sprite.ring(8, 8, 20, 20, SEAM)
    sprite.bevel(9, 9, 19, 19, PORT_DARK, SEAM)
    sprite.rect(10, 10, 18, 18, WELL)
    for bx, by in ((9, 9), (18, 9), (9, 18), (18, 18)):
        sprite.px(bx, by, PORT_METAL)
    if lit_rails:
        sprite.ring(10, 10, 18, 18, base)
    elif grey_rails:
        sprite.ring(10, 10, 18, 18, PORT_LIGHT)
    # Dual trace entries from each side (at local offsets 4 & 7 -> exact alignment with cable y=2 & y=5!)
    for p in (4, 7):
        sprite.rect(8 + p, 9, 8 + p + 1, 12, ring_col)
        sprite.rect(8 + p, 16, 8 + p + 1, 19, ring_col)
        sprite.rect(9, 8 + p, 12, 8 + p + 1, ring_col)
        sprite.rect(16, 8 + p, 19, 8 + p + 1, ring_col)
    sprite.ring(12, 12, 16, 16, hub_ring)
    sprite.rect(13, 13, 15, 15, pad_core)
    if dashed:
        sprite.px(13, 13, IVORY)
    return sprite


def cable_dim_frame(name, tier, frame, look=None, anim=None):
    """One 32 x 32 frame of the unpowered (no FE) conduit sheet.

    In 'rail_stripes' (recommended), uses the exact same crisp vivid inner PCB traces as the active cable,
    with neutral grey outer rail stripes (PORT_LIGHT) and light_emission 4.
    """
    look = CABLE_LOOK if look is None else look
    anim = CABLE_ANIM if anim is None else anim
    if look == 'pcb_traces' and CABLE_POWER_CONTRAST == 'rail_stripes':
        return _paint_pcb_traces(Sprite(fill=WELL), name, tier, frame, anim, energized=False)
    stripe = tier['stripe']
    deep = scale(stripe, .40)
    soft = scale(stripe, .54)
    glint = scale(stripe, .70)
    hollow = CABLE_POWER_CONTRAST == 'hollow_sleep'
    if look == 'pcb_traces':
        sprite = Sprite(fill=WELL)
        sprite.row(0, 0, 32, PORT_LIGHT)
        sprite.row(7, 0, 32, PORT_LIGHT)
        sprite.row(1, 0, 32, JACKET)
        sprite.row(6, 0, 32, JACKET)
        sprite.rect(0, 2, 32, 6, WELL)
        path_a, path_b, bridges = _trace_pattern_paths()
        f = frame % 32
        active = f < 12
        for lane_shift, path in ((0, path_a), (5, path_b)):
            n = len(path)
            for idx, pt in enumerate(path):
                if pt is None:
                    continue
                lx, ly = pt
                pos = int(round(idx * 16 / n)) % 16
                if hollow and pt not in bridges and idx % 3 == 1:
                    col = WELL
                else:
                    col = soft if pt in bridges else deep
                if active:
                    ahead = (pos - (f + lane_shift)) % 16
                    if ahead == 0:
                        col = glint
                    elif ahead in (1, 15) and col != WELL:
                        col = soft
                for tile in (0, 16):
                    sprite.px(tile + lx, ly, col)
        breath = 2 <= f <= 9
        corner_core = WELL if hollow else (soft if breath else WELL)
        sprite.rect(0, 8, 8, 16, WELL)
        sprite.ring(0, 8, 8, 16, PORT_LIGHT)
        sprite.ring(1, 9, 7, 15, JACKET)
        for p in (2, 5):
            sprite.px(p, 8, deep); sprite.px(p, 9, deep)
            sprite.px(p, 14, deep); sprite.px(p, 15, deep)
            sprite.px(0, 8 + p, deep); sprite.px(1, 8 + p, deep)
            sprite.px(6, 8 + p, deep); sprite.px(7, 8 + p, deep)
        sprite.ring(2, 10, 6, 14, soft if breath else deep)
        sprite.rect(3, 11, 5, 13, corner_core)
        sprite.rect(8, 8, 20, 20, JACKET)
        sprite.ring(8, 8, 20, 20, SEAM)
        sprite.bevel(9, 9, 19, 19, PORT_DARK, SEAM)
        sprite.rect(10, 10, 18, 18, WELL)
        sprite.ring(10, 10, 18, 18, PORT_LIGHT)
        for bx, by in ((9, 9), (18, 9), (9, 18), (18, 18)):
            sprite.px(bx, by, PORT_METAL)
        for p in (4, 7):
            sprite.rect(8 + p, 9, 8 + p + 1, 12, deep)
            sprite.rect(8 + p, 16, 8 + p + 1, 19, deep)
            sprite.rect(9, 8 + p, 12, 8 + p + 1, deep)
            sprite.rect(16, 8 + p, 19, 8 + p + 1, deep)
        sprite.ring(12, 12, 16, 16, soft if breath else deep)
        sprite.rect(13, 13, 15, 15, corner_core)
        return sprite
    sprite = cable_glow_frame(name, tier, frame % 16, look, 'single_pulse')
    dim_map = {stripe: deep, lighten(stripe, .26): soft, scale(stripe, .58): deep, IVORY: glint}
    for y in range(sprite.height):
        for x in range(sprite.width):
            px = sprite.rows[y][x][:3]
            if px in dim_map:
                sprite.px(x, y, dim_map[px])
    return sprite


def cable_dim(name, tier, look=None):
    """32-frame subtly animated resting strip for unpowered cables (no FE)."""
    return cable_dim_frame(name, tier, 0, look)


def cable_glow_frame(name, tier, frame, look=None, anim=None):
    """One 32 x 32 frame of the emissive conduit sheet for any CABLE_LOOK x CABLE_ANIM."""
    look = CABLE_LOOK if look is None else look
    anim = CABLE_ANIM if anim is None else anim
    if look == 'pcb_traces':
        return _paint_pcb_traces(Sprite(fill=WELL), name, tier, frame, anim)
    stripe, width, dashed = tier['stripe'], tier['width'], tier['dashed']
    # Saturated tones only — never blown-out pastel white.
    deep = scale(stripe, .34)
    dim = scale(stripe, .58)
    mid = stripe
    bright = lighten(stripe, .26)
    sprite = Sprite(fill=JACKET)

    # 1. Conduit run strip (32 x 8 at y=0..8, vertically mirrored around y=3.5).
    #    4 x 4 runs sample y=0..8; recessed 3 x 3 runs sample y=1..7.
    sprite.row(0, 0, 32, SEAM)
    sprite.row(7, 0, 32, SEAM)
    if look == 'fiber_trace':
        # Sleek dark graphite jacket (75% dark!) with a razor-sharp 2-texel luminous fibre down the middle.
        for x in range(32):
            dist = (x - frame) % 16
            jacket_edge = SHADE if x % 8 in (0, 7) else JACKET
            sprite.px(x, 1, jacket_edge)
            sprite.px(x, 6, jacket_edge)
            # Dark recessed groove flanking the fibre; subtly catches colour only right at the pulse head.
            groove = deep if (anim != 'idle_quiet' and dist in (0, 1, 15)) or (width >= 4 and anim == 'steady_flow' and dist % 8 == 0) else WELL
            sprite.px(x, 2, groove)
            sprite.px(x, 5, groove)
            core = _pulse_tone(dist, anim, deep, dim, mid, bright, dashed, core_pin=(x % 2 == 0))
            sprite.px(x, 3, core)
            sprite.px(x, 4, core)
    elif look == 'glass_conduit':
        # Dark smoked-glass tube (Mekanism-style): dark glass walls with subtle steel sheen + luminous inner core.
        for x in range(32):
            dist = (x - frame) % 16
            glass_rim = STEEL_DEEP if x % 8 in (1, 2) else SEAM
            sprite.px(x, 1, glass_rim)
            sprite.px(x, 6, glass_rim)
            inner_wall = dim if (width >= 4 and (anim == 'steady_flow' or dist in (0, 1, 15))) else deep if dist in (0, 1, 2, 14, 15) or anim != 'single_pulse' else WELL
            sprite.px(x, 2, inner_wall)
            sprite.px(x, 5, inner_wall)
            core = _pulse_tone(dist, anim, dim, mid, mid if anim == 'idle_quiet' else bright, bright, dashed, core_pin=True)
            sprite.px(x, 3, core)
            sprite.px(x, 4, core)
    elif look == 'conduit_cage':
        # Ender-IO-style dark industrial armored bundle with crisp segmented sight-slits every 8 texels.
        for x in range(32):
            dist = (x - frame) % 16
            rib = x % 8 in (0, 1, 6, 7)
            sprite.px(x, 1, PORT_DARK if rib else SEAM)
            sprite.px(x, 6, PORT_DARK if rib else SEAM)
            if rib:
                sprite.rect(x, 2, x + 1, 6, JACKET if x % 8 in (0, 7) else SHADE)
            else:
                sprite.px(x, 2, WELL)
                sprite.px(x, 5, WELL)
                core = _pulse_tone(dist, anim, deep, dim, mid, bright, dashed, core_pin=True)
                sprite.px(x, 3, core)
                sprite.px(x, 4, core)
    else:
        # classic_sleek: clean smooth dark graphite cable with refined 2-texel (Basic) or 4-texel (Reinforced/Resonant) channel.
        first = 4 - width // 2
        for x in range(32):
            dist = (x - frame) % 16
            sprite.px(x, 1, JACKET)
            sprite.px(x, 6, JACKET)
            sprite.px(x, 2, WELL)
            sprite.px(x, 5, WELL)
            core = _pulse_tone(dist, anim, deep, dim, mid, bright, dashed, core_pin=False)
            outer = dim if (anim == 'idle_quiet' or dist in (0, 1, 15)) else deep
            if width >= 4:
                sprite.px(x, 2, outer)
                sprite.px(x, 5, outer)
            sprite.px(x, 3, core)
            sprite.px(x, 4, core)
            if dashed and x % 8 == 0 and anim == 'idle_quiet':
                sprite.px(x, 3, deep)
                sprite.px(x, 4, deep)

    # 2. Sleek dark hub face with crisp glowing cross/window (12 x 12 at x=8..20, y=8..20) for fiber_trace & classic_sleek.
    breath = abs((frame % 16) - 8)
    hub_core = mid if anim == 'idle_quiet' else (bright if breath <= 2 else mid if breath <= 5 else dim)
    hub_arm = dim if anim == 'idle_quiet' else (mid if breath <= 3 else deep)
    sprite.rect(8, 8, 20, 20, BASE)
    sprite.ring(8, 8, 20, 20, SEAM)
    sprite.bevel(9, 9, 19, 19, HIGHLIGHT, SHADE)
    sprite.rect(10, 10, 18, 18, JACKET)
    sprite.rect(13, 9, 15, 19, WELL)
    sprite.rect(9, 13, 19, 15, WELL)
    sprite.rect(13, 10, 15, 18, hub_arm)
    sprite.rect(10, 13, 18, 15, hub_arm)
    sprite.rect(13, 13, 15, 15, hub_core)
    for bx, by in ((10, 10), (17, 10), (10, 17), (17, 17)):
        sprite.px(bx, by, PORT_METAL)

    # 3. Smoked-glass manifold hub chamber (10 x 8 at x=0..10, y=12..20) for glass_conduit & conduit_cage.
    sprite.rect(0, 12, 10, 20, SEAM)
    sprite.rect(1, 13, 9, 19, WELL)
    if look == 'conduit_cage':
        # Dark armored 5 x 5 node with tight central glowing lens.
        sprite.rect(1, 13, 9, 19, JACKET)
        sprite.bevel(1, 13, 9, 19, PORT_DARK, SEAM)
        sprite.rect(3, 14, 7, 18, WELL)
        sprite.rect(4, 15, 6, 17, hub_core)
    else:
        # Smoked glass chamber showing the saturated core stream passing across.
        sprite.px(2, 13, STEEL_DEEP)
        sprite.px(7, 18, STEEL_DEEP)
        for lx in range(8):
            c = _pulse_tone((lx - frame) % 16, anim, deep, dim, mid, bright, dashed, core_pin=True)
            sprite.px(1 + lx, 15, c)
            sprite.px(1 + lx, 16, c)
            sprite.px(1 + lx, 14, deep if c in (mid, bright, IVORY) else WELL)
            sprite.px(1 + lx, 17, deep if c in (mid, bright, IVORY) else WELL)

    # 4. End cap (6 x 6 at x=20..26, y=12..18) for exposed glass/conduit ends on item models.
    sprite.rect(20, 12, 26, 18, SEAM)
    sprite.rect(21, 13, 25, 17, WELL)
    sprite.rect(22, 14, 24, 16, hub_core)
    return sprite


def controller_front(device):
    sprite = sealed_frame(SHADE)
    plinth(sprite, 28)
    sprite.rect(5, 5, 27, 17, SEAM)
    sprite.row(3, 6, 12, IVORY)
    if device == 'chemical_reactor_controller':
        for x in (5, 11, 17):
            sprite.rect(x, 19, x + 4, 23, STEEL)
            sprite.bevel(x, 19, x + 4, 23, TRIM, STEEL_SHADE)
            sprite.rect(x + 1, 20, x + 3, 22, WELL)
        sprite.px(12, 20, COPPER)
        sprite.rect(24, 18, 26, 26, SEAM)
        sprite.rect(23, 19, 27, 21, STEEL)
        sprite.row(19, 23, 27, TRIM)
    elif device == 'decomposer_reactor_controller':
        for x in (5, 15):
            sprite.rect(x, 19, x + 6, 24, STEEL)
            sprite.bevel(x, 19, x + 6, 24, TRIM, STEEL_SHADE)
            sprite.rect(x + 1, 20, x + 5, 23, WELL)
        sprite.rect(12, 20, 14, 23, COPPER)
        sprite.row(20, 12, 14, COPPER_LIGHT)
        sprite.rect(24, 18, 26, 26, SEAM)
        sprite.rect(23, 22, 27, 24, STEEL)
        sprite.row(22, 23, 27, TRIM)
    elif device == 'synthesizer_reactor_controller':
        for x in (5, 11):
            sprite.rect(x, 19, x + 4, 23, STEEL)
            sprite.bevel(x, 19, x + 4, 23, TRIM, STEEL_SHADE)
            sprite.rect(x + 1, 20, x + 3, 22, WELL)
        sprite.rect(17, 20, 23, 22, SEAM)
        sprite.rect(19, 19, 21, 23, STEEL)
        sprite.row(19, 19, 21, TRIM)
        sprite.rect(25, 18, 27, 20, COPPER)
        sprite.row(18, 25, 27, COPPER_LIGHT)
        sprite.rect(25, 23, 27, 25, STEEL)
        sprite.row(23, 25, 27, TRIM)
    elif device == 'transmutation_reactor_controller':
        # Dual-crystal collider console: armored steel interlocks + static Violet & Ember key bars.
        for x in (5, 13):
            sprite.rect(x, 19, x + 6, 24, STEEL)
            sprite.bevel(x, 19, x + 6, 24, TRIM, STEEL_SHADE)
            sprite.rect(x + 1, 20, x + 5, 23, WELL)
        sprite.rect(6, 21, 10, 22, RGB['violet'])
        sprite.rect(14, 21, 18, 22, RGB['ember'])
        sprite.rect(21, 19, 27, 25, STEEL_DEEP)
        sprite.bevel(21, 19, 27, 25, STEEL, SEAM)
        sprite.rect(23, 21, 25, 23, COPPER)
    elif device == 'primal_reactor_controller':
        # Singularity control deck: static Violet recipe crystal socket beside steel capacitor keys.
        sprite.rect(5, 19, 13, 24, STEEL)
        sprite.bevel(5, 19, 13, 24, TRIM, STEEL_SHADE)
        sprite.rect(6, 20, 12, 23, WELL)
        sprite.rect(7, 21, 11, 22, RGB['violet'])
        for x in (15, 21):
            sprite.rect(x, 19, x + 5, 24, STEEL_DEEP)
            sprite.bevel(x, 19, x + 5, 24, STEEL, SEAM)
            sprite.rect(x + 1, 21, x + 4, 23, COPPER)
    elif device == 'energy_matrix_controller':
        # High-voltage matrix console: static Ember & Blue recipe crystal bars beside steel breaker switch.
        for x, col in ((5, RGB['ember']), (13, RGB['blue'])):
            sprite.rect(x, 19, x + 6, 24, STEEL)
            sprite.bevel(x, 19, x + 6, 24, TRIM, STEEL_SHADE)
            sprite.rect(x + 1, 20, x + 5, 23, WELL)
            sprite.rect(x + 1, 21, x + 5, 22, col)
        sprite.rect(21, 19, 27, 25, STEEL_DEEP)
        sprite.bevel(21, 19, 27, 25, STEEL, SEAM)
        sprite.rect(23, 21, 25, 23, COPPER)
    elif device == 'energy_reactor_controller':
        # Thermal-power reactor console: armored Ember & Amber fuel/flux keys beside Copper heavy breaker.
        for x, col in ((5, RGB['ember']), (13, RGB['amber'])):
            sprite.rect(x, 19, x + 6, 24, STEEL)
            sprite.bevel(x, 19, x + 6, 24, TRIM, STEEL_SHADE)
            sprite.rect(x + 1, 20, x + 5, 23, WELL)
            sprite.rect(x + 1, 21, x + 5, 22, col)
        sprite.rect(21, 19, 27, 25, STEEL_DEEP)
        sprite.bevel(21, 19, 27, 25, STEEL, SEAM)
        sprite.rect(23, 21, 25, 23, COPPER)
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
    if device == 'primal_reactor_controller':
        from hardware_sprites import PRIMAL_BODY, PRIMAL_CORE, PRIMAL_CORE_SOFT, PRIMAL_DEEP, PRIMAL_LIGHT, PRIMAL_VOID, PRIMAL_TEAL, PRIMAL_COOL, PRIMAL_WARM, PRIMAL_GOLD
        sprite = Sprite(fill=SEAM)
        sprite.rect(6, 6, 26, 16, PRIMAL_VOID if active else WELL)
        if active:
            sprite.row(10, 7, 14, PRIMAL_TEAL)
            sprite.row(11, 7, 14, PRIMAL_COOL)
            sprite.row(10, 18, 25, PRIMAL_WARM)
            sprite.row(11, 18, 25, PRIMAL_GOLD)
            sprite.rect(13, 8, 19, 14, PRIMAL_BODY)
            sprite.rect(14, 9, 18, 13, PRIMAL_LIGHT)
            sprite.rect(15, 10, 17, 12, PRIMAL_CORE)
        else:
            sprite.row(10, 7, 14, PRIMAL_DEEP)
            sprite.row(10, 18, 25, PRIMAL_DEEP)
            sprite.rect(14, 9, 18, 13, PRIMAL_DEEP)
            sprite.rect(15, 10, 17, 12, PRIMAL_BODY)
        return sprite
    color = accent(device)
    if active:
        back, ink, glow = scale(color, .22), color, lighten(color, .5)
    else:
        back, ink, glow = WELL, scale(color, .57), scale(color, .57)
    sprite = Sprite(fill=SEAM)
    sprite.rect(6, 6, 26, 16, back)
    if device == 'chemical_reactor_controller':
        sprite.rect(7, 8, 13, 14, ink)
        sprite.rect(8, 9, 12, 13, back)
        sprite.rect(9, 10, 11, 12, glow)
        for y, length in ((7, 10), (10, 6), (13, 8)):
            sprite.rect(15, y, 15 + length, y + 2, ink)
        sprite.rect(23, 7, 25, 9, glow)
    elif device == 'decomposer_reactor_controller':
        sprite.rect(7, 8, 9, 14, ink)
        sprite.rect(11, 8, 13, 14, ink)
        sprite.rect(9, 10, 11, 12, glow)
        for y, length in ((7, 10), (10, 7), (13, 4)):
            sprite.rect(15, y, 15 + length, y + 2, ink)
        sprite.rect(23, 13, 25, 15, glow)
    elif device == 'synthesizer_reactor_controller':
        sprite.rect(9, 8, 11, 14, ink)
        sprite.rect(7, 10, 13, 12, ink)
        sprite.rect(9, 10, 11, 12, glow)
        for y, length in ((7, 4), (10, 7), (13, 10)):
            sprite.rect(15, y, 15 + length, y + 2, ink)
        sprite.rect(23, 13, 25, 15, glow)
    elif device == 'transmutation_reactor_controller':
        ember = RGB['ember'] if active else scale(RGB['ember'], .57)
        ember_hi = lighten(RGB['ember'], .5) if active else ember
        sprite.rect(7, 8, 13, 14, ink)
        sprite.rect(8, 9, 12, 13, back)
        sprite.rect(9, 10, 11, 12, ember)
        sprite.row(10, 14, 18, ink)
        sprite.row(11, 14, 18, glow)
        sprite.rect(19, 8, 25, 14, ember)
        sprite.rect(20, 9, 24, 13, back)
        sprite.rect(21, 10, 23, 12, ember_hi)
    elif device == 'energy_matrix_controller':
        blue = RGB['blue'] if active else scale(RGB['blue'], .57)
        blue_hi = lighten(RGB['blue'], .5) if active else blue
        sprite.rect(7, 7, 13, 15, blue)
        sprite.rect(8, 8, 12, 14, back)
        sprite.rect(9, 10, 11, 12, blue_hi)
        for i, h in enumerate((3, 5, 7, 5)):
            x = 15 + i * 3
            sprite.rect(x, 15 - h, x + 2, 15, ink)
            sprite.row(15 - h, x, x + 2, glow)
    elif device == 'energy_reactor_controller':
        amber = RGB['amber'] if active else scale(RGB['amber'], .57)
        amber_hi = lighten(RGB['amber'], .5) if active else amber
        sprite.rect(7, 8, 13, 14, ink)
        sprite.rect(8, 9, 12, 13, back)
        sprite.rect(9, 10, 11, 12, glow)
        for i, h in enumerate((3, 5, 7, 6)):
            x = 15 + i * 3
            sprite.rect(x, 15 - h, x + 2, 15, amber)
            sprite.row(15 - h, x, x + 2, amber_hi)
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
        'transmutation_reactor_glass_pane': transmutation_reactor_glass_pane(),
        'transmutation_machine_port_unformed': transmutation_machine_port(PORT_UNFORMED),
        'energy_matrix_casing': energy_matrix_casing(),
        'energy_matrix_glass_pane': energy_matrix_glass_pane(),
        'energy_matrix_port_unformed': energy_matrix_port(PORT_UNFORMED),
        'energy_reactor_glass_pane': energy_reactor_glass_pane(),
        'energy_reactor_port_unformed': energy_reactor_port(PORT_UNFORMED),
        'drive_front': drive_front(),
        'drive_bay': drive_bay(),
        'drive_cell_4m': drive_cell(2),
        'drive_cell_16m': drive_cell(4),
        'drive_cell_64m': drive_cell(6),
        'drive_cell_256m': drive_cell(6, crystal='mint'),
        'drive_cell_unknown': drive_cell(None),
        'drive_void_matter_cell': void_drive_cell(),
        'drive_primal_cell_16k': primal_drive_cell(2),
        'drive_primal_cell_64k': primal_drive_cell(4),
        'drive_primal_cell_256k': primal_drive_cell(6),
        'drive_primal_cell_1m': primal_drive_cell(6, crown=True),
        'drive_primal_cell_4m': primal_drive_cell(6, crown=True, apex=True),
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
        'chemical_processor_front': chassis_front('chemical_processor'),
    }
    from bench_showpieces import DEVICES, bench_sprites
    result.update(bench_sprites(Sprite))
    for device in DEVICES:result[device+'_front']=chassis_front(device)
    from reactor_showpiece import reactor_sprites
    result.update(reactor_sprites(Sprite))
    for name in ('blue', 'green', 'amber'):
        result['led_' + name] = flat(LED[name])
    for name in ('violet', 'ice', 'copper', 'leaf', 'amber', 'ember', 'mint'):
        result['light_' + name] = flat(RGB[name])
    result['light_blue_standby'] = flat(scale(RGB['blue'], STANDBY))
    for mode, color in PORT_MODES.items():
        result['machine_port_' + mode] = machine_port(color, contact=mode != 'disabled', mode=mode)
        result['transmutation_machine_port_' + mode] = transmutation_machine_port(color, contact=mode != 'disabled', mode=mode)
        result['energy_matrix_port_' + mode] = energy_matrix_port(color, contact=mode != 'disabled', mode=mode)
        result['energy_reactor_port_' + mode] = energy_reactor_port(color, contact=mode != 'disabled', mode=mode)
    for name, tier in CABLE_TIERS.items():
        result[name] = cable(tier, CABLE_LOOK)
    controllers = ('chemical_reactor_controller', 'preparation_reactor_controller',
                   'decomposer_reactor_controller', 'synthesizer_reactor_controller',
                   'transmutation_reactor_controller', 'primal_reactor_controller',
                   'energy_matrix_controller', 'energy_reactor_controller')
    for device in controllers:
        result[device + '_front'] = controller_front(device)
        result[device + '_display'] = controller_display(device, False)
        result[device + '_display_active'] = controller_display(device, True)
    from work_sprites import strip,activity_bar,terminal_screen as terminal_frames,interface_light,controller_display as controller_frames
    result['transmutation_reactor_casing'] = strip(transmutation_reactor_casing_frame(f) for f in range(16))
    result['energy_reactor_casing'] = strip(energy_reactor_casing_frame(f) for f in range(16))
    result['primal_reactor_casing'] = strip(primal_reactor_casing_frame(f) for f in range(16))
    result['primal_reactor_glass_pane'] = strip(primal_reactor_glass_pane_frame(f) for f in range(16))
    for prefix, tier_key in (
            ('reactor_glass_pane', 'standard'),
            ('transmutation_reactor_glass_pane', 'transmutation'),
            ('primal_reactor_glass_pane', 'primal'),
            ('energy_matrix_glass_pane', 'energy_matrix'),
            ('energy_reactor_glass_pane', 'energy_reactor')):
        for mask in range(1, 15):
            if tier_key == 'primal':
                result[f'{prefix}_ctm_{mask}'] = strip(connected_glass_pane(tier_key, mask, f) for f in range(16))
            else:
                result[f'{prefix}_ctm_{mask}'] = connected_glass_pane(tier_key, mask)
        for corner in range(4):
            result[f'{prefix}_corner_{corner}'] = connected_glass_corner(tier_key, corner)
    result['primal_machine_port_unformed'] = strip(primal_machine_port_frame(PORT_UNFORMED, f) for f in range(16))
    for mode, color in PORT_MODES.items():
        result['primal_machine_port_' + mode] = strip(primal_machine_port_frame(color, f, contact=mode != 'disabled', mode=mode) for f in range(16))
    cable_frames = 32 if (CABLE_LOOK == 'pcb_traces' and CABLE_ANIM == 'single_pulse') else 16
    for name, tier in CABLE_TIERS.items():
        if CABLE_ANIM == 'idle_quiet':
            result[name + '_dim'] = cable_dim_frame(name, tier, 0, CABLE_LOOK, CABLE_ANIM)
            result[name + '_glow'] = cable_glow_frame(name, tier, 0, CABLE_LOOK, CABLE_ANIM)
        else:
            result[name + '_dim'] = strip(cable_dim_frame(name, tier, f, CABLE_LOOK, CABLE_ANIM) for f in range(cable_frames))
            result[name + '_glow'] = strip(cable_glow_frame(name, tier, f, CABLE_LOOK, CABLE_ANIM) for f in range(cable_frames))
    result['activity_on']=strip(activity_bar(f) for f in range(16))
    result['terminal_screen_active']=strip(terminal_frames(f) for f in range(16))
    result['interface_light_active']=strip(interface_light(f) for f in range(16))
    for device in controllers:
        result[device+'_display_active']=strip(controller_frames(device,f) for f in range(16))
    return result


# Animation metadata by sprite name.
def build_animations(cable_anim=None):
    anim = CABLE_ANIM if cable_anim is None else cable_anim
    meta = {'led_red': {'animation': {'frametime': 10}}}
    for name in ('drive_primal_cell_16k', 'drive_primal_cell_64k', 'drive_primal_cell_256k', 'drive_primal_cell_1m', 'drive_primal_cell_4m'):
        meta[name] = {'animation': {'frametime': 3}}
    from reactor_showpiece import REACTOR_ANIMATIONS
    meta.update(REACTOR_ANIMATIONS)
    if anim != 'idle_quiet':
        if anim == 'single_pulse' and CABLE_LOOK == 'pcb_traces':
            ft_basic, ft_fast = 4, 3
        else:
            ft_basic = 3 if anim == 'single_pulse' else 2
            ft_fast = 2
        for name, frametime in (('matter_cable_glow', ft_basic), ('reinforced_matter_cable_glow', ft_fast), ('resonant_matter_cable_glow', ft_fast)):
            meta[name] = {'animation': {'frametime': frametime, 'interpolate': True}}
        for name, frametime in (('matter_cable_dim', ft_basic), ('reinforced_matter_cable_dim', ft_fast), ('resonant_matter_cable_dim', ft_fast)):
            meta[name] = {'animation': {'frametime': frametime if CABLE_POWER_CONTRAST == 'rail_stripes' else 6, 'interpolate': True}}
    for name, frametime in {'activity_on':5,'terminal_screen_active':5,'interface_light_active':2,'chemical_reactor_controller_display_active':4,'preparation_reactor_controller_display_active':4,'decomposer_reactor_controller_display_active':4,'synthesizer_reactor_controller_display_active':4,'transmutation_reactor_controller_display_active':3,'primal_reactor_controller_display_active':3,'energy_matrix_controller_display_active':3,'energy_reactor_controller_display_active':3,'transmutation_reactor_casing':3,'energy_reactor_casing':3,'primal_reactor_casing':3,'primal_reactor_glass_pane':3,**{f'primal_reactor_glass_pane_ctm_{m}':3 for m in range(1,15)},'primal_machine_port_unformed':3,**{'primal_machine_port_'+mode:3 for mode in PORT_MODES},**{'bench_'+device+'_work_active':2 if device in ('decomposer','material_preparator','deep_extractor') else 3 if device in ('sinter_furnace','atmospheric_collector') else 4 for device in ('analyzer','decomposer','material_synthesizer','material_preparator','sinter_furnace','atmospheric_collector','deep_extractor')}}.items():
        meta[name] = {'animation': {'frametime': frametime, 'interpolate': True}}
    return meta


ANIMATIONS = build_animations()
