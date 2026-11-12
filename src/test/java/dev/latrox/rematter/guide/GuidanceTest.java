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
        try (var files = Files.list(chapters)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                String text = Files.readString(file);
                assertFalse(Pattern.compile("/Users/|PROJECT-HANDOFF|chatgpt\\.com/space|private/|\\.claude/").matcher(text).find(), file.toString());
                var links = Pattern.compile("\\]\\(([^):]+\\.md)(?:#[^)]*)?\\)").matcher(text);
                while (links.find()) assertTrue(Files.isRegularFile(chapters.resolve(links.group(1))), file + ": " + links.group(1));
            }
        }
    }
    @Test void tenOptionalAdvancementsRequireRealServerHooksAndGrantNoRecipePermission() throws Exception {
        assertEquals(10, GuidanceEvent.values().length);
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
