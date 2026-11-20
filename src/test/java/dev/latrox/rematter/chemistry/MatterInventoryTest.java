package dev.latrox.rematter.chemistry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    void transferRetainsRemainderAndRespectsDestinationCapacity() {
        MatterInventory source = new MatterInventory(2_000);
        MatterInventory destination = new MatterInventory(1_000);
        source.insert(COPPER, 1_500);
        destination.insert(WATER, 800);

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

        MatterInventory restored = MatterInventory.restore(original.snapshot(), Integer.MAX_VALUE);
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
    void corruptSnapshotsAreRejectedInsteadOfEmptied() {
        SubstanceStack stack = new SubstanceStack(COPPER, 600);
        assertThrows(IllegalArgumentException.class,
                () -> MatterInventory.restore(new MatterInventory.Snapshot(1_000, List.of(stack, stack)), Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> MatterInventory.restore(new MatterInventory.Snapshot(500, List.of(stack)), Integer.MAX_VALUE));
    }

    @Test
    void overlappingInputAndOutputApplyAsOneNetChange() {
        MatterInventory inventory = new MatterInventory(1_000);
        inventory.insert(COPPER, 1_000);
        assertTrue(inventory.apply(Map.of(COPPER, 600L), Map.of(COPPER, 500L)));
        assertTrue(inventory.apply(Map.of(COPPER, 400L), Map.of(COPPER, 300L)));
        assertEquals(800, inventory.amount(COPPER));
    }

    @Test
    void inPlaceRollbackPreservesIdentityAndUnknownMatter() {
        SubstanceKey unknown = SubstanceKey.of("missing_mod:gas", Phase.GAS);
        MatterInventory inventory = new MatterInventory(2_000);
        MatterInventory retainedReference = inventory;
        inventory.insert(WATER, 1_000);
        inventory.insert(unknown, 100);
        MatterInventory.Snapshot before = inventory.snapshot();

        assertTrue(inventory.apply(Map.of(WATER, 1_000L), Map.of(HYDROGEN, 600L, OXYGEN, 300L)));
        inventory.extract(unknown, 100);
        ChemistryFixtures.resize(inventory, 4_000);
        inventory.restoreInPlace(before);

        assertSame(retainedReference, inventory);
        assertEquals(before, retainedReference.snapshot());
        assertEquals(100, retainedReference.amount(unknown));
        assertEquals(1_000, retainedReference.amount(WATER));
        assertEquals(2_000, retainedReference.capacity());
    }

    @Test
    void invalidInPlaceRestoreLeavesEveryExistingFieldUnchanged() {
        MatterInventory inventory = new MatterInventory(2_000);
        inventory.insert(WATER, 1_000);
        MatterInventory.Snapshot before = inventory.snapshot();
        MatterInventory.Snapshot invalid = new MatterInventory.Snapshot(3_000,
                List.of(new SubstanceStack(COPPER, 50), new SubstanceStack(COPPER, 51)));

        assertThrows(IllegalArgumentException.class, () -> inventory.restoreInPlace(invalid));
        assertEquals(before, inventory.snapshot());
    }
    @Test
    void revisionChangesOnEveryCommittedChangeAndNeverOnRejectedOnes() {
        MatterInventory inventory = new MatterInventory(1_000);
        MatterInventory other = new MatterInventory(1_000);
        long revision = inventory.revision();
        assertEquals(0, inventory.extract(WATER, 10));
        assertFalse(inventory.apply(Map.of(WATER, 1L), Map.of()));
        assertEquals(0, inventory.insert(WATER, 0));
        assertEquals(revision, inventory.revision());
        List<Runnable> changes = List.of(
                () -> inventory.insert(WATER, 100),
                () -> inventory.extract(WATER, 10),
                () -> inventory.apply(Map.of(WATER, 10L), Map.of(HYDROGEN, 10L)),
                () -> inventory.transferTo(other, WATER, 5),
                () -> inventory.restoreInPlace(inventory.snapshot()));
        for (Runnable change : changes) {
            long before = inventory.revision();
            change.run();
            assertTrue(inventory.revision() > before);
        }
    }
}
