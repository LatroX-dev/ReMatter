package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.CustomNames;
import dev.latrox.rematter.energy.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Exact energy is sent only to the authorized viewer; coarse exterior lights use the normal block state. */
public record EnergyCubePayload(int containerId,int tier,long energy,int sideModes,boolean locked,String customName) implements CustomPacketPayload {
    public static final Type<EnergyCubePayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"energy_cube"));
    public EnergyCubePayload(int containerId,int tier,long energy,int sideModes,boolean locked){
        this(containerId,tier,energy,sideModes,locked,"");
    }
    public EnergyCubePayload{
        var definition=EnergyCubeTier.byIndex(tier);
        if(containerId<0||energy<0L||energy>definition.capacity()||sideModes<0||(sideModes>>>12)!=0)throw new IllegalArgumentException("Invalid energy cube snapshot");
        for(int i=0;i<6;i++)if(((sideModes>>>(2*i))&3)>=EnergyCubeSideMode.values().length)throw new IllegalArgumentException("Unknown cube side role");
        customName=CustomNames.sanitize(customName);
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,EnergyCubePayload> CODEC=new StreamCodec<>(){
        public EnergyCubePayload decode(RegistryFriendlyByteBuf buffer){return new EnergyCubePayload(buffer.readVarInt(),buffer.readVarInt(),buffer.readVarLong(),buffer.readVarInt(),buffer.readBoolean(),buffer.readUtf(CustomNames.MAX_LENGTH));}
        public void encode(RegistryFriendlyByteBuf buffer,EnergyCubePayload payload){buffer.writeVarInt(payload.containerId);buffer.writeVarInt(payload.tier);buffer.writeVarLong(payload.energy);buffer.writeVarInt(payload.sideModes);buffer.writeBoolean(payload.locked);buffer.writeUtf(payload.customName,CustomNames.MAX_LENGTH);}
    };
    public record Rename(int containerId,String customName) implements CustomPacketPayload {
        public static final Type<Rename> TYPE=new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"energy_cube_rename"));
        public Rename{customName=CustomNames.sanitize(customName);}
        public static final StreamCodec<RegistryFriendlyByteBuf,Rename> CODEC=new StreamCodec<>(){
            public Rename decode(RegistryFriendlyByteBuf buf){return new Rename(buf.readVarInt(),buf.readUtf(CustomNames.MAX_LENGTH));}
            public void encode(RegistryFriendlyByteBuf buf,Rename payload){buf.writeVarInt(payload.containerId);buf.writeUtf(payload.customName,CustomNames.MAX_LENGTH);}
        };
        @Override public Type<Rename> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("1");
        registrar.playToClient(TYPE,CODEC,(payload,context)->{
            if(context.player().containerMenu instanceof EnergyCubeMenu menu&&menu.containerId==payload.containerId)menu.accept(payload);
        });
        registrar.playToServer(Rename.TYPE,Rename.CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer sp&&sp.containerMenu instanceof EnergyCubeMenu menu&&menu.containerId==payload.containerId)
                menu.renameFromClient(sp,payload.customName());
        });
    }
    @Override public Type<EnergyCubePayload> type(){return TYPE;}
}
