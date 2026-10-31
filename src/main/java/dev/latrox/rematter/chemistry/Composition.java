package dev.latrox.rematter.chemistry;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Element counts in one integral formula unit, including simplified mixtures. */
public record Composition(Map<SubstanceId, Long> elements) {
    public Composition {
        Objects.requireNonNull(elements, "elements");
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("A composition must contain at least one element");
        }
        TreeMap<SubstanceId, Long> copy = new TreeMap<>();
        elements.forEach((id, count) -> {
            Objects.requireNonNull(id, "element ID");
            if (count == null || count <= 0) {
                throw new IllegalArgumentException("Element counts must be positive");
            }
            copy.put(id, count);
        });
        elements = Collections.unmodifiableMap(copy);
    }

    public static Composition element(SubstanceId id) {
        return new Composition(Map.of(id, 1L));
    }

    public Map<SubstanceId, Long> multiply(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        TreeMap<SubstanceId, Long> result = new TreeMap<>();
        elements.forEach((id, count) -> result.put(id, Math.multiplyExact(count, quantity)));
        return Collections.unmodifiableMap(result);
    }
}
