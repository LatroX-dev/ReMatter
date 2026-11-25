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

    public record Requirements(boolean pressure, int minField, String catalystItemId, String advancement, String probeBelow) {
        public static final Requirements NONE = new Requirements(false, 0, "", "", "");
        public Requirements {
            catalystItemId = catalystItemId == null ? "" : catalystItemId;
            advancement = advancement == null ? "" : advancement;
            probeBelow = probeBelow == null ? "" : probeBelow;
        }
    }

    public static Requirements requirements(ChemistryRegistry registry, MachineKind kind, Product product) {
        if (product == null) return Requirements.NONE;
        if (kind.synthesis()) {
            String id = product.id().startsWith("fluid:") ? product.id().substring(6) : product.id();
            var requirement = registry.synthesisPolicy().requirement(id);
            return new Requirements(requirement.tier() > 1, 0, "", requirement.advancement(), "");
        }
        if (kind == MachineKind.PRIMAL_REACTOR) {
            return Requirements.NONE;
        }
        var parsedId = parseId(product.id());
        if (parsedId.isEmpty()) return Requirements.NONE;
        if (dev.latrox.rematter.content.MachineRouting.environmentSources(kind)) {
            var source = registry.environmentSource(parsedId.get()).orElse(null);
            if (source != null && source.deepExtraction()) {
                return new Requirements(false, 0, "", "", source.probeBelow());
            }
        }
        var process = registry.recipe(parsedId.get()).orElse(null);
        if (process == null) return Requirements.NONE;
        return new Requirements(false, process.minField(), process.catalyst(), process.requiresAdvancement(), "");
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
        if (kind == MachineKind.PRIMAL_REACTOR) {
            var primalKey = new SubstanceKey(dev.latrox.rematter.chemistry.ReservedSubstances.PRIMAL_MATTER, dev.latrox.rematter.chemistry.Phase.SOLID);
            var tier = dev.latrox.rematter.content.PrimalReactorRecipes.condensation(id, registry).orElse(null);
            if (tier != null) {
                return Optional.of(new Product(id, name, "matter", "", 1, tier.inputs(),
                        Map.of(primalKey, tier.primalOutput()), tier.energy(), tier.ticks()));
            }
            var manifest = dev.latrox.rematter.content.PrimalReactorRecipes.manifestation(id, registry).orElse(null);
            if (manifest != null) {
                return Optional.of(new Product(id, name, "matter", "", 1,
                        Map.of(primalKey, manifest.primalCost()), Map.of(manifest.target(), manifest.outputAmount()),
                        manifest.energy(), manifest.ticks()));
            }
            return Optional.empty();
        }
        var parsedId = parseId(id);
        if (parsedId.isEmpty()) return Optional.empty();
        if (dev.latrox.rematter.content.MachineRouting.environmentSources(kind) && registry.environmentSource(parsedId.get()).isPresent()) return registry.environmentSource(parsedId.get())
                .map(source -> new Product(id, name, "matter", "", source.outputs().size(), Map.of(), source.outputs(), source.energy(), source.ticks()));
        if (!kind.selectable()) return Optional.empty();
        return registry.recipe(parsedId.get())
                .filter(recipe -> dev.latrox.rematter.content.MachineRouting.supports(kind, recipe))
                .map(recipe -> new Product(id, name, "matter", "", recipe.outputs().size(),
                        recipe.inputs(), recipe.outputs(), recipe.energy(), recipe.ticks()));
    }

    private static Optional<SubstanceId> parseId(String raw) {
        try { return Optional.of(SubstanceId.parse(raw)); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    /** A missing row is zero only for a complete, connected inventory. */
    public static OptionalLong available(Map<SubstanceKey, Long> amounts, boolean complete, SubstanceKey key) {
        Long amount = amounts.get(key);
        if (amount != null) return OptionalLong.of(amount);
        return complete ? OptionalLong.of(0) : OptionalLong.empty();
    }
}
