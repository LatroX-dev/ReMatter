package dev.latrox.rematter.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latrox.rematter.content.MachinePortMode.Side;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The one table of connectable sides, and the generated block states and items that draw it. */
class PortSidesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/rematter");

    private static EnumSet<Side> available(MachineKind kind) {
        var sides = EnumSet.noneOf(Side.class);
        for (var side : Side.values()) if (PortSides.available(kind, side)) sides.add(side);
        return sides;
    }

    @Test void theFrontIsNeverAConnectionAndAnOpenTopIsNot() {
        for (var kind : MachineKind.values()) {
            assertFalse(PortSides.available(kind, Side.FRONT), kind.id());
            if (kind.openTop()) {
                assertEquals(EnumSet.of(Side.LEFT, Side.RIGHT, Side.BACK, Side.BOTTOM), available(kind), kind.id());
                assertEquals(PortSides.Closed.OPEN_TOP, PortSides.closed(kind, Side.TOP));
            }
            assertEquals(PortSides.Closed.FRONT, IndustrialStructure.isIndustrial(kind) ? PortSides.Closed.FRONT
                    : PortSides.closed(kind, Side.FRONT), kind.id());
        }
        assertEquals(EnumSet.of(Side.BACK), available(MachineKind.TERMINAL));
        assertTrue(available(MachineKind.INDUSTRIAL_REACTOR).isEmpty() && available(MachineKind.INDUSTRIAL_PREPARATOR).isEmpty());
        var sealed = PortSides.SEALED_TOP == PortSides.SealedTop.CLOSED
                ? EnumSet.of(Side.LEFT, Side.RIGHT, Side.BACK, Side.BOTTOM) : EnumSet.of(Side.LEFT, Side.RIGHT, Side.BACK, Side.BOTTOM, Side.TOP);
        for (var kind : new MachineKind[]{MachineKind.DRIVE, MachineKind.NETWORK_INTERFACE, MachineKind.PRIMAL_CONVERTER,
                MachineKind.CRYSTAL_GENERATOR, MachineKind.INDUSTRIAL_CRYSTAL_GENERATOR}) assertEquals(sealed, available(kind), kind.id());
        for (var side : Side.values()) assertTrue(PortSides.energyCube(side), "Energy Cubes keep all six sides");
    }

    @Test void packedRolesRoundTrip() {
        int packed = PortSides.pack(side -> MachinePortMode.values()[side.ordinal() + 2]);
        for (var side : Side.values()) assertEquals(MachinePortMode.values()[side.ordinal() + 2], PortSides.unpack(packed, side));
    }

    /** What the block can show equals what the table allows: a plate model per supported role, and nothing else. */
    @Test void generatedBlockStatesDrawExactlyTheConnectableSides() throws Exception {
        for (var kind : MachineKind.values()) {
            var state = JsonParser.parseString(Files.readString(ASSETS.resolve("blockstates/" + kind.id() + ".json"))).getAsJsonObject();
            int entries = 0;
            for (var part : state.getAsJsonArray("multipart")) {
                var apply = part.getAsJsonObject().get("apply");
                if (!apply.isJsonObject() || !apply.getAsJsonObject().has("type")) continue;
                var entry = apply.getAsJsonObject();
                assertEquals("rematter:port_plates", entry.get("type").getAsString());
                entries++;
                var sides = entry.getAsJsonObject("sides");
                var initial = entry.getAsJsonObject("default");
                for (var side : Side.values()) {
                    String key = side.name().toLowerCase(Locale.ROOT);
                    JsonObject models = sides.has(key) ? sides.getAsJsonObject(key) : new JsonObject();
                    boolean plates = models.keySet().stream().anyMatch(role -> !role.equals("disabled"));
                    assertEquals(PortSides.available(kind, side), plates, kind.id() + " " + key + " plates");
                    if (!PortSides.available(kind, side)) continue;
                    assertTrue(models.has("disabled"), kind.id() + " " + key + " has no plain face");
                    assertEquals(PortSides.initial(kind, side).getSerializedName(), initial.get(key).getAsString(), kind.id() + " " + key + " initial role");
                    for (var role : MachinePortMode.values())
                        if (role.supported(kind) && MachinePortMode.configurable(kind))
                            assertTrue(models.has(role.getSerializedName()), kind.id() + " " + key + " lacks " + role);
                }
            }
            assertEquals(available(kind).isEmpty() ? 0 : 4, entries, kind.id() + " port plate entries per facing");
        }
    }

    @Test void theItemOfADeviceShowsItsInitialRoles() throws Exception {
        for (var kind : MachineKind.values()) {
            if (available(kind).isEmpty()) continue;
            String item = Files.readString(ASSETS.resolve("items/" + kind.id() + ".json"));
            for (var side : Side.values()) {
                if (side == Side.FRONT || side == Side.TOP && kind.openTop()) continue;
                String key = side.name().toLowerCase(Locale.ROOT);
                String role = PortSides.initial(kind, side).getSerializedName();
                assertTrue(item.contains("_" + key + "_" + role + "\""), kind.id() + " item lacks its " + key + " " + role + " side");
            }
        }
    }

    @Test void ringColoursOfTheDiagramAreOpaqueAndDistinct() {
        var seen = new java.util.HashSet<Integer>();
        for (var role : MachinePortMode.values()) {
            assertEquals(0xff, role.ring() >>> 24);
            assertTrue(seen.add(role.ring()), role + " shares a ring colour");
        }
    }
}
