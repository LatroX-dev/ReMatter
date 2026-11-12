package dev.latrox.rematter.content.preparation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Counted physical ingredients and up to three plain outputs form one indivisible batch. */
public final class PreparationRecipe implements Recipe<PreparationInput> {
    public static final String PREPARATION="preparation", SINTERING="sintering";
    private record Outputs(List<ItemStackTemplate> values) {
        private static final MapCodec<Outputs> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
                ItemStackTemplate.MAP_CODEC.codec().optionalFieldOf("result").forGetter(o->o.values.size()==1?Optional.of(o.values.getFirst()):Optional.empty()),
                ItemStackTemplate.MAP_CODEC.codec().listOf().optionalFieldOf("results").forGetter(o->o.values.size()>1?Optional.of(o.values):Optional.empty())
        ).apply(instance,(single,multiple)->{
            if(single.isPresent()==multiple.isPresent())throw new IllegalArgumentException("Specify exactly one of result or results");
            return new Outputs(single.map(List::of).orElseGet(multiple::orElseThrow));
        }));
        private Outputs { values=List.copyOf(values);if(values.isEmpty()||values.size()>3)throw new IllegalArgumentException("Preparation needs one to three outputs"); }
    }
    public static final MapCodec<PreparationRecipe> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
            Ingredient.CODEC.fieldOf("primary").forGetter(PreparationRecipe::primary),
            Ingredient.CODEC.optionalFieldOf("secondary").forGetter(PreparationRecipe::secondary),
            Ingredient.CODEC.optionalFieldOf("tertiary").forGetter(PreparationRecipe::tertiary),
            Codec.intRange(1,64).optionalFieldOf("primary_count",1).forGetter(PreparationRecipe::primaryCount),
            Codec.intRange(1,64).optionalFieldOf("secondary_count",1).forGetter(PreparationRecipe::secondaryCount),
            Codec.intRange(1,64).optionalFieldOf("tertiary_count",1).forGetter(PreparationRecipe::tertiaryCount),
            Outputs.CODEC.forGetter(recipe->recipe.outputs),
            Codec.intRange(1,PreparationWork.MAX_ENERGY).fieldOf("energy").forGetter(PreparationRecipe::energy),
            Codec.intRange(3,PreparationWork.MAX_TICKS).fieldOf("ticks").forGetter(PreparationRecipe::ticks),
            Codec.intRange(0,2).optionalFieldOf("minimum_tier",0).forGetter(PreparationRecipe::minimumTier),
            Codec.STRING.optionalFieldOf("process",PREPARATION).forGetter(PreparationRecipe::process),
            PreparationNetworkPolicy.CODEC.forGetter(PreparationRecipe::networkRecipe),
            Codec.intRange(0,98).optionalFieldOf("min_precision",0).forGetter(PreparationRecipe::minPrecision),
            PreparationStep.CODEC.listOf().optionalFieldOf("steps").forGetter(recipe->Optional.of(List.copyOf(recipe.steps)))
    ).apply(instance,PreparationRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,PreparationRecipe> STREAM_CODEC=new StreamCodec<>() {
        public PreparationRecipe decode(RegistryFriendlyByteBuf b) {
            var primary=Ingredient.CONTENTS_STREAM_CODEC.decode(b);
            var secondary=Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.decode(b);var tertiary=Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.decode(b);
            int pc=b.readVarInt(),sc=b.readVarInt(),tc=b.readVarInt(),count=b.readVarInt();
            if(count<1||count>3)throw new IllegalArgumentException("Invalid preparation output count");
            var results=new ArrayList<ItemStackTemplate>();
            for(int i=0;i<count;i++)results.add(ItemStackTemplate.fromNonEmptyStack(ItemStack.STREAM_CODEC.decode(b)));
            int energy=b.readVarInt(),ticks=b.readVarInt(),tier=b.readVarInt();String process=b.readUtf(16);boolean network=b.readBoolean();
            int precision=b.readVarInt(),stepCount=b.readVarInt();
            if(stepCount<1||stepCount>3)throw new IllegalArgumentException("Invalid preparation step count");
            var steps=new ArrayList<PreparationStep>();for(int i=0;i<stepCount;i++)steps.add(PreparationStep.valueOf(b.readUtf(16)));
            return new PreparationRecipe(primary,secondary,tertiary,pc,sc,tc,new Outputs(results),energy,ticks,tier,process,network,precision,Optional.of(steps));
        }
        public void encode(RegistryFriendlyByteBuf b,PreparationRecipe r) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(b,r.primary);Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(b,r.secondary);Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(b,r.tertiary);
            b.writeVarInt(r.primaryCount);b.writeVarInt(r.secondaryCount);b.writeVarInt(r.tertiaryCount);b.writeVarInt(r.outputs.values.size());
            for(var result:r.results())ItemStack.STREAM_CODEC.encode(b,result);
            b.writeVarInt(r.energy);b.writeVarInt(r.ticks);b.writeVarInt(r.minimumTier);b.writeUtf(r.process,16);b.writeBoolean(r.networkRecipe);b.writeVarInt(r.minPrecision);
            b.writeVarInt(r.steps.size());for(var step:r.steps)b.writeUtf(step.name(),16);
        }
    };
    private final Ingredient primary;
    private final Optional<Ingredient> secondary,tertiary;
    private final Outputs outputs;
    private final int primaryCount,secondaryCount,tertiaryCount,energy,ticks,minimumTier,minPrecision;
    private final String process;
    private final boolean networkRecipe;
    private final Set<PreparationStep> steps;
    public PreparationRecipe(Ingredient primary,Optional<Ingredient> secondary,ItemStack result,int energy,int ticks,int minimumTier) {
        this(primary,secondary,Optional.empty(),1,1,1,result,energy,ticks,minimumTier,PREPARATION);
    }
    public PreparationRecipe(Ingredient primary,Optional<Ingredient> secondary,Optional<Ingredient> tertiary,int primaryCount,int secondaryCount,int tertiaryCount,ItemStack result,int energy,int ticks,int minimumTier,String process) {
        this(primary,secondary,tertiary,primaryCount,secondaryCount,tertiaryCount,result,energy,ticks,minimumTier,process,true);
    }
    public PreparationRecipe(Ingredient primary,Optional<Ingredient> secondary,Optional<Ingredient> tertiary,int primaryCount,int secondaryCount,int tertiaryCount,ItemStack result,int energy,int ticks,int minimumTier,String process,boolean networkRecipe) {
        this(primary,secondary,tertiary,primaryCount,secondaryCount,tertiaryCount,List.of(result),energy,ticks,minimumTier,process,networkRecipe,0,Set.of());
    }
    public PreparationRecipe(Ingredient primary,Optional<Ingredient> secondary,Optional<Ingredient> tertiary,int primaryCount,int secondaryCount,int tertiaryCount,List<ItemStack> results,int energy,int ticks,int minimumTier,String process,boolean networkRecipe,int minPrecision,Set<PreparationStep> steps) {
        this(primary,secondary,tertiary,primaryCount,secondaryCount,tertiaryCount,new Outputs(results.stream().map(ItemStackTemplate::fromNonEmptyStack).toList()),energy,ticks,minimumTier,process,networkRecipe,minPrecision,steps.isEmpty()?Optional.empty():Optional.of(List.copyOf(steps)));
        for(var result:results)PreparationWork.validate(result.getCount(),result.getMaxStackSize(),energy,ticks);
    }
    private PreparationRecipe(Ingredient primary,Optional<Ingredient> secondary,Optional<Ingredient> tertiary,int primaryCount,int secondaryCount,int tertiaryCount,Outputs outputs,int energy,int ticks,int minimumTier,String process,boolean networkRecipe,int minPrecision,Optional<List<PreparationStep>> steps) {
        this.primary=Objects.requireNonNull(primary);this.secondary=Objects.requireNonNull(secondary);this.tertiary=Objects.requireNonNull(tertiary);
        this.outputs=Objects.requireNonNull(outputs);this.process=Objects.requireNonNull(process);
        PreparationWork.validateInputs(primaryCount,secondaryCount,tertiaryCount,secondary.isPresent(),tertiary.isPresent(),process);
        for(var result:outputs.values){PreparationWork.validate(result.count(),64,energy,ticks);if(!result.components().isEmpty())throw new IllegalArgumentException("Preparation output must be plain");}
        if(minimumTier<0||minimumTier>2||minPrecision<0||minPrecision>98)throw new IllegalArgumentException("Unsupported preparation requirements");
        if((minPrecision>0||outputs.values.size()>1)&&minimumTier!=2)throw new IllegalArgumentException("Precision and multiple results require the industrial tier");
        if(outputs.values.size()>1&&(secondary.isPresent()||tertiary.isPresent()))throw new IllegalArgumentException("Reclaim results require single-input recipe roles");
        var declared=steps.orElseGet(()->List.of(secondary.isPresent()?PreparationStep.MIX:PreparationStep.GRIND));
        if(declared.isEmpty()||declared.size()>3||new HashSet<>(declared).size()!=declared.size())throw new IllegalArgumentException("Invalid preparation steps");
        if(declared.contains(PreparationStep.SEPARATE)&&minimumTier!=2)throw new IllegalArgumentException("Separation requires industrial preparation");
        this.steps=Collections.unmodifiableSet(EnumSet.copyOf(declared));
        this.primaryCount=primaryCount;this.secondaryCount=secondaryCount;this.tertiaryCount=tertiaryCount;this.energy=energy;this.ticks=ticks;this.minimumTier=minimumTier;this.minPrecision=minPrecision;this.networkRecipe=networkRecipe;
    }
    public Item outputItem(){return outputs.values.getFirst().item().value();}
    public void validateOutput(DataComponentMap defaults) {
        var first=outputs.values.getFirst();PreparationWork.validate(first.count(),new ItemStack(Holder.direct(outputItem(),defaults),first.count()).getMaxStackSize(),energy,ticks);
    }
    /** Every output must be checked against its own prospective defaults on reload. */
    public void validateOutputs(java.util.function.Function<Item,DataComponentMap> defaults) {
        for(var output:outputs.values){var item=output.item().value();PreparationWork.validate(output.count(),new ItemStack(Holder.direct(item,defaults.apply(item)),output.count()).getMaxStackSize(),energy,ticks);}
    }
    public Ingredient primary(){return primary;}public Optional<Ingredient> secondary(){return secondary;}public Optional<Ingredient> tertiary(){return tertiary;}
    public int primaryCount(){return primaryCount;}public int secondaryCount(){return secondaryCount;}public int tertiaryCount(){return tertiaryCount;}
    public ItemStack result(){return results().getFirst();}
    public List<ItemStack> results(){return outputs.values.stream().map(value->new ItemStack(value.item(),value.count())).toList();}
    public List<ItemStack> additionalResults(){return results().subList(1,outputs.values.size());}
    public int energy(){return energy;}public int ticks(){return ticks;}public int minimumTier(){return minimumTier;}public int minPrecision(){return minPrecision;}
    public String process(){return process;}public boolean networkRecipe(){return networkRecipe;}public Set<PreparationStep> steps(){return steps;}
    public boolean acceptsPrimary(ItemStack stack){return plain(stack)&&primary.test(stack);}
    public boolean acceptsSecondary(ItemStack stack){return plain(stack)&&secondary.filter(i->i.test(stack)).isPresent();}
    public boolean acceptsTertiary(ItemStack stack){return plain(stack)&&tertiary.filter(i->i.test(stack)).isPresent();}
    public boolean acceptsInsertion(PreparationInput input,int slot,ItemStack candidate) {
        if(slot!=0&&slot!=3&&slot!=4||!plain(candidate))return false;
        var a=slot==0?candidate:input.primary();var b=slot==3?candidate:input.secondary();var c=slot==4?candidate:input.tertiary();
        return(a.isEmpty()||acceptsPrimary(a))&&(b.isEmpty()||acceptsSecondary(b))&&(c.isEmpty()||acceptsTertiary(c));
    }
    public boolean singleInput(){return secondary.isEmpty()&&tertiary.isEmpty();}
    private static boolean plain(ItemStack stack){return !stack.isEmpty()&&stack.getComponentsPatch().isEmpty();}
    @Override public boolean matches(PreparationInput input,Level level){return acceptsPrimary(input.primary())&&(secondary.isEmpty()?input.secondary().isEmpty():acceptsSecondary(input.secondary()))&&(tertiary.isEmpty()?input.tertiary().isEmpty():acceptsTertiary(input.tertiary()))&&PreparationWork.hasRequiredCounts(input.primary().getCount(),input.secondary().getCount(),input.tertiary().getCount(),primaryCount,secondary.isPresent()?secondaryCount:0,tertiary.isPresent()?tertiaryCount:0);}
    @Override public ItemStack assemble(PreparationInput input){return result();}
    @Override public boolean isSpecial(){return true;}@Override public boolean showNotification(){return false;}@Override public String group(){return "";}
    @Override public RecipeSerializer<PreparationRecipe> getSerializer(){return PreparationRecipes.SERIALIZER.get();}@Override public RecipeType<PreparationRecipe> getType(){return PreparationRecipes.TYPE.get();}
    @Override public PlacementInfo placementInfo(){return PlacementInfo.NOT_PLACEABLE;}@Override public RecipeBookCategory recipeBookCategory(){return RecipeBookCategories.CRAFTING_MISC;}
}
