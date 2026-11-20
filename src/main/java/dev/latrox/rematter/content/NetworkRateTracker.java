package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterNetworkInventory;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Rolling server-side delta sampler for a {@link MatterNetworkInventory}.
 * Resets its baseline whenever the physical {@link MatterInventory} member identity set changes,
 * so inserting or removing a filled Matter Cell never produces a spurious production/consumption spike.
 */
public final class NetworkRateTracker {
    public static final long WINDOW_TICKS = 100L;
    public static final long TICKS_PER_MINUTE = 1_200L;
    private static final int MAX_SAMPLES = 24;

    private record Sample(long tick, long revision, Map<SubstanceKey, Long> contents) {}

    private final Deque<Sample> samples = new ArrayDeque<>();
    private @Nullable Set<MatterInventory> trackedMembers;

    public void reset() {
        samples.clear();
        trackedMembers = null;
    }

    public Map<SubstanceKey, Long> sampleRatesPerMinute(@Nullable MatterNetworkInventory inventory, long nowTick) {
        if (inventory == null) {
            reset();
            return Map.of();
        }
        List<MatterInventory> currentMembers = inventory.members();
        if (!sameIdentitySet(currentMembers)) {
            samples.clear();
            var nextSet = Collections.newSetFromMap(new IdentityHashMap<MatterInventory, Boolean>());
            nextSet.addAll(currentMembers);
            trackedMembers = nextSet;
        }
        long revision = inventory.revision();
        Map<SubstanceKey, Long> currentContents = Map.copyOf(inventory.contents());

        if (!samples.isEmpty() && nowTick < samples.peekLast().tick()) {
            samples.clear();
        }
        if (!samples.isEmpty() && samples.peekLast().tick() == nowTick) {
            samples.removeLast();
        }
        samples.addLast(new Sample(nowTick, revision, currentContents));
        while (samples.size() > 2) {
            var first = samples.removeFirst();
            var second = samples.peekFirst();
            if (second != null && nowTick - second.tick() < WINDOW_TICKS) {
                samples.addFirst(first);
                break;
            }
        }
        while (samples.size() > MAX_SAMPLES) {
            samples.removeFirst();
        }
        if (samples.size() < 2) return Map.of();
        Sample oldest = samples.peekFirst();
        Sample newest = samples.peekLast();
        long elapsed = newest.tick() - oldest.tick();
        if (elapsed <= 0 || oldest.revision() == newest.revision()) return Map.of();

        Set<SubstanceKey> union = new HashSet<>(oldest.contents().keySet());
        union.addAll(newest.contents().keySet());
        var rates = new TreeMap<SubstanceKey, Long>();
        for (SubstanceKey key : union) {
            long before = oldest.contents().getOrDefault(key, 0L);
            long after = newest.contents().getOrDefault(key, 0L);
            long delta = after - before;
            if (delta == 0L) continue;
            long perMin = Math.round((double) delta * TICKS_PER_MINUTE / (double) elapsed);
            if (perMin == 0L) {
                perMin = delta > 0 ? 1L : -1L;
            }
            rates.put(key, perMin);
        }
        return Collections.unmodifiableMap(rates);
    }

    private boolean sameIdentitySet(List<MatterInventory> members) {
        if (trackedMembers == null || trackedMembers.size() != members.size()) return false;
        for (MatterInventory member : members) {
            if (!trackedMembers.contains(member)) return false;
        }
        return true;
    }
}
