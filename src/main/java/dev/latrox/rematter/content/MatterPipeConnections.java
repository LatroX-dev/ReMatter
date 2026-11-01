package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Updates legacy pipe shapes once after loading, including newly available chunk borders. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class MatterPipeConnections {
    private MatterPipeConnections() {}

    @SubscribeEvent public static void afterChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var chunk = event.getChunk();
        long nextTick = level.getGameTime() + 1;
        // The chunk is not FULL during this event. Inspect its own palette and enqueue
        // ordinary block ticks directly; defer all world/neighbor access until ticking.
        chunk.findBlocks(state -> state.getBlock() instanceof MatterPipeBlock || state.getBlock() instanceof MachineBlock || state.getBlock() instanceof MachinePortBlock,
                (pos, state) -> chunk.getBlockTicks().schedule(new ScheduledTick<>(
                        state.getBlock(), pos.immutable(), nextTick, TickPriority.NORMAL, 0)));
    }
}
