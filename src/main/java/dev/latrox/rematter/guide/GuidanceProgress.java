package dev.latrox.rematter.guide;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.research.ResearchService;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Explicit successful-operation hooks, rather than inventory items pretending that research happened. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class GuidanceProgress extends SavedData {
    private static final Codec<GuidanceProgress> CODEC = Codec.PASSTHROUGH.xmap(
            raw -> new GuidanceProgress(GuidanceFacts.fromJson(raw.convert(JsonOps.INSTANCE).getValue().getAsJsonObject())),
            data -> new Dynamic<>(JsonOps.INSTANCE, data.facts.toJson()));
    private static final class Registration {
        private static final SavedDataType<GuidanceProgress> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "guide_milestones"), GuidanceProgress::new, CODEC);
    }
    private final GuidanceFacts facts;
    public GuidanceProgress() { this(new GuidanceFacts()); }
    private GuidanceProgress(GuidanceFacts facts) { this.facts = facts; }
    private static GuidanceProgress get(ServerLevel level) { return level.getServer().overworld().getDataStorage().computeIfAbsent(Registration.TYPE); }
    public static void award(ServerLevel level, UUID owner, GuidanceEvent event) {
        if (!level.getServer().isSameThread() || owner == null || !ResearchService.realOwner(level.getServer(), owner)) return;
        var data = get(level); if (!data.facts.record(owner, event)) return;
        data.setDirty(); var player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null && !(player instanceof FakePlayer)) award(player, event);
    }
    private static void award(ServerPlayer player, GuidanceEvent event) {
        var holder = player.level().getServer().getAdvancements().get(Identifier.parse(event.advancement()));
        if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) player.getAdvancements().award(holder, "earned");
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer))
            get(player.level()).facts.earned(player.getUUID()).forEach(value -> award(player, value));
    }
}
