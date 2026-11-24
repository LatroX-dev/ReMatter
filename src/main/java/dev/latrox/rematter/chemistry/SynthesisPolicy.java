package dev.latrox.rematter.chemistry;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Only exact, explicitly curated output IDs can be synthesized. No tag/recipe inference. */
public final class SynthesisPolicy {
    private final Map<String, SynthesisCategory> allowed;
    private final Map<String, SynthesisRequirement> requirements;
    private final Map<String, String> canonicalAliases;

    public SynthesisPolicy(Map<String, SynthesisCategory> allowed) {
        this(allowed, Map.of(), Map.of());
    }

    public SynthesisPolicy(Map<String, SynthesisCategory> allowed, Map<String, SynthesisRequirement> requirements) {
        this(allowed, requirements, Map.of());
    }

    public SynthesisPolicy(Map<String, SynthesisCategory> allowed, Map<String, SynthesisRequirement> requirements,
                           Map<String, String> canonicalAliases) {
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
        if (!checked.keySet().containsAll(requirements.keySet()))
            throw new IllegalArgumentException("Conditions require an explicit synthesis permission");
        this.requirements = Map.copyOf(requirements);
        TreeMap<String, String> checkedAliases = new TreeMap<>();
        Objects.requireNonNull(canonicalAliases, "canonicalAliases").forEach((from, to) -> {
            SubstanceId.parse(from);
            SubstanceId.parse(to);
            if (!checked.containsKey(to)) {
                throw new IllegalArgumentException("Canonical alias target must be permitted: " + from + " -> " + to);
            }
            if (checked.containsKey(from)) {
                throw new IllegalArgumentException("Canonical alias source cannot also be active: " + from);
            }
            checkedAliases.put(from, to);
        });
        this.canonicalAliases = Collections.unmodifiableMap(checkedAliases);
    }

    public String canonicalTarget(String exactId) {
        return canonicalAliases.getOrDefault(exactId, exactId);
    }

    public Map<String, String> canonicalAliases() {
        return canonicalAliases;
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

    public SynthesisRequirement requirement(String exactId) {
        return requirements.getOrDefault(exactId, SynthesisRequirement.DEFAULT);
    }

    public boolean allowsMachine(String exactId, String machine) {
        if (!allowed.containsKey(exactId)) return false;
        String required = requirement(exactId).machine();
        return required.equals(machine)
                || required.equals("material_synthesizer") && "industrial_synthesizer_controller".equals(machine);
    }
}
