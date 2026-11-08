package dev.latrox.rematter.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.stream.Stream;
import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.preparation.*;
import dev.latrox.rematter.network.MachineProgressPayload;
import dev.latrox.rematter.network.TerminalDevicesPayload;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.RootCommitJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * Server-owned machine state and tick orchestration. Inputs remain untouched until the entire output can commit.
 * Pricing, operations, selection, cell bays, network jobs, side roles, visuals and menu views live in collaborators.
 */
public final class MachineBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int ENERGY_CAPACITY = 2_000_000;
    public static final int TANK_CAPACITY = 16_000;
    private static final Codec<Tag> RAW_TAG_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(NbtOps.INSTANCE).getValue(), tag -> new Dynamic<>(NbtOps.INSTANCE, tag));
    private static final Set<String> BLOCK_ENTITY_METADATA = Set.of("id", "x", "y", "z", "components");
    /** Every stored key except the block-entity metadata that vanilla writes itself. */
    private static final MapCodec<CompoundTag> STORED_DATA = new MapCodec<>() {
        @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.empty(); }
        @Override public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
            var tag = new CompoundTag();
            input.entries().forEach(entry -> ops.getStringValue(entry.getFirst()).result()
                    .filter(key -> !BLOCK_ENTITY_METADATA.contains(key))
                    .ifPresent(key -> tag.put(key, ops.convertTo(NbtOps.INSTANCE, entry.getSecond()))));
            return DataResult.success(tag);
        }
        @Override public <T> RecordBuilder<T> encode(CompoundTag input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            for (String key : input.keySet()) prefix.add(key, NbtOps.INSTANCE.convertTo(ops, input.get(key)));
            return prefix;
        }
    };
    private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private final MachineCellBays bays = new MachineCellBays(kind(), this::stack);
    private final MachineStorageSession storageSession = new MachineStorageSession();
    private final MachineSideConfig sides = new MachineSideConfig();
    private final MachineWork work = new MachineWork();
    private final MachineVisuals visuals = new MachineVisuals();
    private final MachineOperations operations = new MachineOperations(this);
    private final MachineControl control = new MachineControl(this);
    private final NetworkJobWorker worker = new NetworkJobWorker(this);
    private final MachineStructureLink structure = new MachineStructureLink(this);
    private @Nullable CompoundTag unsupportedData;
    private UUID owner;
    private boolean automation;
    private boolean networkJobs;
    private MachineAutomationHost automationHost;
    private AnalysisState analysis = AnalysisState.EMPTY;
    private ChemistryRegistry lastRegistry;
    private MachineMatterNetwork cachedNetwork;
    private MachineMatterNetwork boundNetwork;
    private long nextMatterActivityTick;
    private boolean committingOwnedState;
    private boolean visibleCell;
    private int visibleCellMask, visibleCellTiers;
    private boolean loadingItemVisuals;
    private int publishedPreparationStage = -1;
    private boolean publishedPreparationMixing;
    private long nextCrank, manualActiveUntil;
    private int manualStage = -1;
    private boolean manualMixing;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, 16_000, 0) {
        @Override public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return isLiveServerMachine() && !PreparationTier.manual(kind()) && industrialStructureReady() ? super.insert(amount, transaction) : 0;
        }
        @Override protected void onEnergyChanged(int previous) { if (!committingOwnedState) setChanged(); }
    };
    private final FluidStacksResourceHandler fluids = new FluidStacksResourceHandler(1, TANK_CAPACITY) {
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
        control.runState(MachineRunState.automatic());
        setChanged();
    });
    private final RootCommitJournal itemVisualCommit = new RootCommitJournal(this::publishItemVisuals);

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MACHINE_ENTITY.get(), pos, state);
        items = NonNullList.withSize(MachineCellAccess.inventorySize(kind()), ItemStack.EMPTY);
    }
    boolean supportsBatchControl() { return MachineControl.batchControl(kind()); }
    public MachineKind kind() { return ((MachineBlock) getBlockState().getBlock()).kind(); }
    public SimpleEnergyHandler energy() { return energy; }
    public FluidStacksResourceHandler fluids() { return fluids; }
    ItemStack stack(int slot) { return items.get(slot); }
    void putStack(int slot, ItemStack stack) { items.set(slot, stack); }
    MachineWork work() { return work; }
    MachineVisuals visuals() { return visuals; }
    MachineOperations operations() { return operations; }
    NetworkJobWorker networkWorker() { return worker; }
    MachineRunState runState() { return control.runState(); }
    String selectedId() { return control.selectedId(); }
    AnalysisState analysis() { return analysis; }
    void completeAnalysis(MaterialProfile profile) { analysis = AnalysisState.completed(profile); }

    public MachinePortMode portMode(MachinePortMode.Side side) { return sides.mode(side); }
    public MachinePortMode portMode(Direction side) {
        return portMode(MachinePortMode.Side.local(getBlockState().getValue(MachineBlock.FACING), side));
    }
    public boolean allowsPort(@Nullable Direction side, MachinePortMode role) {
        // A side-less query must not provide a route around a configured face.
        if (side == null) return sides.allowsEverywhere(role);
        return portMode(side).allows(role);
    }
    public boolean acceptsMatter(Direction side, boolean cable) {
        return MatterPipeConnectionPolicy.acceptsMachine(kind(), getBlockState().getValue(MachineBlock.FACING), side, cable)
                && allowsPort(side, MachinePortMode.MATTER);
    }
    boolean cyclePort(MachinePortMode.Side side) {
        if (!(level instanceof ServerLevel server) || !isLiveServerMachine()
                || !MachinePortMode.configurable(kind())) return false;
        MachinePortMode previous = sides.cycle(kind(), side);
        MachinePortMode next = sides.mode(side);
        setChanged();
        if (previous.allows(MachinePortMode.MATTER) != next.allows(MachinePortMode.MATTER)
                || previous.allows(MachinePortMode.POWER) != next.allows(MachinePortMode.POWER)) {
            touchObservations();
            MatterPipeBlock.refreshLoadedAround(server, worldPosition);
        }
        level.invalidateCapabilities(worldPosition);
        sendClientUpdate();
        return true;
    }
    boolean cyclePort(Player player, Direction side) {
        return stillValid(player) && cyclePort(MachinePortMode.Side.local(getBlockState().getValue(MachineBlock.FACING), side));
    }
    @Nullable EnergyHandler sidedEnergy(@Nullable Direction side) {
        if (!kind().processes() || IndustrialStructure.isIndustrial(kind()) || PreparationTier.manual(kind())
                || !allowsPort(side, MachinePortMode.POWER)) return null;
        return new PortEnergyHandler(energy, () -> isLiveServerMachine() && allowsPort(side, MachinePortMode.POWER));
    }
    @Nullable ResourceHandler<FluidResource> sidedFluids(@Nullable Direction side) {
        if (!automation || kind() != MachineKind.DECOMPOSER && kind() != MachineKind.SYNTHESIZER
                || !allowsPort(side, MachinePortMode.FLUID_INPUT) && !allowsPort(side, MachinePortMode.FLUID_OUTPUT)) return null;
        return new AutomationResourceHandler<>(automatedFluids,
                () -> allowsPort(side, MachinePortMode.FLUID_INPUT),
                () -> allowsPort(side, MachinePortMode.FLUID_OUTPUT), 1, FluidResource.EMPTY);
    }
    public ResourceHandler<ItemResource> automatedItems(Direction side) {
        Objects.requireNonNull(side);
        return new IndustrialItemHandler(VanillaContainerWrapper.of(this),
                () -> allowsItemInput(side), () -> allowsItemOutput(side), PreparationTier.isPreparation(kind()));
    }
    @Nullable ResourceHandler<ItemResource> sidedItems(@Nullable Direction side) {
        if (side == null || !automation || IndustrialStructure.isIndustrial(kind()) || kind().isStorage()
                || !allowsPort(side, MachinePortMode.ITEM_INPUT) && !allowsPort(side, MachinePortMode.ITEM_OUTPUT)) return null;
        return automatedItems(side);
    }
    ResourceHandler<ItemResource> industrialItems() {
        return new IndustrialItemHandler(VanillaContainerWrapper.of(this), this::automationAllowed, PreparationTier.industrial(kind()));
    }
    public boolean automation() { return automation; }
    private boolean automationAllowed() {
        return automation && !networkJobs && isLiveServerMachine() && industrialStructureReady();
    }
    boolean industrialStructureReady() { return !IndustrialStructure.isIndustrial(kind()) || IndustrialStructure.inspect(this).formed(); }
    public boolean industrialFormed() { return structure.formed(); }
    public int industrialWidth() { var shape = structure.visibleShape(); return shape == null ? 0 : shape.width(); }
    public int industrialHeight() { var shape = structure.visibleShape(); return shape == null ? 0 : shape.height(); }
    public int industrialDepth() { var shape = structure.visibleShape(); return shape == null ? 0 : shape.depth(); }
    public int industrialControllerColumn() { var shape = structure.visibleShape(); return shape == null ? 0 : shape.controllerColumn(); }
    IndustrialStructure.@Nullable ProtectionClaim industrialProtectionClaim() { return structure.claim(); }
    /** Placement creates a new physical installation, even when a player copied full block data. */
    public void clearIndustrialProtectionClaim() {
        if (structure.clearClaim()) { setChanged(); touchObservations(); }
    }
    /**
     * State that networks and shells observe changed without a block change: owner, mounted cells, side roles or a
     * protection claim. Advancing this chunk's revision makes every cached observation of it check again.
     */
    void touchObservations() {
        if (!(level instanceof ServerLevel server)) return;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk != null) ChunkRevisions.touch(chunk);
    }
    /** Visual authority only: a module never owns an inventory, energy buffer or persistent association. */
    public boolean isModuleWorking(BlockPos modulePosition) {
        return isLiveServerMachine() && structure.moduleWorking(modulePosition, work.processedThisTick);
    }
    @Override public void setRemoved() {
        worker.release();
        if (automationHost != null) automationHost.close();
        work.processedThisTick = false;
        structure.removed();
        super.setRemoved();
    }
    /** MachineBlock carries the complete inventory in one block item, as a portable container. */
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // The vanilla default scatters every Container slot. Doing that as well as our packed
        // block drop would duplicate cells on creative breaks and empty survival drop contents.
    }
    /** False for client copies, stale entities and machines whose stored data is kept unread. */
    private boolean isLiveServerMachine() {
        if (unsupportedData != null || isRemoved() || level == null || level.isClientSide()) return false;
        var server = level.getServer();
        return server != null && server.isSameThread() && level.hasChunkAt(worldPosition)
                && level.getBlockEntity(worldPosition) == this;
    }
    private void sendClientUpdate() {
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    // Package-local discovery access never exposes a mutable aggregate to external callers.
    boolean networkLive() { return isLiveServerMachine(); }
    UUID networkOwner() { return owner; }
    String networkStorageStatus() { return storageStatus(); }
    int networkCellCount() { return kind().isStorage() ? bays.installed() : 0; }
    List<MatterInventory> localMatterMembers() { return kind().isStorage() && storageStatus().equals("ready") ? matterStore().members() : List.of(); }
    /** Placement makes the placing player the owner, whatever owner data the placed item carried. */
    void placedBy(@Nullable LivingEntity placer) {
        assignOwner(MachineAccess.placedBy(placer instanceof Player player ? player.getUUID() : null, placer instanceof FakePlayer));
    }
    private void assignOwner(@Nullable UUID next) {
        if (Objects.equals(owner, next)) return;
        owner = next;
        setChanged();
        touchObservations();
        if (level != null) level.invalidateCapabilities(worldPosition);
    }
    public boolean canAccess(Player player) {
        return MachineAccess.allows(owner, player.getUUID(),
                player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), player instanceof FakePlayer);
    }
    /** A deliberate interaction: an unowned machine becomes the player's, then the usual access check applies. */
    public boolean interact(Player player) {
        if (!player.level().isClientSide() && MachineAccess.claims(owner, player instanceof FakePlayer, player.isSpectator()))
            assignOwner(player.getUUID());
        return canUse(player);
    }
    /** The full local store has stable, flat, independently persisted cell members. */
    public MatterStore matterStore() { return bays.store(); }
    public int cellSlot() { return MachineCellAccess.slot(kind()); }
    public int cellBayCount() { return bays.count(); }
    public int cellSlotForBay(int bay) { return MachineCellAccess.slotForBay(kind(), bay); }
    public int[] cellSlots() {
        return java.util.stream.IntStream.range(0, cellBayCount()).map(this::cellSlotForBay).toArray();
    }
    public List<MachineSnapshot.CellView> cellViews() { return bays.views(); }
    public boolean chemicalAutomationAllowed() {
        return kind().isStorage() && storageStatus().equals("ready") && automationAllowed();
    }
    public boolean canRemoveCell() {
        for (int slot : cellSlots()) if (!canRemoveCell(slot)) return false;
        return true;
    }
    public boolean canRemoveCell(int slot) {
        int bay = MachineCellAccess.bayForSlot(kind(), slot);
        if (bay < 0) return true;
        var stack = items.get(slot);
        // Splitting a malformed stack would duplicate the same portable contents into both pieces.
        if (!stack.isEmpty() && stack.getItem() instanceof MatterCellItem && stack.getCount() != 1) return false;
        return bays.canRemove(bay);
    }
    public boolean prepareCellRemoval(int slot) {
        if (!canRemoveCell(slot)) return false;
        int bay = MachineCellAccess.bayForSlot(kind(), slot);
        if (bay >= 0) bays.flush(bay);
        return true;
    }
    private void mountCell(int slot) {
        int bay = MachineCellAccess.bayForSlot(kind(), slot);
        if (bay < 0) return;
        bays.mount(bay);
        touchObservations();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            sendClientUpdate();
        }
        setChanged();
    }
    String storageStatus() {
        return kind().isStorage() ? bays.status() : "ready";
    }
    @Override public void setChanged() {
        if (bays != null) bays.flush();
        super.setChanged();
        publishItemVisuals();
    }
    /** Cached, component-free display stacks. Renderers must treat these as read-only. */
    public ItemStack visibleInputItem() { return visuals.input(); }
    public ItemStack visibleSecondaryItem() { return visuals.secondary(); }
    public ItemStack visibleTertiaryItem() { return visuals.tertiary(); }
    public ItemStack visibleOutputItem() { return visuals.output(); }
    /** Read-only plain item previews from the authoritative running recipe, never inventory contents. */
    public ItemStack visibleProcessInputItem() { return visuals.processInput(); }
    public ItemStack visibleProcessOutputItem() { return visuals.processOutput(); }
    public int visualProgress() { return visuals.progress(); }
    public int visualDuration() { return visuals.duration(); }

    private void publishProcessVisuals() {
        if (isLiveServerMachine() && visuals.publishProcess(work.processedThisTick, work.progress, work.duration, level.getGameTime())) sendClientUpdate();
    }
    private boolean itemVisualsStable() {
        return !loadingItemVisuals && !committingOwnedState && Transaction.getCurrentOpenedTransaction() == null;
    }
    private void publishItemVisuals() {
        if (!MachineVisuals.showsItems(kind()) || !itemVisualsStable() || !isLiveServerMachine()) return;
        if (visuals.refreshItems(kind(), this::stack)) sendClientUpdate();
    }
    public boolean hasVisibleCell() { return visibleCellMask() != 0; }
    public boolean hasVisibleCell(int bay) { return bay >= 0 && bay < cellBayCount() && (visibleCellMask() & 1 << bay) != 0; }
    public int visibleCellMask() {
        if (level != null && level.isClientSide()) return visibleCellMask;
        return bays.visibleMask();
    }
    /** 0 empty, 1/2/3 capacity tiers, 4 unreadable or foreign preserved item. */
    public int visibleCellTier(int bay) {
        if (bay < 0 || bay >= cellBayCount()) return 0;
        if (level != null && level.isClientSide()) return Math.min(4, visibleCellTiers >>> (bay * 3) & 7);
        return bays.visibleTier(bay);
    }
    public int preparationStage() { return PreparationTier.isPreparation(kind()) ? work.preparationStage : -1; }
    public boolean preparationMixing() { return PreparationTier.isPreparation(kind()) && work.preparationMixing; }
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("port_visuals", sides.visuals());
        visuals.write(tag, kind());
        if (cellSlot() >= 0) { tag.putBoolean("cell_present", hasVisibleCell()); tag.putInt("cell_mask", visibleCellMask()); tag.putInt("cell_tiers", bays.visibleTiers()); }
        if (PreparationTier.isPreparation(kind())) { tag.putInt("preparation_stage", work.preparationStage); tag.putBoolean("preparation_mixing", work.preparationMixing); }
        structure.write(tag);
        return tag;
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void handleUpdateTag(ValueInput input) {
        sides.readVisuals(input.getIntOr("port_visuals", 0));
        visuals.read(input, kind());
        if (cellSlot() >= 0) {
            visibleCell = input.getBooleanOr("cell_present", false);
            visibleCellMask = input.getIntOr("cell_mask", visibleCell ? 1 : 0) & ((1 << cellBayCount()) - 1);
            visibleCellTiers = input.getIntOr("cell_tiers", visibleCell ? 1 : 0) & ((1 << (cellBayCount() * 3)) - 1);
        }
        if (PreparationTier.isPreparation(kind())) {
            work.preparationStage = Math.clamp(input.getIntOr("preparation_stage", -1), -1, 2);
            work.preparationMixing = input.getBooleanOr("preparation_mixing", false);
        }
        structure.read(input);
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, ValueInput input) { handleUpdateTag(input); }
    public boolean canUse(Player player) {
        boolean allowed = canAccess(player);
        if (!allowed && !player.level().isClientSide()) player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
        return allowed;
    }
    public boolean hasStoredResources() {
        return unsupportedData != null || automationHost != null && automationHost.hasContents()
                || !isEmpty() || fluids.getAmountAsLong(0) > 0 || energy.getAmountAsLong() > 0;
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
        worker.release(); networkJobs = !networkJobs;
        control.runState(control.runState().pause()); resetProgress(); work.processedThisTick = false;
        level.invalidateCapabilities(worldPosition); setChanged(); return true;
    }
    UUID networkWorkerId() { return worker.id(); }
    boolean acceptsNetworkWork() {
        if (!networkJobs || !supportsNetworkJobs() || !isLiveServerMachine() || !industrialStructureReady()
                || fluids.getAmountAsLong(0) != 0) return false;
        for (int slot = 0; slot < items.size(); slot++) if (slot != 2 && !items.get(slot).isEmpty()) return false;
        return true;
    }
    boolean tryBindNetworkOrder(MachineAutomationHost host, UUID order) { return worker.tryBind(host, order); }
    boolean hasNetworkOrder(MachineAutomationHost host, UUID order) { return worker.has(host, order); }
    boolean networkOrderFree() { return worker.free(); }
    boolean shouldYieldNetworkWork(String backend, boolean otherBackendReady) { return worker.shouldYield(backend, otherBackendReady); }
    void releaseNetworkOrder(MachineAutomationHost host, UUID order) { worker.release(host, order); }
    public void toggleAutomation() {
        automation = !automation;
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            structure.ports().stream().map(IndustrialStructure.Port::position).filter(level::hasChunkAt).forEach(level::invalidateCapabilities);
        }
        setChanged();
    }
    public void toggleRunning() {
        if (networkJobs) { toggleNetworkJobs(); return; }
        control.toggleRunning();
    }
    public boolean requestBatch() { return !networkJobs && control.requestBatch(); }
    public boolean toggleRepeat() { return !networkJobs && control.toggleRepeat(); }
    public void select(int offset) { if (!networkJobs) control.select(offset); }
    public boolean selectRecipe(String exactId) { return !networkJobs && control.select(exactId); }
    public List<MachineChoices.Choice> choices() { return control.choices(); }
    MachineChoices.@Nullable Choice selectedChoice() { return control.selectedChoice(); }
    int selectedIndex() { return control.selectedIndex(); }

    public void serverTick() {
        if (!isLiveServerMachine()) return;
        // A foreign transfer may roll back its member snapshots. Do not complete unrelated work inside it.
        if (Transaction.getCurrentOpenedTransaction() != null) return;
        if (structure.refresh()) sendClientUpdate();
        work.processedThisTick = false;
        visuals.beginTick();
        if (PreparationTier.isPreparation(kind())) work.preparationStage = -1;
        try {
            tickMachine();
        } finally {
            if (PreparationTier.isPreparation(kind()) && !work.processedThisTick) work.preparationStage = -1;
            BlockState state = getBlockState();
            if (!isRemoved() && state.getValue(MachineBlock.ACTIVE) != work.processedThisTick) {
                level.setBlock(worldPosition, state.setValue(MachineBlock.ACTIVE, work.processedThisTick), Block.UPDATE_CLIENTS);
            }
            if (PreparationTier.isPreparation(kind()) && (publishedPreparationStage != work.preparationStage || publishedPreparationMixing != work.preparationMixing)) {
                publishedPreparationStage = work.preparationStage;
                publishedPreparationMixing = work.preparationMixing;
                sendClientUpdate();
            }
            publishProcessVisuals();
            structure.publishActivity(work.processedThisTick && isLiveServerMachine() && industrialStructureReady());
            publishItemVisuals();
        }
    }
    public boolean crank(Player player) { return crank(player, null); }

    boolean crank(Player player, @Nullable MachineBlockEntity terminal) {
        if (!PreparationTier.manual(kind()) || !isLiveServerMachine() || level.getGameTime() < nextCrank) return false;
        if (terminal == null ? !stillValid(player) : !TerminalNetwork.canManage(terminal, this, player)) return false;
        // A first click may arrive before the first block tick, or immediately after a datapack reload.
        if (lastRegistry != ReMatter.chemistry()) tickMachine();
        var operation = operations.operation(ReMatter.chemistry());
        if (operation == null) { work.status = operations.inputStatus(ReMatter.chemistry()); resetProgress(); return false; }
        String reason = operations.readiness(false, null, operation);
        if (!reason.equals("ready")) { work.status = reason; return false; }
        String signature = operation.signature() + ":" + operation.ticks();
        if (!signature.equals(work.job)) { resetProgress(); work.job = signature; }
        work.duration = operation.ticks();
        work.progress = (int) Math.min(work.duration, (long) work.progress + PreparationWork.CRANK_WORK);
        visuals.capture(items.get(0), operation.visualResult(), work.progress, work.duration);
        manualStage = PreparationWork.stage(work.progress, work.duration);
        manualMixing = operations.mixingPreparation();
        nextCrank = level.getGameTime() + PreparationWork.CRANK_COOLDOWN;
        manualActiveUntil = level.getGameTime() + 3;
        if (work.progress >= work.duration) {
            // No callbacks, resource transfer or energy mutation occurs between this recheck and the owned item commit.
            if (!operation.canFinish()) return false;
            operation.finish().run(); resetProgress();
        }
        setChanged(); publishProcessVisuals(); return true;
    }
    private void tickManualPreparation(ChemistryRegistry registry) {
        if (level.getGameTime() < manualActiveUntil) {
            work.processedThisTick = true; work.preparationStage = manualStage; work.preparationMixing = manualMixing;
            work.status = PreparationWork.stageName(work.preparationStage, work.preparationMixing, kind() == MachineKind.SINTER_FURNACE); return;
        }
        var operation = operations.operation(registry);
        if (operation == null) { work.status = operations.inputStatus(registry); resetProgress(); return; }
        var reason = operations.readiness(false, null, operation);
        work.status = reason.equals("ready") ? "crank" : reason;
    }
    private void tickMachine() {
        if (kind() == MachineKind.NETWORK_INTERFACE) { var host = automationHost(); host.tick(); work.status = host.status(); return; }
        if (networkJobs) { worker.tick(); return; }
        ChemistryRegistry registry = ReMatter.chemistry();
        if (!analysis.isEmpty() && !operations.hasValidAnalysis(registry, analysis)) { analysis = AnalysisState.EMPTY; setChanged(); }
        if (registry != lastRegistry) {
            lastRegistry = registry;
            resetProgress();
            if (!control.restore(registry)) work.status = "selection_unavailable";
        }
        if (control.selectionUnavailable()) {
            if (control.runState().running()) { control.runState(control.runState().pause()); setChanged(); }
            work.status = "selection_unavailable";
            return;
        }
        if (supportsBatchControl() && !control.hasSelection()) { work.status = "choose_process"; return; }
        if (PreparationTier.manual(kind())) { tickManualPreparation(registry); return; }
        if (kind().isStorage()) {
            work.status = storageStatus();
            return;
        }
        if (kind() == MachineKind.TERMINAL) {
            work.status = matterNetwork(false).status();
            return;
        }
        if (!industrialStructureReady()) {
            String diagnostic = IndustrialStructure.inspect(this).diagnostic();
            if (boundNetwork != null) restartChangedBatch(diagnostic); else work.status = diagnostic;
            return;
        }
        // Analysis and physical preparation are independent of the matter network.
        boolean needsStorage = kind() != MachineKind.ANALYZER && !PreparationTier.isPreparation(kind());
        MachineMatterNetwork network = needsStorage ? matterNetwork(false) : null;
        MatterStore store = network == null ? null : network.inventory();
        if (network != null && !network.ready()) {
            if (boundNetwork != null) restartChangedBatch(network.status()); else work.status = network.status();
            return;
        }
        if (network != null && !acceptsNetwork(network)) {
            restartChangedBatch("storage_changed");
            return;
        }
        var runState = control.runState();
        if (!runState.running()) {
            if (kind() == MachineKind.ANALYZER) {
                work.status = operations.hasValidAnalysis(registry, analysis) ? "analyzed"
                        : operations.analyzableSample(registry) ? "analysis_needed" : operations.inputStatus(registry);
            } else work.status = supportsBatchControl() && !runState.repeat() && runState.queuedBatches() == 0
                    ? "awaiting_request" : "paused";
            return;
        }
        var operation = operations.operation(registry);
        if (operation == null) { work.status = operations.inputStatus(registry); resetProgress(); return; }
        int speed = speed();
        String fingerprint = operation.signature() + ":" + operation.cost() + ":" + operation.ticks() + ":" + speed;
        if (IndustrialStructure.isIndustrial(kind())) fingerprint += ":" + IndustrialStructure.inspect(this).layoutFingerprint();
        if (!fingerprint.equals(work.job)) { work.progress = 0; work.job = fingerprint; }
        work.duration = operation.ticks();
        String reason = operations.readiness(needsStorage, store, operation);
        if (!reason.equals("ready")) { work.status = reason; return; }
        if (store != null) { storageSession.bind(store); boundNetwork = network; }
        work.progress = (int) Math.min(work.duration, (long) work.progress + speed);
        visuals.capture(items.get(0), operation.visualResult(), work.progress, work.duration);
        work.processedThisTick = true;
        boolean preparation = PreparationTier.isPreparation(kind());
        work.preparationStage = preparation ? PreparationWork.stage(work.progress, work.duration) : -1;
        work.preparationMixing = preparation && operations.mixingPreparation();
        work.status = preparation ? PreparationWork.stageName(work.preparationStage, work.preparationMixing, kind() == MachineKind.SINTER_FURNACE) : "working";
        setChanged();
        if (work.progress < work.duration) return;
        if (!industrialStructureReady()) { work.status = IndustrialStructure.inspect(this).diagnostic(); work.processedThisTick = false; return; }
        // A fresh bounded graph must still have the same owners, routes and mounted inventories.
        if (needsStorage) {
            var finalNetwork = matterNetwork(true);
            if (!finalNetwork.ready() || !acceptsNetwork(finalNetwork)) {
                restartChangedBatch(finalNetwork.ready() ? "storage_changed" : finalNetwork.status());
                return;
            }
            network = finalNetwork;
            store = finalNetwork.inventory();
        }
        reason = operations.readiness(needsStorage, store, operation);
        if (!reason.equals("ready")) { work.status = reason; work.processedThisTick = false; return; }
        boolean changedOwnedState = false;
        List<MatterNetworkInventory.MemberActivity> cableActivity;
        committingOwnedState = true;
        try {
            cableActivity = network == null ? List.of() : commitNetworkMatter(network, operation.inputs(), operation.outputs());
            if (cableActivity == null) {
                work.processedThisTick = false;
                work.status = MachineReadiness.check(true, store, operation.inputs(), operation.outputs(), true,
                        energy.getAmountAsLong(), operation.cost());
                return;
            }
            // Both callbacks below are deferred until matter, physical resources, FE and the queue agree.
            changedOwnedState = true;
            operation.finish().run();
            energy.set((int) (energy.getAmountAsLong() - operation.cost()));
            if (supportsBatchControl()) control.runState(control.runState().completed());
            resetProgress();
            work.status = kind() == MachineKind.ANALYZER ? "analyzed" : "ready";
            if (kind() == MachineKind.ANALYZER) control.runState(control.runState().pause());
        } finally {
            committingOwnedState = false;
            if (changedOwnedState) {
                // Also flush actual authoritative changes if an unexpected exception interrupts finalization.
                if (network != null) network.markChanged();
                setChanged();
            }
        }
        if (network != null) publishNetworkActivity(network, cableActivity);
    }

    /** A null result means no matter was committed. Empty activity still means the commit succeeded. */
    @Nullable List<MatterNetworkInventory.MemberActivity> commitNetworkMatter(MachineMatterNetwork network,
            Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        var inventory = network.inventory();
        if (inventory == null) return null;
        if (level instanceof ServerLevel server && server.getGameTime() >= nextMatterActivityTick
                && network.hasCables() && MatterCableActivity.canTrack(server)) {
            return inventory.applyTracked(inputs, outputs);
        }
        return inventory.apply(inputs, outputs) ? List.of() : null;
    }

    void publishNetworkActivity(MachineMatterNetwork network, List<MatterNetworkInventory.MemberActivity> activity) {
        if (activity.isEmpty() || !(level instanceof ServerLevel server)
                || !MatterCableActivity.beginRouteLookup(server) || !network.current(this, false)) return;
        nextMatterActivityTick = level.getGameTime() + 6;
        network.pulseActivity(server, worldPosition, activity);
    }

    /** Work units per tick, from the machine tier, a formed structure and speed upgrades. */
    int speed() {
        if (PreparationTier.manual(kind())) return 1;
        int base = kind() == MachineKind.INDUSTRIAL_REACTOR ? 2 : PreparationTier.speed(kind());
        if (IndustrialStructure.isIndustrial(kind())) {
            var formation = IndustrialStructure.inspect(this);
            if (formation.formed()) base = formation.baseWorkRate(kind());
        }
        return MachineEnergyPolicy.speed(base, upgrade(), items.get(2).getCount());
    }
    /** The FE price of one batch with the installed upgrades and structure. */
    long cost(long base) {
        IndustrialPerformance performance = null;
        if (IndustrialStructure.isIndustrial(kind())) {
            var formation = IndustrialStructure.inspect(this);
            if (formation.formed()) performance = formation.performance();
        }
        return MachineEnergyPolicy.cost(base, upgrade(), items.get(2).getCount(), kind(), performance);
    }
    private MachineEnergyPolicy.Upgrade upgrade() {
        ItemStack upgrade = items.get(2);
        if (upgrade.is(ModContent.SPEED_UPGRADE.get())) return MachineEnergyPolicy.Upgrade.SPEED;
        return upgrade.is(ModContent.EFFICIENCY_UPGRADE.get()) ? MachineEnergyPolicy.Upgrade.EFFICIENCY : MachineEnergyPolicy.Upgrade.NONE;
    }
    void resetProgress() { work.reset(); storageSession.clear(); boundNetwork = null; }

    /**
     * The machine's matter network. One discovery serves every machine it found, so a change costs one bounded
     * scan per network instead of one per machine. A deep check re-reads every node once per game tick.
     */
    MachineMatterNetwork matterNetwork(boolean deep) {
        if (cachedNetwork == null || !cachedNetwork.current(this, deep)) {
            var network = MachineMatterNetwork.discover(this);
            for (var machine : network.machines()) machine.cachedNetwork = network;
            cachedNetwork = network;
        }
        return cachedNetwork;
    }
    /** Recovery parcels restore their real quantity; unknown definitions remain storable by stable key. */
    public long recoverMatter(UUID actor, SubstanceKey key, long maximum) {
        if (actor == null || key == null || maximum <= 0 || !Objects.equals(owner, actor) || !isLiveServerMachine()
                || Transaction.getCurrentOpenedTransaction() != null) return 0;
        if (kind().isStorage()) {
            if (!storageStatus().equals("ready")) return 0;
            long accepted = insertable(matterStore(), key, maximum);
            if (accepted == 0 || !matterStore().apply(Map.of(), Map.of(key, accepted))) return 0;
            setChanged();
            return accepted;
        }
        var network = matterNetwork(true);
        if (!network.ready()) return 0;
        long accepted = insertable(network.inventory(), key, maximum);
        if (accepted == 0) return 0;
        var activity = commitNetworkMatter(network, Map.of(), Map.of(key, accepted));
        if (activity == null) return 0;
        network.markChanged();
        publishNetworkActivity(network, activity);
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
    /**
     * The unpaid batch restarts against the changed network or structure. Nothing was consumed, and the run
     * state is kept, so repeat and queued work resumes by itself once the source is valid again.
     */
    private void restartChangedBatch(String diagnostic) {
        resetProgress();
        work.processedThisTick = false;
        work.status = diagnostic;
    }
    /** Integrations must revalidate immediately before dispatching an order on the server thread. */
    public boolean isCurrentNetworkWorker(UUID requester, MachineBlockEntity worker) {
        if (requester == null || !Objects.equals(owner, requester) || !isLiveServerMachine()
                || worker == null || !worker.isLiveServerMachine() || worker.getLevel() != level
                || !Objects.equals(worker.owner, requester)) return false;
        var network = matterNetwork(true);
        return network.ready() && network.hasWorker(worker);
    }
    TerminalDevicesPayload.Device terminalDevice() {
        var choice = terminalChoice();
        return new TerminalDevicesPayload.Device(worldPosition, kind(), work.status, choice == null ? "" : choice.id(),
                Math.min(work.progress, work.duration), work.duration,
                supportsBatchControl() ? control.runState().queuedBatches() : 0, choice != null && canQueueFromTerminal());
    }
    boolean queueFromTerminal(String expectedRecipe) {
        var choice = terminalChoice();
        return choice != null && choice.id().equals(expectedRecipe) && canQueueFromTerminal() && requestBatch();
    }
    private boolean canQueueFromTerminal() {
        var runState = control.runState();
        return !runState.repeat() && runState.queuedBatches() < MachineRunState.MAX_QUEUED_BATCHES;
    }
    private MachineChoices.@Nullable Choice terminalChoice() {
        if (!supportsBatchControl() || networkJobs) return null;
        var choice = control.selectedChoice();
        return choice != null && choice.id().equals(control.selectedId()) ? choice : null;
    }

    public MachineSnapshot snapshot(int containerId) { return MachineSnapshots.build(this, containerId); }
    MachineSnapshots.Stock stock() { return MachineSnapshots.stock(this, MachineSnapshot.MAX_ENTRIES); }
    MachineProgressPayload progress(int containerId) { return MachineSnapshots.progress(this, containerId); }
    /** A short read-only overlay for an owner, without previews, choices or the full stock. */
    public @Nullable Summary summaryFor(Player player, int rows) {
        return canAccess(player) ? MachineSnapshots.summary(this, rows) : null;
    }
    public record Summary(String status, int energy, int progress, int duration, long used, long capacity, String storageSource,
                          int types, List<MachineSnapshot.Entry> entries, boolean automation, int fluidAmount, String fluidId,
                          MachineSnapshot.StructureView structure, List<MachineSnapshot.CellView> cells) {
        public Summary { entries = List.copyOf(entries); cells = List.copyOf(cells); }
    }

    @Override protected void saveAdditional(ValueOutput output) {
        bays.flush();
        super.saveAdditional(output);
        if (unsupportedData != null) {
            output.store(STORED_DATA, unsupportedData);
            return;
        }
        output.putInt("schema", MachineSchema.VERSION);
        if (owner != null) output.putString("owner", owner.toString());
        output.putBoolean("automation", automation);
        sides.save(output);
        output.putBoolean("network_jobs", networkJobs);
        if (automationHost != null) output.store("automation_host", RAW_TAG_CODEC, automationHost.save());
        var runState = control.runState();
        output.putBoolean("running", runState.running());
        output.putBoolean("repeat", runState.repeat());
        output.putInt("queued_batches", runState.queuedBatches());
        output.putString("selection", control.selectedId());
        structure.save(output);
        if (!analysis.isEmpty()) {
            ValueOutput savedAnalysis = output.child("analysis");
            savedAnalysis.putString("item", analysis.itemId());
            savedAnalysis.putString("profile", analysis.profileFingerprint());
        }
        // Progress is deliberately not persisted: no input or energy was consumed before final commit.
        output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(items));
        energy.serialize(output.child("power"));
        fluids.serialize(output.child("tank"));
    }
    @Override protected void loadAdditional(ValueInput input) {
        loadingItemVisuals = true;
        try {
            loadMachineState(input);
        } finally {
            loadingItemVisuals = false;
        }
        // Data replaced in place, for example by a command, changes owner, cells and side roles at once.
        touchObservations();
        // The world save remains authoritative; visual state is derived, never persisted separately.
        if (itemVisualsStable()) {
            if (isLiveServerMachine()) publishItemVisuals();
            else visuals.refreshItems(kind(), this::stack);
        }
    }
    private void loadMachineState(ValueInput input) {
        super.loadAdditional(input);
        worker.release();
        resetProgress();
        lastRegistry = null;
        operations.forgetPreparationRecipe();
        cachedNetwork = null;
        items = NonNullList.withSize(MachineCellAccess.inventorySize(kind()), ItemStack.EMPTY);
        var savedItems = input.read("items", RAW_TAG_CODEC).flatMap(raw -> ItemStack.OPTIONAL_CODEC.listOf()
                .parse(input.lookup().createSerializationContext(NbtOps.INSTANCE), raw).result());
        String rejection = MachineSchema.rejection(input.getInt("schema"), input.getString("owner"),
                savedItems.map(List::size), items.size());
        unsupportedData = null;
        if (rejection != null) {
            // Keep everything exactly as stored; the machine stays inert and its item carries the data unchanged.
            unsupportedData = input.read(STORED_DATA).orElseGet(CompoundTag::new);
            owner = input.getString("owner").map(MachineSchema::owner).orElse(null);
            ReMatter.LOGGER.warn("Machine data at {} is kept unchanged and inactive: {}", worldPosition, rejection);
            work.status = "unsupported_data";
            bays.mountAll();
            return;
        }
        owner = input.getString("owner").map(MachineSchema::owner).orElse(null);
        automation = input.getBooleanOr("automation", false);
        sides.load(input, kind());
        networkJobs = supportsNetworkJobs() && input.getBooleanOr("network_jobs", false);
        if (kind() == MachineKind.NETWORK_INTERFACE) input.read("automation_host", RAW_TAG_CODEC).ifPresent(raw -> automationHost().load(raw));
        String savedSelection = input.getStringOr("selection", "");
        String selection = MachineSchema.selection(savedSelection);
        var runState = MachineRunState.restore(input.getBooleanOr("running", false), input.getBooleanOr("repeat", false),
                input.getIntOr("queued_batches", 0));
        control.load(selection.equals(savedSelection) ? runState : MachineRunState.manual(), selection);
        structure.load(input);
        ValueInput savedAnalysis = input.childOrEmpty("analysis");
        analysis = AnalysisState.restore(savedAnalysis.getStringOr("item", ""), savedAnalysis.getStringOr("profile", ""));
        var stacks = savedItems.orElseThrow();
        for (int i = 0; i < items.size(); i++) items.set(i, stacks.get(i));
        energy.deserialize(input.childOrEmpty("power"));
        fluids.deserialize(input.childOrEmpty("tank"));
        bays.mountAll();
    }

    @Override public Component getDisplayName() { return Component.translatable("block.rematter." + kind().id()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return canUse(player) ? new MachineMenu(id, inventory, this) : null; }
    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int index) { int bay = MachineCellAccess.bayForSlot(kind(), index); if (bay >= 0) bays.flush(bay); return items.get(index); }
    @Override public ItemStack removeItem(int index, int amount) {
        if (MachineCellAccess.isCellSlot(kind(), index) && !prepareCellRemoval(index)) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(items, index, amount);
        if (index == 2) worker.forgetRefusal();
        if (!result.isEmpty()) { if (MachineCellAccess.isCellSlot(kind(), index)) mountCell(index); else setChanged(); }
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int index) {
        if (MachineCellAccess.isCellSlot(kind(), index) && !prepareCellRemoval(index)) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.takeItem(items, index);
        if (index == 2) worker.forgetRefusal();
        if (MachineCellAccess.isCellSlot(kind(), index)) mountCell(index);
        return result;
    }
    @Override public void setItem(int index, ItemStack stack) {
        if (MachineCellAccess.isCellSlot(kind(), index)) {
            if (!prepareCellRemoval(index) || Transaction.getCurrentOpenedTransaction() != null) return;
            if (!stack.isEmpty() && (!(stack.getItem() instanceof MatterCellItem) || stack.getCount() != 1)) return;
            if (!stack.isEmpty()) for (int slot = 0; slot < items.size(); slot++)
                if (slot != index && items.get(slot) == stack) return;
            items.set(index, stack); mountCell(index); return;
        }
        items.set(index, stack);
        if (index == 2) worker.forgetRefusal();
        if (index == 0 && kind() == MachineKind.ANALYZER) { analysis = AnalysisState.EMPTY; control.runState(MachineRunState.automatic()); }
        stack.limitSize(getMaxStackSize(stack)); setChanged();
    }
    @Override public void setItem(int index, ItemStack stack, boolean insideTransaction) {
        if (MachineCellAccess.isCellSlot(kind(), index)) { setItem(index, stack); return; }
        if (insideTransaction) items.set(index, stack);
        else setItem(index, stack);
    }
    @Override public void onTransfer(int index, int amountChange, TransactionContext transaction) {
        if (index == 0 && amountChange != 0 && kind() == MachineKind.ANALYZER) inputTransferCommit.updateSnapshots(transaction);
        if (amountChange != 0 && MachineVisuals.showsItems(kind())
                && (index == 0 || index == 1 || (index == 3 || index == 4) && MachineVisuals.showsSecondaryItems(kind()))) {
            itemVisualCommit.updateSnapshots(transaction);
        }
    }
    @Override public boolean stillValid(Player player) { return canUse(player) && Container.stillValidBlockEntity(this, player); }
    @Override public boolean canPlaceItem(int index, ItemStack stack) {
        if (unsupportedData != null || networkJobs && index != 2) return false;
        if (MachineCellAccess.isCellSlot(kind(), index)) return stack.getItem() instanceof MatterCellItem && stack.getCount() == 1 && canRemoveCell(index);
        if (index == 1) return false;
        if (index == 2) return kind().processes() && !PreparationTier.manual(kind()) && (stack.is(ModContent.SPEED_UPGRADE.get()) || stack.is(ModContent.EFFICIENCY_UPGRADE.get()));
        return operations.acceptsInput(index, stack, ReMatter.chemistry());
    }
    @Override public boolean canTakeItem(Container target, int index, ItemStack stack) { return automationAllowed() && !IndustrialStructure.isIndustrial(kind()) && !kind().isStorage() && index == 1; }
    private boolean allowsItemInput(Direction side) {
        return automationAllowed() && !kind().isStorage() && !IndustrialStructure.isIndustrial(kind()) && allowsPort(side, MachinePortMode.ITEM_INPUT);
    }
    private boolean allowsItemOutput(Direction side) {
        return automationAllowed() && !kind().isStorage() && !IndustrialStructure.isIndustrial(kind()) && allowsPort(side, MachinePortMode.ITEM_OUTPUT);
    }
    @Override public int[] getSlotsForFace(Direction side) {
        boolean input = allowsItemInput(side), output = allowsItemOutput(side);
        if (!input) return output ? new int[]{1} : new int[0];
        if (PreparationTier.isPreparation(kind())) return output ? new int[]{0, 3, 4, 1} : new int[]{0, 3, 4};
        return output ? new int[]{0, 1} : new int[]{0};
    }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction side) {
        return allowsItemInput(side) && (index == 0 || PreparationTier.isPreparation(kind()) && (index == 3 || index == 4)) && canPlaceItem(index, stack);
    }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) {
        return allowsItemOutput(side) && index == 1;
    }
    @Override public void clearContent() { for (int slot : cellSlots()) if (!items.get(slot).isEmpty()) return; items.clear(); setChanged(); }
}
