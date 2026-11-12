package dev.latrox.rematter.content;

import com.mojang.authlib.GameProfile;
import dev.latrox.rematter.celltransfer.*;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.network.*;
import dev.latrox.rematter.primal.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Manual, filtered multi-Cell resource transfers over actual server blocks and persistent item components. */
@EventBusSubscriber(modid="rematter")
public final class CellTransferStationGameTests {
    private static final SubstanceKey IRON=SubstanceKey.of("rematter:iron",Phase.SOLID),WATER=SubstanceKey.of("rematter:water",Phase.LIQUID),GAS=SubstanceKey.of("future:unknown_gas",Phase.GAS),COPPER=SubstanceKey.of("rematter:copper",Phase.SOLID);
    private static final Map<String,Consumer<GameTestHelper>> TESTS=Map.of(
            "cell_station_selected_multi_sources_and_partitions",CellTransferStationGameTests::ordinary,
            "cell_station_primal_selection_is_explicit",CellTransferStationGameTests::primal,
            "cell_station_aliases_unknowns_and_cross_kind_are_safe",CellTransferStationGameTests::rejected,
            "cell_station_configuration_rejects_stale_foreign_and_distant_actions",CellTransferStationGameTests::permissions,
            "cell_station_portable_contents_and_visual_privacy",CellTransferStationGameTests::portable,
            "cell_station_type_full_target_accepts_only_existing_keys",CellTransferStationGameTests::types,
            "cell_station_long_unknown_keys_survive_without_ui_truncation",CellTransferStationGameTests::longKey,
            "cell_station_late_permission_change_rolls_back_all_pairs",CellTransferStationGameTests::rollback,
            "cell_station_mixed_channels_roll_back_together",CellTransferStationGameTests::mixedRollback,
            "cell_station_partitions_reserve_before_unrestricted_targets",CellTransferStationGameTests::partitionPriority);
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath("rematter_tests",name);}
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event){event.register(Registries.TEST_FUNCTION,helper->TESTS.forEach((name,test)->helper.register(id(name),test)));}
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event){var env=event.registerEnvironment(id("cell_station_environment"));TESTS.forEach((name,test)->event.registerTest(id(name),new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,id(name)),new TestData<>(env,id("empty"),300,0,true,Rotation.NONE,false,1,1,true,2))));}
    private static CellTransferStationBlockEntity place(GameTestHelper h,BlockPos relative,Player owner){
        h.setBlock(relative,ModContent.CELL_TRANSFER_STATION.get());var station=h.getBlockEntity(relative,CellTransferStationBlockEntity.class);station.placedBy(owner);
        owner.setPos(station.getBlockPos().getX()+.5,station.getBlockPos().getY(),station.getBlockPos().getZ()+.5);return station;
    }
    private static ItemStack cell(Map<SubstanceKey,Long> contents){
        var stack=new ItemStack(ModContent.MATTER_CELL_64K.get());var store=new MatterInventory(64000,512);
        if(!store.apply(Map.of(),contents))throw new IllegalArgumentException("Cell fixture exceeds capacity");
        stack.set(MatterCellData.TYPE.get(),MatterCellData.from(store));return stack;
    }
    private static ItemStack primal(long amount){var stack=new ItemStack(PrimalContent.PRIMAL_CELL_16K.get());stack.set(PrimalCellData.TYPE.get(),PrimalCellData.from(new PrimalCellData.State(UUID.randomUUID(),amount,0,Map.of())));return stack;}
    private static long amount(ItemStack cell,SubstanceKey key){return ((MatterCellItem)cell.getItem()).open(cell).orElseThrow().amount(key);}
    private static boolean action(CellTransferStationBlockEntity station,Player player,CellTransferConfigPayload.Action action,int slot,SubstanceKey key,boolean value){return station.configure(player,new CellTransferConfigPayload(1,station.revision(),action,slot,key,value));}
    private static void ordinary(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var station=place(h,new BlockPos(2,2,2),owner);
        station.setItem(0,cell(Map.of(IRON,1000L,WATER,200L)));station.setItem(1,cell(Map.of(IRON,300L,GAS,100L)));station.setItem(2,cell(Map.of(COPPER,100L)));
        station.setItem(3,cell(Map.of()));station.setItem(4,cell(Map.of()));station.setItem(5,cell(Map.of()));
        h.assertTrue(action(station,owner,CellTransferConfigPayload.Action.CELL_TOGGLE,3,IRON,false),"Could not configure Iron target");
        h.assertTrue(action(station,owner,CellTransferConfigPayload.Action.CELL_TOGGLE,4,WATER,false),"Could not configure liquid target");
        h.assertTrue(action(station,owner,CellTransferConfigPayload.Action.GLOBAL_CLEAR,-1,null,false),"Could not clear selection");
        action(station,owner,CellTransferConfigPayload.Action.GLOBAL_TOGGLE,-1,IRON,false);action(station,owner,CellTransferConfigPayload.Action.GLOBAL_TOGGLE,-1,WATER,false);
        var before=station.saveCustomOnly(h.getLevel().registryAccess());var preview=station.snapshot(1);
        h.assertTrue(before.equals(station.saveCustomOnly(h.getLevel().registryAccess())),"Preview changed persisted resources or policy");
        h.assertTrue(preview.movableOrdinary()==1500&&preview.movablePrimal()==0,"Wrong selected preview");
        var sourceId=station.getItem(0).get(MatterCellData.TYPE.get()).resolve(64000).orElseThrow().cellId();
        h.assertTrue(station.transfer(owner,station.revision())==1500,"Selected multi-source quantity was not exact");
        h.assertTrue(amount(station.getItem(3),IRON)==1300&&amount(station.getItem(4),WATER)==200,"Target Cell partitions were ignored");
        h.assertTrue(amount(station.getItem(1),GAS)==100&&amount(station.getItem(2),COPPER)==100,"Unselected resources changed");
        h.assertTrue(((MatterCellItem)station.getItem(5).getItem()).open(station.getItem(5)).orElseThrow().total()==0,"Later target received duplicate resources");
        h.assertTrue(sourceId.equals(station.getItem(0).get(MatterCellData.TYPE.get()).resolve(64000).orElseThrow().cellId()),"Source identity changed");
        h.assertTrue(CellPartitionData.read(station.getItem(3)).orElseThrow().allowed().equals(Set.of(IRON)),"Cell partition was lost");h.succeed();
    }
    private static void primal(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var station=place(h,new BlockPos(2,2,2),owner);
        station.setItem(0,cell(Map.of(IRON,400L)));station.setItem(1,primal(1000));station.setItem(2,primal(2000));station.setItem(3,cell(Map.of()));station.setItem(4,primal(0));station.setItem(5,primal(0));
        action(station,owner,CellTransferConfigPayload.Action.GLOBAL_CLEAR,-1,null,false);action(station,owner,CellTransferConfigPayload.Action.GLOBAL_TOGGLE,-1,IRON,false);
        h.assertTrue(station.transfer(owner,station.revision())==400,"Iron-only transfer changed another channel");
        h.assertTrue(PrimalCells.view(station.getItem(1)).amount()==1000&&PrimalCells.view(station.getItem(2)).amount()==2000,"Primal moved without its explicit selection");
        h.assertTrue(action(station,owner,CellTransferConfigPayload.Action.GLOBAL_PRIMAL,-1,null,true),"Could not select Primal channel");
        h.assertTrue(station.snapshot(1).movablePrimal()==3000&&station.transfer(owner,station.revision())==3000,"Primal multi-source route failed");
        h.assertTrue(PrimalCells.view(station.getItem(4)).amount()==3000&&PrimalCells.view(station.getItem(5)).amount()==0,"Primal quantity duplicated across targets");
        var before=station.getItem(4).getComponentsPatch();h.assertTrue(!action(station,owner,CellTransferConfigPayload.Action.CELL_CLEAR,4,null,false),"Primal accepted an ordinary partition");
        h.assertTrue(before.equals(station.getItem(4).getComponentsPatch()),"Rejected Primal formatting changed contents");h.succeed();
    }
    private static void rejected(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var station=place(h,new BlockPos(2,2,2),owner);var a=cell(Map.of(IRON,100L));
        station.setItem(0,a);station.setItem(1,a.copy());station.setItem(3,cell(Map.of()));
        h.assertTrue(station.snapshot(1).status().equals("same_cell")&&station.transfer(owner,station.revision())==0,"Duplicate Cell identity transferred twice");
        station.setItem(1,ItemStack.EMPTY);var target=new ItemStack(ModContent.MATTER_CELL_64K.get());station.setItem(3,target);station.setItem(4,target);
        h.assertTrue(station.getItem(4).isEmpty(),"One physical stack was installed into two target slots");
        station.setItem(3,primal(0));h.assertTrue(station.snapshot(1).status().equals("type_mismatch")&&station.transfer(owner,station.revision())==0,"Ordinary contents crossed into Primal");
        var raw=new CompoundTag();raw.putInt("schema",99);raw.putLong("future",34567);var locked=cell(Map.of());locked.set(MatterCellData.TYPE.get(),new MatterCellData(raw));station.setItem(0,locked);
        h.assertTrue(station.snapshot(1).locked()&&station.transfer(owner,station.revision())==0,"Unknown Cell schema was not protected");
        h.assertTrue(raw.equals(locked.get(MatterCellData.TYPE.get()).copyTag()),"Unknown Cell contents changed");
        var unknownStation=new CompoundTag();unknownStation.putInt("schema",72);unknownStation.putString("future_station","preserve");station.loadWithComponents(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess(),unknownStation));station.placedBy(owner);
        h.assertTrue(station.locked(),"Unknown station schema became writable");var drop=((CellTransferStationBlock)station.getBlockState().getBlock()).storedDrop(station,h.getLevel().registryAccess());
        h.assertTrue(unknownStation.equals(drop.get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId()),"Opaque station data was not preserved");h.succeed();
    }
    private static void permissions(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var station=place(h,new BlockPos(2,2,2),owner);station.setItem(0,cell(Map.of(IRON,10L)));station.setItem(3,cell(Map.of()));
        long old=station.revision();station.setItem(0,cell(Map.of(WATER,20L)));
        h.assertTrue(!station.configure(owner,new CellTransferConfigPayload(1,old,CellTransferConfigPayload.Action.GLOBAL_TOGGLE,-1,IRON,false)),"Stale selection applied to a replacement Cell");
        h.assertTrue(station.transfer(owner,old)==0&&amount(station.getItem(0),WATER)==20,"Stale Transfer changed replacement contents");
        var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"ForeignCellTest"));stranger.setPos(owner.getX(),owner.getY(),owner.getZ());
        h.assertTrue(!action(station,stranger,CellTransferConfigPayload.Action.GLOBAL_CLEAR,-1,null,false)&&station.transfer(stranger,station.revision())==0,"Foreign actor changed private station");
        owner.setPos(owner.getX()+20,owner.getY(),owner.getZ());h.assertTrue(!action(station,owner,CellTransferConfigPayload.Action.GLOBAL_CLEAR,-1,null,false),"Distant actor configured station");
        h.succeed();
    }
    private static void portable(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var station=place(h,new BlockPos(2,2,2),owner);
        station.setItem(0,cell(Map.of()));station.setItem(1,cell(Map.of(IRON,64000L)));station.setItem(2,primal(0));station.setItem(3,cell(Map.of(WATER,123L)));station.setItem(4,primal(1000));station.setItem(5,primal(16000));
        action(station,owner,CellTransferConfigPayload.Action.CELL_TOGGLE,3,WATER,false);action(station,owner,CellTransferConfigPayload.Action.GLOBAL_CLEAR,-1,null,false);action(station,owner,CellTransferConfigPayload.Action.GLOBAL_TOGGLE,-1,WATER,false);
        var tag=station.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(tag.keySet().equals(Set.of("cell_mask","cell_tiers","cell_statuses"))&&tag.getIntOr("cell_mask",0)==63,"Public renderer update leaked private state or lost occupied slots");
        h.assertTrue(station.visibleCellTier(2)==5&&station.visibleCellStatus(1)==CellStatus.FULL&&station.visibleCellStatus(4)==CellStatus.STORED,"Installed Cell tier/lamp differs from quantity");
        var drops=Block.getDrops(station.getBlockState(),h.getLevel(),station.getBlockPos(),station);h.assertTrue(drops.size()==1,"Station did not make one portable drop");var data=drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA);
        h.assertTrue(data!=null&&!data.contains("owner"),"Portable station retained old owner");var next=place(h,new BlockPos(5,2,2),owner);
        h.assertTrue(data.loadInto(next,h.getLevel().registryAccess()),"Portable station did not restore");next.placedBy(owner);
        for(int i=0;i<6;i++)h.assertTrue(ItemStack.isSameItemSameComponents(station.getItem(i),next.getItem(i)),"Portable Cell contents/partition changed at slot "+i);
        h.assertTrue(next.selection().equals(station.selection()),"Station selection was not retained");
        h.assertTrue(((CellTransferStationBlock)station.getBlockState().getBlock()).getCloneItemStack(h.getLevel(),station.getBlockPos(),station.getBlockState(),true,owner).isEmpty(),"Creative data pick copied stored Cells");h.succeed();
    }
    private static void types(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var station=place(h,new BlockPos(2,2,2),owner);var keys=new TreeMap<SubstanceKey,Long>();
        for(int i=0;i<512;i++)keys.put(SubstanceKey.of("future:key_"+i,Phase.SOLID),1L);
        var matching=SubstanceKey.of("future:key_511",Phase.SOLID);var newKey=SubstanceKey.of("future:new_key",Phase.GAS);
        station.setItem(0,cell(Map.of(matching,9L,newKey,11L)));station.setItem(3,cell(keys));
        h.assertTrue(station.snapshot(1).movableOrdinary()==9&&station.transfer(owner,station.revision())==9,"Type-full target did not accept exactly its existing key");
        h.assertTrue(amount(station.getItem(0),newKey)==11&&amount(station.getItem(3),matching)==10,"Type limit lost or duplicated a resource");h.succeed();
    }
    private static void longKey(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var station=place(h,new BlockPos(2,2,2),owner);var key=SubstanceKey.of("future:"+"u".repeat(300),Phase.GAS);
        station.setItem(0,cell(Map.of(key,500L)));station.setItem(3,cell(Map.of()));var preview=station.snapshot(1);
        h.assertTrue(preview.partial()&&preview.available().isEmpty()&&preview.movableOrdinary()==500,"Long unknown ID was truncated or blocked instead of marked outside selector bounds");
        var before=station.getItem(0).getComponentsPatch();h.assertTrue(!action(station,owner,CellTransferConfigPayload.Action.CELL_TO_CONTENTS,0,null,false),"Oversize partition key was accepted");h.assertTrue(before.equals(station.getItem(0).getComponentsPatch()),"Rejected oversize formatting changed Cell");
        h.assertTrue(station.transfer(owner,station.revision())==500&&amount(station.getItem(3),key)==500,"ALL transfer did not retain full unknown key");h.succeed();
    }
    private static void rollback(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var station=place(h,new BlockPos(2,2,2),owner);
        station.setItem(0,cell(Map.of(IRON,100L)));station.setItem(1,cell(Map.of(WATER,200L)));station.setItem(3,cell(Map.of()));
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(owner.getUUID(),"CellRollback")){
            @Override public boolean isSpectator(){return amount(station.getItem(0),IRON)==0&&amount(station.getItem(1),WATER)==0;}
        };
        actor.setPos(owner.getX(),owner.getY(),owner.getZ());var before=station.saveCustomOnly(h.getLevel().registryAccess());
        h.assertTrue(station.snapshot(1).movableOrdinary()==300,"Rollback fixture did not plan two source pairs");
        h.assertTrue(station.transfer(actor,station.revision())==0,"Transfer committed after permission changed between pairs");
        h.assertTrue(before.equals(station.saveCustomOnly(h.getLevel().registryAccess())),"Aborted multi-pair transfer retained an intermediate Cell state");h.succeed();
    }

    private static void mixedRollback(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var station=place(h,new BlockPos(2,2,2),owner);
        station.setItem(0,cell(Map.of(IRON,100L)));station.setItem(1,primal(1000));station.setItem(3,cell(Map.of()));station.setItem(4,primal(0));
        var ordinaryBefore=station.getItem(0).get(MatterCellData.TYPE.get()).copyTag();var primalBefore=station.getItem(1).get(PrimalCellData.TYPE.get()).copyTag();
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new GameProfile(owner.getUUID(),"MixedRollback")){
            @Override public boolean isSpectator(){return amount(station.getItem(0),IRON)==0&&PrimalCells.view(station.getItem(1)).amount()==0;}
        };
        actor.setPos(owner.getX(),owner.getY(),owner.getZ());var before=station.saveCustomOnly(h.getLevel().registryAccess());var preview=station.snapshot(1);
        h.assertTrue(preview.movableOrdinary()==100&&preview.movablePrimal()==1000,"Mixed rollback fixture did not plan both channels");
        h.assertTrue(station.transfer(actor,station.revision())==0,"Mixed transfer committed after its late permission failure");
        h.assertTrue(before.equals(station.saveCustomOnly(h.getLevel().registryAccess())),"Mixed abort retained partial source or target resources");
        h.assertTrue(ordinaryBefore.equals(station.getItem(0).get(MatterCellData.TYPE.get()).copyTag()),"Ordinary identity, credits or contents changed on mixed abort");
        h.assertTrue(primalBefore.equals(station.getItem(1).get(PrimalCellData.TYPE.get()).copyTag()),"Primal identity, receipts or contents changed on mixed abort");h.succeed();
    }

    private static void partitionPriority(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var station=place(h,new BlockPos(2,2,2),owner);
        station.setItem(0,cell(Map.of(IRON,100L)));station.setItem(1,cell(Map.of(COPPER,100L)));
        station.setItem(3,cell(Map.of(WATER,63900L)));station.setItem(4,cell(Map.of(IRON,63900L)));
        action(station,owner,CellTransferConfigPayload.Action.CELL_TOGGLE,4,IRON,false);
        h.assertTrue(station.snapshot(1).movableOrdinary()==200,"General target consumed the resource reserved for a fixed partition");
        h.assertTrue(station.transfer(owner,station.revision())==200,"Actual partition-priority route differed from its preview");
        h.assertTrue(amount(station.getItem(3),COPPER)==100&&amount(station.getItem(4),IRON)==64000,"Restrictive target priority lost flexible capacity");h.succeed();
    }

}
