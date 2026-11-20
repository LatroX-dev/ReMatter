package dev.latrox.rematter.content.preparation;

import dev.latrox.rematter.ReMatter;
import java.util.ArrayList;
import java.util.Comparator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/** Client-only native recipe receipt; the public catalogue API has no client class dependency. */
@EventBusSubscriber(modid=ReMatter.MOD_ID,value=Dist.CLIENT)
public final class PreparationClientEvents {
    private PreparationClientEvents(){}
    @SubscribeEvent public static void received(RecipesReceivedEvent event){
        if(!event.getRecipeTypes().contains(PreparationRecipes.TYPE.get()))return;
        var entries=new ArrayList<PreparationRecipes.Entry>();
        for(var holder:event.getRecipeMap().values())if(holder.value() instanceof PreparationRecipe recipe)
            entries.add(new PreparationRecipes.Entry(holder.id().identifier(),recipe));
        entries.sort(Comparator.comparing(entry->entry.id().toString()));
        PreparationRecipes.acceptClient(entries);
    }
}
