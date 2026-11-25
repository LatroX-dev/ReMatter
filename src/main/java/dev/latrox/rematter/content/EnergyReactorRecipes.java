package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure server-side balancing and fuel/coolant rules for the endgame Energy Reactor multiblock.
 * Safe by design: optional coolant grants Cooled Overdrive (Water: +20% FE/t, -15% fuel;
 * Nitrogen Cryo Overdrive: +30% FE/t, -20% fuel), while dry operation and full-buffer auto-throttling
 * remain 100% safe without meltdowns or explosions.
 *
 * Thermodynamically closed: maximum combined efficiency multiplier with Nitrogen Cryo Overdrive is
 * (100+25+30)/(100-20) = 1.9375x (< 2.0x), strictly preventing Crystal, Water-Electrolysis,
 * Uranium-Transmutation/Manifestation, and Primal-Collapse loops.
 */
public final class EnergyReactorRecipes {
    public static final long BUFFER_PER_FUEL_CORE = 512_000_000L;
    public static final long TRANSFER_PER_INDUCTION_COIL = 1_048_576L;
    public static final int MAX_FLUX = 1_000;
    public static final int FLUX_RAMP_PER_TICK = 5;
    public static final int FLUX_DECAY_PER_TICK = 4;
    public static final int COIL_BONUS_PERCENT = 5;
    public static final int MAX_COIL_BONUS_PERCENT = 25;
    public static final int WATER_OUTPUT_BONUS_PERCENT = 20;
    public static final int WATER_FUEL_DISCOUNT_PERCENT = 15;
    public static final int NITROGEN_OUTPUT_BONUS_PERCENT = 30;
    public static final int NITROGEN_FUEL_DISCOUNT_PERCENT = 20;
    /** Maximum coolant tier bonus (Nitrogen Cryo Overdrive), used for thermodynamic upper-bound checks. */
    public static final int COOLANT_OUTPUT_BONUS_PERCENT = NITROGEN_OUTPUT_BONUS_PERCENT;
    public static final int COOLANT_FUEL_DISCOUNT_PERCENT = NITROGEN_FUEL_DISCOUNT_PERCENT;
    public static final long COOLANT_PER_CORE_PER_TICK = 20L;

    public static final long CRYSTAL_FE_PER_CORE = 8_192L;
    public static final long PRIMAL_FE_PER_CORE = 196_608L;
    public static final long PRIMAL_UR_PER_CORE_PER_TICK = 320L;

    public static final SubstanceKey WATER_COOLANT = SubstanceKey.of("rematter:water", Phase.LIQUID);
    public static final SubstanceKey NITROGEN_COOLANT = SubstanceKey.of("rematter:nitrogen", Phase.GAS);

    public enum CoolantTier {
        NONE(0, 0),
        WATER(WATER_OUTPUT_BONUS_PERCENT, WATER_FUEL_DISCOUNT_PERCENT),
        NITROGEN(NITROGEN_OUTPUT_BONUS_PERCENT, NITROGEN_FUEL_DISCOUNT_PERCENT);

        private final int outputBonusPercent;
        private final int fuelDiscountPercent;

        CoolantTier(int outputBonusPercent, int fuelDiscountPercent) {
            this.outputBonusPercent = outputBonusPercent;
            this.fuelDiscountPercent = fuelDiscountPercent;
        }

        public int outputBonusPercent() { return outputBonusPercent; }
        public int fuelDiscountPercent() { return fuelDiscountPercent; }
        public boolean cooled() { return this != NONE; }

        public static CoolantTier fromKey(SubstanceKey key) {
            if (NITROGEN_COOLANT.equals(key)) return NITROGEN;
            if (WATER_COOLANT.equals(key)) return WATER;
            return NONE;
        }
    }

    public enum FuelMode {
        CRYSTAL("crystal"),
        MATTER("matter"),
        PRIMAL("primal");

        private final String id;

        FuelMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static FuelMode load(String raw) {
            if (raw == null || raw.isEmpty()) return CRYSTAL;
            String normalized = raw.toLowerCase(Locale.ROOT);
            for (var mode : values()) {
                if (mode.id.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                    return mode;
                }
            }
            return CRYSTAL;
        }
    }

    public enum MatterFuelFilter {
        AUTO("auto"),
        URANIUM("uranium"),
        VOID_CONDENSATE("void_condensate"),
        LITHIUM_FUSION("lithium_fusion");

        private final String id;
        MatterFuelFilter(String id) { this.id = id; }
        public String id() { return id; }
        public MatterFuelFilter next() { var v = values(); return v[(ordinal() + 1) % v.length]; }
        public boolean accepts(String fuelId) { return this == AUTO || this.id.equals(fuelId); }
        public static MatterFuelFilter load(String raw) {
            if (raw == null || raw.isEmpty()) return AUTO;
            for (var v : values()) if (v.id.equalsIgnoreCase(raw) || v.name().equalsIgnoreCase(raw)) return v;
            return AUTO;
        }
    }

    public enum CoolantMode {
        AUTO("auto"),
        OFF("off"),
        WATER("water"),
        NITROGEN("nitrogen");

