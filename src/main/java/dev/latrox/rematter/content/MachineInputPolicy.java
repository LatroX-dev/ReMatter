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
        if (candidate.isEmpty()) return false;
        if (PreparationTier.isPreparation(kind)) {
            if (!candidate.getComponentsPatch().isEmpty()) return false;
            return recipes.stream()
                    .filter(entry -> PreparationTier.permits(kind, entry.minimumTier(), entry.process()))
                    .anyMatch(entry -> entry.recipe().acceptsInsertion(inputs, slot, candidate));
        }
        if (slot != 0) return false;
        if (kind.decomposition()) return ToolSalvage.plan(chemistry, candidate).isPresent();
        return kind == MachineKind.ANALYZER && candidate.getComponentsPatch().isEmpty()
                && MachineOperations.profile(chemistry, candidate) != null;
    }

    public static boolean acceptsUpgrade(MachineKind kind, ItemStack stack) {
        return MachineEnergyPolicy.supportsUpgrades(kind) && stack.is(ModContent.SPEED_UPGRADE.get());
    }

    public static boolean acceptsEfficiency(MachineKind kind, ItemStack stack) {
        return MachineEnergyPolicy.supportsUpgrades(kind) && stack.is(ModContent.EFFICIENCY_UPGRADE.get());
    }

    public static boolean acceptsAnchor(MachineKind kind) {
        return MachineEnergyPolicy.supportsUpgrades(kind)
                || kind == MachineKind.DRIVE
                || kind == MachineKind.ENERGY_REACTOR
                || kind == MachineKind.ENERGY_MATRIX
                || kind == MachineKind.PRIMAL_CONVERTER
                || kind == MachineKind.NETWORK_INTERFACE;
    }

    public static boolean isUpgradeItem(ItemStack stack) {
        return stack.is(ModContent.SPEED_UPGRADE.get())
                || stack.is(ModContent.EFFICIENCY_UPGRADE.get())
                || stack.is(ModContent.PARALLEL_UPGRADE.get())
                || stack.is(ModContent.PRESSURE_MODULE.get())
                || stack.is(ModContent.EXTRACTION_FOCUS_UPGRADE.get())
                || stack.is(ModContent.MASS_COMPRESSION_UPGRADE.get())
                || stack.is(ModContent.ANCHOR_UPGRADE.get());
    }

    /** Target machine slot for an upgrade item, or -1 if this machine kind does not accept it. */
    public static int upgradeSlotFor(MachineKind kind, ItemStack stack) {
        if (acceptsUpgrade(kind, stack)) return MachineCellAccess.SPEED_SLOT;
        if (acceptsEfficiency(kind, stack)) return MachineCellAccess.EFFICIENCY_SLOT;
        if (IndustrialStructure.isIndustrial(kind) && kind.processes() && stack.is(ModContent.PARALLEL_UPGRADE.get())) return MachineCellAccess.PARALLEL_SLOT;
        if (!IndustrialStructure.isIndustrial(kind) && kind.synthesis() && stack.is(ModContent.PRESSURE_MODULE.get())) return MachineCellAccess.PRESSURE_SLOT;
        if (kind == MachineKind.DEEP_EXTRACTOR && stack.is(ModContent.EXTRACTION_FOCUS_UPGRADE.get())) return MachineCellAccess.PRESSURE_SLOT;
        if (kind == MachineKind.PRIMAL_REACTOR && stack.is(ModContent.MASS_COMPRESSION_UPGRADE.get())) return MachineCellAccess.MASS_COMPRESSION_SLOT;
        if (acceptsAnchor(kind) && stack.is(ModContent.ANCHOR_UPGRADE.get())) return MachineCellAccess.ANCHOR_SLOT;
        return -1;
    }

    /** Maximum installed count for an upgrade slot on the given machine kind. */
    public static int upgradeSlotLimit(MachineKind kind, int slot) {
        if (slot == MachineCellAccess.SPEED_SLOT || slot == MachineCellAccess.EFFICIENCY_SLOT) return MachineEnergyPolicy.MAX_UPGRADES;
        if (slot == MachineCellAccess.PARALLEL_SLOT) return IndustrialStructure.isIndustrial(kind) && kind.processes() ? 3 : 1;
        if (slot == MachineCellAccess.MASS_COMPRESSION_SLOT) return kind == MachineKind.PRIMAL_REACTOR ? 1 : 0;
        if (slot == MachineCellAccess.ANCHOR_SLOT) return acceptsAnchor(kind) ? 1 : 0;
        return 0;
    }
}
