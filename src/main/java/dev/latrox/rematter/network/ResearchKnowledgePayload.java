package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.client.ClientResearchState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Chunked server-authorized knowledge snapshot, independent of catalogue ordinals. */
public record ResearchKnowledgePayload(long update, int index, int parts, int compressedLength, byte[] bytes) implements CustomPacketPayload {
    public static final int CHUNK_BYTES = 32_768;
    public static final int MAX_PARTS = (ResearchKnowledgeWireCodec.MAX_COMPRESSED_BYTES + CHUNK_BYTES - 1) / CHUNK_BYTES;
    public static final Type<ResearchKnowledgePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "research_knowledge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchKnowledgePayload> CODEC = new StreamCodec<>() {
        @Override public ResearchKnowledgePayload decode(RegistryFriendlyByteBuf buffer) {
            return new ResearchKnowledgePayload(buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(CHUNK_BYTES));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, ResearchKnowledgePayload payload) {
            buffer.writeVarLong(payload.update); buffer.writeVarInt(payload.index); buffer.writeVarInt(payload.parts); buffer.writeVarInt(payload.compressedLength); buffer.writeByteArray(payload.bytes);
        }
    };
    public ResearchKnowledgePayload {
        if (update < 1 || compressedLength < 1 || compressedLength > ResearchKnowledgeWireCodec.MAX_COMPRESSED_BYTES || parts < 1 || parts > MAX_PARTS
                || parts != (compressedLength + CHUNK_BYTES - 1) / CHUNK_BYTES || index < 0 || index >= parts
                || bytes.length != Math.min(CHUNK_BYTES, compressedLength - index * CHUNK_BYTES)) throw new IllegalArgumentException("Invalid research knowledge fragment");
        bytes = bytes.clone();
    }
    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static List<ResearchKnowledgePayload> chunks(long update, ResearchKnowledgeWireCodec.State state) {
        byte[] bytes = ResearchKnowledgeWireCodec.encode(state); int parts = (bytes.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        var result = new ArrayList<ResearchKnowledgePayload>(parts);
        for (int index = 0; index < parts; index++) result.add(new ResearchKnowledgePayload(update, index, parts, bytes.length,
                Arrays.copyOfRange(bytes, index * CHUNK_BYTES, Math.min(bytes.length, (index + 1) * CHUNK_BYTES))));
        return List.copyOf(result);
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> ClientResearchState.accept(payload));
    }
}
