package dev.latrox.rematter.automation;

import dev.latrox.rematter.content.AutomationResourceHandler;
import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomationResourceHandlerTest {
    private record Fluid(String id) implements Resource {
        static final Fluid EMPTY = new Fluid("");
        @Override public boolean isEmpty() { return id.isEmpty(); }
    }
    private static final class Tank implements ResourceHandler<Fluid> {
        long amount = 500;
        @Override public int size() { return 1; }
        @Override public Fluid getResource(int index) { return amount > 0 ? new Fluid("water") : Fluid.EMPTY; }
        @Override public long getAmountAsLong(int index) { return amount; }
        @Override public long getCapacityAsLong(int index, Fluid resource) { return 1_000; }
        @Override public boolean isValid(int index, Fluid resource) { return resource.id().equals("water"); }
        @Override public int insert(int index, Fluid resource, int amount, TransactionContext transaction) { return amount; }
        @Override public int extract(int index, Fluid resource, int amount, TransactionContext transaction) { return amount; }
    }

    @Test void anOutputOnlyFaceNeverAdvertisesInsertableResources() {
        var input = new AtomicBoolean(false);
        var output = new AtomicBoolean(true);
        var handler = new AutomationResourceHandler<>(new Tank(), input::get, output::get, 1, Fluid.EMPTY);
        var water = new Fluid("water");
        assertFalse(handler.isValid(0, water));
        assertEquals(500, handler.getAmountAsLong(0));
        try (var transaction = Transaction.openRoot()) {
            assertEquals(0, handler.insert(0, water, 100, transaction));
            assertEquals(100, handler.extract(0, water, 100, transaction));
        }
        input.set(true);
        output.set(false);
        assertTrue(handler.isValid(0, water));
        assertFalse(handler.isValid(0, new Fluid("lava")));
        try (var transaction = Transaction.openRoot()) {
            assertEquals(100, handler.insert(0, water, 100, transaction));
            assertEquals(0, handler.extract(0, water, 100, transaction));
        }
    }
}
