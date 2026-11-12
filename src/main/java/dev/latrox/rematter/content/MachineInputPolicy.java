package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.content.preparation.PreparationInput;
import dev.latrox.rematter.content.preparation.PreparationRecipes;
import dev.latrox.rematter.content.preparation.PreparationTier;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Identical recipe filtering for server inventory/automation and client click prediction. */
public final class MachineInputPolicy {
    private MachineInputPolicy() {}

    public static boolean accepts(MachineKind kind, int slot, ItemStack candidate, PreparationInput inputs,
                                  ChemistryRegistry chemistry, List<PreparationRecipes.Entry> recipes) {
        if (candidate.isEmpty() || !candidate.getComponentsPatch().isEmpty()) return false;
        if (PreparationTier.isPreparation(kind)) return recipes.stream()
                .filter(entry -> PreparationTier.permits(kind, entry.minimumTier(), entry.process()))
                .anyMatch(entry -> entry.recipe().acceptsInsertion(inputs, slot, candidate));
        return slot == 0 && (kind == MachineKind.DECOMPOSER || kind == MachineKind.ANALYZER)
                && MachineOperations.profile(chemistry, candidate) != null;
    }

    public static boolean acceptsUpgrade(MachineKind kind, ItemStack stack) {
        if(IndustrialStructure.isIndustrial(kind))return stack.is(ModContent.SPEED_UPGRADE.get());
        return kind.processes() && kind!=MachineKind.PRIMAL_CONVERTER && !PreparationTier.manual(kind) && (stack.is(ModContent.SPEED_UPGRADE.get())
                || stack.is(ModContent.EFFICIENCY_UPGRADE.get())
                || IndustrialStructure.isIndustrial(kind) && stack.is(ModContent.PARALLEL_UPGRADE.get())
                || kind.synthesis() && stack.is(ModContent.PRESSURE_MODULE.get()));
    }
}
