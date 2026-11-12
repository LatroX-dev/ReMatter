package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatterCellTransfersTest {
    private static final SubstanceKey SOLID=new SubstanceKey(SubstanceId.parse("test:retained"),Phase.SOLID);
    private static final SubstanceKey LIQUID=new SubstanceKey(SubstanceId.parse("test:retained"),Phase.LIQUID);
    private static final SubstanceKey GAS=new SubstanceKey(SubstanceId.parse("absent_mod:unknown"),Phase.GAS);
    private static final class Cell implements MatterCellTransfers.Cell {
        final Object token;
        final long capacity;
        MatterCellData value;
        int count=1;
        boolean current=true;
        Runnable changed=()->{};
        Cell(long capacity,MatterCellData data){this(capacity,data,new Object());}
        Cell(long capacity,MatterCellData data,Object token){this.capacity=capacity;value=data;this.token=token;}
        public Object token(){return token;}
        public long capacity(){return capacity;}
        public boolean current(){return current&&count==1;}
        public MatterCellData data(){return value;}
        public void data(MatterCellData next){value=next;changed.run();}
    }
    @AfterEach void restoreAuthority(){MatterCellData.installAuthority((state,capacity)->Optional.of(state));}
    private static MatterCellData data(long capacity,Map<SubstanceKey,Long> contents){
        var store=new MatterInventory(capacity,512);contents.forEach((key,amount)->assertEquals(amount.longValue(),store.insert(key,amount)));
        return MatterCellData.from(store);
    }
    private static long amount(Cell cell,SubstanceKey key){return cell.value==null?0:cell.value.open(cell.capacity).orElseThrow().amount(key);}
    private static long move(Cell source,Cell target){try(var tx=Transaction.openRoot()){long moved=MatterCellTransfers.transfer(source,target,tx);if(moved>0)tx.commit();return moved;}}

    @Test void exactLongQuantitiesAndAllPhasesMoveWithoutNeedingDefinitions(){
        long exact=9_007_199_254_740_993L;
        var source=new Cell(Long.MAX_VALUE,data(Long.MAX_VALUE,Map.of(SOLID,exact,LIQUID,17L,GAS,31L)));
        var target=new Cell(Long.MAX_VALUE,null);
        var original=source.value.resolve(Long.MAX_VALUE).orElseThrow();
        assertEquals(exact+48,move(source,target));
        assertEquals(0,source.value.open(Long.MAX_VALUE).orElseThrow().total());
        assertEquals(exact,amount(target,SOLID));assertEquals(17,amount(target,LIQUID));assertEquals(31,amount(target,GAS));
        assertEquals(original.cellId(),source.value.resolve(Long.MAX_VALUE).orElseThrow().cellId());
        assertNotEquals(original.cellId(),target.value.resolve(Long.MAX_VALUE).orElseThrow().cellId());
    }
    @Test void aPartialFitLeavesTheExactRemainderAndPreservesBothIdentities(){
        var source=new Cell(64000,data(64000,Map.of(SOLID,12345L)));
        var target=new Cell(64000,data(64000,Map.of(SOLID,63000L)));
        var sourceId=source.value.resolve(source.capacity).orElseThrow().cellId();var targetId=target.value.resolve(target.capacity).orElseThrow().cellId();
        assertEquals(1000,move(source,target));assertEquals(11345,amount(source,SOLID));assertEquals(64000,amount(target,SOLID));
        assertEquals(sourceId,source.value.resolve(source.capacity).orElseThrow().cellId());assertEquals(targetId,target.value.resolve(target.capacity).orElseThrow().cellId());
    }
    @Test void aFullTypeTableStillAcceptsExistingTypesAndRetainsNewOnes(){
        var existing=new TreeMap<SubstanceKey,Long>();
        for(int i=0;i<512;i++)existing.put(new SubstanceKey(SubstanceId.parse("z:known_"+i),Phase.SOLID),1L);
        var common=existing.lastKey();var unknown=new SubstanceKey(SubstanceId.parse("a:not_in_target"),Phase.GAS);
        var source=new Cell(64000,data(64000,Map.of(common,29L,unknown,71L)));
        var target=new Cell(64000,data(64000,existing));
        assertEquals(29,move(source,target));assertEquals(30,amount(target,common));assertEquals(71,amount(source,unknown));
        assertEquals(512,target.value.open(target.capacity).orElseThrow().typeCount());assertEquals(0,amount(target,unknown));
    }
    @Test void abortAndNestedAbortRestoreBothOriginalRawComponents(){
        var source=new Cell(64000,data(64000,Map.of(SOLID,1234L)));var target=new Cell(64000,null);
        var original=source.value;
        try(var outer=Transaction.openRoot()){
            try(var nested=Transaction.open(outer)){assertEquals(1234,MatterCellTransfers.transfer(source,target,nested));}
            assertSame(original,source.value);assertNull(target.value);
            assertEquals(1234,MatterCellTransfers.transfer(source,target,outer));
        }
        assertSame(original,source.value);assertNull(target.value);
    }
    @Test void successiveTransfersInOneAbortedTransactionDoNotRetainIntermediateStates(){
        var a=new Cell(64000,data(64000,Map.of(SOLID,1234L)));var b=new Cell(64000,null);var c=new Cell(64000,null);
        var original=a.value;
        try(var tx=Transaction.openRoot()){
            assertEquals(1234,MatterCellTransfers.transfer(a,b,tx));
            assertEquals(1234,MatterCellTransfers.transfer(b,c,tx));
        }
        assertSame(original,a.value);assertNull(b.value);assertNull(c.value);
    }
    @Test void pendingCreditsAreSettledOnceAndLatestRevisionsFollowTheirOriginalCell(){
        var source=new Cell(64000,data(64000,Map.of(SOLID,100L)));var target=new Cell(64000,data(64000,Map.of(LIQUID,50L)));
        UUID sourceId=source.value.resolve(64000).orElseThrow().cellId(),targetId=target.value.resolve(64000).orElseThrow().cellId();
        MatterCellData.installAuthority((state,capacity)->{
            if(state.creditRevision()==0)return Optional.of(state.withCredit(state.cellId().equals(sourceId)?SOLID:LIQUID,state.cellId().equals(sourceId)?25:7));
            return Optional.of(state);
        });
        assertEquals(125,move(source,target));
        assertEquals(0,source.value.open(64000).orElseThrow().total());assertEquals(125,amount(target,SOLID));assertEquals(57,amount(target,LIQUID));
        assertEquals(1,source.value.resolve(64000).orElseThrow().creditRevision());assertEquals(1,target.value.resolve(64000).orElseThrow().creditRevision());
        assertEquals(sourceId,source.value.resolve(64000).orElseThrow().cellId());assertEquals(targetId,target.value.resolve(64000).orElseThrow().cellId());
        assertEquals(0,move(source,target));assertEquals(125,amount(target,SOLID));assertEquals(57,amount(target,LIQUID));
    }
    @Test void unreadableOverstackedAliasedAndSameIdentityCellsRemainUnchanged(){
        var source=new Cell(64000,data(64000,Map.of(SOLID,100L)));var original=source.value;
        var raw=new CompoundTag();raw.putInt("schema",99);raw.putLong("preserve",Long.MAX_VALUE);
        var locked=new Cell(64000,new MatterCellData(raw));var unknown=locked.value;
        assertEquals(0,move(source,locked));assertSame(original,source.value);assertSame(unknown,locked.value);
        assertEquals(0,move(locked,source));assertSame(unknown,locked.value);
        source.count=2;var empty=new Cell(64000,null);assertEquals(0,move(source,empty));assertNull(empty.value);source.count=1;
        assertEquals(0,move(source,new Cell(64000,null,source.token)));assertSame(original,source.value);
        var copy=new Cell(64000,MatterCellData.from(source.value.resolve(64000).orElseThrow()));
        assertEquals(0,move(source,copy));assertSame(original,source.value);
    }
    @Test void noSpaceAndEmptySourcesNeverRewriteAnyComponent(){
        var source=new Cell(64000,data(64000,Map.of(SOLID,100L)));var full=new Cell(64000,data(64000,Map.of(LIQUID,64000L)));
        var before=source.value;var fullBefore=full.value;
        assertEquals(0,move(source,full));assertSame(before,source.value);assertSame(fullBefore,full.value);
        assertEquals(0,move(new Cell(64000,null),source));assertSame(before,source.value);
    }
    @Test void aStaleReferenceFromAuthorityResolutionRejectsWithoutWritingEitherCell(){
        var source=new Cell(64000,data(64000,Map.of(SOLID,100L)));var target=new Cell(64000,null);var before=source.value;
        MatterCellData.installAuthority((state,capacity)->{target.current=false;return Optional.of(state);});
        assertEquals(0,move(source,target));assertSame(before,source.value);assertNull(target.value);
    }
    @Test void reentryDuringResolutionCannotBorrowEitherPhysicalCell(){
        var source=new Cell(64000,data(64000,Map.of(SOLID,100L)));var target=new Cell(64000,null);var third=new Cell(64000,null);
        MatterCellData.installAuthority((state,capacity)->{assertEquals(0,MatterCellTransfers.transfer(source,third,Transaction.getCurrentOpenedTransaction()));return Optional.of(state);});
        assertEquals(100,move(source,target));assertNull(third.value);assertEquals(100,amount(target,SOLID));
    }
    @Test void aReferenceInvalidatedDuringTheWriteRollsBothComponentsBack(){
        var source=new Cell(64000,data(64000,Map.of(SOLID,100L)));var target=new Cell(64000,null);var original=source.value;
        target.changed=()->source.current=false;
        assertEquals(0,move(source,target));assertSame(original,source.value);assertNull(target.value);
    }
}
