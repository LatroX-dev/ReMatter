"""Palette of the sealed-cube and alcove machine family.

Every sprite of tools/generate_family_assets.py takes its colours from here.
The casing is a dark neutral grey, so status colours and the light steel of
trims and showpieces stand out against it, while recesses stay a step darker
still. Colour is reserved for meaning: status, mode and device identity.
"""
from machine_palette import COPPER_CONTACT, FAMILY, RGB


def rgb(value):
    return tuple(bytes.fromhex(value.lstrip('#')))


def scale(color, factor):
    """Darken a colour toward black."""
    return tuple(max(0, min(255, round(channel * factor))) for channel in color)


def lighten(color, amount):
    """Mix a colour toward white."""
    return tuple(round(channel + (255 - channel) * amount) for channel in color)


# Casing roles, light to dark. The edge stays readable against the darker graphite field.
HIGHLIGHT = rgb('#495059')
BASE = rgb('#2b2f35')
SHADE = rgb('#1d2127')
SEAM = rgb('#13161b')
WELL = rgb('#0a0d11')
# Light steel of keys, knobs, process hardware and mounted cell faceplates.
TRIM = rgb('#c3ccce')
STEEL = rgb('#9aa6ab')
STEEL_SHADE = rgb('#6b777d')
STEEL_DEEP = rgb('#4a545a')
# Connector hardware is dark gunmetal; broad frames should belong to the graphite casing.
PORT_METAL = rgb('#46515b')
PORT_LIGHT = rgb('#64727e')
PORT_DARK = rgb('#2d363f')
PORT_AUTOMATIC = rgb('#4d5a64')
COPPER = COPPER_CONTACT
COPPER_LIGHT = lighten(COPPER, .22)
COPPER_DARK = scale(COPPER, .72)
# The maker's badge sits in the floor shade of a front, so it is drawn a step brighter than a contact.
BADGE = RGB['copper']
BADGE_LIGHT = lighten(BADGE, .28)
IVORY = rgb('#e4decc')
# The cable jacket stays below the casing, so a cable reads as a line in front of a machine.
JACKET = rgb('#181c22')

# A hue on a cell or lamp always means status, never tier or decoration.
LED = {
    'blue': rgb('#54b8f1'),
    'green': rgb('#4fe18e'),
    'amber': rgb('#f1c54a'),
    'red': rgb('#eb5862'),
}
# The dark phase of the blinking red light stays red, so an unreadable cell never reads as an empty bay.
LED_RED_DIM = rgb('#a33a42')

ACTIVITY_ON = RGB['mint']
ACTIVITY_OFF = rgb('#275c63')
# A screen or lens that waits for its network keeps a dim tint of its device colour.
STANDBY = .45

# Cable tiers differ by stripe colour and by stripe width in texels.
CABLE_TIERS = {
    'matter_cable': {'stripe': RGB['copper'], 'width': 2, 'dashed': False},
    'reinforced_matter_cable': {'stripe': rgb('#54bfac'), 'width': 4, 'dashed': False},
    'resonant_matter_cable': {'stripe': rgb('#be8fbb'), 'width': 4, 'dashed': True},
}

# Machine Port modes in the order of the block state property.
PORT_MODES = {
    'automatic': PORT_AUTOMATIC,
    'item_input': RGB['copper'],
    'item_output': RGB['ember'],
    'fluid_input': RGB['blue'],
    'fluid_output': RGB['violet'],
    'power': RGB['amber'],
    'matter': RGB['mint'],
    'chemical': RGB['leaf'],
    'disabled': SEAM,
}
PORT_UNFORMED = SHADE


def accent(device):
    """The device accent keeps its existing assignment."""
    return RGB[FAMILY[device]]
