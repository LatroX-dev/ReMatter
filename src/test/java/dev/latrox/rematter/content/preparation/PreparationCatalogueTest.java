package dev.latrox.rematter.content.preparation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreparationCatalogueTest {
    private static List<Identifier> ids(int count) {
        var ids = new ArrayList<Identifier>();
        for (int i = 0; i < count; i++) ids.add(Identifier.fromNamespaceAndPath("test", "recipe_" + i));
        return ids;
    }

    @Test void theLimitIsInclusiveAndOneMoreRecipeIsRejectedWithoutThrowing() {
        assertTrue(PreparationWork.validCatalogue(ids(PreparationWork.MAX_RECIPES)));
        assertFalse(assertDoesNotThrow(() -> PreparationWork.validCatalogue(ids(PreparationWork.MAX_RECIPES + 1))));
    }

    @Test void duplicateOrOversizedIdentifiersInvalidateTheCatalogue() {
        var duplicate = new ArrayList<>(ids(2));
        duplicate.add(duplicate.getFirst());
        assertFalse(PreparationWork.validCatalogue(duplicate));
        assertFalse(PreparationWork.validCatalogue(List.of(Identifier.fromNamespaceAndPath("test", "x".repeat(520)))));
        assertTrue(PreparationWork.validCatalogue(List.of()));
    }
}
