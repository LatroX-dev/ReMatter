package dev.latrox.rematter.chemistry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Material conservation, process boundaries and native optional-recipe contracts. */
class WorkshopMaterialsTest {
    private static final Path DATA = Path.of("src/main/resources/data");
    private static final List<String> FAMILIES = List.of("rhevon", "lumivex", "virelite");

    @Test void threeIngredientBatchesHaveExactQuantitiesAndRequirePower() throws Exception {
        for (String family : FAMILIES) {
            var mix = recipe(powder(family));
            assertEquals("rematter:preparation", mix.get("type").getAsString());
            assertEquals(1, mix.get("minimum_tier").getAsInt());
            assertEquals("rematter:" + powder(family), mix.getAsJsonObject("result").get("id").getAsString());
            assertEquals(4, mix.getAsJsonObject("result").get("count").getAsInt());
            assertEquals(8000, mix.get("energy").getAsInt());
            assertEquals(160, mix.get("ticks").getAsInt());
            assertTrue(mix.has("secondary") && mix.has("tertiary"));
            assertEquals(family.equals("virelite") ? 1 : 3, count(mix, "primary"));
            assertEquals(1, count(mix, "secondary"));
            assertEquals(1, count(mix, "tertiary"));
            var heat = recipe(product(family));
            assertEquals("sintering", heat.get("process").getAsString());
            assertEquals(1, heat.get("minimum_tier").getAsInt());
            assertEquals(family.equals("virelite") ? "rematter:virelite_blend" : "#c:dusts/" + family,
                    heat.get("primary").getAsString());
            assertFalse(heat.has("secondary") || heat.has("tertiary"));
            assertEquals(1, count(heat, "primary"));
            assertEquals(12000, heat.get("energy").getAsInt());
            assertEquals(200, heat.get("ticks").getAsInt());
            assertEquals("rematter:" + product(family), heat.getAsJsonObject("result").get("id").getAsString());
            assertEquals(1, heat.getAsJsonObject("result").get("count").getAsInt());
        }
        assertEquals("#c:dusts/iron", recipe("rhevon_dust").get("primary").getAsString());
        assertEquals("#c:dusts/copper", recipe("rhevon_dust").get("secondary").getAsString());
        assertEquals("rematter:rhedon", recipe("rhevon_dust").get("tertiary").getAsString());
        assertEquals("#c:dusts/copper", recipe("lumivex_dust").get("primary").getAsString());
        assertEquals("#c:dusts/gold", recipe("lumivex_dust").get("secondary").getAsString());
        assertEquals("rematter:lumin", recipe("lumivex_dust").get("tertiary").getAsString());
    }

