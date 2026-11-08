#!/usr/bin/env python3
"""Draw workshop components from reproducible vector geometry.

The shared projection keeps every edge aligned. Broad silhouettes and a single
central feature distinguish the components at Minecraft inventory scale.
"""
from component_drawing import Drawing, iso


def contacts(d, y=22, z=1.5):
    """Three broad copper fingers, shared by electronic parts and upgrades."""
    for x in (2, 8, 14):
        d.box(x, y, z, 4, 4, .8, top='copper_light', left='copper', right='copper_dark')


def board(d):
    d.box(0, 0, 0, 20, 24, 2, top='argil', left='copper_dark', right='shadow')
    # Matte trim and copper contacts use the same finishes as machine ports.
    d.top_line([(1.5, 12), (1.5, 1.5), (18.5, 1.5), (18.5, 10)], 2.06, 'silith', 1.25)
    # Two wide printed routes are visible without tiny component noise.
    for x in (4, 16):
        d.top_line([(x, 8), (x, 20), (x, 23)], 2.08, 'copper_light', 1.25)
    d.top_line([(10, 16), (10, 23)], 2.08, 'copper_light', 1.25)
    contacts(d)


def upgrade_case(d):
    d.box(0, 0, 0, 20, 24, 3, top='top', left='body', right='shadow')
    d.box(1.5, 1.5, 3, 17, 20, .6, top='dark', left='body', right='dark')
    d.top_line([(1.15, 15), (1.15, 1), (19, 1), (19, 12)], 3.05, 'edge', .62)
    d.top_line([(3, 22), (8, 22), (10, 20), (17, 20)], 3.68, 'diamond', 1.05)
    contacts(d, z=1.8)


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


def structural_bars():
    d = Drawing()
    # Paired iron I-beams with a narrow Silith line inside the web.
    profile = [(0, 0), (7, 0), (7, 1.65), (4.6, 1.65), (4.6, 5.4), (7, 5.4),
               (7, 7), (0, 7), (0, 5.4), (2.4, 5.4), (2.4, 1.65), (0, 1.65)]
    for x, start, end in ((0, 1, 24), (8.7, 5, 28)):
        d.box(x, start, 0, 7, end - start, 1.65, top='top', left='body', right='dark', width=.64)
        d.box(x + 2.4, start, 1.65, 2.2, end - start, 3.75, top='steel', left='body', right='top',
              width=.52)
        d.path3([(x + 4.62, start + 1, 3.8), (x + 4.62, end - 1, 3.8)], 'silith', .54)
        d.box(x, start, 5.4, 7, end - start, 1.6, top='steel', left='body', right='top', width=.64)
        d.face([(x + dx, end, z) for dx, z in profile], 'edge', width=.65)
        d.top_line([(x + 1, end - 1), (x + 1, start + 1), (x + 6.1, start + 1)], 7.02, 'white', .56)
        d.top_line([(x + 5.5, start + 5), (x + 5.5, end - 3.5)], 7.02, 'silith', .59)
    return d


def control_circuit():
    d = Drawing()
    board(d)
    # A redstone core rests in a matrix insulator on copper contacts.
    d.box(4.5, 4.5, 2, 11, 12, 2, top='ceramic', left='argil', right='copper_dark')
    d.box(6, 6, 4, 8, 9, .75, top='redstone', left='copper', right='copper_dark')
    d.top_line([(7.5, 11), (7.5, 7.5), (12.5, 7.5)], 4.82, '#ffc19a', .62)
    return d


def optical_circuit():
    d = Drawing()
    board(d)
    d.box(3, 3, 2, 14, 15, 1.3, top='lumivex', left='copper', right='copper_dark')
    # Diamond glass sits in a bright Lumivex carrier.
    d.octagon(10, 10.5, 3.5, 6.8, 'dark', width=.8)
    d.octagon(10, 10.5, 4.1, 5.25, 'diamond', width=.7)
    d.top_polygon([(5.15, 8.5), (8, 5.65), (12, 5.65), (10, 10.5)],
                  4.14, 'glass', stroke='glass', width=0)
    d.top_polygon([(10, 10.5), (14.85, 8.5), (14.85, 12.5), (12, 15.35)],
                  4.15, 'diamond_dark', stroke='diamond_dark', width=0)
    d.top_line([(8, 6.2), (6.1, 8.1)], 4.2, 'white', .9)
    return d


def speed_upgrade():
    d = Drawing()
    upgrade_case(d)
    # One broad forward arrow; no separate fins or fragmented chevrons.
    arrow = [(7, 19), (13, 19), (13, 12), (17.2, 12), (10, 3.8), (2.8, 12), (7, 12)]
    d.top_polygon(arrow, 3.8, 'rhedon', width=.8)
    d.top_polygon([(10, 3.8), (17.2, 12), (13, 12), (13, 19), (10, 19)],
                  3.84, 'redstone', stroke='redstone', width=0)
    d.top_line([(6, 11), (10, 6), (13.5, 10)], 3.86, 'lumivex', .77)
    return d


