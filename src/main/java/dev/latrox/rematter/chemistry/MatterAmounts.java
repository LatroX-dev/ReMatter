package dev.latrox.rematter.chemistry;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Shared checked arithmetic for immutable amount maps. */
public final class MatterAmounts {
    private MatterAmounts() {}

    public static Map<SubstanceKey, Long> copy(Map<SubstanceKey, Long> source) {
        Objects.requireNonNull(source, "source");
        TreeMap<SubstanceKey, Long> copy = new TreeMap<>();
        source.forEach((key, value) -> {
            Objects.requireNonNull(key, "key");
            if (value == null || value <= 0) {
                throw new IllegalArgumentException("Substance quantities must be positive: " + key);
            }
            copy.put(key, value);
        });
        total(copy);
        return Collections.unmodifiableMap(copy);
    }

    public static long total(Map<SubstanceKey, Long> amounts) {
        long total = 0;
        for (long amount : amounts.values()) {
            if (amount <= 0) {
                throw new IllegalArgumentException("Substance quantities must be positive");
            }
            total = Math.addExact(total, amount);
        }
        return total;
    }

    public static Map<SubstanceKey, Long> multiply(Map<SubstanceKey, Long> source, long factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Multiplier must be positive");
        }
        TreeMap<SubstanceKey, Long> result = new TreeMap<>();
        copy(source).forEach((key, value) -> result.put(key, Math.multiplyExact(value, factor)));
        return copy(result);
    }
}
