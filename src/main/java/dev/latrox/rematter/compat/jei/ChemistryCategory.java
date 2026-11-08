package dev.latrox.rematter.compat.jei;

import dev.latrox.rematter.client.UiPalette;
import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.ScreenChrome.Icon;
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
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class ChemistryCategory implements IRecipeCategory<ChemistryDisplay> {
    private final ChemistryDisplay.Kind kind;
    private final IDrawable icon;
    ChemistryCategory(ChemistryDisplay.Kind kind, IGuiHelper gui) {
        this.kind = kind;
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ReMatterJeiPlugin.machine(kind)));
    }
    @Override public IRecipeType<ChemistryDisplay> getRecipeType() { return ReMatterJeiPlugin.type(kind); }
    @Override public Component getTitle() { return Component.literal(switch (kind) {
        case DECOMPOSITION -> "Material decomposition";
        case SYNTHESIS -> "Material synthesis";
        case ELECTROLYSIS -> "Electrolysis";
        case REACTION -> "Chemical reactions";
    }); }
    @Override public int getWidth() { return 180; }
    @Override public int getHeight() { return 140; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ChemistryDisplay recipe, IFocusGroup focuses) {
        if (!recipe.itemInput().isEmpty()) builder.addInputSlot(9, 27).setStandardSlotBackground().add(item(recipe.itemInput(), recipe.itemCount()));
        if (!recipe.itemOutput().isEmpty()) builder.addOutputSlot(106, 27).setOutputSlotBackground().add(item(recipe.itemOutput(), recipe.itemCount()));
        if (recipe.fluidInput() != null) builder.addInputSlot(9, 27).setStandardSlotBackground()
                .add(BuiltInRegistries.FLUID.getValue(Identifier.parse(recipe.fluidInput().fluidId())), recipe.fluidInput().amount());
        if (recipe.fluidOutput() != null) builder.addOutputSlot(106, 27).setOutputSlotBackground()
                .add(BuiltInRegistries.FLUID.getValue(Identifier.parse(recipe.fluidOutput().fluidId())), recipe.fluidOutput().amount());
        addMatter(builder, recipe.inputs(), RecipeIngredientRole.INPUT, 9);
        addMatter(builder, recipe.outputs(), RecipeIngredientRole.OUTPUT, 106);
    }

    private static void addMatter(IRecipeLayoutBuilder builder, List<MatterIngredient> ingredients, RecipeIngredientRole role, int x) {
        // Keep every ingredient searchable. Large custom recipes cycle the final slot rather than losing ingredients.
        for (int i = 0; i < Math.min(8, ingredients.size()); i++) {
            var slot = builder.addSlot(role, x + (i % 3) * 22, 27 + (i / 3) * 25).setStandardSlotBackground();
            if (i == 7 && ingredients.size() > 8) slot.addIngredients(MatterIngredient.TYPE, ingredients.subList(7, ingredients.size()));
            else slot.add(MatterIngredient.TYPE, ingredients.get(i));
        }
    }

    @Override
    public void draw(ChemistryDisplay recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        ScreenChrome.panel(graphics,0,0,getWidth(),getHeight());
        ScreenChrome.inset(graphics,4,99,getWidth()-8,37);
        var font=Minecraft.getInstance().font;
        ScreenChrome.icon(graphics,Icon.INPUT,6,3,UiPalette.ACCENT);
        ScreenChrome.icon(graphics,Icon.OUTPUT,103,3,UiPalette.ACCENT);
        graphics.text(font,"Input",22,5,UiPalette.TEXT,false);
        graphics.text(font,"Output",119,5,UiPalette.TEXT,false);
        ScreenChrome.icon(graphics,Icon.NEXT,83,29,UiPalette.ACCENT);
        ScreenChrome.icon(graphics,Icon.POWER,6,101,UiPalette.ACCENT);
        graphics.text(font,font.plainSubstrByWidth(recipe.energy()+" FE",148),22,103,UiPalette.TEXT,false);
        ScreenChrome.icon(graphics,Icon.TIME,6,119,UiPalette.ACCENT);
        graphics.text(font,font.plainSubstrByWidth(recipe.ticks()+" ticks",132),22,121,UiPalette.MUTED,false);
        ScreenChrome.icon(graphics,Icon.INFO,160,119,UiPalette.ACCENT);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, ChemistryDisplay recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (inside(mouseX,mouseY,6,101)) {
            tooltip.add(Component.literal(recipe.energy()+" FE per operation, before upgrades"));
        } else if (inside(mouseX,mouseY,6,119)) {
            tooltip.add(Component.literal(recipe.ticks()+" ticks before upgrades"));
        } else if (inside(mouseX,mouseY,6,3)) {
            tooltip.add(Component.literal("Input"));
            if (recipe.fluidInput()!=null) tooltip.add(Component.literal("Fluid consumed: "+recipe.fluidInput().amount()+" mB "+recipe.fluidInput().fluidId()));
            if (!recipe.inputs().isEmpty()) {
                tooltip.add(Component.literal("Matter consumed:"));
                recipe.inputs().forEach(value -> tooltip.add(value.description()));
            }
        } else if (inside(mouseX,mouseY,103,3)) {
            tooltip.add(Component.literal("Output"));
            if (recipe.fluidOutput()!=null) tooltip.add(Component.literal("Fluid produced: "+recipe.fluidOutput().amount()+" mB "+recipe.fluidOutput().fluidId()));
            if (!recipe.outputs().isEmpty()) {
                tooltip.add(Component.literal("Matter produced:"));
                recipe.outputs().forEach(value -> tooltip.add(value.description()));
            }
        } else if (inside(mouseX,mouseY,160,119)) {
            String permission=recipe.kind()==ChemistryDisplay.Kind.DECOMPOSITION
                    ? (recipe.synthesisAllowed()?"Synthesis allowed":"Decomposition only")
                    : recipe.kind()==ChemistryDisplay.Kind.SYNTHESIS?"Approved material output":"Conserves constituent elements";
            tooltip.add(Component.literal(permission));
            if (recipe.itemCount()>1) tooltip.add(Component.literal("Exact batch: "+recipe.itemCount()+" items per operation"));
            tooltip.add(Component.literal("Hover an ingredient for its exact amount"));
        }
    }
    private static boolean inside(double mouseX,double mouseY,int x,int y) {
        return mouseX>=x && mouseX<x+12 && mouseY>=y && mouseY<y+12;
    }

    @Override public Identifier getIdentifier(ChemistryDisplay recipe) {
        return Identifier.fromNamespaceAndPath("rematter", "jei/" + kind.name().toLowerCase(java.util.Locale.ROOT)
                + "/" + recipe.id().replace(':', '/') + "/" + Integer.toUnsignedString(recipe.hashCode(), 16));
    }
    private static ItemStack item(String id, int count) { return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)), count); }
}
