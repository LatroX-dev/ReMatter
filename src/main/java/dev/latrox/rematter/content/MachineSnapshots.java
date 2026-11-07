package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.automation.AutomationPort;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.MatterStore;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.preparation.PreparationTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/** Builds the bounded menu view of one machine. Read-only: nothing here changes machine or network state. */
final class MachineSnapshots {
    private MachineSnapshots() {}

    static MachineSnapshot build(MachineBlockEntity machine, int containerId) {
        ChemistryRegistry registry = ReMatter.chemistry();
        MachineKind kind = machine.kind();
        boolean independent = kind == MachineKind.ANALYZER || PreparationTier.isPreparation(kind);
        MachineMatterNetwork network = independent ? null : machine.matterNetwork(false);
        MatterStore store = independent ? null : kind.isStorage()
                ? machine.storageStatus().equals("ready") ? machine.matterStore() : null
                : network.ready() ? network.inventory() : null;
        boolean readableStorage = store != null;
        Map<SubstanceKey, Long> displayed = !readableStorage ? Map.of() : store.contents();
        int sampleBatch = 1;
        ItemStack sample = machine.stack(0);
        if (kind == MachineKind.ANALYZER || kind == MachineKind.DECOMPOSER) {
            var profile = MachineOperations.profile(registry, sample);
            if (profile != null) {
                sampleBatch = profile.itemCount();
                if (kind == MachineKind.ANALYZER && machine.analysis().matches(MachineOperations.itemId(sample),
                        sample.getComponentsPatch().isEmpty(), profile)) displayed = profile.matter();
            }
        }
        var entries = new ArrayList<MachineSnapshot.Entry>();
        for (var entry : new TreeMap<>(displayed).entrySet()) {
            if (entries.size() == MachineSnapshot.MAX_ENTRIES) break;
            if (entry.getKey().id().toString().length() > MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH) continue;
            entries.add(new MachineSnapshot.Entry(entry.getKey().id().toString(), substanceName(registry, entry.getKey()),
                    entry.getKey().phase().name().toLowerCase(Locale.ROOT), entry.getValue(),
                    store == null ? 0 : store.available(entry.getKey())));
        }
        var choices = machine.choices();
        var chosen = machine.selectedChoice();
        int selected = chosen == null ? -1 : machine.selectedIndex();
        var formation = IndustrialStructure.isIndustrial(kind) ? IndustrialStructure.inspect(machine) : IndustrialStructure.Formation.EMPTY;
        var performance = formation.performance();
        var shape = formation.shape();
        var structure = new MachineSnapshot.StructureView(shape == null ? 0 : shape.width(), shape == null ? 0 : shape.depth(),
                shape == null ? 0 : shape.height(), formation.formed() ? machine.speed() : 0, formation.moduleCount(), formation.diagnostic(),
                performance.grinding(), performance.mixing(), performance.separation(), performance.coils(), performance.chambers(),
                performance.energySavingPercent(kind));
        var work = machine.work();
        var runState = machine.runState();
        boolean batches = machine.supportsBatchControl();
        var fluid = machine.fluids().getResource(0);
        String selectedId = chosen == null ? machine.selectedId() : chosen.id();
        String selectedName = kind == MachineKind.NETWORK_INTERFACE ? machine.automationHost().backend()
                : chosen == null ? machine.selectedId() : chosen.name();
        return new MachineSnapshot(containerId, kind.ordinal(), (int) machine.energy().getAmountAsLong(), work.progress, work.duration,
                work.status, selected, selectedId, selectedName, choices.size(), sampleBatch,
                entries, entries.size() == displayed.size() && (independent || readableStorage), !readableStorage ? 0 : store.total(),
                !readableStorage ? 0 : store.capacity(), machine.automation(), runState.running(), (int) machine.fluids().getAmountAsLong(0),
                fluid.isEmpty() ? "" : fluid.getHoverName().getString(),
                batches && runState.repeat(), batches ? runState.queuedBatches() : 0, preview(machine, registry, readableStorage ? store : null),
                machine.cellSlot() >= 0 ? machine.cellView(0) : MachineSnapshot.CellView.EMPTY,
                store == null ? "none" : kind.isStorage() ? "local" : "network", structure,
                network == null ? MachineSnapshot.NetworkView.EMPTY : network.view(), machine.networkJobs(), machine.cellViews());
    }

