package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A request names only an action and selection; prices, permissions and results are resolved by the server. */
public record MachineActionPayload(int containerId,Action action,String target,long amount) implements CustomPacketPayload {
    public enum Action { LOAD_PRIMAL, CONVERT_SELECT, CONVERT_START, CONVERT_CANCEL, DERIVE, DISCOVER, RESEARCH_RESUME, RESEARCH_PAUSE, RESEARCH_REFUND, ASSAY }
    public static final Type<MachineActionPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"machine_action"));
    public MachineActionPayload {if(target==null||target.length()>512||amount<0||amount>256000)throw new IllegalArgumentException("Invalid machine action");}
    public static final StreamCodec<RegistryFriendlyByteBuf,MachineActionPayload> CODEC=new StreamCodec<>(){
        public MachineActionPayload decode(RegistryFriendlyByteBuf b){return new MachineActionPayload(b.readVarInt(),b.readEnum(Action.class),b.readUtf(512),b.readVarLong());}
        public void encode(RegistryFriendlyByteBuf b,MachineActionPayload p){b.writeVarInt(p.containerId);b.writeEnum(p.action);b.writeUtf(p.target,512);b.writeVarLong(p.amount);}
    };
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("1");
        registrar.playToServer(TYPE,CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player&&player.containerMenu instanceof MachineMenu menu&&menu.containerId==p.containerId)menu.extendedAction(player,p);});
        registrar.playToClient(MachineExtraPayload.TYPE,MachineExtraPayload.CODEC,(p,c)->{if(c.player().containerMenu instanceof MachineMenu menu&&menu.containerId==p.containerId())menu.acceptExtras(p);});
    }
    public Type<MachineActionPayload> type(){return TYPE;}
}
