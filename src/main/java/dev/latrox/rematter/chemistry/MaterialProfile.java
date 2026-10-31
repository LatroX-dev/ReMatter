package dev.latrox.rematter.chemistry;

import java.util.Map;

/** Exact material for a whole item batch; synthesis remains a separate permission. */
public record MaterialProfile(
        String itemId,
        int itemCount,
        Map<SubstanceKey, Long> matter,
        long decompositionEnergy,
        long synthesisEnergy,
        int ticks) {
    public MaterialProfile {
        SubstanceId.parse(itemId);
        matter = MatterAmounts.copy(matter);
        if (itemCount < 1 || itemCount > 64 || matter.isEmpty() || decompositionEnergy <= 0 || synthesisEnergy <= 0 || ticks <= 0) {
            throw new IllegalArgumentException("A material profile requires matter, positive energy, and duration");
        }
    }

    /** Existing single-item definitions retain their stored quantity scale. */
    public MaterialProfile(String itemId, Map<SubstanceKey, Long> matter,
                           long decompositionEnergy, long synthesisEnergy, int ticks) {
        this(itemId, 1, matter, decompositionEnergy, synthesisEnergy, ticks);
    }

    /** Rejects partial batches instead of rounding fractional nuggets or dusts. */
    public Map<SubstanceKey, Long> matterFor(long count) {
        if (count <= 0 || count % itemCount != 0) {
            throw new IllegalArgumentException("Item count must contain whole material batches");
        }
        return MatterAmounts.multiply(matter, count / itemCount);
    }
}
