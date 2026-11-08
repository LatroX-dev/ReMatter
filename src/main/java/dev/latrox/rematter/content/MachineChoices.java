package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.ProcessType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.*;

/** Shared client/server recipe choices, built once per immutable catalogue and machine kind and named in the reader's language. */
public final class MachineChoices {
    public static final int MAX_CHOICES = ChemistryRegistry.MAX_MATERIAL_PROFILES + ChemistryRegistry.MAX_FLUID_PROFILES;
    private static final Map<ChemistryRegistry, EnumMap<MachineKind, List<Choice>>> CACHE = new WeakHashMap<>();
    private static final Map<ChemistryRegistry, EnumMap<MachineKind, Map<String, Choice>>> INDEX = new WeakHashMap<>();

    private MachineChoices() {}

    public static synchronized List<Choice> forKind(MachineKind kind, ChemistryRegistry registry) {
        return CACHE.computeIfAbsent(registry, ignored -> new EnumMap<>(MachineKind.class))
                .computeIfAbsent(kind, ignored -> build(kind, registry));
    }

    public static synchronized Optional<Choice> find(MachineKind kind, ChemistryRegistry registry, String id) {
        return Optional.ofNullable(INDEX.computeIfAbsent(registry, ignored -> new EnumMap<>(MachineKind.class))
                .computeIfAbsent(kind, ignored -> {
                    var byId = new HashMap<String, Choice>();
                    for (Choice choice : forKind(kind, registry)) byId.putIfAbsent(choice.id(), choice);
                    return Map.copyOf(byId);
                }).get(id));
    }

    public static final String FLUID_PREFIX = "fluid:";
    public static String fluidChoiceId(String fluidId) { return FLUID_PREFIX + fluidId; }

    private static List<Choice> build(MachineKind kind, ChemistryRegistry registry) {
        List<Choice> choices = new ArrayList<>();
        if (kind == MachineKind.SYNTHESIZER) {
            for (var profile : registry.profiles()) {
                if (!registry.synthesisPolicy().allowsItem(profile.itemId())) continue;
                Identifier id = Identifier.parse(profile.itemId());
                if (!BuiltInRegistries.ITEM.containsKey(id)) continue;
                var item = BuiltInRegistries.ITEM.getValue(id);
                Component name = item.getName(item.getDefaultInstance());
                choices.add(new Choice(profile.itemId(), profile.itemCount() == 1 ? name.getString()
                        : Component.translatableWithFallback("gui.rematter.choice.items", "%s × %s", profile.itemCount(), name).getString()));
            }
            for (var profile : registry.fluidProfiles()) {
                if (!registry.synthesisPolicy().allowsFluid(profile.fluidId())) continue;
                Identifier id = Identifier.parse(profile.fluidId());
                if (!BuiltInRegistries.FLUID.containsKey(id)) continue;
                Component name = FluidResource.of(BuiltInRegistries.FLUID.getValue(id)).getHoverName();
                choices.add(new Choice(fluidChoiceId(profile.fluidId()),
                        Component.translatableWithFallback("gui.rematter.choice.fluid", "%s mB %s", profile.amount(), name).getString()));
            }
        } else if (kind == MachineKind.ELECTROLYZER || kind == MachineKind.REACTOR || kind == MachineKind.INDUSTRIAL_REACTOR) {
            registry.processes().stream()
                    .filter(recipe -> (kind == MachineKind.ELECTROLYZER) == (recipe.type() == ProcessType.ELECTROLYSIS))
                    .forEach(recipe -> choices.add(new Choice(recipe.id().toString(), ChemistryText.process(recipe).getString())));
        }
        if (choices.size() > MAX_CHOICES) throw new IllegalArgumentException("Machine choice limit exceeded");
        choices.sort(Comparator.comparing(Choice::name).thenComparing(Choice::id));
        return List.copyOf(choices);
    }

    public record Choice(String id, String name) {}
}
