package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The volatile part of an open menu: buffered FE and batch progress. */
public record MachineProgressPayload(int containerId, int energy, int progress, int duration) implements CustomPacketPayload {
    public static final Type<MachineProgressPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "machine_progress"));
    public static final MachineProgressPayload EMPTY = new MachineProgressPayload(-1, 0, 0, 0);

    public MachineProgressPayload {
        if (energy < 0 || energy > MachineBlockEntity.ENERGY_CAPACITY || progress < 0 || duration < 0 || progress > duration)
            throw new IllegalArgumentException("Invalid machine progress");
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineProgressPayload> CODEC = new StreamCodec<>() {
        @Override public MachineProgressPayload decode(RegistryFriendlyByteBuf buffer) {
            return new MachineProgressPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, MachineProgressPayload value) {
            buffer.writeVarInt(value.containerId); buffer.writeVarInt(value.energy);
            buffer.writeVarInt(value.progress); buffer.writeVarInt(value.duration);
        }
    };

    @Override public Type<MachineProgressPayload> type() { return TYPE; }
}
