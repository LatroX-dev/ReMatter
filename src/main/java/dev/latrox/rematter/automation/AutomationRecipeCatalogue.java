package dev.latrox.rematter.automation;

import dev.latrox.rematter.chemistry.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Predicate;
import static dev.latrox.rematter.automation.AutomationPort.*;

/** The same explicit profiles and processes used by standalone machines; never inferred crafting. */
public final class AutomationRecipeCatalogue {
    public static final int MAX_RECIPES = 131_072;
    private AutomationRecipeCatalogue() {}
    public static List<Recipe> chemistry(ChemistryRegistry registry, Predicate<String> itemExists, Predicate<String> fluidExists) {
        var result = new ArrayList<Recipe>();
        for (var p : registry.profiles()) {
            if (!itemExists.test(p.itemId())) continue;
            var item = Map.of(Resource.item(p.itemId()), (long)p.itemCount());
            var matter = matter(p.matter());
            result.add(recipe(id("decompose_item",p.itemId()),"decomposer",item,matter,p.decompositionEnergy(),p.ticks()));
            if (registry.synthesisPolicy().allowsItem(p.itemId())) result.add(recipe(id("synthesize_item",p.itemId()),
                    registry.synthesisPolicy().requirement(p.itemId()).machine(),matter,item,p.synthesisEnergy(),p.ticks(),registry.synthesisPolicy().requirement(p.itemId()).advancement(),registry.synthesisPolicy().requirement(p.itemId()).tier()));
        }
        for (var p : registry.fluidProfiles()) {
            if (!fluidExists.test(p.fluidId())) continue;
            var fluid = Map.of(Resource.fluid(p.fluidId()), (long)p.amount());
            var matter = matter(p.matter());
            result.add(recipe(id("decompose_fluid",p.fluidId()),"decomposer",fluid,matter,p.decompositionEnergy(),p.ticks()));
            if (registry.synthesisPolicy().allowsFluid(p.fluidId())) result.add(recipe(id("synthesize_fluid",p.fluidId()),
                    registry.synthesisPolicy().requirement(p.fluidId()).machine(),matter,fluid,p.synthesisEnergy(),p.ticks(),registry.synthesisPolicy().requirement(p.fluidId()).advancement(),registry.synthesisPolicy().requirement(p.fluidId()).tier()));
        }
        for (var p : registry.processes()) result.add(recipe(id("process",p.id().toString()),
                dev.latrox.rematter.content.MachineRouting.primary(p.type()).id(),matter(p.inputs()),matter(p.outputs()),p.energy(),p.ticks(),p.requiresAdvancement(),1));
        return checked(result);
    }
    public static Map<Resource,Long> matter(Map<SubstanceKey,Long> values) {
        var result = new TreeMap<Resource,Long>(); values.forEach((key,amount)->result.put(Resource.matter(key),amount));
        return ResourceAmounts.copy(result);
    }
    public static String id(String operation,String resource) {
        var key=SubstanceId.parse(resource);return "rematter:automation/"+operation+"/"+key.namespace()+"/"+key.path();
    }
    public static Recipe recipe(String id,String worker,Map<Resource,Long> inputs,Map<Resource,Long> outputs,long energy,int ticks) {
        return recipe(id,worker,inputs,outputs,energy,ticks,"",1);
    }
    public static Recipe recipe(String id,String worker,Map<Resource,Long> inputs,Map<Resource,Long> outputs,long energy,int ticks,String advancement,int tier) {
        String canonical=id+'\n'+worker+'\n'+new TreeMap<>(inputs)+'\n'+new TreeMap<>(outputs)+'\n'+energy+'\n'+ticks+'\n'+advancement+'\n'+tier;
        try {return new Recipe(id,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))),
                worker,inputs,outputs,energy,ticks,advancement,tier);
        } catch (NoSuchAlgorithmException impossible) {throw new AssertionError(impossible);}
    }
    public static List<Recipe> checked(Collection<Recipe> recipes) {
        if(recipes.size()>MAX_RECIPES)throw new IllegalArgumentException("Automation catalogue limit exceeded");
        var result=new TreeMap<String,Recipe>();
        for(var recipe:recipes)if(result.putIfAbsent(recipe.id(),recipe)!=null)throw new IllegalArgumentException("Duplicate automation recipe");
        return List.copyOf(result.values());
    }
}
