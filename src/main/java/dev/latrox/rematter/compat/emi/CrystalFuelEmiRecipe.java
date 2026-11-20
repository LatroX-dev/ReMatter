package dev.latrox.rematter.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.UiIcon;
import dev.latrox.rematter.client.UiPalette;
import dev.latrox.rematter.compat.CrystalFuelDisplay;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

record CrystalFuelEmiRecipe(EmiRecipeCategory category, CrystalFuelDisplay display,
                            List<EmiIngredient> inputs) implements EmiRecipe {
    CrystalFuelEmiRecipe(EmiRecipeCategory category, CrystalFuelDisplay display) {
        this(category, display, List.of(EmiStack.of(display.input())));
    }

    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public Identifier getId() { return display.recipeId(); }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiStack> getOutputs() { return List.of(); }
    @Override public int getDisplayWidth() { return 190; }
    @Override public int getDisplayHeight() { return 96; }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addDrawable(0, 0, getDisplayWidth(), getDisplayHeight(), (graphics, mouseX, mouseY, delta) -> {
            ScreenChrome.panel(graphics, 0, 0, getDisplayWidth(), getDisplayHeight());
            ScreenChrome.inset(graphics, 4, 46, getDisplayWidth() - 8, 46);
        });
        EmiWidgets.icon(widgets, UiIcon.SLOT_INPUT, 6, 3, List.of(Component.translatable("gui.rematter.crystal_fuel.input")));
        widgets.addText(Component.translatable("gui.rematter.crystal_fuel.input"), 22, 5, UiPalette.TEXT, false);
        widgets.addSlot(inputs.getFirst(), 8, 21);
        widgets.addFillingArrow(42, 21, Math.max(1, display.ticks()) * 50);
        EmiWidgets.icon(widgets, UiIcon.ENERGY, 74, 24, List.of(display.yieldValue()));
        EmiWidgets.value(widgets, display.yieldValue().getString(), 90, 26, 92, UiPalette.ACCENT);
        EmiWidgets.icon(widgets, UiIcon.TIME, 8, 52, List.of(display.rateTimeValue()));
        EmiWidgets.value(widgets, display.rateTimeValue().getString(), 24, 54, 154, UiPalette.TEXT);
        EmiWidgets.icon(widgets, UiIcon.RECIPE, 8, 70, List.of(display.input().getHoverName()));
        EmiWidgets.value(widgets, display.input().getHoverName().getString(), 24, 72, 138, UiPalette.MUTED);
        EmiWidgets.icon(widgets, UiIcon.INFO, 168, 70, List.of(display.note()));
    }
}
