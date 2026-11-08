package dev.latrox.rematter.chemistry;

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
        for (JsonObject recipe : recipes) producers.putIfAbsent(recipe.getAsJsonObject("result").get("id").getAsString(), recipe);
        int checked = 0;
        for (JsonObject recipe : recipes) {
            String result = recipe.getAsJsonObject("result").get("id").getAsString();
            var profile = REGISTRY.profile(result);
            if (profile.isEmpty()) continue;
            long count = recipe.getAsJsonObject("result").get("count").getAsLong();
            assertEquals(ingredients(recipe, producers), profile.orElseThrow().matterFor(count), result);
            checked++;
        }
        assertTrue(checked >= 8, "Workshop powders, ceramics and powders keep their ingredient identities");
    }

    @Test
    void renewableNaturalMaterialsCannotReachFreeAluminum() {
        var reachable = MatterReachability.from(REGISTRY, RENEWABLE_ITEMS, RENEWABLE_FLUIDS);
        assertTrue(reachable.matter().contains(BuiltinChemistry.ALUMINA));
        assertFalse(reachable.matter().contains(ALUMINUM), "No reduction route releases aluminum from clay or rock");
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
