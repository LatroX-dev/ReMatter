package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.research.*;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** At most one diff per second, including team changes; unchanged players receive no heartbeat packets. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class ResearchKnowledgeSync {
    private static final Map<MinecraftServer, Map<UUID, Sent>> SENT = new IdentityHashMap<>();
    private static final AtomicLong UPDATES = new AtomicLong();
    private record Fingerprint(ChemistryRegistry chemistry, String research, ResearchContext owner, boolean enabled, boolean available,
                               Set<String> families, Set<SubstanceKey> substances) {}
    private record Sent(Fingerprint fingerprint, ResearchKnowledgeWireCodec.State state) {}
    private ResearchKnowledgeSync() {}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) refresh(player);
    }
    @SubscribeEvent public static void reload(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().filter(player -> !(player instanceof FakePlayer)).forEach(ResearchKnowledgeSync::refresh);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) for (var player : event.getServer().getPlayerList().getPlayers()) if (!(player instanceof FakePlayer)) refresh(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { var sent = SENT.get(player.level().getServer()); if (sent != null) sent.remove(player.getUUID()); }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SENT.remove(event.getServer()); }
    private static void refresh(ServerPlayer player) {
        var server = player.level().getServer(); var owner = player.getUUID();
        var chemistry = ReMatter.chemistry(); boolean enabled = ResearchConfig.enabled(), available = ResearchService.available(server);
        var known = available ? ResearchService.knownSubstances(server, owner) : Set.<SubstanceKey>of();
        var key = new Fingerprint(chemistry, ResearchData.current().fingerprint(), ResearchService.context(server, owner), enabled, available,
                available ? ResearchService.knownFamilies(server, owner) : Set.of(), known);
        var sent = SENT.computeIfAbsent(server, ignored -> new HashMap<>()); var before = sent.get(owner);
        if (before != null && before.fingerprint().equals(key)) return;
        var allowed = new TreeSet<String>();
        if (available) for (String id : chemistry.synthesisPolicy().entries().keySet()) if (ResearchService.allowsSynthesis(server, owner, id)) allowed.add(id);
        var state = new ResearchKnowledgeWireCodec.State(enabled, available, allowed, known);
        if (before == null || !before.state().equals(state)) {
            for (var fragment : ResearchKnowledgePayload.chunks(UPDATES.incrementAndGet(), state)) PacketDistributor.sendToPlayer(player, fragment);
        }
        sent.put(owner, new Sent(key, state));
    }
}
