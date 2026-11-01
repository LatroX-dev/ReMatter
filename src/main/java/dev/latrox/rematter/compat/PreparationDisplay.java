package dev.latrox.rematter.compat;

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
                                 Optional<Ingredient> secondary, Item resultItem, int resultCount,
                                 int baseEnergy, int workTicks, int minimumTier) {
    public static final List<MachineKind> MACHINES = List.of(MachineKind.HAND_PREPARATOR,
            MachineKind.PREPARATOR, MachineKind.INDUSTRIAL_PREPARATOR);

    public static List<PreparationDisplay> from(List<PreparationRecipes.Entry> entries) {
        var result = new ArrayList<PreparationDisplay>();
        for (var entry : entries) for (var machine : MACHINES) {
            if (!PreparationTier.permits(machine, entry.minimumTier())) continue;
            var output = entry.result();
            result.add(new PreparationDisplay(entry.id(), machine, entry.primary(), entry.secondary(),
                    output.getItem(), output.getCount(), entry.energy(), entry.ticks(), entry.minimumTier()));
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
        return Component.translatable(manual() ? "gui.rematter.preparation_manual"
                : PreparationTier.industrial(machine) ? "gui.rematter.preparation_industrial"
                : "gui.rematter.preparation_base");
    }
    public Component inputsNote() {
        return Component.translatable(secondary.isEmpty() ? "gui.rematter.preparation_empty_secondary"
                : "gui.rematter.preparation_two_inputs");
    }
    public Identifier recipeId() {
        return Identifier.fromNamespaceAndPath("rematter", "preparation/" + machine.id() + "/"
                + sourceId.getNamespace() + "/" + sourceId.getPath() + "/" + Integer.toUnsignedString(hashCode(), 16));
    }
}
