package dev.latrox.rematter.client;

import com.google.gson.JsonParser;
import dev.latrox.rematter.content.IndustrialModuleKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A recipe change cannot leave the module's dynamic field wearing its old crystal colour. */
class ModuleAppearanceTest {
    @Test void everyModulesFieldUsesItsRecipesCrystalHue() throws Exception {
        var hues=Map.of("rematter:amber_matrix_crystal",UiPalette.AMBER,
                "rematter:violet_matrix_crystal",UiPalette.VIOLET,
                "rematter:ember_matrix_crystal",UiPalette.EMBER,
                "rematter:blue_matrix_crystal",UiPalette.BLUE);
        for(var kind:IndustrialModuleKind.values()){
            var recipe=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/rematter/recipe",kind.id()+".json"))).getAsJsonObject();
            var crystals=recipe.getAsJsonObject("key").entrySet().stream()
                    .filter(entry->entry.getValue().isJsonPrimitive()).map(entry->entry.getValue().getAsString())
                    .filter(id->id.endsWith("_matrix_crystal")).distinct().toList();
            assertEquals(1,crystals.size(),kind.id()+" needs one identity hue");
            Integer expected=kind==IndustrialModuleKind.PRIMAL_CONDENSER_CORE?UiPalette.PRIMAL:hues.get(crystals.getFirst());
            assertNotNull(expected,kind.id()+" uses an unregistered module crystal");
            assertEquals(expected.intValue(),UiPalette.moduleAccent(kind),kind.id()+" field differs from its crafting crystal");
        }
    }
}
