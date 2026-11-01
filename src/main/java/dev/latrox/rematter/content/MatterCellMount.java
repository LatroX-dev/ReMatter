package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Runtime cache only. The mounted item component is the sole persisted owner of cell contents. */
final class MatterCellMount {
    private ItemStack stack = ItemStack.EMPTY;
    private MatterInventory inventory;

    void mount(ItemStack stack) {
        this.stack = stack;
        this.inventory = stack.getItem() instanceof MatterCellItem cell ? cell.open(stack).orElse(null) : null;
    }
    boolean readable(ItemStack current) { return current == stack && !stack.isEmpty() && stack.getCount() == 1 && inventory != null; }
    MatterInventory inventory() { return inventory; }
    boolean canRemove() {
        return Transaction.getCurrentOpenedTransaction() == null && (inventory == null || inventory.snapshot().reservations().isEmpty());
    }
    void flush() {
        if (inventory == null || stack.isEmpty() || Transaction.getCurrentOpenedTransaction() != null) return;
        // Provisional external chemical transfers never get copied into a movable item.
        var data = MatterCellData.from(inventory);
        if (!data.equals(stack.get(MatterCellData.TYPE.get()))) stack.set(MatterCellData.TYPE.get(), data);
    }
    MachineSnapshot.CellView view(ItemStack current) {
        if (current.isEmpty()) return MachineSnapshot.CellView.EMPTY;
        long capacity = current.getItem() instanceof MatterCellItem cell ? cell.capacity() : 0;
        return new MachineSnapshot.CellView(true, readable(current), current.getHoverName().getString(),capacity,
                readable(current) ? inventory.typeCount() : 0,MatterCellData.MAX_TYPES);
    }
}
