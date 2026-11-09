package dev.latrox.rematter.chemistry;

import java.util.Map;
import java.util.Objects;

/** Matter-only transformation. Registry construction verifies elemental conservation. */
public record ProcessRecipe(
        SubstanceId id,
        ProcessType type,
        Map<SubstanceKey, Long> inputs,
        Map<SubstanceKey, Long> outputs,
        long energy,
        int ticks,
        String requiresAdvancement) {
    public ProcessRecipe(SubstanceId id, ProcessType type, Map<SubstanceKey, Long> inputs,
                         Map<SubstanceKey, Long> outputs, long energy, int ticks) {
        this(id, type, inputs, outputs, energy, ticks, "");
    }
    public ProcessRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(requiresAdvancement, "requiresAdvancement");
        if (!requiresAdvancement.isEmpty()) SubstanceId.parse(requiresAdvancement);
        inputs = MatterAmounts.copy(inputs);
        outputs = MatterAmounts.copy(outputs);
        if (inputs.isEmpty() || outputs.isEmpty() || energy <= 0 || ticks <= 0) {
            throw new IllegalArgumentException("A process requires inputs, outputs, positive energy, and duration");
        }
    }

    public String translationKey() { return translationKey(id); }

    /** Processes are named by language files under {@code process.<namespace>.<path>}. */
    public static String translationKey(SubstanceId id) {
        return "process." + id.namespace() + "." + id.path().replace('/', '.');
    }
}
