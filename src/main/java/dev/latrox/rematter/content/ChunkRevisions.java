package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jspecify.annotations.Nullable;

/**
 * A chunk-local revision changes on both sides of nested structural mutations, without a global mutation lock.
 * One process-wide epoch also changes with every such mutation, explicit touch, chunk load and chunk unload,
 * so a cached observation with an unchanged epoch in the same game tick needs no further check.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class ChunkRevisions {
    private static final AtomicLong EPOCH = new AtomicLong();
    public record Revision(long value, int mutationDepth) { public boolean stable() { return mutationDepth == 0; } }
    /**
     * One chunk as an observation read it: absent, or loaded at a revision. It still matches only while the same chunk
     * object is loaded at that revision outside any mutation, or while an absent chunk stays absent.
     */
    record Stamp(int x, int z, @Nullable WeakReference<LevelChunk> chunk, Revision revision) {
        private static final Revision ABSENT = new Revision(0, 0);
        static Stamp of(int x, int z, @Nullable LevelChunk chunk) {
            return chunk == null ? new Stamp(x, z, null, ABSENT) : new Stamp(x, z, new WeakReference<>(chunk), current(chunk));
        }
        static Stamp capture(ServerLevel level, int x, int z) { return of(x, z, level.getChunkSource().getChunkNow(x, z)); }
        boolean matches(ServerLevel level) {
            var current = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) return current == null;
            return current != null && current == chunk.get() && revision.stable() && revision.equals(ChunkRevisions.current(current));
        }
    }
    public interface Tracked {
        Revision rematter$chunkRevision();
        void rematter$touchChunkRevision();
    }
    public static final class State {
        private long value;
        private int depth;
        public synchronized void begin() { value++; depth=Math.addExact(depth,1); EPOCH.incrementAndGet(); }
        public synchronized void end() {
            if(depth==0)throw new IllegalStateException("Unbalanced chunk mutation");
            value++;depth--;EPOCH.incrementAndGet();
        }
        public synchronized Revision current() { return new Revision(value,depth); }
        public synchronized void touch() { value++; EPOCH.incrementAndGet(); }
        /** Runs one structural mutation inside the bracket, which closes even when the mutation throws. */
        public <T> T mutate(Supplier<T> mutation) {
            begin();
            try { return mutation.get(); }
            finally { end(); }
        }
    }
    private ChunkRevisions() {}
    public static Revision current(Object chunk) {
        if(!(chunk instanceof Tracked tracked))throw new IllegalStateException("Required industrial chunk revision hook is missing");
        return tracked.rematter$chunkRevision();
    }
    static void touch(Object chunk) {
        if (!(chunk instanceof Tracked tracked)) throw new IllegalStateException("Required industrial chunk revision hook is missing");
        tracked.rematter$touchChunkRevision();
    }
    /** Invalidates topology after an owned endpoint changes without a blockstate mutation. Never loads a chunk. */
    public static void touchLoaded(ServerLevel level, net.minecraft.core.BlockPos position) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Topology changes require the server thread");
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk != null) touch(chunk);
    }
    public static long epoch() { return EPOCH.get(); }

    /**
     * Whether a block change can alter any ReMatter observation: networks, shells and cable endpoints depend on
     * block identity, block entities and the facing and mode of machines and ports. Other property changes, such as
     * a machine's activity light, a lit furnace or redstone power, keep every observation valid.
     */
    public static boolean structural(BlockState previous, BlockState next) {
        if (previous.getBlock() != next.getBlock()) return true;
        if (next.getBlock() instanceof MachineBlock) return previous.getValue(MachineBlock.FACING) != next.getValue(MachineBlock.FACING);
        if (next.getBlock() instanceof MachinePortBlock) {
            return previous.getValue(MachinePortBlock.FACING) != next.getValue(MachinePortBlock.FACING)
                    || previous.getValue(MachinePortBlock.FORMED) != next.getValue(MachinePortBlock.FORMED)
                    || previous.getValue(MachinePortBlock.MODE) != next.getValue(MachinePortBlock.MODE);
        }
        return false;
    }

    @SubscribeEvent public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel) EPOCH.incrementAndGet();
    }
    @SubscribeEvent public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel) EPOCH.incrementAndGet();
    }
}
