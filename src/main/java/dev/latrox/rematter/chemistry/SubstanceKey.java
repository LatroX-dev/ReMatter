package dev.latrox.rematter.chemistry;

import java.util.Objects;

/** Phase is part of the stored identity; storing steam never silently produces water. */
public record SubstanceKey(SubstanceId id, Phase phase) implements Comparable<SubstanceKey> {
    public SubstanceKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(phase, "phase");
    }

    public static SubstanceKey of(String id, Phase phase) {
        return new SubstanceKey(SubstanceId.parse(id), phase);
    }

    @Override
    public int compareTo(SubstanceKey other) {
        int idOrder = id.compareTo(other.id);
        return idOrder != 0 ? idOrder : phase.compareTo(other.phase);
    }
}
