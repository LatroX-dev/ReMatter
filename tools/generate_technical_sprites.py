#!/usr/bin/env python3
"""Draw machine components, circuits, and shared upgrade/module hardware from reproducible vector geometry.

The shared projection keeps every edge aligned. Broad silhouettes and a single
central feature distinguish the components at Minecraft inventory scale.
"""
import math
from component_drawing import Drawing, iso


def contacts(d, y=22, z=1.5):
    """Three broad copper fingers for legacy board/module variants."""
    for x in (2, 8, 14):
        d.box(x, y, z, 4, 4, .8, top='copper_light', left='copper', right='copper_dark')


# ------------------------------------------------------------------------------
# Unified Upgrades & Modules Chassis (shared by Speed, Efficiency, Parallel,
# Pressure Module, and Wireless Booster so every slot-in module reads as one family).
# ------------------------------------------------------------------------------
UPGRADE_CHASSIS_LOOKS = ('cartridge_case', 'bevel_module', 'dock_pack')
UPGRADE_CHASSIS_LOOK = 'cartridge_case'


def upgrade_chassis(d, look=None):
    """Draw the shared base housing for all ReMatter upgrades and slot-in modules.

    Returns the top Z height of the recessed emblem floor where each upgrade draws its glyph.
    """
    selected = UPGRADE_CHASSIS_LOOK if look is None else look
    if selected not in UPGRADE_CHASSIS_LOOKS:
        raise ValueError(f'Unknown upgrade chassis look: {selected}')

    if selected == 'cartridge_case':
        # Armored Mekanism-inspired plug-in module cartridge: notched steel pull-tab at back,
        # raised graphite/steel side rails, deep central emblem bay, and stepped 3-pin copper foot.
        d.box(3.5, 0.0, 0.4, 13.0, 2.6, 3.6, top='steel', left='body', right='dark', width=.64)
        d.top_line([(4.6, 1.1), (15.4, 1.1)], 4.05, 'white', .56)
        d.box(0.0, 2.2, 0.0, 20.0, 20.8, 3.4, top='top', left='body', right='shadow', width=.66)
        # Raised armored steel side rails framing the central bay.
        d.box(0.4, 2.6, 3.4, 1.9, 19.8, 0.65, top='edge', left='body', right='dark', width=.48)
        d.box(17.7, 2.6, 3.4, 1.9, 19.8, 0.65, top='edge', left='body', right='dark', width=.48)
        d.top_line([(1.3, 21.4), (1.3, 3.4)], 4.09, 'white', .46)
        # Deep dark central emblem well for crisp contrast at 16x16.
        d.top_polygon([(2.6, 3.6), (17.4, 3.6), (17.4, 21.4), (2.6, 21.4)], 3.46, 'shadow', width=.6)
        d.top_line([(3.2, 20.4), (3.2, 4.2), (16.8, 4.2)], 3.50, 'body', .48)
        # Stepped front connector foot with three copper edge-connector pins.
        d.box(1.8, 22.8, 0.2, 16.4, 2.2, 2.2, top='dark', left='shadow', right='dark', width=.6)
        for px in (3.0, 8.3, 13.6):
            d.box(px, 23.0, 1.6, 3.4, 2.4, 0.85, top='copper_light', left='copper', right='copper_dark', width=.52)
        return 3.56

    if selected == 'bevel_module':
        # Clean beveled plug-in module plate with four corner steel lock-bolts and 3 protruding tabs.
        d.box(0.0, 0.0, 0.0, 20.0, 23.6, 3.0, top='top', left='body', right='shadow', width=.66)
        d.box(1.6, 1.6, 3.0, 16.8, 19.8, 0.55, top='shadow', left='body', right='dark', width=.56)
        d.top_line([(1.0, 16.0), (1.0, 1.0), (19.0, 1.0), (19.0, 14.0)], 3.06, 'white', .56)
        for bx, by in ((0.6, 0.6), (17.4, 0.6), (0.6, 21.0), (17.4, 21.0)):
            d.top_polygon([(bx, by), (bx + 2.0, by), (bx + 2.0, by + 2.0), (bx, by + 2.0)], 3.12, 'steel', width=.42)
        contacts(d, y=22.4, z=1.6)
        return 3.62

    # 'dock_pack': Heavy twin-rail industrial pack with continuous steel bumpers and 4-pin copper bus.
    d.box(0.8, 1.0, 0.0, 18.4, 22.0, 3.2, top='dark', left='body', right='shadow', width=.65)
    d.box(0.0, 0.4, 0.0, 2.6, 23.2, 4.1, top='steel', left='body', right='dark', width=.58)
    d.box(17.4, 0.4, 0.0, 2.6, 23.2, 4.1, top='steel', left='body', right='dark', width=.58)
    d.top_line([(1.2, 22.4), (1.2, 1.4)], 4.15, 'white', .52)
    d.top_polygon([(2.8, 2.6), (17.2, 2.6), (17.2, 21.2), (2.8, 21.2)], 3.26, 'shadow', width=.58)
    d.box(2.6, 22.8, 0.3, 14.8, 2.0, 2.0, top='dark', left='shadow', right='dark', width=.56)
    for px in (3.3, 6.9, 10.5, 14.1):
        d.box(px, 23.2, 1.3, 2.5, 2.1, 0.85, top='copper_light', left='copper', right='copper_dark', width=.48)
    return 3.36


def speed_upgrade(look=None):
    d = Drawing()
    z = upgrade_chassis(d, look)
    # Bold double forward speed chevrons (>>) in Rhedon/Redstone with bright Lumivex leading bevel.
    for dy, main_col, shade_col in ((0.0, 'rhedon', 'redstone'), (6.2, 'redstone', 'rhedon')):
        chev = [
            (4.2, 11.4 + dy), (10.0, 5.2 + dy), (15.8, 11.4 + dy),
            (15.8, 14.6 + dy), (10.0, 8.8 + dy), (4.2, 14.6 + dy),
        ]
        d.top_polygon(chev, z + 0.15, main_col, width=.72)
        d.top_polygon([(10.0, 5.2 + dy), (15.8, 11.4 + dy), (15.8, 14.6 + dy), (10.0, 8.8 + dy)],
                      z + 0.19, shade_col, stroke=shade_col, width=0)
        d.top_line([(5.3, 11.8 + dy), (10.0, 6.8 + dy), (14.4, 11.4 + dy)], z + 0.24, 'lumivex', .64)
    return d


def efficiency_upgrade(look=None):
    d = Drawing()
    z = upgrade_chassis(d, look)
    # Broad ceramic & diamond-blue capacitor shield with bold central Lumivex lightning bolt.
    d.top_polygon([(4.2, 5.2), (15.8, 5.2), (15.8, 13.0), (10.0, 19.8), (4.2, 13.0)],
                  z + 0.15, 'ceramic', width=.76)
    d.top_polygon([(10.0, 5.2), (15.8, 5.2), (15.8, 13.0), (10.0, 19.8)],
                  z + 0.19, 'diamond_dark', stroke='diamond_dark', width=0)
    d.top_line([(5.2, 12.2), (5.2, 6.2), (14.4, 6.2)], z + 0.22, 'white', .52)
    d.top_polygon([(10.8, 6.6), (6.5, 12.4), (9.6, 12.4), (8.6, 17.8),
                   (13.8, 11.0), (10.6, 11.0)], z + 0.28, 'lumivex', width=.62)
    return d


