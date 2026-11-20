package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.research.MaterialResearch;
import dev.latrox.rematter.research.ResearchCatalogue;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Material entering and leaving the server's proposed sequence, including its intermediate stock. */
public final class LabGoalMaterials {
    private LabGoalMaterials() {}

    public record Row(String substanceId, String phase, long required, long available, long remainder) {}

    public static List<Row> summarize(ChemistryRegistry chemistry, List<TerminalBatchPlanner.PlanStep> steps,
            TerminalBatchPlanner.TargetKind kind, String id, String phase, int quantity, Map<SubstanceKey, Long> stock) {
        var required = new TreeMap<SubstanceKey, Long>();
        var produced = new TreeMap<SubstanceKey, Long>();
        for (var step : steps) {
            Map<SubstanceKey, Long> inputs = Map.of(), outputs = Map.of();
            switch (step.kind()) {
                case PROCESS -> {
                    var recipe = chemistry.recipe(SubstanceId.parse(step.choiceId())).orElseThrow();
                    inputs = recipe.inputs();
                    outputs = recipe.outputs();
                }
                case ENVIRONMENT -> outputs = chemistry.environmentSource(SubstanceId.parse(step.choiceId())).orElseThrow().outputs();
                case SYNTHESIS_ITEM -> inputs = chemistry.profile(step.choiceId()).orElseThrow().matter();
                case SYNTHESIS_FLUID -> inputs = chemistry.fluidProfile(step.choiceId().substring(MachineChoices.FLUID_PREFIX.length())).orElseThrow().matter();
            }
            inputs.forEach((key, amount) -> {
                long use = Math.multiplyExact(amount, step.batches());
                long intermediate = produced.getOrDefault(key, 0L);
                long fromPlan = Math.min(use, intermediate);
                produced.put(key, intermediate - fromPlan);
                if (use > fromPlan) required.merge(key, use - fromPlan, Math::addExact);
            });
            outputs.forEach((key, amount) -> produced.merge(key, Math.multiplyExact(amount, step.batches()), Math::addExact));
        }
        if (kind == TerminalBatchPlanner.TargetKind.SUBSTANCE && !steps.isEmpty()) {
            var product = SubstanceKey.of(id, Phase.valueOf(phase.toUpperCase(Locale.ROOT)));
            produced.computeIfPresent(product, (key, amount) -> Math.max(0L, amount - quantity));
        }
        return Stream.concat(required.keySet().stream(), produced.keySet().stream()).distinct().sorted()
                .filter(key -> required.getOrDefault(key, 0L) > 0 || produced.getOrDefault(key, 0L) > 0)
                .map(key -> new Row(key.id().toString(), key.phase().name().toLowerCase(Locale.ROOT), required.getOrDefault(key, 0L),
                        stock.getOrDefault(key, 0L), produced.getOrDefault(key, 0L))).toList();
    }

    /** Find a real, supported sample in an actual family of the loaded research catalogue. */
    public static String researchSample(ChemistryRegistry chemistry, ResearchCatalogue catalogue,
            TerminalBatchPlanner.TargetKind kind, String id, String phase, Predicate<SubstanceKey> known,
            Predicate<String> itemExists) {
        Stream<MaterialResearch> families;
        if (kind == TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM) {
            families = catalogue.forItem(id).stream();
        } else {
            Set<SubstanceKey> target;
            if (kind == TerminalBatchPlanner.TargetKind.SYNTHESIS_FLUID) {
                target = chemistry.fluidProfile(id).map(p -> p.matter().keySet()).orElse(Set.of());
            } else {
                try {
                    target = Set.of(SubstanceKey.of(id, Phase.valueOf(phase.toUpperCase(Locale.ROOT))));
                } catch (IllegalArgumentException invalid) {
                    return "";
                }
            }
            families = catalogue.families().stream().filter(family -> family.substances().stream()
                    .anyMatch(key -> target.contains(key) && !known.test(key)));
        }
        // Prefer a different, inexpensive sample over telling a player to acquire their locked target first.
        return families.flatMap(family -> family.items().stream()).distinct().filter(itemExists)
                .flatMap(item -> chemistry.profile(item).stream())
                .min(Comparator.comparing((MaterialProfile profile) -> profile.itemId().equals(id))
                        .thenComparingLong(MaterialProfile::decompositionEnergy)
                        .thenComparing(MaterialProfile::itemId))
                .map(MaterialProfile::itemId).orElse("");
    }
}
