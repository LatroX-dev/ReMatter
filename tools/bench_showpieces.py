"""Six low-part workshop mechanisms inside the approved full-block alcove.

All shipping boxes stay on the half-pixel grid. Drawn front faces belong to
one material; real items and the small mechanical crank remain renderer-owned.
"""
from family_geometry import Box, mapped_uv
from family_palette import SEAM, STEEL, STEEL_DEEP, STEEL_SHADE, TRIM, WELL, accent, lighten, scale

DEVICES=('analyzer','decomposer','material_synthesizer','material_preparator','hand_preparator','sinter_furnace')
NATURAL={'north':('-x','-y'),'south':('+x','-y'),'west':('+z','-y'),'east':('-z','-y'),'up':('+x','+z'),'down':('+x','-z')}
# (name, lower corner, upper corner, material). Exactly one physical purpose per large silhouette.
PARTS={
 'analyzer':(
  ('scan_plate',(4,4,3),(12,6,8),'plate'),
  ('scan_support_left',(4,6,8),(6,13,10),'metal'),
  ('scan_support_right',(10,6,8),(12,13,10),'metal'),
  ('scan_head',(4,13,5),(12,15,10),'metal')),
 'decomposer':(
  ('crusher_tray',(3,4,2),(13,6,8),'plate'),
  ('crusher_jaw_left',(3,7,3),(6,12,8),'metal'),
  ('crusher_jaw_right',(10,7,3),(13,12,8),'metal'),
  ('crusher_bridge',(3,12,8),(13,14,10),'dark'),
  ('crusher_mount_left',(3,8,8),(6,12,10),'dark'),
  ('crusher_mount_right',(10,8,8),(13,12,10),'dark')),
 'material_synthesizer':(
  ('growth_tray',(3,4,2),(13,6,9),'plate'),
  ('growth_lip_left',(3,6,2),(4,8,9),'metal'),
  ('growth_lip_right',(12,6,2),(13,8,9),'metal'),
  ('growth_focus',(6,10,8),(10,14,10),'metal')),
 'material_preparator':(
  ('prep_tray',(3,4,2),(13,6,8),'plate'),
  ('grinding_head',(3,9,4),(7,14,7),'metal'),
  ('grinding_mount',(4,10,7),(6,12,10),'dark'),
  ('mixing_wall_left',(9,6,3),(10,9,8),'metal'),
  ('mixing_wall_right',(12,6,3),(13,9,8),'metal'),
  ('mixing_wall_back',(10,6,7),(12,9,8),'metal'),
  ('mixing_floor',(10,6,3),(12,7,7),'plate')),
 'hand_preparator':(
  ('hand_mortar_floor',(3,4,3),(9,5,8),'plate'),
  ('hand_mortar_left',(3,5,3),(4,8,8),'metal'),
  ('hand_mortar_right',(8,5,3),(9,8,8),'metal'),
  ('hand_mortar_back',(4,5,7),(8,8,8),'metal'),
  ('hand_crank_mount',(9.5,10.5,3),(11.5,12.5,10),'dark'),
  ('hand_drive_support',(10,4,8),(12,10.5,10),'metal')),
 'sinter_furnace':(
  ('oven_floor',(3,4,2),(13,6,9),'plate'),
  ('oven_lining_left',(3,6,2),(5,12,9),'metal'),
  ('oven_lining_right',(11,6,2),(13,12,9),'metal'),
  ('oven_roof',(3,12,2),(13,14,9),'metal')),
}
# A colored functional inset changes with the public active state; no extra decorative fins.
WORK={
 'analyzer':(('scan_lens',(6,12,5),(10,13,8)),),
 'decomposer':(('crusher_contact_left',(3,8,2.5),(6,11,3)),('crusher_contact_right',(10,8,2.5),(13,11,3))),
 'material_synthesizer':(('growth_seed_left',(4,6,4),(6,7,6)),('growth_seed_right',(10,6,4),(12,7,6))),
 'material_preparator':(('grinding_signal',(4,10,3.5),(6,13,4)),),
 'hand_preparator':(),
 'sinter_furnace':(('oven_heat',(5,6,8),(11,12,10)),),
}

def color(device): return accent('material_preparator' if device=='hand_preparator' else device)
def boxes(device): return [Box(name,lo,hi,flat=True) for name,lo,hi,_ in PARTS[device]]
def work_boxes(device):
 return [Box(name,lo,hi,step=any(b-a==.5 for a,b in zip(lo,hi)),flat=True) for name,lo,hi in WORK[device]]
def materials(device): return {name:material for name,_,_,material in PARTS[device]}
def show_skin(device,box,face):
 return materials(device)[box.name],*mapped_uv(face,box.lo,box.hi,*NATURAL[face],(0,0))
def work_skin(box,face): return 'work',*mapped_uv(face,box.lo,box.hi,*NATURAL[face],(0,0))

def crank_boxes():
 # Rest pose of the renderer-owned crank, centered10.5/11.5/2.5; rotates only around front-normal Z.
 return [Box('bench_crank_arm',(10,11,2),(11,13.5,3),flat=True),
         Box('bench_crank_grip',(9.5,13.5,1.5),(11.5,14.5,2.5),flat=True)]

def bench_sprites(Sprite):
 result={}
 # Broad hard clusters, all within the same approved grey/steel palette.
 for name,base,light,dark in (('metal',STEEL,TRIM,STEEL_SHADE),('dark',STEEL_DEEP,STEEL_SHADE,SEAM)):
  sprite=Sprite(fill=base)
  for y in range(0,32,8):
   sprite.row(y,0,32,light);sprite.row(y+7,0,32,dark)
  for x in (0,15,31):sprite.col(x,0,32,dark)
  result['bench_'+name]=sprite
 for device in DEVICES:
  c=color(device);plate=Sprite(fill=STEEL_DEEP)
  for y in range(1,32,4):plate.row(y,1,31,SEAM)
  plate.ring(0,0,32,32,STEEL_SHADE)
  # A readable two-pixel accent belongs to the working bed, never the casing.
  plate.row(2,2,30,scale(c,.62));result['bench_'+device+'_plate']=plate
  if WORK[device]:
   for active in (False,True):
    tone=c if active else scale(c,.38);sprite=Sprite(fill=tone)
    for y in range(1,32,4):sprite.row(y,0,32,lighten(tone,.22))
    result['bench_'+device+('_work_active' if active else '_work')]=sprite
 return result
