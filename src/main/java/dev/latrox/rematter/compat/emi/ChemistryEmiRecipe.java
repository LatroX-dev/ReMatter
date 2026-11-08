package dev.latrox.rematter.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.latrox.rematter.client.UiPalette;
import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.ScreenChrome.Icon;
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
        int footer = rows() * 20 + 16;
        widgets.addDrawable(0,0,getDisplayWidth(),getDisplayHeight(),(graphics,mouseX,mouseY,delta) -> {
            ScreenChrome.panel(graphics,0,0,getDisplayWidth(),getDisplayHeight());
            ScreenChrome.inset(graphics,0,footer-5,getDisplayWidth(),31);
        });
        icon(widgets,Icon.INPUT,0,0,Component.literal("Input"));
        icon(widgets,Icon.OUTPUT,108,0,Component.literal("Output"));
        widgets.addText(Component.literal("Input"),16,2,UiPalette.TEXT,false);
        widgets.addText(Component.literal("Output"),124,2,UiPalette.TEXT,false);
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
        icon(widgets,Icon.POWER,2,footer-2,Component.literal(energy+" FE per operation, before upgrades"));
        value(widgets,energy+" FE",18,footer,156,UiPalette.TEXT);
        icon(widgets,Icon.TIME,2,footer+10,Component.literal(ticks+" ticks before upgrades"));
        value(widgets,ticks+" ticks",18,footer+12,144,UiPalette.MUTED);
        icon(widgets,Icon.INFO,164,footer+10,Component.literal(permission));
    }

    private static void icon(WidgetHolder widgets,Icon icon,int x,int y,Component tooltip) {
        widgets.addDrawable(x,y,12,12,(graphics,mouseX,mouseY,delta) ->
                ScreenChrome.icon(graphics,icon,0,0,UiPalette.ACCENT));
        widgets.addTooltipText(List.of(tooltip),x,y,12,12);
    }
    private static void value(WidgetHolder widgets,String text,int x,int y,int width,int color) {
        var font=Minecraft.getInstance().font;
        String visible=font.plainSubstrByWidth(text,width);
        widgets.addText(Component.literal(visible),x,y,color,false);
        if (!visible.equals(text)) widgets.addTooltipText(List.of(Component.literal(text)),x,y,font.width(visible),9);
    }

    private static void addAmount(WidgetHolder widgets, EmiIngredient ingredient, int x, int y) {
        boolean fluid = ingredient instanceof EmiStack stack && stack.getKey() instanceof Fluid;
        String amount = ingredient.getAmount() + (ingredient instanceof MatterEmiStack ? " u" : fluid ? " mB" : "");
        String trimmed = Minecraft.getInstance().font.plainSubstrByWidth(amount, 51);
        widgets.addText(Component.literal(trimmed), x, y, UiPalette.ACCENT_BRIGHT, false);
        widgets.addTooltipText(List.of(Component.literal(amount)),x,y,Minecraft.getInstance().font.width(trimmed),9);
    }
}
