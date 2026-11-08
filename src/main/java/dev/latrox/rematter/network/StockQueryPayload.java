package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.StockQuery;
import java.util.HashSet;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The stock view's search, phase filter and favorites, so the server keeps them within its row limit. */
public record StockQueryPayload(int containerId, StockQuery query) implements CustomPacketPayload {
    private static final String[] PHASES = {"solid", "liquid", "gas"};
    public static final Type<StockQueryPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "stock_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StockQueryPayload> CODEC = new StreamCodec<>() {
        @Override public StockQueryPayload decode(RegistryFriendlyByteBuf buffer) {
            int containerId = buffer.readVarInt();
            String text = buffer.readUtf(StockQuery.MAX_TEXT);
            int mask = buffer.readUnsignedByte();
            if (mask >= 1 << PHASES.length) throw new IllegalArgumentException("Invalid phase filter");
            var phases = new HashSet<String>();
            for (int i = 0; i < PHASES.length; i++) if ((mask & 1 << i) != 0) phases.add(PHASES[i]);
            boolean favoritesOnly = buffer.readBoolean();
            int favoriteCount = MachineSnapshot.count(buffer, StockQuery.MAX_FAVORITES);
            var favorites = new HashSet<MachineSnapshot.StockKey>(favoriteCount);
            for (int i = 0; i < favoriteCount; i++)
                favorites.add(new MachineSnapshot.StockKey(buffer.readUtf(MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH), buffer.readUtf(8)));
            int namedCount = MachineSnapshot.count(buffer, StockQuery.MAX_NAMED);
            var named = new HashSet<String>(namedCount);
            for (int i = 0; i < namedCount; i++) named.add(buffer.readUtf(MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH));
            return new StockQueryPayload(containerId, new StockQuery(text, phases, favoritesOnly, favorites, named));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, StockQueryPayload payload) {
            var query = payload.query();
            buffer.writeVarInt(payload.containerId());
            buffer.writeUtf(query.text(), StockQuery.MAX_TEXT);
            int mask = 0;
            for (int i = 0; i < PHASES.length; i++) if (query.phases().contains(PHASES[i])) mask |= 1 << i;
            buffer.writeByte(mask);
            buffer.writeBoolean(query.favoritesOnly());
            buffer.writeVarInt(query.favorites().size());
            for (var key : query.favorites()) {
                buffer.writeUtf(key.id(), MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH);
                buffer.writeUtf(key.phase(), 8);
            }
            buffer.writeVarInt(query.named().size());
            for (var id : query.named()) buffer.writeUtf(id, MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH);
        }
    };

    public StockQueryPayload { Objects.requireNonNull(query); }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof MachineMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.acceptStockQuery(player, payload.query());
            }
        });
    }

    @Override public Type<StockQueryPayload> type() { return TYPE; }
}
