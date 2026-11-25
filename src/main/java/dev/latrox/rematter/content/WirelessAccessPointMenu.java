package dev.latrox.rematter.content;

import dev.latrox.rematter.network.WirelessAccessPointPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/** Menu for Wireless Access Point: Range Boosters, Dimensional Card, Anchor Upgrade, Terminal dock, custom label & telemetry. */
public final class WirelessAccessPointMenu extends AbstractContainerMenu {
    public static final int CYCLE_SECURITY = 0;
    public static final int CYCLE_SECURITY_BACK = 1;
    public static final int CYCLE_REDSTONE = 2;
    public static final int CYCLE_REDSTONE_BACK = 3;
    private final Container inventory;
    private final @Nullable WirelessAccessPointBlockEntity accessPoint;
    private final Player viewer;
    private final BlockPos position;
    private final DataSlot energyLow = DataSlot.standalone();
    private final DataSlot energyHigh = DataSlot.standalone();
    private final DataSlot boosterCount = DataSlot.standalone();
    private final DataSlot statusFlags = DataSlot.standalone(); // bit0=online, bit1=netReady, bit2=dimensional, bit3=anchorInstalled, bit4=anchorActive, bit5=redstonePaused
    private final DataSlot securityMode = DataSlot.standalone();
    private final DataSlot canManageSecurity = DataSlot.standalone();
    private final DataSlot redstoneMode = DataSlot.standalone();
    private String customName = "";
    private int anchorForced;
    private int anchorTotal;
    private int anchorMax = 32;
    private String anchorReason = "idle";
    private boolean stateSent;
    private boolean anchorTab;
    private boolean deviceTab;

    public void setAnchorTab(boolean value) { anchorTab = value; }
    public void setDeviceTab(boolean value) { deviceTab = value; }
    public void setClientCustomName(String value) { customName = CustomNames.sanitize(value); }

