package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class CatalogSync {
    private static ChemistryRegistry cachedRegistry;
    private static Payload cachedPayload;
    private CatalogSync() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // A peer with another catalogue protocol is rejected before vanilla recipe sync decodes the preparation serializer.
        event.registrar("9").playToClient(Payload.TYPE, Payload.CODEC, (payload, context) ->
                ClientChemistry.accept(CatalogWireCodec.decode(payload.bytes)));
    }

    public static void sync(OnDatapackSyncEvent event) {
        ChemistryRegistry registry = ReMatter.chemistry();
        if (registry != cachedRegistry) {
            cachedPayload = new Payload(CatalogWireCodec.encode(registry));
            cachedRegistry = registry;
        }
        Payload payload = cachedPayload;
        event.getRelevantPlayers().filter(player -> !(player instanceof net.neoforged.neoforge.common.util.FakePlayer))
                .forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    public record Payload(byte[] bytes) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "chemistry_catalogue"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = new StreamCodec<>() {
            @Override public Payload decode(RegistryFriendlyByteBuf buffer) {
                return new Payload(buffer.readByteArray(CatalogWireCodec.MAX_COMPRESSED_BYTES));
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Payload payload) { buffer.writeByteArray(payload.bytes); }
        };
        public Payload {
            if (bytes.length == 0 || bytes.length > CatalogWireCodec.MAX_COMPRESSED_BYTES) throw new IllegalArgumentException("Invalid catalogue size");
            bytes = bytes.clone();
        }
        @Override public byte[] bytes() { return bytes.clone(); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
