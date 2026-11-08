"""Orthographic component artwork with dark contours and material gradients."""
from pathlib import Path
import math

from machine_palette import CHASSIS, COPPER_CONTACT, MATTE_TRIM, RECESS, hex_color

ROOT = Path(__file__).resolve().parents[1]


def mix(first, second, amount):
    return hex_color(tuple(round(start + (end - start) * amount) for start, end in zip(first, second)))


LIGHT = (231, 237, 224)
P = {
    'ink': '#161b22', 'dark': mix(RECESS, CHASSIS, .45),
    'body': hex_color(CHASSIS), 'top': hex_color(MATTE_TRIM),
    'edge': mix(MATTE_TRIM, LIGHT, .32), 'steel': mix(MATTE_TRIM, LIGHT, .58),
    'shadow': mix(RECESS, CHASSIS, .18),
    'copper': hex_color(COPPER_CONTACT), 'copper_light': mix(COPPER_CONTACT, (243, 196, 148), .68),
    'copper_dark': mix(COPPER_CONTACT, RECESS, .38),
    'diamond': '#62b9cc', 'diamond_light': '#a7e3ed', 'diamond_dark': '#2b6579',
    'ceramic': '#c8c3ad', 'silith': '#decb9e', 'argil': '#7d8aa3',
    'rhevon': '#77666d', 'virelite': '#bc9fa0',
    'redstone': '#c4514d', 'rhedon': '#c45357', 'lumivex': '#d59256',
    'gold': '#d7a956', 'ender': '#439f88', 'netherite': '#695763',
    'leather': '#795239', 'paper': '#e1d7b5', 'paper_dark': '#aa9d81',
    'glass': '#b8cbcf', 'white': hex_color(LIGHT),
}

# Neutral metal has a wide light shoulder; colorants are reserved for materials.
GRADIENTS = {
    'top': ('#a8afb1', '#64747c', '#3c4952'),
    'body': ('#697881', '#43515a', '#292f39'),
    'dark': ('#53606b', '#323b46', '#252933'),
    'shadow': ('#39434c', '#2a303b', '#1c222b'),
    'steel': ('#e0e2de', '#9daab0', '#61707d'),
    'edge': ('#ccd0c9', '#a6b1b5', '#667683'),
    'copper': ('#efac73', '#c17b4e', '#86462e'),
    'copper_light': ('#f9d3a4', '#e5a371', '#ba7046'),
    'copper_dark': ('#b47150', '#925138', '#663931'),
    'gold': ('#f7d985', '#d8a752', '#976333'),
    'diamond': ('#9adddf', '#60b8cc', '#2d7e9a'),
    'diamond_light': ('#dcf4ee', '#9adddf', '#67bace'),
    'diamond_dark': ('#63aec4', '#36798d', '#255466'),
    'ceramic': ('#ece6ce', '#c8c6b1', '#8a9088'),
    'silith': ('#eee3c2', '#d5c192', '#ab9167'),
    'argil': ('#c5ced5', '#7d8aa3', '#4b5a73'),
    'rhevon': ('#8f8285', '#685862', '#392f3b'),
    'virelite': ('#f0dbce', '#bc9fa0', '#826b76'),
    'redstone': ('#ed8667', '#be4c47', '#81333a'),
    'rhedon': ('#ef927a', '#be4c52', '#802c42'),
    'lumivex': ('#f8c47d', '#d99252', '#a65f36'),
    'ender': ('#a3deba', '#62aa92', '#316e68'),
    'netherite': ('#a28c91', '#715f68', '#433c48'),
    'leather': ('#a87650', '#7b543b', '#523928'),
    'paper': ('#f4eaca', '#dfd3b1', '#ad9872'),
    'glass': ('#eaf3ed', '#c3d4d5', '#7e9c9e'),
}

def iso(x,y,z=0):
    return ((x-y)*.8660254, (x+y)*.5-z)

