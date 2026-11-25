package dev.latrox.rematter.compat;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.ProcessType;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.SynthesisConfig;
import dev.latrox.rematter.client.Quantities;
import dev.latrox.rematter.client.SubstanceNames;
import dev.latrox.rematter.content.ChemistryText;
import dev.latrox.rematter.content.MachineKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Chemistry recipes as JEI and EMI show them: one category per real process, stable IDs and only reachable matter. */
public final class ChemistryViewer {
    private static final Identifier AIR = Identifier.withDefaultNamespace("air");

    private ChemistryViewer() {}

    /** Viewer knowledge is informational. Recipe membership continues to come only from the synthesis allowlist. */
    public static Optional<String> researchTarget(Entry entry) {
        return entry.researchTargets().isEmpty() ? Optional.empty() : Optional.of(entry.researchTargets().getFirst());
    }

    public static List<String> researchTargets(Entry entry) {
        return entry.researchTargets();
    }

    public enum Category {
        DECOMPOSITION, SYNTHESIS, ELECTROLYSIS, REACTION, SEPARATION, PHASE_CHANGE, CRYSTALLIZATION, TRANSMUTATION, PRIMAL_CONDENSATION, AIR_SEPARATION, DEEP_EXTRACTION;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public Component title() { return Component.translatable("gui.rematter.viewer." + id()); }

        /** Physical outputs use the Synthesizer; chemical transformations and environmental sources use reactors. */
        public List<MachineKind> machines() {
            return switch (this) {
                case DECOMPOSITION -> List.of(MachineKind.DECOMPOSER, MachineKind.INDUSTRIAL_DECOMPOSER);
                case SYNTHESIS -> List.of(MachineKind.SYNTHESIZER, MachineKind.INDUSTRIAL_SYNTHESIZER);
                case ELECTROLYSIS -> dev.latrox.rematter.content.MachineRouting.machines(ProcessType.ELECTROLYSIS);
                case REACTION -> dev.latrox.rematter.content.MachineRouting.machines(ProcessType.REACTION);
                case SEPARATION -> dev.latrox.rematter.content.MachineRouting.machines(ProcessType.SEPARATION);
                case PHASE_CHANGE -> dev.latrox.rematter.content.MachineRouting.machines(ProcessType.PHASE_CHANGE);
                case CRYSTALLIZATION -> dev.latrox.rematter.content.MachineRouting.machines(ProcessType.CRYSTALLIZATION);
                case TRANSMUTATION -> dev.latrox.rematter.content.MachineRouting.machines(ProcessType.TRANSMUTATION);
                case PRIMAL_CONDENSATION -> List.of(MachineKind.PRIMAL_REACTOR);
                case AIR_SEPARATION -> List.of(MachineKind.ATMOSPHERIC_COLLECTOR, MachineKind.REACTOR, MachineKind.INDUSTRIAL_REACTOR);
                case DEEP_EXTRACTION -> List.of(MachineKind.DEEP_EXTRACTOR);
            };
        }

        static Category of(ProcessType type) {
            return switch (type) {
                case ELECTROLYSIS -> ELECTROLYSIS;
                case REACTION -> REACTION;
                case SEPARATION -> SEPARATION;
                case PHASE_CHANGE -> PHASE_CHANGE;
                case CRYSTALLIZATION -> CRYSTALLIZATION;
                case TRANSMUTATION -> TRANSMUTATION;
            };
        }
    }

    /** An item batch or fluid volume (mB) that a decomposition consumes or a synthesis produces. */
    public record Resource(boolean fluid, Identifier id, int amount) {}

    public record Matter(SubstanceKey key, long amount) {}

    private record DecomposeGroupKey(int itemCount, List<Matter> outputs, long energy, int ticks, boolean synthesisAllowed) {}

