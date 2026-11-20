package dev.latrox.rematter.research;

import dev.latrox.rematter.chemistry.SubstanceId;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Exact, curated family membership. This grants knowledge, never synthesis permission. */
public record MaterialResearch(String id, String name, Set<String> items, Set<SubstanceKey> substances,
                               boolean derive, boolean startingKnowledge, @Nullable DiscoveryTier discoveryTier,
                               String requiredDimension, long derivePrimal) {
    public MaterialResearch {
        SubstanceId.parse(id); items = Set.copyOf(items); substances = Set.copyOf(substances);
        if (name.isBlank() || items.isEmpty() || substances.isEmpty() || derivePrimal < 4_000 || derivePrimal > 16_000)
            throw new IllegalArgumentException("Incomplete material research " + id);
        items.forEach(SubstanceId::parse);
        if (!requiredDimension.isEmpty()) SubstanceId.parse(requiredDimension);
        if (substances.stream().anyMatch(key -> key.id().toString().equals("rematter:primal_matter")))
            throw new IllegalArgumentException("Primal Matter cannot be learned as an ordinary material");
    }
    /** Multiplication saturates before clamp; even a malicious large profile cannot wrap to a cheap price. */
    public static long derivePrice(long matterSum) {
        if (matterSum <= 0) throw new IllegalArgumentException("Empty derivation");
        return matterSum >= 4_000 ? 16_000 : Math.max(4_000, Math.multiplyExact(4, matterSum));
    }
}
