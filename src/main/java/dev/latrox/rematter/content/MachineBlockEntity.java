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
import dev.latrox.rematter.automation.AutomationPort;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.preparation.*;
import dev.latrox.rematter.network.MachineProgressPayload;
import dev.latrox.rematter.network.TerminalDevicesPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.neoforged.neoforge.transfer.StacksResourceHandler;
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
    private static final Map<ServerLevel, BatchReservations> PLANNING = new java.util.WeakHashMap<>();
    public static final int MAX_ENERGY_CAPACITY = MatterLimits.MAX_MACHINE_ENERGY;
    public static final int TANK_CAPACITY = MatterLimits.MACHINE_FLUID;
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
    private NonNullList<ItemStack> items = NonNullList.withSize(MachineCellAccess.INVENTORY_SIZE, ItemStack.EMPTY);
    private final MachineCellBays bays = new MachineCellBays(kind(), this::stack,this::touchObservations);
    private final MachineStorageSession storageSession = new MachineStorageSession();
    private final MachineSideConfig sides = new MachineSideConfig();
    private final MachineWork work = new MachineWork();
    private MachineSettings settings=MachineSettings.DEFAULT;
    private long ventedTotal;
    private final MachineVisuals visuals = new MachineVisuals();
    private final VisualActivity activity = new VisualActivity();
    private final MachineOperations operations = new MachineOperations(this);
    private final MachineControl control = new MachineControl(this);
    private final NetworkJobWorker worker = new NetworkJobWorker(this);
    private final MachineStructureLink structure = new MachineStructureLink(this);
    private @Nullable CompoundTag unsupportedData;
    private UUID owner;
    private SecurityMode securityMode = SecurityMode.PUBLIC;
    private String customName = "";
    private ChunkAnchorManager.Status anchorStatus = ChunkAnchorManager.Status.IDLE;
    private boolean automation;
    private boolean autoEject;
    private boolean pushingOutputs;
    private boolean networkJobs;
    private MachineAutomationHost automationHost;
    private AnalysisState analysis = AnalysisState.EMPTY;
    private ChemistryRegistry lastRegistry;
    private MachineMatterNetwork cachedNetwork;
    private MachineMatterNetwork boundNetwork;
    private long nextMatterActivityTick;
    /** Not saved: spaces the product chime of a Material Synthesizer whose batches follow each other closely. */
    private long nextSynthesizerChime;
    private boolean committingOwnedState;
    /** Whether this tick found the matter network of a Terminal or a Network Interface ready. */
    private boolean networkOnline;
    private final MachineClientSync clientSync = new MachineClientSync();
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    private boolean redstonePoweredLastTick, redstonePulsePending, redstoneSampled;
    private TerminalJobCoordinator.@Nullable StepRuntime terminalStep;
    private MachineSoundLevel soundLevel = MachineSoundLevel.HIGH;

    private final ControllerEnergyBudget energyBudget=new ControllerEnergyBudget();
    private final MachineExtensions extensions=new MachineExtensions(this);
    MachineExtensions extensions(){return extensions;}
    private final ControllerThroughput transferWork=new ControllerThroughput();
    private final DynamicEnergyHandler energy = new DynamicEnergyHandler();

    private final class DynamicEnergyHandler extends SimpleEnergyHandler {
        private DynamicEnergyHandler() { super(MatterLimits.COMPACT_MACHINE_ENERGY, ControllerEnergyBudget.MAX_INPUT, 0); }
        void syncCapacity(int nextCapacity) { this.capacity = nextCapacity; }
        @Override public long getCapacityAsLong() {
            int cap = energyCapacity();
            this.capacity = cap;
            return cap;
        }
        @Override public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if(!isLiveServerMachine()||kind().generator()||kind().isEnergyReactor()||PreparationTier.manual(kind())||!industrialStructureReady())return 0;
            this.capacity = energyCapacity();
            if (this.energy >= this.capacity) return 0;
            return energyBudget.insert(level.getGameTime(),amount,NativeCableEnergy.rate(),transaction,allowed->super.insert(allowed,transaction));
        }
        @Override protected void onEnergyChanged(int previous) { if (!committingOwnedState) setChanged(); }
    }
    private final FluidStacksResourceHandler fluids = new FluidStacksResourceHandler(1, TANK_CAPACITY) {
        @Override public boolean isValid(int index, FluidResource resource) {
            return isLiveServerMachine() && !networkJobs && (kind().decomposition() || kind().synthesis())
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
        energy.syncCapacity(energyCapacity());
    }
    boolean supportsBatchControl() { return MachineControl.batchControl(kind()); }
    public MachineKind kind() { return ((MachineBlock) getBlockState().getBlock()).kind(); }
    public int speedUpgradeCount() {
        if (!MachineEnergyPolicy.supportsUpgrades(kind()) || items.size() <= MachineCellAccess.SPEED_SLOT) return 0;
        ItemStack stack = items.get(MachineCellAccess.SPEED_SLOT);
        return stack.is(ModContent.SPEED_UPGRADE.get()) ? Math.clamp(stack.getCount(), 0, MachineEnergyPolicy.MAX_UPGRADES) : 0;
    }
    public int efficiencyUpgradeCount() {
        if (!MachineEnergyPolicy.supportsUpgrades(kind()) || items.size() <= MachineCellAccess.EFFICIENCY_SLOT) return 0;
        ItemStack stack = items.get(MachineCellAccess.EFFICIENCY_SLOT);
        return stack.is(ModContent.EFFICIENCY_UPGRADE.get()) ? Math.clamp(stack.getCount(), 0, MachineEnergyPolicy.MAX_UPGRADES) : 0;
    }
    public int parallelUpgradeCount() {
        if (!IndustrialStructure.isIndustrial(kind()) || items.size() <= MachineCellAccess.PARALLEL_SLOT) return 0;
        ItemStack stack = items.get(MachineCellAccess.PARALLEL_SLOT);
        return stack.is(ModContent.PARALLEL_UPGRADE.get()) ? Math.clamp(stack.getCount(), 0, 3) : 0;
    }
    public int energyCapacity() {
        return MachineEnergyPolicy.energyCapacity(kind(), efficiencyUpgradeCount());
    }
    void syncEnergyCapacity() {
        int cap = energyCapacity();
        energy.syncCapacity(cap);
        if (Transaction.getCurrentOpenedTransaction() == null && energy.getAmountAsLong() > cap) {
            energy.set(cap);
        }
    }
    /** Mekanism-style Sneak+Right-Click in-world upgrade installation. Returns true if the held item was handled. */
    boolean tryInstallUpgrade(Player player, ItemStack held) {
        if (!isLiveServerMachine() || unsupportedData != null || !MachineInputPolicy.isUpgradeItem(held)) return false;
        int slot = MachineInputPolicy.upgradeSlotFor(kind(), held);
        if (slot < 0) {
            player.sendOverlayMessage(Component.translatable("message.rematter.upgrade.unsupported", held.getHoverName()));
            return true;
        }
        int limit = MachineInputPolicy.upgradeSlotLimit(kind(), slot);
        ItemStack current = items.get(slot);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, held)) {
            player.sendOverlayMessage(Component.translatable("message.rematter.upgrade.unsupported", held.getHoverName()));
            return true;
        }
        if (current.getCount() >= limit) {
            player.sendOverlayMessage(Component.translatable("message.rematter.upgrade.full", held.getHoverName(), current.getCount(), limit));
            return true;
        }
        if (current.isEmpty()) items.set(slot, held.copyWithCount(1));
        else current.grow(1);
        if (!player.hasInfiniteMaterials()) held.shrink(1);
        syncEnergyCapacity();
        if (slot == MachineCellAccess.ANCHOR_SLOT) syncChunkAnchor();
        setChanged();
        ModSounds.play(level, worldPosition, ModSounds.PORT_CYCLE);
        player.sendOverlayMessage(Component.translatable("message.rematter.upgrade.installed",
                held.getHoverName(), items.get(slot).getCount(), limit));
        return true;
    }
    public String customName() { return customName; }
    public void setCustomName(String next) {
        String clean = CustomNames.sanitize(next);
        if (customName.equals(clean)) return;
        customName = clean;
        setChanged();
    }
    public boolean hasAnchorUpgrade() {
        return items.size() > MachineCellAccess.ANCHOR_SLOT
                && items.get(MachineCellAccess.ANCHOR_SLOT).is(ModContent.ANCHOR_UPGRADE.get());
    }
    public ChunkAnchorManager.Status anchorStatus() { return anchorStatus; }
    public Set<net.minecraft.world.level.ChunkPos> desiredAnchorChunks() {
        if (IndustrialStructure.isIndustrial(kind())) {
            var formation = IndustrialStructure.inspect(this);
            if (formation.formed() && formation.minBounds() != null && formation.maxBounds() != null) {
                int minCx = formation.minBounds().getX() >> 4, maxCx = formation.maxBounds().getX() >> 4;
                int minCz = formation.minBounds().getZ() >> 4, maxCz = formation.maxBounds().getZ() >> 4;
                var set = new HashSet<net.minecraft.world.level.ChunkPos>();
                for (int cx = minCx; cx <= maxCx; cx++) {
                    for (int cz = minCz; cz <= maxCz; cz++) {
                        set.add(new net.minecraft.world.level.ChunkPos(cx, cz));
                    }
                }
                if (!set.isEmpty()) return Set.copyOf(set);
            }
        }
        return ChunkAnchorManager.square(worldPosition, 0);
    }
    public void syncChunkAnchor() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        if (unsupportedData == null && owner != null && hasAnchorUpgrade()) {
            anchorStatus = ChunkAnchorManager.sync(server, worldPosition, owner, desiredAnchorChunks());
        } else {
            ChunkAnchorManager.release(server, worldPosition);
            anchorStatus = new ChunkAnchorManager.Status(false, 0,
                    ChunkAnchorManager.ownerChunkCount(server.getServer(), owner),
                    ChunkLoadingConfig.maxChunksPerPlayer(), "idle");
        }
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) syncChunkAnchor();
    }
    long drivePrimalAmount(){return kind()==MachineKind.DRIVE?bays.primalAmount():0;}
    long drivePrimalCapacity(){return kind()==MachineKind.DRIVE?bays.primalCapacity():0;}
    long drivePrimalFreeSpace(){return kind()==MachineKind.DRIVE?bays.primalFreeSpace():0;}
    long creditDrivePrimal(long amount,TransactionContext tx){return kind()==MachineKind.DRIVE?bays.creditPrimal(amount,tx):0;}
    long debitDrivePrimal(long amount,TransactionContext tx){return kind()==MachineKind.DRIVE?bays.debitPrimal(amount,tx):0;}
    public boolean hasExtractionFocus(){return kind()==MachineKind.DEEP_EXTRACTOR&&stack(MachineCellAccess.PRESSURE_SLOT).is(ModContent.EXTRACTION_FOCUS_UPGRADE.get());}
    public boolean hasMassCompression(){return kind()==MachineKind.PRIMAL_REACTOR&&stack(MachineCellAccess.MASS_COMPRESSION_SLOT).is(ModContent.MASS_COMPRESSION_UPGRADE.get());}
    public long matrixEnergy(){return extensions.matrixEnergy();}
    public long matrixCapacity(){return extensions.matrixCapacity();}
    public long matrixTransferRate(){return extensions.matrixTransferRate();}
    public long matrixInRate(){return extensions.matrixInRate();}
    public long matrixOutRate(){return extensions.matrixOutRate();}
    public boolean matrixAutoOutput(){return extensions.matrixAutoOutput();}
    public long reactorEnergy(){return extensions.reactorEnergy();}
    public long reactorCapacity(){return extensions.reactorCapacity();}
    public long reactorMaxOutputRate(){return extensions.reactorMaxOutputRate();}
    public long reactorGenRate(){return extensions.reactorGenRate();}
    public long reactorOutRate(){return extensions.reactorOutRate();}
    public int reactorFlux(){return extensions.reactorFlux();}
    public EnergyReactorRecipes.FuelMode reactorFuelMode(){return extensions.reactorFuelMode();}
    public boolean reactorCooled(){return extensions.reactorCooled();}
    public int primalStability(){return extensions.primalStability();}
    public int primalMaxStability(){return extensions.primalMaxStability();}
    public int visualMatrixFill(){return clientSync.visualMatrixFill(level, extensions.matrixFillPermille());}
    public int visualPrimalStability(){return clientSync.visualPrimalStability(level, extensions.primalStability());}
    public int visualReactorFlux(){return clientSync.visualReactorFlux(level, extensions.reactorFlux());}
    public boolean visualReactorCooled(){return clientSync.visualReactorCooled(level, extensions.reactorCooled());}
    boolean hasCatalyst(String required){
        if(required==null||required.isEmpty())return true;
        ItemStack focus=stack(MachineCellAccess.DISCOVERY_SLOT);
        if(focus.isEmpty())return false;
        String id=BuiltInRegistries.ITEM.getKey(focus.getItem()).toString();
        if(id.equals(required))return true;
        return "rematter:particle_resonator".equals(required)
                && focus.is(dev.latrox.rematter.primal.PrimalContent.PRIMAL_MATRIX_CORE.get());
    }
    boolean syncSelectionCatalogue(ChemistryRegistry registry){
        if(registry!=lastRegistry){
            boolean initialLoad=lastRegistry==null;
            lastRegistry=registry;
            if(!initialLoad||work.restoredLanes==0)resetProgress();
            if(!control.restore(registry)){
                resetProgress();
                interruptTerminalStep();
                return false;
            }
        }
        if(control.selectionUnavailable()){
            if(control.runState().running()){control.runState(control.runState().pause());setChanged();}
            return false;
        }
        return true;
    }
    void completeRunLanes(int lanes){
        if(!supportsBatchControl())return;
        for(int i=0;i<lanes;i++)control.runState(control.runState().completed());
    }
    boolean hasProbeBelow(String probe){
        if(level==null||probe==null||probe.isEmpty())return false;
        BlockPos below=worldPosition.below();
        if(!level.hasChunkAt(below))return false;
        BlockState state=level.getBlockState(below);
        return switch(probe){
            case "deepslate" -> worldPosition.getY() <= 0
                    && (state.is(net.minecraft.world.level.block.Blocks.DEEPSLATE)
                    || state.is(net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE)
                    || state.is(net.minecraft.world.level.block.Blocks.TUFF)
                    || state.is(net.minecraft.world.level.block.Blocks.BEDROCK));
            case "water" -> {
                var f1 = level.getFluidState(below);
                var f2 = level.getFluidState(below.below());
                yield f1.is(net.minecraft.tags.FluidTags.WATER) && f1.isSource()
                        && f2.is(net.minecraft.tags.FluidTags.WATER);
            }
            case "lava" -> {
                var f1 = level.getFluidState(below);
                yield f1.is(net.minecraft.tags.FluidTags.LAVA) && f1.isSource();
            }
            case "void" -> {
                int minY = level.getMinY();
                if (below.getY() < minY) yield true;
                var cursor = new BlockPos.MutableBlockPos(worldPosition.getX(), below.getY(), worldPosition.getZ());
                for (int y = below.getY(); y >= minY; y--) {
                    cursor.setY(y);
                    if (!level.getBlockState(cursor).isAir()) yield false;
                }
                yield true;
            }
            default -> false;
        };
    }
    java.util.Set<java.util.UUID> establishCellIdentities(){return bays.establishIdentities();}
    java.util.Optional<java.util.UUID> creditConvertedMatter(java.util.UUID op,SubstanceKey key,long amount,TransactionContext tx){return bays.credit(op,key,amount,tx);}
    int conversionBay(SubstanceKey key,long amount){return bays.conversionBay(key,amount);}
    boolean running(){return control.runState().running();}
    boolean allowsPowerOutput(Direction side){return allowsPort(side,MachinePortMode.POWER_OUTPUT)||allowsPort(side,MachinePortMode.POWER);}
    public SimpleEnergyHandler energy() { return energy; }
    public FluidStacksResourceHandler fluids() { return fluids; }
    public MachineSettings settings() { return settings; }
    public long ventedTotal() { return ventedTotal; }
    public int crystallizationTier() {
        if (!kind().synthesis()) return 1;
        if (kind() == MachineKind.INDUSTRIAL_SYNTHESIZER) return industrialStructureReady() ? 2 : 1;
        return stack(MachineCellAccess.PRESSURE_SLOT).is(ModContent.PRESSURE_MODULE.get())
                || stack(2).is(ModContent.PRESSURE_MODULE.get()) ? 2 : 1;
    }
    public int parallelCapacity() {
        if(!IndustrialStructure.isIndustrial(kind()))return 1;
        var formation=IndustrialStructure.inspect(this);if(!formation.formed())return 1;
        int installed=stack(MachineCellAccess.PARALLEL_SLOT).is(ModContent.PARALLEL_UPGRADE.get())?Math.min(3,stack(MachineCellAccess.PARALLEL_SLOT).getCount()):0;
        return Math.min(1+installed,formation.laneRoom(kind()));
    }
    public int parallelRunning() { return Math.min(parallelCapacity(),networkJobs?worker.parallelRunning():work.ticket==null?0:work.ticket.remaining()); }
    public boolean researchAllows(String material){return level instanceof ServerLevel server&&dev.latrox.rematter.research.ResearchService.allowsSynthesis(server,owner,material);}
    boolean knownMaterial(String material){return level instanceof ServerLevel server&&dev.latrox.rematter.research.ResearchData.current().forItem(material).map(f->dev.latrox.rematter.research.ResearchService.knownFamilies(server.getServer(),owner).contains(f.id())).orElse(false);}
    void learnMaterial(String material,dev.latrox.rematter.research.LearningPath path){if(level instanceof ServerLevel server)dev.latrox.rematter.research.ResearchService.learn(server,owner,material,path);}
    void learnProcess(Map<SubstanceKey,Long> inputs,Map<SubstanceKey,Long> outputs){if(level instanceof ServerLevel server)dev.latrox.rematter.research.ResearchService.learnProcess(server.getServer(),owner,inputs,outputs);}
    boolean allowsProcess(dev.latrox.rematter.chemistry.ProcessRecipe recipe){
        return processRestriction(recipe).isEmpty();
    }
    String processRestriction(dev.latrox.rematter.chemistry.ProcessRecipe recipe){
        if(recipe.minField()==0)return "";
        boolean reactorMatch = recipe.type() == dev.latrox.rematter.chemistry.ProcessType.TRANSMUTATION
                ? kind() == MachineKind.TRANSMUTATION_REACTOR
                : kind() == MachineKind.INDUSTRIAL_REACTOR;
        if(!reactorMatch||!industrialStructureReady())return "requires_field";
        var hardware=IndustrialStructure.inspect(this).performance();
        if(hardware.field()<recipe.minField())return "requires_field";
        if(!hasCatalyst(recipe.catalyst()))return "requires_catalyst";
        if(recipe.requiresKnownOutputs()&&!(level instanceof ServerLevel server&&dev.latrox.rematter.research.ResearchService.knownSubstances(server,owner).stream().anyMatch(key->key.id().equals(recipe.product()))))return "requires_research";
        return "";
    }
    int recoveryMask(){return (recoverySlot(3)?8:0)|(recoverySlot(4)?16:0);}
    boolean assayMode(){return extensions.assayMode();}
    private int preparationRecoveryMask;
    boolean recoverySlot(int slot){
        int bit=1<<slot;if((preparationRecoveryMask&bit)==0)return false;
        if(!stack(slot).isEmpty()||Transaction.getCurrentOpenedTransaction()!=null)return true;
        preparationRecoveryMask&=~bit;return false;
    }
    void addRecoveryOutputs(int mask){preparationRecoveryMask|=mask&24;setChanged();}
    boolean preparationPermits(dev.latrox.rematter.content.preparation.PreparationRecipe recipe){
        if(!PreparationTier.permits(kind(),recipe.minimumTier(),recipe.process()))return false;
        if(!IndustrialStructure.isIndustrial(kind()))return recipe.minPrecision()==0;
        var formation=IndustrialStructure.inspect(this);return formation.formed()&&formation.performance().precision()>=recipe.minPrecision()&&formation.performance().jobCapacity(recipe.steps())>0;
    }
    int capacityForInputs(Map<SubstanceKey,Long> inputs,int requested){
        if(kind()!=MachineKind.INDUSTRIAL_REACTOR&&kind()!=MachineKind.TRANSMUTATION_REACTOR)return requested;
        long per=dev.latrox.rematter.chemistry.MatterAmounts.total(inputs);if(per==0)return requested;
        return (int)Math.min(requested,IndustrialStructure.inspect(this).performance().processRoom()/per);
    }
    boolean progressAllows(String advancement) { return level instanceof ServerLevel server&&MachineProgression.allows(server,owner,advancement); }
    boolean targetActive() { return settings.target()>0&&control.runState().repeat(); }
    boolean hasManualWork() {
        if(networkJobs)return worker.readyClaim();
        if(!control.runState().running()||control.runState().queuedBatches()==0||!industrialStructureReady())return false;
        var operation=operations.operation(ReMatter.chemistry());if(operation==null)return false;
        boolean storage=kind()!=MachineKind.ANALYZER&&!PreparationTier.isPreparation(kind());
        var network=storage?matterNetwork(false):null;
        return (network==null||network.ready())&&operations.readiness(storage,network==null?null:network.inventory(),operation).equals("ready");
    }
    static void clearPlanning() { PLANNING.clear(); }
    private BatchReservations planning() { return PLANNING.computeIfAbsent((ServerLevel)level,ignored->new BatchReservations()); }
    public boolean configure(Player player,MachineSettings next) {
        boolean reserveOnly = kind().isEnergyReactor() || kind() == MachineKind.PRIMAL_CONVERTER;
        if(!isLiveServerMachine()||player.isSpectator()||!canAccess(player)||(!reserveOnly&&(!kind().processes()||kind()==MachineKind.ANALYZER||PreparationTier.manual(kind()))))return false;
        if (reserveOnly) {
            if (next.target() != 0 || !next.targetSubstance().isEmpty() || !next.ventedGases().isEmpty()) return false;
            if (next.equals(settings)) return true;
            settings = next;
            setChanged();
            return true;
        }
        if(!next.ventedGases().equals(settings.ventedGases())&&!canRemove(player))return false;
        for(String gas:next.ventedGases())if(!ReMatter.chemistry().isKnown(dev.latrox.rematter.chemistry.SubstanceKey.of(gas,dev.latrox.rematter.chemistry.Phase.GAS)))return false;
        boolean physical=kind().synthesis()||PreparationTier.isPreparation(kind());
        if(physical) {
            if(!next.targetSubstance().isEmpty())return false;
            long maximum=selectedId().startsWith(MachineChoices.FLUID_PREFIX)?TANK_CAPACITY:64;
            if(next.target()>maximum)return false;
        } else if(next.target()>0) {
            var op=operations.operation(ReMatter.chemistry());
            if(op==null||next.targetSubstance().isEmpty()||!op.outputs().containsKey(dev.latrox.rematter.chemistry.SubstanceKey.of(next.targetSubstance(),
                    dev.latrox.rematter.chemistry.Phase.valueOf(next.targetPhase().toUpperCase(java.util.Locale.ROOT)))))return false;
        }
        boolean hadTarget=targetActive();
        boolean targetChanged=next.target()!=settings.target()||!next.targetSubstance().equals(settings.targetSubstance())||!next.targetPhase().equals(settings.targetPhase());
        if(next.equals(settings))return true;
        if (targetChanged) interruptTerminalStep();
        settings=next;resetProgress();
        if(targetChanged) {
            if(next.target()>0)control.runState(MachineRunState.automatic());
            else if(hadTarget)control.runState(MachineRunState.manual());
        }
        setChanged();return true;
    }
    ItemStack stack(int slot) { return items.get(slot); }
    void putStack(int slot, ItemStack stack) { if(items.get(slot).isEmpty())preparationRecoveryMask&=~(1<<slot);items.set(slot, stack); }
    MachineWork work() { return work; }
    public long currentEnergyRate() { return work.energyRate; }
    MachineVisuals visuals() { return visuals; }
    MachineOperations operations() { return operations; }
    NetworkJobWorker networkWorker() { return worker; }
    MachineRunState runState() { return control.runState(); }
    String selectedId() { return control.selectedId(); }
    AnalysisState analysis() { return analysis; }
    void completeAnalysis(MaterialProfile profile) { analysis = AnalysisState.completed(profile); }

    /** The role a side really has: a side that can never be a connection reads as Disabled everywhere. */
    public MachinePortMode portMode(MachinePortMode.Side side) { return sides.mode(kind(), side); }
    public MachinePortMode portMode(Direction side) {
        return portMode(MachinePortMode.Side.local(getBlockState().getValue(MachineBlock.FACING), side));
    }
    /** Every capability, bridge and neighbour link asks here. A side-less query never opens a route around a side. */
    public boolean allowsPort(@Nullable Direction side, MachinePortMode role) {
        return side != null && portMode(side).allows(role);
    }
    public boolean acceptsMatter(Direction side) {
        return MatterCableConnectionPolicy.acceptsMachine(kind(), getBlockState().getValue(MachineBlock.FACING), side)
                && allowsPort(side, MachinePortMode.MATTER);
    }
    boolean cyclePort(MachinePortMode.Side side) { return cyclePort(side, true); }
    boolean cyclePort(MachinePortMode.Side side, boolean forward) {
        if (!(level instanceof ServerLevel server) || !isLiveServerMachine()) return false;
        MachinePortMode previous = sides.cycle(kind(), side, forward);
        if (previous == null) return false;
        MachinePortMode next = sides.mode(kind(), side);
        setChanged();
        if (previous.allows(MachinePortMode.MATTER) != next.allows(MachinePortMode.MATTER)
                || previous.allows(MachinePortMode.POWER) != next.allows(MachinePortMode.POWER)
                || previous.allows(MachinePortMode.POWER_OUTPUT) != next.allows(MachinePortMode.POWER_OUTPUT)) {
            touchObservations();
            MatterCableBlock.refreshLoadedAround(server, worldPosition);
        }
        level.invalidateCapabilities(worldPosition);
        sendClientUpdate();
        return true;
    }
    boolean cyclePort(Player player, Direction side) {
        return stillValid(player) && cyclePort(MachinePortMode.Side.local(getBlockState().getValue(MachineBlock.FACING), side));
    }
    /** The roles the port plates of this block show. */
    @Override public net.neoforged.neoforge.model.data.ModelData getModelData() {
        return net.neoforged.neoforge.model.data.ModelData.of(PortSides.PLATES, sides.visuals(kind()));
    }
    @Nullable EnergyHandler sidedEnergy(@Nullable Direction side) {
        if(kind().generator())return allowsPowerOutput(side)
                ?new PortEnergyHandler(extensions.output(),()->isLiveServerMachine()&&allowsPowerOutput(side)):null;
        if (!kind().processes() || IndustrialStructure.isIndustrial(kind()) || PreparationTier.manual(kind())
                || !allowsPort(side, MachinePortMode.POWER)) return null;
        return new PortEnergyHandler(energy, () -> isLiveServerMachine() && allowsPort(side, MachinePortMode.POWER));
    }
    @Nullable ResourceHandler<FluidResource> sidedFluids(@Nullable Direction side) {
        if (IndustrialStructure.isIndustrial(kind())) return null;
        boolean supportsTank = kind().decomposition()
                || kind().synthesis()
                || (kind() == MachineKind.NETWORK_INTERFACE && fluids.getAmountAsLong(0) > 0);
        if (!automation || !supportsTank
                || !allowsPort(side, MachinePortMode.FLUID_INPUT) && !allowsPort(side, MachinePortMode.FLUID_OUTPUT)) return null;
        return new AutomationResourceHandler<>(automatedFluids,
                () -> kind().decomposition() && allowsPort(side, MachinePortMode.FLUID_INPUT),
                () -> allowsPort(side, MachinePortMode.FLUID_OUTPUT), 1, FluidResource.EMPTY);
    }
    ResourceHandler<FluidResource> industrialFluids() {
        return new AutomationResourceHandler<>(automatedFluids,
                () -> kind().decomposition() && automationAllowed(),
                () -> (kind().decomposition() || kind().synthesis()) && automationAllowed(), 1, FluidResource.EMPTY);
    }
    ResourceHandler<FluidResource> playerFluidHandler() {
        if (kind().decomposition()) return fluids;
        return new AutomationResourceHandler<>(fluids, () -> false, () -> true, 1, FluidResource.EMPTY);
    }
    public ResourceHandler<ItemResource> automatedItems(Direction side) {
        Objects.requireNonNull(side);
        return new IndustrialItemHandler(VanillaContainerWrapper.of(this),
                () -> allowsItemInput(side), () -> allowsItemOutput(side), PreparationTier.isPreparation(kind()),slot->slot==1||recoverySlot(slot));
    }
    @Nullable ResourceHandler<ItemResource> sidedItems(@Nullable Direction side) {
        if (side == null || !automation || IndustrialStructure.isIndustrial(kind()) || kind().isStorage()
                || !allowsPort(side, MachinePortMode.ITEM_INPUT) && !allowsPort(side, MachinePortMode.ITEM_OUTPUT)) return null;
        return automatedItems(side);
    }
    ResourceHandler<ItemResource> industrialItems() {
        return new IndustrialItemHandler(VanillaContainerWrapper.of(this),this::automationAllowed,this::automationAllowed,PreparationTier.industrial(kind()),slot->slot==1||recoverySlot(slot));
    }
    public boolean automation() { return automation; }
    public static boolean supportsAutoEject(MachineKind kind) {
        return !IndustrialStructure.isIndustrial(kind)
                && (PreparationTier.isPreparation(kind) && !PreparationTier.manual(kind)
                || kind.synthesis() || kind == MachineKind.DECOMPOSER);
    }
    public boolean autoEject() { return autoEject && supportsAutoEject(kind()); }
    public boolean toggleAutoEject() {
        if (!isLiveServerMachine() || !supportsAutoEject(kind())) return false;
        autoEject = !autoEject;
        setChanged();
        return true;
    }
    private void pushConfiguredOutputs(ServerLevel server) {
        if (pushingOutputs) return;
        boolean hasItem = !items.get(1).isEmpty() && MachinePortMode.ITEM_OUTPUT.supported(kind());
        boolean hasFluid = fluids.getAmountAsLong(0) > 0 && MachinePortMode.FLUID_OUTPUT.supported(kind());
        if (!hasItem && !hasFluid) return;
        pushingOutputs = true;
        try {
            Direction front = getBlockState().getValue(MachineBlock.FACING);
            for (var side : MachinePortMode.Side.values()) {
                if (!PortSides.available(kind(), side)) continue;
                MachinePortMode mode = portMode(side);
                if (mode != MachinePortMode.ITEM_OUTPUT && mode != MachinePortMode.FLUID_OUTPUT) continue;
                Direction worldDir = side.world(front);
                BlockPos neighborPos = worldPosition.relative(worldDir);
                if (server.getChunkSource().getChunkNow(neighborPos.getX() >> 4, neighborPos.getZ() >> 4) == null) continue;
                if (mode == MachinePortMode.ITEM_OUTPUT && !items.get(1).isEmpty()) {
                    var target = server.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, neighborPos, worldDir.getOpposite());
                    if (target != null) {
                        ItemStack out = items.get(1);
                        int moved = 0;
                        try (var tx = Transaction.openRoot()) {
                            moved = target.insert(ItemResource.of(out), out.getCount(), tx);
                            if (moved > 0) tx.commit();
                        }
                        if (moved > 0) {
                            out.shrink(moved);
                            if (out.isEmpty()) items.set(1, ItemStack.EMPTY);
                            setChanged();
                            publishItemVisuals();
                        }
                    }
                } else if (mode == MachinePortMode.FLUID_OUTPUT && fluids.getAmountAsLong(0) > 0) {
                    var target = server.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, neighborPos, worldDir.getOpposite());
                    if (target != null) {
                        FluidResource resource = fluids.getResource(0);
                        int available = (int) Math.min(Integer.MAX_VALUE, fluids.getAmountAsLong(0));
                        int moved = 0;
                        try (var tx = Transaction.openRoot()) {
                            moved = target.insert(resource, available, tx);
                            if (moved > 0) tx.commit();
                        }
                        if (moved > 0) {
                            int remaining = available - moved;
                            fluids.set(0, remaining > 0 ? resource : FluidResource.EMPTY, remaining);
                            setChanged();
                        }
                    }
                }
            }
        } finally {
            pushingOutputs = false;
        }
    }
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
    public boolean structurePreviewEnabled() { return structure.previewEnabled(); }
    public boolean toggleStructurePreview() {
        if (!isLiveServerMachine() || !IndustrialStructure.isIndustrial(kind())) return false;
        structure.setPreviewEnabled(!structure.previewEnabled());
        sendClientUpdate();
        return true;
    }
    public void setStructurePreviewEnabled(boolean enabled) {
        if (!IndustrialStructure.isIndustrial(kind())) return;
        structure.setPreviewEnabled(enabled);
        sendClientUpdate();
    }
    public boolean hasCustomBlueprint() { return structure.hasCustomBlueprint(); }
    public @Nullable IndustrialBlueprint customIndustrialBlueprint() { return structure.customBlueprint(); }
    public IndustrialBlueprint industrialBlueprint() { return structure.effectiveBlueprint(); }
    public boolean adjustIndustrialBlueprint(IndustrialBlueprint.Parameter parameter, int delta) {
        if (!isLiveServerMachine() || !IndustrialStructure.isIndustrial(kind())) return false;
        boolean changed = structure.adjustBlueprint(parameter, delta);
        if (changed) sendClientUpdate();
        return changed;
    }
    public boolean resetIndustrialBlueprint() {
        if (!isLiveServerMachine() || !IndustrialStructure.isIndustrial(kind())) return false;
        boolean changed = structure.resetBlueprint();
        if (changed) sendClientUpdate();
        return changed;
    }
    public void setIndustrialBlueprint(@Nullable IndustrialBlueprint blueprint) {
        structure.setBlueprint(blueprint);
        sendClientUpdate();
    }
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
    public boolean isModuleVisuallyWorking(BlockPos modulePosition) {
        return isLiveServerMachine() && structure.moduleWorking(modulePosition, activity.showing(level.getGameTime(), 8));
    }
    @Override public void setRemoved() {
        interruptTerminalStep();
        resetProgress();
        worker.release();
        if (automationHost != null) automationHost.close();
        work.processedThisTick = false;
        structure.removed();
        if (level instanceof ServerLevel server) ChunkAnchorManager.release(server, worldPosition);
        super.setRemoved();
    }
    /** MachineBlock carries the complete inventory in one block item, as a portable container. */
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        // The vanilla default scatters every Container slot. Doing that as well as our packed
        // block drop would duplicate cells on creative breaks and empty survival drop contents.
    }
    boolean hasUnsupportedData() { return unsupportedData != null; }
    public RedstoneMode redstoneMode() { return RedstoneMode.controllable(kind()) ? redstoneMode : RedstoneMode.IGNORED; }
    public MachineSoundLevel soundLevel() {
        return ModSounds.workingSound(kind()) == null ? MachineSoundLevel.MUTED : clientSync.soundLevel(level, soundLevel);
    }
    public float soundGain() { return soundLevel().gain(); }
    public boolean soundMuted() { return ModSounds.workingSound(kind()) != null && soundLevel().muted(); }
    public boolean cycleSoundLevel(boolean forward) {
        if (!isLiveServerMachine() || ModSounds.workingSound(kind()) == null) return false;
        setSoundLevel(forward ? soundLevel.next() : soundLevel.previous());
        return true;
    }
    public void setSoundLevel(MachineSoundLevel next) {
        Objects.requireNonNull(next);
        if (ModSounds.workingSound(kind()) == null || this.soundLevel == next) return;
        this.soundLevel = next;
        sendClientUpdate();
        setChanged();
    }
    public boolean toggleSoundMuted() {
        if (!isLiveServerMachine() || ModSounds.workingSound(kind()) == null) return false;
        setSoundMuted(!soundMuted());
        return true;
    }
    public void setSoundMuted(boolean muted) {
        setSoundLevel(muted ? MachineSoundLevel.MUTED : MachineSoundLevel.HIGH);
    }
    public boolean cycleRedstoneMode() {
        if (!isLiveServerMachine() || !RedstoneMode.controllable(kind())) return false;
        setRedstoneMode(redstoneMode.next());
        return true;
    }
    public void setRedstoneMode(RedstoneMode mode) {
        if (!RedstoneMode.controllable(kind())) return;
        redstoneMode = Objects.requireNonNull(mode);
        redstonePulsePending = false;
        if (level != null) {
            redstonePoweredLastTick = isRedstonePowered();
            redstoneSampled = true;
        }
        setChanged();
    }
    /**
     * Evaluates Redstone control signal.
     * - On formed multiblocks: controller always accepts redstone; dedicated REDSTONE ports take precedence over AUTOMATIC ports.
     * - On compact machines: if any side is set to REDSTONE, only those dedicated REDSTONE sides trigger Redstone control;
     *   otherwise any neighboring signal triggers Redstone control.
     */
    boolean isRedstonePowered() {
        if (level == null) return false;
        if (!IndustrialStructure.isIndustrial(kind())) {
            if (MachinePortMode.configurable(kind())) {
                Direction front = getBlockState().getValue(MachineBlock.FACING);
                boolean hasDedicated = false;
                boolean dedicatedSignal = false;
                for (var side : MachinePortMode.Side.values()) {
                    if (!PortSides.available(kind(), side)) continue;
                    if (sides.mode(kind(), side) == MachinePortMode.REDSTONE) {
                        hasDedicated = true;
                        Direction worldSide = side.world(front);
                        if (!dedicatedSignal && level.getSignal(worldPosition.relative(worldSide), worldSide) > 0) {
                            dedicatedSignal = true;
                        }
                    }
                }
                if (hasDedicated) return dedicatedSignal;
            }
            return level.hasNeighborSignal(worldPosition);
        }
        if (level.hasNeighborSignal(worldPosition)) return true;
        var formation = IndustrialStructure.inspect(this);
        if (!formation.formed()) return false;
        boolean hasDedicated = false;
        boolean dedicatedSignal = false;
        boolean autoSignal = false;
        for (var port : formation.ports()) {
            if (!(level.getBlockEntity(port.position()) instanceof MachinePortBlockEntity pbe)) continue;
            var mode = pbe.mode();
            if (mode == MachinePortMode.REDSTONE) {
                hasDedicated = true;
                if (!dedicatedSignal && level.hasNeighborSignal(port.position())) dedicatedSignal = true;
            } else if (mode == MachinePortMode.AUTOMATIC) {
                if (!autoSignal && level.hasNeighborSignal(port.position())) autoSignal = true;
            }
        }
        return hasDedicated ? dedicatedSignal : autoSignal;
    }
    boolean redstoneAllowsWork() {
        if (!RedstoneMode.controllable(kind()) || redstoneMode == RedstoneMode.IGNORED) return true;
        boolean powered = isRedstonePowered();
        return redstoneMode.permits(powered, redstonePulsePending);
    }
    boolean redstoneAllowsGenerator(boolean burningBatchInProgress) {
        if (redstoneMode == RedstoneMode.PULSE && burningBatchInProgress) return true;
        return redstoneAllowsWork();
    }
    void consumeRedstonePulse() {
        if (redstoneMode == RedstoneMode.PULSE) redstonePulsePending = false;
    }
    private void sampleRedstone() {
        if (!RedstoneMode.controllable(kind()) || level == null) return;
        boolean powered = isRedstonePowered();
        if (redstoneSampled && powered && !redstonePoweredLastTick && redstoneMode == RedstoneMode.PULSE) {
            redstonePulsePending = true;
        }
        redstonePoweredLastTick = powered;
        redstoneSampled = true;
    }
    long effectiveNetworkReserve(SubstanceKey key) {
        long local = settings.networkReserve();
        long global = (level instanceof ServerLevel server && owner != null)
                ? NetworkReservesSavedData.get(server.getServer()).amount(owner, key) : 0L;
        return Math.max(local, global);
    }
    long effectivePrimalNetworkReserve() {
        return effectiveNetworkReserve(new SubstanceKey(dev.latrox.rematter.chemistry.ReservedSubstances.PRIMAL_MATTER, dev.latrox.rematter.chemistry.Phase.SOLID));
    }
    boolean violatesNetworkReserve(@Nullable MatterStore store, Map<SubstanceKey, Long> inputs) {
        if (store == null || inputs.isEmpty()) return false;
        for (var entry : inputs.entrySet()) {
            long reserve = effectiveNetworkReserve(entry.getKey());
            if (reserve <= 0L) continue;
            long available = store.amount(entry.getKey());
            if (available >= entry.getValue() && available - entry.getValue() < reserve) return true;
        }
        return false;
    }
    private Map<SubstanceKey, Long> reserveAdjustedContents(@Nullable MatterStore store, Map<SubstanceKey, Long> inputs) {
        if (store == null) return Map.of();
        var raw = store.contents();
        if (inputs.isEmpty()) return raw;
        Map<SubstanceKey, Long> adjusted = null;
        for (var key : inputs.keySet()) {
            long reserve = effectiveNetworkReserve(key);
            if (reserve <= 0L) continue;
            long have = raw.getOrDefault(key, 0L);
            long usable = Math.max(0L, have - reserve);
            if (usable != have) {
                if (adjusted == null) adjusted = new java.util.HashMap<>(raw);
                if (usable == 0L) adjusted.remove(key);
                else adjusted.put(key, usable);
            }
        }
        return adjusted == null ? raw : adjusted;
    }
    void afterRotation(ServerLevel server) {
        structure.refresh();
        touchObservations();
        server.invalidateCapabilities(worldPosition);
        MatterCableBlock.refreshLoadedAround(server, worldPosition);
        sendClientUpdate();
        setChanged();
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
    void sendClientUpdateNow() { sendClientUpdate(); }
    // Package-local discovery access never exposes a mutable aggregate to external callers.
    boolean networkLive() { return isLiveServerMachine(); }
    UUID networkOwner() { return owner; }
    String networkStorageStatus() { return storageStatus(); }
    int networkCellCount() { return kind().isStorage() ? bays.installed() : 0; }
    List<MatterInventory> localMatterMembers() { return kind().isStorage() && storageStatus().equals("ready") ? matterStore().members() : List.of(); }
    dev.latrox.rematter.chemistry.VoidPolicy localVoidPolicy() {
        return kind().isStorage() && storageStatus().equals("ready") ? bays.voidPolicy() : dev.latrox.rematter.chemistry.VoidPolicy.NONE;
    }
    /**
     * Placement makes the placing player the owner, whatever owner data the placed item carried. A fake player places
     * for the owner whose UUID it carries; a placement without any player leaves the machine unowned.
     */
    void placedBy(@Nullable LivingEntity placer) {
        placedBy(placer, ItemStack.EMPTY);
    }
    void placedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (securityMode == SecurityMode.PUBLIC && unsupportedData == null) {
            securityMode = MachineAccess.placementDefault(level, placer);
        }
        String fromItem = CustomNames.fromStack(stack);
        if (!fromItem.isEmpty() && unsupportedData == null) {
            customName = fromItem;
            setChanged();
        }
        assignOwner(placer instanceof Player player&&!(player instanceof FakePlayer)?player.getUUID():null);
        syncChunkAnchor();
        if(kind()==MachineKind.HAND_PREPARATOR&&placer instanceof Player player&&!(player instanceof FakePlayer)&&!player.isSpectator())
            guidance(dev.latrox.rematter.guide.GuidanceEvent.START_WORKSHOP);
    }
    void guidance(dev.latrox.rematter.guide.GuidanceEvent event) {
        if(level instanceof ServerLevel server)dev.latrox.rematter.guide.GuidanceProgress.award(server,owner,event);
    }
    private void assignOwner(@Nullable UUID next) {
        if (Objects.equals(owner, next)) return;
        interruptTerminalStep();
        owner = next;
        setChanged();
        touchObservations();
        syncChunkAnchor();
        if (level != null) level.invalidateCapabilities(worldPosition);
        if (level instanceof ServerLevel server) MatterCableBlock.refreshLoadedAround(server, worldPosition);
    }
    public SecurityMode securityMode() { return securityMode; }
    public boolean canManageSecurity(Player player) { return MachineAccess.canManage(owner, player); }
    public boolean setSecurityMode(Player player, SecurityMode next) {
        if (!isLiveServerMachine() || player.isSpectator() || !canManageSecurity(player) || next == null) return false;
        if (securityMode == next) return true;
        securityMode = next;
        setChanged();
        touchObservations();
        if (level != null) level.invalidateCapabilities(worldPosition);
        if (level instanceof ServerLevel server) MatterCableBlock.refreshLoadedAround(server, worldPosition);
        return true;
    }
    public boolean cycleSecurityMode(Player player, boolean forward) {
        return setSecurityMode(player, forward ? securityMode.next() : securityMode.previous());
    }
    public boolean canAccess(Player player) {
        return MachineAccess.allows(level, owner, securityMode, player);
    }
    public boolean canRemove(Player player) {
        return canAccess(player);
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
        if (!stack.isEmpty() && MachineCellAccess.isMountableBayCell(stack) && stack.getCount()!=1) return false;
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
    public boolean consolidateCells(Player player) {
        if (!isLiveServerMachine()
                || !(level instanceof ServerLevel server)
                || kind() != MachineKind.DRIVE
                || player.isSpectator()
                || !canAccess(player)
                || !stillValid(player)
                || Transaction.getCurrentOpenedTransaction() != null
                || !bays.status().equals("ready")) {
            return false;
        }
        int bayCount = MachineCellAccess.DRIVE_BAYS;
        ItemStack[] captured = new ItemStack[bayCount];
        for (int bay = 0; bay < bayCount; bay++) {
            captured[bay] = stack(MachineCellAccess.slotForBay(MachineKind.DRIVE, bay));
        }
        bays.flush();
        bays.unmountAll();
        long movedOrdinary = 0;
        long movedPrimal = 0;
        try {
            java.util.function.BooleanSupplier permitted = () -> {
                if (!isLiveServerMachine() || !canAccess(player) || !stillValid(player)) return false;
                for (int b = 0; b < bayCount; b++) {
                    ItemStack cur = stack(MachineCellAccess.slotForBay(MachineKind.DRIVE, b));
                    if (cur != captured[b] || (!cur.isEmpty() && cur.getCount() != 1)) return false;
                }
                return true;
            };
            var restrictedTargets = new ArrayList<Integer>();
            var unrestrictedBays = new ArrayList<Integer>();
            var primalBays = new ArrayList<Integer>();
            for (int bay = 0; bay < bayCount; bay++) {
                ItemStack s = captured[bay];
                if (s.getItem() instanceof MatterCellItem) {
                    var spec = CellPartitionData.read(s);
                    if (spec.isEmpty()) return false;
                    if (spec.get().restricted()) restrictedTargets.add(bay);
                    else unrestrictedBays.add(bay);
                } else if (s.getItem() instanceof dev.latrox.rematter.primal.PrimalCellItem) {
                    primalBays.add(bay);
                }
            }
            restrictedTargets.sort(Comparator.<Integer>comparingInt(
                            b -> CellPartitionData.read(captured[b]).map(sp -> sp.allowed().size()).orElse(Integer.MAX_VALUE))
                    .thenComparingInt(b -> b));
            var sourceCandidates = new ArrayList<Integer>(unrestrictedBays);
            for (int b = restrictedTargets.size() - 1; b >= 0; b--) {
                sourceCandidates.add(restrictedTargets.get(b));
            }
            var primalJournals = new dev.latrox.rematter.primal.PrimalCells.Journal[bayCount];
            for (int b : primalBays) {
                int slot = MachineCellAccess.slotForBay(MachineKind.DRIVE, b);
                primalJournals[b] = new dev.latrox.rematter.primal.PrimalCells.Journal(() -> stack(slot));
            }
            try (var tx = Transaction.openRoot()) {
                // Pass 1: pull matching substances into partitioned cells first (narrowest partition first).
                for (int target : restrictedTargets) {
                    int targetSlot = MachineCellAccess.slotForBay(MachineKind.DRIVE, target);
                    for (int source : sourceCandidates) {
                        if (source == target) continue;
                        int sourceSlot = MachineCellAccess.slotForBay(MachineKind.DRIVE, source);
                        movedOrdinary = Math.addExact(movedOrdinary, MatterCellTransfers.move(
                                server, captured[source], captured[target],
                                () -> stack(sourceSlot), () -> stack(targetSlot), permitted, tx));
                    }
                }
                // Pass 2: coalesce unrestricted ordinary cells forward toward lower bay indices.
                for (int i = 0; i < unrestrictedBays.size(); i++) {
                    int target = unrestrictedBays.get(i);
                    int targetSlot = MachineCellAccess.slotForBay(MachineKind.DRIVE, target);
                    for (int j = unrestrictedBays.size() - 1; j > i; j--) {
                        int source = unrestrictedBays.get(j);
                        int sourceSlot = MachineCellAccess.slotForBay(MachineKind.DRIVE, source);
                        movedOrdinary = Math.addExact(movedOrdinary, MatterCellTransfers.move(
                                server, captured[source], captured[target],
                                () -> stack(sourceSlot), () -> stack(targetSlot), permitted, tx));
                    }
                }
                // Pass 3: coalesce Primal cells forward toward lower bay indices.
                for (int i = 0; i < primalBays.size(); i++) {
                    int target = primalBays.get(i);
                    for (int j = primalBays.size() - 1; j > i; j--) {
                        int source = primalBays.get(j);
                        movedPrimal = Math.addExact(movedPrimal, dev.latrox.rematter.primal.PrimalCells.transfer(
                                primalJournals[source], primalJournals[target], Long.MAX_VALUE, tx));
                    }
                }
                if (!permitted.getAsBoolean() || (movedOrdinary == 0 && movedPrimal == 0)) {
                    return false;
                }
                tx.commit();
            }
        } finally {
            bays.mountAll();
            touchObservations();
            if (level != null) {
                level.invalidateCapabilities(worldPosition);
                sendClientUpdate();
            }
            setChanged();
        }
        ModSounds.play(level, worldPosition, ModSounds.CELL_TRANSFER);
        return true;
    }
    @Override public void setChanged() {
        if (bays != null) bays.flush();
        super.setChanged();
        publishItemVisuals();
        if (isLiveServerMachine()) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
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
    /** Accepted manual work, independent of batch resets; client presentation only, never persisted. */
    public int visualCrankSequence() { return clientSync.visualCrankSequence(); }

    private void publishProcessVisuals() {
        if (isLiveServerMachine() && visuals.publishProcess(work.processedThisTick, work.progress, work.duration, level.getGameTime())) sendClientUpdate();
    }
    void publishProcessVisualsNow() { publishProcessVisuals(); }
    private boolean itemVisualsStable() {
        // Energy restoration also marks the machine dirty while a transaction is closing.
        return !clientSync.loadingItemVisuals() && !committingOwnedState && Transaction.getLifecycle() == Transaction.Lifecycle.NONE;
    }
    private void publishItemVisuals() {
        if (!MachineVisuals.showsItems(kind()) || !itemVisualsStable() || !isLiveServerMachine()) return;
        if (visuals.refreshItems(kind(), this::stack)) sendClientUpdate();
    }
    void publishItemVisualsNow() { publishItemVisuals(); }
    public boolean hasVisibleCell() { return visibleCellMask() != 0; }
    public boolean hasVisibleCell(int bay) { return bay >= 0 && bay < cellBayCount() && (visibleCellMask() & 1 << bay) != 0; }
    public int visibleCellMask() { return clientSync.visibleMask(level, bays); }
    /** 0 absent, 1/2/3 Matter capacity tiers, 4 unreadable or foreign, 5/6/7 Primal capacity tiers. */
    public int visibleCellTier(int bay) { return clientSync.visibleTier(level, bays, bay); }
    public CellStatus visibleCellStatus(int bay) { return clientSync.visibleStatus(level, bays, bay); }
    public int preparationStage() { return PreparationTier.isPreparation(kind()) ? work.preparationStage : -1; }
    public boolean preparationMixing() { return PreparationTier.isPreparation(kind()) && work.preparationMixing; }
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        clientSync.writeUpdateTag(this, tag, sides, visuals, bays, work, structure);
        return tag;
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void handleUpdateTag(ValueInput input) {
        clientSync.readUpdateTag(this, input, sides, visuals, work, structure);
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, ValueInput input) { handleUpdateTag(input); }
    public boolean canUse(Player player) {
        boolean allowed = canAccess(player);
        if (!allowed && !player.level().isClientSide()) player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
        return allowed;
    }
    public boolean hasStoredResources() {
        return extensions.hasResources()||unsupportedData != null || automationHost != null && automationHost.hasContents()
                || !isEmpty() || fluids.getAmountAsLong(0) > 0 || energy.getAmountAsLong() > 0 || work.energyPaid > 0;
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
    /**
     * Hands the held resources of a stopped network delivery to the player: matter as recovery parcels, items into the
     * inventory or at the player's feet, fluids into this interface's tank while it has room. Whatever cannot be handed
     * out stays held by the order, which remains stopped until it holds nothing.
     */
    boolean releaseHeldOrder(ServerPlayer player, UUID orderId) {
        var host = automationHost();
        if (host == null || !isLiveServerMachine() || player.isSpectator() || !canRemove(player)) return false;
        var held = host.heldResources(orderId).orElse(null);
        if (held == null) return false;
        var taken = new TreeMap<AutomationPort.Resource, Long>();
        FluidResource tank = fluids.getResource(0);
        long room = TANK_CAPACITY - fluids.getAmountAsLong(0);
        for (var entry : held.entrySet()) {
            var resource = entry.getKey();
            Identifier id = Identifier.parse(resource.id());
            switch (resource.kind()) {
                case MATTER -> taken.put(resource, entry.getValue());
                case ITEM -> {
                    if (BuiltInRegistries.ITEM.containsKey(id) && !new ItemStack(BuiltInRegistries.ITEM.getValue(id)).isEmpty())
                        taken.put(resource, entry.getValue());
                }
                case FLUID -> {
                    if (room == 0 || !BuiltInRegistries.FLUID.containsKey(id)) continue;
                    var fluid = FluidResource.of(BuiltInRegistries.FLUID.getValue(id));
                    if (fluid.isEmpty() || !tank.isEmpty() && !tank.equals(fluid)) continue;
                    long amount = Math.min(room, entry.getValue());
                    taken.put(resource, amount);
                    tank = fluid;
                    room -= amount;
                }
            }
        }
        if (taken.isEmpty() || !host.releaseHeld(orderId, taken)) {
            player.sendSystemMessage(Component.translatable("message.rematter.release_blocked"));
            return false;
        }
        for (var entry : taken.entrySet()) {
            var resource = entry.getKey();
            long amount = entry.getValue();
            switch (resource.kind()) {
                case MATTER -> player.getInventory().placeItemBackInInventory(MatterRecoveryItem.create(resource.matterKey(), amount));
                case ITEM -> {
                    var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(resource.id()));
                    for (long left = amount; left > 0; ) {
                        int count = (int) Math.min(left, item.getDefaultMaxStackSize());
                        player.getInventory().placeItemBackInInventory(new ItemStack(item, count));
                        left -= count;
                    }
                }
                case FLUID -> fluids.set(0, tank, (int) (fluids.getAmountAsLong(0) + amount));
            }
        }
        player.sendSystemMessage(Component.translatable(taken.equals(held) ? "message.rematter.release_complete" : "message.rematter.release_partial"));
        setChanged();
        return true;
    }
    public boolean networkJobs() { return networkJobs; }
    public boolean supportsNetworkJobs() { return kind().supportsNetworkJobs(); }
    public boolean toggleNetworkJobs() {
        if (!isLiveServerMachine() || !supportsNetworkJobs()) return false;
        interruptTerminalStep();
        worker.release(); networkJobs = !networkJobs;
        control.runState(control.runState().pause()); resetProgress(); work.processedThisTick = false;
        level.invalidateCapabilities(worldPosition); setChanged(); return true;
    }
    UUID networkWorkerId() { return worker.id(); }
    boolean acceptsNetworkWork() {
        if (!networkJobs || !supportsNetworkJobs() || !isLiveServerMachine() || !industrialStructureReady()
                || fluids.getAmountAsLong(0) != 0) return false;
        for (int slot = 0; slot < items.size(); slot++)
            if (slot != 2 && slot != MachineCellAccess.EFFICIENCY_SLOT && slot != MachineCellAccess.PARALLEL_SLOT && !items.get(slot).isEmpty()) return false;
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
    public boolean requestBatch() {
        if (networkJobs) return false;
        interruptTerminalStep();
        return control.requestBatch();
    }

    public boolean toggleRepeat() {
        if (networkJobs) return false;
        interruptTerminalStep();
        return control.toggleRepeat();
    }
    /** Cancels an active or queued manual/terminal job and refunds uncommitted FE up to buffer capacity. */
    public boolean cancelJob() {
        if (!isLiveServerMachine() || networkJobs) return false;
        if (terminalStep != null) {
            var step = terminalStep;
            if (step.cancelParentJob()) return true;
            interruptTerminalStep();
            return true;
        }
        if (supportsBatchControl()) {
            var state = control.runState();
            if (!state.repeat() && state.queuedBatches() == 0 && work.progress == 0 && work.energyPaid == 0) return false;
            control.cancel();
            work.status = "awaiting_request";
            work.processedThisTick = false;
            setChanged();
            return true;
        }
        if (!kind().processes() || kind() == MachineKind.PRIMAL_CONVERTER || PreparationTier.manual(kind())) return false;
        if (kind() == MachineKind.ANALYZER && extensions.isResearchRunning()) return false;
        if (work.progress == 0 && work.energyPaid == 0) return false;
        resetProgress();
        control.runState(control.runState().pause());
        work.status = "paused";
        work.processedThisTick = false;
        setChanged();
        return true;
    }
    public void select(int offset) { if (!networkJobs) control.select(offset); }
    public boolean selectRecipe(String exactId) { return !networkJobs && control.select(exactId); }
    public List<MachineChoices.Choice> choices() { return control.choices(); }
    MachineChoices.@Nullable Choice selectedChoice() { return control.selectedChoice(); }
    int selectedIndex() { return control.selectedIndex(); }

    public void serverTick() {
        if (!isLiveServerMachine()) return;
        // A foreign transfer may roll back its member snapshots. Do not complete unrelated work inside it.
        if (Transaction.getCurrentOpenedTransaction() != null) return;
        sampleRedstone();
        boolean structureChanged = structure.refresh();
        if (structureChanged) sendClientUpdate();
        if (hasAnchorUpgrade() && (structureChanged || (level.getGameTime() & 15L) == 0L)) syncChunkAnchor();
        if(IndustrialStructure.isIndustrial(kind())&&IndustrialStructure.inspect(this).formed()&&level instanceof ServerLevel server) {
            if (kind() == MachineKind.INDUSTRIAL_REACTOR) {
                dev.latrox.rematter.research.ResearchService.recordIndustrial(server, owner, dev.latrox.rematter.research.ResearchLedger.Milestone.INDUSTRIAL_REACTOR);
            } else if (kind() == MachineKind.INDUSTRIAL_PREPARATOR) {
                dev.latrox.rematter.research.ResearchService.recordIndustrial(server, owner, dev.latrox.rematter.research.ResearchLedger.Milestone.INDUSTRIAL_PREPARATOR);
            }
            guidance(dev.latrox.rematter.guide.GuidanceEvent.INDUSTRIAL_SCALE);
        }
        work.processedThisTick = false;
        work.energyRate = 0;
        networkOnline = false;
        visuals.beginTick();
        if (PreparationTier.isPreparation(kind())) work.preparationStage = -1;
        try {
            tickMachine();
            if (autoEject && automationAllowed() && supportsAutoEject(kind())
                    && Transaction.getCurrentOpenedTransaction() == null && level instanceof ServerLevel server) {
                pushConfiguredOutputs(server);
            }
        } finally {
            clientSync.finishTick(this, bays, activity, structure, networkOnline);
        }
    }
    public boolean crank(Player player) { return crank(player, null); }
    boolean crank(Player player, @Nullable TerminalAnchor terminal) { return clientSync.crank(this, player, terminal); }

    boolean applyCopiedSetup(Player player, ConfigurationCardData.MachineSetup setup) {
        if (!(level instanceof ServerLevel server) || !isLiveServerMachine() || player.isSpectator() || !canAccess(player)) return false;
        ensureRegistryLoaded();
        boolean portsChanged = sides.applyCopied(kind(), setup.packedPorts());
        setRedstoneMode(setup.redstoneMode());
        setSoundLevel(setup.soundLevel());
        if (IndustrialStructure.isIndustrial(kind()) && setup.sourceKind() == kind()) {
            structure.setBlueprint(setup.blueprint());
            structure.setPreviewEnabled(setup.structurePreview());
        }
        if (automation != setup.automation()) toggleAutomation();
        if (supportsAutoEject(kind()) && autoEject != setup.autoEject()) toggleAutoEject();
        if (supportsNetworkJobs() && networkJobs != setup.networkJobs()) toggleNetworkJobs();
        if (!networkJobs) {
            if (!setup.selection().isEmpty()) selectRecipe(setup.selection());
            if (!configure(player, setup.settings()) && (!setup.settings().ventedGases().isEmpty() || setup.settings().networkReserve() > 0L) && (setup.settings().ventedGases().isEmpty() || canRemove(player))) {
                configure(player, new MachineSettings(0, "", "", setup.settings().ventedGases(), setup.settings().networkReserve()));
            }
            if (supportsBatchControl() && control.runState().repeat() != setup.repeat()) toggleRepeat();
            if (running() != setup.running()) toggleRunning();
        }
        if (portsChanged) {
            touchObservations();
            MatterCableBlock.refreshLoadedAround(server, worldPosition);
        }
        server.invalidateCapabilities(worldPosition);
        sendClientUpdate();
        setChanged();
        return true;
    }

    boolean applyBayCellPartition(Player player, int slot, CellPartitionData.Spec spec) {
        if (!(level instanceof ServerLevel) || !isLiveServerMachine() || player.isSpectator() || !canAccess(player)
                || Transaction.getCurrentOpenedTransaction() != null || !MachineCellAccess.isCellSlot(kind(), slot)
                || !prepareCellRemoval(slot)) {
            return false;
        }
        ItemStack stack = items.get(slot);
        if (!ConfigurationCardData.applyCellPartition(stack, spec)) {
            mountCell(slot);
            return false;
        }
        mountCell(slot);
        return true;
    }
    void ensureRegistryLoaded() { if (lastRegistry != ReMatter.chemistry()) tickMachine(); }
    private void tickMachine() {
        if(extensions.tick())return;
        if(kind()==MachineKind.ANALYZER){
            var profile=MachineOperations.profile(ReMatter.chemistry(),items.get(0));
            if(profile!=null&&items.get(0).getComponentsPatch().isEmpty()&&knownMaterial(profile.itemId())){
                completeAnalysis(profile);work.status="analyzed";control.runState(control.runState().pause());return;
            }
        }
        if (kind() == MachineKind.NETWORK_INTERFACE) {
            var host = automationHost(); host.tick(); work.status = host.status();
            networkOnline = matterNetwork(false).ready();
            return;
        }
        if (networkJobs) { worker.tick(); return; }
        ChemistryRegistry registry = ReMatter.chemistry();
        if (!analysis.isEmpty() && !operations.hasValidAnalysis(registry, analysis)) { analysis = AnalysisState.EMPTY; setChanged(); }
        if (registry != lastRegistry) {
            boolean initialLoad = lastRegistry == null;
            lastRegistry = registry;
            if (!initialLoad || work.restoredLanes == 0) {
                resetProgress();
            }
            if (!control.restore(registry)) {
                resetProgress();
                interruptTerminalStep();
                work.status = "selection_unavailable";
            }
        }
        if (control.selectionUnavailable()) {
            if (control.runState().running()) { control.runState(control.runState().pause()); setChanged(); }
            work.status = "selection_unavailable";
            return;
        }
        if (supportsBatchControl() && !control.hasSelection()) { work.status = "choose_process"; return; }
        if (PreparationTier.manual(kind())) { clientSync.tickManualPreparation(this, registry); return; }
        if (kind().isStorage()) {
            work.status = storageStatus();
            return;
        }
        if (kind() == MachineKind.TERMINAL) {
            var network = matterNetwork(false);
            work.status = network.status();
            networkOnline = network.ready();
            return;
        }
        if (!industrialStructureReady()) {
            String diagnostic = IndustrialStructure.inspect(this).diagnostic();
            if (boundNetwork != null || work.progress > 0 || work.energyPaid > 0) restartChangedBatch(diagnostic); else work.status = diagnostic;
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
        if (!redstoneAllowsWork()) {
            if (work.ticket != null && work.ticket.valid()) planning().touch(work.ticket, level.getGameTime());
            work.status = "redstone_paused";
            return;
        }
        var operation = operations.operation(registry);
        if (operation == null) { work.status = operations.inputStatus(registry); resetProgress(); return; }
        if(work.ticket!=null&&!work.ticket.valid())resetProgressRetainingTransfer();
        if(targetActive()&&network!=null&&network.hasManualWork(this)) {resetProgress();work.status="manual_priority";return;}
        int speed = speed();
        String fingerprint = operation.signature() + ":" + operation.cost() + ":" + operation.ticks() + ":" + speed + ":" + parallelCapacity();
        if (IndustrialStructure.isIndustrial(kind())) fingerprint += ":" + IndustrialStructure.inspect(this).layoutFingerprint();
        if (!fingerprint.equals(work.job)) { resetProgressRetainingTransfer(); work.job = fingerprint; }
        work.duration = operation.ticks();
        long firstTickPerBatch = MachineEnergyPolicy.stepEnergy(operation.cost(), 0, speed, work.duration, 0);
        int activeLanes = work.ticket != null ? work.ticket.remaining() : work.restoredLanes;
        long requiredNow = activeLanes > 0
                ? MachineEnergyPolicy.stepEnergy(Math.multiplyExact(operation.cost(), activeLanes), work.progress, speed, work.duration, work.energyPaid)
                : firstTickPerBatch;
        String reason = operations.readiness(needsStorage, store, operation, requiredNow);
        if (!reason.equals("ready")) {
            if (work.ticket != null && work.ticket.valid()) planning().touch(work.ticket, level.getGameTime());
            work.status = reason;
            return;
        }
        if(work.ticket==null) {
            int limit=capacityForInputs(operation.inputs(),parallelCapacity());
            if(supportsBatchControl()&&!runState.repeat())limit=Math.min(limit,runState.queuedBatches());
            int restoring = work.restoredLanes;
            if (restoring > 0) limit = Math.min(limit, restoring);
            var outputs=operations.storedOutputs(operation);
            dev.latrox.rematter.chemistry.SubstanceKey targetKey=null;
            if(targetActive()&&!settings.targetSubstance().isEmpty()) {
                targetKey=dev.latrox.rematter.chemistry.SubstanceKey.of(settings.targetSubstance(),
                        dev.latrox.rematter.chemistry.Phase.valueOf(settings.targetPhase().toUpperCase(java.util.Locale.ROOT)));
                if(!outputs.containsKey(targetKey)) {work.status="target_unavailable";return;}
            }
            try {
                final MatterStore activeStore = store;
                work.ticket=planning().reserve(worker.id(),network==null?this:network.planningKey(),
                        network==null?Set.of(this):network.planningMembers(),level.getGameTime(),
                        reserveAdjustedContents(activeStore, operation.inputs()),activeStore==null?Long.MAX_VALUE:activeStore.capacity(),
                        operation.inputs(),outputs,limit,energy.getAmountAsLong(),restoring>0?0L:firstTickPerBatch,operations.physicalLimit(operation),
                        targetActive()?settings.target():0,targetKey,
                        activeStore==null?key->false:activeStore::voidsOverflow);
            } catch(ArithmeticException invalidQuantity) {work.status="invalid_quantity";return;}
            if (restoring > 0) {
                if (work.ticket == null || work.ticket.remaining() != restoring) {
                    resetProgress(); work.job = fingerprint;
                    work.status = targetActive() ? "target_reached" : "resources_reserved";
                    return;
                }
                work.restoredLanes = 0;
            }
            if(work.ticket==null) {work.status=targetActive()?"target_reached":"resources_reserved";return;}
        }
        planning().touch(work.ticket,level.getGameTime());
        if (store != null) { storageSession.bind(store); boundNetwork = network; }
        long totalBatchEnergy = Math.multiplyExact(operation.cost(), work.ticket.remaining());
        long due = MachineEnergyPolicy.stepEnergy(totalBatchEnergy, work.progress, speed, work.duration, work.energyPaid);
        if (energy.getAmountAsLong() < due) { work.status = PreparationTier.isPreparation(kind()) ? "energy" : "insufficient_energy"; return; }
        boolean transferred=network==null||network.advanceTransfer(this,transferWork,operation.signature(),
                ControllerThroughput.units(operation.inputs().values(),operation.outputs().values(),work.ticket.remaining()),
                level.getGameTime(),operation.inputs(),operations.storedOutputs(operation));
        if (due > 0) {
            energy.set((int) (energy.getAmountAsLong() - due));
            work.energyPaid += due;
            work.energyRate = due;
        }
        work.progress = (int) Math.min(work.duration, (long) work.progress + speed);
        visuals.capture(items.get(0), operation.visualResult(), work.progress, work.duration);
        work.processedThisTick = true;
        boolean preparation = PreparationTier.isPreparation(kind());
        work.preparationStage = preparation ? PreparationWork.stage(work.progress, work.duration) : -1;
        work.preparationMixing = preparation && operations.mixingPreparation();
        work.status = preparation ? PreparationWork.stageName(work.preparationStage, work.preparationMixing, kind() == MachineKind.SINTER_FURNACE) : "working";
        setChanged();
        if (work.progress < work.duration) return;
        if(!transferred){work.status="cable_limited";return;}
        if (!industrialStructureReady()) { restartChangedBatch(IndustrialStructure.inspect(this).diagnostic()); return; }
        // A deep check must still find the same owner, devices and mounted inventories.
        if (needsStorage) {
            var finalNetwork = matterNetwork(true);
            if (!finalNetwork.ready() || !acceptsNetwork(finalNetwork)) {
                restartChangedBatch(finalNetwork.ready() ? "storage_changed" : finalNetwork.status());
                return;
            }
            network = finalNetwork;
            store = finalNetwork.inventory();
        }
        reason = operations.readiness(needsStorage, store, operation, 0);
        if (!reason.equals("ready")) { work.status = reason; work.processedThisTick = false; return; }
        var ticket=work.ticket;
        if(ticket==null||!ticket.valid()) {resetProgress();work.status="storage_changed";return;}
        if (terminalStep != null && !terminalStep.connectedTo(this)) {
            interruptTerminalStep();
            work.status = "storage_changed";
            return;
        }
        int batches=ticket.remaining();
        for(int batch=0;batch<batches;batch++) {
            long laneCost = operation.cost();
            long unpaid = Math.max(0L, laneCost - work.energyPaid);
            reason=operations.readiness(needsStorage,store,operation,unpaid);
            if(!reason.equals("ready")) {work.status=reason;work.processedThisTick=false;return;}
            long fluidBefore = fluids.getAmountAsLong(0);
            boolean changedOwnedState=false;
            List<MatterNetworkInventory.MemberActivity> cableActivity;
            committingOwnedState=true;
            try {
                var outputs=operations.storedOutputs(operation);
                cableActivity=network==null?List.of():commitNetworkMatter(network,operation.inputs(),outputs);
                if(cableActivity==null) {work.status="output_full";work.processedThisTick=false;return;}
                changedOwnedState=true;
                operation.finish().run();
                long fromPaid = Math.min(work.energyPaid, laneCost);
                work.energyPaid -= fromPaid;
                long remaining = laneCost - fromPaid;
                if (remaining > 0) energy.set((int) (energy.getAmountAsLong() - remaining));
                long vented=operations.ventedAmount(operation);
                ventedTotal=vented>Long.MAX_VALUE-ventedTotal?Long.MAX_VALUE:ventedTotal+vented;
                if (supportsBatchControl()) {
                    control.runState(control.runState().completed());
                    if (terminalStep != null) {
                        terminalStep.completedBatches++;
                        if (control.runState().queuedBatches() == 0) {
                            terminalStep.worker = null;
                            terminalStep = null;
                        }
                    }
                }
                planning().completed(ticket);
            } finally {
                committingOwnedState=false;
                if(changedOwnedState) {if(network!=null)network.markChanged();setChanged();}
            }
            if(kind().synthesis()) {
                guidance(dev.latrox.rematter.guide.GuidanceEvent.PUT_IT_BACK);
                if (fluids.getResource(0).getFluid() == net.minecraft.world.level.material.Fluids.LAVA
                        && fluids.getAmountAsLong(0) - fluidBefore >= 1_000L) {
                    guidance(dev.latrox.rematter.guide.GuidanceEvent.SYNTHESIZED_LAVA);
                }
                var choice=selectedChoice();
                if(choice!=null) {
                    String target=choice.id().startsWith(MachineChoices.FLUID_PREFIX)?choice.id().substring(MachineChoices.FLUID_PREFIX.length()):choice.id();
                    if(dev.latrox.rematter.ReMatter.chemistry().synthesisPolicy().requirement(target).tier()>1)
                        guidance(dev.latrox.rematter.guide.GuidanceEvent.PROPER_TOOLS);
                }
            }
            if(kind()==MachineKind.INDUSTRIAL_REACTOR) {
                var choice=selectedChoice();
                if(choice!=null)dev.latrox.rematter.ReMatter.chemistry().recipe(SubstanceId.parse(choice.id()))
                        .filter(recipe->recipe.requiresKnownOutputs()&&recipe.type()==dev.latrox.rematter.chemistry.ProcessType.CRYSTALLIZATION)
                        .ifPresent(recipe->guidance(dev.latrox.rematter.guide.GuidanceEvent.GROWN_NOT_MINED));
            }
            if(network!=null)publishNetworkActivity(network,cableActivity);
        }
        consumeRedstonePulse();
        if (kind() == MachineKind.ANALYZER && soundGain() > 0.0F) ModSounds.play(level, worldPosition, ModSounds.ANALYZER_COMPLETE, soundGain(), 1.0F);
        if (kind().synthesis() && soundGain() > 0.0F && level.getGameTime() >= nextSynthesizerChime) {
            nextSynthesizerChime = level.getGameTime() + 20;
            ModSounds.play(level, worldPosition, ModSounds.SYNTHESIZER_COMPLETE, soundGain(), 1.0F);
        }
        transferWork.completed();
        resetProgress();work.status=kind()==MachineKind.ANALYZER?"analyzed":"ready";
        if(kind()==MachineKind.ANALYZER)control.runState(control.runState().pause());
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

    boolean advanceNetworkTransfer(String signature,long units) {
        var network=matterNetwork(false);
        return network.ready()&&network.advanceTransfer(this,transferWork,signature,units,level.getGameTime(),Map.of(),Map.of());
    }
    void completeNetworkTransfer(){transferWork.completed();}
    int matterTransferRate(){return matterNetwork(false).matterRate(this);}
    /** Work units per tick, from the machine tier, a formed structure and speed upgrades. */
    int speed() {
        if (PreparationTier.manual(kind())) return 1;
        int base = hasExtractionFocus() ? 2 : IndustrialStructure.usesReactorModules(kind()) ? 2 : PreparationTier.speed(kind());
        if (IndustrialStructure.isIndustrial(kind())) {
            var formation = IndustrialStructure.inspect(this);
            if (formation.formed()) base = formation.baseWorkRate(kind());
        }
        return MachineEnergyPolicy.speed(base, speedUpgradeCount());
    }
    /** The FE price of one batch with the installed Mekanism-style Speed and Efficiency upgrades. */
    long cost(long base) {
        return MachineEnergyPolicy.combined(base, speedUpgradeCount(), efficiencyUpgradeCount());
    }
    int effectiveSavingPercent() {
        long actual = cost(1_000_000L);
        return actual >= 1_000_000L ? 0 : (int) ((1_000_000L - actual) * 100L / 1_000_000L);
    }
    /** Exact current count in the configured scope. -1 means no readable stock observation. */
    public long targetStock() {
        if(settings.target()==0)return 0;
        if(settings.targetSubstance().isEmpty())return kind().synthesis()&&selectedId().startsWith(MachineChoices.FLUID_PREFIX)
                ?fluids.getAmountAsLong(0):items.get(1).getCount();
        var network=matterNetwork(false);if(!network.ready()||network.inventory()==null)return -1;
        return network.inventory().amount(SubstanceKey.of(settings.targetSubstance(),
                dev.latrox.rematter.chemistry.Phase.valueOf(settings.targetPhase().toUpperCase(java.util.Locale.ROOT))));
    }
    void resetProgress() { resetProgress(false); }
    /** Only a topology/layout restart retains work, still keyed to the exact job and quantity. */
    void resetProgressRetainingTransfer() { resetProgress(true); }
    private void refundWorkEnergy() {
        if (work.energyPaid <= 0) return;
        long restored = Math.min(energyCapacity(), energy.getAmountAsLong() + work.energyPaid);
        work.energyPaid = 0;
        energy.set((int) restored);
    }
    private void resetProgress(boolean retainTransfer) {
        if(!retainTransfer)transferWork.completed();
        if(work.ticket!=null&&level instanceof ServerLevel server) {
            var book=PLANNING.get(server);if(book!=null)book.release(worker.id());
        }
        refundWorkEnergy();
        work.ticket=null;work.reset();storageSession.clear();boundNetwork=null;
    }

    /**
     * The machine's matter network. One discovery serves every machine it found, so a change costs one bounded
     * scan per network instead of one per machine. A deep check re-reads every node once per game tick.
     */
    MachineMatterNetwork matterNetwork(boolean deep) {
        if (cachedNetwork == null || !cachedNetwork.current(this, deep)) {
            var network = MachineMatterNetwork.discover(this);
            for (var machine : network.machines()) {
                if (network.sharesPerspective(machine)) machine.cachedNetwork = network;
            }
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
        var members = store.members();
        for (int i = 0; i < members.size(); i++) {
            var member = members.get(i);
            if (!store.canDepositMember(i, key) || !member.hasTypeSpace(Map.of(), Map.of(key, 1L))) continue;
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
        resetProgressRetainingTransfer();
        work.processedThisTick = false;
        work.status = diagnostic;
    }
    /** Integrations must revalidate immediately before dispatching an order on the server thread. */
    public boolean isCurrentNetworkWorker(UUID requester, MachineBlockEntity worker) {
        if (requester == null || !Objects.equals(owner, requester) || !isLiveServerMachine()
                || worker == null || !worker.isLiveServerMachine()
                || !Objects.equals(worker.owner, requester)) return false;
        var network = matterNetwork(true);
        return network.ready() && network.hasWorker(worker);
    }
    TerminalDevicesPayload.Device terminalDevice() {
        var choice = terminalChoice();
        String dimId = level instanceof ServerLevel sl ? sl.dimension().identifier().toString() : "minecraft:overworld";
        return new TerminalDevicesPayload.Device(worldPosition, kind(), work.status, choice == null ? "" : choice.id(),
                Math.min(work.progress, work.duration), work.duration,
                supportsBatchControl() ? control.runState().queuedBatches() : 0, choice != null && canQueueFromTerminal(),
                settings.target(), targetStock(), !settings.targetSubstance().isEmpty(),
                Math.min(parallelRunning(), parallelCapacity()), parallelCapacity(),
                customName, dimId);
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
    int queuedBatchCount() {
        return supportsBatchControl() ? control.runState().queuedBatches() : 0;
    }
    boolean hasOpenAirAccess() {
        if (level == null) return false;
        BlockPos intake = worldPosition.above();
        if (IndustrialStructure.isIndustrial(kind())) {
            var shape = IndustrialStructure.inspect(this).shape();
            if (shape != null) intake = worldPosition.above(shape.height() - 1);
        }
        return level.getBlockState(intake).isAir() && level.canSeeSky(intake);
    }
    boolean dispatchTerminalBatches(TerminalJobCoordinator.StepRuntime step, int maxBatches) {
        if (terminalStep != null || step.worker != null || maxBatches <= 0 || !supportsBatchControl()
                || networkJobs || !industrialStructureReady() || !redstoneAllowsWork()) return false;
        var run = control.runState();
        if (run.repeat() || run.queuedBatches() != 0 || !control.select(step.step.choiceId())) return false;
        int count = Math.min(maxBatches, MachineRunState.MAX_QUEUED_BATCHES);
        control.runState(new MachineRunState(false, true, count));
        terminalStep = step;
        step.worker = this;
        setChanged();
        return true;
    }

    void interruptTerminalStep() {
        if (terminalStep != null) releaseTerminalStep(terminalStep);
    }

    void releaseTerminalStep(TerminalJobCoordinator.StepRuntime expected) {
        if (expected != terminalStep) return;
        terminalStep = null;
        expected.worker = null;
        control.runState(MachineRunState.manual());
        resetProgress();
        work.processedThisTick = false;
        setChanged();
    }

    public MachineSnapshot snapshot(int containerId) { return MachineSnapshots.build(this, containerId); }
    MachineProgressPayload progress(int containerId) { return MachineSnapshots.progress(this, containerId); }
    /** A short read-only overlay for an owner, without previews, choices or the full stock. */
    public @Nullable Summary summaryFor(Player player, int rows) {
        return canAccess(player) ? MachineSnapshots.summary(this, rows) : null;
    }
    public record Summary(String status, int energy, int progress, int duration, long used, long capacity, String storageSource,
                          int types, List<MachineSnapshot.Entry> entries, boolean automation, int fluidAmount, String fluidId,
                          MachineSnapshot.StructureView structure, List<MachineSnapshot.CellView> cells, int staleOrders, int heldOrders) {
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
        if(preparationRecoveryMask!=0)output.putInt("preparation_recovery_mask",preparationRecoveryMask);
        if (owner != null) output.putString("owner", owner.toString());
        if (securityMode != SecurityMode.PUBLIC) output.putString("security", securityMode.getSerializedName());
        if (!customName.isEmpty()) output.putString("custom_name", customName);
        output.putBoolean("automation", automation);
        if (autoEject && supportsAutoEject(kind())) output.putBoolean("auto_eject", true);
        sides.save(output);
        output.putBoolean("network_jobs", networkJobs);
        if (automationHost != null) output.store("automation_host", RAW_TAG_CODEC, automationHost.save());
        // The terminal owns its unpaid requests for this server session. A reloaded block
        // must never resume them as manual production. Player-entered queues still persist.
        var runState = terminalStep == null ? control.runState() : MachineRunState.manual();
        output.putBoolean("running", runState.running());
        output.putBoolean("repeat", runState.repeat());
        output.putInt("queued_batches", runState.queuedBatches());
        output.putString("selection", control.selectedId());
        output.putLong("target",settings.target());
        output.putString("target_substance",settings.targetSubstance());output.putString("target_phase",settings.targetPhase());
        output.store("vented_gases",Codec.STRING.listOf(),settings.ventedGases().stream().sorted().toList());
        if (settings.networkReserve() > 0L) output.putLong("network_reserve", settings.networkReserve());
        output.putLong("vented_total",ventedTotal);
        if (redstoneMode != RedstoneMode.IGNORED) output.putString("redstone_mode", redstoneMode.getSerializedName());
        if (redstonePulsePending) output.putBoolean("redstone_pulse", true);
        if (soundLevel != MachineSoundLevel.HIGH) {
            output.putInt("sound_volume", soundLevel.level());
            if (soundLevel.muted()) output.putBoolean("sound_muted", true);
        }
        structure.save(output);
        extensions.save(output);
        if (!analysis.isEmpty()) {
            ValueOutput savedAnalysis = output.child("analysis");
            savedAnalysis.putString("item", analysis.itemId());
            savedAnalysis.putString("profile", analysis.profileFingerprint());
        }
        output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(items));
        int activeLanes = work.ticket != null && work.ticket.valid() ? work.ticket.remaining() : work.restoredLanes;
        if (work.progress > 0 && work.duration > 0 && !work.job.isEmpty() && !networkJobs && activeLanes > 0) {
            ValueOutput savedWork = output.child("work");
            savedWork.putString("job", work.job);
            savedWork.putInt("progress", work.progress);
            savedWork.putInt("duration", work.duration);
            savedWork.putLong("energy_paid", work.energyPaid);
            savedWork.putInt("lanes", activeLanes);
            energy.serialize(output.child("power"));
        } else if (work.energyPaid > 0) {
            int cap = energyCapacity();
            var savedEnergy = new SimpleEnergyHandler(cap, ControllerEnergyBudget.MAX_INPUT, 0);
            savedEnergy.set((int) Math.min(cap, energy.getAmountAsLong() + work.energyPaid));
            savedEnergy.serialize(output.child("power"));
        } else {
            energy.serialize(output.child("power"));
        }
        fluids.serialize(output.child("tank"));
    }
    @Override protected void loadAdditional(ValueInput input) {
        clientSync.setLoadingItemVisuals(true);
        try {
            loadMachineState(input);
        } finally {
            clientSync.setLoadingItemVisuals(false);
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
        interruptTerminalStep();
        worker.release();
        resetProgress();
        lastRegistry = null;
        operations.forgetPreparationRecipe();
        cachedNetwork = null;
        items = NonNullList.withSize(MachineCellAccess.inventorySize(kind()), ItemStack.EMPTY);
        var context = input.lookup().createSerializationContext(NbtOps.INSTANCE);
        var savedItems = input.read("items", RAW_TAG_CODEC).flatMap(raw -> ItemStack.OPTIONAL_CODEC.listOf().parse(context, raw).result());
        var savedTank = input.childOrEmpty("tank").read(StacksResourceHandler.VALUE_IO_KEY, RAW_TAG_CODEC)
                .flatMap(raw -> FluidStack.OPTIONAL_CODEC.listOf().parse(context, raw).result());
        String rejection = MachineSchema.rejection(input.getInt("schema"), input.getString("owner"),
                savedItems.map(List::size), items.size(), savedTank.map(List::size), fluids.size());
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
        securityMode = SecurityMode.PUBLIC;
        customName = CustomNames.sanitize(input.getStringOr("custom_name", ""));
        try {
            securityMode = input.getString("security").map(SecurityMode::load).orElse(SecurityMode.PUBLIC);
            settings=new MachineSettings(input.getLongOr("target",0),input.getStringOr("target_substance",""),
                    input.getStringOr("target_phase",""),Set.copyOf(input.read("vented_gases",Codec.STRING.listOf()).orElse(List.of())),
                    input.getLongOr("network_reserve",0L));
            ventedTotal=input.getLongOr("vented_total",0);if(ventedTotal<0)throw new IllegalArgumentException("Invalid vent counter");
            redstoneMode = input.getString("redstone_mode").map(RedstoneMode::load).orElse(RedstoneMode.IGNORED);
            if (!RedstoneMode.controllable(kind())) redstoneMode = RedstoneMode.IGNORED;
            redstonePulsePending = redstoneMode == RedstoneMode.PULSE && input.getBooleanOr("redstone_pulse", false);
            redstoneSampled = false;
            if (ModSounds.workingSound(kind()) == null) {
                soundLevel = MachineSoundLevel.HIGH;
            } else {
                int fallbackLevel = input.getBooleanOr("sound_muted", false) ? 0 : MachineSoundLevel.HIGH.level();
                soundLevel = MachineSoundLevel.byLevel(input.getIntOr("sound_volume", fallbackLevel));
            }
        } catch(RuntimeException invalidSettings) {
            unsupportedData=input.read(STORED_DATA).orElseGet(CompoundTag::new);work.status="unsupported_data";bays.mountAll();return;
        }
        automation = input.getBooleanOr("automation", false);
        autoEject = supportsAutoEject(kind()) && input.getBooleanOr("auto_eject", false);
        sides.load(input, kind());
        networkJobs = supportsNetworkJobs() && input.getBooleanOr("network_jobs", false);
        if (kind() == MachineKind.NETWORK_INTERFACE) input.read("automation_host", RAW_TAG_CODEC).ifPresent(raw -> automationHost().load(raw));
        String savedSelection = input.getStringOr("selection", "");
        String selection = MachineSchema.selection(savedSelection);
        var runState = MachineRunState.restore(input.getBooleanOr("running", false), input.getBooleanOr("repeat", false),
                input.getIntOr("queued_batches", 0));
        control.load(selection.equals(savedSelection) ? runState : MachineRunState.manual(), selection);
        preparationRecoveryMask=input.getIntOr("preparation_recovery_mask",0)&24;
        structure.load(input);
        try{extensions.load(input);}catch(RuntimeException invalidExtensions){unsupportedData=input.read(STORED_DATA).orElseGet(CompoundTag::new);work.status="unsupported_data";bays.mountAll();return;}
        ValueInput savedAnalysis = input.childOrEmpty("analysis");
        analysis = AnalysisState.restore(savedAnalysis.getStringOr("item", ""), savedAnalysis.getStringOr("profile", ""));
        var stacks = savedItems.orElseThrow();
        for (int i = 0; i < Math.min(items.size(), stacks.size()); i++) items.set(i, stacks.get(i));
        // Migrate legacy compact Efficiency Upgrades from the shared slot 2 into the dedicated Efficiency slot 10.
        if (!IndustrialStructure.isIndustrial(kind()) && MachineEnergyPolicy.supportsUpgrades(kind())
                && items.get(MachineCellAccess.SPEED_SLOT).is(ModContent.EFFICIENCY_UPGRADE.get())
                && items.get(MachineCellAccess.EFFICIENCY_SLOT).isEmpty()) {
            items.set(MachineCellAccess.EFFICIENCY_SLOT, items.get(MachineCellAccess.SPEED_SLOT));
            items.set(MachineCellAccess.SPEED_SLOT, ItemStack.EMPTY);
        }
        energy.deserialize(input.childOrEmpty("power"));
        syncEnergyCapacity();
        ValueInput savedWork = input.childOrEmpty("work");
        String savedJob = savedWork.getStringOr("job", "");
        int savedProgress = savedWork.getIntOr("progress", 0);
        int savedDuration = savedWork.getIntOr("duration", 0);
        long savedEnergyPaid = savedWork.getLongOr("energy_paid", 0L);
        int savedLanes = savedWork.getIntOr("lanes", 0);
        if (!savedJob.isEmpty() && savedProgress > 0 && savedDuration > 0 && savedProgress <= savedDuration
                && savedEnergyPaid >= 0L && savedLanes >= 1 && savedLanes <= 4 && !networkJobs) {
            work.job = savedJob;
            work.progress = savedProgress;
            work.duration = savedDuration;
            work.energyPaid = savedEnergyPaid;
            work.restoredLanes = savedLanes;
        }
        fluids.deserialize(input.childOrEmpty("tank"));
        bays.mountAll();
    }

    @Override public Component getDisplayName() { return CustomNames.displayName(customName, "block.rematter." + kind().id()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return canUse(player) ? new MachineMenu(id, inventory, this) : null; }
    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int index) { int bay = MachineCellAccess.bayForSlot(kind(), index); if (bay >= 0) bays.flush(bay); return items.get(index); }
    @Override public ItemStack removeItem(int index, int amount) {
        if (MachineCellAccess.isCellSlot(kind(), index) && !prepareCellRemoval(index)) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(items, index, amount);
        if (!result.isEmpty()) {
            if (index == MachineCellAccess.EFFICIENCY_SLOT) syncEnergyCapacity();
            if (index == MachineCellAccess.ANCHOR_SLOT) syncChunkAnchor();
            if (MachineCellAccess.isCellSlot(kind(), index)) mountCell(index); else setChanged();
        }
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int index) {
        if (MachineCellAccess.isCellSlot(kind(), index) && !prepareCellRemoval(index)) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.takeItem(items, index);
        if (index == MachineCellAccess.EFFICIENCY_SLOT) syncEnergyCapacity();
        if (index == MachineCellAccess.ANCHOR_SLOT) syncChunkAnchor();
        if (MachineCellAccess.isCellSlot(kind(), index)) mountCell(index);
        return result;
    }
    @Override public void setItem(int index, ItemStack stack) {
        if (MachineCellAccess.isCellSlot(kind(), index)) {
            if (!prepareCellRemoval(index) || Transaction.getCurrentOpenedTransaction() != null) return;
            if (!stack.isEmpty() && (!MachineCellAccess.isMountableBayCell(stack) || stack.getCount() != 1)) return;
            if (!stack.isEmpty()) for (int slot = 0; slot < items.size(); slot++)
                if (slot != index && items.get(slot) == stack) return;
            items.set(index, stack); mountCell(index); return;
        }
        if(items.get(index).isEmpty())preparationRecoveryMask&=~(1<<index);
        items.set(index, stack);
        if (index == MachineCellAccess.EFFICIENCY_SLOT) syncEnergyCapacity();
        if (index == MachineCellAccess.ANCHOR_SLOT) syncChunkAnchor();
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
        if (unsupportedData != null || networkJobs && index!=2&&index!=MachineCellAccess.EFFICIENCY_SLOT&&index!=MachineCellAccess.PARALLEL_SLOT&&index!=MachineCellAccess.ANCHOR_SLOT || recoverySlot(index)) return false;
        if (MachineCellAccess.isCellSlot(kind(), index)) return MachineCellAccess.isMountableBayCell(stack) && stack.getCount() == 1 && canRemoveCell(index);
        if(index==MachineCellAccess.EFFICIENCY_SLOT)return MachineInputPolicy.acceptsEfficiency(kind(),stack);
        if(index==MachineCellAccess.PARALLEL_SLOT)return IndustrialStructure.isIndustrial(kind())&&kind().processes()&&stack.is(ModContent.PARALLEL_UPGRADE.get())
                || !IndustrialStructure.isIndustrial(kind())&&kind().synthesis()&&stack.is(ModContent.PRESSURE_MODULE.get())&&stack.getCount()==1
                || kind()==MachineKind.DEEP_EXTRACTOR&&stack.is(ModContent.EXTRACTION_FOCUS_UPGRADE.get())&&stack.getCount()==1;
        if(index==MachineCellAccess.PRIMAL_SLOT)return (kind()==MachineKind.ANALYZER||kind()==MachineKind.PRIMAL_CONVERTER||kind()==MachineKind.ENERGY_REACTOR)&&stack.getItem() instanceof dev.latrox.rematter.primal.PrimalCellItem&&stack.getCount()==1;
        if(index==MachineCellAccess.DISCOVERY_SLOT)return kind()==MachineKind.ANALYZER&&stack.is(dev.latrox.rematter.primal.PrimalContent.DISCOVERY_LENS.get())&&stack.getCount()==1
                || kind()==MachineKind.TRANSMUTATION_REACTOR&&(stack.is(ModContent.PARTICLE_RESONATOR.get())||stack.is(dev.latrox.rematter.primal.PrimalContent.PRIMAL_MATRIX_CORE.get()))&&stack.getCount()==1
                || kind()==MachineKind.PRIMAL_REACTOR&&PrimalReactorRecipes.isSeed(stack)&&stack.getCount()==1;
        if(index==MachineCellAccess.MASS_COMPRESSION_SLOT)return kind()==MachineKind.PRIMAL_REACTOR&&stack.is(ModContent.MASS_COMPRESSION_UPGRADE.get())&&stack.getCount()==1;
        if(index==MachineCellAccess.ANCHOR_SLOT)return MachineInputPolicy.acceptsAnchor(kind())&&stack.is(ModContent.ANCHOR_UPGRADE.get())&&stack.getCount()==1;
        if(kind().isEnergyMatrix()){
            if(index==0)return stack.getCount()==1&&dev.latrox.rematter.energy.EnergyCubeBlockEntity.dischargeable(stack);
            if(index==1)return stack.getCount()==1&&dev.latrox.rematter.energy.EnergyCubeBlockEntity.chargeable(stack);
            return false;
        }
        if(index==0&&(kind()==MachineKind.ANALYZER||kind()==MachineKind.PRIMAL_CONVERTER)&&(stack.getItem() instanceof dev.latrox.rematter.primal.PrimalCellItem||dev.latrox.rematter.primal.PrimalCells.sourceUnits(stack)>0))return true;
        if(index==0&&(kind().generator()||kind().isEnergyReactor()))return stack.getComponentsPatch().isEmpty()&&dev.latrox.rematter.content.generation.CrystalFuelData.fuels().containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (index == 1) return false;
        if (index == 2) return MachineInputPolicy.acceptsUpgrade(kind(), stack);
        return operations.acceptsInput(index, stack, ReMatter.chemistry());
    }
    @Override public boolean canTakeItem(Container target, int index, ItemStack stack) { return automationAllowed() && !IndustrialStructure.isIndustrial(kind()) && !kind().isStorage() && (index==1||recoverySlot(index)); }
    private boolean allowsItemInput(Direction side) {
        return automationAllowed() && !kind().isStorage() && !IndustrialStructure.isIndustrial(kind()) && allowsPort(side, MachinePortMode.ITEM_INPUT);
    }
    private boolean allowsItemOutput(Direction side) {
        return automationAllowed() && !kind().isStorage() && !IndustrialStructure.isIndustrial(kind()) && allowsPort(side, MachinePortMode.ITEM_OUTPUT);
    }
    @Override public int[] getSlotsForFace(Direction side) {
        boolean input = allowsItemInput(side), output = allowsItemOutput(side);
        if (!input) return output ? (PreparationTier.isPreparation(kind())?new int[]{1,3,4}:new int[]{1}) : new int[0];
        if (PreparationTier.isPreparation(kind())) return output ? new int[]{0,3,4,1} : new int[]{0,3,4};
        return output ? new int[]{0, 1} : new int[]{0};
    }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction side) {
        return allowsItemInput(side) && (index == 0 || PreparationTier.isPreparation(kind()) && (index == 3 || index == 4)) && canPlaceItem(index, stack);
    }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) {
        return allowsItemOutput(side) && (index==1||recoverySlot(index));
    }
    @Override public void clearContent() { for (int slot : cellSlots()) if (!items.get(slot).isEmpty()) return; items.clear(); syncEnergyCapacity(); syncChunkAnchor(); setChanged(); }
}
