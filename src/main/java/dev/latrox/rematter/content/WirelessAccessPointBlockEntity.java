package dev.latrox.rematter.content;

import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Powered Matter Network transceiver with unified Range Upgrade slot (Boosters or Dimensional Card) and Terminal Link/Charge dock. */
public final class WirelessAccessPointBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int CAPACITY = 128_000;
    public static final int MAX_INSERT = 8_192;
    public static final int STANDBY_DRAIN = 8;
    public static final int BOOSTER_DRAIN = 6;
    public static final int DIMENSIONAL_DRAIN = 256;
    public static final int ANCHOR_DRAIN = SpatialAnchorBlockEntity.DRAIN_PER_CHUNK;
    public static final int BASE_RANGE = 128;
    public static final int RANGE_PER_BOOSTER = 24;
    public static final int MAX_BOOSTERS = 16;
    public static final int MAX_RANGE = BASE_RANGE + MAX_BOOSTERS * RANGE_PER_BOOSTER;
    public static final int SLOT_RANGE = 0;
    public static final int SLOT_TERMINAL = 1;
    public static final int SLOT_ANCHOR = 2;
    public static final int INVENTORY_SIZE = 3;

    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final List<ItemStack> migrationOverflow = new ArrayList<>();
    private final ResourceHandler<ItemResource> itemHandler = VanillaContainerWrapper.of(this);
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INSERT, CAPACITY) {
        @Override protected void onEnergyChanged(int previous) { setChanged(); }
    };
    private final EnergyHandler internalSource = new EnergyHandler() {
        @Override public long getAmountAsLong() { return energy(); }
        @Override public long getCapacityAsLong() { return CAPACITY; }
        @Override public int insert(int amount, TransactionContext tx) {
            TransferPreconditions.checkNonNegative(amount);
            return 0;
        }
        @Override public int extract(int amount, TransactionContext tx) {
            TransferPreconditions.checkNonNegative(amount);
            return energy.extract(amount, tx);
        }
    };
    private @Nullable UUID owner;
    private boolean powered;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    private String customName = "";
    private ChunkAnchorManager.Status anchorStatus = ChunkAnchorManager.Status.IDLE;
    private @Nullable MachineMatterNetwork cachedNetwork;

    public WirelessAccessPointBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.WIRELESS_ACCESS_POINT_ENTITY.get(), pos, state);
    }

    public int energy() { return (int) energy.getAmountAsLong(); }
    public @Nullable UUID owner() { return owner; }
    public String customName() { return customName; }
    public ChunkAnchorManager.Status anchorStatus() { return anchorStatus; }

    public void setCustomName(String next) {
        String clean = CustomNames.sanitize(next);
        if (customName.equals(clean)) return;
        customName = clean;
        setChanged();
    }

    public int boosterCount() {
        ItemStack stack = items.get(SLOT_RANGE);
        return stack.is(ModContent.WIRELESS_BOOSTER.get()) ? Math.clamp(stack.getCount(), 0, MAX_BOOSTERS) : 0;
    }

    public boolean hasDimensionalCard() {
        return items.get(SLOT_RANGE).is(ModContent.DIMENSIONAL_CARD.get());
    }

    public boolean hasAnchorUpgrade() {
        return items.get(SLOT_ANCHOR).is(ModContent.ANCHOR_UPGRADE.get());
    }

    public int effectiveRange() {
        return BASE_RANGE + boosterCount() * RANGE_PER_BOOSTER;
    }

    public static int computeDrain(int boosters, boolean dimensional, boolean anchor) {
        int base = dimensional
                ? DIMENSIONAL_DRAIN
                : STANDBY_DRAIN + Math.clamp(boosters, 0, MAX_BOOSTERS) * BOOSTER_DRAIN;
        return base + (anchor ? ANCHOR_DRAIN : 0);
    }

    public int effectiveDrain() {
        return computeDrain(boosterCount(), hasDimensionalCard(), hasAnchorUpgrade());
    }

    public boolean withinRange(Player player) {
        if (hasDimensionalCard()) return true;
        int range = effectiveRange();
        return player.position().distanceToSqr(Vec3.atCenterOf(worldPosition)) <= (double) range * range;
    }

    public boolean canReach(Player player) {
        if (hasDimensionalCard()) return true;
        return level != null && level == player.level() && withinRange(player);
    }

    public void syncChunkAnchor() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        if (!hasAnchorUpgrade() || owner == null) {
            ChunkAnchorManager.release(server, worldPosition);
            anchorStatus = new ChunkAnchorManager.Status(false, 0,
                    ChunkAnchorManager.ownerChunkCount(server.getServer(), owner),
                    ChunkLoadingConfig.maxChunksPerPlayer(), "idle");
            return;
        }
        if (!ChunkLoadingConfig.enabled()) {
            ChunkAnchorManager.release(server, worldPosition);
            anchorStatus = new ChunkAnchorManager.Status(false, 0,
                    ChunkAnchorManager.ownerChunkCount(server.getServer(), owner),
                    ChunkLoadingConfig.maxChunksPerPlayer(), "disabled");
            return;
        }
        if (!redstoneAllows()) {
            ChunkAnchorManager.release(server, worldPosition);
            anchorStatus = new ChunkAnchorManager.Status(false, 0,
                    ChunkAnchorManager.ownerChunkCount(server.getServer(), owner),
                    ChunkLoadingConfig.maxChunksPerPlayer(), "redstone_paused");
            return;
        }
        if (!powered && energy() < effectiveDrain()) {
            ChunkAnchorManager.release(server, worldPosition);
            anchorStatus = new ChunkAnchorManager.Status(false, 0,
                    ChunkAnchorManager.ownerChunkCount(server.getServer(), owner),
                    ChunkLoadingConfig.maxChunksPerPlayer(), "missing_power");
            return;
        }
        anchorStatus = ChunkAnchorManager.sync(server, worldPosition, owner, ChunkAnchorManager.square(worldPosition, 0));
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) syncChunkAnchor();
    }

    public RedstoneMode redstoneMode() { return redstoneMode; }

    public boolean redstoneAllows() {
        if (redstoneMode == RedstoneMode.IGNORED || level == null) return true;
        boolean sig = level.hasNeighborSignal(worldPosition);
        return redstoneMode == RedstoneMode.HIGH ? sig : !sig;
    }

    public boolean redstonePaused() { return !redstoneAllows(); }

    public boolean cycleRedstoneMode(Player player, boolean forward) {
        if (!networkLive() || player.isSpectator() || !canAccess(player)) return false;
        RedstoneMode next = redstoneMode.stepSimple(forward);
        if (redstoneMode == next) return true;
        redstoneMode = next;
        setChanged();
        syncChunkAnchor();
        return true;
    }

    public boolean powered() { return networkLive() && redstoneAllows() && (powered || energy() >= effectiveDrain()); }

    public boolean matterReady() {
        return networkLive() && matterNetwork(false).ready();
    }

    boolean networkLive() {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || isRemoved() || owner == null) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this;
    }

    List<Direction> connectionSides() {
        return List.of(Direction.DOWN);
    }

    MachineMatterNetwork matterNetwork(boolean deep) {
        if (cachedNetwork == null || !cachedNetwork.current(this, deep)) {
            cachedNetwork = MachineMatterNetwork.discover(this);
        }
        return cachedNetwork;
    }

    private SecurityMode securityMode = SecurityMode.PUBLIC;

    public SecurityMode securityMode() { return securityMode; }

    public boolean canManageSecurity(Player player) { return MachineAccess.canManage(owner, player); }

    public boolean setSecurityMode(Player player, SecurityMode next) {
        if (!networkLive() || player.isSpectator() || !canManageSecurity(player) || next == null) return false;
        if (securityMode == next) return true;
        securityMode = next;
        cachedNetwork = null;
        setChanged();
        touchTopology();
        return true;
    }

    public boolean cycleSecurityMode(Player player, boolean forward) {
        return setSecurityMode(player, forward ? securityMode.next() : securityMode.previous());
    }

    public boolean canRemove(Player player) {
        return canAccess(player);
    }

    public boolean canAccess(Player player) {
        return MachineAccess.allows(level, owner, securityMode, player);
    }

    public void placedBy(@Nullable LivingEntity placer) {
        placedBy(placer, ItemStack.EMPTY);
    }

    public void placedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (securityMode == SecurityMode.PUBLIC) {
            securityMode = MachineAccess.placementDefault(level, placer);
        }
        String fromItem = CustomNames.fromStack(stack);
        if (!fromItem.isEmpty()) customName = fromItem;
        assignOwner(placer instanceof Player player && !(player instanceof FakePlayer) ? player.getUUID() : null);
        syncChunkAnchor();
    }

    public boolean interact(Player player) {
        if (owner == null && !(player instanceof FakePlayer) && !player.isSpectator()) assignOwner(player.getUUID());
        boolean allowed = canAccess(player);
        if (!allowed && !player.level().isClientSide()) player.sendSystemMessage(Component.translatable("message.rematter.private_machine"));
        return allowed;
    }

    private void assignOwner(@Nullable UUID next) {
        if (Objects.equals(owner, next)) return;
        owner = next;
        cachedNetwork = null;
        setChanged();
        touchTopology();
        syncChunkAnchor();
    }

    public void afterRotation(ServerLevel server) {
        cachedNetwork = null;
        setChanged();
        touchTopology();
    }

    private void touchTopology() {
        if (level instanceof ServerLevel server) {
            ChunkRevisions.touchLoaded(server, worldPosition);
            server.invalidateCapabilities(worldPosition);
            MatterCableBlock.refreshLoadedAround(server, worldPosition);
        }
    }

    public @Nullable EnergyHandler sidedEnergy(@Nullable Direction side) {
        if (side == null || !(level instanceof ServerLevel) || !WirelessAccessPointBlock.connectsSide(getBlockState(), side)) return null;
        return new EnergyHandler() {
            @Override public long getAmountAsLong() { return networkLive() ? energy.getAmountAsLong() : 0; }
            @Override public long getCapacityAsLong() { return networkLive() ? CAPACITY : 0; }
            @Override public int insert(int amount, TransactionContext tx) {
                TransferPreconditions.checkNonNegative(amount);
                return networkLive() ? energy.insert(Math.min(amount, MAX_INSERT), tx) : 0;
            }
            @Override public int extract(int amount, TransactionContext tx) {
                TransferPreconditions.checkNonNegative(amount);
                return 0;
            }
        };
    }

    private void bindDockedTerminal() {
        if (!(level instanceof ServerLevel server)) return;
        ItemStack docked = items.get(SLOT_TERMINAL);
        if (docked.getItem() instanceof WirelessMatterTerminalItem) {
            WirelessTerminalData desired = WirelessTerminalData.of(server.dimension(), worldPosition);
            if (!desired.equals(docked.get(WirelessTerminalData.LINK.get()))) {
                docked.set(WirelessTerminalData.LINK.get(), desired);
                setChanged();
            }
        }
    }

    public void serverTick() {
        if (!networkLive() || Transaction.getCurrentOpenedTransaction() != null) return;
        if (!migrationOverflow.isEmpty() && level instanceof ServerLevel server) {
            for (ItemStack extra : migrationOverflow) {
                if (!extra.isEmpty()) Block.popResource(server, worldPosition, extra.copy());
            }
            migrationOverflow.clear();
            setChanged();
        }
        bindDockedTerminal();
        boolean wasPowered = powered;
        int drain = effectiveDrain();
        boolean online = redstoneAllows() && energy() >= drain;
        if (online) {
            try (var tx = Transaction.openRoot()) {
                if (energy.extract(drain, tx) == drain) tx.commit();
                else online = false;
            }
        }
        powered = online;
        if (hasAnchorUpgrade() && (online != wasPowered || online != anchorStatus.active())) {
            syncChunkAnchor();
        }
        if (online && !items.get(SLOT_TERMINAL).isEmpty() && energy() > 0) {
            var sink = ItemAccess.forHandlerIndexStrict(itemHandler, SLOT_TERMINAL).getCapability(Capabilities.Energy.ITEM);
            if (sink != null && sink.getAmountAsLong() < sink.getCapacityAsLong()) {
                try (var tx = Transaction.openRoot()) {
                    if (EnergyHandlerUtil.move(internalSource, sink, MAX_INSERT, tx) > 0) tx.commit();
                }
            }
        }
        BlockState state = getBlockState();
        if (state.getValue(WirelessAccessPointBlock.ACTIVE) != online) {
            level.setBlock(worldPosition, state.setValue(WirelessAccessPointBlock.ACTIVE, online), Block.UPDATE_CLIENTS);
        }
    }

    public List<ItemStack> dropContentsList() {
        var out = new ArrayList<ItemStack>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) out.add(stack.copy());
        }
        for (ItemStack extra : migrationOverflow) {
            if (!extra.isEmpty()) out.add(extra.copy());
        }
        return out;
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {}

    @Override public void setRemoved() {
        cachedNetwork = null;
        if (level instanceof ServerLevel server) {
            ChunkAnchorManager.release(server, worldPosition);
            ChunkRevisions.touchLoaded(server, worldPosition);
        }
        super.setRemoved();
    }

    public void writeMenuInit(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeUtf(customName, CustomNames.MAX_LENGTH);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("schema", 2);
        output.putInt("energy", energy());
        if (owner != null) output.putString("owner", owner.toString());
        if (securityMode != SecurityMode.PUBLIC) output.putString("security", securityMode.getSerializedName());
        if (redstoneMode != RedstoneMode.IGNORED) output.putString("redstone_mode", redstoneMode.getSerializedName());
        if (!customName.isEmpty()) output.putString("custom_name", customName);
        if (!isEmpty()) {
            output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(items));
        }
        if (!migrationOverflow.isEmpty()) {
            output.store("migration_overflow", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(migrationOverflow));
        }
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cachedNetwork = null;
        owner = null;
        securityMode = SecurityMode.PUBLIC;
        redstoneMode = RedstoneMode.IGNORED;
        customName = "";
        energy.set(0);
        items.replaceAll(stack -> ItemStack.EMPTY);
        migrationOverflow.clear();
        int schema = input.getIntOr("schema", -1);
        if (schema != 1 && schema != 2) return;
        input.getString("owner").ifPresent(raw -> {
            try { owner = UUID.fromString(raw); } catch (IllegalArgumentException ignored) { owner = null; }
        });
        securityMode = input.getString("security").filter(SecurityMode::validName).map(SecurityMode::load).orElse(SecurityMode.PUBLIC);
        redstoneMode = RedstoneMode.loadSimple(input.getStringOr("redstone_mode", "ignored"));
        customName = CustomNames.sanitize(input.getStringOr("custom_name", ""));
        input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(loaded -> {
            if (schema == 1) {
                ItemStack legacyBooster = loaded.size() > 0 ? loaded.get(0) : ItemStack.EMPTY;
                ItemStack legacyTerminal = loaded.size() > 1 ? loaded.get(1) : ItemStack.EMPTY;
                ItemStack legacyDim = loaded.size() > 2 ? loaded.get(2) : ItemStack.EMPTY;
                ItemStack legacyAnchor = loaded.size() > 3 ? loaded.get(3) : ItemStack.EMPTY;
                if (!legacyDim.isEmpty()) {
                    items.set(SLOT_RANGE, legacyDim);
                    if (!legacyBooster.isEmpty()) migrationOverflow.add(legacyBooster.copy());
                } else {
                    items.set(SLOT_RANGE, legacyBooster);
                }
                items.set(SLOT_TERMINAL, legacyTerminal);
                items.set(SLOT_ANCHOR, legacyAnchor);
            } else {
                for (int i = 0; i < Math.min(items.size(), loaded.size()); i++) {
                    items.set(i, loaded.get(i));
                }
            }
        });
        input.read("migration_overflow", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(overflow -> {
            for (ItemStack extra : overflow) {
                if (!extra.isEmpty()) migrationOverflow.add(extra.copy());
            }
        });
        energy.set(Math.clamp(input.getIntOr("energy", 0), 0, CAPACITY));
        powered = redstoneAllows() && energy() >= effectiveDrain();
        if (level instanceof ServerLevel) {
            touchTopology();
            syncChunkAnchor();
        }
    }

    @Override public Component getDisplayName() {
        return CustomNames.displayName(customName, "block.rematter.wireless_access_point");
    }

    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return canAccess(player) ? new WirelessAccessPointMenu(id, inventory, this) : null;
    }

    public static boolean validSlotItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (slot == SLOT_RANGE) {
            return stack.is(ModContent.WIRELESS_BOOSTER.get()) || stack.is(ModContent.DIMENSIONAL_CARD.get());
        }
        if (slot == SLOT_TERMINAL) {
            return stack.getItem() instanceof WirelessMatterTerminalItem || EnergyCubeBlockEntity.chargeable(stack);
        }
        if (slot == SLOT_ANCHOR) return stack.is(ModContent.ANCHOR_UPGRADE.get());
        return false;
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override public ItemStack removeItem(int slot, int amount) {
        if (Transaction.getCurrentOpenedTransaction() != null) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            if (slot == SLOT_ANCHOR) syncChunkAnchor();
            setChanged();
        }
        return removed;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (Transaction.getCurrentOpenedTransaction() != null) return ItemStack.EMPTY;
        ItemStack out = ContainerHelper.takeItem(items, slot);
        if (slot == SLOT_ANCHOR && !out.isEmpty()) syncChunkAnchor();
        return out;
    }

    @Override public void setItem(int slot, ItemStack stack) {
        if (Transaction.getCurrentOpenedTransaction() != null) return;
        if (!stack.isEmpty() && !validSlotItem(slot, stack)) return;
        int limit = slot == SLOT_RANGE && stack.is(ModContent.WIRELESS_BOOSTER.get()) ? MAX_BOOSTERS : 1;
        if (stack.getCount() > limit) stack = stack.copyWithCount(limit);
        items.set(slot, stack);
        bindDockedTerminal();
        if (slot == SLOT_ANCHOR) syncChunkAnchor();
        setChanged();
    }

    @Override public void setItem(int slot, ItemStack stack, boolean insideTransaction) {
        if (insideTransaction) items.set(slot, stack);
        else setItem(slot, stack);
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return validSlotItem(slot, stack);
    }

    @Override public int getMaxStackSize() { return MAX_BOOSTERS; }

    @Override public void clearContent() {
        if (Transaction.getCurrentOpenedTransaction() == null) {
            items.replaceAll(stack -> ItemStack.EMPTY);
            syncChunkAnchor();
            setChanged();
        }
    }

    @Override public boolean stillValid(Player player) {
        return canAccess(player) && Container.stillValidBlockEntity(this, player);
    }
}
