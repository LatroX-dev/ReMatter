package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.EnvironmentSource;
import dev.latrox.rematter.chemistry.FluidProfile;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ProcessRecipe;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.chemistry.SynthesisRequirement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Pure, deterministic, cycle-safe multi-step batch planner for Matter Terminals.
 * Strictly restricted to chemistry processes, environment sources, and explicitly allowlisted
 * raw/natural materials and fluids — never manufactured items, tools, or inferred recipes.
 */
public final class TerminalBatchPlanner {
    public static final int MAX_DEPTH = 8;
    public static final int MAX_REQUEST_BATCHES = 256;
    public static final int MAX_STEP_BATCHES = 1_024;
    public static final int MAX_STEPS = 16;

    public enum TargetKind {
        SUBSTANCE,
        SYNTHESIS_ITEM,
        SYNTHESIS_FLUID
    }

    public enum StepKind {
        ENVIRONMENT,
        PROCESS,
        SYNTHESIS_ITEM,
        SYNTHESIS_FLUID
    }

    public enum Status {
        READY,
        MISSING_INPUTS,
        MISSING_MACHINES,
        REQUIRES_RESEARCH,
        REQUIRES_PRESSURE,
        REQUIRES_FIELD,
        REQUIRES_ADVANCEMENT,
        FORBIDDEN_TARGET,
        BATCH_LIMIT_EXCEEDED,
        REQUIRES_OPEN_AIR
    }

    public record CatalogTarget(
            TargetKind kind,
            String id,
            String phase,
            int outputPerBatch,
            boolean unlocked) {
        public CatalogTarget {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(phase, "phase");
        }
    }

    public record PlanStep(
            StepKind kind,
            MachineKind preferredMachine,
            String choiceId,
            int batches,
            long energyPerBatch) {
        public PlanStep {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(preferredMachine, "preferredMachine");
            Objects.requireNonNull(choiceId, "choiceId");
            if (batches <= 0 || batches > MAX_STEP_BATCHES || energyPerBatch < 0) {
                throw new IllegalArgumentException("Invalid planned step bounds");
            }
        }
    }

    public record MissingSubstance(SubstanceKey key, long missing, long required, long available) {}

    public record NetworkContext(
            Map<SubstanceKey, Long> stock,
            Set<MachineKind> onlineKinds,
            int maxFieldCoils,
            int maxPressureTier,
            String dimensionId,
            Set<MachineKind> skyCapableKinds,
            Predicate<String> allowsSynthesis,
            Predicate<SubstanceKey> knowsSubstance,
            Predicate<String> hasAdvancement) {
        public NetworkContext {
            stock = Map.copyOf(stock);
            onlineKinds = onlineKinds.isEmpty() ? Set.of() : EnumSet.copyOf(onlineKinds);
            skyCapableKinds = skyCapableKinds.isEmpty() ? Set.of() : EnumSet.copyOf(skyCapableKinds);
            Objects.requireNonNull(dimensionId, "dimensionId");
            Objects.requireNonNull(allowsSynthesis, "allowsSynthesis");
            Objects.requireNonNull(knowsSubstance, "knowsSubstance");
            Objects.requireNonNull(hasAdvancement, "hasAdvancement");
        }
    }

    public record PlanResult(
            Status status,
            List<PlanStep> steps,
            List<MissingSubstance> missingInputs,
            List<MachineKind> missingMachines,
            long totalEnergy,
            int totalBatches) {
        public PlanResult {
            Objects.requireNonNull(status, "status");
            steps = List.copyOf(steps);
            missingInputs = List.copyOf(missingInputs);
            missingMachines = List.copyOf(missingMachines);
        }

        public boolean executable() {
            return status == Status.READY && !steps.isEmpty();
        }
    }

    private TerminalBatchPlanner() {}

