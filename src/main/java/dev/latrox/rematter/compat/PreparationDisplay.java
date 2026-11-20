package dev.latrox.rematter.compat;

import dev.latrox.rematter.client.Quantities;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.preparation.PreparationRecipes;
import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.content.preparation.PreparationWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** The same synchronized physical recipe, presented with each eligible machine's base costs. */
public record PreparationDisplay(Identifier sourceId, MachineKind machine, Ingredient primary,
                                 Optional<Ingredient> secondary, Optional<Ingredient> tertiary, int primaryCount, int secondaryCount, int tertiaryCount,
                                 Item resultItem, int resultCount, int baseEnergy, int workTicks, int minimumTier, String process) {
    public static final List<MachineKind> MACHINES = List.of(MachineKind.HAND_PREPARATOR,
            MachineKind.PREPARATOR, MachineKind.INDUSTRIAL_PREPARATOR, MachineKind.SINTER_FURNACE);

    public static List<PreparationDisplay> from(List<PreparationRecipes.Entry> entries) {
        var result = new ArrayList<PreparationDisplay>();
        for (var entry : entries) for (var machine : MACHINES) {
            if (!PreparationTier.permits(machine, entry.minimumTier(), entry.process())) continue;
            var output = entry.result();
            result.add(new PreparationDisplay(entry.id(), machine, entry.primary(), entry.secondary(), entry.tertiary(),
                    entry.primaryCount(), entry.secondaryCount(), entry.tertiaryCount(), output.getItem(), output.getCount(),
                    entry.energy(), entry.ticks(), entry.minimumTier(), entry.process()));
        }
        return List.copyOf(result);
    }

    public boolean manual() { return PreparationTier.manual(machine); }
    public long energy() { return Math.multiplyExact((long) baseEnergy, PreparationTier.energyMultiplier(machine)); }
    public int ticks() { return (workTicks + PreparationTier.speed(machine) - 1) / PreparationTier.speed(machine); }
    public int cranks() { return (workTicks + PreparationWork.CRANK_WORK - 1) / PreparationWork.CRANK_WORK; }
    public ItemStack output() { return new ItemStack(resultItem, resultCount); }
    public Component cost() {
        return manual() ? Component.translatable("gui.rematter.preparation_cranks", cranks())
                : Component.translatable(PreparationTier.industrial(machine) ? "gui.rematter.preparation_minimum_cost"
                : "gui.rematter.preparation_cost", energy(), ticks());
    }
    public Component note() {
        return Component.translatable(machine==MachineKind.SINTER_FURNACE ? "gui.rematter.sintering_note" : manual() ? "gui.rematter.preparation_manual"
                : PreparationTier.industrial(machine) ? "gui.rematter.preparation_industrial"
                : "gui.rematter.preparation_base");
    }
    public Component inputsNote() {
        return Component.translatable("gui.rematter.preparation_counted_inputs");
    }
    public Identifier recipeId() { return recipeId(machine, sourceId); }

    /** One display per source recipe and machine, so the source and machine alone make a stable synthetic ID. */
    static Identifier recipeId(MachineKind machine, Identifier source) {
        return Identifier.fromNamespaceAndPath("rematter", "/preparation/" + machine.id() + "/" + source.getNamespace() + "/" + source.getPath());
    }
    /** The visible cost value: cranks for manual work, otherwise FE. */
    public Component costValue() {
        return manual() ? Component.translatable("gui.rematter.viewer.cranks", cranks()) : Quantities.energy(Quantities.number(energy()));
    }
    public Component timeValue() { return Quantities.ticks(Quantities.number(ticks())); }
}
