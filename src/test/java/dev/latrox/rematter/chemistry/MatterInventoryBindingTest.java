package dev.latrox.rematter.chemistry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A detached cache is not a second spendable owner, independently of its substance insertion policy. */
class MatterInventoryBindingTest {
    private static final SubstanceKey IRON=SubstanceKey.of("test:iron",Phase.SOLID);
    private static final SubstanceKey COPPER=SubstanceKey.of("test:copper",Phase.SOLID);

    @Test void invalidBindingImmediatelyRejectsEveryMutationWithoutTouchingTheRawCache(){
        var bound=new AtomicBoolean(true);var cell=new MatterInventory(1000,10,key->true,bound::get);
        assertEquals(600,cell.insert(IRON,600));var before=cell.snapshot();
        var other=new MatterInventory(1000);assertEquals(100,other.insert(COPPER,100));
        bound.set(false);
        assertEquals(0,cell.amount(IRON));assertEquals(0,cell.insert(IRON,1));assertEquals(0,cell.extract(IRON,1));
        assertFalse(cell.canApply(Map.of(IRON,1L),Map.of()));assertFalse(cell.apply(Map.of(IRON,1L),Map.of()));
        assertFalse(cell.hasTypeSpace(Map.of(),Map.of(IRON,1L)));
        assertEquals(0,cell.transferTo(other,IRON,1));assertEquals(0,other.transferTo(cell,COPPER,1));
        assertEquals(before,cell.snapshot());assertEquals(100,other.amount(COPPER));
    }
    @Test void planningCopiesAndCachedNetworksCannotSpendAnInvalidOwner(){
        var bound=new AtomicBoolean(true);var cell=new MatterInventory(1000,10,key->true,bound::get);
        cell.insert(IRON,600);var copy=cell.planningCopy();var network=new MatterNetworkInventory(List.of(cell));var before=cell.snapshot();
        assertTrue(network.canApply(Map.of(IRON,100L),Map.of(COPPER,100L)));
        bound.set(false);
        assertEquals(0,copy.amount(IRON));assertEquals(0,copy.extract(IRON,1));assertEquals(0,copy.insert(COPPER,1));
        assertFalse(network.canApply(Map.of(IRON,100L),Map.of()));
        assertFalse(network.apply(Map.of(IRON,100L),Map.of()));assertFalse(network.apply(Map.of(),Map.of(COPPER,1L)));
        assertEquals(before,cell.snapshot());
    }
    @Test void aNetworkMayStillUseItsOtherCurrentPhysicalCells(){
        var bound=new AtomicBoolean(true);var old=new MatterInventory(1000,10,key->true,bound::get);old.insert(IRON,500);
        var current=new MatterInventory(1000,10,Set.of(COPPER)::contains);current.insert(COPPER,100);
        var network=new MatterNetworkInventory(List.of(old,current));var raw=old.snapshot();bound.set(false);
        assertTrue(network.apply(Map.of(COPPER,50L),Map.of(COPPER,200L)));
        assertEquals(250,current.amount(COPPER));assertEquals(raw,old.snapshot());
        assertFalse(network.apply(Map.of(IRON,1L),Map.of()));
    }
    @Test void rollbackCanRestoreTheRawCacheWithoutReactivatingItsBinding(){
        var bound=new AtomicBoolean(true);var cell=new MatterInventory(1000,10,Set.of(COPPER)::contains,bound::get);
        var raw=new MatterInventory.Snapshot(1000,List.of(new SubstanceStack(IRON,500)));
        cell.restoreInPlace(raw);assertEquals(100,cell.extract(IRON,100));bound.set(false);cell.restoreInPlace(raw);
        assertEquals(raw,cell.snapshot());assertEquals(0,cell.extract(IRON,1));assertEquals(0,cell.insert(COPPER,1));
        bound.set(true);assertEquals(500,cell.extract(IRON,500));assertEquals(0,cell.insert(IRON,1));
    }
    @Test void aBindingThatDisappearsAtWithdrawalRejectsWithoutRetryingTheSameCandidate(){
        var checks=new AtomicInteger();
        // Simulate availability while selecting, followed by rejection at withdrawal. A bounded hostile fixture
        // avoids hanging the test runner if a future planner accidentally retries the same unspendable owner.
        var cell=MatterInventory.restore(new MatterInventory.Snapshot(1000,List.of(new SubstanceStack(IRON,500))),10,key->true,
                ()->{int n=checks.incrementAndGet();return n<100&&(n==1||n%2==0);});
        var network=new MatterNetworkInventory(List.of(cell));
        assertFalse(network.apply(Map.of(IRON,1L),Map.of()));
        assertTrue(checks.get()<10,"Planner retried an input that made no progress");
        assertEquals(List.of(new SubstanceStack(IRON,500)),cell.snapshot().contents());
    }
}
