package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.energy.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Exact energy is sent only to the authorized viewer; coarse exterior lights use the normal block state. */
public record EnergyCubePayload(int containerId,int tier,int energy,int sideModes,boolean locked) implements CustomPacketPayload {
    public static final Type<EnergyCubePayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"energy_cube"));
    public EnergyCubePayload{
        var definition=EnergyCubeTier.byIndex(tier);
        if(containerId<0||energy<0||energy>definition.capacity()||sideModes<0||(sideModes>>>12)!=0)throw new IllegalArgumentException("Invalid energy cube snapshot");
        for(int i=0;i<6;i++)if(((sideModes>>>(2*i))&3)>=EnergyCubeSideMode.values().length)throw new IllegalArgumentException("Unknown cube side role");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,EnergyCubePayload> CODEC=new StreamCodec<>(){
        public EnergyCubePayload decode(RegistryFriendlyByteBuf buffer){return new EnergyCubePayload(buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readBoolean());}
        public void encode(RegistryFriendlyByteBuf buffer,EnergyCubePayload payload){buffer.writeVarInt(payload.containerId);buffer.writeVarInt(payload.tier);buffer.writeVarInt(payload.energy);buffer.writeVarInt(payload.sideModes);buffer.writeBoolean(payload.locked);}
    };
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("1").playToClient(TYPE,CODEC,(payload,context)->{
            if(context.player().containerMenu instanceof EnergyCubeMenu menu&&menu.containerId==payload.containerId)menu.accept(payload);
        });
    }
    @Override public Type<EnergyCubePayload> type(){return TYPE;}
}
