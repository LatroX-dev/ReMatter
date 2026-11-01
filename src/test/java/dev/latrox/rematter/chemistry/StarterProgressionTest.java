package dev.latrox.rematter.chemistry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the complete vanilla bootstrap and its exact material/permission boundaries. */
class StarterProgressionTest {
    private static final Set<String> START = Set.of("rematter:hand_preparator", "rematter:material_preparator", "rematter:decomposer", "rematter:matter_drive",
            "rematter:material_synthesizer", "rematter:matter_cell_64k");

    @Test void industrialInternalsCanBeCraftedBeforeTheirFirstMultiblockAndCannotBeSynthesized() throws Exception {
        for (String name : List.of("grinding_unit", "separation_unit", "mixing_basin", "field_coil", "reaction_chamber")) {
            Set<String> leaves = new HashSet<>();
            expand("rematter:" + name, new HashSet<>(), leaves);
            assertFalse(leaves.isEmpty());
            assertTrue(leaves.stream().allMatch(id -> id.startsWith("minecraft:") || id.startsWith("#minecraft:")));
            assertEquals("rematter:" + name, recipe(name).getAsJsonObject("result").get("id").getAsString());
            assertTrue(BuiltinChemistry.create().profile("rematter:" + name).isEmpty(), "An internal module is manufactured equipment");
            assertNull(getClass().getClassLoader().getResource("data/rematter/rematter/material_profile/materials/" + name + ".json"));
            assertEquals("rematter:" + name, json("data/rematter/advancement/recipes/" + name + ".json")
                    .getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
        }
    }

    @Test void machinesAndTheirFirstCellHaveAnAcyclicVanillaRouteWithOneSpecialMaterial() throws Exception {
        Set<String> leaves = new HashSet<>();
        for (String target : START) expand(target, new HashSet<>(), leaves);
        assertTrue(leaves.stream().allMatch(id -> id.startsWith("minecraft:") || id.startsWith("#minecraft:")));
        assertFalse(leaves.contains("minecraft:diamond"));
        assertFalse(leaves.contains("minecraft:diamond_block"));
        assertFalse(leaves.contains("minecraft:quartz"));
        assertFalse(leaves.contains("minecraft:blaze_rod"));
        assertFalse(leaves.contains("minecraft:amethyst_shard"));
        assertTrue(leaves.containsAll(Set.of("minecraft:sand", "minecraft:clay_ball", "minecraft:flint", "minecraft:iron_bars")));
        assertFalse(leaves.contains("minecraft:water_bucket"));
        for (String target : START) {
            assertTrue(ingredients(recipe(target.substring(9))).stream().noneMatch(id ->
                    id.equals("rematter:analyzer") || id.equals("rematter:matter_storage")));
        }
    }

    @Test void matrixBootstrapUsesDryPreparationThenHeatAndDoesNotInventChemistry() throws Exception {
        var blend = recipe("matrix_blend");
        assertEquals("rematter:preparation", blend.get("type").getAsString());
        assertEquals(List.of("rematter:silith", "rematter:argil"), ingredients(blend));
        assertEquals(0, blend.get("minimum_tier").getAsInt());
        assertEquals(4000, blend.get("energy").getAsInt());
        assertEquals(120, blend.get("ticks").getAsInt());
        assertEquals(4, blend.getAsJsonObject("result").get("count").getAsInt());
        assertNull(getClass().getClassLoader().getResource("data/rematter/advancement/recipes/matrix_blend.json"),
                "A machine process must not advertise an obsolete crafting-table recipe");
        Set<String> preparatorInputs = Set.copyOf(ingredients(recipe("material_preparator")));
        assertEquals(Set.of("rematter:machine_frame", "rematter:control_circuit", "minecraft:piston", "minecraft:flint", "minecraft:iron_bars"), preparatorInputs);
        assertFalse(preparatorInputs.stream().anyMatch(id -> id.contains("matrix")));
        var smelt = recipe("matrix_crystal_from_smelting");
        assertEquals("minecraft:smelting", smelt.get("type").getAsString());
        assertEquals(List.of("rematter:matrix_blend"), ingredients(smelt));
        assertEquals("rematter:matrix_crystal", smelt.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, smelt.getAsJsonObject("result").get("count").getAsInt());
        assertEquals(200, smelt.get("cookingtime").getAsInt());
        assertEquals(List.of("rematter:matrix_crystal", "rematter:matrix_crystal", "rematter:matrix_crystal", "rematter:matrix_crystal", "rematter:rhedon", "rematter:virel"),
                ingredients(recipe("matrix_cluster")));
        for (String name : List.of("matrix_blend", "matrix_crystal", "matrix_cluster", "silith", "argil", "rhedon", "virel", "lumin")) {
            assertTrue(BuiltinChemistry.create().profile("rematter:" + name).isEmpty());
            assertNull(getClass().getClassLoader().getResource("data/rematter/rematter/material_profile/materials/" + name + ".json"));
        }
    }

    @Test void manualExtractionBootstrapsTheElectricAndIndustrialMaterialStages() throws Exception {
        var names = List.of("silith", "argil", "rhedon", "virel", "lumin");
        var inputs = List.of("sand", "clay_ball", "redstone", "quartz", "glowstone_dust");
        for (int i = 0; i < names.size(); i++) {
            var extraction = recipe(names.get(i));
            assertEquals("rematter:preparation", extraction.get("type").getAsString());
            assertEquals(List.of("minecraft:" + inputs.get(i)), ingredients(extraction));
            assertFalse(extraction.has("secondary"));
            assertEquals(i < 2 ? 0 : 1, extraction.get("minimum_tier").getAsInt());
            assertEquals(2000, extraction.get("energy").getAsInt());
            assertEquals(90, extraction.get("ticks").getAsInt());
            assertEquals("rematter:" + names.get(i), extraction.getAsJsonObject("result").get("id").getAsString());
            assertEquals(1, extraction.getAsJsonObject("result").get("count").getAsInt());
        }
        assertEquals(Set.of("minecraft:flint", "minecraft:iron_bars", "#minecraft:planks", "minecraft:stick"),
                Set.copyOf(ingredients(recipe("hand_preparator"))));
        assertEquals(List.of("rematter:control_circuit", "rematter:lumin", "minecraft:glass"), ingredients(recipe("optical_circuit")));
        List<String> industrial = ingredients(recipe("industrial_preparator"));
        assertEquals(4, java.util.Collections.frequency(industrial, "minecraft:iron_block"));
        assertEquals(2, java.util.Collections.frequency(industrial, "minecraft:piston"));
        assertEquals(2, java.util.Collections.frequency(industrial, "rematter:control_circuit"));
        assertEquals(1, java.util.Collections.frequency(industrial, "rematter:matrix_cluster"));
    }

    @Test void cellMatrixCostsScaleExactlyOneFourSixteenWithoutConsumedStorage() throws Exception {
        List<String> cells = List.of("matter_cell_64k", "matter_cell_256k", "matter_cell_1m");
        for (int i = 0; i < cells.size(); i++) {
            List<String> inputs = ingredients(recipe(cells.get(i)));
            assertEquals(2, java.util.Collections.frequency(inputs, "minecraft:iron_ingot"));
            assertEquals(2, java.util.Collections.frequency(inputs, "minecraft:glass"));
            assertEquals(1, java.util.Collections.frequency(inputs, i == 2 ? "rematter:optical_circuit" : "rematter:control_circuit"));
            int crystals = java.util.Collections.frequency(inputs, "rematter:matrix_crystal")
                    + 4 * java.util.Collections.frequency(inputs, "rematter:matrix_cluster");
            assertEquals(1 << (2 * i), crystals);
            assertTrue(inputs.size() <= 9);
        }
    }

    @Test void componentsStorageAndUnprofiledMatrixMaterialsCannotBeSynthesized() throws Exception {
        for (String component : List.of("hand_preparator", "material_preparator", "industrial_preparator", "machine_frame", "control_circuit", "optical_circuit", "matter_drive", "matter_cell_64k", "matter_cell_256k", "matter_cell_1m", "matrix_blend", "matrix_crystal", "matrix_cluster", "silith", "argil", "rhedon", "virel", "lumin")) {
            assertFalse(BuiltinChemistry.create().synthesisPolicy().allowsItem("rematter:" + component));
            assertNull(getClass().getClassLoader().getResource("data/rematter/rematter/material_profile/materials/" + component + ".json"));
            assertNull(getClass().getClassLoader().getResource("data/rematter/rematter/synthesis/materials/" + component + ".json"));
        }
        Set<String> forbidden = Set.of("rematter:hand_preparator", "rematter:material_preparator", "rematter:industrial_preparator", "rematter:machine_frame", "rematter:control_circuit", "rematter:optical_circuit", "rematter:matter_drive",
                "rematter:matter_cell_64k", "rematter:matter_cell_256k", "rematter:matter_cell_1m",
                "rematter:matrix_blend", "rematter:matrix_crystal", "rematter:matrix_cluster",
                "rematter:silith", "rematter:argil", "rematter:rhedon", "rematter:virel", "rematter:lumin");
        var root = getClass().getClassLoader().getResource("data/rematter/rematter/synthesis");
        assertNotNull(root);
        try (var files = Files.walk(Path.of(root.toURI()))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var permission = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (forbidden.contains(permission.get("item").getAsString())) {
                    assertTrue(permission.has("disabled") && permission.get("disabled").getAsBoolean(), file.toString());
                }
            }
        }
    }

