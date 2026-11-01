package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.automation.AutomationPort.*;
import dev.latrox.rematter.automation.AutomationRecipeCatalogue;
import dev.latrox.rematter.content.preparation.PreparationRecipes;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;

/** Server recipe variants retain exact physical ingredients, including two-input preparation. */
public final class MachineAutomationRecipes {
    private MachineAutomationRecipes() {}
    public static List<Recipe> catalogue(ServerLevel level) {
        var result=new ArrayList<>(AutomationRecipeCatalogue.chemistry(ReMatter.chemistry(),
                id->BuiltInRegistries.ITEM.containsKey(Identifier.parse(id)),id->BuiltInRegistries.FLUID.containsKey(Identifier.parse(id))));
        for(var entry:PreparationRecipes.serverRecipes(level)) {
            var primary=items(entry.primary());
            var secondary=entry.secondary().map(MachineAutomationRecipes::items).orElse(List.of(""));
            var output=entry.result();
            if(!output.getComponentsPatch().isEmpty())continue;
            var outputs=Map.of(Resource.item(BuiltInRegistries.ITEM.getKey(output.getItem()).toString()),(long)output.getCount());
            for(var a:primary)for(var b:secondary) {
                if(result.size()>=AutomationRecipeCatalogue.MAX_RECIPES)throw new IllegalArgumentException("Preparation variants exceed automation catalogue limit");
                var inputs=new TreeMap<Resource,Long>();inputs.put(Resource.item(a),1L);
                if(!b.isEmpty())inputs.merge(Resource.item(b),1L,Math::addExact);
                var base=AutomationRecipeCatalogue.id("prepare",entry.id().toString());
                // IDs remain stable under tag reordering or insertion of another variant.
                var fingerprint=UUID.nameUUIDFromBytes((a+'\n'+b).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                result.add(AutomationRecipeCatalogue.recipe(base+"/"+fingerprint,"material_preparator",inputs,outputs,entry.energy(),entry.ticks()));
            }
        }
        return AutomationRecipeCatalogue.checked(result);
    }
    private static List<String> items(Ingredient ingredient) {
        return ingredient.items().filter(holder->ingredient.test(new net.minecraft.world.item.ItemStack(holder))).map(holder->BuiltInRegistries.ITEM.getKey(holder.value()).toString()).distinct().sorted().toList();
    }
    public static boolean supports(MachineKind kind,Recipe recipe) {
        return kind.id().equals(recipe.workerKind()) || kind==MachineKind.INDUSTRIAL_REACTOR&&recipe.workerKind().equals(MachineKind.REACTOR.id())
                || kind==MachineKind.INDUSTRIAL_PREPARATOR&&recipe.workerKind().equals(MachineKind.PREPARATOR.id());
    }
}
