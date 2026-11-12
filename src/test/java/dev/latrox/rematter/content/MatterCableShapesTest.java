package dev.latrox.rematter.content;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatterCableShapesTest {
    private static final double EPSILON = 1e-9;

    private static int bits(Direction... sides) {
        int mask = 0;
        for (Direction side : sides) mask |= MatterCableShapes.bit(side);
        return mask;
    }
    /** Whether the shape fills the model-pixel point, probed with a small box around it. */
    private static boolean fills(VoxelShape shape, double x, double y, double z) {
        double r = .01;
        return Shapes.joinIsNotEmpty(shape, Shapes.box((x - r) / 16, (y - r) / 16, (z - r) / 16, (x + r) / 16, (y + r) / 16, (z + r) / 16), BooleanOp.AND);
    }
    /** Model-pixel point at a distance from the face on a side, moved off the cable axis by offset. */
    private static double[] point(Direction side, double depth, double offset) {
        double along = side.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? depth : 16 - depth;
        return switch (side.getAxis()) {
            case X -> new double[]{along, 8 + offset, 8 + offset};
            case Y -> new double[]{8 + offset, along, 8 + offset};
            case Z -> new double[]{8 + offset, 8 + offset, along};
        };
    }
    private static boolean fills(VoxelShape shape, double[] point) { return fills(shape, point[0], point[1], point[2]); }

    @Test void anUnconnectedCableIsItsFourPixelCore() {
        var bounds = MatterCableShapes.shape(0, 0).bounds();
        for (var axis : Direction.Axis.values()) {
            assertEquals(6 / 16D, bounds.min(axis), EPSILON);
            assertEquals(10 / 16D, bounds.max(axis), EPSILON);
        }
    }

    @Test void anArmReachesTheBlockFaceAtWireWidthAndADeviceArmAddsItsFlangeThere() {
        for (Direction side : Direction.values()) {
            var arm = MatterCableShapes.shape(bits(side), 0);
            var device = MatterCableShapes.shape(bits(side), bits(side));
            for (var shape : new VoxelShape[]{arm, device}) {
                assertTrue(fills(shape, point(side, .2, 1.8)), side + ": the wire reaches the face");
                assertTrue(fills(shape, point(side, 5, 1.8)), side + ": the wire meets the core");
                assertFalse(fills(shape, point(side.getOpposite(), 5, 0)), side + ": nothing grows on the far side");
            }
            // The flange is 6 px wide and 2 px deep; the wire is 4 px wide.
            assertFalse(fills(arm, point(side, 1, 2.8)), side + ": a cable-to-cable arm has no flange");
            assertTrue(fills(device, point(side, .2, 2.8)), side + ": the flange starts at the face");
            assertTrue(fills(device, point(side, 1.8, 2.8)), side + ": the flange is two pixels deep");
            assertFalse(fills(device, point(side, 2.2, 2.8)), side + ": the flange is two pixels deep");
            assertFalse(fills(device, point(side, 1, 3.2)), side + ": the flange is six pixels wide");
        }
    }

    @Test void aFlangeNeedsAConnectedSide() {
        for (Direction side : Direction.values()) {
            assertSame(MatterCableShapes.shape(0, 0), MatterCableShapes.shape(0, bits(side)));
            var other = Direction.values()[(side.ordinal() + 2) % 6];
            assertSame(MatterCableShapes.shape(bits(other), 0), MatterCableShapes.shape(bits(other), bits(side)));
        }
    }

    @Test void threeOrMoreConnectionsWidenTheCoreToTheHub() {
        var elbow = MatterCableShapes.shape(bits(Direction.NORTH, Direction.EAST), 0);
        var straight = MatterCableShapes.shape(bits(Direction.NORTH, Direction.SOUTH), 0);
        var tee = MatterCableShapes.shape(bits(Direction.NORTH, Direction.EAST, Direction.UP), 0);
        assertFalse(fills(elbow, 5.5, 5.5, 5.5));
        assertFalse(fills(straight, 5.5, 5.5, 8));
        assertTrue(fills(tee, 5.5, 5.5, 5.5));
        assertTrue(fills(tee, 10.5, 10.5, 10.5));
        assertFalse(fills(tee, 4.5, 4.5, 4.5));
    }

    @Test void everyCombinationStaysInsideTheBlockAndIsCached() {
        for (int arms = 0; arms < 64; arms++) for (int devices = 0; devices < 64; devices++) {
            var shape = MatterCableShapes.shape(arms, devices);
            assertSame(shape, MatterCableShapes.shape(arms, devices));
            assertSame(shape, MatterCableShapes.shape(arms, devices & arms));
            var bounds = shape.bounds();
            for (var axis : Direction.Axis.values()) assertTrue(bounds.min(axis) >= 0 && bounds.max(axis) <= 1);
            for (Direction side : Direction.values()) {
                boolean arm = (arms & MatterCableShapes.bit(side)) != 0, device = arm && (devices & MatterCableShapes.bit(side)) != 0;
                assertEquals(arm, fills(shape, point(side, 1, 0)));
                assertEquals(device, fills(shape, point(side, 1, 2.8)));
            }
        }
    }
}