    private static MachineSnapshot.Preview preview(MachineBlockEntity machine, ChemistryRegistry registry, @Nullable MatterStore store) {
        if (machine.networkJobs()) return networkOrderPreview(machine, registry);
        MachineKind kind = machine.kind();
        var operations = machine.operations();
        if (PreparationTier.isPreparation(kind)) {
            var holder = operations.preparationRecipe(); if (holder == null) return MachineSnapshot.Preview.EMPTY;
            var recipe = holder.value(); var result = recipe.result();
            return preview(machine, registry, null, holder.id().identifier().toString(), result.getHoverName().getString(), Map.of(), Map.of(),
                    operations.preparationCost(recipe), recipe.ticks(), "item", BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), result.getCount());
        }
        if (kind == MachineKind.ANALYZER || kind == MachineKind.DECOMPOSER) {
            ItemStack input = machine.stack(0);
            var profile = input.getComponentsPatch().isEmpty() ? MachineOperations.profile(registry, input) : null;
            if (profile != null) {
                String id = MachineOperations.itemId(input);
                boolean inspect = kind == MachineKind.ANALYZER;
                Map<SubstanceKey, Long> outputs = inspect && !machine.analysis().matches(id, true, profile) ? Map.of() : profile.matter();
                return preview(machine, registry, store, "item:" + id, input.getHoverName().getString(), Map.of(), outputs,
                        machine.cost(inspect ? MachineOperations.ANALYSIS_ENERGY : profile.decompositionEnergy()),
                        inspect ? MachineOperations.ANALYSIS_TICKS : profile.ticks(),
                        inspect ? "analysis" : "matter", inspect ? id : "", inspect ? 1 : 0);
            }
            if (kind == MachineKind.DECOMPOSER) {
                FluidResource fluid = machine.fluids().getResource(0);
                var conversion = registry.fluidProfile(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()).orElse(null);
                if (conversion != null && !fluid.isEmpty() && fluid.isComponentsPatchEmpty()) {
                    return preview(machine, registry, store, MachineChoices.fluidChoiceId(conversion.fluidId()), fluid.getHoverName().getString(), Map.of(),
                            conversion.matter(), machine.cost(conversion.decompositionEnergy()), conversion.ticks(), "matter", "", 0);
                }
            }
            return MachineSnapshot.Preview.EMPTY;
        }
        if (!machine.supportsBatchControl()) return MachineSnapshot.Preview.EMPTY;
        var choice = machine.selectedChoice();
        if (choice == null) return MachineSnapshot.Preview.EMPTY;
        var operation = operations.operation(registry);
        if (operation == null) return MachineSnapshot.Preview.EMPTY;
        String outputKind = "matter";
        String outputId = "";
        int outputCount = 0;
        if (kind == MachineKind.SYNTHESIZER) {
            if (choice.id().startsWith(MachineChoices.FLUID_PREFIX)) {
                var profile = registry.fluidProfile(choice.id().substring(MachineChoices.FLUID_PREFIX.length())).orElseThrow();
                outputKind = "fluid"; outputId = profile.fluidId(); outputCount = profile.amount();
            } else {
                var profile = registry.profile(choice.id()).orElseThrow();
                outputKind = "item"; outputId = profile.itemId(); outputCount = profile.itemCount();
            }
        }
        return preview(machine, registry, store, choice.id(), choice.name(), operation.inputs(), operation.outputs(),
                operation.cost(), operation.ticks(), outputKind, outputId, outputCount);
    }

    /** Preview the transferred order itself, never a manually selected or inferred recipe. */
    private static MachineSnapshot.Preview networkOrderPreview(MachineBlockEntity machine, ChemistryRegistry registry) {
        var order = machine.networkWorker().currentOrder();
        if (order == null) return MachineSnapshot.Preview.EMPTY;
        var recipe = order.recipe();
        var inputs = new ArrayList<MachineSnapshot.Amount>();
        var outputs = new ArrayList<MachineSnapshot.Amount>();
        for (var entry : recipe.inputs().entrySet()) {
            if (entry.getKey().kind() != AutomationPort.Kind.MATTER || inputs.size() == MachineSnapshot.MAX_PREVIEW_AMOUNTS) continue;
            var key = entry.getKey().matterKey();
            inputs.add(new MachineSnapshot.Amount(key.id().toString(), substanceName(registry, key), entry.getKey().phase(),
                    entry.getValue(), order.ownedInputs().getOrDefault(entry.getKey(), 0L)));
        }
        for (var entry : recipe.outputs().entrySet()) {
            if (entry.getKey().kind() != AutomationPort.Kind.MATTER || outputs.size() == MachineSnapshot.MAX_PREVIEW_AMOUNTS) continue;
            var key = entry.getKey().matterKey();
            outputs.add(new MachineSnapshot.Amount(key.id().toString(), substanceName(registry, key), entry.getKey().phase(), entry.getValue(), 0));
        }
        var first = recipe.outputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).findFirst().orElseThrow();
        String name = first.getValue() + " " + resourceName(registry, first.getKey());
        String physicalInputs = recipe.inputs().entrySet().stream().filter(entry -> entry.getKey().kind() != AutomationPort.Kind.MATTER)
                .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getValue() + " " + resourceName(registry, entry.getKey()))
                .collect(Collectors.joining(" + "));
        if (!physicalInputs.isEmpty()) name = physicalInputs + " → " + name;
        boolean matterOutput = first.getKey().kind() == AutomationPort.Kind.MATTER;
        int count = matterOutput ? recipe.outputs().size() : Math.toIntExact(first.getValue());
        String outputKind = first.getKey().kind().name().toLowerCase(Locale.ROOT);
        int speed = machine.speed();
        return new MachineSnapshot.Preview(recipe.id(), name, inputs, outputs,
                inputs.size() == recipe.inputs().size() && outputs.size() == recipe.outputs().size(),
                machine.cost(machine.networkWorker().baseEnergy(recipe.energy())),
                (int) (((long) recipe.ticks() + speed - 1) / speed), outputKind, first.getKey().id(), count);
    }

    private static String resourceName(ChemistryRegistry registry, AutomationPort.Resource resource) {
        var id = Identifier.parse(resource.id());
        if (resource.kind() == AutomationPort.Kind.ITEM && BuiltInRegistries.ITEM.containsKey(id))
            return new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName().getString();
        if (resource.kind() == AutomationPort.Kind.FLUID && BuiltInRegistries.FLUID.containsKey(id))
            return FluidResource.of(BuiltInRegistries.FLUID.getValue(id)).getHoverName().getString();
        return resource.kind() == AutomationPort.Kind.MATTER ? substanceName(registry, resource.matterKey()) : resource.id();
    }

    private static MachineSnapshot.Preview preview(MachineBlockEntity machine, ChemistryRegistry registry, @Nullable MatterStore store,
                                                   String id, String name, Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                                                   long energy, int ticks, String outputKind, String outputId, int outputCount) {
        var inputRows = amounts(registry, store, inputs);
        var outputRows = amounts(registry, store, outputs);
        int speed = machine.speed();
        return new MachineSnapshot.Preview(id, name, inputRows, outputRows,
                inputRows.size() == inputs.size() && outputRows.size() == outputs.size(), energy,
                (int) (((long) ticks + speed - 1) / speed), outputKind, outputId, outputCount);
    }

    private static List<MachineSnapshot.Amount> amounts(ChemistryRegistry registry, @Nullable MatterStore store, Map<SubstanceKey, Long> amounts) {
        var rows = new ArrayList<MachineSnapshot.Amount>();
        for (var entry : new TreeMap<>(amounts).entrySet()) {
            if (rows.size() == MachineSnapshot.MAX_PREVIEW_AMOUNTS) break;
            if (entry.getKey().id().toString().length() > MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH) continue;
            rows.add(new MachineSnapshot.Amount(entry.getKey().id().toString(), substanceName(registry, entry.getKey()),
                    entry.getKey().phase().name().toLowerCase(Locale.ROOT), entry.getValue(), store == null ? 0 : store.available(entry.getKey())));
        }
        return List.copyOf(rows);
    }

    private static String substanceName(ChemistryRegistry registry, SubstanceKey key) {
        var definition = registry.definition(key.id()).orElse(null);
        if (definition == null) return pretty(key.id().path());
        String translated = Component.translatable(definition.translationKey()).getString();
        return translated.equals(definition.translationKey()) ? pretty(key.id().path()) : translated;
    }

    private static String pretty(String id) {
        String value = id.replace('_', ' ');
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
