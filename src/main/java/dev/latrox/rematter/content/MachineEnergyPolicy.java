package dev.latrox.rematter.content;

import org.jspecify.annotations.Nullable;

/** Pure FE pricing for one batch: speed pays more in total, efficiency has a bounded 20% maximum saving. */
final class MachineEnergyPolicy {
    static final int MAX_UPGRADES = 4;
    enum Upgrade { NONE, SPEED, EFFICIENCY }

    private MachineEnergyPolicy() {}

    /** Work units per tick for a base rate and the installed upgrade stack. */
    static int speed(int baseRate, Upgrade upgrade, int count) {
        return baseRate * (upgrade == Upgrade.SPEED ? 1 + Math.min(MAX_UPGRADES, count) : 1);
    }

    /** A recipe base scaled by a machine-tier multiplier, saturating instead of overflowing. */
    static long scaled(long base, int multiplier) {
        if (base < 0 || multiplier < 0) throw new IllegalArgumentException("Negative energy");
        return multiplier != 0 && base > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : base * multiplier;
    }

    static long cost(long base, Upgrade upgrade, int count, MachineKind kind, @Nullable IndustrialPerformance performance) {
        if (base > MachineBlockEntity.ENERGY_CAPACITY) return base;
        int installed = Math.min(MAX_UPGRADES, count);
        if (upgrade == Upgrade.SPEED) base = Math.addExact(base, Math.multiplyExact(base, installed) / 4);
        else if (upgrade == Upgrade.EFFICIENCY) base -= (base / 20) * installed;
        if (performance != null) base = performance.energyCost(kind, base);
        return base;
    }
}
