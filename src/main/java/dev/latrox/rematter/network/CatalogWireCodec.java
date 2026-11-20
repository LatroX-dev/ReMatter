package dev.latrox.rematter.network;

import dev.latrox.rematter.chemistry.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Versioned, bounded catalogue transport. Contains recipes, never player inventories. */
public final class CatalogWireCodec {
    public static final int MAX_COMPRESSED_BYTES = 524_288;
    private static final int MAX_RAW_BYTES = 16_777_216;
    private static final int MAX_RECORDS = ChemistryRegistry.MAX_SYNTHESIS_ENTRIES;
    private static final int MAX_AMOUNTS = 1_024;
    private static final int VERSION = 9;

    private CatalogWireCodec() {}

    public static byte[] encode(ChemistryRegistry registry) {
        try {
            BoundedBytes raw = new BoundedBytes(MAX_RAW_BYTES);
            DataOutputStream out = new DataOutputStream(raw);
            out.writeInt(VERSION);
            writeCount(out, registry.substances().size(), ChemistryRegistry.MAX_SUBSTANCES);
            for (SubstanceDefinition value : registry.substances()) {
                string(out, value.id().toString());
                string(out, value.translationKey());
                string(out, value.symbol());
                string(out, value.kind().name());
                writeCount(out, value.composition().elements().size(), MAX_AMOUNTS);
                for (var element : value.composition().elements().entrySet()) {
                    string(out, element.getKey().toString()); out.writeLong(element.getValue());
                }
                writeCount(out, value.phases().size(), Phase.values().length);
                for (Phase phase : Phase.values()) if (value.phases().contains(phase)) string(out, phase.name());
            }
            writeCount(out, registry.profiles().size(), ChemistryRegistry.MAX_MATERIAL_PROFILES);
            for (MaterialProfile value : registry.profiles()) {
                string(out, value.itemId()); out.writeInt(value.itemCount()); amounts(out, value.matter());
                out.writeLong(value.decompositionEnergy()); out.writeLong(value.synthesisEnergy()); out.writeInt(value.ticks());
            }
            writeCount(out, registry.fluidProfiles().size(), ChemistryRegistry.MAX_FLUID_PROFILES);
            for (FluidProfile value : registry.fluidProfiles()) {
                string(out, value.fluidId()); out.writeInt(value.amount()); amounts(out, value.matter());
                out.writeLong(value.decompositionEnergy()); out.writeLong(value.synthesisEnergy()); out.writeInt(value.ticks());
            }
            writeCount(out, registry.processes().size(), ChemistryRegistry.MAX_PROCESSES);
            for (ProcessRecipe value : registry.processes()) {
                string(out, value.id().toString()); string(out, value.type().name());
                amounts(out, value.inputs()); amounts(out, value.outputs());
                out.writeLong(value.energy()); out.writeInt(value.ticks()); string(out, value.requiresAdvancement());
                out.writeInt(value.minField());out.writeBoolean(value.requiresKnownOutputs());string(out,value.product()==null?"":value.product().toString());
            }
            writeCount(out, registry.environmentSources().size(), ChemistryRegistry.MAX_PROCESSES);
            for (EnvironmentSource value : registry.environmentSources()) {
                string(out, value.id().toString()); amounts(out, value.outputs());
                out.writeLong(value.energy()); out.writeInt(value.ticks()); out.writeBoolean(value.requiresOpenAir());
                writeCount(out, value.dimensions().size(), 32);
                for (String dimension : new TreeSet<>(value.dimensions())) string(out, dimension);
            }
            writeCount(out, registry.synthesisPolicy().entries().size(), MAX_RECORDS);
            for (var entry : registry.synthesisPolicy().entries().entrySet()) {
                string(out, entry.getKey()); string(out, entry.getValue().name());
                var requirement = registry.synthesisPolicy().requirement(entry.getKey());
                string(out, requirement.machine()); out.writeInt(requirement.tier()); string(out, requirement.advancement());
            }
            out.flush();
            BoundedBytes compressed = new BoundedBytes(MAX_COMPRESSED_BYTES);
            try (DeflaterOutputStream zip = new DeflaterOutputStream(compressed)) { raw.writeTo(zip); }
            return compressed.toByteArray();
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot encode chemistry catalogue", error);
        }
    }

