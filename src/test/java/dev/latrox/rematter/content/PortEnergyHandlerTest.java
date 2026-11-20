package dev.latrox.rematter.content;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortEnergyHandlerTest {
    @Test void twoPortsShareOneStoreAndNestedRollbackCannotDuplicateCapacity() {
        var owner = new SimpleEnergyHandler(1000, 1000, 0);
        var first = new PortEnergyHandler(owner, () -> true);
        var second = new PortEnergyHandler(owner, () -> true);
        try (var root = Transaction.openRoot()) {
            assertEquals(600, first.insert(600, root));
            try (var nested = Transaction.open(root)) {
                assertEquals(400, second.insert(600, nested));
                assertEquals(1000, first.getAmountAsLong());
            }
            assertEquals(600, second.getAmountAsLong());
            root.commit();
        }
        assertEquals(600, owner.getAmountAsLong());
        try (var tx = Transaction.openRoot()) { assertEquals(0, second.extract(600, tx)); tx.commit(); }
        assertEquals(600, owner.getAmountAsLong());
    }
    @Test void simulationRollsBackEvenWhenTheStructureBecomesInvalidBeforeClosing() {
        var owner = new SimpleEnergyHandler(1000, 1000, 0);
        var formed = new AtomicBoolean(true);
        var port = new PortEnergyHandler(owner, formed::get);
        try (var tx = Transaction.openRoot()) {
            assertEquals(700, port.insert(700, tx));
            formed.set(false);
            assertEquals(0, port.insert(1, tx));
            assertEquals(0, port.getCapacityAsLong());
        }
        assertEquals(0, owner.getAmountAsLong());
        formed.set(true);
        assertEquals(0, port.getAmountAsLong());
    }
    @Test void cachedViewNeverRetargetsAReplacementController() {
        var original = new SimpleEnergyHandler(1000, 1000, 0);
        var replacement = new SimpleEnergyHandler(1000, 1000, 0);
        var current = new AtomicReference<>(original);
        var oldPort = new PortEnergyHandler(original, () -> current.get() == original);
        current.set(replacement);
        try (var tx = Transaction.openRoot()) { assertEquals(0, oldPort.insert(500, tx)); tx.commit(); }
        assertEquals(0, original.getAmountAsLong()); assertEquals(0, replacement.getAmountAsLong());
    }
    @Test void DisabledViewsStillRejectNegativeTransferRequests() {
        var port = new PortEnergyHandler(new SimpleEnergyHandler(1000, 1000, 0), () -> false);
        try (var tx = Transaction.openRoot()) {
            assertThrows(IllegalArgumentException.class, () -> port.insert(-1, tx));
            assertThrows(IllegalArgumentException.class, () -> port.extract(-1, tx));
        }
    }
}
