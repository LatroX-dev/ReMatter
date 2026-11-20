package dev.latrox.rematter.content.preparation;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import net.minecraft.world.item.ItemStack;

/** A read-only simulation of every physical slot, rechecked immediately before the batch commit. */
public final class PreparationInventoryPlan {
    private static final int[] ROLES={0,3,4,1};
    private final List<ItemStack> before,after;
    private final int recoveryMask;
    private PreparationInventoryPlan(List<ItemStack> before,List<ItemStack> after,int recoveryMask){this.before=before;this.after=after;this.recoveryMask=recoveryMask;}
    public static Optional<PreparationInventoryPlan> simulate(PreparationRecipe recipe,IntFunction<ItemStack> slots,int maximum) {
        var before=Arrays.stream(ROLES).mapToObj(slot->slots.apply(slot).copy()).toList();
        var input=new PreparationInput(before.get(0),before.get(1),before.get(2));
        if(!recipe.matches(input,null))return Optional.empty();
        var after=new ArrayList<>(before.stream().map(ItemStack::copy).toList());
        after.get(0).shrink(recipe.primaryCount());
        if(recipe.secondary().isPresent())after.get(1).shrink(recipe.secondaryCount());
        if(recipe.tertiary().isPresent())after.get(2).shrink(recipe.tertiaryCount());
        int mask=0;
        var results=recipe.results();
        for(int i=0;i<results.size();i++){
            int index=i==0?3:i;var output=results.get(i);var current=after.get(index);
            int cap=Math.min(maximum,output.getMaxStackSize());
            if(!current.isEmpty()&&!ItemStack.isSameItemSameComponents(current,output)||output.getCount()>cap-current.getCount())return Optional.empty();
            if(current.isEmpty())after.set(index,output.copy());else current.grow(output.getCount());
            if(i>0)mask|=1<<ROLES[index];
        }
        return Optional.of(new PreparationInventoryPlan(before,after,mask));
    }
    public boolean matchesCurrent(IntFunction<ItemStack> slots){for(int i=0;i<ROLES.length;i++)if(!ItemStack.matches(before.get(i),slots.apply(ROLES[i])))return false;return true;}
    /** Invoke only within the machine's already validated matter/FE commit. */
    public boolean commit(IntFunction<ItemStack> slots,BiConsumer<Integer,ItemStack> writer){
        if(!matchesCurrent(slots))return false;
        for(int i=0;i<ROLES.length;i++)writer.accept(ROLES[i],after.get(i).copy());
        return true;
    }
    public int recoveryMask(){return recoveryMask;}
}
