package dev.latrox.rematter.chemistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Collapses explicit cross-mod duplicate synthesis outputs sharing a {@code synthesis_group} to one
 * canonical item according to configured mod namespace priority, recording non-canonical IDs as
 * migration aliases for saved machine selections and configuration cards.
 */
public final class SynthesisUnifier {
    public record Result(
            Map<String, SynthesisCategory> allowed,
            Map<String, SynthesisRequirement> requirements,
            Map<String, String> aliases) {
        public Result {
            allowed = Collections.unmodifiableMap(new TreeMap<>(allowed));
            requirements = Collections.unmodifiableMap(new TreeMap<>(requirements));
            aliases = Collections.unmodifiableMap(new TreeMap<>(aliases));
        }
    }

    private SynthesisUnifier() {}

    public static Result apply(
            Map<String, SynthesisCategory> allowed,
            Map<String, SynthesisRequirement> requirements,
            Map<String, String> groups,
            boolean enabled,
            List<String> modPriority) {
        Objects.requireNonNull(allowed, "allowed");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(modPriority, "modPriority");

        if (!enabled || groups.isEmpty()) {
            return new Result(allowed, requirements, Map.of());
        }

        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < modPriority.size(); i++) {
            rank.putIfAbsent(modPriority.get(i), i);
        }

        Map<String, SynthesisCategory> keptAllowed = new TreeMap<>();
        Map<String, SynthesisRequirement> keptRequirements = new TreeMap<>();
        Map<String, String> aliases = new TreeMap<>();
        Map<String, List<String>> grouped = new TreeMap<>();

        for (var entry : allowed.entrySet()) {
            String itemId = entry.getKey();
            SynthesisCategory category = entry.getValue();
            String group = groups.getOrDefault(itemId, "");
            boolean itemOutput = category == SynthesisCategory.RAW_MATERIAL || category == SynthesisCategory.NATURAL_MATERIAL;
            if (!itemOutput || group.isEmpty()) {
                keptAllowed.put(itemId, category);
                keptRequirements.put(itemId, requirements.getOrDefault(itemId, SynthesisRequirement.DEFAULT));
            } else {
                grouped.computeIfAbsent(group, ignored -> new ArrayList<>()).add(itemId);
            }
        }

        Comparator<String> order = Comparator
                .<String>comparingInt(id -> rank.getOrDefault(SubstanceId.parse(id).namespace(), Integer.MAX_VALUE))
                .thenComparing(Comparator.naturalOrder());

        for (var entry : grouped.entrySet()) {
            List<String> members = entry.getValue();
            members.sort(order);
            String canonical = members.getFirst();
            keptAllowed.put(canonical, allowed.get(canonical));
            keptRequirements.put(canonical, requirements.getOrDefault(canonical, SynthesisRequirement.DEFAULT));
            for (int i = 1; i < members.size(); i++) {
                aliases.put(members.get(i), canonical);
            }
        }

        return new Result(keptAllowed, keptRequirements, aliases);
    }
}
