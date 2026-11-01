package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import java.util.Objects;

/** An unpaid in-progress batch may resume only against the same owned inventory. */
final class MachineStorageSession {
    private MatterInventory bound;
    boolean accepts(MatterInventory inventory) { return bound == null || bound == inventory; }
    void bind(MatterInventory inventory) {
        Objects.requireNonNull(inventory);
        if (!accepts(inventory)) throw new IllegalStateException("Storage changed during a batch");
        bound = inventory;
    }
    void clear() { bound = null; }
}
