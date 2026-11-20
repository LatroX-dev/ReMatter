package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.MaterialProfile;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Safe, loss-bounded durability-scaled decomposition for unenchanted damageable items.
 * Rejects enchantments, custom data, containers, stored energy, and yields that round down to zero.
 */
public final class ToolSalvage {
    public record Plan(MaterialProfile profile, Map<SubstanceKey, Long> matter, int damage, int maxDamage) {
        public Plan { matter = Map.copyOf(matter); }
        public boolean damaged() { return damage > 0; }
    }

    private ToolSalvage() {}

    public static boolean hasOnlyAllowedDamage(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var patch = stack.getComponentsPatch();
        if (patch.isEmpty()) return true;
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0 || stack.isEnchanted()) return false;
        if (patch.size() != 1) return false;
        var only = patch.entrySet().iterator().next();
        if (!only.getKey().equals(DataComponents.DAMAGE) || only.getValue().isEmpty()) return false;
        int damage = stack.getDamageValue();
        return damage >= 0 && damage < stack.getMaxDamage();
    }

    public static Optional<Plan> plan(ChemistryRegistry registry, ItemStack stack) {
        if (!hasOnlyAllowedDamage(stack)) return Optional.empty();
        var profile = MachineOperations.profile(registry, stack);
        if (profile == null) return Optional.empty();
        return plan(profile, stack);
    }

    public static Optional<Plan> plan(MaterialProfile profile, ItemStack stack) {
        if (!hasOnlyAllowedDamage(stack)) return Optional.empty();
        if (stack.getComponentsPatch().isEmpty()) {
            return Optional.of(new Plan(profile, profile.matter(), 0, stack.getMaxDamage()));
        }
        if (profile.itemCount() != 1) return Optional.empty();
        int max = stack.getMaxDamage();
        int damage = stack.getDamageValue();
        return scaleMatter(profile.matter(), damage, max)
                .map(scaled -> new Plan(profile, scaled, damage, max));
    }

    public static Optional<Map<SubstanceKey, Long>> scaleMatter(Map<SubstanceKey, Long> base, int damage, int maxDamage) {
        long remaining = (long) maxDamage - damage;
        if (maxDamage <= 0 || damage < 0 || remaining <= 0 || base.isEmpty()) return Optional.empty();
        var scaled = new LinkedHashMap<SubstanceKey, Long>();
        for (var entry : base.entrySet()) {
            long amount = Math.multiplyExact(entry.getValue(), remaining) / maxDamage;
            if (amount <= 0) return Optional.empty();
            scaled.put(entry.getKey(), amount);
        }
        return Optional.of(Map.copyOf(scaled));
    }
}
