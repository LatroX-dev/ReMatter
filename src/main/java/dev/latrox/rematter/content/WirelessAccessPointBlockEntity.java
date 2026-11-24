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

/** Powered Matter Network transceiver with AE2-style Wireless Booster slot and Terminal Link/Charge dock. */
public final class WirelessAccessPointBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int CAPACITY = 64_000;
    public static final int MAX_INSERT = 8_192;
    public static final int STANDBY_DRAIN = 2;
    public static final int BASE_RANGE = 128;
    public static final int RANGE_PER_BOOSTER = 24;
    public static final int MAX_BOOSTERS = 16;
    public static final int MAX_RANGE = BASE_RANGE + MAX_BOOSTERS * RANGE_PER_BOOSTER;
    public static final int SLOT_BOOSTER = 0;
    public static final int SLOT_TERMINAL = 1;

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
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
    private @Nullable MachineMatterNetwork cachedNetwork;

    public WirelessAccessPointBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.WIRELESS_ACCESS_POINT_ENTITY.get(), pos, state);
    }

    public int energy() { return (int) energy.getAmountAsLong(); }
    public @Nullable UUID owner() { return owner; }

    public int boosterCount() {
        ItemStack stack = items.get(SLOT_BOOSTER);
        return stack.is(ModContent.WIRELESS_BOOSTER.get()) ? Math.clamp(stack.getCount(), 0, MAX_BOOSTERS) : 0;
    }

    public int effectiveRange() {
        return BASE_RANGE + boosterCount() * RANGE_PER_BOOSTER;
    }

    public int effectiveDrain() {
        int boosters = boosterCount();
        return STANDBY_DRAIN + boosters * 2 + (boosters * boosters) / 2;
    }

    public boolean withinRange(Player player) {
        int range = effectiveRange();
        return player.position().distanceToSqr(Vec3.atCenterOf(worldPosition)) <= (double) range * range;
    }

    public boolean powered() { return networkLive() && (powered || energy() >= effectiveDrain()); }

    public boolean matterReady() {
        return networkLive() && matterNetwork(false).ready();
    }

    boolean networkLive() {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || isRemoved() || owner == null) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this;
    }

    List<Direction> connectionSides() {
        return List.of(Direction.DOWN, getBlockState().getValue(WirelessAccessPointBlock.FACING).getOpposite());
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
        if (securityMode == SecurityMode.PUBLIC) {
            securityMode = MachineAccess.placementDefault(level, placer);
        }
        assignOwner(placer instanceof Player player && !(player instanceof FakePlayer) ? player.getUUID() : null);
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
        bindDockedTerminal();
        int drain = effectiveDrain();
        boolean online = energy() >= drain;
        if (online) {
            try (var tx = Transaction.openRoot()) {
                if (energy.extract(drain, tx) == drain) tx.commit();
                else online = false;
            }
        }
        powered = online;
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
        return out;
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {}

    @Override public void setRemoved() {
        cachedNetwork = null;
        if (level instanceof ServerLevel server) ChunkRevisions.touchLoaded(server, worldPosition);
        super.setRemoved();
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("schema", 1);
        output.putInt("energy", energy());
        if (owner != null) output.putString("owner", owner.toString());
        if (securityMode != SecurityMode.PUBLIC) output.putString("security", securityMode.getSerializedName());
        if (!isEmpty()) {
            output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(items));
        }
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cachedNetwork = null;
        owner = null;
        securityMode = SecurityMode.PUBLIC;
        energy.set(0);
        items.replaceAll(stack -> ItemStack.EMPTY);
        if (input.getIntOr("schema", -1) != 1) return;
        input.getString("owner").ifPresent(raw -> {
            try { owner = UUID.fromString(raw); } catch (IllegalArgumentException ignored) { owner = null; }
        });
        securityMode = input.getString("security").filter(SecurityMode::validName).map(SecurityMode::load).orElse(SecurityMode.PUBLIC);
        input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(loaded -> {
            for (int i = 0; i < Math.min(items.size(), loaded.size()); i++) {
                items.set(i, loaded.get(i));
            }
        });
        energy.set(Math.clamp(input.getIntOr("energy", 0), 0, CAPACITY));
        powered = energy() >= effectiveDrain();
        if (level instanceof ServerLevel) touchTopology();
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.rematter.wireless_access_point");
    }

    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return canAccess(player) ? new WirelessAccessPointMenu(id, inventory, this) : null;
    }

    public static boolean validSlotItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (slot == SLOT_BOOSTER) return stack.is(ModContent.WIRELESS_BOOSTER.get());
        if (slot == SLOT_TERMINAL) {
            return stack.getItem() instanceof WirelessMatterTerminalItem || EnergyCubeBlockEntity.chargeable(stack);
        }
        return false;
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override public ItemStack removeItem(int slot, int amount) {
        if (Transaction.getCurrentOpenedTransaction() != null) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        return Transaction.getCurrentOpenedTransaction() != null ? ItemStack.EMPTY : ContainerHelper.takeItem(items, slot);
    }

    @Override public void setItem(int slot, ItemStack stack) {
        if (Transaction.getCurrentOpenedTransaction() != null) return;
        if (!stack.isEmpty() && !validSlotItem(slot, stack)) return;
        int limit = slot == SLOT_BOOSTER ? MAX_BOOSTERS : 1;
        if (stack.getCount() > limit) stack = stack.copyWithCount(limit);
        items.set(slot, stack);
        bindDockedTerminal();
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
            setChanged();
        }
    }

    @Override public boolean stillValid(Player player) {
        return canAccess(player) && Container.stillValidBlockEntity(this, player);
    }
}
