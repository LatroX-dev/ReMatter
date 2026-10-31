package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
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
    private CompoundTag preservedMatter;
    private UUID owner;
    private boolean automation;
    private boolean running = true;
    private int selection;
    private String selectedId = "";
    private int progress;
    private int duration;
    private String job = "";
    private String analyzedId = "";
    private String status = "idle";
    private ChemistryRegistry lastRegistry;
    private BlockPos cachedStorage;
    private long nextNetworkScan;
    private boolean processedThisTick;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, 16_000, 0) {
        @Override public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return isLiveServerMachine() ? super.insert(amount, transaction) : 0;
        }
        @Override protected void onEnergyChanged(int previous) { setChanged(); }
    };
    private final FluidStacksResourceHandler fluids = new FluidStacksResourceHandler(1, 16_000) {
        @Override public boolean isValid(int index, FluidResource resource) {
            return !resource.isEmpty() && resource.isComponentsPatchEmpty()
                    && ReMatter.chemistry().fluidProfile(BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString()).isPresent();
        }
        @Override protected void onContentsChanged(int index, FluidStack previous) { setChanged(); }
    };

    private final ResourceHandler<FluidResource> automatedFluids = new AutomationResourceHandler<>(
            fluids, this::automationAllowed, 1, FluidResource.EMPTY);
    private final RootCommitJournal inputTransferCommit = new RootCommitJournal(() -> {
        analyzedId = "";
        running = true;
        setChanged();
    });

    public MachineBlockEntity(BlockPos pos, BlockState state) { super(ModContent.MACHINE_ENTITY.get(), pos, state); }
    public MachineKind kind() { return ((MachineBlock) getBlockState().getBlock()).kind(); }
    public SimpleEnergyHandler energy() { return energy; }
    public FluidStacksResourceHandler fluids() { return fluids; }
    public ResourceHandler<FluidResource> automatedFluids() { return automatedFluids; }
    public ResourceHandler<ItemResource> automatedItems(Direction side) {
        return new AutomationResourceHandler<>(new WorldlyContainerWrapper(this, Objects.requireNonNull(side)), this::automationAllowed, 2, ItemResource.EMPTY);
    }
    public boolean automation() { return automation; }
    private boolean automationAllowed() {
        return automation && isLiveServerMachine();
    }
    private boolean isLiveServerMachine() {
        if (isRemoved() || level == null || level.isClientSide()) return false;
        var server = level.getServer();
        return server != null && server.isSameThread() && level.hasChunkAt(worldPosition)
                && level.getBlockEntity(worldPosition) == this;
    }
    public void claim(Player player) { if (owner == null) { owner = player.getUUID(); setChanged(); } }
    public boolean canAccess(Player player) { return owner == null || owner.equals(player.getUUID()); }
    public @Nullable MachineSnapshot snapshotFor(Player player) { return canAccess(player) ? snapshot(-1) : null; }
    public MatterInventory matterInventory() { return matter; }
    public boolean chemicalAutomationAllowed() {
        return kind() == MachineKind.STORAGE && preservedMatter == null && automationAllowed();
    }
    public boolean canUse(Player player) {
        boolean allowed = canAccess(player);
        if (!allowed && !player.level().isClientSide()) player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
        return allowed;
    }
    public boolean hasStoredResources() {
        return preservedMatter != null || matter.total() > 0 || !isEmpty() || fluids.getAmountAsLong(0) > 0 || energy.getAmountAsLong() > 0;
    }
    public void toggleAutomation() {
        automation = !automation;
        if (level != null) level.invalidateCapabilities(worldPosition);
        setChanged();
    }
    public void toggleRunning() {
        if (!running && selection < 0 && !selectedId.isEmpty()) return;
        running = !running;
        setChanged();
    }
    public void select(int offset) {
        var choices = choices();
        if (!choices.isEmpty()) {
            selection = selection < 0 ? (offset < 0 ? choices.size() - 1 : 0)
                    : (int) Math.floorMod((long) selection + offset, choices.size());
            selectedId = choices.get(selection).id();
            resetProgress();
            setChanged();
        }
    }
    public boolean selectRecipe(String exactId) {
        var choices = choices();
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).id().equals(exactId)) {
                selection = i;
                selectedId = exactId;
                resetProgress();
                setChanged();
                return true;
            }
        }
        return false;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        processedThisTick = false;
        try {
            tickMachine();
        } finally {
            BlockState state = getBlockState();
            if (!isRemoved() && state.getValue(MachineBlock.ACTIVE) != processedThisTick) {
                level.setBlock(worldPosition, state.setValue(MachineBlock.ACTIVE, processedThisTick), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }
    private void tickMachine() {
        ChemistryRegistry registry = ReMatter.chemistry();
        if (registry != lastRegistry) {
            lastRegistry = registry;
            analyzedId = "";
            resetProgress();
            var options = choices();
            String previousSelectedId = selectedId;
            if (kind() == MachineKind.SYNTHESIZER && registry.fluidProfile(selectedId).isPresent()
                    && !registry.synthesisPolicy().allowsItem(selectedId)) selectedId = MachineChoices.fluidChoiceId(selectedId);
            var restored = RecipeSelection.restore(selectedId, options.stream().map(MachineChoices.Choice::id).toList());
            selection = restored.index();
            selectedId = restored.id();
            if (!selectedId.equals(previousSelectedId)) setChanged();
            if (restored.unavailable()) { running = false; status = "selection_unavailable"; setChanged(); }
        }
        if (selection < 0 && !selectedId.isEmpty()) {
            if (running) { running = false; setChanged(); }
            status = "selection_unavailable";
            return;
        }
        if (!running) { if (!status.equals("analyzed")) status = "paused"; return; }
        if (kind() == MachineKind.STORAGE) { status = preservedMatter == null ? "ready" : "unknown_schema"; return; }
        MachineBlockEntity store = networkStorage(false);
        if (store == null) { status = "no_storage"; return; }
        if (store.preservedMatter != null) { status = "unknown_schema"; return; }
        if (kind() == MachineKind.TERMINAL) { status = "ready"; return; }
        if (kind() == MachineKind.INDUSTRIAL_REACTOR && !hasReactorStructure()) { status = "structure"; return; }
        Operation operation = operation(registry);
        if (operation == null) { status = kind() == MachineKind.ANALYZER ? "insert_sample" : "input"; resetProgress(); return; }
        String fingerprint = operation.signature + ":" + operation.cost + ":" + operation.ticks + ":" + speed();
        if (!fingerprint.equals(job)) { progress = 0; job = fingerprint; }
        duration = operation.ticks;
        if (!store.matter.canApply(operation.inputs, operation.outputs)) { status = "matter"; return; }
        if (energy.getAmountAsLong() < operation.cost) { status = "energy"; return; }
        if (!operation.canFinish()) { status = "output"; return; }
        progress = (int) Math.min(duration, (long) progress + speed());
        processedThisTick = true;
        status = "working";
        setChanged();
        if (progress < duration) return;
        // A changed connection or owner never grants a final transfer against a cached storage.
        if (networkStorage(true) != store) { status = "no_storage"; return; }
        if (!operation.canFinish() || energy.getAmountAsLong() < operation.cost) return;
        if (!store.matter.apply(operation.inputs, operation.outputs)) { status = "matter"; return; }
        operation.finish.run();
        energy.set((int) (energy.getAmountAsLong() - operation.cost));
        store.setChanged();
        resetProgress();
        status = kind() == MachineKind.ANALYZER ? "analyzed" : "ready";
        if (kind() == MachineKind.ANALYZER) running = false;
        setChanged();
    }

    private Operation operation(ChemistryRegistry registry) {
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
                            () -> { if (analyze) analyzedId = id; else items.get(0).shrink(requiredItems); });
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
        selection = Math.floorMod(selection, options.size());
        MachineChoices.Choice choice = options.get(selection);
        selectedId = choice.id();
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
        int base = kind() == MachineKind.INDUSTRIAL_REACTOR ? 2 : 1;
        return base * (items.get(2).is(ModContent.SPEED_UPGRADE.get()) ? 1 + Math.min(4, items.get(2).getCount()) : 1);
    }
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
    private void resetProgress() { progress = 0; duration = 0; job = ""; }

    public List<MachineChoices.Choice> choices() { return MachineChoices.forKind(kind(), ReMatter.chemistry()); }
    private record Operation(String signature, Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                             long cost, int ticks, java.util.function.BooleanSupplier ready, Runnable finish) {
        boolean canFinish() { return ready.getAsBoolean(); }
    }
    private static String pretty(String id) {
        String value = id.replace('_', ' ');
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private MachineBlockEntity networkStorage(boolean force) {
        if (kind() == MachineKind.STORAGE) return this;
        if (level == null) return null;
        if (!force && level.getGameTime() < nextNetworkScan) {
            if (cachedStorage != null && level.hasChunkAt(cachedStorage) && level.getBlockEntity(cachedStorage) instanceof MachineBlockEntity cached
                    && cached.kind() == MachineKind.STORAGE && Objects.equals(cached.owner, owner)) return cached;
            return null;
        }
        nextNetworkScan = level.getGameTime() + 20;
        cachedStorage = null;
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(worldPosition);
        visited.add(worldPosition);
        MachineBlockEntity found = null;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine && machine.kind() == MachineKind.STORAGE) {
                if (found != null && found != machine) return null;
                found = machine;
            }
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (visited.contains(next) || !level.hasChunkAt(next)) continue;
                boolean compatible = level.getBlockState(next).is(ModContent.MATTER_PIPE.get());
                if (!compatible && level.getBlockEntity(next) instanceof MachineBlockEntity machine) compatible = Objects.equals(machine.owner, owner);
                if (!compatible) continue;
                // Count connected nodes, not adjacent air; at most six boundary reads per node.
                if (visited.size() == 64) return null;
                visited.add(next);
                queue.add(next);
            }
        }
        if (found != null) cachedStorage = found.worldPosition;
        return found;
    }
    private boolean hasReactorStructure() {
        if (level == null) return false;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0) continue;
            BlockPos pos = worldPosition.offset(x, 0, z);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(ModContent.REACTOR_CASING.get())) return false;
        }
        return true;
    }

    public MachineSnapshot snapshot(int containerId) {
        MachineBlockEntity store = kind().isStorage() ? this : networkStorage(false);
        var entries = new ArrayList<MachineSnapshot.Entry>();
        if (store != null) {
            store.matter.contents().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().id().toString()))
                    .limit(512).forEach(entry -> entries.add(new MachineSnapshot.Entry(
                            pretty(entry.getKey().id().path()), entry.getKey().phase().name().toLowerCase(Locale.ROOT), entry.getValue())));
        }
        if (kind() == MachineKind.ANALYZER) entries.clear();
        if (kind() == MachineKind.ANALYZER && !items.get(0).isEmpty()) {
            String itemId = BuiltInRegistries.ITEM.getKey(items.get(0).getItem()).toString();
            if (itemId.equals(analyzedId) && items.get(0).getComponentsPatch().isEmpty()) ReMatter.chemistry().profile(itemId).ifPresent(profile -> {
                entries.clear();
                profile.matter().entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().id().toString())).limit(512)
                        .forEach(entry -> entries.add(new MachineSnapshot.Entry(pretty(entry.getKey().id().path()), entry.getKey().phase().name().toLowerCase(Locale.ROOT), entry.getValue())));
            });
        }
        var choices = choices();
        int selected = choices.isEmpty() || selection < 0 ? -1 : Math.floorMod(selection, choices.size());
        var chosen = selected < 0 ? null : choices.get(selected);
        int sampleBatch = kind() == MachineKind.ANALYZER && !items.get(0).isEmpty()
                ? ReMatter.chemistry().profile(BuiltInRegistries.ITEM.getKey(items.get(0).getItem()).toString()).map(MaterialProfile::itemCount).orElse(1) : 1;
        return new MachineSnapshot(containerId, kind().ordinal(), (int) energy.getAmountAsLong(), progress, duration,
                status, selected, chosen == null ? selectedId : chosen.id(), chosen == null ? selectedId : chosen.name(), choices.size(), sampleBatch, entries,
                store == null ? 0 : store.matter.total(), store == null ? 0 : store.matter.capacity(), automation, running,
                (int) fluids.getAmountAsLong(0), fluids.getResource(0).isEmpty() ? "" : fluids.getResource(0).getHoverName().getString());
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("schema", 1);
        if (owner != null) output.putString("owner", owner.toString());
        output.putBoolean("automation", automation);
        output.putBoolean("running", running);
        output.putString("selection", selectedId);
        // Progress is deliberately not persisted: no input or energy was consumed before final commit.
        output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(items));
        energy.serialize(output.child("power"));
        fluids.serialize(output.child("tank"));
        if (preservedMatter != null) output.store("matter", CompoundTag.CODEC, preservedMatter);
        else {
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
        automation = input.getBooleanOr("automation", false);
        running = input.getBooleanOr("running", true);
        selectedId = input.getStringOr("selection", "");
        items = NonNullList.withSize(3, ItemStack.EMPTY);
        input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> { for (int i = 0; i < Math.min(3, list.size()); i++) items.set(i, list.get(i)); });
        energy.deserialize(input.childOrEmpty("power"));
        fluids.deserialize(input.childOrEmpty("tank"));
        preservedMatter = input.read("matter", CompoundTag.CODEC).orElse(null);
        try {
            ValueInput storage = input.childOrEmpty("matter");
            if (storage.getIntOr("schema", 1) != 1) throw new IllegalArgumentException("Unknown schema");
            long capacity = storage.getLongOr("capacity", MATTER_CAPACITY);
            List<SubstanceStack> contents = new ArrayList<>();
            for (ValueInput entry : storage.childrenListOrEmpty("contents")) {
                contents.add(new SubstanceStack(new SubstanceKey(SubstanceId.parse(entry.getStringOr("substance", "")),
                        Phase.valueOf(entry.getStringOr("phase", ""))), entry.getLongOr("amount", -1)));
            }
            matter = MatterInventory.restore(new MatterInventory.Snapshot(1, capacity, contents, List.of()));
            preservedMatter = null;
        } catch (RuntimeException ignored) {
            // Keep the exact raw payload for future schema support; do not erase unknown resources.
            matter = new MatterInventory(MATTER_CAPACITY);
        }
        resetProgress();
        lastRegistry = null;
        cachedStorage = null;
        nextNetworkScan = 0;
    }

    @Override public Component getDisplayName() { return Component.translatable("block.rematter." + kind().id()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return canUse(player) ? new MachineMenu(id, inventory, this) : null; }
    @Override public int getContainerSize() { return 3; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int index) { return items.get(index); }
    @Override public ItemStack removeItem(int index, int amount) { ItemStack result = ContainerHelper.removeItem(items, index, amount); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int index) { return ContainerHelper.takeItem(items, index); }
    @Override public void setItem(int index, ItemStack stack) { items.set(index, stack);
        if (index == 0 && kind() == MachineKind.ANALYZER) { analyzedId = ""; running = true; }
        stack.limitSize(getMaxStackSize(stack)); setChanged(); }
    @Override public void setItem(int index, ItemStack stack, boolean insideTransaction) {
        if (insideTransaction) items.set(index, stack);
        else setItem(index, stack);
    }
    @Override public void onTransfer(int index, int amountChange, TransactionContext transaction) {
        if (index == 0 && amountChange != 0 && kind() == MachineKind.ANALYZER) inputTransferCommit.updateSnapshots(transaction);
    }
    @Override public boolean stillValid(Player player) { return canUse(player) && Container.stillValidBlockEntity(this, player); }
    @Override public boolean canPlaceItem(int index, ItemStack stack) {
        if (index == 1) return false;
        if (index == 2) return kind().processes() && (stack.is(ModContent.SPEED_UPGRADE.get()) || stack.is(ModContent.EFFICIENCY_UPGRADE.get()));
        return (kind() == MachineKind.DECOMPOSER || kind() == MachineKind.ANALYZER) && stack.getComponentsPatch().isEmpty()
                && ReMatter.chemistry().profile(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).isPresent();
    }
    @Override public boolean canTakeItem(Container target, int index, ItemStack stack) { return automationAllowed() && index == 1; }
    @Override public int[] getSlotsForFace(Direction side) { return automationAllowed() ? new int[] {0, 1} : new int[0]; }
    @Override public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction side) { return automationAllowed() && index == 0 && canPlaceItem(index, stack); }
    @Override public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) { return automationAllowed() && index == 1; }
    @Override public void clearContent() { items.clear(); setChanged(); }
}
