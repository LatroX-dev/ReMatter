package dev.latrox.rematter.chemistry;

import java.util.AbstractList;
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
import org.jspecify.annotations.Nullable;

/**
 * An immutable membership view over existing inventories. Reads and network splits never move matter.
 * Batches are planned on disposable copies of only the members they touch, and committed to those
 * physical owners under the same lock. The caller owns connectivity/permission checks and must mark
 * the member owners dirty after a commit.
 */
public final class MatterNetworkInventory implements MatterStore {
    public static final int MAX_MEMBERS = 384;
    private final List<MatterInventory> members;
    private long aggregatedRevision = -1;
    private @Nullable Map<SubstanceKey, Long> aggregatedContents;

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
    @Override public Map<SubstanceKey, Long> contents() {
        synchronized (MatterInventory.MUTATION_LOCK) {
            // Aggregates are rebuilt only after a member changed; repeated reads of unchanged stock share one map.
            validateTotals();
            long revision = revision();
            if (revision == aggregatedRevision && aggregatedContents != null) return aggregatedContents;
            var result = new TreeMap<SubstanceKey, Long>();
            for (var member : members) member.contents().forEach((key, amount) -> result.merge(key, amount, Math::addExact));
            aggregatedContents = Collections.unmodifiableMap(result);
            aggregatedRevision = revision;
            return aggregatedContents;
        }
    }
    @Override public long revision() {
        synchronized (MatterInventory.MUTATION_LOCK) {
            // Every member counter only grows, so the sum changes whenever any member changes.
            long revision = 0;
            for (var member : members) revision += member.revision();
            return revision;
        }
    }

    private long sum(ToLongFunction<MatterInventory> value) {
        synchronized (MatterInventory.MUTATION_LOCK) {
            validateTotals();
            long total = 0;
            for (var member : members) total = Math.addExact(total, value.applyAsLong(member));
            return total;
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
        synchronized (MatterInventory.MUTATION_LOCK) { return plan(inputs, outputs, true, null) != null; }
    }
    @Override public boolean canApply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        synchronized (MatterInventory.MUTATION_LOCK) { return plan(inputs, outputs, false, null) != null; }
    }
    @Override public boolean apply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        return commit(inputs, outputs, null);
    }

    /** Identifies the physical cells that exchanged matter in a committed batch. */
    public record MemberActivity(MatterInventory inventory, boolean withdrew, boolean deposited) {}

    public @Nullable List<MemberActivity> applyTracked(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        var activity = new ArrayList<MemberActivity>();
        return commit(inputs, outputs, activity) ? List.copyOf(activity) : null;
    }

    private boolean commit(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                           @Nullable List<MemberActivity> activity) {
        synchronized (MatterInventory.MUTATION_LOCK) {
            var plan = plan(inputs, outputs, false, activity);
            if (plan == null) return false;
            // Every replacement is already validated. No callback or fallible partial allocation occurs here.
            for (int i=0;i<members.size();i++) if (plan.touched(i)) members.get(i).replaceWithValidated(plan.get(i));
            return true;
        }
    }

    /** The members as seen by one planning attempt: untouched members are read in place, touched ones are copies. */
    private final class Draft extends AbstractList<MatterInventory> {
        private final MatterInventory[] copies = new MatterInventory[members.size()];
        @Override public MatterInventory get(int index) { return copies[index] != null ? copies[index] : members.get(index); }
        @Override public int size() { return copies.length; }
        boolean touched(int index) { return copies[index] != null; }
        MatterInventory edit(int index) {
            if (copies[index] == null) copies[index] = members.get(index).planningCopy();
            return copies[index];
        }
    }

    /** Necessary conditions that need no allocation: enough input and enough total room for the net output. */
    private boolean aggregateFits(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs, boolean typesOnly) {
        for (var input : inputs.entrySet()) {
            long available = 0;
            for (var member : members) available = Math.addExact(available, member.amount(input.getKey()));
            if (available < input.getValue()) return false;
        }
        if (typesOnly) return true;
        long room = MatterAmounts.total(inputs);
        for (var member : members) room = Math.addExact(room, member.freeSpace());
        return room >= MatterAmounts.total(outputs);
    }

    private @Nullable Draft plan(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                                 boolean typesOnly, @Nullable List<MemberActivity> activity) {
        try {
            validateTotals();
            var checkedInputs = MatterAmounts.copy(inputs);
            var checkedOutputs = MatterAmounts.copy(outputs);
            if (!aggregateFits(checkedInputs, checkedOutputs, typesOnly)) return null;
            var pending = new ArrayDeque<List<Integer>>(); pending.add(List.of());
            var visited = new HashSet<List<Integer>>(); var budget = new MatterOutputAllocator.Budget();
            int attempts=0;
            while(!pending.isEmpty() && attempts++<64 && !budget.exhausted()) {
                var selected=pending.removeLast(); if(!visited.add(selected))continue;
                var copies=new Draft();
                var withdrawn = activity == null ? null : new boolean[members.size()];
                var decisions=new ArrayList<Integer>(); var alternatives=new ArrayList<Integer>();
                for(var input:checkedInputs.entrySet()) {
                    long remaining=input.getValue();
                    while(remaining>0) {
                        var candidates=new ArrayList<Integer>();
                        for(int i=0;i<copies.size();i++)if(copies.get(i).amount(input.getKey())>0)candidates.add(i);
                        if(candidates.isEmpty())return null; // Total input availability does not depend on allocation.
                        candidates.sort(Comparator.<Integer>comparingLong(i -> copies.get(i).amount(input.getKey())).thenComparingInt(i -> i));
                        int step=decisions.size();int choice=step<selected.size()?selected.get(step):0;
                        if(choice>=candidates.size())throw new IllegalStateException("Invalid input allocation alternative");
                        decisions.add(choice);alternatives.add(candidates.size());
                        int member = candidates.get(choice);
                        long extracted = copies.edit(member).extract(input.getKey(),remaining);
                        remaining -= extracted;
                        if (withdrawn != null && extracted > 0) withdrawn[member] = true;
                    }
                }
                var allocation=MatterOutputAllocator.allocate(copies,checkedOutputs,typesOnly,budget);
                if(allocation!=null) {
                    if(!typesOnly)for(int i=0;i<copies.size();i++)
                        if(!allocation.get(i).isEmpty()&&!copies.edit(i).apply(Map.of(),allocation.get(i)))throw new IllegalStateException("Invalid output allocation");
                    if (activity != null && withdrawn != null) {
                        for (int i = 0; i < members.size(); i++) {
                            boolean deposited = !allocation.get(i).isEmpty();
                            if (withdrawn[i] || deposited) activity.add(new MemberActivity(members.get(i), withdrawn[i], deposited));
                        }
                    }
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
