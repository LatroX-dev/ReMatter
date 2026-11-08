package dev.latrox.rematter.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.latrox.rematter.chemistry.ShippedChemistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Offline guard for every shipped synthesis permission, before a server ever loads the data. */
class SynthesisOutputsTest {
    private static final List<Path> ROOTS = List.of(ShippedChemistry.MAIN_RESOURCES,
            Path.of("integrations/reforging/datapack"), Path.of("examples/chemistry-datapack"));

    @Test
    void namesOfManufacturedPartsArtifactsAndInternalComponentsAreRejected() {
        for (String name : List.of("example:iron_plate", "example:copper_wire", "example:iron_gear", "example:basic_control_circuit",
                "example:iron_rod", "example:steel_sheet", "example:copper_coil", "oritech:plastic_sheet", "minecraft:polished_granite",
                "minecraft:cut_copper", "minecraft:totem_of_undying", "minecraft:nether_star", "minecraft:echo_shard",
                "rematter:speed_upgrade", "rematter:reactor_casing", "rematter:reactor_glass", "rematter:structural_bars",
                "rematter:virelite_ceramic", "rematter:silith", "rematter:copper_matrix_crystal", "rematter:matrix_blend",
                "rematter:matter_cable", "rematter:reinforced_matter_cable")) {
            assertTrue(SynthesisOutputs.forbiddenName(name), name);
        }
        for (String name : List.of("minecraft:iron_ingot", "minecraft:sand", "alltheores:bronze_dust", "rematter:virelite_blend",
                "rematter:copper_dust", "minecraft:ender_pearl")) {
            assertFalse(SynthesisOutputs.forbiddenName(name), name);
        }
    }

    @Test
    void everyShippedItemPermissionPassesTheNameRulesAndMatchesTheCatalogue() throws IOException {
        Set<String> catalogued = new HashSet<>();
        JsonObject catalogue = ShippedChemistry.read(Path.of("tools/data/material_catalogue.json"));
        for (JsonElement row : catalogue.getAsJsonArray("items")) {
            if (row.getAsJsonObject().get("synthesis").getAsBoolean()) catalogued.add(row.getAsJsonObject().get("item").getAsString());
        }
        for (JsonElement row : catalogue.getAsJsonArray("fluids")) {
            if (row.getAsJsonObject().get("synthesis").getAsBoolean()) catalogued.add(row.getAsJsonObject().get("fluid").getAsString());
        }
        catalogue.getAsJsonArray("gases").forEach(row -> catalogued.add(row.getAsJsonObject().get("substance").getAsString()));
        int checked = 0;
        for (Path root : ROOTS) {
            try (var files = Files.walk(root.resolve("data"))) {
                for (Path file : files.filter(path -> path.toString().replace('\\', '/').contains("/rematter/synthesis/")
                        && path.toString().endsWith(".json")).toList()) {
                    JsonObject permission = ShippedChemistry.read(file);
                    if (permission.has("disabled") && permission.get("disabled").getAsBoolean()) continue;
                    String target = permission.get("item").getAsString();
                    String category = permission.get("category").getAsString();
                    if (category.equals("raw_material") || category.equals("natural_material")) {
                        assertFalse(SynthesisOutputs.forbiddenName(target), file.toString());
                    }
                    if (root.equals(ShippedChemistry.MAIN_RESOURCES)) {
                        assertTrue(catalogued.contains(target), "Permission outside the reviewed catalogue: " + file);
                    }
                    checked++;
                }
            }
        }
        assertTrue(checked >= catalogued.size());
    }
}
