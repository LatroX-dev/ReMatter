package dev.latrox.rematter.primal;

import dev.latrox.rematter.chemistry.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** An explicit target allowlist; prices never come from a client, rarity tag or recipe inference. */
public final class PrimalPricing {
    public static final long MINIMUM_OUTPUT = 250, REFERENCE_OUTPUT = 1000;
    public enum Grade {
        COMMON(1, 64000, 400), SCARCE(2, 96000, 500), INVENTED(4, 128000, 600);
        public final long primalPerUnit, energy; public final int ticks;
        Grade(long primalPerUnit, long energy, int ticks) { this.primalPerUnit = primalPerUnit; this.energy = energy; this.ticks = ticks; }
    }
    private static final Set<String> COMMON = Set.of("iron", "copper", "carbon", "silicon", "tin", "zinc", "aluminum", "lead", "sulfur");
    private static final Set<String> SCARCE = Set.of("gold", "silver", "platinum", "iridium", "osmium", "nickel", "lithium", "titanium", "uranium");
    private static final Set<String> INVENTED = Set.of("arcanium", "cinderium", "enderion", "netherium");
    private PrimalPricing() {}
    public record Quote(SubstanceKey target, long output, long primal, long energy, int ticks) {
        public Quote { if (output <= 0 || primal < output || energy <= 0 || ticks <= 0) throw new IllegalArgumentException("Invalid Primal conversion"); }
        public Map<SubstanceKey, Long> outputs() { return Map.of(target, output); }
    }
    public static Optional<Grade> grade(SubstanceId id) {
        if (!id.namespace().equals("rematter")) return Optional.empty();
        return COMMON.contains(id.path()) ? Optional.of(Grade.COMMON) : SCARCE.contains(id.path()) ? Optional.of(Grade.SCARCE)
                : INVENTED.contains(id.path()) ? Optional.of(Grade.INVENTED) : Optional.empty();
    }
    public static boolean eligible(ChemistryRegistry registry, SubstanceKey target, Predicate<SubstanceId> known) {
        var definition = registry.definition(target.id()).orElse(null);
        return target.phase() == Phase.SOLID && grade(target.id()).isPresent() && definition != null
                && definition.kind() == SubstanceKind.ELEMENT && definition.supports(Phase.SOLID)
                && known.test(target.id()) && useful(registry, target);
    }
    public static List<SubstanceKey> targets(ChemistryRegistry registry, Predicate<SubstanceId> known) {
        return registry.substances().stream().map(s -> new SubstanceKey(s.id(), Phase.SOLID))
                .filter(key -> eligible(registry, key, known)).sorted().toList();
    }
    private static boolean useful(ChemistryRegistry registry, SubstanceKey target) {
        return registry.profiles().stream().anyMatch(profile -> registry.synthesisPolicy().allowsItem(profile.itemId()) && profile.matter().containsKey(target))
                || registry.fluidProfiles().stream().anyMatch(profile -> registry.synthesisPolicy().allowsFluid(profile.fluidId()) && profile.matter().containsKey(target))
                || registry.processes().stream().anyMatch(process -> process.inputs().containsKey(target));
    }
    public static Quote quote(ChemistryRegistry registry, SubstanceKey target, long output, Predicate<SubstanceId> known) {
        if (!eligible(registry, target, known) || output < MINIMUM_OUTPUT || output % MINIMUM_OUTPUT != 0) throw new IllegalArgumentException("Unavailable Primal target or amount");
        var grade = grade(target.id()).orElseThrow();
        return new Quote(target, output, Math.multiplyExact(grade.primalPerUnit, output), ceilScale(grade.energy, output, REFERENCE_OUTPUT),
                Math.toIntExact(ceilScale(grade.ticks, output, REFERENCE_OUTPUT)));
    }
    /** Quotient/remainder multiplication avoids overflowing base * amount. */
    public static long ceilScale(long base, long amount, long reference) {
        if (base < 0 || amount < 0 || reference <= 0) throw new IllegalArgumentException("Invalid scaled price");
        var divided = java.math.BigInteger.valueOf(base).multiply(java.math.BigInteger.valueOf(amount))
                .divideAndRemainder(java.math.BigInteger.valueOf(reference));
        return divided[0].add(divided[1].signum() == 0 ? java.math.BigInteger.ZERO : java.math.BigInteger.ONE).longValueExact();
    }
}
