package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.SubstanceId;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Sent only after an earned learning commit. Hidden Discovery candidates never use this payload. */
public record ResearchLearnedPayload(String family, String name, boolean decompositionOnly) implements CustomPacketPayload {
    public static final Type<ResearchLearnedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "research_learned"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchLearnedPayload> CODEC = new StreamCodec<>() {
        @Override public ResearchLearnedPayload decode(RegistryFriendlyByteBuf buffer) { return new ResearchLearnedPayload(buffer.readUtf(256), buffer.readUtf(512), buffer.readBoolean()); }
        @Override public void encode(RegistryFriendlyByteBuf buffer, ResearchLearnedPayload payload) { buffer.writeUtf(payload.family, 256); buffer.writeUtf(payload.name, 512); buffer.writeBoolean(payload.decompositionOnly); }
    };
    public ResearchLearnedPayload {
        SubstanceId.parse(family);
        if (family.length() > 256 || name.isBlank() || name.length() > 512) throw new IllegalArgumentException("Invalid learned family notice");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> dev.latrox.rematter.client.ClientResearchToasts.show(payload));
    }
}
