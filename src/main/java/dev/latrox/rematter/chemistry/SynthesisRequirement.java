package dev.latrox.rematter.chemistry;

import java.util.Objects;

/** Conditions on an already explicit permission. They never grant synthesis by themselves. */
public record SynthesisRequirement(String machine, int tier, String advancement) {
    public static final SynthesisRequirement DEFAULT = new SynthesisRequirement("material_synthesizer", 1, "");

    public SynthesisRequirement {
        Objects.requireNonNull(machine); Objects.requireNonNull(advancement);
        if (!machine.equals("material_synthesizer"))
            throw new IllegalArgumentException("Unsupported synthesis machine: " + machine);
        if (tier < 1 || tier > 2)
            throw new IllegalArgumentException("Unsupported synthesis tier");
        if (!advancement.isEmpty()) SubstanceId.parse(advancement);
    }
}
