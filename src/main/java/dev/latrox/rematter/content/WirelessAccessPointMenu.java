package dev.latrox.rematter.content;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** AE2-style menu for Wireless Access Point: Range Booster slot, Terminal Link/Charge dock, and live telemetry. */
public final class WirelessAccessPointMenu extends AbstractContainerMenu {
    public static final int CYCLE_SECURITY = 0;
    public static final int CYCLE_SECURITY_BACK = 1;
    private final Container inventory;
    private final @Nullable WirelessAccessPointBlockEntity accessPoint;
    private final Player viewer;
    private final BlockPos position;
    private final DataSlot energyLow = DataSlot.standalone();
    private final DataSlot energyHigh = DataSlot.standalone();
    private final DataSlot boosterCount = DataSlot.standalone();
    private final DataSlot statusFlags = DataSlot.standalone();
    private final DataSlot securityMode = DataSlot.standalone();
    private final DataSlot canManageSecurity = DataSlot.standalone();

    public WirelessAccessPointMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(id, playerInventory, new SimpleContainer(2), null, buf.readBlockPos());
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

        addDataSlot(energyLow);
        addDataSlot(energyHigh);
        addDataSlot(boosterCount);
        addDataSlot(statusFlags);
        securityMode.set(accessPoint != null ? accessPoint.securityMode().ordinal() : SecurityMode.PUBLIC.ordinal());
        addDataSlot(securityMode);
        canManageSecurity.set(accessPoint != null && accessPoint.canManageSecurity(viewer) ? 1 : 0);
        addDataSlot(canManageSecurity);

        addSlot(new Slot(inventory, WirelessAccessPointBlockEntity.SLOT_BOOSTER, 72, 86) {
            @Override public boolean mayPlace(ItemStack stack) {
                return WirelessAccessPointBlockEntity.validSlotItem(WirelessAccessPointBlockEntity.SLOT_BOOSTER, stack);
            }
            @Override public boolean mayPickup(Player player) {
                return accessPoint == null || accessPoint.canAccess(player);
            }
            @Override public int getMaxStackSize() {
                return WirelessAccessPointBlockEntity.MAX_BOOSTERS;
            }
        });
        addSlot(new Slot(inventory, WirelessAccessPointBlockEntity.SLOT_TERMINAL, 128, 86) {
            @Override public boolean mayPlace(ItemStack stack) {
                return WirelessAccessPointBlockEntity.validSlotItem(WirelessAccessPointBlockEntity.SLOT_TERMINAL, stack);
            }
            @Override public boolean mayPickup(Player player) {
                return accessPoint == null || accessPoint.canAccess(player);
            }
            @Override public int getMaxStackSize() {
                return 1;
            }
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

    public int energy() {
        if (accessPoint != null) return accessPoint.energy();
        return ((energyHigh.get() & 0xFFFF) << 16) | (energyLow.get() & 0xFFFF);
    }

    public int capacity() { return WirelessAccessPointBlockEntity.CAPACITY; }

    public int boosters() {
        return accessPoint != null ? accessPoint.boosterCount()
                : Math.clamp(boosterCount.get(), 0, WirelessAccessPointBlockEntity.MAX_BOOSTERS);
    }

    public int effectiveRange() {
        return WirelessAccessPointBlockEntity.BASE_RANGE + boosters() * WirelessAccessPointBlockEntity.RANGE_PER_BOOSTER;
    }

    public int effectiveDrain() {
        int b = boosters();
        return WirelessAccessPointBlockEntity.STANDBY_DRAIN + b * 2 + (b * b) / 2;
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

    @Override
    public boolean clickMenuButton(Player player, int action) {
        if (accessPoint == null || player.containerMenu != this || player.isSpectator() || !stillValid(player)) return false;
        if (action != CYCLE_SECURITY && action != CYCLE_SECURITY_BACK) return false;
        boolean changed = accessPoint.cycleSecurityMode(player, action == CYCLE_SECURITY);
        if (changed) broadcastChanges();
        return changed;
    }

    @Override
    public void broadcastChanges() {
        if (accessPoint != null) {
            int stored = accessPoint.energy();
            energyLow.set(stored & 0xFFFF);
            energyHigh.set((stored >>> 16) & 0xFFFF);
            boosterCount.set(accessPoint.boosterCount());
            int flags = (accessPoint.powered() ? 1 : 0) | (accessPoint.matterReady() ? 2 : 0);
            statusFlags.set(flags);
            securityMode.set(accessPoint.securityMode().ordinal());
            canManageSecurity.set(accessPoint.canManageSecurity(viewer) ? 1 : 0);
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
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (WirelessAccessPointBlockEntity.validSlotItem(WirelessAccessPointBlockEntity.SLOT_BOOSTER, stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (WirelessAccessPointBlockEntity.validSlotItem(WirelessAccessPointBlockEntity.SLOT_TERMINAL, stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }
}
