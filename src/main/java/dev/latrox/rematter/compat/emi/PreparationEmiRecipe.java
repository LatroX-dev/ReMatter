package dev.latrox.rematter.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.latrox.rematter.compat.PreparationDisplay;
import dev.latrox.rematter.client.UiPalette;
import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.UiIcon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

record PreparationEmiRecipe(EmiRecipeCategory category, PreparationDisplay display,
                            List<EmiIngredient> inputs, List<EmiStack> outputs) implements EmiRecipe {
    PreparationEmiRecipe(EmiRecipeCategory category, PreparationDisplay display) {
        this(category, display, ingredients(display), List.of(EmiStack.of(display.output())));
    }
    private static List<EmiIngredient> ingredients(PreparationDisplay display) {
        var inputs = new ArrayList<EmiIngredient>();
        inputs.add(EmiIngredient.of(display.primary(),display.primaryCount()));
        display.secondary().ifPresent(value -> inputs.add(EmiIngredient.of(value,display.secondaryCount())));
        display.tertiary().ifPresent(value -> inputs.add(EmiIngredient.of(value,display.tertiaryCount())));
        return List.copyOf(inputs);
    }
    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public Identifier getId() { return display.recipeId(); }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiStack> getOutputs() { return outputs; }
    @Override public int getDisplayWidth() { return 190; }
    @Override public int getDisplayHeight() { return 112; }
    @Override public void addWidgets(WidgetHolder widgets) {
        widgets.addDrawable(0,0,getDisplayWidth(),getDisplayHeight(),(graphics,mouseX,mouseY,delta) -> {
            ScreenChrome.panel(graphics,0,0,getDisplayWidth(),getDisplayHeight());
            ScreenChrome.inset(graphics,4,48,getDisplayWidth()-8,60);
        });
        EmiWidgets.icon(widgets,UiIcon.SLOT_INPUT,6,3,List.of(Component.translatable("gui.rematter.preparation_inputs")));
        widgets.addText(Component.translatable("gui.rematter.preparation_inputs"),22,5,UiPalette.TEXT,false);
        EmiWidgets.icon(widgets,UiIcon.SLOT_OUTPUT,143,3,List.of(Component.translatable("gui.rematter.viewer.output")));
        for (int i = 0; i < inputs.size(); i++) widgets.addSlot(inputs.get(i),6+i*34,21);
        widgets.addSlot(outputs.getFirst(),143,21).recipeContext(this);
        // Manual work has no elapsed-time promise or automatically filling arrow.
        if (display.manual()) EmiWidgets.icon(widgets,UiIcon.ARROW_RIGHT,105,25,List.of(display.note()));
        else widgets.addFillingArrow(98,21,Math.toIntExact(Math.max(1L,display.ticks())*50));
        EmiWidgets.icon(widgets,display.manual()?UiIcon.CRANK:UiIcon.ENERGY,8,52,List.of(display.cost()));
        EmiWidgets.value(widgets,display.costValue().getString(),24,54,154,UiPalette.TEXT);
        if (!display.manual()) {
            EmiWidgets.icon(widgets,UiIcon.TIME,8,70,List.of(display.cost()));
            EmiWidgets.value(widgets,display.timeValue().getString(),24,72,154,UiPalette.MUTED);
        }
        EmiWidgets.icon(widgets,UiIcon.RECIPE,8,88,List.of(display.output().getHoverName()));
        EmiWidgets.value(widgets,display.output().getHoverName().getString(),24,90,138,UiPalette.ACCENT);
        EmiWidgets.icon(widgets,UiIcon.INFO,168,88,List.of(display.note(),display.inputsNote(),
                Component.translatable("gui.rematter.preparation_plain_inputs")));
    }
}
