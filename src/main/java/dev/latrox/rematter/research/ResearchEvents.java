package dev.latrox.rematter.research;

import dev.latrox.rematter.ReMatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Visits are personal proof. Team contributions happen on membership observation, never in a knowledge read. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class ResearchEvents {
    private static final Map<MinecraftServer, Map<UUID, ResearchIdentity>> MEMBERSHIP = new java.util.IdentityHashMap<>();
    private ResearchEvents() {}
    @SubscribeEvent public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent event) { ResearchSavedData.get(event.getServer()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            var ledger = ResearchService.ledger(player.level().getServer()); if (!ledger.writable()) return;
            ledger.visit(player.getUUID(), player.level().dimension().identifier().toString());
            var context = ResearchService.context(player.level().getServer(), player.getUUID());
            ResearchData.current().families().stream().filter(MaterialResearch::startingKnowledge).forEach(f -> ledger.learn(context, f, LearningPath.STARTING));
            membership(player);
        }
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            var ledger = ResearchService.ledger(player.level().getServer()); if (ledger.writable()) ledger.visit(player.getUUID(), event.getTo().identifier().toString());
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        // Optional FTB APIs do not provide a universal event across versions; poll only online owners, never cache reads.
        if (event.getServer().getTickCount() % 20 == 0) for (var player : event.getServer().getPlayerList().getPlayers()) if (!(player instanceof FakePlayer)) membership(player);
    }
    private static void membership(ServerPlayer player) {
        var server = player.level().getServer(); var ledger = ResearchService.ledger(server); if (!ledger.writable()) return;
        var context = ResearchService.context(server, player.getUUID());
        var previous = MEMBERSHIP.computeIfAbsent(server, ignored -> new HashMap<>()).put(player.getUUID(), context.identity());
        if (!context.identity().equals(previous)) ledger.contributePersonal(player.getUUID(), context.identity());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { MEMBERSHIP.remove(event.getServer()); ResearchAuthority.stopped(event.getServer()); }
}
