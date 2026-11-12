package dev.latrox.rematter.content;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Outline of a cable in the model space of its generated parts: a 4 px wire, a 6 px hub at three or more
 * connections, and a 6 x 6 x 2 px flange where an arm plugs into a device.
 */
final class MatterCableShapes {
    private static final int SIDES = 6;
    private static final VoxelShape CORE = toward(Direction.NORTH, 4, 6, 10);
    private static final VoxelShape HUB = toward(Direction.NORTH, 6, 5, 11);
    private static final VoxelShape[] ARMS = new VoxelShape[SIDES];
    private static final VoxelShape[] FLANGES = new VoxelShape[SIDES];
    private static final VoxelShape[] CACHE = new VoxelShape[1 << 2 * SIDES];
    static {
        for (Direction side : Direction.values()) {
            ARMS[side.get3DDataValue()] = toward(side, 4, 0, 6);
            FLANGES[side.get3DDataValue()] = toward(side, 6, 0, 2);
        }
    }

    private MatterCableShapes() {}

    static int bit(Direction side) { return 1 << side.get3DDataValue(); }

    /** Both masks use {@link #bit}. A flange exists only on a connected side. */
    static VoxelShape shape(int arms, int devices) {
        arms &= (1 << SIDES) - 1;
        devices &= arms;
        int key = arms << SIDES | devices;
        VoxelShape shape = CACHE[key];
        if (shape == null) CACHE[key] = shape = build(arms, devices);
        return shape;
    }

    private static VoxelShape build(int arms, int devices) {
        VoxelShape shape = Integer.bitCount(arms) < 3 ? CORE : HUB;
        for (int side = 0; side < SIDES; side++) {
            if ((arms & 1 << side) != 0) shape = Shapes.or(shape, ARMS[side]);
            if ((devices & 1 << side) != 0) shape = Shapes.or(shape, FLANGES[side]);
        }
        return shape;
    }

    /** A centred square bar of the given width; near and far are pixel distances from the block face on that side. */
    private static VoxelShape toward(Direction side, double width, double near, double far) {
        double low = (8 - width / 2) / 16, high = (8 + width / 2) / 16;
        boolean negative = side.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
        double from = (negative ? near : 16 - far) / 16, to = (negative ? far : 16 - near) / 16;
        return switch (side.getAxis()) {
            case X -> Shapes.box(from, low, low, to, high, high);
            case Y -> Shapes.box(low, from, low, high, to, high);
            case Z -> Shapes.box(low, low, from, high, high, to);
        };
    }
}
