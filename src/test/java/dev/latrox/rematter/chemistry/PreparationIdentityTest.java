package dev.latrox.rematter.chemistry;

import java.nio.file.Path;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mixing, grinding and firing change item forms, never substance identities: a prepared material decomposes into
 * exactly the substances its ingredients contained, so preparation cannot reduce compounds to free elements.
 */
class PreparationIdentityTest {
    private static final ChemistryRegistry REGISTRY = ShippedChemistry.standalone();
    private static final SubstanceKey ALUMINUM = BuiltinChemistry.ALUMINUM;
    private static final Set<String> RENEWABLE_ITEMS = Set.of("minecraft:sand", "minecraft:clay_ball", "minecraft:dirt",
            "minecraft:stone", "minecraft:cobblestone");
    private static final Set<String> RENEWABLE_FLUIDS = Set.of("minecraft:water", "minecraft:lava");

    @Test
    void everyProfiledPreparationResultKeepsTheExactSubstancesOfItsIngredients() throws IOException {
        List<JsonObject> recipes = preparations();
        Map<String, JsonObject> producers = new HashMap<>();
        for (JsonObject recipe : recipes) if(recipe.has("result"))producers.putIfAbsent(recipe.getAsJsonObject("result").get("id").getAsString(),recipe);
        int checked = 0;
        for (JsonObject recipe : recipes) {
            var results=recipe.has("results")?recipe.getAsJsonArray("results"):new com.google.gson.JsonArray();
            if(recipe.has("result"))results.add(recipe.get("result"));
            // A complete material vector needs every output; dedicated inverse tests cover unprofiled components.
            if(results.asList().stream().noneMatch(entry->REGISTRY.profile(entry.getAsJsonObject().get("id").getAsString()).isPresent()))continue;
            boolean profiled=true;Map<SubstanceKey,Long> outputs=new TreeMap<>();
            for(var entry:results){
                var result=entry.getAsJsonObject();String item=result.get("id").getAsString();
                if(!hasMatter(item,producers,new java.util.HashSet<>())){profiled=false;break;}
                long count=result.has("count")?result.get("count").getAsLong():1;
                matter(item,producers).forEach((key,amount)->outputs.merge(key,Math.multiplyExact(amount,count),Math::addExact));
            }
            if(!profiled)continue;
            assertEquals(ingredients(recipe,producers),outputs,results.toString());checked++;
        }
        assertTrue(checked >= 8, "Workshop powders, ceramics and powders keep their ingredient identities");
    }

    @Test
    void standaloneReductionIsExplicitAndReferencePackStillClosesFreeAluminum() {
        var standalone=MatterReachability.from(REGISTRY,RENEWABLE_ITEMS,RENEWABLE_FLUIDS);
        assertTrue(standalone.matter().contains(BuiltinChemistry.ALUMINA));
        assertTrue(standalone.matter().contains(ALUMINUM),"Field4 industrial reduction is an explicit standalone route");
        var pack=ShippedChemistry.load(ShippedChemistry.MAIN_RESOURCES,Path.of("integrations/reforging/datapack"));
        var reachable=MatterReachability.from(pack,RENEWABLE_ITEMS,RENEWABLE_FLUIDS);
        assertTrue(reachable.matter().contains(BuiltinChemistry.ALUMINA));
        assertFalse(reachable.matter().contains(ALUMINUM),"Reference-pack reduction stays disabled until its individual audit");
    }

    private static boolean hasMatter(String item,Map<String,JsonObject> producers,Set<String> seen){
        if(REGISTRY.profile(item).isPresent())return true;
        if(!seen.add(item))return false;
        var source=producers.get(item);if(source==null)return false;
        for(var slot:List.of("primary","secondary","tertiary"))if(source.has(slot)){
            String ingredient=source.get(slot).getAsString();
            if(ingredient.startsWith("#"))ingredient=taggedProfile(ingredient.substring(1));
            if(!hasMatter(ingredient,producers,new java.util.HashSet<>(seen)))return false;
        }
        return true;
    }

    private static Map<SubstanceKey, Long> ingredients(JsonObject recipe, Map<String, JsonObject> producers) {
        Map<SubstanceKey, Long> total = new TreeMap<>();
        for (String slot : List.of("primary", "secondary", "tertiary")) {
            if (!recipe.has(slot)) continue;
            long count = recipe.has(slot + "_count") ? recipe.get(slot + "_count").getAsLong() : 1;
            matter(recipe.get(slot).getAsString(), producers).forEach((key, amount) ->
                    total.merge(key, Math.multiplyExact(amount, count), Math::addExact));
        }
        return total;
    }

    /** One item of an ingredient: its own profile, or its single preparation source divided exactly by the yield. */
    private static Map<SubstanceKey, Long> matter(String ingredient, Map<String, JsonObject> producers) {
        String item = ingredient.startsWith("#") ? taggedProfile(ingredient.substring(1)) : ingredient;
        var profile = REGISTRY.profile(item);
        if (profile.isPresent()) {
            assertEquals(1, profile.orElseThrow().itemCount(), item);
            return profile.orElseThrow().matter();
        }
        JsonObject source = producers.get(item);
        assertNotNull(source, "Ingredient without a profile or preparation source: " + item);
        long yield = source.getAsJsonObject("result").get("count").getAsLong();
        Map<SubstanceKey, Long> share = new TreeMap<>();
        ingredients(source, producers).forEach((key, amount) -> {
            assertEquals(0, amount % yield, "Fractional share of " + key + " in " + item);
            share.put(key, amount / yield);
        });
        return share;
    }

    private static String taggedProfile(String tag) {
        return MatterReachability.tags().getOrDefault(tag, Set.of()).stream().sorted()
                .filter(member -> REGISTRY.profile(member).isPresent()).findFirst()
                .orElseThrow(() -> new AssertionError("No profiled member in #" + tag));
    }

    private static List<JsonObject> preparations() throws IOException {
        try (var files = Files.walk(ShippedChemistry.MAIN_RESOURCES.resolve("data/rematter/recipe"))) {
            return files.filter(path -> path.toString().endsWith(".json")).sorted().map(ShippedChemistry::read)
                    .filter(recipe -> recipe.has("type") && recipe.get("type").getAsString().equals("rematter:preparation"))
                    .toList();
        }
    }
}
