package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.content.generation.CrystalFuelData;
import dev.latrox.rematter.primal.EntangledLinks;
import dev.latrox.rematter.primal.PrimalContent;
import dev.latrox.rematter.research.ResearchService;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Real server capabilities, physical buffers and loaded graphs. Each clock simulation owns its serial environment. */
@EventBusSubscriber(modid=ReMatter.MOD_ID)
public final class ThroughputRuntimeGameTests {
    private static final Map<String,Consumer<GameTestHelper>> TESTS=new LinkedHashMap<>();
    static {
        TESTS.put("throughput_runtime_equal_cables_share_input",ThroughputRuntimeGameTests::nativeInputs);
        TESTS.put("throughput_runtime_native_foreign_budget_rolls_back",ThroughputRuntimeGameTests::combinedInput);
        TESTS.put("throughput_runtime_parallel_batches_share_matter",ThroughputRuntimeGameTests::parallelMatter);
        TESTS.put("throughput_runtime_topology_retains_paid_work",h->parallelMatter(h,true));
        TESTS.put("throughput_runtime_generator_fuel_reload_exact",ThroughputRuntimeGameTests::generatorReload);
        TESTS.put("throughput_runtime_generator_full_and_shared_output",ThroughputRuntimeGameTests::generatorOutput);
        TESTS.put("throughput_runtime_link_repair_invalidates_graph",ThroughputRuntimeGameTests::linkRepair);
        TESTS.put("throughput_runtime_link_range_and_owner",ThroughputRuntimeGameTests::linkRangeOwner);
        TESTS.put("throughput_runtime_link_item_dimension_binding",ThroughputRuntimeGameTests::linkDimension);
        TESTS.put("throughput_runtime_native_route_weakest_tier",ThroughputRuntimeGameTests::nativeRouteTier);
        TESTS.put("throughput_runtime_link_fe_transactions_and_total",ThroughputRuntimeGameTests::linkFeTransactions);
        TESTS.put("throughput_runtime_link_fe_shared_endpoints",ThroughputRuntimeGameTests::linkFeEndpoints);
        TESTS.put("throughput_runtime_link_fe_repair_and_removal",ThroughputRuntimeGameTests::linkFeRepair);
        TESTS.put("throughput_runtime_link_fe_never_loads_peer",ThroughputRuntimeGameTests::linkFeNoChunkLoad);
        TESTS.put("throughput_runtime_generator_pushes_across_link",ThroughputRuntimeGameTests::linkGeneratorPush);
    }
    private ThroughputRuntimeGameTests() {}
    private static Identifier id(String name){return Identifier.parse("rematter_tests:"+name);}
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event){
        event.register(Registries.TEST_FUNCTION,registry->TESTS.forEach((name,body)->registry.register(id(name),body)));
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event){
        TESTS.forEach((name,body)->{
            var environment=event.registerEnvironment(id(name+"_batch"));
            event.registerTest(id(name),new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,id(name)),
                    new TestData<>(environment,id("empty"),1_200,0,true,Rotation.NONE,false,1,1,true,2)));
        });
    }
    private static Player owner(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        ResearchService.ledger(h.getLevel().getServer()).visit(owner.getUUID(),h.getLevel().dimension().identifier().toString());
        return owner;
    }
    private static MachineBlockEntity machine(GameTestHelper h,BlockPos relative,MachineKind kind,Player owner){
        h.setBlock(relative,ModContent.MACHINES.get(kind).get());
        var machine=h.getBlockEntity(relative,MachineBlockEntity.class);machine.placedBy(owner);return machine;
    }
    private static void nextTick(GameTestHelper h,MachineBlockEntity... machines){
        var level=h.getLevel();((ServerLevelData)level.getLevelData()).setGameTime(Math.incrementExact(level.getGameTime()));
        for(var machine:machines)machine.serverTick();
    }
    private static EnergyHandler energy(GameTestHelper h,BlockPos absolute,Direction face){
        var handler=h.getLevel().getCapability(Capabilities.Energy.BLOCK,absolute,face);
        h.assertTrue(handler!=null,"Missing real energy capability at "+absolute+" / "+face);return handler;
    }
    private static EnergyHandler cable(GameTestHelper h,BlockPos relative,MatterCableBlock.Tier tier,Player owner){
        var block=switch(tier){case BASIC->ModContent.MATTER_CABLE.get();case REINFORCED->ModContent.REINFORCED_MATTER_CABLE.get();case RESONANT->ModContent.RESONANT_MATTER_CABLE.get();};
        h.setBlock(relative,block);var absolute=h.absolutePos(relative);CableOwners.placedBy(h.getLevel(),absolute,owner.getUUID());
        return energy(h,absolute,Direction.UP);
    }
    private static int insert(EnergyHandler handler,int amount){try(var tx=Transaction.openRoot()){int moved=handler.insert(amount,tx);tx.commit();return moved;}}
    private static int extract(EnergyHandler handler,int amount){try(var tx=Transaction.openRoot()){int moved=handler.extract(amount,tx);tx.commit();return moved;}}
    private static void nativeInputs(GameTestHelper h){
        var owner=owner(h);var receiver=machine(h,new BlockPos(5,3,5),MachineKind.REACTOR,owner);
        var first=cable(h,new BlockPos(4,3,5),MatterCableBlock.Tier.BASIC,owner);
        h.assertTrue(insert(first,20_000)==2_048,"One Basic native input did not get exactly 2,048 FE");
        var second=cable(h,new BlockPos(6,3,5),MatterCableBlock.Tier.BASIC,owner);
        h.assertTrue(insert(second,20_000)==0,"Another equal cable added a new controller allowance in the same tick");
        h.assertTrue(receiver.energy().getAmountAsLong()==2_048,"Native FE was duplicated or lost");
        nextTick(h);h.assertTrue(insert(first,20_000)+insert(second,20_000)==2_048,"Two Basic origins exceeded the shared allowance next tick");
        h.assertTrue(receiver.energy().getAmountAsLong()==4_096,"A new tick did not reset exactly one allowance");h.succeed();
    }
    private static void combinedInput(GameTestHelper h){
        var owner=owner(h);var receiver=machine(h,new BlockPos(5,3,5),MachineKind.REACTOR,owner);
        var nativeBasic=cable(h,new BlockPos(4,3,5),MatterCableBlock.Tier.BASIC,owner);
        var nativeFast=cable(h,new BlockPos(6,3,5),MatterCableBlock.Tier.RESONANT,owner);
        var north=energy(h,receiver.getBlockPos(),Direction.NORTH);var south=energy(h,receiver.getBlockPos(),Direction.SOUTH);
        try(var outer=Transaction.openRoot()){
            try(var nested=Transaction.open(outer)){h.assertTrue(nativeBasic.insert(2_048,nested)==2_048,"Native simulation failed");h.assertTrue(north.insert(100_000,nested)==100_000,"Foreign nested simulation failed");}
            h.assertTrue(receiver.energy().getAmountAsLong()==0,"Aborted nested transfer changed the real buffer");
            h.assertTrue(nativeBasic.insert(2_048,outer)==2_048,"Nested abort consumed native allowance");
            h.assertTrue(north.insert(100_000,outer)==100_000,"Nested abort consumed total allowance");
            h.assertTrue(south.insert(100_000,outer)==29_024,"Native + foreign did not share the absolute 131,072 cap");
            h.assertTrue(nativeFast.insert(100_000,outer)==0,"A better native input bypassed the exhausted total cap");
        }
        h.assertTrue(receiver.energy().getAmountAsLong()==0,"Aborted root transfer changed energy");
        h.assertTrue(insert(nativeBasic,2_048)==2_048,"Root abort consumed native allowance");
        h.assertTrue(insert(north,100_000)==100_000&&insert(south,100_000)==29_024,"Simulation/rollback did not restore the full total budget");
        h.assertTrue(receiver.energy().getAmountAsLong()==131_072,"Combined input did not retain the exact accepted energy");
        nextTick(h);h.assertTrue(insert(north,128_000)==128_000,"Next tick foreign cap did not reset");
        h.assertTrue(insert(nativeFast,131_072)==3_072,"Native rate was added above the foreign absolute cap");
        h.assertTrue(receiver.energy().getAmountAsLong()==262_144,"Second mixed tick has an incorrect total");h.succeed();
    }
    private static void parallelMatter(GameTestHelper h){parallelMatter(h,false);}
    private static void parallelMatter(GameTestHelper h,boolean changeTopology){
        var owner=owner(h);var relative=new BlockPos(6,2,5);var origin=h.absolutePos(relative);
        var shape=new IndustrialStructure.Shape(3,7,3,1);
        for(var cell:IndustrialStructure.layout(shape)){
            var block=switch(cell.role()){case FRAME->ModContent.REACTOR_CASING.get();case WALL->cell.outward().getAxis().isHorizontal()?ModContent.REACTOR_GLASS.get():ModContent.REACTOR_CASING.get();default->Blocks.AIR;};
            h.getLevel().setBlockAndUpdate(IndustrialStructure.worldPosition(origin,Direction.NORTH,cell),block.defaultBlockState());
        }
        var reactor=machine(h,relative,MachineKind.INDUSTRIAL_REACTOR,owner);
        var west=new BlockPos(5,2,6);var east=new BlockPos(7,2,6);h.setBlock(west,ModContent.MACHINE_PORT.get());h.setBlock(east,ModContent.MACHINE_PORT.get());
        h.setBlock(6,2,6,ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.FIELD_COIL).get());
        h.setBlock(6,6,6,ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.REACTION_CHAMBER).get());
        h.assertTrue(IndustrialStructure.inspect(reactor).formed(),"Parallel fixture failed: "+IndustrialStructure.inspect(reactor).diagnostic());
        var drive=machine(h,new BlockPos(2,2,6),MachineKind.DRIVE,owner);drive.setItem(0,new ItemStack(ModContent.MATTER_CELL_64K.get()));
        cable(h,new BlockPos(3,2,6),MatterCableBlock.Tier.BASIC,owner);cable(h,new BlockPos(4,2,6),MatterCableBlock.Tier.BASIC,owner);
        h.assertTrue(drive.matterStore().apply(Map.of(),Map.of(BuiltinChemistry.WATER,4_000L)),"Water fixture did not fit");
        reactor.setItem(2,new ItemStack(ModContent.SPEED_UPGRADE.get(),4));reactor.setItem(MachineCellAccess.PARALLEL_SLOT,new ItemStack(ModContent.PARALLEL_UPGRADE.get(),3));
        h.assertTrue(insert(energy(h,h.absolutePos(east),Direction.EAST),64_000)==64_000,"Real controller port rejected the batch FE");
        h.assertTrue(reactor.selectRecipe("rematter:water_freezing"),"Reactor rejected the ordinary recipe");for(int i=0;i<4;i++)h.assertTrue(reactor.requestBatch(),"Four finite batches could not be queued");
        h.assertTrue(reactor.matterNetwork(true).throughputTier(reactor)==MatterCableBlock.Tier.BASIC,"Fixture is not truly Basic-limited");
        for(int tick=1;tick<=62;tick++){
            if(changeTopology&&tick==31){
                // Changing a live Basic route to a redundant Basic topology retains this exact job's paid work.
                cable(h,new BlockPos(3,2,7),MatterCableBlock.Tier.BASIC,owner);
                cable(h,new BlockPos(4,2,7),MatterCableBlock.Tier.BASIC,owner);
            }
            nextTick(h,reactor);reactor.serverTick();
            h.assertTrue(drive.matterStore().amount(BuiltinChemistry.ICE)==0,"Parallel jobs or repeated calls added another matter budget at tick "+tick);
            h.assertTrue(drive.matterStore().amount(BuiltinChemistry.WATER)==4_000,"Unfinished work withdrew matter early");
        }
        nextTick(h,reactor);
        h.assertTrue(drive.matterStore().amount(BuiltinChemistry.ICE)==4_000&&drive.matterStore().amount(BuiltinChemistry.WATER)==0,"8,000 units of work did not complete atomically after63 shared128u ticks");
        h.assertTrue(reactor.energy().getAmountAsLong()==0,"Every parallel batch must pay its full FE");h.succeed();
    }
    private static MachineBlockEntity generator(GameTestHelper h,Player owner){
        var machine=machine(h,new BlockPos(5,3,5),MachineKind.CRYSTAL_GENERATOR,owner);if(!machine.running())machine.toggleRunning();
        var fuel=CrystalFuelData.fuel(new ItemStack(ModContent.MATRIX_CRYSTAL.get())).orElseThrow();h.assertTrue(fuel.count()==1&&fuel.energy()==1_024,"Test must exercise the shipped exact1,024FE fuel");
        machine.setItem(0,new ItemStack(ModContent.MATRIX_CRYSTAL.get()));return machine;
    }
    private static void reload(GameTestHelper h,MachineBlockEntity machine){
        var raw=machine.saveCustomOnly(h.getLevel().registryAccess());
        machine.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),raw));
        h.assertTrue(machine.networkLive(),"Reloaded real block entity lost its live identity");
    }
    private static long remaining(MachineBlockEntity generator){return generator.extensions().view().get("fuelRemaining").getAsLong();}
    private static void generatorReload(GameTestHelper h){
        var gen=generator(h,owner(h));nextTick(h,gen);
        h.assertTrue(gen.getItem(0).isEmpty()&&gen.energy().getAmountAsLong()==64&&remaining(gen)==960,"First generation step did not debit exactly one crystal");
        var output=energy(h,gen.getBlockPos(),Direction.NORTH);
        try(var tx=Transaction.openRoot()){h.assertTrue(output.extract(64,tx)==64,"Real generator output simulation failed");}
        h.assertTrue(gen.energy().getAmountAsLong()==64&&remaining(gen)==960,"Aborted output changed buffer or unburnt energy");
        reload(h,gen);h.assertTrue(gen.energy().getAmountAsLong()+remaining(gen)==1_024&&gen.getItem(0).isEmpty(),"Reload duplicated or dropped fuel energy");
        for(int i=0;i<15;i++)nextTick(h,gen);
        h.assertTrue(gen.energy().getAmountAsLong()==1_024&&remaining(gen)==0&&gen.getItem(0).isEmpty(),"One crystal did not produce exactly1,024FE");
        reload(h,gen);for(int i=0;i<3;i++)nextTick(h,gen);
        h.assertTrue(gen.energy().getAmountAsLong()==1_024&&remaining(gen)==0,"Completed fuel paid again after reload");
        h.assertTrue(extract(energy(h,gen.getBlockPos(),Direction.SOUTH),2_048)==1_024,"Final real output was not the exact one-crystal value");h.succeed();
    }
    private static void generatorOutput(GameTestHelper h){
        var gen=generator(h,owner(h));gen.energy().set(MachineBlockEntity.ENERGY_CAPACITY);nextTick(h,gen);
        h.assertTrue(gen.getItem(0).getCount()==1&&remaining(gen)==0,"A full FE buffer consumed a fresh crystal");
        var north=energy(h,gen.getBlockPos(),Direction.NORTH);var south=energy(h,gen.getBlockPos(),Direction.SOUTH);
        try(var outer=Transaction.openRoot()){
            try(var nested=Transaction.open(outer)){h.assertTrue(north.extract(2_048,nested)==2_048,"Generator nested simulation failed");}
            h.assertTrue(gen.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY,"Nested abort did not return generator FE");
            h.assertTrue(north.extract(2_000,outer)==2_000&&south.extract(2_000,outer)==48,"Generator sides did not share one2,048FE output allowance");outer.commit();
        }
        h.assertTrue(extract(energy(h,gen.getBlockPos(),Direction.EAST),2_048)==0,"A third side bypassed the spent output allowance");
        gen.serverTick();h.assertTrue(gen.getItem(0).isEmpty()&&remaining(gen)==960,"Space did not consume exactly one new crystal");
        h.assertTrue(gen.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY-2_048+64,"Generated buffer energy was not exact");
        h.assertTrue(extract(north,2_048)==0,"Generation reset the same-tick output journal");nextTick(h);
        h.assertTrue(extract(north,2_048)==2_048,"Generator output budget did not reset next tick");h.succeed();
    }
    private static BlockPos link(GameTestHelper h,BlockPos relative,Player owner){
        var absolute=h.absolutePos(relative);h.getLevel().getChunk(absolute);var block=PrimalContent.ENTANGLED_LINK.get();h.setBlock(relative,block);
        block.setPlacedBy(h.getLevel(),absolute,block.defaultBlockState(),owner,new ItemStack(block));return absolute;
    }
    private static MachineBlockEntity drive(GameTestHelper h,BlockPos relative,Player owner,long water){
        var drive=machine(h,relative,MachineKind.DRIVE,owner);drive.setItem(0,new ItemStack(ModContent.MATTER_CELL_64K.get()));
        h.assertTrue(drive.matterStore().apply(Map.of(),Map.of(BuiltinChemistry.WATER,water)),"Drive fixture did not fit");return drive;
    }
    private static void linkRepair(GameTestHelper h){
        var owner=owner(h);var origin=machine(h,new BlockPos(2,3,2),MachineKind.REACTOR,owner);
        h.assertTrue(insert(energy(h,origin.getBlockPos(),Direction.NORTH),64)==64,"Origin capability fixture failed");
        var a=link(h,new BlockPos(3,3,2),owner);var b=link(h,new BlockPos(18,3,2),owner);var c=link(h,new BlockPos(18,3,8),owner);
        var first=drive(h,new BlockPos(19,3,2),owner,111);var second=drive(h,new BlockPos(19,3,8),owner,222);
        h.assertTrue(!origin.matterNetwork(true).ready(),"Unpaired link unexpectedly crossed to another network");
        h.assertTrue(EntangledLinks.pair(h.getLevel(),a,b,owner.getUUID()),"Own loaded links did not pair");
        var old=origin.matterNetwork(false);h.assertTrue(old.ready()&&old.checkpointStores().equals(List.of(first)),"Pair did not invalidate the cached no-storage graph");
        h.assertTrue(old.throughputTier(origin)==MatterCableBlock.Tier.BASIC,"Entangled link added a faster transfer tier");
        h.assertTrue(EntangledLinks.pair(h.getLevel(),a,c,owner.getUUID()),"Re-pair failed");
        h.assertTrue(EntangledLinks.peer(h.getLevel(),b,owner.getUUID()).isEmpty(),"Old endpoint still has a reciprocal edge");
        var repaired=origin.matterNetwork(false);h.assertTrue(repaired.ready()&&repaired.checkpointStores().equals(List.of(second)),"Re-pair retained stale cached storage");
        h.getLevel().setBlockAndUpdate(c,Blocks.AIR.defaultBlockState());
        h.assertTrue(EntangledLinks.peer(h.getLevel(),a,owner.getUUID()).isEmpty()&&!origin.matterNetwork(false).ready(),"Removed loaded endpoint remained cached");h.succeed();
    }
    private static void linkRangeOwner(GameTestHelper h){
        var owner=owner(h);var foreign=owner(h);var origin=machine(h,new BlockPos(2,3,2),MachineKind.REACTOR,owner);
        h.assertTrue(insert(energy(h,origin.getBlockPos(),Direction.NORTH),32)==32,"Real origin capability failed");
        var a=link(h,new BlockPos(4,3,4),owner);var edge=link(h,new BlockPos(36,3,4),owner);var far=link(h,new BlockPos(37,3,4),owner);var hostile=link(h,new BlockPos(5,3,4),foreign);
        h.assertTrue(!EntangledLinks.pair(h.getLevel(),a,far,owner.getUUID()),"33-block link was accepted");
        h.assertTrue(EntangledLinks.pair(h.getLevel(),a,edge,owner.getUUID()),"Exactly32 blocks should be allowed for loaded own links");
        h.assertTrue(!EntangledLinks.pair(h.getLevel(),a,hostile,owner.getUUID()),"Foreign endpoint could be paired");
        h.assertTrue(!EntangledLinks.pair(h.getLevel(),a,edge,foreign.getUUID())&&EntangledLinks.peer(h.getLevel(),a,foreign.getUUID()).isEmpty(),"Foreign owner traversed an existing pair");
        h.assertTrue(EntangledLinks.peer(h.getLevel(),a,owner.getUUID()).filter(edge::equals).isPresent(),"Denied pairing modified the previous owned pair");h.succeed();
    }
    private static void linkDimension(GameTestHelper h){
        var owner=owner(h);var origin=machine(h,new BlockPos(2,3,2),MachineKind.REACTOR,owner);
        h.assertTrue(insert(energy(h,origin.getBlockPos(),Direction.NORTH),16)==16,"Real origin capability failed");
        var a=link(h,new BlockPos(4,3,4),owner);var b=link(h,new BlockPos(8,3,4),owner);
        var stack=new ItemStack(PrimalContent.ENTANGLED_LINK_ITEM.get());var root=new CompoundTag();var binding=new CompoundTag();
        binding.putString("dimension",h.getLevel().dimension().identifier().toString().equals("minecraft:the_nether")?"minecraft:overworld":"minecraft:the_nether");binding.putLong("position",a.asLong());root.put("rematter_entangled_origin",binding);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));
        owner.setShiftKeyDown(true);owner.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var context=new UseOnContext(owner,InteractionHand.MAIN_HAND,new BlockHitResult(b.getCenter(),Direction.NORTH,b,false));
        h.assertTrue(stack.useOn(context).consumesAction(),"Binding interaction failed");
        h.assertTrue(EntangledLinks.peer(h.getLevel(),a,owner.getUUID()).isEmpty()&&EntangledLinks.peer(h.getLevel(),b,owner.getUUID()).isEmpty(),"Foreign-dimension item binding created an edge");
        var saved=stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("rematter_entangled_origin").orElseThrow();
        h.assertTrue(saved.getStringOr("dimension","").equals(h.getLevel().dimension().identifier().toString())&&saved.getLongOr("position",0)==b.asLong(),"Foreign binding was not replaced by this real loaded endpoint");h.succeed();
    }
    /** The origin tier must not disguise a slower cable in the middle of the actual route. */
    private static void nativeRouteTier(GameTestHelper h){
        var owner=owner(h);var receiver=machine(h,new BlockPos(7,3,5),MachineKind.REACTOR,owner);
        var input=cable(h,new BlockPos(3,3,5),MatterCableBlock.Tier.RESONANT,owner);
        cable(h,new BlockPos(4,3,5),MatterCableBlock.Tier.BASIC,owner);
        cable(h,new BlockPos(5,3,5),MatterCableBlock.Tier.RESONANT,owner);cable(h,new BlockPos(6,3,5),MatterCableBlock.Tier.RESONANT,owner);
        h.assertTrue(insert(input,131_072)==2_048&&insert(input,131_072)==0,"Fast origin bypassed the actual Basic cable or reset its allowance");
        nextTick(h);cable(h,new BlockPos(4,3,5),MatterCableBlock.Tier.REINFORCED,owner);
        h.assertTrue(insert(input,131_072)==16_384,"Cached route did not adopt the actual Reinforced bottleneck");
        cable(h,new BlockPos(4,3,5),MatterCableBlock.Tier.RESONANT,owner);
        h.assertTrue(insert(input,131_072)==114_688,"Faster re-route did not retain this tick's already spent controller/cable FE");
        h.assertTrue(receiver.energy().getAmountAsLong()==133_120,"Mixed-route transfer did not preserve the exact accepted FE");h.succeed();
    }
    private static void linkFeTransactions(GameTestHelper h){
        var owner=owner(h);var a=link(h,new BlockPos(4,3,5),owner);var b=link(h,new BlockPos(20,3,5),owner);
        var input=cable(h,new BlockPos(3,3,5),MatterCableBlock.Tier.RESONANT,owner);
        cable(h,new BlockPos(21,3,5),MatterCableBlock.Tier.RESONANT,owner);
        var receiver=machine(h,new BlockPos(22,3,5),MachineKind.REACTOR,owner);
        h.assertTrue(insert(input,131_072)==0,"Unpaired FE component reached the remote receiver");
        h.assertTrue(EntangledLinks.pair(h.getLevel(),a,b,owner.getUUID()),"FE pair fixture failed");
        var direct=energy(h,a,Direction.UP);
        try(var outer=Transaction.openRoot()){
            try(var child=Transaction.open(outer)){h.assertTrue(input.insert(131_072,child)==2_048,"Real Link nested FE simulation failed");}
            h.assertTrue(receiver.energy().getAmountAsLong()==0,"Link nested abort retained FE");
            h.assertTrue(input.insert(1_000,outer)==1_000&&direct.insert(131_072,outer)==1_048,"Cable and Link origins did not share one pair budget after nested abort");
            h.assertTrue(receiver.energy().getAmountAsLong()==2_048,"Provisional Link input did not reach the actual MBE");
        }
        h.assertTrue(receiver.energy().getAmountAsLong()==0,"Root abort retained remote energy");
        h.assertTrue(insert(input,131_072)==2_048&&insert(direct,131_072)==0,"Root abort did not restore exactly one shared Basic allowance");
        h.assertTrue(insert(energy(h,receiver.getBlockPos(),Direction.NORTH),131_072)==129_024,"Link FE escaped the native+foreign controller absolute cap");
        h.assertTrue(receiver.energy().getAmountAsLong()==131_072,"Controller did not keep the exact total input");
        nextTick(h);h.assertTrue(insert(direct,131_072)==2_048,"Link allowance did not reset next tick");h.succeed();
    }
    private static void linkFeEndpoints(GameTestHelper h){
        var owner=owner(h);var a=link(h,new BlockPos(4,3,5),owner);var b=link(h,new BlockPos(20,3,5),owner);
        var first=cable(h,new BlockPos(3,3,5),MatterCableBlock.Tier.RESONANT,owner);
        var second=cable(h,new BlockPos(4,3,4),MatterCableBlock.Tier.RESONANT,owner);
        var right=machine(h,new BlockPos(21,3,5),MachineKind.REACTOR,owner);
        var north=machine(h,new BlockPos(20,3,4),MachineKind.REACTOR,owner);
        // A second real side of the same receiver is reachable behind the remote Link.
        cable(h,new BlockPos(20,3,6),MatterCableBlock.Tier.RESONANT,owner);cable(h,new BlockPos(21,3,6),MatterCableBlock.Tier.RESONANT,owner);
        h.assertTrue(EntangledLinks.pair(h.getLevel(),a,b,owner.getUUID()),"Multi-endpoint FE pair failed");
        h.assertTrue(insert(first,1_000)==1_000&&insert(second,131_072)==1_048,"Two source origins/endpoints multiplied Link throughput");
        h.assertTrue(right.energy().getAmountAsLong()+north.energy().getAmountAsLong()==2_048,"Multiple receiver sides created or lost FE");
        var local=machine(h,new BlockPos(5,3,5),MachineKind.REACTOR,owner);
        // Fill the remote recipients explicitly so the next accepted FE must cross the pair in reverse.
        right.energy().set(MachineBlockEntity.ENERGY_CAPACITY);north.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        h.assertTrue(insert(energy(h,b,Direction.UP),131_072)==0&&local.energy().getAmountAsLong()==0,"Reverse flow granted the pair a second directional budget");
        nextTick(h);h.assertTrue(insert(energy(h,b,Direction.UP),131_072)==2_048,"Reverse flow could not reuse a fresh tick's shared allowance");
        h.assertTrue(local.energy().getAmountAsLong()==2_048&&right.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY&&north.energy().getAmountAsLong()==MachineBlockEntity.ENERGY_CAPACITY,"Fresh reverse transfer did not reach the actual opposite-side MBE exactly");h.succeed();
    }
    private static void linkFeRepair(GameTestHelper h){
        var owner=owner(h);var foreign=owner(h);var a=link(h,new BlockPos(4,3,5),owner);var b=link(h,new BlockPos(20,3,5),owner);var c=link(h,new BlockPos(20,3,11),owner);
        var input=cable(h,new BlockPos(3,3,5),MatterCableBlock.Tier.RESONANT,owner);
        var old=machine(h,new BlockPos(21,3,5),MachineKind.REACTOR,owner);var target=machine(h,new BlockPos(21,3,11),MachineKind.REACTOR,owner);
        var stranger=machine(h,new BlockPos(20,3,10),MachineKind.REACTOR,foreign);
        h.assertTrue(EntangledLinks.pair(h.getLevel(),a,b,owner.getUUID())&&insert(input,1_024)==1_024,"Initial FE route failed");
        h.assertTrue(EntangledLinks.pair(h.getLevel(),a,c,owner.getUUID()),"FE re-pair failed");
        h.assertTrue(insert(input,131_072)==1_024&&insert(input,131_072)==0,"Re-pair refreshed the already spent physical Link budget");
        h.assertTrue(old.energy().getAmountAsLong()==1_024&&target.energy().getAmountAsLong()==1_024&&stranger.energy().getAmountAsLong()==0,"Re-pair retained a stale/foreign FE recipient");
        nextTick(h);h.assertTrue(insert(input,131_072)==2_048,"Repaired route failed on a new tick");
        h.getLevel().setBlockAndUpdate(c,Blocks.AIR.defaultBlockState());
        h.assertTrue(insert(input,131_072)==0,"Removed peer still forwarded through cached FE graph");
        var replacement=link(h,new BlockPos(20,3,11),owner);
        h.assertTrue(insert(input,131_072)==0,"An unpaired replacement inherited the old Link edge");
        h.assertTrue(EntangledLinks.pair(h.getLevel(),a,replacement,owner.getUUID())&&insert(input,131_072)==0,"Same-tick replacement refreshed the origin Link allowance");
        nextTick(h);h.assertTrue(insert(input,131_072)==2_048&&stranger.energy().getAmountAsLong()==0,"Replacement edge was not refreshed safely");h.succeed();
    }
    private static void linkGeneratorPush(GameTestHelper h){
        var owner=owner(h);var gen=machine(h,new BlockPos(3,3,5),MachineKind.CRYSTAL_GENERATOR,owner);
        var a=link(h,new BlockPos(4,3,5),owner);var b=link(h,new BlockPos(20,3,5),owner);
        var receiver=machine(h,new BlockPos(21,3,5),MachineKind.REACTOR,owner);
        h.assertTrue(EntangledLinks.pair(h.getLevel(),a,b,owner.getUUID()),"Generator pair failed");
        gen.energy().set(5_000);if(!gen.running())gen.toggleRunning();
        nextTick(h,gen);h.assertTrue(receiver.energy().getAmountAsLong()==2_048&&gen.energy().getAmountAsLong()==2_952,"Actual generator push did not cross the native Link adapter");
        gen.serverTick();h.assertTrue(receiver.energy().getAmountAsLong()==2_048&&gen.energy().getAmountAsLong()==2_952,"Repeated generator output acquired a second same-tick Link allowance");
        nextTick(h,gen);h.assertTrue(receiver.energy().getAmountAsLong()==4_096&&gen.energy().getAmountAsLong()==904,"Second generator tick did not conserve the exact FE");h.succeed();
    }
    private static void linkFeNoChunkLoad(GameTestHelper h){
        var level=h.getLevel();var owner=owner(h);
        // An isolated physically loaded source chunk. Its stored partner is genuinely absent, not a mocked lookup.
        var local=new BlockPos(1_000_015,h.absolutePos(new BlockPos(0,3,0)).getY(),1_000_015);
        level.getChunk(local);var block=PrimalContent.ENTANGLED_LINK.get();level.setBlockAndUpdate(local,block.defaultBlockState());
        block.setPlacedBy(level,local,level.getBlockState(local),owner,new ItemStack(block));
        BlockPos absent=null;
        for(var candidate:List.of(local.east(32),local.west(32),local.north(32),local.south(32)))
            if(level.getChunkSource().getChunkNow(candidate.getX()>>4,candidate.getZ()>>4)==null){absent=candidate;break;}
        h.assertTrue(absent!=null,"No unloaded in-range neighbor chunk exists in the isolated no-load fixture");
        var link=EntangledLinks.loaded(level,local).orElseThrow();var raw=link.saveCustomOnly(level.registryAccess());
        raw.putString("pair",UUID.randomUUID().toString());raw.putLong("peer",absent.asLong());
        link.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),raw));
        var input=energy(h,local,Direction.UP);
        try(var outer=Transaction.openRoot()){
            try(var nested=Transaction.open(outer)){h.assertTrue(input.insert(131_072,nested)==0,"Unloaded peer accepted simulated FE");}
            h.assertTrue(input.insert(131_072,outer)==0,"Unloaded peer accepted committed FE");outer.commit();
        }
        h.assertTrue(level.getChunkSource().getChunkNow(absent.getX()>>4,absent.getZ()>>4)==null,"FE capability/graph lookup loaded the stored peer chunk");
        h.assertTrue(EntangledLinks.peer(level,local,owner.getUUID()).isEmpty(),"Absent peer became an active route");
        level.setBlockAndUpdate(local,Blocks.AIR.defaultBlockState());h.succeed();
    }

}
