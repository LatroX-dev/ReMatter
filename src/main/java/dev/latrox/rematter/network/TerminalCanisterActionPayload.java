package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server-authoritative request from a wired or wireless Matter Terminal to fill or drain a
 * {@link dev.latrox.rematter.content.PressurizedCanisterItem} in the player's inventory.
 */
public record TerminalCanisterActionPayload(
        int containerId,
        String substanceId,
        String phase,
        boolean drainToNetwork
) implements CustomPacketPayload {
    public static final Type<TerminalCanisterActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "terminal_canister_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalCanisterActionPayload> CODEC = new StreamCodec<>() {
        @Override
        public TerminalCanisterActionPayload decode(RegistryFriendlyByteBuf buffer) {
            int containerId = buffer.readVarInt();
            String substanceId = buffer.readUtf(MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH);
            String phase = buffer.readUtf(8);
            boolean drainToNetwork = buffer.readBoolean();
            return new TerminalCanisterActionPayload(containerId, substanceId, phase, drainToNetwork);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, TerminalCanisterActionPayload payload) {
            buffer.writeVarInt(payload.containerId());
            buffer.writeUtf(payload.substanceId(), MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH);
            buffer.writeUtf(payload.phase(), 8);
            buffer.writeBoolean(payload.drainToNetwork());
        }
    };

    public TerminalCanisterActionPayload {
        Objects.requireNonNull(substanceId);
        Objects.requireNonNull(phase);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof MachineMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.useTerminalCanister(player, payload.substanceId(), payload.phase(), payload.drainToNetwork());
            }
        });
    }

    @Override
    public Type<TerminalCanisterActionPayload> type() {
        return TYPE;
    }
}
