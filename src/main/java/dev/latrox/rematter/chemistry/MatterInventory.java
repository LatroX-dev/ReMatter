package dev.latrox.rematter.chemistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.HashSet;

/**
 * Exact, capacity-limited storage. All changes, including transfers between two
 * stores, share a lock so callers cannot observe a partially committed transfer.
 * Definitions are intentionally not required when restoring saved resources.
 */
public final class MatterInventory implements MatterStore {
    static final Object MUTATION_LOCK = new Object();

    private long capacity;
    private long total;
    private long revision;
    private final int maximumTypes;
    private final TreeMap<SubstanceKey, Long> contents = new TreeMap<>();

    public MatterInventory(long capacity) {
        this(capacity, Integer.MAX_VALUE);
    }

    public MatterInventory(long capacity, int maximumTypes) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        if (maximumTypes < 1) throw new IllegalArgumentException("Type capacity must be positive");
        this.maximumTypes = maximumTypes;
    }

    public int maximumTypes() { return maximumTypes; }
    @Override public List<MatterInventory> members() { return List.of(this); }
    public int typeCount() { synchronized (MUTATION_LOCK) { return contents.size(); } }
    /** Changes with every committed change to quantities or capacity, and never repeats. */
    @Override public long revision() { synchronized (MUTATION_LOCK) { return revision; } }
    public boolean hasTypeSpace(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        var checkedInputs = MatterAmounts.copy(inputs); var checkedOutputs = MatterAmounts.copy(outputs);
        synchronized (MUTATION_LOCK) { return hasTypeSpaceLocked(checkedInputs, checkedOutputs); }
    }

    public long capacity() {
        synchronized (MUTATION_LOCK) {
            return capacity;
        }
    }

    public long total() {
        synchronized (MUTATION_LOCK) {
            return total;
        }
    }

    public long freeSpace() {
        synchronized (MUTATION_LOCK) {
            return capacity - total;
        }
    }

    public long amount(SubstanceKey key) {
        Objects.requireNonNull(key, "key");
        synchronized (MUTATION_LOCK) {
            return contents.getOrDefault(key, 0L);
        }
    }

    public Map<SubstanceKey, Long> contents() {
        synchronized (MUTATION_LOCK) {
            return Collections.unmodifiableMap(new TreeMap<>(contents));
        }
    }

    /** Returns the amount inserted, without discarding the caller's remainder. */
    public long insert(SubstanceKey key, long maximum) {
        Objects.requireNonNull(key, "key");
        requireNonNegative(maximum);
        if (ReservedSubstances.reserved(key)) return 0;
        synchronized (MUTATION_LOCK) {
            if (!hasTypeSpaceLocked(Map.of(), Map.of(key, 1L))) return 0;
            long accepted = Math.min(maximum, capacity - total);
            if (accepted > 0) {
                contents.merge(key, accepted, Math::addExact);
                total += accepted;
                revision++;
            }
            return accepted;
        }
    }

    public long extract(SubstanceKey key, long maximum) {
        Objects.requireNonNull(key, "key");
        requireNonNegative(maximum);
        if (ReservedSubstances.reserved(key)) return 0;
        synchronized (MUTATION_LOCK) {
            long extracted = Math.min(maximum, contents.getOrDefault(key, 0L));
            if (extracted > 0) {
                subtract(key, extracted);
                total -= extracted;
                revision++;
            }
            return extracted;
        }
    }

    public boolean canApply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        Map<SubstanceKey, Long> checkedInputs = MatterAmounts.copy(inputs);
        Map<SubstanceKey, Long> checkedOutputs = MatterAmounts.copy(outputs);
        synchronized (MUTATION_LOCK) {
            return canApplyLocked(checkedInputs, checkedOutputs);
        }
    }

    /**
     * Atomically replaces inputs with outputs or leaves the entire store unchanged.
     * External item/fluid owners must commit their side only after success, on the
     * same authoritative server thread, with no callbacks between the two steps.
     */
    public boolean apply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        Map<SubstanceKey, Long> checkedInputs = MatterAmounts.copy(inputs);
        Map<SubstanceKey, Long> checkedOutputs = MatterAmounts.copy(outputs);
        synchronized (MUTATION_LOCK) {
            if (!canApplyLocked(checkedInputs, checkedOutputs)) {
                return false;
            }
            applyLocked(checkedInputs, checkedOutputs);
            return true;
        }
    }

    /** Moves only what the destination can retain. No intermediate resource ownership is exposed. */
    public long transferTo(MatterInventory destination, SubstanceKey key, long maximum) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(key, "key");
        requireNonNegative(maximum);
        if (ReservedSubstances.reserved(key)) return 0;
        if (destination == this || maximum == 0) {
            return 0;
        }
        synchronized (MUTATION_LOCK) {
            if (!destination.hasTypeSpaceLocked(Map.of(), Map.of(key, 1L))) return 0;
            long moved = Math.min(Math.min(maximum, contents.getOrDefault(key, 0L)), destination.capacity - destination.total);
            if (moved == 0) {
                return 0;
            }
            destination.contents.merge(key, moved, Math::addExact);
            destination.total += moved;
            destination.revision++;
            subtract(key, moved);
            total -= moved;
            revision++;
            return moved;
        }
    }

    public Snapshot snapshot() {
        synchronized (MUTATION_LOCK) {
            List<SubstanceStack> stacks = new ArrayList<>(contents.size());
            contents.forEach((key, quantity) -> stacks.add(new SubstanceStack(key, quantity)));
            return new Snapshot(capacity, stacks);
        }
    }

    /**
     * Invalid or newer data is rejected instead of replacing it with an empty store.
     * Missing substance definitions are deliberately preserved by ID and phase.
     */
    public static MatterInventory restore(Snapshot snapshot, int maximumTypes) {
        Objects.requireNonNull(snapshot, "snapshot");
        MatterInventory restored = new MatterInventory(snapshot.capacity(), maximumTypes);
        if (snapshot.contents().size() > maximumTypes) throw new IllegalArgumentException("Saved matter exceeds type capacity");
        synchronized (MUTATION_LOCK) {
            for (SubstanceStack stack : snapshot.contents()) {
                if (restored.contents.containsKey(stack.key())) {
                    throw new IllegalArgumentException("Duplicate stored substance: " + stack.key());
                }
                if (stack.quantity() > restored.capacity - restored.total) {
                    throw new IllegalArgumentException("Saved matter exceeds storage capacity");
                }
                restored.contents.put(stack.key(), stack.quantity());
                restored.total += stack.quantity();
            }
        }
        return restored;
    }

    /**
     * Restores a validated snapshot while retaining this storage's identity.
     * Intended for rollback on the authoritative thread: other holders of this
     * inventory must observe the rollback as well. No partial or invalid saved
     * state is ever applied, and unknown definitions remain untouched.
     */
    public void restoreInPlace(Snapshot snapshot) {
        MatterInventory restored = restore(snapshot, maximumTypes);
        synchronized (MUTATION_LOCK) {
            replaceWithValidated(restored);
        }
    }

    /** Only the network planner calls this after validating every member, under the shared mutation lock. */
    void replaceWithValidated(MatterInventory restored) {
        if (!Thread.holdsLock(MUTATION_LOCK) || restored.maximumTypes != maximumTypes)
            throw new IllegalStateException("Unvalidated inventory replacement");
        capacity = restored.capacity;
        total = restored.total;
        contents.clear();
        contents.putAll(restored.contents);
        revision++;
    }

    /** A detached working copy for planning. Its state is already valid, so it needs no second validation. */
    MatterInventory planningCopy() {
        synchronized (MUTATION_LOCK) {
            var copy = new MatterInventory(capacity, maximumTypes);
            copy.total = total;
            copy.contents.putAll(contents);
            return copy;
        }
    }

    /** Whether the key already holds one of this store's type slots. */
    boolean occupiesType(SubstanceKey key) {
        synchronized (MUTATION_LOCK) {
            return contents.containsKey(key);
        }
    }

    private boolean canApplyLocked(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        if (inputs.keySet().stream().anyMatch(ReservedSubstances::reserved) || outputs.keySet().stream().anyMatch(ReservedSubstances::reserved)) return false;
        for (Map.Entry<SubstanceKey, Long> input : inputs.entrySet()) {
            if (contents.getOrDefault(input.getKey(), 0L) < input.getValue()) {
                return false;
            }
        }
        long remaining = total - MatterAmounts.total(inputs);
        return MatterAmounts.total(outputs) <= capacity - remaining && hasTypeSpaceLocked(inputs, outputs);
    }

    private boolean hasTypeSpaceLocked(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        if (inputs.keySet().stream().anyMatch(ReservedSubstances::reserved) || outputs.keySet().stream().anyMatch(ReservedSubstances::reserved)) return false;
        if (maximumTypes == Integer.MAX_VALUE) return true;
        var types = new HashSet<SubstanceKey>();
        contents.forEach((key, amount) -> { if (amount > inputs.getOrDefault(key, 0L)) types.add(key); });
        types.addAll(outputs.keySet());
        return types.size() <= maximumTypes;
    }

    private void applyLocked(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        inputs.forEach(this::subtract);
        outputs.forEach((key, quantity) -> contents.merge(key, quantity, Math::addExact));
        total = total - MatterAmounts.total(inputs) + MatterAmounts.total(outputs);
        revision++;
    }

    private void subtract(SubstanceKey key, long amount) {
        long remaining = contents.get(key) - amount;
        if (remaining == 0) {
            contents.remove(key);
        } else {
            contents.put(key, remaining);
        }
    }

    private static void requireNonNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    public record Snapshot(long capacity, List<SubstanceStack> contents) {
        public Snapshot {
            contents = List.copyOf(contents);
        }
    }
}