class Drawing:
    def __init__(self):
        self.parts=[]
        self.points=[]
        self.gradients = set()

    @staticmethod
    def color(value):
        return P.get(value,value)

    def fill(self, value):
        if value in GRADIENTS:
            self.gradients.add(value)
            return f'url(#shade-{value})'
        return self.color(value)

    def polygon(self, points, fill, stroke='ink', width=.65):
        points=list(points); self.points.extend(points)
        values=' '.join(f'{x:.4f},{y:.4f}' for x,y in points)
        self.parts.append(f'<polygon points="{values}" fill="{self.fill(fill)}" stroke="{self.color(stroke)}" stroke-width="{width}" stroke-linejoin="round"/>')

    def cutout(self, outer, holes, fill, stroke='ink', width=.65):
        contours = (outer, *holes)
        for contour in contours:
            self.points.extend(contour)
        path = ' '.join('M ' + ' L '.join(f'{x:g},{y:g}' for x, y in contour) + ' Z'
                        for contour in contours)
        self.parts.append(f'<path d="{path}" fill="{self.fill(fill)}" fill-rule="evenodd" '
                          f'stroke="{self.color(stroke)}" stroke-width="{width}" stroke-linejoin="round"/>')

    def line(self, points, stroke='edge', width=.55):
        points=list(points); self.points.extend(points)
        values=' '.join(f'{x:.4f},{y:.4f}' for x,y in points)
        self.parts.append(f'<polyline points="{values}" fill="none" stroke="{self.color(stroke)}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>')

    def face(self, points, fill, stroke='ink', width=.65):
        self.polygon([iso(*point) for point in points],fill,stroke,width)

    def path3(self, points, stroke='edge', width=.55):
        self.line([iso(*point) for point in points],stroke,width)

    def box(self,x,y,z,w,d,h,top='top',left='body',right='dark',stroke='ink',width=.65):
        # Far faces stay hidden: the right and left sides share the same front ridge.
        self.face([(x,y+d,z),(x+w,y+d,z),(x+w,y+d,z+h),(x,y+d,z+h)],left,stroke,width)
        self.face([(x+w,y,z),(x+w,y+d,z),(x+w,y+d,z+h),(x+w,y,z+h)],right,stroke,width)
        self.face([(x,y,z+h),(x+w,y,z+h),(x+w,y+d,z+h),(x,y+d,z+h)],top,stroke,width)

    def top_polygon(self, points, z, fill,stroke='ink',width=.65):
        self.face([(x,y,z) for x,y in points],fill,stroke,width)

    def top_line(self,points,z,stroke='edge',width=.55):
        self.path3([(x,y,z) for x,y in points],stroke,width)

    def octagon(self,x,y,z,r,fill='teal',stroke='ink',width=.65):
        self.top_polygon([(x+r*math.cos(math.pi/8+i*math.pi/4),y+r*math.sin(math.pi/8+i*math.pi/4)) for i in range(8)],z,fill,stroke,width)

    def svg(self):
        xs,ys=zip(*self.points)
        side=max(max(xs)-min(xs),max(ys)-min(ys))+6
        x=(max(xs)+min(xs)-side)/2;y=(max(ys)+min(ys)-side)/2
        definitions = []
        for name in sorted(self.gradients):
            colors = GRADIENTS[name]
            stops = ''.join(f'<stop offset="{offset}" stop-color="{color}"/>'
                            for offset, color in zip(('0', '.55', '1'), colors))
            definitions.append(f'<linearGradient id="shade-{name}" x1="0%" y1="0%" x2="75%" y2="100%">'
                               f'{stops}</linearGradient>')
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="{x:.5f} {y:.5f} {side:.5f} {side:.5f}">'
                + '<defs>' + ''.join(definitions) + '</defs>' + ''.join(self.parts) + '</svg>\n')

    def write(self,name):
        import cairosvg
        source=ROOT/'artwork/components'/f'{name}.svg'
        target=ROOT/'src/main/resources/assets/rematter/textures/item'/f'{name}.png'
        source.parent.mkdir(parents=True,exist_ok=True)
        source.write_text(self.svg())
        cairosvg.svg2png(bytestring=source.read_bytes(),write_to=str(target),output_width=128,output_height=128)

