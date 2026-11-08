package dev.latrox.rematter.compat.rs;

import com.refinedmods.refinedstorage.api.autocrafting.*;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskImpl;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskPlanCraftingCalculatorListener;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskSnapshot;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.*;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.root.RootStorageImpl;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import dev.latrox.rematter.automation.*;
import dev.latrox.rematter.chemistry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;

/** Executes the real RS planner, storage cache and pattern sink without starting Minecraft. */
class RsBridgeNodeTest {
    @Test void nativePlannerAccountsForExactMatterAndMultiOutputsWithoutStartingWork() throws Exception {
        var port=new Port();var node=node(port);node.refreshCatalogue();
        var storage=new RootStorageImpl();storage.addSource(node.getStorage());
        var repository=new PatternRepositoryImpl();node.patterns().forEach(p->repository.add(p,0));
        class Plan implements CraftingCalculatorListener<Void> {
            long extracted,missing;
            public CraftingCalculatorListener<Void> childCalculationStarted(Pattern p,ResourceKey r,Amount a){return this;}
            public void ingredientExtractedFromStorage(ResourceKey r,long amount){assertEquals(new RsMatterKey(WATER),r);extracted+=amount;}
            public void ingredientsExhausted(ResourceKey r,long amount){missing+=amount;}
            public Void getData(){return null;}
        }
        var plan=new Plan();new CraftingCalculatorImpl(repository,storage).calculate(new RsMatterKey(HYDROGEN),32000,plan,CancellationToken.NONE);
        assertEquals(16000,plan.extracted);assertEquals(0,plan.missing);assertEquals(16000,port.store.amount(WATER));assertEquals(0,port.ledger.remainingBatches());
    }
    @Test void realSinkOwnsInputsOnceAndOnlyAcknowledgesAcceptedOutputs() {
        var port=new Port();var node=node(port);node.refreshCatalogue();var pattern=node.patterns().getFirst();
        var inputs=List.of(new ResourceAmount(new RsMatterKey(WATER),2000));
        assertEquals(ExternalPatternSink.Result.ACCEPTED,node.insertAll(pattern,inputs,Action.SIMULATE));
        assertEquals(0,port.ledger.remainingBatches());
        assertEquals(2000,node.getStorage().extract(new RsMatterKey(WATER),2000,Action.EXECUTE,Actor.EMPTY));
        assertEquals(ExternalPatternSink.Result.ACCEPTED,node.insertAll(pattern,inputs,Action.EXECUTE));
        assertEquals(14000,port.store.amount(WATER));assertEquals(2,port.ledger.remainingBatches());
        var order=port.ledger.next().orElseThrow();var worker=UUID.randomUUID();
        assertTrue(port.ledger.claim(order.id(),worker,order.recipe().revision()));
        for(int i=0;i<2;i++){assertTrue(port.ledger.advance(order.id(),worker,order.recipe().revision(),20));assertTrue(port.ledger.complete(order.id(),worker,order.recipe().revision()));}
        node.flushOutputs((resource,amount,action,actor)->Math.min(500,amount));
        assertEquals(5000,port.pendingOutputs().stream().mapToLong(AutomationPort.Delivery::amount).sum());
        node.flushOutputs((resource,amount,action,actor)->{throw new IllegalStateException("Ambiguous external write");});
        assertTrue(port.pendingOutputs().isEmpty());assertTrue(port.ledger.snapshot().orders().getFirst().quarantined());
    }
    @Test void stalePhasesRevisionsAndRevokedCacheCannotSupplyResources() {
        var port=new Port();var node=node(port);node.refreshCatalogue();var pattern=node.patterns().getFirst();
        assertEquals(ExternalPatternSink.Result.REJECTED,node.insertAll(pattern,List.of(new ResourceAmount(new RsMatterKey(new SubstanceKey(WATER.id(),Phase.GAS)),1000)),Action.EXECUTE));
        var root=new RootStorageImpl();root.addSource(node.getStorage());assertEquals(16000,root.get(new RsMatterKey(WATER)));
        port.active=false;node.refreshCatalogue();assertEquals(0,root.get(new RsMatterKey(WATER)));assertTrue(node.patterns().isEmpty());
        assertEquals(0,root.extract(new RsMatterKey(WATER),1000,Action.EXECUTE,Actor.EMPTY));
    }
    @Test void theSixtyFifthNativeTaskIsCancelledInsteadOfThrowingAndTheQueueStaysPersistable() {
        var port=new Port();var node=node(port);node.refreshCatalogue();
        var refusals=new int[1];
        node.setTaskAdmission(task->node.getTasks().size()<=RsTaskPersistence.MAX_TASKS
                &&RsTaskPersistence.encode(TASKS,JsonOps.INSTANCE,node.getTasks()).isPresent(),()->refusals[0]++);
        var storage=new RootStorageImpl();storage.addSource(node.getStorage());
        var repository=new PatternRepositoryImpl();node.patterns().forEach(p->repository.add(p,0));
        var plan=TaskPlanCraftingCalculatorListener.calculatePlan(new CraftingCalculatorImpl(repository,storage),
                new RsMatterKey(HYDROGEN),2000,CancellationToken.NONE).orElseThrow();
        for(int i=0;i<RsTaskPersistence.MAX_TASKS;i++)node.addTask(new TaskImpl(plan,Actor.EMPTY,false));
        var refused=new TaskImpl(plan,Actor.EMPTY,false);
        assertDoesNotThrow(()->node.addTask(refused));
        assertEquals(1,refusals[0]);
        assertEquals(RsTaskPersistence.MAX_TASKS+1,node.getTasks().size());
        assertTrue(refused.createSnapshot().cancelled());assertTrue(refused.createSnapshot().internalStorage().isEmpty());
        assertTrue(node.getTasks().stream().filter(task->task!=refused).noneMatch(task->((TaskImpl)task).createSnapshot().cancelled()));
        var saved=RsTaskPersistence.encode(TASKS,JsonOps.INSTANCE,node.getTasks()).orElseThrow();
        assertEquals(RsTaskPersistence.MAX_TASKS,TASKS.parse(JsonOps.INSTANCE,saved).getOrThrow().size());
        assertEquals(16000,port.store.amount(WATER),"A refused task never extracts matter");
        node.addTask(new TaskImpl(plan,Actor.EMPTY,false));
        assertEquals(RsTaskPersistence.MAX_TASKS,TASKS.parse(JsonOps.INSTANCE,
                RsTaskPersistence.encode(TASKS,JsonOps.INSTANCE,node.getTasks()).orElseThrow()).getOrThrow().size());
    }
    @Test void foreignPlayerActorsGetNothingWhileTheOwnerAndNetworkActorsKeepAccess() {
        var port=new Port();var node=node(port);var storage=node.getStorage();var water=new RsMatterKey(WATER);
        var foreign=new PlayerActor("intruder");
        assertEquals(0,storage.extract(water,1000,Action.EXECUTE,foreign));
        assertEquals(0,storage.insert(water,1000,Action.EXECUTE,foreign));
        assertEquals(16000,port.store.amount(WATER));
        assertEquals(1000,storage.extract(water,1000,Action.EXECUTE,new PlayerActor(OWNER)));
        assertEquals(1000,storage.insert(water,1000,Action.EXECUTE,Actor.EMPTY));
        assertEquals(16000,port.store.amount(WATER));
        node.refreshCatalogue();
        var root=new RootStorageImpl();root.addSource(storage);
        var repository=new PatternRepositoryImpl();node.patterns().forEach(p->repository.add(p,0));
        var plan=TaskPlanCraftingCalculatorListener.calculatePlan(new CraftingCalculatorImpl(repository,root),
                new RsMatterKey(HYDROGEN),2000,CancellationToken.NONE).orElseThrow();
        var stolen=new TaskImpl(plan,foreign,true);var owned=new TaskImpl(plan,new PlayerActor(OWNER),true);
        var refusals=new int[1];node.setTaskAdmission(task->true,()->refusals[0]++);
        node.addTask(stolen);node.addTask(owned);
        assertEquals(1,refusals[0]);
        assertTrue(stolen.createSnapshot().cancelled(),"A foreign player's job never starts");
        assertFalse(owned.createSnapshot().cancelled());
        assertFalse(RsBridgeNode.permitted(new PlayerActor(OWNER),name->false),"An unresolvable name counts as foreign");
    }
    @Test void cancelledOrdersReturnTheirExactTransferredInputsThroughTheGuardedPath() {
        var port=new Port();var node=node(port);node.refreshCatalogue();var pattern=node.patterns().getFirst();
        assertEquals(2000,node.getStorage().extract(new RsMatterKey(WATER),2000,Action.EXECUTE,Actor.EMPTY));
        assertEquals(ExternalPatternSink.Result.ACCEPTED,node.insertAll(pattern,List.of(new ResourceAmount(new RsMatterKey(WATER),2000)),Action.EXECUTE));
        var id=port.ledger.next().orElseThrow().id();
        assertTrue(port.ledger.cancel(id));
        var received=new ArrayList<ResourceAmount>();
        node.flushOutputs((resource,amount,action,actor)->{received.add(new ResourceAmount(resource,Math.min(amount,1500)));return Math.min(amount,1500);});
        assertEquals(List.of(new ResourceAmount(new RsMatterKey(WATER),1500)),received);
        assertEquals(List.of(new AutomationPort.Delivery(id,AutomationPort.Resource.matter(WATER),500)),port.pendingRefunds());
        node.flushOutputs((resource,amount,action,actor)->amount);
        assertTrue(port.ledger.snapshot().orders().isEmpty(),"Every transferred input was returned exactly once");
    }
    private static final String OWNER="owner";
    private static RsBridgeNode node(Port port) { return new RsBridgeNode(port,actor->RsBridgeNode.permitted(actor,OWNER::equals)); }
    private static final Codec<List<TaskSnapshot>> TASKS=RsTaskPersistence.create(RsMatterKey.CODEC.codec().flatComapMap(key->(ResourceKey)key,
            key->key instanceof RsMatterKey matter?DataResult.success(matter):DataResult.error(()->"Not matter")));
    private static final class Port implements AutomationPort {
        final UUID owner=UUID.randomUUID(),bridge=UUID.randomUUID();final MatterInventory store=new MatterInventory(64000);
        final OrderLedger ledger=new OrderLedger();boolean active=true;
        final Recipe recipe=AutomationRecipeCatalogue.recipe("test:electrolysis","electrolyzer",Map.of(AutomationPort.Resource.matter(WATER),1000L),
                Map.of(Resource.matter(HYDROGEN),2000L,Resource.matter(OXYGEN),1000L),4000,20);
        Port(){store.insert(WATER,16000);}
        public UUID ownerId(){return owner;}public UUID bridgeId(){return bridge;}public boolean active(){return active;}
        public Map<SubstanceKey,Long> availableEntries(){return store.availableContents();}
        public long insert(SubstanceKey k,long amount,AutomationPort.Action mode){return !active?0:mode==AutomationPort.Action.SIMULATE?Math.min(amount,store.freeSpace()):store.insert(k,amount);}
        public long extract(SubstanceKey k,long amount,AutomationPort.Action mode){return !active?0:mode==AutomationPort.Action.SIMULATE?Math.min(amount,store.available(k)):store.extract(k,amount);}
        public List<Recipe> recipes(){return List.of(recipe);}public boolean busy(){return ledger.busy();}
        public boolean acceptTransferred(String id,String revision,int batches,Map<Resource,Long> inputs,AutomationPort.Action mode){return active&&id.equals(recipe.id())&&revision.equals(recipe.revision())&&ledger.accept(recipe,batches,inputs,mode);}
        public List<Delivery> pendingOutputs(){return ledger.pendingOutputs();}
        public boolean beginDelivery(UUID id,Resource resource,long amount){return active&&ledger.beginDelivery(id,resource,amount);}
        public void ackOutput(UUID id,Resource resource,long amount){ledger.finishDelivery(id,resource,amount);}
        public List<Delivery> pendingRefunds(){return ledger.pendingRefunds();}
        public boolean beginRefund(UUID id,Resource resource,long amount){return active&&ledger.beginRefund(id,resource,amount);}
        public void ackRefund(UUID id,Resource resource,long amount){ledger.finishRefund(id,resource,amount);}
        public void quarantineDelivery(UUID id,String reason){ledger.quarantineDelivery(id,reason);}public void changed(){}
    }
}
