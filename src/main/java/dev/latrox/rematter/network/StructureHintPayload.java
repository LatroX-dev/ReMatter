package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.MachineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** One server-observed construction error for the authorized controller menu. */
public record StructureHintPayload(int containerId,BlockPos position,String expected,String orientation) implements CustomPacketPayload {
    public static final StructureHintPayload EMPTY=new StructureHintPayload(-1,BlockPos.ZERO,"","");
    public static final Type<StructureHintPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"structure_hint"));
    public StructureHintPayload {
        position=position.immutable();
        if(expected.length()>128||!java.util.Set.of("","up","down").contains(orientation))throw new IllegalArgumentException("Invalid structure hint");
    }
    public boolean present() {return !expected.isEmpty();}
    public static final StreamCodec<RegistryFriendlyByteBuf,StructureHintPayload> CODEC=new StreamCodec<>() {
        public StructureHintPayload decode(RegistryFriendlyByteBuf b) {return new StructureHintPayload(b.readVarInt(),b.readBlockPos(),b.readUtf(128),b.readUtf(8));}
        public void encode(RegistryFriendlyByteBuf b,StructureHintPayload p) {b.writeVarInt(p.containerId);b.writeBlockPos(p.position);b.writeUtf(p.expected,128);b.writeUtf(p.orientation,8);}
    };
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE,CODEC,(payload,context)-> {
            if(context.player().containerMenu instanceof MachineMenu menu&&menu.containerId==payload.containerId) {
                menu.acceptStructureHint(payload);dev.latrox.rematter.client.ClientStructureHints.accept(payload);
            }
        });
    }
    public Type<StructureHintPayload> type() {return TYPE;}
}
