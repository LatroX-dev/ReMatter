package dev.latrox.rematter.chemistry;

import java.util.Map;

/** Explicit canonical fluid batch in mB; no fluid/gas aliases are inferred. */
public record FluidProfile(String fluidId, int amount, Map<SubstanceKey, Long> matter,
                           long decompositionEnergy, long synthesisEnergy, int ticks) {
    public static final int MAX_BATCH_MILLIBUCKETS = 16_000;

    public FluidProfile {
        SubstanceId.parse(fluidId);
        matter = MatterAmounts.copy(matter);
        if (amount <= 0 || amount > MAX_BATCH_MILLIBUCKETS || matter.isEmpty()
                || decompositionEnergy <= 0 || synthesisEnergy <= 0 || ticks <= 0) {
            throw new IllegalArgumentException("A fluid profile requires a bounded whole batch, matter, energy, and duration");
        }
    }

    public Map<SubstanceKey, Long> matterFor(long millibuckets) {
        if (millibuckets <= 0 || millibuckets % amount != 0) {
            throw new IllegalArgumentException("Fluid amount must contain whole material batches");
        }
        return MatterAmounts.multiply(matter, millibuckets / amount);
    }
}
