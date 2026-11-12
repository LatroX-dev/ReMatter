"""One source for renderer hardware, in model pixels; generated Java is never edited by hand.

Rest and extreme stroke poses are checked against the same baked geometry as the block generator.
Faces touching a root are omitted by the renderer, just as hidden faces of baked boxes are omitted.
"""
from pathlib import Path
from family_geometry import Box, check_boxes, check_composition, build, emitted, FACES

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'src/main/java/dev/latrox/rematter/client/anim/RendererParts.java'
# name: (lo, hi); the rest pose is a real, visible part of the mechanism.
PARTS = {
 'STATUS': ((11,1.5,.5),(13,2.5,1)),
 'RAM_RIGHT': ((6,8,4),(6.5,11,7)),
 'RAM_LEFT': ((9.5,8,4),(10,11,7)),
 'SCAN_FRONT': ((4.5,10.5,3.5),(11.5,11.5,4.5)),
 'SCAN_BACK': ((4.5,10.5,6.5),(11.5,11.5,7.5)),
 'SCAN_RIGHT': ((4.5,10.5,4.5),(5.5,11.5,6.5)),
 'SCAN_LEFT': ((10.5,10.5,4.5),(11.5,11.5,6.5)),
 'PESTLE': ((4,8.5,4.5),(6,9,6.5)),
 'MIX_CHARGE': ((10,7,3),(12,8.5,7)),
 'LIQUID_RIGHT': ((3,7,3),(6,9,8)),
 'LIQUID_LEFT': ((10,7,3),(13,9,8)),
 'GLASS_RIGHT': ((3,9,3),(6,10,8)),
 'GLASS_LEFT': ((10,9,3),(13,10,8)),
 'SHUTTLE': ((7,13.5,3.5),(9,14.5,4)),
 'HAND_ARM': ((10,11,2),(11,13.5,3)),
 'HAND_GRIP': ((9.5,13.5,1.5),(11.5,14.5,2.5)),
 'MODULE_BLADE': ((5,7,7.5),(11,11,8.5)),
 'MODULE_EDGE': ((5,6,7.5),(11,7,8.5)),
 'MODULE_PADDLE': ((4,6,7.5),(12,7,8.5)),
 'FIELD_OUTER_FRONT': ((-5.5,-.5,-5.5),(5.5,.5,-4.5)),
 'FIELD_OUTER_BACK': ((-5.5,-.5,4.5),(5.5,.5,5.5)),
 'FIELD_OUTER_RIGHT': ((-5.5,-.5,-4.5),(-4.5,.5,4.5)),
 'FIELD_OUTER_LEFT': ((4.5,-.5,-4.5),(5.5,.5,4.5)),
 'FIELD_INNER_FRONT': ((-3,-.5,-3),(3,.5,-2)),
 'FIELD_INNER_BACK': ((-3,-.5,2),(3,.5,3)),
 'FIELD_INNER_RIGHT': ((-3,-.5,-2),(-2,.5,2)),
 'FIELD_INNER_LEFT': ((2,-.5,-2),(3,.5,2)),
 'FIELD_CORE': ((-1,-1,-1),(1,1,1)),
}
BENCH_PARTS = {'decomposer':('RAM_RIGHT','RAM_LEFT'), 'analyzer':('SCAN_FRONT','SCAN_BACK','SCAN_RIGHT','SCAN_LEFT'),
 'material_preparator':('PESTLE',), 'chemical_reactor':('LIQUID_RIGHT','LIQUID_LEFT','GLASS_RIGHT','GLASS_LEFT','SHUTTLE')}

def box(name,lo=None,hi=None):
 a,b=PARTS[name];return Box(name,lo or a,hi or b,step=min(y-x for x,y in zip(lo or a,hi or b))<1,flat=True)

def poses(device):
 rest=[box(n) for n in BENCH_PARTS.get(device,())]
 yield 'rest',rest
 for end in (-1,1):
  current=[]
  for p in rest:
   lo,hi=list(p.lo),list(p.hi)
   if p.name=='RAM_RIGHT':hi[0]+=1 if end>0 else 0
   elif p.name=='RAM_LEFT':lo[0]-=1 if end>0 else 0
   elif p.name.startswith('SCAN_'):
    for a in (lo,hi):a[1]-=4 if end>0 else 0
   elif p.name=='PESTLE':lo[1]-=1.5 if end>0 else 0
   elif p.name.startswith('LIQUID_'):hi[1]+=end*(1 if p.name.endswith('RIGHT') else -1)
   elif p.name.startswith('GLASS_'):lo[1]+=end*(1 if p.name.endswith('RIGHT') else -1)
   elif p.name=='SHUTTLE':lo[0]+=3*end;hi[0]+=3*end
   # A fully filled sight glass has no empty region to draw.
   if all(b-a>0 for a,b in zip(lo,hi)):current.append(box(p.name,lo,hi))
  yield 'stroke_'+str(end),current