def efficiency_upgrade():
    d = Drawing()
    upgrade_case(d)
    # The broad shield retains energy; the central bolt remains legible when small.
    d.top_polygon([(3.8, 4.5), (16.2, 4.5), (16.2, 12.5), (10, 20), (3.8, 12.5)],
                  3.8, 'ceramic', width=.8)
    d.top_polygon([(10, 4.5), (16.2, 4.5), (16.2, 12.5), (10, 20)],
                  3.84, 'diamond_dark', stroke='diamond_dark', width=0)
    d.top_polygon([(10.6, 6.3), (6.3, 12.4), (9.5, 12.4), (8.5, 17.8),
                   (14, 10.8), (10.6, 10.8)], 3.9, 'lumivex', width=.6)
    return d


def automation_pattern():
    d = Drawing()
    d.box(0, 0, 0, 20, 24, 1.6, top='top', left='body', right='dark')
    d.top_polygon([(1.1, 1.1), (18.9, 1.1), (18.9, 22.9), (1.1, 22.9)], 1.65,
                  'paper', stroke='paper_dark', width=.5)
    # A single 2x2 recipe matrix on a reusable card, with its result in copper.
    d.top_polygon([(2.5, 2.5), (17.5, 2.5), (17.5, 17.5), (2.5, 17.5)],
                  1.7, 'dark', width=.75)
    for x, y, color in ((4, 4, 'steel'), (11, 4, 'steel'), (4, 11, 'steel'), (11, 11, 'copper_light')):
        d.top_polygon([(x, y), (x+5, y), (x+5, y+5), (x, y+5)],
                      1.85, color, stroke=color, width=0)
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
            d.face([(*far, 0), (*right, 0), (*right, thickness), (*far, thickness)],
                   'diamond_dark', width=.45)
            d.face([(*far, 0), (*left, 0), (*left, thickness), (*far, thickness)],
                   'diamond_dark', width=.45)
            holes.append([iso(*point, thickness) for point in (far, right, (x + 7.4, y + 7.4), left)])
    d.cutout([iso(*point, thickness) for point in bevel], holes, 'diamond', width=.69)
    d.top_line([(1.7, 10.6), (1.7, 3.2), (3.2, 1.7), (10.6, 1.7)], thickness, 'diamond_light', .75)
    d.top_line([(12, 4.1), (12, 12), (20, 12)], thickness, 'diamond_light', .64)
    for joint in ([(17.2, 0), (20.4, 0), (20.4, 2.8), (17.2, 2.8)],
                  [(0, 16.6), (2.8, 16.6), (2.8, 19.8), (0, 19.8)],
                  [(21.2, 16.6), (24, 16.6), (24, 19.8), (21.2, 19.8)]):
        d.top_polygon(joint, thickness, 'gold', width=.51)
    return d


def ender_conductor():
    d = Drawing()
    # A pearl core sits in a pair of opposing cast Lumivex and copper jaws.
    d.polygon([(2.8, 12.2), (10.2, 6.2), (15.3, 6.8), (16.1, 10.9), (11.2, 15.0),
               (12.6, 21.4), (8.2, 26.1), (3.5, 24.5), (2.2, 20.4)], 'copper_dark', width=.95)
    d.polygon([(2.8, 12.2), (10.2, 6.2), (15.3, 6.8), (15.0, 10.0), (9.7, 14.6),
               (10.7, 20.7), (7.8, 24.0), (3.4, 22.4)], 'lumivex', width=.6)
    d.polygon([(16.5, 20.3), (21.1, 15.8), (19.8, 12.3), (24.0, 6.1), (28.5, 7.3),
               (30.1, 11.2), (29.4, 17.9), (22.5, 25.4), (17.5, 25.0)], 'copper_dark', width=.95)
    d.polygon([(16.5, 20.3), (21.1, 15.8), (19.8, 12.3), (24.0, 6.1), (28.5, 7.3),
               (29.1, 11.1), (27.8, 15.8), (22.0, 22.5), (17.0, 22.2)], 'copper', width=.6)
    d.polygon([(10.9, 15.2), (14.0, 11.7), (18.8, 10.7), (22.0, 13.5), (22.8, 17.4),
               (19.5, 21.2), (15.1, 21.6), (11.9, 19.5)], 'ender', width=.85)
    d.line([(13.0, 15.0), (15.3, 12.9), (17.4, 12.5)], '#d1ead0', .86)
    d.line([(4.1, 12.5), (10.2, 7.7), (13.0, 7.9)], 'gold', .75)
    d.line([(23.3, 10.5), (24.9, 7.8), (27.3, 8.5)], 'copper_light', .73)
    return d


