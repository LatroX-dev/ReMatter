package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.network.StockFavoritesSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Pushes world-saved pinned substances to real players when they join. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class StockFavoritesEvents {
    private StockFavoritesEvents() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        var server = player.level().getServer();
        if (server == null) return;
        var saved = StockFavoritesSavedData.get(server);
        PacketDistributor.sendToPlayer(player, new StockFavoritesSyncPayload(true, saved.of(player.getUUID())));
    }
}
