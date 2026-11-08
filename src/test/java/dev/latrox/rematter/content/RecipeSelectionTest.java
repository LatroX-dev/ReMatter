package dev.latrox.rematter.content;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class RecipeSelectionTest {
    @Test void removedOrDisallowedSelectionNeverBecomesTheFirstDifferentProduct() {
        var restored = RecipeSelection.restore("example:removed_material", List.of("minecraft:gold_ingot", "minecraft:iron_ingot"), true);
        assertTrue(restored.unavailable());
        assertEquals(-1, restored.index());
        assertEquals("example:removed_material", restored.id());
        var returned = RecipeSelection.restore(restored.id(), List.of("minecraft:gold_ingot", "example:removed_material"), true);
        assertEquals(1, returned.index());
        assertFalse(returned.unavailable());
    }

    @Test void sortingAndLargeCatalogueExpansionKeepTheExactSavedIdentity() {
        List<String> ids = IntStream.range(0, 2_000).mapToObj(i -> "example:material_" + i).toList();
        var restored = RecipeSelection.restore("example:material_1500", ids, true);
        assertEquals(1_500, restored.index());
        assertEquals("example:material_1500", restored.id());
        assertEquals(499, RecipeSelection.restore(restored.id(), ids.reversed(), true).index());
    }

    @Test void OnlyANewEmptySelectionMayChooseTheFirstRecipe() {
        assertEquals(new RecipeSelection(0, "example:first"), RecipeSelection.restore("", List.of("example:first"), true));
        assertEquals(new RecipeSelection(-1, ""), RecipeSelection.restore("", List.of(), true));
        assertTrue(RecipeSelection.restore("example:unknown", List.of(), true).unavailable());
    }

    @Test void manualMachinesWaitForSelectionWhileAutomaticMachinesTakeTheFirstRecipe() {
        List<String> ids = List.of("example:first", "example:chosen");
        assertEquals(new RecipeSelection(-1, ""), RecipeSelection.restore("", ids, false));
        assertEquals(new RecipeSelection(0, "example:first"), RecipeSelection.restore("", ids, true));
        assertEquals(new RecipeSelection(1, "example:chosen"), RecipeSelection.restore("example:chosen", ids, false));
        assertTrue(RecipeSelection.restore("example:removed", ids, false).unavailable());
    }
}
