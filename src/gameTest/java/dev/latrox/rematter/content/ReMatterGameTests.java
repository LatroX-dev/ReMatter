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
        TESTS.put("drive_preserved_items_do_not_overlap_bays",ReMatterGameTests::drivePreservedItems);
        TESTS.put("sand_to_glass",ReMatterGameTests::glass);
        TESTS.put("full_output_is_atomic",ReMatterGameTests::fullOutput);
        TESTS.put("power_loss_is_atomic",ReMatterGameTests::noPower);
        TESTS.put("crystal_tier_is_enforced",ReMatterGameTests::crystalTier);
        TESTS.put("gas_vent_keeps_existing_stock",ReMatterGameTests::vent);
        TESTS.put("stock_target_counts_other_producers",ReMatterGameTests::target);
        TESTS.put("unknown_cell_matter_is_preserved",ReMatterGameTests::unknown);
        TESTS.put("ownership_and_fake_player",ReMatterGameTests::ownership);
        TESTS.put("reactor_separation_outputs_are_exact",ReMatterGameTests::separation);
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
        TESTS.put("player_placed_cable_tiers_connect_in_all_six_directions",ReMatterGameTests::playerCableDirections);
        TESTS.put("cancelled_cable_placement_does_not_claim_or_connect",ReMatterGameTests::cancelledCablePlacement);
        TESTS.put("port_configuration_visuals_survive_reload",ReMatterGameTests::portConfigurationVisuals);
        TESTS.put("claim_refreshes_cable_arms",ReMatterGameTests::claimedCable);
        TESTS.put("preparation_slots_require_one_compatible_recipe",ReMatterGameTests::inputFiltering);
        TESTS.put("industrial_reactor_air_inlet_is_above_roof",ReMatterGameTests::industrialAir);
        TESTS.put("cell_led_tracks_committed_storage",ReMatterGameTests::cellLed);
        TESTS.put("cable_device_arms_follow_neighbours",ReMatterGameTests::deviceArms);
        TESTS.put("drive_activity_follows_readable_cells",ReMatterGameTests::driveActivity);
        TESTS.put("network_devices_light_while_their_network_is_ready",ReMatterGameTests::networkLights);
        TESTS.put("air_source_collects_below_reactor_glass",ReMatterGameTests::airBelowGlass);
        TESTS.put("machine_outlines_occlude_like_their_models",ReMatterGameTests::machineOcclusion);
        TESTS.put("reactor_glass_passes_light_like_glass",ReMatterGameTests::reactorGlassLight);
        TESTS.put("block_state_files_match_registered_states",ReMatterGameTests::blockStateFiles);
    }
    private static void drivePreservedItems(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var drive=place(h,new BlockPos(2,2,2),MachineKind.DRIVE,owner);
        for(int bay=0;bay<6;bay++)drive.setItem(MachineCellAccess.slotForBay(MachineKind.DRIVE,bay),new ItemStack(ModContent.MATTER_CELL_64K.get()));
        drive.setItem(3,new ItemStack(Items.IRON_INGOT,3));drive.setItem(4,new ItemStack(Items.GOLD_INGOT,2));
        var menu=new MachineMenu(1,owner.getInventory(),drive);
        for(var view:List.of(MachineMenu.View.MAIN,MachineMenu.View.CONFIGURATION)) {
            menu.setView(view);
            var active=menu.slots.stream().limit(MachineMenu.FIRST_PLAYER_SLOT).filter(net.minecraft.world.inventory.Slot::isActive).toList();
            for(int a=0;a<active.size();a++)for(int b=a+1;b<active.size();b++) {
                var x=active.get(a);var y=active.get(b);
                h.assertTrue(x.x+16<=y.x||y.x+16<=x.x||x.y+16<=y.y||y.y+16<=x.y,"Active Drive slots overlap in "+view);
            }
        }
        h.assertTrue(menu.slots.get(3).isActive()&&menu.slots.get(3).mayPickup(owner),"Preserved input cannot be removed in Settings");
        h.assertTrue(menu.slots.get(3).remove(3).is(Items.IRON_INGOT)&&drive.getItem(3).isEmpty(),"Preserved input was lost or duplicated");
        h.assertTrue(drive.getItem(4).getCount()==2,"Independent preserved input changed");
        for(int bay=0;bay<6;bay++)h.assertTrue(!drive.getItem(MachineCellAccess.slotForBay(MachineKind.DRIVE,bay)).isEmpty(),"Cell changed during preserved-item removal");
        h.succeed();
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
        var machine=h.getBlockEntity(pos,MachineBlockEntity.class);machine.placedBy(owner);
        if(!(owner instanceof net.neoforged.neoforge.common.util.FakePlayer)){
            dev.latrox.rematter.research.ResearchService.ledger(h.getLevel().getServer()).visit(owner.getUUID(),h.getLevel().dimension().identifier().toString());
            if(kind.synthesis())for(var family:dev.latrox.rematter.research.ResearchData.current().families())dev.latrox.rematter.research.ResearchService.adminGrant(h.getLevel().getServer(),owner.getUUID(),family.id());
        }
        return machine;
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
        var shape=new IndustrialStructure.Shape(3,7,3,1);
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
        h.setBlock(4,6,4,ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.FIELD_COIL).get());
        h.assertTrue(IndustrialStructure.inspect(controller).formed(),"Fixture chamber did not form: "+IndustrialStructure.inspect(controller).diagnostic());
        var drive=place(h,new BlockPos(2,2,4),MachineKind.DRIVE,owner);drive.setItem(0,new ItemStack(ModContent.MATTER_CELL_64K.get()));
        h.assertTrue(drive.matterStore().apply(Map.of(),Map.of(BuiltinChemistry.WATER,4000L)),"Water fixture rejected");
        controller.energy().set(MachineBlockEntity.ENERGY_CAPACITY);controller.setItem(MachineCellAccess.PARALLEL_SLOT,new ItemStack(ModContent.PARALLEL_UPGRADE.get(),3));
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
        var context = new net.minecraft.world.item.context.UseOnContext(player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        // ItemStack.useOn includes NeoForge's captured placement / protection-event path.
        h.assertTrue(stack.useOn(context).consumesAction(), "Player block-item use failed");
        h.assertTrue(!h.getLevel().getBlockState(pos).isAir(), "Player block-item placement failed");
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
    private static net.minecraft.world.InteractionResult useItemAt(GameTestHelper h,BlockPos relative,
            net.minecraft.world.entity.player.Player player,ItemStack stack) {
        var pos=h.absolutePos(relative);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        var hit=new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),net.minecraft.core.Direction.UP,pos,false);
        return stack.useOn(new net.minecraft.world.item.context.UseOnContext(player,
                net.minecraft.world.InteractionHand.MAIN_HAND,hit));
    }
    private static void playerCableDirections(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var foreign=h.makeMockPlayer(GameType.SURVIVAL);
        var center=new BlockPos(4,4,4);
        for(var cable:List.of(ModContent.MATTER_CABLE.get(),ModContent.REINFORCED_MATTER_CABLE.get(),
                ModContent.RESONANT_MATTER_CABLE.get()))for(var side:net.minecraft.core.Direction.values()) {
            var neighbor=center.relative(side);
            h.assertTrue(useItemAt(h,center,owner,new ItemStack(cable)).consumesAction(),"Cable placement failed");
            h.assertTrue(useItemAt(h,neighbor,owner,new ItemStack(cable)).consumesAction(),"Neighbor placement failed");
            h.assertTrue(owner.getUUID().equals(CableOwners.owner(h.getLevel(),h.absolutePos(center))),"Placer ownership lost");
            h.assertTrue(owner.getUUID().equals(CableOwners.owner(h.getLevel(),h.absolutePos(neighbor))),"Neighbor ownership lost");
            h.assertTrue(arm(h.getBlockState(center),side)&&arm(h.getBlockState(neighbor),side.getOpposite()),
                    "Player-placed "+cable+" did not connect in "+side);
            h.assertTrue(!flange(h.getBlockState(center),side),"Cable-to-cable connection grew a device flange");
            h.setBlock(neighbor,Blocks.AIR);
            h.assertTrue(!arm(h.getBlockState(center),side),"Removed cable left an arm");
            h.assertTrue(CableOwners.owner(h.getLevel(),h.absolutePos(neighbor))==null,"Removed cable kept its owner");
            h.assertTrue(useItemAt(h,neighbor,foreign,new ItemStack(cable)).consumesAction(),"Foreign cable placement failed");
            h.assertTrue(!arm(h.getBlockState(center),side)&&!arm(h.getBlockState(neighbor),side.getOpposite()),
                    "Player-placed cables crossed an ownership boundary");
            h.setBlock(neighbor,Blocks.AIR);h.setBlock(center,Blocks.AIR);
        }
        h.succeed();
    }
    private static void cancelledCablePlacement(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var foreign=h.makeMockPlayer(GameType.SURVIVAL);
        var anchor=new BlockPos(3,3,3);var target=anchor.east();var absolute=h.absolutePos(target);
        h.assertTrue(useItemAt(h,anchor,owner,new ItemStack(ModContent.MATTER_CABLE.get())).consumesAction(),"Anchor placement failed");
        var stack=new ItemStack(ModContent.REINFORCED_MATTER_CABLE.get(),2);
        boolean[] intercepted={false};
        Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> listener=event->{
            if(event.getLevel()==h.getLevel()&&event.getPos().equals(absolute)) {
                intercepted[0]=true;
                h.assertTrue(!(event instanceof net.neoforged.neoforge.event.level.BlockEvent.EntityMultiPlaceEvent),
                        "Cable placement changed neighboring blocks before protection accepted it");
                h.assertTrue(CableOwners.owner(h.getLevel(),absolute)==null,"Unaccepted placement already owns the cable");
                h.assertTrue(!arm(h.getBlockState(anchor),net.minecraft.core.Direction.EAST),"Unaccepted placement changed a neighbor arm");
                event.setCanceled(true);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent.class,listener);
        try {
            h.assertTrue(useItemAt(h,target,owner,stack)==net.minecraft.world.InteractionResult.FAIL,"Protection cancellation ignored");
        } finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
        h.assertTrue(intercepted[0],"Player placement bypassed the protection event");
        h.assertTrue(h.getBlockState(target).isAir()&&stack.getCount()==2,"Cancelled placement changed block or consumed cable");
        h.assertTrue(CableOwners.owner(h.getLevel(),absolute)==null,"Cancelled placement retained ownership");
        h.setBlock(target,ModContent.REINFORCED_MATTER_CABLE.get());
        h.assertTrue(CableOwners.owner(h.getLevel(),absolute)==null,"Command placement inherited a cancelled placer");
        h.setBlock(target,Blocks.AIR);
        h.assertTrue(useItemAt(h,target,foreign,new ItemStack(ModContent.RESONANT_MATTER_CABLE.get())).consumesAction(),"Retry placement failed");
        h.assertTrue(foreign.getUUID().equals(CableOwners.owner(h.getLevel(),absolute)),"Retry inherited the cancelled owner");
        h.assertTrue(!arm(h.getBlockState(anchor),net.minecraft.core.Direction.EAST),"Foreign retry connected to owner cable");
        h.setBlock(target,ModContent.MATTER_CABLE.get());
        h.assertTrue(CableOwners.owner(h.getLevel(),absolute)==null,"Command tier replacement inherited ownership");
        h.succeed();
    }
    private static void portConfigurationVisuals(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var relative=new BlockPos(3,3,3);var pos=h.absolutePos(relative);
        var registries=h.getLevel().registryAccess();
        for(var kind:List.of(MachineKind.DRIVE,MachineKind.NETWORK_INTERFACE,MachineKind.REACTOR))
            for(var front:net.minecraft.core.Direction.Plane.HORIZONTAL) {
                h.setBlock(relative,ModContent.MACHINES.get(kind).get().defaultBlockState().setValue(MachineBlock.FACING,front));
                var machine=h.getBlockEntity(relative,MachineBlockEntity.class);machine.placedBy(owner);
                for(var side:MachinePortMode.Side.values()) {
                    var seen=EnumSet.noneOf(MachinePortMode.class);
                    do {
                        var mode=machine.portMode(side);
                        h.assertTrue(seen.add(mode),"Port cycle repeated a mode before returning to Automatic");
                        var packet=machine.getUpdateTag(registries);
                        h.assertTrue((packet.getIntOr("port_visuals",-1) >>> (4*side.ordinal()) & 15)==mode.ordinal(),
                                "Port update omitted "+kind+" "+side+" "+mode);
                        var client=new MachineBlockEntity(pos,machine.getBlockState());
                        client.handleUpdateTag(net.minecraft.world.level.storage.TagValueInput.create(
                                net.minecraft.util.ProblemReporter.DISCARDING,registries,packet));
                        var reloaded=new MachineBlockEntity(pos,machine.getBlockState());
                        reloaded.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                                net.minecraft.util.ProblemReporter.DISCARDING,registries,machine.saveCustomOnly(registries)));
                        for(var face:MachinePortMode.Side.values()) {
                            h.assertTrue(client.portMode(face)==machine.portMode(face),"Client port visual differs from server configuration");
                            h.assertTrue(reloaded.portMode(face)==machine.portMode(face),"Reload changed port configuration");
                            h.assertTrue(client.portMode(face.world(front))==machine.portMode(face),"Rotated world face shows wrong port mode");
                        }
                        h.assertTrue(machine.cyclePort(side),"Server rejected supported port configuration");
                    } while(machine.portMode(side)!=MachinePortMode.AUTOMATIC);
                    h.assertTrue(seen.contains(MachinePortMode.DISABLED)&&seen.contains(MachinePortMode.MATTER),
                            "Port cycle omitted disabled or matter mode");
                }
                h.setBlock(relative,Blocks.AIR);
            }
        h.succeed();
    }
    private static boolean arm(net.minecraft.world.level.block.state.BlockState state,net.minecraft.core.Direction side) {
        return state.getValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(side));
    }
    private static boolean flange(net.minecraft.world.level.block.state.BlockState state,net.minecraft.core.Direction side) {
        return state.getValue(MatterCableBlock.DEVICE_BY_DIRECTION.get(side));
    }
    private static void deviceArms(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        var west=net.minecraft.core.Direction.WEST;var east=net.minecraft.core.Direction.EAST;
        placeItem(h,new BlockPos(2,2,2),owner,new ItemStack(ModContent.MACHINES.get(MachineKind.DRIVE).get()));
        placeItem(h,new BlockPos(3,2,2),owner,new ItemStack(ModContent.MATTER_CABLE.get()));
        placeItem(h,new BlockPos(4,2,2),owner,new ItemStack(ModContent.REINFORCED_MATTER_CABLE.get()));
        h.runAtTickTime(5,()-> {
            var first=h.getBlockState(new BlockPos(3,2,2));var second=h.getBlockState(new BlockPos(4,2,2));
            h.assertTrue(arm(first,west)&&flange(first,west),"Arm into the Drive has no flange");
            h.assertTrue(arm(first,east)&&!flange(first,east),"Arm into another cable grew a flange");
            h.assertTrue(arm(second,west)&&!flange(second,west),"Cable-to-cable arm grew a flange");
            for(var side:net.minecraft.core.Direction.values()) {
                h.assertTrue(arm(first,side)||!flange(first,side),"Flange without an arm on "+side);
                h.assertTrue(arm(second,side)||!flange(second,side),"Flange without an arm on "+side);
            }
            var level=h.getLevel();var firstPos=h.absolutePos(new BlockPos(3,2,2));var secondPos=h.absolutePos(new BlockPos(4,2,2));
            var plugged=first.getShape(level,firstPos).bounds();var plain=second.getShape(level,secondPos).bounds();
            h.assertTrue(plugged.minX==0&&plugged.minY==5/16D&&plugged.maxY==11/16D,"Outline misses the device flange");
            h.assertTrue(plain.minX==0&&plain.minY==6/16D&&plain.maxY==10/16D,"Cable-to-cable outline changed");
            // Structure rotation and mirroring carry the flange with its arm.
            var turned=first.rotate(Rotation.CLOCKWISE_90);
            h.assertTrue(arm(turned,net.minecraft.core.Direction.NORTH)&&flange(turned,net.minecraft.core.Direction.NORTH)
                    &&arm(turned,net.minecraft.core.Direction.SOUTH)&&!flange(turned,net.minecraft.core.Direction.SOUTH)
                    &&!arm(turned,west)&&!flange(turned,west),"Rotation separated a flange from its arm");
            var mirrored=first.mirror(net.minecraft.world.level.block.Mirror.FRONT_BACK);
            h.assertTrue(arm(mirrored,east)&&flange(mirrored,east)&&arm(mirrored,west)&&!flange(mirrored,west),
                    "Mirroring separated a flange from its arm");
            // The neighbouring device goes away: arm and flange go together.
            h.setBlock(new BlockPos(2,2,2),Blocks.AIR);
        });
        h.runAtTickTime(8,()-> {
            var first=h.getBlockState(new BlockPos(3,2,2));
            h.assertTrue(!arm(first,west)&&!flange(first,west),"Removed Drive left an arm or a flange");
            h.assertTrue(arm(first,east)&&!flange(first,east),"Unrelated arm changed");
            h.succeed();
        });
    }
    private static void driveActivity(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var pos=new BlockPos(2,2,2);
        var drive=place(h,pos,MachineKind.DRIVE,owner);drive.serverTick();
        h.assertTrue(!h.getBlockState(pos).getValue(MachineBlock.ACTIVE),"Empty Drive shows activity");
        drive.setItem(MachineCellAccess.slotForBay(MachineKind.DRIVE,4),new ItemStack(ModContent.MATTER_CELL_256K.get()));drive.serverTick();
        h.assertTrue(h.getBlockState(pos).getValue(MachineBlock.ACTIVE),"Readable cell did not light the activity bar");
        var unknown=new net.minecraft.nbt.CompoundTag();unknown.putInt("schema",99);
        var locked=new ItemStack(ModContent.MATTER_CELL_64K.get());locked.set(MatterCellData.TYPE.get(),new MatterCellData(unknown));
        drive.setItem(MachineCellAccess.slotForBay(MachineKind.DRIVE,4),locked);drive.serverTick();
        h.assertTrue(!h.getBlockState(pos).getValue(MachineBlock.ACTIVE),"Only an unreadable cell is mounted, yet the bar is lit");
        drive.setItem(MachineCellAccess.slotForBay(MachineKind.DRIVE,0),new ItemStack(ModContent.MATTER_CELL_64K.get()));drive.serverTick();
        h.assertTrue(h.getBlockState(pos).getValue(MachineBlock.ACTIVE),"One readable cell beside an unreadable one must light the bar");
        for(int bay=0;bay<MachineCellAccess.DRIVE_BAYS;bay++)drive.setItem(MachineCellAccess.slotForBay(MachineKind.DRIVE,bay),ItemStack.EMPTY);
        drive.serverTick();
        h.assertTrue(!h.getBlockState(pos).getValue(MachineBlock.ACTIVE),"Emptied Drive kept its activity bar lit");
        h.succeed();
    }
    private static boolean lit(GameTestHelper h,BlockPos pos) {return h.getBlockState(pos).getValue(MachineBlock.ACTIVE);}
    /** A Terminal lights its screen and a Network Interface its lens exactly while their matter network is ready. */
    private static void networkLights(GameTestHelper h) {
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        // The Drive stands behind the Terminal, the only side a Terminal connects on, and beside the Interface.
        var terminalPos=new BlockPos(2,2,1);var interfacePos=new BlockPos(3,2,2);var lonePos=new BlockPos(5,2,2);
        var drive=place(h,new BlockPos(2,2,2),MachineKind.DRIVE,owner);
        var terminal=place(h,terminalPos,MachineKind.TERMINAL,owner);
        var link=place(h,interfacePos,MachineKind.NETWORK_INTERFACE,owner);
        var lone=place(h,lonePos,MachineKind.TERMINAL,owner);
        Runnable tick=()-> {drive.serverTick();terminal.serverTick();link.serverTick();lone.serverTick();};
        tick.run();
        h.assertTrue(!lit(h,terminalPos)&&!lit(h,interfacePos),"A network without a readable cell lit a Terminal or an Interface");
        drive.setItem(0,new ItemStack(ModContent.MATTER_CELL_64K.get()));tick.run();
        h.assertTrue(lit(h,terminalPos),"A ready network did not light the Terminal screen");
        h.assertTrue(lit(h,interfacePos),"A ready network did not light the Network Interface lens");
        h.assertTrue(!lit(h,lonePos),"A Terminal without a network lit its screen");
        drive.setItem(0,ItemStack.EMPTY);tick.run();
        h.assertTrue(!lit(h,terminalPos)&&!lit(h,interfacePos),"Removing the last cell left a Terminal or an Interface lit");
        h.succeed();
    }
    /** Reactor Glass is glass to the open-air rule as well: an air source below a glass roof still sees the sky. */
    private static void airBelowGlass(GameTestHelper h) {
        var r=rig(h,MachineKind.REACTOR);h.setBlock(3,4,2,ModContent.REACTOR_GLASS.get());
        r.machine.selectRecipe("rematter:air_oxygen");r.machine.requestBatch();
        h.succeedWhen(()->h.assertTrue(r.drive.matterStore().amount(BuiltinChemistry.OXYGEN)==200,"Air source below a Reactor Glass roof did not collect"));
    }
    /** Light and ambient shade pass Reactor Glass as they pass vanilla glass, so a hall is not a dark box. */
    private static void reactorGlassLight(GameTestHelper h) {
        var pos=new BlockPos(2,2,2);h.setBlock(pos,ModContent.REACTOR_GLASS.get());
        var state=h.getBlockState(pos);
        h.assertTrue(!state.canOcclude()&&!state.isSolidRender(),"Reactor Glass hides the faces behind it");
        h.assertTrue(state.propagatesSkylightDown()&&state.getLightDampening()==0,"Reactor Glass dims the light that passes it");
        h.assertTrue(state.getShadeBrightness(h.getLevel(),h.absolutePos(pos))==1F,"Reactor Glass shades the blocks behind it");
        var vanilla=Blocks.GLASS.defaultBlockState();
        h.assertTrue(state.getLightDampening()==vanilla.getLightDampening()
                &&state.getShadeBrightness(h.getLevel(),h.absolutePos(pos))==vanilla.getShadeBrightness(h.getLevel(),h.absolutePos(pos)),
                "Reactor Glass and vanilla glass treat light differently");
        h.succeed();
    }
    private static void machineOcclusion(GameTestHelper h) {
        for(var kind:MachineKind.values()) {
            var block=ModContent.MACHINES.get(kind).get();
            for(var front:net.minecraft.core.Direction.Plane.HORIZONTAL) {
                var state=block.defaultBlockState().setValue(MachineBlock.FACING,front);
                var shape=state.getShape(h.getLevel(),BlockPos.ZERO);
                if(MachineBlock.sealedCube(kind)) {
                    h.assertTrue(net.minecraft.world.level.block.Block.isShapeFullBlock(shape)&&state.isSolidRender()&&state.canOcclude(),
                            kind+" is not a closed cube");
                } else if(MachineBlock.alcoveChassis(kind)) {
                    h.assertTrue(state.canOcclude()&&!state.isSolidRender()&&state.useShapeForLightOcclusion()
                            &&state.propagatesSkylightDown(),kind+" does not occlude by its chassis");
                    for(var side:net.minecraft.core.Direction.values()) {
                        boolean open=side==front||side==net.minecraft.core.Direction.UP;
                        h.assertTrue(net.minecraft.world.level.block.Block.isShapeFullBlock(state.getFaceOcclusionShape(side))!=open,
                                kind+" facing "+front+": wrong occlusion on "+side);
                    }
                    var bounds=shape.bounds();
                    h.assertTrue(bounds.getXsize()==1&&bounds.getYsize()==1&&bounds.getZsize()==1,kind+" outline leaves its footprint");
                    // The alcove is open at the front beside the showpiece and closed by the rear wall.
                    var side=front.getClockWise();
                    var open=new net.minecraft.world.phys.Vec3(.5+front.getStepX()*.47+side.getStepX()*.3,.8,.5+front.getStepZ()*.47+side.getStepZ()*.3);
                    var wall=new net.minecraft.world.phys.Vec3(.5-front.getStepX()*.3+side.getStepX()*.3,.8,.5-front.getStepZ()*.3+side.getStepZ()*.3);
                    h.assertTrue(shape.toAabbs().stream().noneMatch(box->box.contains(open)),kind+" facing "+front+": the alcove is closed");
                    h.assertTrue(shape.toAabbs().stream().anyMatch(box->box.contains(wall)),kind+" facing "+front+": the rear wall is missing");
                    if(kind==MachineKind.REACTOR){
                    var gap=machinePoint(front,8,8.5F,5.5F);
                    h.assertTrue(shape.toAabbs().stream().noneMatch(box->box.contains(gap)),
                            "Reactor facing "+front+": the retired flask still blocks the gap between columns");
                    for(float x:new float[]{4.5F,11.5F}) {
                        var column=machinePoint(front,x,8.5F,5.5F);
                        h.assertTrue(shape.toAabbs().stream().anyMatch(box->box.contains(column)),
                                "Reactor facing "+front+": sight column is missing from its outline");
                    }
                    }
                } else {
                    h.assertTrue(!state.canOcclude()&&!state.isSolidRender(),kind+" bench housing must not occlude");
                }
            }
        }
        h.succeed();
    }

    private static net.minecraft.world.phys.Vec3 machinePoint(net.minecraft.core.Direction front,float x,float y,float z) {
        var left=front.getClockWise();
        return new net.minecraft.world.phys.Vec3(.5+left.getStepX()*(x/16-.5)-front.getStepX()*(z/16-.5),
                y/16,.5+left.getStepZ()*(x/16-.5)-front.getStepZ()*(z/16-.5));
    }
    /** One condition object of a block state file: plain properties, or nested OR / AND lists. */
    private static boolean selects(com.google.gson.JsonObject when,net.minecraft.world.level.block.state.BlockState state,String file) {
        for(var entry:when.entrySet()) {
            if(entry.getKey().equals("OR")||entry.getKey().equals("AND")) {
                boolean any=false,all=true;
                for(var nested:entry.getValue().getAsJsonArray()) {
                    boolean match=selects(nested.getAsJsonObject(),state,file);any|=match;all&=match;
                }
                if(!(entry.getKey().equals("OR")?any:all))return false;
                continue;
            }
            var property=state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if(property==null)throw new IllegalStateException(file+" tests the unknown property "+entry.getKey());
            boolean match=false;
            for(String value:entry.getValue().getAsString().split("\\|")) {
                var parsed=property.getValue(value);
                if(parsed.isEmpty())throw new IllegalStateException(file+" tests "+entry.getKey()+" for the unknown value "+value);
                match|=state.getValue(property).equals(parsed.get());
            }
            if(!match)return false;
        }
        return true;
    }
    /** The client would show a missing model for a block whose file names a property the block does not have. */
    private static void blockStateFiles(GameTestHelper h) {
        int states=0;
        for(var holder:ModContent.BLOCKS.getEntries()) {
            var block=holder.get();String file="blockstates/"+holder.getId().getPath()+".json";
            com.google.gson.JsonObject json;
            try(var stream=ReMatter.class.getResourceAsStream("/assets/rematter/"+file)) {
                h.assertTrue(stream!=null,"Missing "+file);
                json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch(java.io.IOException exception) { throw new IllegalStateException(exception); }
            for(var state:block.getStateDefinition().getPossibleStates()) {
                int models=0;
                if(json.has("variants"))for(var variant:json.getAsJsonObject("variants").entrySet()) {
                    var when=new com.google.gson.JsonObject();
                    if(!variant.getKey().isEmpty())for(String pair:variant.getKey().split(",")) {
                        String[] parts=pair.split("=");when.addProperty(parts[0],parts[1]);
                    }
                    if(selects(when,state,file))models++;
                }
                if(json.has("multipart"))for(var part:json.getAsJsonArray("multipart")) {
                    var when=part.getAsJsonObject().getAsJsonObject("when");
                    if(when==null||selects(when,state,file))models++;
                }
                h.assertTrue(models>0,file+" shows nothing for "+state);
                h.assertTrue(!json.has("variants")||models==1,file+" has "+models+" variants for "+state);
                states++;
            }
        }
        ReMatter.LOGGER.info("ReMatter block state files cover {} registered states",states);
        h.succeed();
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
