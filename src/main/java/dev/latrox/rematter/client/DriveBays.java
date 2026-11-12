package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.latrox.rematter.content.CellStatus;
import dev.latrox.rematter.content.MachineCellAccess;
import net.minecraft.util.LightCoordsUtil;

/**
 * Mounted cells of the Matter Drive. Each occupied bay shows a faceplate, whose ivory bar tells the tier, and one
 * status light. Coordinates are those of the bay openings in the generated housing: the front faces north and +X is
 * the viewer's left, so bays count top to bottom in the left column first.
 */
final class DriveBays {
    /** Block sprites in the order of the indices below. */
    static final String[] SPRITES = {"drive_cell_64k", "drive_cell_256k", "drive_cell_1m", "drive_cell_unknown",
            "led_blue", "led_green", "led_amber", "led_red","drive_primal_cell_16k","drive_primal_cell_64k","drive_primal_cell_256k"};
    private static final int UNKNOWN_CELL = 3, LED_BLUE = 4, LED_GREEN = 5, LED_AMBER = 6, LED_RED = 7;
    static final float BAY_WIDTH = 6, BAY_HEIGHT = 3, FACE_Z = .5F;
    static final float LED_X = 4.5F, LED_Y = 1, LED_SIZE = 1, LED_DEPTH = .5F;
    /** The status sprites are one flat colour; the centre texels stay clear of the neighbouring atlas entries. */
    private static final float LED_UV0 = 7, LED_UV1 = 9;
    private static final int WHITE = UiPalette.WHITE;

    private DriveBays() {}

    /** Left edge of a bay opening, in model pixels. */
    static float x(int bay) { return MachineCellAccess.driveColumn(bay) == 0 ? 9 : 1; }
    /** Lower edge of a bay opening, in model pixels. */
    static float y(int bay) { return 10 - 4 * MachineCellAccess.driveRow(bay); }

    /** Sprite of the faceplate for a public tier: 1 to 3 are the capacities, anything else is an unreadable cell. */
    static int cellSprite(int tier) { return tier>=1&&tier<=3?tier-1:tier>=5&&tier<=7?tier+3:UNKNOWN_CELL; }

    /** Sprite of the status light, or -1 while the server has reported no status for the bay. */
    static int ledSprite(CellStatus status) {
        return switch (status) {
            case EMPTY -> LED_BLUE;
            case STORED -> LED_GREEN;
            case FULL -> LED_AMBER;
            case LOCKED -> LED_RED;
            case ABSENT -> -1;
        };
    }

    /**
     * @param mask one bit per occupied bay
     * @param tiers three bits per bay, see {@link #cellSprite}
     * @param statuses three bits per bay, the ordinal of a {@link CellStatus}
     * @param faceColor shade of the housing front, as the chunk mesh applies it to the faces around the bays
     * @param faceLight packed light in front of the Drive
     */
    static void emit(PoseStack.Pose pose, VertexConsumer buffer, int mask, int tiers, int statuses, SpriteUv uv,
            int faceColor, int faceLight) {
        for (int bay = 0; bay < MachineCellAccess.DRIVE_BAYS; bay++) {
            if ((mask & 1 << bay) == 0) continue;
            float x0 = x(bay), y0 = y(bay);
            int cell = cellSprite(tiers >>> bay * 3 & 7);
            // Only the front of the cell is visible: rails, posts and bands cover its other sides.
            BlockQuads.north(pose, buffer, x0, y0, x0 + BAY_WIDTH, y0 + BAY_HEIGHT, FACE_Z,
                    uv.u(cell, 0), uv.v(cell, 0), uv.u(cell, BAY_WIDTH * 2), uv.v(cell, BAY_HEIGHT * 2), faceColor, faceLight);
            int led = ledSprite(CellStatus.decode(statuses >>> bay * 3 & 7));
            if (led < 0) continue;
            float lx0 = x0 + LED_X, ly0 = y0 + LED_Y, lx1 = lx0 + LED_SIZE, ly1 = ly0 + LED_SIZE;
            float z0 = FACE_Z - LED_DEPTH;
            float u0 = uv.u(led, LED_UV0), v0 = uv.v(led, LED_UV0), u1 = uv.u(led, LED_UV1), v1 = uv.v(led, LED_UV1);
            int bright = LightCoordsUtil.FULL_BRIGHT;
            // The light stands on the faceplate, so its back is never seen.
            BlockQuads.north(pose, buffer, lx0, ly0, lx1, ly1, z0, u0, v0, u1, v1, WHITE, bright);
            // Every side has the colour of the front: a darker half-pixel side would flicker at a distance.
            BlockQuads.east(pose, buffer, lx1, ly0, z0, ly1, FACE_Z, u0, v0, u1, v1, WHITE, bright);
            BlockQuads.west(pose, buffer, lx0, ly0, z0, ly1, FACE_Z, u0, v0, u1, v1, WHITE, bright);
            BlockQuads.up(pose, buffer, lx0, z0, lx1, FACE_Z, ly1, u0, v0, u1, v1, WHITE, bright);
            BlockQuads.down(pose, buffer, lx0, z0, lx1, FACE_Z, ly0, u0, v0, u1, v1, WHITE, bright);
        }
    }
}
