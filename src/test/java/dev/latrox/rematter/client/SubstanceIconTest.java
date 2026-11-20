package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.network.ClientChemistry;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;
import static org.junit.jupiter.api.Assertions.*;

class SubstanceIconTest {
    private static final ToIntFunction<String> WIDTH = text -> text.length() * 6;

    @AfterEach void resetCatalogue() { ClientChemistry.clear(); }

    @Test void undefinedSubstancesAndUnsupportedPhasesShareOneWarningBadge() {
        var registry = BuiltinChemistry.create();
        var removed = SubstanceIcon.compute(registry, SubstanceKey.of("example:removed", Phase.SOLID), WIDTH);
        var wrongPhase = SubstanceIcon.compute(registry, new SubstanceKey(SILICA.id(), Phase.GAS), WIDTH);
        for (var style : new SubstanceIcon.Style[]{removed, wrongPhase}) {
            assertFalse(style.known());
            assertEquals("?", style.first());
            assertEquals(UiPalette.WARNING, style.color());
        }
        assertEquals(removed, wrongPhase);
    }

    @Test void knownFormulaIsSplitOnceAtAnElementBoundary() {
        var style = SubstanceIcon.compute(BuiltinChemistry.create(), SILICA, WIDTH);
        assertTrue(style.known());
        assertEquals("Si", style.first());
        assertEquals("O₂", style.second());
        assertEquals(style, SubstanceIcon.compute(BuiltinChemistry.create(), SILICA, WIDTH));
    }

    @Test void everyCatalogueFormulaRetainsItsCompleteNotation() {
        var registry=BuiltinChemistry.create();
        for(var definition:registry.substances())for(var phase:definition.phases()){
            var style=SubstanceIcon.compute(registry,new SubstanceKey(definition.id(),phase),WIDTH);
            assertEquals(SubstanceLabels.iconLabel(definition),style.first()+style.second(),definition.id().toString());
            assertFalse((style.first()+style.second()).contains("…"));
            if(!style.second().isEmpty())assertTrue(style.second().charAt(0)=='('||Character.isUpperCase(style.second().charAt(0)),definition.id().toString());
        }
    }

    @Test void stylesAreReusedUntilTheCatalogueChanges() {
        ClientChemistry.accept(BuiltinChemistry.create());
        var first = SubstanceIcon.style(WATER, WIDTH);
        assertSame(first, SubstanceIcon.style(WATER, WIDTH));
        assertTrue(first.known());

        ClientChemistry.clear();
        var afterRemoval = SubstanceIcon.style(WATER, WIDTH);
        assertNotSame(first, afterRemoval);
        assertFalse(afterRemoval.known());
    }
}
