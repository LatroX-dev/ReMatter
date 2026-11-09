package dev.latrox.rematter.content;

/** Shared physical capacities. Substance quantities are units, never implied masses. */
public final class MatterLimits {
    public static final long CELL_64K = 64_000;
    public static final long CELL_256K = 256_000;
    public static final long CELL_1M = 1_000_000;
    public static final int CELL_TYPES = 512;
    public static final int MACHINE_ENERGY = 2_400_000;
    public static final int MACHINE_FLUID = 16_000;
    private MatterLimits() {}
}