def parallel_upgrade_drawing(look=None):
    d = Drawing()
    z = upgrade_chassis(d, look)
    # Three bold raised steel-and-Lumivex parallel channel bars (|||) inside the shared bay.
    for x in (4.1, 8.6, 13.1):
        d.box(x, 5.2, z + 0.06, 2.8, 14.4, 1.45, top='steel', left='body', right='dark', width=.58)
        d.top_line([(x + 0.8, 18.6), (x + 0.8, 6.2), (x + 2.0, 6.2)], z + 1.56, 'white', .46)
        d.top_polygon([(x + 0.5, 15.8), (x + 2.3, 15.8), (x + 2.3, 18.6), (x + 0.5, 18.6)],
                      z + 1.55, 'lumivex', stroke='ink', width=.36)
    return d


def pressure_module_drawing(look=None):
    d = Drawing()
    z = upgrade_chassis(d, look)
    # Raised 3D copper-capped hydraulic pressure piston mounted on a Rhevon pressure plate.
    d.box(4.2, 5.8, z + 0.06, 11.6, 12.4, 1.35, top='rhevon', left='body', right='dark', width=.6)
    d.top_line([(5.2, 17.0), (5.2, 6.8), (14.6, 6.8)], z + 1.45, 'edge', .48)
    d.box(6.4, 8.0, z + 1.42, 7.2, 8.0, 2.85, top='steel', left='copper', right='copper_dark', width=.64)
    d.top_polygon([(7.3, 8.9), (12.7, 8.9), (12.7, 15.1), (7.3, 15.1)], z + 4.32, 'copper_light', width=.48)
    d.top_line([(8.0, 14.0), (8.0, 9.6), (11.8, 9.6)], z + 4.38, 'white', .52)
    return d


def wireless_booster_drawing(look=None):
    """Wireless Booster on the unified upgrade chassis: twin steel RF dipole bars and exact Blue crystal (#6794f1)."""
    d = Drawing()
    z = upgrade_chassis(d, look)
    # Twin compact steel RF dipole emitter bars at the upper bay.
    d.box(4.5, 4.4, z + 0.06, 2.2, 4.2, 1.7, top='steel', left='body', right='dark', width=.54)
    d.box(13.3, 4.4, z + 0.06, 2.2, 4.2, 1.7, top='steel', left='body', right='dark', width=.54)
    d.top_line([(5.2, 5.2), (5.9, 5.2)], z + 1.82, 'white', .46)
    d.top_line([(14.0, 5.2), (14.7, 5.2)], z + 1.82, 'white', .46)
    # Recessed crystal cradle + faceted Blue Matrix Crystal core with large flat exact #6794f1 table (>= 260 px).
    d.box(4.4, 8.6, z + 0.06, 11.2, 11.2, 1.15, top='dark', left='body', right='shadow', width=.58)
    gem_outer = [(10.0, 9.0), (14.8, 11.2), (14.8, 16.8), (10.0, 19.2), (5.2, 16.8), (5.2, 11.2)]
    d.top_polygon(gem_outer, z + 1.35, '#6794f1', stroke='ink', width=.78)
    # Lit upper-left crown facet, large exact #6794f1 main table, shaded right facet.
    d.top_polygon([(10.0, 9.4), (14.2, 11.4), (10.0, 12.6), (5.8, 11.4)],
                  z + 1.39, '#99b8f6', stroke='#99b8f6', width=0)
    d.top_polygon([(5.6, 11.5), (10.0, 12.6), (13.4, 15.8), (10.0, 18.7), (5.6, 16.5)],
                  z + 1.40, '#6794f1', stroke='#6794f1', width=0)
    d.top_polygon([(10.0, 12.6), (14.4, 11.5), (14.4, 16.6), (13.4, 15.8)],
                  z + 1.40, '#4463a2', stroke='#4463a2', width=0)
    d.top_line([(7.5, 11.4), (10.0, 10.4), (11.8, 11.2)], z + 1.46, 'white', .56)
    return d


def dimensional_card_drawing(look=None):
    """Dimensional Card on the unified upgrade chassis: twin Ender dimensional antenna rings and faceted Blue Matrix Crystal (#6794f1)."""
    d = Drawing()
    z = upgrade_chassis(d, look)
    # Recessed Netherite-steel dimensional housing bay.
    d.box(3.8, 4.6, z + 0.06, 12.4, 15.0, 1.0, top='netherite', left='body', right='shadow', width=.58)
    # Four diagonal steel phase emitters framing the dimensional core.
    for bx, by in ((4.4, 5.2), (13.6, 5.2), (4.4, 17.2), (13.6, 17.2)):
        d.box(bx, by, z + 1.06, 2.0, 1.8, 0.9, top='steel', left='copper', right='copper_dark', width=.48)
    # Twin horizontal dimensional resonance rails.
    d.top_line([(4.8, 7.6), (15.2, 7.6)], z + 1.18, '#99b8f6', .58)
    d.top_line([(4.8, 16.6), (15.2, 16.6)], z + 1.18, '#99b8f6', .58)
    # Large faceted Blue Matrix Crystal core (exact #6794f1 main table well above 260 px).
    gem_outer = [(10.0, 6.2), (15.0, 9.2), (15.0, 15.2), (10.0, 18.2), (5.0, 15.2), (5.0, 9.2)]
    d.top_polygon(gem_outer, z + 1.36, '#6794f1', stroke='ink', width=.78)
    d.top_polygon([(10.0, 6.7), (14.4, 9.4), (10.0, 10.7), (5.6, 9.4)],
                  z + 1.40, '#99b8f6', stroke='#99b8f6', width=0)
    d.top_polygon([(5.4, 9.5), (10.0, 10.7), (13.5, 14.8), (10.0, 17.7), (5.4, 14.9)],
                  z + 1.42, '#6794f1', stroke='#6794f1', width=0)
    d.top_polygon([(10.0, 10.7), (14.6, 9.5), (14.6, 15.0), (13.5, 14.8)],
                  z + 1.42, '#4463a2', stroke='#4463a2', width=0)
    d.top_polygon([(10.0, 11.4), (11.5, 12.6), (10.0, 13.8), (8.5, 12.6)],
                  z + 1.48, 'white', stroke='white', width=0)
    return d


