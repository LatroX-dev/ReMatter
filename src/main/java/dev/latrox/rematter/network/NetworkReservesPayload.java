package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.client.ClientNetworkReserves;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import dev.latrox.rematter.content.NetworkReservesSavedData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Synchronizes and updates per-owner global network reserves configured in Matter Terminals. */
public final class NetworkReservesPayload {
    private NetworkReservesPayload() {}

    public record Sync(Map<StockKey, Long> reserves) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "network_reserves_sync"));

        public Sync {
            reserves = Map.copyOf(reserves);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = new StreamCodec<>() {
            @Override
            public Sync decode(RegistryFriendlyByteBuf buf) {
                int count = MachineSnapshot.count(buf, NetworkReservesSavedData.MAX_PER_OWNER);
                var map = new LinkedHashMap<StockKey, Long>(count);
                for (int i = 0; i < count; i++) {
                    var key = new StockKey(buf.readUtf(MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH), buf.readUtf(8));
                    long amount = Math.clamp(buf.readVarLong(), 0L, NetworkReservesSavedData.MAX_RESERVE);
                    if (amount > 0L) map.put(key, amount);
                }
                return new Sync(map);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Sync payload) {
                buf.writeVarInt(payload.reserves.size());
                payload.reserves.forEach((key, amount) -> {
                    buf.writeUtf(key.id(), MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH);
                    buf.writeUtf(key.phase(), 8);
                    buf.writeVarLong(amount);
                });
            }
        };

        @Override public Type<Sync> type() { return TYPE; }
    }

    public record Update(int containerId, StockKey key, long amount) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "network_reserve_update"));

        public Update {
            if (containerId < 0 || !NetworkReservesSavedData.isValidKey(key)
                    || amount < 0L || amount > NetworkReservesSavedData.MAX_RESERVE) {
                throw new IllegalArgumentException("Invalid network reserve update");
            }
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = new StreamCodec<>() {
            @Override
            public Update decode(RegistryFriendlyByteBuf buf) {
                int id = buf.readVarInt();
                var key = new StockKey(buf.readUtf(MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH), buf.readUtf(8));
                long amount = buf.readVarLong();
                return new Update(id, key, amount);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Update payload) {
                buf.writeVarInt(payload.containerId);
                buf.writeUtf(payload.key.id(), MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH);
                buf.writeUtf(payload.key.phase(), 8);
                buf.writeVarLong(payload.amount);
            }
        };

        @Override public Type<Update> type() { return TYPE; }
    }

    public static void syncToPlayer(ServerPlayer player, UUID owner) {
        var server = player.level().getServer();
        if (server == null || owner == null) return;
        PacketDistributor.sendToPlayer(player, new Sync(NetworkReservesSavedData.get(server).of(owner)));
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player, player.getUUID());
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Sync.TYPE, Sync.CODEC, (payload, context) -> context.enqueueWork(() -> {
            ClientNetworkReserves.acceptServerSync(payload.reserves());
        }));
        registrar.playToServer(Update.TYPE, Update.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof MachineMenu menu
                    && menu.containerId == payload.containerId()
                    && menu.kind() == MachineKind.TERMINAL) {
                menu.updateGlobalReserve(player, payload.key(), payload.amount());
            }
        });
    }
}
