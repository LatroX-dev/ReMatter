package dev.latrox.rematter.compat.mekanism;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static dev.latrox.rematter.chemistry.BuiltinChemistry.COPPER;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.HYDROGEN;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.NITROGEN;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.OXYGEN;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.STEAM;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.WATER;
import static org.junit.jupiter.api.Assertions.*;

/** Uses real NeoForge transactions, without loading Minecraft or Mekanism registries. */
class ChemicalTransfersTest {
    private static final int HYDROGEN_INDEX = 0;
    private static final int OXYGEN_INDEX = 1;
    private static final int VAPOR_INDEX = 2;

    @Test
    void mappingUsesOnlyExactRegisteredIdsWithoutSteamOrNitrogenAliases() {
        assertEquals(Set.of("mekanism:hydrogen", "mekanism:oxygen", "mekanism:water_vapor"),
                ChemicalMappings.ALL.stream().map(ChemicalMappings.Mapping::chemicalId).collect(Collectors.toSet()));
        assertEquals(ChemicalMappings.ALL.size(), ChemicalMappings.ALL.stream().map(ChemicalMappings.Mapping::matter).distinct().count());
        assertTrue(ChemicalMappings.ALL.stream().allMatch(mapping -> mapping.matter().phase() == Phase.GAS));
    }

