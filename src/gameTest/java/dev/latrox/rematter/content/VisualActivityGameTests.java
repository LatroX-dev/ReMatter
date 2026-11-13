package dev.latrox.rematter.content;

import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeSideMode;
import dev.latrox.rematter.energy.EnergyCubeTier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Visual holds must not buy another tick of work or open a container side. */
@EventBusSubscriber(modid = "rematter")
public final class VisualActivityGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static {
        TESTS.put("processing_visual_hold_does_not_advance_paused_work", VisualActivityGameTests::processing);
        TESTS.put("generator_visual_hold_does_not_generate_while_paused", VisualActivityGameTests::generator);
        TESTS.put("hand_cranks_do_not_blink_at_the_accepted_cadence", VisualActivityGameTests::cranks);
        TESTS.put("module_visual_job_clears_after_a_real_paid_batch", VisualActivityGameTests::moduleJob);
        TESTS.put("energy_cube_flow_is_coarse_and_conserves_contents", VisualActivityGameTests::flow);
        TESTS.put("energy_cube_vanilla_hoppers_obey_side_roles", VisualActivityGameTests::hoppers);
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("rematter_tests", path); }
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach((name, test) -> helper.register(id(name), test)));
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("visual_activity_environment"));
        TESTS.forEach((name, test) -> event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                new TestData<>(environment, id("empty"), name.equals("module_visual_job_clears_after_a_real_paid_batch") ? 1200 : 300, 0, true, Rotation.NONE, false, 1, 1, true, 2))));
    }
    private static MachineBlockEntity machine(GameTestHelper h, BlockPos pos, MachineKind kind, Player owner) {
        h.setBlock(pos, ModContent.MACHINES.get(kind).get());
        var machine = h.getBlockEntity(pos, MachineBlockEntity.class);
        machine.placedBy(owner);
        dev.latrox.rematter.research.ResearchService.ledger(h.getLevel().getServer())
                .visit(owner.getUUID(), h.getLevel().dimension().identifier().toString());
        return machine;
    }
    private static void processing(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var drive = machine(h, new BlockPos(2, 2, 2), MachineKind.DRIVE, owner);
        drive.setItem(0, new ItemStack(ModContent.MATTER_CELL_64K.get()));
        var worker = machine(h, new BlockPos(3, 2, 2), MachineKind.DECOMPOSER, owner);
        worker.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        worker.setItem(0, new ItemStack(Items.SAND));
        worker.serverTick();
        h.assertTrue(worker.visualProgress() > 0 && worker.getBlockState().getValue(MachineBlock.ACTIVE), "Fixture did no real work");
        long energy = worker.energy().getAmountAsLong();
        int progress = worker.visualProgress();
        worker.toggleRunning();
        h.runAfterDelay(8, () -> {
            worker.serverTick();
            h.assertTrue(worker.getBlockState().getValue(MachineBlock.ACTIVE), "Processing visual hold ended before eight ticks");
        });
        h.runAfterDelay(9, () -> {
            worker.serverTick();
            h.assertTrue(!worker.getBlockState().getValue(MachineBlock.ACTIVE), "Processing visual hold lasted more than eight ticks");
            h.assertTrue(worker.energy().getAmountAsLong() == energy && worker.visualProgress() == progress,
                    "Visual hold consumed energy or advanced a paused batch");
            h.assertTrue(worker.getItem(0).is(Items.SAND) && drive.matterStore().total() == 0, "Visual hold completed or duplicated the batch");
            h.succeed();
        });
    }
    private static void generator(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var generator = machine(h, new BlockPos(2, 2, 2), MachineKind.CRYSTAL_GENERATOR, owner);
        generator.setItem(0, new ItemStack(ModContent.MATRIX_CRYSTAL.get(), 2));
        generator.serverTick();
        long energy = generator.energy().getAmountAsLong();
        int count = generator.getItem(0).getCount();
        h.assertTrue(energy > 0 && generator.getBlockState().getValue(MachineBlock.ACTIVE), "Generator fixture produced no FE");
        generator.toggleRunning();
        h.runAfterDelay(20, () -> {
            generator.serverTick();
            h.assertTrue(generator.getBlockState().getValue(MachineBlock.ACTIVE), "Generator hold ended before twenty ticks");
        });
        h.runAfterDelay(21, () -> {
            generator.serverTick();
            h.assertTrue(!generator.getBlockState().getValue(MachineBlock.ACTIVE), "Generator hold lasted more than twenty ticks");
            h.assertTrue(generator.energy().getAmountAsLong() == energy && generator.getItem(0).getCount() == count,
                    "Generator visual hold consumed fuel or generated FE while paused");
            h.succeed();
        });
    }
    private static EnergyCubeBlockEntity cube(GameTestHelper h, BlockPos pos, Player owner) {
        h.setBlock(pos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get());
        var cube = h.getBlockEntity(pos, EnergyCubeBlockEntity.class);
        cube.placedBy(owner);
        return cube;
    }
    private static void cranks(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var manual = machine(h, new BlockPos(3, 3, 3), MachineKind.HAND_PREPARATOR, owner);
        manual.setItem(0, new ItemStack(Items.SAND, 8));
        owner.setPos(manual.getBlockPos().getX() + .5, manual.getBlockPos().getY(), manual.getBlockPos().getZ() + .5);
        h.assertTrue(manual.crank(owner), "First hand crank was not accepted");
        manual.serverTick();
        for (int step = 1; step <= 25; step++) {
            int tick = step;
            h.runAtTickTime(tick, () -> {
                if (tick % 5 == 0) h.assertTrue(manual.crank(owner), "Fastest allowed crank refused at " + tick);
                else h.assertTrue(!manual.crank(owner), "A cooldown crank was unexpectedly accepted");
                manual.serverTick();
                h.assertTrue(manual.getBlockState().getValue(MachineBlock.ACTIVE), "Hand Preparator blinked between accepted cranks");
                h.assertTrue(manual.visualCrankSequence() == 1 + tick / 5, "Accepted crank sequence counts a reset or rejected click");
            });
        }
        h.runAtTickTime(36, () -> {
            manual.serverTick();
            h.assertTrue(!manual.getBlockState().getValue(MachineBlock.ACTIVE), "Hand Preparator stayed lit after its hold");
            h.assertTrue(manual.getItem(0).getCount() == 7 && manual.getItem(1).getCount() == 1,
                    "Visual hold supplied extra manual work or consumed extra input");
            h.assertTrue(manual.energy().getAmountAsLong() == 0, "Manual work acquired FE");
            h.assertTrue(manual.visualCrankSequence() == 6 && !manual.saveCustomOnly(h.getLevel().registryAccess()).contains("visual_crank_sequence"),
                    "Visual crank sequence changed without a crank or leaked into persistent data");
            h.succeed();
        });
    }
    private static void moduleJob(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var origin = h.absolutePos(new BlockPos(4, 2, 3));
        var shape = new IndustrialStructure.Shape(3, 7, 3, 1);
        for (var cell : IndustrialStructure.layout(shape)) {
            var block = switch (cell.role()) {
                case FRAME -> ModContent.REACTOR_CASING.get();
                case WALL -> cell.outward().getAxis().isHorizontal() ? ModContent.REACTOR_GLASS.get() : ModContent.REACTOR_CASING.get();
                default -> Blocks.AIR;
            };
            h.getLevel().setBlock(IndustrialStructure.worldPosition(origin, Direction.NORTH, cell), block.defaultBlockState(), 3);
        }
        var controller = machine(h, new BlockPos(4, 2, 3), MachineKind.INDUSTRIAL_REACTOR, owner);
        h.setBlock(3, 2, 4, ModContent.MACHINE_PORT.get());
        h.setBlock(5, 2, 4, ModContent.MACHINE_PORT.get());
        h.setBlock(4, 2, 4, ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.REACTION_CHAMBER).get());
        h.setBlock(4, 6, 4, ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.FIELD_COIL).get());
        var module = h.getBlockEntity(new BlockPos(4, 2, 4), IndustrialModuleBlockEntity.class);
        h.assertTrue(IndustrialStructure.inspect(controller).formed(), "Visual-job chamber fixture did not form");
        var drive = machine(h, new BlockPos(2, 2, 4), MachineKind.DRIVE, owner);
        drive.setItem(0, new ItemStack(ModContent.MATTER_CELL_64K.get()));
        h.assertTrue(drive.matterStore().apply(Map.of(), Map.of(dev.latrox.rematter.chemistry.BuiltinChemistry.WATER, 1000L)), "Module fixture lacks water");
        controller.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        h.assertTrue(controller.selectRecipe("rematter:water_freezing") && controller.requestBatch(), "Module fixture cannot request one real batch");
        long cost = controller.cost(dev.latrox.rematter.ReMatter.chemistry().recipe(dev.latrox.rematter.chemistry.SubstanceId.parse("rematter:water_freezing")).orElseThrow().energy());
        int[] paused = {0};
        h.runAtTickTime(10, () -> {
            controller.serverTick();
            h.assertTrue(module.visualWorkDuration() == controller.visualDuration() && module.visualWorkDuration() > 0
                    && module.visualWorkProgress() == controller.visualProgress() && module.visualBaseRate() == 2,
                    "Module does not publish the controller raw work and base rate");
            paused[0] = module.visualWorkProgress();
            controller.toggleRunning();
        });
        h.runAtTickTime(25, () -> {
            h.assertTrue(module.visualWorkDuration() > 0 && module.visualWorkProgress() == paused[0]
                    && !module.getBlockState().getValue(IndustrialModuleBlock.ACTIVE), "Paused module lost its job or gained work");
            h.assertTrue(controller.energy().getAmountAsLong() == MachineBlockEntity.ENERGY_CAPACITY
                    && drive.matterStore().amount(dev.latrox.rematter.chemistry.BuiltinChemistry.WATER) == 1000L,
                    "Visual module hold paid or consumed the paused batch");
            controller.toggleRunning();
        });
        int workTicks = Math.ceilDiv(dev.latrox.rematter.ReMatter.chemistry().recipe(dev.latrox.rematter.chemistry.SubstanceId.parse("rematter:water_freezing")).orElseThrow().ticks(), controller.speed());
        h.runAtTickTime(25 + workTicks + 20, () -> {
            h.assertTrue(module.visualWorkDuration() == 0 && module.visualWorkProgress() == 0
                    && !module.getBlockState().getValue(IndustrialModuleBlock.ACTIVE), "Idle formed module kept a loaded job");
            h.assertTrue(drive.matterStore().amount(dev.latrox.rematter.chemistry.BuiltinChemistry.ICE) == 1000L
                    && drive.matterStore().amount(dev.latrox.rematter.chemistry.BuiltinChemistry.WATER) == 0L
                    && controller.energy().getAmountAsLong() == MachineBlockEntity.ENERGY_CAPACITY - cost,
                    "Module visual lifecycle lost quantities or changed the one-batch FE price");
            h.succeed();
        });
    }
    private static void flow(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var cube = cube(h, new BlockPos(3, 3, 3), owner);
        int roles = cube.packedSideModes();
        cube.serverTick();
        var previous = new EnergyCubeBlock.Flow[]{EnergyCubeBlock.Flow.NONE};
        int[] lastChange = {-20};
        for (int step = 1; step <= 120; step++) {
            int tick = step;
            h.runAtTickTime(tick, () -> {
                if (tick <= 30 || tick > 60 && tick <= 90) try (var tx = Transaction.openRoot()) {
                    int moved = tick <= 30 ? Objects.requireNonNull(cube.sidedEnergy(Direction.UP)).insert(100, tx)
                            : Objects.requireNonNull(cube.sidedEnergy(Direction.NORTH)).extract(100, tx);
                    h.assertTrue(moved == 100, "Flow fixture transfer was refused");
                    tx.commit();
                }
                cube.serverTick();
                var shown = cube.getBlockState().getValue(EnergyCubeBlock.FLOW);
                if (shown != previous[0]) {
                    h.assertTrue(tick - lastChange[0] >= 20, "Flow changed twice inside one second");
                    previous[0] = shown;
                    lastChange[0] = tick;
                }
                if (tick == 30) h.assertTrue(shown == EnergyCubeBlock.Flow.IN, "Sustained charging has no upward flow");
                if (tick == 60 || tick == 120) h.assertTrue(shown == EnergyCubeBlock.Flow.NONE, "Steady buffer kept its flow animation");
                if (tick == 90) h.assertTrue(shown == EnergyCubeBlock.Flow.OUT, "Sustained discharge has no downward flow");
                h.assertTrue(cube.packedSideModes() == roles && cube.isEmpty(), "Flow changed side modes or item contents");
                if (tick == 120) {
                    h.assertTrue(cube.energy() == 0, "Flow display created or lost FE");
                    h.succeed();
                }
            });
        }
    }
    private static HopperBlockEntity hopper(GameTestHelper h, BlockPos pos, Direction target, ItemStack content) {
        h.setBlock(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, target));
        var hopper = h.getBlockEntity(pos, HopperBlockEntity.class);
        hopper.setItem(0, content);
        return hopper;
    }
    private static void hoppers(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var closed = cube(h, new BlockPos(3, 3, 3), owner);
        owner.setPos(closed.getBlockPos().getX() + .5, closed.getBlockPos().getY(), closed.getBlockPos().getZ() + .5);
        while (closed.sideMode(Direction.UP) != EnergyCubeSideMode.DISABLED)
            h.assertTrue(closed.cycleSide(owner, MachinePortMode.Side.TOP), "Could not disable cube top");
        var blocked = hopper(h, new BlockPos(3, 4, 3), Direction.DOWN, new ItemStack(Items.IRON_PICKAXE));
        var allowed = hopper(h, new BlockPos(4, 3, 3), Direction.WEST, new ItemStack(Items.IRON_PICKAXE));
        var partial = cube(h, new BlockPos(7, 3, 3), owner);
        var item = new ItemStack(Items.IRON_PICKAXE);
        item.set(DataComponents.REPAIR_COST, 5000);
        partial.setItem(0, item);
        owner.setPos(partial.getBlockPos().getX() + .5, partial.getBlockPos().getY(), partial.getBlockPos().getZ() + .5);
        while (partial.sideMode(Direction.DOWN) != EnergyCubeSideMode.OUTPUT)
            h.assertTrue(partial.cycleSide(owner, MachinePortMode.Side.BOTTOM), "Could not enable cube output");
        var output = hopper(h, new BlockPos(7, 2, 3), Direction.EAST, ItemStack.EMPTY);
        h.runAtTickTime(30, () -> {
            h.assertTrue(blocked.getItem(0).is(Items.IRON_PICKAXE), "Vanilla hopper inserted through Disabled face");
            h.assertTrue(allowed.isEmpty() && closed.getItem(0).is(Items.IRON_PICKAXE), "Control hopper did not use the Input face");
            h.assertTrue(output.isEmpty() && partial.getItem(0).getOrDefault(DataComponents.REPAIR_COST, 0) == 5000,
                    "Vanilla hopper extracted an incompletely charged item");
            partial.getItem(0).set(DataComponents.REPAIR_COST, 10_000);
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(partial.isEmpty() && output.getItem(0).getOrDefault(DataComponents.REPAIR_COST, 0) == 10_000,
                    "Output hopper did not extract the fully charged item exactly once");
            h.assertTrue(blocked.getItem(0).is(Items.IRON_PICKAXE), "Disabled hopper lost its item later");
            h.succeed();
        });
    }
}
