package dev.latrox.rematter.chemistry;

import java.util.Map;
import java.util.Set;

/** Explicit renewable environment input, kept separate from element-conserving reactions. */
public record EnvironmentSource(SubstanceId id, Map<SubstanceKey, Long> outputs, long energy,
                                int ticks, Set<String> dimensions, boolean requiresOpenAir, String probeBelow) {
    public EnvironmentSource(SubstanceId id, Map<SubstanceKey, Long> outputs, long energy,
                             int ticks, Set<String> dimensions, boolean requiresOpenAir) {
        this(id, outputs, energy, ticks, dimensions, requiresOpenAir, "");
    }
    public EnvironmentSource {
        outputs = MatterAmounts.copy(outputs);
        dimensions = Set.copyOf(dimensions);
        probeBelow = probeBelow == null ? "" : probeBelow;
        if (id == null || outputs.isEmpty() || energy <= 0 || ticks <= 0 || dimensions.isEmpty())
            throw new IllegalArgumentException("An environment source requires output, energy, duration and dimensions");
        if (!probeBelow.isEmpty() && !probeBelow.matches("[a-z0-9_]+"))
            throw new IllegalArgumentException("Invalid probe_below selector: " + probeBelow);
        for (var entry : outputs.entrySet()) {
            if (entry.getValue() > 1_000_000)
                throw new IllegalArgumentException("Environment sources produce bounded quantities only");
            if (probeBelow.isEmpty() && entry.getKey().phase() != Phase.GAS)
                throw new IllegalArgumentException("Atmospheric environment sources produce bounded gas quantities only");
        }
        dimensions.forEach(SubstanceId::parse);
    }
    public boolean deepExtraction() { return !probeBelow.isEmpty(); }
    public String translationKey() { return ProcessRecipe.translationKey(id); }
}
