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
    public static final int MAX_RECIPES=256;
    private static final DeferredRegister<RecipeType<?>> TYPES=DeferredRegister.create(Registries.RECIPE_TYPE,ReMatter.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,ReMatter.MOD_ID);
    public static final Supplier<RecipeType<PreparationRecipe>> TYPE=TYPES.register("preparation",
            ()->RecipeType.simple(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID,"preparation")));
    public static final Supplier<RecipeSerializer<PreparationRecipe>> SERIALIZER=SERIALIZERS.register("preparation",
            ()->new RecipeSerializer<>(PreparationRecipe.CODEC,PreparationRecipe.STREAM_CODEC));
    private static final Map<RecipeMap,List<Entry>> SERVER_CACHE=new WeakHashMap<>();
    private static volatile List<Entry> client=List.of();
    private static final List<Consumer<List<Entry>>> LISTENERS=new CopyOnWriteArrayList<>();
    private PreparationRecipes(){}
    public static void register(IEventBus bus) {
        TYPES.register(bus);SERIALIZERS.register(bus);
        NeoForge.EVENT_BUS.addListener(PreparationCatalog::sync);
    }
    public static List<Entry> serverRecipes(ServerLevel level) {
        var manager=level.getServer().getRecipeManager();
        synchronized(SERVER_CACHE){return SERVER_CACHE.computeIfAbsent(manager.recipeMap(),ignored->{
            var entries=new ArrayList<Entry>();
            for(var holder:manager.getRecipes())if(holder.value() instanceof PreparationRecipe recipe)entries.add(new Entry(holder.id().identifier(),recipe));
            entries.sort(Comparator.comparing(e->e.id().toString()));
            return checked(entries);
        });}
    }
    public static List<Entry> clientRecipes(){return client;}
    public static void addClientListener(Consumer<List<Entry>> listener){LISTENERS.add(Objects.requireNonNull(listener));}
    public static void removeClientListener(Consumer<List<Entry>> listener){LISTENERS.remove(listener);}
    public static void clearClient(){acceptClient(List.of());}
    static void acceptClient(List<Entry> entries){client=checked(entries);LISTENERS.forEach(listener->listener.accept(client));}
    static List<Entry> checked(List<Entry> entries) {
        if(entries.size()>MAX_RECIPES)throw new IllegalArgumentException("Too many preparation recipes");
        var seen=new HashSet<Identifier>();
        for(var entry:entries)if(entry.id().toString().length()>512||!seen.add(entry.id()))throw new IllegalArgumentException("Invalid preparation catalogue identity");
        return List.copyOf(entries);
    }
    public record Entry(Identifier id,PreparationRecipe recipe) {
        public Entry{Objects.requireNonNull(id);Objects.requireNonNull(recipe);}
        public Ingredient primary(){return recipe.primary();} public Optional<Ingredient> secondary(){return recipe.secondary();}
        public ItemStack result(){return recipe.result();} public int energy(){return recipe.energy();} public int ticks(){return recipe.ticks();}
        public int minimumTier(){return recipe.minimumTier();} public String process(){return recipe.process();}
        public boolean networkRecipe(){return recipe.networkRecipe();}
        public Optional<Ingredient> tertiary(){return recipe.tertiary();}
        public int primaryCount(){return recipe.primaryCount();} public int secondaryCount(){return recipe.secondaryCount();} public int tertiaryCount(){return recipe.tertiaryCount();}
    }
}