def netherite_sheath():
    d = Drawing()
    # A wide, hollow armored sleeve with a Virelite lip over the dark shell.
    rim = [(3, 15), (7.8, 12.3), (13.4, 14), (17.9, 18.8),
           (18.7, 24.4), (14.7, 28.5), (8.4, 27.8), (3.5, 23.6)]
    opening = [(5.8, 16.9), (8.1, 15.3), (12.2, 16.6), (15.4, 20.2),
               (15.9, 23.5), (13.6, 25.7), (9.4, 25), (6.1, 22.1)]
    back = [(x + 11.7, y - 8.1) for x, y in rim]
    for side, material in enumerate(('rhevon', 'netherite', 'rhevon', 'netherite', 'shadow')):
        d.polygon([rim[side], back[side], back[side + 1], rim[side + 1]], material, width=.61)
    # Raised plates follow the long shoulders instead of wrapping a band around them.
    d.polygon([(10.4, 12.7), (18.2, 7.2), (23.9, 8.6), (16.1, 14.4)], 'rhevon', width=.46)
    d.polygon([(19.4, 18.8), (27.1, 13.5), (27.8, 17.5), (20.2, 22.8)], 'netherite', width=.45)
    d.line([(11.6, 12.6), (18.3, 7.9), (21.3, 8.7)], '#c0a3a3', .63)
    d.line([(21.3, 19.4), (26.6, 15.7)], '#985761', .61)
    for side, material in ((5, 'rhevon'), (6, 'netherite'), (7, 'shadow')):
        first, second = opening[side], opening[(side + 1) % len(opening)]
        d.polygon([first, second, (second[0] + 2.5, second[1] - 1.7),
                   (first[0] + 2.5, first[1] - 1.7)], material, width=.36)
    d.cutout(rim, [opening], 'virelite', width=.72)
    d.line([(4.7, 15.4), (7.9, 13.6), (12.9, 15.1)], '#ead6cf', .56)
    return d


def matrix_cluster():
    d = Drawing()
    # Four pale prisms locked into an iron ferrule. Rhedon and Virel tint the catches.
    rim = [(2, 21), (8, 16.8), (22, 16.8), (29, 21.1), (26, 25), (7, 25)]
    d.polygon([(2, 21), (7, 25), (26, 25), (29, 21.1), (28.3, 26.2),
               (25, 28.8), (7.6, 28.8), (3.1, 25.8)], 'body', width=.83)
    d.polygon(rim, 'steel', width=.78)
    prisms = (
        ((22.1, 3.5), (17.8, 6.5), (17.7, 17.9), (22.3, 20.5), (26, 17.2), (26.1, 6.7), (22.3, 8.4)),
        ((7.2, 9.4), (3.3, 13.2), (4.6, 21.8), (9, 24), (12.3, 20.2), (11.9, 11.6), (8.5, 14.1)),
        ((24, 8.9), (20, 11.9), (18.8, 21.7), (23.7, 24.4), (27.9, 21.1), (28.4, 12.2), (24.5, 14.1)),
        ((15.4, 2.7), (10.8, 6.9), (11.4, 22.3), (16.3, 25.8), (21.1, 22.4), (20.8, 6.8), (16.2, 8.9)),
    )
    for tip, upper_left, foot_left, foot, foot_right, upper_right, ridge in prisms:
        d.polygon([tip, upper_left, foot_left, foot, foot_right, upper_right], 'glass', width=.72)
        d.polygon([upper_left, ridge, foot, foot_left], 'glass', width=.42)
        d.polygon([ridge, upper_right, foot_right, foot], 'edge', width=.42)
        d.polygon([tip, upper_right, ridge, upper_left], '#dfe9e8', width=.38)
        d.line([(upper_left[0] + .8, upper_left[1] + 1.4), (foot_left[0] + .8, foot_left[1] - 1.7)],
               '#f0f5f1', .5)
    d.polygon([(4.2, 22.1), (7.7, 23.8), (11.1, 23.8), (11.1, 26.6), (7.8, 26.6), (4.5, 24.7)],
              'rhedon', width=.58)
    d.polygon([(21.8, 23.8), (25, 23.8), (27.4, 21.7), (27.1, 24.9), (24.9, 26.6), (21.8, 26.6)],
              'virelite', width=.58)
    d.line([(8.6, 27.6), (23.9, 27.6)], 'edge', .55)
    return d


def main():
    for draw in (machine_frame, structural_bars, control_circuit, optical_circuit,
                 speed_upgrade, efficiency_upgrade, automation_pattern,
                 diamond_lattice, ender_conductor, netherite_sheath, matrix_cluster):
        draw().write(draw.__name__)


if __name__ == '__main__':
    main()
