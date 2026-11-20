package dev.latrox.rematter.client.anim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChamberFocusTest {
    private static final int[][] FACINGS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /**
     * The sound of a reactor follows the envelope its modules are drawn with, so the controller must arrive at the
     * key the module renderer uses. The module side is replayed here as the code does it: the server writes the beam
     * target (IndustrialModuleBlockEntity.setWorking, with the module standing where IndustrialStructure.worldPosition
     * puts its cell), the client reads it back as half blocks and turns it by the module's own facing.
     */
    @Test void everyModuleOfAChamberKeysTheControllersFocusForEveryShapeCellAndFacing() {
        int cx = -37, cy = 64, cz = 1203, checked = 0;
        for (int[] facing : FACINGS) for (int[] moduleFacing : FACINGS)
            for (int width = 3; width <= 9; width++) for (int depth = 3; depth <= 9; depth++) for (int height = 4; height <= 9; height++)
                for (int column = 1; column < width - 1; column++) {
                    int rightX = -facing[1], rightZ = facing[0], backX = -facing[0], backZ = -facing[1];
                    var controller = ChamberFocus.ofController(cx, cy, cz, facing[0], facing[1], width, height, depth, column);
                    // The field is drawn at the block centre plus (W/2 - c - .5) to the right and (D/2 - .5) to the back.
                    assertEquals(cx + .5 + rightX * (width / 2.0 - column - .5) + backX * (depth / 2.0 - .5), controller.x(), 1e-9);
                    assertEquals(cy + height / 2.0 - 1, controller.y(), 1e-9);
                    assertEquals(cz + .5 + rightZ * (width / 2.0 - column - .5) + backZ * (depth / 2.0 - .5), controller.z(), 1e-9);
                    for (int mx = 1 - column; mx <= width - column - 2; mx++) for (int md = 1; md <= depth - 2; md++)
                        for (int my : new int[]{0, height - 3}) {
                            int localX = width - 1 - 2 * column - 2 * mx, localZ = depth - 1 - 2 * md;
                            int worldX = localX * rightX + localZ * backX, worldZ = localX * rightZ + localZ * backZ;
                            int moduleRightX = -moduleFacing[1], moduleRightZ = moduleFacing[0], moduleBackX = -moduleFacing[0], moduleBackZ = -moduleFacing[1];
                            int targetX = 1 + worldX * moduleRightX + worldZ * moduleRightZ;
                            int targetY = height - 2 - 2 * my;
                            int targetZ = 1 + worldX * moduleBackX + worldZ * moduleBackZ;
                            assertTrue(Math.abs(targetX) <= 18 && Math.abs(targetY) <= 18 && Math.abs(targetZ) <= 18, "target survives the client clamp");
                            var module = ChamberFocus.ofModule(cx + rightX * mx + backX * md, cy + my, cz + rightZ * mx + backZ * md,
                                    moduleFacing[0], moduleFacing[1], targetX / 2.0F, targetY / 2.0F, targetZ / 2.0F);
                            assertEquals(controller, module);
                            checked++;
                        }
                }
        assertTrue(checked > 10000);
    }
    @Test void moduleFocusTurnsTheLocalTargetExactlyAsTheRendererDid() {
        // Facing-local target (2.5, 3, -1): 2 blocks to the module's right and 1.5 blocks in front of its centre.
        float tx = 2.5F, ty = 3, tz = -1;
        float dx = tx - .5F, dz = tz - .5F;
        assertEquals(new ChamberFocus(Math.round((10 + .5 + dx) * 2), 46, Math.round((30 + .5 + dz) * 2)), ChamberFocus.ofModule(10, 20, 30, 0, -1, tx, ty, tz));
        assertEquals(new ChamberFocus(Math.round((10 + .5 - dz) * 2), 46, Math.round((30 + .5 + dx) * 2)), ChamberFocus.ofModule(10, 20, 30, 1, 0, tx, ty, tz));
        assertEquals(new ChamberFocus(Math.round((10 + .5 - dx) * 2), 46, Math.round((30 + .5 - dz) * 2)), ChamberFocus.ofModule(10, 20, 30, 0, 1, tx, ty, tz));
        assertEquals(new ChamberFocus(Math.round((10 + .5 + dz) * 2), 46, Math.round((30 + .5 - dx) * 2)), ChamberFocus.ofModule(10, 20, 30, -1, 0, tx, ty, tz));
    }
}
