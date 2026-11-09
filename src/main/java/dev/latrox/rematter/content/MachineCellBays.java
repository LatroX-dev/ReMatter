package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterStore;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import net.minecraft.world.item.ItemStack;

/** The Drive's cell bays: mounted cell caches over the machine's item slots and the store built from them. */
final class MachineCellBays {
    private final MachineKind kind;
    private final IntFunction<ItemStack> slots;
    private final MatterCellRack cells = new MatterCellRack();
    private final MatterInventory unavailable = new MatterInventory(1);

    MachineCellBays(MachineKind kind, IntFunction<ItemStack> slots) {
        this.kind = kind;
        this.slots = slots;
    }

    int count() { return MachineCellAccess.bayCount(kind); }
    ItemStack stack(int bay) { return slots.apply(MachineCellAccess.slotForBay(kind, bay)); }
    int installed() {
        int count = 0;
        for (int bay = 0; bay < count(); bay++) if (!stack(bay).isEmpty()) count++;
        return count;
    }

    /** The full local store has stable, flat, independently persisted cell members. */
    MatterStore store() {
        var store = cells.store();
        return store == null ? unavailable : store;
    }
    List<MatterInventory> members() { return cells.members(); }

    void mount(int bay) { cells.mount(bay, stack(bay)); }
    void mountAll() { for (int bay = 0; bay < count(); bay++) mount(bay); }
    void flush() { cells.flush(); }
    void flush(int bay) { cells.mount(bay).flush(); }
    boolean canRemove(int bay) { return cells.mount(bay).canRemove(); }

    /** Every installed cell must be readable before the Drive offers its contents. */
    String status() {
        boolean installed = false;
        for (int bay = 0; bay < count(); bay++) {
            var stack = stack(bay);
            if (stack.isEmpty()) continue;
            installed = true;
            if (!cells.mount(bay).readable(stack)) return "unknown_schema";
        }
        return installed ? "ready" : "missing_cell";
    }

    MachineSnapshot.CellView view(int bay) { return cells.mount(bay).view(stack(bay)); }
    List<MachineSnapshot.CellView> views() { return IntStream.range(0, count()).mapToObj(this::view).toList(); }

    int visibleMask() {
        int mask = 0;
        for (int bay = 0; bay < count(); bay++) if (!stack(bay).isEmpty()) mask |= 1 << bay;
        return mask;
    }
    /** 0 empty, 1/2/3 capacity tiers, 4 unreadable or foreign item. */
    int visibleTier(int bay) {
        var stack = stack(bay);
        if (stack.isEmpty()) return 0;
        if (!cells.mount(bay).readable(stack) || !(stack.getItem() instanceof MatterCellItem item)) return 4;
        return item.capacity() <= MatterLimits.CELL_64K ? 1 : item.capacity() <= MatterLimits.CELL_256K ? 2 : 3;
    }
    int visibleTiers() {
        int tiers = 0;
        for (int bay = 0; bay < count(); bay++) tiers |= visibleTier(bay) << (bay * 3);
        return tiers;
    }
}