        private final String id;
        CoolantMode(String id) { this.id = id; }
        public String id() { return id; }
        public CoolantMode next() { var v = values(); return v[(ordinal() + 1) % v.length]; }
        public boolean allowsWater() { return this == AUTO || this == WATER; }
        public boolean allowsNitrogen() { return this == AUTO || this == NITROGEN; }
        public static CoolantMode load(String raw) {
            if (raw == null || raw.isEmpty()) return AUTO;
            for (var v : values()) if (v.id.equalsIgnoreCase(raw) || v.name().equalsIgnoreCase(raw)) return v;
            return AUTO;
        }
    }

    public record MatterFuel(String id, long fePerCorePerTick, Map<SubstanceKey, Long> inputsPerCorePerTick) {
        public MatterFuel {
            inputsPerCorePerTick = Map.copyOf(inputsPerCorePerTick);
        }
    }

    public static final List<MatterFuel> MATTER_FUELS = List.of(
            new MatterFuel("uranium", 98_304L, Map.of(
                    SubstanceKey.of("rematter:uranium", Phase.SOLID), 40L)),
            new MatterFuel("void_condensate", 49_152L, Map.of(
                    SubstanceKey.of("rematter:void_condensate", Phase.GAS), 50L)),
            new MatterFuel("lithium_fusion", 24_576L, Map.of(
                    SubstanceKey.of("rematter:lithium", Phase.SOLID), 50L,
                    SubstanceKey.of("rematter:hydrogen", Phase.GAS), 50L))
    );

    private EnergyReactorRecipes() {}

    /** Smooth thermal flux multiplier from 25% at cold start (0) up to 100% at full flux (1000). */
    public static int fluxFactorPermille(int flux) {
        int clamped = Math.clamp(flux, 0, MAX_FLUX);
        return 250 + (750 * clamped) / MAX_FLUX;
    }

    /** Coil efficiency bonus percentage (+5% per Reactor Induction Coil, capped at +40%). */
    public static int coilBonusPercent(int inductionCoils) {
        return Math.clamp((long) Math.max(0, inductionCoils) * COIL_BONUS_PERCENT, 0, MAX_COIL_BONUS_PERCENT);
    }

    /** Effective FE/t generated across all Fuel Cores given base output per core, flux, coils, and optional coolant tier. */
    public static long effectiveOutputPerTick(long baseFePerCore, int fuelCores, int inductionCoils, int flux, CoolantTier coolant) {
        if (baseFePerCore <= 0L || fuelCores <= 0) return 0L;
        long raw = Math.multiplyExact(baseFePerCore, fuelCores);
        long withFlux = Math.max(1L, (raw * fluxFactorPermille(flux)) / 1_000L);
        int bonusPct = 100 + coilBonusPercent(inductionCoils) + (coolant == null ? 0 : coolant.outputBonusPercent());
        return (withFlux * bonusPct) / 100L;
    }

    /** Effective FE/t using maximum cooled overdrive (Nitrogen) when {@code cooled} is true. */
    public static long effectiveOutputPerTick(long baseFePerCore, int fuelCores, int inductionCoils, int flux, boolean cooled) {
        return effectiveOutputPerTick(baseFePerCore, fuelCores, inductionCoils, flux, cooled ? CoolantTier.NITROGEN : CoolantTier.NONE);
    }

    /** Effective fuel quantity consumed per tick for the given coolant tier. */
    public static long effectiveFuelCost(long baseUnitsPerCore, int fuelCores, CoolantTier coolant) {
        if (baseUnitsPerCore <= 0L || fuelCores <= 0) return 0L;
        long total = Math.multiplyExact(baseUnitsPerCore, fuelCores);
        int discount = coolant == null ? 0 : coolant.fuelDiscountPercent();
        if (discount <= 0) return total;
        return Math.max(1L, (total * (100 - discount) + 99) / 100L);
    }

    /** Effective fuel quantity using maximum cooled overdrive (Nitrogen) when {@code cooled} is true. */
    public static long effectiveFuelCost(long baseUnitsPerCore, int fuelCores, boolean cooled) {
        return effectiveFuelCost(baseUnitsPerCore, fuelCores, cooled ? CoolantTier.NITROGEN : CoolantTier.NONE);
    }

    /** Coolant units consumed per tick across all Fuel Cores. */
    public static long coolantCost(int fuelCores) {
        return fuelCores <= 0 ? 0L : Math.multiplyExact(COOLANT_PER_CORE_PER_TICK, fuelCores);
    }

    /** 64-bit internal buffer capacity of an Energy Reactor with the given number of Fuel Cores. */
    public static long bufferCapacity(int fuelCores) {
        return fuelCores <= 0 ? 0L : Math.multiplyExact(BUFFER_PER_FUEL_CORE, fuelCores);
    }

    /** Maximum FE/t extraction and push rate across formed power ports for the given number of Induction Coils. */
    public static long maxOutputRate(int inductionCoils) {
        return inductionCoils <= 0 ? 0L : Math.multiplyExact(TRANSFER_PER_INDUCTION_COIL, inductionCoils);
    }
}
