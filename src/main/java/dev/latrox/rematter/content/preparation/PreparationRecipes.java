package dev.latrox.rematter.content.preparation;

import dev.latrox.rematter.ReMatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PreparationRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES=DeferredRegister.create(Registries.RECIPE_TYPE,ReMatter.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,ReMatter.MOD_ID);
    public static final Supplier<RecipeType<PreparationRecipe>> TYPE=TYPES.register("preparation",
            ()->RecipeType.simple(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"preparation")));
    public static final Supplier<RecipeSerializer<PreparationRecipe>> SERIALIZER=SERIALIZERS.register("preparation",
            ()->new RecipeSerializer<>(PreparationRecipe.CODEC,PreparationRecipe.STREAM_CODEC));
    private static final Map<RecipeMap,Catalogue> SERVER_CACHE=new WeakHashMap<>();
    private static volatile List<Entry> client=List.of();
    private static final List<Consumer<List<Entry>>> LISTENERS=new CopyOnWriteArrayList<>();
    private PreparationRecipes(){}
    public static void register(IEventBus bus) {
        TYPES.register(bus);SERIALIZERS.register(bus);
        dev.latrox.rematter.content.EmptyMachineIngredient.register(bus);
        NeoForge.EVENT_BUS.addListener(PreparationCatalog::sync);
    }
    /** The validated server catalogue. Invalid data is logged once per recipe reload and never throws on a lookup. */
    public static Catalogue serverCatalogue(ServerLevel level) {
        var manager=level.getServer().getRecipeManager();
        synchronized(SERVER_CACHE){return SERVER_CACHE.computeIfAbsent(manager.recipeMap(),ignored->{
            var entries=new ArrayList<Entry>();
            for(var holder:manager.getRecipes())if(holder.value() instanceof PreparationRecipe recipe)entries.add(new Entry(holder.id().identifier(),recipe));
            entries.sort(Comparator.comparing(e->e.id().toString()));
            if(PreparationWork.validCatalogue(entries.stream().map(Entry::id).toList()))return new Catalogue(List.copyOf(entries),true);
            ReMatter.LOGGER.error("Rejected {} preparation recipes: at most {} recipes with unique identifiers are supported",entries.size(),PreparationWork.MAX_RECIPES);
            return Catalogue.INVALID;
        });}
    }
    /** Strict access for exports that report an invalid catalogue themselves. */
    public static List<Entry> serverRecipes(ServerLevel level) {
        var catalogue=serverCatalogue(level);
        if(!catalogue.valid())throw new IllegalArgumentException("Invalid preparation catalogue");
        return catalogue.entries();
    }
    public static List<Entry> clientRecipes(){return client;}
    public static void addClientListener(Consumer<List<Entry>> listener){LISTENERS.add(Objects.requireNonNull(listener));}
    public static void removeClientListener(Consumer<List<Entry>> listener){LISTENERS.remove(listener);}
    public static void clearClient(){acceptClient(List.of());}
    static void acceptClient(List<Entry> entries){
        boolean valid=PreparationWork.validCatalogue(entries.stream().map(Entry::id).toList());
        if(!valid)ReMatter.LOGGER.error("Ignored an invalid preparation catalogue with {} recipes",entries.size());
        client=valid?List.copyOf(entries):List.of();
        LISTENERS.forEach(listener->listener.accept(client));
    }
    public record Catalogue(List<Entry> entries,boolean valid) {
        static final Catalogue INVALID=new Catalogue(List.of(),false);
        public Catalogue{entries=List.copyOf(entries);}
    }
    public record Entry(Identifier id,PreparationRecipe recipe) {
        public Entry{Objects.requireNonNull(id);Objects.requireNonNull(recipe);}
        public Ingredient primary(){return recipe.primary();} public Optional<Ingredient> secondary(){return recipe.secondary();}
        public ItemStack result(){return recipe.result();} public int energy(){return recipe.energy();} public int ticks(){return recipe.ticks();}
        public int minimumTier(){return recipe.minimumTier();} public String process(){return recipe.process();}
        public int minPrecision(){return recipe.minPrecision();}
        public java.util.Set<PreparationStep> steps(){return recipe.steps();}
        public List<ItemStack> results(){return recipe.results();}
        public boolean networkRecipe(){return recipe.networkRecipe();}
        public Optional<Ingredient> tertiary(){return recipe.tertiary();}
        public int primaryCount(){return recipe.primaryCount();} public int secondaryCount(){return recipe.secondaryCount();} public int tertiaryCount(){return recipe.tertiaryCount();}
    }
}
