package dev.latrox.rematter.chemistry;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;
import static org.junit.jupiter.api.Assertions.*;

class FluidProfileTest {
    @Test void nonIntegralPerMillibucketRatioIsExactInWholeBatches() {
        var fluid = new FluidProfile("example:molten_copper", 144, Map.of(COPPER, 1_000L), 5, 10, 20);
        assertEquals(Map.of(COPPER, 1_000L), ChemistryFixtures.matterFor(fluid, 144));
        assertEquals(Map.of(COPPER, 9_000L), ChemistryFixtures.matterFor(fluid, 1_296));
        var stored = new MatterInventory(1_000);
        for (int i = 0; i < 1_000; i++) {
            assertTrue(stored.apply(Map.of(), fluid.matter()));
            assertTrue(stored.apply(fluid.matter(), Map.of()));
        }
        assertEquals(0, stored.total());
    }

    @Test void canonicalFluidAndGasDefinitionsKeepVanillaAndMekanismWaterBalance() {
        var registry = ShippedChemistry.standalone();
        var water = registry.fluidProfile("minecraft:water").orElseThrow();
        var electrolysis = registry.recipe(SubstanceId.parse("rematter:water_electrolysis")).orElseThrow();
        var synthesis = registry.recipe(SubstanceId.parse("rematter:water_synthesis")).orElseThrow();
        assertEquals(1_000, water.amount());
        assertEquals(water.matter(), electrolysis.inputs());
        assertEquals(Map.of(HYDROGEN, 1_000L, OXYGEN, 500L), electrolysis.outputs());
        assertEquals(water.matter(), synthesis.outputs());
        assertTrue(registry.fluidProfile("minecraft:flowing_water").isEmpty());
        assertTrue(registry.fluidProfile("mekanism:steam").isEmpty());
    }

    @Test void explicitFluidPermissionDoesNotApproveAnItemWithTheSameRegistryId() {
        var fluid = new FluidProfile("example:material", 100, Map.of(COPPER, 1_000L), 1, 1, 1);
        var item = new MaterialProfile("example:material", Map.of(COPPER, 1_000L), 1, 1, 1);
        var registry = ChemistryFixtures.copyOf(BuiltinChemistry.create()).addFluidProfile(fluid).addProfile(item)
                .allowSynthesis(fluid.fluidId(), SynthesisCategory.FLUID).build();
        assertTrue(registry.synthesisPolicy().allowsFluid(fluid.fluidId()));
        assertFalse(registry.synthesisPolicy().allowsItem(item.itemId()));
        assertThrows(IllegalArgumentException.class, () -> ChemistryFixtures.copyOf(BuiltinChemistry.create())
                .allowSynthesis("example:undefined", SynthesisCategory.FLUID).build());
    }

    @Test void unsupportedAndOverflowingFluidMatterFailBeforePublishingTheCatalogue() {
        var unknown = new FluidProfile("example:unknown", 1_000, Map.of(SubstanceKey.of("absent:gas", Phase.GAS), 1L), 1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> ChemistryFixtures.copyOf(BuiltinChemistry.create()).addFluidProfile(unknown).build());
        var overflow = new FluidProfile("example:overflow", 1, Map.of(WATER, Long.MAX_VALUE), 1, 1, 1);
        assertThrows(ArithmeticException.class, () -> ChemistryFixtures.copyOf(BuiltinChemistry.create()).addFluidProfile(overflow).build());
        var huge = new FluidProfile("example:huge", 1, Map.of(COPPER, Long.MAX_VALUE), 1, 1, 1);
        assertThrows(ArithmeticException.class, () -> ChemistryFixtures.matterFor(huge, 2));
        assertThrows(IllegalArgumentException.class, () -> new FluidProfile("example:oversized", 16_001, Map.of(WATER, 1L), 1, 1, 1));
        var shipped = ShippedChemistry.standalone();
        assertThrows(IllegalArgumentException.class, () -> ChemistryFixtures.copyOf(shipped)
                .addFluidProfile(shipped.fluidProfile("minecraft:water").orElseThrow()));
    }
}
