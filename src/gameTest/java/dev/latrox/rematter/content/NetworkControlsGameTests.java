package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.content.EnergyReactorRecipes.CoolantMode;
import dev.latrox.rematter.content.EnergyReactorRecipes.FuelMode;
import dev.latrox.rematter.content.EnergyReactorRecipes.MatterFuelFilter;
import dev.latrox.rematter.content.MachineSnapshot.StockKey;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.ChannelMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.DirectionMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.GateMode;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeTier;
import dev.latrox.rematter.primal.PrimalCells;
import dev.latrox.rematter.primal.PrimalContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Server GameTests covering:
 * 1. Genuine Reactor operating light (lightLevel = 12) + dedicated REDSTONE vs AUTOMATIC port priority.
 * 2. Global (NetworkReservesSavedData) & per-machine Network Reserves + Energy Reactor fuel/coolant/primal filters.
 * 3. Matter Cable Relay (Redstone gating, POWER_ONLY & MATTER_ONLY channel isolation, FE/t & Matter/t rate throttling).
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class NetworkControlsGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static {
        TESTS.put("reactor_operating_light_and_dedicated_redstone_ports", NetworkControlsGameTests::reactorLightAndRedstonePorts);
        TESTS.put("network_reserves_and_energy_reactor_filters_enforce_floors", NetworkControlsGameTests::networkReservesAndReactorFilters);
        TESTS.put("matter_cable_relay_gates_redstone_separates_channels_and_throttles", NetworkControlsGameTests::matterCableRelayEndToEnd);
        TESTS.put("cable_side_disconnect_isolates_matter_and_power", NetworkControlsGameTests::cableSideDisconnectEndToEnd);
        TESTS.put("matter_cable_relay_one_way_and_matter_filters", NetworkControlsGameTests::matterCableRelayOneWayAndFilters);
        TESTS.put("relay_load_gate_shared_budget_reactor_overhang_and_primal_deposit", NetworkControlsGameTests::relayLoadGateSharedBudgetOverhangAndPrimalDeposit);
    }

    private NetworkControlsGameTests() {}

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("rematter_tests", path); }

    @SubscribeEvent
    public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach((name, fn) -> helper.register(id(name), fn)));
    }

    @SubscribeEvent
    public static void tests(RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(id("network_controls_environment"));
        TESTS.forEach((name, fn) -> event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                new TestData<>(env, id("empty"), 800, 0, true, Rotation.NONE, false, 1, 1, true, 2))));
    }

    private static MachineBlockEntity placeMachine(GameTestHelper h, BlockPos relPos, MachineKind kind, Direction facing, Player owner) {
        h.setBlock(relPos, ModContent.MACHINES.get(kind).get().defaultBlockState().setValue(MachineBlock.FACING, facing));
        var be = h.getBlockEntity(relPos, MachineBlockEntity.class);
        be.placedBy(owner);
        return be;
    }

    private static void setPortMode(MachinePortBlockEntity port, Player owner, Direction face, MachinePortMode target) {
        for (int i = 0; i < MachinePortMode.values().length + 2; i++) {
            if (port.mode() == target) return;
            port.cycleMode(owner, face);
        }
        throw new IllegalStateException("Could not reach port mode " + target + ", stuck at " + port.mode());
    }

    private record ReactorRig(MachineBlockEntity controller, BlockPos corePos, BlockPos westPortPos, MachinePortBlockEntity westPort,
                              BlockPos eastPortPos, MachinePortBlockEntity eastPort, BlockPos southPortPos, MachinePortBlockEntity southPort) {}

    private static ReactorRig buildEnergyReactor(GameTestHelper h, BlockPos origin, Player owner) {
        for (int dx = 0; dx < 5; dx++) {
            for (int dy = 0; dy < 5; dy++) {
                for (int dz = 0; dz < 5; dz++) {
                    boolean boundary = dx == 0 || dx == 4 || dy == 0 || dy == 4 || dz == 0 || dz == 4;
                    if (!boundary) continue;
                    boolean edge = (dy == 0 || dy == 4) || ((dx == 0 || dx == 4) && (dz == 0 || dz == 4));
                    h.setBlock(origin.offset(dx, dy, dz), edge ? ModContent.ENERGY_REACTOR_CASING.get() : ModContent.ENERGY_REACTOR_GLASS.get());
                }
            }
        }
        var controller = placeMachine(h, origin.offset(2, 1, 0), MachineKind.ENERGY_REACTOR, Direction.NORTH, owner);
        BlockPos corePos = origin.offset(1, 1, 1);
        h.setBlock(corePos, ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.REACTOR_FUEL_CORE).get()
                .defaultBlockState().setValue(IndustrialModuleBlock.SUSPENDED, false));
        h.setBlock(origin.offset(1, 1, 2), ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.REACTOR_FUEL_CORE).get()
                .defaultBlockState().setValue(IndustrialModuleBlock.SUSPENDED, false));
        h.setBlock(origin.offset(1, 3, 1), ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.REACTOR_INDUCTION_COIL).get()
                .defaultBlockState().setValue(IndustrialModuleBlock.SUSPENDED, true));
        h.setBlock(origin.offset(1, 3, 2), ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.REACTOR_INDUCTION_COIL).get()
                .defaultBlockState().setValue(IndustrialModuleBlock.SUSPENDED, true));

        BlockPos westPortPos = origin.offset(0, 1, 2);
        BlockPos eastPortPos = origin.offset(4, 1, 2);
        BlockPos southPortPos = origin.offset(2, 1, 4);
        h.setBlock(westPortPos, ModContent.ENERGY_REACTOR_PORT.get());
        h.setBlock(eastPortPos, ModContent.ENERGY_REACTOR_PORT.get());
        h.setBlock(southPortPos, ModContent.ENERGY_REACTOR_PORT.get());
        var westPort = h.getBlockEntity(westPortPos, MachinePortBlockEntity.class);
        var eastPort = h.getBlockEntity(eastPortPos, MachinePortBlockEntity.class);
        var southPort = h.getBlockEntity(southPortPos, MachinePortBlockEntity.class);
        westPort.refreshNow();
        eastPort.refreshNow();
        southPort.refreshNow();
        controller.serverTick();
        h.assertTrue(controller.industrialStructureReady(),
                "Energy Reactor failed to form: " + IndustrialStructure.inspect(controller).diagnostic());
        return new ReactorRig(controller, corePos, westPortPos, westPort, eastPortPos, eastPort, southPortPos, southPort);
    }

    private static ItemStack primalCellWith(long amount) {
        var stack = new ItemStack(PrimalContent.PRIMAL_CELL_4M.get());
        ItemStack[] holder = {stack};
        var journal = new PrimalCells.Journal(() -> holder[0]);
        try (var tx = Transaction.openRoot()) {
            PrimalCells.credit(journal, amount, tx);
            tx.commit();
        }
        return holder[0];
    }

    private static void reactorLightAndRedstonePorts(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2.0, 4.5)));
        var rig = buildEnergyReactor(h, new BlockPos(2, 1, 2), owner);
        var ctrl = rig.controller();
        var level = h.getLevel();

        // Idle light checks
        BlockPos ctrlWorld = ctrl.getBlockPos();
        BlockPos coreWorld = h.absolutePos(rig.corePos());
        h.assertTrue(level.getBlockState(ctrlWorld).getLightEmission(level, ctrlWorld) == 3,
                "Idle Energy Reactor controller must emit subtle glow 3, got " + level.getBlockState(ctrlWorld).getLightEmission(level, ctrlWorld));
        h.assertTrue(level.getBlockState(coreWorld).getLightEmission(level, coreWorld) == 0,
                "Idle Reactor Fuel Core must emit 0 light");

        // Feed crystal fuel so reactor runs
        var emberCrystal = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "ember_matrix_crystal"));
        ctrl.setItem(0, new ItemStack(emberCrystal, 64));
        ctrl.setRedstoneMode(RedstoneMode.LOW);

        // No REDSTONE port yet: powering AUTOMATIC south port pauses the reactor!
        BlockPos southRsPos = rig.southPortPos().south();
        BlockPos eastRsPos = rig.eastPortPos().east();
        h.setBlock(southRsPos, Blocks.REDSTONE_BLOCK);
        ctrl.serverTick();
        h.assertTrue("redstone_paused".equals(ctrl.work().status),
                "With no REDSTONE port, AUTOMATIC port signal must pause reactor (status=" + ctrl.work().status + ")");

        // Configure EAST port as dedicated REDSTONE port (unpowered): stray signal on AUTOMATIC south port is now IGNORED!
        setPortMode(rig.eastPort(), owner, Direction.EAST, MachinePortMode.REDSTONE);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(ctrl.reactorEnergy() > 0L && "generating".equals(ctrl.work().status),
                            "Dedicated REDSTONE port must ignore stray signal on AUTOMATIC port (status=" + ctrl.work().status + ")");
                })
                .thenExecute(() -> {
                    // Active operating light = 12 on BOTH controller and interior Fuel Core!
                    h.assertTrue(level.getBlockState(ctrlWorld).getValue(MachineBlock.ACTIVE), "Active reactor controller must have ACTIVE=true");
                    h.assertTrue(level.getBlockState(ctrlWorld).getLightEmission(level, ctrlWorld) == 12,
                            "Active Energy Reactor controller must emit light 12, got " + level.getBlockState(ctrlWorld).getLightEmission(level, ctrlWorld));
                    h.assertTrue(level.getBlockState(coreWorld).getValue(IndustrialModuleBlock.ACTIVE), "Active Fuel Core must have ACTIVE=true");
                    h.assertTrue(level.getBlockState(coreWorld).getLightEmission(level, coreWorld) == 12,
                            "Active Fuel Core must emit light 12, got " + level.getBlockState(coreWorld).getLightEmission(level, coreWorld));

                    // Now power the dedicated REDSTONE port -> reactor must pause!
                    h.setBlock(eastRsPos, Blocks.REDSTONE_BLOCK);
                    ctrl.serverTick();
                    h.assertTrue("redstone_paused".equals(ctrl.work().status),
                            "Powering dedicated REDSTONE port must pause reactor (status=" + ctrl.work().status + ")");

                    // Mixed Glass + Casing on Reactor roof: open spot (2,4,2) accepts Glass with FORMED=true,
                    // while replacing Casing directly over Induction Coil (1,3,1) at (1,4,1) with Glass must deform!
                    BlockPos openRoof = new BlockPos(2, 1, 2).offset(2, 4, 2);
                    BlockPos coilRoof = new BlockPos(2, 1, 2).offset(1, 4, 1);
                    h.setBlock(openRoof, ModContent.ENERGY_REACTOR_GLASS.get());
                    ctrl.serverTick();
                    h.assertTrue(ctrl.industrialStructureReady(),
                            "Energy Reactor must allow Glass on open roof spot without a ceiling module underneath");
                    h.assertTrue(h.getBlockState(openRoof).getValue(ReactorGlassBlock.FORMED),
                            "Open roof Glass on formed Energy Reactor must immediately receive FORMED=true");
                    h.setBlock(coilRoof, ModContent.ENERGY_REACTOR_GLASS.get());
                    ctrl.serverTick();
                    h.assertTrue(!ctrl.industrialStructureReady(),
                            "Energy Reactor must reject Glass directly above a mounted Induction Coil");
                    h.setBlock(coilRoof, ModContent.ENERGY_REACTOR_CASING.get());
                    ctrl.serverTick();
                    h.assertTrue(ctrl.industrialStructureReady(),
                            "Restoring Casing directly above Induction Coil must reform Energy Reactor");
                })
                .thenSucceed();
    }

    private static void networkReservesAndReactorFilters(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2.0, 4.5)));
        var rig = buildEnergyReactor(h, new BlockPos(2, 1, 2), owner);
        var ctrl = rig.controller();
        setPortMode(rig.westPort(), owner, Direction.WEST, MachinePortMode.MATTER);

        var drive = placeMachine(h, rig.westPortPos().west(), MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_16M.get()));
        drive.setItem(drive.cellSlotForBay(1), primalCellWith(10_000L));

        var uranium = SubstanceKey.of("rematter:uranium", Phase.SOLID);
        var lithium = SubstanceKey.of("rematter:lithium", Phase.SOLID);
        var hydrogen = BuiltinChemistry.HYDROGEN;
        var water = BuiltinChemistry.WATER;
        var net = drive.matterNetwork(true);
        h.assertTrue(Objects.requireNonNull(net.inventory()).apply(Map.of(),
                Map.of(uranium, 5_000L, lithium, 5_000L, hydrogen, 5_000L, water, 5_000L)),
                "Failed to seed network matter");
        net.markChanged();

        var reserves = NetworkReservesSavedData.get(h.getLevel().getServer());
        // 1. Global reserve protects Uranium completely
        reserves.set(owner.getUUID(), new StockKey("rematter:uranium", "solid"), 5_000L);
        h.assertTrue(ctrl.extensions().action(owner, new dev.latrox.rematter.network.MachineActionPayload(
                0, dev.latrox.rematter.network.MachineActionPayload.Action.SET_REACTOR_FUEL_MODE, FuelMode.MATTER.id(), 0)),
                "Switch to MATTER");
        h.assertTrue(ctrl.extensions().action(owner, new dev.latrox.rematter.network.MachineActionPayload(
                0, dev.latrox.rematter.network.MachineActionPayload.Action.SET_REACTOR_MATTER_FILTER, MatterFuelFilter.URANIUM.id(), 0)),
                "Set URANIUM filter");
        h.assertTrue(ctrl.extensions().action(owner, new dev.latrox.rematter.network.MachineActionPayload(
                0, dev.latrox.rematter.network.MachineActionPayload.Action.SET_REACTOR_COOLANT_MODE, CoolantMode.OFF.id(), 0)),
                "Set Coolant OFF");
        for (int i = 0; i < 5; i++) ctrl.serverTick();
        h.assertTrue("network_reserve".equals(ctrl.work().status),
                "Expected network_reserve when Uranium stock equals global floor, got " + ctrl.work().status);
        h.assertTrue(drive.matterNetwork(true).inventory().amount(uranium) == 5_000L,
                "Global reserve must prevent Uranium withdrawal");
        h.assertTrue(drive.matterNetwork(true).inventory().amount(water) == 5_000L,
                "CoolantMode.OFF must consume zero water");

        // 2. Per-machine reserve protects stock even when global reserve is lowered (2 cores * 40 U/t = 80 U/t -> stops at 4840 >= 4800)
        reserves.set(owner.getUUID(), new StockKey("rematter:uranium", "solid"), 0L);
        h.assertTrue(ctrl.configure(owner, new MachineSettings(0L, "", "", Set.of(), 4_800L)),
                "Energy Reactor must accept per-machine networkReserve");
        for (int i = 0; i < 10; i++) ctrl.serverTick();
        long uAfter = drive.matterNetwork(true).inventory().amount(uranium);
        h.assertTrue(uAfter >= 4_800L && uAfter < 5_000L,
                "Reactor must burn Uranium down to >= 4800 floor and stop, got " + uAfter);
        h.assertTrue("network_reserve".equals(ctrl.work().status),
                "Reactor must enter network_reserve status once floor is reached, got " + ctrl.work().status);

        // 3. Matter fuel filter restricts strictly to Lithium + H2 (never touches Uranium)
        h.assertTrue(ctrl.configure(owner, new MachineSettings(0L, "", "", Set.of(), 0L)), "Clear machine reserve");
        h.assertTrue(ctrl.extensions().action(owner, new dev.latrox.rematter.network.MachineActionPayload(
                0, dev.latrox.rematter.network.MachineActionPayload.Action.SET_REACTOR_MATTER_FILTER, MatterFuelFilter.LITHIUM_FUSION.id(), 0)),
                "Set LITHIUM_FUSION filter");
        for (int i = 0; i < 4; i++) ctrl.serverTick();
        h.assertTrue(drive.matterNetwork(true).inventory().amount(uranium) == uAfter,
                "LITHIUM_FUSION filter must not touch Uranium");
        h.assertTrue(drive.matterNetwork(true).inventory().amount(lithium) < 5_000L
                        && drive.matterNetwork(true).inventory().amount(hydrogen) < 5_000L,
                "LITHIUM_FUSION filter must burn Lithium and Hydrogen");
        h.assertTrue(drive.matterNetwork(true).inventory().amount(water) == 5_000L,
                "CoolantMode.OFF must keep water untouched during active generation");

        // 4. Primal mode: local-only toggle refuses Drive Primal, accepts Slot 12 Primal Cell
        h.assertTrue(ctrl.extensions().action(owner, new dev.latrox.rematter.network.MachineActionPayload(
                0, dev.latrox.rematter.network.MachineActionPayload.Action.SET_REACTOR_FUEL_MODE, FuelMode.PRIMAL.id(), 0)),
                "Switch to PRIMAL");
        h.assertTrue(ctrl.extensions().action(owner, new dev.latrox.rematter.network.MachineActionPayload(
                0, dev.latrox.rematter.network.MachineActionPayload.Action.TOGGLE_REACTOR_NET_PRIMAL, "", 0)),
                "Disable network Primal");
        for (int i = 0; i < 4; i++) ctrl.serverTick();
        h.assertTrue(drive.drivePrimalAmount() == 10_000L,
                "allowNetworkPrimal=false must not withdraw Primal from Drive");
        h.assertTrue("missing_primal".equals(ctrl.work().status),
                "With allowNetworkPrimal=false and empty Slot 12, status must be missing_primal, got " + ctrl.work().status);

        ctrl.setItem(12, primalCellWith(5_000L));
        for (int i = 0; i < 4; i++) ctrl.serverTick();
        h.assertTrue(drive.drivePrimalAmount() == 10_000L, "Local Slot 12 burn must leave Drive Primal untouched");
        var localPrimal = PrimalCells.view(ctrl.getItem(12));
        h.assertTrue(localPrimal.readable() && localPrimal.amount() < 5_000L,
                "Local Slot 12 Primal Cell must be consumed, remaining=" + localPrimal.amount());

        // Verify EMI/JEI EnergyReactorDisplay exposes all 7 entries (3 Matter fuels + Primal + Crystal + 2 Coolants)
        var displays = dev.latrox.rematter.compat.EnergyReactorDisplay.entries(ReMatter.chemistry());
        h.assertTrue(displays.size() == 7, "Expected 7 EnergyReactorDisplay entries, got " + displays.size());
        h.assertTrue(displays.stream().filter(dev.latrox.rematter.compat.EnergyReactorDisplay::coolantEntry).count() == 2L,
                "Expected 2 coolant EnergyReactorDisplay entries");

        // Clean up global reserves for this owner
        reserves.set(owner.getUUID(), new StockKey("rematter:uranium", "solid"), 0L);
        h.succeed();
    }

    private static void fillCube(EnergyCubeBlockEntity cube, int amount) {
        try (var tx = Transaction.openRoot()) {
            Objects.requireNonNull(cube.sidedEnergy(Direction.UP)).insert(amount, tx);
            tx.commit();
        }
    }

    private static void matterCableRelayEndToEnd(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);

        // Layout along X axis (West <-> East):
        // (1,2,2) Drive [4M Cell]
        // (2,2,2) Matter Cable
        // (3,2,2) Matter Cable Relay facing EAST (connects WEST <-> EAST; NORTH/SOUTH/UP/DOWN free for Redstone!)
        // (4,2,2) Matter Cable
        // (5,2,2) Chemical Processor
        // Energy Cubes attached to the same cables at (2,2,3) [Source, front=NORTH] and (4,2,3) [Sink, back=NORTH]
        BlockPos drivePos = new BlockPos(1, 2, 2);
        BlockPos cableLeft = new BlockPos(2, 2, 2);
        BlockPos valvePos = new BlockPos(3, 2, 2);
        BlockPos cableRight = new BlockPos(4, 2, 2);
        BlockPos machinePos = new BlockPos(5, 2, 2);
        BlockPos srcCubePos = new BlockPos(2, 2, 3);
        BlockPos sinkCubePos = new BlockPos(4, 2, 3);
        BlockPos rsSidePos = new BlockPos(3, 2, 1); // perpendicular NORTH side face of valve!

        var drive = placeMachine(h, drivePos, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        var proc = placeMachine(h, machinePos, MachineKind.REACTOR, Direction.NORTH, owner);
        proc.energy().set(proc.energyCapacity());

        h.setBlock(cableLeft, ModContent.MATTER_CABLE.get());
        CableOwners.set(h.getLevel(), h.absolutePos(cableLeft), owner.getUUID());
        h.setBlock(cableRight, ModContent.MATTER_CABLE.get());
        CableOwners.set(h.getLevel(), h.absolutePos(cableRight), owner.getUUID());

        h.setBlock(valvePos, ModContent.MATTER_CABLE_RELAY.get().defaultBlockState()
                .setValue(MatterCableRelayBlock.FACING, Direction.EAST));
        var valve = h.getBlockEntity(valvePos, MatterCableRelayBlockEntity.class);
        valve.placedBy(owner);
        // Performance guarantee: Matter Cable Relay must NOT register a per-tick BlockEntityTicker (0 tick/s idle cost)
        h.assertTrue(ModContent.MATTER_CABLE_RELAY.get().getTicker(h.getLevel(), h.getBlockState(valvePos), ModContent.MATTER_CABLE_RELAY_ENTITY.get()) == null,
                "Matter Cable Relay must be purely event-driven without a BlockEntityTicker");
        // Performance guarantee: Redstone blinking next to an ALWAYS_OPEN relay must NOT bump stateRevision / invalidate topology
        long revBeforeAlwaysOpen = valve.stateRevision();
        h.setBlock(rsSidePos, Blocks.REDSTONE_BLOCK);
        h.setBlock(rsSidePos, Blocks.AIR);
        h.assertTrue(valve.stateRevision() == revBeforeAlwaysOpen,
                "ALWAYS_OPEN Matter Cable Relay must not bump stateRevision on neighbor redstone changes");

        h.setBlock(srcCubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH));
        var srcCube = h.getBlockEntity(srcCubePos, EnergyCubeBlockEntity.class);
        srcCube.placedBy(owner);
        fillCube(srcCube, 50_000);

        h.setBlock(sinkCubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.SOUTH));
        var sinkCube = h.getBlockEntity(sinkCubePos, EnergyCubeBlockEntity.class);
        sinkCube.placedBy(owner);

        // 1. REDSTONE_HIGH gate: closed when unpowered -> severs Matter AND FE!
        valve.configure(GateMode.REDSTONE_HIGH, ChannelMode.BOTH, 0, 0);
        long[] sinkSnap = {0L};
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    h.assertFalse(valve.isOpen(), "REDSTONE_HIGH valve must be closed without signal");
                    h.assertFalse(proc.matterNetwork(true).ready(), "Closed valve must sever Matter network");
                    h.assertTrue(sinkCube.energy() == 0L, "Closed valve must block FE transfer, got " + sinkCube.energy());
                    // Power perpendicular NORTH face -> opens valve!
                    h.setBlock(rsSidePos, Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(2, () -> {
                    h.assertTrue(valve.isOpen(), "Powering side face must open REDSTONE_HIGH valve");
                    h.assertTrue(proc.matterNetwork(true).ready(), "Open valve must connect Matter network");
                    h.assertTrue(sinkCube.energy() > 0L, "Open valve must allow FE transfer");

                    // 2. POWER_ONLY + energyLimit=250: FE flows capped at <= 250 FE/t, while Matter network is ISOLATED!
                    valve.configure(GateMode.ALWAYS_OPEN, ChannelMode.POWER_ONLY, 250, 0);
                    h.setBlock(rsSidePos, Blocks.AIR);
                    sinkSnap[0] = sinkCube.energy();
                })
                .thenExecuteAfter(1, () -> {
                    h.assertFalse(proc.matterNetwork(true).ready(), "POWER_ONLY valve must isolate Matter networks");
                    long delta = sinkCube.energy() - sinkSnap[0];
                    h.assertTrue(delta == 250L,
                            "POWER_ONLY valve with energyLimit=250 must cap FE/t to exactly 250, got delta=" + delta);

                    // 3. MATTER_ONLY + matterLimit=32: Matter connects throttled to 32 u/t, while FE is BLOCKED!
                    valve.configure(GateMode.ALWAYS_OPEN, ChannelMode.MATTER_ONLY, 0, 32);
                    sinkSnap[0] = sinkCube.energy();
                })
                .thenExecuteAfter(2, () -> {
                    var procNet = proc.matterNetwork(true);
                    h.assertTrue(procNet.ready(), "MATTER_ONLY valve must allow Matter network");
                    h.assertTrue(procNet.matterRate(proc) == 32,
                            "MATTER_ONLY valve with matterLimit=32 must throttle matterRate to 32 u/t, got " + procNet.matterRate(proc));
                    h.assertTrue(sinkCube.energy() == sinkSnap[0],
                            "MATTER_ONLY valve must block FE transfer completely (before=" + sinkSnap[0] + ", after=" + sinkCube.energy() + ")");
                })
                .thenSucceed();
    }

    private static void cableSideDisconnectEndToEnd(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos drivePos = new BlockPos(1, 2, 2);
        BlockPos cableLeft = new BlockPos(2, 2, 2);
        BlockPos cableRight = new BlockPos(3, 2, 2);
        BlockPos machinePos = new BlockPos(4, 2, 2);
        BlockPos srcCubePos = new BlockPos(2, 2, 3);
        BlockPos sinkCubePos = new BlockPos(3, 2, 3);

        var drive = placeMachine(h, drivePos, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        var proc = placeMachine(h, machinePos, MachineKind.REACTOR, Direction.NORTH, owner);

        h.setBlock(cableLeft, ModContent.MATTER_CABLE.get());
        CableOwners.set(h.getLevel(), h.absolutePos(cableLeft), owner.getUUID());
        h.setBlock(cableRight, ModContent.MATTER_CABLE.get());
        CableOwners.set(h.getLevel(), h.absolutePos(cableRight), owner.getUUID());

        h.setBlock(srcCubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH));
        var srcCube = h.getBlockEntity(srcCubePos, EnergyCubeBlockEntity.class);
        srcCube.placedBy(owner);
        fillCube(srcCube, 40_000);

        h.setBlock(sinkCubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.SOUTH));
        var sinkCube = h.getBlockEntity(sinkCubePos, EnergyCubeBlockEntity.class);
        sinkCube.placedBy(owner);

        long[] snap = {0L};
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    h.assertTrue(h.getBlockState(cableLeft).getValue(MatterCableBlock.EAST), "Left cable must initially connect EAST");
                    h.assertTrue(h.getBlockState(cableRight).getValue(MatterCableBlock.WEST), "Right cable must initially connect WEST");
                    h.assertTrue(proc.matterNetwork(true).ready(), "Connected cables must join Matter network");
                    h.assertTrue(sinkCube.energy() > 0L, "Connected cables must forward FE");

                    // Disconnect cableLeft <-> cableRight
                    h.assertTrue(CableOwners.toggleSideBlocked(h.getLevel(), h.absolutePos(cableLeft), Direction.EAST, owner),
                            "Toggling EAST on cableLeft must disconnect");
                    snap[0] = sinkCube.energy();
                })
                .thenExecuteAfter(2, () -> {
                    h.assertFalse(h.getBlockState(cableLeft).getValue(MatterCableBlock.EAST), "Disconnected cableLeft EAST arm must vanish");
                    h.assertFalse(h.getBlockState(cableRight).getValue(MatterCableBlock.WEST), "Disconnected cableRight WEST arm must vanish");
                    h.assertFalse(proc.matterNetwork(true).ready(), "Disconnected cable link must sever Matter network");
                    h.assertTrue(sinkCube.energy() == snap[0], "Disconnected cable link must stop FE flow");

                    // Reconnect cableLeft <-> cableRight
                    h.assertFalse(CableOwners.toggleSideBlocked(h.getLevel(), h.absolutePos(cableLeft), Direction.EAST, owner),
                            "Second toggle must reconnect cableLeft <-> cableRight");
                    snap[0] = sinkCube.energy();
                })
                .thenExecuteAfter(2, () -> {
                    h.assertTrue(h.getBlockState(cableLeft).getValue(MatterCableBlock.EAST), "Reconnected cableLeft EAST arm must reappear");
                    h.assertTrue(proc.matterNetwork(true).ready(), "Reconnected cables must restore Matter network");
                    h.assertTrue(sinkCube.energy() > snap[0], "Reconnected cables must resume FE flow");

                    // Disconnect cableLeft from Drive (WEST) while keeping cableLeft <-> cableRight connected
                    h.assertTrue(CableOwners.toggleSideBlocked(h.getLevel(), h.absolutePos(cableLeft), Direction.WEST, owner),
                            "Disconnecting cableLeft WEST must detach from Drive");
                })
                .thenExecuteAfter(1, () -> {
                    h.assertFalse(h.getBlockState(cableLeft).getValue(MatterCableBlock.WEST), "Cable WEST arm toward Drive must vanish");
                    h.assertTrue(h.getBlockState(cableLeft).getValue(MatterCableBlock.EAST), "Cable EAST arm must remain connected");
                    h.assertFalse(proc.matterNetwork(true).ready(), "Detached Drive must leave Matter network without storage");
                })
                .thenSucceed();
    }

    private static void matterCableRelayOneWayAndFilters(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos drivePos = new BlockPos(1, 2, 2);
        BlockPos cableLeft = new BlockPos(2, 2, 2);
        BlockPos relayPos = new BlockPos(3, 2, 2);
        BlockPos cableRight = new BlockPos(4, 2, 2);
        BlockPos machinePos = new BlockPos(5, 2, 2);
        BlockPos leftCubePos = new BlockPos(2, 2, 3);
        BlockPos rightCubePos = new BlockPos(4, 2, 3);

        var drive = placeMachine(h, drivePos, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        drive.setItem(drive.cellSlotForBay(1), primalCellWith(1_000L));
        var proc = placeMachine(h, machinePos, MachineKind.REACTOR, Direction.NORTH, owner);

        var iron = SubstanceKey.of("rematter:iron", Phase.SOLID);
        var water = BuiltinChemistry.WATER;
        var hydrogen = BuiltinChemistry.HYDROGEN;
        h.assertTrue(Objects.requireNonNull(drive.matterStore()).apply(Map.of(),
                Map.of(iron, 500L, water, 500L, hydrogen, 500L)), "Failed to seed Drive");

        h.setBlock(cableLeft, ModContent.MATTER_CABLE.get());
        CableOwners.set(h.getLevel(), h.absolutePos(cableLeft), owner.getUUID());
        h.setBlock(cableRight, ModContent.MATTER_CABLE.get());
        CableOwners.set(h.getLevel(), h.absolutePos(cableRight), owner.getUUID());

        // Relay facing EAST => Input = WEST (Green), Output = EAST (Red)
        h.setBlock(relayPos, ModContent.MATTER_CABLE_RELAY.get().defaultBlockState()
                .setValue(MatterCableRelayBlock.FACING, Direction.EAST));
        var relay = h.getBlockEntity(relayPos, MatterCableRelayBlockEntity.class);
        relay.placedBy(owner);

        // Energy Cubes: leftCube pushes NORTH into cableLeft; rightCube receives on NORTH from cableRight
        h.setBlock(leftCubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH));
        var leftCube = h.getBlockEntity(leftCubePos, EnergyCubeBlockEntity.class);
        leftCube.placedBy(owner);
        fillCube(leftCube, 30_000);

        h.setBlock(rightCubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.SOUTH));
        var rightCube = h.getBlockEntity(rightCubePos, EnergyCubeBlockEntity.class);
        rightCube.placedBy(owner);

        // 1. ONE_WAY facing EAST (West -> East): FE flows Left->Right; Matter allows WITHDRAW (Drive->Proc), blocks DEPOSIT (Proc->Drive)!
        relay.configure(GateMode.ALWAYS_OPEN, ChannelMode.BOTH, DirectionMode.ONE_WAY,
                0, 0, MatterCableRelayBlockEntity.STATE_ALL, false, List.of(), "");
        long[] leftSnap = {0L};
        long[] rightSnap = {0L};
        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    h.assertTrue(h.getBlockState(relayPos).getValue(MatterCableRelayBlock.DIRECTIONAL),
                            "ONE_WAY relay must set DIRECTIONAL=true blockstate");
                    h.assertTrue(rightCube.energy() > 0L, "ONE_WAY West->East must allow FE Left->Right");

                    var net = proc.matterNetwork(true);
                    h.assertTrue(net.ready(), "ONE_WAY Drive->Proc must keep Matter network ready");
                    var inv = Objects.requireNonNull(net.inventory());
                    h.assertTrue(inv.amount(iron) == 500L, "Proc on Output side must see withdrawable Iron=500, got " + inv.amount(iron));
                    h.assertTrue(net.primalAmount() == 1_000L, "Proc on Output side must see withdrawable Primal=1000");
                    h.assertTrue(inv.apply(Map.of(iron, 100L), Map.of()), "Proc on Output side must be able to withdraw Iron");
                    h.assertFalse(inv.canApply(Map.of(), Map.of(iron, 50L)), "Proc on Output side must NOT deposit backward through ONE_WAY relay");
                    try (var tx = Transaction.openRoot()) {
                        h.assertTrue(net.creditPrimal(proc, 50L, tx) == 0L, "Proc on Output side must NOT credit Primal backward");
                        h.assertTrue(net.debitPrimal(proc, 100L, tx) == 100L, "Proc on Output side must be able to debit Primal");
                        tx.commit();
                    }

                    // 2. Reverse direction (now facing WEST: East -> West):
                    // FE Left->Right MUST STOP, and Proc->Drive DEPOSIT works while WITHDRAW is blocked!
                    h.assertTrue(relay.reverseDirection(owner), "Reverse direction must succeed");
                    rightSnap[0] = rightCube.energy();
                })
                .thenExecuteAfter(2, () -> {
                    h.assertTrue(relay.outputSide() == Direction.WEST, "Reversed relay must now output WEST");
                    h.assertTrue(rightCube.energy() == rightSnap[0], "ONE_WAY East->West must block FE Left->Right");

                    var net = proc.matterNetwork(true);
                    var inv = Objects.requireNonNull(net.inventory());
                    h.assertTrue(inv.amount(iron) == 0L, "Proc on Input side must NOT withdraw backward (got " + inv.amount(iron) + ")");
                    h.assertTrue(net.primalAmount() == 0L, "Proc on Input side must NOT see withdrawable Primal backward");
                    h.assertFalse(inv.canApply(Map.of(iron, 10L), Map.of()), "Withdraw backward must be rejected");
                    h.assertTrue(inv.apply(Map.of(), Map.of(iron, 50L)), "Proc on Input side MUST deposit forward into Drive");
                    h.assertTrue(drive.matterStore().amount(iron) == 450L, "Drive Iron must now be 450, got " + drive.matterStore().amount(iron));
                    try (var tx = Transaction.openRoot()) {
                        h.assertTrue(net.creditPrimal(proc, 50L, tx) == 50L, "Proc on Input side must credit Primal forward");
                        tx.commit();
                    }
                    h.assertTrue(drive.drivePrimalAmount() == 950L, "Drive Primal must now be 950");

                    // 3. Reverse Energy Cubes so rightCube pushes FE West through East->West relay!
                    h.setBlock(leftCubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get().defaultBlockState()
                            .setValue(MachineBlock.FACING, Direction.SOUTH));
                    h.getBlockEntity(leftCubePos, EnergyCubeBlockEntity.class).placedBy(owner);
                    h.setBlock(rightCubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get().defaultBlockState()
                            .setValue(MachineBlock.FACING, Direction.NORTH));
                    var newRight = h.getBlockEntity(rightCubePos, EnergyCubeBlockEntity.class);
                    newRight.placedBy(owner);
                    fillCube(newRight, 20_000);
                    leftSnap[0] = h.getBlockEntity(leftCubePos, EnergyCubeBlockEntity.class).energy();
                })
                .thenExecuteAfter(2, () -> {
                    long leftAfter = h.getBlockEntity(leftCubePos, EnergyCubeBlockEntity.class).energy();
                    h.assertTrue(leftAfter > leftSnap[0], "ONE_WAY East->West must allow FE Right->Left");

                    // 4. Aggregate-state filter: LIQUIDS ONLY (Solid, Gas, Primal disabled)
                    relay.configure(GateMode.ALWAYS_OPEN, ChannelMode.BOTH, DirectionMode.BIDIRECTIONAL,
                            0, 0, MatterCableRelayBlockEntity.STATE_LIQUID, false, List.of(), "");
                    var net = proc.matterNetwork(true);
                    var inv = Objects.requireNonNull(net.inventory());
                    h.assertTrue(inv.amount(water) == 500L, "LIQUID-only filter must pass Water");
                    h.assertTrue(inv.amount(iron) == 0L, "LIQUID-only filter must block Iron (Solid)");
                    h.assertTrue(inv.amount(hydrogen) == 0L, "LIQUID-only filter must block Hydrogen (Gas)");
                    h.assertTrue(net.primalAmount() == 0L, "LIQUID-only filter must block Primal Matter");
                    h.assertFalse(inv.canApply(Map.of(), Map.of(iron, 10L)), "LIQUID-only filter must reject Solid deposit");
                    h.assertTrue(inv.apply(Map.of(), Map.of(water, 25L)), "LIQUID-only filter must allow Liquid deposit");

                    // 5. Substance Whitelist: ONLY Iron(Solid)
                    relay.configure(GateMode.ALWAYS_OPEN, ChannelMode.BOTH, DirectionMode.BIDIRECTIONAL,
                            0, 0, MatterCableRelayBlockEntity.STATE_ALL, true, List.of(iron), "");
                    var wlInv = Objects.requireNonNull(proc.matterNetwork(true).inventory());
                    h.assertTrue(wlInv.amount(iron) == 450L, "Whitelist [Iron] must allow Iron");
                    h.assertTrue(wlInv.amount(water) == 0L, "Whitelist [Iron] must block Water");
                    h.assertTrue(wlInv.amount(hydrogen) == 0L, "Whitelist [Iron] must block Hydrogen");

                    // 6. Substance Blacklist: block Iron(Solid), allow everything else
                    relay.configure(GateMode.ALWAYS_OPEN, ChannelMode.BOTH, DirectionMode.BIDIRECTIONAL,
                            0, 0, MatterCableRelayBlockEntity.STATE_ALL, false, List.of(iron), "");
                    var blNet = proc.matterNetwork(true);
                    var blInv = Objects.requireNonNull(blNet.inventory());
                    h.assertTrue(blInv.amount(iron) == 0L, "Blacklist [Iron] must block Iron");
                    h.assertTrue(blInv.amount(water) == 525L, "Blacklist [Iron] must allow Water=525, got " + blInv.amount(water));
                    h.assertTrue(blInv.amount(hydrogen) == 500L, "Blacklist [Iron] must allow Hydrogen");
                    h.assertTrue(blNet.primalAmount() == 950L, "Blacklist [Iron] with STATE_PRIMAL enabled must allow Primal");
                })
                .thenSucceed();
    }

    private static void relayLoadGateSharedBudgetOverhangAndPrimalDeposit(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2.0, 4.5)));
        var level = h.getLevel();

        // 1. Relay loaded with REDSTONE_HIGH and NO redstone signal must stay CLOSED before scheduled tick!
        BlockPos gatePos = new BlockPos(1, 2, 1);
        h.setBlock(gatePos, ModContent.MATTER_CABLE_RELAY.get().defaultBlockState().setValue(MatterCableRelayBlock.FACING, Direction.EAST));
        var gateRelay = h.getBlockEntity(gatePos, MatterCableRelayBlockEntity.class);
        gateRelay.placedBy(owner);
        var loadTag = new net.minecraft.nbt.CompoundTag();
        loadTag.putInt("schema", 1);
        loadTag.putString("owner", owner.getUUID().toString());
        loadTag.putString("gate_mode", "redstone_high");
        gateRelay.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), loadTag));
        h.assertFalse(gateRelay.allowsMatter(), "Loaded REDSTONE_HIGH relay without signal must block Matter immediately before tick");
        h.assertFalse(gateRelay.allowsPower(), "Loaded REDSTONE_HIGH relay without signal must block Power immediately before tick");

        // 2. Deposit-only ONE_WAY relay toward a Drive with Primal Cell must report primalDepositCapacity() > 0!
        //    AND two processors sharing a 50 u/t throttling relay must share one 50 u/t per-tick budget!
        BlockPos drivePos = new BlockPos(1, 2, 4);
        BlockPos cLeft = new BlockPos(2, 2, 4);
        BlockPos throttlePos = new BlockPos(3, 2, 4);
        BlockPos cRight = new BlockPos(4, 2, 4);
        BlockPos p1Pos = new BlockPos(5, 2, 4);
        BlockPos p2Pos = new BlockPos(4, 2, 5);

        var drive = placeMachine(h, drivePos, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        drive.setItem(drive.cellSlotForBay(1), primalCellWith(1_000L));
        h.setBlock(cLeft, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(cLeft), owner.getUUID());
        h.setBlock(cRight, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(cRight), owner.getUUID());
        h.setBlock(throttlePos, ModContent.MATTER_CABLE_RELAY.get().defaultBlockState().setValue(MatterCableRelayBlock.FACING, Direction.WEST));
        var throttle = h.getBlockEntity(throttlePos, MatterCableRelayBlockEntity.class);
        throttle.placedBy(owner);
        // Facing WEST => Input=EAST (procs), Output=WEST (drive): deposit-only ONE_WAY with 50 u/t cap!
        throttle.configure(GateMode.ALWAYS_OPEN, ChannelMode.BOTH, DirectionMode.ONE_WAY,
                0, 50, MatterCableRelayBlockEntity.STATE_ALL, false, List.of(), "");

        var p1 = placeMachine(h, p1Pos, MachineKind.REACTOR, Direction.NORTH, owner);
        var p2 = placeMachine(h, p2Pos, MachineKind.REACTOR, Direction.SOUTH, owner);
        var net1 = p1.matterNetwork(true);
        h.assertTrue(net1.ready(), "Deposit-only network must be ready");
        var net2Check = p2.matterNetwork(true);
        h.assertTrue(net2Check.ready(), "Second machine must connect to the deposit-only network");
        h.assertTrue(net1.primalCapacity() == 0L, "Deposit-only Primal Cell must not report withdrawable capacity");
        h.assertTrue(net1.primalDepositCapacity() > 0L && net1.primalFreeSpace() > 0L,
                "Deposit-only Primal Cell must report positive primalDepositCapacity & primalFreeSpace");

        long tick = level.getGameTime();
        var iron = SubstanceKey.of("rematter:iron", Phase.SOLID);
        var w1 = new ControllerThroughput();
        var w2 = new ControllerThroughput();
        h.assertFalse(net1.advanceTransfer(p1, w1, "op1", 100L, tick, Map.of(), Map.of(iron, 100L)),
                "First machine should only get 50 of 100 units in tick 1");
        h.assertTrue(w1.paid() == 50L, "First machine must receive 50 u/t from 50 u/t relay, got " + w1.paid());
        var net2 = p2.matterNetwork(true);
        h.assertFalse(net2.advanceTransfer(p2, w2, "op2", 100L, tick, Map.of(), Map.of(iron, 100L)),
                "Second machine in same tick must be capped by shared relay budget");
        h.assertTrue(w2.paid() == 0L, "Second machine must receive 0 u/t once shared 50 u/t relay budget is spent, got " + w2.paid());
        h.assertFalse(net2.advanceTransfer(p2, w2, "op2", 100L, tick + 1L, Map.of(), Map.of(iron, 100L)),
                "Next tick must replenish relay budget to 50 u/t");
        h.assertTrue(w2.paid() == 50L, "Second machine must receive 50 u/t on tick+1, got " + w2.paid());

        // 3. Energy Reactor overhang preservation when chamber shrinks!
        for (BlockPos p : List.of(gatePos, drivePos, cLeft, throttlePos, cRight, p1Pos, p2Pos)) {
            h.setBlock(p, Blocks.AIR);
        }
        var rig = buildEnergyReactor(h, new BlockPos(2, 1, 2), owner);
        var ctrl = rig.controller();
        setPortMode(rig.westPort(), owner, Direction.WEST, MachinePortMode.POWER_OUTPUT);
        // Place Energy Cube on west port to receive drained overhang
        h.setBlock(rig.westPortPos().west(), ModContent.ENERGY_CUBES.get(EnergyCubeTier.RESONANT).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.SOUTH));
        var cube = h.getBlockEntity(rig.westPortPos().west(), EnergyCubeBlockEntity.class);
        cube.placedBy(owner);
        var emberCrystal = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "ember_matrix_crystal"));
        ctrl.setItem(0, new ItemStack(emberCrystal, 16));

        // Seed reactor energy to 2-core capacity (above 1-core capacity!) via NBT load, then remove 1 Fuel Core!
        var twoCoreCap = IndustrialStructure.inspect(ctrl).performance().reactorCapacity();
        var savedTag = ctrl.saveCustomOnly(level.registryAccess());
        savedTag.putLong("reactor_energy", twoCoreCap);
        ctrl.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), savedTag));
        h.assertTrue(ctrl.reactorEnergy() == twoCoreCap, "Seeded reactor energy must equal 2-core capacity");

        // Remove one Fuel Core -> 1-core capacity is strictly less than twoCoreCap!
        h.setBlock(rig.corePos(), Blocks.AIR);
        ctrl.serverTick();
        var oneCoreCap = IndustrialStructure.inspect(ctrl).performance().reactorCapacity();
        h.assertTrue(oneCoreCap < twoCoreCap, "1-core reactor capacity must be smaller than 2-core capacity");
        h.assertTrue(ctrl.reactorEnergy() > oneCoreCap,
                "Shrinking Energy Reactor must preserve overhang extractably instead of truncating to " + oneCoreCap + ", got " + ctrl.reactorEnergy());
        h.assertTrue(cube.energy() > 0L, "Overhang energy must drain out through POWER port into Energy Cube");
        h.assertTrue("energy_full".equals(ctrl.work().status),
                "Overhang above capacity must pause new fuel generation with energy_full, got " + ctrl.work().status);
        h.succeed();
    }
}