    @Test void componentProfilesConserveTheWorkshopBatchAndKeepFiringRequired() throws Exception {
        var inputs = Map.of(
                "rhevon", Map.of("rematter:iron", 3000L, "rematter:copper", 1000L,
                        "rematter:silicon", 1000L, "rematter:oxygen", 2000L, "rematter:arcane_essence", 1000L),
                "lumivex", Map.of("rematter:copper", 3000L, "rematter:gold", 1000L,
                        "rematter:silicon", 1000L, "rematter:oxygen", 2000L, "rematter:nether_essence", 1000L),
                "virelite", Map.of("rematter:aluminum", 2000L, "rematter:silicon", 4000L,
                        "rematter:oxygen", 13000L, "rematter:hydrogen", 4000L));
        ChemistryRegistry elements = BuiltinChemistry.create();
        for (String family : FAMILIES) {
            JsonObject unfired = profile(powder(family)), finished = profile(product(family));
            assertEquals(unfired.getAsJsonArray("matter"), finished.getAsJsonArray("matter"));
            for (String state : List.of(family, powderState(family))) {
                assertFalse(Files.exists(DATA.resolve("rematter/rematter/substance/" + state + ".json")), state);
            }
            long count = recipe(powder(family)).getAsJsonObject("result").get("count").getAsLong();
            Map<String, Long> atoms = new HashMap<>();
            for (var entry : unfired.getAsJsonArray("matter")) {
                JsonObject component = entry.getAsJsonObject();
                SubstanceId id = SubstanceId.parse(component.get("substance").getAsString());
                var builtin = elements.definition(id);
                long amount = Math.multiplyExact(component.get("amount").getAsLong(), count);
                if (builtin.isPresent()) {
                    Phase phase = Phase.valueOf(component.get("phase").getAsString().toUpperCase(java.util.Locale.ROOT));
                    assertTrue(builtin.orElseThrow().supports(phase), id.toString());
                    builtin.orElseThrow().composition().multiply(amount)
                            .forEach((element, units) -> atoms.merge(element.toString(), units, Math::addExact));
                } else {
                    atoms.merge(id.toString(), amount, Math::addExact);
                }
            }
            assertEquals(inputs.get(family), atoms, family);
            String synthesis = "rematter/rematter/synthesis/compat/rematter/";
            assertEquals("raw_material", json(synthesis + powder(family) + ".json").get("category").getAsString());
            assertTrue(json(synthesis + product(family) + ".json").get("disabled").getAsBoolean());
        }
        // Chemical processes cannot use common elemental stock to bypass the physical firing stage.
        for (ProcessRecipe process : BuiltinChemistry.create().processes()) {
            for (SubstanceKey key : process.inputs().keySet()) assertFalse(isWorkshopState(key.id().path()));
            for (SubstanceKey key : process.outputs().keySet()) assertFalse(isWorkshopState(key.id().path()));
        }
        try (var files = Files.walk(DATA)) {
            for (Path path : files.filter(p -> p.toString().replace('\\', '/').contains("/rematter/process/")
                    && p.toString().endsWith(".json")).toList()) {
                String text = Files.readString(path);
                for (String family : FAMILIES) assertFalse(text.contains("rematter:" + family), path.toString());
            }
        }
    }

