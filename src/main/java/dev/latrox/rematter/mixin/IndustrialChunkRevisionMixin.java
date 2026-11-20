package dev.latrox.rematter.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.latrox.rematter.content.ChunkRevisions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * The revision changes before a structural mutation, so old observations become invalid, and again when it ends,
 * so observations made by callbacks during the mutation are not reused. The bracket also closes when the mutation
 * throws, so a caught exception can never leave the chunk marked as changing.
 */
@Mixin(LevelChunk.class)
abstract class IndustrialChunkRevisionMixin implements ChunkRevisions.Tracked {
    @Unique private ChunkRevisions.State rematter$changes;
    @Unique private synchronized ChunkRevisions.State rematter$revisionState() {
        if(rematter$changes==null)rematter$changes=new ChunkRevisions.State();
        return rematter$changes;
    }
    @Override public ChunkRevisions.Revision rematter$chunkRevision() { return rematter$revisionState().current(); }
    @Override public void rematter$touchChunkRevision() { rematter$revisionState().touch(); }
    @WrapMethod(method = "setBlockState")
    private @Nullable BlockState rematter$trackState(BlockPos pos, BlockState state, int flags, Operation<BlockState> original) {
        if (!rematter$serverChunk() || !ChunkRevisions.structural(((LevelChunk)(Object)this).getBlockState(pos), state))
            return original.call(pos, state, flags);
        return rematter$revisionState().mutate(() -> original.call(pos, state, flags));
    }
    @WrapMethod(method = "setBlockEntity")
    private void rematter$trackEntity(BlockEntity entity, Operation<Void> original) {
        if (rematter$serverChunk()) rematter$revisionState().mutate(() -> original.call(entity));
        else original.call(entity);
    }
    @WrapMethod(method = "removeBlockEntity")
    private void rematter$trackRemoval(BlockPos pos, Operation<Void> original) {
        if (rematter$serverChunk()) rematter$revisionState().mutate(() -> original.call(pos));
        else original.call(pos);
    }
    @Unique private boolean rematter$serverChunk() { return ((LevelChunk)(Object)this).getLevel() instanceof ServerLevel; }
}
