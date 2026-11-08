package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A request to open a device or queue a batch of its already selected recipe. */
public record TerminalDeviceActionPayload(int containerId, BlockPos position, Action action, String recipeId) implements CustomPacketPayload {
    public enum Action { OPEN, QUEUE_BATCH }

    public TerminalDeviceActionPayload {
        position = position.immutable();
        if (recipeId.length() > MachineSnapshot.MAX_CHOICE_ID_LENGTH || (action == Action.QUEUE_BATCH) == recipeId.isEmpty())
            throw new IllegalArgumentException("Invalid terminal request");
    }

    public static final Type<TerminalDeviceActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "terminal_device_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalDeviceActionPayload> CODEC = new StreamCodec<>() {
        @Override public TerminalDeviceActionPayload decode(RegistryFriendlyByteBuf buffer) {
            return new TerminalDeviceActionPayload(buffer.readVarInt(), buffer.readBlockPos(), buffer.readEnum(Action.class),
                    buffer.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, TerminalDeviceActionPayload payload) {
            buffer.writeVarInt(payload.containerId());
            buffer.writeBlockPos(payload.position());
            buffer.writeEnum(payload.action());
            buffer.writeUtf(payload.recipeId(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
        }
    };

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof MachineMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.useTerminalDevice(player, payload);
            }
        });
    }

    @Override public Type<TerminalDeviceActionPayload> type() { return TYPE; }
}
