package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.primal.PrimalCellItem;
import dev.latrox.rematter.primal.PrimalCells;
import dev.latrox.rematter.chemistry.MatterStore;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import net.minecraft.world.item.ItemStack;

/** The Drive's cell bays: mounted cell caches over the machine's item slots and the store built from them. */
final class MachineCellBays {
    private final MachineKind kind;
    private final IntFunction<ItemStack> slots;
    private final MatterCellRack cells;
    private final MatterInventory unavailable = new MatterInventory(1,1,key->false);

    MachineCellBays(MachineKind kind, IntFunction<ItemStack> slots,Runnable membershipChanged) {
        this.kind = kind;
        this.slots = slots;
        cells=new MatterCellRack(membershipChanged);
    }

    int count() { return MachineCellAccess.bayCount(kind); }
    ItemStack stack(int bay) { return slots.apply(MachineCellAccess.slotForBay(kind, bay)); }
    int installed() {
        int count = 0;
        for (int bay = 0; bay < count(); bay++) if (!stack(bay).isEmpty() && !(stack(bay).getItem() instanceof PrimalCellItem)) count++;
        return count;
    }

    /** The full local store has stable, flat, independently persisted cell members. */
    MatterStore store() {
        var store = cells.store();
        return store == null ? unavailable : store;
    }
    List<MatterInventory> members() { return cells.members(); }

    void mount(int bay) { cells.mount(bay, stack(bay).getItem() instanceof PrimalCellItem?ItemStack.EMPTY:stack(bay)); }
    void mountAll() { for (int bay = 0; bay < count(); bay++) mount(bay); }
    void flush() { cells.flush(); }
    void flush(int bay) { if(!(stack(bay).getItem() instanceof PrimalCellItem))cells.mount(bay).flush(); }
    boolean canRemove(int bay) { return stack(bay).getItem() instanceof PrimalCellItem?stack(bay).getCount()==1:cells.mount(bay).canRemove(); }

    /** Every installed cell must be readable before the Drive offers its contents. */
    String status() {
        boolean installed = false;
        for (int bay = 0; bay < count(); bay++) {
            var stack = stack(bay);
            if (stack.isEmpty()) continue;
            if(stack.getItem() instanceof PrimalCellItem){if(!PrimalCells.view(stack).readable())return "unknown_schema";continue;}
            installed = true;
            if (!cells.mount(bay).readable(stack)) return "unknown_schema";
        }
        return installed ? "ready" : "missing_cell";
    }

    java.util.Set<java.util.UUID> establishIdentities(){
        var result=new java.util.HashSet<java.util.UUID>();
        for(int bay=0;bay<count();bay++)if(!(stack(bay).getItem() instanceof PrimalCellItem))cells.mount(bay).establishIdentity().ifPresent(result::add);
        return java.util.Set.copyOf(result);
    }
    /** Matches credit's first-fit order without mutating a cell or establishing a financial identity. */
    int conversionBay(dev.latrox.rematter.chemistry.SubstanceKey target,long amount){
        if(amount<=0)return -1;
        for(int bay=0;bay<count();bay++)if(!(stack(bay).getItem() instanceof PrimalCellItem)){
            var mount=cells.mount(bay);var inventory=mount.inventory();
            if(inventory!=null&&mount.readable(stack(bay))&&inventory.canApply(java.util.Map.of(),java.util.Map.of(target,amount)))return bay;
        }
        return -1;
    }
    java.util.Optional<java.util.UUID> credit(java.util.UUID operation,dev.latrox.rematter.chemistry.SubstanceKey target,long amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
        for(int bay=0;bay<count();bay++)if(!(stack(bay).getItem() instanceof PrimalCellItem)){
            var result=cells.mount(bay).credit(operation,target,amount,tx);if(result.isPresent())return result;
        }
        return java.util.Optional.empty();
    }
    MachineSnapshot.CellView view(int bay) {
        var stack=stack(bay);
        if(stack.getItem() instanceof PrimalCellItem){var view=PrimalCells.view(stack);return new MachineSnapshot.CellView(true,view.readable(),net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),view.amount(),view.capacity(),view.amount()>0?1:0,1);}
        return cells.mount(bay).view(stack);
    }
    long primalAmount(){long sum=0;for(int bay=0;bay<count();bay++)sum=Math.addExact(sum,PrimalCells.view(stack(bay)).amount());return sum;}
    long primalCapacity(){long sum=0;for(int bay=0;bay<count();bay++)sum=Math.addExact(sum,PrimalCells.view(stack(bay)).capacity());return sum;}
    List<MachineSnapshot.CellView> views() { return IntStream.range(0, count()).mapToObj(this::view).toList(); }

    int visibleMask() {
        int mask = 0;
        for (int bay = 0; bay < count(); bay++) if (!stack(bay).isEmpty()) mask |= 1 << bay;
        return mask;
    }
    /** 0 absent, 1/2/3 Matter capacity tiers, 4 unreadable or foreign, 5/6/7 Primal capacity tiers. */
    int visibleTier(int bay) {
        var stack = stack(bay);
        if (stack.isEmpty()) return 0;
        if(stack.getItem() instanceof PrimalCellItem cell)return PrimalCells.view(stack).readable()?(cell.capacity()<=16000?5:cell.capacity()<=64000?6:7):4;
        if (!cells.mount(bay).readable(stack) || !(stack.getItem() instanceof MatterCellItem item)) return 4;
        return item.capacity() <= MatterLimits.CELL_64K ? 1 : item.capacity() <= MatterLimits.CELL_256K ? 2 : 3;
    }
    int visibleTiers() {
        int tiers = 0;
        for (int bay = 0; bay < count(); bay++) tiers |= visibleTier(bay) << (bay * 3);
        return tiers;
    }
    CellStatus visibleStatus(int bay) {
        var stack = stack(bay);
        var mount = cells.mount(bay);
        if (stack.isEmpty()) return CellStatus.ABSENT;
        if(stack.getItem() instanceof PrimalCellItem){var v=PrimalCells.view(stack);return CellStatus.of(true,v.readable(),v.amount(),v.capacity(),0,1);}
        if (!mount.readable(stack)) return CellStatus.LOCKED;
        var inventory = mount.inventory();
        return CellStatus.of(true, true, inventory.total(), inventory.capacity(), inventory.typeCount(), inventory.maximumTypes());
    }
    int visibleStatuses() {
        int result = 0;
        for (int bay = 0; bay < count(); bay++) result |= visibleStatus(bay).ordinal() << (bay * 3);
        return result;
    }
}
