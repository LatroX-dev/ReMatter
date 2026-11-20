package dev.latrox.rematter.content;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.latrox.rematter.content.preparation.PreparationNetworkPolicy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreparationNetworkPolicyTest {
    private static boolean flag(String json) {
        return PreparationNetworkPolicy.CODEC.codec().parse(JsonOps.INSTANCE,JsonParser.parseString(json)).getOrThrow();
    }
    @Test void existingRecipesExportByDefaultAndExplicitFalseRoundTrips() {
        assertTrue(flag("{}"));
        assertTrue(flag("{\"network_recipe\":true}"));
        assertFalse(flag("{\"network_recipe\":false}"));
        var encoded=PreparationNetworkPolicy.CODEC.codec().encodeStart(JsonOps.INSTANCE,false).getOrThrow();
        assertFalse(PreparationNetworkPolicy.CODEC.codec().parse(JsonOps.INSTANCE,encoded).getOrThrow());
        assertFalse(encoded.getAsJsonObject().get("network_recipe").getAsBoolean());
    }
    @Test void invalidPublicationFlagDoesNotSilentlyDisableOrEnableARecipe() {
        assertTrue(PreparationNetworkPolicy.CODEC.codec().parse(JsonOps.INSTANCE,JsonParser.parseString("{\"network_recipe\":\"false\"}")).error().isPresent());
    }
    @Test void suppressingReverseGrindingRetainsMixingSinteringAndPhysicalRecipes() {
        var mixing=new Route("mix",List.of("iron_dust","copper_dust","rhedon"),"rhevon_dust",flag("{}"));
        var firing=new Route("sinter",List.of("rhevon_dust"),"rhevon_ingot",flag("{}"));
        var grinding=new Route("grind",List.of("rhevon_ingot"),"rhevon_dust",flag("{\"network_recipe\":false}"));
        var basePowder=new Route("iron_powder",List.of("iron_ingot"),"iron_dust",flag("{}"));
        var physical=List.of(mixing,firing,grinding,basePowder);
        assertTrue(cycle(physical,"rhevon_ingot",new HashSet<>()));
        var published=PreparationNetworkPolicy.exported(physical,Route::enabled);
        assertEquals(List.of(mixing,firing,basePowder),published);
        assertFalse(cycle(published,"rhevon_ingot",new HashSet<>()));
        assertEquals(List.of(mixing,firing,grinding,basePowder),physical);
        assertThrows(UnsupportedOperationException.class,()->published.add(grinding));
    }
    private static boolean cycle(List<Route> routes,String output,Set<String> active) {
        if(!active.add(output))return true;
        try {
            for(var route:routes)if(route.output.equals(output))for(var input:route.inputs)
                if(cycle(routes,input,active))return true;
            return false;
        } finally {active.remove(output);}
    }
    private record Route(String id,List<String> inputs,String output,boolean enabled) {}
}
