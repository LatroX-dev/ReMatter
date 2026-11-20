package dev.latrox.rematter.content;

/** Installed hardware grants capabilities and simultaneous capacity; upgrades control speed and FE. */
public record IndustrialPerformance(int grinding, int mixing, int separation, int coils, int chambers) {
    public static final int MAX_PARTS_PER_TYPE = 2 * (IndustrialStructure.MAX_SIZE - 2) * (IndustrialStructure.MAX_SIZE - 2);
    public static final IndustrialPerformance EMPTY = new IndustrialPerformance(0, 0, 0, 0, 0);

    public IndustrialPerformance {
        for (int count : new int[]{grinding, mixing, separation, coils, chambers})
            if (count < 0 || count > MAX_PARTS_PER_TYPE) throw new IllegalArgumentException("Invalid industrial part count");
    }

    public int workRate(MachineKind kind) {
        return switch (kind) {
            case INDUSTRIAL_PREPARATOR -> grinding > 0 && mixing > 0 && separation > 0 ? 4 : 0;
            case INDUSTRIAL_REACTOR -> coils > 0 && chambers > 0 ? 2 : 0;
            default -> 0;
        };
    }

    public int field() { return coils; }
    public int precision() { return separation; }
    /** Space for all simultaneous recipe inputs, with no yield or speed multiplier. */
    public long processRoom() { return 16_000L * chambers; }
    public boolean supportsInputUnits(long simultaneousInputs) {
        return simultaneousInputs >= 0 && simultaneousInputs <= processRoom();
    }
    public int jobCapacity(java.util.Set<dev.latrox.rematter.content.preparation.PreparationStep> steps) {
        if (steps.isEmpty()) return 0;
        int result = MAX_PARTS_PER_TYPE;
        for (var step : steps) result = Math.min(result, switch (step) {
            case GRIND -> grinding;
            case MIX -> mixing;
            case SEPARATE -> separation;
        });
        return result;
    }

    /** Hardware never discounts power. Kept as the single policy hook used by FE pricing. */
    public int energySavingPercent(MachineKind kind) {
        return 0;
    }

    /** Hardware leaves the exact FE debit to the controller's upgrade policy. */
    public long energyCost(MachineKind kind, long energy) {
        if (energy < 0) throw new IllegalArgumentException("Negative energy");
        return energy;
    }
}
