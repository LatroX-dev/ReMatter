package dev.latrox.rematter.chemistry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Insertion policies follow physical Cells through planning, routing and rollback without erasing old stock. */
class MatterInventoryPartitionTest {
    private static final SubstanceKey IRON=new SubstanceKey(SubstanceId.parse("test:iron"),Phase.SOLID);
    private static final SubstanceKey COPPER=new SubstanceKey(SubstanceId.parse("test:copper"),Phase.SOLID);
    private static final SubstanceKey GAS=new SubstanceKey(SubstanceId.parse("missing:unknown_gas"),Phase.GAS);

    @Test void aNewPolicyPreservesExistingDisallowedStockAndStillAllowsWithdrawal(){
        var cell=MatterInventory.restore(new MatterInventory.Snapshot(1000,List.of(new SubstanceStack(IRON,700))),10,Set.of(COPPER)::contains);
        assertEquals(700,cell.amount(IRON));assertEquals(0,cell.insert(IRON,1));
        assertFalse(cell.apply(Map.of(),Map.of(IRON,1L)));
        assertTrue(cell.apply(Map.of(IRON,200L),Map.of()));assertEquals(500,cell.amount(IRON));
        assertEquals(500,cell.insert(COPPER,700));assertEquals(1000,cell.total());
    }
    @Test void phaseAndUnknownIdentityArePartOfTheExactPartition(){
        var sameIdLiquid=new SubstanceKey(GAS.id(),Phase.LIQUID);
        var cell=new MatterInventory(1000,10,Set.of(GAS)::contains);
        assertEquals(0,cell.insert(sameIdLiquid,100));assertEquals(100,cell.insert(GAS,100));
        assertTrue(cell.hasTypeSpace(Map.of(),Map.of(GAS,1L)));
        assertFalse(cell.hasTypeSpace(Map.of(),Map.of(sameIdLiquid,1L)));
    }
    @Test void networkRoutesPastAnExistingButNoLongerAcceptedType(){
        var old=MatterInventory.restore(new MatterInventory.Snapshot(1000,List.of(new SubstanceStack(IRON,200))),10,Set.of(COPPER)::contains);
        var target=new MatterInventory(1000,10,Set.of(IRON)::contains);
        var network=new MatterNetworkInventory(List.of(old,target));
        assertTrue(network.apply(Map.of(),Map.of(IRON,300L)));assertEquals(200,old.amount(IRON));assertEquals(300,target.amount(IRON));
        assertTrue(network.apply(Map.of(IRON,500L),Map.of(COPPER,100L)));
        assertEquals(0,network.amount(IRON));assertEquals(100,old.amount(COPPER));
    }
    @Test void filteredOutputFailureNeverCommitsPlannedWithdrawals(){
        var cell=MatterInventory.restore(new MatterInventory.Snapshot(1000,List.of(new SubstanceStack(IRON,700))),10,Set.of(COPPER)::contains);
        var other=new MatterInventory(1000,10,Set.of(COPPER)::contains);
        var network=new MatterNetworkInventory(List.of(cell,other));var before=network.snapshot();
        assertFalse(network.apply(Map.of(IRON,500L),Map.of(GAS,1L)));
        assertEquals(before.members().get(0).state(),cell.snapshot());assertEquals(700,network.amount(IRON));
    }
    @Test void networkRollbackRetainsTheInsertionPolicy(){
        var cell=new MatterInventory(1000,10,Set.of(IRON)::contains);var other=new MatterInventory(1000,10,Set.of(COPPER)::contains);
        var network=new MatterNetworkInventory(List.of(cell,other));var before=network.snapshot();
        assertTrue(network.apply(Map.of(),Map.of(IRON,500L,COPPER,500L)));network.restoreInPlace(before);
        assertEquals(0,network.total());assertEquals(0,cell.insert(COPPER,10));assertEquals(0,other.insert(IRON,10));
        assertTrue(network.apply(Map.of(),Map.of(IRON,1000L,COPPER,1000L)));
    }
    @Test void directTransfersNeverBorrowAnUnacceptedCellSlot(){
        var source=new MatterInventory(1000);source.insert(IRON,600);source.insert(GAS,400);
        var target=new MatterInventory(1000,10,Set.of(GAS)::contains);
        assertEquals(0,source.transferTo(target,IRON,600));assertEquals(400,source.transferTo(target,GAS,400));
        assertEquals(600,source.amount(IRON));assertEquals(400,target.amount(GAS));
    }
    @Test void restrictedEmptyPolicyIsDifferentFromAnUnconfiguredCell(){
        var cell=MatterInventory.restore(new MatterInventory.Snapshot(1000,List.of(new SubstanceStack(IRON,500))),10,key->false);
        assertFalse(cell.canApply(Map.of(),Map.of(COPPER,1L)));assertEquals(0,cell.insert(IRON,1));
        assertEquals(500,cell.extract(IRON,500));assertEquals(0,cell.total());
        assertEquals(1,new MatterInventory(1000).insert(IRON,1));
    }
}
