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
        if (elapsed == 0) return;
        // A blocked or paused job does no visual work. Retain its exact pose so
        // stopping does not snap a press or scanner back through its chamber.
        if (!active) { drive = 0; return; }
        double damping = 0.28;
        double decay = Math.exp(-damping * elapsed);
        // Integrating the starting speed makes every restart smooth, without
        // moving through time during which the server reported no work.
        double travelled = elapsed + (drive - 1) * (1 - decay) / damping;
        phase = (phase + travelled / 80) % 1;
        drive = 1 + (drive - 1) * decay;
    }

    public float phase() { return (float) phase; }
    public float drive() { return (float) drive; }
}
