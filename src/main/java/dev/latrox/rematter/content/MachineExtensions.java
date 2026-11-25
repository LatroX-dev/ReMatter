package dev.latrox.rematter.content;

import com.google.gson.*;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.generation.*;
import dev.latrox.rematter.network.MachineActionPayload;
import dev.latrox.rematter.primal.*;
import dev.latrox.rematter.research.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.*;
import org.jspecify.annotations.Nullable;

/** Explicit non-chemistry operations. Ordinary recipes and automation never acquire this payment reserve. */
final class MachineExtensions {
    private static final Gson JSON=new Gson();
    private final MachineBlockEntity machine;
    private UUID machineId=UUID.randomUUID(), researchId, conversionId;
    private String conversionTarget="";
    private long conversionAmount=1000;
    private int conversionProgress;
    private long conversionEnergyPaid;
    private boolean converting, assay, researchRunning;
    private boolean identityCheckpoint;
    private final Set<UUID> checkpointedCells=new HashSet<>();
    private long energyReceiptRevision,partialEnergyDebt;
    private long checkpointRetryTick;
    private final PrimalCells.Journal primal;
    private final PrimalCells.Journal sourceCell;
    private final CrystalBurner burner=new CrystalBurner();
    private final ControllerEnergyBudget outputBudget=new ControllerEnergyBudget();
    private int primalStability;
    private long matrixEnergy, matrixLastInRate, matrixLastOutRate, matrixTickIn, matrixTickOut;
    private long matrixRateTick = Long.MIN_VALUE;
    private boolean matrixAutoOutput = true;
    private int matrixPushDepth;
    private final Map<BlockPos, Long> matrixDirectInputTicks = new HashMap<>();
    private record MatrixState(long energy, long tickIn, long tickOut) {}
    private final SnapshotJournal<MatrixState> matrixJournal = new SnapshotJournal<>() {
        @Override protected MatrixState createSnapshot() { return new MatrixState(matrixEnergy, matrixTickIn, matrixTickOut); }
        @Override protected void revertToSnapshot(MatrixState state) {
            matrixEnergy = state.energy();
            matrixTickIn = state.tickIn();
            matrixTickOut = state.tickOut();
        }
        @Override protected void onRootCommit(MatrixState originalState) {
            if (matrixEnergy != originalState.energy()) machine.setChanged();
        }
    };
    private long reactorEnergy, reactorLastGenRate, reactorLastOutRate, reactorTickOut;
    private long reactorRateTick = Long.MIN_VALUE;
    private int reactorFlux;
    private EnergyReactorRecipes.FuelMode reactorFuelMode = EnergyReactorRecipes.FuelMode.CRYSTAL;
    private EnergyReactorRecipes.MatterFuelFilter reactorMatterFilter = EnergyReactorRecipes.MatterFuelFilter.AUTO;
    private EnergyReactorRecipes.CoolantMode reactorCoolantMode = EnergyReactorRecipes.CoolantMode.AUTO;
    private boolean reactorAllowNetworkPrimal = true;
    private boolean reactorCooled;
    private String reactorActiveFuel = "";
    private String reactorActiveCoolant = "";
    private record ReactorState(long energy, long tickOut) {}
    private final SnapshotJournal<ReactorState> reactorJournal = new SnapshotJournal<>() {
        @Override protected ReactorState createSnapshot() { return new ReactorState(reactorEnergy, reactorTickOut); }
        @Override protected void revertToSnapshot(ReactorState state) {
            reactorEnergy = state.energy();
            reactorTickOut = state.tickOut();
        }
        @Override protected void onRootCommit(ReactorState originalState) {
            if (reactorEnergy != originalState.energy()) machine.setChanged();
        }
    };
    private record EnergyState(int amount,long revision,long partial,long conversionPaid){}
    private final SnapshotJournal<EnergyState> energyJournal=new SnapshotJournal<>(){
        @Override protected EnergyState createSnapshot(){return new EnergyState((int)machine.energy().getAmountAsLong(),energyReceiptRevision,partialEnergyDebt,conversionEnergyPaid);}
        @Override protected void revertToSnapshot(EnergyState state){machine.energy().set(state.amount());energyReceiptRevision=state.revision();partialEnergyDebt=state.partial();conversionEnergyPaid=state.conversionPaid();}
    };
    MachineExtensions(MachineBlockEntity machine){this.machine=machine;primal=new PrimalCells.Journal(()->machine.stack(MachineCellAccess.PRIMAL_SLOT));sourceCell=new PrimalCells.Journal(()->machine.stack(0));}
    boolean assayMode(){return assay&&machine.kind()==MachineKind.INDUSTRIAL_PREPARATOR;}
    boolean isResearchRunning(){return researchRunning&&researchId!=null;}
    private ServerLevel level(){return (ServerLevel)machine.getLevel();}
    private UUID owner(){return machine.networkOwner();}
    private boolean owned(){return machine.networkLive()&&ResearchService.realOwner(level().getServer(),owner());}
    private ResearchSessions engine(){return ResearchService.researchSession(level().getServer());}
    private ResearchContext context(){return ResearchService.context(level().getServer(),owner());}
    private boolean lens(){return machine.stack(MachineCellAccess.DISCOVERY_SLOT).is(PrimalContent.DISCOVERY_LENS.get());}
    private boolean network(){return machine.matterNetwork(false).ready();}
    private int deriveTicks(){return Math.max(1,(ResearchSessions.DERIVE_TICKS+machine.speed()-1)/Math.max(1,machine.speed()));}
    private long deriveEnergy(){return machine.cost(ResearchSessions.DERIVE_ENERGY);}
    private long discoveryEnergy(){return machine.cost(DiscoveryTier.STEP_ENERGY);}
    private boolean pay(UUID operation,long units,long energy,TransactionContext tx){
        if(!owned()||energy>machine.energy().getAmountAsLong())return false;
        var debit=primal.prepareDebit(operation,units);if(debit.isEmpty())return false;
        ResearchService.recordCellPayment(level().getServer(),owner(),debit.get(),-units,tx);
        ResearchService.recordEnergyPayment(level().getServer(),owner(),machineId,operation,energy,tx);
        energyJournal.updateSnapshots(tx);
        if(!debit.get().apply(tx))return false;
        machine.energy().set(Math.toIntExact(machine.energy().getAmountAsLong()-energy));energyReceiptRevision=ResearchService.energyChanges(level().getServer(),machineId).size();partialEnergyDebt=0;machine.work().energyRate=energy;machine.setChanged();return true;
    }
    private boolean payResearchTick(long due,@Nullable TransactionContext tx){
        if(!owned()||due>machine.energy().getAmountAsLong())return false;
        if(tx!=null)energyJournal.updateSnapshots(tx);
        machine.energy().set(Math.toIntExact(machine.energy().getAmountAsLong()-due));
        machine.work().energyRate=due;machine.setChanged();return true;
    }
    private boolean payConversion(UUID operation,long units,long energy,TransactionContext tx){
        long available=conversionEnergyPaid+machine.energy().getAmountAsLong();
        if(!owned()||energy>available)return false;
        var debit=primal.prepareDebit(operation,units);if(debit.isEmpty())return false;
        ResearchService.recordCellPayment(level().getServer(),owner(),debit.get(),-units,tx);
        ResearchService.recordEnergyPayment(level().getServer(),owner(),machineId,operation,energy,tx);
        energyJournal.updateSnapshots(tx);
        if(!debit.get().apply(tx))return false;
        long fromPaid=Math.min(conversionEnergyPaid,energy);
        conversionEnergyPaid-=fromPaid;
        long remaining=energy-fromPaid;
        if(remaining>0)machine.energy().set(Math.toIntExact(machine.energy().getAmountAsLong()-remaining));
        energyReceiptRevision=ResearchService.energyChanges(level().getServer(),machineId).size();partialEnergyDebt=0;machine.setChanged();return true;
    }
    /** Reconcile authority-new/BE-old energy before that buffer can run work or emit power. */
    private boolean energyDebt(){
        var debts=ResearchService.energyChanges(level().getServer(),machineId);
        if(energyReceiptRevision>debts.size())return true;
        while(energyReceiptRevision<debts.size()){
            long remaining=debts.get(Math.toIntExact(energyReceiptRevision)).energy()-partialEnergyDebt;
            if(remaining<0)return true;
            long fromConversion=Math.min(remaining,conversionEnergyPaid);
            if(fromConversion>0){conversionEnergyPaid-=fromConversion;partialEnergyDebt+=fromConversion;remaining-=fromConversion;machine.setChanged();}
            long paid=Math.min(remaining,machine.energy().getAmountAsLong());
            if(paid>0){machine.energy().set((int)(machine.energy().getAmountAsLong()-paid));partialEnergyDebt+=paid;machine.setChanged();}
            if(paid<remaining)return true;
            energyReceiptRevision++;partialEnergyDebt=0;
        }
        return false;
    }
    private boolean checkpoint(boolean ordinaryOutput){
        if(level().getGameTime()<checkpointRetryTick)return false;
        if(Transaction.getCurrentOpenedTransaction()!=null)throw new IllegalStateException("Identity checkpoint inside transfer");
        var containers=new LinkedHashSet<MachineBlockEntity>();containers.add(machine);
        if(ordinaryOutput)containers.addAll(machine.matterNetwork(true).checkpointStores());
        boolean changed=false;
        for(var container:containers){
            var extension=container.extensions();var ids=new HashSet<>(container.establishCellIdentities());
            if(container==machine){
                var stack=primal.stack();var state=PrimalCells.state(stack);
                if(state.isPresent()){
                    var value=state.get();
                    if(value.cellId().equals(new UUID(0,0))){value=new PrimalCellData.State(UUID.randomUUID(),value.amount(),value.authorityRevision(),value.receipts());stack.set(PrimalCellData.TYPE.get(),PrimalCellData.from(value));}
                    ids.add(value.cellId());
                }
            }
            if(!extension.identityCheckpoint||!extension.checkpointedCells.containsAll(ids)){extension.identityCheckpoint=true;extension.checkpointedCells.clear();extension.checkpointedCells.addAll(ids);container.setChanged();changed=true;}
        }
        if(!changed)return true;
        level().getServer().saveEverything(true,true,true);
        try{
            for(var container:containers){
                var saved=IdentityCheckpointReader.blockEntity(level(),container.getBlockPos()).orElseThrow(()->new java.io.IOException("Missing saved payment container"));
                var extension=container.extensions();
                if(!saved.getStringOr("extension_id","").equals(extension.machineId.toString())||!saved.getBooleanOr("identity_checkpoint",false))throw new java.io.IOException("Controller identity was not saved");
                var items=ItemStack.OPTIONAL_CODEC.listOf().parse(level().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),saved.get("items")).getOrThrow();
                for(int slot=0;slot<MachineCellAccess.INVENTORY_SIZE;slot++){
                    var current=container.stack(slot);
                    if(current.getItem() instanceof MatterCellItem||current.getItem() instanceof PrimalCellItem)
                        if(items.size()<=slot||!ItemStack.matches(current,items.get(slot)))throw new java.io.IOException("Cell identity was not saved");
                }
            }
            return true;
        }catch(java.io.IOException|RuntimeException failure){
            for(var container:containers){container.extensions().identityCheckpoint=false;container.extensions().checkpointedCells.clear();container.setChanged();}
            checkpointRetryTick=level().getGameTime()+100;
            ReMatter.LOGGER.error("Research/conversion paused: payment identities failed physical save readback",failure);return false;
        }
    }
    boolean tick(){
        if(!machine.networkLive())return false;
        if((machine.kind()==MachineKind.ANALYZER||machine.kind()==MachineKind.PRIMAL_CONVERTER)&&!ResearchService.financialReady(level().getServer())){machine.work().status="unsupported_data";return true;}
        if((machine.kind()==MachineKind.ANALYZER||machine.kind()==MachineKind.PRIMAL_CONVERTER||energyReceiptRevision>0||partialEnergyDebt>0)&&energyDebt()){
            machine.work().status="research_energy_debt";return true;
        }
        if(machine.kind().generator()){tickGenerator();return true;}
        if(machine.kind().isEnergyMatrix()){tickEnergyMatrix();return true;}
        if(machine.kind().isEnergyReactor()){tickEnergyReactor();return true;}
        if(machine.kind()==MachineKind.PRIMAL_CONVERTER){tickConverter();return true;}
        if(machine.kind()==MachineKind.PRIMAL_REACTOR){tickPrimalReactor();return true;}
        if(machine.kind()!=MachineKind.ANALYZER||!researchRunning||researchId==null)return false;
        if(!owned()){machine.work().status="research_owner";return true;}
        if(!machine.redstoneAllowsWork()){machine.work().status="redstone_paused";return true;}
        var view=engine().session(context(),researchId).orElse(null);
        if(view==null){machine.work().status="research_identity_changed";return true;}
        long energy=view.mode()==ResearchSessions.Mode.DISCOVERY?discoveryEnergy():deriveEnergy();
        int ticks=view.mode()==ResearchSessions.Mode.DISCOVERY?DiscoveryTier.STEP_TICKS:deriveTicks();
        if(!checkpoint(false)){machine.work().status="identity_checkpoint";return true;}
        var result=engine().advance(context(),researchId,level().getGameTime(),lens(),network(),energy,ticks,this::pay,this::payResearchTick);
        if(result==ResearchSessions.Result.LEARNED&&view.mode()==ResearchSessions.Mode.DERIVE)machine.guidance(dev.latrox.rematter.guide.GuidanceEvent.ON_PAPER);
        var updated=engine().session(context(),researchId).orElse(view);
        machine.work().progress=updated.completedSteps()*updated.stepTicks()+updated.elapsedTicks();
        machine.work().duration=updated.totalSteps()*updated.stepTicks();
        machine.work().processedThisTick=result==ResearchSessions.Result.PAID||result==ResearchSessions.Result.WORKING||result==ResearchSessions.Result.LEARNED;
        machine.work().status=switch(result){case LEARNED->"research_complete";case REFUND_DUE->"research_refund";case PAUSED,UNAVAILABLE->"research_paused";case WAITING->"research_payment";default->"researching";};
        if(result==ResearchSessions.Result.LEARNED||result==ResearchSessions.Result.REFUND_DUE){
            researchRunning=false;
            machine.consumeRedstonePulse();
            if(result==ResearchSessions.Result.LEARNED&&machine.soundGain()>0.0F)ModSounds.play(level(),machine.getBlockPos(),ModSounds.ANALYZER_COMPLETE,machine.soundGain(),1.0F);
        }
        machine.setChanged();return true;
    }
    int primalStability() { return primalStability; }
    int primalMaxStability() {
        return machine.kind() == MachineKind.PRIMAL_REACTOR
                ? PrimalReactorRecipes.seedMaxStability(machine.stack(MachineCellAccess.DISCOVERY_SLOT)) : 0;
    }
    long availablePrimalTotal() {
        var local = PrimalCells.view(primal.stack());
        long localAmount = local.readable() ? local.amount() : 0L;
        long netAmount = machine.matterNetwork(false).primalAmount();
        return Math.addExact(localAmount, netAmount);
    }
    long matrixEnergy() { return matrixEnergy; }
    long matrixCapacity() {
        return machine.kind().isEnergyMatrix() && machine.industrialStructureReady()
                ? IndustrialStructure.inspect(machine).performance().matrixCapacity() : 0L;
    }
    long matrixTransferRate() {
        return machine.kind().isEnergyMatrix() && machine.industrialStructureReady()
                ? IndustrialStructure.inspect(machine).performance().matrixTransferRate() : 0L;
    }
    long matrixInRate() { return matrixLastInRate; }
    long matrixOutRate() { return matrixLastOutRate; }
    boolean matrixAutoOutput() { return matrixAutoOutput; }
    int matrixFillPermille() {
        long cap = matrixCapacity();
        if (cap <= 0L || matrixEnergy <= 0L) return 0;
        return (int) Math.clamp(Math.min(matrixEnergy, cap) * 1_000L / cap, 0L, 1_000L);
    }

    private void advanceMatrixRateWindow(long now) {
        if (matrixRateTick == now) return;
        matrixLastInRate = (matrixRateTick != Long.MIN_VALUE && now == matrixRateTick + 1L) ? matrixTickIn : 0L;
        matrixLastOutRate = (matrixRateTick != Long.MIN_VALUE && now == matrixRateTick + 1L) ? matrixTickOut : 0L;
        matrixTickIn = 0L;
        matrixTickOut = 0L;
        matrixRateTick = now;
    }

    private int insertMatrixEnergy(int amount, TransactionContext tx) {
        net.neoforged.neoforge.transfer.TransferPreconditions.checkNonNegative(amount);
        if (!machine.networkLive() || !machine.kind().isEnergyMatrix() || !machine.industrialStructureReady()) return 0;
        advanceMatrixRateWindow(level().getGameTime());
        long cap = matrixCapacity();
        long rate = matrixTransferRate();
        long room = Math.max(0L, cap - matrixEnergy);
        long budget = Math.max(0L, rate - matrixTickIn);
        int added = (int) Math.min((long) amount, Math.min(room, budget));
        if (added > 0) {
            matrixJournal.updateSnapshots(tx);
            matrixEnergy += added;
            matrixTickIn += added;
        }
        return added;
    }

    private int extractMatrixEnergy(int amount, TransactionContext tx) {
        net.neoforged.neoforge.transfer.TransferPreconditions.checkNonNegative(amount);
        if (!machine.networkLive() || !machine.kind().isEnergyMatrix() || !machine.industrialStructureReady()) return 0;
        advanceMatrixRateWindow(level().getGameTime());
        long rate = matrixTransferRate();
        long budget = Math.max(0L, rate - matrixTickOut);
        int taken = (int) Math.min((long) amount, Math.min(matrixEnergy, budget));
        if (taken > 0) {
            matrixJournal.updateSnapshots(tx);
            matrixEnergy -= taken;
            matrixTickOut += taken;
        }
        return taken;
    }

    private final EnergyHandler matrixItemSink = new EnergyHandler() {
        @Override public long getAmountAsLong() { return matrixEnergy; }
        @Override public long getCapacityAsLong() { return matrixCapacity(); }
        @Override public int insert(int amount, TransactionContext tx) { return insertMatrixEnergy(amount, tx); }
        @Override public int extract(int amount, TransactionContext tx) { return 0; }
    };

    private final EnergyHandler matrixItemSource = new EnergyHandler() {
        @Override public long getAmountAsLong() { return matrixEnergy; }
        @Override public long getCapacityAsLong() { return matrixCapacity(); }
        @Override public int insert(int amount, TransactionContext tx) { return 0; }
        @Override public int extract(int amount, TransactionContext tx) { return extractMatrixEnergy(amount, tx); }
    };

    EnergyHandler matrixPortEnergy(BlockPos portPos, Direction side, MachinePortMode portMode) {
        BlockPos immutablePort = portPos.immutable();
        boolean allowInsert = portMode == MachinePortMode.AUTOMATIC || portMode == MachinePortMode.POWER;
        boolean allowExtract = portMode == MachinePortMode.AUTOMATIC || portMode == MachinePortMode.POWER_OUTPUT;
        return new EnergyHandler() {
            @Override public long getAmountAsLong() { return machine.networkLive() && machine.industrialStructureReady() ? matrixEnergy : 0L; }
            @Override public long getCapacityAsLong() { return machine.networkLive() && machine.industrialStructureReady() ? matrixCapacity() : 0L; }
            @Override public int insert(int amount, TransactionContext tx) {
                net.neoforged.neoforge.transfer.TransferPreconditions.checkNonNegative(amount);
                if (!allowInsert || matrixPushDepth > 0) return 0;
                int added = insertMatrixEnergy(amount, tx);
                if (added > 0 && isDirectMatrixNeighbor(immutablePort, side)) {
                    long tick = level().getGameTime();
                    new RootCommitJournal(() -> matrixDirectInputTicks.put(immutablePort, tick)).updateSnapshots(tx);
                }
                return added;
            }
            @Override public int extract(int amount, TransactionContext tx) {
                net.neoforged.neoforge.transfer.TransferPreconditions.checkNonNegative(amount);
                return allowExtract ? extractMatrixEnergy(amount, tx) : 0;
            }
        };
    }

    private boolean isDirectMatrixNeighbor(BlockPos portPos, Direction outward) {
        BlockPos neighbor = portPos.relative(outward);
        var chunk = level().getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4);
        if (chunk == null) return false;
        return !(chunk.getBlockState(neighbor).getBlock() instanceof MatterCableBlock)
                && !(chunk.getBlockEntity(neighbor) instanceof EntangledLinkBlockEntity)
                && !(chunk.getBlockEntity(neighbor) instanceof DimensionalEntangledLinkBlockEntity);
    }

    private void tickEnergyMatrix() {
        var work = machine.work();
        long now = level().getGameTime();
        advanceMatrixRateWindow(now);
        if (!machine.industrialStructureReady()) {
            work.status = IndustrialStructure.inspect(machine).diagnostic();
            return;
        }
        var formation = IndustrialStructure.inspect(machine);
        long cap = formation.performance().matrixCapacity();
        long rate = formation.performance().matrixTransferRate();
        var itemHandler = VanillaContainerWrapper.of(machine);

        // 1. Drain discharge item in slot 0 into the matrix.
        if (!machine.stack(0).isEmpty() && matrixEnergy < cap && matrixTickIn < rate) {
            var source = ItemAccess.forHandlerIndexStrict(itemHandler, 0).getCapability(Capabilities.Energy.ITEM);
            int offer = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, rate - matrixTickIn));
            if (source != null && offer > 0 && source.getAmountAsLong() > 0) {
                try (var tx = Transaction.openRoot()) {
                    if (EnergyHandlerUtil.move(source, matrixItemSink, offer, tx) > 0) tx.commit();
                }
            }
        }

        // 2. Push power out of formed POWER_OUTPUT ports (always) and AUTOMATIC ports (when Auto-Output is enabled), while Redstone allows.
        if (matrixEnergy > 0 && matrixTickOut < rate && machine.redstoneAllowsWork()) {
            for (var port : formation.ports()) {
                if (matrixEnergy <= 0 || matrixTickOut >= rate) break;
                if (!level().hasChunkAt(port.position())) continue;
                if (!(level().getBlockEntity(port.position()) instanceof MachinePortBlockEntity portEntity)) continue;
                var portMode = portEntity.mode();
                boolean pushes = portMode == MachinePortMode.POWER_OUTPUT
                        || (matrixAutoOutput && portMode == MachinePortMode.AUTOMATIC);
                if (!pushes) continue;
                long lastIn = matrixDirectInputTicks.getOrDefault(port.position(), -10L);
                if (portMode == MachinePortMode.AUTOMATIC && now - lastIn <= 1L) continue;
                BlockPos target = port.position().relative(port.outward());
                if (level().getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) continue;
                if (level().getBlockEntity(target) instanceof MachinePortBlockEntity otherPort
                        && otherPort.controller(port.outward().getOpposite()) == machine) continue;
                var sink = level().getCapability(Capabilities.Energy.BLOCK, target, port.outward().getOpposite());
                if (sink == null) continue;
                MatterCablePower.energizeIfCable(sink);
                int offer = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, rate - matrixTickOut));
                if (offer <= 0) break;
                matrixPushDepth++;
                try (var tx = Transaction.openRoot()) {
                    if (EnergyHandlerUtil.move(matrixItemSource, sink, offer, tx) > 0) tx.commit();
                } finally {
                    matrixPushDepth--;
                }
            }
        }

        // 3. Charge item in slot 1 from remaining output budget.
        if (!machine.stack(1).isEmpty() && matrixEnergy > 0 && matrixTickOut < rate) {
            var sink = ItemAccess.forHandlerIndexStrict(itemHandler, 1).getCapability(Capabilities.Energy.ITEM);
            int offer = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, rate - matrixTickOut));
            if (sink != null && offer > 0 && sink.getAmountAsLong() < sink.getCapacityAsLong()) {
                try (var tx = Transaction.openRoot()) {
                    if (EnergyHandlerUtil.move(matrixItemSource, sink, offer, tx) > 0) tx.commit();
                }
            }
        }

        boolean flowing = matrixTickIn > 0 || matrixTickOut > 0;
        work.processedThisTick = flowing || matrixEnergy > 0;
        work.energyRate = flowing ? Math.max(matrixTickIn, matrixTickOut) : 0L;
        work.status = cap > 0 && matrixEnergy >= cap ? "energy_full" : flowing ? "working" : "ready";
    }

    long reactorEnergy() { return reactorEnergy; }
    long reactorCapacity() {
        return machine.kind().isEnergyReactor() && machine.industrialStructureReady()
                ? IndustrialStructure.inspect(machine).performance().reactorCapacity() : 0L;
    }
    long reactorMaxOutputRate() {
        return machine.kind().isEnergyReactor() && machine.industrialStructureReady()
                ? IndustrialStructure.inspect(machine).performance().reactorMaxOutputRate() : 0L;
    }
    long reactorGenRate() { return reactorLastGenRate; }
    long reactorOutRate() { return reactorLastOutRate; }
    int reactorFlux() { return reactorFlux; }
    EnergyReactorRecipes.FuelMode reactorFuelMode() { return reactorFuelMode; }
    EnergyReactorRecipes.MatterFuelFilter reactorMatterFilter() { return reactorMatterFilter; }
    EnergyReactorRecipes.CoolantMode reactorCoolantMode() { return reactorCoolantMode; }
    boolean reactorAllowNetworkPrimal() { return reactorAllowNetworkPrimal; }
    boolean reactorCooled() { return reactorCooled; }

    private void advanceReactorRateWindow(long now) {
        if (reactorRateTick == now) return;
        reactorLastOutRate = (reactorRateTick != Long.MIN_VALUE && now == reactorRateTick + 1L) ? reactorTickOut : 0L;
        reactorTickOut = 0L;
        reactorRateTick = now;
    }

    private int extractReactorEnergy(int amount, TransactionContext tx) {
        net.neoforged.neoforge.transfer.TransferPreconditions.checkNonNegative(amount);
        if (!machine.networkLive() || !machine.kind().isEnergyReactor() || !machine.industrialStructureReady()) return 0;
        advanceReactorRateWindow(level().getGameTime());
        long rate = reactorMaxOutputRate();
        long budget = Math.max(0L, rate - reactorTickOut);
        int taken = (int) Math.min((long) amount, Math.min(reactorEnergy, budget));
        if (taken > 0) {
            reactorJournal.updateSnapshots(tx);
            reactorEnergy -= taken;
            reactorTickOut += taken;
        }
        return taken;
    }

    private final EnergyHandler reactorSource = new EnergyHandler() {
        @Override public long getAmountAsLong() { return machine.networkLive() && machine.industrialStructureReady() ? reactorEnergy : 0L; }
        @Override public long getCapacityAsLong() { return machine.networkLive() && machine.industrialStructureReady() ? reactorCapacity() : 0L; }
        @Override public int insert(int amount, TransactionContext tx) { return 0; }
        @Override public int extract(int amount, TransactionContext tx) { return extractReactorEnergy(amount, tx); }
    };

    EnergyHandler reactorPortEnergy() {
        return reactorSource;
    }

    private void pushReactorEnergy(IndustrialStructure.Formation formation, long maxOut) {
        if (reactorEnergy <= 0L || reactorTickOut >= maxOut) return;
        for (var port : formation.ports()) {
            if (reactorEnergy <= 0L || reactorTickOut >= maxOut) break;
            if (!level().hasChunkAt(port.position())) continue;
            if (!(level().getBlockEntity(port.position()) instanceof MachinePortBlockEntity portEntity)
                    || (!portEntity.mode().allows(MachinePortMode.POWER_OUTPUT) && portEntity.mode() != MachinePortMode.POWER)) continue;
            BlockPos target = port.position().relative(port.outward());
            if (level().getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null) continue;
            if (level().getBlockEntity(target) instanceof MachinePortBlockEntity otherPort
                    && otherPort.controller(port.outward().getOpposite()) == machine) continue;
            var sink = level().getCapability(Capabilities.Energy.BLOCK, target, port.outward().getOpposite());
            if (sink == null || sink == reactorSource) continue;
            MatterCablePower.energizeIfCable(sink);
            int offer = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, maxOut - reactorTickOut));
            if (offer <= 0) break;
            try (var tx = Transaction.openRoot()) {
                if (EnergyHandlerUtil.move(reactorSource, sink, offer, tx) > 0) tx.commit();
            }
        }
    }

    private void decayReactorFlux(int step) {
        if (reactorFlux > 0) {
            reactorFlux = Math.max(0, reactorFlux - step);
            machine.setChanged();
        }
    }

    private void tickEnergyReactor() {
        var work = machine.work();
        long now = level().getGameTime();
        advanceReactorRateWindow(now);
        reactorLastGenRate = 0L;
        reactorCooled = false;
        reactorActiveFuel = "";
        reactorActiveCoolant = "";
        if (!machine.industrialStructureReady()) {
            decayReactorFlux(EnergyReactorRecipes.FLUX_DECAY_PER_TICK);
            work.status = IndustrialStructure.inspect(machine).diagnostic();
            return;
        }
        var formation = IndustrialStructure.inspect(machine);
        var perf = formation.performance();
        int fuelCores = perf.fuelCores();
        int coils = perf.inductionCoils();
        long cap = perf.reactorCapacity();
        long maxOut = perf.reactorMaxOutputRate();

        // Always allow draining existing buffer (including any overhang after shrinking) through formed POWER ports.
        pushReactorEnergy(formation, maxOut);

        if (!machine.running()) {
            decayReactorFlux(EnergyReactorRecipes.FLUX_DECAY_PER_TICK);
            work.status = "paused";
            return;
        }
        if (!machine.redstoneAllowsGenerator(reactorFlux > 0 || burner.remaining() > 0)) {
            decayReactorFlux(EnergyReactorRecipes.FLUX_DECAY_PER_TICK);
            work.status = "redstone_paused";
            return;
        }

        // Safe auto-throttle: zero fuel burned when the 64-bit internal buffer is full.
        long room = Math.max(0L, cap - reactorEnergy);
        if (room <= 0L) {
            decayReactorFlux(2);
            work.status = "energy_full";
            return;
        }

        // Optional coolant check (AUTO prefers stronger Nitrogen Cryo Overdrive first, falling back to Water).
        var network = machine.matterNetwork(false);
        SubstanceKey coolantKey = null;
        long coolantNeeded = EnergyReactorRecipes.coolantCost(fuelCores);
        if (reactorCoolantMode != EnergyReactorRecipes.CoolantMode.OFF
                && network.ready() && network.inventory() != null && coolantNeeded > 0L) {
            if (reactorCoolantMode.allowsNitrogen()
                    && network.inventory().amount(EnergyReactorRecipes.NITROGEN_COOLANT) - coolantNeeded >= machine.effectiveNetworkReserve(EnergyReactorRecipes.NITROGEN_COOLANT)) {
                coolantKey = EnergyReactorRecipes.NITROGEN_COOLANT;
            } else if (reactorCoolantMode.allowsWater()
                    && network.inventory().amount(EnergyReactorRecipes.WATER_COOLANT) - coolantNeeded >= machine.effectiveNetworkReserve(EnergyReactorRecipes.WATER_COOLANT)) {
                coolantKey = EnergyReactorRecipes.WATER_COOLANT;
            }
        }
        var coolantTier = EnergyReactorRecipes.CoolantTier.fromKey(coolantKey);
        boolean cooled = coolantTier.cooled();

        long produced = 0L;
        String failStatus = "generator_fuel";
        switch (reactorFuelMode) {
            case CRYSTAL -> {
                long fluxBase = Math.max(1L, (EnergyReactorRecipes.CRYSTAL_FE_PER_CORE * EnergyReactorRecipes.fluxFactorPermille(reactorFlux)) / 1_000L);
                long burnDemand = EnergyReactorRecipes.effectiveFuelCost(fluxBase, fuelCores, coolantTier);
                long expectedOut = EnergyReactorRecipes.effectiveOutputPerTick(EnergyReactorRecipes.CRYSTAL_FE_PER_CORE, fuelCores, coils, reactorFlux, coolantTier);
                long scaledBurnDemand = room < expectedOut ? Math.max(1L, (burnDemand * room) / Math.max(1L, expectedOut)) : burnDemand;
                var fuel = CrystalFuelData.fuel(machine.stack(0)).orElse(null);
                var plan = burner.tickMulti(scaledBurnDemand, fuel, machine.stack(0).getCount());
                if (!burner.apply(plan)) {
                    failStatus = "unsupported_data";
                    break;
                }
                if (plan.changesState()) {
                    if (plan.consumedItems() > 0) {
                        machine.stack(0).shrink(plan.consumedItems());
                        machine.consumeRedstonePulse();
                    }
                    if (cooled && plan.producedEnergy() > 0L) {
                        var deepNet = machine.matterNetwork(true);
                        if (deepNet.ready() && deepNet.inventory() != null) {
                            var act = machine.commitNetworkMatter(deepNet, Map.of(coolantKey, coolantNeeded), Map.of());
                            if (act != null) {
                                deepNet.markChanged();
                                machine.publishNetworkActivity(deepNet, act);
                            } else {
                                cooled = false;
                                coolantKey = null;
                                coolantTier = EnergyReactorRecipes.CoolantTier.NONE;
                            }
                        } else {
                            cooled = false;
                            coolantKey = null;
                            coolantTier = EnergyReactorRecipes.CoolantTier.NONE;
                        }
                    }
                    long fullOut = EnergyReactorRecipes.effectiveOutputPerTick(EnergyReactorRecipes.CRYSTAL_FE_PER_CORE, fuelCores, coils, reactorFlux, coolantTier);
                    produced = Math.min(room, plan.producedEnergy() >= scaledBurnDemand ? fullOut
                            : Math.max(1L, (fullOut * plan.producedEnergy()) / Math.max(1L, scaledBurnDemand)));
                    reactorActiveFuel = "crystal";
                } else {
                    failStatus = !burner.readable() ? "unsupported_data" : "generator_fuel";
                }
            }
            case MATTER -> {
                if (!network.ready() || network.inventory() == null) {
                    failStatus = network.status();
                    break;
                }
                EnergyReactorRecipes.MatterFuel chosen = null;
                Map<SubstanceKey, Long> inputs = null;
                boolean blockedByReserve = false;
                for (var candidate : EnergyReactorRecipes.MATTER_FUELS) {
                    if (!reactorMatterFilter.accepts(candidate.id())) continue;
                    var req = new LinkedHashMap<SubstanceKey, Long>();
                    boolean enough = true;
                    boolean reserveHit = false;
                    for (var entry : candidate.inputsPerCorePerTick().entrySet()) {
                        long need = EnergyReactorRecipes.effectiveFuelCost(entry.getValue(), fuelCores, coolantTier);
                        long have = network.inventory().amount(entry.getKey());
                        long reserve = machine.effectiveNetworkReserve(entry.getKey());
                        if (have < need) {
                            enough = false;
                            break;
                        }
                        if (have - need < reserve) {
                            enough = false;
                            reserveHit = true;
                            break;
                        }
                        req.put(entry.getKey(), need);
                    }
                    if (enough) {
                        chosen = candidate;
                        inputs = req;
                        break;
                    }
                    if (reserveHit) blockedByReserve = true;
                }
                if (chosen == null) {
                    failStatus = blockedByReserve ? "network_reserve" : "missing_matter";
                    break;
                }
                if (cooled) {
                    inputs.merge(coolantKey, coolantNeeded, Long::sum);
                }
                var deepNet = machine.matterNetwork(true);
                if (!deepNet.ready() || deepNet.inventory() == null) {
                    failStatus = deepNet.ready() ? "storage_changed" : deepNet.status();
                    break;
                }
                var act = machine.commitNetworkMatter(deepNet, inputs, Map.of());
                if (act == null && cooled) {
                    cooled = false;
                    coolantKey = null;
                    coolantTier = EnergyReactorRecipes.CoolantTier.NONE;
                    inputs.clear();
                    for (var entry : chosen.inputsPerCorePerTick().entrySet()) {
                        inputs.put(entry.getKey(), EnergyReactorRecipes.effectiveFuelCost(entry.getValue(), fuelCores, coolantTier));
                    }
                    act = machine.commitNetworkMatter(deepNet, inputs, Map.of());
                }
                if (act == null) {
                    failStatus = "missing_matter";
                    break;
                }
                deepNet.markChanged();
                machine.publishNetworkActivity(deepNet, act);
                machine.consumeRedstonePulse();
                produced = Math.min(room, EnergyReactorRecipes.effectiveOutputPerTick(chosen.fePerCorePerTick(), fuelCores, coils, reactorFlux, coolantTier));
                reactorActiveFuel = chosen.id();
            }
            case PRIMAL -> {
                long neededPrimal = EnergyReactorRecipes.effectiveFuelCost(EnergyReactorRecipes.PRIMAL_UR_PER_CORE_PER_TICK, fuelCores, coolantTier);
                var localView = PrimalCells.view(primal.stack());
                long localAmt = localView.readable() ? localView.amount() : 0L;
                long rawNetAmt = reactorAllowNetworkPrimal && network.ready() ? network.primalAmount() : 0L;
                long primalReserve = machine.effectivePrimalNetworkReserve();
                long usableNetAmt = Math.max(0L, rawNetAmt - primalReserve);
                if (Math.addExact(localAmt, usableNetAmt) < neededPrimal) {
                    failStatus = reactorAllowNetworkPrimal && Math.addExact(localAmt, rawNetAmt) >= neededPrimal ? "network_reserve" : "missing_primal";
                    break;
                }
                long netAmt = usableNetAmt;
                var deepNet = (netAmt > 0L || cooled) ? machine.matterNetwork(true) : null;
                boolean committed = false;
                try (var tx = Transaction.openRoot()) {
                    long left = neededPrimal;
                    if (localAmt > 0L && left > 0L) {
                        left -= PrimalCells.debit(primal, left, tx);
                    }
                    if (left > 0L && deepNet != null && deepNet.ready()) {
                        left -= deepNet.debitPrimal(machine, left, tx);
                    }
                    if (left == 0L) {
                        tx.commit();
                        committed = true;
                    }
                }
                if (!committed) {
                    failStatus = "missing_primal";
                    break;
                }
                if (deepNet != null && deepNet.ready()) {
                    deepNet.markPrimalDrivesChanged();
                    if (cooled && deepNet.inventory() != null) {
                        var act = machine.commitNetworkMatter(deepNet, Map.of(coolantKey, coolantNeeded), Map.of());
                        if (act != null) {
                            deepNet.markChanged();
                            machine.publishNetworkActivity(deepNet, act);
                        } else {
                            cooled = false;
                            coolantKey = null;
                            coolantTier = EnergyReactorRecipes.CoolantTier.NONE;
                        }
                    }
                } else {
                    cooled = false;
                    coolantKey = null;
                    coolantTier = EnergyReactorRecipes.CoolantTier.NONE;
                }
                machine.consumeRedstonePulse();
                produced = Math.min(room, EnergyReactorRecipes.effectiveOutputPerTick(EnergyReactorRecipes.PRIMAL_FE_PER_CORE, fuelCores, coils, reactorFlux, coolantTier));
                reactorActiveFuel = "primal";
            }
        }

        if (produced > 0L) {
            reactorEnergy = Math.min(cap, reactorEnergy + produced);
            reactorLastGenRate = produced;
            reactorCooled = cooled;
            reactorActiveCoolant = coolantKey == null ? "" : coolantKey.id().toString();
            reactorFlux = Math.min(EnergyReactorRecipes.MAX_FLUX, reactorFlux + EnergyReactorRecipes.FLUX_RAMP_PER_TICK);
            work.processedThisTick = true;
            work.energyRate = produced;
            work.status = "generating";
            machine.setChanged();
            // Push newly produced energy on the same tick for crisp continuous flow.
            pushReactorEnergy(formation, maxOut);
        } else {
            decayReactorFlux(EnergyReactorRecipes.FLUX_DECAY_PER_TICK);
            work.status = failStatus;
        }
    }

    private void decayPrimalStability() {
        if (primalStability > 0 && (level().getGameTime() & 1L) == 0L) {
            primalStability--;
            machine.setChanged();
        }
    }

    private void rampPrimalStability(int maxStability) {
        if ((level().getGameTime() & 1L) != 0L) return;
        if (primalStability < maxStability) {
            primalStability++;
            machine.setChanged();
        } else if (primalStability > maxStability) {
            primalStability--;
            machine.setChanged();
        }
    }

    private void tickPrimalReactor() {
        var work = machine.work();
        var registry = ReMatter.chemistry();
        if (!machine.syncSelectionCatalogue(registry)) {
            decayPrimalStability();
            work.status = "selection_unavailable";
            return;
        }
        if (!machine.industrialStructureReady()) {
            machine.resetProgress();
            decayPrimalStability();
            work.status = IndustrialStructure.inspect(machine).diagnostic();
            return;
        }
        var choice = machine.selectedChoice();
        if (choice == null) {
            machine.resetProgress();
            decayPrimalStability();
            work.status = "choose_process";
            return;
        }
        var runState = machine.runState();
        if (!runState.running()) {
            decayPrimalStability();
            work.status = !runState.repeat() && runState.queuedBatches() == 0 ? "awaiting_request" : "paused";
            return;
        }
        if (!machine.redstoneAllowsWork()) {
            decayPrimalStability();
            work.status = "redstone_paused";
            return;
        }
        int maxStability = PrimalReactorRecipes.seedMaxStability(machine.stack(MachineCellAccess.DISCOVERY_SLOT));
        if (maxStability <= 0) {
            decayPrimalStability();
            work.status = "requires_seed";
            return;
        }
        var tier = PrimalReactorRecipes.condensation(choice.id(), registry).orElse(null);
        if (tier != null) {
            tickPrimalCondensation(tier, runState, maxStability);
            return;
        }
        var manifest = PrimalReactorRecipes.manifestation(choice.id(), registry).orElse(null);
        if (manifest != null) {
            tickPrimalManifestation(manifest, runState, maxStability);
            return;
        }
        decayPrimalStability();
        work.status = "selection_unavailable";
    }

    private void tickPrimalCondensation(PrimalReactorRecipes.CondensationTier tier, MachineRunState runState, int maxStability) {
        var work = machine.work();
        var network = machine.matterNetwork(false);
        if (!network.ready() || network.inventory() == null) {
            decayPrimalStability();
            work.status = network.status();
            return;
        }
        var localView = PrimalCells.view(primal.stack());
        long localCap = localView.readable() ? localView.capacity() : 0L;
        long localFree = localView.readable() ? Math.max(0L, localView.capacity() - localView.amount()) : 0L;
        long netCap = network.primalDepositCapacity();
        long netFree = network.primalFreeSpace();
        long totalCap = Math.addExact(localCap, netCap);
        long totalFree = Math.addExact(localFree, netFree);
        if (totalCap == 0L) {
            decayPrimalStability();
            work.status = "missing_cell";
            return;
        }
        boolean compressed = machine.hasMassCompression();
        Map<SubstanceKey, Long> perLaneInputs = tier.effectiveInputs(compressed);
        long outPerLane = PrimalReactorRecipes.condensationOutput(tier.primalOutput(), primalStability);
        int requestedLanes = machine.parallelCapacity();
        if (!runState.repeat()) requestedLanes = Math.min(requestedLanes, runState.queuedBatches());
        int spaceLanes = (int) Math.min(4L, totalFree / outPerLane);
        if (spaceLanes <= 0) {
            decayPrimalStability();
            work.status = "output_full";
            return;
        }
        var inv = network.inventory();
        int stockLanes = requestedLanes;
        boolean reserveHit = false;
        for (var entry : perLaneInputs.entrySet()) {
            long have = inv.amount(entry.getKey());
            long reserve = machine.effectiveNetworkReserve(entry.getKey());
            long usable = Math.max(0L, have - reserve);
            if (have >= entry.getValue() && usable < entry.getValue()) reserveHit = true;
            stockLanes = (int) Math.min(stockLanes, usable / entry.getValue());
        }
        if (stockLanes <= 0) {
            decayPrimalStability();
            work.status = reserveHit ? "network_reserve" : "missing_matter";
            return;
        }
        int lanes = Math.min(requestedLanes, Math.min(spaceLanes, stockLanes));
        if (lanes <= 0) {
            decayPrimalStability();
            work.status = "awaiting_request";
            return;
        }
        long batchEnergy = Math.multiplyExact(machine.cost(tier.effectiveEnergy(compressed)), lanes);
        int effSpeed = PrimalReactorRecipes.effectiveSpeed(machine.speed(), primalStability);
        work.duration = tier.ticks();
        long due = MachineEnergyPolicy.stepEnergy(batchEnergy, work.progress, effSpeed, work.duration, work.energyPaid);
        if (machine.energy().getAmountAsLong() < due) {
            decayPrimalStability();
            work.status = "insufficient_energy";
            return;
        }
        if (due > 0) {
            machine.energy().set((int) (machine.energy().getAmountAsLong() - due));
            work.energyPaid += due;
            work.energyRate = due;
        }
        work.progress = (int) Math.min(work.duration, (long) work.progress + effSpeed);
        work.processedThisTick = true;
        work.status = "working";
        rampPrimalStability(maxStability);
        machine.setChanged();
        if (work.progress < work.duration) return;

        var deepNet = machine.matterNetwork(true);
        if (!deepNet.ready() || deepNet.inventory() == null) {
            work.status = deepNet.ready() ? "storage_changed" : deepNet.status();
            work.processedThisTick = false;
            return;
        }
        var scaledInputs = new LinkedHashMap<SubstanceKey, Long>(perLaneInputs.size());
        for (var entry : perLaneInputs.entrySet()) {
            scaledInputs.put(entry.getKey(), Math.multiplyExact(entry.getValue(), lanes));
        }
        long outputUnits = Math.multiplyExact(outPerLane, lanes);
        List<MatterNetworkInventory.MemberActivity> cableActivity = null;
        boolean committed = false;
        String failStatus = "output_full";
        try (var tx = Transaction.openRoot()) {
            long remainingToCredit = outputUnits;
            if (localFree > 0 && remainingToCredit > 0) {
                remainingToCredit -= PrimalCells.credit(primal, remainingToCredit, tx);
            }
            if (remainingToCredit > 0) {
                remainingToCredit -= deepNet.creditPrimal(machine, remainingToCredit, tx);
            }
            if (remainingToCredit == 0) {
                cableActivity = machine.commitNetworkMatter(deepNet, scaledInputs, Map.of());
                if (cableActivity != null) {
                    tx.commit();
                    committed = true;
                } else {
                    failStatus = "missing_matter";
                }
            }
        }
        if (!committed) {
            work.status = failStatus;
            work.processedThisTick = false;
            return;
        }
        deepNet.markChanged();
        deepNet.markPrimalDrivesChanged();
        if (cableActivity != null) machine.publishNetworkActivity(deepNet, cableActivity);
        machine.completeRunLanes(lanes);
        machine.consumeRedstonePulse();
        work.progress = 0;
        work.energyPaid = 0;
        work.status = "ready";
        machine.setChanged();
    }

    private void tickPrimalManifestation(PrimalReactorRecipes.ManifestationPlan plan, MachineRunState runState, int maxStability) {
        var work = machine.work();
        if (!owned() || ResearchService.knownSubstances(level(), owner()).stream()
                .noneMatch(k -> k.id().equals(plan.target().id()))) {
            decayPrimalStability();
            work.status = "requires_research";
            return;
        }
        var network = machine.matterNetwork(false);
        if (!network.ready() || network.inventory() == null) {
            decayPrimalStability();
            work.status = network.status();
            return;
        }
        var localView = PrimalCells.view(primal.stack());
        long localAmount = localView.readable() ? localView.amount() : 0L;
        long rawNetPrimal = network.primalAmount();
        long usableNetPrimal = Math.max(0L, rawNetPrimal - machine.effectivePrimalNetworkReserve());
        long totalPrimal = Math.addExact(localAmount, usableNetPrimal);
        int requestedLanes = machine.parallelCapacity();
        if (!runState.repeat()) requestedLanes = Math.min(requestedLanes, runState.queuedBatches());
        int primalLanes = (int) Math.min(4L, totalPrimal / plan.primalCost());
        if (primalLanes <= 0) {
            decayPrimalStability();
            work.status = Math.addExact(localAmount, rawNetPrimal) >= plan.primalCost() ? "network_reserve" : "research_payment";
            return;
        }
        long roomUnits = MachineBlockEntity.insertable(network.inventory(), plan.target(), Math.multiplyExact(plan.outputAmount(), requestedLanes));
        int roomLanes = (int) Math.min(4L, roomUnits / plan.outputAmount());
        if (roomLanes <= 0) {
            decayPrimalStability();
            work.status = "output_full";
            return;
        }
        int lanes = Math.min(requestedLanes, Math.min(primalLanes, roomLanes));
        if (lanes <= 0) {
            decayPrimalStability();
            work.status = "awaiting_request";
            return;
        }
        long batchEnergy = Math.multiplyExact(machine.cost(plan.energy()), lanes);
        int effSpeed = PrimalReactorRecipes.effectiveSpeed(machine.speed(), primalStability);
        work.duration = plan.ticks();
        long due = MachineEnergyPolicy.stepEnergy(batchEnergy, work.progress, effSpeed, work.duration, work.energyPaid);
        if (machine.energy().getAmountAsLong() < due) {
            decayPrimalStability();
            work.status = "insufficient_energy";
            return;
        }
        if (due > 0) {
            machine.energy().set((int) (machine.energy().getAmountAsLong() - due));
            work.energyPaid += due;
            work.energyRate = due;
        }
        work.progress = (int) Math.min(work.duration, (long) work.progress + effSpeed);
        work.processedThisTick = true;
        work.status = "working";
        rampPrimalStability(maxStability);
        machine.setChanged();
        if (work.progress < work.duration) return;

        var deepNet = machine.matterNetwork(true);
        if (!deepNet.ready() || deepNet.inventory() == null) {
            work.status = deepNet.ready() ? "storage_changed" : deepNet.status();
            work.processedThisTick = false;
            return;
        }
        long neededPrimal = Math.multiplyExact(plan.primalCost(), lanes);
        long outAmount = Math.multiplyExact(plan.outputAmount(), lanes);
        List<MatterNetworkInventory.MemberActivity> cableActivity = null;
        boolean committed = false;
        try (var tx = Transaction.openRoot()) {
            long left = neededPrimal;
            if (localAmount > 0 && left > 0) {
                left -= PrimalCells.debit(primal, left, tx);
            }
            if (left > 0) {
                left -= deepNet.debitPrimal(machine, left, tx);
            }
            if (left == 0) {
                cableActivity = machine.commitNetworkMatter(deepNet, Map.of(), Map.of(plan.target(), outAmount));
                if (cableActivity != null) {
                    tx.commit();
                    committed = true;
                }
            }
        }
        if (!committed) {
            work.status = "output_full";
            work.processedThisTick = false;
            return;
        }
        deepNet.markChanged();
        deepNet.markPrimalDrivesChanged();
        if (cableActivity != null) machine.publishNetworkActivity(deepNet, cableActivity);
        machine.completeRunLanes(lanes);
        machine.consumeRedstonePulse();
        work.progress = 0;
        work.energyPaid = 0;
        work.status = "ready";
        machine.setChanged();
    }

    private PrimalPricing.Quote quote(){
        var known=ResearchService.knownSubstances(level(),owner());
        return PrimalPricing.quote(ReMatter.chemistry(),SubstanceKey.of(conversionTarget,Phase.SOLID),conversionAmount,id->known.stream().anyMatch(k->k.id().equals(id)));
    }
    private void tickConverter(){
        var work=machine.work();
        if(!owned()){work.status="research_owner";return;}
        var claims=ResearchService.conversionClaims(level().getServer(),owner(),machineId);
        for(var claim:claims)if(claim.remaining()>0){
            var network=machine.matterNetwork(true);
            if(!network.ready()){work.status=network.status();return;}
            if(!checkpoint(true)){work.status="identity_checkpoint";return;}
            long paid=ResearchService.claimConversion(level().getServer(),owner(),claim.id(),claim.remaining(),
                    (op,target,amount,tx)->network.credit(machine,op,target,amount,tx));
            if(paid==0){work.status="output_full";return;}
            network.markChanged();work.status="ready";machine.setChanged();return;
        }
        if(!converting){work.status="awaiting_request";return;}
        if(!machine.redstoneAllowsWork()){work.status="redstone_paused";return;}
        PrimalPricing.Quote quote;
        try{quote=quote();}catch(IllegalArgumentException|ArithmeticException invalid){converting=false;work.status="requires_research";machine.setChanged();return;}
        var network=machine.matterNetwork(true);
        if(!network.ready()){work.status=network.status();return;}
        if(!ConverterDestination.observe(network,quote.target(),quote.output()).ready()){work.status="output_full";return;}
        if(PrimalCells.view(primal.stack()).amount()<quote.primal()){work.status="research_payment";return;}
        long due=MachineEnergyPolicy.stepEnergy(quote.energy(),conversionProgress,1,quote.ticks(),conversionEnergyPaid);
        if(machine.energy().getAmountAsLong()<due){work.status="research_payment";return;}
        if(due>0){
            machine.energy().set((int)(machine.energy().getAmountAsLong()-due));
            conversionEnergyPaid+=due;
            work.energyRate=due;
        }
        conversionProgress=Math.min(quote.ticks(),conversionProgress+1);work.progress=conversionProgress;work.duration=quote.ticks();work.processedThisTick=true;work.status="working";
        boolean transferred=machine.advanceNetworkTransfer(conversionId.toString(),quote.output());
        if(conversionProgress<quote.ticks()){machine.setChanged();return;}
        if(!transferred){work.status="cable_limited";return;}
        if(!checkpoint(true)){work.status="identity_checkpoint";return;}
        var result=ResearchService.commitConversion(level().getServer(),owner(),machineId,conversionId,quote.target(),quote.output(),quote.energy(),this::payConversion);
        if(result.isPresent()){converting=false;conversionProgress=0;conversionEnergyPaid=0;machine.consumeRedstonePulse();machine.completeNetworkTransfer();work.status="ready";machine.guidance(dev.latrox.rematter.guide.GuidanceEvent.ONE_WAY);}
        else work.status="research_payment";
        machine.setChanged();
    }
    boolean action(net.minecraft.world.entity.player.Player user, MachineActionPayload request) {
        if (!machine.networkLive()) return false;
        if (request.action() == MachineActionPayload.Action.BLUEPRINT_BUILD) {
            if (!IndustrialStructure.isIndustrial(machine.kind()) || IndustrialStructure.inspect(machine).formed()) return false;
            var res = IndustrialAutoBuilder.build(level(), user, machine);
            return res != IndustrialAutoBuilder.Result.DENIED;
        }
        return action(request);
    }
    boolean action(MachineActionPayload request){
        if(!machine.networkLive())return false;
        try{
            switch(request.action()){
                case BLUEPRINT_STEP_UP,BLUEPRINT_STEP_DOWN->{
                    if(!IndustrialStructure.isIndustrial(machine.kind())||IndustrialStructure.inspect(machine).formed())return false;
                    var param=IndustrialBlueprint.Parameter.valueOf(request.target());
                    int step=Math.clamp((int)Math.max(1L,request.amount()),1,16);
                    return machine.adjustIndustrialBlueprint(param,request.action()==MachineActionPayload.Action.BLUEPRINT_STEP_UP?step:-step);
                }
                case BLUEPRINT_RESET->{
                    return IndustrialStructure.isIndustrial(machine.kind())&&!IndustrialStructure.inspect(machine).formed()&&machine.resetIndustrialBlueprint();
                }
                case TOGGLE_MATRIX_OUTPUT->{
                    if(!machine.kind().isEnergyMatrix())return false;
                    matrixAutoOutput=!matrixAutoOutput;
                    machine.setChanged();
                    return true;
                }
                case SET_REACTOR_FUEL_MODE->{
                    if(!machine.kind().isEnergyReactor())return false;
                    var next=EnergyReactorRecipes.FuelMode.load(request.target());
                    if(reactorFuelMode==next)return false;
                    reactorFuelMode=next;
                    machine.setChanged();
                    return true;
                }
                case SET_REACTOR_MATTER_FILTER->{
                    if(!machine.kind().isEnergyReactor())return false;
                    var next=request.target().isEmpty()?reactorMatterFilter.next():EnergyReactorRecipes.MatterFuelFilter.load(request.target());
                    if(reactorMatterFilter==next)return false;
                    reactorMatterFilter=next;
                    machine.setChanged();
                    return true;
                }
                case SET_REACTOR_COOLANT_MODE->{
                    if(!machine.kind().isEnergyReactor())return false;
                    var next=request.target().isEmpty()?reactorCoolantMode.next():EnergyReactorRecipes.CoolantMode.load(request.target());
                    if(reactorCoolantMode==next)return false;
                    reactorCoolantMode=next;
                    machine.setChanged();
                    return true;
                }
                case TOGGLE_REACTOR_NET_PRIMAL->{
                    if(!machine.kind().isEnergyReactor())return false;
                    reactorAllowNetworkPrimal=!reactorAllowNetworkPrimal;
                    machine.setChanged();
                    return true;
                }
                case SET_CUSTOM_NAME->{
                    machine.setCustomName(request.target());
                    return true;
                }
                default->{}
            }
        }catch(IllegalArgumentException invalid){return false;}
        if(!owned()||!ResearchService.financialReady(level().getServer()))return false;
        try{
            switch(request.action()){
                case LOAD_PRIMAL->{
                    if(machine.kind()!=MachineKind.ANALYZER&&machine.kind()!=MachineKind.PRIMAL_CONVERTER)return false;
                    try(var tx=Transaction.openRoot()){
                        long moved=machine.stack(0).getItem() instanceof PrimalCellItem?PrimalCells.transfer(sourceCell,primal,Long.MAX_VALUE,tx):PrimalCells.load(primal,machine.stack(0),64,tx);
                        if(moved==0)return false;tx.commit();machine.guidance(dev.latrox.rematter.guide.GuidanceEvent.SOMETHING_OLDER);machine.setChanged();return true;
                    }
                }
                case CONVERT_SELECT->{
                    if(machine.kind()!=MachineKind.PRIMAL_CONVERTER||converting||request.amount()<250||request.amount()>8000
                            ||ResearchService.conversionClaims(level().getServer(),owner(),machineId).stream().anyMatch(claim->claim.remaining()>0))return false;
                    String previous=conversionTarget;long oldAmount=conversionAmount;conversionTarget=request.target();conversionAmount=request.amount();
                    try{quote();}catch(RuntimeException invalid){conversionTarget=previous;conversionAmount=oldAmount;return false;}
                    conversionProgress=0;conversionId=null;
                }
                case CONVERT_START->{
                    if(machine.kind()!=MachineKind.PRIMAL_CONVERTER||converting||!ResearchService.conversionClaims(level().getServer(),owner(),machineId).stream().noneMatch(c->c.remaining()>0))return false;
                    quote();conversionId=UUID.randomUUID();conversionProgress=0;conversionEnergyPaid=0;converting=true;
                }
                case CONVERT_CANCEL->{
                    if(machine.kind()!=MachineKind.PRIMAL_CONVERTER)return false;
                    if(conversionEnergyPaid>0){
                        long restored=Math.min(machine.energyCapacity(),machine.energy().getAmountAsLong()+conversionEnergyPaid);
                        machine.energy().set((int)restored);conversionEnergyPaid=0;
                    }
                    converting=false;conversionProgress=0;machine.completeNetworkTransfer();
                }
                case ASSAY->{if(machine.kind()!=MachineKind.INDUSTRIAL_PREPARATOR||machine.networkJobs())return false;assay=!assay;machine.resetProgress();}
                case DERIVE->{
                    if(machine.kind()!=MachineKind.ANALYZER)return false;
                    var begun=engine().beginDerive(context(),request.target(),network(),deriveEnergy(),deriveTicks());if(begun.isEmpty())return false;
                    researchId=begun.get().id();researchRunning=true;machine.resetProgress();
                }
                case DISCOVER->{
                    if(machine.kind()!=MachineKind.ANALYZER)return false;
                    var begun=engine().beginDiscovery(context(),DiscoveryTier.of(Math.toIntExact(request.amount())),lens(),discoveryEnergy());if(begun.isEmpty())return false;
                    researchId=begun.get().id();researchRunning=true;machine.resetProgress();
                }
                case RESEARCH_PAUSE->{if(machine.kind()!=MachineKind.ANALYZER||researchId==null)return false;engine().pause(context(),researchId);researchRunning=false;}
                case RESEARCH_RESUME->{
                    if(machine.kind()!=MachineKind.ANALYZER)return false;
                    UUID id=request.target().isEmpty()?researchId:UUID.fromString(request.target());if(id==null||engine().resume(context(),id).isEmpty())return false;researchId=id;researchRunning=true;
                }
                case RESEARCH_REFUND->{
                    if(machine.kind()!=MachineKind.ANALYZER)return false;
                    if(!checkpoint(false))return false;
                    long total=0;
                    for(var claim:engine().refundClaims(context()))total+=engine().claimRefund(context(),claim,PrimalCells.view(primal.stack()).capacity()-PrimalCells.view(primal.stack()).amount(),
                            (op,amount,tx)->{var prepared=primal.prepareRefund(op,amount);if(prepared.isEmpty())return 0;ResearchService.recordCellPayment(level().getServer(),owner(),prepared.get(),amount,tx);return prepared.get().apply(tx)?amount:0;});
                    if(total==0)return false;
                }
                default->{return false;}
            }
            machine.setChanged();return true;
        }catch(IllegalArgumentException|ArithmeticException invalid){return false;}
    }
    JsonObject view(){
        var out=new JsonObject();out.addProperty("blockX",machine.getBlockPos().getX());out.addProperty("blockY",machine.getBlockPos().getY());out.addProperty("blockZ",machine.getBlockPos().getZ());
        out.addProperty("customName",machine.customName());
        var ast=machine.anchorStatus();
        out.addProperty("anchorInstalled",machine.hasAnchorUpgrade());
        out.addProperty("anchorActive",ast.active());
        out.addProperty("anchorForced",ast.forcedChunks());
        out.addProperty("anchorTotal",ast.ownerTotalChunks());
        out.addProperty("anchorMax",ast.maxChunks());
        out.addProperty("anchorReason",ast.reason());
        out.addProperty("matterRate",network()?machine.matterTransferRate():128);out.addProperty("assay",assayMode());out.addProperty("recoveryMask",machine.recoveryMask());
        var reserve=PrimalCells.view(primal.stack());out.addProperty("primal",reserve.amount());out.addProperty("primalCapacity",reserve.capacity());out.addProperty("primalReadable",reserve.readable());
        if(machine.kind()==MachineKind.DRIVE){out.addProperty("primal",machine.drivePrimalAmount());out.addProperty("primalCapacity",machine.drivePrimalCapacity());}
        out.addProperty("fuelRemaining",burner.remaining());out.addProperty("generationRate",generationRate());out.addProperty("fuelEnabled",!CrystalFuelData.fuels().isEmpty());out.add("fuelIds",JSON.toJsonTree(CrystalFuelData.fuels().keySet()));
        out.addProperty("conversionTarget",conversionTarget);out.addProperty("conversionAmount",conversionAmount);out.addProperty("converting",converting);out.addProperty("researchRunning",researchRunning);
        out.addProperty("researchEnabled",ResearchConfig.enabled());
        if(IndustrialStructure.isIndustrial(machine.kind())){
            for(var entry:industrialInitView().entrySet())out.add(entry.getKey(),entry.getValue());
        }
        if(machine.kind()==MachineKind.PRIMAL_REACTOR){
            out.addProperty("primalStability",primalStability);
            out.addProperty("primalMaxStability",primalMaxStability());
            out.addProperty("primalSpeedPct",PrimalReactorRecipes.speedMultiplierPercent(primalStability));
            out.addProperty("primalYieldPct",PrimalReactorRecipes.yieldBonusPercent(primalStability));
            var net=machine.matterNetwork(false);
            out.addProperty("primalTotal",Math.addExact(reserve.readable()?reserve.amount():0L,net.primalAmount()));
            out.addProperty("primalTotalCap",Math.addExact(reserve.readable()?reserve.capacity():0L,net.primalCapacity()));
        }
        if(machine.kind().isEnergyMatrix()){
            var perf=machine.industrialStructureReady()?IndustrialStructure.inspect(machine).performance():IndustrialPerformance.EMPTY;
            out.addProperty("matrixEnergy",matrixEnergy);
            out.addProperty("matrixCapacity",perf.matrixCapacity());
            out.addProperty("matrixTransferRate",perf.matrixTransferRate());
            out.addProperty("matrixInRate",matrixLastInRate);
            out.addProperty("matrixOutRate",matrixLastOutRate);
            out.addProperty("matrixAutoOutput",matrixAutoOutput);
            out.addProperty("matrixCells",perf.matrixCells());
            out.addProperty("matrixProviders",perf.matrixProviders());
        }
        if(machine.kind().isEnergyReactor()){
            var perf=machine.industrialStructureReady()?IndustrialStructure.inspect(machine).performance():IndustrialPerformance.EMPTY;
            out.addProperty("reactorEnergy",reactorEnergy);
            out.addProperty("reactorCapacity",perf.reactorCapacity());
            out.addProperty("reactorMaxOutRate",perf.reactorMaxOutputRate());
            out.addProperty("reactorGenRate",reactorLastGenRate);
            out.addProperty("reactorOutRate",reactorLastOutRate);
            out.addProperty("reactorFlux",reactorFlux);
            out.addProperty("reactorFuelMode",reactorFuelMode.id());
            out.addProperty("reactorMatterFilter",reactorMatterFilter.id());
            out.addProperty("reactorCoolantMode",reactorCoolantMode.id());
            out.addProperty("reactorAllowNetPrimal",reactorAllowNetworkPrimal);
            out.addProperty("reactorCooled",reactorCooled);
            out.addProperty("reactorActiveFuel",reactorActiveFuel);
            out.addProperty("reactorActiveCoolant",reactorActiveCoolant);
            var activeCoolantTier = "rematter:nitrogen".equals(reactorActiveCoolant) ? EnergyReactorRecipes.CoolantTier.NITROGEN
                    : "rematter:water".equals(reactorActiveCoolant) ? EnergyReactorRecipes.CoolantTier.WATER
                    : EnergyReactorRecipes.CoolantTier.NONE;
            out.addProperty("reactorCoolantBonusPct",activeCoolantTier.outputBonusPercent());
            out.addProperty("reactorCoolantDiscountPct",activeCoolantTier.fuelDiscountPercent());
            out.addProperty("reactorFuelCores",perf.fuelCores());
            out.addProperty("reactorInductionCoils",perf.inductionCoils());
            out.addProperty("reactorCoilBonusPct",EnergyReactorRecipes.coilBonusPercent(perf.inductionCoils()));
            out.addProperty("reactorRunning",machine.running());
            var net=machine.matterNetwork(false);
            out.addProperty("primalTotal",Math.addExact(reserve.readable()?reserve.amount():0L,net.ready()?net.primalAmount():0L));
        }
        if(!owned())return out;
        if(machine.kind().synthesis()){
            var knownTargets=new JsonArray();
            for(var choice:MachineChoices.forKind(machine.kind(),ReMatter.chemistry())){String target=choice.id().startsWith(MachineChoices.FLUID_PREFIX)?choice.id().substring(MachineChoices.FLUID_PREFIX.length()):choice.id();if(machine.researchAllows(target))knownTargets.add(target);}
            out.add("knownTargets",knownTargets);
        }
        var known=ResearchService.knownSubstances(level(),owner());var targets=new JsonArray();
        if(machine.kind()==MachineKind.PRIMAL_CONVERTER){
            var claims=ResearchService.conversionClaims(level().getServer(),owner(),machineId);
            out.add("conversionClaims",JSON.toJsonTree(claims));
            for(var key:PrimalPricing.targets(ReMatter.chemistry(),id->true)){
                var quote=PrimalPricing.quote(ReMatter.chemistry(),key,conversionAmount,id->true);var row=new JsonObject();
                row.addProperty("id",key.id().toString());row.addProperty("known",known.stream().anyMatch(k->k.id().equals(key.id())));
                row.addProperty("primal",quote.primal());row.addProperty("energy",quote.energy());row.addProperty("ticks",quote.ticks());
                row.addProperty("ratio",PrimalPricing.grade(key.id()).orElseThrow().primalPerUnit);targets.add(row);
            }
            var pending=claims.stream().filter(claim->claim.remaining()>0).findFirst();
            if(pending.isPresent())out.add("conversionDestination",ConverterDestination.observe(machine.matterNetwork(true),pending.get().target(),pending.get().remaining()).json());
            else if(!conversionTarget.isEmpty()){
                try{var quote=quote();out.add("conversionDestination",ConverterDestination.observe(machine.matterNetwork(true),quote.target(),quote.output()).json());}
                catch(IllegalArgumentException|ArithmeticException invalid){out.add("conversionDestination",ConverterDestination.missing("choose_target").json());}
            }else out.add("conversionDestination",ConverterDestination.missing("choose_target").json());
        }
        out.add("targets",targets);
        if(machine.kind()==MachineKind.ANALYZER){
            var materials=new JsonArray();
            for(var material:ResearchService.materials(level().getServer(),owner())){
                var row=JSON.toJsonTree(material).getAsJsonObject();var substances=new JsonArray();
                for(var key:material.substances()){var substance=new JsonObject();substance.addProperty("id",key.id().toString());substance.addProperty("phase",key.phase().name().toLowerCase(Locale.ROOT));substance.addProperty("known",known.contains(key));substances.add(substance);}
                row.add("substances",substances);materials.add(row);
            }
            out.add("materials",materials);out.addProperty("knownCount",ResearchService.knownFamilies(level().getServer(),owner()).size());
            var discovery=new JsonArray();for(var tier:DiscoveryTier.values())discovery.add(engine().available(context(),tier));out.add("discovery",discovery);
            out.addProperty("deriveEnergy",deriveEnergy());out.addProperty("deriveTicks",deriveTicks());out.addProperty("discoveryStepEnergy",discoveryEnergy());out.addProperty("lens",lens());
            out.addProperty("discoveryAllowed",lens()&&ResearchService.ledger(level().getServer()).discoveryAllowed(context(),ResearchConfig.discoveryMinimum()));
            out.addProperty("deriveReady",network()&&reserve.readable());out.addProperty("researchEnabled",ResearchConfig.enabled());out.addProperty("refundDue",engine().refundDue(context()));
            if(researchId!=null)engine().session(context(),researchId).ifPresent(session->out.add("research",JSON.toJsonTree(session)));
            out.add("sessions",JSON.toJsonTree(engine().sessions(context())));
        }
        return out;
    }
    JsonObject industrialInitView(){
        var out=new JsonObject();
        if(!IndustrialStructure.isIndustrial(machine.kind()))return out;
        var f=IndustrialStructure.inspect(machine);
        out.addProperty("field",f.performance().field());out.addProperty("precision",f.performance().precision());out.addProperty("processRoom",f.performance().processRoom());
        var bp=machine.industrialBlueprint();
        out.addProperty("blueprintCustom",machine.hasCustomBlueprint());
        out.add("blueprint",bp.toJson());
        var bomArr=new JsonArray();
        for(var req:IndustrialStructure.blueprintBom(machine,bp)){
            var item=new JsonObject();
            item.addProperty("material",req.material().name());
            item.addProperty("blockKey",req.blockKey());
            item.addProperty("required",req.required());
            item.addProperty("missing",req.missing());
            bomArr.add(item);
        }
        out.add("blueprintBom",bomArr);
        return out;
    }
    int generationRate(){return machine.kind()==MachineKind.INDUSTRIAL_CRYSTAL_GENERATOR?512:128;}
    private void tickGenerator(){
        pushEnergy();var work=machine.work();
        if(!machine.running()){work.status="paused";return;}
        if(!machine.redstoneAllowsGenerator(burner.remaining()>0)){work.status="redstone_paused";return;}
        var plan=burner.tick(machine.energyCapacity()-machine.energy().getAmountAsLong(),generationRate(),CrystalFuelData.fuel(machine.stack(0)).orElse(null),machine.stack(0).getCount());
        if(!burner.apply(plan)){work.status="unsupported_data";return;}
        if(plan.changesState()){
            if(plan.consumedItems()>0)machine.consumeRedstonePulse();
            machine.stack(0).shrink(plan.consumedItems());machine.energy().set((int)(machine.energy().getAmountAsLong()+plan.producedEnergy()));work.energyRate=plan.producedEnergy();work.processedThisTick=plan.producedEnergy()>0;work.status="generating";machine.setChanged();
        }
        else work.status=!burner.readable()?"unsupported_data":machine.energy().getAmountAsLong()>=machine.energyCapacity()?"energy_full":"generator_fuel";
    }
    private final EnergyHandler output=new EnergyHandler(){
        public long getAmountAsLong(){return machine.energy().getAmountAsLong();}
        public long getCapacityAsLong(){return machine.energyCapacity();}
        public int insert(int amount,TransactionContext tx){return 0;}
        public int extract(int amount,TransactionContext tx){
            if(amount<0)throw new IllegalArgumentException("Negative energy");if(!machine.networkLive()||!machine.kind().generator())return 0;
            int rate=machine.kind()==MachineKind.INDUSTRIAL_CRYSTAL_GENERATOR?16384:2048;
            return outputBudget.insert(level().getGameTime(),Math.min(amount,(int)machine.energy().getAmountAsLong()),rate,tx,allowed->{energyJournal.updateSnapshots(tx);machine.energy().set((int)machine.energy().getAmountAsLong()-allowed);machine.setChanged();return allowed;});
        }
    };
    EnergyHandler output(){return output;}
    private void pushEnergy(){
        if(machine.energy().getAmountAsLong()==0)return;
        for(var side:Direction.values()){
            if(!machine.allowsPowerOutput(side))continue;var pos=machine.getBlockPos().relative(side);
            if(level().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)continue;
            var handler=level().getCapability(Capabilities.Energy.BLOCK,pos,side.getOpposite());if(handler==null||handler==output)continue;
            MatterCablePower.energizeIfCable(handler);
            try(var tx=Transaction.openRoot()){
                int available=Math.min((int)machine.energy().getAmountAsLong(),machine.kind()==MachineKind.INDUSTRIAL_CRYSTAL_GENERATOR?16384:2048);
                int moved=handler.insert(available,tx);if(moved>0&&output.extract(moved,tx)==moved)tx.commit();
            }
        }
    }
    boolean hasResources(){return burner.remaining()>0||!burner.readable()||conversionEnergyPaid>0||matrixEnergy>0||reactorEnergy>0;}
    void save(ValueOutput out){
        if(machine.kind()==MachineKind.ANALYZER||machine.kind()==MachineKind.PRIMAL_CONVERTER||energyReceiptRevision>0||partialEnergyDebt>0||identityCheckpoint||!checkpointedCells.isEmpty()){
            out.putString("extension_id",machineId.toString());out.putBoolean("identity_checkpoint",identityCheckpoint);out.store("checkpointed_cells",com.mojang.serialization.Codec.STRING.listOf(),checkpointedCells.stream().map(UUID::toString).sorted().toList());out.putLong("energy_receipt_revision",energyReceiptRevision);out.putLong("partial_energy_debt",partialEnergyDebt);
        }
        if(researchId!=null){out.putString("research_session",researchId.toString());out.putBoolean("research_running",researchRunning);}
        if(machine.kind()==MachineKind.PRIMAL_CONVERTER){
            out.putString("conversion_target",conversionTarget);out.putLong("conversion_amount",conversionAmount);out.putBoolean("converting",converting);out.putInt("conversion_progress",conversionProgress);
            if(conversionEnergyPaid>0)out.putLong("conversion_energy_paid",conversionEnergyPaid);
            if(conversionId!=null)out.putString("conversion_id",conversionId.toString());
        }
        if(machine.kind()==MachineKind.PRIMAL_REACTOR&&primalStability>0)out.putInt("primal_stability",primalStability);
        if(machine.kind().isEnergyMatrix()){
            if(matrixEnergy>0)out.putLong("matrix_energy",matrixEnergy);
            if(!matrixAutoOutput)out.putBoolean("matrix_auto_output",false);
        }
        if(machine.kind().isEnergyReactor()){
            if(reactorEnergy>0)out.putLong("reactor_energy",reactorEnergy);
            if(reactorFlux>0)out.putInt("reactor_flux",reactorFlux);
            if(reactorFuelMode!=EnergyReactorRecipes.FuelMode.CRYSTAL)out.putString("reactor_fuel_mode",reactorFuelMode.id());
            if(reactorMatterFilter!=EnergyReactorRecipes.MatterFuelFilter.AUTO)out.putString("reactor_matter_filter",reactorMatterFilter.id());
            if(reactorCoolantMode!=EnergyReactorRecipes.CoolantMode.AUTO)out.putString("reactor_coolant_mode",reactorCoolantMode.id());
            if(!reactorAllowNetworkPrimal)out.putBoolean("reactor_network_primal",false);
            out.store("crystal_burner",CompoundTag.CODEC,burner.save());
        }
        if(assay)out.putBoolean("assay",true);
        if(machine.kind().generator())out.store("crystal_burner",CompoundTag.CODEC,burner.save());
    }
    private static UUID storedIdentity(String raw){
        UUID id=UUID.fromString(raw);
        if(!id.toString().equals(raw))throw new IllegalArgumentException("Noncanonical payment identity");
        return id;
    }
    void load(ValueInput in){
        String rawId=in.getStringOr("extension_id","");
        machineId=rawId.isEmpty()?UUID.randomUUID():storedIdentity(rawId);
        if(!rawId.isEmpty()&&!rawId.equals(machineId.toString()))throw new IllegalArgumentException("Invalid extension identity");
        identityCheckpoint=in.getBooleanOr("identity_checkpoint",false);checkpointedCells.clear();
        var cellIds=in.read("checkpointed_cells",com.mojang.serialization.Codec.STRING.listOf()).orElse(List.of());
        if(cellIds.size()>MachineCellAccess.INVENTORY_SIZE)throw new IllegalArgumentException("Too many cell identities");
        for(var id:cellIds)if(!checkpointedCells.add(storedIdentity(id)))throw new IllegalArgumentException("Duplicate cell identity");
        energyReceiptRevision=in.getLongOr("energy_receipt_revision",0);partialEnergyDebt=in.getLongOr("partial_energy_debt",0);
        if(energyReceiptRevision<0||energyReceiptRevision>262144||partialEnergyDebt<0||partialEnergyDebt>1200000||rawId.isEmpty()&&(identityCheckpoint||energyReceiptRevision>0||partialEnergyDebt>0))throw new IllegalArgumentException("Invalid energy payment watermark");
        researchId=null;conversionId=null;
        if(in.getString("research_session").isPresent())researchId=storedIdentity(in.getString("research_session").orElseThrow());
        researchRunning=in.getBooleanOr("research_running",false);assay=in.getBooleanOr("assay",false);
        conversionTarget=in.getStringOr("conversion_target","");conversionAmount=in.getLongOr("conversion_amount",1000);converting=in.getBooleanOr("converting",false);conversionProgress=in.getIntOr("conversion_progress",0);
        conversionEnergyPaid=in.getLongOr("conversion_energy_paid",0);
        if(in.getString("conversion_id").isPresent())conversionId=storedIdentity(in.getString("conversion_id").orElseThrow());
        if(converting&&conversionId==null||conversionAmount<250||conversionAmount>8000||conversionAmount%250!=0||conversionProgress<0||conversionProgress>12000||conversionEnergyPaid<0||conversionEnergyPaid>1_200_000||(!converting&&conversionEnergyPaid!=0)||researchRunning&&researchId==null)throw new IllegalArgumentException("Invalid retained research or conversion job");
        primalStability=Math.clamp(in.getIntOr("primal_stability",0),0,PrimalReactorRecipes.MAX_STABILITY);
        matrixEnergy=in.getLongOr("matrix_energy",0L);
        if(matrixEnergy<0L)throw new IllegalArgumentException("Invalid matrix energy");
        matrixAutoOutput=in.getBooleanOr("matrix_auto_output",true);
        reactorEnergy=in.getLongOr("reactor_energy",0L);
        if(reactorEnergy<0L)throw new IllegalArgumentException("Invalid reactor energy");
        reactorFlux=Math.clamp(in.getIntOr("reactor_flux",0),0,EnergyReactorRecipes.MAX_FLUX);
        reactorFuelMode=EnergyReactorRecipes.FuelMode.load(in.getStringOr("reactor_fuel_mode","crystal"));
        reactorMatterFilter=EnergyReactorRecipes.MatterFuelFilter.load(in.getStringOr("reactor_matter_filter","auto"));
        reactorCoolantMode=EnergyReactorRecipes.CoolantMode.load(in.getStringOr("reactor_coolant_mode","auto"));
        reactorAllowNetworkPrimal=in.getBooleanOr("reactor_network_primal",true);
        if(machine.kind().generator()||machine.kind().isEnergyReactor())in.read("crystal_burner",CompoundTag.CODEC).ifPresent(burner::load);
    }
}
