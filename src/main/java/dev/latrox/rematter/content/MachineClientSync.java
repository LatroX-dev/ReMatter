package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.content.preparation.PreparationWork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** Client-visible bay, preparation and hand-crank presentation state extracted from MachineBlockEntity. */
final class MachineClientSync {
    private boolean visibleCell;
    private int visibleCellMask, visibleCellTiers, visibleCellStatuses;
    private int publishedCellStatuses = -1;
    private boolean loadingItemVisuals;
    private int publishedPreparationStage = -1;
    private boolean publishedPreparationMixing;
    private long nextCrank, manualActiveUntil;
    private int visualCrankSequence;
    private int manualStage = -1;
    private boolean manualMixing;
    private MachineSoundLevel clientSoundLevel = MachineSoundLevel.HIGH;
    private int clientMatrixFill, clientPrimalStability, clientReactorFlux;
    private boolean clientReactorCooled;
    private int publishedMatrixFill = -1, publishedPrimalStability = -1, publishedReactorFlux = -1;
    private boolean publishedReactorCooled;

    boolean loadingItemVisuals() { return loadingItemVisuals; }
    void setLoadingItemVisuals(boolean value) { loadingItemVisuals = value; }
    int visualCrankSequence() { return visualCrankSequence; }
    int visualMatrixFill(@Nullable Level level, int serverFill) {
        return level != null && level.isClientSide() ? clientMatrixFill : serverFill;
    }
    int visualPrimalStability(@Nullable Level level, int serverStability) {
        return level != null && level.isClientSide() ? clientPrimalStability : serverStability;
    }
    int visualReactorFlux(@Nullable Level level, int serverFlux) {
        return level != null && level.isClientSide() ? clientReactorFlux : serverFlux;
    }
    boolean visualReactorCooled(@Nullable Level level, boolean serverCooled) {
        return level != null && level.isClientSide() ? clientReactorCooled : serverCooled;
    }
    MachineSoundLevel soundLevel(@Nullable Level level, MachineSoundLevel serverLevel) {
        return level != null && level.isClientSide() ? clientSoundLevel : serverLevel;
    }

    int visibleMask(@Nullable Level level, MachineCellBays bays) {
        return level != null && level.isClientSide() ? visibleCellMask : bays.visibleMask();
    }

    int visibleTier(@Nullable Level level, MachineCellBays bays, int bay) {
        if (bay < 0 || bay >= bays.count()) return 0;
        return level != null && level.isClientSide() ? MachineCellAccess.decodeTier(visibleCellTiers, bay) : bays.visibleTier(bay);
    }

    CellStatus visibleStatus(@Nullable Level level, MachineCellBays bays, int bay) {
        if (bay < 0 || bay >= bays.count()) return CellStatus.ABSENT;
        return level != null && level.isClientSide()
                ? CellStatus.decode(visibleCellStatuses >>> (bay * 3) & 7) : bays.visibleStatus(bay);
    }

    void writeUpdateTag(MachineBlockEntity machine, CompoundTag tag, MachineSideConfig sides,
            MachineVisuals visuals, MachineCellBays bays, MachineWork work, MachineStructureLink structure) {
        MachineKind kind = machine.kind();
        tag.putInt("port_visuals", sides.visuals(kind));
        if (ModSounds.workingSound(kind) != null) {
            tag.putInt("sound_volume", machine.soundLevel().level());
            tag.putBoolean("sound_muted", machine.soundMuted());
        }
        visuals.write(tag, kind);
        if (machine.cellSlot() >= 0) {
            tag.putBoolean("cell_present", machine.hasVisibleCell());
            tag.putInt("cell_mask", machine.visibleCellMask());
            tag.putInt("cell_tiers", bays.visibleTiers());
        }
        if (kind == MachineKind.DRIVE) {
            tag.putInt("cell_statuses", Transaction.getCurrentOpenedTransaction() == null
                    ? bays.visibleStatuses() : Math.max(0, publishedCellStatuses));
        }
        if (PreparationTier.isPreparation(kind)) {
            tag.putInt("preparation_stage", work.preparationStage);
            tag.putBoolean("preparation_mixing", work.preparationMixing);
        }
        if (kind == MachineKind.HAND_PREPARATOR) tag.putInt("visual_crank_sequence", visualCrankSequence);
        if (kind.isEnergyMatrix()) tag.putInt("matrix_fill", machine.extensions().matrixFillPermille());
        if (kind == MachineKind.PRIMAL_REACTOR) tag.putInt("primal_stability", machine.extensions().primalStability());
        if (kind.isEnergyReactor()) {
            tag.putInt("reactor_flux", machine.extensions().reactorFlux());
            tag.putBoolean("reactor_cooled", machine.extensions().reactorCooled());
        }
        structure.write(tag);
    }

