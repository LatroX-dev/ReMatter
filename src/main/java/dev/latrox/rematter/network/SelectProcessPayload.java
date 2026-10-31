package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Selects a catalogue entry in the player's currently open, server-validated machine. */
public record SelectProcessPayload(int containerId, String choiceId) implements CustomPacketPayload {
    public static final Type<SelectProcessPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "select_process"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectProcessPayload> CODEC = new StreamCodec<>() {
        @Override public SelectProcessPayload decode(RegistryFriendlyByteBuf buffer) {
            return new SelectProcessPayload(buffer.readVarInt(), buffer.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, SelectProcessPayload value) {
            buffer.writeVarInt(value.containerId());
            buffer.writeUtf(value.choiceId(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
        }
    };

    public SelectProcessPayload {
        if (choiceId == null || choiceId.isBlank() || choiceId.length() > MachineSnapshot.MAX_CHOICE_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid process identifier");
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(TYPE, CODEC, (payload, context) -> {
            var player = context.player();
            if (player.containerMenu instanceof MachineMenu menu && menu.containerId == payload.containerId()) {
                menu.selectRecipe(player, payload.choiceId());
            }
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
