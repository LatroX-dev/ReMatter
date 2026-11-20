package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineMenu;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A confirmed request to hand over what one stopped delivery of the open Network Interface still holds. */
public record ReleaseHeldOrderPayload(int containerId, UUID orderId) implements CustomPacketPayload {
    public static final Type<ReleaseHeldOrderPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "release_held_order"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReleaseHeldOrderPayload> CODEC = new StreamCodec<>() {
        @Override public ReleaseHeldOrderPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ReleaseHeldOrderPayload(buffer.readVarInt(), UUIDUtil.STREAM_CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, ReleaseHeldOrderPayload payload) {
            buffer.writeVarInt(payload.containerId());
            UUIDUtil.STREAM_CODEC.encode(buffer, payload.orderId());
        }
    };

    public ReleaseHeldOrderPayload { Objects.requireNonNull(orderId); }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof MachineMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.releaseHeldOrder(player, payload.orderId());
            }
        });
    }

    @Override public Type<ReleaseHeldOrderPayload> type() { return TYPE; }
}
