package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Repairs connection shapes along chunk borders after a chunk loads. A neighbour across the border may have changed
 * while this chunk was unloaded; inside the chunk, ordinary shape updates already kept every connection current.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class MatterCableConnections {
    private MatterCableConnections() {}

    @SubscribeEvent public static void afterChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var chunk = event.getChunk();
        long nextTick = level.getGameTime() + 1;
        // The chunk is not FULL during this event. Inspect its own palette and enqueue
        // ordinary block ticks directly; defer all world/neighbor access until ticking.
        chunk.findBlocks(state -> state.getBlock() instanceof MatterCableBlock || state.getBlock() instanceof MachineBlock || state.getBlock() instanceof MachinePortBlock,
                (pos, state) -> {
                    if (borderColumn(pos.getX()) || borderColumn(pos.getZ())) chunk.getBlockTicks().schedule(new ScheduledTick<>(
                            state.getBlock(), pos.immutable(), nextTick, TickPriority.NORMAL, 0));
                });
    }

    static boolean borderColumn(int coordinate) {
        int local = coordinate & 15;
        return local == 0 || local == 15;
    }
}
