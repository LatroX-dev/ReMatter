package dev.latrox.rematter.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemistryPrerequisitesTest {
    private static final Set<String> ITEMS = Set.of("example:copper_dust", "minecraft:sand");
    private static final Set<String> FLUIDS = Set.of("example:coolant");

    @Test
    void skipsBothDirectionsWhenAnOptionalModOrItemIsMissing() {
        String item = "\"required_mod\":\"example\",\"item\":\"example:copper_dust\"";
        String synthesis = item + ",\"category\":\"raw_material\"";
        assertFalse(available("material_profile", item, Set.of()));
        assertFalse(available("synthesis", synthesis, Set.of()));
        assertFalse(available("material_profile", item.replace("copper_dust", "missing"), Set.of("example")));
        assertFalse(available("synthesis", synthesis.replace("copper_dust", "missing"), Set.of("example")));
        assertTrue(available("material_profile", item, Set.of("example")));
        assertTrue(available("synthesis", synthesis, Set.of("example")));
    }

    @Test
    void checksTheFluidRegistryForOptionalFluidsAndTheirSynthesis() {
        String dependency = "\"required_mod\":\"example\",";
        assertTrue(available("fluid_profile", dependency + "\"fluid\":\"example:coolant\"", Set.of("example")));
        assertFalse(available("fluid_profile", dependency + "\"fluid\":\"example:missing\"", Set.of("example")));
        assertTrue(available("synthesis", dependency + "\"item\":\"example:coolant\",\"category\":\"fluid\"", Set.of("example")));
        assertFalse(available("synthesis", dependency + "\"item\":\"example:missing\",\"category\":\"fluid\"", Set.of("example")));
    }

    @Test
    void preservesStrictValidationForUnguardedTargetsAndHonoursExplicitDependencies() {
        assertTrue(available("material_profile", "\"item\":\"unknown:typo\"", Set.of()));
        assertTrue(available("material_profile", "\"required_mod\":\"rematter\",\"item\":\"rematter:typo\"", Set.of("rematter")));
        assertTrue(available("synthesis", "\"required_mod\":\"example\",\"item\":\"example:missing\",\"disabled\":true", Set.of("example")));
        assertFalse(available("material_profile", "\"item\":\"minecraft:sand\",\"required_item\":\"other:missing\"", Set.of()));
        assertFalse(available("process", "\"required_fluid\":\"example:missing\"", Set.of("example")));
    }

    private static boolean available(String kind, String fields, Set<String> mods) {
        var json = JsonParser.parseString("{" + fields + "}").getAsJsonObject();
        return ChemistryData.prerequisitesMet("rematter/" + kind, json, mods::contains, ITEMS::contains, FLUIDS::contains);
    }
}