def anchor_upgrade_drawing(look=None):
    """Anchor Upgrade on the unified upgrade chassis: unmistakable 3x3 chunk grid + forged Steel/Ivory anchor
    emblem locked onto a glowing Blue Matrix Crystal (#6794f1 >= 260 px) center chunk."""
    d = Drawing()
    z = upgrade_chassis(d, look)
    # Dark recessed 3x3 chunk-map plate with crisp steel coordinate grid lines.
    d.box(3.4, 4.4, z + 0.06, 13.2, 15.2, 0.9, top='dark', left='body', right='shadow', width=.58)
    # Grid lines dividing the plate into a crisp 3x3 chunk matrix.
    for gx in (7.8, 12.2):
        d.top_line([(gx, 5.0), (gx, 19.0)], z + 1.02, 'body', .42)
    for gy in (9.4, 14.6):
        d.top_line([(4.0, gy), (16.0, gy)], z + 1.02, 'body', .42)
    # Highlighted glowing Blue Matrix Crystal center chunk tile (exact #6794f1 >= 320 px).
    d.top_polygon([(5.2, 6.2), (14.8, 6.2), (14.8, 17.8), (5.2, 17.8)], z + 1.06, '#27406d', stroke='#4463a2', width=.45)
    d.top_polygon([(6.0, 7.2), (14.0, 7.2), (14.0, 16.8), (6.0, 16.8)], z + 1.12, '#6794f1', stroke='#99b8f6', width=.48)
    # Four raised steel chunk-corner locking brackets around the grid.
    for bx, by in ((3.6, 4.6), (13.8, 4.6), (3.6, 17.2), (13.8, 17.2)):
        d.box(bx, by, z + 1.0, 2.6, 2.2, 1.0, top='steel', left='rhevon', right='dark', width=.48)
    # Bold forged Steel + Ivory maritime/spatial Anchor glyph seated on top of the Blue core!
    # Top ring eye
    d.box(8.8, 5.4, z + 1.25, 2.4, 2.2, 1.05, top='white', left='steel', right='dark', width=.5)
    # Horizontal stock crossbar
    d.box(6.4, 8.2, z + 1.25, 7.2, 1.6, 1.0, top='steel', left='body', right='dark', width=.48)
    # Central vertical shank
    d.box(9.1, 7.4, z + 1.25, 1.8, 9.2, 1.1, top='white', left='steel', right='dark', width=.48)
    # Curved twin anchor flukes at bottom (bold chevron pointing down-left & down-right)
    fluke = [(5.2, 13.4), (6.8, 12.6), (10.0, 15.6), (13.2, 12.6), (14.8, 13.4), (10.0, 18.2)]
    d.top_polygon(fluke, z + 2.38, 'steel', stroke='ink', width=.64)
    d.top_line([(5.9, 13.4), (10.0, 17.1), (14.1, 13.4)], z + 2.44, 'white', .46)
    return d


def extraction_focus_upgrade_drawing(look=None, frame=0, total_frames=16):
    """Extraction Focus Upgrade on the unified upgrade chassis: twin steel collimator rails, animated Mint
    targeting scan ring, and a faceted Mint Matrix Crystal core (#57cddb >= 260 px on every frame)."""
    d = Drawing()
    z = upgrade_chassis(d, look)
    phase = 2.0 * math.pi * (frame % total_frames) / total_frames
    wave = 0.5 + 0.5 * math.sin(phase)
    sweep_y = 5.4 + 13.4 * (0.5 - 0.5 * math.cos(phase))
    # Armored Rhevon collimator collar cradling the Mint targeting prism.
    d.box(3.8, 4.8, z + 0.06, 12.4, 14.8, 0.95, top='dark', left='body', right='shadow', width=.58)
    # Sweeping Mint seismic scan line across the recessed collimator bay.
    d.top_line([(4.2, sweep_y), (15.8, sweep_y)], z + 1.05, '#9ce3ec', .55)
    # Four corner targeting brackets that pulse in brightness smoothly across the loop.
    bracket_col = '#9ce3ec' if wave > 0.55 else '#57cddb'
    for bx, by, dx, dy in ((4.6, 5.6, 2.2, 2.2), (15.4, 5.6, -2.2, 2.2),
                           (4.6, 18.8, 2.2, -2.2), (15.4, 18.8, -2.2, -2.2)):
        d.top_line([(bx + dx, by), (bx, by), (bx, by + dy)], z + 1.12, bracket_col, .62)
    # Faceted Mint Matrix Crystal core (exact #57cddb main table well above 260 px).
    gem_outer = [(10.0, 6.4), (14.8, 9.2), (14.8, 15.2), (10.0, 18.0), (5.2, 15.2), (5.2, 9.2)]
    d.top_polygon(gem_outer, z + 1.32, '#57cddb', stroke='ink', width=.76)
    d.top_polygon([(10.0, 6.8), (14.2, 9.4), (10.0, 10.6), (5.8, 9.4)],
                  z + 1.36, '#9ce3ec', stroke='#9ce3ec', width=0)
    d.top_polygon([(5.6, 9.5), (10.0, 10.6), (13.3, 14.4), (10.0, 17.5), (5.6, 14.9)],
                  z + 1.38, '#57cddb', stroke='#57cddb', width=0)
    d.top_polygon([(10.0, 10.6), (14.4, 9.5), (14.4, 15.0), (13.3, 14.4)],
                  z + 1.38, '#388690', stroke='#388690', width=0)
    # Pulsing downward drill-focus spark at the center of the crystal.
    pulse = 0.7 + 0.9 * wave
    d.top_polygon([(10.0, 12.2 - pulse), (10.0 + pulse, 12.2), (10.0, 12.2 + pulse), (10.0 - pulse, 12.2)],
                  z + 1.46, 'white', stroke='white', width=0)
    d.top_line([(7.4, 9.2), (10.0, 7.8), (12.0, 9.0)], z + 1.48, 'white', .52)
    return d


def mass_compression_upgrade_drawing(look=None, frame=0, total_frames=16):
    """Mass Compression Upgrade on the unified upgrade chassis: four animated hydraulic steel/copper compression
    pistons pulsing inward around a super-dense Diamond Lattice singularity core. No crystal hue."""
    d = Drawing()
    z = upgrade_chassis(d, look)
    phase = 2.0 * math.pi * (frame % total_frames) / total_frames
    squeeze = 0.5 - 0.5 * math.cos(phase)  # 0 at rest -> 1 at peak compression -> 0 seamlessly
    shift = 1.05 * squeeze
    # Armored Netherite compression cavity floor.
    d.box(3.8, 4.8, z + 0.06, 12.4, 14.8, 0.9, top='netherite', left='body', right='shadow', width=.58)
    # Four hydraulic steel/copper compression jaws sliding symmetrically toward center (10.0, 12.2).
    # Top jaw
    d.box(7.2, 5.2 + shift, z + 1.0, 5.6, 2.8, 1.3, top='steel', left='copper', right='copper_dark', width=.54)
    d.top_line([(7.9, 6.2 + shift), (12.1, 6.2 + shift)], z + 2.36, 'white', .48)
    # Bottom jaw
    d.box(7.2, 16.4 - shift, z + 1.0, 5.6, 2.8, 1.3, top='steel', left='copper', right='copper_dark', width=.54)
    d.top_line([(7.9, 17.4 - shift), (12.1, 17.4 - shift)], z + 2.36, 'copper_light', .48)
    # Left jaw
    d.box(4.2 + shift, 9.4, z + 1.0, 2.5, 5.6, 1.3, top='copper_light', left='copper', right='copper_dark', width=.52)
    # Right jaw
    d.box(13.3 - shift, 9.4, z + 1.0, 2.5, 5.6, 1.3, top='copper_light', left='copper', right='copper_dark', width=.52)
    # Central compressed Diamond-Lattice pressure core (glows brighter white-cyan at peak compression).
    core_r = 2.7 - 0.55 * squeeze
    cx, cy = 10.0, 12.2
    core_poly = [(cx, cy - core_r), (cx + core_r, cy), (cx, cy + core_r), (cx - core_r, cy)]
    d.top_polygon(core_poly, z + 1.42, 'diamond_light' if squeeze > 0.45 else 'diamond', stroke='ink', width=.64)
    inner_r = 1.15 + 0.45 * squeeze
    d.top_polygon([(cx, cy - inner_r), (cx + inner_r, cy), (cx, cy + inner_r), (cx - inner_r, cy)],
                  z + 1.48, 'white' if squeeze > 0.35 else 'glass', stroke='white', width=0)
    return d


