package dev.latrox.rematter.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.SynthesisCategory;
import dev.latrox.rematter.data.ChemistryDocuments;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchExampleTest {
    private static final Path ROOT = Path.of("examples/chemistry-datapack/data/rematter_example/rematter");

    private static JsonObject read(String relative) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relative))).getAsJsonObject();
    }

    @Test void snowExampleLearnsExactPhysicalFormsWithoutExpandingSynthesis() throws Exception {
        var builder = ChemistryRegistry.builder();
        BuiltinChemistry.create().substances().forEach(builder::addSubstance);
        var block = ChemistryDocuments.materialProfile(read("material_profile/snow_block.json"));
        var balls = ChemistryDocuments.materialProfile(read("material_profile/snowball.json"));
        assertEquals("minecraft:snow_block", block.itemId());
        assertEquals("minecraft:snowball", balls.itemId());
        assertEquals(1, block.itemCount());
        assertEquals(4, balls.itemCount());
        assertEquals(Map.of(BuiltinChemistry.ICE, 1_000L), block.matter());
        assertEquals(block.matter(), balls.matter());
        builder.addProfile(block).addProfile(balls);
        var permission = read("synthesis/snow_block.json");
        assertEquals(block.itemId(), permission.get("item").getAsString());
        assertEquals("natural_material", permission.get("category").getAsString());
        builder.allowSynthesis(block.itemId(), SynthesisCategory.NATURAL_MATERIAL);
        var chemistry = builder.build();
        var document = read("material_research/snow.json");
        var family = ResearchCatalogue.parse("rematter_example:snow", document, chemistry).orElseThrow();
        assertEquals("rematter_example:snow", family.id());
        assertEquals(Set.of(block.itemId(), balls.itemId()), family.items());
        assertEquals(Set.of(BuiltinChemistry.ICE), family.substances());
        assertTrue(family.derive());
        assertFalse(family.startingKnowledge());
        assertNull(family.discoveryTier());
        var catalogue = new ResearchCatalogue(java.util.List.of(family));
        assertEquals(family, catalogue.forItem(block.itemId()).orElseThrow());
        assertEquals(family, catalogue.forItem(balls.itemId()).orElseThrow());
        assertTrue(chemistry.synthesisPolicy().allowsItem(block.itemId()));
        assertFalse(chemistry.synthesisPolicy().allowsItem(balls.itemId()));

        // Matching elemental composition is insufficient: the stored phase must also match.
        document.getAsJsonArray("substances").get(0).getAsJsonObject().addProperty("phase", "liquid");
        assertThrows(IllegalArgumentException.class,
                () -> ResearchCatalogue.parse("rematter_example:snow", document, chemistry));
    }
}
