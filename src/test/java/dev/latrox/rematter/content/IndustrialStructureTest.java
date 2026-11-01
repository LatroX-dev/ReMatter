package dev.latrox.rematter.content;

import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialStructureTest {
    @Test void fixedShellHasActualInteriorAndAllEdgesAreFrame() {
        for (var kind : List.of(MachineKind.INDUSTRIAL_PREPARATOR, MachineKind.INDUSTRIAL_REACTOR)) {
            int height = IndustrialStructure.height(kind);
            var cells = IndustrialStructure.layout(kind);
            assertEquals(9 * height, cells.size());
            assertEquals(height - 2, cells.stream().filter(c -> c.role() == IndustrialStructure.Role.INTERIOR).count());
            assertEquals(1, cells.stream().filter(c -> c.role() == IndustrialStructure.Role.CONTROLLER).count());
            assertEquals(height == 3 ? 20 : 24, cells.stream().filter(c -> c.role() == IndustrialStructure.Role.FRAME).count());
            assertEquals(height == 3 ? 5 : 9, cells.stream().filter(c -> c.role() == IndustrialStructure.Role.WALL).count());
            for (var c : cells) if (c.role() == IndustrialStructure.Role.INTERIOR) {
                assertEquals(0, c.x()); assertEquals(1, c.depth()); assertTrue(c.y() >= 0 && c.y() <= height - 3);
            }
        }
    }
    @Test void allPortFacesPointOutsideTheFixedVolumeInEveryControllerOrientation() {
        var origin = new BlockPos(105, 101, 3);
        for (var kind : List.of(MachineKind.INDUSTRIAL_PREPARATOR, MachineKind.INDUSTRIAL_REACTOR)) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                var volume = new HashSet<BlockPos>();
                for (var c : IndustrialStructure.layout(kind)) volume.add(IndustrialStructure.worldPosition(origin, facing, c));
                assertEquals(9 * IndustrialStructure.height(kind), volume.size());
                for (var c : IndustrialStructure.layout(kind)) if (c.role() == IndustrialStructure.Role.WALL) {
                    var world = IndustrialStructure.worldPosition(origin, facing, c);
                    var outward = IndustrialStructure.worldDirection(facing, c.outward());
                    assertFalse(volume.contains(world.relative(outward)));
                    assertTrue(volume.contains(world.relative(outward.getOpposite())));
                }
            }
        }
    }
    @Test void northControllerIsAboveFloorAndHasThePromisedThreeBlockDeepFootprint() {
        var origin = new BlockPos(105, 101, 3);
        var positions = IndustrialStructure.layout(MachineKind.INDUSTRIAL_PREPARATOR).stream()
                .map(cell -> IndustrialStructure.worldPosition(origin, Direction.NORTH, cell)).toList();
        assertTrue(positions.contains(new BlockPos(104, 100, 3)));
        assertTrue(positions.contains(new BlockPos(106, 102, 5)));
        assertFalse(positions.contains(new BlockPos(105, 101, 2)));
        assertThrows(IllegalArgumentException.class, () -> IndustrialStructure.height(MachineKind.REACTOR));
        assertThrows(IllegalArgumentException.class, () -> IndustrialStructure.worldPosition(origin, Direction.UP,
                IndustrialStructure.layout(MachineKind.INDUSTRIAL_PREPARATOR).getFirst()));
    }
    @Test void portsCannotReplaceTheControllerEdgesCornersOrInterior() {
        for (var kind : List.of(MachineKind.INDUSTRIAL_PREPARATOR, MachineKind.INDUSTRIAL_REACTOR)) {
            for (var c : IndustrialStructure.layout(kind)) if (c.role() == IndustrialStructure.Role.WALL) {
                int boundaries = (c.x() != 0 ? 1 : 0) + (c.depth() != 1 ? 1 : 0)
                        + (c.y() == -1 || c.y() == IndustrialStructure.height(kind) - 2 ? 1 : 0);
                assertEquals(1, boundaries);
                assertFalse(c.x() == 0 && c.y() == 0 && c.depth() == 0);
                assertNotNull(c.outward());
            }
        }
    }
}
