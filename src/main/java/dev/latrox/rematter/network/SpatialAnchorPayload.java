package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.content.RedstoneMode;
import dev.latrox.rematter.content.SpatialAnchorMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client-to-server configuration update for Spatial Anchor. */
public final class SpatialAnchorPayload {
    public static final String PROTOCOL = "1";

    private SpatialAnchorPayload() {}

    public record Configure(int containerId, int chunkMask, RedstoneMode redstoneMode, String customName) implements CustomPacketPayload {
        public static final Type<Configure> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "spatial_anchor_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Configure> CODEC = new StreamCodec<>() {
            @Override public Configure decode(RegistryFriendlyByteBuf buf) {
                return new Configure(buf.readVarInt(), buf.readVarInt(), buf.readEnum(RedstoneMode.class), buf.readUtf(CustomNames.MAX_LENGTH));
            }
            @Override public void encode(RegistryFriendlyByteBuf buf, Configure payload) {
                buf.writeVarInt(payload.containerId);
                buf.writeVarInt(payload.chunkMask);
                buf.writeEnum(payload.redstoneMode);
                buf.writeUtf(CustomNames.sanitize(payload.customName), CustomNames.MAX_LENGTH);
            }
        };

        @Override public Type<Configure> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL).playToServer(Configure.TYPE, Configure.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof SpatialAnchorMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.configure(sp, payload.chunkMask(), payload.redstoneMode(), payload.customName());
            }
        });
    }
}
