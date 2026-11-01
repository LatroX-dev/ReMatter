package dev.latrox.rematter.chemistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.function.ToLongFunction;

/**
 * An immutable membership view over existing inventories. Reads and network splits never move matter.
 * Batches are planned on disposable copies and committed to all physical owners under the same lock.
 * The caller owns connectivity/permission checks and must mark the member owners dirty after a commit.
 */
public final class MatterNetworkInventory implements MatterStore {
    public static final int MAX_MEMBERS = 64;
    private final List<MatterInventory> members;

    public MatterNetworkInventory(List<MatterInventory> members) {
        this.members = List.copyOf(members);
        if (members.isEmpty() || members.size() > MAX_MEMBERS) throw new IllegalArgumentException("Invalid network size");
        var identities = Collections.newSetFromMap(new IdentityHashMap<MatterInventory, Boolean>());
        for (var member : this.members) if (!identities.add(member)) throw new IllegalArgumentException("Duplicate inventory owner");
        synchronized (MatterInventory.MUTATION_LOCK) { validateTotals(); }
    }

    @Override public List<MatterInventory> members() { return members; }
    @Override public long capacity() { return sum(MatterInventory::capacity); }
    @Override public long total() { return sum(MatterInventory::total); }
    @Override public long freeSpace() { return sum(MatterInventory::freeSpace); }
    @Override public int typeCount() { return contents().size(); }
    @Override public long amount(SubstanceKey key) { Objects.requireNonNull(key); return sum(member -> member.amount(key)); }
    @Override public long available(SubstanceKey key) { Objects.requireNonNull(key); return sum(member -> member.available(key)); }
    @Override public long reserved(SubstanceKey key) { Objects.requireNonNull(key); return sum(member -> member.reserved(key)); }
    @Override public Map<SubstanceKey, Long> contents() { return amounts(false); }
    @Override public Map<SubstanceKey, Long> availableContents() { return amounts(true); }

    private long sum(ToLongFunction<MatterInventory> value) {
        synchronized (MatterInventory.MUTATION_LOCK) {
            validateTotals();
            long total = 0;
            for (var member : members) total = Math.addExact(total, value.applyAsLong(member));
            return total;
        }
    }
    private Map<SubstanceKey, Long> amounts(boolean onlyAvailable) {
        synchronized (MatterInventory.MUTATION_LOCK) {
            validateTotals();
            var result = new TreeMap<SubstanceKey, Long>();
            for (var member : members) (onlyAvailable ? member.availableContents() : member.contents())
                    .forEach((key, amount) -> result.merge(key, amount, Math::addExact));
            return Collections.unmodifiableMap(result);
        }
    }
    private void validateTotals() {
        long capacity = 0, total = 0;
        for (var member : members) {
            capacity = Math.addExact(capacity, member.capacity());
            total = Math.addExact(total, member.total());
        }
        if (total > capacity) throw new IllegalStateException("Network exceeds member capacity");
    }

