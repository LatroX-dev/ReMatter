package dev.latrox.rematter.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

record ChemistryEmiRecipe(EmiRecipeCategory category, Identifier id, List<EmiIngredient> inputs,
                          List<EmiStack> outputs, long energy, int ticks, String permission) implements EmiRecipe {
    ChemistryEmiRecipe {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }
    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public Identifier getId() { return id; }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiStack> getOutputs() { return outputs; }
    @Override public int getDisplayWidth() { return 180; }
    private int rows() { return Math.max(1, Math.max(inputs.size(), outputs.size())); }
    @Override public int getDisplayHeight() { return rows() * 20 + 48; }

    @Override public void addWidgets(WidgetHolder widgets) {
        widgets.addText(Component.literal("Input"), 0, 0, 0xFF555555, false);
        widgets.addText(Component.literal("Output"), 108, 0, 0xFF555555, false);
        for (int index = 0; index < inputs.size(); index++) {
            var input = inputs.get(index);
            widgets.addSlot(input, 0, 12 + index * 20)
                    .appendTooltip(Component.literal("Required: " + input.getAmount()));
            addAmount(widgets, input, 21, 17 + index * 20);
        }
        for (int index = 0; index < outputs.size(); index++) {
            var output = outputs.get(index);
            widgets.addSlot(output, 108, 12 + index * 20).recipeContext(this)
                    .appendTooltip(Component.literal("Produced: " + output.getAmount()));
            addAmount(widgets, output, 129, 17 + index * 20);
        }
        widgets.addFillingArrow(78, 12, (int) Math.min(Integer.MAX_VALUE, Math.max(1L, ticks) * 50L));
        int footer = rows() * 20 + 16;
        String cost = energy + " FE · " + ticks + " ticks";
        String trimmed = Minecraft.getInstance().font.plainSubstrByWidth(cost, getDisplayWidth());
        widgets.addText(Component.literal(trimmed), 0, footer, 0xFF555555, false);
        widgets.addTooltipText(List.of(Component.literal(cost)), 0, footer, getDisplayWidth(), 10);
        widgets.addText(Component.literal(permission), 0, footer + 12, 0xFF555555, false);
    }

    private static void addAmount(WidgetHolder widgets, EmiIngredient ingredient, int x, int y) {
        boolean fluid = ingredient instanceof EmiStack stack && stack.getKey() instanceof Fluid;
        String amount = ingredient.getAmount() + (ingredient instanceof MatterEmiStack ? " u" : fluid ? " mB" : "");
        String trimmed = Minecraft.getInstance().font.plainSubstrByWidth(amount, 51);
        widgets.addText(Component.literal(trimmed), x, y, 0xFF555555, false);
        widgets.addTooltipText(List.of(Component.literal(amount)), x, y, 51, 10);
    }
}