    public static ChemistryRegistry decode(byte[] compressed) {
        if (compressed.length == 0 || compressed.length > MAX_COMPRESSED_BYTES) {
            throw new IllegalArgumentException("Invalid chemistry catalogue size");
        }
        try {
            BoundedBytes raw = new BoundedBytes(MAX_RAW_BYTES);
            try (InflaterInputStream zip = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
                zip.transferTo(raw);
            }
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw.toByteArray()));
            if (in.readInt() != VERSION) throw new IllegalArgumentException("Unsupported chemistry catalogue version");
            ChemistryRegistry.Builder builder = ChemistryRegistry.builder();
            int substances = count(in, ChemistryRegistry.MAX_SUBSTANCES);
            for (int i = 0; i < substances; i++) {
                SubstanceId id = SubstanceId.parse(string(in));
                String translation = string(in), symbol = string(in);
                SubstanceKind kind = SubstanceKind.valueOf(string(in));
                Map<SubstanceId, Long> composition = new TreeMap<>();
                int elements = count(in, MAX_AMOUNTS);
                for (int e = 0; e < elements; e++) {
                    if (composition.putIfAbsent(SubstanceId.parse(string(in)), in.readLong()) != null) {
                        throw new IllegalArgumentException("Duplicate element in catalogue");
                    }
                }
                EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
                int phaseCount = count(in, Phase.values().length);
                for (int p = 0; p < phaseCount; p++) {
                    if (!phases.add(Phase.valueOf(string(in)))) throw new IllegalArgumentException("Duplicate phase");
                }
                builder.addSubstance(new SubstanceDefinition(id, translation, symbol, kind, new Composition(composition), phases));
            }
            int profiles = count(in, ChemistryRegistry.MAX_MATERIAL_PROFILES);
            for (int i = 0; i < profiles; i++) {
                builder.addProfile(new MaterialProfile(string(in), in.readInt(), amounts(in), in.readLong(), in.readLong(), in.readInt()));
            }
            int fluids = count(in, ChemistryRegistry.MAX_FLUID_PROFILES);
            for (int i = 0; i < fluids; i++) {
                builder.addFluidProfile(new FluidProfile(string(in), in.readInt(), amounts(in), in.readLong(), in.readLong(), in.readInt()));
            }
            int processes = count(in, ChemistryRegistry.MAX_PROCESSES);
            for (int i = 0; i < processes; i++) {
                builder.addProcess(new ProcessRecipe(SubstanceId.parse(string(in)), ProcessType.valueOf(string(in)),
                        amounts(in), amounts(in), in.readLong(), in.readInt(), string(in),in.readInt(),in.readBoolean(),optionalId(string(in))));
            }
            int sources = count(in, ChemistryRegistry.MAX_PROCESSES);
            for (int i = 0; i < sources; i++) {
                var id = SubstanceId.parse(string(in)); var outputs = amounts(in); long energy = in.readLong(); int ticks = in.readInt();
                boolean open = in.readBoolean(); int dimensions = count(in, 32); var worlds = new TreeSet<String>();
                for (int d = 0; d < dimensions; d++) if (!worlds.add(string(in))) throw new IllegalArgumentException("Duplicate source dimension");
                builder.addEnvironmentSource(new EnvironmentSource(id, outputs, energy, ticks, worlds, open));
            }
            int permissions = count(in, MAX_RECORDS);
            for (int i = 0; i < permissions; i++) {
                String id = string(in); var category = SynthesisCategory.valueOf(string(in));
                builder.allowSynthesis(id, category, new SynthesisRequirement(string(in), in.readInt(), string(in)));
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing chemistry catalogue data");
            return builder.build();
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid chemistry catalogue", error);
        }
    }

    private static SubstanceId optionalId(String id){return id.isEmpty()?null:SubstanceId.parse(id);}

    private static void amounts(DataOutputStream out, Map<SubstanceKey, Long> values) throws IOException {
        writeCount(out, values.size(), MAX_AMOUNTS);
        for (var value : values.entrySet()) {
            string(out, value.getKey().id().toString()); string(out, value.getKey().phase().name()); out.writeLong(value.getValue());
        }
    }

    private static Map<SubstanceKey, Long> amounts(DataInputStream in) throws IOException {
        Map<SubstanceKey, Long> values = new TreeMap<>();
        int count = count(in, MAX_AMOUNTS);
        for (int i = 0; i < count; i++) {
            SubstanceKey key = SubstanceKey.of(string(in), Phase.valueOf(string(in)));
            if (values.putIfAbsent(key, in.readLong()) != null) throw new IllegalArgumentException("Duplicate quantity");
        }
        return values;
    }

    private static void writeCount(DataOutputStream out, int count, int maximum) throws IOException {
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Chemistry catalogue entry limit exceeded");
        out.writeInt(count);
    }

    private static int count(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid chemistry catalogue entry count");
        return count;
    }

    private static void string(DataOutputStream out, String value) throws IOException {
        if (value.length() > 512) throw new IllegalArgumentException("Chemistry catalogue string exceeds 512 characters");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeCount(out, bytes.length, 2_048); out.write(bytes);
    }

    private static String string(DataInputStream in) throws IOException {
        int count = count(in, 2_048);
        byte[] bytes = in.readNBytes(count);
        if (bytes.length != count) throw new EOFException("Truncated chemistry catalogue string");
        String value = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        if (value.length() > 512) throw new IllegalArgumentException("Chemistry catalogue string exceeds 512 characters");
        return value;
    }

    private static final class BoundedBytes extends ByteArrayOutputStream {
        private final int maximum;
        BoundedBytes(int maximum) { this.maximum = maximum; }
        @Override public synchronized void write(int value) {
            if (count >= maximum) throw new IllegalArgumentException("Chemistry catalogue exceeds size limit");
            super.write(value);
        }
        @Override public synchronized void write(byte[] bytes, int offset, int length) {
            if (length > maximum - count) throw new IllegalArgumentException("Chemistry catalogue exceeds size limit");
            super.write(bytes, offset, length);
        }
    }
}
