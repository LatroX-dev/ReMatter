package dev.latrox.rematter.compat.jei;

import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.UiIcon;
import dev.latrox.rematter.client.UiPalette;
import dev.latrox.rematter.compat.CrystalFuelDisplay;
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

final class CrystalFuelCategory implements IRecipeCategory<CrystalFuelDisplay> {
    private final MachineKind machine;
    private final IDrawable icon;

    CrystalFuelCategory(MachineKind machine, IGuiHelper gui) {
        this.machine = machine;
        this.icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModContent.MACHINES.get(machine).get()));
    }

    @Override public IRecipeType<CrystalFuelDisplay> getRecipeType() { return ReMatterJeiPlugin.crystalFuelType(machine); }
    @Override public Component getTitle() { return Component.translatable("block.rematter." + machine.id()); }
    @Override public int getWidth() { return 190; }
    @Override public int getHeight() { return 96; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CrystalFuelDisplay recipe, IFocusGroup focuses) {
        builder.addInputSlot(8, 21).setStandardSlotBackground().add(recipe.input());
    }

    @Override
    public void draw(CrystalFuelDisplay recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        ScreenChrome.panel(graphics, 0, 0, getWidth(), getHeight());
        ScreenChrome.inset(graphics, 4, 46, getWidth() - 8, 46);
        ScreenChrome.icon(graphics, UiIcon.SLOT_INPUT, 4, 1, UiPalette.ACCENT);
        var font = Minecraft.getInstance().font;
        graphics.text(font, Component.translatable("gui.rematter.crystal_fuel.input"), 22, 5, UiPalette.TEXT, false);
        ScreenChrome.icon(graphics, UiIcon.ARROW_RIGHT, 42, 23, UiPalette.ACCENT);
        ScreenChrome.icon(graphics, UiIcon.ENERGY, 72, 22, UiPalette.ACCENT);
        ScreenChrome.text(graphics, font, recipe.yieldValue().getString(), 90, 26, 92, UiPalette.ACCENT);
        ScreenChrome.icon(graphics, UiIcon.TIME, 6, 50, UiPalette.ACCENT);
        ScreenChrome.text(graphics, font, recipe.rateTimeValue().getString(), 24, 54, 154, UiPalette.TEXT);
        ScreenChrome.icon(graphics, UiIcon.RECIPE, 6, 68, UiPalette.ACCENT);
        ScreenChrome.text(graphics, font, recipe.input().getHoverName().getString(), 24, 72, 138, UiPalette.MUTED);
        ScreenChrome.icon(graphics, UiIcon.INFO, 166, 68, UiPalette.ACCENT);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, CrystalFuelDisplay recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, 6, 3)) tooltip.add(Component.translatable("gui.rematter.crystal_fuel.input"));
        else if (inside(mouseX, mouseY, 74, 24)) tooltip.add(recipe.yieldValue());
        else if (inside(mouseX, mouseY, 8, 52)) tooltip.add(recipe.rateTimeValue());
        else if (inside(mouseX, mouseY, 8, 70)) tooltip.add(recipe.input().getHoverName());
        else if (inside(mouseX, mouseY, 168, 70)) tooltip.add(recipe.note());
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 12;
    }

    @Override public Identifier getIdentifier(CrystalFuelDisplay recipe) { return recipe.recipeId(); }
}
