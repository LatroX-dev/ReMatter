package dev.latrox.rematter.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedstoneAndComparatorTest {
    @Test
    void comparatorScaleFollowsVanillaZeroNonEmptyAndFullBounds() {
        assertEquals(0, MachineComparators.scale(0, 100));
        assertEquals(0, MachineComparators.scale(-5, 100));
        assertEquals(0, MachineComparators.scale(50, 0));
        assertEquals(1, MachineComparators.scale(1, 1_000_000));
        assertEquals(8, MachineComparators.scale(500, 1_000));
        assertEquals(14, MachineComparators.scale(999, 1_000));
        assertEquals(15, MachineComparators.scale(1_000, 1_000));
        assertEquals(15, MachineComparators.scale(1_500, 1_000));
    }

    @Test
    void redstoneModesGateSignalsAndCycleCleanly() {
        assertTrue(RedstoneMode.IGNORED.permits(false, false));
        assertTrue(RedstoneMode.IGNORED.permits(true, true));
        assertTrue(RedstoneMode.HIGH.permits(true, false));
        assertFalse(RedstoneMode.HIGH.permits(false, true));
        assertTrue(RedstoneMode.LOW.permits(false, false));
        assertFalse(RedstoneMode.LOW.permits(true, true));
        assertTrue(RedstoneMode.PULSE.permits(false, true));
        assertFalse(RedstoneMode.PULSE.permits(true, false));

        assertEquals(RedstoneMode.HIGH, RedstoneMode.IGNORED.next());
        assertEquals(RedstoneMode.LOW, RedstoneMode.HIGH.next());
        assertEquals(RedstoneMode.PULSE, RedstoneMode.LOW.next());
        assertEquals(RedstoneMode.IGNORED, RedstoneMode.PULSE.next());

        for (var mode : RedstoneMode.values()) {
            assertEquals(mode, RedstoneMode.load(mode.getSerializedName()));
            assertEquals(mode, RedstoneMode.byIndex(mode.ordinal()));
        }
        assertEquals(RedstoneMode.IGNORED, RedstoneMode.byIndex(-1));
        assertEquals(RedstoneMode.IGNORED, RedstoneMode.byIndex(99));
        assertThrows(IllegalArgumentException.class, () -> RedstoneMode.load("invalid"));

        assertTrue(RedstoneMode.controllable(MachineKind.DECOMPOSER));
        assertTrue(RedstoneMode.controllable(MachineKind.CRYSTAL_GENERATOR));
        assertTrue(RedstoneMode.controllable(MachineKind.INDUSTRIAL_REACTOR));
        assertFalse(RedstoneMode.controllable(MachineKind.HAND_PREPARATOR));
        assertFalse(RedstoneMode.controllable(MachineKind.DRIVE));
        assertFalse(RedstoneMode.controllable(MachineKind.TERMINAL));
        assertFalse(RedstoneMode.controllable(MachineKind.NETWORK_INTERFACE));
    }

    @Test
    void driveBayHitMappingMatchesViewerColumnsAndRowsAcrossAllHorizontalFacings() {
        BlockPos origin = new BlockPos(10, 64, -20);
        double[] rowYs = {11.0 / 16.0, 7.5 / 16.0, 3.5 / 16.0};
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Direction viewerLeft = facing.getClockWise();
            for (int col = 0; col < 2; col++) {
                double lateral = col == 0 ? 0.25 : -0.25;
                for (int row = 0; row < 3; row++) {
                    Vec3 point = new Vec3(
                            origin.getX() + 0.5 + facing.getStepX() * 0.5 + viewerLeft.getStepX() * lateral,
                            origin.getY() + rowYs[row],
                            origin.getZ() + 0.5 + facing.getStepZ() * 0.5 + viewerLeft.getStepZ() * lateral);
                    var hit = new BlockHitResult(point, facing, origin, false);
                    assertEquals(col * 3 + row, DriveBayInteraction.hitBay(origin, facing, hit),
                            facing + " col=" + col + " row=" + row);
                }
            }
        }
    }
}
