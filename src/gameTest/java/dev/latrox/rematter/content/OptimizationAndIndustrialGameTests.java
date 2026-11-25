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
        TESTS.put("legacy_controller_registry_aliases_resolve_to_reactor_controllers",
                OptimizationAndIndustrialGameTests::legacyControllerAliases);
        TESTS.put("deep_extractor_extracts_mineral_slurry_over_deepslate",
                OptimizationAndIndustrialGameTests::deepExtractorOverDeepslate);
        TESTS.put("transmutation_reactor_forms_and_transmutes_iron_to_gold",
                OptimizationAndIndustrialGameTests::transmutationReactor);
        TESTS.put("primal_reactor_forms_and_condenses_into_4m_primal_cell",
                OptimizationAndIndustrialGameTests::primalReactor);
        TESTS.put("energy_matrix_buffers_64bit_fe_silently_and_connects_glass",
                OptimizationAndIndustrialGameTests::energyMatrixAndConnectedGlass);
        TESTS.put("energy_reactor_generates_across_modes_with_coolant_and_feeds_matrix",
                OptimizationAndIndustrialGameTests::energyReactorModesCoolantAndMatrixFeed);
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
        BlockPos cablePos = new BlockPos(2, 2, 3);
        BlockPos apPos = new BlockPos(2, 3, 3);
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5)));
        stranger.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5)));

        var drive = placeMachine(h, drivePos, MachineKind.DRIVE, Direction.NORTH, player);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        h.setBlock(cablePos, ModContent.MATTER_CABLE.get());
        CableOwners.set(h.getLevel(), h.absolutePos(cablePos), player.getUUID());

        h.setBlock(apPos, ModContent.WIRELESS_ACCESS_POINT.get().defaultBlockState().setValue(WirelessAccessPointBlock.FACING, Direction.NORTH));
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

        // Foreign player carrying a charged copy of the same link is allowed on PUBLIC AP, refused on PRIVATE AP.
        ItemStack stolen = terminal.copy();
        WirelessMatterTerminalItem.setEnergy(stolen, WirelessMatterTerminalItem.OPEN_COST * 10);
        stranger.getInventory().setItem(12, stolen);
        h.assertTrue(WirelessMatterTerminalItem.prepareHotkeyOpen(stranger) != null,
                "PUBLIC AP should allow other players with a bound terminal");
        ap.setSecurityMode(player, SecurityMode.PRIVATE);
        h.assertTrue(WirelessMatterTerminalItem.prepareHotkeyOpen(stranger) == null,
                "Stranger carrying a bound terminal must be refused by PRIVATE AP");

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

        // Verify default PUBLIC access and PRIVATE isolation across all auxiliary devices exposed to Jade.
        BlockPos cubePos = new BlockPos(1, 2, 2);
        h.setBlock(cubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get().defaultBlockState().setValue(EnergyCubeBlock.FACING, Direction.NORTH));
        var cube = h.getBlockEntity(cubePos, EnergyCubeBlockEntity.class);
        cube.placedBy(owner);
        h.assertTrue(cube.canAccess(owner) && cube.canAccess(stranger) && !cube.canManageSecurity(stranger), "Energy Cube PUBLIC default mismatch");
        cube.setSecurityMode(owner, SecurityMode.PRIVATE);
        h.assertTrue(cube.canAccess(owner) && !cube.canAccess(stranger), "Energy Cube PRIVATE isolation mismatch");

        BlockPos apPos = new BlockPos(2, 2, 2);
        h.setBlock(apPos, ModContent.WIRELESS_ACCESS_POINT.get().defaultBlockState());
        var ap = h.getBlockEntity(apPos, WirelessAccessPointBlockEntity.class);
        ap.placedBy(owner);
        h.assertTrue(ap.canAccess(owner) && ap.canAccess(stranger) && !ap.canManageSecurity(stranger), "Wireless AP PUBLIC default mismatch");
        ap.setSecurityMode(owner, SecurityMode.PRIVATE);
        h.assertTrue(ap.canAccess(owner) && !ap.canAccess(stranger), "Wireless AP PRIVATE isolation mismatch");

        BlockPos emitterPos = new BlockPos(3, 2, 2);
        h.setBlock(emitterPos, ModContent.MATTER_LEVEL_EMITTER.get().defaultBlockState());
        var emitter = h.getBlockEntity(emitterPos, MatterLevelEmitterBlockEntity.class);
        emitter.placedBy(owner);
        h.assertTrue(emitter.canAccess(owner) && emitter.canAccess(stranger) && !emitter.canManageSecurity(stranger), "Level Emitter PUBLIC default mismatch");
        emitter.setSecurityMode(owner, SecurityMode.PRIVATE);
        h.assertTrue(emitter.canAccess(owner) && !emitter.canAccess(stranger), "Level Emitter PRIVATE isolation mismatch");

        BlockPos linkPos = new BlockPos(4, 2, 2);
        var linkState = PrimalContent.ENTANGLED_LINK.get().defaultBlockState();
        h.setBlock(linkPos, linkState);
        PrimalContent.ENTANGLED_LINK.get().setPlacedBy(h.getLevel(), h.absolutePos(linkPos), linkState, owner, new ItemStack(PrimalContent.ENTANGLED_LINK.get()));
        var link = h.getBlockEntity(linkPos, EntangledLinkBlockEntity.class);
        h.assertTrue(link.canRemove(owner) && link.canRemove(stranger) && !link.canManageSecurity(stranger), "Entangled Link PUBLIC default mismatch");
        link.setSecurityMode(owner, SecurityMode.PRIVATE);
        h.assertTrue(link.canRemove(owner) && !link.canRemove(stranger), "Entangled Link PRIVATE isolation mismatch");

        BlockPos stationPos = new BlockPos(5, 2, 2);
        h.setBlock(stationPos, ModContent.CELL_TRANSFER_STATION.get().defaultBlockState().setValue(CellTransferStationBlock.FACING, Direction.NORTH));
        var station = h.getBlockEntity(stationPos, CellTransferStationBlockEntity.class);
        station.placedBy(owner);
        h.assertTrue(station.canAccess(owner) && station.canAccess(stranger) && !station.canManageSecurity(stranger), "Cell Transfer Station PUBLIC default mismatch");
        station.setSecurityMode(owner, SecurityMode.PRIVATE);
        h.assertTrue(station.canAccess(owner) && !station.canAccess(stranger), "Cell Transfer Station PRIVATE isolation mismatch");

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

    private static void legacyControllerAliases(GameTestHelper h) {
        var oldDecomp = Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "industrial_decomposer_controller");
        var oldSynth = Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "industrial_synthesizer_controller");
        h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(oldDecomp)
                        == ModContent.MACHINES.get(MachineKind.INDUSTRIAL_DECOMPOSER).get(),
                "Legacy industrial_decomposer_controller block alias must resolve to Decomposer Reactor Controller");
        h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(oldDecomp)
                        == ModContent.MACHINES.get(MachineKind.INDUSTRIAL_DECOMPOSER).get().asItem(),
                "Legacy industrial_decomposer_controller item alias must resolve to Decomposer Reactor Controller");
        h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(oldSynth)
                        == ModContent.MACHINES.get(MachineKind.INDUSTRIAL_SYNTHESIZER).get(),
                "Legacy industrial_synthesizer_controller block alias must resolve to Synthesizer Reactor Controller");
        h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(oldSynth)
                        == ModContent.MACHINES.get(MachineKind.INDUSTRIAL_SYNTHESIZER).get().asItem(),
                "Legacy industrial_synthesizer_controller item alias must resolve to Synthesizer Reactor Controller");
        h.succeed();
    }

    private static void deepExtractorOverDeepslate(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos extPos = new BlockPos(3, 3, 3);
        BlockPos drivePos = extPos.east();
        // Verify ordinary cobblestone is rejected under genuine-site rules, then switch to Deepslate (y <= 0 in test flat world).
        h.setBlock(extPos.below(), Blocks.COBBLESTONE);
        var extractor = placeMachine(h, extPos, MachineKind.DEEP_EXTRACTOR, Direction.NORTH, owner);
        var drive = placeMachine(h, drivePos, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        extractor.energy().set(extractor.energyCapacity());
        extractor.setItem(2, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        extractor.setItem(MachineCellAccess.PARALLEL_SLOT, new ItemStack(ModContent.EXTRACTION_FOCUS_UPGRADE.get(), 1));
        h.assertTrue(extractor.selectRecipe("rematter:deep_mineral_extraction"), "Failed to select deep_mineral_extraction");
        h.assertTrue(extractor.requestBatch(), "Failed to queue deep_mineral_extraction batch");

        var slurry = dev.latrox.rematter.chemistry.SubstanceKey.of("rematter:mineral_slurry", dev.latrox.rematter.chemistry.Phase.LIQUID);
        var brine = dev.latrox.rematter.chemistry.SubstanceKey.of("rematter:saline_brine", dev.latrox.rematter.chemistry.Phase.LIQUID);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue("requires_probe_target".equals(extractor.work().status),
                        "Cobblestone must be rejected with requires_probe_target, got " + extractor.work().status))
                .thenExecute(() -> {
                    h.setBlock(extPos.below(), Blocks.DEEPSLATE);
                    extractor.energy().set(extractor.energyCapacity());
                })
                .thenWaitUntil(() -> {
                    extractor.energy().set(extractor.energyCapacity());
                    var inv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
                    h.assertTrue(inv.amount(slurry) >= 250L,
                            "Deep-Core Extractor should extract >= 250 u mineral_slurry over Deepslate at y<=0, got "
                                    + inv.amount(slurry) + " (status=" + extractor.work().status + ")");
                })
                .thenExecute(() -> {
                    // Verify shallow 1-deep water is rejected for Brine Pumping, then 2-deep water source succeeds!
                    h.setBlock(extPos.below(), Blocks.WATER);
                    h.setBlock(extPos.below(2), Blocks.DEEPSLATE);
                    h.assertTrue(extractor.selectRecipe("rematter:brine_pumping"), "Failed to select brine_pumping");
                    h.assertTrue(extractor.requestBatch(), "Failed to queue brine_pumping");
                })
                .thenWaitUntil(() -> h.assertTrue("requires_probe_target".equals(extractor.work().status),
                        "1-deep water puddle must be rejected for brine_pumping, got " + extractor.work().status))
                .thenExecute(() -> {
                    h.setBlock(extPos.below(2), Blocks.WATER);
                    extractor.energy().set(extractor.energyCapacity());
                })
                .thenWaitUntil(() -> {
                    extractor.energy().set(extractor.energyCapacity());
                    var inv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
                    h.assertTrue(inv.amount(brine) >= 250L,
                            "Deep-Core Extractor should extract >= 250 u saline_brine over 2-deep water source, got "
                                    + inv.amount(brine) + " (status=" + extractor.work().status + ")");
                })
                .thenSucceed();
    }

    private static Chamber build5x5x5Reactor(GameTestHelper h, BlockPos origin, MachineKind kind,
                                             IndustrialModuleKind floorMod, IndustrialModuleKind ceilMod,
                                             int floorCount, int ceilCount, Player owner) {
        int size = 5;
        var casingBlock = ModContent.reactorCasing(kind);
        var glassBlock = ModContent.reactorGlass(kind);
        var portBlock = ModContent.machinePort(kind);
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    boolean boundary = dx == 0 || dx == size - 1 || dy == 0 || dy == size - 1 || dz == 0 || dz == size - 1;
                    if (!boundary) continue;
                    boolean edge = (dy == 0 || dy == size - 1) || ((dx == 0 || dx == size - 1) && (dz == 0 || dz == size - 1));
                    h.setBlock(origin.offset(dx, dy, dz), edge ? casingBlock : glassBlock);
                }
            }
        }
        var controller = placeMachine(h, origin.offset(2, 1, 0), kind, Direction.NORTH, owner);
        int placedFloor = 0, placedCeil = 0;
        for (int dx = 1; dx <= 3 && placedFloor < floorCount; dx++) {
            for (int dz = 1; dz <= 3 && placedFloor < floorCount; dz++) {
                h.setBlock(origin.offset(dx, 1, dz),
                        ModContent.INDUSTRIAL_MODULES.get(floorMod).get().defaultBlockState().setValue(IndustrialModuleBlock.SUSPENDED, false));
                placedFloor++;
            }
        }
        for (int dx = 1; dx <= 3 && placedCeil < ceilCount; dx++) {
            for (int dz = 1; dz <= 3 && placedCeil < ceilCount; dz++) {
                h.setBlock(origin.offset(dx, size - 2, dz),
                        ModContent.INDUSTRIAL_MODULES.get(ceilMod).get().defaultBlockState().setValue(IndustrialModuleBlock.SUSPENDED, true));
                placedCeil++;
            }
        }
        BlockPos powerPos = origin.offset(0, 1, 2);
        BlockPos matterPos = origin.offset(size - 1, 1, 2);
        h.setBlock(powerPos, portBlock);
        h.setBlock(matterPos, portBlock);
        var powerPort = h.getBlockEntity(powerPos, MachinePortBlockEntity.class);
        var matterPort = h.getBlockEntity(matterPos, MachinePortBlockEntity.class);
        powerPort.refreshNow();
        matterPort.refreshNow();
        return new Chamber(controller, powerPort, matterPort, powerPort, matterPort, powerPos, matterPos, powerPos, matterPos);
    }

    private static void transmutationReactor(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ResearchService.ledger(h.getLevel().getServer()).visit(owner.getUUID(), h.getLevel().dimension().identifier().toString());
        h.assertTrue(ResearchService.learn(h.getLevel(), owner.getUUID(), "minecraft:gold_ingot", LearningPath.ANALYSIS),
                "Owner failed to learn gold research");
        h.assertTrue(ResearchService.learn(h.getLevel(), owner.getUUID(), "minecraft:blaze_rod", LearningPath.ANALYSIS),
                "Owner failed to learn cinderium research");

        // Transmutation Reactor (5x5x5, 8 Field Coils + 4 Particle Emitters -> Field 12) transmuting
        // Tier 2 Gold (Particle Resonator OR backward-compatible Primal Matrix Core) and verifying Tier 3 requires Primal Matrix Core.
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2.0, 3.5)));
        Chamber trans = build5x5x5Reactor(h, new BlockPos(1, 1, 1), MachineKind.TRANSMUTATION_REACTOR,
                IndustrialModuleKind.FIELD_COIL, IndustrialModuleKind.PARTICLE_EMITTER, 8, 4, owner);
        h.assertTrue(trans.controller().industrialStructureReady(),
                "Transmutation Reactor failed to form: " + IndustrialStructure.inspect(trans.controller()).diagnostic());
        // Strict tier progression check: standard Reactor Casing must NOT satisfy Transmutation Reactor frame.
        h.setBlock(new BlockPos(1, 1, 1), ModContent.REACTOR_CASING.get());
        h.assertTrue(!trans.controller().industrialStructureReady(),
                "Transmutation Reactor must reject standard Reactor Casing under strict tier progression");
        h.setBlock(new BlockPos(1, 1, 1), ModContent.TRANSMUTATION_REACTOR_CASING.get());
        h.assertTrue(trans.controller().industrialStructureReady(),
                "Transmutation Reactor should reform after restoring Transmutation Reactor Casing");
        setBlockPortMode(trans.powerPort(), owner, Direction.WEST, MachinePortMode.POWER);
        setBlockPortMode(trans.matterPort(), owner, Direction.EAST, MachinePortMode.MATTER);

        var silver = dev.latrox.rematter.chemistry.SubstanceKey.of("rematter:silver", dev.latrox.rematter.chemistry.Phase.SOLID);
        var zinc = dev.latrox.rematter.chemistry.SubstanceKey.of("rematter:zinc", dev.latrox.rematter.chemistry.Phase.SOLID);
        var nitrogen = dev.latrox.rematter.chemistry.SubstanceKey.of("rematter:nitrogen", dev.latrox.rematter.chemistry.Phase.GAS);
        var sulfur = dev.latrox.rematter.chemistry.SubstanceKey.of("rematter:sulfur", dev.latrox.rematter.chemistry.Phase.SOLID);
        var titanium = dev.latrox.rematter.chemistry.SubstanceKey.of("rematter:titanium", dev.latrox.rematter.chemistry.Phase.SOLID);

        var transDrive = placeMachine(h, trans.matterPos().east(), MachineKind.DRIVE, Direction.NORTH, owner);
        transDrive.setItem(transDrive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_16M.get()));
        var transNet = transDrive.matterNetwork(true);
        h.assertTrue(Objects.requireNonNull(transNet.inventory()).apply(Map.of(),
                Map.of(silver, 6_000L, zinc, 2_000L, nitrogen, 5_000L, BuiltinChemistry.HYDROGEN, 5_000L,
                        BuiltinChemistry.OXYGEN, 3_000L, sulfur, 2_000L, titanium, 2_000L)), "Failed to seed Ag/Zn/S/Ti/N2/H2/O2");
        transNet.markChanged();

        trans.controller().setItem(2, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        trans.controller().energy().set(trans.controller().energyCapacity());
        h.assertTrue(trans.controller().selectRecipe("rematter:transmute_gold"), "Failed to select transmute_gold");
        h.assertTrue(trans.controller().requestBatch(), "Failed to queue transmute_gold");

        // 1. Tier 2 refuses without Focus Catalyst.
        // 2. Tier 2 succeeds with Particle Resonator (not consumed, zero byproducts).
        // 3. Tier 2 ALSO succeeds with Primal Matrix Core (backward compatibility!).
        // 4. Tier 3 (transmute_cinderium) refuses Particle Resonator (requires_catalyst) and succeeds with Primal Matrix Core!
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue("requires_catalyst".equals(trans.controller().work().status),
                        "Expected requires_catalyst without Particle Resonator, got " + trans.controller().work().status))
                .thenExecute(() -> {
                    trans.controller().setItem(MachineCellAccess.DISCOVERY_SLOT, new ItemStack(ModContent.PARTICLE_RESONATOR.get()));
                    trans.controller().energy().set(trans.controller().energyCapacity());
                })
                .thenWaitUntil(() -> {
                    trans.controller().energy().set(trans.controller().energyCapacity());
                    var inv = Objects.requireNonNull(transDrive.matterNetwork(true).inventory());
                    long gold = inv.amount(BuiltinChemistry.GOLD);
                    h.assertTrue(gold >= 1_000L,
                            "Expected >= 1,000 u Gold with Particle Resonator (got " + gold + ", status=" + trans.controller().work().status + ")");
                })
                .thenExecute(() -> {
                    h.assertTrue(trans.controller().getItem(MachineCellAccess.DISCOVERY_SLOT).is(ModContent.PARTICLE_RESONATOR.get()),
                            "Focus Catalyst must not be consumed");
                    var inv = Objects.requireNonNull(transDrive.matterNetwork(true).inventory());
                    h.assertTrue(inv.amount(silver) == 3_000L && inv.amount(zinc) == 1_000L
                            && inv.amount(nitrogen) == 3_500L && inv.amount(BuiltinChemistry.HYDROGEN) == 3_500L,
                            "Expected exact input consumption with zero extra outputs");
                    // Swap to Primal Matrix Core and run a second Tier 2 Gold batch -> must be backward-compatible!
                    trans.controller().setItem(MachineCellAccess.DISCOVERY_SLOT, new ItemStack(PrimalContent.PRIMAL_MATRIX_CORE.get()));
                    trans.controller().energy().set(trans.controller().energyCapacity());
                    h.assertTrue(trans.controller().requestBatch(), "Failed to queue second transmute_gold batch");
                })
                .thenWaitUntil(() -> {
                    trans.controller().energy().set(trans.controller().energyCapacity());
                    var inv = Objects.requireNonNull(transDrive.matterNetwork(true).inventory());
                    long gold = inv.amount(BuiltinChemistry.GOLD);
                    h.assertTrue(gold >= 2_000L,
                            "Primal Matrix Core must be backward-compatible for Tier 2 Gold (got " + gold + ", status=" + trans.controller().work().status + ")");
                })
                .thenExecute(() -> {
                    // Now test Tier 3 Cinderium: Particle Resonator must be rejected (requires_catalyst)!
                    trans.controller().setItem(MachineCellAccess.DISCOVERY_SLOT, new ItemStack(ModContent.PARTICLE_RESONATOR.get()));
                    h.assertTrue(trans.controller().selectRecipe("rematter:transmute_cinderium"), "Failed to select transmute_cinderium");
                    h.assertTrue(trans.controller().requestBatch(), "Failed to queue transmute_cinderium");
                })
                .thenWaitUntil(() -> h.assertTrue("requires_catalyst".equals(trans.controller().work().status),
                        "Tier 3 Cinderium must reject Particle Resonator with requires_catalyst, got " + trans.controller().work().status))
                .thenExecute(() -> {
                    trans.controller().setItem(MachineCellAccess.DISCOVERY_SLOT, new ItemStack(PrimalContent.PRIMAL_MATRIX_CORE.get()));
                    trans.controller().energy().set(trans.controller().energyCapacity());
                })
                .thenWaitUntil(() -> {
                    trans.controller().energy().set(trans.controller().energyCapacity());
                    var inv = Objects.requireNonNull(transDrive.matterNetwork(true).inventory());
                    long cinder = inv.amount(dev.latrox.rematter.chemistry.SubstanceKey.of("rematter:cinderium", dev.latrox.rematter.chemistry.Phase.SOLID));
                    h.assertTrue(cinder >= 1_000L,
                            "Tier 3 Cinderium must succeed with Primal Matrix Core (got " + cinder + ", status=" + trans.controller().work().status + ")");
                })
                .thenSucceed();
    }

    private static void primalReactor(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ResearchService.ledger(h.getLevel().getServer()).visit(owner.getUUID(), h.getLevel().dimension().identifier().toString());
        h.assertTrue(ResearchService.learn(h.getLevel(), owner.getUUID(), "minecraft:gold_ingot", LearningPath.ANALYSIS),
                "Owner failed to learn gold research");
        h.assertTrue(ResearchService.learn(h.getLevel(), owner.getUUID(), "minecraft:blaze_rod", LearningPath.ANALYSIS),
                "Owner failed to learn cinderium research");

        // Primal Reactor (5x5x5, 2 Particle Emitters + 2 Primal Condenser Cores + Singularity Seed + Mass Compression Upgrade)
        // Mode A: 3-phase Raw Mass Collapse (SiO2 + H2O + H2) + FE -> Primal Matter + Stability ramp.
        // Mode B: Automated Bulk Primal Manifestation (Ur + FE -> 4,000 u Gold).
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2.0, 3.5)));
        Chamber primal = build5x5x5Reactor(h, new BlockPos(1, 1, 1), MachineKind.PRIMAL_REACTOR,
                IndustrialModuleKind.PARTICLE_EMITTER, IndustrialModuleKind.PRIMAL_CONDENSER_CORE, 2, 2, owner);
        h.assertTrue(primal.controller().industrialStructureReady(),
                "Primal Reactor failed to form: " + IndustrialStructure.inspect(primal.controller()).diagnostic());
        // Strict tier progression check: Transmutation Casing must NOT satisfy Primal Reactor frame.
        h.setBlock(new BlockPos(1, 1, 1), ModContent.TRANSMUTATION_REACTOR_CASING.get());
        h.assertTrue(!primal.controller().industrialStructureReady(),
                "Primal Reactor must reject Transmutation Reactor Casing under strict tier progression");
        h.setBlock(new BlockPos(1, 1, 1), ModContent.PRIMAL_REACTOR_CASING.get());
        h.assertTrue(primal.controller().industrialStructureReady(),
                "Primal Reactor should reform after restoring Primal Reactor Casing");
        setBlockPortMode(primal.powerPort(), owner, Direction.WEST, MachinePortMode.POWER);
        setBlockPortMode(primal.matterPort(), owner, Direction.EAST, MachinePortMode.MATTER);

        var primalDrive = placeMachine(h, primal.matterPos().east(), MachineKind.DRIVE, Direction.NORTH, owner);
        primalDrive.setItem(primalDrive.cellSlotForBay(0), new ItemStack(PrimalContent.PRIMAL_CELL_4M.get()));
        primalDrive.setItem(primalDrive.cellSlotForBay(1), new ItemStack(ModContent.MATTER_CELL_16M.get()));
        var primalNet = primalDrive.matterNetwork(true);
        h.assertTrue(Objects.requireNonNull(primalNet.inventory()).apply(Map.of(),
                Map.of(BuiltinChemistry.SILICA, 40_000L, BuiltinChemistry.WATER, 15_000L, BuiltinChemistry.HYDROGEN, 15_000L)),
                "Failed to seed 3-phase collapse matter");
        primalNet.markChanged();

        primal.controller().setItem(2, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        primal.controller().setItem(MachineCellAccess.MASS_COMPRESSION_SLOT, new ItemStack(ModContent.MASS_COMPRESSION_UPGRADE.get(), 1));
        primal.controller().energy().set(primal.controller().energyCapacity());
        h.assertTrue(primal.controller().selectRecipe(PrimalReactorRecipes.COLLAPSE_RAW), "Failed to select primal_collapse_raw");
        primal.controller().toggleRepeat();

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue("requires_seed".equals(primal.controller().work().status),
                        "Expected requires_seed without Singularity Seed, got " + primal.controller().work().status))
                .thenExecute(() -> {
                    primal.controller().setItem(MachineCellAccess.DISCOVERY_SLOT, new ItemStack(PrimalContent.PRIMAL_MATRIX_CORE.get()));
                    primal.controller().energy().set(primal.controller().energyCapacity());
                })
                .thenWaitUntil(() -> {
                    primal.controller().energy().set(primal.controller().energyCapacity());
                    long primalStored = dev.latrox.rematter.primal.PrimalCells.view(primalDrive.getItem(primalDrive.cellSlotForBay(0))).amount();
                    h.assertTrue(primalStored >= 8_000L,
                            "Expected >= 8,000 u Primal Matter in 4M Primal Cell (got " + primalStored + ", status=" + primal.controller().work().status + ")");
                })
                .thenExecute(() -> {
                    long remainingSilica = Objects.requireNonNull(primalDrive.matterNetwork(true).inventory()).amount(BuiltinChemistry.SILICA);
                    h.assertTrue(remainingSilica < 20_000L,
                            "3-phase collapse must consume Silica, remaining: " + remainingSilica);
                    h.assertTrue(primal.controller().primalStability() > 0,
                            "Singularity stability should ramp up under continuous operation, got " + primal.controller().primalStability());
                    // Switch to Mode B: automated bulk Primal Manifestation of Gold (4,000 u Gold per batch)
                    h.assertTrue(primal.controller().selectRecipe(PrimalReactorRecipes.MANIFEST_PREFIX + "rematter:gold"),
                            "Failed to select Gold manifestation");
                    if (!primal.controller().running()) primal.controller().toggleRepeat();
                })
                .thenWaitUntil(() -> {
                    primal.controller().energy().set(primal.controller().energyCapacity());
                    long gold = Objects.requireNonNull(primalDrive.matterNetwork(true).inventory()).amount(BuiltinChemistry.GOLD);
                    h.assertTrue(gold >= PrimalReactorRecipes.MANIFEST_BATCH,
                            "Expected >= 4,000 u manifested Gold (got " + gold + ", status=" + primal.controller().work().status + ")");
                })
                .thenSucceed();
    }

    private static void energyMatrixAndConnectedGlass(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2.0, 3.5)));
        BlockPos origin = new BlockPos(1, 1, 1);
        int size = 5;
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    boolean boundary = dx == 0 || dx == size - 1 || dy == 0 || dy == size - 1 || dz == 0 || dz == size - 1;
                    if (!boundary) continue;
                    boolean edge = (dy == 0 || dy == size - 1) || ((dx == 0 || dx == size - 1) && (dz == 0 || dz == size - 1));
                    h.setBlock(origin.offset(dx, dy, dz), edge ? ModContent.ENERGY_MATRIX_CASING.get() : ModContent.ENERGY_MATRIX_GLASS.get());
                }
            }
        }
        var controller = placeMachine(h, origin.offset(2, 1, 0), MachineKind.ENERGY_MATRIX, Direction.NORTH, owner);

        // Stack mixed-tier Cells and Providers freely in 3D inside the hollow 3x3x3 cavity!
        h.setBlock(origin.offset(1, 1, 1), ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.BASIC).get());
        h.setBlock(origin.offset(1, 2, 1), ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.REINFORCED).get());
        h.setBlock(origin.offset(1, 3, 1), ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.RESONANT).get());
        h.setBlock(origin.offset(2, 2, 2), ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.PRIMAL).get());
        h.setBlock(origin.offset(3, 1, 1), ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.BASIC).get());
        h.setBlock(origin.offset(3, 2, 1), ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.RESONANT).get());

        // One side Power Port + one roof Power Port (Energy Matrix allows ports on any wall face including top/bottom).
        BlockPos powerPos = origin.offset(0, 1, 2);
        BlockPos topPortPos = origin.offset(2, size - 1, 2);
        h.setBlock(powerPos, ModContent.ENERGY_MATRIX_PORT.get());
        h.setBlock(topPortPos, ModContent.ENERGY_MATRIX_PORT.get());
        var powerPort = h.getBlockEntity(powerPos, MachinePortBlockEntity.class);
        var topPort = h.getBlockEntity(topPortPos, MachinePortBlockEntity.class);
        powerPort.refreshNow();
        topPort.refreshNow();

        h.assertTrue(controller.industrialStructureReady(),
                "Energy Matrix failed to form: " + IndustrialStructure.inspect(controller).diagnostic());
        h.assertTrue(ModSounds.workingSound(MachineKind.ENERGY_MATRIX) == null && ModSounds.accentSound(MachineKind.ENERGY_MATRIX) == null,
                "Energy Matrix must be completely silent");
        long expectedCap = 64_000_000L + 512_000_000L + 4_096_000_000L + 32_768_000_000L; // 37.44G FE
        long expectedRate = 262_144L + 4_194_304L; // 4,456,448 FE/t
        h.assertTrue(controller.matrixCapacity() == expectedCap,
                "Expected " + expectedCap + " FE mixed-tier matrix capacity, got " + controller.matrixCapacity());
        h.assertTrue(controller.matrixTransferRate() == expectedRate,
                "Expected " + expectedRate + " FE/t mixed-tier transfer rate, got " + controller.matrixTransferRate());

        // Verify Connected Glass FORMED property + strict shell separation (Primal Reactor Casing MUST NOT form Energy Matrix!)
        controller.serverTick();
        BlockPos glassPos = origin.offset(1, 2, 0);
        h.assertTrue(h.getBlockState(glassPos).getValue(ReactorGlassBlock.FORMED),
                "Side glass on formed Energy Matrix must have FORMED=true");
        h.setBlock(origin, ModContent.PRIMAL_REACTOR_CASING.get());
        controller.serverTick();
        h.assertTrue(!controller.industrialStructureReady(),
                "Energy Matrix must reject Primal Reactor Casing under strict shell separation");
        h.assertTrue(!h.getBlockState(glassPos).getValue(ReactorGlassBlock.FORMED),
                "Deformed Energy Matrix glass must reset FORMED=false");
        h.setBlock(origin, ModContent.ENERGY_MATRIX_CASING.get());
        controller.serverTick();
        h.assertTrue(controller.industrialStructureReady(), "Restoring Energy Matrix Casing must reform Energy Matrix");
        h.assertTrue(h.getBlockState(glassPos).getValue(ReactorGlassBlock.FORMED),
                "Reformed Energy Matrix glass must restore FORMED=true");

        // Mixed Glass + Casing on Energy Matrix roof: open spot (2,4,1) accepts Glass with FORMED=true,
        // while replacing Casing directly over ceiling Cell (1,3,1) at (1,4,1) with Glass must deform!
        BlockPos openRoofGlass = origin.offset(2, size - 1, 1);
        BlockPos cellRoofSpot = origin.offset(1, size - 1, 1);
        h.setBlock(openRoofGlass, ModContent.ENERGY_MATRIX_GLASS.get());
        controller.serverTick();
        h.assertTrue(controller.industrialStructureReady(),
                "Energy Matrix must allow Glass on open roof spot without directly touching a Cell/Provider");
        h.assertTrue(h.getBlockState(openRoofGlass).getValue(ReactorGlassBlock.FORMED),
                "Open roof Glass on formed Energy Matrix must immediately receive FORMED=true");
        h.setBlock(cellRoofSpot, ModContent.ENERGY_MATRIX_GLASS.get());
        controller.serverTick();
        h.assertTrue(!controller.industrialStructureReady(),
                "Energy Matrix must reject Glass directly above a Cell touching the roof");
        h.setBlock(cellRoofSpot, ModContent.ENERGY_MATRIX_CASING.get());
        controller.serverTick();
        h.assertTrue(controller.industrialStructureReady(),
                "Restoring Casing directly above roof-touching Cell must reform Energy Matrix");
        h.assertTrue(h.getBlockState(openRoofGlass).getValue(ReactorGlassBlock.FORMED),
                "Reformed Energy Matrix must restore FORMED=true on mixed roof Glass");

        setBlockPortMode(powerPort, owner, Direction.WEST, MachinePortMode.POWER);
        // Verify exact per-tick shared transfer rate limit (4,456,448 FE/t) on the real Power Port
        var energyCap = h.getLevel().getCapability(Capabilities.Energy.BLOCK, h.absolutePos(powerPos), Direction.WEST);
        h.assertTrue(energyCap != null, "Energy Matrix POWER port must expose FE capability");
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(energyCap.insert(4_000_000, tx) == 4_000_000, "First 4M FE insert failed");
            h.assertTrue(energyCap.insert(4_000_000, tx) == 456_448, "Second insert did not clamp to exact 4,456,448 FE/t cap");
            h.assertTrue(energyCap.insert(1, tx) == 0, "Third insert bypassed exhausted per-tick rate cap");
            tx.commit();
        }
        h.assertTrue(controller.matrixEnergy() == expectedRate,
                "Expected " + expectedRate + " FE after first tick cap, got " + controller.matrixEnergy());

        // Verify 64-bit NBT persistence > Integer.MAX_VALUE (2.4G FE) + live port insertion above 2^31-1 + item charging
        var registries = h.getLevel().registryAccess();
        var saved = controller.saveCustomOnly(registries);
        saved.putLong("matrix_energy", 2_400_000_000L);
        controller.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, registries, saved));
        h.assertTrue(controller.matrixEnergy() == 2_400_000_000L,
                "Expected exact 64-bit 2.4G FE (> Integer.MAX_VALUE), got " + controller.matrixEnergy());

        // Insert empty Portable Energy Cell into Charge slot (1) and verify next-tick port insert + item charging!
        controller.setItem(1, new ItemStack(ModContent.PORTABLE_ENERGY_CELL.get()));
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    try (var tx = Transaction.openRoot()) {
                        int added = energyCap.insert(1_000_000, tx);
                        h.assertTrue(added == 1_000_000, "Expected 1M FE insert above Integer.MAX_VALUE, got " + added);
                        tx.commit();
                    }
                    h.assertTrue(controller.matrixEnergy() > 2_380_000_000L,
                            "64-bit matrix energy corrupted after >2^31 port insert: " + controller.matrixEnergy());
                })
                .thenWaitUntil(() -> {
                    ItemStack cell = controller.getItem(1);
                    int stored = PortableEnergyCellItem.energy(cell);
                    h.assertTrue(stored >= 1_000_000,
                            "Expected Portable Energy Cell in Matrix charge slot to charge >= 1M FE, got " + stored);
                })
                .thenSucceed();
    }

    private static void energyReactorModesCoolantAndMatrixFeed(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        owner.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2.0, 3.5)));

        // 1. Build 5x5x5 Energy Reactor at (1, 1, 1) with 2 Reactor Fuel Cores + 2 Reactor Induction Coils
        BlockPos reactorOrigin = new BlockPos(1, 1, 1);
        Chamber reactor = build5x5x5Reactor(h, reactorOrigin, MachineKind.ENERGY_REACTOR,
                IndustrialModuleKind.REACTOR_FUEL_CORE, IndustrialModuleKind.REACTOR_INDUCTION_COIL, 2, 2, owner);
        h.assertTrue(reactor.controller().industrialStructureReady(),
                "Energy Reactor failed to form: " + IndustrialStructure.inspect(reactor.controller()).diagnostic());
        h.assertTrue(ModSounds.workingSound(MachineKind.ENERGY_REACTOR) != null,
                "Energy Reactor must expose its dedicated sound event");
        h.assertTrue(reactor.controller().reactorCapacity() == 1_024_000_000L,
                "Expected 1.024G FE buffer for 2 Fuel Cores, got " + reactor.controller().reactorCapacity());
        h.assertTrue(reactor.controller().reactorMaxOutputRate() == 2_097_152L,
                "Expected 2,097,152 FE/t output rate for 2 Induction Coils, got " + reactor.controller().reactorMaxOutputRate());

        // Strict shell separation: Energy Matrix Casing must NOT satisfy Energy Reactor shell!
        reactor.controller().serverTick();
        BlockPos reactorGlassPos = reactorOrigin.offset(1, 2, 0);
        h.assertTrue(h.getBlockState(reactorGlassPos).getValue(ReactorGlassBlock.FORMED),
                "Formed Energy Reactor glass must have FORMED=true");
        h.setBlock(reactorOrigin, ModContent.ENERGY_MATRIX_CASING.get());
        reactor.controller().serverTick();
        h.assertTrue(!reactor.controller().industrialStructureReady(),
                "Energy Reactor must reject Energy Matrix Casing");
        h.setBlock(reactorOrigin, ModContent.ENERGY_REACTOR_CASING.get());
        reactor.controller().serverTick();
        h.assertTrue(reactor.controller().industrialStructureReady(),
                "Restoring Energy Reactor Casing must reform Energy Reactor");

        // Configure West port as MATTER (connected to Matter Drive) and East port as POWER_OUTPUT (feeding adjacent Energy Matrix!)
        setBlockPortMode(reactor.powerPort(), owner, Direction.WEST, MachinePortMode.MATTER);
        setBlockPortMode(reactor.matterPort(), owner, Direction.EAST, MachinePortMode.POWER_OUTPUT);

        var drive = placeMachine(h, reactor.powerPos().west(), MachineKind.DRIVE, Direction.NORTH, owner);
        var primalCell = new ItemStack(PrimalContent.PRIMAL_CELL_4M.get());
        ItemStack[] cellHolder = {primalCell};
        var journal = new dev.latrox.rematter.primal.PrimalCells.Journal(() -> cellHolder[0]);
        try (var tx = Transaction.openRoot()) {
            dev.latrox.rematter.primal.PrimalCells.credit(journal, 500_000L, tx);
            tx.commit();
        }
        drive.setItem(drive.cellSlotForBay(0), cellHolder[0]);
        drive.setItem(drive.cellSlotForBay(1), new ItemStack(ModContent.MATTER_CELL_16M.get()));
        var matterNet = drive.matterNetwork(true);
        var voidGas = dev.latrox.rematter.chemistry.SubstanceKey.of("rematter:void_condensate", dev.latrox.rematter.chemistry.Phase.GAS);
        h.assertTrue(Objects.requireNonNull(matterNet.inventory()).apply(Map.of(),
                Map.of(voidGas, 20_000L, BuiltinChemistry.WATER, 20_000L)),
                "Failed to seed void_condensate + water coolant");
        matterNet.markChanged();

        // 2. Build an adjacent 4x3x3 Energy Matrix directly touching Reactor's East POWER port at (6, 2, 3)!
        BlockPos matrixOrigin = new BlockPos(6, 1, 2);
        for (int dx = 0; dx < 4; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                for (int dz = 0; dz < 3; dz++) {
                    boolean boundary = dx == 0 || dx == 3 || dy == 0 || dy == 2 || dz == 0 || dz == 2;
                    if (!boundary) continue;
                    boolean edge = (dy == 0 || dy == 2) || ((dx == 0 || dx == 3) && (dz == 0 || dz == 2));
                    h.setBlock(matrixOrigin.offset(dx, dy, dz), edge ? ModContent.ENERGY_MATRIX_CASING.get() : ModContent.ENERGY_MATRIX_GLASS.get());
                }
            }
        }
        var matrixCtrl = placeMachine(h, matrixOrigin.offset(1, 1, 0), MachineKind.ENERGY_MATRIX, Direction.NORTH, owner);
        h.setBlock(matrixOrigin.offset(1, 1, 1), ModContent.ENERGY_MATRIX_CELLS.get(dev.latrox.rematter.energy.EnergyMatrixCellTier.RESONANT).get());
        h.setBlock(matrixOrigin.offset(2, 1, 1), ModContent.ENERGY_MATRIX_PROVIDERS.get(dev.latrox.rematter.energy.EnergyMatrixProviderTier.RESONANT).get());
        BlockPos matrixInPortPos = matrixOrigin.offset(0, 1, 1); // (6, 2, 3) == reactor.matterPos().east()!
        BlockPos matrixOutPortPos = matrixOrigin.offset(3, 1, 1);
        h.setBlock(matrixInPortPos, ModContent.ENERGY_MATRIX_PORT.get());
        h.setBlock(matrixOutPortPos, ModContent.ENERGY_MATRIX_PORT.get());
        var matrixInPort = h.getBlockEntity(matrixInPortPos, MachinePortBlockEntity.class);
        var matrixOutPort = h.getBlockEntity(matrixOutPortPos, MachinePortBlockEntity.class);
        matrixInPort.refreshNow();
        matrixOutPort.refreshNow();
        setBlockPortMode(matrixInPort, owner, Direction.WEST, MachinePortMode.POWER);
        setBlockPortMode(matrixOutPort, owner, Direction.EAST, MachinePortMode.POWER);
        h.assertTrue(matrixCtrl.industrialStructureReady(),
                "Adjacent Energy Matrix failed to form: " + IndustrialStructure.inspect(matrixCtrl).diagnostic());

        // 3. Seed Crystal Fuel in Slot 0 (mode defaults to CRYSTAL) and test Crystal -> Matter+Coolant -> Primal -> Safe Pause!
        var emberCrystal = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, "ember_matrix_crystal"));
        reactor.controller().setItem(0, new ItemStack(emberCrystal, 64));

        long[] afterCrystal = {0L};
        long[] afterMatter = {0L};
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(matrixCtrl.matrixEnergy() >= 50_000L && reactor.controller().reactorFlux() > 0,
                            "Expected Crystal mode to ramp flux and push >= 50k FE into Energy Matrix (matrixFe="
                                    + matrixCtrl.matrixEnergy() + ", flux=" + reactor.controller().reactorFlux()
                                    + ", status=" + reactor.controller().work().status + ")");
                })
                .thenExecute(() -> {
                    afterCrystal[0] = matrixCtrl.matrixEnergy();
                    // Switch to MATTER mode (draws void_condensate + water Coolant Overdrive!)
                    h.assertTrue(reactor.controller().extensions().action(owner,
                            new dev.latrox.rematter.network.MachineActionPayload(0,
                                    dev.latrox.rematter.network.MachineActionPayload.Action.SET_REACTOR_FUEL_MODE, "matter", 0)),
                            "Failed to switch Energy Reactor to MATTER mode");
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(reactor.controller().reactorCooled()
                                    && matrixCtrl.matrixEnergy() >= afterCrystal[0] + 500_000L,
                            "Expected Matter + Coolant Overdrive to deliver +500k FE (cooled="
                                    + reactor.controller().reactorCooled() + ", matrixFe=" + matrixCtrl.matrixEnergy()
                                    + ", status=" + reactor.controller().work().status + ")");
                })
                .thenExecute(() -> {
                    afterMatter[0] = matrixCtrl.matrixEnergy();
                    // Switch to PRIMAL mode (draws 8 Primal/t/core from connected Matter Drive's 4M Primal Cell!)
                    h.assertTrue(reactor.controller().extensions().action(owner,
                            new dev.latrox.rematter.network.MachineActionPayload(0,
                                    dev.latrox.rematter.network.MachineActionPayload.Action.SET_REACTOR_FUEL_MODE, "primal", 0)),
                            "Failed to switch Energy Reactor to PRIMAL mode");
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(matrixCtrl.matrixEnergy() >= afterMatter[0] + 1_000_000L,
                            "Expected Primal mode to push +1M FE into Energy Matrix (matrixFe="
                                    + matrixCtrl.matrixEnergy() + ", status=" + reactor.controller().work().status + ")");
                })
                .thenExecute(() -> {
                    // Pause via Run/Pause toggle -> must safely throttle to "paused" with zero explosion
                    reactor.controller().toggleRunning();
                })
                .thenWaitUntil(() -> {
                    h.assertTrue("paused".equals(reactor.controller().work().status)
                                    && reactor.controller().industrialStructureReady(),
                            "Expected safe paused auto-throttle while staying formed, got status="
                                    + reactor.controller().work().status);
                })
                .thenSucceed();
    }
}