    void readUpdateTag(MachineBlockEntity machine, ValueInput input, MachineSideConfig sides,
            MachineVisuals visuals, MachineWork work, MachineStructureLink structure) {
        MachineKind kind = machine.kind();
        Level level = machine.getLevel();
        int fallbackLevel = input.getBooleanOr("sound_muted", false) ? 0 : MachineSoundLevel.HIGH.level();
        clientSoundLevel = MachineSoundLevel.byLevel(input.getIntOr("sound_volume", fallbackLevel));
        int shownPorts = sides.visuals(kind);
        sides.readVisuals(kind, input.getIntOr("port_visuals", shownPorts));
        if (sides.visuals(kind) != shownPorts && level != null && level.isClientSide()) {
            machine.requestModelDataUpdate();
            level.sendBlockUpdated(machine.getBlockPos(), machine.getBlockState(), machine.getBlockState(), Block.UPDATE_IMMEDIATE);
        }
        visuals.read(input, kind);
        int bayCount = machine.cellBayCount();
        if (machine.cellSlot() >= 0) {
            visibleCell = input.getBooleanOr("cell_present", false);
            visibleCellMask = input.getIntOr("cell_mask", visibleCell ? 1 : 0) & ((1 << bayCount) - 1);
            visibleCellTiers = input.getIntOr("cell_tiers", visibleCell ? 1 : 0) & ((1 << (bayCount * 4)) - 1);
            visibleCellStatuses = input.getIntOr("cell_statuses", 0) & ((1 << (bayCount * 3)) - 1);
        }
        if (PreparationTier.isPreparation(kind)) {
            work.preparationStage = Math.clamp(input.getIntOr("preparation_stage", -1), -1, 2);
            work.preparationMixing = input.getBooleanOr("preparation_mixing", false);
        }
        if (kind == MachineKind.HAND_PREPARATOR) {
            visualCrankSequence = input.getIntOr("visual_crank_sequence", visualCrankSequence);
        }
        if (kind.isEnergyMatrix()) {
            clientMatrixFill = Math.clamp(input.getIntOr("matrix_fill", clientMatrixFill), 0, 1_000);
        }
        if (kind == MachineKind.PRIMAL_REACTOR) {
            clientPrimalStability = Math.clamp(input.getIntOr("primal_stability", clientPrimalStability), 0, PrimalReactorRecipes.MAX_STABILITY);
        }
        if (kind.isEnergyReactor()) {
            clientReactorFlux = Math.clamp(input.getIntOr("reactor_flux", clientReactorFlux), 0, EnergyReactorRecipes.MAX_FLUX);
            clientReactorCooled = input.getBooleanOr("reactor_cooled", clientReactorCooled);
        }
        structure.read(input);
    }

