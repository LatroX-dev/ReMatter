package dev.latrox.rematter.network;

import dev.latrox.rematter.chemistry.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

class CatalogWireCodecTest {
    @Test
    void completeCatalogueRetainsExactChemistryAndExplicitSynthesisPermissions() throws Exception {
        ChemistryRegistry original = BuiltinChemistry.create().toBuilder()
                .addProfile(new MaterialProfile("example:copper_sample", 9, Map.of(BuiltinChemistry.COPPER, 17L), 31, 59, 7))
                .addFluidProfile(new FluidProfile("example:molten_copper", 144, Map.of(BuiltinChemistry.COPPER, 1_000L), 1, 2, 3))
                .build();
        ChemistryRegistry received = CatalogWireCodec.decode(CatalogWireCodec.encode(original));
        assertEquals(java.util.List.copyOf(original.substances()), java.util.List.copyOf(received.substances()));
        assertEquals(java.util.List.copyOf(original.profiles()), java.util.List.copyOf(received.profiles()));
        assertEquals(java.util.List.copyOf(original.fluidProfiles()), java.util.List.copyOf(received.fluidProfiles()));
        assertEquals(java.util.List.copyOf(original.processes()), java.util.List.copyOf(received.processes()));
        assertEquals(original.synthesisPolicy().entries(), received.synthesisPolicy().entries());
        assertFalse(received.synthesisPolicy().allows("example:copper_sample"));
        ProcessRecipe electrolysis = received.recipe(SubstanceId.parse("rematter:water_electrolysis")).orElseThrow();
        assertEquals(Map.of(BuiltinChemistry.HYDROGEN, 1_000L, BuiltinChemistry.OXYGEN, 500L), electrolysis.outputs());
    }

    @Test
    void emptyCatalogueIsValidBeforeServerSynchronization() {
        ChemistryRegistry empty = CatalogWireCodec.decode(CatalogWireCodec.encode(ChemistryRegistry.builder().build()));
        assertTrue(empty.substances().isEmpty());
        assertTrue(empty.profiles().isEmpty());
        assertTrue(empty.fluidProfiles().isEmpty());
        assertTrue(empty.processes().isEmpty());
        assertTrue(empty.synthesisPolicy().entries().isEmpty());
    }

    @Test
    void truncatedCompressedAndUncompressedPayloadsAreRejected() throws Exception {
        byte[] wire = CatalogWireCodec.encode(BuiltinChemistry.create());
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.decode(Arrays.copyOf(wire, wire.length / 2)));
        byte[] raw = new InflaterInputStream(new ByteArrayInputStream(wire)).readAllBytes();
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.decode(compress(Arrays.copyOf(raw, raw.length - 1))));
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.decode(compress(Arrays.copyOf(raw, raw.length + 1))));
    }

    @Test
    void hostileSizeVersionAndCountFieldsAreRejectedBeforeAllocation() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.decode(new byte[CatalogWireCodec.MAX_COMPRESSED_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.decode(compress(ints(99, 0, 0, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.decode(compress(ints(1, 0, 0, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.decode(compress(ints(2, -1))));
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.decode(compress(ints(2, Integer.MAX_VALUE))));
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.decode(compress(ints(2, 1, Integer.MAX_VALUE))));
    }

    @Test
    void compressedPayloadCannotInflateBeyondTheBound() throws Exception {
        byte[] oversized = compress(new byte[16_777_217]);
        assertTrue(oversized.length < CatalogWireCodec.MAX_COMPRESSED_BYTES);
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.decode(oversized));
    }

    @Test
    void serverRejectsUntransportableNamesBeforeSync() {
        SubstanceId id = SubstanceId.parse("example:long_name");
        ChemistryRegistry catalogue = ChemistryRegistry.builder().addSubstance(new SubstanceDefinition(id,
                "a".repeat(513), "X", SubstanceKind.ELEMENT, Composition.element(id), java.util.Set.of(Phase.SOLID))).build();
        assertThrows(IllegalArgumentException.class, () -> CatalogWireCodec.encode(catalogue));
    }

    @Test void expandedCatalogueRoundTripsAllEntriesBeyondTheOldChoiceLimit() {
        var builder = BuiltinChemistry.create().toBuilder();
        for (int i = 0; i < 2_000; i++) {
            String id = "example:material_" + i;
            builder.addProfile(new MaterialProfile(id, i % 2 == 0 ? 9 : 1,
                    Map.of(BuiltinChemistry.COPPER, 1_000L + i), 10 + i, 20 + i, 100));
            if (i % 3 == 0) builder.allowSynthesis(id, SynthesisCategory.RAW_MATERIAL);
        }
        var original = builder.build();
        var restored = CatalogWireCodec.decode(CatalogWireCodec.encode(original));
        assertEquals(java.util.List.copyOf(original.profiles()), java.util.List.copyOf(restored.profiles()));
        assertEquals(original.synthesisPolicy().entries(), restored.synthesisPolicy().entries());
        assertTrue(restored.profile("example:material_1999").isPresent());
    }

    private static byte[] ints(int... values) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            for (int value : values) out.writeInt(value);
        }
        return bytes.toByteArray();
    }

    private static byte[] compress(byte[] raw) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) { out.write(raw); }
        return bytes.toByteArray();
    }
}
