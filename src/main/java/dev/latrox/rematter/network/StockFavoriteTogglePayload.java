package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import dev.latrox.rematter.content.StockFavoritesSavedData;
import dev.latrox.rematter.content.StockQuery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client-to-server toggle or one-time local import for world-saved pinned substances. */
public record StockFavoriteTogglePayload(boolean bulkImport, List<StockKey> keys) implements CustomPacketPayload {
    public static final Type<StockFavoriteTogglePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "stock_favorite_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StockFavoriteTogglePayload> CODEC = new StreamCodec<>() {
        @Override
        public StockFavoriteTogglePayload decode(RegistryFriendlyByteBuf buf) {
            boolean bulk = buf.readBoolean();
            int count = MachineSnapshot.count(buf, bulk ? StockQuery.MAX_FAVORITES : 1);
            var list = new ArrayList<StockKey>(count);
            for (int i = 0; i < count; i++) {
                list.add(new StockKey(buf.readUtf(MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH), buf.readUtf(8)));
            }
            return new StockFavoriteTogglePayload(bulk, list);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, StockFavoriteTogglePayload payload) {
            buf.writeBoolean(payload.bulkImport);
            buf.writeVarInt(payload.keys.size());
            for (var key : payload.keys) {
                buf.writeUtf(key.id(), MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH);
                buf.writeUtf(key.phase(), 8);
            }
        }
    };

    public StockFavoriteTogglePayload(StockKey key) {
        this(false, List.of(key));
    }

    public StockFavoriteTogglePayload(boolean bulkImport, Collection<StockKey> keys) {
        this(bulkImport, List.copyOf(keys));
    }

    public StockFavoriteTogglePayload {
        keys = List.copyOf(keys);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var server = player.level().getServer();
            if (server == null) return;
            var saved = StockFavoritesSavedData.get(server);
            boolean changed = false;
            if (payload.bulkImport()) {
                changed = saved.importIfEmpty(player.getUUID(), payload.keys());
            } else if (payload.keys().size() == 1) {
                changed = saved.toggle(player.getUUID(), payload.keys().getFirst());
            }
            if (changed) {
                PacketDistributor.sendToPlayer(player, new StockFavoritesSyncPayload(false, saved.of(player.getUUID())));
            }
        }));
    }
}
