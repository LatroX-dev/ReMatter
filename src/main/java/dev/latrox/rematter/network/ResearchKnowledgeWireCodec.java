package dev.latrox.rematter.network;

import dev.latrox.rematter.chemistry.*;
import java.io.*;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Display permissions only: no inventories, prices, research sessions or hidden candidate order. */
public final class ResearchKnowledgeWireCodec {
    public static final int MAX_COMPRESSED_BYTES = 2_097_152, MAX_RAW_BYTES = 25_165_824, MAX_ID_LENGTH = 256;
    private static final int VERSION = 1, MAX_KEYS = 3 * ChemistryRegistry.MAX_SUBSTANCES;
    public record State(boolean enabled, boolean available, Set<String> allowedSynthesis, Set<SubstanceKey> knownSubstances) {
        public State {
            allowedSynthesis = Set.copyOf(allowedSynthesis); knownSubstances = Set.copyOf(knownSubstances);
            if (allowedSynthesis.size() > ChemistryRegistry.MAX_SYNTHESIS_ENTRIES || knownSubstances.size() > MAX_KEYS)
                throw new IllegalArgumentException("Research display exceeds catalogue bounds");
            allowedSynthesis.forEach(ResearchKnowledgeWireCodec::validId);
            knownSubstances.forEach(key -> validId(key.id().toString()));
            if (!available && (!allowedSynthesis.isEmpty() || !knownSubstances.isEmpty())) throw new IllegalArgumentException("Unavailable knowledge exposes no permissions");
        }
    }
    private ResearchKnowledgeWireCodec() {}
    public static byte[] encode(State state) {
        try {
            var raw = new LimitedBytes(MAX_RAW_BYTES); var output = new DataOutputStream(raw);
            output.writeInt(VERSION); output.writeBoolean(state.enabled()); output.writeBoolean(state.available());
            output.writeInt(state.allowedSynthesis().size());
            for (String id : new TreeSet<>(state.allowedSynthesis())) output.writeUTF(id);
            output.writeInt(state.knownSubstances().size());
            for (var key : new TreeSet<>(state.knownSubstances())) { output.writeUTF(key.id().toString()); output.writeByte(key.phase().ordinal()); }
            output.flush(); var compressed = new LimitedBytes(MAX_COMPRESSED_BYTES);
            try (var zip = new DeflaterOutputStream(compressed)) { raw.writeTo(zip); }
            return compressed.toByteArray();
        } catch (IOException invalid) { throw new IllegalArgumentException("Cannot encode research display", invalid); }
    }
    public static State decode(byte[] compressed) {
        if (compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES) throw new IllegalArgumentException("Invalid research display size");
        try {
            var raw = new LimitedBytes(MAX_RAW_BYTES);
            try (var zip = new InflaterInputStream(new ByteArrayInputStream(compressed))) { zip.transferTo(raw); }
            var input = new DataInputStream(new ByteArrayInputStream(raw.toByteArray()));
            if (input.readInt() != VERSION) throw new IllegalArgumentException("Unsupported research display version");
            boolean enabled = input.readBoolean(), available = input.readBoolean();
            var allowed = new TreeSet<String>(); int count = count(input, ChemistryRegistry.MAX_SYNTHESIS_ENTRIES);
            for (int i = 0; i < count; i++) if (!allowed.add(validId(input.readUTF()))) throw new IllegalArgumentException("Duplicate synthesis permission");
            var known = new TreeSet<SubstanceKey>(); count = count(input, MAX_KEYS);
            for (int i = 0; i < count; i++) {
                var id = SubstanceId.parse(validId(input.readUTF())); int phase = input.readUnsignedByte();
                if (phase >= Phase.values().length || !known.add(new SubstanceKey(id, Phase.values()[phase]))) throw new IllegalArgumentException("Invalid known substance");
            }
            if (input.read() != -1) throw new IllegalArgumentException("Trailing research display bytes");
            return new State(enabled, available, allowed, known);
        } catch (IOException invalid) { throw new IllegalArgumentException("Invalid research display", invalid); }
    }
    private static String validId(String value) {
        if (value.length() > MAX_ID_LENGTH) throw new IllegalArgumentException("Research display ID too long");
        SubstanceId.parse(value); return value;
    }
    private static int count(DataInputStream input, int maximum) throws IOException {
        int count = input.readInt(); if (count < 0 || count > maximum) throw new IllegalArgumentException("Research display count outside bounds"); return count;
    }
    private static final class LimitedBytes extends ByteArrayOutputStream {
        private final int limit;
        LimitedBytes(int limit) { this.limit = limit; }
        @Override public synchronized void write(int value) { check(1); super.write(value); }
        @Override public synchronized void write(byte[] values, int offset, int length) { check(length); super.write(values, offset, length); }
        private void check(int length) { if (length < 0 || count > limit - length) throw new IllegalArgumentException("Research display exceeds byte bound"); }
    }
}
