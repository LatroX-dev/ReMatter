package dev.latrox.rematter.content.preparation;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Plain physical ingredients become a physical material batch; no virtual matter or cell is involved. */
public final class PreparationRecipe implements Recipe<PreparationInput> {
    public static final MapCodec<PreparationRecipe> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
            Ingredient.CODEC.fieldOf("primary").forGetter(PreparationRecipe::primary),
            Ingredient.CODEC.optionalFieldOf("secondary").forGetter(PreparationRecipe::secondary),
            ItemStack.CODEC.fieldOf("result").forGetter(PreparationRecipe::result),
            Codec.intRange(1,PreparationWork.MAX_ENERGY).fieldOf("energy").forGetter(PreparationRecipe::energy),
            Codec.intRange(3,PreparationWork.MAX_TICKS).fieldOf("ticks").forGetter(PreparationRecipe::ticks),
            Codec.intRange(0,1).optionalFieldOf("minimum_tier",0).forGetter(PreparationRecipe::minimumTier)
    ).apply(instance,PreparationRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,PreparationRecipe> STREAM_CODEC=new StreamCodec<>() {
        public PreparationRecipe decode(RegistryFriendlyByteBuf buffer) {
            return new PreparationRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.decode(buffer),
                    ItemStack.STREAM_CODEC.decode(buffer),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt());
        }
        public void encode(RegistryFriendlyByteBuf buffer,PreparationRecipe recipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer,recipe.primary);Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(buffer,recipe.secondary);
            ItemStack.STREAM_CODEC.encode(buffer,recipe.result);buffer.writeVarInt(recipe.energy);buffer.writeVarInt(recipe.ticks);buffer.writeVarInt(recipe.minimumTier);
        }
    };
    private final Ingredient primary;
    private final Optional<Ingredient> secondary;
    private final ItemStack result;
    private final int energy,ticks,minimumTier;
    public PreparationRecipe(Ingredient primary,Optional<Ingredient> secondary,ItemStack result,int energy,int ticks,int minimumTier) {
        this.primary=Objects.requireNonNull(primary);this.secondary=Objects.requireNonNull(secondary);
        Objects.requireNonNull(result);
        PreparationWork.validate(result.getCount(),result.getMaxStackSize(),energy,ticks);
        if(result.isEmpty()||!result.getComponentsPatch().isEmpty())throw new IllegalArgumentException("Preparation output must be a plain item stack");
        if(minimumTier<0||minimumTier>1)throw new IllegalArgumentException("Unsupported preparation tier");
        this.result=result.copy();this.energy=energy;this.ticks=ticks;this.minimumTier=minimumTier;
    }
    public Ingredient primary(){return primary;} public Optional<Ingredient> secondary(){return secondary;}
    public ItemStack result(){return result.copy();} public int energy(){return energy;} public int ticks(){return ticks;}
    public int minimumTier(){return minimumTier;}
    public boolean acceptsPrimary(ItemStack stack){return plain(stack)&&primary.test(stack);}
    public boolean acceptsSecondary(ItemStack stack){return plain(stack)&&secondary.filter(ingredient->ingredient.test(stack)).isPresent();}
    public boolean singleInput(){return secondary.isEmpty();}
    private static boolean plain(ItemStack stack){return !stack.isEmpty()&&stack.getComponentsPatch().isEmpty();}
    @Override public boolean matches(PreparationInput input,Level level){return acceptsPrimary(input.primary())
            &&(singleInput()?input.secondary().isEmpty():acceptsSecondary(input.secondary()));}
    @Override public ItemStack assemble(PreparationInput input){return result();}
    @Override public boolean isSpecial(){return true;}
    @Override public boolean showNotification(){return false;}
    @Override public String group(){return "";}
    @Override public RecipeSerializer<PreparationRecipe> getSerializer(){return PreparationRecipes.SERIALIZER.get();}
    @Override public RecipeType<PreparationRecipe> getType(){return PreparationRecipes.TYPE.get();}
    @Override public PlacementInfo placementInfo(){return PlacementInfo.NOT_PLACEABLE;}
    @Override public RecipeBookCategory recipeBookCategory(){return RecipeBookCategories.CRAFTING_MISC;}
}
