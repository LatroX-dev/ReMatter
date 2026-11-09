"""Fit Blockbench-authored service frames into the authored machine housings."""
import copy
import json
from pathlib import Path

ARTWORK = Path(__file__).resolve().parents[1] / 'artwork/machines'
DIRECTIONS = ('east', 'west', 'south', 'down')


def authored(name):
    return json.loads((ARTWORK / (name + '.json')).read_text())


def subtract(element, opening):
    """Carve an axis-aligned socket well, retaining the housing's face materials."""
    a, b = element['from'], element['to']
    lo = [max(a[i], opening[0][i]) for i in range(3)]
    hi = [min(b[i], opening[1][i]) for i in range(3)]
    if any(lo[i] >= hi[i] for i in range(3)):
        return [element]
    # Authored rotated details are kept intact; all service openings lie in the chassis.
    if element.get('rotation', {}).get('angle', 0):
        raise ValueError('Socket intersects a rotated detail: ' + element.get('name', '?'))
    pieces = []
    start, end = list(a), list(b)
    for axis in range(3):
        if start[axis] < lo[axis]:
            edge = end.copy()
            edge[axis] = lo[axis]
            pieces.append((start.copy(), edge))
            start[axis] = lo[axis]
        if end[axis] > hi[axis]:
            edge = start.copy()
            edge[axis] = hi[axis]
            pieces.append((edge, end.copy()))
            end[axis] = hi[axis]
    return [dict(copy.deepcopy(element), name=element.get('name', 'housing') + f'_cut_{i}',
                 **{'from': start, 'to': end}) for i, (start, end) in enumerate(pieces)]


def bezel(*, rack=False):
    """Use a short recessed frame on a closed panel or in a machine's base."""
    result = []
    for element in authored('service_frame')['elements']:
        if element['name'] in ('base_tie', 'roof_tie', 'left_face_trim', 'right_face_trim'):
            continue
        element['from'][1] = max(5.15, element['from'][1])
        element['to'][1] = min(10.85, element['to'][1])
        # A rack's side wall is one pixel deep, keeping the six cartridge bays clear.
        for point in ('from', 'to'):
            z = element[point][2]
            element[point][2] = round((z - 1.48) * (.43 if rack else 1) + .04, 5)
        result.append(element)
    return result


def machine_housing(name):
    # Import here to keep the shared coordinate transform in one place.
    from connection_geometry import rotate
    model = authored(name)
    rack = name == 'matter_drive'
    directions = ('south',) if name == 'matter_terminal' else DIRECTIONS
    frames = []
    for direction in directions:
        # Angled vessel shells keep their authored shape; their well ends before the shell.
        shallow_vessel = name in ('decomposer', 'electrolyzer') and direction != 'down'
        inset = 1.05 if rack else 2 if shallow_vessel else 3.5
        opening, = rotate([{'from': [5.85, 5.85, 0], 'to': [10.15, 10.15, inset]}], direction)
        elements = []
        for element in model['elements']:
            elements.extend(subtract(element, (opening['from'], opening['to'])))
        model['elements'] = elements
        frame = (bezel(rack=rack) if rack or direction == 'down' or name == 'sinter_furnace'
                 else authored('service_frame')['elements'])
        if name == 'material_synthesizer' and direction != 'down':
            # End at the crystal tips rather than adding an empty hoop above the pedestals.
            frame = [element for element in frame if element['from'][1] < 10.85]
            for element in frame:
                element['to'][1] = min(element['to'][1], 10.85)
        if shallow_vessel:
            for element in frame:
                if element['name'] == 'socket_recess':
                    element['from'][2], element['to'][2] = 1.95, 2
                elif element['name'] == 'socket_contact':
                    element['from'][2], element['to'][2] = 1.90, 1.96
        for element in rotate(frame, direction):
            element['name'] = direction + '_' + element['name']
            frames.append(element)
    accent = model['textures']['teal']
    model['textures'].update(authored('service_frame')['textures'])
    model['textures']['teal'] = accent
    model['elements'].extend(frames)
    return model
