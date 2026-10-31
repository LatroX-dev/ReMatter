package dev.latrox.rematter.chemistry;

import java.util.Map;

/** Material for one ordinary item; synthesis permission is kept in a separate allowlist. */
public record MaterialProfile(
        String itemId,
        Map<SubstanceKey, Long> matter,
        long decompositionEnergy,
        long synthesisEnergy,
        int ticks) {
    public MaterialProfile {
        SubstanceId.parse(itemId);
        matter = MatterAmounts.copy(matter);
        if (matter.isEmpty() || decompositionEnergy <= 0 || synthesisEnergy <= 0 || ticks <= 0) {
            throw new IllegalArgumentException("A material profile requires matter, positive energy, and duration");
        }
    }

    public Map<SubstanceKey, Long> matterFor(long itemCount) {
        return MatterAmounts.multiply(matter, itemCount);
    }
}
