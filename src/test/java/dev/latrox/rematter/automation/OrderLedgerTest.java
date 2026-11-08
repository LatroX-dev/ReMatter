package dev.latrox.rematter.automation;

import dev.latrox.rematter.automation.AutomationPort.*;
import dev.latrox.rematter.chemistry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderLedgerTest {
    private static final Resource COPPER=Resource.matter(BuiltinChemistry.COPPER), WATER=Resource.fluid("minecraft:water"), SAND=Resource.item("minecraft:sand");
    private static final Resource GLASS=Resource.item("minecraft:glass"), OXYGEN=Resource.matter(BuiltinChemistry.OXYGEN);
    private static Recipe recipe() {return new Recipe("test:processing","revision1","material_preparator",Map.of(COPPER,10L,WATER,100L,SAND,1L),Map.of(GLASS,4L,OXYGEN,5L),4000,120);}
    @Test void acceptedOrdersReserveEnoughSerializedSpaceForAllFutureOutputsAndQuarantine() {
        var inputs=new TreeMap<Resource,Long>();var outputs=new TreeMap<Resource,Long>();
        for(int i=0;i<128;i++) {
            inputs.put(Resource.item("test:input_"+i+"_"+"x".repeat(480)),1L);
            outputs.put(Resource.item("test:output_"+i+"_"+"y".repeat(480)),1L);
        }
        var recipe=new Recipe("test:large","revision1","material_preparator",inputs,outputs,1,120);
        var ledger=new OrderLedger();int accepted=0;
        for(int i=0;i<64;i++) {
            var before=ledger.snapshot();
            if(!ledger.accept(recipe,1,inputs,Action.EXECUTE)){assertEquals(before,ledger.snapshot());break;}
            accepted++;var id=ledger.next().orElseThrow().id();completeOne(ledger,id);
            assertTrue(OrderLedgerCodec.write(ledger).length()<=OrderLedgerCodec.MAX_LENGTH);
        }
        assertTrue(accepted>0&&accepted<64);
        ledger.quarantineDelivery(ledger.snapshot().orders().getFirst().id(),"\u0000".repeat(256));
        String saved=OrderLedgerCodec.write(ledger);
        assertEquals(ledger.snapshot(),OrderLedgerCodec.read(saved).ledger().orElseThrow().snapshot());
    }
    @Test void simulationAndWrongTransferredVectorsDoNotTakeOwnership() {
        var ledger=new OrderLedger();var recipe=recipe();var exact=ResourceAmounts.multiply(recipe.inputs(),2);var before=ledger.snapshot();
        assertTrue(ledger.accept(recipe,2,exact,Action.SIMULATE));assertEquals(before,ledger.snapshot());
        assertFalse(ledger.accept(recipe,1,exact,Action.EXECUTE));assertEquals(before,ledger.snapshot());
        var wrong=new TreeMap<>(recipe.inputs());wrong.remove(COPPER);wrong.put(new Resource(Kind.MATTER,"rematter:copper","gas"),10L);
        assertFalse(ledger.accept(recipe,1,wrong,Action.EXECUTE));assertEquals(before,ledger.snapshot());
    }
    @Test void transferredMatterIsNotDebitedAgainAndEachCompletedBatchCreatesOutputsOnce() {
        var source=new MatterInventory(1000);source.insert(BuiltinChemistry.COPPER,100);
        assertEquals(20,source.extract(BuiltinChemistry.COPPER,20));
        var ledger=new OrderLedger();var recipe=recipe();assertTrue(ledger.accept(recipe,2,ResourceAmounts.multiply(recipe.inputs(),2),Action.EXECUTE));
        var order=ledger.next().orElseThrow();var worker=UUID.randomUUID();
        assertFalse(ledger.claim(order.id(),worker,"stale"));assertTrue(ledger.claim(order.id(),worker,recipe.revision()));
        assertFalse(ledger.claim(order.id(),UUID.randomUUID(),recipe.revision()));assertTrue(ledger.next().isEmpty());
        assertFalse(ledger.complete(order.id(),worker,recipe.revision()));
        assertTrue(ledger.advance(order.id(),worker,recipe.revision(),119));assertFalse(ledger.complete(order.id(),worker,recipe.revision()));
        assertTrue(ledger.advance(order.id(),worker,recipe.revision(),1));assertTrue(ledger.complete(order.id(),worker,recipe.revision()));
        assertFalse(ledger.complete(order.id(),worker,recipe.revision()));
        assertEquals(recipe.inputs(),ledger.order(order.id()).orElseThrow().ownedInputs());
        assertEquals(recipe.outputs(),ledger.order(order.id()).orElseThrow().pendingOutputs());assertEquals(80,source.amount(BuiltinChemistry.COPPER));
    }
    @Test void partialDeliveryCancellationAndPartialRefundRetainEveryUnacceptedResource() {
        var ledger=accepted(2);var id=ledger.next().orElseThrow().id();completeOne(ledger,id);
        deliver(ledger,id,GLASS,4,1);assertEquals(3,ledger.order(id).orElseThrow().pendingOutputs().get(GLASS));
        var before=ledger.snapshot();assertFalse(ledger.beginDelivery(id,GLASS,4));assertEquals(before,ledger.snapshot());
        assertTrue(ledger.cancel(id));assertTrue(ledger.next().isEmpty());assertEquals(0,ledger.remainingBatches());
        assertFalse(ledger.cancel(id),"An order is cancelled once");
        refund(ledger,id,COPPER,10,7);assertEquals(3,ledger.order(id).orElseThrow().ownedInputs().get(COPPER));
        refund(ledger,id,COPPER,3,3);refund(ledger,id,WATER,100,100);refund(ledger,id,SAND,1,1);
        assertTrue(ledger.pendingRefunds().isEmpty());assertTrue(ledger.order(id).isPresent());
        deliver(ledger,id,GLASS,3,3);deliver(ledger,id,OXYGEN,5,5);assertTrue(ledger.order(id).isEmpty());
    }
    @Test void refundsUseTheDurableGuardAndNeverMixWithOutputAcknowledgements() {
        var ledger=accepted(2);var id=ledger.next().orElseThrow().id();completeOne(ledger,id);
        assertFalse(ledger.beginRefund(id,COPPER,10),"Only a cancelled order refunds its inputs");
        assertTrue(ledger.cancel(id));
        assertEquals(List.of(new Delivery(id,COPPER,10L),new Delivery(id,WATER,100L),new Delivery(id,SAND,1L)).stream().sorted(
                Comparator.comparing(Delivery::resource)).toList(),ledger.pendingRefunds());
        assertFalse(ledger.beginRefund(id,COPPER,11));
        assertTrue(ledger.beginRefund(id,COPPER,10));
        var saved=OrderLedger.restore(ledger.snapshot());
        assertTrue(ledger.pendingRefunds().isEmpty());assertTrue(ledger.pendingOutputs().isEmpty());
        assertFalse(ledger.beginDelivery(id,GLASS,4),"One attempt guards the whole order");
        assertThrows(IllegalStateException.class,()->ledger.finishDelivery(id,COPPER,10));
        ledger.finishRefund(id,COPPER,6);
        assertEquals(4,ledger.order(id).orElseThrow().ownedInputs().get(COPPER));
        assertTrue(saved.pendingRefunds().isEmpty(),"A copy saved during the refund stays locked");
        assertThrows(IllegalStateException.class,()->saved.finishRefund(id,COPPER,6));
        assertTrue(ledger.beginDelivery(id,GLASS,4));
        assertThrows(IllegalStateException.class,()->ledger.finishRefund(id,GLASS,4));
        ledger.finishDelivery(id,GLASS,4);
        assertEquals(Map.of(OXYGEN,5L),ledger.order(id).orElseThrow().pendingOutputs());
    }
    @Test void parameterOnlyChangesRebaseUnclaimedWorkButAMaterialChangeDoesNot() {
        var ledger=accepted(2);var order=ledger.next().orElseThrow();var worker=UUID.randomUUID();
        var slower=new Recipe(recipe().id(),"revision2",recipe().workerKind(),recipe().inputs(),recipe().outputs(),6000,240);
        assertTrue(ledger.claim(order.id(),worker,"revision1"));
        assertFalse(ledger.rebase(order.id(),slower),"A claimed order keeps its revision until the worker releases it");
        assertTrue(ledger.release(order.id(),worker));
        var changedInputs=new TreeMap<>(recipe().inputs());changedInputs.put(COPPER,11L);
        assertFalse(ledger.rebase(order.id(),new Recipe(recipe().id(),"revision3",recipe().workerKind(),changedInputs,recipe().outputs(),4000,120)));
        assertFalse(ledger.rebase(order.id(),new Recipe("test:other","revision3",recipe().workerKind(),recipe().inputs(),recipe().outputs(),4000,120)));
        assertTrue(ledger.rebase(order.id(),slower));
        var rebased=ledger.order(order.id()).orElseThrow();
        assertEquals(slower,rebased.recipe());assertEquals(order.ownedInputs(),rebased.ownedInputs());assertEquals(2,rebased.remainingBatches());
        assertFalse(ledger.claim(order.id(),worker,"revision1"));assertTrue(ledger.claim(order.id(),worker,"revision2"));
    }
    @Test void quarantineSuppressesAutomaticRetriesWithoutDiscardingInputsOrOutputs() {
        var ledger=accepted(2);var id=ledger.next().orElseThrow().id();completeOne(ledger,id);
        var held=ledger.order(id).orElseThrow();ledger.quarantineDelivery(id,"delivery_outcome_unknown");
        assertTrue(ledger.next().isEmpty());assertTrue(ledger.pendingOutputs().isEmpty());assertTrue(ledger.pendingRefunds().isEmpty());
        assertFalse(ledger.beginDelivery(id,GLASS,1));
        var decoded=OrderLedgerCodec.read(OrderLedgerCodec.write(ledger));assertTrue(decoded.readable());
        var restored=decoded.ledger().orElseThrow().order(id).orElseThrow();
        assertEquals(held.ownedInputs(),restored.ownedInputs());assertEquals(held.pendingOutputs(),restored.pendingOutputs());assertTrue(restored.quarantined());
    }
    @Test void savedInFlightCopiesStayLockedWhenTheOriginalPartiallyFinishes() {
        var ledger=accepted(2);var id=ledger.next().orElseThrow().id();completeOne(ledger,id);
        var before=ledger.order(id).orElseThrow();
        assertTrue(ledger.beginDelivery(id,GLASS,4));
        assertTrue(ledger.pendingOutputs().isEmpty());
        assertFalse(ledger.advance(id,before.workerId(),before.recipe().revision(),1));
        var saved=ledger.snapshot();
        assertEquals("delivery_in_flight",saved.orders().getFirst().quarantineReason());
        var dropped=OrderLedger.restore(saved);
        var reloaded=OrderLedgerCodec.read(OrderLedgerCodec.write(ledger)).ledger().orElseThrow();

        ledger.finishDelivery(id,GLASS,2);
        var live=ledger.order(id).orElseThrow();
        assertFalse(live.quarantined());
        assertEquals(before.ownedInputs(),live.ownedInputs());
        assertEquals(Map.of(GLASS,2L,OXYGEN,5L),live.pendingOutputs());
        for(var copy:List.of(dropped,reloaded)) {
            assertEquals(saved,copy.snapshot(),"Finishing the old live object must not change saved ownership");
            assertTrue(copy.pendingOutputs().isEmpty());
            assertFalse(copy.beginDelivery(id,GLASS,4));
            assertFalse(copy.beginDelivery(id,OXYGEN,5));
            assertThrows(IllegalStateException.class,()->copy.finishDelivery(id,GLASS,2));
            assertThrows(IllegalStateException.class,()->copy.finishDelivery(id,OXYGEN,1));
            assertEquals(saved,copy.snapshot());
        }
    }
    @Test void zeroAcceptanceClearsOnlyTheOriginalLiveAttemptAndRetainsAllOutputs() {
        var ledger=accepted(1);var id=ledger.next().orElseThrow().id();completeOne(ledger,id);
        var before=ledger.snapshot();
        assertTrue(ledger.beginDelivery(id,GLASS,2));
        var saved=OrderLedger.restore(ledger.snapshot());
        ledger.finishDelivery(id,GLASS,0);
        assertEquals(before,ledger.snapshot());
        assertEquals(2,ledger.pendingOutputs().size());
        assertTrue(saved.pendingOutputs().isEmpty());
        assertThrows(IllegalStateException.class,()->saved.finishDelivery(id,GLASS,0));
        assertTrue(ledger.beginDelivery(id,GLASS,2),"A confirmed zero transfer can be attempted later");
    }
    @Test void deliveryAcknowledgementsMustMatchTheSingleOfferedResourceAndAmount() {
        var ledger=accepted(1);var id=ledger.next().orElseThrow().id();completeOne(ledger,id);
        var before=ledger.snapshot();
        assertFalse(ledger.beginDelivery(id,GLASS,0));
        assertFalse(ledger.beginDelivery(id,GLASS,5));
        assertFalse(ledger.beginDelivery(id,SAND,1));
        assertEquals(before,ledger.snapshot());
        assertTrue(ledger.beginDelivery(id,GLASS,2));
        var guarded=ledger.snapshot();
        assertFalse(ledger.beginDelivery(id,OXYGEN,5),"The whole order is guarded during one output transfer");
        assertThrows(IllegalArgumentException.class,()->ledger.finishDelivery(id,OXYGEN,1));
        assertThrows(IllegalArgumentException.class,()->ledger.finishDelivery(id,GLASS,3));
        assertThrows(IllegalArgumentException.class,()->ledger.finishDelivery(id,GLASS,-1));
        assertThrows(IllegalStateException.class,()->ledger.finishRefund(id,GLASS,1));
        assertEquals(guarded,ledger.snapshot());
        ledger.finishDelivery(id,GLASS,2);
        var finished=ledger.snapshot();
        assertThrows(IllegalStateException.class,()->ledger.finishDelivery(id,GLASS,2));
        assertEquals(finished,ledger.snapshot(),"A duplicate acknowledgement cannot consume another portion");
        assertTrue(ledger.beginDelivery(id,GLASS,2));ledger.finishDelivery(id,GLASS,2);
        assertTrue(ledger.beginDelivery(id,OXYGEN,5));ledger.finishDelivery(id,OXYGEN,5);
        assertTrue(ledger.order(id).isEmpty());
    }
    @Test void anExceptionalDeliveryCannotBeFinishedOrRetriedAfterQuarantine() {
        var ledger=accepted(2);var id=ledger.next().orElseThrow().id();completeOne(ledger,id);
        var before=ledger.order(id).orElseThrow();
        assertTrue(ledger.beginDelivery(id,GLASS,4));
        ledger.quarantineDelivery(id,"receiver_outcome_unknown");
        var guarded=ledger.snapshot();
        assertFalse(ledger.beginDelivery(id,GLASS,4));
        assertThrows(IllegalStateException.class,()->ledger.finishDelivery(id,GLASS,0));
        assertThrows(IllegalStateException.class,()->ledger.finishDelivery(id,GLASS,4));
        assertEquals(guarded,ledger.snapshot());
        assertEquals(before.ownedInputs(),ledger.order(id).orElseThrow().ownedInputs());
        assertEquals(before.pendingOutputs(),ledger.order(id).orElseThrow().pendingOutputs());
        assertEquals(guarded,OrderLedgerCodec.read(OrderLedgerCodec.write(ledger)).ledger().orElseThrow().snapshot());
    }
    @Test void durableProgressAndClaimRemainExactButRevisionOrWorkerChangesCannotCompleteIt() {
        var ledger=accepted(1);var order=ledger.next().orElseThrow();var worker=UUID.randomUUID();
        assertTrue(ledger.claim(order.id(),worker,order.recipe().revision()));assertTrue(ledger.advance(order.id(),worker,order.recipe().revision(),70));
        var restored=OrderLedgerCodec.read(OrderLedgerCodec.write(ledger)).ledger().orElseThrow();assertEquals(ledger.snapshot(),restored.snapshot());
        assertFalse(restored.advance(order.id(),worker,"changed",50));assertFalse(restored.complete(order.id(),UUID.randomUUID(),order.recipe().revision()));
        assertTrue(restored.release(order.id(),worker));assertEquals(0,restored.order(order.id()).orElseThrow().progress());
        assertEquals(order.ownedInputs(),restored.order(order.id()).orElseThrow().ownedInputs());
        assertTrue(restored.claim(order.id(),worker,order.recipe().revision()));assertTrue(restored.advance(order.id(),worker,order.recipe().revision(),Integer.MAX_VALUE));
        assertTrue(restored.complete(order.id(),worker,order.recipe().revision()));assertEquals(0,restored.remainingBatches());
    }
    @Test void boundsAndOverflowAreRejectedBeforeAcceptingAnyTransferredInput() {
        var ledger=new OrderLedger();var recipe=recipe();
        assertFalse(ledger.accept(recipe,65,recipe.inputs(),Action.EXECUTE));
        var huge=new Recipe("test:huge","1","decomposer",Map.of(COPPER,Long.MAX_VALUE),Map.of(GLASS,1L),0,1);
        assertFalse(ledger.accept(huge,2,Map.of(COPPER,Long.MAX_VALUE),Action.EXECUTE));assertTrue(ledger.snapshot().orders().isEmpty());
        assertTrue(ledger.accept(recipe,64,ResourceAmounts.multiply(recipe.inputs(),64),Action.EXECUTE));assertTrue(ledger.busy());
        assertFalse(ledger.accept(recipe,1,recipe.inputs(),Action.EXECUTE));
        assertThrows(IllegalArgumentException.class,()->new Resource(Kind.ITEM,"minecraft:sand","solid"));
        assertThrows(IllegalArgumentException.class,()->new Resource(Kind.MATTER,"rematter:copper","unknown"));
    }
    @Test void exactJsonRejectsFutureFieldsDuplicateKeysAndFractionalAmountsWithoutLosingRawData() {
        var ledger=accepted(1);String valid=OrderLedgerCodec.write(ledger);
        for(String raw:List.of(valid.replace("\"schema\":1","\"schema\":42"),valid.replace("\"schema\":1","\"schema\":1,\"future\":99"),
                valid.replace("\"schema\":1","\"schema\":1,\"schema\":1"),valid.replace("\"amount\":10","\"amount\":10.5"),"[]","not json")) {
            var loaded=OrderLedgerCodec.read(raw);assertFalse(loaded.readable());assertEquals(raw,loaded.write());
        }
        assertEquals(ledger.snapshot(),OrderLedgerCodec.read(valid).ledger().orElseThrow().snapshot());
        assertEquals(recipe(),OrderLedgerCodec.readRecipe(OrderLedgerCodec.writeRecipe(recipe())));
    }
    @Test void forgedRestoredOutputAndInputOwnershipNeverBecomeAvailable() {
        var order=accepted(1).next().orElseThrow();
        assertThrows(IllegalArgumentException.class,()->new OrderLedger.Order(order.id(),order.recipe(),1,1,0,
                order.ownedInputs(),Map.of(GLASS,1L),false,"",null));
        assertThrows(IllegalArgumentException.class,()->new OrderLedger.Order(order.id(),order.recipe(),1,1,0,
                Map.of(COPPER,11L),Map.of(),true,"",null));
        assertThrows(IllegalArgumentException.class,()->OrderLedger.restore(new OrderLedger.Snapshot(1,List.of(order,order))));
    }
    @Test void anOwnerReleasesAStoppedDeliveryOnlyOnceAndOnlyWhatItHolds() {
        var ledger=accepted(2);var id=ledger.next().orElseThrow().id();completeOne(ledger,id);
        var worker=ledger.order(id).orElseThrow().workerId();
        assertTrue(ledger.releasable(id).isEmpty(),"A running order is not releasable");
        assertTrue(ledger.beginDelivery(id,GLASS,4));
        assertTrue(ledger.releasable(id).isEmpty(),"A live delivery attempt still guards the order");
        ledger.quarantineDelivery(id,"receiver_outcome_unknown");
        var stopped=ledger.releasable(id).orElseThrow();
        assertEquals(stopped,ledger.firstReleasable().orElseThrow());
        var held=OrderLedger.held(stopped);
        assertEquals(Map.of(COPPER,10L,WATER,100L,SAND,1L,GLASS,4L,OXYGEN,5L),held);
        assertFalse(ledger.releaseHeld(id,held),"The worker lease ends first");
        assertTrue(ledger.release(id,worker));
        var before=ledger.snapshot();
        assertFalse(ledger.releaseHeld(id,Map.of(GLASS,5L)));
        assertFalse(ledger.releaseHeld(id,Map.of()));
        assertFalse(ledger.releaseHeld(id,Map.of(Resource.item("minecraft:stone"),1L)));
        assertEquals(before,ledger.snapshot());
        assertTrue(ledger.releaseHeld(id,Map.of(COPPER,10L,SAND,1L,GLASS,4L,OXYGEN,5L)));
        var rest=ledger.order(id).orElseThrow();
        assertTrue(rest.cancelled());assertTrue(rest.quarantined());assertEquals(Map.of(WATER,100L),OrderLedger.held(rest));
        assertTrue(ledger.pendingRefunds().isEmpty());assertTrue(ledger.pendingOutputs().isEmpty());assertTrue(ledger.next().isEmpty());
        assertEquals(ledger.snapshot(),OrderLedgerCodec.read(OrderLedgerCodec.write(ledger)).ledger().orElseThrow().snapshot());
        assertTrue(ledger.releaseHeld(id,Map.of(WATER,100L)));
        assertTrue(ledger.isEmpty());assertFalse(ledger.releaseHeld(id,Map.of(WATER,100L)));
    }
    @Test void aResourceHeldAsInputAndOutputIsTakenFromTheInputsFirst() {
        var recipe=new Recipe("test:catalyst","revision1","chemical_reactor",Map.of(COPPER,10L),Map.of(COPPER,3L,OXYGEN,1L),1,1);
        var ledger=new OrderLedger();assertTrue(ledger.accept(recipe,2,Map.of(COPPER,20L),Action.EXECUTE));
        var id=ledger.next().orElseThrow().id();var worker=UUID.randomUUID();
        assertTrue(ledger.claim(id,worker,"revision1"));assertTrue(ledger.advance(id,worker,"revision1",1));assertTrue(ledger.complete(id,worker,"revision1"));
        assertTrue(ledger.release(id,worker));ledger.quarantineDelivery(id,"receiver_outcome_unknown");
        assertEquals(Map.of(COPPER,13L,OXYGEN,1L),OrderLedger.held(ledger.order(id).orElseThrow()));
        assertTrue(ledger.releaseHeld(id,Map.of(COPPER,12L)));
        var rest=ledger.order(id).orElseThrow();
        assertEquals(Map.of(),rest.ownedInputs());assertEquals(Map.of(COPPER,1L,OXYGEN,1L),rest.pendingOutputs());
    }
    private static void deliver(OrderLedger ledger,UUID id,Resource resource,long offered,long accepted) {
        assertTrue(ledger.beginDelivery(id,resource,offered));ledger.finishDelivery(id,resource,accepted);
    }
    private static void refund(OrderLedger ledger,UUID id,Resource resource,long offered,long accepted) {
        assertTrue(ledger.beginRefund(id,resource,offered));ledger.finishRefund(id,resource,accepted);
    }
    private static OrderLedger accepted(int batches) {
        var ledger=new OrderLedger();assertTrue(ledger.accept(recipe(),batches,ResourceAmounts.multiply(recipe().inputs(),batches),Action.EXECUTE));return ledger;
    }
    private static void completeOne(OrderLedger ledger,UUID id) {
        var worker=UUID.randomUUID();assertTrue(ledger.claim(id,worker,"revision1"));assertTrue(ledger.advance(id,worker,"revision1",120));assertTrue(ledger.complete(id,worker,"revision1"));
    }
}
