package dev.latrox.rematter.guide;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MatterCableBlock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GuidanceTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static JsonObject json(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    @Test void offlineFactsAreExactIdempotentAndFutureDataRemainsPreserved() {
        var owner = UUID.randomUUID(); var facts = new GuidanceFacts();
        assertTrue(facts.record(owner, GuidanceEvent.PUT_IT_BACK)); assertFalse(facts.record(owner, GuidanceEvent.PUT_IT_BACK));
        assertEquals(Set.of(GuidanceEvent.PUT_IT_BACK), GuidanceFacts.fromJson(facts.toJson()).earned(owner));
        var future = new JsonObject(); future.addProperty("schema", 2); future.addProperty("opaque", "never erase");
        var locked = GuidanceFacts.fromJson(future); assertFalse(locked.readable());
        assertFalse(locked.record(owner, GuidanceEvent.FIRST_LESSON)); assertEquals(future, locked.toJson());
        var invalid = facts.toJson(); invalid.getAsJsonObject("earned").getAsJsonArray(owner.toString()).add("PUT_IT_BACK");
        assertFalse(GuidanceFacts.fromJson(invalid).readable());
    }
    @Test void everyMachineAndKeyCrystalHasTheSharedRoleAndNeedsPattern() throws Exception {
        for (var kind : MachineKind.values()) assertFalse(ItemGuidance.forItem("rematter:" + kind.id()).orElseThrow().hints().isEmpty(), kind.id());
        Map.of("ice", ItemGuidance.Role.RESEARCH, "copper", ItemGuidance.Role.BREAKDOWN, "leaf", ItemGuidance.Role.SYNTHESIS,
                "violet", ItemGuidance.Role.REACTION, "amber", ItemGuidance.Role.PREPARATION, "ember", ItemGuidance.Role.HEAT,
                "blue", ItemGuidance.Role.LINK, "mint", ItemGuidance.Role.STORAGE).forEach((colour, role) -> {
                    var spec = ItemGuidance.forItem("rematter:" + colour + "_matrix_crystal").orElseThrow();
                    assertEquals(role, spec.role()); assertEquals("tooltip.rematter.guidance.key_crystal", spec.hints().getFirst().key());
                });
        assertTrue(ItemGuidance.forItem("another_mod:machine").isEmpty());
        var english = json(RESOURCES.resolve("assets/rematter/lang/en_us.json"));
        for (String item : ItemGuidance.items()) {
            var spec = ItemGuidance.forItem(item).orElseThrow(); assertTrue(english.has(spec.role().key()), spec.role().key());
            spec.hints().forEach(hint -> assertTrue(english.has(hint.key()), hint.key()));
        }
    }
    @Test void cableTooltipsMatchActualRatesAndModuleRecipesUseTheirFunctionCrystal() throws Exception {
        var ids = new String[]{"matter_cable", "reinforced_matter_cable", "resonant_matter_cable"};
        for (int i = 0; i < ids.length; i++) {
            var hint = ItemGuidance.forItem("rematter:" + ids[i]).orElseThrow().hints().getFirst();
            assertEquals(java.util.List.of(MatterCableBlock.Tier.values()[i].matterRate(), MatterCableBlock.Tier.values()[i].energyRate()), hint.arguments());
        }
        for (String id : new String[]{"grinding_unit", "separation_unit", "mixing_basin", "field_coil", "reaction_chamber"}) {
            var recipe = json(RESOURCES.resolve("data/rematter/recipe/" + id + ".json"));
            assertEquals("rematter:" + (id.equals("field_coil") || id.equals("reaction_chamber") ? "violet" : "amber") + "_matrix_crystal", recipe.getAsJsonObject("key").get("M").getAsString());
        }
    }
    @Test void guideTopicsExistInBothBooksAndPublicChaptersContainNoPlanningReferences() throws Exception {
        var english = json(RESOURCES.resolve("assets/rematter/lang/en_us.json"));
        Path chapters = RESOURCES.resolve("assets/rematter/guides/rematter/guide");
        for (var topic : GuideTopics.values()) {
            assertTrue(topic.bookPage() > 0 && topic.bookPage() <= GuideTopics.PAGE_COUNT);
            assertTrue(english.has("guide.rematter.page." + topic.bookPage())); assertTrue(Files.isRegularFile(chapters.resolve(topic.chapter())));
        }
        String research = english.get("guide.rematter.page.29").getAsString().toLowerCase(java.util.Locale.ROOT);
        assertTrue(research.contains("teaches") && research.contains("sample"), "The guide must explain sample-based research");
        assertFalse(english.get("guide.rematter.page.30").getAsString().contains("Analysis is not an"));
        for (int page = 1; page <= GuideTopics.PAGE_COUNT; page++)
            assertTrue(english.get("guide.rematter.page." + page).getAsString().split("\\n", -1).length <= 14, "Book text must fit the native 128-pixel text height: page " + page);
        assertFalse(english.get("guide.rematter.page.1").getAsString().toLowerCase(java.util.Locale.ROOT).contains("furnace"));
        try (var files = Files.list(chapters)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                String text = Files.readString(file);
                assertFalse(Pattern.compile("/Users/|PROJECT-HANDOFF|chatgpt\\.com/space|private/|\\.claude/").matcher(text).find(), file.toString());
                var links = Pattern.compile("\\]\\(([^):]+\\.md)(?:#[^)]*)?\\)").matcher(text);
                while (links.find()) assertTrue(Files.isRegularFile(chapters.resolve(links.group(1))), file + ": " + links.group(1));
            }
        }
    }
    @Test void guideChaptersCoverEveryModItemWithSequentialPositionsAndResolvableRecipeTags() throws Exception {
        Path chapters = RESOURCES.resolve("assets/rematter/guides/rematter/guide");
        Set<String> allItems = new java.util.HashSet<>();
        try (var files = Files.list(RESOURCES.resolve("assets/rematter/items"))) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> allItems.add("rematter:" + p.getFileName().toString().replaceFirst("\\.json$", "")));
        }
        Set<String> craftableOutputs = new java.util.HashSet<>();
        try (var recipes = Files.list(RESOURCES.resolve("data/rematter/recipe"))) {
            for (Path recipeFile : recipes.filter(p -> p.toString().endsWith(".json")).toList()) {
                var obj = json(recipeFile);
                if (obj.has("result")) {
                    var res = obj.get("result");
                    if (res.isJsonObject() && res.getAsJsonObject().has("id")) {
                        craftableOutputs.add(res.getAsJsonObject().get("id").getAsString());
                    } else if (res.isJsonPrimitive()) {
                        craftableOutputs.add(res.getAsString());
                    }
                }
            }
        }
        Path serviceFile = RESOURCES.resolve("META-INF/services/guideme.compiler.tags.RecipeTypeMappingSupplier");
        assertTrue(Files.isRegularFile(serviceFile), "GuideME RecipeTypeMappingSupplier service file must exist");
        assertTrue(Files.readString(serviceFile).contains(ReMatterGuideRecipes.class.getName()));

        var positions = new java.util.HashMap<Integer, String>();
        var indexedItems = new java.util.HashMap<String, String>();
        var posPattern = Pattern.compile("^\\s*position:\\s*(\\d+)\\s*$", Pattern.MULTILINE);
        var itemIdPattern = Pattern.compile("^\\s*-\\s*(rematter:[a-z0-9_]+)\\s*$", Pattern.MULTILINE);
        var recipeForPattern = Pattern.compile("<RecipeFor\\s+id=\"([^\"]+)\"\\s*/>");

        int childCount = 0;
        try (var mdFiles = Files.list(chapters)) {
            for (Path file : mdFiles.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                String name = file.getFileName().toString();
                String content = Files.readString(file);
                int endFrontmatter = content.indexOf("\n---\n", 4);
                assertTrue(content.startsWith("---\n") && endFrontmatter > 0, "Valid frontmatter required in " + name);
                String frontmatter = content.substring(4, endFrontmatter);
                String body = content.substring(endFrontmatter + 5);

                if (!name.equals("index.md")) {
                    childCount++;
                    var mPos = posPattern.matcher(frontmatter);
                    assertTrue(mPos.find(), "Missing navigation position in " + name);
                    int pos = Integer.parseInt(mPos.group(1));
                    assertNull(positions.put(pos, name), "Duplicate navigation position " + pos + " in " + name);
                }
                var mItems = itemIdPattern.matcher(frontmatter);
                while (mItems.find()) {
                    String itemId = mItems.group(1);
                    assertTrue(allItems.contains(itemId), "Unknown item_id " + itemId + " in " + name);
                    assertNull(indexedItems.put(itemId, name), "Duplicate item_id " + itemId + " in " + name);
                }
                var mRecipes = recipeForPattern.matcher(body);
                while (mRecipes.find()) {
                    String targetId = mRecipes.group(1);
                    assertTrue(craftableOutputs.contains(targetId), "Unresolvable <RecipeFor id=\"" + targetId + "\"> in " + name);
                }
            }
        }
        assertEquals(java.util.stream.IntStream.rangeClosed(1, childCount).boxed().collect(java.util.stream.Collectors.toSet()),
                positions.keySet(), "Child chapters must have contiguous positions 1.." + childCount);
        assertEquals(allItems, indexedItems.keySet(), "GuideME item_ids must cover every registered ReMatter item");
    }
    @Test void optionalAdvancementsRequireRealServerHooksAndGrantNoRecipePermission() throws Exception {
        assertTrue(java.util.List.of(GuidanceEvent.values()).contains(GuidanceEvent.SYNTHESIZED_LAVA));
        var english = json(RESOURCES.resolve("assets/rematter/lang/en_us.json"));
        for (var event : GuidanceEvent.values()) {
            var advancement = json(RESOURCES.resolve("data/rematter/advancement/progression/" + event.id() + ".json"));
            assertEquals("minecraft:impossible", advancement.getAsJsonObject("criteria").getAsJsonObject("earned").get("trigger").getAsString());
            assertFalse(advancement.has("rewards"));
            assertTrue(english.has("advancement.rematter." + event.id() + ".title"));
            assertTrue(english.has("advancement.rematter." + event.id() + ".description"));
        }
    }
}
