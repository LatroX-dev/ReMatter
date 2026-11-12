package dev.latrox.rematter.content;

import dev.latrox.rematter.energy.*;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** The same four physical connector seats expose the same native FE contracts in every placement rotation. */
@EventBusSubscriber(modid="rematter")
public final class EnergyCubePortGameTests {
    private static final Direction[] HORIZONTAL={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static final Map<String,Consumer<GameTestHelper>> TESTS=Map.of(
            "energy_cube_all_horizontal_faces",EnergyCubePortGameTests::faces,
            "energy_cube_horizontal_cable_connections",EnergyCubePortGameTests::cables);
    private static Identifier id(String path){return Identifier.fromNamespaceAndPath("rematter_tests",path);}
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event){
        event.register(Registries.TEST_FUNCTION,helper->TESTS.forEach((name,test)->helper.register(id(name),test)));
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event){
        var environment=event.registerEnvironment(id("energy_ports_environment"));
        TESTS.forEach((name,test)->event.registerTest(id(name),new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,id(name)),
                new TestData<>(environment,id("empty"),200,0,true,Rotation.NONE,false,1,1,true,2))));
    }
    private static EnergyCubeBlockEntity place(GameTestHelper h,BlockPos pos,EnergyCubeTier tier,Direction facing,Player owner){
        h.setBlock(pos,ModContent.ENERGY_CUBES.get(tier).get().defaultBlockState().setValue(EnergyCubeBlock.FACING,facing));
        var cube=h.getBlockEntity(pos,EnergyCubeBlockEntity.class);cube.placedBy(owner);
        owner.setPos(cube.getBlockPos().getX()+.5,cube.getBlockPos().getY(),cube.getBlockPos().getZ()+.5);
        // Seed storage without spending the per-tick input allowance under test; retain valid schema/slots/owner.
        var data=cube.saveCustomOnly(h.getLevel().registryAccess());data.putInt("energy",tier.transferRate());
        cube.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),data));
        return cube;
    }
    private static void mode(GameTestHelper h,EnergyCubeBlockEntity cube,Player owner,Direction world,EnergyCubeSideMode desired){
        var front=cube.getBlockState().getValue(EnergyCubeBlock.FACING);
        for(var side:MachinePortMode.Side.values())if(side.world(front)==world){
            for(int i=0;i<3&&cube.sideMode(world)!=desired;i++)h.assertTrue(cube.cycleSide(owner,side),"Owner could not configure "+world);
            h.assertTrue(cube.sideMode(world)==desired,"Mode did not follow local-to-world rotation");return;
        }
        throw new IllegalArgumentException("Unknown world face");
    }
    private static EnergyHandler external(GameTestHelper h,EnergyCubeBlockEntity cube,Direction side){
        return h.getLevel().getCapability(Capabilities.Energy.BLOCK,cube.getBlockPos(),side);
    }
    private static void faces(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var pos=new BlockPos(4,3,4);
        for(var tier:EnergyCubeTier.values())for(var front:HORIZONTAL){
            var cube=place(h,pos,tier,front,owner);int rate=tier.transferRate();
            for(var side:HORIZONTAL){
                h.assertTrue(cube.sideMode(side)==(side==front?EnergyCubeSideMode.OUTPUT:EnergyCubeSideMode.INPUT),"Placement rotation changed the default face roles");
                mode(h,cube,owner,side,EnergyCubeSideMode.INPUT);var input=Objects.requireNonNull(external(h,cube,side));
                try(var tx=Transaction.openRoot()){
                    h.assertTrue(input.insert(1,tx)==1&&input.extract(1,tx)==0,"Horizontal INPUT did not enforce its FE role");
                }
                h.assertTrue(cube.energy()==rate,"Simulated horizontal input changed storage");
                mode(h,cube,owner,side,EnergyCubeSideMode.OUTPUT);var output=Objects.requireNonNull(external(h,cube,side));
                try(var tx=Transaction.openRoot()){
                    h.assertTrue(output.extract(1,tx)==1&&output.insert(1,tx)==0,"Horizontal OUTPUT did not enforce its FE role");
                }
                h.assertTrue(cube.energy()==rate,"Simulated horizontal output changed storage");
                mode(h,cube,owner,side,EnergyCubeSideMode.DISABLED);
                h.assertTrue(external(h,cube,side)==null,"Disabled horizontal face exposed a fresh FE capability");
                try(var tx=Transaction.openRoot()){
                    h.assertTrue(input.insert(1,tx)==0&&output.extract(1,tx)==0&&output.getAmountAsLong()==0,
                            "A stale capability bypassed a disabled horizontal face");
                }
            }
            // Four physical ports share one allowance, rather than multiplying it four times.
            for(var side:HORIZONTAL)mode(h,cube,owner,side,EnergyCubeSideMode.INPUT);
            try(var tx=Transaction.openRoot()){
                for(var side:HORIZONTAL)h.assertTrue(Objects.requireNonNull(external(h,cube,side)).insert(rate/4,tx)==rate/4,"Four inputs did not share the tier budget");
                h.assertTrue(Objects.requireNonNull(external(h,cube,Direction.NORTH)).insert(1,tx)==0,"Four faces multiplied input allowance");tx.commit();
            }
            h.assertTrue(cube.energy()==2*rate,"Shared input accounting differs from stored energy");
            for(var side:HORIZONTAL)mode(h,cube,owner,side,EnergyCubeSideMode.OUTPUT);
            try(var tx=Transaction.openRoot()){
                for(var side:HORIZONTAL)h.assertTrue(Objects.requireNonNull(external(h,cube,side)).extract(rate/4,tx)==rate/4,"Four outputs did not share the tier budget");
                h.assertTrue(Objects.requireNonNull(external(h,cube,Direction.NORTH)).extract(1,tx)==0,"Four faces multiplied output allowance");tx.commit();
            }
            h.assertTrue(cube.energy()==rate,"Shared output accounting differs from stored energy");h.setBlock(pos,Blocks.AIR);
        }
        h.succeed();
    }
    private static MatterCableBlock cable(EnergyCubeTier tier){return switch(tier){
        case BASIC->ModContent.MATTER_CABLE.get();case REINFORCED->ModContent.REINFORCED_MATTER_CABLE.get();case RESONANT->ModContent.RESONANT_MATTER_CABLE.get();
    };}
    private static void arm(GameTestHelper h,BlockPos pos,Direction towardCube,boolean connected){
        var state=h.getBlockState(pos);
        h.assertTrue(state.getValue(MatterCableBlock.PROPERTY_BY_DIRECTION.get(towardCube))==connected,"Cable arm disagrees with Cube face availability");
        h.assertTrue(state.getValue(MatterCableBlock.DEVICE_BY_DIRECTION.get(towardCube))==connected,"Cube connector lost its device flange");
    }
    private static void cables(GameTestHelper h){
        var owner=h.makeMockPlayer(GameType.SURVIVAL);var pos=new BlockPos(6,3,6);
        // Transport allowance belongs to the physical cable position and intentionally survives replacement.
        // Reusing this compact fixture therefore needs a fresh tick for each tier/rotation, not a new block object.
        for(int group=0;group<EnergyCubeTier.values().length*HORIZONTAL.length;group++){
            final int index=group;
            h.runAtTickTime(group+1,()->{
            var tier=EnergyCubeTier.values()[index/HORIZONTAL.length];var front=HORIZONTAL[index%HORIZONTAL.length];
            for(var side:HORIZONTAL){
            var cube=place(h,pos,tier,front,owner);
            for(var face:Direction.values())mode(h,cube,owner,face,face==side?EnergyCubeSideMode.INPUT:EnergyCubeSideMode.DISABLED);
            var wire=pos.relative(side);var outside=pos.relative(side,2);var absolute=h.absolutePos(wire);
            h.setBlock(outside,Blocks.STONE);h.setBlock(wire,cable(tier));CableOwners.set(h.getLevel(),absolute,owner.getUUID());
            arm(h,wire,side.getOpposite(),true);
            var incoming=Objects.requireNonNull(MatterCablePower.capability(h.getLevel(),absolute,side));
            try(var tx=Transaction.openRoot()){h.assertTrue(incoming.insert(1,tx)==1,"Native cable could not reach horizontal Cube input: "+tier+" "+front+"/"+side);}
            CableOwners.set(h.getLevel(),absolute,UUID.randomUUID());arm(h,wire,side.getOpposite(),false);
            try(var tx=Transaction.openRoot()){h.assertTrue(incoming.insert(1,tx)==0,"Foreign cable reached horizontal Cube input");}
            CableOwners.set(h.getLevel(),absolute,owner.getUUID());arm(h,wire,side.getOpposite(),true);
            mode(h,cube,owner,side,EnergyCubeSideMode.DISABLED);arm(h,wire,side.getOpposite(),false);
            try(var tx=Transaction.openRoot()){h.assertTrue(incoming.insert(1,tx)==0,"Cached cable route bypassed a disabled horizontal Cube face");}
            mode(h,cube,owner,side,EnergyCubeSideMode.OUTPUT);arm(h,wire,side.getOpposite(),true);
            try(var tx=Transaction.openRoot()){h.assertTrue(incoming.insert(1,tx)==0,"Cable input bypassed horizontal OUTPUT role");}
            h.setBlock(outside,ModContent.MACHINES.get(MachineKind.DECOMPOSER).get().defaultBlockState().setValue(MachineBlock.FACING,side));
            var receiver=h.getBlockEntity(outside,MachineBlockEntity.class);receiver.placedBy(owner);
            CableOwners.set(h.getLevel(),absolute,UUID.randomUUID());cube.serverTick();
            h.assertTrue(cube.energy()==tier.transferRate()&&receiver.energy().getAmountAsLong()==0,"Foreign cable accepted Cube output");
            CableOwners.set(h.getLevel(),absolute,owner.getUUID());cube.serverTick();
            h.assertTrue(cube.energy()==0&&receiver.energy().getAmountAsLong()==tier.transferRate(),"Horizontal Cube output failed native cable delivery");
            h.setBlock(outside,Blocks.AIR);h.setBlock(wire,Blocks.AIR);h.setBlock(pos,Blocks.AIR);
            }
            if(index==EnergyCubeTier.values().length*HORIZONTAL.length-1)h.succeed();
            });
        }
    }
}
