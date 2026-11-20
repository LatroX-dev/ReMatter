package dev.latrox.rematter.chemistry;

import com.google.gson.JsonParser;
import dev.latrox.rematter.data.ChemistryDocuments;
import dev.latrox.rematter.network.CatalogWireCodec;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentSourceTest {
    @Test void environmentSourcesRemainSeparateFromBalancedRecipesAndSurviveTheCatalogueWire() {
        var registry=ShippedChemistry.standalone();var source=registry.environmentSources().iterator().next();
        assertEquals(Set.of("minecraft:overworld"),source.dimensions());assertTrue(source.requiresOpenAir());
        assertEquals(Map.of(BuiltinChemistry.OXYGEN,200L),source.outputs());
        var decoded=CatalogWireCodec.decode(CatalogWireCodec.encode(registry));
        assertEquals(source,decoded.environmentSource(source.id()).orElseThrow());
        assertEquals(registry.synthesisPolicy().requirement("minecraft:diamond"),decoded.synthesisPolicy().requirement("minecraft:diamond"));
        assertThrows(IllegalArgumentException.class,()->new ProcessRecipe(source.id(),ProcessType.REACTION,Map.of(),source.outputs(),source.energy(),source.ticks()));
    }
    @Test void environmentalDefinitionsRejectUnknownMatterSolidsAndUnboundedFreeProduction() {
        assertThrows(IllegalArgumentException.class,()->new EnvironmentSource(SubstanceId.parse("test:air"),Map.of(BuiltinChemistry.IRON,1L),100,20,Set.of("minecraft:overworld"),true));
        assertThrows(IllegalArgumentException.class,()->new EnvironmentSource(SubstanceId.parse("test:air"),Map.of(BuiltinChemistry.OXYGEN,1L),0,20,Set.of("minecraft:overworld"),true));
        var unknown=new EnvironmentSource(SubstanceId.parse("test:air"),Map.of(SubstanceKey.of("test:unknown",Phase.GAS),1L),100,20,Set.of("minecraft:overworld"),true);
        assertThrows(IllegalArgumentException.class,()->ChemistryFixtures.copyOf(BuiltinChemistry.create()).addEnvironmentSource(unknown).build());
    }
    @Test void explicitlyRenewableAndValidModConditionsAreRequired() {
        var json=JsonParser.parseString("{\"unavailable_with_mods\":[\"enderio\"]}").getAsJsonObject();
        assertFalse(ChemistryDocuments.modsMet(json,id->true));assertTrue(ChemistryDocuments.modsMet(json,id->false));
        assertThrows(IllegalArgumentException.class,()->ChemistryDocuments.environmentSource(SubstanceId.parse("test:air"),json));
        assertThrows(IllegalArgumentException.class,()->ChemistryDocuments.modsMet(JsonParser.parseString("{\"unavailable_with_mods\":[12]}").getAsJsonObject(),id->false));
    }
}
