package dev.latrox.rematter.automation;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.*;
import dev.latrox.rematter.automation.AutomationPort.*;

/** Versioned, strict JSON. Unknown or malformed payloads stay opaque and must not become an empty queue. */
public final class OrderLedgerCodec {
    public static final int MAX_LENGTH=1_048_576;
    private OrderLedgerCodec() {}
    public record Loaded(Optional<OrderLedger> ledger,String raw) {
        public Loaded {Objects.requireNonNull(ledger);Objects.requireNonNull(raw);}
        public boolean readable() {return ledger.isPresent();}
        public String write() {return ledger.map(OrderLedgerCodec::write).orElse(raw);}
    }
    public static Loaded read(String raw) {
        Objects.requireNonNull(raw);
        if(raw.length()>MAX_LENGTH)return new Loaded(Optional.empty(),raw);
        try(var reader=new JsonReader(new StringReader(raw))) {
            reader.setStrictness(Strictness.STRICT);
            var value=readValue(reader,0,new int[]{0});
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing ledger data");
            var object=object(value,"schema","orders");
            int schema=integer(object,"schema");
            var rows=array(object,"orders",OrderLedger.MAX_ORDERS);var orders=new ArrayList<OrderLedger.Order>();
            for(var row:rows)orders.add(order(row));
            return new Loaded(Optional.of(OrderLedger.restore(new OrderLedger.Snapshot(schema,orders))),raw);
        }catch(Exception unknown){return new Loaded(Optional.empty(),raw);}
    }
    public static String write(OrderLedger ledger) {
        var snapshot=ledger.snapshot();var root=new JsonObject();root.addProperty("schema",snapshot.schema());
        var rows=new JsonArray();for(var order:snapshot.orders())rows.add(order(order));root.add("orders",rows);
        String json=root.toString();if(json.length()>MAX_LENGTH)throw new IllegalStateException("Ledger exceeds persistence bound");return json;
    }
    /** Reserve enough encoded space for all future outputs, acknowledgements and quarantine metadata at acceptance. */
    static boolean fits(OrderLedger.Snapshot snapshot) {
        long length=64;
        for(var order:snapshot.orders()) {
            var envelope=order(order);
            envelope.add("inputs",amounts(ResourceAmounts.multiply(order.recipe().inputs(),order.batches())));
            envelope.add("outputs",amounts(ResourceAmounts.multiply(order.recipe().outputs(),order.batches())));
            envelope.addProperty("progress",order.recipe().ticks());
            envelope.addProperty("worker","00000000-0000-0000-0000-000000000000");
            envelope.addProperty("quarantine","\u0000".repeat(256));
            length+=envelope.toString().length()+2;
            if(length>MAX_LENGTH)return false;
        }return true;
    }
    public static JsonObject writeRecipe(Recipe recipe) {
        var json=new JsonObject();json.addProperty("id",recipe.id());json.addProperty("revision",recipe.revision());json.addProperty("worker",recipe.workerKind());
        json.add("inputs",amounts(recipe.inputs()));json.add("outputs",amounts(recipe.outputs()));json.addProperty("energy",recipe.energy());json.addProperty("ticks",recipe.ticks());json.addProperty("requires_advancement",recipe.requiresAdvancement());json.addProperty("required_tier",recipe.requiredTier());return json;
    }
    public static Recipe readRecipe(JsonElement value) {
        var json=object(value,"id","revision","worker","inputs","outputs","energy","ticks","requires_advancement","required_tier");
        return new Recipe(string(json,"id"),string(json,"revision"),string(json,"worker"),
                amounts(json,"inputs"),amounts(json,"outputs"),number(json,"energy"),integer(json,"ticks"),string(json,"requires_advancement"),integer(json,"required_tier"));
    }
    private static JsonObject order(OrderLedger.Order order) {
        var json=new JsonObject();json.addProperty("id",order.id().toString());json.add("recipe",writeRecipe(order.recipe()));
        json.addProperty("batches",order.batches());json.addProperty("remaining",order.remainingBatches());json.addProperty("progress",order.progress());
        json.add("inputs",amounts(order.ownedInputs()));json.add("outputs",amounts(order.pendingOutputs()));
        json.addProperty("cancelled",order.cancelled());json.addProperty("quarantine",order.quarantineReason());
        json.addProperty("worker",order.workerId()==null?"":order.workerId().toString());return json;
    }
    private static OrderLedger.Order order(JsonElement value) {
        var json=object(value,"id","recipe","batches","remaining","progress","inputs","outputs","cancelled","quarantine","worker");
        String worker=string(json,"worker");var cancelled=json.get("cancelled");
        if(!cancelled.isJsonPrimitive()||!cancelled.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean");
        return new OrderLedger.Order(UUID.fromString(string(json,"id")),readRecipe(json.get("recipe")),integer(json,"batches"),integer(json,"remaining"),
                integer(json,"progress"),amounts(json,"inputs"),amounts(json,"outputs"),cancelled.getAsBoolean(),string(json,"quarantine"),worker.isEmpty()?null:UUID.fromString(worker));
    }
    private static JsonArray amounts(Map<Resource,Long> amounts) {
        var rows=new JsonArray();amounts.forEach((key,value)->{
            var row=new JsonObject();row.addProperty("kind",key.kind().name().toLowerCase(Locale.ROOT));row.addProperty("id",key.id());
            row.addProperty("phase",key.phase());row.addProperty("amount",value);rows.add(row);
        });return rows;
    }
    private static Map<Resource,Long> amounts(JsonObject object,String name) {
        var result=new TreeMap<Resource,Long>();
        for(var element:array(object,name,ResourceAmounts.MAX_RESOURCES)) {
            var row=object(element,"kind","id","phase","amount");String kind=string(row,"kind");
            if(!Set.of("item","fluid","matter").contains(kind))throw new IllegalArgumentException("Unknown resource kind");
            var key=new Resource(Kind.valueOf(kind.toUpperCase(Locale.ROOT)),string(row,"id"),string(row,"phase"));
            if(result.putIfAbsent(key,number(row,"amount"))!=null)throw new IllegalArgumentException("Duplicate resource");
        }return ResourceAmounts.copy(result);
    }
    private static JsonObject object(JsonElement value,String...keys) {
        if(!value.isJsonObject()||!value.getAsJsonObject().keySet().equals(Set.of(keys)))throw new IllegalArgumentException("Unknown object fields");
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonObject object,String name,int max) {
        var value=object.get(name);if(!value.isJsonArray()||value.getAsJsonArray().size()>max)throw new IllegalArgumentException("Invalid resource list");return value.getAsJsonArray();
    }
    private static String string(JsonObject object,String name) {
        var value=object.get(name);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string");return value.getAsString();
    }
    private static long number(JsonObject object,String name) {
        var value=object.get(name);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected exact number");
        return value.getAsBigDecimal().toBigIntegerExact().longValueExact();
    }
    private static int integer(JsonObject object,String name) {return Math.toIntExact(number(object,name));}
    /** Reject duplicate keys before Gson can replace their values; bound nesting and parser work. */
    private static JsonElement readValue(JsonReader reader,int depth,int[] nodes) throws java.io.IOException {
        if(depth>16||++nodes[0]>100_000)throw new IllegalArgumentException("Ledger structure is too large");
        return switch(reader.peek()) {
            case BEGIN_OBJECT -> {
                var object=new JsonObject();reader.beginObject();
                while(reader.hasNext()){String key=reader.nextName();if(object.has(key))throw new IllegalArgumentException("Duplicate field");object.add(key,readValue(reader,depth+1,nodes));}
                reader.endObject();yield object;
            }
            case BEGIN_ARRAY -> {
                var array=new JsonArray();reader.beginArray();while(reader.hasNext())array.add(readValue(reader,depth+1,nodes));reader.endArray();yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {reader.nextNull();yield JsonNull.INSTANCE;}
            default -> throw new IllegalArgumentException("Invalid ledger JSON");
        };
    }
}
