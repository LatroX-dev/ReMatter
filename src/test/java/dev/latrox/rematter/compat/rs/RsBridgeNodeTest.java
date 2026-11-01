package dev.latrox.rematter.compat.rs;

import com.refinedmods.refinedstorage.api.autocrafting.*;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.*;
import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.*;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.root.RootStorageImpl;
import dev.latrox.rematter.automation.*;
import dev.latrox.rematter.chemistry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.*;

/** Executes the real RS planner, storage cache and pattern sink without starting Minecraft. */
class RsBridgeNodeTest {
    @Test void nativePlannerAccountsForExactMatterAndMultiOutputsWithoutStartingWork() throws Exception {
        var port=new Port();var node=new RsBridgeNode(port);node.refreshCatalogue();
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
        var port=new Port();var node=new RsBridgeNode(port);node.refreshCatalogue();var pattern=node.patterns().getFirst();
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
        var port=new Port();var node=new RsBridgeNode(port);node.refreshCatalogue();var pattern=node.patterns().getFirst();
        assertEquals(ExternalPatternSink.Result.REJECTED,node.insertAll(pattern,List.of(new ResourceAmount(new RsMatterKey(new SubstanceKey(WATER.id(),Phase.GAS)),1000)),Action.EXECUTE));
        var root=new RootStorageImpl();root.addSource(node.getStorage());assertEquals(16000,root.get(new RsMatterKey(WATER)));
        port.active=false;node.refreshCatalogue();assertEquals(0,root.get(new RsMatterKey(WATER)));assertTrue(node.patterns().isEmpty());
        assertEquals(0,root.extract(new RsMatterKey(WATER),1000,Action.EXECUTE,Actor.EMPTY));
    }
    private static final class Port implements AutomationPort {
        final UUID owner=UUID.randomUUID(),bridge=UUID.randomUUID();final MatterInventory store=new MatterInventory(64000);
        final OrderLedger ledger=new OrderLedger();boolean active=true;
        final Recipe recipe=AutomationRecipeCatalogue.recipe("test:electrolysis","electrolyzer",Map.of(Resource.matter(WATER),1000L),
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
        public void quarantineDelivery(UUID id,String reason){ledger.quarantineDelivery(id,reason);}public void changed(){}
    }
}
