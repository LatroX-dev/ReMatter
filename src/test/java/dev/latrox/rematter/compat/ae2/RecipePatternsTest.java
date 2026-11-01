package dev.latrox.rematter.compat.ae2;

import dev.latrox.rematter.automation.AutomationPort;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipePatternsTest {
    private static AutomationPort.Recipe recipe() {
        return new AutomationPort.Recipe("rematter:test/process", "revision-1", "reactor",
                Map.of(AutomationPort.Resource.item("minecraft:iron_ingot"), 2L,
                        AutomationPort.Resource.fluid("minecraft:water"), 1_000L,
                        new AutomationPort.Resource(AutomationPort.Kind.MATTER, "missing:retained", "gas"), 9_007_199_254_740_993L),
                Map.of(AutomationPort.Resource.item("minecraft:gold_ingot"), 1L,
                        new AutomationPort.Resource(AutomationPort.Kind.MATTER, "missing:retained", "liquid"), 3L), 4_000, 80);
    }

    @Test void completeRecipeSurvivesWithoutALoadedHostOrSubstanceDefinition() {
        assertEquals(recipe(), RecipePatterns.decode(RecipePatterns.encode(recipe())));
    }

    @Test void numericTypesAndUnknownFieldsCannotChangeAStoredRecipe() {
        var numeric = RecipePatterns.encode(recipe());
        numeric.putDouble("energy", 4_000);
        assertThrows(IllegalArgumentException.class, () -> RecipePatterns.decode(numeric));
        var extra = RecipePatterns.encode(recipe());
        extra.putBoolean("free", true);
        assertThrows(IllegalArgumentException.class, () -> RecipePatterns.decode(extra));
        var future = RecipePatterns.encode(recipe());
        future.putInt("schema", 2);
        assertThrows(IllegalArgumentException.class, () -> RecipePatterns.decode(future));
    }

    @Test void duplicateAndFractionalInputRowsAreRejected() {
        var duplicate = RecipePatterns.encode(recipe());
        var rows = duplicate.getList("inputs").orElseThrow();
        rows.add(rows.getCompound(0).orElseThrow().copy());
        assertThrows(IllegalArgumentException.class, () -> RecipePatterns.decode(duplicate));
        var fractional = RecipePatterns.encode(recipe());
        fractional.getList("inputs").orElseThrow().getCompound(0).orElseThrow().putDouble("amount", 1.5);
        assertThrows(IllegalArgumentException.class, () -> RecipePatterns.decode(fractional));
    }

    @Test void phaseAndRecipeRevisionRemainInTheDefinition() {
        var original = recipe();
        var revised = new AutomationPort.Recipe(original.id(), "revision-2", original.workerKind(), original.inputs(),
                original.outputs(), original.energy(), original.ticks());
        assertNotEquals(RecipePatterns.encode(original), RecipePatterns.encode(revised));
        var decoded = RecipePatterns.decode(RecipePatterns.encode(original));
        assertTrue(decoded.inputs().containsKey(new AutomationPort.Resource(AutomationPort.Kind.MATTER, "missing:retained", "gas")));
        assertFalse(decoded.inputs().containsKey(new AutomationPort.Resource(AutomationPort.Kind.MATTER, "missing:retained", "liquid")));
    }
}
