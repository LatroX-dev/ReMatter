package dev.latrox.rematter.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.SynthesisCategory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.research.ResearchLedgerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ResearchCatalogueTest {
    @Test void everyReviewedCatalogueFamilyAndExactFormHasOneResearchDocument() throws Exception {
        var source = JsonParser.parseString(Files.readString(Path.of("tools/data/material_catalogue.json"))).getAsJsonObject();
        var expected = new HashMap<String, Set<String>>();
        source.getAsJsonArray("items").forEach(e -> { var row = e.getAsJsonObject(); expected.computeIfAbsent(row.get("family").getAsString(), ignored -> new HashSet<>()).add(row.get("item").getAsString()); });
        assertEquals(166, expected.size()); var actual = new HashMap<String, Set<String>>(); int discoveries = 0;
        try (var paths = Files.list(Path.of("src/main/resources/data/rematter/rematter/material_research"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var doc = JsonParser.parseString(Files.readString(path)).getAsJsonObject(); var items = new HashSet<String>(); doc.getAsJsonArray("items").forEach(e -> items.add(e.getAsString()));
                actual.put(path.getFileName().toString().replace(".json", ""), items);
                assertFalse(doc.get("starting_knowledge").getAsBoolean());
                if (doc.get("discovery_tier").getAsInt() > 0) discoveries++;
            }
        }
        assertEquals(expected, actual); assertEquals(61, discoveries);
        assertEquals(0, read("echo").get("discovery_tier").getAsInt());
        assertEquals(0, read("stellarite").get("discovery_tier").getAsInt());
        assertEquals(0, read("rhevon").get("discovery_tier").getAsInt());
        assertEquals(0, read("awakened_supremium_essence").get("discovery_tier").getAsInt());
        assertEquals("minecraft:the_nether", read("netherite_scrap").get("requires_dimension").getAsString());
        assertEquals("Sand & Glass", read("silica").get("name").getAsString());
    }
    private JsonObject read(String id) throws Exception { return JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/rematter/rematter/material_research/" + id + ".json"))).getAsJsonObject(); }
    @Test void runtimeFiltersMissingOptionalItemsButDoesNotInferOrExpandSynthesis() throws Exception {
        var builder = ChemistryRegistry.builder(); BuiltinChemistry.create().substances().forEach(builder::addSubstance);
        builder.addProfile(new MaterialProfile("minecraft:sand", Map.of(SILICA, 1_000L), 8_000, 16_000, 100));
        builder.allowSynthesis("minecraft:sand", SynthesisCategory.NATURAL_MATERIAL);
        var chemistry = builder.build(); var family = ResearchCatalogue.parse("rematter:silica", read("silica"), chemistry).orElseThrow();
        assertEquals(Set.of("minecraft:sand"), family.items()); assertEquals(4_000, family.derivePrimal());
        assertTrue(ResearchCatalogue.parse("rematter:certus_quartz", read("certus_quartz"), chemistry).isEmpty());
        assertFalse(chemistry.synthesisPolicy().allowsItem("minecraft:glass"));
    }
    @Test void duplicateMembershipAndDefinitionChangesAreObservable() {
        var first = family("sand", DiscoveryTier.I, ""); var other = new MaterialResearch("rematter:other", "Other", first.items(), first.substances(), true, false, null, "", 4_000);
        assertThrows(IllegalArgumentException.class, () -> new ResearchCatalogue(List.of(first, other)));
        var changed = new MaterialResearch(first.id(), first.name(), first.items(), first.substances(), true, false, DiscoveryTier.II, "", 4_000);
        assertNotEquals(new ResearchCatalogue(List.of(first)).fingerprint(), new ResearchCatalogue(List.of(changed)).fingerprint());
    }
}
