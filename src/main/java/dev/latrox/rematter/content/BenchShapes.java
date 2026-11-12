package dev.latrox.rematter.content;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Generated from the same bench mechanisms as the block models. */
final class BenchShapes {
    private BenchShapes() {}
    static VoxelShape showpiece(MachineKind kind){
        return switch(kind){
            case ANALYZER -> Shapes.or(Block.box(4,4,3,12,6,8),
                    Block.box(4,6,8,6,13,10),
                    Block.box(10,6,8,12,13,10),
                    Block.box(4,13,5,12,15,10),
                    Block.box(6,12,5,10,13,8));
            case DECOMPOSER -> Shapes.or(Block.box(3,4,2,13,6,8),
                    Block.box(3,7,3,6,12,8),
                    Block.box(10,7,3,13,12,8),
                    Block.box(3,12,8,13,14,10),
                    Block.box(3,8,8,6,12,10),
                    Block.box(10,8,8,13,12,10),
                    Block.box(3,8,2.5,6,11,3),
                    Block.box(10,8,2.5,13,11,3));
            case SYNTHESIZER -> Shapes.or(Block.box(3,4,2,13,6,9),
                    Block.box(3,6,2,4,8,9),
                    Block.box(12,6,2,13,8,9),
                    Block.box(6,10,8,10,14,10),
                    Block.box(4,6,4,6,7,6),
                    Block.box(10,6,4,12,7,6));
            case PREPARATOR -> Shapes.or(Block.box(3,4,2,13,6,8),
                    Block.box(3,9,4,7,14,7),
                    Block.box(4,10,7,6,12,10),
                    Block.box(9,6,3,10,9,8),
                    Block.box(12,6,3,13,9,8),
                    Block.box(10,6,7,12,9,8),
                    Block.box(10,6,3,12,7,7),
                    Block.box(4,10,3.5,6,13,4));
            case HAND_PREPARATOR -> Shapes.or(Block.box(3,4,3,9,5,8),
                    Block.box(3,5,3,4,8,8),
                    Block.box(8,5,3,9,8,8),
                    Block.box(4,5,7,8,8,8),
                    Block.box(9.5,10.5,3,11.5,12.5,10),
                    Block.box(10,4,8,12,10.5,10),
                    Block.box(7,8,1.5,14,15,3));
            case SINTER_FURNACE -> Shapes.or(Block.box(3,4,2,13,6,9),
                    Block.box(3,6,2,5,12,9),
                    Block.box(11,6,2,13,12,9),
                    Block.box(3,12,2,13,14,9),
                    Block.box(5,6,8,11,12,10));
            default -> Shapes.empty();
        };
    }
}
