package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterNetworkInventory;
import dev.latrox.rematter.chemistry.MatterStore;
import dev.latrox.rematter.chemistry.VoidPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Six independent item owners. The aggregate is a transient view and is never saved. */
final class MatterCellRack {
    private final List<MatterCellMount> mounts;
    private final ItemStack[] voidStacks = new ItemStack[MachineCellAccess.DRIVE_BAYS];
    private List<MatterInventory> members = List.of();
    private VoidPolicy voidPolicy = VoidPolicy.NONE;
    private @Nullable MatterStore aggregate;
    private final Runnable membershipChanged;

    MatterCellRack(Runnable membershipChanged) {
        this.membershipChanged=membershipChanged;
        var values = new ArrayList<MatterCellMount>(MachineCellAccess.DRIVE_BAYS);
        for (int bay = 0; bay < MachineCellAccess.DRIVE_BAYS; bay++) {
            values.add(new MatterCellMount());
            voidStacks[bay] = ItemStack.EMPTY;
        }
        mounts = List.copyOf(values);
    }
    MatterCellMount mount(int bay) { refresh();return mounts.get(bay); }
    void mount(int bay, ItemStack stack) {
        if (stack.getItem() instanceof VoidMatterCellItem) {
            voidStacks[bay] = stack;
            mounts.get(bay).mount(ItemStack.EMPTY);
        } else {
            voidStacks[bay] = ItemStack.EMPTY;
            mounts.get(bay).mount(stack);
        }
        rebuild();
    }
    void flush() { refresh();mounts.forEach(MatterCellMount::flush);rebuild(); }
    List<MatterInventory> members() { refresh();return members; }
    VoidPolicy voidPolicy() { refresh();return voidPolicy; }
    @Nullable MatterStore store() { refresh();return aggregate; }
    private void refresh(){mounts.forEach(MatterCellMount::refresh);rebuild();}
    private void rebuild() {
        var next = mounts.stream().map(MatterCellMount::inventory).filter(Objects::nonNull).toList();
        var policies = new ArrayList<VoidPolicy>();
        for (var stack : voidStacks) {
            if (!stack.isEmpty()) VoidMatterCellItem.policy(stack).ifPresent(policies::add);
        }
        var nextVoid = VoidPolicy.merge(policies);
        if (next.equals(members) && nextVoid.equals(voidPolicy) && (aggregate != null || next.isEmpty() && !nextVoid.present())) return;
        members = List.copyOf(next);
        voidPolicy = nextVoid;
        aggregate = members.isEmpty() && !voidPolicy.present() ? null
                : members.size() == 1 && !voidPolicy.present() ? members.getFirst()
                : new MatterNetworkInventory(members, voidPolicy);
        membershipChanged.run();
    }
}