def validate():
 from bench_showpieces import boxes as bench_boxes,work_boxes
 from reactor_showpiece import column_boxes
 # Check internal hardware and every emitted face of the stationary showpiece. Casing is outside its swept niche.
 count=0
 for device in BENCH_PARTS:
  from generate_family_assets import chassis
  baked=chassis()+(column_boxes() if device=='chemical_reactor' else bench_boxes(device)+work_boxes(device))
  drawn=emitted(baked,build(baked,lambda b,f:'texture',baked))
  for name,parts in poses(device):
   check_boxes(device+' '+name,parts)
   # Reactor liquid and upper glass touch but do not emit their common horizontal face.
   moving=[(p,tuple(f for f in FACES if not (device=='chemical_reactor' and (p.name.startswith('LIQUID_') or p.name.startswith('GLASS_')) and f in ('up','down')))) for p in parts]
   check_composition(device+' renderer '+name,drawn+moving)
   count+=1
 # The moving module parts use the same floor and ceiling geometry as their baked counterparts.
 from generate_module_assets import BODY,WORK,mirrored,PLATE,COLLAR
 for device,names in {'grinding_unit':('MODULE_BLADE','MODULE_EDGE'),'mixing_basin':('MODULE_PADDLE',)}.items():
  static=[Box(PLATE[0],*PLATE[1:]),Box(COLLAR[0],*COLLAR[1:],flat=True)]
  static += [Box(name,lo,hi,step=min(b-a for a,b in zip(lo,hi))<1,flat=True) for name,lo,hi,_,_ in BODY[device] if name not in ('blade','paddle')]
  static += [Box(name,lo,hi,step=min(b-a for a,b in zip(lo,hi))<1,flat=True) for name,lo,hi,_ in WORK[device] if name!='cutting_edge']
  for suspended in (False,True):
   for stroke in (0,1):
    baked=[Box(b.name,*(mirrored(b.lo,b.hi) if suspended else (b.lo,b.hi)),step=b.step,flat=True) for b in static]
    drawn=emitted(baked,build(baked,lambda b,f:'texture',baked))
    moving=[]
    for name in names:
     a=box(name);lo,hi=list(a.lo),list(a.hi)
     if name=='MODULE_BLADE':lo[1]-=stroke
     elif name=='MODULE_EDGE':lo[1]-=stroke;hi[1]-=stroke
     if suspended:lo,hi=mirrored(lo,hi)
     b=box(name,lo,hi)
     omitted='down' if suspended else 'up'
     moving.append((b,tuple(f for f in FACES if f!=omitted)))
    check_boxes(device+' renderer',baked+[b for b,_ in moving])
    check_composition(device+f' renderer suspended={suspended} stroke={stroke}',drawn+moving)
    count+=1
 return count

def java_source():
 lines=['package dev.latrox.rematter.client.anim;','','/** Generated by tools/renderer_parts.py; model-pixel coordinates. */','public final class RendererParts {',
        '    public record Part(float x0,float y0,float z0,float x1,float y1,float z1) {}']
 from reactor_showpiece import REACTOR_LIQUID
 lines.append('    public static final boolean REACTOR_LIQUID_RENDERER = '+str(REACTOR_LIQUID=='renderer').lower()+';')
 for name,(lo,hi) in PARTS.items():
  args=','.join(f'{v:g}F' if v%1 else f'{int(v)}F' for v in (*lo,*hi))
  lines.append(f'    public static final Part {name} = new Part({args});')
 lines+=['    private RendererParts() {}','}']
 return '\n'.join(lines)+'\n'

def generate(check=False):
 count=validate();text=java_source()
 if check:
  if not JAVA.is_file() or JAVA.read_text()!=text:raise ValueError('RendererParts.java is stale; run python3 tools/renderer_parts.py')
 else:JAVA.parent.mkdir(parents=True,exist_ok=True);JAVA.write_text(text)
 print(f'{"Checked" if check else "Generated"} renderer hardware; {count} baked/renderer pose compositions')

if __name__=='__main__':
 import argparse
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');generate(p.parse_args().check)