    @Test
    void abortedSimulationRestoresTheSameInventoryAndDoesNotMarkDirty() {
        MatterInventory inventory = new MatterInventory(2_000);
        inventory.insert(COPPER, 200);
        AtomicInteger commits = new AtomicInteger();
        ChemicalTransfers handler = handler(inventory, commits);
        MatterInventory.Snapshot before = inventory.snapshot();

        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(1_800, handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 4_000, transaction));
            assertEquals(1_800, inventory.amount(HYDROGEN));
            assertTrue(handler.isInTransaction());
        }

        assertEquals(before, inventory.snapshot());
        assertEquals(0, commits.get());
        assertFalse(handler.isInTransaction());
    }

    @Test
    void commitAcrossSeveralChemicalSlotsRetainsMatterAndNotifiesOnce() {
        MatterInventory inventory = new MatterInventory(2_000);
        AtomicInteger commits = new AtomicInteger();
        ChemicalTransfers handler = handler(inventory, commits);

        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(1_000, handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 1_000, transaction));
            assertEquals(500, handler.insert(OXYGEN_INDEX, "mekanism:oxygen", 500, transaction));
            assertEquals(0, commits.get());
            transaction.commit();
        }

        assertEquals(Map.of(HYDROGEN, 1_000L, OXYGEN, 500L), inventory.contents());
        assertEquals(1, commits.get());
    }

    @Test
    void nestedCommitStillRollsBackWhenTheOuterTransactionAborts() {
        MatterInventory inventory = new MatterInventory(2_000);
        inventory.insert(HYDROGEN, 1_000);
        AtomicInteger commits = new AtomicInteger();
        ChemicalTransfers handler = handler(inventory, commits);
        MatterInventory.Snapshot before = inventory.snapshot();

        try (Transaction outer = Transaction.openRoot()) {
            assertEquals(100, handler.extract(HYDROGEN_INDEX, "mekanism:hydrogen", 100, outer));
            try (Transaction inner = Transaction.open(outer)) {
                assertEquals(500, handler.insert(OXYGEN_INDEX, "mekanism:oxygen", 500, inner));
                inner.commit();
            }
            assertEquals(1_400, inventory.total());
        }

        assertEquals(before, inventory.snapshot());
        assertEquals(0, commits.get());
    }

    @Test
    void nestedAbortRestoresOnlyItsOwnChangesBeforeOuterCommit() {
        MatterInventory inventory = new MatterInventory(2_000);
        AtomicInteger commits = new AtomicInteger();
        ChemicalTransfers handler = handler(inventory, commits);

        try (Transaction outer = Transaction.openRoot()) {
            handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 1_000, outer);
            try (Transaction inner = Transaction.open(outer)) {
                handler.extract(HYDROGEN_INDEX, "mekanism:hydrogen", 500, inner);
                handler.insert(OXYGEN_INDEX, "mekanism:oxygen", 500, inner);
            }
            assertEquals(Map.of(HYDROGEN, 1_000L), inventory.contents());
            outer.commit();
        }

        assertEquals(Map.of(HYDROGEN, 1_000L), inventory.contents());
        assertEquals(1, commits.get());
    }

    @Test
    void transferBetweenTwoStoresCanAbortBothSidesWithoutLossOrDuplication() {
        MatterInventory source = new MatterInventory(2_000);
        MatterInventory target = new MatterInventory(600);
        source.insert(HYDROGEN, 1_000);
        ChemicalTransfers from = handler(source, new AtomicInteger());
        ChemicalTransfers to = handler(target, new AtomicInteger());

        try (Transaction transaction = Transaction.openRoot()) {
            int accepted = to.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 1_000, transaction);
            assertEquals(600, accepted);
            assertEquals(accepted, from.extract(HYDROGEN_INDEX, "mekanism:hydrogen", accepted, transaction));
            assertEquals(1_000, source.amount(HYDROGEN) + target.amount(HYDROGEN));
        }
        assertEquals(1_000, source.amount(HYDROGEN));
        assertEquals(0, target.amount(HYDROGEN));

        try (Transaction transaction = Transaction.openRoot()) {
            int accepted = to.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 1_000, transaction);
            assertEquals(accepted, from.extract(HYDROGEN_INDEX, "mekanism:hydrogen", accepted, transaction));
            transaction.commit();
        }
        assertEquals(400, source.amount(HYDROGEN));
        assertEquals(600, target.amount(HYDROGEN));
    }

    @Test
    void reservationsProtectChemicalInputsAndCapacityAndSurviveRollback() {
        MatterInventory inventory = new MatterInventory(2_000);
        inventory.insert(HYDROGEN, 1_000);
        inventory.insert(OXYGEN, 500);
        var token = inventory.reserve(Map.of(HYDROGEN, 800L, OXYGEN, 400L), Map.of(WATER, 800L)).orElseThrow();
        inventory.reserve(Map.of(), Map.of(COPPER, 400L)).orElseThrow();
        ChemicalTransfers handler = handler(inventory, new AtomicInteger());
        MatterInventory.Snapshot before = inventory.snapshot();

        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(200, handler.amount(HYDROGEN_INDEX));
            assertEquals(100, handler.insert(VAPOR_INDEX, "mekanism:water_vapor", 1_000, transaction));
            assertEquals(200, handler.extract(HYDROGEN_INDEX, "mekanism:hydrogen", 1_000, transaction));
            assertEquals(0, handler.extract(HYDROGEN_INDEX, "mekanism:hydrogen", 1_000, transaction));
        }

        assertEquals(before, inventory.snapshot());
        assertTrue(inventory.commit(token));
        assertEquals(800, inventory.amount(WATER));
    }

    @Test
    void cachedHandlerHonorsAutomationAndDefinitionChangesButRollbackAlwaysWorks() {
        MatterInventory inventory = new MatterInventory(2_000);
        AtomicBoolean enabled = new AtomicBoolean(true);
        AtomicBoolean known = new AtomicBoolean(true);
        AtomicInteger commits = new AtomicInteger();
        ChemicalTransfers handler = new ChemicalTransfers(() -> inventory, enabled::get,
                key -> known.get(), commits::incrementAndGet);

        try (Transaction transaction = Transaction.openRoot()) {
            handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 1_000, transaction);
            enabled.set(false);
            assertEquals(0, handler.extract(HYDROGEN_INDEX, "mekanism:hydrogen", 100, transaction));
            assertEquals(0, handler.insert(OXYGEN_INDEX, "mekanism:oxygen", 100, transaction));
            assertEquals(0, handler.amount(HYDROGEN_INDEX));
            assertEquals(0, handler.capacity(HYDROGEN_INDEX));
        }
        assertEquals(0, inventory.total());
        enabled.set(true);
        inventory.insert(HYDROGEN, 500);
        known.set(false);
        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(0, handler.extract(HYDROGEN_INDEX, "mekanism:hydrogen", 500, transaction));
            assertEquals(0, handler.insert(OXYGEN_INDEX, "mekanism:oxygen", 500, transaction));
            transaction.commit();
        }
        assertEquals(500, inventory.amount(HYDROGEN));
        assertEquals(0, commits.get());
    }

    @Test
    void unsupportedChemicalsNeverConvertOrExposeUnknownStoredDefinitions() {
        MatterInventory inventory = new MatterInventory(5_000);
        SubstanceKey unknown = SubstanceKey.of("missing:gas", Phase.GAS);
        inventory.insert(unknown, 700);
        inventory.insert(NITROGEN, 500);
        ChemicalTransfers handler = handler(inventory, new AtomicInteger());
        MatterInventory.Snapshot before = inventory.snapshot();

        try (Transaction transaction = Transaction.openRoot()) {
            for (String chemical : Set.of("mekanism:nitrogen", "mekanism:steam", "mekanism:fissile_fuel", "other:hydrogen")) {
                for (int index = 0; index < handler.size(); index++) {
                    assertFalse(handler.isValid(index, chemical));
                    assertEquals(0, handler.insert(index, chemical, 1_000, transaction));
                    assertEquals(0, handler.extract(index, chemical, 1_000, transaction));
                }
            }
            transaction.commit();
        }

        assertEquals(before, inventory.snapshot());
        assertEquals(before, MatterInventory.restore(inventory.snapshot()).snapshot());
    }

    @Test
    void intTransfersAgainstLongCapacityKeepEveryUnitAndRejectNegativeRequests() {
        MatterInventory inventory = new MatterInventory(Long.MAX_VALUE);
        inventory.insert(HYDROGEN, Long.MAX_VALUE - 1);
        ChemicalTransfers handler = handler(inventory, new AtomicInteger());

        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(1, handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", Integer.MAX_VALUE, transaction));
            assertEquals(Long.MAX_VALUE, handler.amount(HYDROGEN_INDEX));
            assertEquals(0, handler.insert(OXYGEN_INDEX, "mekanism:oxygen", 1, transaction));
            assertEquals(Integer.MAX_VALUE, handler.extract(HYDROGEN_INDEX, "mekanism:hydrogen", Integer.MAX_VALUE, transaction));
            assertThrows(IllegalArgumentException.class,
                    () -> handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", -1, transaction));
            assertThrows(IllegalArgumentException.class,
                    () -> handler.extract(HYDROGEN_INDEX, "mekanism:hydrogen", -1, transaction));
            transaction.commit();
        }

        assertEquals(Long.MAX_VALUE - Integer.MAX_VALUE, inventory.amount(HYDROGEN));
    }

    @Test
    void inventoryReplacementIsReadFreshWhileRollbackRestoresItsOriginalObject() {
        MatterInventory first = new MatterInventory(1_000);
        AtomicReference<MatterInventory> current = new AtomicReference<>(first);
        ChemicalTransfers handler = new ChemicalTransfers(current::get, () -> true,
                BuiltinChemistry.create()::isKnown, () -> {});
        try (Transaction transaction = Transaction.openRoot()) {
            handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 500, transaction);
            current.set(new MatterInventory(2_000));
            assertEquals(0, handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 100, transaction));
            assertEquals(0, current.get().total());
        }
        assertEquals(0, first.total());
        assertEquals(2_000, handler.capacity(HYDROGEN_INDEX));
        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(1_500, handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 1_500, transaction));
            transaction.commit();
        }
        assertEquals(1_500, current.get().amount(HYDROGEN));
        assertEquals(0, first.total());
    }

    @Test
    void waterElectrolysisRoundTripMatchesMekanismTwoToTwoAndOneRecipeExactly() {
        MatterInventory inventory = new MatterInventory(2_000);
        ChemicalTransfers handler = handler(inventory, new AtomicInteger());
        // Mekanism Version Locked 2.11: 500 recipe operations consume 1,000 mB
        // water and produce 1,000 hydrogen + 500 oxygen (chemical units).
        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(1_000, handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 1_000, transaction));
            assertEquals(500, handler.insert(OXYGEN_INDEX, "mekanism:oxygen", 500, transaction));
            transaction.commit();
        }
        assertTrue(inventory.apply(Map.of(HYDROGEN, 1_000L, OXYGEN, 500L), Map.of(WATER, 1_000L)));
        assertEquals(Map.of(WATER, 1_000L), inventory.contents());
        assertTrue(inventory.apply(Map.of(WATER, 1_000L), Map.of(STEAM, 1_000L)));
        try (Transaction transaction = Transaction.openRoot()) {
            // The separate rotary water_vapor recipe returns exactly 1 mB water
            // per chemical unit. No remainder or additional water is introduced.
            assertEquals(1_000, handler.extract(VAPOR_INDEX, "mekanism:water_vapor", 2_000, transaction));
            transaction.commit();
        }
        assertEquals(0, inventory.total());
    }

    private static ChemicalTransfers handler(MatterInventory inventory, AtomicInteger commits) {
        return new ChemicalTransfers(() -> inventory, () -> true,
                BuiltinChemistry.create()::isKnown, commits::incrementAndGet);
    }
}
