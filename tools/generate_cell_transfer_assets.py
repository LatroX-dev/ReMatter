#!/usr/bin/env python3
"""Generate the unpowered six-bay Cell Transfer Station and its independent crafting recipe.

CONSOLE_LOOK selects a twin three-bay console (recommended), a continuous service rail or separate recessed panels.
All three keep the same twelve family-grid boxes and the same honest empty bays. Three source bays are on the viewer's left,
three target bays on the right. The Mint recipe crystal sets the identity; there is no energy/network connector or active
state, and no stored cell is painted into the item model.
"""
from pathlib import Path
import argparse
from family_geometry import Box, check_boxes
from family_palette import (BASE, COPPER, COPPER_LIGHT, HIGHLIGHT, IVORY, PORT_DARK, PORT_LIGHT, PORT_METAL,
                            SEAM, SHADE, STEEL_SHADE, WELL)
from family_sprites import DRIVE_BAYS, Sprite, sealed_frame, plinth, bolt
from generate_family_assets import Family, HORIZONTAL, ROTATION, MODEL, sealed_body, sealed_skin, drive_cell_box, drive_led_box
from generated_files import GeneratedFiles
from machine_palette import RGB
from recipe_discovery import shaped_discovery

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'src/main/resources/assets/rematter'
DATA=ROOT/'src/main/resources/data/rematter'
NAME='cell_transfer_station'
CONSOLE_LOOKS=('twin_three_bay','continuous_rail','recessed_panels')
CONSOLE_LOOK='twin_three_bay'
PATTERN=['SMS','CFC','SBS']
KEYS={'S':'rematter:structural_bars','M':'rematter:mint_matrix_crystal','C':'rematter:control_circuit',
      'F':'rematter:machine_frame','B':'rematter:cell_housing'}


def front(look=CONSOLE_LOOK):
    if look not in CONSOLE_LOOKS:raise ValueError(f'Unknown console look: {look}')
    image=sealed_frame(BASE)
    plinth(image,28)
    image.row(1,3,29,RGB['mint'])
    # The faceplate follows the Drive bay coordinates, but its three arrows distinguish two transfer groups.
    image.rect(14,2,18,28,BASE);image.col(14,2,28,HIGHLIGHT);image.col(17,2,28,SHADE)
    if look=='continuous_rail':
        for x in (2,18):
            image.rect(x,5,x+12,28,PORT_DARK)
            image.col(x,5,28,PORT_LIGHT)
    elif look=='recessed_panels':
        for x in (2,18):
            image.rect(x,2,x+12,28,SEAM)
            image.row(2,x,x+12,PORT_METAL)
    for x,y in DRIVE_BAYS:
        image.ring(x,y,x+12,y+6,PORT_METAL)
        image.row(y,x,x+12,PORT_LIGHT)
        image.row(y+5,x,x+12,SEAM)
        image.rect(x+1,y+1,x+11,y+5,WELL)
    for x in (4,20):
        image.row(3,x,x+8,RGB['mint'])
    for y in (9,17,25):
        image.row(y,14,17,IVORY);image.px(16,y-1,IVORY);image.px(17,y,IVORY);image.px(16,y+1,IVORY)
    return image


def inner():
    image=Sprite(fill=SHADE)
    for x,y in DRIVE_BAYS:
        image.rect(x,y,x+12,y+6,WELL)
        image.row(y,x+1,x+11,PORT_DARK)
        # These fixed fingers are at the back of the empty bay, not an inserted Cell faceplate.
        for finger in (x+3,x+7):
            image.rect(finger,y+4,finger+2,y+6,COPPER)
            image.row(y+4,finger,finger+2,COPPER_LIGHT)
    return image


def geometry():
    # Exactly the Drive's six cartridge openings. Viewer-left has high x and is the source group.
    body=sealed_body(2)+[Box('mullion',(7,2,0),(9,15,2))]
    for x in (1,9):
        body.append(Box(f'header_{x}',(x,13,.5),(x+6,15,2)))
        for y in (5,9):body.append(Box(f'rail_{x}_{y}',(x,y,0),(x+6,y+1,2)))
    return body


def assets(look=CONSOLE_LOOK):
    family=Family();body=geometry()
    textures={'front':NAME+'_front','plain':'casing_plain','inner':NAME+'_inner'}
    shown=family.model(NAME,'network',textures,body,sealed_skin({face:'plain' for face in ('south','up','down','east','west')}),particle='casing_plain')
    family.compose(NAME,shown)
    mounted=[(box,faces) for bay in range(6) for box,faces in (
        (drive_cell_box(bay),('north',)),(drive_led_box(bay),('north','east','west','up','down')))]
    check_boxes(NAME+' mounted cells',[box for box,_ in mounted])
    family.compose(NAME+' full',shown+mounted)
    family.blockstates[NAME]={'variants':{f'facing={facing}':{'model':MODEL+NAME,**ROTATION[facing]} for facing in HORIZONTAL}}
    family.items[NAME]={'model':{'type':'minecraft:model','model':MODEL+NAME}}
    return family,{NAME+'_front':front(look),NAME+'_inner':inner()}


def generate(check=False):
    for item in KEYS.values():
        if not (ART/f'items/{item.split(":")[1]}.json').is_file():raise ValueError(f'Unknown ingredient asset: {item}')
    family,art=assets();out=GeneratedFiles(ROOT,check)
    for name,sprite in art.items():out.png(ART/f'textures/block/{name}.png',sprite.width,sprite.height,sprite.pixel)
    for folder,files in (('models/block',family.models),('blockstates',family.blockstates),('items',family.items)):
        for name,value in files.items():out.json(ART/folder/f'{name}.json',value)
    out.json(DATA/f'recipe/{NAME}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':PATTERN,'key':KEYS,'result':{'id':'rematter:'+NAME,'count':1}})
    out.json(DATA/f'advancement/recipes/{NAME}.json',shaped_discovery(NAME,PATTERN,KEYS))
    out.json(DATA/f'loot_table/blocks/{NAME}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'rematter:'+NAME}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    print(f"{'Verified' if check else 'Generated'} {out.count} Cell Transfer Station files; twelve boxes, six bays, four facings ({CONSOLE_LOOK})")


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');generate(parser.parse_args().check)
