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
                .adjust(IndustrialBlueprint.Parameter.GRINDING, 1)
                .adjust(IndustrialBlueprint.Parameter.SHELL_STYLE, 1);
        assertEquals(IndustrialBlueprint.ShellStyle.ALL_GLASS, prep.shellStyle());
        assertEquals(4, prep.workRate());
        assertEquals(3, prep.performance().precision());
        assertEquals(prep, IndustrialBlueprint.restore(MachineKind.INDUSTRIAL_PREPARATOR, prep.save()).orElseThrow());
        assertEquals(prep, IndustrialBlueprint.fromJson(MachineKind.INDUSTRIAL_PREPARATOR, prep.toJson()));
    }

    @Test
    void blueprintShellStylesAndSymmetricModulePlacementWorkAcrossSizes() {
        // 1. Shell style switch controls BOM and planned wall/endcap materials:
        //    SIDES_GLASS (default) -> Glass on 4 side walls, Casing on floor & roof
        //    ALL_GLASS             -> Glass on side walls + open floor & roof, Casing on frame & mounts
        //    FULL_CASING           -> Casing everywhere (0 Glass)
        var base = IndustrialBlueprint.defaultFor(MachineKind.ENERGY_REACTOR)
                .adjust(IndustrialBlueprint.Parameter.CHAMBERS, 2)
                .adjust(IndustrialBlueprint.Parameter.COILS, 2); // 5x5x5 with 4 Fuel Cores + 4 Coils
        assertEquals(IndustrialBlueprint.ShellStyle.SIDES_GLASS, base.shellStyle());
        var allGlass = base.adjust(IndustrialBlueprint.Parameter.SHELL_STYLE, 1);
        assertEquals(IndustrialBlueprint.ShellStyle.ALL_GLASS, allGlass.shellStyle());
        var fullCasing = allGlass.adjust(IndustrialBlueprint.Parameter.SHELL_STYLE, 1);
        assertEquals(IndustrialBlueprint.ShellStyle.FULL_CASING, fullCasing.shellStyle());
        assertEquals(IndustrialBlueprint.ShellStyle.SIDES_GLASS, fullCasing.adjust(IndustrialBlueprint.Parameter.SHELL_STYLE, 1).shellStyle());
        assertEquals(IndustrialBlueprint.ShellStyle.FULL_CASING, base.adjust(IndustrialBlueprint.Parameter.SHELL_STYLE, -1).shellStyle());

        var sidesBom = base.requiredCounts();
        var allGlassBom = allGlass.requiredCounts();
        var casingBom = fullCasing.requiredCounts();
        assertFalse(casingBom.containsKey(GLASS), "FULL_CASING must require zero Glass");
        assertTrue(allGlassBom.get(GLASS) > sidesBom.get(GLASS), "ALL_GLASS must use more Glass than SIDES_GLASS");
        assertEquals(10, allGlassBom.get(GLASS) - sidesBom.get(GLASS),
                "5x5x5 with 4 ceiling + 4 floor corner mounts opens 5 roof + 5 floor glass tiles (+10 total)");
        assertEquals(sidesBom.get(CASING) + sidesBom.get(GLASS), casingBom.get(CASING),
                "FULL_CASING replaces all Glass with Casing 1:1");

        // 2. Concentric ring placement on 5x5x5 (3x3 interior):
        //    outer ring corners -> outer ring side midpoints (all 8 perimeter cells) -> center last!
        IndustrialStructure.Materials empty = (x, y, z) -> x == 0 && y == 0 && z == 0 ? CONTROLLER : AIR;
        var twoCore = IndustrialBlueprint.defaultFor(MachineKind.ENERGY_REACTOR); // 2 Fuel Cores (ceiling) + 2 Coils (floor)
        var coreCoords2 = twoCore.collectMismatches(empty).stream()
                .filter(m -> m.expected() == REACTOR_FUEL_CORE)
                .map(m -> m.cell().x() + "," + m.cell().depth())
                .toList();
        assertEquals(List.of("-1,1", "1,3"), coreCoords2, "2 modules must land in diagonally opposite corners");

        var fourCore = twoCore.adjust(IndustrialBlueprint.Parameter.CHAMBERS, 2);
        var coreCoords4 = fourCore.collectMismatches(empty).stream()
                .filter(m -> m.expected() == REACTOR_FUEL_CORE)
                .map(m -> m.cell().x() + "," + m.cell().depth())
                .toList();
        assertEquals(List.of("-1,1", "1,3", "1,1", "-1,3"), coreCoords4, "4 modules must fill all 4 corners in opposite pairs");

        var eightCore = fourCore.adjust(IndustrialBlueprint.Parameter.CHAMBERS, 4);
        var coreCoords8 = eightCore.collectMismatches(empty).stream()
                .filter(m -> m.expected() == REACTOR_FUEL_CORE)
                .map(m -> m.cell().x() + "," + m.cell().depth())
                .toList();
        assertEquals(List.of("-1,1", "1,3", "1,1", "-1,3", "0,1", "0,3", "-1,2", "1,2"), coreCoords8,
                "5..8th modules must fill all 4 side midpoints around the outer ring before touching center");

        var nineCore = eightCore.adjust(IndustrialBlueprint.Parameter.CHAMBERS, 1);
        var coreCoords9 = nineCore.collectMismatches(empty).stream()
                .filter(m -> m.expected() == REACTOR_FUEL_CORE)
                .map(m -> m.cell().x() + "," + m.cell().depth())
                .toList();
        assertEquals("0,2", coreCoords9.get(8), "9th module takes the inner center once outer ring is full");

        // 3. 7x7x7 (5x5 interior): outer ring corners (0..3) -> outer side midpoints (4..7) -> rest of outer ring (8..15)
        //    -> inner ring corners (16..19) -> inner ring side midpoints (20..23) -> center (24)!
        var big7 = new IndustrialBlueprint(MachineKind.ENERGY_REACTOR, 7, 5, 7, 3, 2, 25, 1, 0, 0, 0, IndustrialBlueprint.ShellStyle.ALL_GLASS);
        var bigCores = big7.collectMismatches(empty).stream()
                .filter(m -> m.expected() == REACTOR_FUEL_CORE)
                .map(IndustrialStructure.LocalMismatch::cell)
                .toList();
        assertEquals(25, bigCores.size());
        for (int i = 0; i < 4; i++) {
            var c = bigCores.get(i);
            assertTrue(Math.abs(c.x()) == 2 && (c.depth() == 1 || c.depth() == 5), "First 4 slots must be outer corners: " + c);
        }
        assertEquals(List.of("0,1", "0,5", "-2,3", "2,3"),
                bigCores.subList(4, 8).stream().map(c -> c.x() + "," + c.depth()).toList(),
                "Slots 4..7 on 5x5 must be opposite outer side midpoints");
        for (int i = 0; i < 16; i++) {
            var c = bigCores.get(i);
            assertTrue(Math.abs(c.x()) == 2 || c.depth() == 1 || c.depth() == 5,
                    "All first 16 slots must stay on outer ring before entering inner ring: " + c);
        }
        assertEquals(List.of("-1,2", "1,4", "1,2", "-1,4"),
                bigCores.subList(16, 20).stream().map(c -> c.x() + "," + c.depth()).toList(),
                "Slots 16..19 must restart with inner ring corners");
        assertEquals(List.of("0,2", "0,4", "-1,3", "1,3"),
                bigCores.subList(20, 24).stream().map(c -> c.x() + "," + c.depth()).toList(),
                "Slots 20..23 must take inner ring side midpoints");
        assertEquals("0,3", bigCores.get(24).x() + "," + bigCores.get(24).depth(), "25th slot is exact center");
        // Every opposite pair before the final center must mirror cleanly around (0, 3)!
        for (int pairStart = 0; pairStart < 24; pairStart += 2) {
            var a = bigCores.get(pairStart);
            var b = bigCores.get(pairStart + 1);
            assertEquals(0, a.x() + b.x(), "Opposite pair X must mirror around 0: " + a + " vs " + b);
            assertEquals(6, a.depth() + b.depth(), "Opposite pair depth must mirror around center 3: " + a + " vs " + b);
        }

        // 4. 9x9x9 (7x7 interior): all 24 outer-ring slots fill before inner ring corners at 24..27!
        var big9 = new IndustrialBlueprint(MachineKind.ENERGY_REACTOR, 9, 5, 9, 4, 2, 28, 1, 0, 0, 0, IndustrialBlueprint.ShellStyle.ALL_GLASS);
        var cores9 = big9.collectMismatches(empty).stream()
                .filter(m -> m.expected() == REACTOR_FUEL_CORE)
                .map(IndustrialStructure.LocalMismatch::cell)
                .toList();
        assertEquals(28, cores9.size());
        for (int i = 0; i < 24; i++) {
            var c = cores9.get(i);
            assertTrue(Math.abs(c.x()) == 3 || c.depth() == 1 || c.depth() == 7,
                    "All first 24 slots on 7x7 must fill the outer perimeter ring first: " + c);
        }
        assertEquals(List.of("-2,2", "2,6", "2,2", "-2,6"),
                cores9.subList(24, 28).stream().map(c -> c.x() + "," + c.depth()).toList(),
                "Slots 24..27 on 7x7 must start the next inner ring at its 4 corners");

        // 5. 5x5x5 Energy Matrix (3x3x3 interior):
        //    Phase 1: center column (0, 0..2, 2) stacked to the roof!
        //    Phase 2: 4 corners expanding evenly by height (y=0 across all 4 corners, then y=1, then y=2)!
        //    Phase 3: 4 cardinal sides expanding evenly by height (y=0 across all 4 sides, then y=1, then y=2)!
        var matrix3c4p = IndustrialBlueprint.defaultFor(MachineKind.ENERGY_MATRIX)
                .adjust(IndustrialBlueprint.Parameter.CHAMBERS, 1) // 3 Cells
                .adjust(IndustrialBlueprint.Parameter.COILS, 2);   // 4 Providers
        var matrixMismatches = matrix3c4p.collectMismatches(empty);
        var cellCoords = matrixMismatches.stream()
                .filter(m -> m.expected() == ENERGY_MATRIX_CELL)
                .map(m -> m.cell().x() + "," + m.cell().y() + "," + m.cell().depth())
                .toList();
        var providerCoords = matrixMismatches.stream()
                .filter(m -> m.expected() == ENERGY_MATRIX_PROVIDER)
                .map(m -> m.cell().x() + "," + m.cell().y() + "," + m.cell().depth())
                .toList();
        assertEquals(List.of("0,0,2", "0,1,2", "0,2,2"), cellCoords,
                "First 3 Matrix parts must stack the center column straight up to the roof");
        assertEquals(List.of("-1,0,1", "1,0,3", "1,0,1", "-1,0,3"), providerCoords,
                "Next 4 Matrix parts must spread evenly into all 4 floor corners in opposite pairs");

        // Same-type stacking check: default 5x5x5 Matrix (2 Cells + 2 Providers) must NOT stack a Provider on top of the 2 center Cells!
        var matrixDefault = IndustrialBlueprint.defaultFor(MachineKind.ENERGY_MATRIX);
        var defMismatches = matrixDefault.collectMismatches(empty);
        assertEquals(List.of("0,0,2", "0,1,2"), defMismatches.stream()
                .filter(m -> m.expected() == ENERGY_MATRIX_CELL)
                .map(m -> m.cell().x() + "," + m.cell().y() + "," + m.cell().depth()).toList(),
                "2 Cells stack cleanly in center column");
        assertEquals(List.of("-1,0,1", "1,0,3"), defMismatches.stream()
                .filter(m -> m.expected() == ENERGY_MATRIX_PROVIDER)
                .map(m -> m.cell().x() + "," + m.cell().y() + "," + m.cell().depth()).toList(),
                "2 Providers must take their own pure corner columns instead of stacking on top of Cells");

        // 5 Cells + 4 Providers on 5x5x5: Cells take center (3) + 2 opposite corners (2); Providers stack cleanly in the OTHER 2 corners (2 each)!
        var matrix5c4p = matrixDefault
                .adjust(IndustrialBlueprint.Parameter.CHAMBERS, 3)
                .adjust(IndustrialBlueprint.Parameter.COILS, 2);
        var m5c4p = matrix5c4p.collectMismatches(empty);
        assertEquals(List.of("1,0,1", "-1,0,3", "1,1,1", "-1,1,3"), m5c4p.stream()
                .filter(m -> m.expected() == ENERGY_MATRIX_PROVIDER)
                .map(m -> m.cell().x() + "," + m.cell().y() + "," + m.cell().depth()).toList(),
                "4 Providers must stack purely on the 2 remaining opposite corner columns without mixing onto Cell columns");

        // Full 7x7x7 Energy Matrix (5x5x5 interior, 125 blocks): verify phase order across 5x5 footprint:
        // [0..4] center pillar (5), [5..24] inner diagonals (20), [25..44] outer corners (20),
        // [45..64] inner sides (20), [65..84] outer sides (20), [85..124] remaining 8 columns evenly (40)!
        var matrix7 = new IndustrialBlueprint(MachineKind.ENERGY_MATRIX, 7, 7, 7, 3, 2, 124, 1, 0, 0, 0, IndustrialBlueprint.ShellStyle.SIDES_GLASS);
        var m7Parts = matrix7.collectMismatches(empty).stream()
                .filter(m -> m.expected() == ENERGY_MATRIX_CELL || m.expected() == ENERGY_MATRIX_PROVIDER)
                .map(IndustrialStructure.LocalMismatch::cell)
                .toList();
        assertEquals(125, m7Parts.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("0," + i + ",3", m7Parts.get(i).x() + "," + m7Parts.get(i).y() + "," + m7Parts.get(i).depth(),
                    "Slots 0..4 on 7x7x7 Matrix must stack center column (0,0..4,3)");
        }
        for (int i = 5; i < 25; i++) {
            var c = m7Parts.get(i);
            assertTrue(Math.abs(c.x()) == 1 && Math.abs(c.depth() - 3) == 1,
                    "Slots 5..24 must fill inner diagonals evenly: " + c);
        }
        for (int i = 25; i < 45; i++) {
            var c = m7Parts.get(i);
            assertTrue(Math.abs(c.x()) == 2 && Math.abs(c.depth() - 3) == 2,
                    "Slots 25..44 must expand evenly into outer corners: " + c);
        }
        for (int i = 45; i < 65; i++) {
            var c = m7Parts.get(i);
            assertTrue((c.x() == 0 && Math.abs(c.depth() - 3) == 1) || (Math.abs(c.x()) == 1 && c.depth() == 3),
                    "Slots 45..64 must expand from center along inner cardinal axes: " + c);
        }
        for (int i = 65; i < 85; i++) {
            var c = m7Parts.get(i);
            assertTrue((c.x() == 0 && Math.abs(c.depth() - 3) == 2) || (Math.abs(c.x()) == 2 && c.depth() == 3),
                    "Slots 65..84 must reach outer cardinal sides: " + c);
        }
        for (int i = 85; i < 125; i++) {
            var c = m7Parts.get(i);
            int dx = Math.abs(c.x()), dz = Math.abs(c.depth() - 3);
            assertTrue(dx > 0 && dz > 0 && dx != dz, "Slots 85..124 must evenly fill remaining columns: " + c);
        }
    }

    @Test
    void inferPartialBlueprintDetectsHalfToThreeQuarterBuiltChambersWithoutFalsePositives() {
        // 1. Stray nearby casings (< 8 blocks) or disconnected neighbor must NOT trigger partial inference.
        var stray = new java.util.HashMap<Long, IndustrialStructure.Material>();
        stray.put(coordKey(0, 0, 0), CONTROLLER);
        stray.put(coordKey(0, -1, 0), CASING);
        stray.put(coordKey(-1, -1, 0), CASING);
        stray.put(coordKey(1, -1, 0), CASING);
        assertTrue(IndustrialBlueprint.inferPartial(MachineKind.ENERGY_REACTOR,
                (x, y, z) -> stray.getOrDefault(coordKey(x, y, z), AIR)).isEmpty());

        // 2. Half-built 7x6x7 Energy Reactor with off-center controller (column=2, x in [-2..4], y in [-1..4], z in [0..6]):
        //    floor + front/left/right partial walls up to y=4, roof completely missing, rear wall mostly open, 3 ports placed!
        var partialShape = new IndustrialStructure.Shape(7, 6, 7, 2);
        var partial = new java.util.HashMap<Long, IndustrialStructure.Material>();
        for (var cell : IndustrialStructure.layout(partialShape)) {
            if (cell.role() == IndustrialStructure.Role.CONTROLLER) {
                partial.put(coordKey(cell.x(), cell.y(), cell.depth()), CONTROLLER);
            } else if (cell.y() == -1) {
                partial.put(coordKey(cell.x(), cell.y(), cell.depth()), CASING);
            } else if (cell.role() == IndustrialStructure.Role.FRAME && cell.y() < 4 && cell.depth() < 6) {
                partial.put(coordKey(cell.x(), cell.y(), cell.depth()), CASING);
            } else if (cell.role() == IndustrialStructure.Role.FRAME && cell.x() == -2 && cell.depth() == 0 && cell.y() == 4) {
                // One front-left corner reaches y=4 so height 6 is clear even without roof.
                partial.put(coordKey(cell.x(), cell.y(), cell.depth()), CASING);
            } else if (cell.role() == IndustrialStructure.Role.WALL && cell.y() == 0 && cell.depth() == 3 && (cell.x() == -2 || cell.x() == 4)) {
                partial.put(coordKey(cell.x(), cell.y(), cell.depth()), PORT);
            } else if (cell.role() == IndustrialStructure.Role.WALL && cell.y() == 0 && cell.depth() == 6 && cell.x() == 1) {
                partial.put(coordKey(cell.x(), cell.y(), cell.depth()), PORT);
            } else if (cell.role() == IndustrialStructure.Role.WALL && cell.x() == -2 && cell.y() >= 0 && cell.y() <= 2) {
                partial.put(coordKey(cell.x(), cell.y(), cell.depth()), GLASS);
            }
        }
        // Add a disconnected neighbor casing 2 blocks away to verify BFS ignores it!
        partial.put(coordKey(-5, -1, 0), CASING);
        var inferred = IndustrialBlueprint.inferPartial(MachineKind.ENERGY_REACTOR,
                (x, y, z) -> partial.getOrDefault(coordKey(x, y, z), AIR)).orElseThrow();
        assertEquals(7, inferred.width());
        assertEquals(6, inferred.height());
        assertEquals(7, inferred.depth());
        assertEquals(2, inferred.controllerColumn());
        assertEquals(3, inferred.ports());
        assertEquals(IndustrialBlueprint.ShellStyle.SIDES_GLASS, inferred.shellStyle());

        // 3. 3/4-built 5x5x5 Energy Matrix with roof glass -> detects ALL_GLASS!
        var matShape = new IndustrialStructure.Shape(5, 5, 5, 2);
        var matBlocks = new java.util.HashMap<Long, IndustrialStructure.Material>();
        for (var cell : IndustrialStructure.layout(matShape)) {
            if (cell.role() == IndustrialStructure.Role.CONTROLLER) {
                matBlocks.put(coordKey(cell.x(), cell.y(), cell.depth()), CONTROLLER);
            } else if (cell.role() == IndustrialStructure.Role.FRAME && cell.depth() < 4) {
                matBlocks.put(coordKey(cell.x(), cell.y(), cell.depth()), CASING);
            } else if (cell.role() == IndustrialStructure.Role.WALL && cell.y() == 3 && cell.x() == 0 && cell.depth() == 1) {
                matBlocks.put(coordKey(cell.x(), cell.y(), cell.depth()), GLASS);
            } else if (cell.role() == IndustrialStructure.Role.WALL && cell.y() == -1) {
                matBlocks.put(coordKey(cell.x(), cell.y(), cell.depth()), CASING);
            } else if (cell.role() == IndustrialStructure.Role.FRAME && cell.y() == -1) {
                matBlocks.put(coordKey(cell.x(), cell.y(), cell.depth()), CASING);
            }
        }
        var inferredMat = IndustrialBlueprint.inferPartial(MachineKind.ENERGY_MATRIX,
                (x, y, z) -> matBlocks.getOrDefault(coordKey(x, y, z), AIR)).orElseThrow();
        assertEquals(5, inferredMat.width());
        assertEquals(5, inferredMat.height());
        assertEquals(5, inferredMat.depth());
        assertEquals(IndustrialBlueprint.ShellStyle.ALL_GLASS, inferredMat.shellStyle());
    }

    private static long coordKey(int x, int y, int z) {
        return ((long) (x + 16) << 16) | ((long) (y + 16) << 8) | (z + 16);
    }

    @Test
    void blueprintDistributesPortsBottomFirstAndSymmetricallyKeepingFrontClear() {
        IndustrialStructure.Materials empty = (x, y, z) -> x == 0 && y == 0 && z == 0 ? CONTROLLER : AIR;
        var base5 = IndustrialBlueprint.defaultFor(MachineKind.ENERGY_REACTOR); // 5x5x5, 2 ports

        // 2 ports on 5x5x5: WEST center & EAST center on bottom wall row y == 0 (front completely clear!)
        var ports2 = base5.collectMismatches(empty).stream()
                .filter(m -> m.expected() == PORT)
                .map(m -> m.cell().x() + "," + m.cell().y() + "," + m.cell().depth())
                .toList();
        assertEquals(List.of("-2,0,2", "2,0,2"), ports2, "2 ports must sit opposite on WEST & EAST bottom row (y=0)");

        // 3 ports on 5x5x5: WEST center + EAST center + SOUTH center, all on y == 0!
        var ports3 = base5.adjust(IndustrialBlueprint.Parameter.PORTS, 1).collectMismatches(empty).stream()
                .filter(m -> m.expected() == PORT)
                .map(m -> m.cell().x() + "," + m.cell().y() + "," + m.cell().depth())
                .toList();
        assertEquals(List.of("-2,0,2", "2,0,2", "0,0,4"), ports3, "3 ports must form bottom-row T (WEST, EAST, SOUTH center)");

        // 4 ports on 5x5x5: WEST center + EAST center + symmetric SOUTH pair, all on y == 0!
        var ports4 = base5.adjust(IndustrialBlueprint.Parameter.PORTS, 2).collectMismatches(empty).stream()
                .filter(m -> m.expected() == PORT)
                .map(m -> m.cell().x() + "," + m.cell().y() + "," + m.cell().depth())
                .toList();
        assertEquals(List.of("-2,0,2", "2,0,2", "-1,0,4", "1,0,4"), ports4,
                "4 ports must sit symmetrically on WEST/EAST centers + SOUTH pair on bottom row (y=0)");

        // 9 ports on 5x5x5: all 9 bottom-row slots on WEST/EAST/SOUTH filled before touching front NORTH wall or y == 1!
        var ports9 = base5.adjust(IndustrialBlueprint.Parameter.PORTS, 7).collectMismatches(empty).stream()
                .filter(m -> m.expected() == PORT)
                .map(IndustrialStructure.LocalMismatch::cell)
                .toList();
        assertEquals(9, ports9.size());
        assertTrue(ports9.stream().allMatch(c -> c.y() == 0 && c.depth() > 0),
                "Up to 9 ports on 5x5x5 stay on bottom row (y=0) off the front controller wall: " + ports9);

        // 12 ports on 5x5x5: all 11 bottom-row slots filled first, only the 12th rises to y == 1!
        var ports12 = base5.adjust(IndustrialBlueprint.Parameter.PORTS, 10).collectMismatches(empty).stream()
                .filter(m -> m.expected() == PORT)
                .map(IndustrialStructure.LocalMismatch::cell)
                .toList();
        assertEquals(12, ports12.size());
        assertEquals(11, ports12.stream().filter(c -> c.y() == 0).count(), "Bottom row y=0 must fill completely first");
        assertEquals(1, ports12.stream().filter(c -> c.y() == 1).count(), "Only overflow port moves up to y=1");

        // 3x4x3 compact reactor: 2 ports on WEST & EAST y == 0, 3rd on SOUTH y == 0!
        var base3 = IndustrialBlueprint.defaultFor(MachineKind.INDUSTRIAL_REACTOR);
        var ports3x3 = base3.adjust(IndustrialBlueprint.Parameter.PORTS, 1).collectMismatches(empty).stream()
                .filter(m -> m.expected() == PORT)
                .map(m -> m.cell().x() + "," + m.cell().y() + "," + m.cell().depth())
                .toList();
        assertEquals(List.of("-1,0,1", "1,0,1", "0,0,2"), ports3x3, "3x4x3 ports must sit opposite on bottom row y=0");
    }

    @Test
    void exactLayoutBlueprintPreservesTiersMixedGlassAndClearsOnParameterEdit() {
        var shape = new IndustrialStructure.Shape(4, 3, 3, 1);
        var layout = new java.util.LinkedHashMap<Long, IndustrialStructure.Material>();
        for (var cell : IndustrialStructure.layout(shape)) {
            if (cell.role() == IndustrialStructure.Role.CONTROLLER) continue;
            long k = IndustrialBlueprint.localCoordKey(cell.x(), cell.y(), cell.depth());
            if (cell.role() == IndustrialStructure.Role.INTERIOR) {
                layout.put(k, cell.x() == 0
                        ? IndustrialStructure.Material.RESONANT_ENERGY_MATRIX_CELL
                        : IndustrialStructure.Material.PRIMAL_ENERGY_MATRIX_PROVIDER);
            } else if (cell.role() == IndustrialStructure.Role.WALL && cell.y() == 0 && cell.depth() == 1 && (cell.x() == -1 || cell.x() == 2)) {
                layout.put(k, PORT);
            } else if (cell.role() == IndustrialStructure.Role.WALL && cell.y() == 0 && cell.depth() == 2 && cell.x() == 0) {
                layout.put(k, GLASS);
            } else {
                layout.put(k, CASING);
            }
        }
        var bp = new IndustrialBlueprint(MachineKind.ENERGY_MATRIX, 4, 3, 3, 1, 2, 1, 1, 0, 0, 0,
                IndustrialBlueprint.ShellStyle.SIDES_GLASS).withExactLayout(layout);

        // NBT & JSON round-trip preserve exactLayout!
        var restored = IndustrialBlueprint.restore(MachineKind.ENERGY_MATRIX, bp.save()).orElseThrow();
        var fromJson = IndustrialBlueprint.fromJson(MachineKind.ENERGY_MATRIX, bp.toJson());
        assertEquals(bp, restored);
        assertEquals(bp, fromJson);

        var counts = restored.requiredCounts();
        assertEquals(1, counts.get(IndustrialStructure.Material.RESONANT_ENERGY_MATRIX_CELL));
        assertEquals(1, counts.get(IndustrialStructure.Material.PRIMAL_ENERGY_MATRIX_PROVIDER));
        assertEquals(1, counts.get(GLASS));
        assertEquals(2, counts.get(PORT));

        // Adjusting any parameter clears exactLayout and returns cleanly to parametric planning!
        var edited = restored.adjust(IndustrialBlueprint.Parameter.WIDTH, 1);
        assertTrue(edited.exactLayout().isEmpty());
        var restyled = restored.withShellStyle(IndustrialBlueprint.ShellStyle.FULL_CASING);
        assertTrue(restyled.exactLayout().isEmpty());

        // ControllerThroughput bounded shared budget & directed route check
        var ct = new ControllerThroughput();
        assertEquals(50L, ct.advanceBounded("sig", 100L, 1L, 1000L, 50L));
        assertFalse(ct.isComplete());
        assertEquals(0L, ct.advanceBounded("sig", 100L, 1L, 1000L, 0L));
        assertEquals(50L, ct.advanceBounded("sig", 100L, 2L, 1000L, 50L));
        assertTrue(ct.isComplete());
    }
}
