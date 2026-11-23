package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.EnvironmentSource;
import dev.latrox.rematter.chemistry.FluidProfile;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.MatterNetworkInventory;
import dev.latrox.rematter.chemistry.ProcessRecipe;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.research.ResearchConfig;
import dev.latrox.rematter.research.ResearchService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Live, non-escrowing multi-step batch dispatcher for Matter Terminals (wired and wireless).
 * Holds zero resources; every step queues finite batches directly onto online network workers
 * as upstream intermediates arrive in network storage.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class TerminalJobCoordinator {
    public static final int MAX_ACTIVE_JOBS_PER_OWNER = 8;
    private static final int TICK_INTERVAL = 5;

    private static final Map<MinecraftServer, Map<UUID, List<ActiveJob>>> JOBS = new IdentityHashMap<>();
    private static final AtomicInteger NEXT_JOB_ID = new AtomicInteger(1);

    public record JobSummary(
            int jobId,
            TerminalBatchPlanner.TargetKind targetKind,
            String targetId,
            String phase,
            int requestedBatches,
            int completedSteps,
            int totalSteps,
            int completedBatches,
            int totalBatches,
            String status) {}

    /** A live lease for one step. Only an authoritative, paid worker commit advances it. */
    static final class StepRuntime {
        final ActiveJob job;
        final TerminalBatchPlanner.PlanStep step;
        int completedBatches;
        @Nullable MachineBlockEntity worker;

        StepRuntime(ActiveJob job, TerminalBatchPlanner.PlanStep step) {
            this.job = job;
            this.step = step;
        }

        boolean done() { return completedBatches == step.batches(); }

        boolean connectedTo(MachineBlockEntity candidate) {
            if (worker != candidate || !job.level.isLoaded(job.anchorPos)) return false;
            var network = resolveAnchorNetwork(job.level, job.anchorPos, job.owner);
            return network != null && network.ready() && network.hasWorker(candidate);
        }

        void release() {
            if (worker != null) worker.releaseTerminalStep(this);
        }

        boolean cancelParentJob() {
            return TerminalJobCoordinator.cancel(job.level, job.owner, job.jobId);
        }
    }

    private static final class ActiveJob {
        final int jobId;
        final ServerLevel level;
        final BlockPos anchorPos;
        final UUID owner;
        final TerminalBatchPlanner.TargetKind targetKind;
        final String targetId;
        final String phase;
        final int requestedBatches;
        final List<StepRuntime> steps;
        String status = "working";

        ActiveJob(
                int jobId,
                ServerLevel level,
                BlockPos anchorPos,
                UUID owner,
                TerminalBatchPlanner.TargetKind targetKind,
                String targetId,
                String phase,
                int requestedBatches,
                List<TerminalBatchPlanner.PlanStep> plannedSteps) {
            this.jobId = jobId;
            this.level = level;
            this.anchorPos = anchorPos.immutable();
            this.owner = owner;
            this.targetKind = targetKind;
            this.targetId = targetId;
            this.phase = phase;
            this.requestedBatches = requestedBatches;
            this.steps = plannedSteps.stream().map(step -> new StepRuntime(this, step)).toList();
        }

        JobSummary summary() {
            int doneSteps = 0;
            int doneBatches = 0;
            int totalBatches = 0;
            for (StepRuntime s : steps) {
                if (s.done()) doneSteps++;
                doneBatches += Math.min(s.completedBatches, s.step.batches());
                totalBatches += s.step.batches();
            }
            return new JobSummary(
                    jobId,
                    targetKind,
                    targetId,
                    phase,
                    requestedBatches,
                    doneSteps,
                    steps.size(),
                    doneBatches,
                    totalBatches,
                    status);
        }
    }

    private TerminalJobCoordinator() {}

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        var jobs = JOBS.remove(event.getServer());
        if (jobs != null) jobs.values().forEach(list -> list.forEach(job -> job.steps.forEach(StepRuntime::release)));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % TICK_INTERVAL != 0) return;
        tickServer(server);
    }

    public static void tickServer(MinecraftServer server) {
        var byOwner = JOBS.get(server);
        if (byOwner == null || byOwner.isEmpty()) return;
        ChemistryRegistry chemistry = ReMatter.chemistry();
        Iterator<Map.Entry<UUID, List<ActiveJob>>> ownerIt = byOwner.entrySet().iterator();
        while (ownerIt.hasNext()) {
            List<ActiveJob> list = ownerIt.next().getValue();
            list.removeIf(job -> tickJob(job, chemistry));
            if (list.isEmpty()) ownerIt.remove();
        }
    }

    public static TerminalBatchPlanner.NetworkContext buildNetworkContext(
            ServerLevel level, UUID owner, @Nullable ServerPlayer player, MachineMatterNetwork network) {
        MinecraftServer server = level.getServer();
        Map<SubstanceKey, Long> stock = new HashMap<>();
        MatterNetworkInventory inv = network.ready() ? network.inventory() : null;
        if (inv != null) {
            inv.contents().forEach(stock::put);
        }
        Set<MachineKind> onlineKinds = EnumSet.noneOf(MachineKind.class);
        Set<MachineKind> skyKinds = EnumSet.noneOf(MachineKind.class);
        int maxField = 0;
        int maxPressure = 1;
        for (MachineBlockEntity worker : network.workers()) {
            if (!worker.supportsBatchControl() || worker.networkJobs() || !worker.industrialStructureReady()) {
                continue;
            }
            if (!worker.redstoneAllowsWork()) continue;
            onlineKinds.add(worker.kind());
            if (worker.hasOpenAirAccess()) skyKinds.add(worker.kind());
            if (worker.kind() == MachineKind.INDUSTRIAL_REACTOR) {
                maxField = Math.max(maxField, IndustrialStructure.inspect(worker).performance().field());
            }
            if (worker.kind().synthesis()) {
                maxPressure = Math.max(maxPressure, worker.crystallizationTier());
            }
        }
        boolean researchOn = ResearchConfig.enabled();
        Set<SubstanceKey> knownSubs = researchOn && ResearchService.available(server)
                ? ResearchService.knownSubstances(server, owner)
                : Set.of();
        return new TerminalBatchPlanner.NetworkContext(
                stock,
                onlineKinds,
                maxField,
                maxPressure,
                level.dimension().identifier().toString(),
                skyKinds,
                id -> !researchOn || (ResearchService.available(server) && ResearchService.allowsSynthesis(server, owner, id)),
                key -> !researchOn || knownSubs.contains(key),
                adv -> hasAdvancement(server, owner, player, adv));
    }

    public static int submit(
            ServerLevel level,
            BlockPos anchorPos,
            UUID owner,
            TerminalBatchPlanner.TargetKind targetKind,
            String targetId,
            String phase,
            int requestedBatches,
            TerminalBatchPlanner.PlanResult plan) {
        if (!plan.executable()) return -1;
        var byOwner = JOBS.computeIfAbsent(level.getServer(), ignored -> new HashMap<>());
        List<ActiveJob> list = byOwner.computeIfAbsent(owner, ignored -> new ArrayList<>());
        if (list.size() >= MAX_ACTIVE_JOBS_PER_OWNER) return -1;
        int id = NEXT_JOB_ID.getAndIncrement();
        ActiveJob job = new ActiveJob(id, level, anchorPos, owner, targetKind, targetId, phase, requestedBatches, plan.steps());
        list.add(job);
        tickJob(job, ReMatter.chemistry());
        return id;
    }

    public static boolean cancel(ServerLevel level, UUID owner, int jobId) {
        var byOwner = JOBS.get(level.getServer());
        if (byOwner == null) return false;
        List<ActiveJob> list = byOwner.get(owner);
        if (list == null) return false;
        for (Iterator<ActiveJob> it = list.iterator(); it.hasNext(); ) {
            ActiveJob job = it.next();
            if (job.jobId == jobId) {
                job.steps.forEach(StepRuntime::release);
                it.remove();
                return true;
            }
        }
        return false;
    }

    public static List<JobSummary> jobsFor(ServerLevel level, UUID owner) {
        var byOwner = JOBS.get(level.getServer());
        if (byOwner == null) return List.of();
        List<ActiveJob> list = byOwner.get(owner);
        if (list == null || list.isEmpty()) return List.of();
        return list.stream().map(ActiveJob::summary).toList();
    }

    private static boolean tickJob(ActiveJob job, ChemistryRegistry chemistry) {
        if (job.steps.stream().allMatch(StepRuntime::done)) return true;
        if (!job.level.isLoaded(job.anchorPos)) {
            job.steps.forEach(StepRuntime::release);
            job.status = "network_unloaded";
            return false;
        }
        MachineMatterNetwork network = resolveAnchorNetwork(job.level, job.anchorPos, job.owner);
        if (network == null || !network.ready() || network.inventory() == null) {
            job.steps.forEach(StepRuntime::release);
            job.status = network == null ? "network_invalid" : network.status();
            return false;
        }
        MatterNetworkInventory inventory = network.inventory();

        boolean anyInFlight = false;
        String blockedReason = null;
        for (StepRuntime rt : job.steps) {
            if (rt.worker != null && !network.hasWorker(rt.worker)) rt.release();
            if (rt.done()) continue;
            if (rt.worker != null) {
                anyInFlight = true;
                continue;
            }

            int remainingToDispatch = rt.step.batches() - rt.completedBatches;
            int affordable = affordableBatches(chemistry, inventory, rt.step, remainingToDispatch);
            if (affordable <= 0) {
                if (blockedReason == null) blockedReason = "waiting_inputs";
                continue;
            }
            MachineBlockEntity worker = selectWorker(network, chemistry, rt);
            if (worker == null) {
                if (blockedReason == null) blockedReason = "waiting_worker";
                continue;
            }
            if (worker.dispatchTerminalBatches(rt, affordable)) anyInFlight = true;
        }
        job.status = anyInFlight ? "working" : (blockedReason != null ? blockedReason : "working");
        return false;
    }

    private static int affordableBatches(
            ChemistryRegistry chemistry,
            MatterNetworkInventory inventory,
            TerminalBatchPlanner.PlanStep step,
            int maxBatches) {
        Map<SubstanceKey, Long> inputs = switch (step.kind()) {
            case ENVIRONMENT -> Map.of();
            case PROCESS -> chemistry.recipe(SubstanceId.parse(step.choiceId()))
                    .map(ProcessRecipe::inputs)
                    .orElse(null);
            case SYNTHESIS_ITEM -> chemistry.profile(step.choiceId())
                    .map(MaterialProfile::matter)
                    .orElse(null);
            case SYNTHESIS_FLUID -> {
                String fluidId = step.choiceId().startsWith(MachineChoices.FLUID_PREFIX)
                        ? step.choiceId().substring(MachineChoices.FLUID_PREFIX.length())
                        : step.choiceId();
                yield chemistry.fluidProfile(fluidId).map(FluidProfile::matter).orElse(null);
            }
        };
        if (inputs == null) return 0;
        if (inputs.isEmpty()) return maxBatches;
        long limit = maxBatches;
        for (var entry : inputs.entrySet()) {
            long avail = inventory.amount(entry.getKey());
            long possible = avail / entry.getValue();
            limit = Math.min(limit, possible);
            if (limit <= 0) return 0;
        }
        return (int) limit;
    }

    private static @Nullable MachineBlockEntity selectWorker(
            MachineMatterNetwork network, ChemistryRegistry chemistry, StepRuntime rt) {
        MachineBlockEntity fallback = null;
        for (MachineBlockEntity worker : network.workers()) {
            if (worker.queuedBatchCount() != 0 || !canRunStep(worker, chemistry, rt.step)) continue;
            if (worker.selectedId().equals(rt.step.choiceId())) return worker;
            if (fallback == null || worker.kind() == rt.step.preferredMachine()) {
                fallback = worker;
            }
        }
        return fallback;
    }

    private static boolean canRunStep(
            MachineBlockEntity worker, ChemistryRegistry chemistry, TerminalBatchPlanner.PlanStep step) {
        if (!worker.supportsBatchControl() || worker.networkJobs() || !worker.industrialStructureReady()) {
            return false;
        }
        if (!worker.redstoneAllowsWork() || worker.runState().repeat()) return false;
        return switch (step.kind()) {
            case ENVIRONMENT -> {
                if (!MachineRouting.environmentSources(worker.kind())) yield false;
                EnvironmentSource source = chemistry.environmentSource(SubstanceId.parse(step.choiceId())).orElse(null);
                yield source != null && (!source.requiresOpenAir() || worker.hasOpenAirAccess());
            }
            case PROCESS -> {
                ProcessRecipe recipe = chemistry.recipe(SubstanceId.parse(step.choiceId())).orElse(null);
                yield recipe != null && MachineRouting.supports(worker.kind(), recipe) && worker.allowsProcess(recipe);
            }
            case SYNTHESIS_ITEM -> worker.kind().synthesis()
                    && chemistry.synthesisPolicy().requirement(step.choiceId()).tier() <= worker.crystallizationTier();
            case SYNTHESIS_FLUID -> {
                if (!worker.kind().synthesis()) yield false;
                String fluidId = step.choiceId().startsWith(MachineChoices.FLUID_PREFIX)
                        ? step.choiceId().substring(MachineChoices.FLUID_PREFIX.length())
                        : step.choiceId();
                yield chemistry.synthesisPolicy().requirement(fluidId).tier() <= worker.crystallizationTier();
            }
        };
    }

    private static @Nullable MachineMatterNetwork resolveAnchorNetwork(
            ServerLevel level, BlockPos pos, UUID owner) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MachineBlockEntity terminal
                && terminal.kind() == MachineKind.TERMINAL
                && terminal.networkLive()
                && Objects.equals(terminal.networkOwner(), owner)) {
            return terminal.matterNetwork(false);
        }
        if (be instanceof WirelessAccessPointBlockEntity ap
                && ap.networkLive()
                && ap.powered()
                && Objects.equals(ap.owner(), owner)) {
            return ap.matterNetwork(false);
        }
        return null;
    }

    private static boolean hasAdvancement(
            MinecraftServer server, UUID owner, @Nullable ServerPlayer onlinePlayer, String advId) {
        if (advId.isEmpty()) return true;
        ServerPlayer target = onlinePlayer != null ? onlinePlayer : server.getPlayerList().getPlayer(owner);
        if (target == null) return false;
        Identifier id = Identifier.tryParse(advId);
        if (id == null) return false;
        AdvancementHolder holder = server.getAdvancements().get(id);
        return holder != null && target.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
