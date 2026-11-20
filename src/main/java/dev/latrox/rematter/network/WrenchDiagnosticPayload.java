package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.client.ClientWrenchDiagnostics;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Compact server-authoritative look-target diagnostic reply for the Matter Wrench HUD overlay.
 * When {@code locked} is {@code true}, all private quantities and network details are zeroed.
 */
public record WrenchDiagnosticPayload(
        BlockPos pos,
        Direction face,
        boolean locked,
        String status,
        long energy,
        long energyCapacity,
        int transferRate,
        long matterUsed,
        long matterCapacity,
        int matterTypes,
        int networkNodes,
        int networkCells,
        int linkState,
        int linkDistance,
        boolean redstoneActive,
        long threshold
) implements CustomPacketPayload {
    public static final Type<WrenchDiagnosticPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "wrench_diagnostic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WrenchDiagnosticPayload> CODEC = new StreamCodec<>() {
        @Override
        public WrenchDiagnosticPayload decode(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            Direction face = Direction.from3DDataValue(buf.readUnsignedByte());
            boolean locked = buf.readBoolean();
            String status = buf.readUtf(64);
            long energy = buf.readVarLong();
            long energyCapacity = buf.readVarLong();
            int transferRate = buf.readVarInt();
            long matterUsed = buf.readVarLong();
            long matterCapacity = buf.readVarLong();
            int matterTypes = buf.readVarInt();
            int networkNodes = buf.readVarInt();
            int networkCells = buf.readVarInt();
            int linkState = buf.readVarInt();
            int linkDistance = buf.readVarInt();
            boolean redstoneActive = buf.readBoolean();
            long threshold = buf.readVarLong();
            return new WrenchDiagnosticPayload(
                    pos, face, locked, status, energy, energyCapacity, transferRate,
                    matterUsed, matterCapacity, matterTypes, networkNodes, networkCells,
                    linkState, linkDistance, redstoneActive, threshold);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, WrenchDiagnosticPayload payload) {
            buf.writeBlockPos(payload.pos());
            buf.writeByte(payload.face().get3DDataValue());
            buf.writeBoolean(payload.locked());
            buf.writeUtf(payload.status(), 64);
            buf.writeVarLong(payload.energy());
            buf.writeVarLong(payload.energyCapacity());
            buf.writeVarInt(payload.transferRate());
            buf.writeVarLong(payload.matterUsed());
            buf.writeVarLong(payload.matterCapacity());
            buf.writeVarInt(payload.matterTypes());
            buf.writeVarInt(payload.networkNodes());
            buf.writeVarInt(payload.networkCells());
            buf.writeVarInt(payload.linkState());
            buf.writeVarInt(payload.linkDistance());
            buf.writeBoolean(payload.redstoneActive());
            buf.writeVarLong(payload.threshold());
        }
    };

    public WrenchDiagnosticPayload {
        Objects.requireNonNull(pos);
        Objects.requireNonNull(face);
        Objects.requireNonNull(status);
    }

    public static WrenchDiagnosticPayload locked(BlockPos pos, Direction face) {
        return new WrenchDiagnosticPayload(pos, face, true, "locked", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, -1);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> ClientWrenchDiagnostics.accept(payload));
    }

    @Override
    public Type<WrenchDiagnosticPayload> type() {
        return TYPE;
    }
}
