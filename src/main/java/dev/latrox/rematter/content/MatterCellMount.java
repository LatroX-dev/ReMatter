package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** Runtime cache only. The mounted item component is the sole persisted owner of cell contents. */
final class MatterCellMount {
    private ItemStack stack = ItemStack.EMPTY;
    private MatterInventory inventory;
    private long writtenRevision;
    private @Nullable MatterCellData written;

    void mount(ItemStack stack) {
        this.stack = stack;
        this.inventory = stack.getItem() instanceof MatterCellItem cell ? cell.open(stack).orElse(null) : null;
        written = stack.get(MatterCellData.TYPE.get());
        writtenRevision = inventory == null ? 0 : inventory.revision();
    }
    boolean readable(ItemStack current) { return current == stack && !stack.isEmpty() && stack.getCount() == 1 && inventory != null; }
    MatterInventory inventory() { return inventory; }
    boolean canRemove() {
        return Transaction.getCurrentOpenedTransaction() == null && (inventory == null || inventory.snapshot().reservations().isEmpty());
    }
    void flush() {
        if (inventory == null || stack.isEmpty() || Transaction.getCurrentOpenedTransaction() != null) return;
        // Unchanged contents are not serialised again, unless something replaced the item's component meanwhile.
        long revision = inventory.revision();
        var current = stack.get(MatterCellData.TYPE.get());
        if (revision == writtenRevision && current == written) return;
        // Provisional external chemical transfers never get copied into a movable item.
        var data = MatterCellData.from(inventory);
        if (!data.equals(current)) stack.set(MatterCellData.TYPE.get(), data);
        written = stack.get(MatterCellData.TYPE.get());
        writtenRevision = revision;
    }
    MachineSnapshot.CellView view(ItemStack current) {
        if (current.isEmpty()) return MachineSnapshot.CellView.EMPTY;
        long capacity = current.getItem() instanceof MatterCellItem cell ? cell.capacity() : 0;
        boolean readable = readable(current);
        return installedView(BuiltInRegistries.ITEM.getKey(current.getItem()), readable, capacity, readable ? inventory.typeCount() : 0);
    }
    /** An occupied bay names its item by registry ID; the client resolves the display name. */
    static MachineSnapshot.CellView installedView(Identifier item, boolean readable, long capacity, int types) {
        return new MachineSnapshot.CellView(true, readable, item.toString(), capacity, types, MatterCellData.MAX_TYPES);
    }
}
