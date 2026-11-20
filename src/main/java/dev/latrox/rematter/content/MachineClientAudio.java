package dev.latrox.rematter.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Server-safe hook allowing active machines to drive seamless client-side audio loops without loading client classes. */
public final class MachineClientAudio {
    @FunctionalInterface
    public interface Listener {
        void onActiveTick(Level level, BlockPos pos, MachineKind kind);
    }

    private static volatile @Nullable Listener listener;

    private MachineClientAudio() {}

    public static void install(Listener handler) {
        listener = handler;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MachineKind kind) {
        tick(level, pos, state, kind, false);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MachineKind kind, boolean muted) {
        if (muted || !state.getValue(MachineBlock.ACTIVE)) return;
        Listener current = listener;
        if (current != null && ((level.getGameTime() + pos.asLong()) & 3L) == 0L) {
            current.onActiveTick(level, pos, kind);
        }
    }
}
