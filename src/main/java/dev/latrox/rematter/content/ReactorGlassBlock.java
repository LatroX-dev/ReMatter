package dev.latrox.rematter.content;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Reactor containment glass. Standalone blocks keep their full four-sided frame; once part of a formed reactor
 * shell ({@link #FORMED}), adjacent same-tier glass blocks merge into one unified window keeping accents on the outer rim.
 */
public final class ReactorGlassBlock extends TransparentBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public ReactorGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }
}
