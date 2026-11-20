package dev.latrox.rematter.guide;

import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.ModContent;
import dev.latrox.rematter.content.preparation.PreparationRecipe;
import dev.latrox.rematter.content.preparation.PreparationRecipes;
import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;
import java.util.ArrayList;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/** Loaded by GuideME via ServiceLoader to render ReMatter preparation and sintering recipes. */
public final class ReMatterGuideRecipes implements RecipeTypeMappingSupplier {
    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(PreparationRecipes.TYPE.get(), ReMatterGuideRecipes::preparationBox);
    }

    private static LytStandardRecipeBox<PreparationRecipe> preparationBox(RecipeHolder<PreparationRecipe> holder) {
        var recipe = holder.value();
        var machineBlock = ModContent.MACHINES.get(machineFor(recipe)).get();
        var inputs = new ArrayList<SlotDisplay>(3);
        inputs.add(PreparationRecipe.slotDisplay(recipe.primary(), recipe.primaryCount()));
        recipe.secondary().ifPresent(ing -> inputs.add(PreparationRecipe.slotDisplay(ing, recipe.secondaryCount())));
        recipe.tertiary().ifPresent(ing -> inputs.add(PreparationRecipe.slotDisplay(ing, recipe.tertiaryCount())));
        var outputs = recipe.results().stream().map(ItemStackTemplate::fromNonEmptyStack).toList();

        var builder = LytStandardRecipeBox.builder()
                .icon(machineBlock)
                .title(machineBlock.asItem().getDefaultInstance().getItemName().getString())
                .input(LytSlotGrid.rowFromDisplays(inputs, false));
        if (outputs.size() == 1) {
            builder.output(outputs.getFirst());
        } else {
            builder.output(LytSlotGrid.rowFromStacks(outputs, false));
        }
        return builder.build(holder);
    }

    private static MachineKind machineFor(PreparationRecipe recipe) {
        if (PreparationRecipe.SINTERING.equals(recipe.process())) {
            return MachineKind.SINTER_FURNACE;
        }
        return switch (recipe.minimumTier()) {
            case 0 -> MachineKind.HAND_PREPARATOR;
            case 1 -> MachineKind.PREPARATOR;
            default -> MachineKind.INDUSTRIAL_PREPARATOR;
        };
    }
}
