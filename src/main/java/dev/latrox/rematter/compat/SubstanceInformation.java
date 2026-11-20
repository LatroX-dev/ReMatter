package dev.latrox.rematter.compat;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.SubstanceDefinition;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.List;
import java.util.Optional;

/** Read-only catalogue facts. Composition counts describe one formula unit, never an inferred mass. */
public record SubstanceInformation(SubstanceKey key, long amount, Optional<SubstanceDefinition> definition,
                                   List<Element> elements) {
    public record Element(SubstanceId id, long count, Optional<SubstanceDefinition> definition) {}

    public SubstanceInformation {
        if (key == null || amount < 0) throw new IllegalArgumentException("Invalid substance information");
        definition = java.util.Objects.requireNonNull(definition);
        elements = List.copyOf(elements);
    }

    /** An unsupported stored phase remains unknown even when the catalogue defines the same ID in another phase. */
    public static SubstanceInformation inspect(ChemistryRegistry registry, SubstanceKey key, long amount) {
        var definition = registry.definition(key.id()).filter(value -> value.supports(key.phase()));
        var elements = definition.stream().flatMap(value -> value.composition().elements().entrySet().stream())
                .map(entry -> new Element(entry.getKey(), entry.getValue(), registry.definition(entry.getKey()))).toList();
        return new SubstanceInformation(key, amount, definition, elements);
    }
}
