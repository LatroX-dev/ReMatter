package dev.latrox.rematter.compat.rs;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.refinedmods.refinedstorage.api.autocrafting.*;
import com.refinedmods.refinedstorage.api.autocrafting.task.*;
import com.refinedmods.refinedstorage.api.resource.*;
import com.refinedmods.refinedstorage.api.resource.list.*;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.autocrafting.autocrafter.AutocrafterExternalPatternSinkDetails;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;
import net.minecraft.world.item.ItemStack;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/** Complete native task snapshots through public RS APIs; one unreadable task rejects the entire payload. */
public final class RsTaskPersistence {
    public static final int MAX_TASKS=64, MAX_PATTERNS=4096, MAX_RESOURCES=65536;
    public static final Codec<List<TaskSnapshot>> CODEC=Codec.lazyInitialized(()->create(ResourceCodecs.NATIVE_CODEC));
    private RsTaskPersistence() {}

    /** Injection keeps codec tests independent of Minecraft's registry bootstrap. Production uses RS's resource registry. */
    static Codec<List<TaskSnapshot>> create(Codec<ResourceKey> resources) {
        return new Codec<>() {
            @Override public <T> DataResult<Pair<List<TaskSnapshot>,T>> decode(DynamicOps<T> ops,T input) {
                try {
                    var reader=new Reader<>(ops,resources);var root=reader.object(input,Set.of("schema","tasks"),Set.of());
                    if(reader.number(root.get("schema"))!=1)throw new IllegalArgumentException("Unknown task schema");
                    var tasks=reader.list(root.get("tasks"),MAX_TASKS,reader::task);var ids=new HashSet<TaskId>();
                    for(var task:tasks)if(!ids.add(task.id()))throw new IllegalArgumentException("Duplicate task");
                    return DataResult.success(Pair.of(tasks,ops.empty()));
                }catch(RuntimeException invalid){return DataResult.error(()->"Unsupported or invalid RS task payload");}
            }
            @Override public <T> DataResult<T> encode(List<TaskSnapshot> tasks,DynamicOps<T> ops,T prefix) {
                try {
                    if(tasks.size()>MAX_TASKS)throw new IllegalArgumentException("Too many native tasks");
                    var writer=new Writer<>(ops,resources);var ids=new HashSet<TaskId>();
                    for(var task:tasks)if(!ids.add(task.id()))throw new IllegalArgumentException("Duplicate task");
                    T value=writer.object(Map.of("schema",ops.createInt(1),"tasks",writer.list(tasks,writer::task)));
                    return ops.mergeToMap(prefix,ops.getMap(value).getOrThrow());
                }catch(RuntimeException invalid){return DataResult.error(()->"Unsupported or invalid RS task payload");}
            }
        };
    }
    private record NamedActor(String name) implements Actor { @Override public String getName(){return name;} }