    /** Lists all valid requestable targets (craftable substances + explicitly allowlisted synthesis outputs). */
    public static List<CatalogTarget> listTargets(
            ChemistryRegistry registry,
            Predicate<String> itemExists,
            Predicate<String> fluidExists,
            Predicate<String> allowsSynthesis,
            Predicate<SubstanceKey> knowsSubstance,
            boolean researchEnabled) {
        List<CatalogTarget> targets = new ArrayList<>();
        Set<SubstanceKey> producibleKeys = new HashSet<>();
        for (ProcessRecipe recipe : registry.processes()) {
            producibleKeys.addAll(recipe.outputs().keySet());
        }
        for (EnvironmentSource source : registry.environmentSources()) {
            producibleKeys.addAll(source.outputs().keySet());
        }
        for (SubstanceKey key : new java.util.TreeSet<>(producibleKeys)) {
            if (ReservedSubstances.reserved(key)) continue;
            boolean unlocked = !researchEnabled || knowsSubstance.test(key);
            targets.add(new CatalogTarget(
                    TargetKind.SUBSTANCE,
                    key.id().toString(),
                    key.phase().name().toLowerCase(Locale.ROOT),
                    1,
                    unlocked));
        }
        for (MaterialProfile profile : registry.profiles()) {
            String itemId = profile.itemId();
            if (!registry.synthesisPolicy().allowsItem(itemId) || !itemExists.test(itemId)) continue;
            boolean unlocked = !researchEnabled || allowsSynthesis.test(itemId);
            targets.add(new CatalogTarget(
                    TargetKind.SYNTHESIS_ITEM,
                    itemId,
                    "",
                    profile.itemCount(),
                    unlocked));
        }
        for (FluidProfile profile : registry.fluidProfiles()) {
            String fluidId = profile.fluidId();
            if (!registry.synthesisPolicy().allowsFluid(fluidId) || !fluidExists.test(fluidId)) continue;
            boolean unlocked = !researchEnabled || allowsSynthesis.test(fluidId);
            targets.add(new CatalogTarget(
                    TargetKind.SYNTHESIS_FLUID,
                    fluidId,
                    "",
                    profile.amount(),
                    unlocked));
        }
        return List.copyOf(targets);
    }

    public static PlanResult plan(
            ChemistryRegistry registry,
            TargetKind kind,
            String targetId,
            String phaseName,
            int requestedBatches,
            NetworkContext ctx) {
        if (requestedBatches <= 0 || requestedBatches > MAX_REQUEST_BATCHES) {
            return empty(Status.BATCH_LIMIT_EXCEEDED);
        }
        return switch (kind) {
            case SYNTHESIS_ITEM -> planSynthesisItem(registry, targetId, requestedBatches, ctx);
            case SYNTHESIS_FLUID -> planSynthesisFluid(registry, targetId, requestedBatches, ctx);
            case SUBSTANCE -> planSubstance(registry, targetId, phaseName, requestedBatches, ctx);
        };
    }

    private static PlanResult planSynthesisItem(
            ChemistryRegistry registry, String itemId, int batches, NetworkContext ctx) {
        if (!registry.synthesisPolicy().allowsItem(itemId)) {
            return empty(Status.FORBIDDEN_TARGET);
        }
        MaterialProfile profile = registry.profile(itemId).orElse(null);
        if (profile == null) return empty(Status.FORBIDDEN_TARGET);
        if (!ctx.allowsSynthesis().test(itemId)) return empty(Status.REQUIRES_RESEARCH);
        SynthesisRequirement req = registry.synthesisPolicy().requirement(itemId);
        if (!req.advancement().isEmpty() && !ctx.hasAdvancement().test(req.advancement())) {
            return empty(Status.REQUIRES_ADVANCEMENT);
        }
        if (req.tier() > ctx.maxPressureTier() && ctx.onlineKinds().contains(MachineKind.SYNTHESIZER)) {
            return empty(Status.REQUIRES_PRESSURE);
        }

        PlannerState state = new PlannerState(registry, ctx);
        for (var entry : profile.matter().entrySet()) {
            long needed = Math.multiplyExact(entry.getValue(), (long) batches);
            if (!state.satisfy(entry.getKey(), needed, new HashSet<>())) {
                return empty(Status.BATCH_LIMIT_EXCEEDED);
            }
        }
        if (!ctx.onlineKinds().contains(MachineKind.SYNTHESIZER)) {
            state.missingMachines.add(MachineKind.SYNTHESIZER);
        } else if (req.tier() > ctx.maxPressureTier()) {
            return empty(Status.REQUIRES_PRESSURE);
        }
        state.appendStep(new PlanStep(
                StepKind.SYNTHESIS_ITEM,
                MachineKind.SYNTHESIZER,
                itemId,
                batches,
                profile.synthesisEnergy()));
        return state.finish();
    }

