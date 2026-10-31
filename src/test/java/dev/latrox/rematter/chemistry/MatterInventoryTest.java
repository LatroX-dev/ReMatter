package dev.latrox.rematter.chemistry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.latrox.rematter.chemistry.BuiltinChemistry.COPPER;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.HYDROGEN;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.OXYGEN;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.WATER;
import static org.junit.jupiter.api.Assertions.*;

class MatterInventoryTest {
    @Test
    void insufficientOutputSpaceDoesNotConsumeInput() {
        MatterInventory inventory = new MatterInventory(1_000);
        assertEquals(1_000, inventory.insert(WATER, 1_000));
        Map<SubstanceKey, Long> before = inventory.contents();

        assertFalse(inventory.apply(Map.of(WATER, 1_000L), Map.of(HYDROGEN, 1_000L, OXYGEN, 500L)));
        assertEquals(before, inventory.contents());
        assertEquals(1_000, inventory.total());
    }

    @Test
    void missingOneOfSeveralInputsDoesNotConsumeOtherInputs() {
        MatterInventory inventory = new MatterInventory(10_000);
        inventory.insert(HYDROGEN, 1_000);
        inventory.insert(OXYGEN, 499);

        assertFalse(inventory.apply(Map.of(HYDROGEN, 1_000L, OXYGEN, 500L), Map.of(WATER, 1_000L)));
        assertEquals(1_000, inventory.amount(HYDROGEN));
        assertEquals(499, inventory.amount(OXYGEN));
        assertEquals(0, inventory.amount(WATER));
    }

    @Test
    void reservationProtectsInputsAndOutputCapacityFromOtherMachines() {
        MatterInventory inventory = new MatterInventory(2_000);
        inventory.insert(WATER, 1_000);
        UUID token = inventory.reserve(Map.of(WATER, 1_000L), Map.of(HYDROGEN, 1_000L, OXYGEN, 500L)).orElseThrow();

        assertEquals(0, inventory.available(WATER));
        assertEquals(1_000, inventory.reserved(WATER));
        assertEquals(0, inventory.extract(WATER, 1_000));
        assertEquals(500, inventory.insert(COPPER, 10_000));
        assertEquals(0, inventory.freeSpace());
        assertFalse(inventory.apply(Map.of(), Map.of(COPPER, 1L)));
        assertFalse(inventory.setCapacity(1_999));
        assertTrue(inventory.commit(token));
        assertEquals(2_000, inventory.total());
        assertEquals(0, inventory.amount(WATER));
        assertEquals(1_000, inventory.amount(HYDROGEN));
        assertEquals(500, inventory.amount(OXYGEN));
        assertFalse(inventory.commit(token));
    }

    @Test
    void cancellingDoesNotConsumeOrCreateMatter() {
        MatterInventory inventory = new MatterInventory(2_000);
        inventory.insert(WATER, 1_000);
        UUID token = inventory.reserve(Map.of(WATER, 1_000L), Map.of(HYDROGEN, 1_000L, OXYGEN, 500L)).orElseThrow();
        assertTrue(inventory.cancel(token));
        assertFalse(inventory.cancel(token));
        assertEquals(Map.of(WATER, 1_000L), inventory.contents());
        assertEquals(1_000, inventory.available(WATER));
        assertEquals(1_000, inventory.freeSpace());
    }

    @Test
    void reservationSurvivesRestartAndCompletionRemainsSingleUse() {
        MatterInventory original = new MatterInventory(2_000);
        original.insert(WATER, 1_000);
        UUID token = original.reserve(Map.of(WATER, 1_000L), Map.of(HYDROGEN, 1_000L, OXYGEN, 500L)).orElseThrow();

        MatterInventory restored = MatterInventory.restore(original.snapshot());
        assertEquals(0, restored.extract(WATER, 1_000));
        assertTrue(restored.commit(token));
        MatterInventory afterSecondRestart = MatterInventory.restore(restored.snapshot());
        assertFalse(afterSecondRestart.commit(token));
        assertEquals(Map.of(HYDROGEN, 1_000L, OXYGEN, 500L), afterSecondRestart.contents());
    }

    @Test
    void transferRetainsRemainderAndRespectsBothStoresReservations() {
        MatterInventory source = new MatterInventory(2_000);
        MatterInventory destination = new MatterInventory(1_000);
        source.insert(COPPER, 1_500);
        source.reserve(Map.of(COPPER, 1_000L), Map.of()).orElseThrow();
        destination.reserve(Map.of(), Map.of(WATER, 800L)).orElseThrow();

        assertEquals(200, source.transferTo(destination, COPPER, 5_000));
        assertEquals(1_300, source.amount(COPPER));
        assertEquals(200, destination.amount(COPPER));
        assertEquals(1_500, source.amount(COPPER) + destination.amount(COPPER));
        assertEquals(0, source.transferTo(destination, COPPER, 5_000));
        assertEquals(0, source.transferTo(source, COPPER, 5_000));
    }

