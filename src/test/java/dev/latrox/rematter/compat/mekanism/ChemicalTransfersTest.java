package dev.latrox.rematter.compat.mekanism;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.SynthesisCategory;
import dev.latrox.rematter.chemistry.SynthesisPolicy;
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
import static dev.latrox.rematter.chemistry.BuiltinChemistry.OXYGEN;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.STEAM;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.WATER;
import static org.junit.jupiter.api.Assertions.*;

/** Uses real NeoForge transactions, without loading Minecraft or Mekanism registries. */
class ChemicalTransfersTest {
    private static final int HYDROGEN_INDEX = 0;
    private static final int OXYGEN_INDEX = 1;
    private static final int VAPOR_INDEX = 2;

    @Test void sixCellChemicalTransactionsRollbackEveryBayAndKeepUnrelatedReservations() {
        var cells = new java.util.ArrayList<MatterInventory>();
        for (int bay = 0; bay < 6; bay++) { var cell = new MatterInventory(100, 2); cell.insert(HYDROGEN, 50); cells.add(cell); }
        var token = cells.get(4).reserve(Map.of(HYDROGEN, 40L), Map.of()).orElseThrow();
        var store = new dev.latrox.rematter.chemistry.MatterNetworkInventory(cells);
        var before = store.snapshot(); var commits = new AtomicInteger();
        var handler = new ChemicalTransfers(() -> store, () -> true, key -> true, exported -> true, commits::incrementAndGet, ChemicalMappings.ALL);
        try (var outer = Transaction.openRoot()) {
            assertEquals(260, handler.extract(HYDROGEN_INDEX, "mekanism:hydrogen", 500, outer));
            try (var inner = Transaction.open(outer)) {
                assertEquals(560, handler.insert(OXYGEN_INDEX, "mekanism:oxygen", 1_000, inner)); inner.commit();
            }
        }
        assertEquals(before, store.snapshot()); assertEquals(0, commits.get());
        assertTrue(cells.get(4).reservation(token).isPresent());
    }
    @Test void driveChemicalInsertionSkipsTypeFullBaysAndMemberReplacementCannotJoinAnOpenTransaction() {
        var fullType = new MatterInventory(1_000, 1); fullType.insert(COPPER, 1);
        var open = new MatterInventory(100, 1);
        var original = new dev.latrox.rematter.chemistry.MatterNetworkInventory(java.util.List.of(fullType, open));
        var current = new AtomicReference<dev.latrox.rematter.chemistry.MatterStore>(original);
        var handler = new ChemicalTransfers(current::get, () -> true, key -> true, exported -> true, () -> {}, ChemicalMappings.ALL);
        try (var tx = Transaction.openRoot()) {
            assertEquals(100, handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 1_000, tx));
            assertEquals(0, fullType.amount(HYDROGEN));
            current.set(new dev.latrox.rematter.chemistry.MatterNetworkInventory(java.util.List.of(fullType, new MatterInventory(100, 1))));
            assertEquals(0, handler.insert(HYDROGEN_INDEX, "mekanism:hydrogen", 100, tx));
        }
        assertEquals(0, open.total()); assertEquals(1, fullType.total()); assertEquals(1, current.get().total());
    }

    @Test
    void exportRequiresAGasPermissionOrTheChemicalsOwnFluidPermission() {
        var policy = new SynthesisPolicy(Map.of(
                HYDROGEN.id().toString(), SynthesisCategory.GAS,
                "mekanism:chlorine", SynthesisCategory.FLUID,
                OXYGEN.id().toString(), SynthesisCategory.RAW_MATERIAL,
                "mekanism:oxygen", SynthesisCategory.RAW_MATERIAL));
        var byId = ChemicalMappings.ALL.stream().collect(Collectors.toMap(ChemicalMappings.Mapping::chemicalId, mapping -> mapping));
        assertTrue(ChemicalMappings.exportable(policy, byId.get("mekanism:hydrogen")));
        assertTrue(ChemicalMappings.exportable(policy, byId.get("mekanism:chlorine")));
        assertFalse(ChemicalMappings.exportable(policy, byId.get("mekanism:oxygen")), "Only GAS or FLUID releases count");
        assertFalse(ChemicalMappings.exportable(policy, byId.get("mekanism:sulfur_dioxide")));

        var inventory = new MatterInventory(10_000);
        inventory.insert(HYDROGEN, 1_000);
        var handler = new ChemicalTransfers(() -> inventory, () -> true, key -> true,
                mapping -> ChemicalMappings.exportable(policy, mapping), () -> {}, ChemicalMappings.ALL);
        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(500, handler.insert(OXYGEN_INDEX, "mekanism:oxygen", 500, transaction), "Imports need no release");
            assertEquals(0, handler.amount(OXYGEN_INDEX));
            assertEquals(0, handler.extract(OXYGEN_INDEX, "mekanism:oxygen", 500, transaction));
            assertEquals(1_000, handler.amount(HYDROGEN_INDEX));
            assertEquals(400, handler.extract(HYDROGEN_INDEX, "mekanism:hydrogen", 400, transaction));
            transaction.commit();
        }
        assertEquals(500, inventory.amount(OXYGEN));
        assertEquals(600, inventory.amount(HYDROGEN));
    }

    @Test
    void mappingUsesOnlyExactRegisteredIdsWithoutSteamOrNitrogenAliases() {
        assertEquals(Set.of("mekanism:hydrogen", "mekanism:oxygen", "mekanism:water_vapor", "mekanism:chlorine",
                        "mekanism:sulfur_dioxide", "mekanism:sulfur_trioxide", "mekanism:sulfuric_acid",
                        "mekanism:hydrogen_chloride", "mekanism:hydrofluoric_acid", "mekanism:ethene", "mekanism:lithium", "mekanism:osmium"),
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
                key -> known.get(), exported -> true, commits::incrementAndGet, ChemicalMappings.ALL);

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
        inventory.insert(SubstanceKey.of("rematter:nitrogen", Phase.GAS), 500);
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
                BuiltinChemistry.create()::isKnown, exported -> true, () -> {}, ChemicalMappings.ALL);
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

    @Test void scaledLithiumTransfersKeepPartialInternalUnitsAndRespectWholeExternalCapacity() {
        var mapping = ChemicalMappings.ALL.stream().filter(value -> value.chemicalId().equals("mekanism:lithium")).findFirst().orElseThrow();
        assertEquals(10, mapping.unitsPerChemical()); // 100 native units <-> 1 lithium dust (1000 internal units).
        MatterInventory inventory = new MatterInventory(24);
        inventory.insert(mapping.matter(), 9);
        ChemicalTransfers handler = new ChemicalTransfers(() -> inventory, () -> true, key -> true, exported -> true, () -> {}, java.util.List.of(mapping));
        assertEquals(0, handler.amount(0));
        assertEquals(2, handler.capacity(0));
        var before = inventory.snapshot();
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(1, handler.insert(0, mapping.chemicalId(), 50, tx));
            assertEquals(19, inventory.amount(mapping.matter()));
            assertEquals(0, handler.insert(0, mapping.chemicalId(), 1, tx));
            assertEquals(1, handler.extract(0, mapping.chemicalId(), 50, tx));
            assertEquals(9, inventory.amount(mapping.matter()));
            tx.commit();
        }
        assertEquals(before, inventory.snapshot());
    }

    @Test void scaledChemicalsAbortAcrossNestedTransactionsWithoutLosingRemaindersOrReservations() {
        var mapping = ChemicalMappings.ALL.stream().filter(value -> value.chemicalId().equals("mekanism:osmium")).findFirst().orElseThrow();
        assertEquals(5, mapping.unitsPerChemical()); // 1 native ingot (1000 internal units) -> 200 chemical units.
        MatterInventory inventory = new MatterInventory(2_000);
        inventory.insert(mapping.matter(), 1_003);
        inventory.reserve(Map.of(mapping.matter(), 7L), Map.of()).orElseThrow();
        ChemicalTransfers handler = new ChemicalTransfers(() -> inventory, () -> true, key -> true, exported -> true, () -> {}, java.util.List.of(mapping));
        var before = inventory.snapshot();
        try (Transaction outer = Transaction.openRoot()) {
            assertEquals(199, handler.extract(0, mapping.chemicalId(), 200, outer));
            assertEquals(8, inventory.amount(mapping.matter()));
            try (Transaction nested = Transaction.open(outer)) {
                assertEquals(200, handler.insert(0, mapping.chemicalId(), 200, nested));
                nested.commit();
            }
        }
        assertEquals(before, inventory.snapshot());
    }

    @Test void scaledLongCapacityNeverOverflowsOrRoundsARequestedExternalUnit() {
        var mapping = ChemicalMappings.ALL.stream().filter(value -> value.chemicalId().equals("mekanism:lithium")).findFirst().orElseThrow();
        MatterInventory inventory = new MatterInventory(Long.MAX_VALUE);
        inventory.insert(mapping.matter(), Long.MAX_VALUE - 7);
        ChemicalTransfers handler = new ChemicalTransfers(() -> inventory, () -> true, key -> true, exported -> true, () -> {}, java.util.List.of(mapping));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, handler.insert(0, mapping.chemicalId(), Integer.MAX_VALUE, tx));
            assertEquals(Integer.MAX_VALUE, handler.extract(0, mapping.chemicalId(), Integer.MAX_VALUE, tx));
            tx.commit();
        }
        assertEquals(Long.MAX_VALUE - 7 - 10L * Integer.MAX_VALUE, inventory.amount(mapping.matter()));
    }

    @Test void hydrogenChlorideInfusingUsesTwoFormulaUnitsForItsOneNativeOutput() {
        var mapping = ChemicalMappings.ALL.stream().filter(value -> value.chemicalId().equals("mekanism:hydrogen_chloride")).findFirst().orElseThrow();
        MatterInventory inventory = new MatterInventory(100);
        ChemicalTransfers handler = new ChemicalTransfers(() -> inventory, () -> true, key -> true, exported -> true, () -> {}, java.util.List.of(mapping));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(1, handler.insert(0, mapping.chemicalId(), 1, tx));
            tx.commit();
        }
        // The installed recipe consumes one H2 and one Cl2, i.e. two HCl formula units.
        assertEquals(2, inventory.amount(mapping.matter()));
        assertEquals(1, handler.amount(0));
    }

    private static ChemicalTransfers handler(MatterInventory inventory, AtomicInteger commits) {
        return new ChemicalTransfers(() -> inventory, () -> true,
                BuiltinChemistry.create()::isKnown, exported -> true, commits::incrementAndGet, ChemicalMappings.ALL);
    }
}
