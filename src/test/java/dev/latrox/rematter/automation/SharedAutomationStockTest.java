package dev.latrox.rematter.automation;

import dev.latrox.rematter.automation.AutomationPort.*;
import dev.latrox.rematter.chemistry.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.IRON;
import static org.junit.jupiter.api.Assertions.*;

/** Shared inventory/port ownership tests, without Minecraft or claims about native network execution. */
class SharedAutomationStockTest {
    private static final UUID PLAYER_OWNER=UUID.randomUUID();
    private static final Resource NUGGET=Resource.item("minecraft:iron_nugget");
    private static final SubstanceKey LIQUID_IRON=new SubstanceKey(IRON.id(),Phase.LIQUID);
    private static final Recipe RECIPE=AutomationRecipeCatalogue.recipe("test:iron_nuggets","material_synthesizer",
            Map.of(Resource.matter(IRON),1000L),Map.of(NUGGET,9L),8000,100);

    @Test void twoSimulatedOrdersRaceForTheLastSharedBatchWithoutDoubleOwnership() throws Exception {
        var firstCell=cell(600);var secondCell=cell(400);
        assertEquals(17,secondCell.insert(LIQUID_IRON,17));
        var first=new Port(new MatterNetworkInventory(List.of(firstCell,secondCell)));
        var second=new Port(new MatterNetworkInventory(List.of(secondCell,firstCell)));
        var beforeFirst=firstCell.snapshot();var beforeSecond=secondCell.snapshot();
        var oneLedger=first.ledger.snapshot();var twoLedger=second.ledger.snapshot();
        for(var port:List.of(first,second)) {
            assertEquals(1000,port.extract(IRON,1000,Action.SIMULATE));
            assertTrue(port.acceptTransferred(RECIPE.id(),RECIPE.revision(),1,RECIPE.inputs(),Action.SIMULATE));
        }
        assertEquals(beforeFirst,firstCell.snapshot());assertEquals(beforeSecond,secondCell.snapshot());
        assertEquals(oneLedger,first.ledger.snapshot());assertEquals(twoLedger,second.ledger.snapshot());

        // Both planners see the last complete batch before either starts its actual extraction.
        var planned=new CyclicBarrier(2);
        try(var threads=Executors.newFixedThreadPool(2)) {
            var one=threads.submit(()->raceOrder(first,planned));
            var two=threads.submit(()->raceOrder(second,planned));
            long a=one.get(10,TimeUnit.SECONDS),b=two.get(10,TimeUnit.SECONDS);
            assertEquals(1000,a+b);assertNotEquals(a,b);
        }
        assertEquals(0,firstCell.amount(IRON)+secondCell.amount(IRON));
        assertEquals(17,first.inventory.amount(LIQUID_IRON));
        assertEquals(1,first.ledger.snapshot().orders().size()+second.ledger.snapshot().orders().size());
        long held=List.of(first,second).stream().flatMap(port->port.ledger.snapshot().orders().stream())
                .mapToLong(order->order.ownedInputs().getOrDefault(Resource.matter(IRON),0L)).sum();
        assertEquals(1000,held,"Exactly the physically extracted batch belongs to one ledger");
        assertEquals(0,first.extract(IRON,1000,Action.EXECUTE));
        assertEquals(0,second.extract(IRON,1000,Action.EXECUTE));
    }

    @Test void phasesAndReservationsStayExactAcrossBothPortsAndLaterRefills() {
        var a=cell(600);var b=cell(400);assertEquals(1000,b.insert(LIQUID_IRON,1000));
        var reservation=a.reserve(Map.of(IRON,1L),Map.of()).orElseThrow();
        var first=new Port(new MatterNetworkInventory(List.of(a,b)));
        var second=new Port(new MatterNetworkInventory(List.of(b,a)));
        assertEquals(999,first.extract(IRON,1000,Action.SIMULATE));
        var before=first.inventory.snapshot();
        assertFalse(first.inventory.apply(Map.of(IRON,1000L),Map.of()));
        assertEquals(before,first.inventory.snapshot());
        assertEquals(1000,second.extract(LIQUID_IRON,1000,Action.EXECUTE));
        assertFalse(second.acceptTransferred(RECIPE.id(),RECIPE.revision(),1,
                Map.of(Resource.matter(LIQUID_IRON),1000L),Action.EXECUTE));
        assertTrue(second.ledger.snapshot().orders().isEmpty());
        // A rejected external order still owns its extracted inputs and can return that exact phase.
        assertEquals(1000,second.insert(LIQUID_IRON,1000,Action.EXECUTE));
        assertTrue(a.cancel(reservation));
        assertEquals(1000,order(first));
        assertEquals(0,order(second));
        assertEquals(1000,second.insert(IRON,1000,Action.EXECUTE));
        assertEquals(1000,order(second));
        assertEquals(2,first.ledger.remainingBatches()+second.ledger.remainingBatches());
        assertEquals(Map.of(LIQUID_IRON,1000L),first.inventory.contents());
    }

