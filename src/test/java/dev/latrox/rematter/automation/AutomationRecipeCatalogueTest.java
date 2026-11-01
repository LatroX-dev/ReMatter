package dev.latrox.rematter.automation;

import dev.latrox.rematter.chemistry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.latrox.rematter.automation.AutomationPort.*;

class AutomationRecipeCatalogueTest {
    @Test void everyAllowedItemFluidAndProcessIsOfferedWithoutAnIronSpecialCase() {
        var registry=BuiltinChemistry.create();
        var recipes=AutomationRecipeCatalogue.chemistry(registry,id->true,id->true);
        for(var profile:registry.profiles()) {
            var outputs=Map.of(Resource.item(profile.itemId()),(long)profile.itemCount());
            assertEquals(registry.synthesisPolicy().allowsItem(profile.itemId()),recipes.stream().anyMatch(r->r.workerKind().equals("material_synthesizer")&&r.outputs().equals(outputs)),profile.itemId());
            assertTrue(recipes.stream().anyMatch(r->r.workerKind().equals("decomposer")&&r.inputs().equals(outputs)&&r.outputs().equals(AutomationRecipeCatalogue.matter(profile.matter()))),profile.itemId());
        }
        for(var profile:registry.fluidProfiles()) {
            var outputs=Map.of(Resource.fluid(profile.fluidId()),(long)profile.amount());
            assertEquals(registry.synthesisPolicy().allowsFluid(profile.fluidId()),recipes.stream().anyMatch(r->r.workerKind().equals("material_synthesizer")&&r.outputs().equals(outputs)),profile.fluidId());
        }
        for(var process:registry.processes()) {
            var recipe=recipes.stream().filter(r->r.id().equals(AutomationRecipeCatalogue.id("process",process.id().toString()))).findFirst().orElseThrow();
            assertEquals(AutomationRecipeCatalogue.matter(process.inputs()),recipe.inputs());
            assertEquals(AutomationRecipeCatalogue.matter(process.outputs()),recipe.outputs());
            assertEquals(process.energy(),recipe.energy());assertEquals(process.ticks(),recipe.ticks());
        }
        assertTrue(recipes.stream().anyMatch(r->r.outputs().size()>1));
        assertTrue(recipes.stream().flatMap(r->r.outputs().keySet().stream()).anyMatch(r->r.phase().equals("gas")));
    }
    @Test void missingOptionalRegistrationsAreNotAdvertisedAsAirOrEmptyFluids() {
        var recipes=AutomationRecipeCatalogue.chemistry(BuiltinChemistry.create(),id->false,id->false);
        assertFalse(recipes.isEmpty());
        assertTrue(recipes.stream().flatMap(r->r.inputs().keySet().stream()).allMatch(r->r.kind()==Kind.MATTER));
        assertTrue(recipes.stream().flatMap(r->r.outputs().keySet().stream()).allMatch(r->r.kind()==Kind.MATTER));
    }
    @Test void revisionChangesWithCostTimingPhaseAndEveryByproduct() {
        var input=Map.of(Resource.item("test:feed"),1L);var output=Map.of(Resource.fluid("test:water"),1000L);
        var a=AutomationRecipeCatalogue.recipe("test:recipe","decomposer",input,output,100,20);
        assertNotEquals(a.revision(),AutomationRecipeCatalogue.recipe(a.id(),a.workerKind(),input,output,101,20).revision());
        assertNotEquals(a.revision(),AutomationRecipeCatalogue.recipe(a.id(),a.workerKind(),input,output,100,21).revision());
        var more=new TreeMap<>(output);more.put(Resource.matter(BuiltinChemistry.OXYGEN),5L);
        assertNotEquals(a.revision(),AutomationRecipeCatalogue.recipe(a.id(),a.workerKind(),input,more,100,20).revision());
        assertEquals(a,AutomationRecipeCatalogue.recipe(a.id(),a.workerKind(),new HashMap<>(input),new HashMap<>(output),100,20));
    }
}
