package dev.latrox.rematter.compat;

import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.client.Quantities;
import dev.latrox.rematter.content.EnergyReactorRecipes;
import dev.latrox.rematter.content.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Shared EMI/JEI display model for Energy Reactor fuel routes and optional Coolant Overdrive. */
public record EnergyReactorDisplay(Identifier recipeId, Component title, List<ChemistryViewer.Matter> matterInputs,
                                   ItemStack itemInput, long fePerCorePerTick, long cooledFePerCorePerTick,
                                   boolean coolantEntry, Component consumptionSummary, Component outputSummary, Component note) {
    public EnergyReactorDisplay {
        matterInputs = List.copyOf(matterInputs);
        itemInput = itemInput == null ? ItemStack.EMPTY : itemInput.copy();
    }

    public static List<EnergyReactorDisplay> entries(ChemistryRegistry registry) {
        var list = new ArrayList<EnergyReactorDisplay>();
        // 1. Matter fuels
        for (var fuel : EnergyReactorRecipes.MATTER_FUELS) {
            var matters = fuel.inputsPerCorePerTick().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new ChemistryViewer.Matter(e.getKey(), e.getValue()))
                    .toList();
            long cooledFe = EnergyReactorRecipes.effectiveOutputPerTick(fuel.fePerCorePerTick(), 1, 0, EnergyReactorRecipes.MAX_FLUX, true);
            list.add(new EnergyReactorDisplay(
                    Identifier.fromNamespaceAndPath("rematter", "/energy_reactor/matter/" + fuel.id()),
                    Component.translatable("gui.rematter.energy_reactor.fuel." + fuel.id()),
                    matters, ItemStack.EMPTY, fuel.fePerCorePerTick(), cooledFe, false,
                    Component.translatable("gui.rematter.energy_reactor.per_core_tick"),
                    Component.translatable("gui.rematter.energy_reactor.output_rate", Quantities.compact(fuel.fePerCorePerTick()), Quantities.compact(cooledFe)),
                    Component.translatable("gui.rematter.energy_reactor.note_matter")));
        }
        // 2. Primal Matter fuel
        var primalKey = new SubstanceKey(ReservedSubstances.PRIMAL_MATTER, Phase.SOLID);
        if (registry.isKnown(primalKey)) {
            long cooledFe = EnergyReactorRecipes.effectiveOutputPerTick(EnergyReactorRecipes.PRIMAL_FE_PER_CORE, 1, 0, EnergyReactorRecipes.MAX_FLUX, true);
            list.add(new EnergyReactorDisplay(
                    Identifier.fromNamespaceAndPath("rematter", "/energy_reactor/primal"),
                    Component.translatable("gui.rematter.energy_reactor.fuel.primal"),
                    List.of(new ChemistryViewer.Matter(primalKey, EnergyReactorRecipes.PRIMAL_UR_PER_CORE_PER_TICK)),
                    ItemStack.EMPTY, EnergyReactorRecipes.PRIMAL_FE_PER_CORE, cooledFe, false,
                    Component.translatable("gui.rematter.energy_reactor.per_core_tick"),
                    Component.translatable("gui.rematter.energy_reactor.output_rate", Quantities.compact(EnergyReactorRecipes.PRIMAL_FE_PER_CORE), Quantities.compact(cooledFe)),
                    Component.translatable("gui.rematter.energy_reactor.note_primal")));
        }
        // 3. Matrix Crystal fuel mode
        long cooledCrystal = EnergyReactorRecipes.effectiveOutputPerTick(EnergyReactorRecipes.CRYSTAL_FE_PER_CORE, 1, 0, EnergyReactorRecipes.MAX_FLUX, true);
        list.add(new EnergyReactorDisplay(
                Identifier.fromNamespaceAndPath("rematter", "/energy_reactor/crystal"),
                Component.translatable("gui.rematter.energy_reactor.fuel.crystal"),
                List.of(), new ItemStack(ModContent.MATRIX_CRYSTAL.get()),
                EnergyReactorRecipes.CRYSTAL_FE_PER_CORE, cooledCrystal, false,
                Component.translatable("gui.rematter.energy_reactor.crystal_input"),
                Component.translatable("gui.rematter.energy_reactor.output_rate", Quantities.compact(EnergyReactorRecipes.CRYSTAL_FE_PER_CORE), Quantities.compact(cooledCrystal)),
                Component.translatable("gui.rematter.energy_reactor.note_crystal")));
        // 4. Optional Coolant Overdrive (Water +20%/-15% vs Nitrogen Cryo Overdrive +30%/-20%)
        for (var coolant : List.of(EnergyReactorRecipes.WATER_COOLANT, EnergyReactorRecipes.NITROGEN_COOLANT)) {
            if (!registry.isKnown(coolant)) continue;
            var tier = EnergyReactorRecipes.CoolantTier.fromKey(coolant);
            list.add(new EnergyReactorDisplay(
                    Identifier.fromNamespaceAndPath("rematter", "/energy_reactor/coolant/" + coolant.id().path()),
                    Component.translatable(tier == EnergyReactorRecipes.CoolantTier.NITROGEN
                            ? "gui.rematter.energy_reactor.coolant_nitrogen_title"
                            : "gui.rematter.energy_reactor.coolant_water_title"),
                    List.of(new ChemistryViewer.Matter(coolant, EnergyReactorRecipes.COOLANT_PER_CORE_PER_TICK)),
                    ItemStack.EMPTY, 0L, 0L, true,
                    Component.translatable("gui.rematter.energy_reactor.per_core_tick"),
                    Component.translatable("gui.rematter.energy_reactor.coolant_bonus",
                            tier.outputBonusPercent(), tier.fuelDiscountPercent()),
                    Component.translatable("gui.rematter.energy_reactor.note_coolant")));
        }
        return List.copyOf(list);
    }
}
