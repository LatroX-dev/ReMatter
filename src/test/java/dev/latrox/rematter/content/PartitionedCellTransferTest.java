package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.*;
import java.util.*;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartitionedCellTransferTest {
    private static final SubstanceKey IRON=new SubstanceKey(SubstanceId.parse("test:iron"),Phase.SOLID);
    private static final SubstanceKey COPPER=new SubstanceKey(SubstanceId.parse("test:copper"),Phase.SOLID);
    private static final class Cell implements MatterCellTransfers.Cell {
        MatterCellData value;CellPartitionData policy;final long capacity=1000;
        Cell(long iron,long copper){var store=new MatterInventory(capacity);store.insert(IRON,iron);store.insert(COPPER,copper);value=MatterCellData.from(store);}
        public Object token(){return this;}public long capacity(){return capacity;}public boolean current(){return true;}
        public MatterCellData data(){return value;}public void data(MatterCellData next){value=next;}
        public CellPartitionData partition(){return policy;}
        long amount(SubstanceKey key){return value.open(capacity).orElseThrow().amount(key);}
    }
    @AfterEach void reset(){MatterCellData.installAuthority((state,capacity)->Optional.of(state));}
    @Test void globalSelectionAndDestinationPartitionAreBothRequired(){
        var source=new Cell(600,400);var target=new Cell(0,0);target.policy=CellPartitionData.allowOnly(List.of(COPPER));
        try(var tx=Transaction.openRoot()){assertEquals(0,MatterCellTransfers.transfer(source,target,Set.of(IRON)::contains,tx));tx.commit();}
        assertEquals(600,source.amount(IRON));assertEquals(0,target.amount(COPPER));
        try(var tx=Transaction.openRoot()){assertEquals(400,MatterCellTransfers.transfer(source,target,Set.of(COPPER)::contains,tx));tx.commit();}
        assertEquals(600,source.amount(IRON));assertEquals(0,source.amount(COPPER));assertEquals(400,target.amount(COPPER));
    }
    @Test void oldStockCanLeaveARestrictedSourceAndRollbackPreservesBothPolicies(){
        var source=new Cell(600,0);source.policy=CellPartitionData.allowOnly(List.of(COPPER));
        var target=new Cell(0,0);target.policy=CellPartitionData.allowOnly(List.of(IRON));var before=source.value;
        try(var tx=Transaction.openRoot()){assertEquals(600,MatterCellTransfers.transfer(source,target,tx));}
        assertSame(before,source.value);assertEquals(600,source.amount(IRON));assertEquals(0,target.amount(IRON));
        assertTrue(source.policy.open().orElseThrow().accepts(COPPER));assertTrue(target.policy.open().orElseThrow().accepts(IRON));
    }
    @Test void policyReplacementDuringResolutionDoesNotCommitAStalePlan(){
        var source=new Cell(100,0);var target=new Cell(0,0);target.policy=CellPartitionData.allowOnly(List.of(IRON));var before=source.value;
        MatterCellData.installAuthority((state,capacity)->{target.policy=CellPartitionData.allowOnly(List.of(COPPER));return Optional.of(state);});
        try(var tx=Transaction.openRoot()){assertEquals(0,MatterCellTransfers.transfer(source,target,tx));tx.commit();}
        assertSame(before,source.value);assertEquals(0,target.amount(IRON));
    }
}
