"""Cuboid geometry of the machine family.

A model is a list of axis-aligned boxes in model pixels, 16 to a block, with
its front at north and the viewer's left at +X. This module decides which
faces of a box can be seen, maps sprites onto them at two texels per pixel,
and rejects geometry that would flicker: coordinates off the half-pixel grid,
slivers, and faces that lie in one plane, look the same way and overlap.
"""
from itertools import combinations

FACES = ('north', 'south', 'east', 'west', 'up', 'down')
AXIS = {'north': 2, 'south': 2, 'east': 0, 'west': 0, 'up': 1, 'down': 1}
OUTWARD = {'north': -1, 'south': 1, 'east': 1, 'west': -1, 'up': 1, 'down': -1}
VECTOR = {'+x': (1, 0, 0), '-x': (-1, 0, 0), '+y': (0, 1, 0), '-y': (0, -1, 0), '+z': (0, 0, 1), '-z': (0, 0, -1)}
# Corners of a face seen from outside: top left, bottom left, bottom right,
# top right. Each entry picks the from (0) or to (1) coordinate per axis.
CORNERS = {
    'down': ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
    'up': ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    'north': ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    'south': ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    'west': ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    'east': ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
}
# Where the six sides of a north-pointing part end up after the block state
# rotation that turns it toward another direction.
TURNED = {
    'north': dict(north='north', south='south', east='east', west='west', up='up', down='down'),
    'east': dict(north='east', south='west', east='south', west='north', up='up', down='down'),
    'south': dict(north='south', south='north', east='west', west='east', up='up', down='down'),
    'west': dict(north='west', south='east', east='north', west='south', up='up', down='down'),
    'up': dict(north='up', south='down', east='east', west='west', up='south', down='north'),
    'down': dict(north='down', south='up', east='east', west='west', up='north', down='south'),
}
ROTATION = {
    'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270},
    'up': {'x': 270}, 'down': {'x': 90},
}
EPSILON = 1e-9


class GeometryError(ValueError):
    pass


class Box:
    def __init__(self, name, start, end, *, emissive=False, step=False, flat=False):
        self.name = name
        self.lo = tuple(float(value) for value in start)
        self.hi = tuple(float(value) for value in end)
        self.emissive = emissive
        # A step may be half a pixel deep; every other box needs a full pixel.
        self.step = step
        # A flat box takes the light of its block without ambient occlusion: lights and showpieces keep
        # their brightness when the machine stands between other blocks.
        self.flat = flat

    def size(self):
        return tuple(self.hi[axis] - self.lo[axis] for axis in range(3))

    def plane(self, face):
        return (self.lo if OUTWARD[face] < 0 else self.hi)[AXIS[face]]

    def rectangle(self, face):
        first, second = (axis for axis in range(3) if axis != AXIS[face])
        return self.lo[first], self.lo[second], self.hi[first], self.hi[second]

    def on_boundary(self, face):
        return self.plane(face) == (0 if OUTWARD[face] < 0 else 16)

    def turned(self, direction):
        """The box after the block state rotation from north to direction."""
        def point(position):
            x, y, z = position
            return {'north': (x, y, z), 'east': (16 - z, y, x), 'south': (16 - x, y, 16 - z),
                    'west': (z, y, 16 - x), 'up': (x, 16 - z, y), 'down': (x, z, 16 - y)}[direction]
        first, second = point(self.lo), point(self.hi)
        return Box(f'{self.name}@{direction}', [min(pair) for pair in zip(first, second)],
                   [max(pair) for pair in zip(first, second)], emissive=self.emissive, step=self.step, flat=self.flat)


def number(value):
    return int(value) if float(value).is_integer() else value


def subtract(rectangle, cut):
    """The parts of a rectangle that a second rectangle leaves uncovered."""
    left, bottom, right, top = rectangle
    cut_left, cut_bottom = max(left, cut[0]), max(bottom, cut[1])
    cut_right, cut_top = min(right, cut[2]), min(top, cut[3])
    if cut_left >= cut_right - EPSILON or cut_bottom >= cut_top - EPSILON:
        return [rectangle]
    pieces = [(left, bottom, cut_left, top), (cut_right, bottom, right, top),
              (cut_left, bottom, cut_right, cut_bottom), (cut_left, cut_top, cut_right, top)]
    return [piece for piece in pieces if piece[2] - piece[0] > EPSILON and piece[3] - piece[1] > EPSILON]


def hidden(box, face, context):
    """Whether other boxes cover the whole face. Faces on the block boundary stay."""
    if box.on_boundary(face):
        return False
    axis, plane = AXIS[face], box.plane(face)
    remaining = [box.rectangle(face)]
    for other in context:
        if other is box:
            continue
        if OUTWARD[face] < 0:
            covers = other.lo[axis] < plane <= other.hi[axis]
        else:
            covers = other.lo[axis] <= plane < other.hi[axis]
        if not covers:
            continue
        cut = other.rectangle(face)
        remaining = [piece for rectangle in remaining for piece in subtract(rectangle, cut)]
        if not remaining:
            return True
    return False


def visible_faces(box, context):
    return [face for face in FACES if not hidden(box, face, context)]


def corner_positions(face, lo, hi):
    return [tuple((lo, hi)[pick][axis] for axis, pick in enumerate(corner)) for corner in CORNERS[face]]


