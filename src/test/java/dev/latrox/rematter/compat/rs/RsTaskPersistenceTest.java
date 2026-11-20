package dev.latrox.rematter.compat.rs;

import com.google.gson.JsonElement;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.refinedmods.refinedstorage.api.autocrafting.*;
import com.refinedmods.refinedstorage.api.autocrafting.calculation.*;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.autocrafting.task.*;
import com.refinedmods.refinedstorage.api.resource.*;
import com.refinedmods.refinedstorage.api.resource.list.*;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.StorageImpl;
import com.refinedmods.refinedstorage.api.storage.root.RootStorageImpl;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import net.minecraft.nbt.NbtOps;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RsTaskPersistenceTest {
    private record Key(String id,String components) implements ResourceKey {}
    private static final Codec<Key> KEY=RecordCodecBuilder.create(i->i.group(Codec.STRING.fieldOf("id").forGetter(Key::id),
            Codec.STRING.fieldOf("components").forGetter(Key::components)).apply(i,Key::new));
    private static final Codec<ResourceKey> RESOURCE=KEY.flatComapMap(value->(ResourceKey)value,
            value->value instanceof Key key?DataResult.success(key):DataResult.error(()->"Unknown resource type"));
    private static final Codec<List<TaskSnapshot>> CODEC=RsTaskPersistence.create(RESOURCE);

    @Test void nativeTaskStepsPersistExhaustedCountersAndKeepTransferredInputsOutOfRestoredCustody() {
        ResourceKey input=new Key("test:iron","{}"),output=new Key("test:ingot","{}");
        var pattern=new Pattern(UUID.randomUUID(),PatternLayout.external(List.of(new Ingredient(1000,List.of(input))),List.of(new ResourceAmount(output,1))));
        var stock=new StorageImpl();stock.insert(input,16000,Action.EXECUTE,Actor.EMPTY);
        var root=new RootStorageImpl();root.addSource(stock);
        var patterns=new PatternRepositoryImpl();patterns.add(pattern,0);
        var plan=TaskPlanCraftingCalculatorListener.calculatePlan(new CraftingCalculatorImpl(patterns,root),output,16,CancellationToken.NONE).orElseThrow();
        var task=new TaskImpl(plan,Actor.EMPTY,false);
        assertEquals(16000,stock.getStored());assertTrue(task.createSnapshot().internalStorage().isEmpty());
        long[] ownedBySink={0};var sinkId=ExternalPatternSinkId.create();
        ExternalPatternSink sink=new ExternalPatternSink(){
            public Result insertAll(Pattern p,Collection<ResourceAmount> resources,Action action){
                assertEquals(pattern,p);
                if(action==Action.EXECUTE)for(var value:resources){assertEquals(input,value.resource());ownedBySink[0]+=value.amount();}
                return Result.ACCEPTED;
            }
            public ExternalPatternSinkId getId(){return sinkId;}
            public ExternalPatternSinkDetails getDetails(){return null;}
        };
        for(int tick=0;tick<64&&ownedBySink[0]<16000;tick++){
            task.step(root,layout->List.of(sink),StepBehavior.DEFAULT,TaskListener.EMPTY);
            var snapshot=task.createSnapshot();
            var encoded=CODEC.encodeStart(NbtOps.INSTANCE,List.of(snapshot)).getOrThrow();
            var restored=CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow().getFirst();
            assertEquals(encoded,CODEC.encodeStart(NbtOps.INSTANCE,List.of(restored)).getOrThrow());
            task=new TaskImpl(restored);
        }
        assertEquals(16000,ownedBySink[0]);assertEquals(0,stock.getStored());
        var snapshot=task.createSnapshot();assertTrue(snapshot.internalStorage().isEmpty());
        assertEquals(0L,snapshot.patterns().get(pattern).ingredients().get(0).get(input));
        assertEquals(0,snapshot.patterns().get(pattern).externalPattern().iterationsToSendToSink());
        for(int tick=0;tick<3;tick++)task.step(root,layout->List.of(sink),StepBehavior.DEFAULT,TaskListener.EMPTY);
        assertEquals(16000,ownedBySink[0]);assertEquals(0,stock.getStored());
    }

    @Test void everyNativeTaskFieldAndArbitraryResourcePayloadRoundTripsWithoutMinecraftBootstrap() {
        var original=sample();JsonElement encoded=CODEC.encodeStart(JsonOps.INSTANCE,List.of(original)).getOrThrow();
        var restored=CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow().getFirst();
        assertEquals(encoded,CODEC.encodeStart(JsonOps.INSTANCE,List.of(restored)).getOrThrow());
        assertEquals(original.id(),restored.id());assertEquals(original.resource(),restored.resource());assertEquals(original.amount(),restored.amount());
        assertEquals(original.actor(),restored.actor());assertTrue(restored.notifyActor());assertTrue(restored.cancelled());
        assertEquals(TaskState.RETURNING_INTERNAL_STORAGE,restored.state());assertEquals(2,restored.patterns().size());
        assertEquals(List.copyOf(original.internalStorage().copyState()),List.copyOf(restored.internalStorage().copyState()));
        var internal=restored.patterns().values().stream().filter(p->p.internalPattern()!=null).findFirst().orElseThrow();
        assertEquals(2,internal.internalPattern().iterationsRemaining());assertEquals(1,internal.pattern().layout().byproducts().size());
        assertEquals(2,internal.pattern().layout().ingredients().getFirst().inputs().size());
        var external=restored.patterns().values().stream().filter(p->p.externalPattern()!=null).findFirst().orElseThrow().externalPattern();
        assertEquals(2,external.pendingSinkIds().size());assertEquals(ExternalPatternSink.Result.LOCKED,external.lastSinkResult());
        assertEquals(1,external.iterationsReceived());assertTrue(external.interceptedAnythingSinceLastStep());
    }
    @Test void unknownFieldsOrOneUnknownResourceRejectTheWholeListWithoutPartialTasks() {
        var encoded=CODEC.encodeStart(JsonOps.INSTANCE,List.of(sample())).getOrThrow().getAsJsonObject();
        var extra=encoded.deepCopy();extra.addProperty("future_schema_field",1);assertTrue(CODEC.parse(JsonOps.INSTANCE,extra).result().isEmpty());
        var unknown=encoded.deepCopy();var second=unknown.getAsJsonArray("tasks").get(0).deepCopy();
        second.getAsJsonObject().addProperty("id",UUID.randomUUID().toString());second.getAsJsonObject().getAsJsonObject("resource").remove("components");
        unknown.getAsJsonArray("tasks").add(second);
        var result=CODEC.parse(JsonOps.INSTANCE,unknown);assertTrue(result.result().isEmpty());assertTrue(result.error().isPresent());
        assertTrue(result.error().orElseThrow().partialValue().isEmpty());
    }
    @Test void duplicateTasksUnknownNestedFieldsAndFractionalCountersAreRejected() {
        var encoded=CODEC.encodeStart(JsonOps.INSTANCE,List.of(sample())).getOrThrow().getAsJsonObject();
        var duplicate=encoded.deepCopy();duplicate.getAsJsonArray("tasks").add(duplicate.getAsJsonArray("tasks").get(0).deepCopy());
        assertTrue(CODEC.parse(JsonOps.INSTANCE,duplicate).result().isEmpty());
        var fraction=encoded.deepCopy();fraction.getAsJsonArray("tasks").get(0).getAsJsonObject().addProperty("amount",1.5);
        assertTrue(CODEC.parse(JsonOps.INSTANCE,fraction).result().isEmpty());
        var extra=encoded.deepCopy();extra.getAsJsonArray("tasks").get(0).getAsJsonObject().addProperty("future",true);
        assertTrue(CODEC.parse(JsonOps.INSTANCE,extra).result().isEmpty());
        var negative=encoded.deepCopy();
        negative.getAsJsonArray("tasks").get(0).getAsJsonObject().getAsJsonArray("patterns").get(0).getAsJsonObject()
                .getAsJsonObject("state").getAsJsonArray("ingredients").get(0).getAsJsonObject()
                .getAsJsonArray("possibilities").get(0).getAsJsonObject().addProperty("amount",-1);
        assertTrue(CODEC.parse(JsonOps.INSTANCE,negative).result().isEmpty());
    }
    @Test void foreignSinkDisplayDetailsAreOmittedWithoutLosingTaskState() {
        var task=sample();var entries=new LinkedHashMap<>(task.patterns());
        var selected=entries.entrySet().stream().filter(e->e.getValue().externalPattern()!=null).findFirst().orElseThrow();
        var old=selected.getValue();var external=old.externalPattern();
        var unknown=new TaskSnapshot.ExternalPatternSnapshot(external.expectedOutputs(),external.simulatedIterationInputs(),5,2,1,true,
                external.lastSinkResult(),new ExternalPatternSinkDetails(){},external.pendingSinkIds());
        entries.put(selected.getKey(),new TaskSnapshot.PatternSnapshot(old.root(),old.pattern(),old.ingredients(),null,unknown));
        var changed=new TaskSnapshot(task.id(),task.resource(),task.amount(),Actor.EMPTY,false,task.startTime(),entries,task.completedPatterns(),
                task.initialRequirements(),task.internalStorage(),task.state(),task.cancelled());
        var encoded=CODEC.encodeStart(JsonOps.INSTANCE,List.of(changed)).getOrThrow();
        var restored=CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow().getFirst();
        var restoredExternal=restored.patterns().get(selected.getKey()).externalPattern();
        assertNull(restoredExternal.lastSinkDetails());
        assertEquals(unknown.pendingSinkIds().size(),restoredExternal.pendingSinkIds().size());
        assertEquals(unknown.iterationsToSendToSink(),restoredExternal.iterationsToSendToSink());
        assertEquals(List.copyOf(changed.internalStorage().copyState()),List.copyOf(restored.internalStorage().copyState()));
    }
    private static TaskSnapshot sample() {
        ResourceKey a=new Key("othermod:sample","{custom_name:A,damage:3}"),b=new Key("othermod:sample","{custom_name:B,damage:9}");
        ResourceKey gas=new Key("custom:gas","{phase:gas,variant:isotope}"),result=new Key("othermod:product","{opaque:[1,2,3]}"),bucket=new Key("minecraft:bucket","{}");
        var internal=new Pattern(UUID.randomUUID(),PatternLayout.internal(List.of(new Ingredient(2,List.of(a,b))),List.of(new ResourceAmount(result,1)),List.of(new ResourceAmount(bucket,1))));
        var external=new Pattern(UUID.randomUUID(),PatternLayout.external(List.of(new Ingredient(3,List.of(gas))),List.of(new ResourceAmount(result,2))));
        var patterns=new LinkedHashMap<Pattern,TaskSnapshot.PatternSnapshot>();
        patterns.put(internal,new TaskSnapshot.PatternSnapshot(false,internal,Map.of(0,Map.of(a,1L,b,2L)),new TaskSnapshot.InternalPatternSnapshot(3,2),null));
        var state=new TaskSnapshot.ExternalPatternSnapshot(resources(result,4),resources(gas,3),5,2,1,true,ExternalPatternSink.Result.LOCKED,null,
                new ArrayDeque<>(List.of(ExternalPatternSinkId.create(),ExternalPatternSinkId.create())));
        patterns.put(external,new TaskSnapshot.PatternSnapshot(true,external,Map.of(0,Map.of(gas,3L)),null,state));
        var complete=new TaskSnapshot.PatternSnapshot(false,internal,Map.of(),new TaskSnapshot.InternalPatternSnapshot(1,0),null);
        var storage=MutableResourceListImpl.orderPreserving();storage.add(a,7);storage.add(b,3);
        return new TaskSnapshot(TaskId.create(),result,10,new PlayerActor("test-player"),true,12345,patterns,List.of(complete),resources(gas,15),storage,
                TaskState.RETURNING_INTERNAL_STORAGE,true);
    }
    private static ResourceList resources(ResourceKey key,long amount) {var list=MutableResourceListImpl.orderPreserving();list.add(key,amount);return list;}
}
