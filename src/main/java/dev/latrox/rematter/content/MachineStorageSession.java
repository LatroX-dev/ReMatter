package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterStore;
import java.util.List;
import java.util.Objects;

/** An unpaid in-progress batch may resume only against the same owned inventory. */
final class MachineStorageSession {
    private List<MatterInventory> bound;
    private Object boundVoid;
    boolean accepts(MatterStore inventory) {
        return bound == null || (bound.equals(inventory.members()) && Objects.equals(boundVoid, inventory.voidSignature()));
    }
    void bind(MatterStore inventory) {
        Objects.requireNonNull(inventory);
        if (!accepts(inventory)) throw new IllegalStateException("Storage changed during a batch");
        bound = List.copyOf(inventory.members());
        boundVoid = inventory.voidSignature();
    }
    void clear() { bound = null; boundVoid = null; }
}
