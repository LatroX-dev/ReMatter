package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.compat.mekanism.MekanismCompat;
import dev.latrox.rematter.research.LearningPath;
import dev.latrox.rematter.research.ResearchService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Server GameTests covering Atmospheric Collector, Drive Cell Consolidation,
 * Industrial Auto-Builder, Terminal Multi-Step Batch Coordinator, and Cross-Mod Pressurized Canister compat.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class OverhaulExpansionGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static {
        TESTS.put("atmospheric_collector_harvests_open_air_and_cave_sources_at_double_efficiency", OverhaulExpansionGameTests::atmosphericCollector);
        TESTS.put("drive_cell_consolidation_packs_partitioned_cells_first_and_coalesces_split_stacks", OverhaulExpansionGameTests::driveCellConsolidation);
        TESTS.put("industrial_auto_builder_constructs_shell_and_mounted_fittings_and_port_canister", OverhaulExpansionGameTests::industrialAutoBuilderAndPortCanister);
        TESTS.put("terminal_multi_step_batch_coordinator_executes_chain_and_supports_cancel", OverhaulExpansionGameTests::terminalBatchCoordinator);
        TESTS.put("terminal_jobs_count_only_their_own_commits_and_preserve_manual_work", OverhaulExpansionGameTests::terminalJobIsolation);
        TESTS.put("pressurized_canister_cross_mod_ae2_fluid_guards_and_mekanism_compat", OverhaulExpansionGameTests::pressurizedCanisterCrossMod);
    }

    private OverhaulExpansionGameTests() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("rematter_tests", path);
    }

    @SubscribeEvent
    public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach((name, fn) -> helper.register(id(name), fn)));
    }

    @SubscribeEvent
    public static void tests(RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(id("overhaul_expansion_environment"));
        TESTS.forEach((name, fn) -> event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                new TestData<>(env, id("empty"), 2_400, 0, true, Rotation.NONE, false, 1, 1, true, 2))));
    }

    private static MachineBlockEntity placeMachine(GameTestHelper h, BlockPos relPos, MachineKind kind, Direction facing, Player owner) {
        h.setBlock(relPos, ModContent.MACHINES.get(kind).get().defaultBlockState().setValue(MachineBlock.FACING, facing));
        var be = h.getBlockEntity(relPos, MachineBlockEntity.class);
        be.placedBy(owner);
        return be;
    }

    private static BlockHitResult hit(GameTestHelper h, BlockPos relPos, Direction face) {
        BlockPos worldPos = h.absolutePos(relPos);
        Vec3 center = Vec3.atCenterOf(worldPos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return new BlockHitResult(center, face, worldPos, false);
    }

    private static void registerResearchOwner(GameTestHelper h, Player owner) {
        var server = h.getLevel().getServer();
        ResearchService.ledger(server).visit(owner.getUUID(), h.getLevel().dimension().identifier().toString());
    }

    private static void atmosphericCollector(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        registerResearchOwner(h, owner);

        BlockPos driveRel = new BlockPos(2, 2, 2);
        BlockPos collectorRel = new BlockPos(3, 2, 2);
        var drive = placeMachine(h, driveRel, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_64K.get()));

        var collector = placeMachine(h, collectorRel, MachineKind.ATMOSPHERIC_COLLECTOR, Direction.NORTH, owner);
        collector.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        h.assertTrue(collector.selectRecipe("rematter:air_oxygen"), "Atmospheric Collector should select air_oxygen");

        // Block sky directly above collector: open-air source must pause without spending any FE.
        h.setBlock(collectorRel.above(2), Blocks.STONE);
        collector.requestBatch();

        h.runAtTickTime(40, () -> {
            var inv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
            h.assertTrue(inv.amount(BuiltinChemistry.OXYGEN) == 0L, "Roofed open-air collector must not harvest oxygen");
            h.assertTrue(collector.energy().getAmountAsLong() == MachineBlockEntity.ENERGY_CAPACITY,
                    "Roofed open-air collector must not spend FE");
            // Clear sky so real server ticks propagate sky light and run at 2x speed (400 ticks) and half base FE (8,000 FE).
            h.setBlock(collectorRel.above(2), Blocks.AIR);
        });

        h.runAtTickTime(470, () -> {
            var inv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
            long expectedEnergyAfterAir = MachineBlockEntity.ENERGY_CAPACITY - 8_000L;
            h.assertTrue(inv.amount(BuiltinChemistry.OXYGEN) == 200L,
                    "Expected 200 OXYGEN from 1 air_oxygen batch, got " + inv.amount(BuiltinChemistry.OXYGEN));
            h.assertTrue(collector.energy().getAmountAsLong() == expectedEnergyAfterAir,
                    "Expected 8,000 FE spent (half of 16,000 base), remaining=" + collector.energy().getAmountAsLong());

            // Subterranean source (cavern_vapor) works even when roofed over!
            h.setBlock(collectorRel.above(2), Blocks.STONE);
            collector.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
            h.assertTrue(collector.selectRecipe("rematter:cavern_vapor"), "Collector should select cavern_vapor");
            collector.requestBatch();
        });

        h.succeedWhen(() -> {
            var inv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
            h.assertTrue(inv.amount(BuiltinChemistry.STEAM) == 250L,
                    "Expected 250 STEAM from roofed cavern_vapor batch, got " + inv.amount(BuiltinChemistry.STEAM)
                            + " status=" + collector.snapshot(0).status());
        });
    }

    private static ItemStack cell64kWith(Map<SubstanceKey, Long> contents) {
        ItemStack stack = new ItemStack(ModContent.MATTER_CELL_64K.get());
        if (!contents.isEmpty()) {
            MatterInventory inv = new MatterInventory(MatterLimits.CELL_64K);
            inv.apply(Map.of(), contents);
            stack.set(MatterCellData.TYPE.get(), MatterCellData.from(inv));
        }
        return stack;
    }

    private static Map<SubstanceKey, Long> readCell64k(ItemStack stack) {
        MatterCellData data = stack.get(MatterCellData.TYPE.get());
        if (data == null) return Map.of();
        return data.open(MatterLimits.CELL_64K).orElseThrow().contents();
    }

    private static void driveCellConsolidation(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos driveRel = new BlockPos(2, 2, 2);
        var drive = placeMachine(h, driveRel, MachineKind.DRIVE, Direction.NORTH, owner);
        BlockPos absDrive = h.absolutePos(driveRel);
        owner.setPos(absDrive.getX() + 0.5, absDrive.getY() + 0.5, absDrive.getZ() + 0.5);

        ItemStack cell0 = cell64kWith(Map.of(
                BuiltinChemistry.COPPER, 10_000L,
                BuiltinChemistry.IRON, 12_000L,
                BuiltinChemistry.SILICON, 5_000L));
        ItemStack cell1 = cell64kWith(Map.of(
                BuiltinChemistry.COPPER, 15_000L,
                BuiltinChemistry.IRON, 8_000L,
                BuiltinChemistry.SILICON, 7_000L));
        ItemStack cell2 = cell64kWith(Map.of(BuiltinChemistry.GOLD, 40_000L));

        // Bay 3 is explicitly partitioned for COPPER only and starts empty.
        ItemStack partitionedCopperCell = new ItemStack(ModContent.MATTER_CELL_64K.get());
        partitionedCopperCell.set(CellPartitionData.TYPE.get(), CellPartitionData.allowOnly(Set.of(BuiltinChemistry.COPPER)));

        drive.setItem(drive.cellSlotForBay(0), cell0);
        drive.setItem(drive.cellSlotForBay(1), cell1);
        drive.setItem(drive.cellSlotForBay(2), cell2);
        drive.setItem(drive.cellSlotForBay(3), partitionedCopperCell);

        h.assertTrue(drive.consolidateCells(owner), "Drive cell consolidation should succeed and move scattered stock");
        h.assertTrue(drive.storageStatus().equals("ready"),
                "Drive storage status must stay 'ready' without quantityConflict, got " + drive.storageStatus());

        var netInv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
        h.assertTrue(netInv.amount(BuiltinChemistry.COPPER) == 25_000L, "COPPER total not conserved");
        h.assertTrue(netInv.amount(BuiltinChemistry.IRON) == 20_000L, "IRON total not conserved");
        h.assertTrue(netInv.amount(BuiltinChemistry.SILICON) == 12_000L, "SILICON total not conserved");
        h.assertTrue(netInv.amount(BuiltinChemistry.GOLD) == 40_000L, "GOLD total not conserved");

        // Verify partitioned Bay 3 absorbed all 25,000 COPPER first, and Bay 2 was freed completely (remaining 72k fits in Bays 0 + 1).
        var bay3Amounts = readCell64k(drive.getItem(drive.cellSlotForBay(3)));
        h.assertTrue(bay3Amounts.getOrDefault(BuiltinChemistry.COPPER, 0L) == 25_000L,
                "Partitioned bay 3 should hold all 25,000 COPPER, got " + bay3Amounts);
        var bay2Amounts = readCell64k(drive.getItem(drive.cellSlotForBay(2)));
        h.assertTrue(bay2Amounts.isEmpty(),
                "Coalescing 72,000 remaining units into Bays 0+1 (128,000 cap) should empty Bay 2, got " + bay2Amounts);
        h.succeed();
    }

    private static void industrialAutoBuilderAndPortCanister(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        registerResearchOwner(h, owner);

        BlockPos ctrlRel = new BlockPos(4, 3, 2);
        var reactor = placeMachine(h, ctrlRel, MachineKind.INDUSTRIAL_REACTOR, Direction.NORTH, owner);
        BlockPos absCtrl = h.absolutePos(ctrlRel);
        owner.setPos(absCtrl.getX() + 0.5, absCtrl.getY(), absCtrl.getZ() - 1.5);

        // Equip Matter Wrench in slot 0 BEFORE adding building materials to slots 1..5!
        ItemStack wrench = new ItemStack(ModContent.MATTER_WRENCH.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        owner.getInventory().add(new ItemStack(ModContent.REACTOR_CASING_ITEM.get(), 64));
        owner.getInventory().add(new ItemStack(ModContent.REACTOR_GLASS_ITEM.get(), 32));
        owner.getInventory().add(new ItemStack(ModContent.MACHINE_PORT_ITEM.get(), 8));
        owner.getInventory().add(new ItemStack(ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.REACTION_CHAMBER).get(), 4));
        owner.getInventory().add(new ItemStack(ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.FIELD_COIL).get(), 4));

        // 1. Place a foreign Stone block inside the 3x4x3 chamber volume: Auto-Build must abort completely (0 placed, 0 consumed, preview ON).
        BlockPos obstructPos = IndustrialStructure.worldPosition(
                absCtrl, Direction.NORTH, new IndustrialStructure.Cell(0, 0, 1, IndustrialStructure.Role.INTERIOR, null));
        h.getLevel().setBlock(obstructPos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        ChunkRevisions.touchLoaded(h.getLevel(), obstructPos);
        int casingsBefore = owner.getInventory().countItem(ModContent.REACTOR_CASING_ITEM.get());

        var blockedRes = IndustrialAutoBuilder.build(h.getLevel(), owner, reactor);
        h.assertTrue(blockedRes.placed() == 0 && blockedRes.obstructed() == 1 && !blockedRes.formed(),
                "Obstructed Auto-Build must place 0 blocks and report 1 obstruction, got " + blockedRes);
        h.assertTrue(owner.getInventory().countItem(ModContent.REACTOR_CASING_ITEM.get()) == casingsBefore,
                "Obstructed Auto-Build must not consume any inventory items");
        h.assertTrue(h.getLevel().getBlockState(absCtrl.west()).isAir(),
                "Obstructed Auto-Build must leave surrounding shell positions untouched");
        h.assertTrue(reactor.structurePreviewEnabled(),
                "Obstructed Auto-Build should automatically enable the chamber preview overlay");

        // 2. Clear the obstruction and trigger Auto-Build via Matter Wrench: full chamber should now assemble and form!
        h.getLevel().setBlock(obstructPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        ChunkRevisions.touchLoaded(h.getLevel(), obstructPos);

        var wrenchRes = WrenchInteractions.interact(owner, h.getLevel(), absCtrl, wrench, hit(h, ctrlRel, Direction.NORTH));
        h.assertTrue(wrenchRes.consumesAction(), "Matter Wrench in PORTS mode on unformed controller should trigger Auto-Build");

        var inspection = IndustrialStructure.inspect(reactor);
        h.assertTrue(inspection.formed(), "Auto-built 3x4x3 Industrial Reactor should form immediately, status=" + inspection.diagnostic());
        h.assertTrue(!reactor.structurePreviewEnabled(), "Completing Auto-Build must disable structure preview overlay");
        h.assertTrue(!reactor.extensions().action(owner, new dev.latrox.rematter.network.MachineActionPayload(
                        0, dev.latrox.rematter.network.MachineActionPayload.Action.BLUEPRINT_STEP_UP, "WIDTH", 1)),
                "Formed Industrial Reactor must reject blueprint configuration changes");
        h.assertTrue(!reactor.extensions().action(owner, new dev.latrox.rematter.network.MachineActionPayload(
                        0, dev.latrox.rematter.network.MachineActionPayload.Action.BLUEPRINT_BUILD, "", 0)),
                "Formed Industrial Reactor must reject Auto-Build action");
        h.assertTrue(inspection.performance().field() >= 1 && inspection.performance().processRoom() >= 16_000L,
                "Auto-built Industrial Reactor should mount Field Coil and Reaction Chamber: " + inspection.performance());

        // Verify suspended Field Coil vs floor-mounted Reaction Chamber states inside the 3x4x3 interior (local x=0, y=0/1, depth=1).
        BlockPos interiorFloor = IndustrialStructure.worldPosition(
                absCtrl, Direction.NORTH, new IndustrialStructure.Cell(0, 0, 1, IndustrialStructure.Role.INTERIOR, null));
        BlockPos interiorCeiling = IndustrialStructure.worldPosition(
                absCtrl, Direction.NORTH, new IndustrialStructure.Cell(0, 1, 1, IndustrialStructure.Role.INTERIOR, null));
        var floorState = h.getLevel().getBlockState(interiorFloor);
        var ceilingState = h.getLevel().getBlockState(interiorCeiling);
        h.assertTrue(floorState.getBlock() instanceof IndustrialModuleBlock && !floorState.getValue(IndustrialModuleBlock.SUSPENDED),
                "Floor fitting should have SUSPENDED=false: " + floorState);
        h.assertTrue(ceilingState.getBlock() instanceof IndustrialModuleBlock && ceilingState.getValue(IndustrialModuleBlock.SUSPENDED),
                "Ceiling fitting should have SUSPENDED=true: " + ceilingState);

        // Connect one of the formed Machine Ports as MATTER to a Drive and test Pressurized Canister deposit via MachinePortBlock!
        var firstPort = inspection.ports().getFirst();
        BlockPos portWorld = firstPort.position();
        owner.setPos(portWorld.getX() + 0.5, portWorld.getY(), portWorld.getZ() + 0.5);
        var portBe = Objects.requireNonNull((MachinePortBlockEntity) h.getLevel().getBlockEntity(portWorld));
        for (int guard = 0; guard < 8 && portBe.mode() != MachinePortMode.MATTER; guard++) {
            if (!portBe.cycleMode(owner, firstPort.outward())) break;
        }

        BlockPos driveWorld = portWorld.relative(firstPort.outward());
        h.getLevel().setBlock(
                driveWorld,
                ModContent.MACHINES.get(MachineKind.DRIVE).get().defaultBlockState().setValue(MachineBlock.FACING, firstPort.outward()),
                3);
        var drive = Objects.requireNonNull((MachineBlockEntity) h.getLevel().getBlockEntity(driveWorld));
        drive.placedBy(owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_64K.get()));
        reactor.serverTick();
        portBe.refreshNow();

        ItemStack canister = new ItemStack(ModContent.PRESSURIZED_CANISTER.get());
        PressurizedCanisterData.writeMatter(canister, BuiltinChemistry.HYDROGEN, 5_000);
        owner.setItemInHand(InteractionHand.MAIN_HAND, canister);
        owner.setPos(portWorld.getX() + 0.5, portWorld.getY(), portWorld.getZ() + 0.5);

        BlockState portState = h.getLevel().getBlockState(portWorld);
        BlockHitResult portHit = new BlockHitResult(Vec3.atCenterOf(portWorld), firstPort.outward(), portWorld, false);
        var portUse = portState.useItemOn(canister, h.getLevel(), owner, InteractionHand.MAIN_HAND, portHit);
        h.assertTrue(portUse.consumesAction(), "Pressurized Canister on MATTER MachinePort should consume action");
        h.assertTrue(PressurizedCanisterItem.storedAmount(canister) == 0,
                "Canister should empty into network via MachinePort, remaining=" + PressurizedCanisterItem.storedAmount(canister)
                        + " netStatus=" + reactor.matterNetwork(true).status()
                        + " portMode=" + portBe.mode()
                        + " portFormed=" + portState.getValue(MachinePortBlock.FORMED));
        h.assertTrue(Objects.requireNonNull(drive.matterNetwork(true).inventory()).amount(BuiltinChemistry.HYDROGEN) == 5_000L,
                "Drive should receive 5,000 HYDROGEN through multiblock MachinePort");
        h.succeed();
    }

    private static void terminalBatchCoordinator(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        registerResearchOwner(h, owner);
        var server = h.getLevel().getServer();
        var rCtx = ResearchService.context(server, owner.getUUID());
        ResearchService.learn(h.getLevel(), owner.getUUID(), "minecraft:glass", LearningPath.ANALYSIS);
        ResearchService.ledger(server).learnSubstances(
                rCtx, Set.of(BuiltinChemistry.SILICON, BuiltinChemistry.OXYGEN, BuiltinChemistry.SILICA));

        // Build compact connected row: DRIVE(1,2,2) - ATMOSPHERIC_COLLECTOR(2,2,2) - REACTOR(3,2,2) - SYNTHESIZER(4,2,2)
        // and TERMINAL at (1,2,3) facing SOUTH so its rear face (NORTH) connects directly to DRIVE's rear face (SOUTH)!
        var drive = placeMachine(h, new BlockPos(1, 2, 2), MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_64K.get()));
        var collector = placeMachine(h, new BlockPos(2, 2, 2), MachineKind.ATMOSPHERIC_COLLECTOR, Direction.NORTH, owner);
        var reactor = placeMachine(h, new BlockPos(3, 2, 2), MachineKind.REACTOR, Direction.NORTH, owner);
        var synth = placeMachine(h, new BlockPos(4, 2, 2), MachineKind.SYNTHESIZER, Direction.NORTH, owner);
        var terminal = placeMachine(h, new BlockPos(1, 2, 3), MachineKind.TERMINAL, Direction.SOUTH, owner);

        // Give Speed Upgrades (4x) to workers so the 3-step chain finishes rapidly across real server ticks!
        collector.setItem(2, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        reactor.setItem(2, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        synth.setItem(2, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        collector.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        reactor.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        synth.energy().set(MachineBlockEntity.ENERGY_CAPACITY);

        var net = terminal.matterNetwork(true);
        var inv = Objects.requireNonNull(net.inventory(), "Terminal network inventory should be ready");
        h.assertTrue(inv.apply(Map.of(), Map.of(BuiltinChemistry.SILICON, 4_000L)), "Failed to seed SILICON");
        net.markChanged();

        var planCtx = TerminalJobCoordinator.buildNetworkContext(h.getLevel(), owner.getUUID(), null, terminal.matterNetwork(true));
        var plan = TerminalBatchPlanner.plan(
                ReMatter.chemistry(),
                TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM,
                "minecraft:glass",
                "",
                1,
                planCtx);
        h.assertTrue(plan.executable(), "Multi-step glass plan should be READY, got " + plan.status() + " missing=" + plan.missingMachines());

        BlockPos absTerminal = h.absolutePos(new BlockPos(1, 2, 3));
        int jobId = TerminalJobCoordinator.submit(
                h.getLevel(),
                absTerminal,
                owner.getUUID(),
                TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM,
                "minecraft:glass",
                "",
                1,
                plan);
        h.assertTrue(jobId > 0, "Job submission should return positive jobId");

        // Keep worker FE topped up while real server ticks advance ControllerThroughput and ServerTickEvent.Post!
        h.onEachTick(() -> {
            collector.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
            reactor.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
            synth.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        });

        h.succeedWhen(() -> {
            h.assertTrue(TerminalJobCoordinator.jobsFor(h.getLevel(), owner.getUUID()).isEmpty(),
                    "Multi-step glass job should complete within simulated ticks: "
                            + TerminalJobCoordinator.jobsFor(h.getLevel(), owner.getUUID())
                            + " collector=" + collector.snapshot(0).status()
                            + " reactor=" + reactor.snapshot(0).status()
                            + " synth=" + synth.snapshot(0).status());
            ItemStack synthesized = synth.getItem(1);
            h.assertTrue(synthesized.is(Items.GLASS) && synthesized.getCount() == 1,
                    "Synthesizer output slot 1 should contain 1x minecraft:glass, got " + synthesized);

            // Also verify immediate 1-click cancellation on a newly submitted multi-batch job!
            var cancelPlan = TerminalBatchPlanner.plan(
                    ReMatter.chemistry(),
                    TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM,
                    "minecraft:glass",
                    "",
                    2,
                    TerminalJobCoordinator.buildNetworkContext(h.getLevel(), owner.getUUID(), null, terminal.matterNetwork(true)));
            int cancelId = TerminalJobCoordinator.submit(
                    h.getLevel(),
                    absTerminal,
                    owner.getUUID(),
                    TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM,
                    "minecraft:glass",
                    "",
                    2,
                    cancelPlan);
            h.assertTrue(cancelId > 0, "Second job should submit");
            h.assertTrue(TerminalJobCoordinator.cancel(h.getLevel(), owner.getUUID(), cancelId), "Cancel should succeed");
            h.assertTrue(TerminalJobCoordinator.jobsFor(h.getLevel(), owner.getUUID()).isEmpty(), "Jobs list should be empty after cancel");
        });
    }

    private static void terminalJobIsolation(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        registerResearchOwner(h, owner);
        var level = h.getLevel();
        var ownerId = owner.getUUID();
        ResearchService.ledger(level.getServer()).learnSubstances(
                ResearchService.context(level.getServer(), ownerId), Set.of(BuiltinChemistry.OXYGEN));
        var drive = placeMachine(h, new BlockPos(1, 2, 2), MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_64K.get()));
        var collector = placeMachine(h, new BlockPos(2, 2, 2), MachineKind.ATMOSPHERIC_COLLECTOR, Direction.NORTH, owner);
        var terminal = placeMachine(h, new BlockPos(1, 2, 3), MachineKind.TERMINAL, Direction.SOUTH, owner);
        collector.energy().set(MachineBlockEntity.ENERGY_CAPACITY);
        collector.setItem(2, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        var kind = TerminalBatchPlanner.TargetKind.SUBSTANCE;
        var plan = TerminalBatchPlanner.plan(ReMatter.chemistry(), kind, "rematter:oxygen", "gas", 256,
                TerminalJobCoordinator.buildNetworkContext(level, ownerId, null, terminal.matterNetwork(true)));
        h.assertTrue(plan.executable() && plan.steps().getFirst().batches() == 2, "Expected two Oxygen batches");
        java.util.function.IntSupplier submit = () -> TerminalJobCoordinator.submit(level,
                terminal.getBlockPos(), ownerId, kind, "rematter:oxygen", "gas", 256, plan);
        int first = submit.getAsInt();
        int second = submit.getAsInt();
        h.assertTrue(first > 0 && second > first && collector.queuedBatchCount() == 2,
                "Same-recipe jobs must have separate ownership of unpaid work");
        h.assertTrue(collector.saveCustomOnly(level.registryAccess()).getIntOr("queued_batches", -1) == 0,
                "A live terminal lease must not be saved as a manual queue");
        var inv = Objects.requireNonNull(terminal.matterNetwork(true).inventory());
        h.succeedWhen(() -> {
            var jobs = TerminalJobCoordinator.jobsFor(level, ownerId);
            h.assertTrue(inv.amount(BuiltinChemistry.OXYGEN) == 200, "Waiting for one paid batch");
            h.assertTrue(jobs.size() == 2 && jobs.getFirst().completedBatches() == 1
                    && jobs.getLast().completedBatches() == 0, "Only the job that paid for the batch gets credit");
            h.assertTrue(TerminalJobCoordinator.cancel(level, ownerId, second) && collector.queuedBatchCount() == 1,
                    "Canceling a waiting job must keep the running job's remaining batch");
            int third = submit.getAsInt();
            h.assertTrue(third > second && TerminalJobCoordinator.cancel(level, ownerId, first)
                    && collector.queuedBatchCount() == 0, "Canceling the running job removes only its unpaid work");
            TerminalJobCoordinator.tickServer(level.getServer());
            h.assertTrue(collector.queuedBatchCount() == 2 && collector.requestBatch() && collector.queuedBatchCount() == 1,
                    "A manual batch takes priority over unpaid terminal work");
            h.assertTrue(TerminalJobCoordinator.cancel(level, ownerId, third) && collector.queuedBatchCount() == 1,
                    "Canceling a terminal job must preserve the manual batch of the same recipe");
            h.assertTrue(collector.saveCustomOnly(level.registryAccess()).getIntOr("queued_batches", -1) == 1,
                    "Manual batches still persist");
            h.assertTrue(inv.amount(BuiltinChemistry.OXYGEN) == 200, "Canceling must preserve completed output");
        });
    }

    private static final class Ae2RuntimeCheck {
        static void verify(GameTestHelper h) {
            var ae2Strategy = new dev.latrox.rematter.compat.ae2.Ae2CanisterStrategy();
            AtomicReference<ItemStack> slotRef = new AtomicReference<>(new ItemStack(ModContent.PRESSURIZED_CANISTER.get()));
            var ctx = new dev.latrox.rematter.compat.ae2.Ae2CanisterStrategy.Context(slotRef::get, slotRef::set);

            var hydrogenKey = dev.latrox.rematter.compat.ae2.MatterKey.of(BuiltinChemistry.HYDROGEN);
            var copperKey = dev.latrox.rematter.compat.ae2.MatterKey.of(BuiltinChemistry.COPPER);
            h.assertTrue(ae2Strategy.insert(ctx, copperKey, 1_000L, appeng.api.config.Actionable.MODULATE) == 0L,
                    "AE2 canister strategy must reject solid COPPER");
            h.assertTrue(ae2Strategy.insert(ctx, hydrogenKey, 20_000L, appeng.api.config.Actionable.SIMULATE) == PressurizedCanisterData.CAPACITY,
                    "AE2 canister simulate insert should clamp to 16,000");
            h.assertTrue(PressurizedCanisterItem.storedAmount(slotRef.get()) == 0, "Simulate insert must not mutate stack");
            h.assertTrue(ae2Strategy.insert(ctx, hydrogenKey, 10_000L, appeng.api.config.Actionable.MODULATE) == 10_000L,
                    "AE2 canister modulate insert should store 10,000 HYDROGEN");
            var contained = Objects.requireNonNull(ae2Strategy.getContainedStack(slotRef.get()));
            h.assertTrue(contained.what().equals(hydrogenKey) && contained.amount() == 10_000L,
                    "AE2 getContainedStack mismatch: " + contained);
            h.assertTrue(ae2Strategy.extract(ctx, hydrogenKey, 4_000L, appeng.api.config.Actionable.MODULATE) == 4_000L,
                    "AE2 modulate extract should extract 4,000");
            h.assertTrue(PressurizedCanisterItem.storedAmount(slotRef.get()) == 6_000,
                    "Remaining canister amount should be 6,000");
        }
    }

    private static void pressurizedCanisterCrossMod(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);

        // 1. Optional AE2 ContainerItemStrategy runtime check when AE2 is loaded.
        if (ModList.get().isLoaded("ae2")) {
            Ae2RuntimeCheck.verify(h);
        }

        // 2. Terminal inventory canister fill + drain & solid-phase exclusion.
        BlockPos termDriveRel = new BlockPos(1, 2, 1);
        var termDrive = placeMachine(h, termDriveRel, MachineKind.DRIVE, Direction.NORTH, owner);
        termDrive.setItem(termDrive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_64K.get()));
        var termNet = termDrive.matterNetwork(true);
        var termInv = Objects.requireNonNull(termNet.inventory());
        h.assertTrue(termInv.apply(Map.of(), Map.of(BuiltinChemistry.HYDROGEN, 12_000L, BuiltinChemistry.COPPER, 8_000L)),
                "Seed HYDROGEN and COPPER");
        termNet.markChanged();

        owner.getInventory().setItem(0, new ItemStack(ModContent.PRESSURIZED_CANISTER.get()));
        h.assertTrue(PressurizedCanisterItem.transferInInventory(
                        owner.getInventory(), termDrive.matterNetwork(true), termDrive, BuiltinChemistry.COPPER, false) == 0,
                "Pressurized Canister must reject solid COPPER fill in Terminal");
        int filled = PressurizedCanisterItem.transferInInventory(
                owner.getInventory(), termDrive.matterNetwork(true), termDrive, BuiltinChemistry.HYDROGEN, false);
        h.assertTrue(filled == 12_000 && PressurizedCanisterItem.storedAmount(owner.getInventory().getItem(0)) == 12_000,
                "Canister should fill 12,000 HYDROGEN from Terminal, got " + filled);
        int drained = PressurizedCanisterItem.transferInInventory(
                owner.getInventory(), termDrive.matterNetwork(true), termDrive, null, true);
        h.assertTrue(drained == 12_000 && PressurizedCanisterItem.storedAmount(owner.getInventory().getItem(0)) == 0,
                "Canister should drain 12,000 HYDROGEN back to Terminal, got " + drained);

        // 3. Fluid tank input/output guards: Decomposer accepts external fluid input, Synthesizer rejects external fluid input!
        BlockPos decompRel = new BlockPos(2, 2, 2);
        BlockPos synthRel = new BlockPos(4, 2, 2);
        var decomp = placeMachine(h, decompRel, MachineKind.DECOMPOSER, Direction.NORTH, owner);
        var synth = placeMachine(h, synthRel, MachineKind.SYNTHESIZER, Direction.NORTH, owner);
        decomp.toggleAutomation();
        synth.toggleAutomation();

        var decompFluid = Objects.requireNonNull(Capabilities.Fluid.BLOCK.getCapability(
                h.getLevel(), h.absolutePos(decompRel), decomp.getBlockState(), decomp, Direction.SOUTH));
        var synthFluid = Objects.requireNonNull(Capabilities.Fluid.BLOCK.getCapability(
                h.getLevel(), h.absolutePos(synthRel), synth.getBlockState(), synth, Direction.SOUTH));

        try (var tx = Transaction.openRoot()) {
            int acceptedByDecomp = decompFluid.insert(FluidResource.of(Fluids.WATER), 1_000, tx);
            int acceptedBySynth = synthFluid.insert(FluidResource.of(Fluids.WATER), 1_000, tx);
            h.assertTrue(acceptedByDecomp == 1_000, "Decomposer fluid handler should accept 1,000 mB WATER, got " + acceptedByDecomp);
            h.assertTrue(acceptedBySynth == 0, "Synthesizer output fluid tank must reject external fluid insertion, got " + acceptedBySynth);
        }

        // 4. Mekanism handheld chemical tank -> ReMatter Drive interaction when Mekanism is loaded.
        if (ModList.get().isLoaded("mekanism")) {
            var mekTankItem = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("mekanism", "basic_chemical_tank"));
            if (mekTankItem.isPresent()) {
                BlockPos driveRel = new BlockPos(3, 2, 4);
                var drive = placeMachine(h, driveRel, MachineKind.DRIVE, Direction.NORTH, owner);
                drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_64K.get()));
                var driveInv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
                h.assertTrue(driveInv.apply(Map.of(), Map.of(BuiltinChemistry.OXYGEN, 4_000L)), "Seed 4,000 OXYGEN");
                drive.matterNetwork(true).markChanged();

                ItemStack mekTank = new ItemStack(mekTankItem.get());
                owner.setItemInHand(InteractionHand.MAIN_HAND, mekTank);
                owner.setShiftKeyDown(true);
                BlockPos absDrive = h.absolutePos(driveRel);
                owner.setPos(absDrive.getX() + 0.5, absDrive.getY(), absDrive.getZ() + 0.5);
                var extractRes = MekanismCompat.interactWithMachine(mekTank, h.getLevel(), absDrive, owner, InteractionHand.MAIN_HAND, drive);
                h.assertTrue(extractRes.consumesAction(), "Sneak-right-clicking Drive with Mekanism tank should extract OXYGEN");
                h.assertTrue(driveInv.amount(BuiltinChemistry.OXYGEN) < 4_000L, "Drive OXYGEN should decrease into Mekanism tank");

                owner.setShiftKeyDown(false);
                var depositRes = MekanismCompat.interactWithMachine(mekTank, h.getLevel(), absDrive, owner, InteractionHand.MAIN_HAND, drive);
                h.assertTrue(depositRes.consumesAction(), "Right-clicking Drive with filled Mekanism tank should deposit OXYGEN back");
                h.assertTrue(driveInv.amount(BuiltinChemistry.OXYGEN) == 4_000L, "Drive OXYGEN should return to 4,000");
            }
        }
        h.succeed();
    }
}
