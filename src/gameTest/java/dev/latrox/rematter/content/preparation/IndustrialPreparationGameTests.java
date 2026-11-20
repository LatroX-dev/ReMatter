package dev.latrox.rematter.content.preparation;

import dev.latrox.rematter.content.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Real item prototypes, component checks and serializer round trips. Registered only in the test mod. */
@EventBusSubscriber(modid="rematter")
public final class IndustrialPreparationGameTests {
    private static final Map<String,Consumer<GameTestHelper>> TESTS=Map.of(
            "reclaim_outputs_are_atomic",IndustrialPreparationGameTests::outputs,
            "filled_hand_preparator_cannot_upgrade",IndustrialPreparationGameTests::emptyIngredient,
            "preparation_capabilities_roundtrip",IndustrialPreparationGameTests::codec);
    private static Identifier id(String name){return Identifier.parse("rematter_tests:industrial_preparation/"+name);}
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event){
        event.register(Registries.TEST_FUNCTION,helper->TESTS.forEach((name,function)->helper.register(id(name),function)));
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event){
        var environment=event.registerEnvironment(id("empty"));
        TESTS.forEach((name,function)->event.registerTest(id(name),new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION,id(name)),new TestData<>(environment,Identifier.parse("rematter_tests:empty"),100,0,true,Rotation.NONE,false,1,1,true,2))));
    }
    private static PreparationRecipe recipe(){return new PreparationRecipe(Ingredient.of(Items.REDSTONE),Optional.empty(),Optional.empty(),4,1,1,
            List.of(new ItemStack(Items.IRON_INGOT,3),new ItemStack(Items.COPPER_INGOT),new ItemStack(Items.GOLD_INGOT)),8000,160,2,
            PreparationRecipe.PREPARATION,false,4,Set.of(PreparationStep.SEPARATE));}
    private static Map<Integer,ItemStack> inventory(){var slots=new HashMap<Integer,ItemStack>();for(int slot:new int[]{0,1,3,4})slots.put(slot,ItemStack.EMPTY);slots.put(0,new ItemStack(Items.REDSTONE,4));return slots;}
    private static void outputs(GameTestHelper h){
        var recipe=recipe();var slots=inventory();slots.put(4,new ItemStack(Items.GOLD_INGOT,64));
        h.assertTrue(PreparationInventoryPlan.simulate(recipe,slots::get,64).isEmpty(),"One blocked byproduct must block the complete batch");
        h.assertTrue(slots.get(0).getCount()==4&&slots.get(1).isEmpty(),"Simulation consumed inputs or produced a partial output");
        slots.put(4,ItemStack.EMPTY);var plan=PreparationInventoryPlan.simulate(recipe,slots::get,64).orElseThrow();
        slots.put(3,new ItemStack(Items.STICK));
        h.assertTrue(!plan.commit(slots::get,slots::put)&&slots.get(0).getCount()==4,"Changed destination was not revalidated");
        slots.put(3,ItemStack.EMPTY);plan=PreparationInventoryPlan.simulate(recipe,slots::get,64).orElseThrow();
        h.assertTrue(plan.commit(slots::get,slots::put),"Complete reclaim did not commit");
        h.assertTrue(slots.get(0).isEmpty()&&slots.get(1).getCount()==3&&slots.get(3).is(Items.COPPER_INGOT)&&slots.get(4).is(Items.GOLD_INGOT),"Reclaim quantities differ");
        h.assertTrue(plan.recoveryMask()==((1<<3)|(1<<4)),"Returned ingredient roles are not marked output");h.succeed();
    }
    private static void emptyIngredient(GameTestHelper h){
        var ingredient=new EmptyMachineIngredient();var block=ModContent.MACHINES.get(MachineKind.HAND_PREPARATOR).get();var fresh=new ItemStack(block);
        h.assertTrue(ingredient.test(fresh),"Fresh Hand Preparator must be usable");
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
        var machine=(MachineBlockEntity)h.getLevel().getBlockEntity(pos);
        var empty=fresh.copy();empty.set(DataComponents.BLOCK_ENTITY_DATA,TypedEntityData.of(machine.getType(),machine.saveCustomOnly(h.getLevel().registryAccess())));
        h.assertTrue(ingredient.test(empty),"An emptied placed Hand Preparator must remain usable");
        machine.setItem(0,new ItemStack(Items.IRON_INGOT));var filled=fresh.copy();filled.set(DataComponents.BLOCK_ENTITY_DATA,TypedEntityData.of(machine.getType(),machine.saveCustomOnly(h.getLevel().registryAccess())));
        h.assertTrue(!ingredient.test(filled),"Filled portable machine cannot be consumed by an upgrade recipe");
        var id=Identifier.parse("rematter:material_preparator_from_hand_preparator");
        var holder=h.getLevel().getServer().getRecipeManager().getRecipes().stream()
                .filter(value->value.id().identifier().equals(id)).findFirst().orElseThrow(()->new IllegalStateException("The actual recipe reload omitted the Hand Preparator upgrade"));
        h.assertTrue(holder.value() instanceof CraftingRecipe,"Loaded upgrade has the wrong recipe type");
        var upgrade=(CraftingRecipe)holder.value();
        var amber=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.parse("rematter:amber_matrix_crystal"));
        var grid=new ArrayList<ItemStack>(List.of(new ItemStack(amber),new ItemStack(ModContent.STRUCTURAL_BARS.get()),new ItemStack(amber),
                new ItemStack(ModContent.STRUCTURAL_BARS.get()),fresh,new ItemStack(ModContent.STRUCTURAL_BARS.get()),
                new ItemStack(ModContent.CONTROL_CIRCUIT.get()),ItemStack.EMPTY,new ItemStack(ModContent.CONTROL_CIRCUIT.get())));
        h.assertTrue(upgrade.matches(CraftingInput.of(3,3,grid),h.getLevel()),"The actual loaded crafting recipe rejects a fresh Hand Preparator");
        grid.set(4,empty);var ready=CraftingInput.of(3,3,grid);
        h.assertTrue(upgrade.matches(ready,h.getLevel()),"The actual loaded crafting recipe rejects an emptied portable Hand Preparator");
        var result=upgrade.assemble(ready);h.assertTrue(result.is(ModContent.MACHINES.get(MachineKind.PREPARATOR).get().asItem())&&result.getCount()==1&&result.getComponentsPatch().isEmpty(),"Upgrade does not yield one plain Material Preparator");
        grid.set(4,filled);var preserved=filled.copy();
        h.assertTrue(!upgrade.matches(CraftingInput.of(3,3,grid),h.getLevel()),"The actual loaded recipe accepts a filled portable Hand Preparator");
        h.assertTrue(ItemStack.matches(preserved,filled),"Recipe matching mutated the rejected filled machine");h.succeed();
    }
    private static void codec(GameTestHelper h){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            var expected=recipe();PreparationRecipe.STREAM_CODEC.encode(buffer,expected);var actual=PreparationRecipe.STREAM_CODEC.decode(buffer);
            h.assertTrue(actual.minimumTier()==2&&actual.minPrecision()==4&&actual.steps().equals(Set.of(PreparationStep.SEPARATE)),"Capabilities changed in transit");
            h.assertTrue(actual.results().size()==3&&!actual.networkRecipe(),"Multiresult or network flag changed");
            for(int i=0;i<3;i++)h.assertTrue(ItemStack.matches(expected.results().get(i),actual.results().get(i)),"Output quantity changed in transit");
            h.assertTrue(buffer.readableBytes()==0,"Serializer left trailing bytes");h.succeed();
        }finally{buffer.release();}
    }
}
