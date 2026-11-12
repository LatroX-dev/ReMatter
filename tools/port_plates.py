"""Port plates: the closed sides of a device as models of their own, chosen by the role of the side.

A side that is a connection shows the family port plate: the seat painted on the casing face and a ring that
stands half a pixel proud of it, in the colour of the role. A side that is disabled, or that can never be a
connection, shows the plain casing face and nothing else. The body model of a device therefore leaves out every
closed face that can carry a plate; a block state entry of the type rematter:port_plates lists, per side and
role, the model that draws the face instead, and the game picks one by the roles the block entity publishes.
Plates are ordinary block models in the chunk mesh, so they neither pop in with distance nor flicker.

Which sides can be a connection is decided in PortSides.java; this module mirrors that table for the default
look of the item and reads the one taste switch, SEALED_TOP, from the Java source.
"""
from pathlib import Path
import re

from family_geometry import AXIS, Box, GeometryError, build, check_boxes, mapped_uv
from family_palette import PORT_MODES

ROOT = Path(__file__).resolve().parents[1]
MODEL = 'rematter:block/'
TYPE = 'rematter:port_plates'
# The local sides of a device and the model face each one is, with the front at north and the viewer's left at +X.
FACE = {'front': 'north', 'right': 'west', 'back': 'south', 'left': 'east', 'top': 'up', 'bottom': 'down'}
# The faces a sealed device closes. The front never carries a plate.
CLOSED = ('left', 'right', 'back', 'bottom', 'top')
ROLES = tuple(mode for mode in PORT_MODES if mode != 'disabled')
# Casing families: the sprite of a plain face and the sprite of the same face with the seat painted on it.
CASINGS = {'plain': ('casing_plain', 'casing_port'), 'reactor': ('reactor_casing', 'reactor_casing_port')}
# Bench machines on the alcove chassis: their top is the open working area and never a connection.
OPEN_TOP = ('chemical_reactor', 'analyzer', 'decomposer', 'material_synthesizer', 'material_preparator',
            'hand_preparator', 'sinter_furnace')
# The ring: its outer edge on the face in pixels, one pixel wide, half a pixel proud.
RING = (2, 14)
RISE = .5
AXES = 'xyz'


def sealed_top():
    """The value of PortSides.SEALED_TOP: CONNECTION, OPT_IN or CLOSED."""
    source = (ROOT / 'src/main/java/dev/latrox/rematter/content/PortSides.java').read_text()
    found = re.search(r'SealedTop\s+SEALED_TOP\s*=\s*SealedTop\.(CONNECTION|OPT_IN|CLOSED)\s*;', source)
    if not found:
        raise GeometryError('PortSides.java no longer names SEALED_TOP')
    return found.group(1)


def faces_of(device):
    """The sides whose face a plate model draws instead of the body."""
    return tuple(side for side in CLOSED if not (side == 'top' and device in OPEN_TOP))


def connectable(device):
    """The sides that can be a connection; mirrors PortSides.available."""
    if device == 'matter_terminal':
        return ('back',)
    return tuple(side for side in faces_of(device) if side != 'top' or sealed_top() != 'CLOSED')


def initial(device, side):
    """The role of a side nobody has configured; mirrors PortSides.initial."""
    if side not in connectable(device) or (side == 'top' and sealed_top() == 'OPT_IN'):
        return 'disabled'
    return 'automatic'


def face_slab(side):
    """The casing face of a side as the outer half pixel of the block; only its outer face is drawn."""
    return Box('port_face_' + side, (0, 0, 0), (16, 16, RISE), step=True).turned(FACE[side])


def ring_boxes(side):
    low, high = RING
    canonical = (('top', (low, high - 1), (high, high)), ('bottom', (low, low), (high, low + 1)),
                 ('left', (high - 1, low + 1), (high, high - 1)), ('right', (low, low + 1), (low + 1, high - 1)))
    return [Box(f'port_ring_{name}_{side}', (*start, -RISE), (*end, 0), step=True, proud=True).turned(FACE[side])
            for name, start, end in canonical]


def ring_skin(face):
    """The outer face of the ring samples the Machine Port sprite by position; a wall takes the ring colour."""
    depth = AXIS[face]

    def skin(box, side):
        if side == face:
            return 'ring'
        along = next(axis for axis in range(3) if axis not in (depth, AXIS[side]))
        length = box.hi[along] - box.lo[along]
        # Row 5 of the sprite is the body of the ring; a full-length wall starts on the lit corner texel.
        origin = (4 if length >= RING[1] - RING[0] else 6, 5)
        return ('ring', *mapped_uv(side, box.lo, box.hi, '+' + AXES[along], '+' + AXES[depth], origin))
    return skin


