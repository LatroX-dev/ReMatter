package dev.latrox.rematter.research;

/** Discovery has a real minimum duration unaffected by ordinary Speed upgrades. */
public enum DiscoveryTier {
    I(4), II(8), III(16);
    public static final long STEP_ENERGY = 600_000;
    public static final long STEP_PRIMAL = 4_000;
    public static final int STEP_TICKS = 1_500;
    private final int steps;
    DiscoveryTier(int steps) { this.steps = steps; }
    public int steps() { return steps; }
    public long primal() { return STEP_PRIMAL * steps; }
    public long energy() { return STEP_ENERGY * steps; }
    public int minimumTicks() { return STEP_TICKS * steps; }
    public static DiscoveryTier of(int value) {
        if (value < 1 || value > values().length) throw new IllegalArgumentException("Invalid Discovery tier");
        return values()[value - 1];
    }
}
