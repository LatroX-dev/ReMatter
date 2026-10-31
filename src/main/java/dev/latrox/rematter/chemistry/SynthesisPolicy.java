package dev.latrox.rematter.chemistry;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Only exact, explicitly curated output IDs can be synthesized. No tag/recipe inference. */
public final class SynthesisPolicy {
    private final Map<String, SynthesisCategory> allowed;

    public SynthesisPolicy(Map<String, SynthesisCategory> allowed) {
        TreeMap<String, SynthesisCategory> checked = new TreeMap<>();
        allowed.forEach((id, category) -> {
            SubstanceId.parse(id);
            Objects.requireNonNull(category, "category");
            if (!category.permitted()) {
                throw new IllegalArgumentException("Forbidden synthesis category for " + id + ": " + category);
            }
            checked.put(id, category);
        });
        this.allowed = Collections.unmodifiableMap(checked);
    }

    public boolean allows(String exactId) {
        return allowed.containsKey(exactId);
    }

    public boolean allowsItem(String exactId) {
        SynthesisCategory category = allowed.get(exactId);
        return category == SynthesisCategory.RAW_MATERIAL || category == SynthesisCategory.NATURAL_MATERIAL;
    }

    public boolean allowsFluid(String exactId) {
        return allowed.get(exactId) == SynthesisCategory.FLUID;
    }

    public Optional<SynthesisCategory> category(String exactId) {
        return Optional.ofNullable(allowed.get(exactId));
    }

    public Map<String, SynthesisCategory> entries() {
        return allowed;
    }
}
