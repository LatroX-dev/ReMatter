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
        int ticks) {
    public ProcessRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        inputs = MatterAmounts.copy(inputs);
        outputs = MatterAmounts.copy(outputs);
        if (inputs.isEmpty() || outputs.isEmpty() || energy <= 0 || ticks <= 0) {
            throw new IllegalArgumentException("A process requires inputs, outputs, positive energy, and duration");
        }
    }

    public long energyAtTick(int tick) {
        if (tick < 0 || tick >= ticks) {
            throw new IllegalArgumentException("Tick outside recipe duration");
        }
        long base = energy / ticks;
        long remainder = energy % ticks;
        return base + (tick < remainder ? 1 : 0);
    }
}