    private static final class Writer<T> {
        private final DynamicOps<T> ops;private final Codec<ResourceKey> resources;
        Writer(DynamicOps<T> ops,Codec<ResourceKey> resources){this.ops=ops;this.resources=resources;}
        T object(Map<String,T> fields){return ops.createMap(fields.entrySet().stream().map(e->Pair.of(ops.createString(e.getKey()),e.getValue())));}
        <E>T list(Collection<E> values,Function<E,T> encode){return ops.createList(values.stream().map(encode));}
        T string(String value){return ops.createString(value);}
        T number(long value){return ops.createLong(value);}
        T resource(ResourceKey value){return resources.encodeStart(ops,value).getOrThrow();}
        T amounts(Collection<ResourceAmount> values){
            if(values.size()>MAX_RESOURCES)throw new IllegalArgumentException("Too many resources");
            return list(values,value->object(Map.of("resource",resource(value.resource()),"amount",number(value.amount()))));
        }
        T resourceList(ResourceList value){return amounts(value.copyState());}
        T counters(Map<ResourceKey,Long> values){
            if(values.size()>MAX_RESOURCES)throw new IllegalArgumentException("Too many ingredient alternatives");
            return list(values.entrySet(),entry->{
                if(entry.getValue()<0)throw new IllegalArgumentException("Negative ingredient counter");
                return object(Map.of("resource",resource(entry.getKey()),"amount",number(entry.getValue())));
            });
        }
        T pattern(Pattern pattern){
            var layout=pattern.layout();if(layout.ingredients().size()>MAX_RESOURCES)throw new IllegalArgumentException("Too many ingredients");
            for(var ingredient:layout.ingredients())if(ingredient.inputs().size()>MAX_RESOURCES)throw new IllegalArgumentException("Too many ingredient alternatives");
            return object(Map.of("id",string(pattern.id().toString()),"type",string(layout.type().name()),
                    "ingredients",list(layout.ingredients(),ingredient->object(Map.of("amount",number(ingredient.amount()),"inputs",list(ingredient.inputs(),this::resource)))),
                    "outputs",amounts(layout.outputs()),"byproducts",amounts(layout.byproducts())));
        }
        T patternSnapshot(TaskSnapshot.PatternSnapshot snapshot){
            if(snapshot.ingredients().size()>MAX_RESOURCES)throw new IllegalArgumentException("Too many ingredient states");
            var fields=new LinkedHashMap<String,T>();fields.put("root",ops.createBoolean(snapshot.root()));fields.put("pattern",pattern(snapshot.pattern()));
            fields.put("ingredients",list(snapshot.ingredients().entrySet(),entry->object(Map.of("index",number(entry.getKey()),
                    "possibilities",counters(entry.getValue())))));
            if((snapshot.internalPattern()==null)==(snapshot.externalPattern()==null))throw new IllegalArgumentException("Invalid task pattern kind");
            if(snapshot.internalPattern()!=null){var value=snapshot.internalPattern();fields.put("internal",object(Map.of("original",number(value.originalIterationsRemaining()),"remaining",number(value.iterationsRemaining()))));}
            if(snapshot.externalPattern()!=null)fields.put("external",external(snapshot.externalPattern()));
            return object(fields);
        }
        T external(TaskSnapshot.ExternalPatternSnapshot snapshot){
            var fields=new LinkedHashMap<String,T>();fields.put("expected",resourceList(snapshot.expectedOutputs()));
            fields.put("simulated",resourceList(snapshot.simulatedIterationInputs()));fields.put("original",number(snapshot.originalIterationsToSendToSink()));
            fields.put("remaining",number(snapshot.iterationsToSendToSink()));fields.put("received",number(snapshot.iterationsReceived()));
            fields.put("intercepted",ops.createBoolean(snapshot.interceptedAnythingSinceLastStep()));
            if(snapshot.lastSinkResult()!=null)fields.put("result",string(snapshot.lastSinkResult().name()));
            if(snapshot.lastSinkDetails()!=null) {
                if(!(snapshot.lastSinkDetails() instanceof AutocrafterExternalPatternSinkDetails details))throw new IllegalArgumentException("Unknown native sink details");
                fields.put("details",object(Map.of("name",string(details.name()),"stack",ItemStack.OPTIONAL_CODEC.encodeStart(ops,details.stack()).getOrThrow())));
            }
            if(snapshot.pendingSinkIds().size()>MAX_RESOURCES)throw new IllegalArgumentException("Too many sink receipts");
            fields.put("sinks",list(snapshot.pendingSinkIds(),id->string(id.id().toString())));return object(fields);
        }
        T task(TaskSnapshot task){
            if(task.patterns().size()>MAX_PATTERNS||task.completedPatterns().size()>MAX_PATTERNS)throw new IllegalArgumentException("Too many patterns");
            var fields=new LinkedHashMap<String,T>();fields.put("id",string(task.id().id().toString()));fields.put("resource",resource(task.resource()));
            fields.put("amount",number(task.amount()));fields.put("notify",ops.createBoolean(task.notifyActor()));fields.put("started",number(task.startTime()));
            String actorType=task.actor()==Actor.EMPTY?"empty":task.actor() instanceof PlayerActor?"player":"named";
            fields.put("actor",object(Map.of("kind",string(actorType),"name",string(task.actor().getName()))));
            fields.put("patterns",list(task.patterns().entrySet(),entry->object(Map.of("key",pattern(entry.getKey()),"state",patternSnapshot(entry.getValue())))));
            fields.put("completed",list(task.completedPatterns(),this::patternSnapshot));fields.put("initial",resourceList(task.initialRequirements()));
            fields.put("storage",resourceList(task.internalStorage()));fields.put("state",string(task.state().name()));fields.put("cancelled",ops.createBoolean(task.cancelled()));
            return object(fields);
        }
    }
    private static final class Reader<T> {
        private final DynamicOps<T> ops;private final Codec<ResourceKey> resources;
        Reader(DynamicOps<T> ops,Codec<ResourceKey> resources){this.ops=ops;this.resources=resources;}
        Map<String,T> object(T value,Set<String> required,Set<String> optional){
            var result=new LinkedHashMap<String,T>();
            ops.getMap(value).getOrThrow().entries().forEach(entry->{
                String key=string(entry.getFirst());if(!required.contains(key)&&!optional.contains(key)||result.putIfAbsent(key,entry.getSecond())!=null)
                    throw new IllegalArgumentException("Unknown or duplicate field");
            });
            if(!result.keySet().containsAll(required))throw new IllegalArgumentException("Missing field");return result;
        }
        <E>List<E> list(T value,int maximum,Function<T,E> decode){
            var values=ops.getStream(value).getOrThrow().limit((long)maximum+1).toList();
            if(values.size()>maximum)throw new IllegalArgumentException("List exceeds bound");return values.stream().map(decode).toList();
        }
        String string(T value){return ops.getStringValue(value).getOrThrow();}
        long number(T value){return new BigDecimal(ops.getNumberValue(value).getOrThrow().toString()).toBigIntegerExact().longValueExact();}
        long nonnegative(T value){long number=number(value);if(number<0)throw new IllegalArgumentException("Negative quantity");return number;}
        boolean bool(T value){return ops.getBooleanValue(value).getOrThrow();}
        ResourceKey resource(T value){return resources.parse(ops,value).getOrThrow();}
        List<ResourceAmount> amounts(T value){return list(value,MAX_RESOURCES,entry->{var row=object(entry,Set.of("resource","amount"),Set.of());return new ResourceAmount(resource(row.get("resource")),nonnegative(row.get("amount")));});}
        Map<ResourceKey,Long> amountMap(T value){
            var result=new LinkedHashMap<ResourceKey,Long>();
            for(var row:amounts(value))if(result.putIfAbsent(row.resource(),row.amount())!=null)throw new IllegalArgumentException("Duplicate resource key");return result;
        }
        Map<ResourceKey,Long> counters(T value){
            var result=new LinkedHashMap<ResourceKey,Long>();
            for(var entry:list(value,MAX_RESOURCES,Function.identity())){
                var row=object(entry,Set.of("resource","amount"),Set.of());
                if(result.putIfAbsent(resource(row.get("resource")),nonnegative(row.get("amount")))!=null)
                    throw new IllegalArgumentException("Duplicate ingredient alternative");
            }
            return result;
        }
        ResourceList resourceList(T value){
            var list=MutableResourceListImpl.orderPreserving();amountMap(value).forEach(list::add);return list;
        }
        Pattern pattern(T value){
            var row=object(value,Set.of("id","type","ingredients","outputs","byproducts"),Set.of());
            var ingredients=list(row.get("ingredients"),MAX_RESOURCES,element->{var ingredient=object(element,Set.of("amount","inputs"),Set.of());
                return new Ingredient(nonnegative(ingredient.get("amount")),list(ingredient.get("inputs"),MAX_RESOURCES,this::resource));});
            return new Pattern(UUID.fromString(string(row.get("id"))),new PatternLayout(ingredients,amounts(row.get("outputs")),amounts(row.get("byproducts")),PatternType.valueOf(string(row.get("type")))));
        }
        TaskSnapshot.PatternSnapshot patternSnapshot(T value){
            var row=object(value,Set.of("root","pattern","ingredients"),Set.of("internal","external"));
            if(row.containsKey("internal")==row.containsKey("external"))throw new IllegalArgumentException("Invalid task pattern kind");
            var ingredients=new LinkedHashMap<Integer,Map<ResourceKey,Long>>();
            for(var element:list(row.get("ingredients"),MAX_RESOURCES,Function.identity())){
                var entry=object(element,Set.of("index","possibilities"),Set.of());int index=Math.toIntExact(nonnegative(entry.get("index")));
                if(ingredients.putIfAbsent(index,counters(entry.get("possibilities")))!=null)throw new IllegalArgumentException("Duplicate ingredient index");
            }
            TaskSnapshot.InternalPatternSnapshot internal=null;
            if(row.containsKey("internal")){var state=object(row.get("internal"),Set.of("original","remaining"),Set.of());internal=new TaskSnapshot.InternalPatternSnapshot(nonnegative(state.get("original")),nonnegative(state.get("remaining")));}
            return new TaskSnapshot.PatternSnapshot(bool(row.get("root")),pattern(row.get("pattern")),ingredients,internal,row.containsKey("external")?external(row.get("external")):null);
        }
        TaskSnapshot.ExternalPatternSnapshot external(T value){
            var row=object(value,Set.of("expected","simulated","original","remaining","received","intercepted","sinks"),Set.of("result","details"));
            ExternalPatternSinkDetails details=null;
            if(row.containsKey("details")){var saved=object(row.get("details"),Set.of("name","stack"),Set.of());details=new AutocrafterExternalPatternSinkDetails(string(saved.get("name")),ItemStack.OPTIONAL_CODEC.parse(ops,saved.get("stack")).getOrThrow());}
            return new TaskSnapshot.ExternalPatternSnapshot(resourceList(row.get("expected")),resourceList(row.get("simulated")),
                    nonnegative(row.get("original")),nonnegative(row.get("remaining")),nonnegative(row.get("received")),bool(row.get("intercepted")),
                    row.containsKey("result")?ExternalPatternSink.Result.valueOf(string(row.get("result"))):null,details,
                    new ArrayDeque<>(list(row.get("sinks"),MAX_RESOURCES,entry->new ExternalPatternSinkId(UUID.fromString(string(entry))))));
        }
        TaskSnapshot task(T value){
            var row=object(value,Set.of("id","resource","amount","notify","started","actor","patterns","completed","initial","storage","state","cancelled"),Set.of());
            var actor=object(row.get("actor"),Set.of("kind","name"),Set.of());String name=string(actor.get("name"));
            Actor restoredActor=switch(string(actor.get("kind"))){case "empty"->Actor.EMPTY;case "player"->new PlayerActor(name);case "named"->new NamedActor(name);default->throw new IllegalArgumentException("Unknown actor kind");};
            var patterns=new LinkedHashMap<Pattern,TaskSnapshot.PatternSnapshot>();
            for(var element:list(row.get("patterns"),MAX_PATTERNS,Function.identity())){
                var entry=object(element,Set.of("key","state"),Set.of());var key=pattern(entry.get("key"));var state=patternSnapshot(entry.get("state"));
                if(!key.equals(state.pattern())||!key.layout().equals(state.pattern().layout())||patterns.putIfAbsent(key,state)!=null)throw new IllegalArgumentException("Invalid pattern identity");
            }
            return new TaskSnapshot(new TaskId(UUID.fromString(string(row.get("id")))),resource(row.get("resource")),nonnegative(row.get("amount")),restoredActor,
                    bool(row.get("notify")),number(row.get("started")),patterns,list(row.get("completed"),MAX_PATTERNS,this::patternSnapshot),
                    resourceList(row.get("initial")),resourceList(row.get("storage")),TaskState.valueOf(string(row.get("state"))),bool(row.get("cancelled")));
        }
    }
}