    @Test void partialReceiptsAndSavedInFlightCopiesRemainWithTheirOriginalInterface() {
        var a=cell(1000);var b=cell(1000);
        var first=new Port(new MatterNetworkInventory(List.of(a,b)));
        var second=new Port(new MatterNetworkInventory(List.of(b,a)));
        assertNotEquals(first.bridgeId(),second.bridgeId());
        assertEquals(1000,order(first));assertEquals(1000,order(second));
        UUID firstOrder=complete(first),secondOrder=complete(second);
        assertNotEquals(firstOrder,secondOrder);
        var secondBefore=second.ledger.snapshot();
        assertFalse(second.beginDelivery(firstOrder,NUGGET,9));
        assertTrue(first.beginDelivery(firstOrder,NUGGET,9));
        var dropped=OrderLedgerCodec.read(OrderLedgerCodec.write(first.ledger)).ledger().orElseThrow();
        Map<Resource,Long> firstRecipient=new HashMap<>(),secondRecipient=new HashMap<>();
        firstRecipient.merge(NUGGET,4L,Math::addExact);first.ackOutput(firstOrder,NUGGET,4);
        assertEquals(secondBefore,second.ledger.snapshot());
        assertEquals(5,first.pendingOutputs().getFirst().amount());
        assertTrue(dropped.pendingOutputs().isEmpty());
        assertEquals(9,dropped.order(firstOrder).orElseThrow().pendingOutputs().get(NUGGET));
        assertThrows(IllegalStateException.class,()->dropped.finishDelivery(firstOrder,NUGGET,4));

        assertTrue(second.beginDelivery(secondOrder,NUGGET,9));
        second.ackOutput(secondOrder,NUGGET,0);
        assertEquals(secondBefore,second.ledger.snapshot());
        assertTrue(second.beginDelivery(secondOrder,NUGGET,9));
        secondRecipient.merge(NUGGET,9L,Math::addExact);second.ackOutput(secondOrder,NUGGET,9);
        assertTrue(second.ledger.snapshot().orders().isEmpty());
        assertTrue(first.beginDelivery(firstOrder,NUGGET,5));
        firstRecipient.merge(NUGGET,5L,Math::addExact);first.ackOutput(firstOrder,NUGGET,5);
        assertEquals(Map.of(NUGGET,9L),firstRecipient);assertEquals(Map.of(NUGGET,9L),secondRecipient);
        assertTrue(first.ledger.snapshot().orders().isEmpty());
        assertEquals(0,first.inventory.total());
        assertTrue(dropped.order(firstOrder).orElseThrow().quarantined(),"A saved copy cannot emit the same output again");
    }

    @Test void backendElectionIsIndependentAndDeterministicWithinTheOrderedNetwork() {
        record Candidate(String name,String backend,boolean eligible) {}
        var disabled=new Candidate("first","ae2",false);
        var rs=new Candidate("second","rs",true);
        var ae=new Candidate("third","ae2",true);
        var aeLater=new Candidate("fourth","ae2",true);
        var rsLater=new Candidate("fifth","rs",true);
        var ordered=List.of(disabled,rs,ae,aeLater,rsLater);
        assertSame(ae,AutomationNetworkPolicy.elected(ordered,"ae2",Candidate::backend,Candidate::eligible).orElseThrow());
        assertSame(rs,AutomationNetworkPolicy.elected(ordered,"rs",Candidate::backend,Candidate::eligible).orElseThrow());
        assertSame(aeLater,AutomationNetworkPolicy.elected(List.of(disabled,rs,aeLater,rsLater),"ae2",
                Candidate::backend,Candidate::eligible).orElseThrow());
        assertTrue(AutomationNetworkPolicy.elected(ordered,"missing",Candidate::backend,Candidate::eligible).isEmpty());
        assertTrue(AutomationNetworkPolicy.elected(List.of(disabled),"ae2",Candidate::backend,Candidate::eligible).isEmpty());
    }

    @Test void aWorkerCannotBeStolenByAnEqualButDifferentHostOrAnotherOrder() {
        record Host(String label) {}
        var first=new Host("same-value");var second=new Host("same-value");
        assertEquals(first,second);assertNotSame(first,second);
        var lease=new AutomationWorkerLease<Host>();var order=UUID.randomUUID();var otherOrder=UUID.randomUUID();
        assertTrue(lease.current().isEmpty());assertTrue(lease.tryAcquire(first,order));
        assertTrue(lease.tryAcquire(first,order),"An identical claim is idempotent");
        assertFalse(lease.tryAcquire(second,order));assertFalse(lease.tryAcquire(first,otherOrder));
        assertFalse(lease.release(second,order));assertFalse(lease.release(first,otherOrder));
        assertSame(first,lease.current().orElseThrow().owner());assertEquals(order,lease.current().orElseThrow().orderId());
        assertTrue(lease.matches(first,order));assertFalse(lease.matches(second,order));
        assertTrue(lease.release(first,order));assertTrue(lease.current().isEmpty());
        assertTrue(lease.tryAcquire(second,otherOrder));assertFalse(lease.release(first,order));
        assertTrue(lease.matches(second,otherOrder));
    }

