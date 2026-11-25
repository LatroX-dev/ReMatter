package dev.latrox.rematter.content;

/** Shared physical capacities. Substance quantities are units, never implied masses. */
public final class MatterLimits {
    /**
     * Matter Cell capacities scaled to match AE2 (1k/4k/16k/64k) and Refined Storage (4k/16k/64k/256k)
     * storage ratios while preserving exact 1,000 u = 1 ingot / 1 bucket chemistry conservation.
     */
    public static final long CELL_4M = 4_000_000L;
    public static final long CELL_16M = 16_000_000L;
    public static final long CELL_64M = 64_000_000L;
    public static final long CELL_256M = 256_000_000L;
    public static final int CELL_TYPES = 512;
    public static final int COMPACT_MACHINE_ENERGY = 64_000;
    public static final int INDUSTRIAL_MACHINE_ENERGY = 512_000;
    public static final int TRANSMUTATION_REACTOR_ENERGY = 2_000_000;
    public static final int PRIMAL_REACTOR_ENERGY = 8_000_000;
    public static final int GENERATOR_ENERGY = 64_000;
    public static final int INDUSTRIAL_GENERATOR_ENERGY = 512_000;
    public static final int CONVERTER_ENERGY = 512_000;
    public static final int MAX_MACHINE_ENERGY = 128_000_000;
    public static final int MACHINE_FLUID = 16_000;
    private MatterLimits() {}
}
