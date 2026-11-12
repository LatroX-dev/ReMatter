package dev.latrox.rematter.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.ReMatter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Owner-only menu detail view. Discovery permutations, payment receipts and other identities never enter this payload. */
public record MachineExtraPayload(int containerId,String json) implements CustomPacketPayload {
    public static final int MAX_CHARS=262144;
    public static final Type<MachineExtraPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"machine_extra"));
    public MachineExtraPayload{if(json.length()>MAX_CHARS)throw new IllegalArgumentException("Machine detail view too large");}
    public JsonObject data(){return JsonParser.parseString(json).getAsJsonObject();}
    public static final StreamCodec<RegistryFriendlyByteBuf,MachineExtraPayload> CODEC=new StreamCodec<>(){
        public MachineExtraPayload decode(RegistryFriendlyByteBuf b){return new MachineExtraPayload(b.readVarInt(),b.readUtf(MAX_CHARS));}
        public void encode(RegistryFriendlyByteBuf b,MachineExtraPayload p){b.writeVarInt(p.containerId);b.writeUtf(p.json,MAX_CHARS);}
    };
    public Type<MachineExtraPayload> type(){return TYPE;}
}