    public WirelessAccessPointMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(id, playerInventory, new SimpleContainer(WirelessAccessPointBlockEntity.INVENTORY_SIZE), null, buf.readBlockPos());
        if (buf.readableBytes() > 0) {
            this.customName = CustomNames.sanitize(buf.readUtf(CustomNames.MAX_LENGTH));
        }
    }

    public WirelessAccessPointMenu(int id, Inventory playerInventory, WirelessAccessPointBlockEntity accessPoint) {
        this(id, playerInventory, accessPoint, accessPoint, accessPoint.getBlockPos());
    }

    private WirelessAccessPointMenu(int id, Inventory playerInventory, Container inventory,
                                    @Nullable WirelessAccessPointBlockEntity accessPoint, BlockPos position) {
        super(ModContent.WIRELESS_ACCESS_POINT_MENU.get(), id);
        this.inventory = inventory;
        this.accessPoint = accessPoint;
        this.viewer = playerInventory.player;
        this.position = position;
        if (accessPoint != null) {
            this.customName = accessPoint.customName();
        }

        addDataSlot(energyLow);
        addDataSlot(energyHigh);
        addDataSlot(boosterCount);
        addDataSlot(statusFlags);
        securityMode.set(accessPoint != null ? accessPoint.securityMode().ordinal() : SecurityMode.PUBLIC.ordinal());
        addDataSlot(securityMode);
        canManageSecurity.set(accessPoint != null && accessPoint.canManageSecurity(viewer) ? 1 : 0);
        addDataSlot(canManageSecurity);
        redstoneMode.set(accessPoint != null ? accessPoint.redstoneMode().ordinal() : RedstoneMode.IGNORED.ordinal());
        addDataSlot(redstoneMode);

        addSlot(new Slot(inventory, WirelessAccessPointBlockEntity.SLOT_RANGE, 64, 80) {
            @Override public boolean mayPlace(ItemStack stack) {
                return WirelessAccessPointBlockEntity.validSlotItem(WirelessAccessPointBlockEntity.SLOT_RANGE, stack);
            }
            @Override public boolean mayPickup(Player player) {
                return accessPoint == null || accessPoint.canAccess(player);
            }
            @Override public int getMaxStackSize() { return WirelessAccessPointBlockEntity.MAX_BOOSTERS; }
            @Override public int getMaxStackSize(ItemStack stack) {
                return stack.is(ModContent.DIMENSIONAL_CARD.get()) ? 1 : WirelessAccessPointBlockEntity.MAX_BOOSTERS;
            }
            @Override public boolean isActive() { return !anchorTab && !deviceTab; }
        });
        addSlot(new Slot(inventory, WirelessAccessPointBlockEntity.SLOT_TERMINAL, 136, 80) {
            @Override public boolean mayPlace(ItemStack stack) {
                return WirelessAccessPointBlockEntity.validSlotItem(WirelessAccessPointBlockEntity.SLOT_TERMINAL, stack);
            }
            @Override public boolean mayPickup(Player player) {
                return accessPoint == null || accessPoint.canAccess(player);
            }
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean isActive() { return !anchorTab && !deviceTab; }
        });
        addSlot(new Slot(inventory, WirelessAccessPointBlockEntity.SLOT_ANCHOR, 186, 25) {
            @Override public boolean mayPlace(ItemStack stack) {
                return WirelessAccessPointBlockEntity.validSlotItem(WirelessAccessPointBlockEntity.SLOT_ANCHOR, stack);
            }
            @Override public boolean mayPickup(Player player) {
                return accessPoint == null || accessPoint.canAccess(player);
            }
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean isActive() { return anchorTab && !deviceTab; }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 18 + col * 18, 135 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 18 + col * 18, 193));
        }
    }

    public BlockPos position() { return position; }
    public String customName() { return accessPoint != null ? accessPoint.customName() : customName; }
    public int anchorForced() { return anchorForced; }
    public int anchorTotal() { return anchorTotal; }
    public int anchorMax() { return anchorMax; }
    public String anchorReason() { return anchorReason; }

    public int energy() {
        if (accessPoint != null) return accessPoint.energy();
        return ((energyHigh.get() & 0xFFFF) << 16) | (energyLow.get() & 0xFFFF);
    }

    public int capacity() { return WirelessAccessPointBlockEntity.CAPACITY; }

    public int boosters() {
        return accessPoint != null ? accessPoint.boosterCount()
                : Math.clamp(boosterCount.get(), 0, WirelessAccessPointBlockEntity.MAX_BOOSTERS);
    }

    public boolean hasDimensionalCard() {
        return accessPoint != null ? accessPoint.hasDimensionalCard() : (statusFlags.get() & 4) != 0;
    }

    public boolean hasAnchorUpgrade() {
        return accessPoint != null ? accessPoint.hasAnchorUpgrade() : (statusFlags.get() & 8) != 0;
    }

    public boolean anchorActive() {
        return accessPoint != null ? accessPoint.anchorStatus().active() : (statusFlags.get() & 16) != 0;
    }

    public int effectiveRange() {
        return WirelessAccessPointBlockEntity.BASE_RANGE + boosters() * WirelessAccessPointBlockEntity.RANGE_PER_BOOSTER;
    }

    public int effectiveDrain() {
        return WirelessAccessPointBlockEntity.computeDrain(boosters(), hasDimensionalCard(), hasAnchorUpgrade());
    }

    public boolean online() {
        return accessPoint != null ? accessPoint.powered() : (statusFlags.get() & 1) != 0;
    }

    public boolean networkReady() {
        return accessPoint != null ? accessPoint.matterReady() : (statusFlags.get() & 2) != 0;
    }

    public SecurityMode securityMode() {
        return accessPoint != null ? accessPoint.securityMode() : SecurityMode.byIndex(securityMode.get());
    }

    public boolean canManageSecurity() {
        return accessPoint != null ? accessPoint.canManageSecurity(viewer) : canManageSecurity.get() != 0;
    }

    public RedstoneMode redstoneMode() {
        return accessPoint != null ? accessPoint.redstoneMode() : RedstoneMode.byIndex(redstoneMode.get());
    }

    public boolean redstonePaused() {
        return accessPoint != null ? accessPoint.redstonePaused() : (statusFlags.get() & 32) != 0;
    }

    public void renameFromClient(ServerPlayer player, String nextName) {
        if (accessPoint == null || player.containerMenu != this || player.isSpectator() || !stillValid(player) || !accessPoint.canAccess(player)) return;
        accessPoint.setCustomName(nextName);
        broadcastChanges();
    }

    public void sendChunkMap(ServerPlayer sp) {
        if (accessPoint == null || !(accessPoint.getLevel() instanceof net.minecraft.server.level.ServerLevel server)) return;
        var map = ChunkMapSnapshot.capture(server, accessPoint.getBlockPos(), accessPoint.owner(),
                ChunkAnchorManager.square(accessPoint.getBlockPos(), 0), sp);
        PacketDistributor.sendToPlayer(sp, new dev.latrox.rematter.network.ChunkMapPayload.Sync(containerId, map));
    }

    public void acceptState(WirelessAccessPointPayload.State state) {
        this.customName = state.customName();
        this.anchorForced = state.anchorForced();
        this.anchorTotal = state.anchorTotal();
        this.anchorMax = state.anchorMax();
        this.anchorReason = state.anchorReason();
    }

    @Override
    public boolean clickMenuButton(Player player, int action) {
        if (accessPoint == null || player.containerMenu != this || player.isSpectator() || !stillValid(player)) return false;
        if (action == CYCLE_SECURITY || action == CYCLE_SECURITY_BACK) {
            boolean changed = accessPoint.cycleSecurityMode(player, action == CYCLE_SECURITY);
            if (changed) broadcastChanges();
            return changed;
        }
        if (action == CYCLE_REDSTONE || action == CYCLE_REDSTONE_BACK) {
            boolean changed = accessPoint.cycleRedstoneMode(player, action == CYCLE_REDSTONE);
            if (changed) broadcastChanges();
            return changed;
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        if (accessPoint != null) {
            int stored = accessPoint.energy();
            energyLow.set(stored & 0xFFFF);
            energyHigh.set((stored >>> 16) & 0xFFFF);
            boosterCount.set(accessPoint.boosterCount());
            var ast = accessPoint.anchorStatus();
            int flags = (accessPoint.powered() ? 1 : 0)
                    | (accessPoint.matterReady() ? 2 : 0)
                    | (accessPoint.hasDimensionalCard() ? 4 : 0)
                    | (accessPoint.hasAnchorUpgrade() ? 8 : 0)
                    | (ast.active() ? 16 : 0)
                    | (accessPoint.redstonePaused() ? 32 : 0);
            statusFlags.set(flags);
            securityMode.set(accessPoint.securityMode().ordinal());
            canManageSecurity.set(accessPoint.canManageSecurity(viewer) ? 1 : 0);
            redstoneMode.set(accessPoint.redstoneMode().ordinal());
            if (viewer instanceof ServerPlayer sp) {
                String n = accessPoint.customName();
                if (!stateSent || !n.equals(customName) || anchorForced != ast.forcedChunks()
                        || anchorTotal != ast.ownerTotalChunks() || anchorMax != ast.maxChunks()
                        || !anchorReason.equals(ast.reason())) {
                    stateSent = true;
                    customName = n;
                    anchorForced = ast.forcedChunks();
                    anchorTotal = ast.ownerTotalChunks();
                    anchorMax = ast.maxChunks();
                    anchorReason = ast.reason();
                    PacketDistributor.sendToPlayer(sp, new WirelessAccessPointPayload.State(
                            containerId, n, anchorForced, anchorTotal, anchorMax, anchorReason));
                }
            }
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return accessPoint == null || accessPoint.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || accessPoint != null && (!accessPoint.canAccess(player) || !stillValid(player))) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int machineSlots = WirelessAccessPointBlockEntity.INVENTORY_SIZE;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (WirelessAccessPointBlockEntity.validSlotItem(WirelessAccessPointBlockEntity.SLOT_RANGE, stack)) {
            if (!moveItemStackTo(stack, WirelessAccessPointBlockEntity.SLOT_RANGE, WirelessAccessPointBlockEntity.SLOT_RANGE + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (WirelessAccessPointBlockEntity.validSlotItem(WirelessAccessPointBlockEntity.SLOT_TERMINAL, stack)) {
            if (!moveItemStackTo(stack, WirelessAccessPointBlockEntity.SLOT_TERMINAL, WirelessAccessPointBlockEntity.SLOT_TERMINAL + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (WirelessAccessPointBlockEntity.validSlotItem(WirelessAccessPointBlockEntity.SLOT_ANCHOR, stack)) {
            if (!moveItemStackTo(stack, WirelessAccessPointBlockEntity.SLOT_ANCHOR, WirelessAccessPointBlockEntity.SLOT_ANCHOR + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }
}