    @Test void cellRecipesNeverConsumeAnExistingStorageAndTheDriveRejectsFilledChestData() throws Exception {
        Set<String> plainParts = Set.of("rematter:matrix_crystal", "rematter:matrix_cluster", "rematter:control_circuit", "rematter:optical_circuit");
        for (String cell : List.of("matter_cell_64k", "matter_cell_256k", "matter_cell_1m")) {
            for (String input : ingredients(recipe(cell))) {
                assertTrue(input.startsWith("minecraft:") || plainParts.contains(input), "Data-carrying storage cannot be a vanilla crafting ingredient: " + input);
            }
        }
        var chest = recipe("matter_drive").getAsJsonObject("key").getAsJsonObject("H");
        // NeoForge's IngredientCodecs dispatch key is distinct from HolderSet's "type".
        assertEquals("neoforge:components", chest.get("neoforge:ingredient_type").getAsString());
        assertFalse(chest.has("type"));
        assertEquals(Set.of("!minecraft:block_entity_data", "minecraft:container", "!minecraft:container_loot", "!minecraft:custom_data"), chest.getAsJsonObject("components").keySet());
        assertTrue(chest.getAsJsonObject("components").getAsJsonArray("minecraft:container").isEmpty());
    }

    @Test void starterRecipesAreDiscoverableInTheVanillaRecipeBook() throws Exception {
        for (String name : List.of("machine_frame", "control_circuit", "decomposer", "material_synthesizer",
                "matter_drive", "matter_cell_64k", "matter_cell_256k", "matter_cell_1m", "analyzer",
                "hand_preparator", "material_preparator", "industrial_preparator", "optical_circuit", "matrix_crystal_from_smelting", "matrix_cluster")) {
            var advancement = json("data/rematter/advancement/recipes/" + name + ".json");
            assertEquals("rematter:" + name, advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
            assertEquals("minecraft:inventory_changed", advancement.getAsJsonObject("criteria").getAsJsonObject("has_material").get("trigger").getAsString());
            assertNotNull(recipe(name));
        }
    }

    private void expand(String item, Set<String> visiting, Set<String> leaves) throws Exception {
        if (item.startsWith("minecraft:") || item.startsWith("#minecraft:")) { leaves.add(item); return; }
        assertTrue(item.startsWith("rematter:"), "Foreign dependency in starter route: " + item);
        assertTrue(visiting.add(item), "Starter recipe cycle: " + item);
        String name = item.equals("rematter:matrix_crystal") ? "matrix_crystal_from_smelting" : item.substring(9);
        for (String ingredient : ingredients(recipe(name))) expand(ingredient, visiting, leaves);
        visiting.remove(item);
    }

    private List<String> ingredients(JsonObject recipe) {
        List<String> result = new ArrayList<>();
        if (recipe.has("ingredients")) recipe.getAsJsonArray("ingredients").forEach(value -> result.add(item(value)));
        else if (recipe.has("primary")) {
            result.add(item(recipe.get("primary")));
            if (recipe.has("secondary")) result.add(item(recipe.get("secondary")));
        }
        else if (recipe.has("ingredient")) result.add(item(recipe.get("ingredient")));
        else {
            var key = recipe.getAsJsonObject("key");
            for (JsonElement line : recipe.getAsJsonArray("pattern")) {
                for (char symbol : line.getAsString().toCharArray()) if (symbol != ' ') result.add(item(key.get(String.valueOf(symbol))));
            }
        }
        return result;
    }
    private String item(JsonElement value) {
        if (value.isJsonPrimitive()) return value.getAsString();
        var items = value.getAsJsonObject().getAsJsonArray("items");
        assertEquals(1, items.size(), "Starter uses exact items, not inferred tag variants");
        return items.get(0).getAsString();
    }
    private JsonObject recipe(String name) throws Exception { return json("data/rematter/recipe/" + name + ".json"); }
    private JsonObject json(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        }
    }
}