# ------------------------------------------------------------------------------
# Distinct Electronic Circuits (IC Microchips — visually unmistakable vs Upgrades)
# ------------------------------------------------------------------------------
CIRCUIT_LOOKS = ('ic_microchip', 'edge_pcb', 'wafer_die')
CIRCUIT_LOOK = 'ic_microchip'


def _ic_pin_x(d, x, y, w, pin_top, pin_side, pin_dark):
    d.box(x, y, 0.3, w, 2.2, 1.0, top=pin_top, left=pin_side, right=pin_dark, width=.5)


def _ic_pin_y(d, x, y, depth, pin_top, pin_side, pin_dark):
    d.box(x, y, 0.3, 2.2, depth, 1.0, top=pin_top, left=pin_side, right=pin_dark, width=.5)


def control_circuit(look=None):
    selected = CIRCUIT_LOOK if look is None else look
    if selected not in CIRCUIT_LOOKS:
        raise ValueError(f'Unknown circuit look: {selected}')
    d = Drawing()

    if selected == 'ic_microchip':
        # Classic dual-in-line IC microchip: 4+4 gull-wing copper pins on opposing edges,
        # square dark Argil package, printed copper/silith traces, beveled Redstone core die.
        for px in (2.0, 6.2, 10.4, 14.6):
            _ic_pin_y(d, px, -3.2, 3.6, 'copper_light', 'copper', 'copper_dark')
        d.box(0.0, 0.0, 0.0, 18.8, 18.8, 2.6, top='argil', left='body', right='shadow', width=.68)
        d.top_line([(1.2, 17.4), (1.2, 1.2), (17.4, 1.2)], 2.65, 'steel', .52)
        for px in (3.1, 7.3, 11.5, 15.7):
            d.top_line([(px, 1.0), (px, 17.8)], 2.66, 'copper_light', .62)
        d.top_line([(2.2, 9.4), (16.6, 9.4)], 2.66, 'silith', .56)
        # Central ceramic socket + beveled Redstone silicon die.
        d.box(4.0, 4.0, 2.6, 10.8, 10.8, 1.35, top='ceramic', left='argil', right='copper_dark', width=.6)
        d.box(5.6, 5.6, 3.95, 7.6, 7.6, 0.95, top='redstone', left='copper', right='copper_dark', width=.56)
        d.top_line([(6.8, 11.8), (6.8, 6.8), (11.8, 6.8)], 4.96, '#ffc19a', .58)
        for px in (2.0, 6.2, 10.4, 14.6):
            _ic_pin_y(d, px, 18.4, 3.6, 'copper_light', 'copper', 'copper_dark')
        return d

    if selected == 'edge_pcb':
        # Wide notched Printed Circuit Board (PCB) with L-keyed corner, zig-zag copper traces & redstone IC.
        d.box(0.0, 2.0, 0.0, 23.0, 15.6, 1.8, top='argil', left='copper_dark', right='shadow', width=.66)
        d.box(19.0, 0.0, 0.0, 4.0, 2.4, 1.8, top='copper_light', left='copper', right='copper_dark', width=.56)
        d.top_line([(1.6, 15.8), (1.6, 3.4), (17.8, 3.4)], 1.86, 'silith', .72)
        d.top_line([(3.0, 15.2), (3.0, 11.0), (6.4, 11.0), (6.4, 6.0), (19.6, 6.0)], 1.88, 'copper_light', .75)
        d.top_line([(5.0, 16.2), (16.0, 16.2), (16.0, 11.5), (21.2, 11.5)], 1.88, 'copper_light', .75)
        d.box(6.2, 5.2, 1.8, 9.6, 9.2, 1.5, top='ceramic', left='argil', right='copper_dark', width=.58)
        d.box(7.6, 6.4, 3.3, 6.8, 6.8, 0.85, top='redstone', left='copper', right='copper_dark', width=.55)
        d.top_line([(8.8, 11.8), (8.8, 7.6), (13.0, 7.6)], 4.22, '#ffc19a', .55)
        for sx in (18.2, 18.2):
            d.box(sx, 13.0, 1.8, 2.8, 2.0, 0.9, top='steel', left='body', right='dark', width=.45)
        return d

    # 'wafer_die': Octagonal ceramic/argil silicon circuit wafer with radial copper contact pads.
    h = 2.2
    oct_pts = [(11 + 11.5 * math.cos(math.pi / 8 + i * math.pi / 4),
                11 + 11.5 * math.sin(math.pi / 8 + i * math.pi / 4)) for i in range(8)]
    for first, second in zip(oct_pts[1:6], oct_pts[2:7]):
        d.face([(*first, 0), (*second, 0), (*second, h), (*first, h)], 'body' if first[0] < 11 else 'shadow', width=.64)
    d.top_polygon(oct_pts, h, 'argil', width=.7)
    for i in range(8):
        ang = i * math.pi / 4
        cx, cy = 11 + 8.6 * math.cos(ang), 11 + 8.6 * math.sin(ang)
        d.top_line([(11, 11), (cx, cy)], h + .06, 'copper_light', .72)
        d.octagon(cx, cy, h + .1, 1.5, 'copper_light', width=.44)
    d.box(6.0, 6.0, h, 10.0, 10.0, 1.5, top='ceramic', left='argil', right='copper_dark', width=.6)
    d.box(7.4, 7.4, h + 1.5, 7.2, 7.2, 0.85, top='redstone', left='copper', right='copper_dark', width=.55)
    d.top_line([(8.5, 13.0), (8.5, 8.6), (13.0, 8.6)], h + 2.42, '#ffc19a', .55)
    return d


