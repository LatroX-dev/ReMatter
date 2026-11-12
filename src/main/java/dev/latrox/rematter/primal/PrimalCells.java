package dev.latrox.rematter.primal;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.BiFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** Closed resource operations. A caller supplies the explicit physical payment slot. */
public final class PrimalCells {
    public static final long SHARD_UNITS = 1000;
    private PrimalCells() {}
    private static volatile BiFunction<PrimalCellData.State, ItemStack, Optional<PrimalCellData.State>> authority = (state, stack) -> Optional.of(state);
    public record Receipt(UUID cellId, long delta, long authorityRevision) {
        public Receipt { Objects.requireNonNull(cellId); if (delta == 0 || authorityRevision < 1) throw new IllegalArgumentException("Invalid authority receipt"); }
    }
    private static volatile BiFunction<UUID, UUID, Optional<Receipt>> receiptLookup = (cell, operation) -> Optional.empty();
    public static void installReceiptLookup(BiFunction<UUID, UUID, Optional<Receipt>> lookup) { receiptLookup = Objects.requireNonNull(lookup); }
    /** Server authority may reconcile durable payment records without changing an item during a read. */
    public static void installAuthority(BiFunction<PrimalCellData.State, ItemStack, Optional<PrimalCellData.State>> resolver) {
        authority = Objects.requireNonNull(resolver);
    }
    public record View(boolean installed, boolean readable, long amount, long capacity, @Nullable UUID cellId) {}
    public static View view(ItemStack stack) {
        if (!(stack.getItem() instanceof PrimalCellItem cell)) return new View(false, false, 0, 0, null);
        var state = state(stack);
        return new View(true, state.isPresent(), state.map(PrimalCellData.State::amount).orElse(0L), cell.capacity(),
                state.map(PrimalCellData.State::cellId).orElse(null));
    }
    /** A pristine empty cell has no identity until its first committed write. Reads never mutate items. */
    public static Optional<PrimalCellData.State> state(ItemStack stack) {
        if (!(stack.getItem() instanceof PrimalCellItem cell) || stack.getCount() != 1) return Optional.empty();
        var data = stack.get(PrimalCellData.TYPE.get());
        var decoded = data == null ? Optional.of(new PrimalCellData.State(new UUID(0, 0), 0, 0, Map.of())) : data.open(cell.capacity());
        try {
            return decoded.flatMap(value -> authority.apply(value, stack).filter(resolved -> resolved.cellId().equals(value.cellId())
                    && resolved.amount() <= cell.capacity() && resolved.authorityRevision() >= value.authorityRevision()));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    public static long sourceUnits(String itemId) {
        return switch (itemId) {
            case "rematter:primal_shard", "rematter:deepslate_primal_ore", "rematter:nether_primal_ore", "rematter:end_primal_ore" -> SHARD_UNITS;
            default -> 0;
        };
    }
    public static long sourceUnits(ItemStack stack) {
        // Components may carry unknown contents. Never destroy them through a generic item-value rule.
        return stack.isEmpty() || !stack.getComponentsPatch().isEmpty() ? 0 : sourceUnits(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    /** Loads whole source items only; a partial thousand never consumes an item. */
    public static long load(Journal destination, ItemStack source, long maximumItems, TransactionContext parent) {
        if (maximumItems <= 0) return 0;
        long units = sourceUnits(source); var view = view(destination.stack());
        if (units == 0 || !view.readable()) return 0;
        int items = (int)Math.min(Math.min(maximumItems, source.getCount()), (view.capacity() - view.amount()) / units);
        if (items == 0) return 0;
        var payment = destination.prepareDelta(Math.multiplyExact(items, units));
        if (payment.isEmpty()) return 0;
        var sourceJournal = new SnapshotJournal<Integer>() {
            @Override protected Integer createSnapshot() { return source.getCount(); }
            @Override protected void revertToSnapshot(Integer count) { source.setCount(count); }
            void consume(TransactionContext transaction) { updateSnapshots(transaction); source.shrink(items); }
        };
        try (var transaction = Transaction.open(parent)) {
            if (!payment.get().apply(transaction)) return 0;
            sourceJournal.consume(transaction); transaction.commit(); return Math.multiplyExact(items, units);
        }
    }
    /** Both cells retain their identity and receipts; the exact quantity moves once or neither changes. */
    public static long transfer(Journal source, Journal destination, long maximum, TransactionContext parent) {
        if (maximum <= 0 || source.stack() == destination.stack()) return 0;
        var from = view(source.stack()); var to = view(destination.stack());
        if (!from.readable() || !to.readable() || from.cellId() != null && from.cellId().equals(to.cellId()) && from.amount() > 0) return 0;
        long amount = Math.min(maximum, Math.min(from.amount(), to.capacity() - to.amount()));
        if (amount == 0) return 0;
        var debit = source.prepareDelta(-amount); var credit = destination.prepareDelta(amount);
        if (debit.isEmpty() || credit.isEmpty()) return 0;
        try (var transaction = Transaction.open(parent)) {
            if (!debit.get().apply(transaction) || !credit.get().apply(transaction)) return 0;
            transaction.commit(); return amount;
        }
    }
    /** One journal per explicitly mounted slot, shared by all of that slot's operations. */
    public static final class Journal extends SnapshotJournal<Optional<PrimalCellData>> {
        private final Supplier<ItemStack> slot;
        private ItemStack transactionStack = ItemStack.EMPTY;
        public Journal(Supplier<ItemStack> slot) { this.slot = Objects.requireNonNull(slot); }
        public ItemStack stack() { return slot.get(); }
        public Optional<Payment> prepareDebit(UUID operation, long amount) { return prepare(operation, amount, false); }
        public Optional<Payment> prepareRefund(UUID operation, long amount) { return prepare(operation, amount, true); }
        private Optional<Payment> prepareDelta(long delta) {
            var current = stack(); var opened = state(current); if (opened.isEmpty() || delta == 0) return Optional.empty();
            var before = opened.get(); long amount;
            try { amount = Math.addExact(before.amount(), delta); } catch (ArithmeticException invalid) { return Optional.empty(); }
            if (amount < 0 || amount > ((PrimalCellItem)current.getItem()).capacity()) return Optional.empty();
            if (before.cellId().equals(new UUID(0, 0))) before = new PrimalCellData.State(UUID.randomUUID(), before.amount(), before.authorityRevision(), before.receipts());
            return Optional.of(new Payment(this, current.copy(), before, before.withAmount(amount), UUID.randomUUID(), false));
        }
        /**
         * The caller must have durably finalized this operation in its authoritative deduplication ledger first.
         * Merely committing an in-memory transaction is insufficient: premature acknowledgement reopens replay.
         */
        public boolean acknowledgeFinalized(UUID operation, long delta, TransactionContext transaction) {
            var current = stack(); var opened = state(current); if (opened.isEmpty()) return false;
            var before = opened.get();
            var settled = receiptLookup.apply(before.cellId(), operation).orElse(null);
            if (settled == null || !settled.cellId().equals(before.cellId()) || settled.delta() != delta || settled.authorityRevision() > before.authorityRevision()
                    || !Objects.equals(before.receipts().get(operation), delta) || isInTransaction() && current != transactionStack) return false;
            var receipts = new java.util.HashMap<>(before.receipts()); receipts.remove(operation);
            updateSnapshots(transaction);
            current.set(PrimalCellData.TYPE.get(), PrimalCellData.from(new PrimalCellData.State(before.cellId(), before.amount(), before.authorityRevision(), receipts)));
            return true;
        }
        private Optional<Payment> prepare(UUID operation, long amount, boolean refund) {
            if (amount <= 0) throw new IllegalArgumentException("Payment must be positive");
            var stack = stack(); var opened = state(stack); if (opened.isEmpty()) return Optional.empty();
            var before = opened.get(); long delta = refund ? amount : -amount;
            Optional<Receipt> receipt;
            try { receipt = receiptLookup.apply(before.cellId(), operation); } catch (RuntimeException unavailable) { return Optional.empty(); }
            if (receipt.isPresent()) {
                var known = receipt.get();
                return known.cellId().equals(before.cellId()) && known.delta() == delta && known.authorityRevision() <= before.authorityRevision()
                        ? Optional.of(new Payment(this, stack.copy(), before, before, operation, true)) : Optional.empty();
            }
            if (before.receipts().containsKey(operation)) return Optional.empty();
            long after;
            try { after = Math.addExact(before.amount(), delta); } catch (ArithmeticException invalid) { return Optional.empty(); }
            if (after < 0 || after > ((PrimalCellItem) stack.getItem()).capacity() || before.receipts().size() == PrimalCellData.MAX_RECEIPTS) return Optional.empty();
            if (before.cellId().equals(new UUID(0, 0))) before = new PrimalCellData.State(UUID.randomUUID(), before.amount(), before.authorityRevision(), before.receipts());
            return Optional.of(new Payment(this, stack.copy(), before, before.withReceipt(operation, delta), operation, false));
        }
        private boolean apply(Payment payment, TransactionContext transaction) {
            var current = stack();
            if (!ItemStack.isSameItemSameComponents(current, payment.expected) || current.getCount() != 1) return false;
            if (payment.alreadyApplied) return true;
            if (isInTransaction() && current != transactionStack) return false;
            updateSnapshots(transaction); current.set(PrimalCellData.TYPE.get(), PrimalCellData.from(payment.after)); return true;
        }
        @Override protected Optional<PrimalCellData> createSnapshot() { transactionStack = stack(); return Optional.ofNullable(transactionStack.get(PrimalCellData.TYPE.get())); }
        @Override protected void revertToSnapshot(Optional<PrimalCellData> snapshot) {
            if (snapshot.isPresent()) transactionStack.set(PrimalCellData.TYPE.get(), snapshot.get()); else transactionStack.remove(PrimalCellData.TYPE.get());
        }
        @Override protected void onRootCommit(Optional<PrimalCellData> snapshot) { transactionStack = ItemStack.EMPTY; }
    }
    public static final class Payment {
        private final Journal journal; private final ItemStack expected;
        private final PrimalCellData.State before, after; private final UUID operation; private final boolean alreadyApplied;
        private Payment(Journal journal, ItemStack expected, PrimalCellData.State before, PrimalCellData.State after, UUID operation, boolean alreadyApplied) {
            this.journal = journal; this.expected = expected; this.before = before; this.after = after; this.operation = operation; this.alreadyApplied = alreadyApplied;
        }
        public UUID cellId() { return after.cellId(); }
        public UUID operationId() { return operation; }
        public long beforeAmount() { return before.amount(); }
        public long afterAmount() { return after.amount(); }
        public boolean alreadyApplied() { return alreadyApplied; }
        public boolean apply(TransactionContext transaction) { return journal.apply(this, transaction); }
    }
}
