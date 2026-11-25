package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.content.preparation.PreparationTier;
import org.jspecify.annotations.Nullable;

/**
 * Pure FE pricing and Mekanism-style upgrade scaling.
 * Speed Upgrades accelerate work up to 10x (+25% batch FE per unmatched upgrade).
 * Efficiency Upgrades double internal FE capacity each (up to 16x), cancel Speed surcharges 1:1,
 * and save 5% base FE per excess upgrade (bounded so total cost never drops below 75% of base).
 */
public final class MachineEnergyPolicy {
    public static final int MAX_UPGRADES = 4;
    /** Four unmatched speed upgrades double a batch price. */
    static final int MAX_SPEED_FACTOR = 2;
    /** Speed multipliers for 0..4 Speed Upgrades: scales compact machines up to 10x (200 t -> 1.0 s). */
    private static final int[] SPEED_MULTIPLIERS = {1, 2, 4, 7, 10};
    /** Internal FE buffer multipliers for 0..4 Efficiency Upgrades (doubles per upgrade, up to 16x). */
    private static final int[] BUFFER_MULTIPLIERS = {1, 2, 4, 8, 16};
    /** The largest base price a catalogue entry may declare. Processing debits per tick, independent of buffer size. */
    public static final long MAX_BATCH_ENERGY = 16_000_000L;
    public enum Upgrade { NONE, SPEED, EFFICIENCY }

    private MachineEnergyPolicy() {}

    /** Whether this machine accepts Speed and Efficiency Upgrades. */
    public static boolean supportsUpgrades(MachineKind kind) {
        return kind.processes() && kind != MachineKind.PRIMAL_CONVERTER && !PreparationTier.manual(kind);
    }

    /** Base internal FE capacity of a machine before Efficiency Upgrades. */
    public static int baseEnergyCapacity(MachineKind kind) {
        return switch (kind) {
            case DRIVE, TERMINAL, NETWORK_INTERFACE, HAND_PREPARATOR, ENERGY_MATRIX, ENERGY_REACTOR -> 0;
            case CRYSTAL_GENERATOR -> MatterLimits.GENERATOR_ENERGY;
            case INDUSTRIAL_CRYSTAL_GENERATOR -> MatterLimits.INDUSTRIAL_GENERATOR_ENERGY;
            case PRIMAL_CONVERTER -> MatterLimits.CONVERTER_ENERGY;
            case TRANSMUTATION_REACTOR -> MatterLimits.TRANSMUTATION_REACTOR_ENERGY;
            case PRIMAL_REACTOR -> MatterLimits.PRIMAL_REACTOR_ENERGY;
            default -> IndustrialStructure.isIndustrial(kind)
                    ? MatterLimits.INDUSTRIAL_MACHINE_ENERGY
                    : MatterLimits.COMPACT_MACHINE_ENERGY;
        };
    }

    /** Effective internal FE buffer capacity after Efficiency Upgrades. */
    public static int energyCapacity(MachineKind kind, int efficiencyUpgrades) {
        int base = baseEnergyCapacity(kind);
        if (base == 0 || !supportsUpgrades(kind)) return base;
        return base * bufferMultiplier(efficiencyUpgrades);
    }

    /** Internal FE capacity multiplier for 0..4 Efficiency Upgrades. */
    public static int bufferMultiplier(int efficiencyUpgrades) {
        return BUFFER_MULTIPLIERS[installed(efficiencyUpgrades)];
    }

    /** Work-rate multiplier for 0..4 Speed Upgrades. */
    public static int speedMultiplier(int speedUpgrades) {
        return SPEED_MULTIPLIERS[installed(speedUpgrades)];
    }

    /** Work units per tick for a base rate and installed Speed Upgrades. */
    public static int speed(int baseRate, int speedUpgrades) {
        return baseRate * speedMultiplier(speedUpgrades);
    }

    /** Work units per tick for a base rate and the installed single upgrade stack. */
    static int speed(int baseRate, Upgrade upgrade, int count) {
        return upgrade == Upgrade.SPEED ? speed(baseRate, count) : baseRate;
    }