def optical_circuit(look=None):
    selected = CIRCUIT_LOOK if look is None else look
    if selected not in CIRCUIT_LOOKS:
        raise ValueError(f'Unknown circuit look: {selected}')
    d = Drawing()

    if selected == 'ic_microchip':
        # Quad-Flat IC Package (QFP): 3 gleaming Lumivex/gold pins on ALL FOUR sides + diamond-glass prism die!
        for pos in (3.2, 8.3, 13.4):
            _ic_pin_y(d, pos, -3.2, 3.6, 'gold', 'lumivex', 'copper_dark')
            _ic_pin_x(d, -3.2, pos, 3.6, 'gold', 'lumivex', 'copper_dark')
        d.box(0.0, 0.0, 0.0, 18.8, 18.8, 2.7, top='dark', left='body', right='shadow', width=.68)
        d.top_line([(1.2, 17.4), (1.2, 1.2), (17.4, 1.2)], 2.75, 'edge', .52)
        for pos in (4.3, 9.4, 14.5):
            d.top_line([(pos, 1.0), (pos, 17.8)], 2.76, 'lumivex', .6)
            d.top_line([(1.0, pos), (17.8, pos)], 2.76, 'lumivex', .6)
        # Raised Lumivex carrier + dark octagonal bezel + faceted diamond-glass optical prism.
        d.box(3.0, 3.0, 2.7, 12.8, 12.8, 1.25, top='lumivex', left='copper', right='copper_dark', width=.62)
        d.octagon(9.4, 9.4, 4.05, 5.7, 'dark', width=.74)
        d.octagon(9.4, 9.4, 4.45, 4.4, 'diamond', width=.66)
        d.top_polygon([(5.4, 7.8), (7.8, 5.4), (11.0, 5.4), (9.4, 9.4)], 4.49, 'glass', stroke='glass', width=0)
        d.top_polygon([(9.4, 9.4), (13.4, 7.8), (13.4, 11.0), (11.0, 13.4)], 4.50, 'diamond_dark', stroke='diamond_dark', width=0)
        d.top_line([(7.6, 6.0), (6.0, 7.6)], 4.56, 'white', .8)
        for pos in (3.2, 8.3, 13.4):
            _ic_pin_y(d, pos, 18.4, 3.6, 'gold', 'lumivex', 'copper_dark')
            _ic_pin_x(d, 18.4, pos, 3.6, 'gold', 'lumivex', 'copper_dark')
        return d

    if selected == 'edge_pcb':
        # Wide notched Optical PCB with Lumivex bus routes and gleaming diamond-glass lens core.
        d.box(0.0, 2.0, 0.0, 23.0, 15.6, 1.8, top='dark', left='body', right='shadow', width=.66)
        d.box(19.0, 0.0, 0.0, 4.0, 2.4, 1.8, top='gold', left='lumivex', right='copper_dark', width=.56)
        d.top_line([(1.6, 15.8), (1.6, 3.4), (17.8, 3.4)], 1.86, 'lumivex', .72)
        d.top_line([(3.0, 15.2), (3.0, 11.0), (20.4, 11.0)], 1.88, 'diamond_light', .68)
        d.box(5.4, 4.2, 1.8, 11.4, 11.2, 1.25, top='lumivex', left='copper', right='copper_dark', width=.6)
        d.octagon(11.1, 9.8, 3.15, 5.2, 'dark', width=.72)
        d.octagon(11.1, 9.8, 3.55, 4.0, 'diamond', width=.62)
        d.top_polygon([(7.5, 8.3), (9.6, 6.2), (12.6, 6.2), (11.1, 9.8)], 3.59, 'glass', stroke='glass', width=0)
        d.top_polygon([(11.1, 9.8), (14.7, 8.3), (14.7, 11.3), (12.6, 13.4)], 3.60, 'diamond_dark', stroke='diamond_dark', width=0)
        d.top_line([(9.4, 6.7), (8.0, 8.1)], 3.65, 'white', .76)
        return d

    # 'wafer_die': Octagonal dark optical silicon wafer with radial Lumivex pads and diamond-glass prism.
    h = 2.2
    oct_pts = [(11 + 11.5 * math.cos(math.pi / 8 + i * math.pi / 4),
                11 + 11.5 * math.sin(math.pi / 8 + i * math.pi / 4)) for i in range(8)]
    for first, second in zip(oct_pts[1:6], oct_pts[2:7]):
        d.face([(*first, 0), (*second, 0), (*second, h), (*first, h)], 'body' if first[0] < 11 else 'shadow', width=.64)
    d.top_polygon(oct_pts, h, 'dark', width=.7)
    for i in range(8):
        ang = i * math.pi / 4
        cx, cy = 11 + 8.6 * math.cos(ang), 11 + 8.6 * math.sin(ang)
        d.top_line([(11, 11), (cx, cy)], h + .06, 'lumivex', .72)
        d.octagon(cx, cy, h + .1, 1.5, 'gold', width=.44)
    d.octagon(11, 11, h + .25, 6.8, 'lumivex', width=.68)
    d.octagon(11, 11, h + .85, 5.4, 'dark', width=.66)
    d.octagon(11, 11, h + 1.25, 4.3, 'diamond', width=.62)
    d.top_polygon([(7.1, 9.4), (9.4, 7.1), (12.6, 7.1), (11, 11)], h + 1.30, 'glass', stroke='glass', width=0)
    d.top_polygon([(11, 11), (14.9, 9.4), (14.9, 12.6), (12.6, 14.9)], h + 1.31, 'diamond_dark', stroke='diamond_dark', width=0)
    d.top_line([(9.2, 7.7), (7.8, 9.1)], h + 1.36, 'white', .78)
    return d


# ------------------------------------------------------------------------------
# Structural Hardware & Advanced Sintered Components
# ------------------------------------------------------------------------------
def machine_frame():
    d = Drawing()
    # Iron beams and pale matrix blend retain their recipe colors.
    d.box(0, 0, 0, 24, 4, 4)
    d.box(0, 4, 0, 4, 16, 4)
    d.box(20, 4, 0, 4, 16, 4)
    d.box(0, 20, 0, 24, 4, 4)
    for x, y in ((0, 0), (20, 0), (0, 20), (20, 20)):
        d.top_polygon([(x+.7, y+.7), (x+3.3, y+.7), (x+3.3, y+3.3), (x+.7, y+3.3)],
                      4.12, 'ceramic', width=.5)
    d.top_line([(5, 1.8), (18.5, 1.8)], 4.15, 'edge', .92)
    d.top_line([(1.8, 5), (1.8, 18.5)], 4.15, 'edge', .92)
    return d


STRUCTURAL_BARS_LOOKS = ('bar_bundle', 'stepped_bars', 'hex_bars', 'truss_lattice', 'twin_ibeam')
STRUCTURAL_BARS_LOOK = 'bar_bundle'


