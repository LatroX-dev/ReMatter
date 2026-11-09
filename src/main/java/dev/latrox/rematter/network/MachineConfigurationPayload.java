package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSettings;
import java.util.HashSet;
import java.util.TreeSet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Configuration applies only to the currently open, authorized server menu. */
public record MachineConfigurationPayload(int containerId,MachineSettings settings) implements CustomPacketPayload {
    public static final Type<MachineConfigurationPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"machine_configuration"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MachineConfigurationPayload> CODEC=new StreamCodec<>() {
        public MachineConfigurationPayload decode(RegistryFriendlyByteBuf b) {return new MachineConfigurationPayload(b.readVarInt(),read(b));}
        public void encode(RegistryFriendlyByteBuf b,MachineConfigurationPayload p) {b.writeVarInt(p.containerId);write(b,p.settings);}
    };
    public static MachineSettings read(RegistryFriendlyByteBuf b) {
        long target=b.readVarLong();String substance=b.readUtf(256),phase=b.readUtf(16);int count=b.readVarInt();
        if(count<0||count>32)throw new IllegalArgumentException("Too many gas settings");
        var gases=new HashSet<String>();for(int i=0;i<count;i++)if(!gases.add(b.readUtf(256)))throw new IllegalArgumentException("Duplicate gas setting");
        return new MachineSettings(target,substance,phase,gases);
    }
    public static void write(RegistryFriendlyByteBuf b,MachineSettings s) {
        b.writeVarLong(s.target());b.writeUtf(s.targetSubstance(),256);b.writeUtf(s.targetPhase(),16);b.writeVarInt(s.ventedGases().size());
        for(String gas:new TreeSet<>(s.ventedGases()))b.writeUtf(gas,256);
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("1");
        registrar.playToServer(TYPE,CODEC,(payload,context)-> {
            if(context.player() instanceof ServerPlayer player&&player.containerMenu instanceof MachineMenu menu&&menu.containerId==payload.containerId)
                menu.configure(player,payload.settings);
        });
        registrar.playToClient(MachineSettingsPayload.TYPE,MachineSettingsPayload.CODEC,(payload,context)-> {
            if(context.player().containerMenu instanceof MachineMenu menu&&menu.containerId==payload.containerId())menu.acceptSettings(payload);
        });
    }
    public Type<MachineConfigurationPayload> type() {return TYPE;}
}