    @Test
    void unknownDefinitionsArePreservedAcrossStorageRestarts() {
        SubstanceKey missing = SubstanceKey.of("missing_mod:unobtainium", Phase.LIQUID);
        MatterInventory original = new MatterInventory(4_096);
        original.insert(missing, 1_234);
        assertFalse(BuiltinChemistry.create().isKnown(missing));

        MatterInventory restored = MatterInventory.restore(original.snapshot());
        assertEquals(1_234, restored.amount(missing));
        assertEquals(original.snapshot(), restored.snapshot());
    }

    @Test
    void exactLongBoundaryDoesNotOverflowOrWrapCapacity() {
        MatterInventory inventory = new MatterInventory(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, inventory.insert(COPPER, Long.MAX_VALUE));
        assertEquals(0, inventory.insert(WATER, 1));
        assertEquals(Long.MAX_VALUE, inventory.total());
        assertTrue(inventory.apply(Map.of(COPPER, Long.MAX_VALUE), Map.of(WATER, Long.MAX_VALUE)));
        assertEquals(Long.MAX_VALUE, inventory.amount(WATER));
        assertEquals(0, inventory.amount(COPPER));
    }

    @Test
    void overflowAndInvalidAmountsAreRejectedBeforeAnyMutation() {
        MatterInventory inventory = new MatterInventory(10_000);
        inventory.insert(COPPER, 1_000);
        Map<SubstanceKey, Long> before = inventory.contents();
        assertThrows(ArithmeticException.class,
                () -> inventory.apply(Map.of(COPPER, 1_000L), Map.of(WATER, Long.MAX_VALUE, OXYGEN, 1L)));
        assertThrows(IllegalArgumentException.class, () -> inventory.extract(COPPER, -1));
        assertThrows(IllegalArgumentException.class, () -> inventory.insert(COPPER, -1));
        assertThrows(IllegalArgumentException.class,
                () -> inventory.apply(Map.of(COPPER, 0L), Map.of()));
        assertEquals(before, inventory.contents());
    }

    @Test
    void corruptAndFutureSnapshotsAreRejectedInsteadOfEmptied() {
        SubstanceStack stack = new SubstanceStack(COPPER, 600);
        assertThrows(IllegalArgumentException.class,
                () -> MatterInventory.restore(new MatterInventory.Snapshot(2, 1_000, List.of(stack), List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> MatterInventory.restore(new MatterInventory.Snapshot(1, 1_000, List.of(stack, stack), List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> MatterInventory.restore(new MatterInventory.Snapshot(1, 500, List.of(stack), List.of())));
        MatterInventory.Reservation reservation = new MatterInventory.Reservation(UUID.randomUUID(),
                Map.of(COPPER, 601L), Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> MatterInventory.restore(new MatterInventory.Snapshot(1, 1_000, List.of(stack), List.of(reservation))));
    }

    @Test
    void overlappingInputAndOutputKeepsOtherReservationsIntact() {
        MatterInventory inventory = new MatterInventory(1_000);
        inventory.insert(COPPER, 1_000);
        UUID first = inventory.reserve(Map.of(COPPER, 600L), Map.of(COPPER, 500L)).orElseThrow();
        UUID second = inventory.reserve(Map.of(COPPER, 400L), Map.of(COPPER, 300L)).orElseThrow();
        assertTrue(inventory.commit(first));
        assertEquals(500, inventory.available(COPPER));
        assertTrue(inventory.commit(second));
        assertEquals(800, inventory.amount(COPPER));
    }

    @Test
    void inPlaceRollbackPreservesIdentityUnknownMatterAndReservations() {
        SubstanceKey unknown = SubstanceKey.of("missing_mod:gas", Phase.GAS);
        MatterInventory inventory = new MatterInventory(2_000);
        MatterInventory retainedReference = inventory;
        inventory.insert(WATER, 1_000);
        inventory.insert(unknown, 100);
        UUID token = inventory.reserve(Map.of(WATER, 1_000L), Map.of(HYDROGEN, 1_000L, OXYGEN, 500L)).orElseThrow();
        MatterInventory.Snapshot before = inventory.snapshot();

        assertTrue(inventory.commit(token));
        inventory.extract(unknown, 100);
        assertTrue(inventory.setCapacity(4_000));
        inventory.restoreInPlace(before);

        assertSame(retainedReference, inventory);
        assertEquals(before, retainedReference.snapshot());
        assertEquals(100, retainedReference.amount(unknown));
        assertEquals(0, retainedReference.available(WATER));
        assertTrue(retainedReference.commit(token));
    }

    @Test
    void invalidInPlaceRestoreLeavesEveryExistingFieldUnchanged() {
        MatterInventory inventory = new MatterInventory(2_000);
        inventory.insert(WATER, 1_000);
        inventory.reserve(Map.of(WATER, 1_000L), Map.of(HYDROGEN, 1_000L, OXYGEN, 500L)).orElseThrow();
        MatterInventory.Snapshot before = inventory.snapshot();
        MatterInventory.Snapshot invalid = new MatterInventory.Snapshot(1, 3_000,
                List.of(new SubstanceStack(COPPER, 50)),
                List.of(new MatterInventory.Reservation(UUID.randomUUID(), Map.of(COPPER, 51L), Map.of())));

        assertThrows(IllegalArgumentException.class, () -> inventory.restoreInPlace(invalid));
        assertEquals(before, inventory.snapshot());
    }
}
