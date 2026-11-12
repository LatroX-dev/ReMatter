package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** Exact, partial ordinary-cell transfers. Components remain the only persisted owner of their contents. */
public final class MatterCellTransfers {
    private MatterCellTransfers() {}
    private static final ThreadLocal<Set<Object>> ACTIVE=ThreadLocal.withInitial(
            ()->Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final ThreadLocal<IdentityHashMap<Object,ComponentJournal>> JOURNALS=
            ThreadLocal.withInitial(IdentityHashMap::new);

    /** Captures both physical items; current references and permissions are rechecked under the reentry guard. */
    public static long move(ServerLevel level,ItemStack source,ItemStack destination,
                            Supplier<ItemStack> currentSource,Supplier<ItemStack> currentDestination,
                            BooleanSupplier permitted,TransactionContext parent) {
        return move(level,source,destination,currentSource,currentDestination,permitted,key->true,parent);
    }
    public static long move(ServerLevel level,ItemStack source,ItemStack destination,
                            Supplier<ItemStack> currentSource,Supplier<ItemStack> currentDestination,
                            BooleanSupplier permitted,Predicate<dev.latrox.rematter.chemistry.SubstanceKey> selection,TransactionContext parent) {
        if(!level.getServer().isSameThread()||source==destination
                ||!(source.getItem() instanceof MatterCellItem sourceItem)
                ||!(destination.getItem() instanceof MatterCellItem destinationItem))return 0;
        var from=new ItemCell(source,sourceItem.capacity(),currentSource,permitted);
        var to=new ItemCell(destination,destinationItem.capacity(),currentDestination,permitted);
        return transfer(from,to,selection,parent);
    }

    /** Package-private resource boundary also used by the transaction tests, without a game registry. */
    interface Cell {
        Object token();
        long capacity();
        boolean current();
        @Nullable MatterCellData data();
        void data(@Nullable MatterCellData value);
        default @Nullable CellPartitionData partition(){return null;}
    }
    private record ItemCell(ItemStack stack,long capacity,Supplier<ItemStack> reference,BooleanSupplier permitted) implements Cell {
        public Object token(){return stack;}
        public boolean current(){return stack.getCount()==1&&permitted.getAsBoolean()&&reference.get()==stack;}
        public @Nullable MatterCellData data(){return stack.get(MatterCellData.TYPE.get());}
        public @Nullable CellPartitionData partition(){return stack.get(CellPartitionData.TYPE.get());}
        public void data(@Nullable MatterCellData value){if(value==null)stack.remove(MatterCellData.TYPE.get());else stack.set(MatterCellData.TYPE.get(),value);}
    }
    private static final class ComponentJournal extends SnapshotJournal<Optional<MatterCellData>> {
        private final Cell cell;
        ComponentJournal(Cell cell){this.cell=cell;}
        @Override protected Optional<MatterCellData> createSnapshot(){return Optional.ofNullable(cell.data());}
        @Override protected void revertToSnapshot(Optional<MatterCellData> previous){cell.data(previous.orElse(null));}
        @Override protected void onRootCommit(Optional<MatterCellData> previous){
            var journals=JOURNALS.get();journals.remove(cell.token(),this);
            if(journals.isEmpty())JOURNALS.remove();
        }
        void write(MatterCellData value,TransactionContext tx){updateSnapshots(tx);cell.data(value);}
    }
    private static void cleanJournals(){
        var journals=JOURNALS.get();
        journals.values().removeIf(journal->!journal.isInTransaction());
        if(journals.isEmpty())JOURNALS.remove();
    }
    private static ComponentJournal journal(Cell cell){
        // NeoForge rolls participants back in registration order. Reusing the physical cell's journal is
        // essential: a second transfer must retain its root snapshot, not register an intermediate owner.
        cleanJournals();
        return JOURNALS.get().computeIfAbsent(cell.token(),ignored->new ComponentJournal(cell));
    }
    record Plan(long moved,MatterCellData.State source,MatterCellData.State destination) {}

    static Optional<Plan> plan(MatterCellData.State source,long sourceCapacity,
                               MatterCellData.State destination,long destinationCapacity) {
        return plan(source,sourceCapacity,destination,destinationCapacity,key->true,key->true);
    }
    static Optional<Plan> plan(MatterCellData.State source,long sourceCapacity,
                               MatterCellData.State destination,long destinationCapacity,
                               Predicate<dev.latrox.rematter.chemistry.SubstanceKey> destinationFilter,
                               Predicate<dev.latrox.rematter.chemistry.SubstanceKey> selection) {
        if(source.cellId().equals(destination.cellId()))return Optional.empty();
        try {
            if(source.contents().stream().anyMatch(row->ReservedSubstances.reserved(row.key()))
                    ||destination.contents().stream().anyMatch(row->ReservedSubstances.reserved(row.key())))return Optional.empty();
            var from=MatterInventory.restore(new MatterInventory.Snapshot(sourceCapacity,source.contents()),MatterCellData.MAX_TYPES);
            var to=MatterInventory.restore(new MatterInventory.Snapshot(destinationCapacity,destination.contents()),MatterCellData.MAX_TYPES,destinationFilter);
            long moved=0;
            for(var row:from.snapshot().contents()){
                if(!selection.test(row.key()))continue;
                moved=Math.addExact(moved,from.transferTo(to,row.key(),row.quantity()));
                if(to.freeSpace()==0)break;
            }
            if(moved==0)return Optional.empty();
            return Optional.of(new Plan(moved,
                    new MatterCellData.State(source.cellId(),from.snapshot().contents(),source.creditRevision()),
                    new MatterCellData.State(destination.cellId(),to.snapshot().contents(),destination.creditRevision())));
        } catch(IllegalArgumentException|ArithmeticException invalid){return Optional.empty();}
    }
    private static Optional<MatterCellData.State> resolve(@Nullable MatterCellData data,long capacity){
        return data==null?Optional.of(new MatterCellData.State(UUID.randomUUID(),List.of(),0)):data.resolve(capacity);
    }
    private static boolean stable(Cell cell,@Nullable MatterCellData component,@Nullable CellPartitionData partition){return cell.current()&&cell.data()==component&&cell.partition()==partition;}
    private static Optional<CellPartitionData.Spec> spec(@Nullable CellPartitionData partition){return partition==null?Optional.of(CellPartitionData.ANY):partition.open();}
    public static long preview(ItemStack source,ItemStack destination,Predicate<dev.latrox.rematter.chemistry.SubstanceKey> selection){
        if(source==destination||source.getCount()!=1||destination.getCount()!=1
                ||!(source.getItem() instanceof MatterCellItem from)||!(destination.getItem() instanceof MatterCellItem to))return 0;
        var originalSource=source.get(MatterCellData.TYPE.get());var originalTarget=destination.get(MatterCellData.TYPE.get());
        var sourcePartition=source.get(CellPartitionData.TYPE.get());var targetPartition=destination.get(CellPartitionData.TYPE.get());
        var sourceSpec=spec(sourcePartition);var targetSpec=spec(targetPartition);
        var sourceState=resolve(originalSource,from.capacity());var targetState=resolve(originalTarget,to.capacity());
        if(sourceSpec.isEmpty()||targetSpec.isEmpty()||sourceState.isEmpty()||targetState.isEmpty())return 0;
        var preview=plan(sourceState.get(),from.capacity(),targetState.get(),to.capacity(),targetSpec.get(),selection);
        if(source.get(MatterCellData.TYPE.get())!=originalSource||destination.get(MatterCellData.TYPE.get())!=originalTarget
                ||source.get(CellPartitionData.TYPE.get())!=sourcePartition||destination.get(CellPartitionData.TYPE.get())!=targetPartition)return 0;
        return preview.map(Plan::moved).orElse(0L);
    }

    static long transfer(Cell source,Cell destination,TransactionContext parent){return transfer(source,destination,key->true,parent);}
    static long transfer(Cell source,Cell destination,Predicate<dev.latrox.rematter.chemistry.SubstanceKey> selection,TransactionContext parent){
        Object sourceToken=source.token(),destinationToken=destination.token();
        if(sourceToken==destinationToken)return 0;
        var active=ACTIVE.get();
        if(active.contains(sourceToken)||active.contains(destinationToken))return 0;
        active.add(sourceToken);active.add(destinationToken);
        try {
            if(!source.current()||!destination.current())return 0;
            var originalSource=source.data();var originalDestination=destination.data();
            var sourcePartition=source.partition();var targetPartition=destination.partition();
            var sourcePolicy=spec(sourcePartition);var targetPolicy=spec(targetPartition);
            if(sourcePolicy.isEmpty()||targetPolicy.isEmpty())return 0;
            // Resolve once: pending converter credits are included, and their latest revisions follow their cell IDs.
            var sourceState=resolve(originalSource,source.capacity());
            var destinationState=resolve(originalDestination,destination.capacity());
            if(sourceState.isEmpty()||destinationState.isEmpty()
                    ||!stable(source,originalSource,sourcePartition)||!stable(destination,originalDestination,targetPartition))return 0;
            var planned=plan(sourceState.get(),source.capacity(),destinationState.get(),destination.capacity(),targetPolicy.get(),selection);
            if(planned.isEmpty())return 0;
            var result=planned.get();
            var sourceData=MatterCellData.from(result.source());var destinationData=MatterCellData.from(result.destination());
            if(!stable(source,originalSource,sourcePartition)||!stable(destination,originalDestination,targetPartition))return 0;
            try(var tx=Transaction.open(parent)){
                journal(source).write(sourceData,tx);
                journal(destination).write(destinationData,tx);
                if(!stable(source,sourceData,sourcePartition)||!stable(destination,destinationData,targetPartition))return 0;
                tx.commit();return result.moved();
            }
        } finally {
            active.remove(sourceToken);active.remove(destinationToken);
            if(active.isEmpty())ACTIVE.remove();
            cleanJournals();
        }
    }
}
