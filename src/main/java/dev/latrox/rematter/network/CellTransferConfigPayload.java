package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.celltransfer.CellTransferMenu;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.CellPartitionData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/** One bounded delta per action, with the revision of the six physical Cells the player actually saw. */
public record CellTransferConfigPayload(int containerId,long revision,Action action,int slot,@Nullable SubstanceKey key,boolean value) implements CustomPacketPayload {
    public enum Action { TRANSFER,GLOBAL_ALL,GLOBAL_PRIMAL,GLOBAL_CLEAR,GLOBAL_TOGGLE,CELL_ANY,CELL_TOGGLE,CELL_TO_CONTENTS,CELL_CLEAR }
    public static final Type<CellTransferConfigPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"cell_transfer_configuration"));
    public CellTransferConfigPayload {
        boolean cell=action.name().startsWith("CELL_");boolean keyed=action==Action.GLOBAL_TOGGLE||action==Action.CELL_TOGGLE;
        if(containerId<0||revision<0||(cell?slot<0||slot>=6:slot!=-1)||keyed!=(key!=null)||key!=null&&(key.id().toString().length()>CellPartitionData.MAX_ID_LENGTH||ReservedSubstances.reserved(key)))throw new IllegalArgumentException("Invalid Cell Transfer action");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,CellTransferConfigPayload> CODEC=new StreamCodec<>(){
        public CellTransferConfigPayload decode(RegistryFriendlyByteBuf b){int id=b.readVarInt();long revision=b.readVarLong();var action=b.readEnum(Action.class);int slot=b.readVarInt();var key=b.readBoolean()?CellTransferPayload.readKey(b):null;return new CellTransferConfigPayload(id,revision,action,slot,key,b.readBoolean());}
        public void encode(RegistryFriendlyByteBuf b,CellTransferConfigPayload p){b.writeVarInt(p.containerId);b.writeVarLong(p.revision);b.writeEnum(p.action);b.writeVarInt(p.slot);b.writeBoolean(p.key!=null);if(p.key!=null)CellTransferPayload.writeKey(b,p.key);b.writeBoolean(p.value);}
    };
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("1");
        registrar.playToServer(TYPE,CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player&&player.containerMenu instanceof CellTransferMenu menu&&menu.containerId==payload.containerId)menu.configure(player,payload);
        });
        registrar.playToClient(CellTransferPayload.TYPE,CellTransferPayload.CODEC,(payload,context)->{
            if(context.player().containerMenu instanceof CellTransferMenu menu&&menu.containerId==payload.containerId())menu.accept(payload);
        });
    }
    @Override public Type<CellTransferConfigPayload> type(){return TYPE;}
}
