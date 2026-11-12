package dev.latrox.rematter.content.preparation;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact recipe vectors, including counts and FE, checked without a running game registry. */
class PreparationInverseTest {
    private static final Path ROOT=Path.of("src/main/resources/data/rematter/recipe");
    private static JsonObject recipe(String id)throws Exception{return JsonParser.parseString(Files.readString(ROOT.resolve(id+".json"))).getAsJsonObject();}
    private static String canonical(JsonElement ingredient){
        String value=ingredient.getAsString();
        if(value.startsWith("#c:dusts/"))return "rematter:"+value.substring("#c:dusts/".length())+(value.endsWith("diamond")?"_powder":"_dust");
        if(value.startsWith("#c:ingots/"))return "minecraft:"+value.substring("#c:ingots/".length())+"_ingot";
        return value;
    }
    private static Map<String,Long> inputs(JsonObject json){
        var result=new TreeMap<String,Long>();
        for(String role:List.of("primary","secondary","tertiary"))if(json.has(role))result.merge(canonical(json.get(role)),json.has(role+"_count")?json.get(role+"_count").getAsLong():1L,Long::sum);
        return result;
    }
    private static Map<String,Long> outputs(JsonObject json){
        var result=new TreeMap<String,Long>();var outputs=json.has("results")?json.getAsJsonArray("results"):new JsonArray();
        if(json.has("result"))outputs.add(json.get("result"));
        for(var element:outputs){var value=element.getAsJsonObject();result.merge(value.get("id").getAsString(),value.has("count")?value.get("count").getAsLong():1L,Long::sum);}
        return result;
    }
    private static Map<String,Long> craftingInputs(JsonObject recipe){
        var result=new TreeMap<String,Long>();
        if(recipe.has("ingredients"))for(var item:recipe.getAsJsonArray("ingredients"))result.merge(canonical(item),1L,Long::sum);
        else for(var row:recipe.getAsJsonArray("pattern"))for(char key:row.getAsString().toCharArray())if(key!=' ')result.merge(canonical(recipe.getAsJsonObject("key").get(String.valueOf(key))),1L,Long::sum);
        return result;
    }
    @Test void eachReclaimIsExactlyOneForwardBatchIncludingAllByproducts()throws Exception{
        var pairs=new LinkedHashMap<String,String>();pairs.put("rhevon_dust_separation","rhevon_dust");pairs.put("lumivex_dust_separation","lumivex_dust");
        for(String colour:List.of("ice","copper","leaf","violet","amber","ember","blue","mint"))pairs.put(colour+"_matrix_crystal_reclaim",colour+"_matrix_crystal");
        pairs.put("cell_rack_reclaim","cell_rack");pairs.put("machine_frame_reclaim","machine_frame");
        for(var pair:pairs.entrySet()){
            var backward=recipe(pair.getKey());var forward=recipe(pair.getValue());
            assertEquals(outputs(forward),inputs(backward),pair.getKey()+" consumes the whole exact batch");
            assertEquals(forward.has("primary")?inputs(forward):craftingInputs(forward),outputs(backward),pair.getKey()+" returns every ingredient exactly once");
            assertEquals(2,backward.get("minimum_tier").getAsInt());assertFalse(backward.get("network_recipe").getAsBoolean());
            assertEquals("separate",backward.getAsJsonArray("steps").get(0).getAsString());
        }
    }
    private static void chain(String directId,Map<String,Integer> stages)throws Exception{
        var direct=recipe(directId);var net=new TreeMap<String,Long>();long energy=0;
        for(var stage:stages.entrySet()){
            var data=recipe(stage.getKey());int batches=stage.getValue();energy+=data.get("energy").getAsLong()*batches;
            inputs(data).forEach((key,count)->net.merge(key,-count*batches,Long::sum));outputs(data).forEach((key,count)->net.merge(key,count*batches,Long::sum));
        }
        var expected=new TreeMap<String,Long>();inputs(direct).forEach((key,count)->expected.merge(key,-count,Long::sum));outputs(direct).forEach((key,count)->expected.merge(key,count,Long::sum));
        net.values().removeIf(value->value==0);expected.values().removeIf(value->value==0);
        assertEquals(expected,net,directId+" exact material vector");assertEquals(energy,direct.get("energy").getAsLong(),directId+" full chain FE");
    }
    @Test void directRecipesPreserveEveryInputAndTheEntireSameMachineEnergy()throws Exception{
        chain("rhevon_dust_direct",Map.of("iron_dust",3,"copper_dust",1,"rhedon",1,"rhevon_dust",1));
        chain("lumivex_dust_direct",Map.of("copper_dust",3,"gold_dust",1,"lumin",1,"lumivex_dust",1));
        chain("virelite_blend_direct",Map.of("silith",1,"argil",1,"virel",1,"virelite_blend",1));
        chain("diamond_matrix_blend_direct",Map.of("diamond_powder",1,"gold_dust",1,"diamond_matrix_blend",1));
    }
    @Test void industrialOnlyOutputsHaveNoCompactOrWorkbenchBypass()throws Exception{
        for(String id:List.of("parallel_upgrade","netherite_sheath_blend","primal_crystal","discovery_lens")){
            var data=recipe(id);assertEquals("rematter:preparation",data.get("type").getAsString());assertEquals(2,data.get("minimum_tier").getAsInt());assertTrue(data.get("min_precision").getAsInt()>=1);
        }
        assertEquals("minecraft:crafting_shaped",recipe("pressure_module").get("type").getAsString());
        assertFalse(Files.exists(ROOT.resolve("compat/oritech/assembler/netherite_sheath_blend.json")));
        var upgrade=recipe("material_preparator_from_hand_preparator");assertEquals("rematter:empty_machine",upgrade.getAsJsonObject("key").getAsJsonObject("H").get("neoforge:ingredient_type").getAsString());
        assertEquals(Map.of("rematter:material_preparator",1L),outputs(upgrade));
    }
}
