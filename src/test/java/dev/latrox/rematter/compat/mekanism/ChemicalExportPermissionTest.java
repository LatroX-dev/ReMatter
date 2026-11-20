package dev.latrox.rematter.compat.mekanism;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ShippedChemistry;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKind;
import dev.latrox.rematter.chemistry.SynthesisCategory;
import dev.latrox.rematter.chemistry.SynthesisPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the shipped gas permissions against the Mekanism chemical mappings. */
class ChemicalExportPermissionTest {
    private static final ChemistryRegistry REGISTRY = ShippedChemistry.standalone();

    @Test
    void everyMappedChemicalCanLeaveStorageUnderTheShippedPermissions() {
        for (var mapping : ChemicalMappings.ALL) {
            assertTrue(ChemicalMappings.exportable(REGISTRY.synthesisPolicy(), mapping), mapping.chemicalId());
        }
    }

    @Test
    void gasPermissionsReleaseExactlyTheMappedPlainSubstances() {
        var permitted = REGISTRY.synthesisPolicy().entries().entrySet().stream()
                .filter(entry -> entry.getValue() == SynthesisCategory.GAS).map(Map.Entry::getKey).collect(Collectors.toSet());
        var mapped = ChemicalMappings.ALL.stream().map(mapping -> mapping.matter().id().toString()).collect(Collectors.toSet());
        assertEquals(mapped, permitted);
        for (String id : permitted) {
            var definition = REGISTRY.definition(SubstanceId.parse(id)).orElseThrow();
            assertNotEquals(SubstanceKind.MIXTURE, definition.kind(), id);
            assertTrue(definition.supports(Phase.GAS), id);
        }
    }

    @Test
    void neitherItemReleasesNorForbiddenCategoriesExportAChemical() {
        for (var mapping : ChemicalMappings.ALL) {
            String substance = mapping.matter().id().toString();
            for (var category : SynthesisCategory.values()) {
                if (!category.permitted()) {
                    assertThrows(IllegalArgumentException.class, () -> new SynthesisPolicy(Map.of(substance, category)));
                } else if (category != SynthesisCategory.GAS) {
                    var policy = new SynthesisPolicy(Map.of(substance, category));
                    assertFalse(ChemicalMappings.exportable(policy, mapping), mapping.chemicalId() + " via " + category);
                }
            }
        }
    }
}