    @Test void fairnessWaitsForACompletedOrderAndOnlyYieldsToAnActuallyReadyOtherBackend() {
        var first=new Object();var second=new Object();
        var lease=new AutomationWorkerLease<Object>();var firstOrder=UUID.randomUUID();var secondOrder=UUID.randomUUID();
        assertFalse(lease.shouldYield("ae2",true));
        assertTrue(lease.tryAcquire(first,firstOrder));
        assertFalse(lease.completed(second,firstOrder,"rs"));
        assertFalse(lease.completed(first,secondOrder,"rs"));
        assertFalse(lease.shouldYield("ae2",true));
        assertTrue(lease.completed(first,firstOrder,"ae2"));
        assertTrue(lease.matches(first,firstOrder),"Recording a completion cannot hand off an active lease");
        assertFalse(lease.tryAcquire(second,secondOrder));
        assertFalse(lease.shouldYield("ae2",false),"An idle second backend must not stall useful work");
        assertTrue(lease.shouldYield("ae2",true));
        assertFalse(lease.shouldYield("rs",true));
        assertTrue(lease.release(first,firstOrder));
        assertTrue(lease.tryAcquire(second,secondOrder));
        assertTrue(lease.completed(second,secondOrder,"rs"));
        assertTrue(lease.shouldYield("rs",true));
        assertFalse(lease.shouldYield("ae2",true));
        assertFalse(lease.completed(first,firstOrder,"ae2"));
        assertTrue(lease.shouldYield("rs",true),"A stale owner cannot alter fairness for a later order");
    }

    private static long raceOrder(Port port,CyclicBarrier planned) throws Exception {
        assertEquals(1000,port.extract(IRON,1000,Action.SIMULATE));
        planned.await(5,TimeUnit.SECONDS);return order(port);
    }
    private static long order(Port port) {
        long obtained=port.extract(IRON,1000,Action.EXECUTE);
        var transferred=obtained==0?Map.<Resource,Long>of():Map.of(Resource.matter(IRON),obtained);
        assertEquals(obtained==1000,port.acceptTransferred(RECIPE.id(),RECIPE.revision(),1,transferred,Action.EXECUTE));
        return obtained;
    }
    private static UUID complete(Port port) {
        var order=port.ledger.next().orElseThrow();var worker=UUID.randomUUID();
        assertTrue(port.ledger.claim(order.id(),worker,RECIPE.revision()));
        assertTrue(port.ledger.advance(order.id(),worker,RECIPE.revision(),RECIPE.ticks()));
        assertTrue(port.ledger.complete(order.id(),worker,RECIPE.revision()));return order.id();
    }
    private static MatterInventory cell(long iron) {
        var cell=new MatterInventory(64000,512);assertEquals(iron,cell.insert(IRON,iron));return cell;
    }

    /** Only world liveness is replaced; all allocation, ownership and receipt operations use production core types. */
    private static final class Port implements AutomationPort {
        private final UUID owner=PLAYER_OWNER,bridge=UUID.randomUUID();
        private final MatterNetworkInventory inventory;
        private final OrderLedger ledger=new OrderLedger();
        Port(MatterNetworkInventory inventory) { this.inventory=inventory; }
        public UUID ownerId(){return owner;}public UUID bridgeId(){return bridge;}public boolean active(){return true;}
        public Map<SubstanceKey,Long> availableEntries(){return inventory.availableContents();}
        public long insert(SubstanceKey key,long maximum,Action action) {
            if(maximum<=0)return 0;
            var outputs=Map.of(key,maximum);
            return (action==Action.SIMULATE?inventory.canApply(Map.of(),outputs):inventory.apply(Map.of(),outputs))?maximum:0;
        }
        public long extract(SubstanceKey key,long maximum,Action action) {
            if(maximum<=0)return 0;
            long amount=Math.min(maximum,inventory.available(key));
            if(amount==0||action==Action.SIMULATE)return amount;
            return inventory.apply(Map.of(key,amount),Map.of())?amount:0;
        }
        public List<Recipe> recipes(){return List.of(RECIPE);}public boolean busy(){return ledger.busy();}
        public boolean acceptTransferred(String id,String revision,int batches,Map<Resource,Long> inputs,Action action) {
            return id.equals(RECIPE.id())&&revision.equals(RECIPE.revision())&&ledger.accept(RECIPE,batches,inputs,action);
        }
        public List<Delivery> pendingOutputs(){return ledger.pendingOutputs();}
        public boolean beginDelivery(UUID id,Resource resource,long offered){return ledger.beginDelivery(id,resource,offered);}
        public void ackOutput(UUID id,Resource resource,long accepted){ledger.finishDelivery(id,resource,accepted);}
        public void quarantineDelivery(UUID id,String reason){ledger.quarantineDelivery(id,reason);}public void changed(){}
    }
}
