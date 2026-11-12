package dev.latrox.rematter.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.latrox.rematter.chemistry.SubstanceId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Small read-only menu models shared by the converter and its searchable target list. */
final class PrimalConverterView {
    record Target(String id,boolean known,long ratio,long primal,long energy,int ticks){}
    record Destination(String status,int x,int y,int z,int bay,String item,long used,long capacity){
        boolean ready(){return status.equals("ready");}
        long free(){return capacity-used;}
    }
    private PrimalConverterView(){}
    static List<Target> targets(JsonObject source){
        var values=new ArrayList<Target>();var seen=new HashSet<String>();
        for(var entry:array(source,"targets"))if(entry.isJsonObject()&&values.size()<512){
            var row=entry.getAsJsonObject();String id=text(row,"id","");
            try{SubstanceId.parse(id);}catch(IllegalArgumentException invalid){continue;}
            long primal=number(row,"primal",0),energy=number(row,"energy",0),ratio=number(row,"ratio",0);
            int ticks=(int)Math.min(Integer.MAX_VALUE,number(row,"ticks",0));
            if(primal>0&&energy>0&&ticks>0&&ratio>0&&seen.add(id))values.add(new Target(id,flag(row,"known",false),ratio,primal,energy,ticks));
        }
        return List.copyOf(values);
    }
    static Destination destination(JsonObject source){
        var value=source.has("conversionDestination")&&source.get("conversionDestination").isJsonObject()?source.getAsJsonObject("conversionDestination"):new JsonObject();
        String status=text(value,"status","choose_target");
        long used=number(value,"used",0),capacity=number(value,"capacity",0);int bay=(int)number(value,"bay",0);
        if(status.equals("ready")&&(bay<1||bay>6||capacity<=0||used>capacity))status="no_compatible_cell";
        return new Destination(status,signed(value,"x"),signed(value,"y"),signed(value,"z"),bay,text(value,"item",""),Math.min(used,capacity),capacity);
    }
    static JsonArray array(JsonObject source,String key){return source.has(key)&&source.get(key).isJsonArray()?source.getAsJsonArray(key):new JsonArray();}
    static String text(JsonObject source,String key,String fallback){try{return source.has(key)&&source.get(key).isJsonPrimitive()?source.get(key).getAsString():fallback;}catch(RuntimeException invalid){return fallback;}}
    static long number(JsonObject source,String key,long fallback){try{return source.has(key)&&source.get(key).isJsonPrimitive()?Math.max(0,source.get(key).getAsLong()):fallback;}catch(RuntimeException invalid){return fallback;}}
    static boolean flag(JsonObject source,String key,boolean fallback){return source.has(key)&&source.get(key).isJsonPrimitive()&&source.get(key).getAsJsonPrimitive().isBoolean()?source.get(key).getAsBoolean():fallback;}
    private static int signed(JsonObject source,String key){try{return source.has(key)?source.get(key).getAsInt():0;}catch(RuntimeException invalid){return 0;}}
}
