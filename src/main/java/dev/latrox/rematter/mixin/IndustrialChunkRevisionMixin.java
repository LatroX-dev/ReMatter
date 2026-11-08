package dev.latrox.rematter.mixin;

import dev.latrox.rematter.content.ChunkRevisions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** HEAD invalidates old observations; RETURN prevents observations made by callbacks being reused. */
@Mixin(LevelChunk.class)
abstract class IndustrialChunkRevisionMixin implements ChunkRevisions.Tracked {
    @Unique private ChunkRevisions.State rematter$changes;
    @Unique private synchronized ChunkRevisions.State rematter$revisionState() {
        if(rematter$changes==null)rematter$changes=new ChunkRevisions.State();
        return rematter$changes;
    }
    @Override public ChunkRevisions.Revision rematter$chunkRevision() { return rematter$revisionState().current(); }
    @Override public void rematter$touchChunkRevision() { rematter$revisionState().touch(); }
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void rematter$stateBegin(CallbackInfoReturnable<?> callback) { if (rematter$serverChunk()) rematter$revisionState().begin(); }
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void rematter$stateEnd(CallbackInfoReturnable<?> callback) { if (rematter$serverChunk()) rematter$revisionState().end(); }
    @Inject(method = {"setBlockEntity", "removeBlockEntity"}, at = @At("HEAD"))
    private void rematter$entityBegin(CallbackInfo callback) { if (rematter$serverChunk()) rematter$revisionState().begin(); }
    @Inject(method = {"setBlockEntity", "removeBlockEntity"}, at = @At("RETURN"))
    private void rematter$entityEnd(CallbackInfo callback) { if (rematter$serverChunk()) rematter$revisionState().end(); }
    @Unique private boolean rematter$serverChunk() { return ((LevelChunk)(Object)this).getLevel() instanceof ServerLevel; }
}
