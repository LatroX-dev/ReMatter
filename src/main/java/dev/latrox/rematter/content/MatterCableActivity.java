package dev.latrox.rematter.content;

import dev.latrox.rematter.network.MatterCablePulsePayload;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Bounded display traffic from committed matter and energy transfers. */
final class MatterCableActivity {
    private static final int MAX_MATTER_PULSES_PER_WINDOW = 6;
    private static final int MAX_ENERGY_PULSES_PER_WINDOW = 2;
    private static final int MAX_ROUTE_LOOKUPS_PER_WINDOW = 8;
    private static final double VIEW_DISTANCE = 40;
    private static final WeakHashMap<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();

    private MatterCableActivity() {}

    static boolean canTrack(ServerLevel level) {
        if (level.players().isEmpty()) return false;
        var budget = BUDGETS.get(level);
        return budget == null || budget.window != level.getGameTime() / 4
                || (budget.matterPulses < MAX_MATTER_PULSES_PER_WINDOW && budget.lookups < MAX_ROUTE_LOOKUPS_PER_WINDOW);
    }

    static boolean canTrackEnergy(ServerLevel level) {
        if (level.players().isEmpty()) return false;
        var budget = BUDGETS.get(level);
        return budget == null || budget.window != level.getGameTime() / 4
                || budget.energyPulses < MAX_ENERGY_PULSES_PER_WINDOW;
    }

    static boolean beginRouteLookup(ServerLevel level) {
        if (!canTrack(level)) return false;
        budget(level).lookups++;
        return true;
    }

    static void pulse(ServerLevel level, List<BlockPos> path) {
        send(level, path, false);
    }

    static void energy(ServerLevel level, List<BlockPos> path) {
        send(level, path, true);
    }

    private static void send(ServerLevel level, List<BlockPos> path, boolean energy) {
        if (path.isEmpty() || !(energy ? canTrackEnergy(level) : !level.players().isEmpty())) return;
        var budget = budget(level);
        if (!energy && budget.matterPulses >= MAX_MATTER_PULSES_PER_WINDOW) return;

        int shown = Math.min(path.size(), MatterCablePulsePayload.MAX_PATH);
        var payload = new MatterCablePulsePayload(energy, path.subList(0, shown));
        Vec3 middle = Vec3.atCenterOf(path.get(shown / 2));
        double radius = VIEW_DISTANCE + shown / 2.0;
        PacketDistributor.sendToPlayersNear(level, null, middle.x, middle.y, middle.z, radius, payload);
        if (energy) budget.energyPulses++;
        else budget.matterPulses++;
    }

    private static Budget budget(ServerLevel level) {
        long window = level.getGameTime() / 4;
        var budget = BUDGETS.get(level);
        if (budget == null || budget.window != window) {
            budget = new Budget(window);
            BUDGETS.put(level, budget);
        }
        return budget;
    }

    private static final class Budget {
        private final long window;
        private int matterPulses;
        private int energyPulses;
        private int lookups;

        private Budget(long window) { this.window = window; }
    }
}
