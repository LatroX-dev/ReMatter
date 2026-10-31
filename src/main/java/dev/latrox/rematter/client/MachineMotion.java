package dev.latrox.rematter.client;

/** Client-only mechanical motion. The server's public ACTIVE flag is the only input. */
public final class MachineMotion {
    private double lastTime = Double.NaN;
    private double phase;
    private double drive;

    public MachineMotion(long seed) {
        long mixed = (seed ^ (seed >>> 33)) * 0xff51afd7ed558ccdl;
        phase = (mixed >>> 40) / (double) (1L << 24);
    }

    public void advance(double time, boolean active) {
        if (!Double.isFinite(time)) return;
        if (Double.isNaN(lastTime)) { lastTime = time; return; }
        // Hidden or unloaded mechanisms do not catch up with a sudden burst.
        double elapsed = Math.clamp(time - lastTime, 0, 5);
        lastTime = time;
        double target = active ? 1 : 0;
        double damping = active ? 0.28 : 0.38;
        double decay = Math.exp(-damping * elapsed);
        // Integrating speed keeps the angle continuous when power changes.
        double travelled = target * elapsed + (drive - target) * (1 - decay) / damping;
        phase = (phase + travelled / 80) % 1;
        drive = target + (drive - target) * decay;
        if (!active && drive < 0.001) drive = 0;
    }

    public float phase() { return (float) phase; }
    public float drive() { return (float) drive; }
}
