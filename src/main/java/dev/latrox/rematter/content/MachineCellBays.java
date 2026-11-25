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
    dev.latrox.rematter.chemistry.VoidPolicy voidPolicy() { return cells.voidPolicy(); }

    void mount(int bay) { cells.mount(bay, stack(bay).getItem() instanceof PrimalCellItem?ItemStack.EMPTY:stack(bay)); }
    void mountAll() { for (int bay = 0; bay < count(); bay++) mount(bay); }
    void unmountAll() { for (int bay = 0; bay < count(); bay++) cells.mount(bay, ItemStack.EMPTY); }
    void flush() { cells.flush(); }
    void flush(int bay) { if(!(stack(bay).getItem() instanceof PrimalCellItem)&&!(stack(bay).getItem() instanceof VoidMatterCellItem))cells.mount(bay).flush(); }
    boolean canRemove(int bay) {
        var stack = stack(bay);
        if (stack.getItem() instanceof PrimalCellItem || stack.getItem() instanceof VoidMatterCellItem) return stack.getCount() == 1;
        return cells.mount(bay).canRemove();
    }

    /** Every installed cell must be readable before the Drive offers its contents. */
    String status() {
        boolean installed = false;
        for (int bay = 0; bay < count(); bay++) {
            var stack = stack(bay);
            if (stack.isEmpty()) continue;
            if(stack.getItem() instanceof PrimalCellItem){if(!PrimalCells.view(stack).readable())return "unknown_schema";continue;}
            if(stack.getItem() instanceof VoidMatterCellItem){installed=true;if(VoidMatterCellItem.policy(stack).isEmpty())return "unknown_schema";continue;}
            installed = true;
            if (!cells.mount(bay).readable(stack)) return "unknown_schema";
        }
        return installed ? "ready" : "missing_cell";
    }

    java.util.Set<java.util.UUID> establishIdentities(){
        var result=new java.util.HashSet<java.util.UUID>();
        for(int bay=0;bay<count();bay++)if(!(stack(bay).getItem() instanceof PrimalCellItem)&&!(stack(bay).getItem() instanceof VoidMatterCellItem))cells.mount(bay).establishIdentity().ifPresent(result::add);
        return java.util.Set.copyOf(result);
    }
    /** Matches credit's first-fit order without mutating a cell or establishing a financial identity. */
    int conversionBay(dev.latrox.rematter.chemistry.SubstanceKey target,long amount){
        if(amount<=0)return -1;
        for(int bay=0;bay<count();bay++)if(!(stack(bay).getItem() instanceof PrimalCellItem)&&!(stack(bay).getItem() instanceof VoidMatterCellItem)){
            var mount=cells.mount(bay);var inventory=mount.inventory();
            if(inventory!=null&&mount.readable(stack(bay))&&inventory.canApply(java.util.Map.of(),java.util.Map.of(target,amount)))return bay;
        }
        return -1;
    }
    java.util.Optional<java.util.UUID> credit(java.util.UUID operation,dev.latrox.rematter.chemistry.SubstanceKey target,long amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
        for(int bay=0;bay<count();bay++)if(!(stack(bay).getItem() instanceof PrimalCellItem)&&!(stack(bay).getItem() instanceof VoidMatterCellItem)){
            var result=cells.mount(bay).credit(operation,target,amount,tx);if(result.isPresent())return result;
        }
        return java.util.Optional.empty();
    }
    MachineSnapshot.CellView view(int bay) {
        var stack=stack(bay);
        if(stack.getItem() instanceof PrimalCellItem){var view=PrimalCells.view(stack);return new MachineSnapshot.CellView(true,view.readable(),net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),view.amount(),view.capacity(),view.amount()>0?1:0,1);}
        if(stack.getItem() instanceof VoidMatterCellItem){
            var policy=VoidMatterCellItem.policy(stack);
            int types=policy.map(p->p.allowed().size()).orElse(0);
            return new MachineSnapshot.CellView(true,policy.isPresent(),net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),0,0,policy.isPresent()?types:0,MatterCellData.MAX_TYPES);
        }
        return cells.mount(bay).view(stack);
    }
    long primalAmount(){long sum=0;for(int bay=0;bay<count();bay++)sum=Math.addExact(sum,PrimalCells.view(stack(bay)).amount());return sum;}
    long primalCapacity(){long sum=0;for(int bay=0;bay<count();bay++)sum=Math.addExact(sum,PrimalCells.view(stack(bay)).capacity());return sum;}
    long primalFreeSpace(){long sum=0;for(int bay=0;bay<count();bay++){var v=PrimalCells.view(stack(bay));if(v.readable())sum=Math.addExact(sum,Math.max(0L,v.capacity()-v.amount()));}return sum;}
    long creditPrimal(long amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
        long remaining=amount;
        for(int bay=0;bay<count()&&remaining>0;bay++){
            int b=bay;
            if(!(stack(b).getItem() instanceof PrimalCellItem))continue;
            long added=PrimalCells.credit(new PrimalCells.Journal(()->stack(b)),remaining,tx);
            remaining-=added;
        }
        return amount-remaining;
    }
    long debitPrimal(long amount,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
        long remaining=amount;
        for(int bay=0;bay<count()&&remaining>0;bay++){
            int b=bay;
            if(!(stack(b).getItem() instanceof PrimalCellItem))continue;
            long taken=PrimalCells.debit(new PrimalCells.Journal(()->stack(b)),remaining,tx);
            remaining-=taken;
        }
        return amount-remaining;
    }
    List<MachineSnapshot.CellView> views() { return IntStream.range(0, count()).mapToObj(this::view).toList(); }

    int visibleMask() {
        int mask = 0;
        for (int bay = 0; bay < count(); bay++) if (!stack(bay).isEmpty()) mask |= 1 << bay;
        return mask;
    }
    /** 0 absent, 1/2/3/8 Matter tiers, 4 unreadable, 5/6/7/9/11 Primal tiers, 10 Void cell. */
    int visibleTier(int bay) {
        var stack = stack(bay);
        if (stack.isEmpty()) return 0;
        if (stack.getItem() instanceof VoidMatterCellItem) return VoidMatterCellItem.policy(stack).isPresent() ? 10 : 4;
        if (stack.getItem() instanceof PrimalCellItem cell) {
            return PrimalCells.view(stack).readable()
                    ? (cell.capacity() <= 16_000 ? 5 : cell.capacity() <= 64_000 ? 6 : cell.capacity() <= 256_000 ? 7 : cell.capacity() <= 1_000_000 ? 9 : 11) : 4;
        }
        if (!cells.mount(bay).readable(stack) || !(stack.getItem() instanceof MatterCellItem item)) return 4;
        return item.capacity() <= MatterLimits.CELL_4M ? 1
                : item.capacity() <= MatterLimits.CELL_16M ? 2
                : item.capacity() <= MatterLimits.CELL_64M ? 3 : 8;
    }
    int visibleTiers() {
        int tiers = 0;
        for (int bay = 0; bay < count(); bay++) tiers |= visibleTier(bay) << (bay * 4);
        return tiers;
    }
    CellStatus visibleStatus(int bay) {
        var stack = stack(bay);
        if (stack.isEmpty()) return CellStatus.ABSENT;
        if (stack.getItem() instanceof VoidMatterCellItem) return VoidMatterCellItem.status(stack);
        if (stack.getItem() instanceof PrimalCellItem) {
            var v = PrimalCells.view(stack);
            return CellStatus.of(true, v.readable(), v.amount(), v.capacity(), 0, 1);
        }
        var mount = cells.mount(bay);
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
