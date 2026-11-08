#!/usr/bin/env python3
"""Draw the flat inventory icons for storage accessories and the guide."""
from component_drawing import Drawing

def book():
    d=Drawing()
    d.box(0,0,0,23,29,1,top='leather',left='copper_dark',right='shadow')
    d.box(1,.8,1,21.5,27.3,3,top='paper',left='paper',right='paper_dark',width=.5)
    # Page edges follow the very same projection as the cover.
    for z in (1.8,2.7,3.5):
        d.path3([(1.3,28.1,z),(22.4,28.1,z),(22.4,1.4,z)],'paper_dark',.28)
    d.box(0,0,4.1,23,29,1.2,top='leather',left='copper_dark',right='shadow')
    d.top_polygon([(0,0),(3.3,0),(3.3,29),(0,29)],5.32,'leather',width=.5)
    d.top_line([(4.5,2),(21,2),(21,27),(4.5,27)],5.35,'silith',.62)
    # Simple matter-network emblem; no pseudo-lettering, gems or jewellery.
    nodes=((12,8),(7.2,17.5),(17,17.5))
    d.top_line([nodes[0],nodes[1],nodes[2],nodes[0]],5.4,'silith',1.1)
    for x,y in nodes:d.octagon(x,y,5.45,2.3,'silith',width=.45)
    d.top_polygon([(15,25),(18,25),(18,30.5),(16.5,29.4),(15,30.5)],5.5,'paper',width=.5)
    return d

def recovered():
    d=Drawing()
    # The contents stay enclosed: a small engineered recovery canister.
    d.box(0,0,0,12,12,2.3,top='top',left='body',right='dark')
    d.box(1,1,2.3,10,10,19,top='body',left='body',right='dark')
    d.face([(2.2,11,5),(8.8,11,5),(8.8,11,18.5),(2.2,11,18.5)],'ink','edge',.4)
    # Glass reads consistently even when the recovered contents vary.
    d.face([(3.3,11.02,6.2),(7.7,11.02,6.2),(7.7,11.02,14),(3.3,11.02,14)],'glass',width=.3)
    d.path3([(4.05,11.04,7.7),(4.05,11.04,12.8),(6.3,11.04,12.8)],'white',.42)
    d.box(0,0,21.3,12,12,2,top='top',left='body',right='dark')
    d.box(2.7,3,23.3,6.6,6,2.2,top='copper_light',left='copper',right='copper_dark',width=.5)
    return d

def rack():
    d=Drawing()
    # A stateless three-bay carrier: three separate rails, one rear control board.
    d.box(0,0,0,20,26,2,top='dark',left='body',right='shadow')
    d.box(1,0,2,18,2.2,3,top='argil',left='dark',right='shadow',width=.5)
    d.top_line([(4,1.1),(16,1.1)],5.05,'redstone',.6)
    for row in range(3):
        y=4+row*7
        d.box(1,y,2,18,1.5,2.3,top='top',left='body',right='dark',width=.45)
        d.top_polygon([(3,y+.2),(15.3,y+.2),(15.3,y+1.2),(3,y+1.2)],4.35,'ink',width=.25)
        d.top_line([(3.4,y+1.1),(15.1,y+1.1)],4.36,'silith',.62)
        d.box(16,y+.15,4.3,1.6,1.2,.6,top='copper_light',left='copper',right='copper_dark',width=.25)
    d.box(0,1,2,1.3,24,3,top='edge',left='body',right='dark',width=.45)
    d.box(18.7,1,2,1.3,24,3,top='top',left='body',right='dark',width=.45)
    return d

def main():
    book().write('guide')
    recovered().write('recovered_matter')
    rack().write('cell_rack')
    print('Drew 3 storage and book sprites from fixed geometry.')

if __name__=='__main__':main()