    private static PlanResult planSynthesisFluid(
            ChemistryRegistry registry, String fluidId, int batches, NetworkContext ctx) {
        if (!registry.synthesisPolicy().allowsFluid(fluidId)) {
            return empty(Status.FORBIDDEN_TARGET);
        }
        FluidProfile profile = registry.fluidProfile(fluidId).orElse(null);
        if (profile == null) return empty(Status.FORBIDDEN_TARGET);
        if (!ctx.allowsSynthesis().test(fluidId)) return empty(Status.REQUIRES_RESEARCH);
        SynthesisRequirement req = registry.synthesisPolicy().requirement(fluidId);
        if (!req.advancement().isEmpty() && !ctx.hasAdvancement().test(req.advancement())) {
            return empty(Status.REQUIRES_ADVANCEMENT);
        }
        if (req.tier() > ctx.maxPressureTier() && ctx.onlineKinds().contains(MachineKind.SYNTHESIZER)) {
            return empty(Status.REQUIRES_PRESSURE);
        }

        PlannerState state = new PlannerState(registry, ctx);
        for (var entry : profile.matter().entrySet()) {
            long needed = Math.multiplyExact(entry.getValue(), (long) batches);
            if (!state.satisfy(entry.getKey(), needed, new HashSet<>())) {
                return empty(Status.BATCH_LIMIT_EXCEEDED);
            }
        }
        if (!ctx.onlineKinds().contains(MachineKind.SYNTHESIZER)) {
            state.missingMachines.add(MachineKind.SYNTHESIZER);
        } else if (req.tier() > ctx.maxPressureTier()) {
            return empty(Status.REQUIRES_PRESSURE);
        }
        state.appendStep(new PlanStep(
                StepKind.SYNTHESIS_FLUID,
                MachineKind.SYNTHESIZER,
                MachineChoices.fluidChoiceId(fluidId),
                batches,
                profile.synthesisEnergy()));
        return state.finish();
    }

    private static PlanResult planSubstance(
            ChemistryRegistry registry, String idText, String phaseText, int unitsOrBatches, NetworkContext ctx) {
        SubstanceKey targetKey;
        try {
            Phase phase = Phase.valueOf(phaseText.toUpperCase(Locale.ROOT));
            targetKey = new SubstanceKey(SubstanceId.parse(idText), phase);
        } catch (IllegalArgumentException invalid) {
            return empty(Status.FORBIDDEN_TARGET);
        }
        if (ReservedSubstances.reserved(targetKey) || !registry.isKnown(targetKey)) {
            return empty(Status.FORBIDDEN_TARGET);
        }

        if (!ctx.knowsSubstance().test(targetKey)) return empty(Status.REQUIRES_RESEARCH);

        PlannerState state = new PlannerState(registry, ctx);
        Set<SubstanceKey> visiting = new HashSet<>();
        visiting.add(targetKey);
        if (!state.produceDirectly(targetKey, unitsOrBatches, visiting)) {
            return state.specialFailure != null ? empty(state.specialFailure) : empty(Status.FORBIDDEN_TARGET);
        }
        return state.finish();
    }

    private static PlanResult empty(Status status) {
        return new PlanResult(status, List.of(), List.of(), List.of(), 0L, 0);
    }

    private static final class PlannerState {
        private final ChemistryRegistry registry;
        private final NetworkContext ctx;
        private final Map<SubstanceKey, Long> virtualStock;
        private final LinkedHashMap<String, PlanStep> mergedSteps = new LinkedHashMap<>();
        private final Map<SubstanceKey, Long> missingRaw = new TreeMap<>();
        private final Set<MachineKind> missingMachines = EnumSet.noneOf(MachineKind.class);
        private Status specialFailure;

        PlannerState(ChemistryRegistry registry, NetworkContext ctx) {
            this.registry = registry;
            this.ctx = ctx;
            this.virtualStock = new HashMap<>(ctx.stock());
        }

        boolean satisfy(SubstanceKey key, long needed, Set<SubstanceKey> visiting) {
            if (needed <= 0) return true;
            long have = virtualStock.getOrDefault(key, 0L);
            long used = Math.min(have, needed);
            if (used > 0) {
                virtualStock.put(key, have - used);
            }
            long deficit = needed - used;
            if (deficit <= 0) return true;

            if (visiting.contains(key) || visiting.size() >= MAX_DEPTH) {
                missingRaw.merge(key, deficit, Math::addExact);
                return true;
            }
            Set<SubstanceKey> nextVisiting = new HashSet<>(visiting);
            nextVisiting.add(key);
            if (!produceDirectly(key, deficit, nextVisiting)) {
                missingRaw.merge(key, deficit, Math::addExact);
            }
            return mergedSteps.size() <= MAX_STEPS && !overflowed;
        }

        private boolean overflowed;

