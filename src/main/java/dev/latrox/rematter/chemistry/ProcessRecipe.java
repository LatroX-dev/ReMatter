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
        String requiresAdvancement,
        int minField,
        boolean requiresKnownOutputs,
        SubstanceId product) {
    public ProcessRecipe(SubstanceId id, ProcessType type, Map<SubstanceKey, Long> inputs,
                         Map<SubstanceKey, Long> outputs, long energy, int ticks) {
        this(id, type, inputs, outputs, energy, ticks, "", 0, false, null);
    }
    public ProcessRecipe(SubstanceId id, ProcessType type, Map<SubstanceKey, Long> inputs,
                         Map<SubstanceKey, Long> outputs, long energy, int ticks, String requiresAdvancement) {
        this(id,type,inputs,outputs,energy,ticks,requiresAdvancement,0,false,null);
    }
    public ProcessRecipe {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(requiresAdvancement, "requiresAdvancement");
        if (!requiresAdvancement.isEmpty()) SubstanceId.parse(requiresAdvancement);
        inputs = MatterAmounts.copy(inputs);
        outputs = MatterAmounts.copy(outputs);
        if(minField<0||minField>98||minField==0&&(requiresKnownOutputs||product!=null)
                ||minField>0&&(product==null||outputs.keySet().stream().noneMatch(key->key.id().equals(product))))
            throw new IllegalArgumentException("Invalid industrial process capabilities or product");
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
