package dev.latrox.rematter.compat.jei;

import dev.latrox.rematter.compat.PreparationDisplay;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.ModContent;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class PreparationCategory implements IRecipeCategory<PreparationDisplay> {
    private final MachineKind machine;
    private final IDrawable icon;
    PreparationCategory(MachineKind machine, IGuiHelper gui) {
        this.machine = machine;
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModContent.MACHINES.get(machine).get()));
    }
    @Override public IRecipeType<PreparationDisplay> getRecipeType() { return ReMatterJeiPlugin.preparationType(machine); }
    @Override public Component getTitle() { return Component.translatable("block.rematter." + machine.id()); }
    @Override public int getWidth() { return 190; }
    @Override public int getHeight() { return 112; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, PreparationDisplay recipe, IFocusGroup focuses) {
        builder.addInputSlot(8, 23).setStandardSlotBackground().add(recipe.primary());
        recipe.secondary().ifPresent(ingredient -> builder.addInputSlot(42, 23).setStandardSlotBackground().add(ingredient));
        builder.addOutputSlot(145, 23).setOutputSlotBackground().add(recipe.output());
    }
    @Override public void draw(PreparationDisplay recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.text(font, Component.translatable("gui.rematter.preparation_inputs"), 6, 4, 0xFF404040, false);
        graphics.text(font, "→", 95, 27, 0xFF178C8B, false);
        drawLine(graphics, recipe.cost(), 53, 0xFF404040);
        drawLine(graphics, recipe.note(), 70, 0xFF267D73);
        drawLine(graphics, recipe.inputsNote(), 87, 0xFF666666);
    }
    private void drawLine(GuiGraphicsExtractor graphics, Component text, int y, int color) {
        var font = Minecraft.getInstance().font;
        graphics.text(font, font.plainSubstrByWidth(text.getString(), getWidth() - 12), 6, y, color, false);
    }
    @Override public void getTooltip(ITooltipBuilder tooltip, PreparationDisplay recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY < 50) return;
        tooltip.add(recipe.cost()); tooltip.add(recipe.note()); tooltip.add(recipe.inputsNote());
        tooltip.add(Component.translatable("gui.rematter.preparation_plain_inputs"));
    }
    @Override public Identifier getIdentifier(PreparationDisplay recipe) { return recipe.recipeId(); }
}
