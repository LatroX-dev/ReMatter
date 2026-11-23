package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.celltransfer.CellTransferStationBlock;
import dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeTier;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import dev.latrox.rematter.primal.PrimalContent;
import dev.latrox.rematter.research.LearningPath;
import dev.latrox.rematter.research.ResearchService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Server GameTests for Auto-Eject, Wireless Hotkey, live FE/t + ownership boundaries, and Industrial Decomposer / Synthesizer multiblocks. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class OptimizationAndIndustrialGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static {
        TESTS.put("auto_eject_pushes_only_explicit_output_faces_and_copies_via_card",
                OptimizationAndIndustrialGameTests::autoEjectAndConfigCard);
        TESTS.put("wireless_hotkey_validates_carried_terminal_and_opens_menu",
                OptimizationAndIndustrialGameTests::wirelessHotkey);
        TESTS.put("live_energy_rate_and_auxiliary_device_ownership_boundaries",
                OptimizationAndIndustrialGameTests::energyRateAndAuxiliaryOwnership);
        TESTS.put("industrial_decomposer_transfers_items_and_fluids_via_ports_with_parallel_limit",
                OptimizationAndIndustrialGameTests::industrialDecomposerChamber);
        TESTS.put("industrial_synthesizer_crystallizes_diamonds_in_parallel_without_pressure_module",
                OptimizationAndIndustrialGameTests::industrialSynthesizerChamber);
    }

    private OptimizationAndIndustrialGameTests() {}

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("rematter_tests", path); }

    @SubscribeEvent
    public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach((name, fn) -> helper.register(id(name), fn)));
    }

    @SubscribeEvent
    public static void tests(RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(id("optimization_industrial_environment"));
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

    private static void setSidePortMode(MachineBlockEntity machine, MachinePortMode.Side side, MachinePortMode desired) {
        for (int i = 0; i < 12 && machine.portMode(side) != desired; i++) {
            machine.cyclePort(side);
        }
        if (machine.portMode(side) != desired) {
            throw new IllegalStateException("Could not set side " + side + " to " + desired + " on " + machine.kind());
        }
    }

    private static void setBlockPortMode(MachinePortBlockEntity port, Player owner, Direction outward, MachinePortMode desired) {
        for (int i = 0; i < 12 && port.mode() != desired; i++) {
            port.cycleMode(owner, outward);
        }
        if (port.mode() != desired) {
            throw new IllegalStateException("Could not set Machine Port mode to " + desired + ", got " + port.mode());
        }
    }

    /** Builds a 3×7×3 reactor-module chamber (height 7 -> 4 parallel lanes) with controller facing NORTH at (x+1, y+1, z). */
    private static Chamber buildReactorChamber(GameTestHelper h, BlockPos origin, MachineKind controllerKind, Player owner) {
        int height = 7;
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < height; dy++) {
                for (int dz = 0; dz < 3; dz++) {
                    boolean boundary = dx == 0 || dx == 2 || dy == 0 || dy == height - 1 || dz == 0 || dz == 2;
                    if (!boundary) continue;
                    boolean edge = (dy == 0 || dy == height - 1) || ((dx == 0 || dx == 2) && (dz == 0 || dz == 2));
                    h.setBlock(origin.offset(dx, dy, dz), edge ? ModContent.REACTOR_CASING.get() : ModContent.REACTOR_GLASS.get());
                }
            }
        }
        BlockPos controllerPos = origin.offset(1, 1, 0);
        var controller = placeMachine(h, controllerPos, controllerKind, Direction.NORTH, owner);

        // Mount 1 Field Stabilizer on interior floor and 1 Reaction Focus suspended under interior ceiling.
        h.setBlock(origin.offset(1, 1, 1),
                ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.FIELD_COIL).get().defaultBlockState().setValue(IndustrialModuleBlock.SUSPENDED, false));
        h.setBlock(origin.offset(1, height - 2, 1),
                ModContent.INDUSTRIAL_MODULES.get(IndustrialModuleKind.REACTION_CHAMBER).get().defaultBlockState().setValue(IndustrialModuleBlock.SUSPENDED, true));

        // Four Machine Ports on wall panels: Power (west y+1), Matter (east y+1), Item (west y+2), Fluid (east y+2).
        BlockPos powerPos = origin.offset(0, 1, 1);
        BlockPos matterPos = origin.offset(2, 1, 1);
        BlockPos itemPos = origin.offset(0, 2, 1);
        BlockPos fluidPos = origin.offset(2, 2, 1);
        h.setBlock(powerPos, ModContent.MACHINE_PORT.get());
        h.setBlock(matterPos, ModContent.MACHINE_PORT.get());
        h.setBlock(itemPos, ModContent.MACHINE_PORT.get());
        h.setBlock(fluidPos, ModContent.MACHINE_PORT.get());

        var powerPort = h.getBlockEntity(powerPos, MachinePortBlockEntity.class);
        var matterPort = h.getBlockEntity(matterPos, MachinePortBlockEntity.class);
        var itemPort = h.getBlockEntity(itemPos, MachinePortBlockEntity.class);
        var fluidPort = h.getBlockEntity(fluidPos, MachinePortBlockEntity.class);
        powerPort.refreshNow();
        matterPort.refreshNow();
        itemPort.refreshNow();
        fluidPort.refreshNow();
        return new Chamber(controller, powerPort, matterPort, itemPort, fluidPort, powerPos, matterPos, itemPos, fluidPos);
    }

    private record Chamber(MachineBlockEntity controller, MachinePortBlockEntity powerPort, MachinePortBlockEntity matterPort,
                           MachinePortBlockEntity itemPort, MachinePortBlockEntity fluidPort,
                           BlockPos powerPos, BlockPos matterPos, BlockPos itemPos, BlockPos fluidPos) {}

    private static void autoEjectAndConfigCard(GameTestHelper h) {
        h.assertTrue(MachineBlockEntity.supportsAutoEject(MachineKind.DECOMPOSER)
                && MachineBlockEntity.supportsAutoEject(MachineKind.SYNTHESIZER)
                && MachineBlockEntity.supportsAutoEject(MachineKind.PREPARATOR)
                && MachineBlockEntity.supportsAutoEject(MachineKind.SINTER_FURNACE),
                "Compact physical-output machines must support Auto-Eject");
        h.assertFalse(MachineBlockEntity.supportsAutoEject(MachineKind.HAND_PREPARATOR)
                || MachineBlockEntity.supportsAutoEject(MachineKind.ANALYZER)
                || MachineBlockEntity.supportsAutoEject(MachineKind.INDUSTRIAL_DECOMPOSER)
                || MachineBlockEntity.supportsAutoEject(MachineKind.INDUSTRIAL_SYNTHESIZER),
                "Manual, Analyzer, and Industrial controllers must not advertise Auto-Eject");

        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ResearchService.ledger(h.getLevel().getServer()).visit(owner.getUUID(), h.getLevel().dimension().identifier().toString());
        h.assertTrue(ResearchService.learn(h.getLevel(), owner.getUUID(), "minecraft:glass", LearningPath.ANALYSIS),
                "Owner failed to learn glass research");

        BlockPos synthPos = new BlockPos(3, 2, 3);
        BlockPos drivePos = synthPos.south();
        BlockPos leftChestPos = synthPos.east();   // facing NORTH -> LEFT is EAST
        BlockPos rightChestPos = synthPos.west();  // facing NORTH -> RIGHT is WEST

        var synth = placeMachine(h, synthPos, MachineKind.SYNTHESIZER, Direction.NORTH, owner);
        var drive = placeMachine(h, drivePos, MachineKind.DRIVE, Direction.SOUTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        var driveNet = drive.matterNetwork(true);
        var inv = Objects.requireNonNull(driveNet.inventory());
        h.assertTrue(inv.apply(Map.of(), Map.of(BuiltinChemistry.SILICA, 10_000L)), "Failed to seed silica");
        driveNet.markChanged();

        h.setBlock(leftChestPos, Blocks.CHEST);
        h.setBlock(rightChestPos, Blocks.CHEST);
        setSidePortMode(synth, MachinePortMode.Side.LEFT, MachinePortMode.ITEM_OUTPUT);
        // RIGHT remains AUTOMATIC -> must never be auto-ejected into!
        h.assertTrue(synth.portMode(MachinePortMode.Side.RIGHT) == MachinePortMode.AUTOMATIC, "RIGHT should default to AUTOMATIC");
        h.assertFalse(synth.autoEject(), "Auto-Eject must start disabled");

        synth.energy().set(synth.energyCapacity());
        synth.setItem(2, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        synth.toggleAutomation();
        h.assertTrue(synth.selectRecipe("minecraft:glass"), "Could not select glass");
        h.assertTrue(synth.requestBatch(), "Could not queue first glass batch");

        Container leftChest = h.getBlockEntity(leftChestPos, ChestBlockEntity.class);
        Container rightChest = h.getBlockEntity(rightChestPos, ChestBlockEntity.class);

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(synth.getItem(1).is(Items.GLASS) && synth.getItem(1).getCount() == 1,
                        "First batch should stay in output slot while Auto-Eject is off"))
                .thenExecute(() -> {
                    h.assertTrue(leftChest.getItem(0).isEmpty() && rightChest.getItem(0).isEmpty(),
                            "Chests must stay empty while Auto-Eject is off");
                    h.assertTrue(synth.toggleAutoEject() && synth.autoEject(), "Auto-Eject should enable");
                })
                .thenWaitUntil(() -> h.assertTrue(leftChest.getItem(0).is(Items.GLASS) && leftChest.getItem(0).getCount() == 1,
                        "Auto-Eject should push glass to explicit ITEM_OUTPUT chest"))
                .thenExecute(() -> {
                    h.assertTrue(synth.getItem(1).isEmpty(), "Output slot should be empty after Auto-Eject");
                    h.assertTrue(rightChest.getItem(0).isEmpty(), "AUTOMATIC face must never receive Auto-Eject output");

                    // Copy setup via ConfigurationCardData onto a second Synthesizer and verify autoEject copies cleanly.
                    var copied = placeMachine(h, new BlockPos(6, 2, 3), MachineKind.SYNTHESIZER, Direction.NORTH, owner);
                    var captured = new ConfigurationCardData.MachineSetup(
                            synth.kind(), PortSides.pack(synth::portMode), synth.redstoneMode(), synth.running(),
                            synth.runState().repeat(), synth.automation(), synth.networkJobs(), synth.selectedId(),
                            synth.settings(), synth.soundLevel(), synth.customIndustrialBlueprint(),
                            synth.structurePreviewEnabled(), synth.autoEject());
                    var card = ConfigurationCardData.fromMachine(captured);
                    h.assertTrue(copied.applyCopiedSetup(owner, card.machineSetup().orElseThrow()), "Failed to apply config card");
                    h.assertTrue(copied.autoEject(), "Copied Synthesizer must preserve Auto-Eject=true");
                    h.assertTrue(copied.portMode(MachinePortMode.Side.LEFT) == MachinePortMode.ITEM_OUTPUT,
                            "Copied Synthesizer must preserve LEFT=ITEM_OUTPUT");
                })
                .thenSucceed();
    }

    private static void wirelessHotkey(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var stranger = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos drivePos = new BlockPos(2, 2, 2);
        BlockPos apPos = new BlockPos(2, 2, 3);
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5)));
        stranger.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5)));

        var drive = placeMachine(h, drivePos, MachineKind.DRIVE, Direction.NORTH, player);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));

        h.setBlock(apPos, ModContent.WIRELESS_ACCESS_POINT.get().defaultBlockState().setValue(WirelessAccessPointBlock.FACING, Direction.SOUTH));
        var ap = h.getBlockEntity(apPos, WirelessAccessPointBlockEntity.class);
        ap.placedBy(player);
        var powerCap = h.getLevel().getCapability(Capabilities.Energy.BLOCK, h.absolutePos(apPos), Direction.DOWN);
        Objects.requireNonNull(powerCap, "Wireless AP bottom face must expose Energy capability");
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(powerCap.insert(8_192, tx) > 0, "Failed to power Wireless AP");
            tx.commit();
        }
        ap.serverTick();
        h.assertTrue(ap.powered() && ap.matterReady(), "Wireless AP should be powered and linked to Drive");

        // No terminal carried -> hotkey fails safely.
        h.assertTrue(WirelessMatterTerminalItem.prepareHotkeyOpen(player) == null,
                "Hotkey must fail when no terminal is carried");

        // Uncharged bound terminal in main inventory (not hotbar) -> fails safely.
        ItemStack terminal = new ItemStack(ModContent.WIRELESS_MATTER_TERMINAL.get());
        terminal.set(WirelessTerminalData.LINK.get(), WirelessTerminalData.of(h.getLevel().dimension(), h.absolutePos(apPos)));
        player.getInventory().setItem(12, terminal);
        h.assertTrue(WirelessMatterTerminalItem.prepareHotkeyOpen(player) == null,
                "Uncharged terminal must not open");

        // Foreign player carrying a charged copy of the same link must be refused.
        ItemStack stolen = terminal.copy();
        WirelessMatterTerminalItem.setEnergy(stolen, WirelessMatterTerminalItem.OPEN_COST * 10);
        stranger.getInventory().setItem(12, stolen);
        h.assertTrue(WirelessMatterTerminalItem.prepareHotkeyOpen(stranger) == null,
                "Stranger carrying a bound terminal must be refused by AP ownership");

        // Charge owner's terminal in inventory slot 12 -> hotkey resolves anchor, deducts OPEN_COST, and produces valid wireless MachineMenu!
        int initialEnergy = WirelessMatterTerminalItem.OPEN_COST * 10;
        WirelessMatterTerminalItem.setEnergy(terminal, initialEnergy);
        var anchor = WirelessMatterTerminalItem.prepareHotkeyOpen(player);
        h.assertTrue(anchor != null && anchor.lockedPlayerSlot() == 12,
                "Charged bound terminal in inventory slot 12 should resolve via hotkey");
        h.assertTrue(WirelessMatterTerminalItem.energy(terminal) == initialEnergy - WirelessMatterTerminalItem.OPEN_COST,
                "Hotkey activation must deduct OPEN_COST from the carried terminal");
        var menu = new MachineMenu(1, player.getInventory(), anchor);
        h.assertTrue(menu.wireless() && menu.stillValid(player) && TerminalNetwork.isValidAnchor(anchor, player),
                "Hotkey anchor must produce a valid wireless MachineMenu");
        h.succeed();
    }

    private static void energyRateAndAuxiliaryOwnership(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var stranger = h.makeMockPlayer(GameType.SURVIVAL);

        // Verify ownership isolation across all auxiliary devices exposed to Jade.
        BlockPos cubePos = new BlockPos(1, 2, 2);
        h.setBlock(cubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get().defaultBlockState().setValue(EnergyCubeBlock.FACING, Direction.NORTH));
        var cube = h.getBlockEntity(cubePos, EnergyCubeBlockEntity.class);
        cube.placedBy(owner);
        h.assertTrue(cube.canAccess(owner) && !cube.canAccess(stranger), "Energy Cube ownership mismatch");

        BlockPos apPos = new BlockPos(2, 2, 2);
        h.setBlock(apPos, ModContent.WIRELESS_ACCESS_POINT.get().defaultBlockState());
        var ap = h.getBlockEntity(apPos, WirelessAccessPointBlockEntity.class);
        ap.placedBy(owner);
        h.assertTrue(ap.canAccess(owner) && !ap.canAccess(stranger), "Wireless AP ownership mismatch");

        BlockPos emitterPos = new BlockPos(3, 2, 2);
        h.setBlock(emitterPos, ModContent.MATTER_LEVEL_EMITTER.get().defaultBlockState());
        var emitter = h.getBlockEntity(emitterPos, MatterLevelEmitterBlockEntity.class);
        emitter.placedBy(owner);
        h.assertTrue(emitter.canAccess(owner) && !emitter.canAccess(stranger), "Level Emitter ownership mismatch");

        BlockPos linkPos = new BlockPos(4, 2, 2);
        var linkState = PrimalContent.ENTANGLED_LINK.get().defaultBlockState();
        h.setBlock(linkPos, linkState);
        PrimalContent.ENTANGLED_LINK.get().setPlacedBy(h.getLevel(), h.absolutePos(linkPos), linkState, owner, new ItemStack(PrimalContent.ENTANGLED_LINK.get()));
        var link = h.getBlockEntity(linkPos, EntangledLinkBlockEntity.class);
        h.assertTrue(link.canRemove(owner) && !link.canRemove(stranger), "Entangled Link ownership mismatch");

        BlockPos stationPos = new BlockPos(5, 2, 2);
        h.setBlock(stationPos, ModContent.CELL_TRANSFER_STATION.get().defaultBlockState().setValue(CellTransferStationBlock.FACING, Direction.NORTH));
        var station = h.getBlockEntity(stationPos, CellTransferStationBlockEntity.class);
        station.placedBy(owner);
        h.assertTrue(station.canAccess(owner) && !station.canAccess(stranger), "Cell Transfer Station ownership mismatch");

        // Verify live currentEnergyRate() on a working Decomposer returns > 0 FE/t while active and 0 once idle.
        var drive = placeMachine(h, new BlockPos(2, 2, 4), MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        var decomposer = placeMachine(h, new BlockPos(3, 2, 4), MachineKind.DECOMPOSER, Direction.NORTH, owner);
        decomposer.energy().set(decomposer.energyCapacity());
        decomposer.setItem(0, new ItemStack(Items.SAND, 1));
        h.assertTrue(decomposer.currentEnergyRate() == 0L, "Idle decomposer must report 0 FE/t");

        h.startSequence()
                .thenExecuteAfter(2, () -> h.assertTrue(decomposer.currentEnergyRate() > 0L,
                        "Active decomposer must report live positive FE/t, got " + decomposer.currentEnergyRate()))
                .thenWaitUntil(() -> h.assertTrue(decomposer.getItem(0).isEmpty() && decomposer.currentEnergyRate() == 0L,
                        "Decomposer should finish sand and return to 0 FE/t"))
                .thenSucceed();
    }

    private static void industrialDecomposerChamber(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5)));
        Chamber chamber = buildReactorChamber(h, new BlockPos(1, 1, 1), MachineKind.INDUSTRIAL_DECOMPOSER, owner);
        h.assertTrue(chamber.controller().industrialStructureReady(), "Industrial Decomposer chamber failed to form");

        setBlockPortMode(chamber.powerPort(), owner, Direction.WEST, MachinePortMode.POWER);
        setBlockPortMode(chamber.matterPort(), owner, Direction.EAST, MachinePortMode.MATTER);
        setBlockPortMode(chamber.itemPort(), owner, Direction.WEST, MachinePortMode.ITEM_INPUT);
        setBlockPortMode(chamber.fluidPort(), owner, Direction.EAST, MachinePortMode.FLUID_INPUT);

        // Connect Matter Drive with 256k Cell adjacent to the Matter port.
        var drive = placeMachine(h, chamber.matterPos().east(), MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_16M.get()));

        chamber.controller().toggleAutomation();
        chamber.controller().setItem(2, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        chamber.controller().setItem(MachineCellAccess.PARALLEL_SLOT, new ItemStack(ModContent.PARALLEL_UPGRADE.get(), 3));
        h.assertTrue(chamber.controller().parallelCapacity() == 4,
                "3x7x3 Industrial Decomposer with 3 Parallel Upgrades should reach 4 lanes, got " + chamber.controller().parallelCapacity());

        // Power via Power Port.
        var energyCap = h.getLevel().getCapability(Capabilities.Energy.BLOCK, h.absolutePos(chamber.powerPos()), Direction.WEST);
        Objects.requireNonNull(energyCap, "Power Machine Port must expose Energy capability");
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(energyCap.insert(chamber.controller().energyCapacity(), tx) > 0, "Failed to power Industrial Decomposer");
            tx.commit();
        }
        chamber.controller().energy().set(chamber.controller().energyCapacity());

        // Insert exactly 3 Sand via Item Input Machine Port -> must run 3 parallel lanes (not 4!) and store 3,000 u Silica.
        var itemCap = h.getLevel().getCapability(Capabilities.Item.BLOCK, h.absolutePos(chamber.itemPos()), Direction.WEST);
        Objects.requireNonNull(itemCap, "Item Input Machine Port must expose Item capability");
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(itemCap.insert(ItemResource.of(Items.SAND), 3, tx) == 3, "Failed to insert 3 Sand via Machine Port");
            tx.commit();
        }

        h.startSequence()
                .thenWaitUntil(() -> {
                    var inv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
                    h.assertTrue(inv.amount(BuiltinChemistry.SILICA) == 3_000L && chamber.controller().getItem(0).isEmpty(),
                            "Expected 3,000 u Silica from 3 Sand, got " + inv.amount(BuiltinChemistry.SILICA));
                })
                .thenExecute(() -> {
                    // Insert 2,000 mB Water via Fluid Input Machine Port -> decompose into 2,000 u liquid water!
                    var fluidCap = h.getLevel().getCapability(Capabilities.Fluid.BLOCK, h.absolutePos(chamber.fluidPos()), Direction.EAST);
                    Objects.requireNonNull(fluidCap, "Fluid Input Machine Port must expose Fluid capability");
                    try (var tx = Transaction.openRoot()) {
                        h.assertTrue(fluidCap.insert(FluidResource.of(Fluids.WATER), 2_000, tx) == 2_000,
                                "Failed to insert 2,000 mB Water via Machine Port");
                        tx.commit();
                    }
                })
                .thenWaitUntil(() -> {
                    var inv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
                    h.assertTrue(inv.amount(BuiltinChemistry.WATER) == 2_000L
                                    && chamber.controller().fluids().getAmountAsLong(0) == 0L,
                            "Expected 2,000 u Water from 2,000 mB Water, got " + inv.amount(BuiltinChemistry.WATER));
                })
                .thenSucceed();
    }

    private static void industrialSynthesizerChamber(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5)));
        ResearchService.ledger(h.getLevel().getServer()).visit(owner.getUUID(), h.getLevel().dimension().identifier().toString());
        h.assertTrue(ResearchService.learn(h.getLevel(), owner.getUUID(), "minecraft:diamond", LearningPath.ANALYSIS),
                "Owner failed to learn diamond research");
        h.assertTrue(ResearchService.learn(h.getLevel(), owner.getUUID(), "minecraft:water", LearningPath.DECOMPOSITION),
                "Owner failed to learn liquid water research");

        Chamber chamber = buildReactorChamber(h, new BlockPos(1, 1, 1), MachineKind.INDUSTRIAL_SYNTHESIZER, owner);
        h.assertTrue(chamber.controller().industrialStructureReady(), "Industrial Synthesizer chamber failed to form");
        h.assertTrue(chamber.controller().crystallizationTier() == 2,
                "Formed Industrial Synthesizer must have built-in Tier 2 crystallization without a Pressure Module");

        setBlockPortMode(chamber.powerPort(), owner, Direction.WEST, MachinePortMode.POWER);
        setBlockPortMode(chamber.matterPort(), owner, Direction.EAST, MachinePortMode.MATTER);
        setBlockPortMode(chamber.itemPort(), owner, Direction.WEST, MachinePortMode.ITEM_OUTPUT);
        setBlockPortMode(chamber.fluidPort(), owner, Direction.EAST, MachinePortMode.FLUID_OUTPUT);

        var drive = placeMachine(h, chamber.matterPos().east(), MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_16M.get()));
        var driveNet = drive.matterNetwork(true);
        var inv = Objects.requireNonNull(driveNet.inventory());
        var diamondCarbon = dev.latrox.rematter.chemistry.SubstanceKey.of("rematter:diamond", dev.latrox.rematter.chemistry.Phase.SOLID);
        h.assertTrue(inv.apply(Map.of(), Map.of(diamondCarbon, 10_000L, BuiltinChemistry.WATER, 8_000L)),
                "Failed to seed Diamond Carbon and Water");
        driveNet.markChanged();

        chamber.controller().toggleAutomation();
        chamber.controller().energy().set(chamber.controller().energyCapacity());
        chamber.controller().setItem(2, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        chamber.controller().setItem(MachineCellAccess.PARALLEL_SLOT, new ItemStack(ModContent.PARALLEL_UPGRADE.get(), 3));
        h.assertTrue(chamber.controller().parallelCapacity() == 4, "Industrial Synthesizer should have 4 parallel lanes");

        h.assertTrue(chamber.controller().selectRecipe("minecraft:diamond"), "Could not select Diamond");
        for (int i = 0; i < 4; i++) {
            h.assertTrue(chamber.controller().requestBatch(), "Could not queue Diamond batch " + (i + 1));
        }

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(
                        chamber.controller().getItem(1).is(Items.DIAMOND) && chamber.controller().getItem(1).getCount() == 4,
                        "Expected 4 synthesized Diamonds, got " + chamber.controller().getItem(1)))
                .thenExecute(() -> {
                    var currentInv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
                    h.assertTrue(currentInv.amount(diamondCarbon) == 6_000L,
                            "4 Diamonds must consume exactly 4,000 u diamond carbon, remaining: " + currentInv.amount(diamondCarbon));
                    // Extract all 4 Diamonds through the Item Output Machine Port!
                    var itemCap = h.getLevel().getCapability(Capabilities.Item.BLOCK, h.absolutePos(chamber.itemPos()), Direction.WEST);
                    Objects.requireNonNull(itemCap, "Item Output Machine Port must expose Item capability");
                    try (var tx = Transaction.openRoot()) {
                        h.assertTrue(itemCap.extract(ItemResource.of(Items.DIAMOND), 4, tx) == 4,
                                "Failed to extract 4 Diamonds via Machine Port");
                        tx.commit();
                    }
                    // Now synthesize 4,000 mB Water and extract via Fluid Output Machine Port!
                    chamber.controller().energy().set(chamber.controller().energyCapacity());
                    h.assertTrue(chamber.controller().selectRecipe("fluid:minecraft:water"), "Could not select Water");
                    for (int i = 0; i < 4; i++) {
                        h.assertTrue(chamber.controller().requestBatch(), "Could not queue Water batch " + (i + 1));
                    }
                })
                .thenWaitUntil(() -> h.assertTrue(chamber.controller().fluids().getAmountAsLong(0) == 4_000L,
                        "Expected 4,000 mB synthesized Water, got " + chamber.controller().fluids().getAmountAsLong(0)))
                .thenExecute(() -> {
                    var currentInv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
                    h.assertTrue(currentInv.amount(BuiltinChemistry.WATER) == 4_000L,
                            "4,000 mB Water must consume exactly 4,000 u water, remaining: " + currentInv.amount(BuiltinChemistry.WATER));
                    var fluidCap = h.getLevel().getCapability(Capabilities.Fluid.BLOCK, h.absolutePos(chamber.fluidPos()), Direction.EAST);
                    Objects.requireNonNull(fluidCap, "Fluid Output Machine Port must expose Fluid capability");
                    try (var tx = Transaction.openRoot()) {
                        h.assertTrue(fluidCap.extract(FluidResource.of(Fluids.WATER), 4_000, tx) == 4_000,
                                "Failed to extract 4,000 mB Water via Machine Port");
                        tx.commit();
                    }
                })
                .thenSucceed();
    }
}
