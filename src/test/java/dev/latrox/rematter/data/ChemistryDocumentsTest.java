package dev.latrox.rematter.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChemistryDocumentsTest {
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }

    @Test void aSubstanceWithoutSymbolIsValidAndKeepsAnEmptySymbol() {
        var substance = ChemistryDocuments.substance(SubstanceId.parse("example:mix"), json("""
                {"schema_version": 1, "translation_key": "substance.example.mix", "kind": "mixture",
                 "composition": {"rematter:carbon": 1}, "phases": ["solid"]}"""));
        assertEquals("", substance.symbol());
        assertTrue(substance.supports(Phase.SOLID));
    }

    @Test void disabledMustBeABooleanAndOnlyTheCurrentSchemaIsRead() {
        assertTrue(ChemistryDocuments.disabled(json("{\"disabled\": true}")));
        assertFalse(ChemistryDocuments.disabled(json("{}")));
        assertThrows(IllegalArgumentException.class, () -> ChemistryDocuments.disabled(json("{\"disabled\": \"true\"}")));
        assertDoesNotThrow(() -> ChemistryDocuments.requireSchema(json("{\"schema_version\": 1}")));
        assertThrows(IllegalArgumentException.class, () -> ChemistryDocuments.requireSchema(json("{\"schema_version\": 2}")));
        assertThrows(IllegalArgumentException.class, () -> ChemistryDocuments.requireSchema(json("{}")));
    }

    @Test void fractionalDuplicateOrNonPositiveAmountsAreRejected() {
        String profile = """
                {"item": "minecraft:snowball", "matter": [%s], "decomposition_energy": 1, "synthesis_energy": 1, "ticks": 1}""";
        assertEquals(1000L, ChemistryDocuments.materialProfile(json(profile.formatted(
                "{\"substance\": \"rematter:water\", \"phase\": \"solid\", \"amount\": 1000}"))).matter().values().iterator().next());
        for (String matter : new String[]{
                "{\"substance\": \"rematter:water\", \"phase\": \"solid\", \"amount\": 1.5}",
                "{\"substance\": \"rematter:water\", \"phase\": \"solid\", \"amount\": 0}",
                "{\"substance\": \"rematter:water\", \"phase\": \"solid\", \"amount\": 1}, {\"substance\": \"rematter:water\", \"phase\": \"solid\", \"amount\": 2}"})
            assertThrows(RuntimeException.class, () -> ChemistryDocuments.materialProfile(json(profile.formatted(matter))), matter);
    }
}
