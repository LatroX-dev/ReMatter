package dev.latrox.rematter.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.latrox.rematter.compat.PreparationDisplay;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

record PreparationEmiRecipe(EmiRecipeCategory category, PreparationDisplay display,
                            List<EmiIngredient> inputs, List<EmiStack> outputs) implements EmiRecipe {
    PreparationEmiRecipe(EmiRecipeCategory category, PreparationDisplay display) {
        this(category, display, ingredients(display), List.of(EmiStack.of(display.output())));
    }
    private static List<EmiIngredient> ingredients(PreparationDisplay display) {
        var inputs = new ArrayList<EmiIngredient>();
        inputs.add(EmiIngredient.of(display.primary()));
        display.secondary().ifPresent(value -> inputs.add(EmiIngredient.of(value)));
        return List.copyOf(inputs);
    }
    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public Identifier getId() { return display.recipeId(); }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiStack> getOutputs() { return outputs; }
    @Override public int getDisplayWidth() { return 190; }
    @Override public int getDisplayHeight() { return 112; }
    @Override public void addWidgets(WidgetHolder widgets) {
        widgets.addText(Component.translatable("gui.rematter.preparation_inputs"), 6, 4, 0xFF555555, false);
        for (int i = 0; i < inputs.size(); i++) widgets.addSlot(inputs.get(i), 6 + i * 34, 21);
        widgets.addSlot(outputs.getFirst(), 143, 21).recipeContext(this);
        // Manual work has no elapsed-time promise or automatically filling arrow.
        if (display.manual()) widgets.addText(Component.literal("→"), 95, 27, 0xFF178C8B, false);
        else widgets.addFillingArrow(88, 21, Math.toIntExact(Math.max(1L, display.ticks()) * 50));
        text(widgets, display.cost(), 53, 0xFF555555);
        text(widgets, display.note(), 70, 0xFF267D73);
        text(widgets, display.inputsNote(), 87, 0xFF666666);
        widgets.addTooltipText(List.of(Component.translatable("gui.rematter.preparation_plain_inputs")), 0, 100, getDisplayWidth(), 12);
    }
    private void text(WidgetHolder widgets, Component text, int y, int color) {
        String visible = Minecraft.getInstance().font.plainSubstrByWidth(text.getString(), getDisplayWidth() - 12);
        widgets.addText(Component.literal(visible), 6, y, color, false);
        widgets.addTooltipText(List.of(text), 0, y, getDisplayWidth(), 12);
    }
}