def corner_uvs(uv, rotation):
    """Texture coordinates of the four face corners, as the game assigns them."""
    result = []
    for corner in range(4):
        source = (corner + rotation // 90) % 4
        result.append((uv[0] if source in (0, 1) else uv[2], uv[1] if source in (0, 3) else uv[3]))
    return result


def solve_uv(face, lo, hi, wanted):
    """Find the uv rectangle and rotation that give each face corner its wanted texture coordinate."""
    us = sorted({round(u, 6) for u, _ in wanted})
    vs = sorted({round(v, 6) for _, v in wanted})
    if len(us) != 2 or len(vs) != 2:
        raise GeometryError(f'{face} face needs an axis-aligned texture rectangle')
    for rotation in (0, 90, 180, 270):
        for u_pair in (us, us[::-1]):
            for v_pair in (vs, vs[::-1]):
                uv = (u_pair[0], v_pair[0], u_pair[1], v_pair[1])
                if all(abs(a - c) < 1e-6 and abs(b - d) < 1e-6
                       for (a, b), (c, d) in zip(corner_uvs(uv, rotation), wanted)):
                    return uv, rotation
    raise GeometryError(f'no texture mapping for the {face} face')


def mapped_uv(face, lo, hi, u_axis, v_axis, origin, sprite=32):
    """Lay a sprite region onto a face at exactly two texels per pixel.

    u_axis and v_axis name the model directions in which the sprite's u and v
    grow, for example '-x' and '-y' for a north face seen from the front.
    origin is the texel of the region's top left corner.
    """
    u_index, v_index = 'xyz'.index(u_axis[1]), 'xyz'.index(v_axis[1])
    if u_index == v_index or AXIS[face] in (u_index, v_index):
        raise GeometryError(f'texture axes of the {face} face must span the face')
    positions = corner_positions(face, lo, hi)

    def along(axis, position):
        return sum(a * b for a, b in zip(VECTOR[axis], position))

    low_u = min(along(u_axis, position) for position in positions)
    low_v = min(along(v_axis, position) for position in positions)
    unit = 32 / sprite  # uv units per model pixel at two texels per pixel
    wanted = [((origin[0] / 2 + along(u_axis, position) - low_u) * unit,
               (origin[1] / 2 + along(v_axis, position) - low_v) * unit) for position in positions]
    uv, rotation = solve_uv(face, lo, hi, wanted)
    if min(uv) < -EPSILON or max(uv) > 16 + EPSILON:
        raise GeometryError(f'texture region of the {face} face leaves the sprite')
    return uv, rotation


def element(box, faces):
    """One model element. faces maps a side to its texture key, uv and rotation."""
    result = {'name': box.name, 'from': [number(value) for value in box.lo],
              'to': [number(value) for value in box.hi]}
    if box.emissive:
        result['shade'] = False
        result['light_emission'] = 15
    if box.flat:
        result['neoforge_data'] = {'ambient_occlusion': False}
    result['faces'] = {}
    for face in FACES:
        if face not in faces:
            continue
        data = faces[face]
        entry = {'texture': '#' + data['texture']}
        if 'uv' in data:
            entry['uv'] = [number(round(value, 4)) for value in data['uv']]
        if data.get('rotation'):
            entry['rotation'] = data['rotation']
        if box.on_boundary(face):
            entry['cullface'] = face
        result['faces'][face] = entry
    return result


def build(boxes, skin, context=None):
    """Elements of the boxes with every face that the context leaves visible.

    skin(box, face) returns a texture key, or a (key, uv, rotation) tuple for a
    face with its own sprite region. Faces on the block boundary get a cullface.
    """
    context = list(boxes) if context is None else context
    elements = []
    for box in boxes:
        faces = {}
        for face in visible_faces(box, context):
            texture = skin(box, face)
            if isinstance(texture, tuple):
                key, uv, rotation = texture
                faces[face] = {'texture': key, 'uv': uv, 'rotation': rotation}
            else:
                faces[face] = {'texture': texture}
        if not faces:
            raise GeometryError(f'{box.name} has no visible face')
        elements.append(element(box, faces))
    return elements


def emitted(boxes, elements):
    """Pair each box with the sides its element draws."""
    return [(box, tuple(item['faces'])) for box, item in zip(boxes, elements)]


def check_boxes(label, boxes):
    for box in boxes:
        for value in box.lo + box.hi:
            if abs(value * 2 - round(value * 2)) > EPSILON:
                raise GeometryError(f'{label}: {box.name} leaves the half-pixel grid at {value}')
            if value < 0 or value > 16:
                raise GeometryError(f'{label}: {box.name} leaves the block at {value}')
        size = box.size()
        if min(size) < .5 - EPSILON:
            raise GeometryError(f'{label}: {box.name} is thinner than half a pixel')
        thin = [value for value in size if value < 1 - EPSILON]
        if thin and (not box.step or len(thin) > 1):
            raise GeometryError(f'{label}: {box.name} is a sliver of {size}; only a step may be half a pixel deep')


def check_composition(label, parts, allowed_overlap=()):
    """Reject z-fighting in a set of (box, drawn sides) that the game shows together."""
    for (first, first_faces), (second, second_faces) in combinations(parts, 2):
        for face in set(first_faces) & set(second_faces):
            if abs(first.plane(face) - second.plane(face)) > EPSILON:
                continue
            a, b = first.rectangle(face), second.rectangle(face)
            if min(a[2], b[2]) - max(a[0], b[0]) > EPSILON and min(a[3], b[3]) - max(a[1], b[1]) > EPSILON:
                raise GeometryError(f'{label}: {first.name} and {second.name} share the {face} plane')
        if all(min(first.hi[axis], second.hi[axis]) - max(first.lo[axis], second.lo[axis]) > EPSILON
               for axis in range(3)):
            names = first.name + ' ' + second.name
            if not any(a in names and b in names for a, b in allowed_overlap):
                raise GeometryError(f'{label}: {first.name} and {second.name} intersect')


def turned_parts(parts, direction):
    return [(box.turned(direction), tuple(TURNED[direction][face] for face in faces)) for box, faces in parts]
