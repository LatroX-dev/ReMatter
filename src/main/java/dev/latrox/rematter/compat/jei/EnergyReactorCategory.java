package dev.latrox.rematter.compat.jei;

import dev.latrox.rematter.client.Quantities;
import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.UiIcon;
import dev.latrox.rematter.client.UiPalette;
import dev.latrox.rematter.compat.EnergyReactorDisplay;
import dev.latrox.rematter.content.MachineKind;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class EnergyReactorCategory implements IRecipeCategory<EnergyReactorDisplay> {
    private static final int WIDTH = 196, HEIGHT = 92;
    private final IDrawable icon;

    EnergyReactorCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(ReMatterJeiPlugin.machine(MachineKind.ENERGY_REACTOR)));
    }

    @Override public IRecipeType<EnergyReactorDisplay> getRecipeType() { return ReMatterJeiPlugin.energyReactorType(); }
    @Override public Component getTitle() { return Component.translatable("block.rematter.energy_reactor_controller"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public Identifier getRegistryName(EnergyReactorDisplay recipe) { return recipe.recipeId(); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, EnergyReactorDisplay recipe, IFocusGroup focuses) {
        int row = 0;
        if (!recipe.itemInput().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 7, 17 + row * 20).add(recipe.itemInput());
            row++;
        }
        for (var m : recipe.matterInputs()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 7, 17 + row * 20)
                    .add(MatterIngredient.TYPE, new MatterIngredient(m.key(), m.amount()));
            row++;
        }
    }

    @Override
    public void draw(EnergyReactorDisplay recipe, IRecipeSlotsView view, GuiGraphicsExtractor g, double mx, double my) {
        var font = Minecraft.getInstance().font;
        int footer = 58;
        ScreenChrome.panel(g, 0, 0, WIDTH, HEIGHT);
        ScreenChrome.inset(g, 4, footer - 2, WIDTH - 8, 32);
        ScreenChrome.icon(g, UiIcon.SLOT_INPUT, 4, 2, UiPalette.TEXT);
        g.text(font, Component.translatable("gui.rematter.viewer.input"), 20, 4, UiPalette.TEXT, false);
        ScreenChrome.icon(g, recipe.coolantEntry() ? UiIcon.LIQUID : UiIcon.ENERGY, 110, 2,
                recipe.coolantEntry() ? UiPalette.LIQUID : UiPalette.ACCENT);
        g.text(font, Component.translatable("gui.rematter.viewer.output"), 126, 4, UiPalette.TEXT, false);

        int row = 0;
        if (!recipe.itemInput().isEmpty()) {
            ScreenChrome.slot(g, 6, 16 + row * 20);
            ScreenChrome.text(g, font, recipe.consumptionSummary().getString(), 27, 21 + row * 20, 56, UiPalette.ACCENT_BRIGHT);
            row++;
        }
        for (var m : recipe.matterInputs()) {
            ScreenChrome.slot(g, 6, 16 + row * 20);
            ScreenChrome.text(g, font, Quantities.number(m.amount()) + " u/t", 27, 21 + row * 20, 56, UiPalette.ACCENT_BRIGHT);
            row++;
        }
        ScreenChrome.icon(g, UiIcon.ARROW_RIGHT, 90, 20, UiPalette.ACCENT);
        ScreenChrome.text(g, font, recipe.outputSummary().getString(), 114, 21, 78,
                recipe.coolantEntry() ? UiPalette.LIQUID : UiPalette.ACCENT);

        ScreenChrome.text(g, font, recipe.title().getString(), 8, footer + 3, 162, UiPalette.TEXT);
        ScreenChrome.text(g, font, recipe.consumptionSummary().getString(), 8, footer + 15, 162, UiPalette.MUTED);
        ScreenChrome.icon(g, UiIcon.INFO, 174, footer + 11, UiPalette.MUTED);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, EnergyReactorDisplay recipe, IRecipeSlotsView view, double mx, double my) {
        if (mx >= 172 && mx <= 192 && my >= 66 && my <= 86) {
            tooltip.add(recipe.note());
        }
    }
}
