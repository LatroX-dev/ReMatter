package dev.latrox.rematter.content.preparation;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Counted physical ingredients become one physical batch; inputs remain in fixed roles. */
public final class PreparationRecipe implements Recipe<PreparationInput> {
    public static final String PREPARATION = "preparation", SINTERING = "sintering";
    public static final MapCodec<PreparationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.fieldOf("primary").forGetter(PreparationRecipe::primary),
            Ingredient.CODEC.optionalFieldOf("secondary").forGetter(PreparationRecipe::secondary),
            Ingredient.CODEC.optionalFieldOf("tertiary").forGetter(PreparationRecipe::tertiary),
            Codec.intRange(1,64).optionalFieldOf("primary_count",1).forGetter(PreparationRecipe::primaryCount),
            Codec.intRange(1,64).optionalFieldOf("secondary_count",1).forGetter(PreparationRecipe::secondaryCount),
            Codec.intRange(1,64).optionalFieldOf("tertiary_count",1).forGetter(PreparationRecipe::tertiaryCount),
            ItemStackTemplate.MAP_CODEC.codec().fieldOf("result").forGetter(recipe -> recipe.result),
            Codec.intRange(1,PreparationWork.MAX_ENERGY).fieldOf("energy").forGetter(PreparationRecipe::energy),
            Codec.intRange(3,PreparationWork.MAX_TICKS).fieldOf("ticks").forGetter(PreparationRecipe::ticks),
            Codec.intRange(0,1).optionalFieldOf("minimum_tier",0).forGetter(PreparationRecipe::minimumTier),
            Codec.STRING.optionalFieldOf("process",PREPARATION).forGetter(PreparationRecipe::process),
            PreparationNetworkPolicy.CODEC.forGetter(PreparationRecipe::networkRecipe)
    ).apply(instance,PreparationRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,PreparationRecipe> STREAM_CODEC = new StreamCodec<>() {
        public PreparationRecipe decode(RegistryFriendlyByteBuf buffer) {
            return new PreparationRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.decode(buffer),
                    Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.decode(buffer),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),
                    ItemStack.STREAM_CODEC.decode(buffer),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readUtf(16),buffer.readBoolean());
        }
        public void encode(RegistryFriendlyByteBuf buffer,PreparationRecipe recipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer,recipe.primary);Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(buffer,recipe.secondary);
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(buffer,recipe.tertiary);
            buffer.writeVarInt(recipe.primaryCount);buffer.writeVarInt(recipe.secondaryCount);buffer.writeVarInt(recipe.tertiaryCount);
            ItemStack.STREAM_CODEC.encode(buffer,recipe.result());buffer.writeVarInt(recipe.energy);buffer.writeVarInt(recipe.ticks);
            buffer.writeVarInt(recipe.minimumTier);buffer.writeUtf(recipe.process,16);buffer.writeBoolean(recipe.networkRecipe);
        }
    };
    private final Ingredient primary;
    private final Optional<Ingredient> secondary, tertiary;
    private final ItemStackTemplate result;
    private final int primaryCount, secondaryCount, tertiaryCount, energy, ticks, minimumTier;
    private final String process;
    private final boolean networkRecipe;
    public PreparationRecipe(Ingredient primary,Optional<Ingredient> secondary,ItemStack result,int energy,int ticks,int minimumTier) {
        this(primary,secondary,Optional.empty(),1,1,1,result,energy,ticks,minimumTier,PREPARATION);
    }
    public PreparationRecipe(Ingredient primary,Optional<Ingredient> secondary,Optional<Ingredient> tertiary,
                             int primaryCount,int secondaryCount,int tertiaryCount,ItemStack result,
                             int energy,int ticks,int minimumTier,String process) {
        this(primary,secondary,tertiary,primaryCount,secondaryCount,tertiaryCount,result,energy,ticks,minimumTier,process,true);
    }
    public PreparationRecipe(Ingredient primary,Optional<Ingredient> secondary,Optional<Ingredient> tertiary,
                             int primaryCount,int secondaryCount,int tertiaryCount,ItemStack result,
                             int energy,int ticks,int minimumTier,String process,boolean networkRecipe) {
        this(primary,secondary,tertiary,primaryCount,secondaryCount,tertiaryCount,
                checkedTemplate(result,energy,ticks),energy,ticks,minimumTier,process,networkRecipe);
    }
    private PreparationRecipe(Ingredient primary,Optional<Ingredient> secondary,Optional<Ingredient> tertiary,
                              int primaryCount,int secondaryCount,int tertiaryCount,ItemStackTemplate result,
                              int energy,int ticks,int minimumTier,String process,boolean networkRecipe) {
        this.primary=Objects.requireNonNull(primary);this.secondary=Objects.requireNonNull(secondary);this.tertiary=Objects.requireNonNull(tertiary);
        Objects.requireNonNull(result);Objects.requireNonNull(process);
        // Recipe JSON is decoded before the world binds item prototypes.
        PreparationWork.validate(result.count(),64,energy,ticks);
        PreparationWork.validateInputs(primaryCount,secondaryCount,tertiaryCount,secondary.isPresent(),tertiary.isPresent(),process);
        if(!result.components().isEmpty())throw new IllegalArgumentException("Preparation output must be a plain item stack");
        if(minimumTier<0||minimumTier>1)throw new IllegalArgumentException("Unsupported preparation tier");
        this.primaryCount=primaryCount;this.secondaryCount=secondaryCount;this.tertiaryCount=tertiaryCount;
        this.result=result;this.energy=energy;this.ticks=ticks;this.minimumTier=minimumTier;this.process=process;
        this.networkRecipe=networkRecipe;
    }
    private static ItemStackTemplate checkedTemplate(ItemStack result,int energy,int ticks) {
        Objects.requireNonNull(result);
        PreparationWork.validate(result.getCount(),result.getMaxStackSize(),energy,ticks);
        return ItemStackTemplate.fromNonEmptyStack(result);
    }
    public Item outputItem(){return result.item().value();}
    /** Checks the recipe against this reload's prospective item defaults. */
    public void validateOutput(DataComponentMap defaults) {
        var candidate = new ItemStack(Holder.direct(outputItem(), defaults), result.count());
        PreparationWork.validate(result.count(), candidate.getMaxStackSize(), energy, ticks);
    }
    public Ingredient primary(){return primary;} public Optional<Ingredient> secondary(){return secondary;} public Optional<Ingredient> tertiary(){return tertiary;}
    public int primaryCount(){return primaryCount;} public int secondaryCount(){return secondaryCount;} public int tertiaryCount(){return tertiaryCount;}
    public ItemStack result(){return new ItemStack(result.item(), result.count());} public int energy(){return energy;} public int ticks(){return ticks;}
    public int minimumTier(){return minimumTier;} public String process(){return process;}
    public boolean networkRecipe(){return networkRecipe;}
    public boolean acceptsPrimary(ItemStack stack){return plain(stack)&&primary.test(stack);}
    public boolean acceptsSecondary(ItemStack stack){return plain(stack)&&secondary.filter(ingredient->ingredient.test(stack)).isPresent();}
    public boolean acceptsTertiary(ItemStack stack){return plain(stack)&&tertiary.filter(ingredient->ingredient.test(stack)).isPresent();}
    /** Empty companion slots are allowed while assembling a recipe; occupied slots must agree on one recipe. */
    public boolean acceptsInsertion(PreparationInput input, int slot, ItemStack candidate) {
        if (slot != 0 && slot != 3 && slot != 4 || !plain(candidate)) return false;
        ItemStack first = slot == 0 ? candidate : input.primary();
        ItemStack second = slot == 3 ? candidate : input.secondary();
        ItemStack third = slot == 4 ? candidate : input.tertiary();
        return (first.isEmpty() || acceptsPrimary(first))
                && (second.isEmpty() || acceptsSecondary(second))
                && (third.isEmpty() || acceptsTertiary(third));
    }
    public boolean singleInput(){return secondary.isEmpty()&&tertiary.isEmpty();}
    private static boolean plain(ItemStack stack){return !stack.isEmpty()&&stack.getComponentsPatch().isEmpty();}
    @Override public boolean matches(PreparationInput input,Level level){return acceptsPrimary(input.primary())
            &&(secondary.isEmpty()?input.secondary().isEmpty():acceptsSecondary(input.secondary()))
            &&(tertiary.isEmpty()?input.tertiary().isEmpty():acceptsTertiary(input.tertiary()))
            &&PreparationWork.hasRequiredCounts(input.primary().getCount(),input.secondary().getCount(),input.tertiary().getCount(),
                    primaryCount,secondary.isPresent()?secondaryCount:0,tertiary.isPresent()?tertiaryCount:0);}
    @Override public ItemStack assemble(PreparationInput input){return result();}
    @Override public boolean isSpecial(){return true;}
    @Override public boolean showNotification(){return false;}
    @Override public String group(){return "";}
    @Override public RecipeSerializer<PreparationRecipe> getSerializer(){return PreparationRecipes.SERIALIZER.get();}
    @Override public RecipeType<PreparationRecipe> getType(){return PreparationRecipes.TYPE.get();}
    @Override public PlacementInfo placementInfo(){return PlacementInfo.NOT_PLACEABLE;}
    @Override public RecipeBookCategory recipeBookCategory(){return RecipeBookCategories.CRAFTING_MISC;}
}