        boolean produceDirectly(SubstanceKey key, long neededUnits, Set<SubstanceKey> visiting) {
            // Try renewable routes that a worker here can actually run before planning a chemical process.
            boolean localSource = false;
            boolean sourceExceedsLimit = false;
            if (key.phase() == Phase.GAS) {
                for (EnvironmentSource source : registry.environmentSources()) {
                    Long yield = source.outputs().get(key);
                    if (yield == null || !source.dimensions().contains(ctx.dimensionId())) continue;
                    localSource = true;
                    MachineKind worker = chooseEnvironmentMachine(source);
                    if (worker == null) continue;
                    long batches = ceilDiv(neededUnits, yield);
                    if (batches > MAX_STEP_BATCHES) {
                        sourceExceedsLimit = true;
                        continue;
                    }
                    long energy = worker == MachineKind.ATMOSPHERIC_COLLECTOR
                            ? Math.max(1L, source.energy() / 2L) : source.energy();
                    appendStep(new PlanStep(StepKind.ENVIRONMENT, worker, source.id().toString(), (int) batches, energy));
                    creditSurplus(source.outputs(), (int) batches, key, neededUnits);
                    return !overflowed;
                }
            }

            ProcessRecipe recipe = chooseProcessRecipe(key, visiting);
            if (localSource && (recipe == null || chooseProcessMachine(recipe) == null)) {
                if (sourceExceedsLimit) {
                    overflowed = true;
                    specialFailure = Status.BATCH_LIMIT_EXCEEDED;
                    return false;
                }
                if (ctx.onlineKinds().stream().anyMatch(MachineRouting::environmentSources)) {
                    specialFailure = Status.REQUIRES_OPEN_AIR;
                } else {
                    missingMachines.add(MachineKind.ATMOSPHERIC_COLLECTOR);
                }
                return true;
            }
            if (recipe == null) return false;
            long perBatch = recipe.outputs().getOrDefault(key, 0L);
            long batchesLong = ceilDiv(neededUnits, perBatch);
            if (batchesLong > MAX_STEP_BATCHES) {
                overflowed = true;
                specialFailure = Status.BATCH_LIMIT_EXCEEDED;
                return false;
            }
            int batches = (int) batchesLong;
            for (var inEntry : recipe.inputs().entrySet()) {
                long inNeeded = Math.multiplyExact(inEntry.getValue(), (long) batches);
                if (!satisfy(inEntry.getKey(), inNeeded, visiting)) {
                    overflowed = true;
                    specialFailure = Status.BATCH_LIMIT_EXCEEDED;
                    return false;
                }
            }
            MachineKind machine = chooseProcessMachine(recipe);
            if (machine == null) {
                MachineKind required = recipe.minField() > 0 ? MachineKind.INDUSTRIAL_REACTOR : MachineKind.REACTOR;
                missingMachines.add(required);
                machine = required;
            }
            appendStep(new PlanStep(StepKind.PROCESS, machine, recipe.id().toString(), batches, recipe.energy()));
            creditSurplus(recipe.outputs(), batches, key, neededUnits);
            return !overflowed;
        }

        private MachineKind chooseEnvironmentMachine(EnvironmentSource source) {
            for (MachineKind candidate : List.of(
                    MachineKind.ATMOSPHERIC_COLLECTOR, MachineKind.INDUSTRIAL_REACTOR, MachineKind.REACTOR)) {
                if (!ctx.onlineKinds().contains(candidate)) continue;
                if (source.requiresOpenAir() && !ctx.skyCapableKinds().contains(candidate)) continue;
                return candidate;
            }
            return null;
        }

        private ProcessRecipe chooseProcessRecipe(SubstanceKey target, Set<SubstanceKey> visiting) {
            List<ProcessRecipe> candidates = new ArrayList<>();
            Status blockedReason = null;
            for (ProcessRecipe recipe : registry.processes()) {
                if (!recipe.outputs().containsKey(target)) continue;
                if (recipe.inputs().keySet().stream().anyMatch(visiting::contains)) continue;
                if (!recipe.requiresAdvancement().isEmpty() && !ctx.hasAdvancement().test(recipe.requiresAdvancement())) {
                    if (blockedReason == null) blockedReason = Status.REQUIRES_ADVANCEMENT;
                    continue;
                }
                if (recipe.requiresKnownOutputs() && recipe.product() != null) {
                    boolean knownOut = registry.substances().stream()
                            .filter(def -> def.id().equals(recipe.product()))
                            .flatMap(def -> def.phases().stream().map(p -> new SubstanceKey(def.id(), p)))
                            .anyMatch(ctx.knowsSubstance());
                    if (!knownOut) {
                        if (blockedReason == null) blockedReason = Status.REQUIRES_RESEARCH;
                        continue;
                    }
                }
                if (recipe.minField() > ctx.maxFieldCoils()
                        && ctx.onlineKinds().contains(MachineKind.INDUSTRIAL_REACTOR)) {
                    blockedReason = Status.REQUIRES_FIELD;
                    continue;
                }
                candidates.add(recipe);
            }
            if (candidates.isEmpty()) {
                if (blockedReason != null && specialFailure == null) {
                    specialFailure = blockedReason;
                }
                return null;
            }
            candidates.sort(Comparator
                    .comparingInt((ProcessRecipe r) -> scoreRecipe(r))
                    .reversed()
                    .thenComparingLong(ProcessRecipe::energy)
                    .thenComparing(r -> r.id().toString()));
            return candidates.getFirst();
        }