def structural_bars(look=None):
    selected = STRUCTURAL_BARS_LOOK if look is None else look
    if selected not in STRUCTURAL_BARS_LOOKS:
        raise ValueError(f'Unknown structural_bars look: {selected}')
    d = Drawing()

    if selected == 'bar_bundle':
        # Three parallel square-section forged steel bars (strictly non-round!) bound by two
        # slim graphite clamp collars with Silith locking inlays. Drawn back-to-front for crisp 3D depth.
        xs = (1.6, 8.8, 16.0)
        bar_w, bar_h = 4.6, 4.2
        # Back bar segments (y = 0..6.4)
        for x in xs:
            d.box(x, 0.0, 0.5, bar_w, 6.6, bar_h, top='steel', left='body', right='dark', width=.62)
            d.top_line([(x + 1.0, 5.8), (x + 1.0, 1.2), (x + 3.6, 1.2)], 4.74, 'white', .52)
        # Rear graphite clamp collar (y = 6.4..8.8)
        d.box(0.5, 6.4, 0.0, 21.2, 2.4, 5.3, top='top', left='shadow', right='dark', width=.64)
        d.top_line([(1.5, 7.2), (20.6, 7.2)], 5.35, 'edge', .46)
        d.top_line([(2.0, 8.0), (20.1, 8.0)], 5.35, 'silith', .58)
        # Middle bar segments (y = 8.8..19.0)
        for x in xs:
            d.box(x, 8.8, 0.5, bar_w, 10.4, bar_h, top='steel', left='body', right='dark', width=.62)
            d.top_line([(x + 1.0, 18.2), (x + 1.0, 9.6)], 4.74, 'white', .52)
        # Front graphite clamp collar (y = 19.0..21.4)
        d.box(0.5, 19.0, 0.0, 21.2, 2.4, 5.3, top='top', left='shadow', right='dark', width=.64)
        d.top_line([(1.5, 19.8), (20.6, 19.8)], 5.35, 'edge', .46)
        d.top_line([(2.0, 20.6), (20.1, 20.6)], 5.35, 'silith', .58)
        # Front protruding square bar ends (y = 21.4..28.0) with sharp square end faces!
        for x in xs:
            d.box(x, 21.4, 0.5, bar_w, 6.6, bar_h, top='steel', left='edge', right='dark', width=.62)
            d.top_line([(x + 1.0, 27.0), (x + 1.0, 22.2)], 4.74, 'white', .52)
            # Recessed dark square core mark on each front end face emphasizes square bar stock.
            d.face([(x + 1.2, 28.02, 1.5), (x + 3.4, 28.02, 1.5), (x + 3.4, 28.02, 3.7), (x + 1.2, 28.02, 3.7)],
                   'body', width=.38)
        return d

    if selected == 'stepped_bars':
        # Three staggered flat rectangular steel bar profiles with Silith channel grooves.
        for x, y0, y1 in ((1.0, 0.0, 23.0), (8.4, 2.6, 26.2), (15.8, 5.2, 29.0)):
            d.box(x, y0, 0.0, 5.2, y1 - y0, 3.6, top='steel', left='edge', right='dark', width=.64)
            d.top_line([(x + 1.0, y1 - 1.2), (x + 1.0, y0 + 1.2), (x + 4.2, y0 + 1.2)], 3.65, 'white', .54)
            d.top_line([(x + 3.2, y0 + 2.4), (x + 3.2, y1 - 2.0)], 3.66, 'silith', .64)
            d.face([(x + 1.2, y1 + 0.02, 0.9), (x + 4.0, y1 + 0.02, 0.9),
                    (x + 4.0, y1 + 0.02, 2.7), (x + 1.2, y1 + 0.02, 2.7)], 'body', width=.38)
        return d

    if selected == 'hex_bars':
        # Three angular chamfered-crown (octagonal/hex-section, non-round) forged steel bars
        # held together by one central heavy graphite clamp yoke with Silith lock rivets.
        xs = (1.6, 8.8, 16.0)
        for y0, y1 in ((0.0, 11.4), (15.6, 28.0)):
            for x in xs:
                d.box(x, y0, 0.4, 4.8, y1 - y0, 3.2, top='body', left='body', right='dark', width=.58)
                d.box(x + 0.9, y0, 3.6, 3.0, y1 - y0, 1.2, top='steel', left='edge', right='top', width=.56)
                d.top_line([(x + 1.5, y1 - 1.0), (x + 1.5, y0 + 1.0)], 4.85, 'white', .48)
            if y0 == 0.0:
                d.box(0.4, 11.2, 0.0, 21.4, 4.6, 5.6, top='top', left='shadow', right='dark', width=.66)
                d.top_line([(1.4, 12.2), (20.8, 12.2)], 5.65, 'edge', .52)
                for rx in (3.0, 10.2, 17.4):
                    d.top_polygon([(rx, 12.7), (rx + 2.0, 12.7), (rx + 2.0, 14.7), (rx, 14.7)],
                                  5.66, 'silith', width=.44)
        return d

    if selected == 'truss_lattice':
        h = 3.2
        outer = [(1.5, 0), (22.5, 0), (24, 1.5), (24, 22.5), (22.5, 24), (1.5, 24), (0, 22.5), (0, 1.5)]
        for first, second in zip(outer[2:6], outer[3:7]):
            d.face([(*first, 0), (*second, 0), (*second, h), (*first, h)],
                   'dark' if first[0] > 12 or first[1] > 12 else 'body', width=.68)
        holes = []
        for x in (3.6, 13.8):
            for y in (3.6, 13.8):
                w = 6.6
                far, right, left = (x, y), (x + w, y), (x, y + w)
                d.face([(*far, 0), (*right, 0), (*right, h), (*far, h)], 'body', width=.48)
                d.face([(*far, 0), (*left, 0), (*left, h), (*far, h)], 'shadow', width=.48)
                holes.append([iso(*p, h) for p in (far, right, (x + w, y + w), left)])
        d.cutout([iso(*p, h) for p in outer], holes, 'steel', width=.72)
        d.top_line([(2.0, 21.5), (2.0, 2.0), (21.5, 2.0)], h + .04, 'white', .68)
        d.top_line([(12.0, 2.4), (12.0, 21.6)], h + .04, 'edge', .75)
        d.top_line([(2.4, 12.0), (21.6, 12.0)], h + .04, 'edge', .75)
        for cx, cy in ((.6, .6), (19.6, .6), (.6, 19.6), (19.6, 19.6)):
            d.box(cx, cy, h, 3.8, 3.8, .7, top='top', left='body', right='dark', width=.5)
            d.top_polygon([(cx + 1.1, cy + 1.1), (cx + 2.7, cy + 1.1), (cx + 2.7, cy + 2.7), (cx + 1.1, cy + 2.7)],
                          h + .74, 'silith', width=.42)
        d.box(9.4, 9.4, h, 5.2, 5.2, .85, top='edge', left='body', right='dark', width=.56)
        d.top_polygon([(10.6, 10.6), (13.4, 10.6), (13.4, 13.4), (10.6, 13.4)], h + .88, 'silith', width=.46)
        return d

    # Legacy twin_ibeam: paired iron I-beams with a narrow Silith line inside the web.
    profile = [(0, 0), (7, 0), (7, 1.65), (4.6, 1.65), (4.6, 5.4), (7, 5.4),
               (7, 7), (0, 7), (0, 5.4), (2.4, 5.4), (2.4, 1.65), (0, 1.65)]
    for x, start, end in ((0, 1, 24), (8.7, 5, 28)):
        d.box(x, start, 0, 7, end - start, 1.65, top='top', left='body', right='dark', width=.64)
        d.box(x + 2.4, start, 1.65, 2.2, end - start, 3.75, top='steel', left='body', right='top', width=.52)
        d.path3([(x + 4.62, start + 1, 3.8), (x + 4.62, end - 1, 3.8)], 'silith', .54)
        d.box(x, start, 5.4, 7, end - start, 1.6, top='steel', left='body', right='top', width=.64)
        d.face([(x + dx, end, z) for dx, z in profile], 'edge', width=.65)
        d.top_line([(x + 1, end - 1), (x + 1, start + 1), (x + 6.1, start + 1)], 7.02, 'white', .56)
        d.top_line([(x + 5.5, start + 5), (x + 5.5, end - 3.5)], 7.02, 'silith', .59)
    return d


def automation_pattern():
    d = Drawing()
    d.box(0, 0, 0, 20, 24, 1.6, top='top', left='body', right='dark')
    d.top_polygon([(1.1, 1.1), (18.9, 1.1), (18.9, 22.9), (1.1, 22.9)], 1.65,
                  'paper', stroke='paper_dark', width=.5)
    # A single 2x2 recipe matrix on a reusable card, with its result in copper.
    d.top_polygon([(2.5, 2.5), (17.5, 2.5), (17.5, 17.5), (2.5, 17.5)], 1.7, 'dark', width=.75)
    for x, y, color in ((4, 4, 'steel'), (11, 4, 'steel'), (4, 11, 'steel'), (11, 11, 'copper_light')):
        d.top_polygon([(x, y), (x+5, y), (x+5, y+5), (x, y+5)], 1.85, color, stroke=color, width=0)
    d.top_line([(4, 20.5), (16, 20.5)], 1.8, 'copper_dark', 1.4)
    d.top_line([(4, 22.5), (12, 22.5)], 1.8, 'copper_dark', .7)
    return d


