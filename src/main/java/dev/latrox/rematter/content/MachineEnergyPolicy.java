package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import org.jspecify.annotations.Nullable;

/**
 * Pure FE pricing for one batch. All savings combined are bounded to 25% of the price before savings.
 * The price is never reduced to fit the buffer; instead every catalogue entry must fit at full speed.
 */
public final class MachineEnergyPolicy {
    static final int MAX_UPGRADES = 4;
    /** Four speed upgrades double a batch price. */
    static final int MAX_SPEED_FACTOR = 2;
    /** Speed multipliers for 0..4 Speed Upgrades: scales compact machines up to 10x (200 t -> 1.0 s). */
    private static final int[] SPEED_MULTIPLIERS = {1, 2, 4, 7, 10};
    /** The largest base price a catalogue entry may declare, so that it still fits the buffer at full speed. */
    public static final long MAX_BATCH_ENERGY = MachineBlockEntity.ENERGY_CAPACITY / MAX_SPEED_FACTOR;
    public enum Upgrade { NONE, SPEED, EFFICIENCY }

    private MachineEnergyPolicy() {}

    /** Work units per tick for a base rate and the installed upgrade stack. */
    static int speed(int baseRate, Upgrade upgrade, int count) {
        return baseRate * (upgrade == Upgrade.SPEED ? SPEED_MULTIPLIERS[installed(count)] : 1);
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

    public static long cost(long base, Upgrade upgrade, int count, MachineKind kind, @Nullable IndustrialPerformance performance) {
        if (base < 0) throw new IllegalArgumentException("Negative energy");
        long price = base;
        if (upgrade == Upgrade.SPEED) {
            long surcharge = scaled(base, installed(count)) / 4;
            price = base > Long.MAX_VALUE - surcharge ? Long.MAX_VALUE : base + surcharge;
        }
        long reference = price;
        if (upgrade == Upgrade.EFFICIENCY) price -= (base / 20) * installed(count);
        if (performance != null) price = performance.energyCost(kind, price);
        // Subtraction avoids overflow and rounds the debit upward, even for a one-FE batch.
        return Math.max(price, reference - reference / 4);
    }

    public static long combined(long base,int speed,int efficiency){
        if(base<0)throw new IllegalArgumentException("Negative energy");
        long surcharge=scaled(base,installed(speed))/4;
        long reference=base>Long.MAX_VALUE-surcharge?Long.MAX_VALUE:base+surcharge;
        long discount=base/20*installed(efficiency);
        return Math.max(reference-discount,reference-reference/4);
    }

    /** Load-time check: rejects a catalogue containing a batch that could not fit the buffer at full speed. */
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