    void finishTick(MachineBlockEntity machine, MachineCellBays bays, VisualActivity activity,
            MachineStructureLink structure, boolean networkOnline) {
        MachineKind kind = machine.kind();
        Level level = machine.getLevel();
        MachineWork work = machine.work();
        if (PreparationTier.isPreparation(kind) && !work.processedThisTick) work.preparationStage = -1;
        BlockState state = machine.getBlockState();
        int cellStatuses = kind == MachineKind.DRIVE ? bays.visibleStatuses() : 0;
        if (work.processedThisTick) activity.worked(level.getGameTime());
        boolean shownActive = switch (kind) {
            case DRIVE -> CellStatus.anyReadable(cellStatuses);
            case TERMINAL, NETWORK_INTERFACE -> networkOnline;
            default -> activity.showing(level.getGameTime(), kind.generator() ? 20 : 8);
        };
        if (!machine.isRemoved() && state.getValue(MachineBlock.ACTIVE) != shownActive) {
            level.setBlock(machine.getBlockPos(), state.setValue(MachineBlock.ACTIVE, shownActive), Block.UPDATE_CLIENTS);
        }
        if (PreparationTier.isPreparation(kind)
                && (publishedPreparationStage != work.preparationStage || publishedPreparationMixing != work.preparationMixing)) {
            publishedPreparationStage = work.preparationStage;
            publishedPreparationMixing = work.preparationMixing;
            machine.sendClientUpdateNow();
        }
        machine.publishProcessVisualsNow();
        structure.publishActivity(shownActive && machine.networkLive() && machine.industrialStructureReady());
        machine.publishItemVisualsNow();
        if (kind == MachineKind.DRIVE && cellStatuses != publishedCellStatuses) {
            publishedCellStatuses = cellStatuses;
            machine.sendClientUpdateNow();
            level.updateNeighbourForOutputSignal(machine.getBlockPos(), state.getBlock());
        }
        if (kind.isEnergyMatrix()) {
            int bucket = machine.extensions().matrixFillPermille() / 25;
            if (bucket != publishedMatrixFill) {
                publishedMatrixFill = bucket;
                machine.sendClientUpdateNow();
                level.updateNeighbourForOutputSignal(machine.getBlockPos(), state.getBlock());
            }
        }
        if (kind == MachineKind.PRIMAL_REACTOR) {
            int bucket = machine.extensions().primalStability() / 25;
            if (bucket != publishedPrimalStability) {
                publishedPrimalStability = bucket;
                machine.sendClientUpdateNow();
            }
        }
        if (kind.isEnergyReactor()) {
            int bucket = machine.extensions().reactorFlux() / 25;
            boolean cooled = machine.extensions().reactorCooled();
            if (bucket != publishedReactorFlux || cooled != publishedReactorCooled) {
                publishedReactorFlux = bucket;
                publishedReactorCooled = cooled;
                machine.sendClientUpdateNow();
                level.updateNeighbourForOutputSignal(machine.getBlockPos(), state.getBlock());
            }
        }
    }

    boolean crank(MachineBlockEntity machine, Player player, @Nullable TerminalAnchor terminal) {
        MachineKind kind = machine.kind();
        Level level = machine.getLevel();
        if (!PreparationTier.manual(kind) || !machine.networkLive() || level.getGameTime() < nextCrank) return false;
        if (terminal == null ? !machine.stillValid(player) : !TerminalNetwork.canManage(terminal, machine, player)) return false;
        machine.ensureRegistryLoaded();
        var operations = machine.operations();
        var work = machine.work();
        var operation = operations.operation(ReMatter.chemistry());
        if (operation == null) { work.status = operations.inputStatus(ReMatter.chemistry()); machine.resetProgress(); return false; }
        String reason = operations.readiness(false, null, operation);
        if (!reason.equals("ready")) { work.status = reason; return false; }
        String signature = operation.signature() + ":" + operation.ticks();
        if (!signature.equals(work.job)) { machine.resetProgress(); work.job = signature; }
        work.duration = operation.ticks();
        work.progress = (int) Math.min(work.duration, (long) work.progress + PreparationWork.CRANK_WORK);
        machine.visuals().capture(machine.stack(0), operation.visualResult(), work.progress, work.duration);
        manualStage = PreparationWork.stage(work.progress, work.duration);
        manualMixing = operations.mixingPreparation();
        nextCrank = level.getGameTime() + PreparationWork.CRANK_COOLDOWN;
        manualActiveUntil = level.getGameTime() + 3;
        if (work.progress >= work.duration) {
            if (!operation.canFinish()) return false;
            operation.finish().run();
            machine.resetProgress();
        }
        visualCrankSequence++;
        float gain = machine.soundGain();
        if (gain > 0.0F) {
            ModSounds.play(level, machine.getBlockPos(), ModSounds.HAND_CRANK, gain, 1.0F);
        }
        machine.setChanged();
        machine.publishProcessVisualsNow();
        machine.sendClientUpdateNow();
        return true;
    }

    void tickManualPreparation(MachineBlockEntity machine, ChemistryRegistry registry) {
        var work = machine.work();
        var operations = machine.operations();
        if (machine.getLevel().getGameTime() < manualActiveUntil) {
            work.processedThisTick = true;
            work.preparationStage = manualStage;
            work.preparationMixing = manualMixing;
            work.status = PreparationWork.stageName(work.preparationStage, work.preparationMixing, machine.kind() == MachineKind.SINTER_FURNACE);
            return;
        }
        var operation = operations.operation(registry);
        if (operation == null) { work.status = operations.inputStatus(registry); machine.resetProgress(); return; }
        var reason = operations.readiness(false, null, operation);
        work.status = reason.equals("ready") ? "crank" : reason;
    }
}
