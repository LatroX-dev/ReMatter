package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.generation.CrystalFuelData;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Synchronizes authoritative server Crystal Generator fuels to clients on login and /reload. */
public final class CrystalFuelSync {
    private static final int MAX_ENTRIES = 64;
    private CrystalFuelSync() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Payload.TYPE, Payload.CODEC,
                (payload, context) -> ClientCrystalFuels.accept(payload.fuels()));
    }

    public static void sync(OnDatapackSyncEvent event) {
        var payload = new Payload(CrystalFuelData.fuels());
        event.getRelevantPlayers()
                .filter(player -> !(player instanceof FakePlayer))
                .forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    public record Payload(Map<String, CrystalFuelData.Fuel> fuels) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "crystal_fuels"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = new StreamCodec<>() {
            @Override
            public Payload decode(RegistryFriendlyByteBuf buffer) {
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Invalid crystal fuel sync count: " + size);
                }
                var map = new TreeMap<String, CrystalFuelData.Fuel>();
                for (int i = 0; i < size; i++) {
                    String itemId = buffer.readUtf(128);
                    int count = buffer.readVarInt();
                    long energy = buffer.readVarLong();
                    if (map.put(itemId, new CrystalFuelData.Fuel(itemId, count, energy)) != null) {
                        throw new IllegalArgumentException("Duplicate crystal fuel entry: " + itemId);
                    }
                }
                return new Payload(Collections.unmodifiableMap(map));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Payload payload) {
                buffer.writeVarInt(payload.fuels.size());
                for (var fuel : payload.fuels.values()) {
                    buffer.writeUtf(fuel.itemId(), 128);
                    buffer.writeVarInt(fuel.count());
                    buffer.writeVarLong(fuel.energy());
                }
            }
        };

        public Payload {
            if (fuels.size() > MAX_ENTRIES) {
                throw new IllegalArgumentException("Too many crystal fuel entries: " + fuels.size());
            }
            var sorted = new TreeMap<String, CrystalFuelData.Fuel>();
            fuels.forEach((key, value) -> {
                if (!key.equals(value.itemId())) {
                    throw new IllegalArgumentException("Mismatched crystal fuel key: " + key);
                }
                sorted.put(key, value);
            });
            fuels = Collections.unmodifiableMap(sorted);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
