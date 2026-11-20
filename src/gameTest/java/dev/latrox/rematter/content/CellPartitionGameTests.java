package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Physical Drive Cells retain insertion policies through routing, remounting and native item serialization. */
@EventBusSubscriber(modid=ReMatter.MOD_ID)
public final class CellPartitionGameTests {
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath("rematter_tests",name);}
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event){
        event.register(Registries.TEST_FUNCTION,h->{h.register(id("partitioned_drive_routing"),CellPartitionGameTests::routing);h.register(id("partitioned_cell_credit_and_raw_protection"),CellPartitionGameTests::protectedData);h.register(id("partition_live_policy_remount"),CellPartitionGameTests::livePolicy);h.register(id("partition_conflicting_quantity_protection"),CellPartitionGameTests::quantityConflict);});
    }
    @SubscribeEvent public static void register(RegisterGameTestsEvent event){
        var env=event.registerEnvironment(id("cell_partitions"));
        for(String name:List.of("partitioned_drive_routing","partitioned_cell_credit_and_raw_protection","partition_live_policy_remount","partition_conflicting_quantity_protection"))event.registerTest(id(name),new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,id(name)),
                new TestData<>(env,id("empty"),200,0,true,Rotation.NONE,false,1,1,true,2)));
    }
    private static ItemStack cell(Map<SubstanceKey,Long> contents,SubstanceKey...allowed){
        var item=new ItemStack(ModContent.MATTER_CELL_64K.get());var inv=new MatterInventory(64000,512);contents.forEach(inv::insert);
        item.set(MatterCellData.TYPE.get(),MatterCellData.from(inv));item.set(CellPartitionData.TYPE.get(),CellPartitionData.allowOnly(List.of(allowed)));return item;
    }
    private static void routing(GameTestHelper h){
        var player=h.makeMockPlayer(GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(2,2,2));
        h.getLevel().setBlock(pos,ModContent.MACHINES.get(MachineKind.DRIVE).get().defaultBlockState(),3);
        var drive=(MachineBlockEntity)h.getLevel().getBlockEntity(pos);drive.placedBy(player);
        var unknown=new SubstanceKey(SubstanceId.parse("uninstalled:retained_vapour"),Phase.GAS);
        var first=cell(Map.of(BuiltinChemistry.COPPER,700L,unknown,11L),BuiltinChemistry.IRON);
        var second=cell(Map.of(),BuiltinChemistry.COPPER);
        drive.setItem(drive.cellSlotForBay(0),first);drive.setItem(drive.cellSlotForBay(1),second);
        h.assertTrue(drive.matterStore().apply(Map.of(),Map.of(BuiltinChemistry.COPPER,100L)),"No eligible target Cell was found");
        h.assertTrue(((MatterCellItem)first.getItem()).open(drive.getItem(drive.cellSlotForBay(0))).orElseThrow().amount(BuiltinChemistry.COPPER)==700,"Existing disallowed type attracted new output");
        h.assertTrue(((MatterCellItem)second.getItem()).open(drive.getItem(drive.cellSlotForBay(1))).orElseThrow().amount(BuiltinChemistry.COPPER)==100,"Copper missed its partitioned Cell");
        h.assertTrue(drive.matterStore().apply(Map.of(BuiltinChemistry.COPPER,800L),Map.of(BuiltinChemistry.IRON,50L)),"Disallowed existing stock could not be withdrawn");
        h.assertTrue(drive.matterStore().amount(unknown)==11,"Unknown definition was lost");
        var removed=drive.removeItem(drive.cellSlotForBay(1),1);removed.set(CellPartitionData.TYPE.get(),CellPartitionData.allowOnly(List.of(unknown)));
        var context=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded=ItemStack.OPTIONAL_CODEC.encodeStart(context,removed).getOrThrow();var restored=ItemStack.OPTIONAL_CODEC.parse(context,encoded).getOrThrow();
        h.assertTrue(CellPartitionData.read(restored).orElseThrow().allowed().equals(Set.of(unknown)),"Native item codec lost partition");
        drive.setItem(drive.cellSlotForBay(1),restored);
        h.assertTrue(!drive.matterStore().apply(Map.of(),Map.of(BuiltinChemistry.COPPER,1L)),"Remounted Cell forgot its new filter");
        h.assertTrue(drive.matterStore().apply(Map.of(),Map.of(unknown,50L)),"Unknown but explicitly allowed ID could not be stored");
        h.assertTrue(drive.matterStore().amount(unknown)==61,"Unknown quantity changed after filtering");h.succeed();
    }
    private static void protectedData(GameTestHelper h){
        var item=cell(Map.of(BuiltinChemistry.IRON,100L),BuiltinChemistry.COPPER);var mount=new MatterCellMount();mount.mount(item);var before=item.get(MatterCellData.TYPE.get());
        try(var tx=Transaction.openRoot()){
            h.assertTrue(mount.credit(UUID.randomUUID(),BuiltinChemistry.IRON,1,tx).isEmpty(),"Converter credit bypassed target partition");tx.commit();
        }
        h.assertTrue(item.get(MatterCellData.TYPE.get())==before,"Rejected credit changed Cell identity or quantities");
        var raw=new CompoundTag();raw.putInt("schema",99);raw.putString("future","preserve");item.set(CellPartitionData.TYPE.get(),new CellPartitionData(raw));
        h.assertTrue(((MatterCellItem)item.getItem()).open(item).isEmpty(),"Unknown partition was treated as unrestricted");
        var context=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded=ItemStack.OPTIONAL_CODEC.encodeStart(context,item).getOrThrow();var restored=ItemStack.OPTIONAL_CODEC.parse(context,encoded).getOrThrow();
        h.assertTrue(restored.get(CellPartitionData.TYPE.get()).copyTag().equals(raw),"Unknown policy was normalized");
        h.assertTrue(restored.get(MatterCellData.TYPE.get()).equals(before),"Unknown policy erased stored resources");h.succeed();
    }
    private static void livePolicy(GameTestHelper h){
        var player=h.makeMockPlayer(GameType.SURVIVAL);var pos=new BlockPos(4,2,4);
        h.setBlock(pos,ModContent.MACHINES.get(MachineKind.DRIVE).get());
        var drive=h.getBlockEntity(pos,MachineBlockEntity.class);drive.placedBy(player);
        var unknown=new SubstanceKey(SubstanceId.parse("uninstalled:live_retained_vapour"),Phase.GAS);
        var item=cell(Map.of(BuiltinChemistry.IRON,100L,unknown,11L));item.remove(CellPartitionData.TYPE.get());
        drive.setItem(drive.cellSlotForBay(0),item);
        var original=drive.matterStore();var cachedNetwork=new MatterNetworkInventory(original.members());
        var observed=MachineMatterNetwork.discover(drive);
        h.assertTrue(observed.current(drive,true),"Native network fixture was not current");
        h.assertTrue(original.apply(Map.of(),Map.of(BuiltinChemistry.IRON,25L)),"Initial unfiltered Cell rejected output");
        // A server API may replace a component in-place without removing/reinserting its ItemStack.
        item.set(CellPartitionData.TYPE.get(),CellPartitionData.allowOnly(List.of(BuiltinChemistry.COPPER)));
        h.assertTrue(!original.apply(Map.of(),Map.of(BuiltinChemistry.IRON,1L)),"Old insertion handle bypassed a changed policy");
        h.assertTrue(!original.apply(Map.of(BuiltinChemistry.IRON,1L),Map.of())&&!cachedNetwork.apply(Map.of(BuiltinChemistry.IRON,1L),Map.of()),"Old store/network withdrew before the policy handover");
        var current=drive.matterStore();
        h.assertTrue(current!=original&&!observed.current(drive,true),"Policy remount did not invalidate cached store/network identity");
        h.assertTrue(current.amount(BuiltinChemistry.IRON)==125&&current.amount(unknown)==11,"Safe remount lost committed or unknown stock");
        h.assertTrue(!cachedNetwork.apply(Map.of(BuiltinChemistry.IRON,1L),Map.of()),"Retired cached network could withdraw the same physical stock again");
        try(var tx=Transaction.openRoot()){
            h.assertTrue(drive.creditConvertedMatter(UUID.randomUUID(),BuiltinChemistry.IRON,1,tx).isEmpty(),"Converter credit retained a stale unfiltered mount");tx.commit();
        }
        h.assertTrue(current.apply(Map.of(BuiltinChemistry.IRON,25L),Map.of(BuiltinChemistry.COPPER,10L)),"Current policy blocked withdrawal of existing disallowed stock");
        drive.setChanged();var quantities=item.get(MatterCellData.TYPE.get());
        var raw=new CompoundTag();raw.putInt("schema",99);raw.putString("future","unchanged");
        item.set(CellPartitionData.TYPE.get(),new CellPartitionData(raw));
        h.assertTrue(!current.apply(Map.of(),Map.of(BuiltinChemistry.COPPER,1L)),"Old handle accepted input under an unknown policy");
        h.assertTrue(!drive.storageStatus().equals("ready"),"Unknown live policy did not lock the Drive");drive.setChanged();
        h.assertTrue(item.get(CellPartitionData.TYPE.get()).copyTag().equals(raw)&&item.get(MatterCellData.TYPE.get())==quantities,"Unknown live policy changed raw data");
        try(var tx=Transaction.openRoot()){
            h.assertTrue(drive.creditConvertedMatter(UUID.randomUUID(),BuiltinChemistry.COPPER,1,tx).isEmpty(),"Converter credited an unknown policy");tx.commit();
        }
        item.remove(CellPartitionData.TYPE.get());var recovered=drive.matterStore();
        h.assertTrue(recovered.amount(BuiltinChemistry.IRON)==100&&recovered.amount(BuiltinChemistry.COPPER)==10&&recovered.amount(unknown)==11,"Valid policy recovery changed stock");
        h.assertTrue(!original.apply(Map.of(),Map.of(BuiltinChemistry.IRON,1L))&&!current.apply(Map.of(),Map.of(BuiltinChemistry.COPPER,1L)),"Retired handles revived when policy became unrestricted again");
        h.assertTrue(recovered.apply(Map.of(BuiltinChemistry.IRON,100L,unknown,11L),Map.of()),"Recovered current store could not release old stock");
        h.succeed();
    }
    private static void quantityConflict(GameTestHelper h){
        var item=cell(Map.of(BuiltinChemistry.IRON,100L),BuiltinChemistry.IRON);var mount=new MatterCellMount();mount.mount(item);
        var old=Objects.requireNonNull(mount.inventory());h.assertTrue(old.insert(BuiltinChemistry.IRON,25)==25,"Dirty cache fixture rejected stock");
        var cached=new MatterNetworkInventory(List.of(old));
        var raw=new CompoundTag();raw.putInt("schema",99);raw.putString("future_quantities","untouched");var replacement=new MatterCellData(raw);
        item.set(MatterCellData.TYPE.get(),replacement);item.set(CellPartitionData.TYPE.get(),CellPartitionData.allowOnly(List.of(BuiltinChemistry.COPPER)));
        h.assertTrue(old.extract(BuiltinChemistry.IRON,1)==0&&!old.apply(Map.of(BuiltinChemistry.IRON,1L),Map.of()),"Old handle withdrew before recognizing the competing quantity writer");
        h.assertTrue(!cached.apply(Map.of(BuiltinChemistry.IRON,1L),Map.of()),"Cached network withdrew before a conflicting quantity refresh");
        h.assertTrue(old.snapshot().contents().equals(List.of(new SubstanceStack(BuiltinChemistry.IRON,125))),"Binding check changed committed cache quantities before handover");
        mount.flush();
        h.assertTrue(!mount.readable(item)&&mount.inventory()==null,"Conflicting quantity writer silently remounted");
        h.assertTrue(item.get(MatterCellData.TYPE.get())==replacement&&replacement.copyTag().equals(raw),"Dirty cache overwrote an external unknown quantity component");
        h.assertTrue(old.insert(BuiltinChemistry.IRON,1)==0&&old.extract(BuiltinChemistry.IRON,1)==0,"Conflicting retired cache retained a spendable duplicate");
        try(var tx=Transaction.openRoot()){
            h.assertTrue(mount.credit(UUID.randomUUID(),BuiltinChemistry.COPPER,1,tx).isEmpty(),"Conflicting quantity writer accepted Converter credit");tx.commit();
        }
        h.succeed();
    }
}
