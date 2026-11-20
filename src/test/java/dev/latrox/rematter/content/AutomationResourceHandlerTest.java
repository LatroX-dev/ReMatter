package dev.latrox.rematter.content;

import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomationResourceHandlerTest {
    private record TestResource(boolean isEmpty) implements Resource {}
    private static final TestResource EMPTY = new TestResource(true);
    private static final TestResource MATERIAL = new TestResource(false);

    @Test void cachedHandleBecomesInertWithoutQueryingTheDetachedContainer() {
        AtomicBoolean live = new AtomicBoolean(true);
        TestContainer delegate = new TestContainer(live);
        ResourceHandler<TestResource> cached = new AutomationResourceHandler<>(delegate, live::get, 1, EMPTY);
        assertEquals(1, cached.size());
        assertEquals(10, cached.getAmountAsLong(0));

        live.set(false); // Models the owner disabling automation, unloading, or replacing the BE.
        assertEquals(1, cached.size());
        assertEquals(EMPTY, cached.getResource(0));
        assertEquals(0, cached.getAmountAsLong(0));
        assertEquals(0, cached.getCapacityAsLong(0, MATERIAL));
        assertFalse(cached.isValid(0, MATERIAL));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, cached.insert(0, MATERIAL, 5, tx));
            assertEquals(0, cached.extract(0, MATERIAL, 5, tx));
            assertEquals(0, cached.insert(MATERIAL, 5, tx));
            assertEquals(0, cached.extract(MATERIAL, 5, tx));
            tx.commit();
        }
        assertEquals(10, delegate.amount);
    }

    @Test void guardedTransfersKeepTheCallersNestedTransactionAndRollback() {
        AtomicBoolean live = new AtomicBoolean(true);
        TestContainer delegate = new TestContainer(live);
        ResourceHandler<TestResource> cached = new AutomationResourceHandler<>(delegate, live::get, 1, EMPTY);
        try (Transaction outer = Transaction.openRoot()) {
            assertEquals(4, cached.extract(MATERIAL, 4, outer));
            try (Transaction nested = Transaction.open(outer)) {
                assertEquals(2, cached.insert(MATERIAL, 2, nested));
                nested.commit();
            }
            assertEquals(8, delegate.amount);
        }
        assertEquals(10, delegate.amount);
        try (Transaction committed = Transaction.openRoot()) {
            assertEquals(3, cached.insert(MATERIAL, 3, committed));
            committed.commit();
        }
        assertEquals(13, delegate.amount);
    }

    @Test void anOutputOnlyFaceNeverAdvertisesInsertableResources() {
        AtomicBoolean input = new AtomicBoolean(false);
        AtomicBoolean output = new AtomicBoolean(true);
        TestContainer delegate = new TestContainer(new AtomicBoolean(true));
        ResourceHandler<TestResource> face = new AutomationResourceHandler<>(delegate, input::get, output::get, 1, EMPTY);
        assertFalse(face.isValid(0, MATERIAL));
        assertEquals(10, face.getAmountAsLong(0));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(0, face.insert(0, MATERIAL, 5, tx));
            assertEquals(5, face.extract(0, MATERIAL, 5, tx));
        }
        input.set(true);
        output.set(false);
        assertTrue(face.isValid(0, MATERIAL));
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(5, face.insert(0, MATERIAL, 5, tx));
            assertEquals(0, face.extract(0, MATERIAL, 5, tx));
        }
        assertEquals(10, delegate.amount);
    }

    private static final class TestContainer extends SnapshotJournal<Integer> implements ResourceHandler<TestResource> {
        private final AtomicBoolean live;
        private int amount = 10;
        TestContainer(AtomicBoolean live) { this.live = live; }
        private void accessible() { assertTrue(live.get(), "A stale wrapper touched its detached backing container"); }
        @Override public int size() { accessible(); return 1; }
        @Override public TestResource getResource(int index) { accessible(); return amount > 0 ? MATERIAL : EMPTY; }
        @Override public long getAmountAsLong(int index) { accessible(); return amount; }
        @Override public long getCapacityAsLong(int index, TestResource resource) { accessible(); return 100; }
        @Override public boolean isValid(int index, TestResource resource) { accessible(); return !resource.isEmpty(); }
        @Override public int insert(int index, TestResource resource, int requested, TransactionContext tx) {
            accessible(); updateSnapshots(tx); int moved = Math.min(requested, 100 - amount); amount += moved; return moved;
        }
        @Override public int extract(int index, TestResource resource, int requested, TransactionContext tx) {
            accessible(); updateSnapshots(tx); int moved = Math.min(requested, amount); amount -= moved; return moved;
        }
        @Override protected Integer createSnapshot() { return amount; }
        @Override protected void revertToSnapshot(Integer previous) { amount = previous; }
    }
}
