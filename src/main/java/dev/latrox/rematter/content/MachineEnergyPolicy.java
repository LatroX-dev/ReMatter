package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import org.jspecify.annotations.Nullable;

/**
 * Pure FE pricing for one batch: speed pays more in total, efficiency has a bounded 20% maximum saving.
 * The price is never reduced to fit the buffer; instead every catalogue entry must fit at full speed.
 */
public final class MachineEnergyPolicy {
    static final int MAX_UPGRADES = 4;
    /** Four speed upgrades double a batch price. */
    static final int MAX_SPEED_FACTOR = 2;
    /** The largest base price a catalogue entry may declare, so that it still fits the buffer at full speed. */
    public static final long MAX_BATCH_ENERGY = MachineBlockEntity.ENERGY_CAPACITY / MAX_SPEED_FACTOR;
    enum Upgrade { NONE, SPEED, EFFICIENCY }

    private MachineEnergyPolicy() {}

    /** Work units per tick for a base rate and the installed upgrade stack. */
    static int speed(int baseRate, Upgrade upgrade, int count) {
        return baseRate * (upgrade == Upgrade.SPEED ? 1 + installed(count) : 1);
    }

    /** A recipe base scaled by a machine-tier multiplier, saturating instead of overflowing. */
    static long scaled(long base, int multiplier) {
        if (base < 0 || multiplier < 0) throw new IllegalArgumentException("Negative energy");
        return multiplier != 0 && base > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : base * multiplier;
    }

    static long cost(long base, Upgrade upgrade, int count, MachineKind kind, @Nullable IndustrialPerformance performance) {
        if (base < 0) throw new IllegalArgumentException("Negative energy");
        long price = base;
        if (upgrade == Upgrade.SPEED) {
            long surcharge = scaled(base, installed(count)) / 4;
            price = base > Long.MAX_VALUE - surcharge ? Long.MAX_VALUE : base + surcharge;
        } else if (upgrade == Upgrade.EFFICIENCY) price -= (base / 20) * installed(count);
        return performance == null ? price : performance.energyCost(kind, price);
    }

    /** A batch whose price exceeds the buffer can never start; it is reported, never discounted. */
    static boolean fitsBuffer(long price) { return price <= MachineBlockEntity.ENERGY_CAPACITY; }

    /** Load-time check: rejects a catalogue containing a batch that could not fit the buffer at full speed. */
    public static void requireFits(ChemistryRegistry registry) {
        for (var profile : registry.profiles())
            requireFits(profile.itemId(), Math.max(profile.decompositionEnergy(), profile.synthesisEnergy()));
        for (var profile : registry.fluidProfiles())
            requireFits(profile.fluidId(), Math.max(profile.decompositionEnergy(), profile.synthesisEnergy()));
        for (var process : registry.processes()) requireFits(process.id().toString(), process.energy());
    }

    private static void requireFits(String id, long energy) {
        if (energy > MAX_BATCH_ENERGY)
            throw new IllegalArgumentException("Batch energy of " + id + " exceeds the " + MAX_BATCH_ENERGY + " FE limit");
    }

    private static int installed(int count) { return Math.clamp(count, 0, MAX_UPGRADES); }
}