    /** {@code process} names the chemical process that a reaction, separation or phase change entry shows. */
    public record Entry(Category category, Identifier id, Optional<Component> process, List<Resource> consumedChoices, Optional<Resource> produced,
                       List<Matter> inputs, List<Matter> outputs, long energy, int ticks, boolean synthesisAllowed,
                       String requiresAdvancement, int requiredTier, List<String> researchTargets, int minField, String catalyst, String probeBelow) {
        public Entry(Category category, Identifier id, Optional<Component> process, List<Resource> consumedChoices, Optional<Resource> produced,
                List<Matter> inputs, List<Matter> outputs, long energy, int ticks, boolean synthesisAllowed,
                String requiresAdvancement, int requiredTier, List<String> researchTargets, int minField, String catalyst) {
            this(category, id, process, consumedChoices, produced, inputs, outputs, energy, ticks, synthesisAllowed,
                    requiresAdvancement, requiredTier, researchTargets, minField, catalyst, "");
        }
        public Entry(Category category, Identifier id, Optional<Component> process, List<Resource> consumedChoices, Optional<Resource> produced,
                List<Matter> inputs, List<Matter> outputs, long energy, int ticks, boolean synthesisAllowed,
                String requiresAdvancement, int requiredTier, List<String> researchTargets) {
            this(category, id, process, consumedChoices, produced, inputs, outputs, energy, ticks, synthesisAllowed,
                    requiresAdvancement, requiredTier, researchTargets, 0, "", "");
        }
        public Entry(Category category, Identifier id, Optional<Component> process, Optional<Resource> consumed, Optional<Resource> produced,
                List<Matter> inputs, List<Matter> outputs, long energy, int ticks, boolean synthesisAllowed) {
            this(category, id, process, consumed.map(List::of).orElse(List.of()), produced, inputs, outputs, energy, ticks,
                    synthesisAllowed, "", 1, defaultResearchTargets(synthesisAllowed, consumed, produced), 0, "", "");
        }
        public Entry(Category category, Identifier id, Optional<Component> process, Optional<Resource> consumed, Optional<Resource> produced,
                List<Matter> inputs, List<Matter> outputs, long energy, int ticks, boolean synthesisAllowed,
                String requiresAdvancement, int requiredTier) {
            this(category, id, process, consumed.map(List::of).orElse(List.of()), produced, inputs, outputs, energy, ticks,
                    synthesisAllowed, requiresAdvancement, requiredTier, defaultResearchTargets(synthesisAllowed, consumed, produced), 0, "", "");
        }
        public Entry {
            consumedChoices = List.copyOf(consumedChoices);
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            researchTargets = List.copyOf(researchTargets);
            catalyst = catalyst == null ? "" : catalyst;
            probeBelow = probeBelow == null ? "" : probeBelow;
        }

        public Optional<Resource> consumed() {
            return consumedChoices.isEmpty() ? Optional.empty() : Optional.of(consumedChoices.getFirst());
        }

        private static List<String> defaultResearchTargets(boolean synthesisAllowed, Optional<Resource> consumed, Optional<Resource> produced) {
            return synthesisAllowed ? produced.or(() -> consumed).map(resource -> List.of(resource.id().toString())).orElse(List.of()) : List.of();
        }

        /** The same explanation in every viewer: the synthesis permission, the exact batch and where amounts are shown. */
        public List<Component> notes() {
            var notes = new ArrayList<Component>();
            process.ifPresent(notes::add);
            if(requiredTier>1)notes.add(Component.translatable("gui.rematter.viewer.pressure_required"));
            if(minField>0)notes.add(Component.translatable("gui.rematter.viewer.min_field_required",minField));
            if(!catalyst.isEmpty())notes.add(Component.translatable("gui.rematter.viewer.catalyst_required",SubstanceNames.catalystRequirement(catalyst)));
            if(!probeBelow.isEmpty())notes.add(SubstanceNames.probeRequirement(probeBelow));
            if(!requiresAdvancement.isEmpty())notes.add(Component.translatable("gui.rematter.viewer.advancement_required",requiresAdvancement));
            notes.add(Component.translatable(switch (category) {
                case DECOMPOSITION -> synthesisAllowed ? "gui.rematter.viewer.synthesis_allowed" : "gui.rematter.viewer.decomposition_only";
                case SYNTHESIS, CRYSTALLIZATION -> "gui.rematter.viewer.approved_output";
                case AIR_SEPARATION -> "gui.rematter.viewer.renewable_air";
                case DEEP_EXTRACTION -> "gui.rematter.viewer.renewable_extraction";
                case TRANSMUTATION -> "gui.rematter.viewer.nuclear_transmutation";
                case PRIMAL_CONDENSATION -> "gui.rematter.viewer.primal_condensation_note";
                default -> "gui.rematter.viewer.conserves_elements";
            }));
            Stream.of(consumed(), produced).flatMap(Optional::stream).filter(resource -> !resource.fluid() && resource.amount() > 1)
                    .forEach(resource -> notes.add(Component.translatable("gui.rematter.viewer.exact_batch", resource.amount())));
            notes.add(Component.translatable("gui.rematter.viewer.amount_hint"));
            return notes;
        }
    }

    public static List<Entry> entries(ChemistryRegistry registry) {
        return entries(registry, id -> !id.equals(AIR) && BuiltInRegistries.ITEM.containsKey(id), BuiltInRegistries.FLUID::containsKey);
    }

