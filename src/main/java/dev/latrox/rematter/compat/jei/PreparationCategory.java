package dev.latrox.rematter.compat.jei;

import dev.latrox.rematter.compat.PreparationDisplay;
import dev.latrox.rematter.client.UiPalette;
import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.ScreenChrome.Icon;
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
        builder.addInputSlot(8, 23).setStandardSlotBackground().addItemStacks(stacks(recipe.primary(),recipe.primaryCount()));
        recipe.secondary().ifPresent(ingredient -> builder.addInputSlot(42, 23).setStandardSlotBackground().addItemStacks(stacks(ingredient,recipe.secondaryCount())));
        recipe.tertiary().ifPresent(ingredient -> builder.addInputSlot(76,23).setStandardSlotBackground().addItemStacks(stacks(ingredient,recipe.tertiaryCount())));
        builder.addOutputSlot(145, 23).setOutputSlotBackground().add(recipe.output());
    }
    private static java.util.List<ItemStack> stacks(net.minecraft.world.item.crafting.Ingredient ingredient,int count) {
        return ingredient.items().map(holder->new ItemStack(holder,count)).filter(ingredient::test).toList();
    }
    @Override public void draw(PreparationDisplay recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        ScreenChrome.panel(graphics,0,0,getWidth(),getHeight());
        ScreenChrome.inset(graphics,4,48,getWidth()-8,60);
        ScreenChrome.icon(graphics,Icon.INPUT,6,3,UiPalette.ACCENT);
        graphics.text(Minecraft.getInstance().font,Component.translatable("gui.rematter.preparation_inputs"),22,5,UiPalette.TEXT,false);
        ScreenChrome.icon(graphics,Icon.OUTPUT,143,3,UiPalette.ACCENT);
        ScreenChrome.icon(graphics,Icon.NEXT,105,25,UiPalette.ACCENT);
        ScreenChrome.icon(graphics,recipe.manual()?Icon.CRANK:Icon.POWER,8,52,UiPalette.ACCENT);
        value(graphics,recipe.manual()?recipe.cranks()+" turns":recipe.energy()+" FE",24,54,154,UiPalette.TEXT);
        if (!recipe.manual()) {
            ScreenChrome.icon(graphics,Icon.TIME,8,70,UiPalette.ACCENT);
            value(graphics,recipe.ticks()+" ticks",24,72,154,UiPalette.MUTED);
        }
        ScreenChrome.icon(graphics,Icon.RECIPE,8,88,UiPalette.ACCENT);
        value(graphics,recipe.output().getHoverName().getString(),24,90,138,UiPalette.ACCENT);
        ScreenChrome.icon(graphics,Icon.INFO,168,88,UiPalette.ACCENT);
    }
    private static void value(GuiGraphicsExtractor graphics,String text,int x,int y,int width,int color) {
        var font=Minecraft.getInstance().font;
        graphics.text(font,font.plainSubstrByWidth(text,width),x,y,color,false);
    }
    @Override public void getTooltip(ITooltipBuilder tooltip, PreparationDisplay recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (inside(mouseX,mouseY,6,3)) tooltip.add(Component.translatable("gui.rematter.preparation_inputs"));
        else if (inside(mouseX,mouseY,143,3)) tooltip.add(Component.literal("Output"));
        else if (inside(mouseX,mouseY,8,52) || !recipe.manual() && inside(mouseX,mouseY,8,70)) tooltip.add(recipe.cost());
        else if (inside(mouseX,mouseY,8,88)) tooltip.add(recipe.output().getHoverName());
        else if (inside(mouseX,mouseY,168,88)) {
            tooltip.add(recipe.note()); tooltip.add(recipe.inputsNote());
            tooltip.add(Component.translatable("gui.rematter.preparation_plain_inputs"));
        }
    }
    private static boolean inside(double mouseX,double mouseY,int x,int y) {
        return mouseX>=x && mouseX<x+12 && mouseY>=y && mouseY<y+12;
    }
    @Override public Identifier getIdentifier(PreparationDisplay recipe) { return recipe.recipeId(); }
}