def diamond_lattice():
    d = Drawing()
    # One fused diamond grid with four open cells and a pale matrix foundation.
    thickness = 2.3
    bevel = [(2, 0), (22, 0), (24, 2), (24, 22), (22, 24), (2, 24), (0, 22), (0, 2)]
    for first, second in zip(bevel[2:6], bevel[3:7]):
        d.face([(*first, 0), (*second, 0), (*second, thickness), (*first, thickness)], 'ceramic', width=.7)
    holes = []
    for x in (3.15, 13.45):
        for y in (3.15, 13.45):
            far = (x, y)
            right = (x + 7.4, y)
            left = (x, y + 7.4)
            d.face([(*far, 0), (*right, 0), (*right, thickness), (*far, thickness)], 'diamond_dark', width=.45)
            d.face([(*far, 0), (*left, 0), (*left, thickness), (*far, thickness)], 'diamond_dark', width=.45)
            holes.append([iso(*point, thickness) for point in (far, right, (x + 7.4, y + 7.4), left)])
    d.cutout([iso(*point, thickness) for point in bevel], holes, 'diamond', width=.69)
    d.top_line([(1.7, 10.6), (1.7, 3.2), (3.2, 1.7), (10.6, 1.7)], thickness, 'diamond_light', .75)
    d.top_line([(12, 4.1), (12, 12), (20, 12)], thickness, 'diamond_light', .64)
    for joint in ([(17.2, 0), (20.4, 0), (20.4, 2.8), (17.2, 2.8)],
                  [(0, 16.6), (2.8, 16.6), (2.8, 19.8), (0, 19.8)],
                  [(21.2, 16.6), (24, 16.6), (24, 19.8), (21.2, 19.8)]):
        d.top_polygon(joint, thickness, 'gold', width=.51)
    return d


ENDER_CONDUCTOR_LOOKS = ('caged_resonator', 'tuning_fork', 'bus_relay')
ENDER_CONDUCTOR_LOOK = 'caged_resonator'


def ender_conductor(look=None):
    selected = ENDER_CONDUCTOR_LOOK if look is None else look
    if selected not in ENDER_CONDUCTOR_LOOKS:
        raise ValueError(f'Unknown ender_conductor look: {selected}')
    d = Drawing()

    if selected == 'caged_resonator':
        # Precision caged resonant bus-coil: Copper terminal blocks at both ends, twin machined
        # Lumivex side bus rails, copper bridge rings, and a faceted Ender core prism centered inside.
        # Back Copper terminal block + contact lug.
        d.box(5.5, 0.0, 1.0, 7.8, 3.2, 3.8, top='copper_light', left='copper', right='copper_dark', width=.6)
        d.box(3.0, 2.8, 0.4, 12.8, 3.4, 5.2, top='lumivex', left='copper', right='copper_dark', width=.64)
        # Left-back Lumivex conductor rail.
        d.box(2.2, 5.6, 0.8, 2.8, 16.8, 4.4, top='lumivex', left='copper', right='copper_dark', width=.6)
        d.top_line([(3.2, 21.4), (3.2, 6.6)], 5.25, 'gold', .52)
        # Lower copper coil bed beneath the suspended Ender core.
        d.box(5.0, 7.6, 0.4, 8.8, 12.8, 1.4, top='copper_dark', left='shadow', right='copper_dark', width=.52)
        # Suspended faceted Ender resonance crystal/pearl core.
        d.box(5.8, 9.2, 1.8, 7.2, 9.6, 5.6, top='ender', left='ender', right='#316e68', width=.68)
        d.top_polygon([(6.6, 10.0), (12.2, 10.0), (12.2, 13.6), (6.6, 13.6)], 7.45, '#a3deba', stroke='#a3deba', width=0)
        d.top_line([(7.2, 17.2), (7.2, 10.8), (11.6, 10.8)], 7.48, '#d1ead0', .68)
        # Right-front Lumivex conductor rail + copper induction arches.
        d.box(13.8, 5.6, 0.8, 2.8, 16.8, 4.4, top='lumivex', left='copper', right='copper_dark', width=.6)
        d.top_line([(14.8, 21.4), (14.8, 6.6)], 5.25, 'gold', .52)
        for cy in (7.4, 19.2):
            d.box(3.4, cy, 4.8, 12.0, 1.6, 1.6, top='copper_light', left='copper', right='copper_dark', width=.52)
        # Front Lumivex yoke + Copper terminal lug.
        d.box(3.0, 22.0, 0.4, 12.8, 3.4, 5.2, top='lumivex', left='copper', right='copper_dark', width=.64)
        d.top_line([(4.2, 23.2), (14.4, 23.2)], 5.65, 'gold', .54)
        d.box(5.5, 25.2, 1.0, 7.8, 3.2, 3.8, top='copper_light', left='copper', right='copper_dark', width=.6)
        d.top_line([(6.6, 26.4), (12.0, 26.4)], 4.86, 'white', .46)
        return d

    if selected == 'tuning_fork':
        # Twin-prong Lumivex/Copper resonance fork arcing around a suspended faceted Ender core.
        d.box(5.2, 0.0, 0.8, 8.4, 6.2, 4.2, top='copper', left='copper_dark', right='shadow', width=.66)
        d.box(3.2, 5.4, 0.4, 12.4, 4.0, 5.0, top='lumivex', left='copper', right='copper_dark', width=.64)
        d.top_line([(4.4, 6.8), (14.2, 6.8)], 5.45, 'gold', .55)
        # Left prong (back)
        d.box(2.0, 9.0, 0.8, 3.2, 18.0, 4.4, top='lumivex', left='copper', right='copper_dark', width=.62)
        d.top_line([(3.0, 25.8), (3.0, 10.2)], 5.25, 'gold', .52)
        # Suspended Ender resonance prism between the prongs
        d.box(6.4, 13.0, 2.0, 6.2, 8.6, 5.4, top='ender', left='ender', right='#316e68', width=.68)
        d.top_line([(7.4, 20.0), (7.4, 14.2), (11.4, 14.2)], 7.46, '#d1ead0', .68)
        # Right prong (front)
        d.box(13.6, 9.0, 0.8, 3.2, 18.0, 4.4, top='lumivex', left='copper', right='copper_dark', width=.62)
        d.top_line([(14.6, 25.8), (14.6, 10.2)], 5.25, 'gold', .52)
        return d

    # 'bus_relay': Flat machined octagonal Lumivex-Copper induction ring with opposing terminal lugs.
    h = 3.0
    d.box(8.0, -2.6, 0.4, 6.0, 3.4, 2.2, top='copper_light', left='copper', right='copper_dark', width=.58)
    oct_pts = [(11 + 10.6 * math.cos(math.pi / 8 + i * math.pi / 4),
                11 + 10.6 * math.sin(math.pi / 8 + i * math.pi / 4)) for i in range(8)]
    for first, second in zip(oct_pts[1:6], oct_pts[2:7]):
        d.face([(*first, 0), (*second, 0), (*second, h), (*first, h)], 'copper' if first[0] < 11 else 'copper_dark', width=.66)
    d.top_polygon(oct_pts, h, 'lumivex', width=.72)
    d.top_line([(5.0, 15.0), (5.0, 7.0), (9.0, 3.2), (14.5, 3.2)], h + .05, 'gold', .6)
    d.octagon(11, 11, h + .1, 6.6, 'copper_dark', width=.68)
    d.octagon(11, 11, h + .7, 5.4, 'ender', width=.68)
    d.top_line([(8.2, 9.2), (10.8, 7.2), (13.4, 8.2)], h + .78, '#d1ead0', .72)
    d.box(8.0, 21.2, 0.4, 6.0, 3.4, 2.2, top='copper_light', left='copper', right='copper_dark', width=.58)
    return d


