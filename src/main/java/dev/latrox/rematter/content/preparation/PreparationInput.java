package dev.latrox.rematter.content.preparation;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record PreparationInput(ItemStack primary, ItemStack secondary) implements RecipeInput {
    @Override public ItemStack getItem(int index) {
        return switch(index) { case 0 -> primary; case 1 -> secondary; default -> throw new IndexOutOfBoundsException(index); };
    }
    @Override public int size() { return 2; }
}
