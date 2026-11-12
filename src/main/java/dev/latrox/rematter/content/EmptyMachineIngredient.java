package dev.latrox.rematter.content;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Rejects filled, foreign, unreadable and component-bearing portable Hand Preparators. */
public record EmptyMachineIngredient() implements ICustomIngredient {
    public static final MapCodec<EmptyMachineIngredient> CODEC=MapCodec.unit(new EmptyMachineIngredient());
    private static final DeferredRegister<IngredientType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES,"rematter");
    public static final Supplier<IngredientType<EmptyMachineIngredient>> TYPE=TYPES.register("empty_machine",()->new IngredientType<>(CODEC));
    private static final Identifier ITEM=Identifier.parse("rematter:hand_preparator");
    private static final Set<String> FIELDS=Set.of("schema","owner","automation","network_jobs","running","repeat","queued_batches","selection","target","target_substance","target_phase","vented_gases","vented_total","items","power","tank","ports");
    public static void register(IEventBus bus){TYPES.register(bus);}
    @Override public boolean test(ItemStack stack) {
        if(stack.isEmpty()||!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ITEM))return false;
        var data=stack.get(DataComponents.BLOCK_ENTITY_DATA);
        var plain=stack.copy();plain.remove(DataComponents.BLOCK_ENTITY_DATA);
        if(!plain.getComponentsPatch().isEmpty())return false;
        if(data==null)return true;
        return data.type()==ModContent.MACHINE_ENTITY.get()&&emptyData(data.copyTagWithoutId(),MachineSchema.VERSION,MachineCellAccess.inventorySize(MachineKind.HAND_PREPARATOR));
    }
    /** Empty slots have exactly the empty optional-stack encoding; unknown fields are preserved by refusal. */
    public static boolean emptyData(CompoundTag tag,int schema,int slots) {
        if(!FIELDS.containsAll(tag.keySet())||tag.getIntOr("schema",-1)!=schema)return false;
        var items=tag.getList("items").orElse(null);
        if(items==null||items.size()!=slots)return false;
        for(var item:items)if(!(item instanceof CompoundTag compound)||!compound.isEmpty())return false;
        var power=tag.getCompound("power").orElse(null);
        if(power==null||!power.keySet().equals(Set.of("energy"))||power.getInt("energy").orElse(-1)!=0)return false;
        var tank=tag.getCompound("tank").orElse(null);
        if(tank==null||!tank.keySet().equals(Set.of("stacks")))return false;
        var fluids=tank.getList("stacks").orElse(null);
        return fluids!=null&&fluids.size()==1&&fluids.getFirst() instanceof CompoundTag fluid&&fluid.isEmpty();
    }
    @Override public Stream<Holder<Item>> items(){return BuiltInRegistries.ITEM.get(ITEM).map(holder->Stream.<Holder<Item>>of(holder)).orElseGet(Stream::empty);}
    @Override public boolean isSimple(){return false;}
    @Override public IngredientType<?> getType(){return TYPE.get();}
}
