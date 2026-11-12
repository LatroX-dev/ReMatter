package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.network.ResearchKnowledgePayload;
import dev.latrox.rematter.network.ResearchKnowledgeWireCodec;
import java.util.Arrays;
import java.util.Set;

/** A display-only copy of the current player's authorized view. Never grants a server operation. */
public final class ClientResearchState {
    private static ResearchKnowledgeWireCodec.State current = new ResearchKnowledgeWireCodec.State(true, false, Set.of(), Set.of());
    private static long revision, applied, pending;
    private static byte[][] fragments;
    private static int compressedLength, received;
    private static boolean ready;
    private ClientResearchState() {}
    public static boolean ready() { return ready; }
    public static boolean enabled() { return current.enabled(); }
    public static boolean available() { return ready && current.available(); }
    public static long revision() { return revision; }
    public static boolean known(String exactItemOrFluidId) { return available() && current.allowedSynthesis().contains(exactItemOrFluidId); }
    public static boolean knownSubstance(SubstanceKey key) { return available() && current.knownSubstances().contains(key); }
    public static void clear() {
        current = new ResearchKnowledgeWireCodec.State(true, false, Set.of(), Set.of()); ready = false; applied = pending = 0;
        fragments = null; received = compressedLength = 0; revision++;
    }
    public static void accept(ResearchKnowledgePayload payload) {
        if (payload.update() <= applied || payload.update() < pending) return;
        if (payload.update() > pending) {
            pending = payload.update(); fragments = new byte[payload.parts()][]; compressedLength = payload.compressedLength(); received = 0; ready = false; revision++;
        }
        if (fragments == null || fragments.length != payload.parts() || compressedLength != payload.compressedLength()) { discard(); throw new IllegalArgumentException("Inconsistent research fragments"); }
        byte[] bytes = payload.bytes();
        if (fragments[payload.index()] != null) {
            if (!Arrays.equals(fragments[payload.index()], bytes)) { discard(); throw new IllegalArgumentException("Conflicting research fragment"); }
            return;
        }
        fragments[payload.index()] = bytes; received++;
        if (received != fragments.length) return;
        byte[] combined = new byte[compressedLength];
        for (int i = 0; i < fragments.length; i++) System.arraycopy(fragments[i], 0, combined, i * ResearchKnowledgePayload.CHUNK_BYTES, fragments[i].length);
        try { current = ResearchKnowledgeWireCodec.decode(combined); applied = pending; ready = true; revision++; }
        catch (RuntimeException invalid) { discard(); throw invalid; }
        fragments = null; received = compressedLength = 0;
    }
    private static void discard() { fragments = null; received = compressedLength = 0; ready = false; revision++; }
}
