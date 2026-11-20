package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.TerminalBatchPlanner;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client-to-server request to preview, submit, poll, or cancel a multi-step terminal batch plan. */
public record TerminalBatchRequestPayload(
        int containerId,
        Action action,
        TerminalBatchPlanner.TargetKind targetKind,
        String targetId,
        String phase,
        int batches,
        int jobId) implements CustomPacketPayload {
    public enum Action {
        PREVIEW,
        SUBMIT,
        CANCEL,
        POLL,
        PIN_PREVIEW
    }

    public static final int MAX_PHASE_LEN = 16;

    public TerminalBatchRequestPayload {
        if (targetId.length() > MachineSnapshot.MAX_CHOICE_ID_LENGTH
                || phase.length() > MAX_PHASE_LEN
                || batches < 0
                || batches > TerminalBatchPlanner.MAX_REQUEST_BATCHES) {
            throw new IllegalArgumentException("Invalid terminal batch request");
        }
    }

    public static final Type<TerminalBatchRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "terminal_batch_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalBatchRequestPayload> CODEC = new StreamCodec<>() {
        @Override
        public TerminalBatchRequestPayload decode(RegistryFriendlyByteBuf buf) {
            return new TerminalBatchRequestPayload(
                    buf.readVarInt(),
                    buf.readEnum(Action.class),
                    buf.readEnum(TerminalBatchPlanner.TargetKind.class),
                    buf.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH),
                    buf.readUtf(MAX_PHASE_LEN),
                    buf.readVarInt(),
                    buf.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, TerminalBatchRequestPayload payload) {
            buf.writeVarInt(payload.containerId());
            buf.writeEnum(payload.action());
            buf.writeEnum(payload.targetKind());
            buf.writeUtf(payload.targetId(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
            buf.writeUtf(payload.phase(), MAX_PHASE_LEN);
            buf.writeVarInt(payload.batches());
            buf.writeVarInt(payload.jobId());
        }
    };

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(TYPE, CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof MachineMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.handleTerminalBatchRequest(player, payload);
            }
        });
    }

    @Override
    public Type<TerminalBatchRequestPayload> type() {
        return TYPE;
    }
}
