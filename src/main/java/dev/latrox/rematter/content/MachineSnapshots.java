package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.automation.AutomationPort;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.MatterStore;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.network.MachineProgressPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * Builds the bounded views of one machine: the menu snapshot, its stock rows, its progress and a short summary.
 * Read-only: nothing here changes machine or network state. Names are never rendered here; clients translate IDs.
 */
final class MachineSnapshots {
    private MachineSnapshots() {}

    /** Stock rows in key order, limited to {@code entries}; {@code totalTypes} counts every stored type. */
    record Stock(boolean readable, int totalTypes, long used, long capacity, String source, List<MachineSnapshot.Entry> entries) {
        Stock { entries = List.copyOf(entries); }
    }

    private record Source(@Nullable MachineMatterNetwork network, @Nullable MatterStore store, boolean independent) {}

    private static Source source(MachineBlockEntity machine) {
        MachineKind kind = machine.kind();
        boolean independent = kind == MachineKind.ANALYZER || PreparationTier.isPreparation(kind);
        MachineMatterNetwork network = independent ? null : machine.matterNetwork(false);
        MatterStore store = independent ? null : kind.isStorage()
                ? machine.storageStatus().equals("ready") ? machine.matterStore() : null
                : network.ready() ? network.inventory() : null;
        return new Source(network, store, independent);
    }

    static Stock stock(MachineBlockEntity machine, int limit) { return stock(machine, source(machine), limit); }

    private static Stock stock(MachineBlockEntity machine, Source source, int limit) {
        ChemistryRegistry registry = ReMatter.chemistry();
        var store = source.store();
        Map<SubstanceKey, Long> displayed = store == null ? Map.of() : store.contents();
        if (machine.kind() == MachineKind.ANALYZER) {
            ItemStack sample = machine.stack(0);
            var profile = MachineOperations.profile(registry, sample);
            if (profile != null && machine.analysis().matches(MachineOperations.itemId(sample), sample.getComponentsPatch().isEmpty(), profile))
                displayed = profile.matter();
        }
        var entries = new ArrayList<MachineSnapshot.Entry>();
        if (limit > 0) for (var entry : new TreeMap<>(displayed).entrySet()) {
            if (entries.size() == limit) break;
            SubstanceKey key = entry.getKey();
            if (key.id().toString().length() > MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH) continue;
            entries.add(new MachineSnapshot.Entry(key.id().toString(), phase(key), entry.getValue(),
                    store == null ? 0 : store.available(key), registry.isKnown(key)));
        }
        String origin = store == null ? "none" : machine.kind().isStorage() ? "local" : "network";
        return new Stock(source.independent() || store != null, displayed.size(), store == null ? 0 : store.total(),
                store == null ? 0 : store.capacity(), origin, entries);
    }

    static MachineProgressPayload progress(MachineBlockEntity machine, int containerId) {
        var work = machine.work();
        return new MachineProgressPayload(containerId, (int) machine.energy().getAmountAsLong(), Math.min(work.progress, work.duration), work.duration);
    }

    static MachineBlockEntity.Summary summary(MachineBlockEntity machine, int rows) {
        var work = machine.work();
        var stock = stock(machine, rows);
        return new MachineBlockEntity.Summary(work.status, (int) machine.energy().getAmountAsLong(), Math.min(work.progress, work.duration),
                work.duration, stock.used(), stock.capacity(), stock.source(), stock.totalTypes(), stock.entries(), machine.automation(),
                (int) machine.fluids().getAmountAsLong(0), fluidId(machine.fluids().getResource(0)), structure(machine), machine.cellViews());
    }

