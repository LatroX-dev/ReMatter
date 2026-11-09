package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Real server blocks, cells, tick processing and permissions. Not part of the distributable mod. */
@EventBusSubscriber(modid="rematter")
public final class ReMatterGameTests {
    private static final Map<String,Consumer<GameTestHelper>> TESTS=new LinkedHashMap<>();
    static {
        TESTS.put("sand_to_glass",ReMatterGameTests::glass);
        TESTS.put("full_output_is_atomic",ReMatterGameTests::fullOutput);
        TESTS.put("power_loss_is_atomic",ReMatterGameTests::noPower);
        TESTS.put("crystal_tier_is_enforced",ReMatterGameTests::crystalTier);
        TESTS.put("gas_vent_keeps_existing_stock",ReMatterGameTests::vent);
        TESTS.put("stock_target_counts_other_producers",ReMatterGameTests::target);
        TESTS.put("unknown_cell_matter_is_preserved",ReMatterGameTests::unknown);
        TESTS.put("ownership_and_fake_player",ReMatterGameTests::ownership);
        TESTS.put("separator_outputs_are_exact",ReMatterGameTests::separation);
        TESTS.put("phase_change_preserves_identity",ReMatterGameTests::phase);
        TESTS.put("air_source_requires_sky",ReMatterGameTests::air);
        TESTS.put("network_change_resumes_without_duplication",ReMatterGameTests::networkChange);
        TESTS.put("filled_machine_drop_keeps_contents_without_owner",ReMatterGameTests::drop);
        TESTS.put("industrial_parallel_batches_pay_individually",ReMatterGameTests::parallel);
        TESTS.put("cables_keep_owners_and_energy_separate",ReMatterGameTests::cables);
        TESTS.put("full_byproduct_is_atomic",ReMatterGameTests::byproduct);
        TESTS.put("advancement_revoke_updates_offline_mirror",ReMatterGameTests::advancement);
        TESTS.put("large_valid_storage_network",ReMatterGameTests::largeNetwork);
        TESTS.put("normal_placed_cables_connect",ReMatterGameTests::placedCables);
        TESTS.put("claim_refreshes_cable_arms",ReMatterGameTests::claimedCable);
        TESTS.put("preparation_slots_require_one_compatible_recipe",ReMatterGameTests::inputFiltering);
        TESTS.put("industrial_reactor_air_inlet_is_above_roof",ReMatterGameTests::industrialAir);
        TESTS.put("cell_led_tracks_committed_storage",ReMatterGameTests::cellLed);
    }
    private static Identifier id(String path) {return Identifier.fromNamespaceAndPath("rematter_tests",path);}
    @SubscribeEvent
    public static void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach((name,function)->helper.register(id(name),function)));
    }
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        var environment=event.registerEnvironment(id("empty"));
        TESTS.forEach((name,function)->event.registerTest(id(name),new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION,id(name)),
                new TestData<>(environment,id("empty"),1200,0,true,Rotation.NONE,false,1,1,true,2))));
    }
    private record Rig(MachineBlockEntity drive,MachineBlockEntity machine,net.minecraft.world.entity.player.Player owner) {}
    private static Rig rig(GameTestHelper h,MachineKind kind) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        var drive=place(h,new BlockPos(2,2,2),MachineKind.DRIVE,owner);
        drive.setItem(0,new ItemStack(ModContent.MATTER_CELL_64K.get()));
        var machine=place(h,new BlockPos(3,2,2),kind,owner);
        machine.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        return new Rig(drive,machine,owner);
    }
    private static MachineBlockEntity place(GameTestHelper h,BlockPos pos,MachineKind kind,net.minecraft.world.entity.player.Player owner) {
        h.setBlock(pos,ModContent.MACHINES.get(kind).get());
        var machine=h.getBlockEntity(pos,MachineBlockEntity.class);machine.placedBy(owner);return machine;
    }
    private static void put(Rig rig,Map<SubstanceKey,Long> matter) {
        if(!rig.drive.matterStore().apply(Map.of(),matter))throw new IllegalStateException("Fixture does not fit");
        rig.drive.setChanged();
    }
    private static void glass(GameTestHelper h) {
        var r=rig(h,MachineKind.DECOMPOSER);r.machine.setItem(0,new ItemStack(Items.SAND));
        var synth=place(h,new BlockPos(1,2,2),MachineKind.SYNTHESIZER,r.owner);synth.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        synth.selectRecipe("minecraft:glass");synth.requestBatch();
        h.succeedWhen(()-> {
            h.assertTrue(synth.getItem(1).is(Items.GLASS),"Glass was not produced");
            h.assertTrue(r.machine.getItem(0).isEmpty(),"Sand input was not consumed exactly once");
            h.assertTrue(r.drive.matterStore().total()==0,"Silica was duplicated or lost");
            h.assertTrue(r.machine.energy().getAmountAsLong()+synth.energy().getAmountAsLong()==2L*MachineBlockEntity.ENERGY_CAPACITY-24000,"Wrong FE debit");
        });
    }
    private static void fullOutput(GameTestHelper h) {
        var r=rig(h,MachineKind.SYNTHESIZER);put(r,Map.of(BuiltinChemistry.SILICA,1000L));
        h.assertTrue(!r.machine.configure(r.owner,new MachineSettings(65,"","",Set.of())),"Impossible output target accepted");
        r.machine.setItem(1,new ItemStack(Items.GLASS,64));r.machine.selectRecipe("minecraft:glass");r.machine.requestBatch();
        h.runAtTickTime(160,()-> {
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.SILICA)==1000,"A full output consumed matter");
            h.assertTrue(r.machine.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY,"A full output consumed FE");h.succeed();
        });
    }
    private static void noPower(GameTestHelper h) {
        var r=rig(h,MachineKind.SYNTHESIZER);put(r,Map.of(BuiltinChemistry.SILICA,1000L));
        r.machine.energy().set(0);r.machine.selectRecipe("minecraft:glass");r.machine.requestBatch();
        h.runAtTickTime(140,()-> {
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.SILICA)==1000,"No-power operation consumed matter");
            h.assertTrue(r.machine.getItem(1).isEmpty(),"No-power operation produced output");
            r.machine.energy().set(16000);
        });
        h.succeedWhen(()->h.assertTrue(r.machine.getItem(1).is(Items.GLASS),"Work did not resume after power returned"));
    }
    private static void crystalTier(GameTestHelper h) {
        var r=rig(h,MachineKind.SYNTHESIZER);put(r,Map.of(SubstanceKey.of("rematter:diamond",Phase.SOLID),1000L));
        h.assertTrue(r.machine.selectRecipe("minecraft:diamond"),"Diamond recipe unavailable");r.machine.requestBatch();
        h.runAtTickTime(160,()-> {
            h.assertTrue(r.machine.getItem(1).isEmpty(),"Tier one created a diamond");
            h.assertTrue(!r.machine.canPlaceItem(2,new ItemStack(ModContent.PARALLEL_UPGRADE.get())),"Compact machine accepted industrial parallel upgrade");
            r.machine.setItem(2,new ItemStack(ModContent.PRESSURE_MODULE.get()));
        });
        h.succeedWhen(()->h.assertTrue(r.machine.getItem(1).is(Items.DIAMOND),"Pressure module did not unlock diamond"));
    }
    private static void vent(GameTestHelper h) {
        var r=rig(h,MachineKind.REACTOR);put(r,Map.of(BuiltinChemistry.WATER,1000L,BuiltinChemistry.OXYGEN,7L));
        r.machine.selectRecipe("rematter:water_electrolysis");
        h.assertTrue(r.machine.configure(r.owner,new MachineSettings(0,"","",Set.of("rematter:oxygen"))),"Vent setting rejected");
        r.machine.requestBatch();
        h.succeedWhen(()-> {
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.HYDROGEN)==1000,"Hydrogen missing");
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.OXYGEN)==7,"Vent deleted existing oxygen or retained new oxygen");
            h.assertTrue(r.machine.ventedTotal()==500,"Wrong vent accounting");
        });
    }
    private static void target(GameTestHelper h) {
        var r=rig(h,MachineKind.REACTOR);put(r,Map.of(BuiltinChemistry.HYDROGEN,6000L,BuiltinChemistry.OXYGEN,3000L));
        var other=place(h,new BlockPos(1,2,2),MachineKind.REACTOR,r.owner);other.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        r.machine.selectRecipe("rematter:water_synthesis");other.selectRecipe("rematter:water_synthesis");
        var setting=new MachineSettings(2000,"rematter:water","liquid",Set.of());
        h.assertTrue(r.machine.configure(r.owner,setting)&&other.configure(r.owner,setting),"Target rejected");
        var locked=place(h,new BlockPos(2,2,3),MachineKind.SYNTHESIZER,r.owner);locked.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        locked.selectRecipe("minecraft:diamond");locked.requestBatch();
        h.runAtTickTime(700,()-> {
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.WATER)==2000,"Multiple producers overshot or missed target");
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.HYDROGEN)==4000,"Wrong target consumption");
            h.assertTrue(r.machine.configure(r.owner,MachineSettings.DEFAULT),"Target could not be disabled");
            h.assertTrue(!r.machine.runState().running(),"Disabling a target changed into unbounded production");
        });
        h.runAtTickTime(1000,()-> {h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.WATER)==2000,"Disabled target kept producing");h.succeed();});
    }
    private static void unknown(GameTestHelper h) {
        var r=rig(h,MachineKind.DECOMPOSER);var unknown=SubstanceKey.of("test:missing",Phase.GAS);
        put(r,Map.of(unknown,123L));
        h.runAtTickTime(30,()-> {
            h.assertTrue(r.drive.matterStore().amount(unknown)==123,"Unknown matter was discarded");
            var saved=r.drive.getItem(0).copy();r.drive.setItem(0,ItemStack.EMPTY);r.drive.setItem(0,saved);
            h.assertTrue(r.drive.matterStore().amount(unknown)==123,"Cell round trip discarded unknown matter");h.succeed();
        });
    }
    private static void ownership(GameTestHelper h) {
        var r=rig(h,MachineKind.SYNTHESIZER);var foreign=h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(r.machine.canAccess(r.owner),"Owner denied");
        h.assertTrue(!r.machine.canAccess(foreign),"Foreign player gained access");
        var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(r.owner.getUUID(),"OwnerAutomation"));
        h.assertTrue(r.machine.canAccess(fake),"Owner fake player denied");
        var spectator=h.makeMockPlayer(GameType.SPECTATOR);var watched=place(h,new BlockPos(5,2,2),MachineKind.SYNTHESIZER,spectator);
        h.assertTrue(!watched.configure(spectator,new MachineSettings(1,"","",Set.of())),"Spectator changed production settings");h.succeed();
    }
    private static void separation(GameTestHelper h) {
        var r=rig(h,MachineKind.REACTOR);put(r,Map.of(BuiltinChemistry.SOIL,1000L));
        h.assertTrue(r.machine.selectRecipe("rematter:water_synthesis"),"Unified reactor rejected reaction");
        r.machine.selectRecipe("rematter:soil_separation");r.machine.requestBatch();
        h.succeedWhen(()-> {
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.CARBON)==1000,"Carbon missing");
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.SILICA)==1000,"Silica missing");
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.WATER)==1000,"Water missing");
        });
    }
    private static void phase(GameTestHelper h) {
        var r=rig(h,MachineKind.REACTOR);put(r,Map.of(BuiltinChemistry.WATER,1000L));
        r.machine.selectRecipe("rematter:water_evaporation");r.machine.requestBatch();
        h.succeedWhen(()-> {
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.STEAM)==1000,"Steam missing");
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.WATER)==0,"Water duplicated");
        });
    }
    private static void air(GameTestHelper h) {
        var r=rig(h,MachineKind.REACTOR);h.setBlock(3,3,2,Blocks.STONE);
        r.machine.selectRecipe("rematter:air_oxygen");r.machine.requestBatch();
        h.runAtTickTime(250,()-> {
            h.assertTrue(r.drive.matterStore().total()==0,"Blocked air source produced gas");h.setBlock(3,3,2,Blocks.AIR);
        });
        h.succeedWhen(()->h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.OXYGEN)==200,"Air source did not resume"));
    }
    private static void networkChange(GameTestHelper h) {
        var r=rig(h,MachineKind.SYNTHESIZER);put(r,Map.of(BuiltinChemistry.SILICA,1000L));
        r.machine.selectRecipe("minecraft:glass");r.machine.requestBatch();
        var cell=r.drive.getItem(0).copy();
        h.runAtTickTime(20,()->r.drive.setItem(0,ItemStack.EMPTY));
        h.runAtTickTime(170,()-> {
            h.assertTrue(r.machine.getItem(1).isEmpty(),"Disconnected storage produced output");
            h.assertTrue(r.machine.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY,"Disconnected work consumed FE");
            r.drive.setItem(0,cell);
        });
        h.succeedWhen(()-> {
            h.assertTrue(r.machine.getItem(1).getCount()==1,"Batch did not resume exactly once");
            h.assertTrue(r.drive.matterStore().total()==0,"Resumed work left duplicate matter");
        });
    }
    private static void drop(GameTestHelper h) {
        var r=rig(h,MachineKind.SYNTHESIZER);r.machine.setItem(1,new ItemStack(Items.GLASS,13));
        var drops=net.minecraft.world.level.block.Block.getDrops(r.machine.getBlockState(),h.getLevel(),r.machine.getBlockPos(),r.machine);
        h.assertTrue(drops.size()==1,"Filled machine did not produce one portable drop");
        var data=drops.getFirst().get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        h.assertTrue(data!=null&&!data.contains("owner"),"Portable machine retained the old owner");
        var next=place(h,new BlockPos(5,2,2),MachineKind.SYNTHESIZER,h.makeMockPlayer(GameType.SURVIVAL));
        h.assertTrue(data.loadInto(next,h.getLevel().registryAccess()),"Portable data was not restored");
        h.assertTrue(next.getItem(1).getCount()==13,"Portable output was lost");
        h.assertTrue(next.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY,"Portable FE was lost");h.succeed();
    }
    private static void parallel(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var origin=h.absolutePos(new BlockPos(4,2,3));
        var shape=new IndustrialStructure.Shape(3,4,3,1);
        for(var cell:IndustrialStructure.layout(shape)) {
            var pos=IndustrialStructure.worldPosition(origin,net.minecraft.core.Direction.NORTH,cell);
            var block=switch(cell.role()) {
                case FRAME -> ModContent.REACTOR_CASING.get();
                case WALL -> cell.outward().getAxis().isHorizontal()?ModContent.REACTOR_GLASS.get():ModContent.REACTOR_CASING.get();
                default -> Blocks.AIR;
            };
            h.getLevel().setBlock(pos,block.defaultBlockState(),3);
        }
        var controller=place(h,new BlockPos(4,2,3),MachineKind.INDUSTRIAL_REACTOR,owner);
        h.setBlock(3,2,4,ModContent.MACHINE_PORT.get());h.setBlock(5,2,4,ModContent.MACHINE_PORT.get());
        h.setBlock(4,2,4,ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.REACTION_CHAMBER).get());
        h.setBlock(4,3,4,ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.FIELD_COIL).get());
        h.assertTrue(IndustrialStructure.inspect(controller).formed(),"Fixture chamber did not form: "+IndustrialStructure.inspect(controller).diagnostic());
        var drive=place(h,new BlockPos(2,2,4),MachineKind.DRIVE,owner);drive.setItem(0,new ItemStack(ModContent.MATTER_CELL_64K.get()));
        h.assertTrue(drive.matterStore().apply(Map.of(),Map.of(BuiltinChemistry.WATER,4000L)),"Water fixture rejected");
        controller.energy().set(MachineBlockEntity.ENERGY_CAPACITY);controller.setItem(2,new ItemStack(ModContent.PARALLEL_UPGRADE.get(),3));
        controller.selectRecipe("rematter:water_evaporation");for(int i=0;i<4;i++)controller.requestBatch();
        long debit=4*controller.cost(ReMatter.chemistry().recipe(SubstanceId.parse("rematter:water_evaporation")).orElseThrow().energy());
        h.runAtTickTime(10,()->h.assertTrue(controller.parallelRunning()==4,"Four lanes were not reserved together"));
        h.runAtTickTime(20,()->h.setBlock(3,1,3,Blocks.AIR));
        h.runAtTickTime(40,()-> {
            h.assertTrue(drive.matterStore().amount(BuiltinChemistry.WATER)==4000,"Broken chamber consumed reserved inputs");
            h.assertTrue(controller.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY,"Broken chamber consumed FE");
            h.assertTrue(IndustrialStructure.inspect(controller).mismatch()!=null,"Broken chamber lacks a construction hint");
            h.setBlock(3,1,3,ModContent.REACTOR_CASING.get());
        });
        h.succeedWhen(()-> {
            h.assertTrue(drive.matterStore().amount(BuiltinChemistry.STEAM)==4000,"Parallel output is not exact");
            h.assertTrue(drive.matterStore().amount(BuiltinChemistry.WATER)==0,"Parallel input duplicated");
            h.assertTrue(controller.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY-debit,"Parallel FE debit is not per batch");
        });
    }
    private static void cables(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var foreign=h.makeMockPlayer(GameType.SURVIVAL);
        var drive=place(h,new BlockPos(2,2,2),MachineKind.DRIVE,owner);drive.setItem(0,new ItemStack(ModContent.MATTER_CELL_64K.get()));
        var processor=place(h,new BlockPos(4,2,2),MachineKind.DECOMPOSER,owner);
        var outsider=place(h,new BlockPos(4,2,3),MachineKind.DECOMPOSER,foreign);
        h.setBlock(3,2,2,ModContent.MATTER_CABLE.get());h.setBlock(3,2,3,ModContent.MATTER_CABLE.get());
        var cable=h.absolutePos(new BlockPos(3,2,2));var other=h.absolutePos(new BlockPos(3,2,3));
        CableOwners.set(h.getLevel(),cable,owner.getUUID());CableOwners.set(h.getLevel(),other,foreign.getUUID());
        h.assertTrue(!CableOwners.canRemove(h.getLevel(),cable,foreign),"Foreign player may break owned cable");
        h.assertTrue(processor.matterNetwork(true).ready(),"Owner cable did not connect storage");
        var handler=h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,cable,net.minecraft.core.Direction.UP);
        try(var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            h.assertTrue(handler.insert(1000,tx)==1000,"Owned cable refused FE");tx.commit();
        }
        h.assertTrue(processor.energy().getAmountAsLong()==1000&&outsider.energy().getAmountAsLong()==0,"FE crossed an ownership boundary");
        try(var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {handler.insert(500,tx);}
        h.assertTrue(processor.energy().getAmountAsLong()==1000,"Aborted cable transfer leaked FE");
        CableOwners.set(h.getLevel(),cable,foreign.getUUID());
        h.assertTrue(!processor.matterNetwork(true).ready(),"Ownership change left a cached foreign network");h.succeed();
    }
    private static void placeItem(GameTestHelper h, BlockPos relative, net.minecraft.world.entity.player.Player player, ItemStack stack) {
        var pos = h.absolutePos(relative);
        h.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var hit = new net.minecraft.world.phys.BlockHitResult(pos.below().getCenter().add(0, .5, 0),
                net.minecraft.core.Direction.UP, pos.below(), false);
        var context = new net.minecraft.world.item.context.BlockPlaceContext(player,
                net.minecraft.world.InteractionHand.MAIN_HAND, stack, hit);
        ((net.minecraft.world.item.BlockItem) stack.getItem()).place(context);
        h.assertTrue(!h.getLevel().getBlockState(pos).isAir(), "Normal block-item placement failed");
    }
    private static void placedCables(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        placeItem(h, new BlockPos(2,2,2), owner, new ItemStack(ModContent.MACHINES.get(MachineKind.DRIVE).get()));
        h.getBlockEntity(new BlockPos(2,2,2), MachineBlockEntity.class).setItem(0, new ItemStack(ModContent.MATTER_CELL_64K.get()));
        placeItem(h, new BlockPos(3,2,2), owner, new ItemStack(ModContent.MATTER_CABLE.get()));
        placeItem(h, new BlockPos(4,2,2), owner, new ItemStack(ModContent.REINFORCED_MATTER_CABLE.get()));
        placeItem(h, new BlockPos(5,2,2), owner, new ItemStack(ModContent.RESONANT_MATTER_CABLE.get()));
        placeItem(h, new BlockPos(6,2,2), owner, new ItemStack(ModContent.MACHINES.get(MachineKind.DECOMPOSER).get()));
        h.runAtTickTime(5, () -> {
            for (int x=3;x<=5;x++) {
                var state=h.getBlockState(new BlockPos(x,2,2));
                h.assertTrue(state.getValue(net.minecraft.world.level.block.PipeBlock.EAST)
                        && state.getValue(net.minecraft.world.level.block.PipeBlock.WEST), "Normally placed cable has no endpoint arm at " + x);
            }
            var machine=h.getBlockEntity(new BlockPos(6,2,2),MachineBlockEntity.class);
            h.assertTrue(machine.matterNetwork(true).ready(), "Normally placed cable did not find the Drive");
            h.succeed();
        });
    }
    private static void claimedCable(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        h.setBlock(2,2,2,ModContent.MACHINES.get(MachineKind.REACTOR).get());
        placeItem(h,new BlockPos(3,2,2),owner,new ItemStack(ModContent.MATTER_CABLE.get()));
        h.runAtTickTime(5,()-> {
            h.assertTrue(!h.getBlockState(new BlockPos(3,2,2)).getValue(net.minecraft.world.level.block.PipeBlock.WEST),"Unowned machine connected");
            var machine=h.getBlockEntity(new BlockPos(2,2,2),MachineBlockEntity.class);
            machine.interact(owner);
            h.assertTrue(h.getBlockState(new BlockPos(3,2,2)).getValue(net.minecraft.world.level.block.PipeBlock.WEST),"Claim did not refresh cable arm");
            h.succeed();
        });
    }
    private static void inputFiltering(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        var machine=place(h,new BlockPos(2,2,2),MachineKind.PREPARATOR,owner);
        var recipes=dev.latrox.rematter.content.preparation.PreparationRecipes.serverRecipes(h.getLevel());
        var inputs=new dev.latrox.rematter.content.preparation.PreparationInput(new ItemStack(ModContent.IRON_DUST.get()),ItemStack.EMPTY,ItemStack.EMPTY);
        h.assertTrue(MachineInputPolicy.accepts(MachineKind.PREPARATOR,3,new ItemStack(ModContent.COPPER_DUST.get()),inputs,ReMatter.chemistry(),recipes),"Valid incomplete mixing recipe rejected");
        h.assertTrue(!MachineInputPolicy.accepts(MachineKind.PREPARATOR,3,new ItemStack(ModContent.ARGIL.get()),inputs,ReMatter.chemistry(),recipes),"Ingredient from an unrelated recipe accepted");
        machine.setItem(0,new ItemStack(ModContent.IRON_DUST.get()));
        h.assertTrue(machine.canPlaceItem(3,new ItemStack(ModContent.COPPER_DUST.get())),"Server rejected compatible input");
        h.assertTrue(!machine.canPlaceItem(3,new ItemStack(ModContent.ARGIL.get())),"Server accepted incompatible mixing input");
        for(int slot:new int[]{0,3,4})h.assertTrue(!machine.canPlaceItem(slot,new ItemStack(Items.DIRT)),"Server accepted an item without a recipe");
        h.succeed();
    }
    private static void industrialAir(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var origin=h.absolutePos(new BlockPos(4,2,3));
        var shape=new IndustrialStructure.Shape(3,4,3,1);
        for(var cell:IndustrialStructure.layout(shape)) {
            var block=switch(cell.role()) {
                case FRAME -> ModContent.REACTOR_CASING.get();
                case WALL -> cell.outward().getAxis().isHorizontal()?ModContent.REACTOR_GLASS.get():ModContent.REACTOR_CASING.get();
                default -> Blocks.AIR;
            };
            h.getLevel().setBlock(IndustrialStructure.worldPosition(origin,net.minecraft.core.Direction.NORTH,cell),block.defaultBlockState(),3);
        }
        var reactor=place(h,new BlockPos(4,2,3),MachineKind.INDUSTRIAL_REACTOR,owner);
        h.setBlock(3,2,4,ModContent.MACHINE_PORT.get());h.setBlock(5,2,4,ModContent.MACHINE_PORT.get());
        h.setBlock(4,2,4,ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.REACTION_CHAMBER).get());
        h.setBlock(4,3,4,ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.FIELD_COIL).get());
        var drive=place(h,new BlockPos(2,2,4),MachineKind.DRIVE,owner);drive.setItem(0,new ItemStack(ModContent.MATTER_CELL_64K.get()));
        reactor.energy().set(MachineBlockEntity.ENERGY_CAPACITY);reactor.selectRecipe("rematter:air_oxygen");reactor.requestBatch();
        var inlet=origin.above(shape.height()-1);
        h.runAtTickTime(10,()->h.getLevel().setBlock(inlet,Blocks.STONE.defaultBlockState(),3));
        h.runAtTickTime(60,()-> {
            h.assertTrue(drive.matterStore().amount(BuiltinChemistry.OXYGEN)==0,"Blocked industrial air inlet produced gas");
            h.assertTrue(reactor.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY,"Blocked industrial air inlet spent FE");
            h.getLevel().setBlock(inlet,Blocks.AIR.defaultBlockState(),3);
        });
        h.succeedWhen(()-> {
            h.assertTrue(drive.matterStore().amount(BuiltinChemistry.OXYGEN)==200,"Industrial air source did not recover after reopening inlet");
            h.assertTrue(reactor.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY-reactor.cost(16000),"Industrial air source paid wrong FE");
        });
    }
    private static void byproduct(GameTestHelper h) {
        var r=rig(h,MachineKind.REACTOR);put(r,Map.of(BuiltinChemistry.WATER,1000L,BuiltinChemistry.CARBON,63000L));
        r.machine.selectRecipe("rematter:water_electrolysis");r.machine.requestBatch();
        h.runAtTickTime(250,()-> {
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.WATER)==1000,"A full byproduct consumed water");
            h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.HYDROGEN)==0,"A partial batch escaped");
            h.assertTrue(r.machine.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY,"A full byproduct consumed FE");h.succeed();
        });
    }
    private static void advancement(GameTestHelper h) {
        var owner=UUID.randomUUID();var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),
                new com.mojang.authlib.GameProfile(owner,"ProgressionTest"),net.minecraft.server.level.ClientInformation.createDefault());
        String requirement="minecraft:story/mine_stone";
        var definition=h.getLevel().getServer().getAdvancements().get(Identifier.parse(requirement));
        h.assertTrue(definition!=null,"Advancement fixture missing");
        h.assertTrue(!MachineProgression.allows(h.getLevel(),owner,requirement),"Unacquired advancement was granted");
        for(String criterion:definition.value().criteria().keySet())player.getAdvancements().award(definition,criterion);
        h.assertTrue(MachineProgression.allows(h.getLevel(),owner,requirement),"Completed advancement was not mirrored for offline owner");
        for(String criterion:definition.value().criteria().keySet())player.getAdvancements().revoke(definition,criterion);
        h.assertTrue(!MachineProgression.allows(h.getLevel(),owner,requirement),"Revoked advancement stayed granted");
        for(String criterion:definition.value().criteria().keySet())player.getAdvancements().getOrStartProgress(definition).grantProgress(criterion);
        MachineProgression.capture(player);
        h.assertTrue(MachineProgression.allows(h.getLevel(),owner,requirement),"Pre-existing award was not captured at first login");
        h.assertTrue(!MachineProgression.allows(h.getLevel(),owner,"test:missing"),"Missing definition granted access");h.succeed();
    }
    private static void cellLed(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        var drive=place(h,new BlockPos(2,2,2),MachineKind.DRIVE,owner);
        drive.setItem(0,new ItemStack(ModContent.MATTER_CELL_64K.get()));drive.serverTick();
        h.assertTrue(drive.visibleCellStatus(0)==CellStatus.EMPTY,"Empty cell indicator missing");
        h.assertTrue(drive.matterStore().apply(Map.of(),Map.of(BuiltinChemistry.SOIL,64000L)),"LED fixture did not fit");drive.serverTick();
        h.assertTrue(drive.visibleCellStatus(0)==CellStatus.FULL,"Full indicator did not follow live contents");
        var packet=drive.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue((packet.getIntOr("cell_statuses",0)&7)==CellStatus.FULL.ordinal(),"Public update did not carry full indicator");
        h.assertTrue(!packet.toString().contains("rematter:soil")&&!packet.toString().contains("64000"),"LED leaked detailed stock");
        h.assertTrue(drive.matterStore().apply(Map.of(BuiltinChemistry.SOIL,1L),Map.of()),"Could not extract LED sample");drive.serverTick();
        h.assertTrue(drive.visibleCellStatus(0)==CellStatus.STORED,"Green indicator did not follow extraction");
        var unknown=new net.minecraft.nbt.CompoundTag();unknown.putInt("schema",99);unknown.putLong("preserved",37);
        var locked=new ItemStack(ModContent.MATTER_CELL_64K.get());locked.set(MatterCellData.TYPE.get(),new MatterCellData(unknown));drive.setItem(0,locked);drive.serverTick();
        h.assertTrue(drive.visibleCellStatus(0)==CellStatus.LOCKED,"Unreadable cell was not red");
        h.assertTrue(locked.get(MatterCellData.TYPE.get()).copyTag().equals(unknown),"Reading the LED changed preserved contents");
        h.succeed();
    }
    private static void largeNetwork(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var drives=new ArrayList<MachineBlockEntity>();
        for(int x=1;x<=8;x++)for(int z=1;z<=8;z++) {
            var drive=place(h,new BlockPos(x,1,z),MachineKind.DRIVE,owner);drives.add(drive);
            for(int bay=0;bay<6;bay++)drive.setItem(MachineCellAccess.slotForBay(MachineKind.DRIVE,bay),new ItemStack(ModContent.MATTER_CELL_64K.get()));
        }
        var workers=new ArrayList<MachineBlockEntity>();
        for(int x=1;x<=5;x++)for(int z=1;z<=4;z++) {
            var worker=place(h,new BlockPos(x,2,z),MachineKind.DECOMPOSER,owner);workers.add(worker);
            worker.energy().set(8000);worker.setItem(0,new ItemStack(Items.SAND));
        }
        long start=System.nanoTime();var network=workers.getFirst().matterNetwork(true);
        h.assertTrue(network.ready(),"Valid large topology rejected: "+network.status());
        h.assertTrue(network.inventory().members().size()==384&&network.inventory().capacity()==384*MatterLimits.CELL_64K,"Large network did not expose every cell");
        for(int i=0;i<100;i++)workers.get(i%workers.size()).matterNetwork(true).inventory().contents();
        ReMatter.LOGGER.info("ReMatter large-network sample: 64 Drives, 384 Cells, 20 workers, initial scan + 100 stock views = {} us",(System.nanoTime()-start)/1000);
        h.succeedWhen(()-> {
            h.assertTrue(workers.getFirst().matterNetwork(true).inventory().amount(BuiltinChemistry.SILICA)==20000,"Large production did not commit exact batches");
            h.assertTrue(workers.stream().allMatch(w->w.getItem(0).isEmpty()&&w.energy().getAmountAsLong()==0),"Large production lost or duplicated an input/FE debit");
        });
    }
}