        private int scoreRecipe(ProcessRecipe recipe) {
            int score = 0;
            if (chooseProcessMachine(recipe) != null) score += 100;
            boolean allInStock = recipe.inputs().entrySet().stream()
                    .allMatch(e -> virtualStock.getOrDefault(e.getKey(), 0L) >= e.getValue());
            if (allInStock) score += 50;
            long inStockCount = recipe.inputs().keySet().stream()
                    .filter(k -> virtualStock.getOrDefault(k, 0L) > 0)
                    .count();
            score += (int) (inStockCount * 10);
            if (recipe.minField() == 0) score += 5;
            return score;
        }

        private MachineKind chooseProcessMachine(ProcessRecipe recipe) {
            if (recipe.minField() > 0) {
                return ctx.onlineKinds().contains(MachineKind.INDUSTRIAL_REACTOR)
                        && ctx.maxFieldCoils() >= recipe.minField()
                        ? MachineKind.INDUSTRIAL_REACTOR
                        : null;
            }
            if (ctx.onlineKinds().contains(MachineKind.INDUSTRIAL_REACTOR)) {
                return MachineKind.INDUSTRIAL_REACTOR;
            }
            if (ctx.onlineKinds().contains(MachineKind.REACTOR)
                    && MachineRouting.supports(MachineKind.REACTOR, recipe)) {
                return MachineKind.REACTOR;
            }
            return null;
        }

        private void creditSurplus(
                Map<SubstanceKey, Long> outputs, int batches, SubstanceKey consumedKey, long consumedUnits) {
            for (var out : outputs.entrySet()) {
                long produced = Math.multiplyExact(out.getValue(), (long) batches);
                long leftover = out.getKey().equals(consumedKey)
                        ? Math.max(0L, produced - consumedUnits)
                        : produced;
                if (leftover > 0) {
                    virtualStock.merge(out.getKey(), leftover, Math::addExact);
                }
            }
        }

        void appendStep(PlanStep step) {
            String key = step.kind().name() + ":" + step.choiceId();
            PlanStep existing = mergedSteps.get(key);
            if (existing != null) {
                int combined = existing.batches() + step.batches();
                if (combined > MAX_STEP_BATCHES) {
                    overflowed = true;
                    specialFailure = Status.BATCH_LIMIT_EXCEEDED;
                    return;
                }
                mergedSteps.put(key, new PlanStep(
                        existing.kind(),
                        existing.preferredMachine(),
                        existing.choiceId(),
                        combined,
                        existing.energyPerBatch()));
            } else {
                if (mergedSteps.size() >= MAX_STEPS) {
                    overflowed = true;
                    specialFailure = Status.BATCH_LIMIT_EXCEEDED;
                    return;
                }
                mergedSteps.put(key, step);
            }
        }

        PlanResult finish() {
            if (overflowed) return empty(Status.BATCH_LIMIT_EXCEEDED);
            List<PlanStep> steps = List.copyOf(mergedSteps.values());
            List<MissingSubstance> missingList = new ArrayList<>();
            for (var entry : missingRaw.entrySet()) {
                long avail = ctx.stock().getOrDefault(entry.getKey(), 0L);
                missingList.add(new MissingSubstance(
                        entry.getKey(),
                        entry.getValue(),
                        Math.addExact(avail, entry.getValue()),
                        avail));
            }
            List<MachineKind> missingMachineList = List.copyOf(missingMachines);
            long totalEnergy = 0L;
            int totalBatches = 0;
            for (PlanStep step : steps) {
                totalEnergy = Math.addExact(totalEnergy, Math.multiplyExact(step.energyPerBatch(), (long) step.batches()));
                totalBatches += step.batches();
            }
            Status finalStatus = specialFailure != null
                    ? specialFailure
                    : !missingList.isEmpty()
                            ? Status.MISSING_INPUTS
                            : !missingMachineList.isEmpty()
                                    ? Status.MISSING_MACHINES
                                    : Status.READY;
            return new PlanResult(finalStatus, steps, missingList, missingMachineList, totalEnergy, totalBatches);
        }

        private static long ceilDiv(long x, long y) {
            return (x + y - 1L) / y;
        }
    }
}
