package dev.latrox.rematter.content;

import dev.latrox.rematter.network.StructureHintPayload;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static dev.latrox.rematter.content.IndustrialStructure.Material.*;
import static org.junit.jupiter.api.Assertions.*;

class StructureDiagnosticsTest {
    private record Point(int x, int y, int depth) {}

    private static Map<Point, IndustrialStructure.Material> validChamber(MachineKind kind, IndustrialStructure.Shape shape) {
        var blocks = new HashMap<Point, IndustrialStructure.Material>();
        int ports = 0;
        for (var cell : IndustrialStructure.layout(shape)) {
            blocks.put(new Point(cell.x(), cell.y(), cell.depth()), switch (cell.role()) {
                case CONTROLLER -> CONTROLLER;
                case INTERIOR -> AIR;
                case FRAME -> CASING;
                case WALL -> cell.outward().getAxis().isVertical() ? CASING : ports++ < 2 ? PORT : GLASS;
            });
        }
        if (kind == MachineKind.INDUSTRIAL_PREPARATOR) {
            blocks.put(new Point(0, 0, 1), MIXING_BASIN);
            blocks.put(new Point(0, shape.height() - 3, 1), GRINDING_UNIT);
            blocks.put(new Point(0, 0, 2), SEPARATION_UNIT);
        } else {
            blocks.put(new Point(0, 0, 1), FIELD_COIL);
            blocks.put(new Point(0, shape.height() - 3, 1), REACTION_CHAMBER);
        }
        return blocks;
    }

    @Test
    void collectMismatchesReportsAllMissingShellPortsInteriorJunkAndModuleMounts() {
        var shape = new IndustrialStructure.Shape(5, 5, 5, 2);
        var valid = validChamber(MachineKind.INDUSTRIAL_PREPARATOR, shape);
        assertEquals(List.of(), IndustrialStructure.collectMismatches(
                MachineKind.INDUSTRIAL_PREPARATOR, shape, (x, y, z) -> valid.getOrDefault(new Point(x, y, z), AIR)));

        var broken = new HashMap<>(valid);
        broken.put(new Point(-2, 0, 0), AIR);
        broken.put(new Point(1, 1, 0), AIR);
        broken.put(new Point(1, 2, 2), OTHER);
        var mismatches = IndustrialStructure.collectMismatches(
                MachineKind.INDUSTRIAL_PREPARATOR, shape, (x, y, z) -> broken.getOrDefault(new Point(x, y, z), AIR));
        assertTrue(mismatches.size() >= 3);
        assertTrue(mismatches.stream().anyMatch(m -> m.cell().x() == -2 && m.cell().y() == 0 && m.cell().depth() == 0 && m.expected() == CASING));
        assertTrue(mismatches.stream().anyMatch(m -> m.cell().x() == 1 && m.cell().y() == 1 && m.cell().depth() == 0 && m.expected() == GLASS));
        assertTrue(mismatches.stream().anyMatch(m -> m.cell().x() == 1 && m.cell().y() == 2 && m.cell().depth() == 2 && m.expected() == AIR));

        var emptyShell = new HashMap<>(valid);
        emptyShell.entrySet().removeIf(e -> e.getValue().module());
        var mountHints = IndustrialStructure.collectMismatches(
                MachineKind.INDUSTRIAL_PREPARATOR, shape, (x, y, z) -> emptyShell.getOrDefault(new Point(x, y, z), AIR));
        assertFalse(mountHints.isEmpty());
        assertTrue(mountHints.stream().anyMatch(m -> m.orientation().equals("up") && m.expected() == MIXING_BASIN));
        assertTrue(mountHints.stream().anyMatch(m -> m.orientation().equals("down") && m.expected() == GRINDING_UNIT));
    }

    @Test
    void shortPreparatorProjectsMissingHeightAgainstMinimumChamberHeight() {
        var shortShape = new IndustrialStructure.Shape(3, 3, 4, 1);
        var blocks = validChamber(MachineKind.INDUSTRIAL_REACTOR, shortShape);
        var mismatches = IndustrialStructure.collectMismatches(
                MachineKind.INDUSTRIAL_PREPARATOR, shortShape, (x, y, z) -> blocks.getOrDefault(new Point(x, y, z), AIR));
        assertFalse(mismatches.isEmpty());
        assertTrue(mismatches.stream().anyMatch(m -> m.cell().y() == 3), "Must highlight upper frame/wall rows needed to reach height 5");
    }

