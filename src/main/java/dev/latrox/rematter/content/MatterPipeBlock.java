package dev.latrox.rematter.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Six-way visual connections follow the same block kinds as the matter network. */
public final class MatterPipeBlock extends PipeBlock {
    public static final MapCodec<MatterPipeBlock> CODEC = simpleCodec(MatterPipeBlock::new);

    public MatterPipeBlock(Properties properties) {
        super(6, properties);
        BlockState state = stateDefinition.any();
        for (var property : PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }
    @Override protected MapCodec<MatterPipeBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }
    private static boolean connects(BlockState neighbor) {
        return neighbor.getBlock() instanceof MatterPipeBlock || neighbor.getBlock() instanceof MachineBlock || neighbor.getBlock() instanceof MachinePortBlock;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(side), connects(context.getLevel().getBlockState(context.getClickedPos().relative(side))));
        }
        return state;
    }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction side, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(side), connects(neighbor));
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        refreshLoadedAround(level, pos);
    }
    /** Restores saved connection shapes without loading the neighboring chunks. */
    static void refreshLoadedAround(ServerLevel level, BlockPos pos) {
        refreshLoadedPipe(level, pos);
        for (Direction side : Direction.values()) refreshLoadedPipe(level, pos.relative(side));
    }
    private static void refreshLoadedPipe(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return;
        BlockState state = chunk.getBlockState(pos);
        if (!(state.getBlock() instanceof MatterPipeBlock)) return;
        BlockState updated = state;
        for (Direction side : Direction.values()) {
            BlockPos neighborPos = pos.relative(side);
            var neighborChunk = level.getChunkSource().getChunkNow(neighborPos.getX() >> 4, neighborPos.getZ() >> 4);
            // Keep the saved connection until that neighboring chunk is available.
            if (neighborChunk != null) updated = updated.setValue(PROPERTY_BY_DIRECTION.get(side), connects(neighborChunk.getBlockState(neighborPos)));
        }
        if (updated != state) level.setBlock(pos, updated, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state;
        for (Direction side : Direction.values()) rotated = rotated.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(side)), state.getValue(PROPERTY_BY_DIRECTION.get(side)));
        return rotated;
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Direction side : Direction.values()) mirrored = mirrored.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(side)), state.getValue(PROPERTY_BY_DIRECTION.get(side)));
        return mirrored;
    }
}
