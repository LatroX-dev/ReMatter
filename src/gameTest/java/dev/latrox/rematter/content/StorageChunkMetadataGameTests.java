package dev.latrox.rematter.content;

import dev.latrox.rematter.celltransfer.*;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.energy.*;
import dev.latrox.rematter.network.CellTransferConfigPayload;
import dev.latrox.rematter.primal.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Covers native chunk metadata and pending-block-entity promotion, beyond portable item/custom-only reloads. */
@EventBusSubscriber(modid="rematter")
public final class StorageChunkMetadataGameTests {
    private static final SubstanceKey IRON=BuiltinChemistry.IRON,COPPER=BuiltinChemistry.COPPER,WATER=BuiltinChemistry.WATER,
            UNKNOWN_GAS=SubstanceKey.of("missing:retained_gas",Phase.GAS);
    private static final Map<String,Consumer<GameTestHelper>> TESTS=Map.of(
            "storage_chunk_metadata_keeps_cube_energy_items_and_owner",StorageChunkMetadataGameTests::cubes,
            "storage_chunk_metadata_keeps_station_cells_and_selection",StorageChunkMetadataGameTests::station,
            "storage_chunk_metadata_does_not_unlock_future_payloads",StorageChunkMetadataGameTests::unknown);
    private static Identifier id(String name){return Identifier.fromNamespaceAndPath("rematter_tests",name);}
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event){
        event.register(Registries.TEST_FUNCTION,helper->TESTS.forEach((name,test)->helper.register(id(name),test)));
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event){
        var environment=event.registerEnvironment(id("storage_chunk_environment"));
        TESTS.forEach((name,test)->event.registerTest(id(name),new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,id(name)),
                new TestData<>(environment,id("empty"),200,0,true,Rotation.NONE,false,1,1,true,2))));
    }
    private static CompoundTag binaryRoundTrip(CompoundTag tag){
        try{
            var bytes=new ByteArrayOutputStream();
            try(var out=new DataOutputStream(bytes)){NbtIo.write(tag,out);}
            try(var in=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))){return NbtIo.read(in);}
        }catch(IOException problem){throw new UncheckedIOException(problem);}
    }
    private static CompoundTag chunkSave(GameTestHelper h,BlockEntity entity){
        var tag=Objects.requireNonNull(h.getLevel().getChunkAt(entity.getBlockPos())
                .getBlockEntityNbtForSaving(entity.getBlockPos(),h.getLevel().registryAccess()));
        h.assertTrue(tag.get("keepPacked") instanceof ByteTag&&!tag.getBooleanOr("keepPacked",true),
                "Native live chunk save did not add its unpacked boolean metadata");
        return tag;
    }
    private static BlockEntity promoteSaved(GameTestHelper h,BlockEntity previous,CompoundTag disk){
        var tag=binaryRoundTrip(disk);h.assertTrue(tag.equals(disk),"Binary chunk NBT roundtrip changed stored data");
        var chunk=h.getLevel().getChunkAt(previous.getBlockPos());
        chunk.removeBlockEntity(previous.getBlockPos());chunk.setBlockEntityNbt(tag);
        var restored=Objects.requireNonNull(chunk.getBlockEntity(previous.getBlockPos()));
        h.assertTrue(restored!=previous,"Chunk promotion reused the original block entity");return restored;
    }
    private static EnergyCubeBlockEntity cube(GameTestHelper h,BlockPos pos,EnergyCubeTier tier,Player owner){
        h.setBlock(pos,ModContent.ENERGY_CUBES.get(tier).get());var cube=h.getBlockEntity(pos,EnergyCubeBlockEntity.class);cube.placedBy(owner);
        owner.setPos(cube.getBlockPos().getX()+.5,cube.getBlockPos().getY(),cube.getBlockPos().getZ()+.5);
        // Reuse the native FE item adapter registered by EnergyCubeGameTests; no adapter is shipped in the mod.
        for(int slot=0;slot<tier.chargingSlots();slot++){
            var item=new ItemStack(Items.IRON_PICKAXE);item.set(DataComponents.REPAIR_COST,1234+slot);cube.setItem(slot,item);
        }
        var seeded=cube.saveCustomOnly(h.getLevel().registryAccess());seeded.putInt("energy",tier.capacity()-123);
        cube.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),seeded));
        h.assertTrue(cube.cycleSide(owner,MachinePortMode.Side.RIGHT)&&cube.cycleSide(owner,MachinePortMode.Side.RIGHT)
                &&cube.cycleSide(owner,MachinePortMode.Side.LEFT),"Could not seed non-default Cube sides");return cube;
    }
    private static void cubes(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        for(var tier:EnergyCubeTier.values()){
            var cube=cube(h,new BlockPos(2+tier.ordinal()*3,2,2),tier,owner);
            var before=cube.saveCustomOnly(h.getLevel().registryAccess());int sides=cube.packedSideModes();
            for(boolean packed:new boolean[]{false,true}){
                var saved=chunkSave(h,cube);saved.putBoolean("keepPacked",packed);
                cube=(EnergyCubeBlockEntity)promoteSaved(h,cube,saved);
                h.assertTrue(!cube.locked()&&owner.getUUID().equals(cube.owner()),"Chunk metadata locked Cube or changed owner");
                h.assertTrue(cube.energy()==tier.capacity()-123&&cube.packedSideModes()==sides,"Chunk metadata changed FE or side roles");
                for(int slot=0;slot<tier.chargingSlots();slot++)h.assertTrue(cube.getItem(slot).is(Items.IRON_PICKAXE)
                        &&cube.getItem(slot).getOrDefault(DataComponents.REPAIR_COST,0)==1234+slot,"Chunk reload changed a charging item");
                h.assertTrue(before.equals(cube.saveCustomOnly(h.getLevel().registryAccess())),"Chunk reload changed Cube payload");
                try(var tx=Transaction.openRoot()){
                    h.assertTrue(Objects.requireNonNull(cube.sidedEnergy(Direction.NORTH)).extract(1,tx)==1,"Reloaded Cube cannot expose its stored FE");
                }
                h.assertTrue(before.equals(cube.saveCustomOnly(h.getLevel().registryAccess())),"Aborted post-load FE read changed Cube");
            }
        }
        h.succeed();
    }
    private static ItemStack cell(Map<SubstanceKey,Long> contents){
        var stack=new ItemStack(ModContent.MATTER_CELL_64K.get());var store=new MatterInventory(64000,512);
        if(!store.apply(Map.of(),contents))throw new IllegalArgumentException("Cell fixture exceeds capacity");
        stack.set(MatterCellData.TYPE.get(),MatterCellData.from(store));return stack;
    }
    private static ItemStack primal(long amount){
        var stack=new ItemStack(PrimalContent.PRIMAL_CELL_16K.get());
        stack.set(PrimalCellData.TYPE.get(),PrimalCellData.from(new PrimalCellData.State(UUID.randomUUID(),amount,0,Map.of())));return stack;
    }
    private static boolean action(CellTransferStationBlockEntity station,Player owner,CellTransferConfigPayload.Action action,SubstanceKey key,boolean value){
        return station.configure(owner,new CellTransferConfigPayload(0,station.revision(),action,-1,key,value));
    }
    private static CellTransferStationBlockEntity filledStation(GameTestHelper h,BlockPos pos,Player owner){
        h.setBlock(pos,ModContent.CELL_TRANSFER_STATION.get());var station=h.getBlockEntity(pos,CellTransferStationBlockEntity.class);station.placedBy(owner);
        owner.setPos(station.getBlockPos().getX()+.5,station.getBlockPos().getY(),station.getBlockPos().getZ()+.5);
        station.setItem(0,cell(Map.of(IRON,1000L,WATER,200L,UNKNOWN_GAS,100L)));station.setItem(1,cell(Map.of(COPPER,700L)));station.setItem(2,primal(1000));
        var fixed=cell(Map.of(IRON,64000L));fixed.set(CellPartitionData.TYPE.get(),CellPartitionData.allowOnly(Set.of(IRON)));
        var other=cell(Map.of());other.set(CellPartitionData.TYPE.get(),CellPartitionData.allowOnly(Set.of(COPPER,WATER)));
        station.setItem(3,fixed);station.setItem(4,other);station.setItem(5,primal(0));
        h.assertTrue(action(station,owner,CellTransferConfigPayload.Action.GLOBAL_CLEAR,null,false)
                &&action(station,owner,CellTransferConfigPayload.Action.GLOBAL_TOGGLE,COPPER,false)
                &&action(station,owner,CellTransferConfigPayload.Action.GLOBAL_PRIMAL,null,true),"Could not seed mixed-channel station selection");return station;
    }
    private static void station(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var station=filledStation(h,new BlockPos(3,2,3),owner);
        var before=station.saveCustomOnly(h.getLevel().registryAccess());var selection=station.selection();var stacks=new ArrayList<ItemStack>();
        for(int slot=0;slot<6;slot++)stacks.add(station.getItem(slot).copy());
        for(boolean packed:new boolean[]{false,true}){
            var saved=chunkSave(h,station);saved.putBoolean("keepPacked",packed);
            station=(CellTransferStationBlockEntity)promoteSaved(h,station,saved);
            h.assertTrue(!station.locked()&&owner.getUUID().equals(station.owner()),"Chunk metadata locked Station or changed owner");
            h.assertTrue(selection.equals(station.selection()),"Chunk reload changed selected ordinary/Primal channels");
            for(int slot=0;slot<6;slot++)h.assertTrue(station.getItem(slot).getCount()==1
                    &&ItemStack.isSameItemSameComponents(stacks.get(slot),station.getItem(slot)),"Chunk reload lost Cell contents, identity or partition at slot "+slot);
            var preview=station.snapshot(0);h.assertTrue(!preview.locked()&&preview.movableOrdinary()==700&&preview.movablePrimal()==1000,
                    "Chunk-reloaded Station cannot plan its exact selected transfer");
            h.assertTrue(before.equals(station.saveCustomOnly(h.getLevel().registryAccess())),"Chunk reload or preview changed Station payload");
        }
        h.assertTrue(station.transfer(owner,station.revision())==1700,"Restored Station cannot execute the saved selection");
        h.assertTrue(((MatterCellItem)station.getItem(4).getItem()).open(station.getItem(4)).orElseThrow().amount(COPPER)==700
                &&PrimalCells.view(station.getItem(5)).amount()==1000,"Restored transfer crossed or lost resource channels");h.succeed();
    }
    private static void unknown(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);
        for(boolean packed:new boolean[]{false,true}){
            int z=packed?6:2;var cube=cube(h,new BlockPos(2,2,z),EnergyCubeTier.BASIC,owner);
            var future=new CompoundTag();future.putLong("retained",9_000_000_000L);future.putString("format","do not discard");
            var payload=cube.saveCustomOnly(h.getLevel().registryAccess());payload.put("future_resource",future.copy());
            var saved=chunkSave(h,cube);saved.putBoolean("keepPacked",packed);saved.put("future_resource",future.copy());
            cube=(EnergyCubeBlockEntity)promoteSaved(h,cube,saved);
            h.assertTrue(cube.locked(),"Known chunk metadata caused a real future Cube field to be ignored");
            cube.clearContent();cube.serverTick();h.assertTrue(payload.equals(cube.saveCustomOnly(h.getLevel().registryAccess())),"Locked Cube future data changed");
            var station=filledStation(h,new BlockPos(5,2,z),owner);payload=station.saveCustomOnly(h.getLevel().registryAccess());payload.put("future_resource",future.copy());
            saved=chunkSave(h,station);saved.putBoolean("keepPacked",packed);saved.put("future_resource",future.copy());
            station=(CellTransferStationBlockEntity)promoteSaved(h,station,saved);
            h.assertTrue(station.locked()&&station.transfer(owner,station.revision())==0,"Known chunk metadata unlocked a real future Station field");
            station.clearContent();h.assertTrue(payload.equals(station.saveCustomOnly(h.getLevel().registryAccess())),"Locked Station lost Cells or future data");
        }
        h.succeed();
    }
}
