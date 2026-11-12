package dev.latrox.rematter.content.generation;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.content.MachineEnergyPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
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
        var smelt = read("recipe/matrix_crystal_from_smelting.json");
        assertEquals("rematter:matrix_blend", smelt.get("ingredient").getAsString()); assertEquals(1, smelt.getAsJsonObject("result").get("count").getAsInt());
        for (String id : CrystalFuelData.ALLOWED) {
            var data = read("rematter/crystal_fuel/" + id.substring("rematter:".length()) + ".json");
            var fuel = CrystalFuelData.parse(data).orElseThrow();
            assertEquals(1, fuel.count()); assertEquals(1024, fuel.energy());
            assertTrue(fuel.energy() <= (cheapestPolicyFloor / outputs) / 2, "Clay, furnace fuel and coloured additives deliberately cost zero in this lower bound");
        }
    }
    @Test void allOwnCoreProducersAndReclaimsAreExplicitAndOneForOne() throws Exception {
        Set<String> actual = new HashSet<>();
        try (var paths = Files.walk(ROOT.resolve("recipe"))) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                var results = new java.util.ArrayList<JsonObject>();
                if (json.has("result") && json.get("result").isJsonObject()) results.add(json.getAsJsonObject("result"));
                if (json.has("results")) json.getAsJsonArray("results").forEach(value -> results.add(value.getAsJsonObject()));
                if (results.stream().anyMatch(result -> "rematter:matrix_crystal".equals(result.get("id").getAsString()))) actual.add(ROOT.resolve("recipe").relativize(file).toString());
            }
        }
        Set<String> expected = new HashSet<>(); expected.add("matrix_crystal_from_smelting.json");
        for (String fuel : CrystalFuelData.ALLOWED) if (!fuel.equals("rematter:matrix_crystal")) {
            String name = fuel.substring("rematter:".length()); expected.add(name + "_reclaim.json");
            var craft = read("recipe/" + name + ".json");
            assertEquals(1, craft.getAsJsonObject("result").get("count").getAsInt());
            assertEquals(1, craft.getAsJsonArray("ingredients").asList().stream().filter(value -> value.getAsString().equals("rematter:matrix_crystal")).count());
            var reclaim = read("recipe/" + name + "_reclaim.json"); assertEquals(fuel, reclaim.get("primary").getAsString());
            assertEquals(1, reclaim.getAsJsonArray("results").get(0).getAsJsonObject().get("count").getAsInt());
        }
        assertEquals(expected, actual, "A new source of matrix cores requires a fresh closed-cycle audit");
    }
}
