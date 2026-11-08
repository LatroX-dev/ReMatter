package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * A chunk-local revision changes on both sides of nested structural mutations, without a global mutation lock.
 * One process-wide epoch also changes with every such mutation, explicit touch, chunk load and chunk unload,
 * so a cached observation with an unchanged epoch in the same game tick needs no further check.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class ChunkRevisions {
    private static final AtomicLong EPOCH = new AtomicLong();
    public record Revision(long value, int mutationDepth) { public boolean stable() { return mutationDepth == 0; } }
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
