package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Owner-authorized settings and bounded working-lane counters; never carries inventories. */
public record MachineSettingsPayload(int containerId,MachineSettings settings,long ventedTotal,long targetStock,
                                     int running,int capacity,int tier,boolean mayVent) implements CustomPacketPayload {
    public static final Type<MachineSettingsPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"machine_settings"));
    public static final MachineSettingsPayload EMPTY=new MachineSettingsPayload(-1,MachineSettings.DEFAULT,0,0,0,1,1,false);
    public MachineSettingsPayload {
        if(ventedTotal<0||targetStock< -1||running<0||running>capacity||capacity<1||capacity>4||tier<1||tier>2)throw new IllegalArgumentException("Invalid machine settings view");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,MachineSettingsPayload> CODEC=new StreamCodec<>() {
        public MachineSettingsPayload decode(RegistryFriendlyByteBuf b) {
            return new MachineSettingsPayload(b.readVarInt(),MachineConfigurationPayload.read(b),b.readVarLong(),b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean());
        }
        public void encode(RegistryFriendlyByteBuf b,MachineSettingsPayload p) {
            b.writeVarInt(p.containerId);MachineConfigurationPayload.write(b,p.settings);b.writeVarLong(p.ventedTotal);b.writeVarLong(p.targetStock);
            b.writeVarInt(p.running);b.writeVarInt(p.capacity);b.writeVarInt(p.tier);b.writeBoolean(p.mayVent);
        }
    };
    public Type<MachineSettingsPayload> type() {return TYPE;}
}