    @Test
    void structureHintPayloadRoundTripsEntriesOrientationsAndBounds() {
        var entries = List.of(
                new StructureHintPayload.Entry(new BlockPos(11, 70, -8), "rematter:reactor_casing", ""),
                new StructureHintPayload.Entry(new BlockPos(12, 70, -7), "rematter:field_coil", "up"),
                new StructureHintPayload.Entry(new BlockPos(12, 72, -7), "rematter:reaction_chamber", "down"));
        var payload = new StructureHintPayload(
                7,
                new BlockPos(11, 70, -8),
                "rematter:reactor_casing",
                "",
                entries,
                true,
                new BlockPos(10, 69, -10),
                new BlockPos(14, 73, -6));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            StructureHintPayload.CODEC.encode(buf, payload);
            StructureHintPayload decoded = StructureHintPayload.CODEC.decode(buf);
            assertEquals(payload, decoded);
            assertTrue(decoded.showOverlay());
            assertEquals(0, buf.readableBytes());

            StructureHintPayload hidden = new StructureHintPayload(
                    7, payload.position(), payload.expected(), payload.orientation(),
                    entries, true, payload.minBounds(), payload.maxBounds(), false);
            StructureHintPayload.CODEC.encode(buf, hidden);
            StructureHintPayload decodedHidden = StructureHintPayload.CODEC.decode(buf);
            assertEquals(hidden, decodedHidden);
            assertFalse(decodedHidden.showOverlay());
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void industrialBlueprintAutoExpandsFootprintComputesEffectsAndExactBom() {
        var reactor = IndustrialBlueprint.defaultFor(MachineKind.INDUSTRIAL_REACTOR);
        assertEquals(3, reactor.width());
        assertEquals(4, reactor.height());
        assertEquals(3, reactor.depth());
        assertEquals(2, reactor.ports());
        assertEquals(1, reactor.chambers());
        assertEquals(1, reactor.coils());
        assertEquals(2, reactor.workRate());
        assertEquals(1, reactor.laneRoom());

        var defaultCounts = reactor.requiredCounts();
        assertEquals(1, defaultCounts.get(CONTROLLER));
        assertEquals(2, defaultCounts.get(PORT));
        assertEquals(1, defaultCounts.get(REACTION_CHAMBER));
        assertEquals(1, defaultCounts.get(FIELD_COIL));

        // Adding 2 more Reaction Chambers and 2 more Field Coils (total 6 modules > 2 mounts in 3x3) auto-grows footprint.
        var expanded = reactor
                .adjust(IndustrialBlueprint.Parameter.CHAMBERS, 2)
                .adjust(IndustrialBlueprint.Parameter.COILS, 2)
                .adjust(IndustrialBlueprint.Parameter.HEIGHT, 2)
                .adjust(IndustrialBlueprint.Parameter.PORTS, 2);
        assertEquals(3, expanded.chambers());
        assertEquals(3, expanded.coils());
        assertTrue(expanded.slotCapacity() >= 6, "Footprint must auto-expand to fit 6 modules");
        assertEquals(6, expanded.height());
        assertEquals(4, expanded.ports());
        assertEquals(2, expanded.workRate());
        assertEquals(3, expanded.laneRoom());
        assertEquals(3, expanded.performance().field());
        assertEquals(48_000L, expanded.performance().processRoom());

        // Against an empty world (only controller placed at local origin (0,0,0)), BOM missing counts equal required counts except controller.
        var mismatches = expanded.collectMismatches((x, y, z) -> x == 0 && y == 0 && z == 0 ? CONTROLLER : AIR);
        assertFalse(mismatches.isEmpty());
        var bom = expanded.billOfMaterials(mismatches);
        var chamberReq = bom.stream().filter(r -> r.material() == REACTION_CHAMBER).findFirst().orElseThrow();
        assertEquals(3, chamberReq.required());
        assertEquals(3, chamberReq.missing());
        var controllerReq = bom.stream().filter(r -> r.material() == CONTROLLER).findFirst().orElseThrow();
        assertEquals(1, controllerReq.required());
        assertEquals(0, controllerReq.missing());

        // Shrinking width/depth back down trims module counts cleanly without dropping below 1 of each required module.
        var shrunk = expanded
                .adjust(IndustrialBlueprint.Parameter.WIDTH, -10)
                .adjust(IndustrialBlueprint.Parameter.DEPTH, -10);
        assertEquals(3, shrunk.width());
        assertEquals(3, shrunk.depth());
        assertEquals(1, shrunk.chambers());
        assertEquals(1, shrunk.coils());

        // Verify NBT and JSON round-trip on Industrial Preparator blueprint.
        var prep = IndustrialBlueprint.defaultFor(MachineKind.INDUSTRIAL_PREPARATOR)
                .adjust(IndustrialBlueprint.Parameter.SEPARATION, 2)
                .adjust(IndustrialBlueprint.Parameter.GRINDING, 1);
        assertEquals(4, prep.workRate());
        assertEquals(3, prep.performance().precision());
        assertEquals(prep, IndustrialBlueprint.restore(MachineKind.INDUSTRIAL_PREPARATOR, prep.save()).orElseThrow());
        assertEquals(prep, IndustrialBlueprint.fromJson(MachineKind.INDUSTRIAL_PREPARATOR, prep.toJson()));
    }
}
