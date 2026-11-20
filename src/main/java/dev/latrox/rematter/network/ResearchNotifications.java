package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.compat.TeamAccess;
import dev.latrox.rematter.research.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Explicit post-commit hook. Login, starting knowledge and administrative grants never call it. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class ResearchNotifications {
    private static final Map<MinecraftServer, Map<UUID, Set<String>>> NOTIFIED = new IdentityHashMap<>();
    private ResearchNotifications() {}
    public static void emitLearned(MinecraftServer server, UUID owner, MaterialResearch family) {
        if (!server.isSameThread() || !ResearchService.realOwner(server, owner) || family.startingKnowledge()) return;
        dev.latrox.rematter.guide.GuidanceProgress.award(server.overworld(), owner, dev.latrox.rematter.guide.GuidanceEvent.FIRST_LESSON);
        boolean decompositionOnly = family.items().stream().noneMatch(id -> ReMatter.chemistry().synthesisPolicy().allowsItem(id));
        var notice = new ResearchLearnedPayload(family.id(), family.name().length() > 512 ? family.name().substring(0, 512) : family.name(), decompositionOnly);
        var sent = NOTIFIED.computeIfAbsent(server, ignored -> new HashMap<>());
        for (var player : server.getPlayerList().getPlayers()) {
            if (player instanceof FakePlayer || !player.getUUID().equals(owner) && ResearchConfig.sharing() != ResearchConfig.Sharing.SERVER && !TeamAccess.shared(owner, player.getUUID())) continue;
            if (!ResearchService.knownFamilies(server, player.getUUID()).contains(family.id())) continue;
            if (sent.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(family.id())) PacketDistributor.sendToPlayer(player, notice);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) { var sent = NOTIFIED.get(player.level().getServer()); if (sent != null) sent.remove(player.getUUID()); }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { NOTIFIED.remove(event.getServer()); }
}
