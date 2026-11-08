package dev.latrox.rematter.content;

/** Installed hardware affects work and power only; it never changes recipe quantities. */
public record IndustrialPerformance(int grinding, int mixing, int separation, int coils, int chambers) {
    public static final int MAX_PARTS_PER_TYPE = 2 * (IndustrialStructure.MAX_SIZE - 2) * (IndustrialStructure.MAX_SIZE - 2);
    public static final IndustrialPerformance EMPTY = new IndustrialPerformance(0, 0, 0, 0, 0);

    public IndustrialPerformance {
        for (int count : new int[]{grinding, mixing, separation, coils, chambers})
            if (count < 0 || count > MAX_PARTS_PER_TYPE) throw new IllegalArgumentException("Invalid industrial part count");
    }

    public int workRate(MachineKind kind) {
        return switch (kind) {
            case INDUSTRIAL_PREPARATOR -> grinding > 0 && mixing > 0 && separation > 0 ? 2 * (grinding + mixing) : 0;
            case INDUSTRIAL_REACTOR -> coils > 0 && chambers > 0 ? 2 * chambers : 0;
            default -> 0;
        };
    }

    /** The first support is required. Additional supports asymptotically approach 25% savings. */
    public int energySavingPercent(MachineKind kind) {
        if (workRate(kind) == 0) return 0;
        int supports = kind == MachineKind.INDUSTRIAL_PREPARATOR ? separation : coils;
        return 25 * (supports - 1) / (supports + 2);
    }

    /** Round the FE debit upward, including tiny recipes, without multiplying a potentially huge cost. */
    public long energyCost(MachineKind kind, long energy) {
        if (energy < 0) throw new IllegalArgumentException("Negative energy");
        int saving = energySavingPercent(kind);
        return energy - (energy / 100 * saving + energy % 100 * saving / 100);
    }
}