    /** Exact cumulative FE due by {@code progress} out of {@code duration} work units; reaches {@code totalEnergy} at completion. */
    public static long cumulativeEnergy(long totalEnergy, int progress, int duration) {
        if (totalEnergy < 0 || progress < 0 || duration < 1 || progress > duration)
            throw new IllegalArgumentException("Invalid progress energy step");
        if (totalEnergy == 0 || progress == 0) return 0;
        if (progress == duration) return totalEnergy;
        return dev.latrox.rematter.primal.PrimalPricing.ceilScale(totalEnergy, progress, duration);
    }

    /** FE due on the next tick when advancing up to {@code speed} work units from {@code progress}. */
    public static long stepEnergy(long totalEnergy, int progress, int speed, int duration, long alreadyPaid) {
        if (speed < 1 || alreadyPaid < 0) throw new IllegalArgumentException("Invalid step energy input");
        int next = (int) Math.min(duration, (long) progress + speed);
        return Math.max(0L, cumulativeEnergy(totalEnergy, next, duration) - alreadyPaid);
    }

    /** A recipe base scaled by a machine-tier multiplier, saturating instead of overflowing. */
    static long scaled(long base, int multiplier) {
        if (base < 0 || multiplier < 0) throw new IllegalArgumentException("Negative energy");
        return multiplier != 0 && base > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : base * multiplier;
    }

    public static long cost(long base, int speed, int efficiency, MachineKind kind, @Nullable IndustrialPerformance performance) {
        if (base < 0) throw new IllegalArgumentException("Negative energy");
        long price = combined(base, speed, efficiency);
        if (performance != null) price = performance.energyCost(kind, price);
        return Math.max(price, base - base / 4);
    }

    public static long cost(long base, Upgrade upgrade, int count, MachineKind kind, @Nullable IndustrialPerformance performance) {
        return cost(base, upgrade == Upgrade.SPEED ? count : 0, upgrade == Upgrade.EFFICIENCY ? count : 0, kind, performance);
    }

    /**
     * Mekanism-style combined batch FE cost:
     * Efficiency cancels Speed's +25% surcharge 1:1; excess Efficiency saves 5% of base each (max -20%),
     * and never reduces below 75% of base.
     */
    public static long combined(long base, int speed, int efficiency) {
        if (base < 0) throw new IllegalArgumentException("Negative energy");
        int s = installed(speed);
        int e = installed(efficiency);
        long price;
        if (s > e) {
            long surcharge = scaled(base, s - e) / 4;
            price = base > Long.MAX_VALUE - surcharge ? Long.MAX_VALUE : base + surcharge;
        } else {
            long discount = (base / 20) * (e - s);
            price = base - discount;
        }
        return Math.max(price, base - base / 4);
    }

    /** Nominal batch energy percentage relative to base cost (80..200%). */
    public static int costPercent(int speed, int efficiency) {
        int s = installed(speed);
        int e = installed(efficiency);
        return s > e ? 100 + 25 * (s - e) : 100 - 5 * (e - s);
    }

    /** Load-time check: rejects a catalogue containing a batch above the global catalogue ceiling. */
    public static void requireFits(ChemistryRegistry registry) {
        for (var profile : registry.profiles())
            requireFits(profile.itemId(), Math.max(profile.decompositionEnergy(), profile.synthesisEnergy()));
        for (var profile : registry.fluidProfiles())
            requireFits(profile.fluidId(), Math.max(profile.decompositionEnergy(), profile.synthesisEnergy()));
        for (var process : registry.processes()) requireFits(process.id().toString(), process.energy());
        for (var source : registry.environmentSources()) requireFits(source.id().toString(), source.energy());
    }

    private static void requireFits(String id, long energy) {
        if (energy > MAX_BATCH_ENERGY)
            throw new IllegalArgumentException("Batch energy of " + id + " exceeds the " + MAX_BATCH_ENERGY + " FE limit");
    }

    private static int installed(int count) { return Math.clamp(count, 0, MAX_UPGRADES); }
}
