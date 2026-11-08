package dev.latrox.rematter.chemistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.HashSet;

/**
 * Exact, capacity-limited storage. All changes, including transfers between two
 * stores, share a lock so callers cannot observe a partially committed transfer.
 * Definitions are intentionally not required when restoring saved resources.
 */
public final class MatterInventory implements MatterStore {
    public static final int SCHEMA_VERSION = 1;
    static final Object MUTATION_LOCK = new Object();

    private long capacity;
    private long total;
    private long revision;
    private final int maximumTypes;
    private final TreeMap<SubstanceKey, Long> contents = new TreeMap<>();
    private final LinkedHashMap<UUID, Reservation> reservations = new LinkedHashMap<>();

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
    /** Changes with every committed change to quantities, capacity or reservations, and never repeats. */
    @Override public long revision() { synchronized (MUTATION_LOCK) { return revision; } }
    public boolean hasTypeSpace(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        var checkedInputs = MatterAmounts.copy(inputs); var checkedOutputs = MatterAmounts.copy(outputs);
        synchronized (MUTATION_LOCK) { return hasTypeSpaceLocked(checkedInputs, checkedOutputs, null); }
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
            return capacity - total - reservedExpansion(null);
        }
    }

    public boolean setCapacity(long newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        synchronized (MUTATION_LOCK) {
            if (newCapacity < total || reservedExpansion(null) > newCapacity - total) {
                return false;
            }
            capacity = newCapacity;
            revision++;
            return true;
        }
    }

    public long amount(SubstanceKey key) {
        Objects.requireNonNull(key, "key");
        synchronized (MUTATION_LOCK) {
            return contents.getOrDefault(key, 0L);
        }
    }

    public long available(SubstanceKey key) {
        Objects.requireNonNull(key, "key");
        synchronized (MUTATION_LOCK) {
            return availableLocked(key, null);
        }
    }

    public long reserved(SubstanceKey key) {
        Objects.requireNonNull(key, "key");
        synchronized (MUTATION_LOCK) {
            return contents.getOrDefault(key, 0L) - availableLocked(key, null);
        }
    }

    public Map<SubstanceKey, Long> contents() {
        synchronized (MUTATION_LOCK) {
            return Collections.unmodifiableMap(new TreeMap<>(contents));
        }
    }

    public Map<SubstanceKey, Long> availableContents() {
        synchronized (MUTATION_LOCK) {
            TreeMap<SubstanceKey, Long> available = new TreeMap<>();
            for (SubstanceKey key : contents.keySet()) {
                long amount = availableLocked(key, null);
                if (amount > 0) {
                    available.put(key, amount);
                }
            }
            return Collections.unmodifiableMap(available);
        }
    }

    /** Returns the amount inserted, without discarding the caller's remainder. */
    public long insert(SubstanceKey key, long maximum) {
        Objects.requireNonNull(key, "key");
        requireNonNegative(maximum);
        synchronized (MUTATION_LOCK) {
            if (!hasTypeSpaceLocked(Map.of(), Map.of(key, 1L), null)) return 0;
            long accepted = Math.min(maximum, capacity - total - reservedExpansion(null));
            if (accepted > 0) {
                contents.merge(key, accepted, Math::addExact);
                total += accepted;
                revision++;
            }
            return accepted;
        }
    }

    /** Reserved inputs cannot be extracted by another machine. */
    public long extract(SubstanceKey key, long maximum) {
        Objects.requireNonNull(key, "key");
        requireNonNegative(maximum);
        synchronized (MUTATION_LOCK) {
            long extracted = Math.min(maximum, availableLocked(key, null));
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
            return canApplyLocked(checkedInputs, checkedOutputs, null);
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
            if (!canApplyLocked(checkedInputs, checkedOutputs, null)) {
                return false;
            }
            applyLocked(checkedInputs, checkedOutputs);
            return true;
        }
    }

    /** Reserves input quantities and any additional output capacity without consuming either. */
    public Optional<UUID> reserve(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        Map<SubstanceKey, Long> checkedInputs = MatterAmounts.copy(inputs);
        Map<SubstanceKey, Long> checkedOutputs = MatterAmounts.copy(outputs);
        if (checkedInputs.isEmpty() && checkedOutputs.isEmpty()) {
            throw new IllegalArgumentException("An empty operation cannot be reserved");
        }
        synchronized (MUTATION_LOCK) {
            if (!canReserveLocked(checkedInputs, checkedOutputs)) {
                return Optional.empty();
            }
            UUID token = UUID.randomUUID();
            reservations.put(token, new Reservation(token, checkedInputs, checkedOutputs));
            revision++;
            return Optional.of(token);
        }
    }

    /** Tokens are single use. A repeated completion never duplicates output. */
    public boolean commit(UUID token) {
        Objects.requireNonNull(token, "token");
        synchronized (MUTATION_LOCK) {
            Reservation reservation = reservations.get(token);
            if (reservation == null) {
                return false;
            }
            if (!canApplyLocked(reservation.inputs(), reservation.outputs(), token)) {
                throw new IllegalStateException("A reserved operation lost its resources or capacity");
            }
            applyLocked(reservation.inputs(), reservation.outputs());
            reservations.remove(token);
            return true;
        }
    }

    public boolean cancel(UUID token) {
        Objects.requireNonNull(token, "token");
        synchronized (MUTATION_LOCK) {
            if (reservations.remove(token) == null) return false;
            revision++;
            return true;
        }
    }

    public Optional<Reservation> reservation(UUID token) {
        synchronized (MUTATION_LOCK) {
            return Optional.ofNullable(reservations.get(token));
        }
    }

    /** Moves the entire unreserved inventory to an empty destination, or leaves both owners unchanged. */
    public boolean transferAllTo(MatterInventory destination) {
        Objects.requireNonNull(destination, "destination");
        synchronized (MUTATION_LOCK) {
            if (destination == this || destination.total != 0 || !reservations.isEmpty() || !destination.reservations.isEmpty()
                    || !destination.canApplyLocked(Map.of(), contents, null)) return false;
            destination.applyLocked(Map.of(), contents);
            contents.clear();
            total = 0;
            revision++;
            return true;
        }
    }

    /** Moves only what the destination can retain. No intermediate resource ownership is exposed. */
    public long transferTo(MatterInventory destination, SubstanceKey key, long maximum) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(key, "key");
        requireNonNegative(maximum);
        if (destination == this || maximum == 0) {
            return 0;
        }
        synchronized (MUTATION_LOCK) {
            if (!destination.hasTypeSpaceLocked(Map.of(), Map.of(key, 1L), null)) return 0;
            long moved = Math.min(maximum, availableLocked(key, null));
            moved = Math.min(moved,
                    destination.capacity - destination.total - destination.reservedExpansion(null));
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
            return new Snapshot(SCHEMA_VERSION, capacity, stacks, List.copyOf(reservations.values()));
        }
    }

    /**
     * Invalid or newer data is rejected instead of replacing it with an empty store.
     * Missing substance definitions are deliberately preserved by ID and phase.
     */
    public static MatterInventory restore(Snapshot snapshot) {
        return restore(snapshot, Integer.MAX_VALUE);
    }
    public static MatterInventory restore(Snapshot snapshot, int maximumTypes) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported matter storage schema: " + snapshot.schemaVersion());
        }
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
            for (Reservation reservation : snapshot.reservations()) {
                if (restored.reservations.containsKey(reservation.token())
                        || !restored.canReserveLocked(reservation.inputs(), reservation.outputs())) {
                    throw new IllegalArgumentException("Invalid saved reservation: " + reservation.token());
                }
                restored.reservations.put(reservation.token(), reservation);
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
        reservations.clear();
        reservations.putAll(restored.reservations);
        revision++;
    }

    /** A detached working copy for planning. Its state is already valid, so it needs no second validation. */
    MatterInventory planningCopy() {
        synchronized (MUTATION_LOCK) {
            var copy = new MatterInventory(capacity, maximumTypes);
            copy.total = total;
            copy.contents.putAll(contents);
            copy.reservations.putAll(reservations);
            return copy;
        }
    }

    /** Whether the key already holds one of this store's type slots, as stock or as a reserved output. */
    boolean occupiesType(SubstanceKey key) {
        synchronized (MUTATION_LOCK) {
            if (contents.containsKey(key)) return true;
            for (Reservation reservation : reservations.values()) if (reservation.outputs().containsKey(key)) return true;
            return false;
        }
    }

    /** Type slots held by stock and reserved outputs, without copying the stock table. */
    int occupiedTypes() {
        synchronized (MUTATION_LOCK) {
            if (reservations.isEmpty()) return contents.size();
            var types = new HashSet<>(contents.keySet());
            for (Reservation reservation : reservations.values()) types.addAll(reservation.outputs().keySet());
            return types.size();
        }
    }

    private boolean canReserveLocked(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        if (reservations.size() >= maximumTypes) return false;
        for (Map.Entry<SubstanceKey, Long> input : inputs.entrySet()) {
            if (availableLocked(input.getKey(), null) < input.getValue()) {
                return false;
            }
        }
        long expansion = Math.max(0, MatterAmounts.total(outputs) - MatterAmounts.total(inputs));
        return expansion <= capacity - total - reservedExpansion(null) && hasTypeSpaceLocked(inputs, outputs, null);
    }

    private boolean canApplyLocked(
            Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs, UUID excludedReservation) {
        for (Map.Entry<SubstanceKey, Long> input : inputs.entrySet()) {
            if (availableLocked(input.getKey(), excludedReservation) < input.getValue()) {
                return false;
            }
        }
        long remaining = total - MatterAmounts.total(inputs);
        long occupiedByReservations = reservedExpansion(excludedReservation);
        return MatterAmounts.total(outputs) <= capacity - remaining - occupiedByReservations
                && hasTypeSpaceLocked(inputs, outputs, excludedReservation);
    }

    private boolean hasTypeSpaceLocked(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs, UUID excludedReservation) {
        if (maximumTypes == Integer.MAX_VALUE) return true;
        var types = new HashSet<SubstanceKey>();
        contents.forEach((key, amount) -> { if (amount > inputs.getOrDefault(key, 0L)) types.add(key); });
        types.addAll(outputs.keySet());
        for (var reservation : reservations.values()) if (!reservation.token().equals(excludedReservation)) types.addAll(reservation.outputs().keySet());
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

    private long availableLocked(SubstanceKey key, UUID excludedReservation) {
        long available = contents.getOrDefault(key, 0L);
        for (Reservation reservation : reservations.values()) {
            if (!reservation.token().equals(excludedReservation)) {
                available -= reservation.inputs().getOrDefault(key, 0L);
            }
        }
        return available;
    }

    private long reservedExpansion(UUID excludedReservation) {
        long totalExpansion = 0;
        for (Reservation reservation : reservations.values()) {
            if (!reservation.token().equals(excludedReservation)) {
                long expansion = Math.max(0,
                        MatterAmounts.total(reservation.outputs()) - MatterAmounts.total(reservation.inputs()));
                totalExpansion = Math.addExact(totalExpansion, expansion);
            }
        }
        return totalExpansion;
    }

    private static void requireNonNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    public record Reservation(UUID token, Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        public Reservation {
            Objects.requireNonNull(token, "token");
            inputs = MatterAmounts.copy(inputs);
            outputs = MatterAmounts.copy(outputs);
            if (inputs.isEmpty() && outputs.isEmpty()) {
                throw new IllegalArgumentException("An empty operation cannot be reserved");
            }
        }
    }

    public record Snapshot(
            int schemaVersion,
            long capacity,
            List<SubstanceStack> contents,
            List<Reservation> reservations) {
        public Snapshot {
            contents = List.copyOf(contents);
            reservations = List.copyOf(reservations);
        }
    }
}
