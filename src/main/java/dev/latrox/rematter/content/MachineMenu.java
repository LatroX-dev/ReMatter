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
    public enum View { MAIN, UPGRADES, CONFIGURATION }
    private final Container inventory;
    private final MachineBlockEntity machine;
    private final Player player;
    private MachineSnapshot snapshot = MachineSnapshot.EMPTY;
    private long nextSnapshot;
    private View view = View.MAIN;

    public MachineMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(4), null); }
    public MachineMenu(int id, Inventory inventory, MachineBlockEntity machine) { this(id, inventory, machine, machine); }
    private MachineMenu(int id, Inventory playerInventory, Container inventory, MachineBlockEntity machine) {
        super(ModContent.MACHINE_MENU.get(), id);
        this.inventory = inventory;
        this.machine = machine;
        this.player = playerInventory.player;
        // Shared fixed client/server layout: role changes never replace slots or alter their inventory identity.
        addSlot(new Slot(inventory, 0, 44, 62) {
            @Override public boolean mayPlace(ItemStack stack) {
                return (!diskRole() || stack.getItem() instanceof MatterCellItem && stack.getCount() == 1)
                        && inventory.canPlaceItem(0, stack);
            }
            @Override public int getMaxStackSize() { return diskRole() ? 1 : super.getMaxStackSize(); }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player) && (!diskRole() || machine.canRemoveCell()); }
            @Override public boolean isActive() { return view == View.MAIN && (diskRole() || inputRole()) || view == View.CONFIGURATION && (diskRole() || hasItem()); }
        });
        addSlot(new Slot(inventory, 1, 188, 62) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player); }
            @Override public boolean isActive() { return view == View.MAIN && (PreparationTier.isPreparation(kind()) || kind() == MachineKind.SYNTHESIZER && (!snapshot.preview().outputKind().equals("fluid") || hasItem())) || view == View.CONFIGURATION && hasItem(); }
        });
        addSlot(new Slot(inventory, 2, 44, 100) {
            @Override public boolean mayPlace(ItemStack stack) { return inventory.canPlaceItem(2, stack); }
            @Override public int getMaxStackSize() { return 4; }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player); }
            @Override public boolean isActive() { return view == View.UPGRADES && supportsUpgrades() || view == View.CONFIGURATION && !supportsUpgrades() && hasItem(); }
        });
        Container cellInventory = inventory.getContainerSize() > 3 ? inventory : new SimpleContainer(1);
        int cellIndex = inventory.getContainerSize() > 3 ? 3 : 0;
        addSlot(new Slot(cellInventory, cellIndex, 80, 62) {
            @Override public boolean mayPlace(ItemStack stack) {
                if(PreparationTier.isPreparation(kind()))return cellInventory.canPlaceItem(cellIndex,stack);
                return false; // Former machine cells can be recovered, but new cells belong in storage.
            }
            @Override public int getMaxStackSize() { return PreparationTier.isPreparation(kind())?64:1; }
            @Override public boolean mayPickup(Player player) { return machine == null || machine.canAccess(player) && machine.canRemoveCell(); }
            @Override public boolean isActive() { return view == View.MAIN && PreparationTier.isPreparation(kind()) || view == View.CONFIGURATION && hasItem(); }
        });
        addStandardInventorySlots(playerInventory, 47, 158);
    }
    public MachineKind kind() { return machine == null ? MachineKind.values()[snapshot.kind()] : machine.kind(); }
    public boolean inputRole() { return kind() == MachineKind.ANALYZER || kind() == MachineKind.DECOMPOSER || PreparationTier.isPreparation(kind()); }
    public boolean diskRole() { return kind().isStorage(); }
    public int diskSlot() { return diskRole() ? 0 : -1; }
    public boolean supportsUpgrades() { return kind().processes() && !PreparationTier.manual(kind()); }
    public View view() { return view; }
    public void setView(View value) { view = java.util.Objects.requireNonNull(value); }
    public void setSettingsOpen(boolean value) { setView(value ? View.CONFIGURATION : View.MAIN); }
    public boolean settingsOpen() { return view != View.MAIN; }
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
            case 7 -> { if (!machine.migrateLegacyStorageToCell()) return false; }
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
            int target = diskSlot();
            if (target < 0 || !moveItemStackTo(original, target, target + 1, false)) return ItemStack.EMPTY;
        } else if (original.is(ModContent.SPEED_UPGRADE.get()) || original.is(ModContent.EFFICIENCY_UPGRADE.get())) {
            if (!supportsUpgrades() || !slots.get(2).mayPlace(original)
                    || !moveItemStackTo(original, 2, 3, false)) return ItemStack.EMPTY;
        } else if (PreparationTier.isPreparation(kind())) {
            if (!(slots.get(0).mayPlace(original) && moveItemStackTo(original, 0, 1, false))
                    && !(slots.get(3).mayPlace(original) && moveItemStackTo(original, 3, 4, false))) return ItemStack.EMPTY;
        } else if (!slots.get(0).mayPlace(original) || !moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (original.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, original);
        return result;
    }
    @Override public boolean stillValid(Player player) { return machine == null || machine.stillValid(player); }
}
