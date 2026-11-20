package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.client.StockFavorites;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import dev.latrox.rematter.content.StockQuery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-to-client synchronization of a player's world-saved pinned substances. */
public record StockFavoritesSyncPayload(boolean initialLogin, List<StockKey> favorites) implements CustomPacketPayload {
    public static final Type<StockFavoritesSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "stock_favorites_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StockFavoritesSyncPayload> CODEC = new StreamCodec<>() {
        @Override
        public StockFavoritesSyncPayload decode(RegistryFriendlyByteBuf buf) {
            boolean login = buf.readBoolean();
            int count = MachineSnapshot.count(buf, StockQuery.MAX_FAVORITES);
            var list = new ArrayList<StockKey>(count);
            for (int i = 0; i < count; i++) {
                list.add(new StockKey(buf.readUtf(MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH), buf.readUtf(8)));
            }
            return new StockFavoritesSyncPayload(login, list);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, StockFavoritesSyncPayload payload) {
            buf.writeBoolean(payload.initialLogin);
            buf.writeVarInt(payload.favorites.size());
            for (var key : payload.favorites) {
                buf.writeUtf(key.id(), MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH);
                buf.writeUtf(key.phase(), 8);
            }
        }
    };

    public StockFavoritesSyncPayload(boolean initialLogin, Collection<StockKey> favorites) {
        this(initialLogin, List.copyOf(favorites));
    }

    public StockFavoritesSyncPayload {
        favorites = List.copyOf(favorites);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            var player = context.player();
            if (player != null) {
                var imported = StockFavorites.get().acceptServerSync(player.getUUID(), payload.initialLogin(), payload.favorites());
                if (!imported.isEmpty()) {
                    context.reply(new StockFavoriteTogglePayload(true, imported));
                }
            }
        }));
    }
}
