package dev.latrox.rematter.content;

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
    private final Container inventory;
    private final MachineBlockEntity machine;
    private final Player player;
    private MachineSnapshot snapshot = MachineSnapshot.EMPTY;
    private long nextSnapshot;

    public MachineMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(3), null); }
    public MachineMenu(int id, Inventory inventory, MachineBlockEntity machine) { this(id, inventory, machine, machine); }
    private MachineMenu(int id, Inventory playerInventory, Container inventory, MachineBlockEntity machine) {
        super(ModContent.MACHINE_MENU.get(), id);
        this.inventory = inventory;
        this.machine = machine;
        this.player = playerInventory.player;
        addSlot(new Slot(inventory, 0, 17, 66) {
            @Override public boolean mayPlace(ItemStack stack) { return inventory.canPlaceItem(0, stack); }
        });
        addSlot(new Slot(inventory, 1, 71, 66) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new Slot(inventory, 2, 44, 93) {
            @Override public boolean mayPlace(ItemStack stack) { return inventory.canPlaceItem(2, stack); }
            @Override public int getMaxStackSize() { return 4; }
        });
        addStandardInventorySlots(playerInventory, 65, 174);
    }
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
            default -> { return false; }
        }
        nextSnapshot = 0;
        broadcastChanges();
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack result = original.copy();
        if (index < 3) {
            if (!moveItemStackTo(original, 3, slots.size(), true)) return ItemStack.EMPTY;
        } else if (original.is(ModContent.SPEED_UPGRADE.get()) || original.is(ModContent.EFFICIENCY_UPGRADE.get())) {
            if (!moveItemStackTo(original, 2, 3, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (original.getCount() == result.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, original);
        return result;
    }
    @Override public boolean stillValid(Player player) { return machine == null || machine.stillValid(player); }
}
