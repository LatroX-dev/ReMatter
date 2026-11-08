package dev.latrox.rematter.content;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyncedRowsTest {
    private static MachineSnapshot.Entry entry(String id, long amount) { return new MachineSnapshot.Entry(id, "solid", amount, true); }

    @Test void firstUpdateSendsEveryRowAndAnUnchangedListSendsNothing() {
        var server = new SyncedRows<MachineSnapshot.StockKey, MachineSnapshot.Entry>(MachineSnapshot.Entry::key);
        var rows = List.of(entry("rematter:iron", 5), entry("rematter:copper", 7));
        var first = server.update(rows);
        assertEquals(2, first.changed().size());
        assertTrue(first.removed().isEmpty());
        assertTrue(server.update(rows).isEmpty());
    }

    @Test void onlyChangedAndRemovedRowsTravelAndTheClientCopyConverges() {
        var server = new SyncedRows<MachineSnapshot.StockKey, MachineSnapshot.Entry>(MachineSnapshot.Entry::key);
        var client = new SyncedRows<MachineSnapshot.StockKey, MachineSnapshot.Entry>(MachineSnapshot.Entry::key);
        var initial = server.update(List.of(entry("rematter:iron", 5), entry("rematter:copper", 7), entry("rematter:tin", 1)));
        client.apply(true, initial.changed(), initial.removed());
        var next = List.of(entry("rematter:iron", 5), entry("rematter:copper", 8), entry("rematter:zinc", 2));
        var delta = server.update(next);
        assertEquals(List.of(entry("rematter:copper", 8), entry("rematter:zinc", 2)), delta.changed());
        assertEquals(List.of(new MachineSnapshot.StockKey("rematter:tin", "solid")), delta.removed());
        client.apply(false, delta.changed(), delta.removed());
        assertEquals(server.values(), client.values());
        assertEquals(List.of(entry("rematter:copper", 8), entry("rematter:iron", 5), entry("rematter:zinc", 2)), client.values());
    }

    @Test void aResetReplacesWhateverTheClientHeld() {
        var client = new SyncedRows<MachineSnapshot.StockKey, MachineSnapshot.Entry>(MachineSnapshot.Entry::key);
        client.apply(false, List.of(entry("rematter:stale", 1)), List.of());
        client.apply(true, List.of(entry("rematter:iron", 2)), List.of());
        assertEquals(List.of(entry("rematter:iron", 2)), client.values());
        assertNull(client.get(new MachineSnapshot.StockKey("rematter:stale", "solid")));
    }
}
