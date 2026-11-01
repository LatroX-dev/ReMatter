package dev.latrox.rematter.content;

import dev.latrox.rematter.content.preparation.PreparationTier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MachineMenu extends AbstractContainerMenu {
    public static final int FIRST_PLAYER_SLOT = 4;
    private final Container inventory;
    private final MachineBlockEntity machine;
    private final Player player;
    private MachineSnapshot snapshot = MachineSnapshot.EMPTY;
    private long nextSnapshot;
    private boolean settingsOpen;

    public MachineMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(4), null); }
    public MachineMenu(int id, Inventory inventory, MachineBlockEntity machine) { this(id, inventory, machine, machine); }
    private MachineMenu(int id, Inventory playerInventory, Container inventory, MachineBlockEntity machine) {
        super(ModContent.MACHINE_MENU.get(), id);
        this.inventory = inventory;
        this.machine = machine;
        this.player = playerInventory.player;
        addSlot(new Slot(inventory, 0, 18, 70) {
            @Override public boolean mayPlace(ItemStack stack) {
                return (kind() != MachineKind.DRIVE || stack.getItem() instanceof MatterCellItem && stack.getCount() == 1)
                        && inventory.canPlaceItem(0, stack);
            }
            @Override public int getMaxStackSize() { return kind() == MachineKind.DRIVE ? 1 : super.getMaxStackSize(); }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player) && (kind() != MachineKind.DRIVE || machine.canRemoveCell()); }
            @Override public boolean isActive() { return kind() == MachineKind.DRIVE || inputRole() && !settingsOpen || settingsOpen && hasItem(); }
        });
        addSlot(new Slot(inventory, 1, 80, 70) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean isActive() { return !settingsOpen && (PreparationTier.isPreparation(kind()) || kind() == MachineKind.SYNTHESIZER && (!snapshot.preview().outputKind().equals("fluid") || hasItem())) || settingsOpen && hasItem(); }
        });
        addSlot(new Slot(inventory, 2, 50, 108) {
            @Override public boolean mayPlace(ItemStack stack) { return inventory.canPlaceItem(2, stack); }
            @Override public int getMaxStackSize() { return 4; }
            @Override public boolean isActive() { return settingsOpen && (kind().processes() && !PreparationTier.manual(kind()) || hasItem()); }
        });
        Container cellInventory = inventory.getContainerSize() > 3 ? inventory : new SimpleContainer(1);
        int cellIndex = inventory.getContainerSize() > 3 ? 3 : 0;
        addSlot(new Slot(cellInventory, cellIndex, 16, 180) {
            @Override public boolean mayPlace(ItemStack stack) {
                if(PreparationTier.isPreparation(kind()))return cellInventory.canPlaceItem(cellIndex,stack);
                return MachineCellAccess.hasLocalSlot(kind()) && stack.getItem() instanceof MatterCellItem
                        && stack.getCount() == 1 && cellInventory.canPlaceItem(cellIndex, stack);
            }
            @Override public int getMaxStackSize() { return PreparationTier.isPreparation(kind())?64:1; }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player) && machine.canRemoveCell(); }
            @Override public boolean isActive() { return MachineCellAccess.hasLocalSlot(kind()) || PreparationTier.isPreparation(kind()); }
        });
        addStandardInventorySlots(playerInventory, 149, 160);
    }
    public MachineKind kind() { return machine == null ? MachineKind.values()[snapshot.kind()] : machine.kind(); }
    public boolean inputRole() { return kind() == MachineKind.ANALYZER || kind() == MachineKind.DECOMPOSER || PreparationTier.isPreparation(kind()); }
    public void setSettingsOpen(boolean value) { settingsOpen = value; }
    public boolean settingsOpen() { return settingsOpen; }
    public MachineSnapshot snapshot() { return snapshot; }
    public void acceptSnapshot(MachineSnapshot value) { snapshot = value; }
    public boolean selectRecipe(Player player, String id) {
        if (machine == null || player.containerMenu != this || !stillValid(player)
                || id.length() > MachineSnapshot.MAX_CHOICE_ID_LENGTH || !machine.selectRecipe(id)) return false;
        nextSnapshot = 0;
        broadcastChanges();
        return true;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (machine == null || !(player instanceof ServerPlayer serverPlayer)) return;
        long tick = player.level().getGameTime();
        if (tick < nextSnapshot) return;
        nextSnapshot = tick + 5;
        MachineSnapshot next = machine.snapshot(containerId);
        if (!snapshot.equals(next)) {
            snapshot = next;
            PacketDistributor.sendToPlayer(serverPlayer, next);
        }
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (machine == null || player.containerMenu != this || !stillValid(player)) return false;
        switch (id) {
            case 0 -> machine.select(-1);
            case 1 -> machine.select(1);
            case 2 -> machine.toggleRunning();
            case 3 -> machine.toggleAutomation();
            case 4 -> machine.requestBatch();
            case 5 -> machine.toggleRepeat();
            case 6 -> { if(!machine.crank(player))return false; }
            default -> { return false; }
        }
        nextSnapshot = 0;
        broadcastChanges();
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        if(index==MachineCellAccess.slot(kind())&&machine!=null&&!machine.prepareCellRemoval())return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack result = original.copy();
        if (index < FIRST_PLAYER_SLOT) {
            if (!moveItemStackTo(original, FIRST_PLAYER_SLOT, slots.size(), true)) return ItemStack.EMPTY;
        } else if (original.getItem() instanceof MatterCellItem) {
            int target = MachineCellAccess.slot(kind());
            if (target < 0 || !moveItemStackTo(original, target, target + 1, false)) return ItemStack.EMPTY;
        } else if (original.is(ModContent.SPEED_UPGRADE.get()) || original.is(ModContent.EFFICIENCY_UPGRADE.get())) {
            if (!moveItemStackTo(original, 2, 3, false)) return ItemStack.EMPTY;
        } else if (PreparationTier.isPreparation(kind())) {
            if(!moveItemStackTo(original,0,1,false)&&!moveItemStackTo(original,3,4,false))return ItemStack.EMPTY;
        } else if (!moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (original.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, original);
        return result;
    }
    @Override public boolean stillValid(Player player) { return machine == null || machine.stillValid(player); }
}
