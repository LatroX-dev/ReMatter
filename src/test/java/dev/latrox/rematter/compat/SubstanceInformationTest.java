package dev.latrox.rematter.compat;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubstanceInformationTest {
    @Test void waterUsesItsDeclaredFormulaCountsAndKeepsExactShownAmounts() {
        var registry = BuiltinChemistry.create();
        var key = SubstanceKey.of("rematter:water", Phase.LIQUID);
        var info = SubstanceInformation.inspect(registry, key, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, info.amount());
        assertEquals(Map.of("rematter:hydrogen", 2L, "rematter:oxygen", 1L), info.elements().stream()
                .collect(Collectors.toMap(element -> element.id().toString(), SubstanceInformation.Element::count)));
        assertTrue(info.elements().stream().allMatch(element -> element.definition().isPresent()));
        assertEquals("H₂O", info.definition().orElseThrow().symbol());
        assertEquals(0, SubstanceInformation.inspect(registry, key, 0).amount());
    }

    @Test void removedDefinitionsAndUnsupportedStoredPhasesKeepTheirOriginalIdentity() {
        var registry = BuiltinChemistry.create();
        var missing = SubstanceKey.of("removed:ancient_solution", Phase.GAS);
        var unsupported = SubstanceKey.of("rematter:hydrogen", Phase.SOLID);
        for (var key : java.util.List.of(missing, unsupported)) {
            var info = SubstanceInformation.inspect(registry, key, 12_345);
            assertEquals(key, info.key());
            assertEquals(12_345, info.amount());
            assertTrue(info.definition().isEmpty());
            assertTrue(info.elements().isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> SubstanceInformation.inspect(registry, missing, -1));
    }
}
