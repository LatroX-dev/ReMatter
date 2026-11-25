package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.primal.PrimalContent;
import dev.latrox.rematter.primal.PrimalPricing;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Shared Primal Reactor modes combining:
 * (A) Real 3-phase Matter Collapse tiers (never pure FE -> Ur from nothing),
 * (B) Automated Bulk Primal Manifestation (Ur + FE -> known allowlisted elements into the Matter Network),
 * (C) Singularity Seed cap & persistent 0..1000 Stability scaling speed (up to 2.5x) and condensation yield (+50%).
 */
public final class PrimalReactorRecipes {
    public static final String COLLAPSE_RAW = "rematter:primal_collapse_raw";
    public static final String COLLAPSE_REFINED = "rematter:primal_collapse_refined";
    public static final String COLLAPSE_TRANSMUTED = "rematter:primal_collapse_transmuted";
    public static final String MANIFEST_PREFIX = "primal_manifest:";
    public static final long MANIFEST_BATCH = 4_000L;
    public static final int BASE_TICKS = 400;
    public static final int MAX_STABILITY = 1_000;
    public static final int CRYSTAL_STABILITY_CAP = 600;
    public static final int CORE_STABILITY_CAP = 1_000;
    public static final int MASS_COMPRESSION_DISCOUNT_PERCENT = 30;

    private static final SubstanceKey NITROGEN = SubstanceKey.of("rematter:nitrogen", Phase.GAS);
    private static final SubstanceKey PLATINUM = SubstanceKey.of("rematter:platinum", Phase.SOLID);

    public record CondensationTier(String id, String translationKey, Map<SubstanceKey, Long> inputs,
                                   long primalOutput, long energy, int ticks) {
        public Map<SubstanceKey, Long> effectiveInputs(boolean compressed) {
            if (!compressed) return inputs;
            var scaled = new LinkedHashMap<SubstanceKey, Long>(inputs.size());
            for (var entry : inputs.entrySet()) {
                scaled.put(entry.getKey(), Math.max(1L, entry.getValue() * (100L - MASS_COMPRESSION_DISCOUNT_PERCENT) / 100L));
            }
            return Map.copyOf(scaled);
        }

        public long effectiveEnergy(boolean compressed) {
            return compressed ? Math.max(1L, energy * (100L - MASS_COMPRESSION_DISCOUNT_PERCENT) / 100L) : energy;
        }
    }

    public record ManifestationPlan(String id, SubstanceKey target, long primalCost, long outputAmount, long energy, int ticks) {}

    public static final List<CondensationTier> CONDENSATION_TIERS = List.of(
            new CondensationTier(
                    COLLAPSE_RAW,
                    "process.rematter.primal_collapse_raw",
                    Map.of(BuiltinChemistry.SILICA, 4_000L, BuiltinChemistry.WATER, 1_000L, BuiltinChemistry.HYDROGEN, 1_000L),
                    1_000L, 5_600_000L, BASE_TICKS),
            new CondensationTier(
                    COLLAPSE_REFINED,
                    "process.rematter.primal_collapse_refined",
                    Map.of(BuiltinChemistry.IRON, 3_000L, BuiltinChemistry.COPPER, 3_000L, BuiltinChemistry.WATER, 1_000L, NITROGEN, 1_000L),
                    2_000L, 8_400_000L, BASE_TICKS),
            new CondensationTier(
                    COLLAPSE_TRANSMUTED,
                    "process.rematter.primal_collapse_transmuted",
                    Map.of(BuiltinChemistry.GOLD, 2_500L, PLATINUM, 2_500L, BuiltinChemistry.HYDROGEN, 1_500L, NITROGEN, 1_500L),
                    4_000L, 14_000_000L, BASE_TICKS));

    private PrimalReactorRecipes() {}

    public static List<CondensationTier> availableTiers(ChemistryRegistry registry) {
        var out = new ArrayList<CondensationTier>(CONDENSATION_TIERS.size());
        for (var tier : CONDENSATION_TIERS) {
            if (tier.inputs().keySet().stream().allMatch(registry::isKnown)) out.add(tier);
        }
        return List.copyOf(out);
    }

    public static Optional<CondensationTier> condensation(String id, ChemistryRegistry registry) {
        if (id == null || id.isEmpty()) return Optional.empty();
        for (var tier : availableTiers(registry)) {
            if (tier.id().equals(id)) return Optional.of(tier);
        }
        return Optional.empty();
    }

    public static Optional<ManifestationPlan> manifestation(String id, ChemistryRegistry registry) {
        if (id == null || !id.startsWith(MANIFEST_PREFIX)) return Optional.empty();
        String raw = id.substring(MANIFEST_PREFIX.length());
        try {
            var key = new SubstanceKey(SubstanceId.parse(raw), Phase.SOLID);
            var quote = PrimalPricing.quote(registry, key, MANIFEST_BATCH, ignored -> true);
            return Optional.of(new ManifestationPlan(id, key, quote.primal(), quote.output(),
                    Math.multiplyExact(quote.energy(), 4L), BASE_TICKS));
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            return Optional.empty();
        }
    }

    public static String manifestChoiceId(SubstanceId id) {
        return MANIFEST_PREFIX + id;
    }

    public static boolean isSeed(ItemStack stack) {
        return seedMaxStability(stack) > 0;
    }

    /** Returns maximum attainable stability (0..1000) for the installed Singularity Seed item. */
    public static int seedMaxStability(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(PrimalContent.PRIMAL_MATRIX_CORE.get())) return CORE_STABILITY_CAP;
        if (stack.is(PrimalContent.PRIMAL_CRYSTAL.get())) return CRYSTAL_STABILITY_CAP;
        return 0;
    }

    /** Speed percentage from 100% (0 stability) to 250% (1000 stability). */
    public static int speedMultiplierPercent(int stability) {
        return 100 + (Math.clamp(stability, 0, MAX_STABILITY) * 150) / MAX_STABILITY;
    }

    /** Yield bonus percentage from 0% (0 stability) to +50% (1000 stability). */
    public static int yieldBonusPercent(int stability) {
        return (Math.clamp(stability, 0, MAX_STABILITY) * 50) / MAX_STABILITY;
    }

    public static int effectiveSpeed(int baseSpeed, int stability) {
        return Math.max(1, (baseSpeed * speedMultiplierPercent(stability) + 50) / 100);
    }

    public static long condensationOutput(long baseOutput, int stability) {
        int clamped = Math.clamp(stability, 0, MAX_STABILITY);
        return Math.max(1L, baseOutput * (1_000L + clamped / 2L) / 1_000L);
    }

    public static List<MachineChoices.Choice> choices(ChemistryRegistry registry) {
        var list = new ArrayList<MachineChoices.Choice>();
        for (var tier : availableTiers(registry)) {
            list.add(new MachineChoices.Choice(tier.id(), Component.translatable(tier.translationKey()).getString()));
        }
        for (var key : PrimalPricing.targets(registry, ignored -> true)) {
            var def = registry.definition(key.id()).orElse(null);
            Component name = def == null
                    ? Component.literal(key.id().toString())
                    : Component.translatableWithFallback(def.translationKey(), ChemistryText.readable(def.id().path()));
            list.add(new MachineChoices.Choice(
                    manifestChoiceId(key.id()),
                    Component.translatableWithFallback("process.rematter.primal_manifest", "Manifest %s", name).getString()));
        }
        return List.copyOf(list);
    }
}
