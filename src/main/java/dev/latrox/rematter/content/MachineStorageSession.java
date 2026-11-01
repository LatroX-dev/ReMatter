package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterStore;
import java.util.List;
import java.util.Objects;

/** An unpaid in-progress batch may resume only against the same owned inventory. */
final class MachineStorageSession {
    private List<MatterInventory> bound;
    boolean accepts(MatterStore inventory) { return bound == null || bound.equals(inventory.members()); }
    void bind(MatterStore inventory) {
        Objects.requireNonNull(inventory);
        if (!accepts(inventory)) throw new IllegalStateException("Storage changed during a batch");
        bound = List.copyOf(inventory.members());
    }
    void clear() { bound = null; }
}