    static MachineSnapshot build(MachineBlockEntity machine, int containerId) {
        ChemistryRegistry registry = ReMatter.chemistry();
        MachineKind kind = machine.kind();
        var source = source(machine);
        var store = source.store();
        int sampleBatch = 1;
        if (kind == MachineKind.ANALYZER || kind == MachineKind.DECOMPOSER) {
            var profile = MachineOperations.profile(registry, machine.stack(0));
            if (profile != null) sampleBatch = profile.itemCount();
        }
        var chosen = machine.selectedChoice();
        var runState = machine.runState();
        boolean batches = machine.supportsBatchControl();
        var fluids = machine.fluids();
        return new MachineSnapshot(containerId, kind.ordinal(), machine.work().status, chosen == null ? -1 : machine.selectedIndex(),
                chosen == null ? machine.selectedId() : chosen.id(), machine.choices().size(), sampleBatch,
                store == null ? 0 : store.total(), store == null ? 0 : store.capacity(), machine.automation(), runState.running(),
                (int) fluids.getAmountAsLong(0), fluidId(fluids.getResource(0)), batches && runState.repeat(), batches ? runState.queuedBatches() : 0,
                preview(machine, registry, store), store == null ? "none" : kind.isStorage() ? "local" : "network", structure(machine),
                source.network() == null ? MachineSnapshot.NetworkView.EMPTY : source.network().view(), machine.networkJobs(),
                kind == MachineKind.NETWORK_INTERFACE ? machine.automationHost().backend() : "", machine.cellViews());
    }

    private static MachineSnapshot.StructureView structure(MachineBlockEntity machine) {
        MachineKind kind = machine.kind();
        var formation = IndustrialStructure.isIndustrial(kind) ? IndustrialStructure.inspect(machine) : IndustrialStructure.Formation.EMPTY;
        var performance = formation.performance();
        var shape = formation.shape();
        return new MachineSnapshot.StructureView(shape == null ? 0 : shape.width(), shape == null ? 0 : shape.depth(),
                shape == null ? 0 : shape.height(), formation.formed() ? machine.speed() : 0, formation.moduleCount(), formation.diagnostic(),
                performance.grinding(), performance.mixing(), performance.separation(), performance.coils(), performance.chambers(),
                performance.energySavingPercent(kind));
    }

