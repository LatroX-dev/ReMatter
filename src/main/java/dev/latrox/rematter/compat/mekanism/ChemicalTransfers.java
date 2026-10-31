package dev.latrox.rematter.compat.mekanism;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.SubstanceKey;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One shared transaction journal per storage block, used by every face.
 * It owns no chemical buffer: transactions operate on the real matter inventory.
 */
final class ChemicalTransfers extends SnapshotJournal<ChemicalTransfers.State> {
    private final Supplier<MatterInventory> inventory;
    private final BooleanSupplier automationAllowed;
    private final Predicate<SubstanceKey> known;
    private final Runnable committed;
    private final List<ChemicalMappings.Mapping> mappings;
    private MatterInventory transactionInventory;

    ChemicalTransfers(Supplier<MatterInventory> inventory, BooleanSupplier automationAllowed,
                      Predicate<SubstanceKey> known, Runnable committed) {
        this(inventory, automationAllowed, known, committed, ChemicalMappings.ALL);
    }

    ChemicalTransfers(Supplier<MatterInventory> inventory, BooleanSupplier automationAllowed,
                      Predicate<SubstanceKey> known, Runnable committed, List<ChemicalMappings.Mapping> mappings) {
        this.inventory = Objects.requireNonNull(inventory);
        this.automationAllowed = Objects.requireNonNull(automationAllowed);
        this.known = Objects.requireNonNull(known);
        this.committed = Objects.requireNonNull(committed);
        this.mappings = List.copyOf(mappings);
    }

    int size() {
        return mappings.size();
    }

    String chemicalId(int index) { return mapping(index).chemicalId(); }

    boolean isEnabled(int index) {
        var mapping = mapping(index);
        return automationAllowed.getAsBoolean() && currentInventory() != null && known.test(mapping.matter());
    }

    long amount(int index) {
        return isEnabled(index) ? inventory.get().available(mapping(index).matter()) / mapping(index).unitsPerChemical() : 0;
    }

    long capacity(int index) {
        return isEnabled(index) ? inventory.get().capacity() / mapping(index).unitsPerChemical() : 0;
    }

    boolean isValid(int index, String chemicalId) {
        return mapping(index).chemicalId().equals(chemicalId) && isEnabled(index);
    }

    int insert(int index, String chemicalId, int amount, TransactionContext transaction) {
        checkArguments(amount, transaction);
        if (!isValid(index, chemicalId) || amount == 0) return 0;
        MatterInventory target = inventory.get();
        var mapping = mapping(index);
        int accepted = (int) Math.min(amount, target.freeSpace() / mapping.unitsPerChemical());
        if (accepted == 0) return 0;
        long internal = Math.multiplyExact((long) accepted, mapping.unitsPerChemical());
        updateSnapshots(transaction);
        return target.apply(Map.of(), Map.of(mapping.matter(), internal)) ? accepted : 0;
    }

    int extract(int index, String chemicalId, int amount, TransactionContext transaction) {
        checkArguments(amount, transaction);
        if (!isValid(index, chemicalId) || amount == 0) return 0;
        MatterInventory source = inventory.get();
        var mapping = mapping(index);
        int extracted = (int) Math.min(amount, source.available(mapping.matter()) / mapping.unitsPerChemical());
        if (extracted == 0) return 0;
        long internal = Math.multiplyExact((long) extracted, mapping.unitsPerChemical());
        updateSnapshots(transaction);
        return source.apply(Map.of(mapping.matter(), internal), Map.of()) ? extracted : 0;
    }

    @Override
    protected State createSnapshot() {
        MatterInventory current = Objects.requireNonNull(inventory.get(), "Storage is unavailable");
        transactionInventory = current;
        return new State(current, current.snapshot());
    }

    @Override
    protected void revertToSnapshot(State state) {
        // Rollback must work even when the gate or a substance definition changed.
        state.inventory().restoreInPlace(state.snapshot());
    }

    @Override
    protected void onRootCommit(State originalState) {
        committed.run();
    }

    private ChemicalMappings.Mapping mapping(int index) {
        return mappings.get(index);
    }

    private MatterInventory currentInventory() {
        MatterInventory current = inventory.get();
        // A load replacing the owner's storage must not let one journal mutate
        // a second inventory during the same transaction. Retry in a new root.
        return isInTransaction() && current != transactionInventory ? null : current;
    }

    private static void checkArguments(int amount, TransactionContext transaction) {
        if (amount < 0) throw new IllegalArgumentException("Chemical amount must be non-negative");
        Objects.requireNonNull(transaction, "transaction");
    }

    record State(MatterInventory inventory, MatterInventory.Snapshot snapshot) {}
}