    @Override public boolean hasTypeSpace(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        synchronized (MatterInventory.MUTATION_LOCK) { return plan(inputs, outputs, true) != null; }
    }
    @Override public boolean canApply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        synchronized (MatterInventory.MUTATION_LOCK) { return plan(inputs, outputs, false) != null; }
    }
    @Override public boolean apply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        synchronized (MatterInventory.MUTATION_LOCK) {
            var plan = plan(inputs, outputs, false);
            if (plan == null) return false;
            // Every replacement is already validated. No callback or fallible partial allocation occurs here.
            for (int i=0;i<members.size();i++) members.get(i).replaceWithValidated(plan.get(i));
            return true;
        }
    }

    private List<MatterInventory> plan(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs, boolean typesOnly) {
        try {
            validateTotals();
            var checkedInputs = MatterAmounts.copy(inputs);
            var checkedOutputs = MatterAmounts.copy(outputs);
            var pending = new ArrayDeque<List<Integer>>(); pending.add(List.of());
            var visited = new HashSet<List<Integer>>(); var budget = new MatterOutputAllocator.Budget();
            int attempts=0;
            while(!pending.isEmpty() && attempts++<64 && !budget.exhausted()) {
                var selected=pending.removeLast(); if(!visited.add(selected))continue;
                var copies=new ArrayList<MatterInventory>(members.size());
                for(var member:members)copies.add(MatterInventory.restore(member.snapshot(),member.maximumTypes()));
                var decisions=new ArrayList<Integer>(); var alternatives=new ArrayList<Integer>();
                for(var input:checkedInputs.entrySet()) {
                    long remaining=input.getValue();
                    while(remaining>0) {
                        var candidates=new ArrayList<Integer>();
                        for(int i=0;i<copies.size();i++)if(copies.get(i).available(input.getKey())>0)candidates.add(i);
                        if(candidates.isEmpty())return null; // Total input availability does not depend on allocation.
                        candidates.sort(Comparator.<Integer>comparingInt(i -> copies.get(i).available(input.getKey())==copies.get(i).amount(input.getKey())?0:1)
                                .thenComparingLong(i -> copies.get(i).available(input.getKey())).thenComparingInt(i -> i));
                        int step=decisions.size();int choice=step<selected.size()?selected.get(step):0;
                        if(choice>=candidates.size())throw new IllegalStateException("Invalid input allocation alternative");
                        decisions.add(choice);alternatives.add(candidates.size());
                        remaining-=copies.get(candidates.get(choice)).extract(input.getKey(),remaining);
                    }
                }
                var allocation=MatterOutputAllocator.allocate(copies,checkedOutputs,typesOnly,budget);
                if(allocation!=null) {
                    if(!typesOnly)for(int i=0;i<copies.size();i++)
                        if(!copies.get(i).apply(Map.of(),allocation.get(i)))throw new IllegalStateException("Invalid output allocation");
                    return copies;
                }
                // A different input owner can release the only sufficiently large cell or a required type slot.
                for(int step=0;step<decisions.size()&&pending.size()<64;step++) {
                    for(int choice=0;choice<alternatives.get(step)&&pending.size()<64;choice++) {
                        if(choice==decisions.get(step))continue;
                        var next=new ArrayList<>(decisions.subList(0,step));next.add(choice);
                        if(!visited.contains(next))pending.add(List.copyOf(next));
                    }
                }
            }
            return null;
        } catch (ArithmeticException overflow) {
            // Exact aggregate quantities must never wrap or saturate into a usable resource count.
            return null;
        }
    }

    /** Runtime rollback only. Persist each physical member normally; this record is not a resource container. */
    public Snapshot snapshot() {
        synchronized (MatterInventory.MUTATION_LOCK) {
            validateTotals();
            return new Snapshot(members.stream().map(member -> new MemberSnapshot(member, member.snapshot())).toList());
        }
    }
    public void restoreInPlace(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        synchronized (MatterInventory.MUTATION_LOCK) {
            if(snapshot.members().size()!=members.size()) throw new IllegalArgumentException("Network membership changed");
            var restored = new ArrayList<MatterInventory>(members.size());
            long totalCapacity=0;
            for(int i=0;i<members.size();i++) {
                var saved=snapshot.members().get(i); var member=members.get(i);
                if(saved.inventory()!=member) throw new IllegalArgumentException("Network member identity changed");
                var copy=MatterInventory.restore(saved.state(),member.maximumTypes());
                totalCapacity=Math.addExact(totalCapacity,copy.capacity());
                restored.add(copy);
            }
            for(int i=0;i<members.size();i++) members.get(i).replaceWithValidated(restored.get(i));
        }
    }
    public record MemberSnapshot(MatterInventory inventory, MatterInventory.Snapshot state) {
        public MemberSnapshot { Objects.requireNonNull(inventory); Objects.requireNonNull(state); }
    }
    public record Snapshot(List<MemberSnapshot> members) {
        public Snapshot { members=List.copyOf(members); }
    }
}
