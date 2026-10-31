package dev.latrox.rematter.compat.jei;

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
        if (!recipe.itemInput().isEmpty()) builder.addInputSlot(9, 27).setStandardSlotBackground().add(item(recipe.itemInput()));
        if (!recipe.itemOutput().isEmpty()) builder.addOutputSlot(106, 27).setOutputSlotBackground().add(item(recipe.itemOutput()));
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
        var font = Minecraft.getInstance().font;
        graphics.text(font, "Input", 6, 4, 0xFF404040, false);
        graphics.text(font, "Output", 103, 4, 0xFF404040, false);
        graphics.text(font, "→", 85, 30, 0xFF178C8B, false);
        graphics.text(font, recipe.energy() + " FE · " + recipe.ticks() + " ticks", 6, 104, 0xFF404040, false);
        String permission = recipe.kind() == ChemistryDisplay.Kind.DECOMPOSITION
                ? (recipe.synthesisAllowed() ? "Synthesis allowed" : "Decomposition only")
                : recipe.kind() == ChemistryDisplay.Kind.SYNTHESIS ? "Approved material output" : "Conserves constituent elements";
        graphics.text(font, permission, 6, 117, 0xFF267D73, false);
        graphics.text(font, "Hover matter for exact amounts", 6, 130, 0xFF666666, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, ChemistryDisplay recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY < 103) return;
        tooltip.add(Component.literal(recipe.energy() + " FE per operation; " + recipe.ticks() + " ticks before upgrades"));
        if (recipe.fluidInput() != null) tooltip.add(Component.literal("Fluid consumed: " + recipe.fluidInput().amount() + " mB " + recipe.fluidInput().fluidId()));
        if (recipe.fluidOutput() != null) tooltip.add(Component.literal("Fluid produced: " + recipe.fluidOutput().amount() + " mB " + recipe.fluidOutput().fluidId()));
        if (!recipe.inputs().isEmpty()) {
            tooltip.add(Component.literal("Matter consumed:"));
            recipe.inputs().forEach(value -> tooltip.add(value.description()));
        }
        if (!recipe.outputs().isEmpty()) {
            tooltip.add(Component.literal("Matter produced:"));
            recipe.outputs().forEach(value -> tooltip.add(value.description()));
        }
    }

    @Override public Identifier getIdentifier(ChemistryDisplay recipe) {
        return Identifier.fromNamespaceAndPath("rematter", "jei/" + kind.name().toLowerCase(java.util.Locale.ROOT)
                + "/" + recipe.id().replace(':', '/') + "/" + Integer.toUnsignedString(recipe.hashCode(), 16));
    }
    private static ItemStack item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)).getDefaultInstance(); }
}
