package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterNetworkInventory;
import dev.latrox.rematter.chemistry.MatterStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Six independent item owners. The aggregate is a transient view and is never saved. */
final class MatterCellRack {
    private final List<MatterCellMount> mounts;
    private List<MatterInventory> members = List.of();
    private @Nullable MatterStore aggregate;
    private final Runnable membershipChanged;

    MatterCellRack(Runnable membershipChanged) {
        this.membershipChanged=membershipChanged;
        var values = new ArrayList<MatterCellMount>(MachineCellAccess.DRIVE_BAYS);
        for (int bay = 0; bay < MachineCellAccess.DRIVE_BAYS; bay++) values.add(new MatterCellMount());
        mounts = List.copyOf(values);
    }
    MatterCellMount mount(int bay) { refresh();return mounts.get(bay); }
    void mount(int bay, ItemStack stack) { mounts.get(bay).mount(stack); rebuild(); }
    void flush() { refresh();mounts.forEach(MatterCellMount::flush);rebuild(); }
    List<MatterInventory> members() { refresh();return members; }
    @Nullable MatterStore store() { refresh();return aggregate; }
    private void refresh(){mounts.forEach(MatterCellMount::refresh);rebuild();}
    private void rebuild() {
        var next = mounts.stream().map(MatterCellMount::inventory).filter(java.util.Objects::nonNull).toList();
        if (next.equals(members)) return;
        members = List.copyOf(next);
        aggregate = members.isEmpty() ? null : members.size() == 1 ? members.getFirst() : new MatterNetworkInventory(members);
        membershipChanged.run();
    }
}
