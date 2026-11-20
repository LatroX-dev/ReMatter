package dev.latrox.rematter.network;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.content.LabGoalMaterials;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineMenu;
import dev.latrox.rematter.content.MachineSnapshot;
import dev.latrox.rematter.content.TerminalBatchPlanner;
import dev.latrox.rematter.content.TerminalJobCoordinator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-to-client multi-step batch plan preview and active terminal job status snapshot. */
public record TerminalBatchPlanPayload(
        int containerId,
        TerminalBatchPlanner.TargetKind targetKind,
        String targetId,
        String phase,
        int batches,
        TerminalBatchPlanner.Status status,
        List<TerminalBatchPlanner.PlanStep> steps,
        List<MissingRow> missingInputs,
        List<MachineKind> missingMachines,
        long totalEnergy,
        List<TerminalJobCoordinator.JobSummary> activeJobs,
        boolean targetResearchMissing, String researchSample, List<LabGoalMaterials.Row> materials, int materialCount) implements CustomPacketPayload {

    public record MissingRow(String substanceId, String phase, long missing, long available) {}

    public static final int MAX_MATERIAL_ROWS = 128;
    public static final int MAX_MISSING_ROWS = 24;
    public static final TerminalBatchPlanPayload EMPTY = new TerminalBatchPlanPayload(
            -1, TerminalBatchPlanner.TargetKind.SUBSTANCE, "", "", 0, TerminalBatchPlanner.Status.FORBIDDEN_TARGET, List.of(), List.of(), List.of(), 0L, List.of(), false, "", List.of(), 0);

    public TerminalBatchPlanPayload {
        steps = List.copyOf(steps);
        missingInputs = List.copyOf(missingInputs);
        missingMachines = List.copyOf(missingMachines);
        activeJobs = List.copyOf(activeJobs);
        materials = List.copyOf(materials);
        if (materialCount < 0 || materials.size() != Math.min(materialCount, MAX_MATERIAL_ROWS))
            throw new IllegalArgumentException("Material list and row count disagree");
    }

    public static final Type<TerminalBatchPlanPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "terminal_batch_plan"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalBatchPlanPayload> CODEC = new StreamCodec<>() {
        @Override
        public TerminalBatchPlanPayload decode(RegistryFriendlyByteBuf buf) {
            int cid = buf.readVarInt();
            var kind = buf.readEnum(TerminalBatchPlanner.TargetKind.class);
            String tid = buf.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH);
            String ph = buf.readUtf(TerminalBatchRequestPayload.MAX_PHASE_LEN);
            int reqBatches = buf.readVarInt();
            TerminalBatchPlanner.Status st = buf.readEnum(TerminalBatchPlanner.Status.class);
            int stepCount = Math.min(TerminalBatchPlanner.MAX_STEPS, Math.max(0, buf.readVarInt()));
            List<TerminalBatchPlanner.PlanStep> steps = new ArrayList<>(stepCount);
            for (int i = 0; i < stepCount; i++) {
                steps.add(new TerminalBatchPlanner.PlanStep(
                        buf.readEnum(TerminalBatchPlanner.StepKind.class),
                        buf.readEnum(MachineKind.class),
                        buf.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH),
                        buf.readVarInt(),
                        buf.readVarLong()));
            }
            int missCount = Math.min(MAX_MISSING_ROWS, Math.max(0, buf.readVarInt()));
            List<MissingRow> missing = new ArrayList<>(missCount);
            for (int i = 0; i < missCount; i++) {
                missing.add(new MissingRow(
                        buf.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH),
                        buf.readUtf(TerminalBatchRequestPayload.MAX_PHASE_LEN),
                        buf.readVarLong(),
                        buf.readVarLong()));
            }
            int machCount = Math.min(MachineKind.values().length, Math.max(0, buf.readVarInt()));
            List<MachineKind> missingMachines = new ArrayList<>(machCount);
            for (int i = 0; i < machCount; i++) {
                missingMachines.add(buf.readEnum(MachineKind.class));
            }
            long energy = buf.readVarLong();
            int jobCount = Math.min(TerminalJobCoordinator.MAX_ACTIVE_JOBS_PER_OWNER, Math.max(0, buf.readVarInt()));
            List<TerminalJobCoordinator.JobSummary> jobs = new ArrayList<>(jobCount);
            for (int i = 0; i < jobCount; i++) {
                jobs.add(new TerminalJobCoordinator.JobSummary(
                        buf.readVarInt(),
                        buf.readEnum(TerminalBatchPlanner.TargetKind.class),
                        buf.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH),
                        buf.readUtf(TerminalBatchRequestPayload.MAX_PHASE_LEN),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readUtf(64)));
            }
            boolean targetResearchMissing = buf.readBoolean();
            String sample = buf.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH);
            int materialCount = buf.readVarInt();
            if (materialCount < 0) throw new IllegalArgumentException("Invalid material count");
            List<LabGoalMaterials.Row> materials = new ArrayList<>();
            for (int i = 0; i < Math.min(materialCount, MAX_MATERIAL_ROWS); i++) {
                materials.add(new LabGoalMaterials.Row(buf.readUtf(MachineSnapshot.MAX_CHOICE_ID_LENGTH),
                        buf.readUtf(TerminalBatchRequestPayload.MAX_PHASE_LEN), buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
            }
            return new TerminalBatchPlanPayload(cid, kind, tid, ph, reqBatches, st, steps, missing, missingMachines, energy, jobs, targetResearchMissing, sample, materials, materialCount);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, TerminalBatchPlanPayload p) {
            buf.writeVarInt(p.containerId());
            buf.writeEnum(p.targetKind());
            buf.writeUtf(p.targetId(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
            buf.writeUtf(p.phase(), TerminalBatchRequestPayload.MAX_PHASE_LEN);
            buf.writeVarInt(p.batches());
            buf.writeEnum(p.status());
            int stepCount = Math.min(TerminalBatchPlanner.MAX_STEPS, p.steps().size());
            buf.writeVarInt(stepCount);
            for (int i = 0; i < stepCount; i++) {
                var s = p.steps().get(i);
                buf.writeEnum(s.kind());
                buf.writeEnum(s.preferredMachine());
                buf.writeUtf(s.choiceId(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
                buf.writeVarInt(s.batches());
                buf.writeVarLong(s.energyPerBatch());
            }
            int missCount = Math.min(MAX_MISSING_ROWS, p.missingInputs().size());
            buf.writeVarInt(missCount);
            for (int i = 0; i < missCount; i++) {
                var m = p.missingInputs().get(i);
                buf.writeUtf(m.substanceId(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
                buf.writeUtf(m.phase(), TerminalBatchRequestPayload.MAX_PHASE_LEN);
                buf.writeVarLong(m.missing());
                buf.writeVarLong(m.available());
            }
            int machCount = Math.min(MachineKind.values().length, p.missingMachines().size());
            buf.writeVarInt(machCount);
            for (int i = 0; i < machCount; i++) {
                buf.writeEnum(p.missingMachines().get(i));
            }
            buf.writeVarLong(p.totalEnergy());
            int jobCount = Math.min(TerminalJobCoordinator.MAX_ACTIVE_JOBS_PER_OWNER, p.activeJobs().size());
            buf.writeVarInt(jobCount);
            for (int i = 0; i < jobCount; i++) {
                var j = p.activeJobs().get(i);
                buf.writeVarInt(j.jobId());
                buf.writeEnum(j.targetKind());
                buf.writeUtf(j.targetId(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
                buf.writeUtf(j.phase(), TerminalBatchRequestPayload.MAX_PHASE_LEN);
                buf.writeVarInt(j.requestedBatches());
                buf.writeVarInt(j.completedSteps());
                buf.writeVarInt(j.totalSteps());
                buf.writeVarInt(j.completedBatches());
                buf.writeVarInt(j.totalBatches());
                buf.writeUtf(j.status(), 64);
            }
            buf.writeBoolean(p.targetResearchMissing());
            buf.writeUtf(p.researchSample(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
            buf.writeVarInt(p.materialCount());
            for (var row : p.materials()) {
                buf.writeUtf(row.substanceId(), MachineSnapshot.MAX_CHOICE_ID_LENGTH);
                buf.writeUtf(row.phase(), TerminalBatchRequestPayload.MAX_PHASE_LEN);
                buf.writeVarLong(row.required());
                buf.writeVarLong(row.available());
                buf.writeVarLong(row.remainder());
            }
        }
    };

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3").playToClient(TYPE, CODEC, (payload, context) -> {
            if (context.player() != null
                    && context.player().containerMenu instanceof MachineMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.acceptTerminalBatchPlan(payload);
            }
        });
    }

    @Override
    public Type<TerminalBatchPlanPayload> type() {
        return TYPE;
    }
}
