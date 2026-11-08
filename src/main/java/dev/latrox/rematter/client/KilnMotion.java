package dev.latrox.rematter.client;

/** Stage-driven motion; pausing a process never advances its press. */
final class KilnMotion {
    private double lastTime = Double.NaN;
    private float compression, heat;

    float compression() { return compression; }
    float heat() { return heat; }

    void advance(double time, boolean active, int stage) {
        if (!Double.isFinite(time)) return;
        if (Double.isNaN(lastTime)) { lastTime = time; return; }
        float elapsed = (float) Math.clamp(time - lastTime, 0, 5);
        lastTime = time;
        if (!active || stage < 0 || stage > 2) {
            // A cooling glow is visual decay, never apparent production.
            heat = Math.max(0, heat - elapsed * .06F);
            return;
        }
        float targetCompression = stage == 0 || stage == 1 ? 1 : 0;
        compression += Math.clamp(targetCompression - compression, -elapsed * .045F, elapsed * .045F);
        float targetHeat = stage == 1 ? 1 : 0;
        heat += Math.clamp(targetHeat - heat, -elapsed * .06F, elapsed * .06F);
    }
}