    @Test void theSamePowderHasOneFiringCostAndNoRoundTripYieldOrExperienceGain() throws Exception {
        for (String family : List.of("rhevon", "lumivex")) {
            var mixed = recipe(powder(family)).getAsJsonObject("result");
            var grind = recipe(family + "_dust_from_ingot");
            var reground = grind.getAsJsonObject("result");
            assertEquals("#c:ingots/" + family, grind.get("primary").getAsString());
            assertEquals(1, count(grind, "primary"));
            assertEquals(1, reground.get("count").getAsInt());
            assertEquals(mixed.get("id"), reground.get("id"));
            assertEquals(Set.of("id", "count"), mixed.keySet(), "Mixed powder has no hidden state components");
            assertEquals(mixed.keySet(), reground.keySet(), "Reground powder is the same plain item");
            assertEquals(profile(product(family)).get("matter"), profile(powder(family)).get("matter"),
                    "Grinding retains the complete ingredient vector");
            try (var files = Files.walk(DATA.resolve("rematter/recipe"))) {
                long firingPaths = 0;
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var candidate = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    if (candidate.has("process") && candidate.get("process").getAsString().equals("sintering")
                            && candidate.getAsJsonObject("result").get("id").getAsString().equals("rematter:" + product(family))) firingPaths++;
                }
                assertEquals(1, firingPaths, "Mixed and reground powder use the same firing recipe");
            }
            for (String method : List.of("smelting", "blasting")) {
                var recast = recipe(family + "_ingot_from_dust_" + method);
                assertEquals("#c:dusts/" + family, recast.get("ingredient").getAsString());
                assertEquals("rematter:" + product(family), recast.getAsJsonObject("result").get("id").getAsString());
                assertEquals(1, recast.getAsJsonObject("result").get("count").getAsInt());
                assertEquals(0.0, recast.get("experience").getAsDouble());
            }
            var dustTag = json("c/tags/item/dusts/" + family + ".json").getAsJsonArray("values");
            assertEquals(1, dustTag.size());
            assertEquals("rematter:" + family + "_dust", dustTag.get(0).getAsString());
        }
        for (String metal : List.of("iron", "copper", "gold")) {
            var matter = profile(metal + "_dust").getAsJsonArray("matter").get(0).getAsJsonObject();
            assertEquals("rematter:" + metal, matter.get("substance").getAsString());
            assertEquals(1000, matter.get("amount").getAsInt());
        }
    }

    @Test void optionalRecipesUseNativeSchemasAndPreserveTheSameBatch() throws Exception {
        for (String family : List.of("rhevon", "lumivex")) {
            var mekanism = recipe("compat/mekanism/crushing/" + family);
            assertGuard(mekanism, "mekanism");
            assertEquals("mekanism:crushing", mekanism.get("type").getAsString());
            assertEquals(1, mekanism.getAsJsonObject("input").get("count").getAsInt());
            assertEquals("#c:ingots/" + family, mekanism.getAsJsonObject("input").get("ingredient").getAsString());
            assertEquals("rematter:" + family + "_dust", mekanism.getAsJsonObject("output").get("id").getAsString());
            assertEquals(1, mekanism.getAsJsonObject("output").get("count").getAsInt());
            var oritech = recipe("compat/oritech/pulverizer/" + family);
            assertGuard(oritech, "oritech");
            assertEquals("oritech:pulverizer", oritech.get("type").getAsString());
            assertEquals(1, oritech.getAsJsonArray("itemInputs").size());
            assertEquals("#c:ingots/" + family, oritech.getAsJsonArray("itemInputs").get(0).getAsString());
            assertEquals(1, oritech.getAsJsonArray("itemResults").get(0).getAsJsonObject().get("count").getAsInt());
        }
        for (String family : FAMILIES) {
            var foundry = recipe("compat/oritech/foundry/" + family);
            assertGuard(foundry, "oritech");
            assertEquals("oritech:foundry", foundry.get("type").getAsString());
            assertEquals(2, foundry.getAsJsonArray("itemInputs").size());
            foundry.getAsJsonArray("itemInputs").forEach(input -> assertEquals("rematter:" + powder(family), input.getAsString()));
            var result = foundry.getAsJsonArray("itemResults").get(0).getAsJsonObject();
            assertEquals("rematter:" + product(family), result.get("id").getAsString());
            assertEquals(2, result.get("count").getAsInt());
        }
    }

    @Test void machineFallbacksYieldToRegisteredAlternativePowders() throws Exception {
        var mekanism = recipe("compat/mekanism/crushing/ender_pearl");
        assertGuard(mekanism, "mekanism");
        assertFallback(mekanism, List.of("ae2:ender_dust"));

        var oritech = recipe("compat/oritech/pulverizer/diamond");
        assertGuard(oritech, "oritech");
        assertFallback(oritech, List.of("alltheores:diamond_dust", "ftbmaterials:diamond_dust"));
    }

    @Test void onlyAlloyRecyclingIsExcludedFromAutomaticNetworkPatterns() throws Exception {
        Set<String> recycling = Set.of("rhevon_dust_from_ingot", "lumivex_dust_from_ingot");
        Path recipes = DATA.resolve("rematter/recipe");
        try (var files = Files.walk(recipes)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var recipe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!recipe.get("type").getAsString().equals("rematter:preparation")) continue;
                String id = recipes.relativize(file).toString().replace('\\', '/').replaceFirst("\\.json$", "");
                boolean exported = !recipe.has("network_recipe") || recipe.get("network_recipe").getAsBoolean();
                assertEquals(!recycling.contains(id), exported, id);
            }
        }
        for (String id : recycling) {
            var grind = recipe(id);
            assertFalse(grind.get("network_recipe").getAsBoolean());
            assertEquals("rematter:preparation", grind.get("type").getAsString());
            assertEquals(1, grind.getAsJsonObject("result").get("count").getAsInt(),
                    "Recycling remains a physical recipe for manual patterns and item automation");
        }
    }

    @Test void removedDuplicatePowdersHaveNoItemRecipeOrChemistryRegistration() throws Exception {
        String registrations = Files.readString(Path.of("src/main/java/dev/latrox/rematter/content/ModContent.java"));
        for (String family : List.of("rhevon", "lumivex")) {
            assertFalse(registrations.contains("\"" + family + "_blend\""));
            for (String path : List.of("recipe/", "rematter/substance/", "rematter/material_profile/compat/rematter/",
                    "rematter/synthesis/compat/rematter/")) {
                assertFalse(Files.exists(DATA.resolve("rematter/" + path + family + "_blend.json")));
            }
            assertFalse(Files.exists(DATA.resolve("rematter/recipe/" + family + "_ingot_from_dust.json")));
        }
        assertEquals(List.of("rematter:rhevon_dust", "rematter:lumivex_dust", "rematter:virelite_blend"),
                json("rematter/tags/item/workshop_powders.json").getAsJsonArray("values").asList().stream()
                        .map(e -> e.getAsString()).toList());
    }

    @Test void workshopMaterialsHaveConsumersButDoNotGateTheirOwnFurnace() throws Exception {
        var furnace = recipe("sinter_furnace");
        assertEquals("minecraft:crafting_shaped", furnace.get("type").getAsString());
        Set<String> allowed = Set.of("rematter:structural_bars", "minecraft:brick", "rematter:machine_frame",
                "rematter:control_circuit", "rematter:ember_matrix_crystal");
        furnace.getAsJsonObject("key").entrySet().forEach(e -> assertTrue(allowed.contains(e.getValue().getAsString())));
        assertEquals("rematter:rhevon_ingot", recipe("grinding_unit").getAsJsonObject("key").get("R").getAsString());
        assertEquals("rematter:lumivex_ingot", recipe("optical_circuit").getAsJsonObject("key").get("L").getAsString());
        assertEquals("rematter:virelite_ceramic", recipe("reaction_chamber").getAsJsonObject("key").get("V").getAsString());
        for (String component : List.of("sinter_furnace", "grinding_unit", "separation_unit", "mixing_basin", "field_coil", "optical_circuit", "reaction_chamber")) {
            assertFalse(Files.exists(DATA.resolve("rematter/rematter/synthesis/compat/rematter/" + component + ".json")));
        }
    }

    private void assertGuard(JsonObject recipe, String mod) {
        var conditions = recipe.getAsJsonArray("neoforge:conditions");
        assertEquals("neoforge:mod_loaded", conditions.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(mod, conditions.get(0).getAsJsonObject().get("modid").getAsString());
        assertRegistered(conditions.get(1).getAsJsonObject(), "minecraft:recipe_serializer", recipe.get("type").getAsString());
    }

    private void assertFallback(JsonObject recipe, List<String> alternatives) {
        var conditions = recipe.getAsJsonArray("neoforge:conditions");
        assertEquals(2 + alternatives.size(), conditions.size());
        for (int i = 0; i < alternatives.size(); i++) {
            var condition = conditions.get(i + 2).getAsJsonObject();
            assertEquals("neoforge:not", condition.get("type").getAsString());
            assertRegistered(condition.getAsJsonObject("value"), "minecraft:item", alternatives.get(i));
        }
    }

    private void assertRegistered(JsonObject condition, String registry, String id) {
        assertEquals("neoforge:registered", condition.get("type").getAsString());
        assertEquals(registry, condition.get("registry").getAsString());
        assertEquals(id, condition.get("value").getAsString());
    }
    private int count(JsonObject recipe, String role) { return recipe.has(role + "_count") ? recipe.get(role + "_count").getAsInt() : 1; }
    private String product(String family) { return family + (family.equals("virelite") ? "_ceramic" : "_ingot"); }
    private String powder(String family) { return family + (family.equals("virelite") ? "_blend" : "_dust"); }
    private String powderState(String family) { return family + (family.equals("virelite") ? "_blend" : "_powder"); }
    private boolean isWorkshopState(String state) { return FAMILIES.stream().anyMatch(f -> state.equals(f) || state.equals(powderState(f))); }
    private JsonObject profile(String item) throws Exception { return json("rematter/rematter/material_profile/compat/rematter/" + item + ".json"); }
    private JsonObject recipe(String name) throws Exception { return json("rematter/recipe/" + name + ".json"); }
    private JsonObject json(String path) throws Exception { return JsonParser.parseString(Files.readString(DATA.resolve(path))).getAsJsonObject(); }
}
