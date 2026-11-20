package dev.latrox.rematter.client.anim;

/**
 * The centre of an industrial chamber in half blocks, the one key under which its modules and its controller meet.
 *
 * <p>Both derivations give the same three integers. With the controller at C, {@code right} and {@code back} the
 * unit steps of its facing turned clockwise and reversed, shape (W, H, D) and controller column c:
 * <ul>
 * <li>Controller: the renderer draws the field at C + 0.5 + right * (W/2 - c - 0.5) + back * (D/2 - 0.5) and
 * C.y + H/2 - 1. Doubled: 2C + 1 + right * (W - 2c - 1) + back * (D - 1), and 2C.y + H - 2.</li>
 * <li>Module in cell (mx, my, md) stands at M = C + right * mx + back * md + up * my. The server sends it the
 * target, in half blocks from its own corner, as 1 + right * (W - 1 - 2c - 2mx) + back * (D - 1 - 2md) turned into
 * the module's facing, and H - 2 - 2my. Doubled world position: 2M + 1 + right * (W - 1 - 2c - 2mx)
 * + back * (D - 1 - 2md) = 2C + 1 + right * (W - 2c - 1) + back * (D - 1), and 2M.y + H - 2 - 2my = 2C.y + H - 2.</li>
 * </ul>
 */
public record ChamberFocus(long x2, long y2, long z2) {
    /** Controller side. {@code facingX/Z} is the horizontal step of the controller's facing. */
    public static ChamberFocus ofController(int x, int y, int z, int facingX, int facingZ, int width, int height, int depth, int column) {
        // Clockwise of the facing is (-fz, fx); its opposite is (-fx, -fz).
        int along = width - 2 * column - 1, deep = depth - 1;
        return new ChamberFocus(2L * x + 1 - facingZ * along - facingX * deep, 2L * y + height - 2, 2L * z + 1 + facingX * along - facingZ * deep);
    }

    /** Module side. The beam target is in blocks from the module's corner, in the module's own facing. */
    public static ChamberFocus ofModule(int x, int y, int z, int facingX, int facingZ, float targetX, float targetY, float targetZ) {
        float dx = targetX - .5F, dz = targetZ - .5F;
        float rx = -facingZ * dx - facingX * dz, rz = facingX * dx - facingZ * dz;
        return new ChamberFocus(Math.round((x + .5 + rx) * 2), Math.round((y + (double) targetY) * 2), Math.round((z + .5 + rz) * 2));
    }

    public double x() { return x2 / 2.0; }
    public double y() { return y2 / 2.0; }
    public double z() { return z2 / 2.0; }
}