NETHERITE_SHEATH_LOOKS = ('clamped_jacket', 'plate_shroud', 'hex_collar')
NETHERITE_SHEATH_LOOK = 'clamped_jacket'


def netherite_sheath(look=None):
    selected = NETHERITE_SHEATH_LOOK if look is None else look
    if selected not in NETHERITE_SHEATH_LOOKS:
        raise ValueError(f'Unknown netherite_sheath look: {selected}')
    d = Drawing()

    if selected == 'clamped_jacket':
        # Sleek precision-armored thermal sheath: a warm Virelite ceramic thermal liner protruding
        # cleanly from both ends of a low-profile chamfered Netherite armor casing with flush
        # forged Rhevon clamping bands and recessed dorsal heat-shield ribs.
        # Back & front Virelite ceramic inner thermal sleeve peeking out both ends.
        d.box(4.6, 0.0, 0.8, 10.8, 3.4, 5.2, top='virelite', left='virelite', right='rhevon', width=.6)
        d.top_line([(5.6, 1.4), (14.2, 1.4)], 6.06, '#f0dbce', .52)
        # Main low-profile chamfered Netherite outer armor shell (split into 3 segments between flush bands).
        for y0, y1 in ((2.6, 9.2), (11.6, 17.6), (20.0, 25.4)):
            d.box(2.0, y0, 0.2, 16.0, y1 - y0, 6.2, top='netherite', left='rhevon', right='shadow', width=.64)
            # Raised chamfered armor ridge on top face for sleek faceted armored profile.
            d.box(3.8, y0 + 0.5, 6.4, 12.4, y1 - y0 - 1.0, 1.15, top='netherite', left='rhevon', right='dark', width=.54)
            d.top_line([(4.8, y1 - 1.0), (4.8, y0 + 1.1), (15.0, y0 + 1.1)], 7.60, '#c0a3a3', .52)
            # Subtle thermal vent slit on the upper armor ridge.
            d.top_line([(6.6, (y0 + y1) / 2), (13.4, (y0 + y1) / 2)], 7.62, 'shadow', .62)
            # Two flush interlocking Rhevon clamping bands wrapping cleanly around the shell.
            if y0 == 2.6:
                d.box(1.4, 9.0, 0.0, 17.2, 2.8, 7.9, top='rhevon', left='netherite', right='shadow', width=.64)
                d.top_line([(2.4, 10.0), (17.4, 10.0)], 7.95, '#ead6cf', .56)
                d.top_line([(3.0, 11.0), (16.8, 11.0)], 7.95, 'virelite', .52)
            elif y0 == 11.6:
                d.box(1.4, 17.4, 0.0, 17.2, 2.8, 7.9, top='rhevon', left='netherite', right='shadow', width=.64)
                d.top_line([(2.4, 18.4), (17.4, 18.4)], 7.95, '#ead6cf', .56)
                d.top_line([(3.0, 19.4), (16.8, 19.4)], 7.95, 'virelite', .52)
        # Front protruding Virelite ceramic thermal collar with recessed bore opening!
        d.box(4.6, 25.0, 0.8, 10.8, 3.2, 5.2, top='virelite', left='virelite', right='rhevon', width=.64)
        d.top_line([(5.6, 26.2), (14.2, 26.2)], 6.06, '#f0dbce', .54)
        d.face([(6.4, 28.22, 1.9), (13.6, 28.22, 1.9), (13.6, 28.22, 4.9), (6.4, 28.22, 4.9)], 'shadow', width=.54)
        d.face([(6.8, 28.22, 2.2), (13.2, 28.22, 2.2), (13.2, 28.22, 3.2), (6.8, 28.22, 3.2)], 'netherite', width=.32)
        return d

    if selected == 'plate_shroud':
        # Layered shingled Netherite thermal armor plates over a warm Virelite ceramic spine.
        d.box(2.2, 0.4, 0.0, 15.6, 26.8, 2.2, top='virelite', left='virelite', right='rhevon', width=.64)
        d.top_line([(3.4, 25.6), (3.4, 1.6), (16.6, 1.6)], 2.25, '#f0dbce', .52)
        for idx, py in enumerate((2.0, 9.6, 17.2)):
            z = 2.2 + idx * 0.45
            d.box(0.8, py, z, 18.4, 7.6, 2.6, top='netherite', left='rhevon', right='shadow', width=.64)
            d.top_line([(2.0, py + 6.2), (2.0, py + 1.2), (17.8, py + 1.2)], z + 2.65, '#c0a3a3', .52)
            for bx in (2.2, 16.0):
                d.top_polygon([(bx, py + 2.8), (bx + 1.8, py + 2.8), (bx + 1.8, py + 4.6), (bx, py + 4.6)],
                              z + 2.68, 'virelite', width=.4)
        return d

    # 'hex_collar': Slimmer, sharper precision-machined hexagonal armor sleeve with Virelite end-flange.
    rim = [(4.0, 16.0), (8.2, 13.8), (12.6, 15.0), (16.0, 19.0),
           (16.4, 23.6), (13.0, 26.6), (8.2, 26.0), (4.4, 22.6)]
    opening = [(6.5, 17.5), (8.6, 16.2), (11.5, 17.1), (13.8, 19.8),
               (14.1, 22.5), (12.1, 24.3), (9.0, 23.9), (6.8, 21.5)]
    back = [(x + 13.8, y - 9.6) for x, y in rim]
    for side, material in enumerate(('rhevon', 'netherite', 'rhevon', 'netherite', 'shadow')):
        d.polygon([rim[side], back[side], back[side + 1], rim[side + 1]], material, width=.62)
    d.polygon([(10.6, 13.9), (19.6, 7.6), (24.4, 8.8), (15.4, 15.2)], 'rhevon', width=.5)
    d.polygon([(17.4, 19.4), (26.4, 13.1), (27.0, 16.8), (18.0, 23.0)], 'netherite', width=.5)
    d.line([(12.0, 13.8), (19.6, 8.5), (22.6, 9.2)], '#c0a3a3', .6)
    d.line([(19.2, 19.8), (25.6, 15.3)], '#985761', .56)
    for side, material in ((5, 'rhevon'), (6, 'netherite'), (7, 'shadow')):
        first, second = opening[side], opening[(side + 1) % len(opening)]
        d.polygon([first, second, (second[0] + 2.8, second[1] - 1.9), (first[0] + 2.8, first[1] - 1.9)],
                  material, width=.38)
    d.cutout(rim, [opening], 'virelite', width=.74)
    d.line([(5.4, 16.2), (8.4, 14.8), (12.2, 15.8)], '#f0dbce', .54)
    return d


def main():
    # The Matrix Cluster, Parallel Upgrade, Pressure Module, Wireless Booster, Energy Core,
    # and Portable Energy Cell are owned by generate_illustrated_icons.py (with shared checksums).
    for draw in (machine_frame, structural_bars, control_circuit, optical_circuit,
                 speed_upgrade, efficiency_upgrade, automation_pattern,
                 diamond_lattice, ender_conductor, netherite_sheath):
        draw().write(draw.__name__)


if __name__ == '__main__':
    main()
