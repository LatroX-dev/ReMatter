package dev.latrox.rematter.chemistry;

import java.util.Objects;

/** One integral formula-unit quantity; zero is represented by an absent entry. */
public record SubstanceStack(SubstanceKey key, long quantity) {
    public SubstanceStack {
        Objects.requireNonNull(key, "key");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Substance quantities must be positive");
        }
    }

    public SubstanceStack multiply(long factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Multiplier must be positive");
        }
        return new SubstanceStack(key, Math.multiplyExact(quantity, factor));
    }
}
