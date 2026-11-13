package dev.latrox.rematter.content.generation;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.content.MachineEnergyPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The intentionally conservative own-recipe proof fails when another core-producing route is added. */
class CrystalFuelAuditTest {
    private static final Path ROOT = Path.of("src/main/resources/data/rematter");
    private static JsonObject read(String path) throws Exception { return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject(); }
    @Test void sandAlonePaysMoreThanTwiceTheEnabledFuelEvenWithMaximumPolicyDiscountAndFourOutputs() throws Exception {
        long sand = read("rematter/material_profile/vanilla/sand.json").get("synthesis_energy").getAsLong();
        long cheapestPolicyFloor = sand - sand / 4;
        assertTrue(MachineEnergyPolicy.combined(sand, 0, 4) >= cheapestPolicyFloor);
        var silith = read("recipe/silith.json"); assertEquals("minecraft:sand", silith.get("primary").getAsString()); assertEquals(1, silith.getAsJsonObject("result").get("count").getAsInt());
        var blend = read("recipe/matrix_blend.json");
        assertEquals("rematter:silith", blend.get("primary").getAsString()); assertEquals("rematter:argil", blend.get("secondary").getAsString());
        int outputs = blend.getAsJsonObject("result").get("count").getAsInt(); assertEquals(4, outputs);
        var core = read("recipe/matrix_crystal.json");
        assertEquals("rematter:preparation", core.get("type").getAsString());
        assertEquals("rematter:matrix_blend", core.get("primary").getAsString()); assertEquals(1, core.getAsJsonObject("result").get("count").getAsInt());
        for (String id : CrystalFuelData.ALLOWED) {
            var data = read("rematter/crystal_fuel/" + id.substring("rematter:".length()) + ".json");
            var fuel = CrystalFuelData.parse(data).orElseThrow();
            assertEquals(1, fuel.count()); assertEquals(1024, fuel.energy());
            assertTrue(fuel.energy() <= (cheapestPolicyFloor / outputs) / 2, "Clay, preparation and coloured additives deliberately cost zero in this lower bound");
        }
    }
    @Test void allOwnCoreProducersAndReclaimsAreExplicitAndOneForOne() throws Exception {
        Map<String, Set<String>> actual = new HashMap<>();
        try (var paths = Files.walk(ROOT.resolve("recipe"))) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                var results = new java.util.ArrayList<JsonObject>();
                if (json.has("result") && json.get("result").isJsonObject()) results.add(json.getAsJsonObject("result"));
                if (json.has("results")) json.getAsJsonArray("results").forEach(value -> results.add(value.getAsJsonObject()));
                for (var result : results) {
                    String output = result.get("id").getAsString();
                    if (CrystalFuelData.ALLOWED.contains(output)) actual.computeIfAbsent(output, ignored -> new HashSet<>())
                            .add(ROOT.resolve("recipe").relativize(file).toString());
                }
            }
        }
        Set<String> expected = new HashSet<>(); expected.add("matrix_crystal.json");
        for (String fuel : CrystalFuelData.ALLOWED) if (!fuel.equals("rematter:matrix_crystal")) {
            String name = fuel.substring("rematter:".length()); expected.add(name + "_reclaim.json");
            var preparation = read("recipe/" + name + ".json");
            assertEquals("rematter:preparation", preparation.get("type").getAsString());
            assertEquals("rematter:matrix_crystal", preparation.get("primary").getAsString());
            assertFalse(preparation.has("primary_count"));
            assertEquals(1, preparation.getAsJsonObject("result").get("count").getAsInt());
            var reclaim = read("recipe/" + name + "_reclaim.json"); assertEquals(fuel, reclaim.get("primary").getAsString());
            assertEquals("rematter:preparation", reclaim.get("type").getAsString());
            assertEquals(2, reclaim.get("minimum_tier").getAsInt());
            assertEquals(2, reclaim.getAsJsonArray("results").size());
            assertEquals("rematter:matrix_crystal", reclaim.getAsJsonArray("results").get(0).getAsJsonObject().get("id").getAsString());
            assertEquals(1, reclaim.getAsJsonArray("results").get(0).getAsJsonObject().get("count").getAsInt());
            assertEquals(1, reclaim.getAsJsonArray("results").get(1).getAsJsonObject().get("count").getAsInt());
            String additive = preparation.get("secondary").getAsString();
            String returned = reclaim.getAsJsonArray("results").get(1).getAsJsonObject().get("id").getAsString();
            if (additive.startsWith("#")) {
                String[] tag = additive.substring(1).split(":", 2);
                var values = JsonParser.parseString(Files.readString(ROOT.getParent().resolve(tag[0] + "/tags/item/" + tag[1] + ".json")))
                        .getAsJsonObject().getAsJsonArray("values");
                assertTrue(values.asList().stream().anyMatch(value -> value.isJsonPrimitive() && value.getAsString().equals(returned)),
                        "Reclaim must return one item of the original additive tag");
            } else assertEquals(additive, returned);
            assertEquals(Set.of(name + ".json"), actual.get(fuel), "A coloured core has no other single- or multi-result producer");
        }
        assertEquals(expected, actual.get("rematter:matrix_crystal"), "A new source of matrix cores requires a fresh closed-cycle audit");
        assertEquals(CrystalFuelData.ALLOWED, actual.keySet());
    }

    @Test void everyCrystalHasExactlyOnePreparationSourceAndItsPoweredStepCostsMoreThanItsFuel() throws Exception {
        Set<String> creation = new HashSet<>();
        try (var paths = Files.walk(ROOT.resolve("recipe"))) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!json.has("result") || !json.get("result").isJsonObject()) continue;
                var result = json.getAsJsonObject("result");
                String output = result.get("id").getAsString();
                if (!CrystalFuelData.ALLOWED.contains(output)) continue;
                assertTrue(creation.add(output), "More than one direct producer of " + output);
                String id = output.substring("rematter:".length());
                assertEquals(id + ".json", ROOT.resolve("recipe").relativize(file).toString());
                assertEquals("rematter:preparation", json.get("type").getAsString());
                assertEquals(1, result.get("count").getAsInt());
                assertEquals(4000, json.get("energy").getAsInt());
                assertEquals(640, json.get("ticks").getAsInt());
                int tier = Set.of("matrix_crystal", "ice_matrix_crystal", "copper_matrix_crystal", "amber_matrix_crystal", "ember_matrix_crystal").contains(id) ? 0 : 1;
                assertEquals(tier, json.get("minimum_tier").getAsInt());
                long price = json.get("energy").getAsLong();
                long conservativeFloor = price - price / 4;
                assertTrue(MachineEnergyPolicy.combined(price, 0, 4) >= conservativeFloor);
                assertTrue(conservativeFloor > CrystalFuelData.parse(read("rematter/crystal_fuel/" + id + ".json")).orElseThrow().energy(),
                        "Powered preparation cannot pay for itself even if all inputs are free");
                assertFalse(json.has("tertiary"));
            }
        }
        assertEquals(CrystalFuelData.ALLOWED, creation, "All nine cores have their reviewed exclusive preparation route");
    }
}