    private static MachineSnapshot.Preview preview(MachineBlockEntity machine, ChemistryRegistry registry, @Nullable MatterStore store) {
        if (machine.networkJobs()) return networkOrderPreview(machine, registry);
        MachineKind kind = machine.kind();
        var operations = machine.operations();
        if (PreparationTier.isPreparation(kind)) {
            var holder = operations.preparationRecipe(); if (holder == null) return MachineSnapshot.Preview.EMPTY;
            var recipe = holder.value(); var result = recipe.result();
            return preview(machine, registry, null, holder.id().identifier().toString(), Map.of(), Map.of(),
                    operations.preparationCost(recipe), recipe.ticks(), "item", MachineOperations.itemId(result), result.getCount());
        }
        if (kind == MachineKind.ANALYZER || kind == MachineKind.DECOMPOSER) {
            ItemStack input = machine.stack(0);
            var profile = input.getComponentsPatch().isEmpty() ? MachineOperations.profile(registry, input) : null;
            if (profile != null) {
                String id = MachineOperations.itemId(input);
                boolean inspect = kind == MachineKind.ANALYZER;
                Map<SubstanceKey, Long> outputs = inspect && !machine.analysis().matches(id, true, profile) ? Map.of() : profile.matter();
                return preview(machine, registry, store, MachineSnapshot.ITEM_SOURCE + id, Map.of(), outputs,
                        machine.cost(inspect ? MachineOperations.ANALYSIS_ENERGY : profile.decompositionEnergy()),
                        inspect ? MachineOperations.ANALYSIS_TICKS : profile.ticks(),
                        inspect ? "analysis" : "matter", inspect ? id : "", inspect ? 1 : 0);
            }
            if (kind == MachineKind.DECOMPOSER) {
                FluidResource fluid = machine.fluids().getResource(0);
                var conversion = registry.fluidProfile(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()).orElse(null);
                if (conversion != null && !fluid.isEmpty() && fluid.isComponentsPatchEmpty()) {
                    return preview(machine, registry, store, MachineChoices.fluidChoiceId(conversion.fluidId()), Map.of(), conversion.matter(),
                            machine.cost(conversion.decompositionEnergy()), conversion.ticks(), "matter", "", 0);
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
        return preview(machine, registry, store, choice.id(), operation.inputs(), operation.outputs(),
                operation.cost(), operation.ticks(), outputKind, outputId, outputCount);
    }

    /** Preview the transferred order itself, never a manually selected or inferred recipe. */
    private static MachineSnapshot.Preview networkOrderPreview(MachineBlockEntity machine, ChemistryRegistry registry) {
        var order = machine.networkWorker().currentOrder();
        if (order == null) return MachineSnapshot.Preview.EMPTY;
        var recipe = order.recipe();
        var inputs = new ArrayList<MachineSnapshot.Amount>();
        var outputs = new ArrayList<MachineSnapshot.Amount>();
        var resources = new ArrayList<MachineSnapshot.Resource>();
        for (var entry : new TreeMap<>(recipe.inputs()).entrySet()) {
            var resource = entry.getKey();
            if (resource.kind() != AutomationPort.Kind.MATTER) {
                if (resources.size() < MachineSnapshot.MAX_PREVIEW_AMOUNTS)
                    resources.add(new MachineSnapshot.Resource(resource.kind().name().toLowerCase(Locale.ROOT), resource.id(), entry.getValue()));
                continue;
            }
            if (inputs.size() == MachineSnapshot.MAX_PREVIEW_AMOUNTS) continue;
            var key = resource.matterKey();
            inputs.add(new MachineSnapshot.Amount(key.id().toString(), phase(key), entry.getValue(),
                    order.ownedInputs().getOrDefault(resource, 0L), registry.isKnown(key)));
        }
        for (var entry : new TreeMap<>(recipe.outputs()).entrySet()) {
            if (entry.getKey().kind() != AutomationPort.Kind.MATTER || outputs.size() == MachineSnapshot.MAX_PREVIEW_AMOUNTS) continue;
            var key = entry.getKey().matterKey();
            outputs.add(new MachineSnapshot.Amount(key.id().toString(), phase(key), entry.getValue(), 0, registry.isKnown(key)));
        }
        var first = recipe.outputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).findFirst().orElseThrow();
        boolean matterOutput = first.getKey().kind() == AutomationPort.Kind.MATTER;
        int count = matterOutput ? recipe.outputs().size() : Math.toIntExact(first.getValue());
        String outputKind = first.getKey().kind().name().toLowerCase(Locale.ROOT);
        long physicalInputs = recipe.inputs().keySet().stream().filter(resource -> resource.kind() != AutomationPort.Kind.MATTER).count();
        int speed = machine.speed();
        return new MachineSnapshot.Preview(recipe.id(), inputs, outputs,
                inputs.size() + resources.size() == recipe.inputs().size() && outputs.size() == recipe.outputs().size()
                        && resources.size() == physicalInputs,
                machine.cost(machine.networkWorker().baseEnergy(recipe.energy())),
                (int) (((long) recipe.ticks() + speed - 1) / speed), outputKind, first.getKey().id(), count, resources);
    }

    private static MachineSnapshot.Preview preview(MachineBlockEntity machine, ChemistryRegistry registry, @Nullable MatterStore store,
                                                   String id, Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                                                   long energy, int ticks, String outputKind, String outputId, int outputCount) {
        var inputRows = amounts(registry, store, inputs);
        var outputRows = amounts(registry, store, outputs);
        int speed = machine.speed();
        return new MachineSnapshot.Preview(id, inputRows, outputRows,
                inputRows.size() == inputs.size() && outputRows.size() == outputs.size(), energy,
                (int) (((long) ticks + speed - 1) / speed), outputKind, outputId, outputCount, List.of());
    }

    private static List<MachineSnapshot.Amount> amounts(ChemistryRegistry registry, @Nullable MatterStore store, Map<SubstanceKey, Long> amounts) {
        var rows = new ArrayList<MachineSnapshot.Amount>();
        for (var entry : new TreeMap<>(amounts).entrySet()) {
            if (rows.size() == MachineSnapshot.MAX_PREVIEW_AMOUNTS) break;
            SubstanceKey key = entry.getKey();
            if (key.id().toString().length() > MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH) continue;
            rows.add(new MachineSnapshot.Amount(key.id().toString(), phase(key), entry.getValue(),
                    store == null ? 0 : store.available(key), registry.isKnown(key)));
        }
        return List.copyOf(rows);
    }

    private static String phase(SubstanceKey key) { return key.phase().name().toLowerCase(Locale.ROOT); }

    private static String fluidId(FluidResource fluid) {
        return fluid.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
    }
}
