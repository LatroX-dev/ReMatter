package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.automation.AutomationPort;
import dev.latrox.rematter.automation.OrderLedger;
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

    /** Stock rows in key order, selected by the viewer's query within the row limit; {@code totalTypes} counts every stored type. */
    record Stock(boolean readable, int totalTypes, long used, long capacity, String source, List<MachineSnapshot.Entry> entries, int matchingTypes, boolean approximate) {
        Stock { entries = List.copyOf(entries); }
    }

    private record Source(@Nullable MachineMatterNetwork network, @Nullable MatterStore store, boolean independent) {}

    private static Source source(MachineBlockEntity machine) {
        MachineKind kind = machine.kind();
        boolean independent = kind == MachineKind.ANALYZER || kind.isEnergyMatrix() || PreparationTier.isPreparation(kind);
        MachineMatterNetwork network = independent ? null : machine.matterNetwork(false);
        MatterStore store = independent ? null : kind.isStorage()
                ? machine.storageStatus().equals("ready") ? machine.matterStore() : null
                : network.ready() ? network.inventory() : null;
        return new Source(network, store, independent);
    }

    static Stock stock(MachineBlockEntity machine, int limit) { return stock(machine, source(machine), StockQuery.ALL, limit); }

    /** A menu's stock rows, rebuilt only after the store, its revision, the catalogue or the viewer's query changed. */
    static final class StockView {
        private @Nullable MatterStore store;
        private long revision;
        private @Nullable ChemistryRegistry registry;
        private StockQuery query = StockQuery.ALL;
        private @Nullable Stock stock;

        /** Returns the previous instance while nothing that shapes the rows changed. */
        Stock refresh(MachineBlockEntity machine, StockQuery query, int limit) {
            var source = source(machine);
            var store = source.store();
            long revision = store == null ? 0 : store.revision();
            var registry = ReMatter.chemistry();
            // An analyzer shows its inspected sample, which no store revision tracks.
            if (stock == null || machine.kind() == MachineKind.ANALYZER || store != this.store || revision != this.revision
                    || registry != this.registry || !query.equals(this.query)) {
                stock = stock(machine, source, query, limit);
                this.store = store;
                this.revision = revision;
                this.registry = registry;
                this.query = query;
            }
            return stock;
        }

        Stock refresh(MachineMatterNetwork network, StockQuery query, int limit) {
            MatterStore store = network.ready() ? network.inventory() : null;
            var source = new Source(network, store, false);
            long revision = store == null ? 0 : store.revision();
            var registry = ReMatter.chemistry();
            if (stock == null || store != this.store || revision != this.revision
                    || registry != this.registry || !query.equals(this.query)) {
                stock = stock(null, source, query, limit);
                this.store = store;
                this.revision = revision;
                this.registry = registry;
                this.query = query;
            }
            return stock;
        }
    }

    private static Stock stock(@Nullable MachineBlockEntity machine, Source source, StockQuery query, int limit) {
        ChemistryRegistry registry = ReMatter.chemistry();
        var store = source.store();
        Map<SubstanceKey, Long> displayed = store == null ? Map.of() : store.contents();
        if (machine != null && machine.kind() == MachineKind.ANALYZER) {
            ItemStack sample = machine.stack(0);
            var profile = MachineOperations.profile(registry, sample);
            if (profile != null && machine.analysis().matches(MachineOperations.itemId(sample), sample.getComponentsPatch().isEmpty(), profile))
                displayed = profile.matter();
        }
        var entries = new ArrayList<MachineSnapshot.Entry>();
        var sorted = new TreeMap<>(displayed);
        var keys = sorted.keySet().stream().filter(key -> key.id().toString().length() <= MachineSnapshot.MAX_SUBSTANCE_ID_LENGTH).toList();
        for (var key : query.select(keys, limit)) {
            entries.add(new MachineSnapshot.Entry(key.id().toString(), phase(key), sorted.get(key), registry.isKnown(key)));
        }
        String origin = store == null ? "none" : machine != null && machine.kind().isStorage() ? "local" : "network";
        return new Stock(source.independent() || store != null, displayed.size(), store == null ? 0 : store.total(),
                store == null ? 0 : store.capacity(), origin, entries, query.matches(keys), query.namesTruncated());
    }

    static MachineProgressPayload progress(MachineBlockEntity machine, int containerId) {
        var work = machine.work();
        int cap = machine.energyCapacity();
        int stored = (int) Math.clamp(machine.energy().getAmountAsLong(), 0, cap);
        return new MachineProgressPayload(containerId, stored, cap, Math.min(work.progress, work.duration), work.duration);
    }

    static MachineBlockEntity.Summary summary(MachineBlockEntity machine, int rows) {
        var work = machine.work();
        var stock = stock(machine, rows);
        var host = machine.automationHost();
        return new MachineBlockEntity.Summary(work.status, (int) machine.energy().getAmountAsLong(), Math.min(work.progress, work.duration),
                work.duration, stock.used(), stock.capacity(), stock.source(), stock.totalTypes(), stock.entries(), machine.automation(),
                (int) machine.fluids().getAmountAsLong(0), fluidId(machine.fluids().getResource(0)), structure(machine), machine.cellViews(),
                host == null ? 0 : host.staleOrderCount(), host == null ? 0 : host.quarantinedOrderCount());
    }

    static MachineSnapshot buildWirelessTerminal(MachineMatterNetwork network, int containerId) {
        MatterStore store = network.ready() ? network.inventory() : null;
        return new MachineSnapshot(containerId, MachineKind.TERMINAL.ordinal(), network.status(), -1, "", 0, 1,
                store == null ? 0 : store.total(), store == null ? 0 : store.capacity(), false, false,
                0, "", false, 0, MachineSnapshot.Preview.EMPTY, store == null ? "none" : "network",
                MachineSnapshot.StructureView.EMPTY, network.view(), false, "", MachineSnapshot.OrdersView.EMPTY, List.of());
    }

    static MachineSnapshot build(MachineBlockEntity machine, int containerId) {
        ChemistryRegistry registry = ReMatter.chemistry();
        MachineKind kind = machine.kind();
        var source = source(machine);
        var store = source.store();
        int sampleBatch = 1;
        if (kind == MachineKind.ANALYZER || kind.decomposition()) {
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
                kind == MachineKind.NETWORK_INTERFACE ? machine.automationHost().backend() : "", orders(machine, registry), machine.cellViews());
    }

    /** The interface's outdated and stopped orders, with the resources held by the oldest stopped delivery. */
    private static MachineSnapshot.OrdersView orders(MachineBlockEntity machine, ChemistryRegistry registry) {
        var host = machine.automationHost();
        if (host == null) return MachineSnapshot.OrdersView.EMPTY;
        var order = host.releasableOrder().orElse(null);
        if (order == null) return new MachineSnapshot.OrdersView(host.staleOrderCount(), host.quarantinedOrderCount(), "", List.of(), List.of(), true);
        var held = OrderLedger.held(order);
        var matter = new ArrayList<MachineSnapshot.Amount>();
        var resources = new ArrayList<MachineSnapshot.Resource>();
        for (var entry : held.entrySet()) {
            var resource = entry.getKey();
            if (resource.kind() != AutomationPort.Kind.MATTER) {
                if (resources.size() < MachineSnapshot.MAX_PREVIEW_AMOUNTS)
                    resources.add(new MachineSnapshot.Resource(resource.kind().name().toLowerCase(Locale.ROOT), resource.id(), entry.getValue()));
            } else if (matter.size() < MachineSnapshot.MAX_PREVIEW_AMOUNTS) {
                var key = resource.matterKey();
                matter.add(new MachineSnapshot.Amount(key.id().toString(), phase(key), entry.getValue(), 0, registry.isKnown(key)));
            }
        }
        return new MachineSnapshot.OrdersView(host.staleOrderCount(), host.quarantinedOrderCount(), order.id().toString(),
                matter, resources, matter.size() + resources.size() == held.size());
    }

    private static MachineSnapshot.StructureView structure(MachineBlockEntity machine) {
        MachineKind kind = machine.kind();
        var formation = IndustrialStructure.isIndustrial(kind) ? IndustrialStructure.inspect(machine) : IndustrialStructure.Formation.EMPTY;
        var performance = formation.performance();
        var shape = formation.shape();
        int secondary = switch (kind) {
            case PRIMAL_REACTOR -> performance.emitters();
            case ENERGY_REACTOR -> performance.inductionCoils();
            case ENERGY_MATRIX -> performance.matrixProviders();
            default -> performance.coils();
        };
        int primary = switch (kind) {
            case PRIMAL_REACTOR -> performance.condensers();
            case TRANSMUTATION_REACTOR -> performance.emitters();
            case ENERGY_REACTOR -> performance.fuelCores();
            case ENERGY_MATRIX -> performance.matrixCells();
            default -> performance.chambers();
        };
        int coreCount = !formation.formed() ? 0 : kind == MachineKind.INDUSTRIAL_PREPARATOR ? performance.grinding() : primary;
        return new MachineSnapshot.StructureView(shape == null ? 0 : shape.width(), shape == null ? 0 : shape.depth(),
                shape == null ? 0 : shape.height(), formation.formed() ? machine.speed() : 0, coreCount, formation.diagnostic(),
                performance.grinding(), performance.mixing(), performance.separation(), secondary, primary,
                formation.formed()?machine.effectiveSavingPercent():0);
    }

    private static MachineSnapshot.Preview preview(MachineBlockEntity machine, ChemistryRegistry registry, @Nullable MatterStore store) {
        if (machine.networkJobs()) return networkOrderPreview(machine, registry);
        MachineKind kind = machine.kind();
        var operations = machine.operations();
        if (PreparationTier.isPreparation(kind)) {
            if(machine.assayMode()){
                var sample=machine.stack(0);var plan=dev.latrox.rematter.content.preparation.AssayPlan.create(registry,sample,!sample.isEmpty()&&machine.knownMaterial(MachineOperations.itemId(sample))).orElse(null);
                if(plan==null)return MachineSnapshot.Preview.EMPTY;
                return preview(machine,registry,null,MachineSnapshot.ITEM_SOURCE+plan.itemId(),Map.of(),Map.of(),machine.cost(plan.energy()),plan.ticks(),"item",plan.itemId(),1);
            }
            var holder = operations.preparationRecipe(); if (holder == null) return MachineSnapshot.Preview.EMPTY;
            var recipe = holder.value(); var result = recipe.result();
            return preview(machine, registry, null, holder.id().identifier().toString(), Map.of(), Map.of(),
                    operations.preparationCost(recipe), recipe.ticks(), "item", MachineOperations.itemId(result), result.getCount());
        }
        if (kind == MachineKind.ANALYZER || kind.decomposition()) {
            ItemStack input = machine.stack(0);
            var salvage = kind.decomposition()
                    ? ToolSalvage.plan(registry, input).orElse(null)
                    : (input.getComponentsPatch().isEmpty() ? java.util.Optional.ofNullable(MachineOperations.profile(registry, input))
                            .map(p -> new ToolSalvage.Plan(p, p.matter(), 0, 0)).orElse(null) : null);
            if (salvage != null) {
                var profile = salvage.profile();
                String id = MachineOperations.itemId(input);
                boolean inspect = kind == MachineKind.ANALYZER;
                Map<SubstanceKey, Long> outputs = inspect && !machine.analysis().matches(id, true, profile) ? Map.of() : salvage.matter();
                return preview(machine, registry, store, MachineSnapshot.ITEM_SOURCE + id, Map.of(), outputs,
                        inspect&&machine.knownMaterial(id)?0:machine.cost(inspect?dev.latrox.rematter.research.ResearchService.analysisEnergy(profile):profile.decompositionEnergy()),
                        inspect?(machine.knownMaterial(id)?0:dev.latrox.rematter.research.ResearchService.analysisTicks(profile)):profile.ticks(),
                        inspect ? "analysis" : "matter", inspect ? id : "", inspect ? 1 : 0);
            }
            if (kind.decomposition()) {
                FluidResource fluid = machine.fluids().getResource(0);
                var conversion = registry.fluidProfile(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()).orElse(null);
                if (conversion != null && !fluid.isEmpty() && fluid.isComponentsPatchEmpty()) {
                    return preview(machine, registry, store, MachineChoices.fluidChoiceId(conversion.fluidId()), Map.of(), conversion.matter(),
                            machine.cost(conversion.decompositionEnergy()), conversion.ticks(), "matter", "", 0);
                }
            }
            return MachineSnapshot.Preview.EMPTY;
        }
        if (kind == MachineKind.PRIMAL_REACTOR) {
            var choice = machine.selectedChoice();
            if (choice == null) return MachineSnapshot.Preview.EMPTY;
            int stability = machine.extensions().primalStability();
            int effSpeed = PrimalReactorRecipes.effectiveSpeed(machine.speed(), stability);
            var tier = PrimalReactorRecipes.condensation(choice.id(), registry).orElse(null);
            if (tier != null) {
                boolean compressed = machine.hasMassCompression();
                var in = tier.effectiveInputs(compressed);
                var inRows = amounts(registry, store, in);
                long outUr = PrimalReactorRecipes.condensationOutput(tier.primalOutput(), stability);
                var outRows = List.of(new MachineSnapshot.Amount(
                        dev.latrox.rematter.chemistry.ReservedSubstances.PRIMAL_MATTER.toString(), "solid", outUr, 0L, true));
                return new MachineSnapshot.Preview(tier.id(), inRows, outRows,
                        inRows.size() == in.size(), machine.cost(tier.effectiveEnergy(compressed)),
                        (int) (((long) tier.ticks() + effSpeed - 1) / effSpeed),
                        "matter", "", 0, List.of());
            }
            var manifest = PrimalReactorRecipes.manifestation(choice.id(), registry).orElse(null);
            if (manifest != null) {
                long primalAvail = machine.extensions().availablePrimalTotal();
                var inRows = List.of(new MachineSnapshot.Amount(
                        dev.latrox.rematter.chemistry.ReservedSubstances.PRIMAL_MATTER.toString(), "solid", manifest.primalCost(), primalAvail, true));
                var outRows = amounts(registry, store, Map.of(manifest.target(), manifest.outputAmount()));
                return new MachineSnapshot.Preview(manifest.id(), inRows, outRows,
                        true, machine.cost(manifest.energy()),
                        (int) (((long) manifest.ticks() + effSpeed - 1) / effSpeed),
                        "matter", "", 0, List.of());
            }
            return MachineSnapshot.Preview.EMPTY;
        }
        if (!machine.supportsBatchControl()) return MachineSnapshot.Preview.EMPTY;
        var choice = machine.selectedChoice();
        if (choice == null) return MachineSnapshot.Preview.EMPTY;
        var operation = operations.previewOperation(registry);
        if (operation == null) return MachineSnapshot.Preview.EMPTY;
        String outputKind = "matter";
        String outputId = "";
        int outputCount = 0;
        if (kind.synthesis()) {
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
                    store == null ? 0 : store.amount(key), registry.isKnown(key)));
        }
        return List.copyOf(rows);
    }

    private static String phase(SubstanceKey key) { return key.phase().name().toLowerCase(Locale.ROOT); }

    private static String fluidId(FluidResource fluid) {
        return fluid.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString();
    }
}
