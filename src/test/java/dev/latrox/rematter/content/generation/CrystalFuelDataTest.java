package dev.latrox.rematter.content.generation;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CrystalFuelDataTest {
    @Test void onlyTheNineExplicitCrystalsCanBePricedAndPrimalIsNeverFuel() {
        assertEquals(9, CrystalFuelData.ALLOWED.size());
        for (String item : CrystalFuelData.ALLOWED) {
            var data = JsonParser.parseString("{\"schema_version\":1,\"item\":\"" + item + "\",\"energy\":2048,\"count\":1}").getAsJsonObject();
            assertEquals(2048, CrystalFuelData.parse(data).orElseThrow().energy());
            data.addProperty("enabled", false); assertTrue(CrystalFuelData.parse(data).isEmpty());
        }
        for (String item : new String[]{"rematter:primal_crystal", "rematter:primal_shard", "rematter:primal_cell_64k", "minecraft:diamond"})
            assertThrows(IllegalArgumentException.class, () -> new CrystalFuelData.Fuel(item, 1, 2048));
    }
    @Test void fractionalAndOverflowingEnergyAndSchemasCannotBecomeValidFuel() {
        for (String changed : new String[]{"\"energy\":1.5", "\"energy\":9223372036854775808", "\"energy\":-1", "\"energy\":0", "\"energy\":1000000001"}) {
            var json = JsonParser.parseString("{\"schema_version\":1,\"item\":\"rematter:matrix_crystal\"," + changed + "}").getAsJsonObject();
            assertThrows(RuntimeException.class, () -> CrystalFuelData.parse(json));
        }
        var schema = JsonParser.parseString("{\"schema_version\":1.5,\"item\":\"rematter:matrix_crystal\",\"energy\":2048}").getAsJsonObject();
        assertThrows(RuntimeException.class, () -> CrystalFuelData.parse(schema));
    }
    @Test void packOverridesAreAllDisabledUntilTheirActualRegenerativePathsAreAudited() throws Exception {
        for (String item : CrystalFuelData.ALLOWED) {
            var name = item.substring("rematter:".length());
            var file = Path.of("integrations/reforging/datapack/data/rematter/rematter/crystal_fuel", name + ".json");
            assertTrue(CrystalFuelData.parse(JsonParser.parseString(Files.readString(file)).getAsJsonObject()).isEmpty());
        }
    }
}