    /** Profiles whose item or fluid is not registered in this game are left out together with their matter. */
    static List<Entry> entries(ChemistryRegistry registry, Predicate<Identifier> itemExists, Predicate<Identifier> fluidExists) {
        return entries(registry, itemExists, fluidExists, SynthesisConfig.unifyOutputs(), SynthesisConfig.modPriority());
    }

    static List<Entry> entries(ChemistryRegistry registry, Predicate<Identifier> itemExists, Predicate<Identifier> fluidExists,
                               boolean unifyDecomposition, List<String> modPriority) {
        List<Entry> result = new ArrayList<>();
        var policy = registry.synthesisPolicy();
        Map<DecomposeGroupKey, List<MaterialProfile>> decomposeGroups = new LinkedHashMap<>();
        for (var profile : registry.profiles()) {
            Identifier item = Identifier.tryParse(profile.itemId());
            if (item == null || !itemExists.test(item)) continue;
            var matter = matter(profile.matter());
            boolean allowedDirect = policy.allowsItem(profile.itemId());
            boolean allowedCanonical = allowedDirect || policy.allowsItem(policy.canonicalTarget(profile.itemId()));
            if (unifyDecomposition) {
                var key = new DecomposeGroupKey(profile.itemCount(), matter, profile.decompositionEnergy(), profile.ticks(), allowedCanonical);
                decomposeGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(profile);
            } else {
                var resource = new Resource(false, item, profile.itemCount());
                List<String> targets = allowedCanonical ? List.of(policy.canonicalTarget(profile.itemId())) : List.of();
                result.add(new Entry(Category.DECOMPOSITION, recipeId(Category.DECOMPOSITION, "item", item), Optional.empty(),
                        List.of(resource), Optional.empty(), List.of(), matter, profile.decompositionEnergy(), profile.ticks(),
                        allowedCanonical, "", 1, targets));
            }
            if (allowedDirect) {
                var resource = new Resource(false, item, profile.itemCount());
                var synthesis = Category.SYNTHESIS;
                var req = policy.requirement(profile.itemId());
                result.add(new Entry(synthesis, recipeId(synthesis, "item", item), Optional.empty(), Optional.empty(),
                        Optional.of(resource), matter, List.of(), profile.synthesisEnergy(), profile.ticks(), true,
                        req.advancement(), req.tier()));
            }
        }
        if (unifyDecomposition) {
            Map<String, Integer> rankByMod = new HashMap<>();
            for (int i = 0; i < modPriority.size(); i++) rankByMod.putIfAbsent(modPriority.get(i), i);
            int fallbackRank = modPriority.size();
            Comparator<MaterialProfile> order = Comparator
                    .<MaterialProfile>comparingInt(p -> policy.allowsItem(p.itemId()) ? 0 : 1)
                    .thenComparingInt(p -> rankByMod.getOrDefault(namespace(p.itemId()), fallbackRank))
                    .thenComparing(MaterialProfile::itemId);
            for (var grouped : decomposeGroups.entrySet()) {
                var key = grouped.getKey();
                var members = new ArrayList<>(grouped.getValue());
                members.sort(order);
                Identifier primaryId = Identifier.parse(members.getFirst().itemId());
                List<Resource> choices = members.stream()
                        .map(p -> new Resource(false, Identifier.parse(p.itemId()), p.itemCount()))
                        .toList();
                List<String> targets = key.synthesisAllowed()
                        ? members.stream().map(p -> policy.canonicalTarget(p.itemId()))
                                .filter(policy::allowsItem).distinct().toList()
                        : List.of();
                result.add(new Entry(Category.DECOMPOSITION, recipeId(Category.DECOMPOSITION, "item", primaryId), Optional.empty(),
                        choices, Optional.empty(), List.of(), key.outputs(), key.energy(), key.ticks(),
                        key.synthesisAllowed(), "", 1, targets));
            }
        }
        for (var profile : registry.fluidProfiles()) {
            Identifier fluid = Identifier.tryParse(profile.fluidId());
            if (fluid == null || !fluidExists.test(fluid)) continue;
            var resource = new Resource(true, fluid, profile.amount());
            var matter = matter(profile.matter());
            boolean allowed = policy.allowsFluid(profile.fluidId());
            result.add(new Entry(Category.DECOMPOSITION, recipeId(Category.DECOMPOSITION, "fluid", fluid), Optional.empty(), Optional.of(resource),
                    Optional.empty(), List.of(), matter, profile.decompositionEnergy(), profile.ticks(), allowed));
            if (allowed) result.add(new Entry(Category.SYNTHESIS, recipeId(Category.SYNTHESIS, "fluid", fluid), Optional.empty(), Optional.empty(),
                    Optional.of(resource), matter, List.of(), profile.synthesisEnergy(), profile.ticks(), true,
                    policy.requirement(profile.fluidId()).advancement(), policy.requirement(profile.fluidId()).tier()));
        }
        for (var process : registry.processes()) {
            var category = Category.of(process.type());
            var source = Identifier.fromNamespaceAndPath(process.id().namespace(), process.id().path());
            result.add(new Entry(category, recipeId(category, "process", source), Optional.of(ChemistryText.process(process)), List.of(), Optional.empty(),
                    matter(process.inputs()), matter(process.outputs()), process.energy(), process.ticks(), false,
                    process.requiresAdvancement(), 1, List.of(), process.minField(), process.catalyst()));
        }
        var primalKey = new SubstanceKey(dev.latrox.rematter.chemistry.ReservedSubstances.PRIMAL_MATTER, dev.latrox.rematter.chemistry.Phase.SOLID);
        if (registry.isKnown(primalKey)) {
            for (var tier : dev.latrox.rematter.content.PrimalReactorRecipes.availableTiers(registry)) {
                var tid = Identifier.parse(tier.id());
                result.add(new Entry(Category.PRIMAL_CONDENSATION, recipeId(Category.PRIMAL_CONDENSATION, "collapse", tid),
                        Optional.of(Component.translatable(tier.translationKey())), List.of(), Optional.empty(),
                        matter(tier.inputs()), List.of(new Matter(primalKey, tier.primalOutput())),
                        tier.energy(), tier.ticks(), false, "", 1, List.of(), 0, "rematter:primal_crystal"));
            }
        }
        for(var source:registry.environmentSources()) {
            var id=Identifier.parse(source.id().toString());
            var cat = source.deepExtraction() ? Category.DEEP_EXTRACTION : Category.AIR_SEPARATION;
            result.add(new Entry(cat,recipeId(cat,"source",id),
                    Optional.of(Component.translatable(source.translationKey())),List.of(),Optional.empty(),List.of(),matter(source.outputs()),source.energy(),source.ticks(),false,"",1,List.of(),0,"",source.probeBelow()));
        }
        return List.copyOf(result);
    }

