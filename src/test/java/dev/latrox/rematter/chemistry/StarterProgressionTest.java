package dev.latrox.rematter.chemistry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the complete vanilla bootstrap and its exact material/permission boundaries. */
class StarterProgressionTest {
    private static final Set<String> BOOTSTRAP = Set.of("rematter:hand_preparator", "rematter:material_preparator", "rematter:decomposer",
            "rematter:cell_housing", "rematter:matter_cell_64k");

    @Test void furnaceAndIndustrialInternalsHaveAnAcyclicVanillaRouteAndCannotBeSynthesized() throws Exception {
        for (String name : List.of("sinter_furnace", "grinding_unit", "separation_unit", "mixing_basin", "field_coil", "reaction_chamber")) {
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

    @Test void bootstrapAndGemPoweredEquipmentHaveAcyclicVanillaRoutes() throws Exception {
        Set<String> leaves = new HashSet<>();
        for (String target : BOOTSTRAP) expand(target, new HashSet<>(), leaves);
        assertTrue(leaves.stream().allMatch(id -> id.startsWith("minecraft:") || id.startsWith("#minecraft:")));
        assertFalse(leaves.contains("minecraft:diamond"));
        assertFalse(leaves.contains("minecraft:diamond_block"));
        assertFalse(leaves.contains("minecraft:emerald"));
        assertFalse(leaves.contains("minecraft:ender_pearl"));
        assertFalse(leaves.contains("minecraft:netherite_ingot"));
        assertFalse(leaves.contains("minecraft:quartz"));
        assertFalse(leaves.contains("minecraft:blaze_rod"));
        assertFalse(leaves.contains("minecraft:amethyst_shard"));
        assertTrue(leaves.containsAll(Set.of("minecraft:sand", "minecraft:clay_ball", "minecraft:flint", "minecraft:iron_ingot")));
        assertFalse(leaves.contains("minecraft:iron_bars"));
        assertFalse(leaves.contains("minecraft:water_bucket"));
        Set<String> starterMachines = new HashSet<>(BOOTSTRAP);
        starterMachines.addAll(Set.of("rematter:matter_drive", "rematter:material_synthesizer"));
        for (String target : starterMachines) {
            assertFalse(ingredients(recipe(target.substring(9))).contains("rematter:analyzer"));
        }
        var gems = Map.of("rematter:matter_drive", "minecraft:diamond",
                "rematter:material_synthesizer", "minecraft:emerald",
                "rematter:matter_terminal", "minecraft:diamond");
        for (var entry : gems.entrySet()) {
            Set<String> equipmentLeaves = new HashSet<>();
            expand(entry.getKey(), new HashSet<>(), equipmentLeaves);
            assertTrue(equipmentLeaves.contains(entry.getValue()), entry.getKey());
            assertTrue(equipmentLeaves.stream().allMatch(id -> id.startsWith("minecraft:") || id.startsWith("#minecraft:")));
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
        assertEquals(Set.of("rematter:machine_frame", "rematter:control_circuit", "rematter:amber_matrix_crystal",
                "rematter:structural_bars"), preparatorInputs);
        assertTrue(ingredients(recipe("hand_preparator")).stream().noneMatch(id -> id.startsWith("rematter:")),
                "Manual preparation must work before the first crystal can be produced");
        var smelt = recipe("matrix_crystal_from_smelting");
        assertEquals("minecraft:smelting", smelt.get("type").getAsString());
        assertEquals(List.of("rematter:matrix_blend"), ingredients(smelt));
        assertEquals("rematter:matrix_crystal", smelt.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, smelt.getAsJsonObject("result").get("count").getAsInt());
        assertEquals(200, smelt.get("cookingtime").getAsInt());
        var cluster = ingredientCounts(recipe("matrix_cluster"));
        assertEquals(4, cluster.get("rematter:matrix_crystal"));
        assertTrue(cluster.keySet().containsAll(Set.of("rematter:rhedon", "rematter:virel")));
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
        assertEquals(Set.of("minecraft:flint", "minecraft:iron_ingot", "minecraft:copper_ingot"),
                Set.copyOf(ingredients(recipe("hand_preparator"))));
        assertTrue(ingredients(recipe("optical_circuit")).contains("rematter:lumivex_ingot"));
        assertTrue(ingredients(recipe("industrial_preparator")).containsAll(
                Set.of("rematter:matrix_cluster", "rematter:rhevon_ingot")));
    }

    @Test void cellMatrixCostsScaleExactlyOneFourSixteenWithoutConsumedStorage() throws Exception {
        var housing = recipe("cell_housing");
        assertEquals("minecraft:crafting_shaped", housing.get("type").getAsString());
        assertEquals(Map.of("rematter:structural_bars", 4, "minecraft:glass", 4), ingredientCounts(housing));
        assertEquals("rematter:cell_housing", housing.getAsJsonObject("result").get("id").getAsString());
        assertEquals(2, housing.getAsJsonObject("result").get("count").getAsInt());
        List<String> cells = List.of("matter_cell_64k", "matter_cell_256k", "matter_cell_1m");
        for (int i = 0; i < cells.size(); i++) {
            var cellRecipe = recipe(cells.get(i));
            List<String> inputs = ingredients(cellRecipe);
            var amounts = ingredientCounts(cellRecipe);
            assertEquals(1, amounts.get("rematter:cell_housing"));
            assertEquals(1, amounts.get(i == 2 ? "rematter:optical_circuit" : "rematter:control_circuit"));
            if (i >= 1) assertEquals(1, amounts.get("rematter:ender_conductor"));
            if (i == 2) {
                assertEquals(1, amounts.get("rematter:diamond_lattice"));
                assertEquals(1, amounts.get("rematter:netherite_sheath"));
            }
            int crystals = java.util.Collections.frequency(inputs, "rematter:matrix_crystal")
                    + 4 * java.util.Collections.frequency(inputs, "rematter:matrix_cluster");
            assertEquals(1 << (2 * i), crystals);
            assertEquals(1, cellRecipe.getAsJsonObject("result").get("count").getAsInt());
            assertTrue(inputs.size() <= 9);
        }
    }

    @Test void componentsStorageAndUnprofiledMatrixMaterialsCannotBeSynthesized() throws Exception {
        for (String component : List.of("hand_preparator", "material_preparator", "industrial_preparator", "sinter_furnace", "machine_frame", "structural_bars", "control_circuit", "optical_circuit", "cell_housing", "matter_drive", "matter_cell_64k", "matter_cell_256k", "matter_cell_1m", "matrix_blend", "matrix_crystal", "matrix_cluster", "silith", "argil", "rhedon", "virel", "lumin", "diamond_lattice", "ender_conductor", "netherite_sheath")) {
            assertFalse(BuiltinChemistry.create().synthesisPolicy().allowsItem("rematter:" + component));
            assertNull(getClass().getClassLoader().getResource("data/rematter/rematter/material_profile/materials/" + component + ".json"));
            assertNull(getClass().getClassLoader().getResource("data/rematter/rematter/synthesis/materials/" + component + ".json"));
        }
        Set<String> forbidden = Set.of("rematter:hand_preparator", "rematter:material_preparator", "rematter:industrial_preparator", "rematter:sinter_furnace", "rematter:machine_frame", "rematter:structural_bars", "rematter:control_circuit", "rematter:optical_circuit", "rematter:cell_housing", "rematter:matter_drive",
                "rematter:matter_cell_64k", "rematter:matter_cell_256k", "rematter:matter_cell_1m",
                "rematter:matrix_blend", "rematter:matrix_crystal", "rematter:matrix_cluster",
                "rematter:silith", "rematter:argil", "rematter:rhedon", "rematter:virel", "rematter:lumin",
                "rematter:diamond_lattice", "rematter:ender_conductor", "rematter:netherite_sheath");
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

    @Test void cellAndStorageRecipesUseOnlyStatelessPartsWithoutExistingContainers() throws Exception {
        Set<String> plainParts = Set.of("rematter:matrix_crystal", "rematter:matrix_cluster", "rematter:control_circuit",
                "rematter:optical_circuit", "rematter:machine_frame", "rematter:cell_housing", "rematter:cell_rack",
                "rematter:structural_bars", "rematter:diamond_lattice", "rematter:ender_conductor", "rematter:netherite_sheath",
                "rematter:mint_matrix_crystal");
        for (String name : List.of("matter_drive", "matter_cell_64k", "matter_cell_256k", "matter_cell_1m")) {
            var crafting = recipe(name);
            List<String> inputs = ingredients(crafting);
            assertEquals("minecraft:crafting_shaped", crafting.get("type").getAsString());
            if(name.equals("matter_drive"))assertEquals(2, java.util.Collections.frequency(inputs,"rematter:cell_rack"));
            else assertEquals(1, java.util.Collections.frequency(inputs, "rematter:cell_housing"));
            for (String input : inputs) {
                assertTrue(plainParts.contains(input), "Only stateless parts are allowed; no chest, existing cell or filled machine: " + input);
            }
        }
    }

    @Test void racksDoNotConsumeLiveCells() throws Exception {
        assertTrue(ingredients(recipe("cell_rack")).contains("rematter:structural_bars"));
        assertFalse(ingredients(recipe("cell_rack")).contains("minecraft:iron_bars"));
        assertFalse(BuiltinChemistry.create().synthesisPolicy().allowsItem("rematter:cell_rack"));
        Set<String> leaves=new HashSet<>();expand("rematter:cell_rack",new HashSet<>(),leaves);
        assertTrue(leaves.stream().allMatch(id->id.startsWith("minecraft:")||id.startsWith("#minecraft:")));
    }

    @Test void allCraftingAndStarterSmeltingRecipesAreDiscoverableInTheVanillaRecipeBook() throws Exception {
        var root = getClass().getClassLoader().getResource("data/rematter/recipe");
        assertNotNull(root);
        Path recipeRoot = Path.of(root.toURI());
        Set<String> names = new TreeSet<>();
        try (var files = Files.walk(recipeRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var crafting = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (crafting.get("type").getAsString().startsWith("minecraft:crafting_")) {
                    String relative = recipeRoot.relativize(file).toString().replace('\\', '/');
                    names.add(relative.substring(0, relative.length() - ".json".length()));
                }
            }
        }
        assertTrue(names.containsAll(Set.of("machine_frame", "structural_bars", "control_circuit", "optical_circuit", "cell_housing",
                "matrix_cluster", "guide", "matter_cell_64k", "matter_cell_256k", "matter_cell_1m",
                "analyzer", "decomposer", "material_synthesizer", "matter_drive", "cell_rack", "matter_terminal",
                "hand_preparator", "material_preparator", "industrial_preparator", "sinter_furnace", "electrolyzer",
                "chemical_reactor", "industrial_reactor", "network_interface", "grinding_unit", "separation_unit",
                "mixing_basin", "field_coil", "reaction_chamber", "reactor_casing", "reactor_glass", "machine_port",
                "matter_pipe", "reinforced_matter_cable", "resonant_matter_cable", "speed_upgrade", "efficiency_upgrade")));
        names.add("matrix_crystal_from_smelting");
        for (String name : names) {
            var advancement = json("data/rematter/advancement/recipes/" + name + ".json");
            assertEquals("rematter:" + name, advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
            assertEquals("minecraft:inventory_changed", advancement.getAsJsonObject("criteria").getAsJsonObject("has_material").get("trigger").getAsString());
            assertNotNull(recipe(name));
        }
    }

    private Map<String, Integer> ingredientCounts(JsonObject recipe) {
        Map<String, Integer> counts = new HashMap<>();
        for (String input : ingredients(recipe)) counts.merge(input, 1, Integer::sum);
        return counts;
    }

    private void expand(String item, Set<String> visiting, Set<String> leaves) throws Exception {
        item = switch (item) {
            case "#c:dusts/diamond" -> "rematter:diamond_powder";
            case "#c:dusts/netherite" -> "rematter:netherite_powder";
            case "#rematter:full_ender_pearl_powders" -> "rematter:ender_pearl_powder";
            default -> item;
        };
        if (item.startsWith("#c:ingots/")) {
            String material = item.substring("#c:ingots/".length());
            String namespace = Set.of("iron", "copper", "gold").contains(material) ? "minecraft:" : "rematter:";
            expand(namespace + material + "_ingot", visiting, leaves);
            return;
        }
        if (item.startsWith("#c:dusts/")) { expand("rematter:" + item.substring("#c:dusts/".length()) + "_dust", visiting, leaves); return; }
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
            for (String role : List.of("primary", "secondary", "tertiary")) {
                if (!recipe.has(role)) continue;
                int count = recipe.has(role + "_count") ? recipe.get(role + "_count").getAsInt() : 1;
                for (int i = 0; i < count; i++) result.add(item(recipe.get(role)));
            }
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
