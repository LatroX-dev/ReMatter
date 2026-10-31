package dev.latrox.rematter.content;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class RecipeSelectionTest {
    @Test void removedOrDisallowedSelectionNeverBecomesTheFirstDifferentProduct() {
        var restored = RecipeSelection.restore("example:old_material", List.of("minecraft:gold_ingot", "minecraft:iron_ingot"));
        assertTrue(restored.unavailable());
        assertEquals(-1, restored.index());
        assertEquals("example:old_material", restored.id());
        var returned = RecipeSelection.restore(restored.id(), List.of("minecraft:gold_ingot", "example:old_material"));
        assertEquals(1, returned.index());
        assertFalse(returned.unavailable());
    }

    @Test void sortingAndLargeCatalogueExpansionKeepTheExactSavedIdentity() {
        List<String> ids = IntStream.range(0, 2_000).mapToObj(i -> "example:material_" + i).toList();
        var restored = RecipeSelection.restore("example:material_1500", ids);
        assertEquals(1_500, restored.index());
        assertEquals("example:material_1500", restored.id());
        assertEquals(499, RecipeSelection.restore(restored.id(), ids.reversed()).index());
    }

    @Test void OnlyANewEmptySelectionMayChooseTheFirstRecipe() {
        assertEquals(new RecipeSelection(0, "example:first"), RecipeSelection.restore("", List.of("example:first")));
        assertEquals(new RecipeSelection(-1, ""), RecipeSelection.restore("", List.of()));
        assertTrue(RecipeSelection.restore("example:unknown", List.of()).unavailable());
    }
}
