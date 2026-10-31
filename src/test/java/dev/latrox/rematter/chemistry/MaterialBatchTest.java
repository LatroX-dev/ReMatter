package dev.latrox.rematter.chemistry;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;
import static org.junit.jupiter.api.Assertions.*;

class MaterialBatchTest {
    private static MaterialProfile nuggets() {
        return new MaterialProfile("example:copper_nugget", 9, Map.of(COPPER, 1_000L), 8_000, 16_000, 100);
    }

    @Test void nuggetsIngotsAndBlocksHaveTheSameExactMaterialAcrossRepeatedCraftingCycles() {
        var nuggets = nuggets();
        var registry = BuiltinChemistry.create();
        var ingot = registry.profile("minecraft:copper_ingot").orElseThrow();
        var block = registry.profile("minecraft:copper_block").orElseThrow();
        assertEquals(ingot.matter(), nuggets.matterFor(9));
        assertEquals(block.matter(), nuggets.matterFor(81));
        MatterInventory inventory = new MatterInventory(9_000);
        for (int cycle = 0; cycle < 1_000; cycle++) {
            assertTrue(inventory.apply(Map.of(), block.matter()));
            for (int batch = 0; batch < 9; batch++) assertTrue(inventory.apply(nuggets.matter(), Map.of()));
            assertEquals(0, inventory.total(), "A block to 81 nuggets cycle must leave no rounded residue");
        }
        assertEquals(1_000, ingot.matter().get(COPPER));
    }

    @Test void partialBatchesAndInvalidCountsNeverProduceApproximateMatter() {
        for (long count : new long[]{-1, 0, 1, 8, 10, 64, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> nuggets().matterFor(count));
        }
        for (int count : new int[]{0, -1, 65, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new MaterialProfile("example:nugget", count,
                    Map.of(COPPER, 1_000L), 1, 1, 1));
        }
        assertEquals(Map.of(COPPER, 7_000L), nuggets().matterFor(63));
    }

    @Test void wholeBatchCapacityAndReservationsDoNotPartiallyConsumeOrProduceMatter() {
        MatterInventory store = new MatterInventory(1_000);
        store.insert(WATER, 1);
        var before = store.snapshot();
        assertFalse(store.canApply(Map.of(), nuggets().matter()));
        assertFalse(store.apply(Map.of(), nuggets().matter()));
        assertEquals(before, store.snapshot());
        store.extract(WATER, 1);
        assertTrue(store.apply(Map.of(), nuggets().matter()));
        var reservation = store.reserve(nuggets().matter(), Map.of()).orElseThrow();
        assertFalse(store.apply(nuggets().matter(), Map.of()));
        store = MatterInventory.restore(store.snapshot());
        assertTrue(store.commit(reservation));
        assertFalse(store.commit(reservation));
        assertEquals(0, store.total());
    }

    @Test void manufacturedBatchProfilesNeverGrantSynthesisOrEraseSavedUnknownIds() {
        var plates = new MaterialProfile("example:copper_plate", 3, Map.of(COPPER, 2_000L), 1, 1, 1);
        var registry = BuiltinChemistry.create().toBuilder().addProfile(plates).build();
        assertFalse(registry.synthesisPolicy().allowsItem(plates.itemId()));
        var unknown = SubstanceKey.of("absent:rare_material", Phase.SOLID);
        MatterInventory store = new MatterInventory(5_000);
        store.insert(unknown, 33);
        assertTrue(store.apply(Map.of(), plates.matter()));
        var saved = MatterInventory.restore(store.snapshot());
        assertEquals(Map.of(unknown, 33L, COPPER, 2_000L), saved.contents());
        assertFalse(registry.isKnown(unknown));
    }

    @Test void allotropesConserveCarbonButDoNotBecomeInterchangeableStorageOrPermissions() {
        var diamond = SubstanceKey.of("example:diamond", Phase.SOLID);
        var definition = new SubstanceDefinition(diamond.id(), "substance.example.diamond", "C",
                SubstanceKind.ALLOTROPE, Composition.element(CARBON.id()), Set.of(Phase.SOLID));
        var profile = new MaterialProfile("example:diamond", Map.of(diamond, 1_000L), 1, 1, 1);
        var registry = BuiltinChemistry.create().toBuilder().addSubstance(definition).addProfile(profile)
                .allowSynthesis(profile.itemId(), SynthesisCategory.RAW_MATERIAL).build();
        assertEquals(registry.elementAmounts(Map.of(CARBON, 1_000L)), registry.elementAmounts(profile.matter()));
        MatterInventory inventory = new MatterInventory(2_000);
        inventory.insert(CARBON, 1_000);
        assertFalse(inventory.apply(profile.matter(), Map.of()));
        assertEquals(1_000, inventory.amount(CARBON));
        assertTrue(registry.processes().stream().noneMatch(recipe -> recipe.outputs().containsKey(diamond)));
        assertThrows(IllegalArgumentException.class, () -> new SubstanceDefinition(diamond.id(), "diamond", "C",
                SubstanceKind.ALLOTROPE, Composition.element(diamond.id()), Set.of(Phase.SOLID)));
    }
}
