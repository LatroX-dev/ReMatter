package dev.latrox.rematter.network;

import static org.junit.jupiter.api.Assertions.*;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.client.ClientResearchState;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResearchKnowledgePayloadTest {
    @AfterEach void logout() { ClientResearchState.clear(); }
    private static ResearchKnowledgeWireCodec.State small(boolean known) {
        return new ResearchKnowledgeWireCodec.State(true, true, known ? Set.of("minecraft:iron_ingot") : Set.of(), Set.of(BuiltinChemistry.IRON));
    }
    private static byte[] zip(java.util.function.Consumer<DataOutputStream> writer) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var compressed = new DeflaterOutputStream(bytes); var output = new DataOutputStream(compressed)) { writer.accept(output); }
        return bytes.toByteArray();
    }
    @Test void compressedKnowledgeAndActualChunkCodecRoundTripWithoutCatalogueOrdinals() {
        var expected = small(true); assertEquals(expected, ResearchKnowledgeWireCodec.decode(ResearchKnowledgeWireCodec.encode(expected)));
        for (var fragment : ResearchKnowledgePayload.chunks(1, expected)) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                ResearchKnowledgePayload.CODEC.encode(buffer, fragment);
                var read = ResearchKnowledgePayload.CODEC.decode(buffer); assertEquals(0, buffer.readableBytes());
                assertEquals(fragment.update(), read.update()); assertArrayEquals(fragment.bytes(), read.bytes());
                var copy = read.bytes(); copy[0] ^= 1; assertArrayEquals(fragment.bytes(), read.bytes());
            } finally { buffer.release(); }
        }
    }
    @Test void outOfOrderChunksStayUnreadyUntilCompleteAndStaleSnapshotsNeverRestoreRevokedKnowledge() {
        var random = new Random(17); var ids = new HashSet<String>();
        for (int i = 0; i < 8000; i++) ids.add("test:material_" + i + "_" + Long.toUnsignedString(random.nextLong(), 16));
        var full = new ResearchKnowledgeWireCodec.State(true, true, ids, Set.of(BuiltinChemistry.COPPER));
        var chunks = ResearchKnowledgePayload.chunks(4, full); assertTrue(chunks.size() > 1);
        for (int i = chunks.size() - 1; i >= 0; i--) {
            ClientResearchState.accept(chunks.get(i)); if (i != 0) assertFalse(ClientResearchState.ready());
        }
        assertTrue(ClientResearchState.ready()); assertTrue(ClientResearchState.known(ids.iterator().next()));
        ResearchKnowledgePayload.chunks(5, small(false)).forEach(ClientResearchState::accept);
        chunks.forEach(ClientResearchState::accept);
        assertTrue(ClientResearchState.ready()); assertFalse(ClientResearchState.known(ids.iterator().next()));
        assertFalse(ClientResearchState.known("minecraft:diamond_sword"));
        ClientResearchState.clear(); assertFalse(ClientResearchState.ready()); assertFalse(ClientResearchState.known("minecraft:iron_ingot"));
    }
    @Test void disabledResearchStillUsesExactServerPermissionAndUnavailableStateGrantsNothing() {
        var disabled = new ResearchKnowledgeWireCodec.State(false, true, Set.of("minecraft:sand"), Set.of());
        ResearchKnowledgePayload.chunks(1, disabled).forEach(ClientResearchState::accept);
        assertFalse(ClientResearchState.enabled()); assertTrue(ClientResearchState.known("minecraft:sand")); assertFalse(ClientResearchState.known("minecraft:nether_star"));
        ResearchKnowledgePayload.chunks(2, new ResearchKnowledgeWireCodec.State(true, false, Set.of(), Set.of())).forEach(ClientResearchState::accept);
        assertTrue(ClientResearchState.ready()); assertFalse(ClientResearchState.available()); assertFalse(ClientResearchState.known("minecraft:sand"));
        assertThrows(IllegalArgumentException.class, () -> new ResearchKnowledgeWireCodec.State(true, false, Set.of("minecraft:sand"), Set.of()));
    }
    @Test void malformedFragmentsAndCompressedRecordsFailClosed() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ResearchKnowledgePayload(1, 0, 100000, 5, new byte[5]));
        assertThrows(IllegalArgumentException.class, () -> new ResearchKnowledgePayload(0, 0, 1, 5, new byte[5]));
        assertThrows(IllegalArgumentException.class, () -> ResearchKnowledgeWireCodec.decode(new byte[]{1, 2, 3}));
        var oversized = zip(out -> { try { out.writeInt(1); out.writeBoolean(true); out.writeBoolean(true); out.writeInt(ChemistryRegistry.MAX_SYNTHESIS_ENTRIES + 1); } catch (Exception invalid) { throw new RuntimeException(invalid); } });
        assertThrows(IllegalArgumentException.class, () -> ResearchKnowledgeWireCodec.decode(oversized));
        var duplicates = zip(out -> { try { out.writeInt(1); out.writeBoolean(true); out.writeBoolean(true); out.writeInt(2); out.writeUTF("minecraft:sand"); out.writeUTF("minecraft:sand"); out.writeInt(0); } catch (Exception invalid) { throw new RuntimeException(invalid); } });
        assertThrows(IllegalArgumentException.class, () -> ResearchKnowledgeWireCodec.decode(duplicates));
        ResearchKnowledgePayload.chunks(1, small(true)).forEach(ClientResearchState::accept);
        assertTrue(ClientResearchState.ready());
        assertThrows(IllegalArgumentException.class, () -> ClientResearchState.accept(new ResearchKnowledgePayload(2, 0, 1, 3, new byte[]{1, 2, 3})));
        assertFalse(ClientResearchState.ready()); assertFalse(ClientResearchState.known("minecraft:iron_ingot"));
    }
    @Test void learnedNotificationCodecCarriesOnlyTheCompletedPublicFamily() {
        var notice = new ResearchLearnedPayload("rematter:iron", "Iron", false);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try { ResearchLearnedPayload.CODEC.encode(buffer, notice); assertEquals(notice, ResearchLearnedPayload.CODEC.decode(buffer)); assertEquals(0, buffer.readableBytes()); }
        finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new ResearchLearnedPayload("bad id", "Iron", false));
        assertThrows(IllegalArgumentException.class, () -> new ResearchLearnedPayload("rematter:iron", "", false));
    }
}
