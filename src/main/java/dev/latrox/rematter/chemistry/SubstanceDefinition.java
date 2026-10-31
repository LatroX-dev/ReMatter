package dev.latrox.rematter.chemistry;

import java.util.Objects;
import java.util.Set;

public record SubstanceDefinition(
        SubstanceId id,
        String translationKey,
        String symbol,
        SubstanceKind kind,
        Composition composition,
        Set<Phase> phases) {
    public SubstanceDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(translationKey, "translationKey");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(composition, "composition");
        phases = Set.copyOf(phases);
        if (translationKey.isBlank() || phases.isEmpty()) {
            throw new IllegalArgumentException("A substance requires a name and at least one phase");
        }
        if (kind == SubstanceKind.ELEMENT
                && (composition.elements().size() != 1 || !composition.elements().containsKey(id))) {
            throw new IllegalArgumentException("An element must consist only of itself: " + id);
        }
        if (kind == SubstanceKind.ALLOTROPE
                && (composition.elements().size() != 1 || composition.elements().containsKey(id))) {
            throw new IllegalArgumentException("An allotrope needs a distinct identity and exactly one underlying element: " + id);
        }
    }

    public boolean supports(Phase phase) {
        return phases.contains(phase);
    }
}
