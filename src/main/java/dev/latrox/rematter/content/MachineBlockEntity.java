package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.automation.AutomationPort;
import dev.latrox.rematter.automation.OrderLedger;
import dev.latrox.rematter.automation.AutomationWorkerLease;
import dev.latrox.rematter.content.preparation.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/** Server-owned machine state. Inputs remain untouched until the entire output can commit. */
public final class MachineBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int ENERGY_CAPACITY = 1_000_000;
    public static final long MATTER_CAPACITY = 1_000_000;
    private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private MatterInventory matter = new MatterInventory(MATTER_CAPACITY);
    private final MatterCellMount cell = new MatterCellMount();
    private final MatterInventory unavailableCell = new MatterInventory(1);
    private final MachineStorageSession storageSession = new MachineStorageSession();
    private net.minecraft.nbt.Tag preservedMatter;
    private MatterStorageState storageState = MatterStorageState.cell();
    private UUID owner;
    private boolean automation;
    private boolean networkJobs;
    private final UUID networkWorkerId = UUID.randomUUID();
    private MachineAutomationHost automationHost;
    private final AutomationWorkerLease<MachineAutomationHost> networkWorkLease = new AutomationWorkerLease<>();
    private MachineMatterNetwork assignedTopology;
    private String networkWorkSignature = "";
    private MachineRunState runState = MachineRunState.automatic();
    private int selection = -1;
    private String selectedId = "";
    private int progress;
    private int duration;
    private String job = "";
    private AnalysisState analysis = AnalysisState.EMPTY;
    private String status = "idle";
    private String migrationFeedback = "";
    private long migrationFeedbackUntil;
    private ChemistryRegistry lastRegistry;
    private MachineMatterNetwork cachedNetwork;
    private MachineMatterNetwork boundNetwork;
    private long networkCacheUntil;
    private boolean processedThisTick;
    private boolean committingOwnedState;
    private boolean visibleCell;
    private int preparationStage = -1;
    private int publishedPreparationStage = -1;
    private PreparationRecipe lastPreparationRecipe;
    private boolean preparationMixing, publishedPreparationMixing;
    private long nextCrank, manualActiveUntil;
    private int manualStage = -1;
    private boolean manualMixing;
    private boolean industrialFormed;
    private IndustrialStructure.Shape industrialShape;
    private IndustrialStructure.ProtectionClaim industrialClaim;
    private List<IndustrialStructure.Port> industrialPorts = List.of();
    private List<IndustrialStructure.Module> industrialModules = List.of();
    private boolean publishedModuleWorking;
    private long lastIndustrialWorkTick = Long.MIN_VALUE;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, 16_000, 0) {
        @Override public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return isLiveServerMachine() && !PreparationTier.manual(kind()) && industrialStructureReady() ? super.insert(amount, transaction) : 0;
        }
        @Override protected void onEnergyChanged(int previous) { if (!committingOwnedState) setChanged(); }
    };
    private final FluidStacksResourceHandler fluids = new FluidStacksResourceHandler(1, 16_000) {
        @Override public boolean isValid(int index, FluidResource resource) {
            return !networkJobs && (kind() == MachineKind.DECOMPOSER || kind() == MachineKind.SYNTHESIZER)
                    && !resource.isEmpty() && resource.isComponentsPatchEmpty()
                    && ReMatter.chemistry().fluidProfile(BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString()).isPresent();
        }
        @Override protected void onContentsChanged(int index, FluidStack previous) { if (!committingOwnedState) setChanged(); }
    };

    private final ResourceHandler<FluidResource> automatedFluids = new AutomationResourceHandler<>(
            fluids, this::automationAllowed, 1, FluidResource.EMPTY);
    private final RootCommitJournal inputTransferCommit = new RootCommitJournal(() -> {
        analysis = AnalysisState.EMPTY;
        runState = MachineRunState.automatic();
        setChanged();
    });

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MACHINE_ENTITY.get(), pos, state);
        items = NonNullList.withSize(MachineCellAccess.inventorySize(kind()), ItemStack.EMPTY);
        if (supportsBatchControl()) runState = MachineRunState.manual();
        if (PreparationTier.manual(kind())) runState = MachineRunState.manual();
        if (kind().isStorage()) matter = new MatterInventory(1);
    }
    private boolean supportsBatchControl() {
        return kind() == MachineKind.SYNTHESIZER || kind() == MachineKind.ELECTROLYZER
                || kind() == MachineKind.REACTOR || kind() == MachineKind.INDUSTRIAL_REACTOR;
    }
    public MachineKind kind() { return ((MachineBlock) getBlockState().getBlock()).kind(); }
    public SimpleEnergyHandler energy() { return energy; }
    public FluidStacksResourceHandler fluids() { return fluids; }
    public ResourceHandler<FluidResource> automatedFluids() { return automatedFluids; }
    public ResourceHandler<ItemResource> automatedItems(Direction side) {
        return new AutomationResourceHandler<>(new WorldlyContainerWrapper(this, Objects.requireNonNull(side)), this::automationAllowed,
                kind().isStorage() || IndustrialStructure.isIndustrial(kind()) ? 0 : PreparationTier.isPreparation(kind()) ? 3 : 2, ItemResource.EMPTY);
    }
    ResourceHandler<ItemResource> industrialItems() {
        return new IndustrialItemHandler(VanillaContainerWrapper.of(this), this::automationAllowed, PreparationTier.industrial(kind()));
    }
    public boolean automation() { return automation; }
    private boolean automationAllowed() {
        return automation && !networkJobs && isLiveServerMachine() && industrialStructureReady();
    }
    private boolean industrialStructureReady() { return !IndustrialStructure.isIndustrial(kind()) || IndustrialStructure.inspect(this).formed(); }
    public boolean industrialFormed() { return level instanceof ServerLevel ? IndustrialStructure.inspect(this).formed() : industrialFormed; }
    private IndustrialStructure.Shape visibleIndustrialShape() {
        if(level instanceof ServerLevel){var current=IndustrialStructure.inspect(this);return current.formed()?current.shape():null;}
        return industrialFormed ? industrialShape : null;
    }
    public int industrialWidth() { var shape=visibleIndustrialShape(); return shape==null?0:shape.width(); }
    public int industrialHeight() { var shape=visibleIndustrialShape(); return shape==null?0:shape.height(); }
    public int industrialDepth() { var shape=visibleIndustrialShape(); return shape==null?0:shape.depth(); }
    public int industrialControllerColumn() { var shape=visibleIndustrialShape(); return shape==null?0:shape.controllerColumn(); }
    IndustrialStructure.@Nullable ProtectionClaim industrialProtectionClaim() { return industrialClaim; }
    /** Placement creates a new physical installation, even when a player copied full block data. */
    public void clearIndustrialProtectionClaim() {
        if(industrialClaim!=null){industrialClaim=null;setChanged();}
    }
    private void refreshIndustrialFormation() {
        if (!IndustrialStructure.isIndustrial(kind())) return;
        var current = IndustrialStructure.inspect(this);
        if(current.formed()) {
            var claim=new IndustrialStructure.ProtectionClaim(current.shape(),getBlockState().getValue(MachineBlock.FACING));
            if(!claim.equals(industrialClaim)){industrialClaim=claim;setChanged();}
        }
        if (industrialFormed != current.formed() || !Objects.equals(industrialShape,current.shape()) || !industrialPorts.equals(current.ports())
                || !industrialModules.equals(current.modules())) {
            setModulesWorking(industrialModules,false);
            publishedModuleWorking=false;
            var changed = new HashSet<BlockPos>();
            industrialPorts.forEach(port -> changed.add(port.position()));
            current.ports().forEach(port -> changed.add(port.position()));
            industrialFormed = current.formed(); industrialShape=current.shape(); industrialPorts = current.ports(); industrialModules=current.modules();
            changed.stream().filter(level::hasChunkAt).forEach(level::invalidateCapabilities);
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }
    /** Visual authority only: a module never owns an inventory, energy buffer or persistent association. */
    public boolean isModuleWorking(BlockPos modulePosition) {
        if(!isLiveServerMachine() || !IndustrialStructure.isIndustrial(kind()) || !processedThisTick
                || lastIndustrialWorkTick < level.getGameTime()-1) return false;
        var formation=IndustrialStructure.inspect(this);
        return formation.formed() && formation.modules().stream().anyMatch(module->module.position().equals(modulePosition));
    }
    private void setModulesWorking(List<IndustrialStructure.Module> modules,boolean active) {
        if(!(level instanceof ServerLevel server) || !server.getServer().isSameThread())return;
        for(var module:modules)if(server.hasChunkAt(module.position())
                && server.getBlockEntity(module.position()) instanceof IndustrialModuleBlockEntity entity)entity.setWorking(this,active);
    }
    private void publishIndustrialActivity() {
        if(!IndustrialStructure.isIndustrial(kind()))return;
        boolean active=processedThisTick && isLiveServerMachine() && industrialStructureReady();
        if(active)lastIndustrialWorkTick=level.getGameTime();
        // Rebind reloaded/replaced module BEs even if their positions and the controller's work state stayed identical.
        // The module setter writes blockstate/update data only when its visible state actually changes.
        if(active || publishedModuleWorking){
            setModulesWorking(industrialModules,active);
            publishedModuleWorking=active;
        }
    }
    @Override public void setRemoved() {
        releaseNetworkWork();
        if (automationHost != null) automationHost.close();
        processedThisTick=false;lastIndustrialWorkTick=Long.MIN_VALUE;
        setModulesWorking(industrialModules,false);
        super.setRemoved();
    }
    private boolean isLiveServerMachine() {
        if (isRemoved() || level == null || level.isClientSide()) return false;
        var server = level.getServer();
        return server != null && server.isSameThread() && level.hasChunkAt(worldPosition)
                && level.getBlockEntity(worldPosition) == this;
    }
    // Package-local discovery access never exposes a mutable aggregate to external callers.
    boolean networkLive() { return isLiveServerMachine(); }
    UUID networkOwner() { return owner; }
    String networkStorageStatus() { return storageStatus(); }
    boolean networkUsesCell() { return kind().isStorage() && !legacyInternalStorage(); }
    public void claim(Player player) { if (owner == null) { owner = player.getUUID(); setChanged(); if(level!=null)level.invalidateCapabilities(worldPosition); } }
    public boolean canAccess(Player player) { return owner == null || owner.equals(player.getUUID()); }
    public @Nullable MachineSnapshot snapshotFor(Player player) { return canAccess(player) ? snapshot(-1) : null; }
    public MatterInventory matterInventory() {
        if (kind().isStorage() && !legacyInternalStorage()) return cell.inventory() == null ? unavailableCell : cell.inventory();
        return matter;
    }
    private boolean legacyInternalStorage() {
        return kind() == MachineKind.STORAGE && storageState.mode().orElse(null) == MatterStorageState.Mode.LEGACY_INTERNAL;
    }
    public int cellSlot() { return MachineCellAccess.slot(kind()); }
    private boolean hasRecoveryCell() { return MachineCellAccess.hasLocalSlot(kind()) && !items.get(cellSlot()).isEmpty(); }
    private ItemStack cellStack() { return cellSlot() < 0 ? ItemStack.EMPTY : items.get(cellSlot()); }
    public boolean chemicalAutomationAllowed() {
        return kind().isStorage() && storageStatus().equals("ready") && automationAllowed();
    }
    public boolean canRemoveCell() {
        return cellSlot() < 0 || cell.canRemove() && (!legacyInternalStorage() || matter.snapshot().reservations().isEmpty());
    }
    public boolean prepareCellRemoval() {
        if (!canRemoveCell()) return false;
        if (cellSlot() >= 0) cell.flush();
        return true;
    }
    private void mountCell() {
        cell.mount(cellStack());
        migrationFeedback = "";
        if (MachineCellAccess.hasLocalSlot(kind())) {
            runState = runState.pause();
            resetProgress();
            processedThisTick = false;
        }
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        setChanged();
    }
    private String storageStatus() {
        if (kind().isStorage() && (preservedMatter != null || storageState.mode().isEmpty())) return "unknown_schema";
        if (kind().isStorage() && !legacyInternalStorage()) {
            if (cellStack().isEmpty()) return "missing_cell";
            if (!cell.readable(cellStack())) return "unknown_schema";
        }
        return "ready";
    }
    /** Called only by the authorized open menu. Neither insertion nor world load migrates resources. */
    public boolean migrateLegacyStorageToCell() {
        if (!isLiveServerMachine() || !legacyInternalStorage()) return false;
        if (preservedMatter != null) return migrationRefused("unknown_schema");
        if (!canRemoveCell()) return migrationRefused("migration_busy");
        if (cellStack().isEmpty()) return migrationRefused("missing_cell");
        if (!cell.readable(cellStack())) return migrationRefused("unknown_schema");
        var destination = cell.inventory();
        String readiness = LegacyStorageMigration.readiness(matter, destination);
        if (!readiness.equals("ready")) return migrationRefused(readiness);
        // Prepare the serializable component before the atomic ownership move; no callbacks occur between these steps.
        var prepared = MatterInventory.restore(new MatterInventory.Snapshot(1, destination.capacity(),
                matter.snapshot().contents(), List.of()), destination.maximumTypes());
        var data = MatterCellData.from(prepared);
        if (!LegacyStorageMigration.migrate(matter, destination)) return false;
        cellStack().set(MatterCellData.TYPE.get(), data);
        storageState = MatterStorageState.cell();
        migrationFeedback = "";
        status = "ready";
        level.invalidateCapabilities(worldPosition);
        setChanged();
        return true;
    }
    private boolean migrationRefused(String reason) {
        migrationFeedback = reason;
        migrationFeedbackUntil = level.getGameTime() + 100;
        status = reason;
        return false;
    }
    @Override public void setChanged() {
        if (cell != null && cellSlot() >= 0) cell.flush();
        super.setChanged();
    }
    public boolean hasVisibleCell() {
        return cellSlot() >= 0 && (level != null && level.isClientSide() ? visibleCell : cellStack().getItem() instanceof MatterCellItem);
    }
    public int preparationStage() { return PreparationTier.isPreparation(kind()) ? preparationStage : -1; }
    public boolean preparationMixing() { return PreparationTier.isPreparation(kind()) && preparationMixing; }
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag=new CompoundTag();
        if(cellSlot()>=0)tag.putBoolean("cell_present",hasVisibleCell());
        if(PreparationTier.isPreparation(kind())){tag.putInt("preparation_stage",preparationStage);tag.putBoolean("preparation_mixing",preparationMixing);}
        if(IndustrialStructure.isIndustrial(kind())){
            tag.putBoolean("industrial_formed",industrialFormed);
            if(industrialFormed&&industrialShape!=null){
                tag.putInt("industrial_width",industrialShape.width());tag.putInt("industrial_height",industrialShape.height());
                tag.putInt("industrial_depth",industrialShape.depth());tag.putInt("industrial_column",industrialShape.controllerColumn());
            }
        }
        return tag;
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return cellSlot()>=0 || PreparationTier.isPreparation(kind()) || IndustrialStructure.isIndustrial(kind()) ? net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this) : null;
    }
    @Override public void handleUpdateTag(ValueInput input) {
        if(cellSlot()>=0)visibleCell=input.getBooleanOr("cell_present",false);
        if(PreparationTier.isPreparation(kind())){preparationStage=Math.clamp(input.getIntOr("preparation_stage",-1),-1,2);preparationMixing=input.getBooleanOr("preparation_mixing",false);}
        if(IndustrialStructure.isIndustrial(kind())){
            industrialFormed=input.getBooleanOr("industrial_formed",false);industrialShape=null;
            if(industrialFormed)try{
                industrialShape=new IndustrialStructure.Shape(input.getIntOr("industrial_width",0),input.getIntOr("industrial_height",0),
                        input.getIntOr("industrial_depth",0),input.getIntOr("industrial_column",0));
                if(industrialShape.height()<IndustrialStructure.height(kind()))throw new IllegalArgumentException("Wrong industrial height");
            }catch(IllegalArgumentException invalid){industrialFormed=false;industrialShape=null;}
        }
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ValueInput input) { handleUpdateTag(input); }
    public boolean canUse(Player player) {
        boolean allowed = canAccess(player);
        if (!allowed && !player.level().isClientSide()) player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
        return allowed;
    }
    public boolean hasStoredResources() {
        return automationHost != null && automationHost.hasContents() || preservedMatter != null || kind() == MachineKind.STORAGE && storageState.mode().isEmpty()
                || matter.total() > 0 || !isEmpty() || fluids.getAmountAsLong(0) > 0 || energy.getAmountAsLong() > 0;
    }
    public @Nullable MachineAutomationHost automationHost() {
        if (kind() != MachineKind.NETWORK_INTERFACE) return null;
        if (automationHost == null) automationHost = new MachineAutomationHost(this);
        return automationHost;
    }
    public boolean cycleAutomationBackend() {
        var host = automationHost();
        return host != null && host.cycleBackend();
    }
    public boolean networkJobs() { return networkJobs; }
    public boolean supportsNetworkJobs() { return kind().processes() && kind() != MachineKind.ANALYZER && !PreparationTier.manual(kind()); }
    public boolean toggleNetworkJobs() {
        if (!isLiveServerMachine() || !supportsNetworkJobs()) return false;
        releaseNetworkWork(); networkJobs = !networkJobs;
        runState = runState.pause(); resetProgress(); processedThisTick = false;
        level.invalidateCapabilities(worldPosition); setChanged(); return true;
    }
    UUID networkWorkerId() { return networkWorkerId; }
    boolean acceptsNetworkWork() {
        if (!networkJobs || !supportsNetworkJobs() || !isLiveServerMachine() || !industrialStructureReady()
                || fluids.getAmountAsLong(0) != 0) return false;
        for (int slot = 0; slot < items.size(); slot++) if (slot != 2 && !items.get(slot).isEmpty()) return false;
        return true;
    }
    boolean tryBindNetworkOrder(MachineAutomationHost host, UUID order) {
        if (networkWorkLease.matches(host,order)) return true;
        if (networkWorkLease.current().isPresent() || !acceptsNetworkWork() || !host.active()) return false;
        var topology = matterNetwork(true);
        if (!topology.ready() || !topology.interfaces().contains(host.machine())
                || !networkWorkLease.tryAcquire(host,order)) return false;
        resetProgress(); assignedTopology = topology; networkWorkSignature = "";
        return true;
    }
    boolean hasNetworkOrder(MachineAutomationHost host, UUID order) { return networkWorkLease.matches(host,order); }
    boolean networkOrderFree() { return networkWorkLease.current().isEmpty(); }
    boolean shouldYieldNetworkWork(String backend,boolean otherBackendReady) { return networkWorkLease.shouldYield(backend,otherBackendReady); }
    void releaseNetworkOrder(MachineAutomationHost host, UUID order) {
        if (!networkWorkLease.release(host,order)) return;
        assignedTopology = null; networkWorkSignature = "";
        resetProgress(); processedThisTick = false;
    }
    private void releaseNetworkWork() {
        var claim = networkWorkLease.current().orElse(null);
        if (claim == null) return;
        releaseNetworkWork(claim);
    }
    private void releaseNetworkWork(AutomationWorkerLease.Claim<MachineAutomationHost> claim) {
        claim.owner().release(claim.orderId(), this);
        // Also clear an orphaned local claim, without ever touching another interface's binding.
        releaseNetworkOrder(claim.owner(),claim.orderId());
    }
    private void tickNetworkWork() {
        if (!acceptsNetworkWork()) { releaseNetworkWork(); status = industrialStructureReady() ? "automation_clear_slots" : IndustrialStructure.inspect(this).diagnostic(); return; }
        var claim = networkWorkLease.current().orElse(null);
        if (claim == null) { status = "network_controlled"; return; }
        var assignedHost = claim.owner();var assignedOrder = claim.orderId();
        var order = assignedHost.order(assignedOrder, this);
        var topology = matterNetwork(false);
        if (order == null || !topology.ready() || assignedTopology == null || !assignedTopology.sameTopology(topology)) {
            releaseNetworkWork(claim); status = "storage_changed"; return;
        }
        var recipe = order.recipe();
        long base = recipe.energy();
        if (PreparationTier.industrial(kind())) base = base > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : base * 2;
        long required = cost(base);
        String signature = recipe.revision() + ":" + required + ":" + speed();
        if (IndustrialStructure.isIndustrial(kind())) signature += ":" + IndustrialStructure.inspect(this).layoutFingerprint();
        if (!networkWorkSignature.isEmpty() && !networkWorkSignature.equals(signature)) { releaseNetworkWork(claim); status = "network_controlled"; return; }
        networkWorkSignature = signature;
        if (energy.getAmountAsLong() < required) { status = "insufficient_energy"; return; }
        if (!assignedHost.advance(assignedOrder, this, speed())) { releaseNetworkWork(claim); status = "network_controlled"; return; }
        order = assignedHost.order(assignedOrder, this);
        if (order == null) { releaseNetworkWork(claim); status = "network_controlled"; return; }
        progress = order.progress(); duration = recipe.ticks(); processedThisTick = true;
        preparationStage = PreparationTier.isPreparation(kind()) ? PreparationWork.stage(progress,duration) : -1;
        preparationMixing = PreparationTier.isPreparation(kind()) && recipe.inputs().values().stream().mapToLong(Long::longValue).sum() > 1;
        status = PreparationTier.isPreparation(kind()) ? PreparationWork.stageName(preparationStage,preparationMixing) : "working";
        if (progress >= duration) {
            var fresh = matterNetwork(true);
            if (!fresh.ready() || !assignedTopology.sameTopology(fresh) || !acceptsNetworkWork() || energy.getAmountAsLong() < required) {
                releaseNetworkWork(claim); status = "storage_changed"; return;
            }
            // The ledger owns these inputs. Completion never withdraws the same matter or items again.
            if (!assignedHost.complete(assignedOrder, this)) { releaseNetworkWork(claim); status = "network_controlled"; return; }
            energy.set((int)(energy.getAmountAsLong() - required));
            if(order.remainingBatches()==1)networkWorkLease.completed(assignedHost,assignedOrder,assignedHost.backend());
            assignedHost.changed(); progress = 0; duration = 0;
        }
        setChanged();
    }
    public void toggleAutomation() {
        automation = !automation;
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            industrialPorts.stream().map(IndustrialStructure.Port::position).filter(level::hasChunkAt).forEach(level::invalidateCapabilities);
        }
        setChanged();
    }
    public void toggleRunning() {
        if (networkJobs) { toggleNetworkJobs(); return; }
        if(PreparationTier.manual(kind()))return;
        if (!kind().processes()) return;
        if (selection < 0 && !selectedId.isEmpty()) return;
        runState = runState.toggleRunning();
        setChanged();
    }
    public boolean requestBatch() {
        if (networkJobs || !supportsBatchControl() || selection < 0 || choices().isEmpty()) return false;
        runState = runState.requestBatch();
        setChanged();
        return true;
    }
    public boolean toggleRepeat() {
        if (networkJobs || !supportsBatchControl() || selection < 0 || choices().isEmpty()) return false;
        runState = runState.toggleRepeat();
        setChanged();
        return true;
    }
    public void select(int offset) {
        if (networkJobs) return;
        var options = choices();
        if (!options.isEmpty()) {
            int index = selection < 0 ? (offset < 0 ? options.size() - 1 : 0)
                    : (int) Math.floorMod((long) selection + offset, options.size());
            selectRecipe(options.get(index).id());
        }
    }
    public boolean selectRecipe(String exactId) {
        if (networkJobs) return false;
        var options = choices();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id().equals(exactId)) {
                selection = i;
                if (!selectedId.equals(exactId)) {
                    selectedId = exactId;
                    runState = MachineRunState.manual();
                    resetProgress();
                    setChanged();
                }
                return true;
            }
        }
        return false;
    }

    public void serverTick() {
        if (!isLiveServerMachine()) return;
        // A foreign transfer may roll back its member snapshots. Do not complete unrelated work inside it.
        if (net.neoforged.neoforge.transfer.transaction.Transaction.getCurrentOpenedTransaction() != null) return;
        refreshIndustrialFormation();
        processedThisTick = false;
        if(PreparationTier.isPreparation(kind()))preparationStage=-1;
        try {
            tickMachine();
        } finally {
            if(PreparationTier.isPreparation(kind())&&!processedThisTick)preparationStage=-1;
            BlockState state = getBlockState();
            if (!isRemoved() && state.getValue(MachineBlock.ACTIVE) != processedThisTick) {
                level.setBlock(worldPosition, state.setValue(MachineBlock.ACTIVE, processedThisTick), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
            if(PreparationTier.isPreparation(kind())&&(publishedPreparationStage!=preparationStage||publishedPreparationMixing!=preparationMixing)){
                publishedPreparationStage=preparationStage;
                publishedPreparationMixing=preparationMixing;
                level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
            publishIndustrialActivity();
        }
    }
    public boolean crank(Player player) {
        if(!PreparationTier.manual(kind())||!isLiveServerMachine()||!stillValid(player)||level.getGameTime()<nextCrank)return false;
        // A first click may arrive before the first block tick, or immediately after a datapack reload.
        if(lastRegistry!=ReMatter.chemistry())tickMachine();
        var operation=operation(ReMatter.chemistry());
        if(operation==null){status=inputStatus(ReMatter.chemistry());resetProgress();return false;}
        String reason=operationReadiness(false,null,operation);
        if(!reason.equals("ready")){status=reason;return false;}
        String signature=operation.signature+":"+operation.ticks;
        if(!signature.equals(job)){resetProgress();job=signature;}
        duration=operation.ticks;
        progress=(int)Math.min(duration,(long)progress+PreparationWork.CRANK_WORK);
        manualStage=PreparationWork.stage(progress,duration);
        manualMixing=!lastPreparationRecipe.singleInput();
        nextCrank=level.getGameTime()+PreparationWork.CRANK_COOLDOWN;
        manualActiveUntil=level.getGameTime()+3;
        if(progress>=duration){
            // No callbacks, resource transfer or energy mutation occurs between this recheck and the owned item commit.
            if(!operation.canFinish())return false;
            operation.finish.run();resetProgress();
        }
        setChanged();return true;
    }
    private void tickManualPreparation(ChemistryRegistry registry){
        if(level.getGameTime()<manualActiveUntil){
            processedThisTick=true;preparationStage=manualStage;preparationMixing=manualMixing;
            status=PreparationWork.stageName(preparationStage,preparationMixing);return;
        }
        var operation=operation(registry);
        if(operation==null){status=inputStatus(registry);resetProgress();return;}
        var reason=operationReadiness(false,null,operation);
        status=reason.equals("ready")?"crank":reason;
    }
    private void tickMachine() {
        if (kind() == MachineKind.NETWORK_INTERFACE) { var host = automationHost(); host.tick(); status = host.status(); return; }
        if (networkJobs) { tickNetworkWork(); return; }
        ChemistryRegistry registry = ReMatter.chemistry();
        if (!analysis.isEmpty() && !hasValidAnalysis(registry)) { analysis = AnalysisState.EMPTY; setChanged(); }
        if (registry != lastRegistry) {
            lastRegistry = registry;
            resetProgress();
            var options = choices();
            String previousSelectedId = selectedId;
            if (kind() == MachineKind.SYNTHESIZER && registry.fluidProfile(selectedId).isPresent()
                    && !registry.synthesisPolicy().allowsItem(selectedId)) selectedId = MachineChoices.fluidChoiceId(selectedId);
            var restored = RecipeSelection.restore(selectedId, options.stream().map(MachineChoices.Choice::id).toList(),
                    !supportsBatchControl() || runState.repeat());
            selection = restored.index();
            selectedId = restored.id();
            if (!selectedId.equals(previousSelectedId)) setChanged();
            if (restored.unavailable()) { runState = runState.pause(); status = "selection_unavailable"; setChanged(); }
        }
        if (selection < 0 && !selectedId.isEmpty()) {
            if (runState.running()) { runState = runState.pause(); setChanged(); }
            status = "selection_unavailable";
            return;
        }
        if (supportsBatchControl() && selection < 0) { status = "choose_process"; return; }
        if(PreparationTier.manual(kind())){tickManualPreparation(registry);return;}
        if (kind().isStorage()) {
            status = !migrationFeedback.isEmpty() && level.getGameTime() < migrationFeedbackUntil ? migrationFeedback : storageStatus();
            return;
        }
        if (kind() == MachineKind.TERMINAL) {
            status = matterNetwork(false).status();
            return;
        }
        if (!industrialStructureReady()) {
            String diagnostic = IndustrialStructure.inspect(this).diagnostic();
            if (boundNetwork != null) pauseChangedNetwork(diagnostic); else status = diagnostic;
            return;
        }
        // Analysis and physical preparation are independent of the matter network.
        boolean needsStorage = kind() != MachineKind.ANALYZER && !PreparationTier.isPreparation(kind());
        MachineMatterNetwork network = needsStorage ? matterNetwork(false) : null;
        MatterStore store = network == null ? null : network.inventory();
        if (network != null && !network.ready()) {
            if (boundNetwork != null) pauseChangedNetwork(network.status()); else status = network.status();
            return;
        }
        if (network != null && !acceptsNetwork(network)) {
            pauseChangedNetwork("storage_changed");
            return;
        }
        if (!runState.running()) {
            if (kind() == MachineKind.ANALYZER) {
                status = hasValidAnalysis(registry) ? "analyzed"
                        : analyzableSample(registry) ? "analysis_needed" : inputStatus(registry);
            } else status = supportsBatchControl() && !runState.repeat() && runState.queuedBatches() == 0
                    ? "awaiting_request" : "paused";
            return;
        }
        Operation operation = operation(registry);
        if (operation == null) { status = inputStatus(registry); resetProgress(); return; }
        String fingerprint = operation.signature + ":" + operation.cost + ":" + operation.ticks + ":" + speed();
        if(IndustrialStructure.isIndustrial(kind()))fingerprint += ":" + IndustrialStructure.inspect(this).layoutFingerprint();
        if (!fingerprint.equals(job)) { progress = 0; job = fingerprint; }
        duration = operation.ticks;
        String reason = operationReadiness(needsStorage,store,operation);
        if (!reason.equals("ready")) { status = reason; return; }
        if (store != null) { storageSession.bind(store); boundNetwork = network; }
        progress = (int) Math.min(duration, (long) progress + speed());
        processedThisTick = true;
        preparationStage=PreparationTier.isPreparation(kind())?PreparationWork.stage(progress,duration):-1;
        preparationMixing=PreparationTier.isPreparation(kind())&&!lastPreparationRecipe.singleInput();
        status = PreparationTier.isPreparation(kind()) ? PreparationWork.stageName(preparationStage,preparationMixing) : "working";
        setChanged();
        if (progress < duration) return;
        if(!industrialStructureReady()){status=IndustrialStructure.inspect(this).diagnostic();processedThisTick=false;return;}
        // A fresh bounded graph must still have the same owners, routes and mounted inventories.
        if (needsStorage) {
            var finalNetwork = matterNetwork(true);
            if (!finalNetwork.ready() || !acceptsNetwork(finalNetwork)) {
                pauseChangedNetwork(finalNetwork.ready() ? "storage_changed" : finalNetwork.status());
                return;
            }
            network = finalNetwork;
            store = finalNetwork.inventory();
        }
        reason = operationReadiness(needsStorage, store, operation);
        if (!reason.equals("ready")) { status = reason; processedThisTick = false; return; }
        boolean changedOwnedState = false;
        committingOwnedState = true;
        try {
            if (needsStorage && !store.apply(operation.inputs, operation.outputs)) {
                processedThisTick = false;
                status = MachineReadiness.check(true, store, operation.inputs, operation.outputs, true,
                        energy.getAmountAsLong(), operation.cost);
                return;
            }
            // Both callbacks below are deferred until matter, physical resources, FE and the queue agree.
            changedOwnedState = true;
            operation.finish.run();
            energy.set((int) (energy.getAmountAsLong() - operation.cost));
            if (supportsBatchControl()) runState = runState.completed();
            resetProgress();
            status = kind() == MachineKind.ANALYZER ? "analyzed" : "ready";
            if (kind() == MachineKind.ANALYZER) runState = runState.pause();
        } finally {
            committingOwnedState = false;
            if (changedOwnedState) {
                // Also flush actual authoritative changes if an unexpected exception interrupts finalization.
                if (network != null) network.markChanged();
                setChanged();
            }
        }
    }

    private String operationReadiness(boolean needsStorage,MatterStore store,Operation operation){
        if(PreparationTier.isPreparation(kind()))return PreparationWork.readiness(!items.get(0).isEmpty(),!items.get(3).isEmpty(),!lastPreparationRecipe.singleInput(),
                items.get(0).getComponentsPatch().isEmpty()&&items.get(3).getComponentsPatch().isEmpty(),true,
                operation.canFinish(),energy.getAmountAsLong(),operation.cost);
        return MachineReadiness.check(needsStorage,store,operation.inputs,operation.outputs,
                operation.canFinish(),energy.getAmountAsLong(),operation.cost);
    }
    private String inputStatus(ChemistryRegistry registry) {
        if(PreparationTier.isPreparation(kind())){
            if(items.get(0).isEmpty())return "input";
            if(!items.get(0).getComponentsPatch().isEmpty()||!items.get(3).getComponentsPatch().isEmpty())return "unsupported_components";
            if(!(level instanceof ServerLevel server))return "unsupported_input";
            var candidates=PreparationRecipes.serverRecipes(server).stream().filter(e->e.recipe().acceptsPrimary(items.get(0))).toList();
            if(candidates.isEmpty())return "unsupported_input";
            if(candidates.stream().noneMatch(e->PreparationTier.permits(kind(),e.minimumTier())))return "preparation_tier";
            if(candidates.stream().anyMatch(e->e.recipe().singleInput())&&!items.get(3).isEmpty())return "secondary_slot_occupied";
            return items.get(3).isEmpty()?"secondary_input":"unsupported_input";
        }
        if (supportsBatchControl()) return "choose_process";
        ItemStack input = items.get(0);
        if (!input.isEmpty()) {
            if (!input.getComponentsPatch().isEmpty()) return "unsupported_components";
            var profile = registry.profile(BuiltInRegistries.ITEM.getKey(input.getItem()).toString()).orElse(null);
            if (profile == null) return "unsupported_input";
            if (kind() == MachineKind.DECOMPOSER && input.getCount() < profile.itemCount()) return "input_batch";
        }
        if (kind() == MachineKind.DECOMPOSER && !fluids.getResource(0).isEmpty()) {
            var fluid = fluids.getResource(0);
            if (!fluid.isComponentsPatchEmpty()) return "unsupported_components";
            var profile = registry.fluidProfile(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()).orElse(null);
            return profile == null ? "unsupported_input" : "input_batch";
        }
        return kind() == MachineKind.ANALYZER ? "insert_sample" : "input";
    }

    private Operation operation(ChemistryRegistry registry) {
        if(PreparationTier.isPreparation(kind())){
            var holder=preparationRecipe();if(holder==null)return null;
            var recipe=holder.value();var result=recipe.result();
            if(recipe!=lastPreparationRecipe){resetProgress();lastPreparationRecipe=recipe;}
            return new Operation(holder.id().identifier().toString(),Map.of(),Map.of(),preparationCost(recipe),recipe.ticks(),
                    ()->recipe.matches(new PreparationInput(items.get(0),items.get(3)),level)&&fitsOutput(result),
                    ()->{items.get(0).shrink(1);if(!recipe.singleInput())items.get(3).shrink(1);
                        if(items.get(1).isEmpty())items.set(1,result);else items.get(1).grow(result.getCount());});
        }
        if (kind() == MachineKind.ANALYZER || kind() == MachineKind.DECOMPOSER) {
            ItemStack input = items.get(0);
            if (!input.isEmpty() && input.getComponentsPatch().isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(input.getItem()).toString();
                var profile = registry.profile(id).orElse(null);
                if (profile != null) {
                    boolean analyze = kind() == MachineKind.ANALYZER;
                    int requiredItems = analyze ? 1 : profile.itemCount();
                    if (input.getCount() < requiredItems) return null;
                    return new Operation("item:" + id + ":" + requiredItems, Map.of(), analyze ? Map.of() : profile.matter(),
                            cost(analyze ? 500 : profile.decompositionEnergy()), analyze ? 40 : profile.ticks(),
                            () -> items.get(0).getCount() >= requiredItems && items.get(0).getItem() == input.getItem() && items.get(0).getComponentsPatch().isEmpty(),
                            () -> { if (analyze) analysis = AnalysisState.completed(profile); else items.get(0).shrink(requiredItems); });
                }
            }
            if (kind() == MachineKind.DECOMPOSER) {
                FluidResource fluid = fluids.getResource(0);
                var conversion = registry.fluidProfile(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()).orElse(null);
                if (conversion != null && fluid.isComponentsPatchEmpty() && fluids.getAmountAsLong(0) >= conversion.amount()) {
                    return new Operation("fluid:" + conversion.fluidId(), Map.of(), conversion.matter(),
                            cost(conversion.decompositionEnergy()), conversion.ticks(),
                            () -> fluids.getResource(0).equals(fluid) && fluids.getAmountAsLong(0) >= conversion.amount(),
                            () -> fluids.set(0, fluid, (int) fluids.getAmountAsLong(0) - conversion.amount()));
                }
            }
            return null;
        }
        var options = choices();
        if (options.isEmpty() || selection < 0) return null;
        MachineChoices.Choice choice = options.get(Math.floorMod(selection, options.size()));
        if (kind() == MachineKind.SYNTHESIZER) {
            var conversion = choice.id().startsWith("fluid:") ? registry.fluidProfile(choice.id().substring(6)).orElse(null) : null;
            if (conversion != null) {
                if (!registry.synthesisPolicy().allowsFluid(conversion.fluidId())) return null;
                var resource = FluidResource.of(BuiltInRegistries.FLUID.getValue(Identifier.parse(conversion.fluidId())));
                return new Operation(choice.id(), conversion.matter(), Map.of(),
                        cost(conversion.synthesisEnergy()), conversion.ticks(),
                        () -> (fluids.getResource(0).isEmpty() || fluids.getResource(0).equals(resource)) && fluids.getAmountAsLong(0) <= 16_000 - conversion.amount(),
                        () -> fluids.set(0, resource, (int) fluids.getAmountAsLong(0) + conversion.amount()));
            }
            if (!registry.synthesisPolicy().allowsItem(choice.id())) return null;
            var profile = registry.profile(choice.id()).orElse(null);
            Identifier outputId = Identifier.parse(choice.id());
            if (!BuiltInRegistries.ITEM.containsKey(outputId)) return null;
            Item output = BuiltInRegistries.ITEM.getValue(outputId);
            if (profile == null || output == null) return null;
            var result = new ItemStack(output, profile.itemCount());
            return new Operation(choice.id() + ":" + profile.itemCount(), profile.matter(), Map.of(), cost(profile.synthesisEnergy()), profile.ticks(),
                    () -> fitsOutput(result), () -> {
                        if (items.get(1).isEmpty()) items.set(1, result);
                        else items.get(1).grow(result.getCount());
                    });
        }
        var recipe = registry.recipe(SubstanceId.parse(choice.id())).orElse(null);
        if (recipe == null) return null;
        return new Operation(choice.id(), recipe.inputs(), recipe.outputs(), cost(recipe.energy()), recipe.ticks(), () -> true, () -> {});
    }

    private int speed() {
        if(PreparationTier.manual(kind()))return 1;
        int base = kind() == MachineKind.INDUSTRIAL_REACTOR ? 2 : PreparationTier.speed(kind());
        if(IndustrialStructure.isIndustrial(kind())){
            var formation=IndustrialStructure.inspect(this);
            if(formation.formed())base=formation.baseWorkRate(kind());
        }
        return base * (items.get(2).is(ModContent.SPEED_UPGRADE.get()) ? 1 + Math.min(4, items.get(2).getCount()) : 1);
    }
    private net.minecraft.world.item.crafting.RecipeHolder<PreparationRecipe> preparationRecipe(){
        if(!(level instanceof ServerLevel server))return null;
        // Preserve the server's recipe priority, skipping recipes unavailable to this machine tier.
        return server.getServer().getRecipeManager().recipeMap()
                .getRecipesFor(PreparationRecipes.TYPE.get(),new PreparationInput(items.get(0),items.get(3)),server)
                .filter(holder->PreparationTier.permits(kind(),holder.value().minimumTier())).findFirst().orElse(null);
    }
    private long preparationCost(PreparationRecipe recipe){return cost(Math.multiplyExact((long)recipe.energy(),PreparationTier.energyMultiplier(kind())));}
    private long cost(long base) {
        // Speed pays more total energy, efficiency has a bounded 20% maximum saving.
        if (base > ENERGY_CAPACITY) return base;
        int count = Math.min(4, items.get(2).getCount());
        if (items.get(2).is(ModContent.SPEED_UPGRADE.get())) return Math.addExact(base, Math.multiplyExact(base, count) / 4);
        if (items.get(2).is(ModContent.EFFICIENCY_UPGRADE.get())) return base - (base / 20) * count;
        return base;
    }
    private boolean fitsOutput(ItemStack result) {
        ItemStack output = items.get(1);
        int maximum = Math.min(getMaxStackSize(), result.getMaxStackSize());
        return result.getCount() <= maximum && (output.isEmpty()
                || (ItemStack.isSameItemSameComponents(output, result) && result.getCount() <= maximum - output.getCount()));
    }
    private void resetProgress() { progress = 0; duration = 0; job = ""; storageSession.clear(); boundNetwork = null; }

    public List<MachineChoices.Choice> choices() { return MachineChoices.forKind(kind(), ReMatter.chemistry()); }
    private record Operation(String signature, Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                             long cost, int ticks, java.util.function.BooleanSupplier ready, Runnable finish) {
        boolean canFinish() { return ready.getAsBoolean(); }
    }
    private static String pretty(String id) {
        String value = id.replace('_', ' ');
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    MachineMatterNetwork matterNetwork(boolean force) {
        if (force || cachedNetwork == null || level == null || level.getGameTime() >= networkCacheUntil || !cachedNetwork.stillValid(this)) {
            cachedNetwork = MachineMatterNetwork.discover(this);
            networkCacheUntil = level == null ? 0 : level.getGameTime() + 20;
        }
        return cachedNetwork;
    }
    /** Recovery parcels restore their real quantity; unknown definitions remain storable by stable key. */
    public long recoverMatter(UUID actor, SubstanceKey key, long maximum) {
        if (actor == null || key == null || maximum <= 0 || !Objects.equals(owner, actor) || !isLiveServerMachine()
                || net.neoforged.neoforge.transfer.transaction.Transaction.getCurrentOpenedTransaction() != null) return 0;
        if (kind().isStorage()) {
            if (!storageStatus().equals("ready")) return 0;
            long accepted = insertable(matterInventory(), key, maximum);
            if (accepted == 0 || !matterInventory().apply(Map.of(), Map.of(key, accepted))) return 0;
            setChanged();
            return accepted;
        }
        var network = matterNetwork(true);
        if (!network.ready()) return 0;
        long accepted = insertable(network.inventory(), key, maximum);
        if (accepted == 0 || !network.inventory().apply(Map.of(), Map.of(key, accepted))) return 0;
        network.markChanged();
        return accepted;
    }
    static long insertable(MatterStore store, SubstanceKey key, long maximum) {
        if (maximum <= 0) return 0;
        long capacity = 0;
        for (var member : store.members()) {
            if (!member.hasTypeSpace(Map.of(), Map.of(key, 1L))) continue;
            long add = Math.min(maximum - capacity, member.freeSpace());
            capacity += add;
            if (capacity == maximum) break;
        }
        return capacity;
    }
    private boolean acceptsNetwork(MachineMatterNetwork network) {
        return network.ready() && (boundNetwork == null || boundNetwork.sameTopology(network))
                && storageSession.accepts(network.inventory());
    }
    private void pauseChangedNetwork(String diagnostic) {
        runState = runState.pause();
        resetProgress();
        processedThisTick = false;
        status = diagnostic;
        setChanged();
    }
    /** Read-only discovery for optional integrations; no live mutable inventory escapes this boundary. */
    public NetworkInspection inspectMatterNetwork(UUID requester) {
        if (requester == null || !Objects.equals(owner, requester) || !isLiveServerMachine())
            return new NetworkInspection(MachineSnapshot.NetworkView.EMPTY, List.of());
        var network = matterNetwork(true);
        var workers = network.ready() ? network.workers().stream()
                .map(worker -> new NetworkWorker(worker.getBlockPos().immutable(), worker.kind(), worker.selectedId,
                        worker.runState.running(), worker.runState.queuedBatches(), worker.runState.repeat())).toList() : List.<NetworkWorker>of();
        return new NetworkInspection(network.view(), workers);
    }
    /** Integrations must revalidate immediately before dispatching an order on the server thread. */
    public boolean isCurrentNetworkWorker(UUID requester, MachineBlockEntity worker) {
        if (requester == null || !Objects.equals(owner, requester) || !isLiveServerMachine()
                || worker == null || !worker.isLiveServerMachine() || worker.getLevel() != level
                || !Objects.equals(worker.owner, requester)) return false;
        var network = matterNetwork(true);
        return network.ready() && network.workers().stream().anyMatch(member -> member == worker);
    }
    public record NetworkWorker(BlockPos position, MachineKind kind, String selectedRecipe,
                                boolean running, int queuedBatches, boolean repeat) {}
    public record NetworkInspection(MachineSnapshot.NetworkView network, List<NetworkWorker> workers) {
        public NetworkInspection { workers = List.copyOf(workers); }
    }
    public boolean hasPreparationStructure() { return PreparationTier.industrial(kind()) && IndustrialStructure.inspect(this).formed(); }

    public MachineSnapshot snapshot(int containerId) {
        ChemistryRegistry registry = ReMatter.chemistry();
        boolean independent = kind() == MachineKind.ANALYZER || PreparationTier.isPreparation(kind());
        MachineMatterNetwork network = independent ? null : matterNetwork(false);
        MatterStore store = independent ? null : kind().isStorage()
                ? storageStatus().equals("ready") ? matterInventory() : null
                : network.ready() ? network.inventory() : null;
        boolean readableStorage = store != null;
        Map<SubstanceKey, Long> displayed = !readableStorage ? Map.of() : store.contents();
        int sampleBatch = 1;
        if ((kind() == MachineKind.ANALYZER || kind() == MachineKind.DECOMPOSER) && !items.get(0).isEmpty()) {
            String itemId = BuiltInRegistries.ITEM.getKey(items.get(0).getItem()).toString();
            var profile = registry.profile(itemId).orElse(null);
            if (profile != null) {
                sampleBatch = profile.itemCount();
                if (kind() == MachineKind.ANALYZER && analysis.matches(itemId, items.get(0).getComponentsPatch().isEmpty(), profile)) displayed = profile.matter();
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
        var choices = choices();
        int selected = choices.isEmpty() || selection < 0 ? -1 : Math.floorMod(selection, choices.size());
        var chosen = selected < 0 ? null : choices.get(selected);
        var formation = IndustrialStructure.isIndustrial(kind()) ? IndustrialStructure.inspect(this) : IndustrialStructure.Formation.EMPTY;
        var structure = new MachineSnapshot.StructureView(formation.shape()==null?0:formation.shape().width(), formation.shape()==null?0:formation.shape().depth(),
                formation.shape()==null?0:formation.shape().height(), formation.formed()?speed():0, formation.moduleCount(), formation.diagnostic());
        return new MachineSnapshot(containerId, kind().ordinal(), (int) energy.getAmountAsLong(), progress, duration,
                status, selected, chosen == null ? selectedId : chosen.id(), kind() == MachineKind.NETWORK_INTERFACE ? automationHost().backend() : chosen == null ? selectedId : chosen.name(), choices.size(), sampleBatch,
                entries, entries.size() == displayed.size() && (independent || readableStorage), !readableStorage ? 0 : store.total(), !readableStorage ? 0 : store.capacity(),
                automation, runState.running(), (int) fluids.getAmountAsLong(0),
                fluids.getResource(0).isEmpty() ? "" : fluids.getResource(0).getHoverName().getString(),
                supportsBatchControl() && runState.repeat(), supportsBatchControl() ? runState.queuedBatches() : 0, preview(registry, readableStorage ? store : null),
                cellSlot() >= 0 ? cell.view(cellStack()) : MachineSnapshot.CellView.EMPTY,
                store == null ? "none" : kind().isStorage() ? legacyInternalStorage() ? "legacy" : "local" : "network", structure,
                network == null ? MachineSnapshot.NetworkView.EMPTY : network.view(), networkJobs);
    }

    private MachineSnapshot.Preview preview(ChemistryRegistry registry, MatterStore store) {
        if (networkJobs) return networkOrderPreview(registry);
        if(PreparationTier.isPreparation(kind())){
            var holder=preparationRecipe();if(holder==null)return MachineSnapshot.Preview.EMPTY;
            var recipe=holder.value();var result=recipe.result();
            return preview(registry,null,holder.id().identifier().toString(),result.getHoverName().getString(),Map.of(),Map.of(),
                    preparationCost(recipe),recipe.ticks(),"item",BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),result.getCount());
        }
        if (kind() == MachineKind.ANALYZER || kind() == MachineKind.DECOMPOSER) {
            ItemStack input = items.get(0);
            if (!input.isEmpty() && input.getComponentsPatch().isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(input.getItem()).toString();
                var profile = registry.profile(id).orElse(null);
                if (profile != null) {
                    boolean inspect = kind() == MachineKind.ANALYZER;
                    Map<SubstanceKey, Long> outputs = inspect && !analysis.matches(id, true, profile) ? Map.of() : profile.matter();
                    return preview(registry, store, "item:" + id, input.getHoverName().getString(), Map.of(), outputs,
                            cost(inspect ? 500 : profile.decompositionEnergy()), inspect ? 40 : profile.ticks(),
                            inspect ? "analysis" : "matter", inspect ? id : "", inspect ? 1 : 0);
                }
            }
            if (kind() == MachineKind.DECOMPOSER) {
                FluidResource fluid = fluids.getResource(0);
                var profile = registry.fluidProfile(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString()).orElse(null);
                if (profile != null && !fluid.isEmpty() && fluid.isComponentsPatchEmpty()) {
                    return preview(registry, store, "fluid:" + profile.fluidId(), fluid.getHoverName().getString(), Map.of(), profile.matter(),
                            cost(profile.decompositionEnergy()), profile.ticks(), "matter", "", 0);
                }
            }
            return MachineSnapshot.Preview.EMPTY;
        }
        if (!supportsBatchControl()) return MachineSnapshot.Preview.EMPTY;
        var options = choices();
        if (options.isEmpty() || selection < 0) return MachineSnapshot.Preview.EMPTY;
        var choice = options.get(Math.floorMod(selection, options.size()));
        Operation operation = operation(registry);
        if (operation == null) return MachineSnapshot.Preview.EMPTY;
        String outputKind = "matter";
        String outputId = "";
        int outputCount = 0;
        if (kind() == MachineKind.SYNTHESIZER) {
            if (choice.id().startsWith("fluid:")) {
                var profile = registry.fluidProfile(choice.id().substring(6)).orElseThrow();
                outputKind = "fluid"; outputId = profile.fluidId(); outputCount = profile.amount();
            } else {
                var profile = registry.profile(choice.id()).orElseThrow();
                outputKind = "item"; outputId = profile.itemId(); outputCount = profile.itemCount();
            }
        }
        return preview(registry, store, choice.id(), choice.name(), operation.inputs, operation.outputs,
                operation.cost, operation.ticks, outputKind, outputId, outputCount);
    }

    /** Preview the transferred order itself, never a manually selected or inferred recipe. */
    private MachineSnapshot.Preview networkOrderPreview(ChemistryRegistry registry) {
        var claim = networkWorkLease.current().orElse(null);
        if (claim == null) return MachineSnapshot.Preview.EMPTY;
        var order = claim.owner().order(claim.orderId(), this);
        if (order == null) return MachineSnapshot.Preview.EMPTY;
        var recipe = order.recipe();
        var inputs = new ArrayList<MachineSnapshot.Amount>();
        var outputs = new ArrayList<MachineSnapshot.Amount>();
        for (var entry : recipe.inputs().entrySet()) {
            if (entry.getKey().kind() != AutomationPort.Kind.MATTER || inputs.size() == MachineSnapshot.MAX_PREVIEW_AMOUNTS) continue;
            var key = entry.getKey().matterKey();
            inputs.add(new MachineSnapshot.Amount(key.id().toString(),substanceName(registry,key),entry.getKey().phase(),
                    entry.getValue(),order.ownedInputs().getOrDefault(entry.getKey(),0L)));
        }
        for (var entry : recipe.outputs().entrySet()) {
            if (entry.getKey().kind() != AutomationPort.Kind.MATTER || outputs.size() == MachineSnapshot.MAX_PREVIEW_AMOUNTS) continue;
            var key = entry.getKey().matterKey();
            outputs.add(new MachineSnapshot.Amount(key.id().toString(),substanceName(registry,key),entry.getKey().phase(),entry.getValue(),0));
        }
        var first = recipe.outputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).findFirst().orElseThrow();
        String name = first.getValue() + " " + automationResourceName(registry,first.getKey());
        String physicalInputs = recipe.inputs().entrySet().stream().filter(entry -> entry.getKey().kind() != AutomationPort.Kind.MATTER)
                .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getValue()+" "+automationResourceName(registry,entry.getKey()))
                .collect(java.util.stream.Collectors.joining(" + "));
        if (!physicalInputs.isEmpty()) name = physicalInputs + " → " + name;
        boolean matterOutput = first.getKey().kind() == AutomationPort.Kind.MATTER;
        int count = matterOutput ? recipe.outputs().size() : Math.toIntExact(first.getValue());
        String outputKind = first.getKey().kind().name().toLowerCase(Locale.ROOT);
        long base = recipe.energy();
        if (PreparationTier.industrial(kind())) base = base > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : base * 2;
        return new MachineSnapshot.Preview(recipe.id(),name,inputs,outputs,
                inputs.size() == recipe.inputs().size() && outputs.size() == recipe.outputs().size(),cost(base),
                (int)(((long)recipe.ticks()+speed()-1)/speed()),outputKind,first.getKey().id(),count);
    }
    private static String automationResourceName(ChemistryRegistry registry, AutomationPort.Resource resource) {
        var id = Identifier.parse(resource.id());
        if (resource.kind() == AutomationPort.Kind.ITEM && BuiltInRegistries.ITEM.containsKey(id))
            return new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName().getString();
        if (resource.kind() == AutomationPort.Kind.FLUID && BuiltInRegistries.FLUID.containsKey(id))
            return FluidResource.of(BuiltInRegistries.FLUID.getValue(id)).getHoverName().getString();
        return resource.kind() == AutomationPort.Kind.MATTER ? substanceName(registry,resource.matterKey()) : resource.id();
    }

    private MachineSnapshot.Preview preview(ChemistryRegistry registry, MatterStore store, String id, String name,
                                            Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                                            long energy, int ticks, String outputKind, String outputId, int outputCount) {
        var inputRows = previewAmounts(registry, store, inputs);
        var outputRows = previewAmounts(registry, store, outputs);
        return new MachineSnapshot.Preview(id, name, inputRows, outputRows,
                inputRows.size() == inputs.size() && outputRows.size() == outputs.size(), energy,
                (int) (((long) ticks + speed() - 1) / speed()), outputKind, outputId, outputCount);
    }
    private List<MachineSnapshot.Amount> previewAmounts(ChemistryRegistry registry, MatterStore store, Map<SubstanceKey, Long> amounts) {
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

    private boolean hasValidAnalysis(ChemistryRegistry registry) {
        if (kind() != MachineKind.ANALYZER || items.get(0).isEmpty()) return false;
        ItemStack sample = items.get(0);
        String id = BuiltInRegistries.ITEM.getKey(sample.getItem()).toString();
        return analysis.matches(id, sample.getComponentsPatch().isEmpty(), registry.profile(id).orElse(null));
    }
    private boolean analyzableSample(ChemistryRegistry registry) {
        ItemStack sample = items.get(0);
        return !sample.isEmpty() && sample.getComponentsPatch().isEmpty()
                && registry.profile(BuiltInRegistries.ITEM.getKey(sample.getItem()).toString()).isPresent();
    }

    @Override protected void saveAdditional(ValueOutput output) {
        if (cellSlot() >= 0) cell.flush();
        super.saveAdditional(output);
        output.putInt("schema", 1);
        if (owner != null) output.putString("owner", owner.toString());
        output.putBoolean("automation", automation);
        output.putBoolean("network_jobs", networkJobs);
        if (automationHost != null) output.store("automation_host", MatterStorageState.RAW_CODEC, automationHost.save());
        output.putBoolean("running", runState.running());
        output.putInt("control_schema", MachineRunState.SCHEMA_VERSION);
        output.putBoolean("repeat", runState.repeat());
        output.putInt("queued_batches", runState.queuedBatches());
        output.putString("selection", selectedId);
        if(IndustrialStructure.isIndustrial(kind())&&industrialClaim!=null)output.store("industrial_claim",CompoundTag.CODEC,industrialClaim.save());
        if (!analysis.isEmpty()) {
            ValueOutput savedAnalysis = output.child("analysis");
            savedAnalysis.putInt("schema", AnalysisState.SCHEMA_VERSION);
            savedAnalysis.putString("item", analysis.itemId());
            savedAnalysis.putString("profile", analysis.profileFingerprint());
        }
        // Progress is deliberately not persisted: no input or energy was consumed before final commit.
        output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(items));
        energy.serialize(output.child("power"));
        fluids.serialize(output.child("tank"));
        if (kind() == MachineKind.STORAGE) output.store("storage_state", MatterStorageState.CODEC, storageState);
        if (preservedMatter != null) output.store("matter", MatterStorageState.RAW_CODEC, preservedMatter);
        else if (!kind().isStorage() || legacyInternalStorage()) {
            ValueOutput storage = output.child("matter");
            storage.putInt("schema", 1);
            storage.putLong("capacity", matter.capacity());
            var list = storage.childrenList("contents");
            matter.contents().forEach((key, amount) -> {
                var entry = list.addChild();
                entry.putString("substance", key.id().toString());
                entry.putString("phase", key.phase().name());
                entry.putLong("amount", amount);
            });
        }
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try { owner = input.getString("owner").map(UUID::fromString).orElse(null); }
        catch (IllegalArgumentException exception) { owner = new UUID(0, 0); }
        releaseNetworkWork();
        automation = input.getBooleanOr("automation", false);
        networkJobs = supportsNetworkJobs() && input.getBooleanOr("network_jobs", false);
        if (kind() == MachineKind.NETWORK_INTERFACE) input.read("automation_host", MatterStorageState.RAW_CODEC).ifPresent(raw -> automationHost().load(raw));
        runState = MachineRunState.restore(input.getIntOr("control_schema", 0), input.getBooleanOr("running", true),
                input.getBooleanOr("repeat", true), input.getIntOr("queued_batches", 0));
        selectedId = input.getStringOr("selection", "");
        industrialClaim=IndustrialStructure.isIndustrial(kind())?input.read("industrial_claim",CompoundTag.CODEC)
                .flatMap(IndustrialStructure.ProtectionClaim::restore)
                .filter(claim->claim.shape().height()>=IndustrialStructure.height(kind())).orElse(null):null;
        ValueInput savedAnalysis = input.childOrEmpty("analysis");
        analysis = AnalysisState.restore(savedAnalysis.getIntOr("schema", 0), savedAnalysis.getStringOr("item", ""),
                savedAnalysis.getStringOr("profile", ""));
        items = NonNullList.withSize(MachineCellAccess.inventorySize(kind()), ItemStack.EMPTY);
        input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> { for (int i = 0; i < Math.min(items.size(), list.size()); i++) items.set(i, list.get(i)); });
        energy.deserialize(input.childOrEmpty("power"));
        fluids.deserialize(input.childOrEmpty("tank"));
        storageState = kind() == MachineKind.STORAGE
                ? input.read("storage_state", MatterStorageState.CODEC).orElseGet(MatterStorageState::legacy)
                : MatterStorageState.cell();
        preservedMatter = input.read("matter", MatterStorageState.RAW_CODEC).orElse(null);
        if (kind().isStorage() && !legacyInternalStorage()) {
            // Never import legacy internal contents into a second cell-owned inventory implicitly.
            matter = new MatterInventory(1);
            cell.mount(cellStack());
        } else {
            var restored = preservedMatter == null ? Optional.of(new MatterInventory(MATTER_CAPACITY)) : LegacyMatterData.open(preservedMatter);
            matter = restored.orElseGet(() -> new MatterInventory(MATTER_CAPACITY));
            if (restored.isPresent()) preservedMatter = null;
        }
        if (cellSlot() >= 0) cell.mount(cellStack());
        // Former local cells are retained only for recovery. Loading them must not silently restart on a network.
        if (hasRecoveryCell()) runState = runState.pause();
        migrationFeedback = "";
        resetProgress();
        lastRegistry = null;
        lastPreparationRecipe = null;
        cachedNetwork = null;
        networkCacheUntil = 0;
    }

    @Override public Component getDisplayName() { return Component.translatable("block.rematter." + kind().id()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return canUse(player) ? new MachineMenu(id, inventory, this) : null; }
    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int index) { if (index == cellSlot()) cell.flush(); return items.get(index); }
    @Override public ItemStack removeItem(int index, int amount) {
        if (index == cellSlot() && !prepareCellRemoval()) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(items, index, amount);
        if (!result.isEmpty()) { if (index == cellSlot()) mountCell(); else setChanged(); }
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int index) {
        if (index == cellSlot() && !prepareCellRemoval()) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.takeItem(items, index);
        if (index == cellSlot()) mountCell();
        return result;
    }
    @Override public void setItem(int index, ItemStack stack) {
        if (index == cellSlot()) {
            if (!prepareCellRemoval() || net.neoforged.neoforge.transfer.transaction.Transaction.getCurrentOpenedTransaction() != null) return;
            if (!stack.isEmpty() && (!(stack.getItem() instanceof MatterCellItem) || stack.getCount() != 1)) return;
            if (!stack.isEmpty() && !kind().isStorage()) return;
            items.set(index, stack); mountCell(); return;
        }
        items.set(index, stack);
        if (index == 0 && kind() == MachineKind.ANALYZER) { analysis = AnalysisState.EMPTY; runState = MachineRunState.automatic(); }
        stack.limitSize(getMaxStackSize(stack)); setChanged(); }
    @Override public void setItem(int index, ItemStack stack, boolean insideTransaction) {
        if (index == cellSlot()) { setItem(index, stack); return; }
        if (insideTransaction) items.set(index, stack);
        else setItem(index, stack);
    }
    @Override public void onTransfer(int index, int amountChange, TransactionContext transaction) {
        if (index == 0 && amountChange != 0 && kind() == MachineKind.ANALYZER) inputTransferCommit.updateSnapshots(transaction);
    }
    @Override public boolean stillValid(Player player) { return canUse(player) && Container.stillValidBlockEntity(this, player); }
    @Override public boolean canPlaceItem(int index, ItemStack stack) {
        if (networkJobs && index != 2) return false;
        if (index == cellSlot()) return kind().isStorage() && stack.getItem() instanceof MatterCellItem && stack.getCount() == 1 && canRemoveCell();
        if (index == 1) return false;
        if (index == 2) return kind().processes() && !PreparationTier.manual(kind()) && (stack.is(ModContent.SPEED_UPGRADE.get()) || stack.is(ModContent.EFFICIENCY_UPGRADE.get()));
        if(PreparationTier.isPreparation(kind())){
            if(!(level instanceof ServerLevel server)||(index!=0&&index!=3))return false;
            return PreparationRecipes.serverRecipes(server).stream().filter(entry->PreparationTier.permits(kind(),entry.minimumTier()))
                    .anyMatch(entry->index==0?entry.recipe().acceptsPrimary(stack):entry.recipe().acceptsSecondary(stack));
        }
        return (kind() == MachineKind.DECOMPOSER || kind() == MachineKind.ANALYZER) && stack.getComponentsPatch().isEmpty()
                && ReMatter.chemistry().profile(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).isPresent();
    }
    @Override public boolean canTakeItem(Container target, int index, ItemStack stack) { return automationAllowed() && !IndustrialStructure.isIndustrial(kind()) && !kind().isStorage() && index == 1; }
    @Override public int[] getSlotsForFace(Direction side) { return !automationAllowed()||kind().isStorage()||IndustrialStructure.isIndustrial(kind())?new int[0]:PreparationTier.isPreparation(kind())?new int[]{0,3,1}:new int[]{0,1}; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction side) { return automationAllowed() && !IndustrialStructure.isIndustrial(kind()) && !kind().isStorage() && (index == 0 || PreparationTier.isPreparation(kind())&&index==3) && canPlaceItem(index, stack); }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) { return automationAllowed() && !IndustrialStructure.isIndustrial(kind()) && !kind().isStorage() && index == 1; }
    @Override public void clearContent() { if (!cellStack().isEmpty()) return; items.clear(); setChanged(); }
}
