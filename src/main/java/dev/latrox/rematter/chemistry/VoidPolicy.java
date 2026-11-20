package dev.latrox.rematter.chemistry;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Immutable summary of mounted overflow-voiding cells in a Drive or Matter Network.
 * Overflow voiding applies strictly after all compatible finite Matter Cells have been filled.
 */
public record VoidPolicy(boolean present, boolean unrestricted, Set<SubstanceKey> allowed) implements Predicate<SubstanceKey> {
    public static final VoidPolicy NONE = new VoidPolicy(false, false, Set.of());

    public VoidPolicy {
        Objects.requireNonNull(allowed, "allowed");
        if (!present && (unrestricted || !allowed.isEmpty())) {
            throw new IllegalArgumentException("Absent void policy cannot accept substances");
        }
        allowed = unrestricted ? Set.of() : Set.copyOf(new TreeSet<>(allowed));
    }

    public static VoidPolicy any() {
        return new VoidPolicy(true, true, Set.of());
    }

    public static VoidPolicy only(Collection<SubstanceKey> keys) {
        var clean = new TreeSet<SubstanceKey>();
        for (var key : keys) {
            if (!ReservedSubstances.reserved(key)) clean.add(key);
        }
        return new VoidPolicy(true, false, clean);
    }

    public static VoidPolicy merge(Collection<VoidPolicy> policies) {
        boolean present = false;
        boolean unrestricted = false;
        var merged = new TreeSet<SubstanceKey>();
        for (var policy : policies) {
            if (!policy.present) continue;
            present = true;
            if (policy.unrestricted) {
                unrestricted = true;
            } else if (!unrestricted) {
                merged.addAll(policy.allowed);
            }
        }
        if (!present) return NONE;
        return unrestricted ? any() : new VoidPolicy(true, false, merged);
    }

    public boolean accepts(SubstanceKey key) {
        return present && !ReservedSubstances.reserved(key) && (unrestricted || allowed.contains(key));
    }

    @Override
    public boolean test(SubstanceKey key) {
        return accepts(key);
    }
}
