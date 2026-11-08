package dev.latrox.rematter.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.latrox.rematter.client.Quantities;
import dev.latrox.rematter.client.UiPalette;
import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.ScreenChrome.Icon;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;

record ChemistryEmiRecipe(EmiRecipeCategory category, Identifier id, List<EmiIngredient> inputs,
                          List<EmiStack> outputs, long energy, int ticks, List<Component> notes) implements EmiRecipe {
    ChemistryEmiRecipe {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        notes = List.copyOf(notes);
    }
    @Override public EmiRecipeCategory getCategory() { return category; }
    @Override public Identifier getId() { return id; }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiStack> getOutputs() { return outputs; }
    @Override public int getDisplayWidth() { return 180; }
    private int rows() { return Math.max(1, Math.max(inputs.size(), outputs.size())); }
    @Override public int getDisplayHeight() { return rows() * 20 + 48; }

    @Override public void addWidgets(WidgetHolder widgets) {
        int footer = rows() * 20 + 16;
        widgets.addDrawable(0,0,getDisplayWidth(),getDisplayHeight(),(graphics,mouseX,mouseY,delta) -> {
            ScreenChrome.panel(graphics,0,0,getDisplayWidth(),getDisplayHeight());
            ScreenChrome.inset(graphics,0,footer-5,getDisplayWidth(),31);
        });
        Component input = Component.translatable("gui.rematter.viewer.input");
        Component output = Component.translatable("gui.rematter.viewer.output");
        EmiWidgets.icon(widgets,Icon.INPUT,0,0,List.of(input));
        EmiWidgets.icon(widgets,Icon.OUTPUT,108,0,List.of(output));
        widgets.addText(input,16,2,UiPalette.TEXT,false);
        widgets.addText(output,124,2,UiPalette.TEXT,false);
        for (int index = 0; index < inputs.size(); index++) {
            var ingredient = inputs.get(index);
            widgets.addSlot(ingredient, 0, 12 + index * 20)
                    .appendTooltip(Component.translatable("gui.rematter.viewer.required", amount(ingredient)));
            addAmount(widgets, ingredient, 21, 17 + index * 20);
        }
        for (int index = 0; index < outputs.size(); index++) {
            var product = outputs.get(index);
            widgets.addSlot(product, 108, 12 + index * 20).recipeContext(this)
                    .appendTooltip(Component.translatable("gui.rematter.viewer.produced", amount(product)));
            addAmount(widgets, product, 129, 17 + index * 20);
        }
        widgets.addFillingArrow(78, 12, (int) Math.min(Integer.MAX_VALUE, Math.max(1L, ticks) * 50L));
        EmiWidgets.icon(widgets,Icon.POWER,2,footer-2,List.of(Component.translatable("gui.rematter.viewer.energy_hint",Quantities.number(energy))));
        EmiWidgets.value(widgets,Quantities.energy(Quantities.number(energy)).getString(),18,footer,156,UiPalette.TEXT);
        EmiWidgets.icon(widgets,Icon.TIME,2,footer+10,List.of(Component.translatable("gui.rematter.viewer.ticks_hint",Quantities.number(ticks))));
        EmiWidgets.value(widgets,Quantities.ticks(Quantities.number(ticks)).getString(),18,footer+12,144,UiPalette.MUTED);
        EmiWidgets.icon(widgets,Icon.INFO,164,footer+10,notes);
    }

    private static Component amount(EmiIngredient ingredient) {
        String value = Quantities.number(ingredient.getAmount());
        if (ingredient instanceof MatterEmiStack) return Quantities.matter(value);
        return ingredient instanceof EmiStack stack && stack.getKey() instanceof Fluid ? Quantities.fluid(value) : Component.literal(value);
    }

    private static void addAmount(WidgetHolder widgets, EmiIngredient ingredient, int x, int y) {
        Component amount = amount(ingredient);
        String trimmed = Minecraft.getInstance().font.plainSubstrByWidth(amount.getString(), 51);
        widgets.addText(Component.literal(trimmed), x, y, UiPalette.ACCENT_BRIGHT, false);
        widgets.addTooltipText(List.of(amount),x,y,Minecraft.getInstance().font.width(trimmed),9);
    }
}
