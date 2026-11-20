package dev.latrox.rematter.primal;

/** Pure world-generation policy, kept independent of Minecraft's registry bootstrap. */
public final class PrimalPlacementPolicy {
    private PrimalPlacementPolicy() {}
    public static boolean permits(String actual, String expected, int minimumRadius, int x, int z) {
        return actual.equals(expected) && minimumRadius >= 0 && (long)x * x + (long)z * z >= (long)minimumRadius * minimumRadius;
    }
}