    private static String namespace(String itemId) {
        int colon = itemId.indexOf(':');
        return colon > 0 ? itemId.substring(0, colon) : "";
    }

    /** Matter that some listed recipe consumes or yields. Substances that no active content reaches stay out of the viewers. */
    public static List<SubstanceKey> substances(List<Entry> entries) {
        return entries.stream().flatMap(entry -> Stream.concat(entry.inputs().stream(), entry.outputs().stream()))
                .map(Matter::key).distinct().sorted().toList();
    }

    /** The viewer name of a matter ingredient: its labelled name and phase. */
    public static Component matterName(SubstanceKey key) {
        return Component.translatable("gui.rematter.viewer.substance_phase", SubstanceNames.labelled(key), SubstanceNames.phase(key.phase()));
    }

    /** The lines every substance tooltip starts with: the labelled name, the phase and, for an undefined key, the unknown hint. */
    public static List<Component> matterLines(SubstanceKey key) {
        var lines = new ArrayList<Component>();
        lines.add(SubstanceNames.labelled(key));
        lines.add(SubstanceNames.phase(key.phase()));
        if (!SubstanceNames.known(key)) lines.add(Component.translatable("screen.rematter.unknown_substance_hint"));
        return lines;
    }

    public static List<Component> matterTooltip(SubstanceKey key, long amount) {
        var lines = new ArrayList<>(dev.latrox.rematter.client.SubstanceDetails.tooltip(new dev.latrox.rematter.client.SubstanceDetails.Hover(key, amount), false));
        lines.add(Component.translatable("gui.rematter.viewer.stored_substance"));
        return lines;
    }

    /** Synthetic IDs start with a slash and depend only on the source, so bookmarks and favorites survive restarts. */
    static Identifier recipeId(Category category, String source, Identifier id) {
        return Identifier.fromNamespaceAndPath("rematter", "/" + category.id() + "/" + source + "/" + id.getNamespace() + "/" + id.getPath());
    }

    private static List<Matter> matter(Map<SubstanceKey, Long> amounts) {
        return amounts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new Matter(entry.getKey(), entry.getValue())).toList();
    }
}
