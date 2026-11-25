package dev.latrox.rematter.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.latrox.rematter.client.Quantities;
import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.UiIcon;
import dev.latrox.rematter.client.UiPalette;
import dev.latrox.rematter.compat.EnergyReactorDisplay;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

record EnergyReactorEmiRecipe(EmiRecipeCategory category, EnergyReactorDisplay display,
                              List<EmiIngredient> inputs) implements EmiRecipe {
    EnergyReactorEmiRecipe(EmiRecipeCategory category, EnergyReactorDisplay display) {
        this(category, display, buildInputs(display));
    }

    private static List<EmiIngredient> buildInputs(EnergyReactorDisplay display) {
        var list = new ArrayList<EmiIngredient>();
        if (!display.itemInput().isEmpty()) list.add(EmiStack.of(display.itemInput()));
        for (var m : display.matterInputs()) list.add(new MatterEmiStack(m.key(), m.amount()));
        return List.copyOf(list);
    }

    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public Identifier getId() { return display.recipeId(); }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiStack> getOutputs() { return List.of(); }
    @Override public int getDisplayWidth() { return 196; }
    private int rows() { return Math.max(1, inputs.size()); }
    @Override public int getDisplayHeight() { return rows() * 20 + 52; }

    @Override public void addWidgets(WidgetHolder widgets) {
        int footer = rows() * 20 + 18;
        widgets.addDrawable(0, 0, getDisplayWidth(), getDisplayHeight(), (graphics, mx, my, delta) -> {
            ScreenChrome.panel(graphics, 0, 0, getDisplayWidth(), getDisplayHeight());
            ScreenChrome.inset(graphics, 4, footer - 2, getDisplayWidth() - 8, 32);
        });
        Component inLabel = Component.translatable("gui.rematter.viewer.input");
        Component outLabel = Component.translatable("gui.rematter.viewer.output");
        EmiWidgets.icon(widgets, UiIcon.SLOT_INPUT, 4, 2, List.of(inLabel));
        widgets.addText(inLabel, 20, 4, UiPalette.TEXT, false);
        EmiWidgets.icon(widgets, display.coolantEntry() ? UiIcon.LIQUID : UiIcon.ENERGY, 110, 2, List.of(outLabel));
        widgets.addText(outLabel, 126, 4, UiPalette.TEXT, false);

        for (int i = 0; i < inputs.size(); i++) {
            var ing = inputs.get(i);
            int y = 16 + i * 20;
            widgets.addSlot(ing, 6, y);
            String txt = ing instanceof MatterEmiStack
                    ? Quantities.number(ing.getAmount()) + " u/t"
                    : display.consumptionSummary().getString();
            EmiWidgets.value(widgets, txt, 27, y + 5, 56, UiPalette.ACCENT_BRIGHT);
        }
        widgets.addFillingArrow(86, 18, 1000);
        EmiWidgets.value(widgets, display.outputSummary().getString(), 114, 21, 78,
                display.coolantEntry() ? UiPalette.LIQUID : UiPalette.ACCENT);

        EmiWidgets.value(widgets, display.title().getString(), 8, footer + 3, 162, UiPalette.TEXT);
        EmiWidgets.value(widgets, display.consumptionSummary().getString(), 8, footer + 15, 162, UiPalette.MUTED);
        EmiWidgets.icon(widgets, UiIcon.INFO, 174, footer + 11, List.of(display.note()));
    }
}
