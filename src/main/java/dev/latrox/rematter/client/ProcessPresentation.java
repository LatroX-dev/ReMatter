package dev.latrox.rematter.client;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.ProcessType;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.MachineKind;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/** Read-only catalogue information. It never grants a recipe or starts a machine. */
public final class ProcessPresentation {
    private ProcessPresentation() {}

    public record Product(String id, String name, String outputKind, String outputId, long outputCount,
                          Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                          long energy, int ticks) {
        public Product { inputs = Map.copyOf(inputs); outputs = Map.copyOf(outputs); }
    }

    public static Optional<Product> resolve(ChemistryRegistry registry, MachineKind kind, String id, String name) {
        if (kind.synthesis()) {
            if (id.startsWith("fluid:")) {
                String fluid = id.substring(6);
                if (!registry.synthesisPolicy().allowsFluid(fluid)||!registry.synthesisPolicy().allowsMachine(fluid,kind.id())) return Optional.empty();
                return registry.fluidProfile(fluid).map(profile -> new Product(id, name, "fluid", fluid,
                        profile.amount(), profile.matter(), Map.of(), profile.synthesisEnergy(), profile.ticks()));
            }
            if (!registry.synthesisPolicy().allowsItem(id) || !registry.synthesisPolicy().allowsMachine(id, kind.id())) return Optional.empty();
            return registry.profile(id).map(profile -> new Product(id, name, "item", id, profile.itemCount(),
                    profile.matter(), Map.of(), profile.synthesisEnergy(), profile.ticks()));
        }
        if (dev.latrox.rematter.content.MachineRouting.environmentSources(kind) && registry.environmentSource(SubstanceId.parse(id)).isPresent()) return registry.environmentSource(SubstanceId.parse(id))
                .map(source -> new Product(id, name, "matter", "", source.outputs().size(), Map.of(), source.outputs(), source.energy(), source.ticks()));
        if (!kind.selectable()) return Optional.empty();
        return registry.recipe(SubstanceId.parse(id))
                .filter(recipe -> dev.latrox.rematter.content.MachineRouting.supports(kind, recipe))
                .map(recipe -> new Product(id, name, "matter", "", recipe.outputs().size(),
                        recipe.inputs(), recipe.outputs(), recipe.energy(), recipe.ticks()));
    }

    /** A missing row is zero only for a complete, connected inventory. */
    public static OptionalLong available(Map<SubstanceKey, Long> amounts, boolean complete, SubstanceKey key) {
        Long amount = amounts.get(key);
        if (amount != null) return OptionalLong.of(amount);
        return complete ? OptionalLong.of(0) : OptionalLong.empty();
    }
}
