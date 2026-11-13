package dev.latrox.rematter.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.content.MachinePortMode;
import dev.latrox.rematter.content.PortAppearance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The generated port plates: a plain face for a side without a connection, seat and ring for a connection. */
class PortPlateModelsTest {
    private static final Path MODELS = Path.of("src/main/resources/assets/rematter/models/block");
    private static final List<String> SIDES = List.of("left", "right", "back", "bottom", "top");
    private static final List<String> FACES = List.of("east", "west", "south", "down", "up");

    private static JsonObject read(String name) throws Exception {
        return JsonParser.parseString(Files.readString(MODELS.resolve(name + ".json"))).getAsJsonObject();
    }

    @Test void aDisabledSideIsThePlainCasingFaceAndNothingElse() throws Exception {
        for (String casing : List.of("plain", "reactor")) for (int i = 0; i < SIDES.size(); i++) {
            var model = read("port_" + casing + "_" + SIDES.get(i) + "_disabled");
            assertEquals("rematter:block/port_side_" + SIDES.get(i), model.get("parent").getAsString());
            assertEquals(casing.equals("plain") ? "rematter:block/casing_plain" : "rematter:block/reactor_casing",
                    model.getAsJsonObject("textures").get("face").getAsString());
            assertFalse(model.getAsJsonObject("textures").has("ring"));
            var elements = read("port_side_" + SIDES.get(i)).getAsJsonArray("elements");
            assertEquals(1, elements.size());
            var faces = elements.get(0).getAsJsonObject().getAsJsonObject("faces");
            assertEquals(java.util.Set.of(FACES.get(i)), faces.keySet());
            assertEquals(FACES.get(i), faces.getAsJsonObject(FACES.get(i)).get("cullface").getAsString());
        }
    }

    @Test void everyRoleWearsTheRingOfTheMachinePortOfThatRole() throws Exception {
        for (var role : MachinePortMode.values()) {
            if (role == MachinePortMode.DISABLED) continue;
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/rematter/textures/block/machine_port_"
                    + role.getSerializedName() + ".png")));
            for (String casing : List.of("plain", "reactor")) for (String side : SIDES) {
                var textures = read("port_" + casing + "_" + side + "_" + role.getSerializedName()).getAsJsonObject("textures");
                assertEquals("rematter:block/machine_port_" + role.getSerializedName(), textures.get("ring").getAsString());
                assertTrue(textures.get("face").getAsString().endsWith("casing_port"), "a connection shows the painted seat");
            }
        }
    }

    @Test void cubeBlockStatesDecodeEveryDirectionAndShowTheActualInitialModes() throws Exception {
        for (String cube : List.of("energy_cube", "reinforced_energy_cube", "resonant_energy_cube")) {
            var state = JsonParser.parseString(Files.readString(MODELS.getParent().getParent().resolve("blockstates/" + cube + ".json"))).getAsJsonObject();
            for (var part : state.getAsJsonArray("multipart")) {
                var entry = part.getAsJsonObject().getAsJsonObject("apply");
                if (!entry.has("type")) continue;
                for (var side : entry.getAsJsonObject("sides").entrySet()) {
                    var models = side.getValue().getAsJsonObject();
                    assertEquals(java.util.Set.of("disabled", "energy_input", "energy_output"), models.keySet());
                    for (var role : models.keySet())
                        assertEquals(role, PortAppearance.valueOf(role.toUpperCase(java.util.Locale.ROOT)).getSerializedName());
                    assertEquals(side.getKey().equals("front") ? "energy_output" : "energy_input",
                            entry.getAsJsonObject("default").get(side.getKey()).getAsString());
                    assertNotEquals(models.get("energy_input"), models.get("energy_output"));
                }
            }
        }
    }

    @Test void theRingStandsHalfAPixelProudOfItsFaceAndASolidNeighbourHidesIt() throws Exception {
        for (int i = 0; i < SIDES.size(); i++) {
            String face = FACES.get(i);
            int axis = face.equals("east") || face.equals("west") ? 0 : face.equals("up") || face.equals("down") ? 1 : 2;
            boolean positive = face.equals("east") || face.equals("south") || face.equals("up");
            var elements = read("port_plate_" + SIDES.get(i)).getAsJsonArray("elements");
            assertEquals(5, elements.size(), "one face and four ring boxes");
            for (int index = 1; index < elements.size(); index++) {
                var box = elements.get(index).getAsJsonObject();
                double from = box.getAsJsonArray("from").get(axis).getAsDouble(), to = box.getAsJsonArray("to").get(axis).getAsDouble();
                assertEquals(positive ? 16 : -.5, from, 1e-9, "the ring starts on the casing plane");
                assertEquals(positive ? 16.5 : 0, to, 1e-9, "the ring is a real half-pixel step");
                assertTrue(box.getAsJsonObject("faces").has(face), "the outer face of the ring is drawn");
                assertFalse(box.getAsJsonObject("faces").has(opposite(face)), "the hidden inner face is not written");
                for (var drawn : box.getAsJsonObject("faces").entrySet())
                    assertEquals(face, drawn.getValue().getAsJsonObject().get("cullface").getAsString());
                for (int other = 0; other < 3; other++) if (other != axis) {
                    assertTrue(box.getAsJsonArray("from").get(other).getAsDouble() >= 2);
                    assertTrue(box.getAsJsonArray("to").get(other).getAsDouble() <= 14);
                }
            }
        }
    }

    private static String opposite(String face) {
        return switch (face) { case "east" -> "west"; case "west" -> "east"; case "south" -> "north"; case "up" -> "down"; default -> "up"; };
    }
}
