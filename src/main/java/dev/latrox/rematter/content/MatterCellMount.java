package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.*;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
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
    private @Nullable CellPartitionData partition;
    private long generation;
    private boolean quantityConflict;
    private UUID cellId;
    private long creditRevision;
    private record State(MatterInventory.Snapshot inventory,UUID cellId,long creditRevision,MatterCellData component){}
    private final SnapshotJournal<State> journal=new SnapshotJournal<>(){
        @Override protected State createSnapshot(){return new State(inventory.snapshot(),cellId,creditRevision,stack.get(MatterCellData.TYPE.get()));}
        @Override protected void revertToSnapshot(State state){inventory.restoreInPlace(state.inventory());cellId=state.cellId();creditRevision=state.creditRevision();if(state.component()==null)stack.remove(MatterCellData.TYPE.get());else stack.set(MatterCellData.TYPE.get(),state.component());written=stack.get(MatterCellData.TYPE.get());writtenRevision=inventory.revision();}
    };

    void mount(ItemStack stack) {
        var previous=inventory;
        long binding=++generation;
        this.stack = stack;
        quantityConflict=false;
        partition=stack.get(CellPartitionData.TYPE.get());
        written = stack.get(MatterCellData.TYPE.get());
        var resolved=written==null||!(stack.getItem() instanceof MatterCellItem cell)?Optional.<MatterCellData.State>empty():written.resolve(cell.capacity());
        cellId=resolved.map(MatterCellData.State::cellId).orElse(null);creditRevision=resolved.map(MatterCellData.State::creditRevision).orElse(0L);
        var policy=CellPartitionData.read(stack);
        inventory=null;
        if(stack.getItem() instanceof MatterCellItem cell&&policy.isPresent()&&(written==null||resolved.isPresent())
                &&stack.get(CellPartitionData.TYPE.get())==partition&&stack.get(MatterCellData.TYPE.get())==written){
            var contents=resolved.map(MatterCellData.State::contents).orElse(List.of());
            inventory=MatterInventory.restore(new MatterInventory.Snapshot(cell.capacity(),contents),MatterCellData.MAX_TYPES,
                    policy.get(),()->generation==binding&&this.stack==stack&&bindingCurrent());
        }
        writtenRevision = inventory == null ? 0 : written!=null&&resolved.isPresent()&&!MatterCellData.from(resolved.get()).equals(written)?-1:inventory.revision();
        retire(previous);
    }
    private boolean bindingCurrent(){
        return !quantityConflict&&inventory!=null&&!stack.isEmpty()&&stack.getCount()==1
                &&stack.get(CellPartitionData.TYPE.get())==partition&&stack.get(MatterCellData.TYPE.get())==written;
    }
    /** A policy may change in-place. Only an unchanged quantity component permits an automatic handover. */
    void refresh(){
        if(quantityConflict||Transaction.getLifecycle()!=Transaction.Lifecycle.NONE)return;
        if(stack.get(MatterCellData.TYPE.get())!=written){
            // A competing quantity writer is ambiguous, especially while this cache has committed dirty stock.
            // Keep its raw component untouched and require an explicit physical remount to resolve ownership.
            quantityConflict=true;++generation;retire(inventory);inventory=null;return;
        }
        if(stack.get(CellPartitionData.TYPE.get())!=partition){
            flushCommitted();
            mount(stack);
        }
    }
    private static void retire(@Nullable MatterInventory previous){
        // Persisted contents have one new current owner. Old network/insert/withdraw handles cannot spend a copy.
        if(previous!=null)previous.restoreInPlace(new MatterInventory.Snapshot(previous.capacity(),List.of()));
    }
    boolean readable(ItemStack current) { refresh();return current == stack&&bindingCurrent(); }
    @Nullable MatterInventory inventory() { return bindingCurrent()?inventory:null; }
    boolean canRemove() { return Transaction.getLifecycle()==Transaction.Lifecycle.NONE; }
    void flush() {
        refresh();
        if(!bindingCurrent()||Transaction.getLifecycle()!=Transaction.Lifecycle.NONE)return;
        flushCommitted();
    }
    private void flushCommitted(){
        if(inventory==null||stack.isEmpty()||stack.getCount()!=1||Transaction.getLifecycle()!=Transaction.Lifecycle.NONE
                ||stack.get(MatterCellData.TYPE.get())!=written)return;
        // Only this cache's committed quantities are handed over; an external raw replacement is never overwritten.
        long revision = inventory.revision();
        var current = stack.get(MatterCellData.TYPE.get());
        if (revision == writtenRevision && current == written) return;
        // Provisional external chemical transfers never get copied into a movable item.
        if(cellId==null)cellId=UUID.randomUUID();
        var data = MatterCellData.from(inventory,cellId,creditRevision);
        if (!data.equals(current)) stack.set(MatterCellData.TYPE.get(), data);
        written = stack.get(MatterCellData.TYPE.get());
        writtenRevision = revision;
    }
    Optional<UUID> establishIdentity(){
        if(inventory==null||!readable(stack)||Transaction.getLifecycle()!=Transaction.Lifecycle.NONE)return Optional.empty();
        if(cellId==null)cellId=UUID.randomUUID();
        var data=MatterCellData.from(inventory,cellId,creditRevision);if(!data.equals(stack.get(MatterCellData.TYPE.get())))stack.set(MatterCellData.TYPE.get(),data);
        written=stack.get(MatterCellData.TYPE.get());writtenRevision=inventory.revision();return Optional.of(cellId);
    }
    Optional<UUID> credit(UUID operation,SubstanceKey target,long amount,TransactionContext tx){
        if(inventory==null||!readable(stack)||!inventory.canApply(Map.of(),Map.of(target,amount)))return Optional.empty();
        journal.updateSnapshots(tx);if(cellId==null)cellId=UUID.randomUUID();
        if(!inventory.apply(Map.of(),Map.of(target,amount)))return Optional.empty();
        creditRevision=Math.incrementExact(creditRevision);
        stack.set(MatterCellData.TYPE.get(),MatterCellData.from(inventory,cellId,creditRevision));
        written=stack.get(MatterCellData.TYPE.get());writtenRevision=inventory.revision();return Optional.of(cellId);
    }
    MachineSnapshot.CellView view(ItemStack current) {
        if (current.isEmpty()) return MachineSnapshot.CellView.EMPTY;
        long capacity = current.getItem() instanceof MatterCellItem cell ? cell.capacity() : 0;
        boolean readable = readable(current);
        return installedView(BuiltInRegistries.ITEM.getKey(current.getItem()), readable, readable ? inventory.total() : 0, capacity, readable ? inventory.typeCount() : 0);
    }
    /** An occupied bay names its item by registry ID; the client resolves the display name. */
    static MachineSnapshot.CellView installedView(Identifier item, boolean readable, long used, long capacity, int types) {
        return new MachineSnapshot.CellView(true, readable, item.toString(), readable ? used : 0, capacity, readable ? types : 0, MatterCellData.MAX_TYPES);
    }
}
