package dev.latrox.rematter.compat.jei;

import dev.latrox.rematter.client.Quantities;
import dev.latrox.rematter.client.SubstanceNames;
import dev.latrox.rematter.client.UiPalette;
import dev.latrox.rematter.client.ScreenChrome;
import dev.latrox.rematter.client.ScreenChrome.Icon;
import dev.latrox.rematter.compat.ChemistryViewer;
import java.util.List;
import java.util.Optional;
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

final class ChemistryCategory implements IRecipeCategory<ChemistryViewer.Entry> {
    private final ChemistryViewer.Category category;
    private final IDrawable icon;
    ChemistryCategory(ChemistryViewer.Category category, IGuiHelper gui) {
        this.category = category;
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ReMatterJeiPlugin.machine(category.machines().getFirst())));
    }
    @Override public IRecipeType<ChemistryViewer.Entry> getRecipeType() { return ReMatterJeiPlugin.type(category); }
    @Override public Component getTitle() { return category.title(); }
    @Override public int getWidth() { return 180; }
    @Override public int getHeight() { return 140; }
    @Override public IDrawable getIcon() { return icon; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ChemistryViewer.Entry recipe, IFocusGroup focuses) {
        recipe.consumed().ifPresent(resource -> addResource(builder.addInputSlot(9, 27).setStandardSlotBackground(), resource));
        recipe.produced().ifPresent(resource -> addResource(builder.addOutputSlot(106, 27).setOutputSlotBackground(), resource));
        addMatter(builder, recipe.inputs(), RecipeIngredientRole.INPUT, 9);
        addMatter(builder, recipe.outputs(), RecipeIngredientRole.OUTPUT, 106);
    }

    private static void addResource(mezz.jei.api.gui.builder.IRecipeSlotBuilder slot, ChemistryViewer.Resource resource) {
        if (resource.fluid()) slot.add(BuiltInRegistries.FLUID.getValue(resource.id()), resource.amount());
        else slot.add(new ItemStack(BuiltInRegistries.ITEM.getValue(resource.id()), resource.amount()));
    }

    private static void addMatter(IRecipeLayoutBuilder builder, List<ChemistryViewer.Matter> matter, RecipeIngredientRole role, int x) {
        var ingredients = matter.stream().map(value -> new MatterIngredient(value.key(), value.amount())).toList();
        // Keep every ingredient searchable. Large custom recipes cycle the final slot rather than losing ingredients.
        for (int i = 0; i < Math.min(8, ingredients.size()); i++) {
            var slot = builder.addSlot(role, x + (i % 3) * 22, 27 + (i / 3) * 25).setStandardSlotBackground();
            if (i == 7 && ingredients.size() > 8) slot.addIngredients(MatterIngredient.TYPE, ingredients.subList(7, ingredients.size()));
            else slot.add(MatterIngredient.TYPE, ingredients.get(i));
        }
    }

    @Override
    public void draw(ChemistryViewer.Entry recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        ScreenChrome.panel(graphics,0,0,getWidth(),getHeight());
        ScreenChrome.inset(graphics,4,99,getWidth()-8,37);
        var font=Minecraft.getInstance().font;
        ScreenChrome.icon(graphics,Icon.INPUT,6,3,UiPalette.ACCENT);
        ScreenChrome.icon(graphics,Icon.OUTPUT,103,3,UiPalette.ACCENT);
        graphics.text(font,Component.translatable("gui.rematter.viewer.input"),22,5,UiPalette.TEXT,false);
        graphics.text(font,Component.translatable("gui.rematter.viewer.output"),119,5,UiPalette.TEXT,false);
        ScreenChrome.icon(graphics,Icon.NEXT,83,29,UiPalette.ACCENT);
        ScreenChrome.icon(graphics,Icon.POWER,6,101,UiPalette.ACCENT);
        graphics.text(font,font.plainSubstrByWidth(Quantities.energy(Quantities.number(recipe.energy())).getString(),148),22,103,UiPalette.TEXT,false);
        ScreenChrome.icon(graphics,Icon.TIME,6,119,UiPalette.ACCENT);
        graphics.text(font,font.plainSubstrByWidth(Quantities.ticks(Quantities.number(recipe.ticks())).getString(),132),22,121,UiPalette.MUTED,false);
        ScreenChrome.icon(graphics,Icon.INFO,160,119,UiPalette.ACCENT);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, ChemistryViewer.Entry recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (inside(mouseX,mouseY,6,101)) {
            tooltip.add(Component.translatable("gui.rematter.viewer.energy_hint",Quantities.number(recipe.energy())));
        } else if (inside(mouseX,mouseY,6,119)) {
            tooltip.add(Component.translatable("gui.rematter.viewer.ticks_hint",Quantities.number(recipe.ticks())));
        } else if (inside(mouseX,mouseY,6,3)) {
            tooltip.add(Component.translatable("gui.rematter.viewer.input"));
            side(tooltip,recipe.consumed(),recipe.inputs(),"consumed");
        } else if (inside(mouseX,mouseY,103,3)) {
            tooltip.add(Component.translatable("gui.rematter.viewer.output"));
            side(tooltip,recipe.produced(),recipe.outputs(),"produced");
        } else if (inside(mouseX,mouseY,160,119)) {
            recipe.notes().forEach(tooltip::add);
            ChemistryViewer.researchTarget(recipe).ifPresent(target -> tooltip.add(dev.latrox.rematter.client.ResearchDisplay.note(target)));
        }
    }
    private static void side(ITooltipBuilder tooltip,Optional<ChemistryViewer.Resource> resource,List<ChemistryViewer.Matter> matter,String role) {
        resource.filter(ChemistryViewer.Resource::fluid).ifPresent(fluid -> tooltip.add(Component.translatable("gui.rematter.viewer.fluid_"+role,
                Quantities.fluid(Quantities.number(fluid.amount())),SubstanceNames.fluid(fluid.id().toString()))));
        if (matter.isEmpty()) return;
        tooltip.add(Component.translatable("gui.rematter.viewer.matter_"+role));
        matter.forEach(value -> tooltip.add(Component.translatable("gui.rematter.viewer.matter_line",
                Quantities.matter(Quantities.number(value.amount())),ChemistryViewer.matterName(value.key()))));
    }
    private static boolean inside(double mouseX,double mouseY,int x,int y) {
        return mouseX>=x && mouseX<x+12 && mouseY>=y && mouseY<y+12;
    }

    @Override public Identifier getIdentifier(ChemistryViewer.Entry recipe) { return recipe.id(); }
}
