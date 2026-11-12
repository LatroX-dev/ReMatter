#!/usr/bin/env python3
"""Generate server collision shapes from the same low-part bench geometry as the models."""
from pathlib import Path
import argparse
from bench_showpieces import PARTS, WORK, DEVICES
from generated_files import GeneratedFiles
ROOT=Path(__file__).resolve().parents[1]
KINDS=('ANALYZER','DECOMPOSER','SYNTHESIZER','PREPARATOR','HAND_PREPARATOR','SINTER_FURNACE')
def number(value): return str(int(value)) if float(value).is_integer() else str(value)
def generate(check):
    lines=['package dev.latrox.rematter.content;','','import net.minecraft.world.level.block.Block;','import net.minecraft.world.phys.shapes.Shapes;','import net.minecraft.world.phys.shapes.VoxelShape;','','/** Generated from the same bench mechanisms as the block models. */','final class BenchShapes {','    private BenchShapes() {}','    static VoxelShape showpiece(MachineKind kind){','        return switch(kind){']
    for name,kind in zip(DEVICES,KINDS):
        boxes=[(lo,hi) for _,lo,hi,_ in PARTS[name]]+[(lo,hi) for _,lo,hi in WORK[name]]
        if name=='hand_preparator':boxes.append(((7,8,1.5),(14,15,3))) # swept volume of the rotating crank
        values=['Block.box('+','.join(number(x) for x in (*lo,*hi))+')' for lo,hi in boxes]
        lines.append('            case '+kind+' -> Shapes.or('+',\n                    '.join(values)+');')
    lines+=['            default -> Shapes.empty();','        };','    }','}','']
    out=GeneratedFiles(ROOT,check);out.bytes(ROOT/'src/main/java/dev/latrox/rematter/content/BenchShapes.java','\n'.join(lines).encode())
    print(('Verified' if check else 'Generated')+' server shapes for six bench mechanisms')
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');generate(parser.parse_args().check)
