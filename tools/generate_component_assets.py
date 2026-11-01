#!/usr/bin/env python3
"""Original 64px Clean Lab component art and a UV-compatible inserted-cell atlas.

Production/check generation uses only the standard library. Optional previews
use Pillow; they show actual resource pixels and never enter game resources.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/rematter'
SIZE = 64
P = {
    'ink': (35, 40, 43), 'rail': (50, 57, 60), 'rail_lit': (76, 85, 87),
    'steel': (119, 133, 134), 'silver': (180, 193, 192),
    'white': (230, 225, 210), 'white_lit': (252, 247, 230), 'white_mid': (204, 200, 187),
    'white_shadow': (155, 158, 151), 'ceramic_edge': (110, 119, 116),
    'copper': (173, 105, 67), 'copper_lit': (231, 169, 115), 'copper_hi': (255, 213, 159), 'copper_dark': (104, 61, 43),
    'glass': (24, 63, 70), 'glass_mid': (38, 92, 98), 'glass_edge': (77, 137, 139),
    'cyan_deep': (19, 108, 120), 'cyan': (43, 184, 189), 'cyan_mid': (91, 213, 210),
    'cyan_lit': (169, 245, 230), 'cyan_hi': (231, 255, 247),
    'board': (43, 76, 73), 'board_lit': (66, 107, 98), 'board_dark': (29, 53, 54),
    'sand': (212, 201, 174), 'sand_lit': (247, 238, 209), 'sand_mid': (187, 177, 152),
    'sand_shadow': (141, 134, 115), 'sand_pore': (106, 108, 95),
    'clay': (151, 143, 159), 'clay_lit': (205, 197, 211), 'clay_mid': (175, 165, 183),
    'clay_dark': (94, 91, 111), 'clay_deep': (61, 64, 79),
    'red': (155, 59, 50), 'red_lit': (221, 105, 77), 'red_mid': (183, 77, 59),
    'red_dark': (89, 45, 43), 'red_deep': (58, 39, 41), 'red_hot': (255, 160, 112),
    'milk': (208, 222, 227), 'milk_lit': (246, 249, 240), 'milk_mid': (174, 194, 209),
    'milk_dark': (118, 146, 167), 'milk_edge': (84, 112, 133),
    'gold': (230, 180, 70), 'gold_lit': (255, 225, 133), 'gold_hi': (255, 246, 196),
    'gold_mid': (195, 139, 48), 'gold_dark': (135, 92, 39),
    'violet': (156, 137, 191), 'violet_lit': (211, 196, 232),
}


class Sprite:
    def __init__(self):
        self.pixels = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]

    def dot(self, x, y, color):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.pixels[y][x] = (*P[color], 255) if isinstance(color, str) else color

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): self.dot(x, y, color)

    def line(self, a, b, color):
        x, y = a; xx, yy = b
        dx, dy = abs(xx - x), -abs(yy - y)
        sx, sy = 1 if x < xx else -1, 1 if y < yy else -1
        err = dx + dy
        while True:
            self.dot(x, y, color)
            if (x, y) == (xx, yy): break
            e = 2 * err
            if e >= dy: err += dy; x += sx
            if e <= dx: err += dx; y += sy

    def poly(self, points, color, outline=None):
        for y in range(min(p[1] for p in points), max(p[1] for p in points) + 1):
            for x in range(min(p[0] for p in points), max(p[0] for p in points) + 1):
                inside = False
                for i, (ax, ay) in enumerate(points):
                    bx, by = points[(i + 1) % len(points)]
                    if (ay > y + .5) != (by > y + .5) and x + .5 < (bx - ax) * (y + .5 - ay) / (by - ay) + ax:
                        inside = not inside
                if inside: self.dot(x, y, color)
        if outline:
            for i, point in enumerate(points): self.line(point, points[(i + 1) % len(points)], outline)

    def png(self):
        def chunk(kind, data):
            return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
        raw = b''.join(b'\0' + bytes(v for p in row for v in p) for row in self.pixels)
        return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')


def bolt(s, x, y, copper=True):
    s.rect(x, y, x+3, y+3, 'copper_dark' if copper else 'ink')
    s.rect(x, y, x+2, y+2, 'copper' if copper else 'steel')
    s.line((x, y), (x+2, y), 'copper_lit' if copper else 'silver')
    s.dot(x+1, y+1, 'copper_hi' if copper else 'white_lit')


def mineral_surface(s, shades, seed, spacing=4):
    """Sparse mineral inclusions within existing faces, preserving every hand-drawn edge."""
    originals = [row[:] for row in s.pixels]
    for y in range(5, 60, spacing):
        for x in range(4, 60, spacing):
            value = (x*47 + y*79 + seed*113) % 23
            if value > 7: continue
            base = originals[y][x]
            for source, targets in shades.items():
                if base != (*P[source], 255): continue
                target = targets[value % len(targets)]
                for dx, dy in ((0,0), (1,0), (0,1)):
                    if originals[y+dy][x+dx] == base: s.dot(x+dx, y+dy, target)


def frame():
    s = Sprite()
    # An open chassis, with the back strut visible through a real alpha centre.
    s.poly([(23, 10), (28, 12), (28, 38), (23, 36)], 'white_shadow', 'ink')
    s.line((24, 13), (24, 34), 'white')
    s.poly([(7, 42), (24, 31), (57, 42), (41, 54)], 'rail', 'ink')
    s.poly([(15, 41), (24, 35), (49, 43), (41, 48)], (0, 0, 0, 0))
    s.poly([(7, 42), (41, 53), (41, 59), (7, 48)], 'white_mid', 'ink')
    s.poly([(41, 53), (57, 42), (57, 48), (41, 59)], 'white_shadow', 'ink')
    s.line((9, 43), (39, 53), 'white_lit')
    s.poly([(6, 18), (13, 20), (13, 47), (6, 45)], 'white', 'ink')
    s.poly([(13, 20), (17, 17), (17, 44), (13, 47)], 'rail_lit', 'ink')
    s.line((7, 21), (7, 42), 'white_lit')
    s.poly([(48, 22), (56, 17), (56, 45), (48, 51)], 'white_shadow', 'ink')
    s.poly([(42, 23), (48, 22), (48, 51), (42, 49)], 'white', 'ink')
    s.line((43, 26), (43, 46), 'white_lit')
    s.poly([(6, 17), (23, 6), (57, 17), (41, 28)], 'white', 'ink')
    s.poly([(15, 17), (24, 11), (47, 18), (40, 23)], (0, 0, 0, 0), 'ceramic_edge')
    s.line((7, 17), (23, 7), 'white_lit'); s.line((24, 7), (55, 17), 'white_lit')
    s.poly([(6, 17), (41, 28), (41, 33), (6, 22)], 'white_mid', 'ink')
    s.poly([(41, 28), (57, 17), (57, 22), (41, 33)], 'rail_lit', 'ink')
    s.line((9, 20), (38, 29), 'white')
    for x, y in ((8, 22), (43, 30), (8, 43), (43, 50)): bolt(s, x, y)
    s.line((18, 49), (28, 52), 'ceramic_edge')
    return s


def circuit():
    s = Sprite()
    # Thin square control PCB: exposed traces, processor and an edge connector.
    s.poly([(9, 10), (45, 4), (55, 13), (55, 45), (44, 56), (8, 52), (5, 44), (5, 17)], 'ink')
    s.poly([(10, 11), (44, 6), (52, 13), (52, 43), (43, 52), (9, 49), (8, 43), (8, 18)], 'board')
    s.poly([(11, 12), (43, 8), (48, 12), (11, 17)], 'board_lit')
    s.poly([(48, 16), (52, 14), (52, 43), (44, 50), (44, 46), (48, 42)], 'board_dark')
    s.line((10, 19), (10, 43), 'board_lit')
    traces = [[(13, 19), (18, 19), (21, 22)], [(38, 12), (38, 19), (36, 21)],
              [(46, 25), (41, 25), (38, 28)], [(45, 39), (40, 39), (36, 35)],
              [(14, 42), (21, 42), (25, 37)], [(14, 28), (19, 28)],
              [(29, 45), (29, 40), (30, 37)]]
    for route in traces:
        for a, b in zip(route, route[1:]): s.line(a, b, 'copper_lit')
        x, y = route[0]; s.rect(x-1, y-1, x+1, y+1, 'copper'); s.dot(x, y, 'copper_hi')
    for y in (23, 27, 31, 35): s.rect(18, y, 41, y+1, 'copper')
    for x in (23, 27, 31, 35): s.rect(x, 19, x+1, 39, 'copper_lit')
    s.poly([(22, 20), (34, 18), (39, 22), (39, 34), (35, 38), (21, 36), (20, 24)], 'ink')
    s.poly([(23, 22), (33, 20), (36, 23), (36, 33), (33, 35), (23, 33)], 'rail')
    s.line((23, 22), (33, 20), 'silver'); s.line((24, 25), (32, 24), 'rail_lit')
    s.rect(25, 27, 29, 30, 'cyan_deep'); s.rect(25, 27, 27, 28, 'cyan_lit')
    for x in (13, 19, 25, 31, 37):
        s.poly([(x, 48), (x+3, 48), (x+3, 54), (x, 54)], 'copper_dark')
        s.rect(x, 49, x+2, 53, 'copper_lit'); s.line((x, 49), (x+2, 49), 'copper_hi')
    for x, y in ((10, 14), (45, 12), (10, 44), (44, 43)): bolt(s, x, y, False)
    s.rect(39, 16, 44, 18, 'white_mid'); s.rect(40, 16, 43, 17, 'white_lit')
    return s


def core_prism(s, x, y, w, h):
    m = x + w//2
    s.poly([(m, y), (x+w, y+5), (x+w, y+h-6), (m, y+h), (x, y+h-5), (x, y+5)], 'cyan_deep', 'glass')
    s.poly([(m, y+1), (x+w-1, y+5), (m, y+9), (x+1, y+5)], 'cyan_lit')
    s.poly([(x+1, y+6), (m, y+10), (m, y+h-2), (x+1, y+h-6)], 'cyan')
    s.poly([(m+1, y+10), (x+w-1, y+6), (x+w-1, y+h-7), (m+1, y+h-2)], 'cyan_mid')
    s.line((m, y+2), (m, y+h-2), 'cyan_hi')
    s.line((x+2, y+7), (x+2, y+h-7), 'cyan_lit')
    s.poly([(m+2, y+h-12), (x+w-2, y+h-14), (x+w-2, y+h-8), (m+2, y+h-5)], 'cyan_deep')
    if w >= 12:
        s.line((x+3, y+h//2), (m-2, y+h//2+2), 'glass_mid')
        s.line((m+2, y+h//2+4), (x+w-2, y+h//2+2), 'cyan_hi')


def cell(tier):
    s = Sprite()
    left, right = ((18, 42), (13, 46), (8, 49))[tier-1]
    accent = ('cyan', 'gold', 'violet')[tier-1]
    accent_lit = ('cyan_lit', 'gold_lit', 'violet_lit')[tier-1]
    # Each tier has a different case width and window/core arrangement, not just a recolour.
    s.poly([(left, 9), (left+7, 3), (right+2, 3), (right+9, 9), (right+9, 50), (right, 59), (left, 59)], 'ink')
    s.poly([(right, 11), (right+7, 7), (right+7, 49), (right, 56)], 'white_shadow', 'ink')
    s.line((right+5, 14), (right+5, 45), 'white_mid')
    s.poly([(left+1, 9), (left+8, 4), (right+1, 4), (right+6, 8), (right-1, 13)], 'white_lit', 'ceramic_edge')
    s.rect(left+1, 11, right-1, 55, 'white')
    s.line((left+1, 12), (left+1, 52), 'white_lit')
    s.rect(left+5, 16, right-5, 47, 'rail')
    s.rect(left+6, 17, right-6, 46, 'glass')
    s.rect(left+7, 18, right-7, 45, 'glass_mid')
    s.line((left+7, 19), (left+7, 43), 'glass_edge')
    if tier == 1: core_prism(s, left+7, 19, right-left-14, 25)
    elif tier == 2:
        core_prism(s, left+8, 19, 12, 26)
        core_prism(s, left+19, 21, 7, 22)
    else:
        for x, y in ((left+8, 18), (left+21, 18), (left+8, 32), (left+21, 32)):
            core_prism(s, x, y, 11, 14)
    # Pale window reflections never cover the cyan core silhouette.
    s.line((left+8, 18), (left+13, 18), 'cyan_lit')
    s.line((right-7, 37), (right-7, 44), 'glass_edge')
    for x in (left-1, right-3):
        s.rect(x, 11, x+4, 54, 'rail'); s.line((x, 13), (x, 51), 'rail_lit')
        for y in (13, 46):
            s.rect(x-1, y, x+5, y+4, 'ink'); s.rect(x, y, x+4, y+2, 'rail_lit')
    s.rect(left+4, 10, right-4, 15, 'white'); s.line((left+5, 10), (right-5, 10), 'white_lit')
    s.rect(left+4, 48, right-4, 54, 'white_mid'); s.line((left+5, 48), (right-5, 48), 'white_lit')
    start = (left+right)//2 - (tier*4-2)//2
    for i in range(tier): s.rect(start+i*4, 51, start+i*4+1, 53, accent)
    s.rect(left+6, 12, left+8, 13, accent_lit)
    s.line((left+11, 12), (right-6, 12), 'white_mid')
    s.line((right+2, 9), (right+5, 7), 'silver')
    for x,y in ((left, 14), (right-2, 14), (left, 47), (right-2, 47)):
        s.rect(x,y,x+1,y+1,'copper');s.dot(x,y,'copper_lit')
    for x in range(left+7, right-5, 6):
        s.rect(x, 55, x+3, 60, 'copper_dark'); s.rect(x, 55, x+2, 59, 'copper')
        s.line((x, 55), (x+2, 55), 'copper_hi'); s.line((x, 57), (x, 59), 'copper_lit')
    for y in (19, 25, 31, 37): s.line((right+2, y), (right+5, y-2), 'rail')
    if tier == 3:
        # Reinforced shoulder brackets distinguish the dense cartridge in silhouette.
        for x in (left-3, right+5):
            s.rect(x, 23, x+3, 28, 'rail'); s.rect(x, 34, x+3, 39, 'rail')
            s.line((x, 23), (x+3, 23), 'copper_lit'); s.line((x, 34), (x+3, 34), 'copper')
    return s


def silith():
    s = Sprite()
    # Chalky, irregular sintered grains: pits have lit lower rims and deep centres.
    shapes = [([(9, 26), (16, 20), (29, 22), (34, 30), (31, 44), (17, 48), (8, 40)],
               [(22, 13), (29, 7), (41, 9), (46, 16), (44, 32), (31, 35), (23, 28)],
               [(31, 34), (40, 28), (52, 31), (57, 40), (54, 51), (43, 56), (31, 49)])]
    for points in shapes[0]:
        s.poly(points, 'sand', 'sand_shadow')
        x, y = points[0]; top = points[1]
        s.poly([points[0], points[1], points[2], (points[3][0]-2, points[3][1]-1), (top[0]+1, y+6)], 'sand_lit')
        s.poly([points[3], points[4], points[5], (top[0]+1, y+6)], 'sand_mid')
        s.line(points[0], points[1], 'white_lit')
    for x, y in ((14,29),(25,39),(29,17),(37,23),(44,36),(38,46),(49,49)):
        # Tiny, asymmetric voids in granular fracture faces, not regular square holes.
        s.dot(x,y,'sand_shadow');s.dot(x+1,y,'sand_pore');s.dot(x+1,y+1,'sand_shadow')
        s.dot(x,y+2,'sand_lit');s.dot(x+2,y+1,'sand')
    for x,y in ((9,29),(11,39),(24,15),(23,25),(34,33),(53,36),(53,46),(44,54)):
        s.dot(x,y,'sand_lit');s.dot(x+1,y-1,'sand');s.dot(x+1,y,'sand_mid')
    for x,y in ((4,47),(16,53),(25,49),(57,30),(8,18)):
        s.poly([(x,y),(x+3,y-2),(x+5,y+1),(x+4,y+5),(x,y+4)], 'sand_mid', 'sand_shadow')
        s.rect(x+1,y,x+2,y+1,'sand_lit')
    mineral_surface(s, {'sand':('sand_lit','sand_mid'), 'sand_lit':('sand','white_lit'),
                        'sand_mid':('sand','sand_shadow')}, 3, 3)
    return s


def argil():
    s = Sprite()
    # Broad torn clay lamellae; their layered sides contrast with every crystalline fraction.
    for points, depth in [([(5,37),(22,27),(49,32),(57,40),(38,53),(12,49)],6),
                          ([(9,25),(27,17),(51,24),(46,33),(29,41),(7,34)],5),
                          ([(18,13),(34,6),(48,13),(43,24),(30,29),(15,22)],6)]:
        front = points[2:]+points[:1]
        s.poly(points+[(x,y+depth) for x,y in reversed(points)], 'clay_dark', 'clay_deep')
        s.poly(points, 'clay', 'clay_deep')
        s.poly([points[0],points[1],points[2],(points[2][0]-7,points[2][1]+4),(points[0][0]+2,points[0][1]+3)], 'clay_lit')
        s.line(points[0],points[1],'milk_lit')
        for a,b in zip(front,front[1:]):
            s.line((a[0],a[1]+2),(b[0],b[1]+2),'clay_mid')
            s.line((a[0],a[1]+4),(b[0],b[1]+4),'clay_deep')
    s.line((25,16),(31,19),'clay_dark');s.line((31,19),(37,18),'clay_dark')
    s.line((15,30),(23,32),'clay_mid');s.line((34,39),(44,36),'clay_lit')
    for x,y in ((4,52),(48,53),(54,25)):
        s.poly([(x,y),(x+5,y-2),(x+7,y+1),(x+3,y+4)],'clay_mid','clay_dark')
        s.line((x+1,y),(x+4,y-1),'clay_lit')
    for a,b in (((22,14),(28,11)),((30,17),(41,13)),((19,27),(27,23)),
                ((13,39),(22,35)),((29,43),(41,38)),((34,25),(40,22))):
        s.line(a,b,'clay_mid')
        s.dot(a[0]+1,a[1]-1,'clay_lit')
    mineral_surface(s, {'clay':('clay_mid','clay_dark'), 'clay_lit':('clay_mid','milk_lit')}, 7, 5)
    return s


def rhedon():
    s = Sprite()
    # Jagged dark ferric fragments with red fracture planes; not translucent gems.
    s.poly([(20,12),(28,5),(39,10),(44,21),(39,39),(25,40),(16,28)],'red_deep','ink')
    s.poly([(21,12),(28,6),(37,11),(31,19),(22,21)],'red_mid')
    s.poly([(22,22),(31,20),(37,12),(41,22),(36,35),(28,30)],'red')
    s.poly([(25,14),(28,10),(32,12),(29,19)],'red_lit')
    s.poly([(5,33),(15,23),(29,31),(31,48),(22,58),(8,53)],'red_dark','ink')
    s.poly([(6,33),(15,25),(26,32),(17,39)],'red_mid')
    s.poly([(8,35),(16,40),(21,55),(9,51)],'red')
    s.poly([(30,34),(42,26),(55,32),(59,44),(49,55),(32,52),(26,43)],'red_dark','ink')
    s.poly([(31,34),(42,28),(54,33),(43,39),(35,41)],'red')
    s.poly([(44,40),(56,35),(57,43),(48,52),(43,49)],'red_deep')
    s.poly([(27,8),(32,10),(30,14),(26,17),(23,14)],'red_lit')
    s.poly([(32,20),(38,15),(40,22),(37,27),(32,25)],'red_dark')
    s.poly([(8,37),(12,37),(16,44),(15,49),(10,47)],'red_deep')
    s.poly([(17,29),(23,32),(21,36),(17,35)],'red_lit')
    s.poly([(31,40),(35,41),(40,48),(36,50),(31,47)],'red_deep')
    s.poly([(39,31),(44,30),(49,32),(44,35),(40,34)],'red_mid')
    for points in [[(20,15),(23,22),(22,28),(27,34)],[(14,28),(17,34),(16,42),(20,49)],[(35,38),(41,41),(43,49)],[(47,31),(50,34),(47,40)]]:
        for a,b in zip(points,points[1:]):
            s.line(a,b,'red_lit'); s.line((a[0]+1,a[1]),(b[0]+1,b[1]),'red_mid')
        s.dot(*points[0],'red_hot')
    for x,y in ((3,23),(57,53),(32,57)):
        s.poly([(x,y),(x+3,y-1),(x+5,y+3),(x+2,y+5),(x,y+3)],'red_dark','red_deep')
        s.line((x+1,y),(x+3,y),'red_lit')
    mineral_surface(s, {'red':('red_mid','red_dark'), 'red_dark':('red_deep','red'),
                        'red_deep':('red_dark',)}, 11, 4)
    return s


def virel():
    s = Sprite()
    # Slender milky prisms, oblique natural tips and cloudy internal cleavage.
    for x,y,w,h in ((6,29,14,25),(24,5,17,49),(43,23,13,31)):
        m=x+w//2
        s.poly([(x,y+9),(m,y),(x+w,y+6),(x+w,y+h-5),(m,y+h),(x,y+h-5)],'milk_mid','milk_edge')
        s.poly([(x+1,y+9),(m,y+1),(m,y+h-2),(x+1,y+h-6)],'milk_lit')
        s.poly([(m+1,y+2),(x+w-1,y+7),(x+w-1,y+h-6),(m+1,y+h-2)],'milk')
        s.poly([(x+w-5,y+7),(x+w-1,y+9),(x+w-1,y+h-6),(x+w-5,y+h-4)],'milk_mid')
        s.line((x+w-1,y+10),(x+w-1,y+h-7),'milk_dark')
        s.line((m,y+2),(m,y+h-2),'white_lit')
        s.poly([(x+2,y+13),(m-1,y+17),(m-1,y+22),(x+2,y+18)],'milk_mid')
        s.poly([(m+2,y+h-17),(x+w-2,y+h-21),(x+w-2,y+h-14),(m+2,y+h-11)],'milk_dark')
        s.line((x+2,y+h-9),(m-2,y+h-6),'milk')
        s.line((x+w-2,y+10),(x+w-2,y+17),'milk_lit')
        s.line((m+2,y+10),(m+2,y+h-6),'milk_mid')
        s.poly([(m+3,y+15),(x+w-2,y+18),(x+w-2,y+24),(m+3,y+21)],'milk_lit')
    s.poly([(15,51),(21,47),(25,51),(22,57),(17,58)],'milk','milk_edge')
    s.line((16,51),(21,48),'white_lit')
    s.poly([(50,57),(55,53),(60,57),(56,61)],'milk_mid','milk_edge')
    s.line((51,57),(55,54),'milk_lit')
    return s


def golden_grain(s,x,y,w,h):
    s.poly([(x,y+3),(x+w//2,y),(x+w,y+3),(x+w,y+h),(x+w//2,y+h+3),(x,y+h)],'gold_dark','gold_dark')
    s.poly([(x,y+3),(x+w//2,y),(x+w,y+3),(x+w//2,y+6)],'gold_lit')
    s.poly([(x,y+4),(x+w//2,y+7),(x+w//2,y+h+2),(x,y+h)],'gold')
    s.poly([(x+w//2+1,y+7),(x+w,y+4),(x+w,y+h),(x+w//2+1,y+h+2)],'gold_mid')
    s.line((x+1,y+3),(x+w//2,y+1),'gold_hi')
    s.dot(x+1,y+5,'gold_lit')


def lumin():
    s=Sprite()
    # Many individual golden grains; no repeated large shard silhouette.
    for p in ((25,9,11,16),(14,22,11,10),(37,20,10,12),(25,26,13,13),(5,35,10,9),
              (15,37,12,12),(38,37,13,12),(28,42,12,12),(48,46,10,8),(7,53,6,5),
              (21,54,5,4),(51,27,6,6),(5,24,5,5),(42,54,5,4)):
        golden_grain(s,*p)
    s.dot(14,13,'gold_hi');s.dot(13,14,'gold');s.dot(15,14,'gold')
    s.dot(49,13,'gold_lit');s.dot(49,14,'gold')
    return s


def matrix_blend():
    s=Sprite()
    # Dry, fine material: one low mound and loose grains, visibly not a rock or vessel.
    s.poly([(4,48),(11,39),(19,34),(25,22),(32,19),(39,31),(49,36),(59,48),(51,55),(15,56)],'sand_shadow')
    s.poly([(5,47),(13,39),(20,36),(26,24),(32,21),(38,32),(48,38),(56,48),(49,52),(17,53)],'sand')
    s.poly([(13,42),(21,36),(26,25),(31,23),(34,30),(31,38),(24,44)],'sand_lit')
    s.poly([(34,36),(42,35),(48,39),(54,47),(46,51),(36,48),(29,45)],'clay_mid')
    s.poly([(38,38),(43,38),(48,42),(43,46),(35,44)],'clay_lit')
    for x,y in ((11,46),(19,42),(25,31),(29,26),(31,37),(17,49),(24,48),(49,46),(38,47),(43,41),(32,51),(54,51)):
        s.rect(x,y,x+1,y+1,'sand_lit' if x%2 else 'white_lit')
        s.dot(x,y+2,'sand_shadow')
    for x,y in ((8,34),(17,30),(40,25),(55,35),(4,54),(23,58),(57,57)):
        s.rect(x,y,x+2,y+1,'sand_mid');s.dot(x,y,'sand_lit')
    mineral_surface(s, {'sand':('sand_lit','sand_mid','clay_mid'),
                        'sand_lit':('sand','white_lit'), 'clay_mid':('sand','clay_lit')}, 5, 3)
    return s


def lattice_cube(s,x,y,w,h):
    # Ordered, grown lattice: a cubic top and two orthogonal pierced crystal faces.
    m=x+w//2; shoulder=y+6
    s.poly([(x,shoulder),(m,y),(x+w,shoulder),(x+w,y+h-7),(m,y+h),(x,y+h-7)],'cyan_deep','glass')
    s.poly([(x+1,shoulder),(m,y+1),(x+w-1,shoulder),(m,shoulder+7)],'cyan_lit')
    s.poly([(x+1,shoulder+1),(m,shoulder+8),(m,y+h-1),(x+1,y+h-7)],'cyan')
    s.poly([(m+1,shoulder+8),(x+w-1,shoulder+1),(x+w-1,y+h-7),(m+1,y+h-1)],'cyan_deep')
    s.line((m,shoulder+7),(m,y+h-1),'cyan_hi')
    s.line((x+1,shoulder),(m,y+1),'cyan_hi');s.line((m,y+1),(x+w-1,shoulder),'cyan_hi')
    if w>=24:
        for a,b in (((x+5,shoulder+3),(x+5,y+h-6)),((x+11,shoulder+6),(x+11,y+h-3)),((m+6,shoulder+4),(m+6,y+h-4)),((m+12,shoulder+1),(m+12,y+h-8))):s.line(a,b,'cyan_lit')
        for yy in (shoulder+12,shoulder+21):
            s.line((x+1,yy-5),(m,yy+2),'cyan_lit');s.line((m+1,yy+2),(x+w-1,yy-5),'cyan')
        for px,py in ((x+6,shoulder+7),(x+6,shoulder+17),(m+7,shoulder+6),(m+7,shoulder+16)):
            s.poly([(px,py),(px+4,py+2),(px+4,py+7),(px,py+5)],'glass_mid')
            s.line((px,py+5),(px+3,py+6),'cyan_hi')
        s.poly([(m-5,y+5),(m,y+3),(m+5,y+5),(m,y+8)],'cyan_deep')
        s.line((m-4,y+5),(m,y+3),'glass_mid')
    else:
        s.poly([(x+3,shoulder+5),(m-2,shoulder+7),(m-2,y+h-5),(x+3,y+h-8)],'cyan_deep')
        s.line((x+3,shoulder+5),(m-2,shoulder+7),'cyan_hi')
        s.line((m+3,shoulder+7),(x+w-3,shoulder+4),'cyan_mid')


def matrix_crystal():
    s=Sprite()
    lattice_cube(s,13,6,36,48)
    # Two tiny growth fragments retain the technical crystal's ordered geometry.
    s.poly([(5,49),(10,46),(15,49),(15,54),(10,57),(5,54)],'cyan_deep','glass')
    s.poly([(6,49),(10,47),(14,49),(10,52)],'cyan_lit');s.line((10,52),(10,56),'cyan')
    s.poly([(51,52),(55,50),(59,52),(59,56),(55,59),(51,56)],'cyan_deep','glass')
    s.poly([(52,52),(55,51),(58,52),(55,54)],'cyan_mid')
    return s


def matrix_cluster():
    s=Sprite()
    # Four distinct cores in a precise 2×2 coupling, separated by dark channels.
    s.poly([(7,39),(28,27),(57,39),(57,48),(35,60),(7,48)],'milk_dark','ink')
    s.poly([(8,39),(29,28),(56,39),(35,51)],'milk_lit')
    s.poly([(8,41),(35,53),(35,58),(8,47)],'milk_mid')
    s.poly([(36,53),(56,42),(56,47),(36,58)],'milk_edge')
    lattice_cube(s,23,3,17,25)
    lattice_cube(s,4,15,17,26)
    lattice_cube(s,42,16,17,26)
    lattice_cube(s,23,29,17,26)
    s.poly([(26,24),(31,21),(36,24),(36,29),(31,32),(26,29)],'red_dark','ink')
    s.poly([(27,24),(31,22),(35,24),(31,27)],'red_lit')
    for a,b in (((19,39),(25,42)),((39,43),(45,40)),((20,19),(25,16)),((39,17),(44,20))):
        s.line(a,b,'milk_lit')
    return s


def optical_circuit():
    s=Sprite()
    # A ceramic photonic board, with a visible milky waveguide and golden optical junctions.
    s.poly([(17,5),(43,5),(56,16),(56,45),(44,57),(15,57),(5,46),(5,17)],'ink')
    s.poly([(18,7),(42,7),(53,17),(53,44),(42,53),(17,53),(8,44),(8,18)],'white_mid')
    s.poly([(18,8),(42,8),(50,16),(48,19),(14,19),(10,17)],'white_lit')
    s.poly([(14,19),(46,19),(48,23),(48,42),(41,49),(17,49),(12,42),(12,24)],'board_dark','rail')
    for y in (25,30,35,40):
        s.rect(8,y,12,y+1,'copper');s.rect(49,y,53,y+1,'copper_lit')
    for x in (20,27,34,41):s.rect(x,51,x+2,59,'copper_dark');s.rect(x,52,x+1,58,'copper_lit')
    s.poly([(20,39),(25,22),(35,22),(41,39),(37,43),(30,30),(24,43)],'milk_edge')
    s.line((21,39),(26,23),'milk_lit');s.line((26,23),(34,23),'milk_lit');s.line((34,23),(40,39),'milk')
    s.line((23,40),(30,29),'cyan_mid');s.line((30,29),(37,40),'cyan_lit')
    for x,y in ((19,37),(27,18),(37,37)):
        s.poly([(x,y),(x+6,y),(x+8,y+4),(x+4,y+8),(x,y+5)],'gold_dark','ink')
        s.poly([(x+1,y+1),(x+5,y+1),(x+6,y+4),(x+3,y+5)],'gold_lit')
        s.dot(x+2,y+1,'gold_hi')
    for x,y in ((12,12),(45,12),(12,45),(45,45)):bolt(s,x,y,False)
    s.rect(27,10,35,12,'rail');s.rect(28,10,32,11,'cyan_lit')
    return s


def cell_atlas():
    """64px texture, with the original normalized 32×32 box UVs unchanged.

    The Drive uses an 8×4.3×7.1 cube at texOffs(0,0), LayerDefinition(32,32).
    Doubling the pixels preserves every UV boundary and increases detail only.
    The texture depicts an installed cartridge, never capacity or tier state.
    """
    s=Sprite();s.rect(0,0,63,63,'rail')
    # Top: physical u14..30/v0..14. White shell, dark inset and cyan window.
    s.rect(14,0,30,13,'white_mid');s.rect(15,0,29,12,'white')
    s.line((15,0),(28,0),'white_lit');s.line((15,12),(28,12),'ceramic_edge')
    s.rect(18,3,26,9,'rail');s.rect(19,4,25,8,'glass')
    s.poly([(22,4),(25,6),(22,8),(19,6)],'cyan');s.line((22,4),(22,8),'cyan_hi')
    s.rect(14,4,16,10,'rail');s.rect(28,4,30,10,'rail')
    # Front/north: physical u14..30/v14..22, fitted in the existing Drive bay.
    s.rect(14,14,30,22,'white');s.line((15,14),(29,14),'white_lit')
    s.rect(16,16,28,21,'rail');s.rect(18,17,26,20,'glass')
    s.rect(20,17,24,20,'cyan_deep');s.rect(21,17,23,19,'cyan');s.dot(22,17,'cyan_hi')
    s.rect(14,17,16,20,'rail_lit');s.rect(28,17,30,20,'rail_lit')
    # Side faces stay coherent at the shared face edges.
    for a,b in ((0,13),(31,44),(46,60)):
        s.rect(a,14,b,22,'white_shadow');s.line((a,14),(b,14),'white_mid')
        s.line((a,21),(b,21),'rail');s.line((a,22),(b,22),'ink')
    for x in (48,52,56):s.rect(x,16,x+1,20,'copper');s.dot(x,16,'copper_hi')
    s.rect(4,17,10,18,'rail');s.rect(34,17,41,18,'rail')
    return s


ART = {
    'machine_frame': ('Machine Frame', frame),
    'control_circuit': ('Control Circuit', circuit),
    'optical_circuit': ('Optical Circuit', optical_circuit),
    'matter_cell_64k': ('64k Matter Cell', lambda: cell(1)),
    'matter_cell_256k': ('256k Matter Cell', lambda: cell(2)),
    'matter_cell_1m': ('1M Matter Cell', lambda: cell(3)),
    'silith': ('Silith', silith),
    'argil': ('Argil', argil),
    'rhedon': ('Rhedon', rhedon),
    'virel': ('Virel', virel),
    'lumin': ('Lumin', lumin),
    'matrix_blend': ('Matrix Blend', matrix_blend),
    'matrix_crystal': ('Matrix Crystal', matrix_crystal),
    'matrix_cluster': ('Matrix Cluster', matrix_cluster),
}


def write(path, content, check):
    if check:
        if not path.is_file() or path.read_bytes() != content: raise ValueError(f'Generated asset differs: {path.relative_to(ROOT)}')
    else:
        path.parent.mkdir(parents=True, exist_ok=True);path.write_bytes(content)


def previews(path, sprites):
    from PIL import Image, ImageDraw, ImageFont
    columns=4;rows=(len(ART)+columns-1)//columns
    sheet=Image.new('RGB',(columns*242+32,rows*264+86),(231,230,220));draw=ImageDraw.Draw(sheet)
    title=ImageFont.load_default(size=21);label=ImageFont.load_default(size=16);small=ImageFont.load_default(size=11)
    draw.text((22,17),'REMATTER / CLEAN LAB',font=title,fill=P['rail'])
    draw.text((23,47),'Actual 64 px resource art · nearest-neighbour preview',font=small,fill=P['steel'])
    transparent=Image.new('RGBA',(columns*SIZE,rows*SIZE),(0,0,0,0))
    for i,(name,(name_label,_)) in enumerate(ART.items()):
        x,y=18+i%columns*242,74+i//columns*264
        draw.rectangle((x,y,x+228,y+250),fill=(245,243,234),outline=(192,194,185))
        for yy in range(0,192,12):
            for xx in range(0,192,12):
                c=(224,226,216) if (xx//12+yy//12)%2 else (236,237,229)
                draw.rectangle((x+18+xx,y+12+yy,x+29+xx,y+23+yy),fill=c)
        icon=Image.new('RGBA',(SIZE,SIZE));icon.putdata([pixel for row in sprites[name].pixels for pixel in row])
        transparent.paste(icon,(i%columns*SIZE,i//columns*SIZE))
        scaled=icon.resize((192,192),Image.Resampling.NEAREST);sheet.paste(scaled,(x+18,y+12),scaled)
        draw.text((x+14,y+215),name_label,font=label,fill=P['rail'])
        draw.text((x+14,y+237),'64 × 64 / original pixel geometry',font=small,fill=P['steel'])
    path.parent.mkdir(parents=True,exist_ok=True);sheet.save(path)
    transparent.save(path.with_name(path.stem+'-64px-transparent.png'))
    transparent.resize((transparent.width*4,transparent.height*4),Image.Resampling.NEAREST).save(path.with_name(path.stem+'-4x-transparent.png'))
    # Actual 16px slot sampling on a dark GUI field, enlarged only after downsampling.
    slots=Image.new('RGB',(7*56,2*46),(26,32,35));slot_draw=ImageDraw.Draw(slots)
    for i,(name,_) in enumerate(ART.items()):
        x,y=12+i%7*56,8+i//7*46
        slot_draw.rectangle((x-3,y-3,x+18,y+18),fill=(43,51,53),outline=(90,103,102))
        icon=Image.new('RGBA',(SIZE,SIZE));icon.putdata([pixel for row in sprites[name].pixels for pixel in row])
        icon=icon.resize((16,16),Image.Resampling.NEAREST);slots.paste(icon,(x,y),icon)
        slot_draw.text((x-1,y+23),str(i+1),font=ImageFont.load_default(size=9),fill=(194,202,197))
    slots.save(path.with_name(path.stem+'-16px-dark.png'))
    slots.resize((slots.width*4,slots.height*4),Image.Resampling.NEAREST).save(path.with_name(path.stem+'-16px-dark-4x.png'))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    parser.add_argument('--preview',type=Path,help='Optional contact sheet outside game assets (requires Pillow)')
    args=parser.parse_args();sprites={}
    for name,(_,factory) in ART.items():
        sprite=factory();sprites[name]=sprite
        write(ASSETS/f'textures/item/{name}.png',sprite.png(),args.check)
        for folder,data in {
            'models/item':{'parent':'minecraft:item/generated','textures':{'layer0':f'rematter:item/{name}'}},
            'items':{'model':{'type':'minecraft:model','model':f'rematter:item/{name}'}},
        }.items():write(ASSETS/folder/f'{name}.json',(json.dumps(data,indent=2)+'\n').encode(),args.check)
    write(ASSETS/'textures/entity/cell.png',cell_atlas().png(),args.check)
    if args.preview:previews(args.preview,sprites)
    print(f'{"Checked" if args.check else "Generated"} {len(sprites)} original 64px component icons and a UV-compatible 64px cell atlas.')


if __name__=='__main__':main()
