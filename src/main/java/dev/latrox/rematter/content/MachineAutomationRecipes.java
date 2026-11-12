package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.automation.AutomationPort.*;
import dev.latrox.rematter.automation.AutomationRecipeCatalogue;
import dev.latrox.rematter.content.preparation.PreparationRecipes;
import dev.latrox.rematter.content.preparation.PreparationNetworkPolicy;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;

/** Server recipe variants retain exact physical ingredients, including counted three-input preparation and sintering. */
public final class MachineAutomationRecipes {
    private MachineAutomationRecipes() {}
    public static List<Recipe> catalogue(ServerLevel level) {
        var result=new ArrayList<>(AutomationRecipeCatalogue.chemistry(ReMatter.chemistry(),
                id->BuiltInRegistries.ITEM.containsKey(Identifier.parse(id)),id->BuiltInRegistries.FLUID.containsKey(Identifier.parse(id))));
        for(var entry:PreparationNetworkPolicy.exported(PreparationRecipes.serverRecipes(level),PreparationRecipes.Entry::networkRecipe)) {
            var primary=items(entry.primary());
            var secondary=entry.secondary().map(MachineAutomationRecipes::items).orElse(List.of(""));
            var tertiary=entry.tertiary().map(MachineAutomationRecipes::items).orElse(List.of(""));
            var outputs=new TreeMap<Resource,Long>();
            boolean plain=true;
            for(var output:entry.results()){
                if(!output.getComponentsPatch().isEmpty()){plain=false;break;}
                outputs.merge(Resource.item(BuiltInRegistries.ITEM.getKey(output.getItem()).toString()),(long)output.getCount(),Math::addExact);
            }
            if(!plain)continue;
            for(var a:primary)for(var b:secondary)for(var c:tertiary) {
                if(result.size()>=AutomationRecipeCatalogue.MAX_RECIPES)throw new IllegalArgumentException("Preparation variants exceed automation catalogue limit");
                var inputs=new TreeMap<Resource,Long>();inputs.put(Resource.item(a),(long)entry.primaryCount());
                if(!b.isEmpty())inputs.merge(Resource.item(b),(long)entry.secondaryCount(),Math::addExact);
                if(!c.isEmpty())inputs.merge(Resource.item(c),(long)entry.tertiaryCount(),Math::addExact);
                var base=AutomationRecipeCatalogue.id("prepare",entry.id().toString());
                // IDs remain stable under tag reordering or insertion of another variant.
                String identity=a+'\n'+b+'\n'+c+'\n'+entry.primaryCount()+":"+entry.secondaryCount()+":"+entry.tertiaryCount()+":"+entry.process()+":"+entry.minimumTier()+":"+entry.minPrecision()+":"+entry.steps();
                var fingerprint=UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                String worker=entry.process().equals("sintering")?MachineKind.SINTER_FURNACE.id():entry.minimumTier()==2?MachineKind.INDUSTRIAL_PREPARATOR.id():MachineKind.PREPARATOR.id();
                result.add(AutomationRecipeCatalogue.recipe(base+"/"+fingerprint,worker,inputs,outputs,entry.energy(),entry.ticks()));
            }
        }
        return AutomationRecipeCatalogue.checked(result);
    }
    private static List<String> items(Ingredient ingredient) {
        return ingredient.items().filter(holder->ingredient.test(new net.minecraft.world.item.ItemStack(holder))).map(holder->BuiltInRegistries.ITEM.getKey(holder.value()).toString()).distinct().sorted().toList();
    }
    public static boolean supports(MachineBlockEntity machine,Recipe recipe) {
        if(!supports(machine.kind(),recipe)||machine.crystallizationTier()<recipe.requiredTier()||!machine.progressAllows(recipe.requiresAdvancement()))return false;
        if(machine.kind().synthesis())return recipe.outputs().keySet().stream()
                .filter(resource->resource.kind()==Kind.ITEM||resource.kind()==Kind.FLUID).allMatch(resource->machine.researchAllows(resource.id()));
        if(recipe.id().startsWith("rematter:automation/process/")){
            return ReMatter.chemistry().processes().stream().filter(process->AutomationRecipeCatalogue.id("process",process.id().toString()).equals(recipe.id()))
                    .anyMatch(process->MachineRouting.supports(machine.kind(),process)&&machine.progressAllows(process.requiresAdvancement())&&machine.allowsProcess(process));
        }
        if(recipe.id().startsWith("rematter:automation/prepare/")){
            if(!(machine.getLevel() instanceof ServerLevel server))return false;
            return PreparationRecipes.serverRecipes(server).stream().filter(entry->{
                String prefix=AutomationRecipeCatalogue.id("prepare",entry.id().toString())+"/";
                return recipe.id().startsWith(prefix)&&recipe.id().substring(prefix.length()).indexOf('/')<0;
            })
                    .filter(PreparationRecipes.Entry::networkRecipe).anyMatch(entry->machine.preparationPermits(entry.recipe()));
        }
        return true;
    }
    public static boolean supports(MachineKind kind,Recipe recipe) {
        return kind.id().equals(recipe.workerKind()) || kind==MachineKind.INDUSTRIAL_REACTOR&&recipe.workerKind().equals(MachineKind.REACTOR.id())
                || kind==MachineKind.INDUSTRIAL_PREPARATOR&&recipe.workerKind().equals(MachineKind.PREPARATOR.id());
    }
    /** Per-recipe hardware room for simultaneous network jobs, in addition to height and upgrades. */
    static int capacity(MachineBlockEntity machine,Recipe recipe,int requested) {
        if(requested<1)return 0;
        if(machine.kind()==MachineKind.INDUSTRIAL_PREPARATOR&&recipe.id().startsWith("rematter:automation/prepare/")){
            if(!(machine.getLevel() instanceof ServerLevel server))return 0;
            return PreparationRecipes.serverRecipes(server).stream().filter(entry->{
                String prefix=AutomationRecipeCatalogue.id("prepare",entry.id().toString())+"/";
                return recipe.id().startsWith(prefix)&&recipe.id().substring(prefix.length()).indexOf('/')<0;
            }).filter(entry->machine.preparationPermits(entry.recipe())).mapToInt(entry->Math.min(requested,
                    IndustrialStructure.inspect(machine).performance().jobCapacity(entry.steps()))).max().orElse(0);
        }
        return requested;
    }
}
