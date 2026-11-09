package dev.latrox.rematter.chemistry;

import java.util.Map;
import java.util.Set;

/** Explicit renewable environment input, kept separate from element-conserving reactions. */
public record EnvironmentSource(SubstanceId id, Map<SubstanceKey, Long> outputs, long energy,
                                int ticks, Set<String> dimensions, boolean requiresOpenAir) {
    public EnvironmentSource {
        outputs = MatterAmounts.copy(outputs);
        dimensions = Set.copyOf(dimensions);
        if (id == null || outputs.isEmpty() || energy <= 0 || ticks <= 0 || dimensions.isEmpty())
            throw new IllegalArgumentException("An environment source requires gas output, energy, duration and dimensions");
        for (var entry : outputs.entrySet())
            if (entry.getKey().phase() != Phase.GAS || entry.getValue() > 1_000_000)
                throw new IllegalArgumentException("Environment sources produce bounded gas quantities only");
        dimensions.forEach(SubstanceId::parse);
    }
    public String translationKey() { return ProcessRecipe.translationKey(id); }
}