def face_element(side):
    slab, face = face_slab(side), FACE[side]
    number = lambda value: int(value) if float(value).is_integer() else value
    return {'name': 'face', 'from': [number(value) for value in slab.lo], 'to': [number(value) for value in slab.hi],
            'faces': {face: {'texture': '#face', 'cullface': face}}}


def ring_elements(side):
    """The four boxes of the ring. Every face carries the cullface of the side: a solid neighbour hides the ring."""
    face, ring = FACE[side], ring_boxes(side)
    check_boxes('port ring ' + side, ring)
    block = Box('block', (0, 0, 0), (16, 16, 16))
    elements = build(ring, ring_skin(face), ring + [block])
    for element in elements:
        element['name'] = element['name'].split('@')[0]
        for entry in element['faces'].values():
            entry['cullface'] = face
    return ring, elements


def plated():
    """The sides that can carry a plate on some device. A closed sealed top needs none."""
    return tuple(side for side in CLOSED if side != 'top' or sealed_top() != 'CLOSED')


def geometry_models():
    """The parent models of every side: the plain face, and the face with its ring where a plate can be."""
    models = {}
    for side in CLOSED:
        models['port_side_' + side] = {'parent': 'minecraft:block/block', 'textures': {'particle': '#face'},
                                       'elements': [face_element(side)]}
        if side not in plated():
            continue
        ring, elements = ring_elements(side)
        if any(FACE[side] not in element['faces'] for element in elements):
            raise GeometryError(f'port ring {side}: a ring box does not show its outer face')
        models['port_plate_' + side] = {'parent': 'minecraft:block/block', 'textures': {'particle': '#face'},
                                        'elements': [face_element(side)] + elements}
    return models


def blank_model(casing, side):
    return f'port_{casing}_{side}_disabled'


def plate_model(casing, side, role):
    return f'port_{casing}_{side}_{role}'


def add_family_models(family):
    """Every plate of the two casing families: per side one plain face and one plate per role."""
    family.models.update(geometry_models())
    for casing, (plain, seat) in CASINGS.items():
        for side in CLOSED:
            family.child(blank_model(casing, side), 'port_side_' + side, {'face': plain})
            for role in ROLES if side in plated() else ():
                family.child(plate_model(casing, side, role), 'port_plate_' + side,
                             {'face': seat, 'ring': 'machine_port_' + role})


def definition(device, casing='plain'):
    """The block state entry of the plates of one device, without its rotation."""
    roles = ('automatic',) if device == 'matter_terminal' else ROLES
    sides = {}
    for side in faces_of(device):
        sides[side] = {'disabled': MODEL + blank_model(casing, side)}
        if side in connectable(device):
            sides[side].update({role: MODEL + plate_model(casing, side, role) for role in roles})
    return {'type': TYPE, 'default': {side: initial(device, side) for side in sides}, 'sides': sides}


def omitted_faces(device):
    """Model faces the body of the device leaves to its plates."""
    return tuple(FACE[side] for side in faces_of(device))


def default_models(entry):
    """The plate models of an unconfigured device, for its item."""
    shown = []
    for side, models in entry['sides'].items():
        model = models.get(entry['default'].get(side, 'disabled'), models.get('disabled'))
        if model:
            shown.append(model)
    return shown


def referenced(node):
    """Every model a port plate entry below a block state names."""
    if isinstance(node, dict):
        if node.get('type') == TYPE:
            for models in node['sides'].values():
                yield from models.values()
        else:
            for value in node.values():
                yield from referenced(value)
    elif isinstance(node, list):
        for value in node:
            yield from referenced(value)


def parts(device, connected):
    """The plates of a device as (box, drawn faces) for the z-fighting checks: all blank, or all connected."""
    result = []
    for side in faces_of(device):
        result.append((face_slab(side), (FACE[side],)))
        if connected and side in connectable(device):
            ring, elements = ring_elements(side)
            result += [(box, tuple(element['faces'])) for box, element in zip(ring, elements)]
    return result


# The model files this module owns; a generator sweeps the ones a changed SEALED_TOP no longer needs.
OWNED = ('port_side_*.json', 'port_plate_*.json', 'port_plain_*.json', 'port_reactor_*.json')
# A plate face lies in the outer half pixel of the body it closes; that overlap is the design.
OVERLAP = (('port_face_', ''),)
