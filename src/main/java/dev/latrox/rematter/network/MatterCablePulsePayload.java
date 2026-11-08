package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The ordered cable positions in a single visual pulse. */
public record MatterCablePulsePayload(boolean energy, List<BlockPos> path) implements CustomPacketPayload {
    public static final int MAX_PATH = 64;
    public static final Type<MatterCablePulsePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "matter_cable_pulse"));
    private static final StreamCodec<RegistryFriendlyByteBuf, MatterCablePulsePayload> CODEC = new StreamCodec<>() {
        @Override
        public MatterCablePulsePayload decode(RegistryFriendlyByteBuf buffer) {
            boolean energy = buffer.readBoolean();
            int count = buffer.readUnsignedByte();
            if (count < 1 || count > MAX_PATH) throw new IllegalArgumentException("Invalid cable pulse length");
            var positions = new ArrayList<BlockPos>(count);
            positions.add(buffer.readBlockPos());
            for (int i = 1; i < count; i++) {
                int direction = buffer.readUnsignedByte();
                if (direction >= 6) throw new IllegalArgumentException("Invalid cable pulse direction");
                positions.add(positions.getLast().relative(Direction.from3DDataValue(direction)));
            }
            return new MatterCablePulsePayload(energy, positions);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MatterCablePulsePayload payload) {
            var path = payload.path();
            buffer.writeBoolean(payload.energy());
            buffer.writeByte(path.size());
            buffer.writeBlockPos(path.getFirst());
            for (int i = 1; i < path.size(); i++) {
                var step = Direction.getNearest(path.get(i).subtract(path.get(i - 1)), Direction.NORTH);
                buffer.writeByte(step.get3DDataValue());
            }
        }
    };

    public MatterCablePulsePayload {
        if (path.isEmpty() || path.size() > MAX_PATH) throw new IllegalArgumentException("Invalid cable pulse length");
        path = path.stream().map(BlockPos::immutable).toList();
        for (int i = 1; i < path.size(); i++) {
            if (path.get(i - 1).distManhattan(path.get(i)) != 1) {
                throw new IllegalArgumentException("Disconnected cable pulse");
            }
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC);
    }

    @Override
    public Type<MatterCablePulsePayload> type() {
        return TYPE;
    }
}
