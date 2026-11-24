package dev.latrox.rematter.content;

import dev.latrox.rematter.energy.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Native storage, charging and transport contracts. The synthetic FE item adapter exists only in this QA source set. */
@EventBusSubscriber(modid="rematter")
public final class EnergyCubeGameTests {
    private static final Map<String,Consumer<GameTestHelper>> TESTS=Map.of(
            "energy_cube_tier_io_and_rollback",EnergyCubeGameTests::budgets,
            "energy_cube_native_item_charging",EnergyCubeGameTests::charging,
            "energy_cube_portable_contents",EnergyCubeGameTests::portable,
            "energy_cube_native_cables_and_owners",EnergyCubeGameTests::cables,
            "energy_cube_unknown_data_is_preserved",EnergyCubeGameTests::unknown,
            "energy_cube_upgrade_transfers_energy_sides_and_items",EnergyCubeGameTests::upgradeTransfersEnergy);
    private static Identifier id(String path){return Identifier.fromNamespaceAndPath("rematter_tests",path);}
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event){
        event.register(Registries.TEST_FUNCTION,helper->TESTS.forEach((name,test)->helper.register(id(name),test)));
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event){
        var environment=event.registerEnvironment(id("energy_environment"));
        TESTS.forEach((name,test)->event.registerTest(id(name),new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,id(name)),
                new TestData<>(environment,id("empty"),200,0,true,Rotation.NONE,false,1,1,true,2))));
    }
    @SubscribeEvent public static void testItemCapability(RegisterCapabilitiesEvent event){
        event.registerItem(Capabilities.Energy.ITEM,(stack,access)->new ItemAccessEnergyHandler(access,DataComponents.REPAIR_COST,10_000,10_000,0),Items.IRON_PICKAXE);
    }
    private static EnergyCubeBlockEntity place(GameTestHelper h,BlockPos pos,EnergyCubeTier tier,Player owner){
        h.setBlock(pos,ModContent.ENERGY_CUBES.get(tier).get());
        var cube=h.getBlockEntity(pos,EnergyCubeBlockEntity.class);cube.placedBy(owner);return cube;
    }
    private static int fill(EnergyCubeBlockEntity cube,int offered){
        try(var tx=Transaction.openRoot()){int moved=Objects.requireNonNull(cube.sidedEnergy(Direction.UP)).insert(offered,tx);tx.commit();return moved;}
    }
    private static void budgets(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        for(var tier:EnergyCubeTier.values()){
            var cube=place(h,new BlockPos(2+tier.ordinal()*3,2,2),tier,owner);var input=Objects.requireNonNull(cube.sidedEnergy(Direction.UP));
            try(var simulation=Transaction.openRoot()){h.assertTrue(input.insert(tier.transferRate(),simulation)==tier.transferRate(),"Cube simulation refused its tier");}
            h.assertTrue(cube.energy()==0,"Simulation changed stored FE");
            try(var tx=Transaction.openRoot()){
                h.assertTrue(input.insert(1,tx)==1,"Input refused first FE");
                h.assertTrue(Objects.requireNonNull(cube.sidedEnergy(Direction.EAST)).insert(Integer.MAX_VALUE,tx)==tier.transferRate()-1,"Two sides multiplied or reduced tier rate");
                h.assertTrue(input.insert(1,tx)==0,"Shared input budget exceeded");tx.commit();
            }
            h.assertTrue(cube.energy()==tier.transferRate(),"Stored energy differs from accepted input");
            var output=Objects.requireNonNull(cube.sidedEnergy(Direction.NORTH));
            try(var tx=Transaction.openRoot()){h.assertTrue(output.extract(Integer.MAX_VALUE,tx)==tier.transferRate(),"Output differs from tier");}
            h.assertTrue(cube.energy()==tier.transferRate(),"Aborted output changed energy");
            owner.setPos(cube.getBlockPos().getX()+.5,cube.getBlockPos().getY(),cube.getBlockPos().getZ()+.5);
            h.assertTrue(cube.cycleSide(owner,MachinePortMode.Side.FRONT)&&cube.sideMode(MachinePortMode.Side.FRONT)==EnergyCubeSideMode.INPUT_OUTPUT,"Front did not cycle to INPUT_OUTPUT");
            h.assertTrue(cube.cycleSide(owner,MachinePortMode.Side.FRONT)&&cube.sideMode(MachinePortMode.Side.FRONT)==EnergyCubeSideMode.DISABLED,"Could not disable output");
            try(var tx=Transaction.openRoot()){h.assertTrue(output.extract(1,tx)==0,"Cached output bypassed disabled side");}
            h.assertTrue(cube.sidedEnergy(null)==null,"Side-less query bypassed face configuration");
            h.setBlock(new BlockPos(2+tier.ordinal()*3,2,2),Blocks.AIR);
            try(var tx=Transaction.openRoot()){h.assertTrue(input.insert(1,tx)==0,"Removed cube retained an active capability");}
        }
        h.succeed();
    }
    private static void charging(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var cube=place(h,new BlockPos(2,2,2),EnergyCubeTier.REINFORCED,owner);
        cube.setItem(0,new ItemStack(Items.IRON_PICKAXE));cube.setItem(1,new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(fill(cube,99_999)==16_384,"Charging source did not accept tier rate");cube.serverTick();
        int a=cube.getItem(0).getOrDefault(DataComponents.REPAIR_COST,0),b=cube.getItem(1).getOrDefault(DataComponents.REPAIR_COST,0);
        h.assertTrue(a==8192&&b==8192,"Both item slots must share exact available FE fairly: "+a+"/"+b);
        h.assertTrue(cube.energy()==0&&a+b==16_384,"Item component changes did not exactly debit buffer FE");
        var automated=Objects.requireNonNull(cube.sidedItems(Direction.NORTH));var resource=ItemResource.of(cube.getItem(0));
        try(var tx=Transaction.openRoot()){h.assertTrue(automated.extract(0,resource,1,tx)==0,"Automation removed a partially charged item");}
        // A fully charged item can leave; no extra copy remains in its slot.
        cube.getItem(0).set(DataComponents.REPAIR_COST,10_000);resource=ItemResource.of(cube.getItem(0));
        try(var tx=Transaction.openRoot()){h.assertTrue(automated.extract(0,resource,1,tx)==1,"Fully charged item could not leave output");tx.commit();}
        h.assertTrue(cube.getItem(0).isEmpty()&&!cube.getItem(1).isEmpty(),"Charged item extraction lost or duplicated a slot");h.succeed();
    }
    private static void portable(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var cube=place(h,new BlockPos(2,2,2),EnergyCubeTier.BASIC,owner);
        h.assertTrue(fill(cube,2048)==2048,"Portable fixture missing FE");var item=new ItemStack(Items.IRON_PICKAXE);item.set(DataComponents.REPAIR_COST,3456);cube.setItem(0,item);
        owner.setPos(cube.getBlockPos().getX()+.5,cube.getBlockPos().getY(),cube.getBlockPos().getZ()+.5);
        h.assertTrue(cube.cycleSide(owner,MachinePortMode.Side.LEFT),"Portable fixture could not change a side role");
        int roles=cube.packedSideModes();
        var drops=Block.getDrops(cube.getBlockState(),h.getLevel(),cube.getBlockPos(),cube);
        h.assertTrue(drops.size()==1,"Cube did not make exactly one portable item");var data=drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA);
        h.assertTrue(data!=null&&!data.contains("owner"),"Portable cube retained installation owner");
        var next=place(h,new BlockPos(5,2,2),EnergyCubeTier.BASIC,owner);
        h.assertTrue(data.loadInto(next,h.getLevel().registryAccess()),"Cube data failed to restore");next.placedBy(owner);
        h.assertTrue(!next.locked()&&next.energy()==2048,"Portable FE lost or locked");
        h.assertTrue(next.getItem(0).is(Items.IRON_PICKAXE)&&next.getItem(0).getOrDefault(DataComponents.REPAIR_COST,0)==3456,"Portable charging item changed");
        h.assertTrue(next.packedSideModes()==roles&&next.getBlockState().getValue(EnergyCubeBlock.FLOW)==EnergyCubeBlock.Flow.NONE,
                "Portable cube lost face roles or copied transient flow");
        var block=(EnergyCubeBlock)cube.getBlockState().getBlock();
        h.assertTrue(block.getCloneItemStack(h.getLevel(),cube.getBlockPos(),cube.getBlockState(),true,owner).isEmpty(),"Creative data pick could copy stored FE/items");
        h.assertTrue(!block.getCloneItemStack(h.getLevel(),cube.getBlockPos(),cube.getBlockState(),false,owner).has(DataComponents.BLOCK_ENTITY_DATA),"Ordinary pick copied stored contents");h.succeed();
    }
    private static void cables(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var cube=place(h,new BlockPos(3,3,3),EnergyCubeTier.BASIC,owner);
        h.setBlock(new BlockPos(3,3,2),ModContent.MATTER_CABLE.get());CableOwners.set(h.getLevel(),h.absolutePos(new BlockPos(3,3,2)),owner.getUUID());
        h.setBlock(new BlockPos(3,3,1),ModContent.MACHINES.get(MachineKind.DECOMPOSER).get());
        var receiver=h.getBlockEntity(new BlockPos(3,3,1),MachineBlockEntity.class);receiver.placedBy(owner);
        h.assertTrue(fill(cube,2048)==2048,"Cable source empty");cube.serverTick();
        h.assertTrue(cube.energy()==0&&receiver.energy().getAmountAsLong()==2048,"Cube could not feed owned machine through native cable");
        var target=place(h,new BlockPos(7,3,3),EnergyCubeTier.BASIC,owner);
        target.setSecurityMode(owner,SecurityMode.PRIVATE);
        var cable=h.absolutePos(new BlockPos(7,3,4));h.setBlock(new BlockPos(7,3,4),ModContent.MATTER_CABLE.get());h.setBlock(new BlockPos(7,3,5),Blocks.STONE);
        CableOwners.set(h.getLevel(),cable,UUID.randomUUID());var incoming=Objects.requireNonNull(MatterCablePower.capability(h.getLevel(),cable,Direction.SOUTH));
        try(var tx=Transaction.openRoot()){h.assertTrue(incoming.insert(2048,tx)==0,"Foreign cable reached private cube");tx.commit();}
        CableOwners.set(h.getLevel(),cable,owner.getUUID());
        try(var tx=Transaction.openRoot()){h.assertTrue(incoming.insert(2048,tx)==2048,"Owned cable did not discover cube FE endpoint");tx.commit();}
        h.assertTrue(target.energy()==2048,"Native cable receipt differs from stored FE");h.succeed();
    }
    private static void unknown(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var cube=place(h,new BlockPos(2,2,2),EnergyCubeTier.BASIC,owner);
        var raw=new CompoundTag();raw.putInt("schema",91);raw.putLong("energy",9_000_000_000L);raw.putString("future_contents","untouched");
        cube.loadWithComponents(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess(),raw));cube.placedBy(owner);
        h.assertTrue(cube.locked(),"Unknown storage schema was not protected");
        var cap=Objects.requireNonNull(cube.sidedEnergy(Direction.UP));
        try(var tx=Transaction.openRoot()){h.assertTrue(cap.insert(1,tx)==0&&cap.extract(1,tx)==0,"Protected cube allowed FE mutation");}
        cube.clearContent();cube.serverTick();
        var drop=((EnergyCubeBlock)cube.getBlockState().getBlock()).storedDrop(cube,h.getLevel().registryAccess());
        h.assertTrue(raw.equals(drop.get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId()),"Unknown portable data was modified");h.succeed();
    }
    private static void upgradeTransfersEnergy(GameTestHelper h){
        var level=h.getLevel();
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        var basic=place(h,new BlockPos(2,2,2),EnergyCubeTier.BASIC,owner);
        // Seed Basic Cube with 2,500,000 FE, custom side mode, charge-slot item, and discharge-slot item.
        var basicItem=(EnergyCubeItem)ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get().asItem();
        var reinfItem=(EnergyCubeItem)ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get().asItem();
        var resonItem=(EnergyCubeItem)ModContent.ENERGY_CUBES.get(EnergyCubeTier.RESONANT).get().asItem();
        var pick=new ItemStack(Items.IRON_PICKAXE);pick.set(DataComponents.REPAIR_COST,4321);
        basic.setItem(0,pick);
        var pocket=new ItemStack(ModContent.PORTABLE_ENERGY_CELL.get());
        PortableEnergyCellItem.setEnergy(pocket,123_456);
        basic.setItem(1,pocket);
        owner.setPos(basic.getBlockPos().getX()+.5,basic.getBlockPos().getY(),basic.getBlockPos().getZ()+.5);
        h.assertTrue(basic.cycleSide(owner,MachinePortMode.Side.LEFT),"Could not customize Basic Cube side");
        int customModes=basic.packedSideModes();
        var basicDrop=((EnergyCubeBlock)basic.getBlockState().getBlock()).storedDrop(basic,level.registryAccess());
        // Set exact 2,500,000 FE directly in valid portable BE tag (avoids ticking 1,221 ticks).
        var basicTag=basicDrop.get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId();
        basicTag.putInt("energy",2_500_000);
        basicDrop.set(DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.TypedEntityData.of(ModContent.ENERGY_CUBE_ENTITY.get(),basicTag));
        h.assertTrue(basicItem.storedEnergy(basicDrop)==2_500_000,"Basic fixture did not hold 2,500,000 FE");

        // 1. Upgrade Basic -> Reinforced via real RecipeManager recipe (KCK / LBL / OEO).
        var recipes=level.getServer().getRecipeManager();
        var reinfGrid=net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(
                new ItemStack(ModContent.ENERGY_CORE.get()), new ItemStack(ModContent.REINFORCED_MATTER_CABLE_ITEM.get()), new ItemStack(ModContent.ENERGY_CORE.get()),
                new ItemStack(ModContent.LUMIVEX_INGOT.get()), basicDrop.copy(), new ItemStack(ModContent.LUMIVEX_INGOT.get()),
                new ItemStack(ModContent.OPTICAL_CIRCUIT.get()), new ItemStack(ModContent.ENDER_CONDUCTOR.get()), new ItemStack(ModContent.OPTICAL_CIRCUIT.get())));
        var reinfHolder=recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,reinfGrid,level).orElseThrow(()->new AssertionError("Reinforced Energy Cube upgrade recipe did not match"));
        h.assertTrue(reinfHolder.value() instanceof EnergyUpgradeRecipe,"Expected EnergyUpgradeRecipe for Reinforced Cube");
        var reinfStack=reinfHolder.value().assemble(reinfGrid);
        h.assertTrue(reinfStack.is(reinfItem)&&reinfItem.storedEnergy(reinfStack)==2_500_000,
                "Reinforced upgrade did not transfer exact 2,500,000 FE: "+reinfItem.storedEnergy(reinfStack));
        var reinfData=reinfStack.get(DataComponents.BLOCK_ENTITY_DATA);
        h.assertTrue(reinfData!=null&&!reinfData.contains("owner"),"Upgraded Reinforced Cube retained stale owner");

        // Place upgraded Reinforced Cube and verify FE, custom sides, and both charge/discharge items restored cleanly.
        var placedReinf=place(h,new BlockPos(5,2,2),EnergyCubeTier.REINFORCED,owner);
        h.assertTrue(reinfData.loadInto(placedReinf,level.registryAccess()),"Upgraded Reinforced data failed to load");
        placedReinf.placedBy(owner);
        h.assertTrue(!placedReinf.locked()&&placedReinf.energy()==2_500_000&&placedReinf.packedSideModes()==customModes,
                "Placed upgraded Reinforced Cube lost energy or side modes");
        h.assertTrue(placedReinf.getItem(0).is(Items.IRON_PICKAXE)&&placedReinf.getItem(0).getOrDefault(DataComponents.REPAIR_COST,0)==4321,
                "Placed upgraded Reinforced Cube lost charge-slot item");
        h.assertTrue(placedReinf.getItem(2).is(ModContent.PORTABLE_ENERGY_CELL.get())&&PortableEnergyCellItem.energy(placedReinf.getItem(2))==123_456,
                "Placed upgraded Reinforced Cube lost discharge-slot item");

        // 2. Upgrade Reinforced (24,000,000 FE) -> Resonant via real RecipeManager recipe (KCK / NBN / DGD).
        var reinfTag=reinfData.copyTagWithoutId();reinfTag.putInt("energy",24_000_000);
        reinfStack.set(DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.TypedEntityData.of(ModContent.ENERGY_CUBE_ENTITY.get(),reinfTag));
        var resonGrid=net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(
                new ItemStack(ModContent.ENERGY_CORE.get()), new ItemStack(ModContent.RESONANT_MATTER_CABLE_ITEM.get()), new ItemStack(ModContent.ENERGY_CORE.get()),
                new ItemStack(ModContent.NETHERITE_SHEATH.get()), reinfStack.copy(), new ItemStack(ModContent.NETHERITE_SHEATH.get()),
                new ItemStack(ModContent.DIAMOND_LATTICE.get()), new ItemStack(ModContent.MATRIX_CLUSTER.get()), new ItemStack(ModContent.DIAMOND_LATTICE.get())));
        var resonHolder=recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,resonGrid,level).orElseThrow(()->new AssertionError("Resonant Energy Cube upgrade recipe did not match"));
        var resonStack=resonHolder.value().assemble(resonGrid);
        h.assertTrue(resonStack.is(resonItem)&&resonItem.storedEnergy(resonStack)==24_000_000,
                "Resonant upgrade did not transfer exact 24,000,000 FE: "+resonItem.storedEnergy(resonStack));

        // 3. Fresh empty Basic Cube upgrades cleanly without empty BE NBT clutter.
        var emptyGrid=net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(
                new ItemStack(ModContent.ENERGY_CORE.get()), new ItemStack(ModContent.REINFORCED_MATTER_CABLE_ITEM.get()), new ItemStack(ModContent.ENERGY_CORE.get()),
                new ItemStack(ModContent.LUMIVEX_INGOT.get()), new ItemStack(basicItem), new ItemStack(ModContent.LUMIVEX_INGOT.get()),
                new ItemStack(ModContent.OPTICAL_CIRCUIT.get()), new ItemStack(ModContent.ENDER_CONDUCTOR.get()), new ItemStack(ModContent.OPTICAL_CIRCUIT.get())));
        var emptyOut=recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,emptyGrid,level).orElseThrow().value().assemble(emptyGrid);
        h.assertTrue(emptyOut.is(reinfItem)&&!emptyOut.has(DataComponents.BLOCK_ENTITY_DATA),"Empty cube upgrade attached unnecessary BE data");

        // 4. Corrupted / unknown-schema Basic Cube MUST be refused so protected data cannot be wiped.
        var lockedCube=new ItemStack(basicItem);
        var unknownTag=new CompoundTag();unknownTag.putInt("schema",99);unknownTag.putInt("energy",500);
        lockedCube.set(DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.TypedEntityData.of(ModContent.ENERGY_CUBE_ENTITY.get(),unknownTag));
        var badGrid=net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(
                new ItemStack(ModContent.ENERGY_CORE.get()), new ItemStack(ModContent.REINFORCED_MATTER_CABLE_ITEM.get()), new ItemStack(ModContent.ENERGY_CORE.get()),
                new ItemStack(ModContent.LUMIVEX_INGOT.get()), lockedCube, new ItemStack(ModContent.LUMIVEX_INGOT.get()),
                new ItemStack(ModContent.OPTICAL_CIRCUIT.get()), new ItemStack(ModContent.ENDER_CONDUCTOR.get()), new ItemStack(ModContent.OPTICAL_CIRCUIT.get())));
        h.assertTrue(recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,badGrid,level).isEmpty(),
                "Corrupted/unknown-schema Energy Cube was unexpectedly accepted for tier upgrade");
        h.succeed();
    }
}
