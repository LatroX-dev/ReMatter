package dev.latrox.rematter.compat;

import dev.latrox.rematter.content.MachineKind;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreparationDisplayTest {
    @Test void recipeIdDependsOnlyOnMachineAndSourceRecipe() {
        var source = Identifier.parse("rematter:preparation/iron_dust");
        assertEquals(Identifier.parse("rematter:/preparation/" + MachineKind.PREPARATOR.id() + "/rematter/preparation/iron_dust"),
                PreparationDisplay.recipeId(MachineKind.PREPARATOR, source));
        assertEquals(PreparationDisplay.recipeId(MachineKind.PREPARATOR, source), PreparationDisplay.recipeId(MachineKind.PREPARATOR, source));
        assertNotEquals(PreparationDisplay.recipeId(MachineKind.PREPARATOR, source),
                PreparationDisplay.recipeId(MachineKind.INDUSTRIAL_PREPARATOR, source));
    }
}
