package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.primal.PrimalContent;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Actual server inventory gestures, including protected mounted-cell rejection. Not shipped with the mod. */
@EventBusSubscriber(modid=ReMatter.MOD_ID)
public final class MatterCellTransferGameTests {
    private static final Identifier ID=Identifier.fromNamespaceAndPath("rematter_tests","ordinary_cell_inventory_transfer_gesture");
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event){
        event.register(Registries.TEST_FUNCTION,helper->helper.register(ID,MatterCellTransferGameTests::gesture));
    }
    @SubscribeEvent public static void register(RegisterGameTestsEvent event){
        var env=event.registerEnvironment(Identifier.fromNamespaceAndPath("rematter_tests","cell_transfer"));
        event.registerTest(ID,new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,ID),
                new TestData<>(env,Identifier.fromNamespaceAndPath("rematter_tests","empty"),200,0,true,Rotation.NONE,false,1,1,true,2)));
    }
    private static ItemStack cell(long amount){
        var result=new ItemStack(ModContent.MATTER_CELL_64K.get());
        if(amount>0){var store=new MatterInventory(64000,512);store.insert(BuiltinChemistry.COPPER,amount);result.set(MatterCellData.TYPE.get(),MatterCellData.from(store));}
        return result;
    }
    private static long amount(ItemStack stack){return ((MatterCellItem)stack.getItem()).open(stack).orElseThrow().amount(BuiltinChemistry.COPPER);}
    private static void gesture(GameTestHelper h){
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        var source=cell(1234);var destination=cell(63000);player.getInventory().setItem(0,destination);player.containerMenu.setCarried(source);
        var slot=new Slot(player.getInventory(),0,0,0);
        var carried=new SlotAccess(){public ItemStack get(){return player.containerMenu.getCarried();}public boolean set(ItemStack value){player.containerMenu.setCarried(value);return true;}};
        var item=(MatterCellItem)destination.getItem();
        h.assertTrue(item.overrideOtherStackedOnMe(destination,source,slot,ClickAction.SECONDARY,player,carried),"Recognized gesture did not consume click");
        h.assertTrue(amount(source)==234&&amount(destination)==64000,"Partial transfer was not exact");
        h.assertTrue(carried.get()==source&&slot.getItem()==destination&&source.getCount()==1&&destination.getCount()==1,"Gesture swapped or consumed physical cells");
        var sourceData=source.get(MatterCellData.TYPE.get());var destinationData=destination.get(MatterCellData.TYPE.get());
        h.assertTrue(item.overrideOtherStackedOnMe(destination,source,slot,ClickAction.SECONDARY,player,carried),"Full destination fell through to vanilla swap");
        h.assertTrue(source.get(MatterCellData.TYPE.get())==sourceData&&destination.get(MatterCellData.TYPE.get())==destinationData,"Full destination rewrote contents");
        // A copied identity is protected, even though these are different ItemStack objects.
        var copied=source.copy();player.getInventory().setItem(0,copied);
        h.assertTrue(((MatterCellItem)copied.getItem()).overrideOtherStackedOnMe(copied,source,slot,ClickAction.SECONDARY,player,carried),"Same-identity gesture fell through");
        h.assertTrue(amount(source)==234&&amount(copied)==234,"Same identity was treated as two resource owners");
        var raw=new net.minecraft.nbt.CompoundTag();raw.putInt("schema",99);raw.putLong("preserved",Long.MAX_VALUE);
        var locked=cell(0);locked.set(MatterCellData.TYPE.get(),new MatterCellData(raw));player.getInventory().setItem(0,locked);
        h.assertTrue(((MatterCellItem)locked.getItem()).overrideOtherStackedOnMe(locked,source,slot,ClickAction.SECONDARY,player,carried),"Locked gesture fell through");
        h.assertTrue(locked.get(MatterCellData.TYPE.get()).copyTag().equals(raw)&&amount(source)==234,"Unknown data was changed");
        var pristine=cell(0);player.getInventory().setItem(0,pristine);source.setCount(2);
        h.assertTrue(((MatterCellItem)pristine.getItem()).overrideOtherStackedOnMe(pristine,source,slot,ClickAction.SECONDARY,player,carried),"Stacked gesture fell through");
        h.assertTrue(source.getCount()==2&&pristine.get(MatterCellData.TYPE.get())==null,"Invalid count was normalized or moved");source.setCount(1);
        // A stale cursor argument must not debit the detached item or the replacement cursor.
        var replacement=cell(17);player.containerMenu.setCarried(replacement);
        h.assertTrue(((MatterCellItem)pristine.getItem()).overrideOtherStackedOnMe(pristine,source,slot,ClickAction.SECONDARY,player,carried),"Stale cursor gesture fell through");
        h.assertTrue(amount(source)==234&&amount(replacement)==17&&pristine.get(MatterCellData.TYPE.get())==null,"A stale cursor reference changed contents");
        player.containerMenu.setCarried(source);
        var denied=new Slot(player.getInventory(),0,0,0){@Override public boolean mayPlace(ItemStack stack){return false;}};
        ((MatterCellItem)pristine.getItem()).overrideOtherStackedOnMe(pristine,source,denied,ClickAction.SECONDARY,player,carried);
        h.assertTrue(amount(source)==234&&pristine.get(MatterCellData.TYPE.get())==null,"Slot permission was ignored");
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlock(pos,ModContent.MACHINES.get(MachineKind.DRIVE).get().defaultBlockState(),3);
        var drive=(MachineBlockEntity)h.getLevel().getBlockEntity(pos);drive.placedBy(player);var mounted=cell(5000);drive.setItem(drive.cellSlotForBay(0),mounted);
        var driveSlot=new Slot(drive,drive.cellSlotForBay(0),0,0);
        h.assertTrue(((MatterCellItem)mounted.getItem()).overrideOtherStackedOnMe(mounted,source,driveSlot,ClickAction.SECONDARY,player,carried),"Mounted-cell gesture fell through to vanilla");
        drive.serverTick();
        h.assertTrue(amount(drive.getItem(drive.cellSlotForBay(0)))==5000&&amount(source)==234,"Mounted cache overwrote or duplicated a transfer");
        var chest=new SimpleContainer(cell(100));var foreignSlot=new Slot(chest,0,0,0);var chestCell=chest.getItem(0);
        h.assertTrue(((MatterCellItem)chestCell.getItem()).overrideOtherStackedOnMe(chestCell,source,foreignSlot,ClickAction.SECONDARY,player,carried),"Non-player inventory gesture fell through");
        h.assertTrue(amount(chestCell)==100&&amount(source)==234,"Non-player inventory unexpectedly transferred");
        var primal=new ItemStack(PrimalContent.PRIMAL_CELL_16K.get());
        h.assertTrue(!item.overrideOtherStackedOnMe(destination,primal,slot,ClickAction.SECONDARY,player,carried),"Ordinary cell accepted Primal reserve transfer");
        h.assertTrue(!item.overrideOtherStackedOnMe(destination,source,slot,ClickAction.PRIMARY,player,carried),"Primary click was hijacked");
        h.succeed();
    }
}
