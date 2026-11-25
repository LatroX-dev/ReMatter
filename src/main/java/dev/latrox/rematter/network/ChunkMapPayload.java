package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.ChunkMapSnapshot;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.SpatialAnchorMenu;
import dev.latrox.rematter.content.WirelessAccessPointMenu;
import dev.latrox.rematter.primal.DimensionalEntangledLinkMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** On-demand S2C/C2S sync for the 5x5 top-down chunk coverage map (zero per-tick overhead). */
public final class ChunkMapPayload {
    public static final String PROTOCOL = "1";

    private ChunkMapPayload() {}

    public record Request(int containerId) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "chunk_map_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = new StreamCodec<>() {
            @Override public Request decode(RegistryFriendlyByteBuf buf) { return new Request(buf.readVarInt()); }
            @Override public void encode(RegistryFriendlyByteBuf buf, Request payload) { buf.writeVarInt(payload.containerId); }
        };
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Sync(int containerId, ChunkMapSnapshot map) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "chunk_map_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = new StreamCodec<>() {
            @Override public Sync decode(RegistryFriendlyByteBuf buf) { return new Sync(buf.readVarInt(), ChunkMapSnapshot.read(buf)); }
            @Override public void encode(RegistryFriendlyByteBuf buf, Sync payload) {
                buf.writeVarInt(payload.containerId);
                payload.map.write(buf);
            }
        };
        @Override public Type<Sync> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar(PROTOCOL);
        reg.playToServer(Request.TYPE, Request.CODEC, (payload, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            var menu = sp.containerMenu;
            if (menu == null || menu.containerId != payload.containerId()) return;
            if (menu instanceof MachineMenu m) m.sendChunkMap(sp);
            else if (menu instanceof SpatialAnchorMenu m) m.sendChunkMap(sp);
            else if (menu instanceof DimensionalEntangledLinkMenu m) m.sendChunkMap(sp);
            else if (menu instanceof WirelessAccessPointMenu m) m.sendChunkMap(sp);
        });
        reg.playToClient(Sync.TYPE, Sync.CODEC, (payload, ctx) -> {
            ClientChunkMaps.accept(payload.containerId(), payload.map());
        });
    }
}
