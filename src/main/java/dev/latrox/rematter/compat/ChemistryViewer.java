package dev.latrox.rematter.compat;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.ProcessType;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.Quantities;
import dev.latrox.rematter.client.SubstanceNames;
import dev.latrox.rematter.content.ChemistryText;
import dev.latrox.rematter.content.MachineKind;
import java.util.ArrayList;
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

    public enum Category {
        DECOMPOSITION, SYNTHESIS, ELECTROLYSIS, REACTION, SEPARATION, PHASE_CHANGE, CRYSTALLIZATION, AIR_SEPARATION;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public Component title() { return Component.translatable("gui.rematter.viewer." + id()); }

        /** The machines that run this category. Electrolysis has its own machine; every other process runs in the reactors. */
        public List<MachineKind> machines() {
            return switch (this) {
                case DECOMPOSITION -> List.of(MachineKind.DECOMPOSER);
                case SYNTHESIS -> List.of(MachineKind.SYNTHESIZER);
                case ELECTROLYSIS -> dev.latrox.rematter.content.MachineRouting.machines(ProcessType.ELECTROLYSIS);
                case REACTION -> dev.latrox.rematter.content.MachineRouting.machines(ProcessType.REACTION);
                case SEPARATION -> dev.latrox.rematter.content.MachineRouting.machines(ProcessType.SEPARATION);
                case PHASE_CHANGE -> dev.latrox.rematter.content.MachineRouting.machines(ProcessType.PHASE_CHANGE);
                case CRYSTALLIZATION -> List.of(MachineKind.CRYSTALLIZER);
                case AIR_SEPARATION -> List.of(MachineKind.AIR_SEPARATOR);
            };
        }

        static Category of(ProcessType type) {
            return switch (type) {
                case ELECTROLYSIS -> ELECTROLYSIS;
                case REACTION -> REACTION;
                case SEPARATION -> SEPARATION;
                case PHASE_CHANGE -> PHASE_CHANGE;
                case CRYSTALLIZATION -> CRYSTALLIZATION;
            };
        }
    }

    /** An item batch or fluid volume (mB) that a decomposition consumes or a synthesis produces. */
    public record Resource(boolean fluid, Identifier id, int amount) {}

    public record Matter(SubstanceKey key, long amount) {}

    /** {@code process} names the chemical process that a reaction, separation or phase change entry shows. */
    public record Entry(Category category, Identifier id, Optional<Component> process, Optional<Resource> consumed, Optional<Resource> produced,
                       List<Matter> inputs, List<Matter> outputs, long energy, int ticks, boolean synthesisAllowed,
                       String requiresAdvancement,int requiredTier) {
        public Entry(Category category,Identifier id,Optional<Component> process,Optional<Resource> consumed,Optional<Resource> produced,
                List<Matter> inputs,List<Matter> outputs,long energy,int ticks,boolean synthesisAllowed) {
            this(category,id,process,consumed,produced,inputs,outputs,energy,ticks,synthesisAllowed,"",1);
        }
        public Entry {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
        }

        /** The same explanation in every viewer: the synthesis permission, the exact batch and where amounts are shown. */
        public List<Component> notes() {
            var notes = new ArrayList<Component>();
            process.ifPresent(notes::add);
            if(requiredTier>1)notes.add(Component.translatable("gui.rematter.viewer.pressure_required"));
            if(!requiresAdvancement.isEmpty())notes.add(Component.translatable("gui.rematter.viewer.advancement_required",requiresAdvancement));
            notes.add(Component.translatable(switch (category) {
                case DECOMPOSITION -> synthesisAllowed ? "gui.rematter.viewer.synthesis_allowed" : "gui.rematter.viewer.decomposition_only";
                case SYNTHESIS, CRYSTALLIZATION -> "gui.rematter.viewer.approved_output";
                case AIR_SEPARATION -> "gui.rematter.viewer.renewable_air";
                default -> "gui.rematter.viewer.conserves_elements";
            }));
            Stream.of(consumed, produced).flatMap(Optional::stream).filter(resource -> !resource.fluid() && resource.amount() > 1)
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
        List<Entry> result = new ArrayList<>();
        for (var profile : registry.profiles()) {
            Identifier item = Identifier.tryParse(profile.itemId());
            if (item == null || !itemExists.test(item)) continue;
            var resource = new Resource(false, item, profile.itemCount());
            var matter = matter(profile.matter());
            boolean allowed = registry.synthesisPolicy().allowsItem(profile.itemId());
            result.add(new Entry(Category.DECOMPOSITION, recipeId(Category.DECOMPOSITION, "item", item), Optional.empty(), Optional.of(resource),
                    Optional.empty(), List.of(), matter, profile.decompositionEnergy(), profile.ticks(), allowed));
            var synthesis=registry.synthesisPolicy().requirement(profile.itemId()).machine().equals("crystallizer")?Category.CRYSTALLIZATION:Category.SYNTHESIS;
            if (allowed) result.add(new Entry(synthesis, recipeId(synthesis, "item", item), Optional.empty(), Optional.empty(),
                    Optional.of(resource), matter, List.of(), profile.synthesisEnergy(), profile.ticks(), true,
                    registry.synthesisPolicy().requirement(profile.itemId()).advancement(),registry.synthesisPolicy().requirement(profile.itemId()).tier()));
        }
        for (var profile : registry.fluidProfiles()) {
            Identifier fluid = Identifier.tryParse(profile.fluidId());
            if (fluid == null || !fluidExists.test(fluid)) continue;
            var resource = new Resource(true, fluid, profile.amount());
            var matter = matter(profile.matter());
            boolean allowed = registry.synthesisPolicy().allowsFluid(profile.fluidId());
            result.add(new Entry(Category.DECOMPOSITION, recipeId(Category.DECOMPOSITION, "fluid", fluid), Optional.empty(), Optional.of(resource),
                    Optional.empty(), List.of(), matter, profile.decompositionEnergy(), profile.ticks(), allowed));
            if (allowed) result.add(new Entry(Category.SYNTHESIS, recipeId(Category.SYNTHESIS, "fluid", fluid), Optional.empty(), Optional.empty(),
                    Optional.of(resource), matter, List.of(), profile.synthesisEnergy(), profile.ticks(), true,
                    registry.synthesisPolicy().requirement(profile.fluidId()).advancement(),registry.synthesisPolicy().requirement(profile.fluidId()).tier()));
        }
        for (var process : registry.processes()) {
            var category = Category.of(process.type());
            var source = Identifier.fromNamespaceAndPath(process.id().namespace(), process.id().path());
            result.add(new Entry(category, recipeId(category, "process", source), Optional.of(ChemistryText.process(process)), Optional.empty(), Optional.empty(),
                    matter(process.inputs()), matter(process.outputs()), process.energy(), process.ticks(), false,process.requiresAdvancement(),1));
        }
        for(var source:registry.environmentSources()) {
            var id=Identifier.parse(source.id().toString());
            result.add(new Entry(Category.AIR_SEPARATION,recipeId(Category.AIR_SEPARATION,"source",id),
                    Optional.of(Component.translatable(source.translationKey())),Optional.empty(),Optional.empty(),List.of(),matter(source.outputs()),source.energy(),source.ticks(),false));
        }
        return List.copyOf(result);
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
        var lines = matterLines(key);
        lines.add(Component.translatable("gui.rematter.viewer.matter_units", Quantities.number(amount)));
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
