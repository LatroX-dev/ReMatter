package dev.latrox.rematter.content;

import dev.latrox.rematter.celltransfer.CellTransferStationBlockEntity;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.ReservedSubstances;
import dev.latrox.rematter.compat.CrystalFuelDisplay;
import dev.latrox.rematter.content.generation.CrystalFuelData;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeTier;
import dev.latrox.rematter.network.TerminalMonitorPayload;
import dev.latrox.rematter.primal.PrimalCellData;
import dev.latrox.rematter.primal.PrimalContent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Server GameTests for 4M/1M/Void cells, Portable Energy Cell, Matter Level Emitter, Crystal Fuel displays, Machine Sound, Pressurized Canister, Cell Filters, Wrench HUD, and Terminal Monitor. */
@EventBusSubscriber(modid = "rematter")
public final class StorageAndGadgetsGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static {
        TESTS.put("canister_deposit_separates_stored_and_voided_units", StorageAndGadgetsGameTests::canisterOverflowReceipt);
        TESTS.put("endgame_and_void_cells_fill_finite_first_and_discard_overflow", StorageAndGadgetsGameTests::endgameAndVoidCells);
        TESTS.put("portable_energy_cell_docks_and_recharges_inventory_without_battery_ping_pong", StorageAndGadgetsGameTests::portableEnergyCell);
        TESTS.put("matter_level_emitter_tracks_ordinary_and_primal_stock_with_directional_redstone", StorageAndGadgetsGameTests::matterLevelEmitter);
        TESTS.put("machine_sound_mute_and_crystal_fuel_displays_behave_consistently", StorageAndGadgetsGameTests::soundMuteAndCrystalFuelDisplays);
        TESTS.put("industrial_controller_blueprint_and_preview_toggle_sync_and_copy", StorageAndGadgetsGameTests::industrialBlueprintAndPreviewToggle);
        TESTS.put("pressurized_canister_transfers_liquid_and_gas_matter_and_fluids_without_mixing", StorageAndGadgetsGameTests::pressurizedCanisterTransfers);
        TESTS.put("configuration_card_stamps_cell_filters_across_offhand_station_and_drive_bays", StorageAndGadgetsGameTests::configurationCardCellFilters);
        TESTS.put("wrench_diagnostics_and_terminal_monitor_enforce_ownership_and_track_rates", StorageAndGadgetsGameTests::wrenchDiagnosticsAndTerminalMonitor);
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("rematter_tests", path); }

    @SubscribeEvent
    public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach((name, fn) -> helper.register(id(name), fn)));
    }

    @SubscribeEvent
    public static void tests(RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(id("storage_gadgets_environment"));
        TESTS.forEach((name, fn) -> event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                new TestData<>(env, id("empty"), 400, 0, true, Rotation.NONE, false, 1, 1, true, 2))));
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

    private static void endgameAndVoidCells(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var drive = placeMachine(h, new BlockPos(2, 2, 2), MachineKind.DRIVE, Direction.NORTH, owner);

        // Mount 4M Matter Cell and 1M Primal Cell and verify capacities.
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        drive.setItem(drive.cellSlotForBay(1), new ItemStack(PrimalContent.PRIMAL_CELL_1M.get()));
        var net = drive.matterNetwork(true);
        var inv = Objects.requireNonNull(net.inventory(), "Drive with 4M cell should expose network inventory");
        h.assertTrue(inv.capacity() == MatterLimits.CELL_4M, "Expected 4M ordinary capacity, got " + inv.capacity());
        h.assertTrue(drive.drivePrimalCapacity() == 1_000_000L, "Expected 1M primal capacity, got " + drive.drivePrimalCapacity());

        // Replace with 64k cell + partitioned Void Matter Cell (COPPER only): finite fills first, excess COPPER voids, IRON overflow is rejected.
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_64K.get()));
        ItemStack voidCell = new ItemStack(ModContent.VOID_MATTER_CELL.get());
        voidCell.set(CellPartitionData.TYPE.get(), CellPartitionData.allowOnly(Set.of(BuiltinChemistry.COPPER)));
        drive.setItem(drive.cellSlotForBay(1), voidCell);

        var voidNet = drive.matterNetwork(true);
        var voidInv = Objects.requireNonNull(voidNet.inventory(), "Drive with 64k + void cell should expose network inventory");
        h.assertTrue(voidInv.apply(Map.of(), Map.of(BuiltinChemistry.COPPER, 70_000L)),
                "Partitioned void cell failed to absorb overflow COPPER after filling 64k cell");
        voidNet.markChanged();
        h.assertTrue(voidInv.amount(BuiltinChemistry.COPPER) == MatterLimits.CELL_64K,
                "64k cell should hold exact 64,000 COPPER after 70,000 insertion, got " + voidInv.amount(BuiltinChemistry.COPPER));
        h.assertTrue(!voidInv.canApply(Map.of(), Map.of(BuiltinChemistry.IRON, 1L)),
                "Partitioned void cell must not accept unpartitioned IRON once finite space is full");
        h.succeed();
    }

    private static void canisterOverflowReceipt(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var drive = placeMachine(h, new BlockPos(2, 2, 2), MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_64K.get()));
        ItemStack sink = new ItemStack(ModContent.VOID_MATTER_CELL.get());
        sink.set(CellPartitionData.TYPE.get(), CellPartitionData.allowOnly(Set.of(BuiltinChemistry.OXYGEN)));
        drive.setItem(drive.cellSlotForBay(1), sink);
        var network = drive.matterNetwork(true);
        var stock = Objects.requireNonNull(network.inventory());
        h.assertTrue(stock.apply(Map.of(), Map.of(BuiltinChemistry.OXYGEN, 63_250L)), "Seed finite storage");
        var partial = PressurizedCanisterItem.depositWithReceipt(drive, owner, BuiltinChemistry.OXYGEN, 1_000);
        h.assertTrue(partial.stored() == 750 && partial.voided() == 250, "Local deposit must report both quantities");
        var overflow = PressurizedCanisterItem.depositIntoNetwork(network, drive, BuiltinChemistry.OXYGEN, 1_000);
        h.assertTrue(overflow.stored() == 0 && overflow.voided() == 1_000, "Network deposit is fully voided");
        var rejected = PressurizedCanisterItem.depositIntoNetwork(network, null, BuiltinChemistry.HYDROGEN, 1_000);
        h.assertTrue(rejected.accepted() == 0 && stock.amount(BuiltinChemistry.OXYGEN) == 64_000,
                "Overflow filter and finite stock must agree with receipts");
        h.succeed();
    }

    private static void portableEnergyCell(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        h.setBlock(new BlockPos(2, 2, 2), ModContent.ENERGY_CUBES.get(EnergyCubeTier.RESONANT).get());
        var cube = h.getBlockEntity(new BlockPos(2, 2, 2), EnergyCubeBlockEntity.class);
        cube.placedBy(owner);
        try (var tx = Transaction.openRoot()) {
            Objects.requireNonNull(cube.sidedEnergy(Direction.UP)).insert(65_536, tx);
            tx.commit();
        }

        // Charge Portable Energy Cell in Energy Cube charge slot 0.
        ItemStack pocket = new ItemStack(ModContent.PORTABLE_ENERGY_CELL.get());
        cube.setItem(0, pocket);
        cube.serverTick();
        h.assertTrue(PortableEnergyCellItem.energy(cube.getItem(0)) == PortableEnergyCellItem.MAX_TRANSFER,
                "Portable Energy Cell did not charge at its 16,000 FE transfer rate in Energy Cube");

        // Discharge Portable Energy Cell in BASIC Energy Cube discharge slot (slot 1) into an empty cube.
        h.setBlock(new BlockPos(4, 2, 2), ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get());
        var emptyCube = h.getBlockEntity(new BlockPos(4, 2, 2), EnergyCubeBlockEntity.class);
        emptyCube.placedBy(owner);
        int dischargeSlot = emptyCube.tier().chargingSlots();
        ItemStack charged = PortableEnergyCellItem.createCharged();
        emptyCube.setItem(dischargeSlot, charged);
        emptyCube.serverTick();
        h.assertTrue(emptyCube.energy() > 0 && PortableEnergyCellItem.energy(emptyCube.getItem(dischargeSlot)) < PortableEnergyCellItem.CAPACITY,
                "Portable Energy Cell did not discharge into Energy Cube discharge slot");

        // Inventory auto-charge recharges Wireless Matter Terminal while skipping other Portable Energy Cells and EnergyCubeItems.
        ItemStack activeBattery = PortableEnergyCellItem.createCharged();
        PortableEnergyCellItem.setAutoCharge(activeBattery, true);
        ItemStack terminal = new ItemStack(ModContent.WIRELESS_MATTER_TERMINAL.get());
        ItemStack secondBattery = new ItemStack(ModContent.PORTABLE_ENERGY_CELL.get());
        ItemStack cubeItem = new ItemStack(ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get());

        owner.getInventory().setItem(0, activeBattery);
        owner.getInventory().setItem(1, terminal);
        owner.getInventory().setItem(2, secondBattery);
        owner.getInventory().setItem(3, cubeItem);

        int sent = PortableEnergyCellItem.chargeInventoryItems(activeBattery, owner.getInventory());
        h.assertTrue(sent > 0 && WirelessMatterTerminalItem.energy(terminal) == sent,
                "Portable Energy Cell did not recharge Wireless Matter Terminal in inventory");
        h.assertTrue(PortableEnergyCellItem.energy(secondBattery) == 0,
                "Portable Energy Cell must never ping-pong energy into another Portable Energy Cell");
        h.succeed();
    }

    private static void matterLevelEmitter(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var primalKey = new dev.latrox.rematter.chemistry.SubstanceKey(ReservedSubstances.PRIMAL_MATTER, dev.latrox.rematter.chemistry.Phase.SOLID);
        BlockPos driveRel = new BlockPos(2, 2, 4);
        BlockPos cableRel = new BlockPos(2, 2, 3);
        BlockPos emitterRel = new BlockPos(2, 2, 2);
        BlockPos copyEmitterRel = new BlockPos(3, 2, 2);

        var drive = placeMachine(h, driveRel, MachineKind.DRIVE, Direction.SOUTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        ItemStack primalCell = new ItemStack(PrimalContent.PRIMAL_CELL_1M.get());
        primalCell.set(PrimalCellData.TYPE.get(), PrimalCellData.from(new PrimalCellData.State(UUID.randomUUID(), 2_500L, 0L, Map.of())));
        drive.setItem(drive.cellSlotForBay(1), primalCell);

        h.setBlock(cableRel, ModContent.MATTER_CABLE.get());
        CableOwners.placedBy(h.getLevel(), h.absolutePos(cableRel), owner.getUUID());
        h.setBlock(emitterRel, ModContent.MATTER_LEVEL_EMITTER.get().defaultBlockState()
                .setValue(MatterLevelEmitterBlock.FACING, Direction.NORTH));
        var emitter = h.getBlockEntity(emitterRel, MatterLevelEmitterBlockEntity.class);
        emitter.placedBy(owner);

        var inv = Objects.requireNonNull(drive.matterNetwork(true).inventory());
        inv.apply(Map.of(), Map.of(BuiltinChemistry.COPPER, 400L));
        drive.matterNetwork(true).markChanged();

        emitter.configure(BuiltinChemistry.COPPER, 500L, false);
        emitter.serverTick();
        h.assertTrue(!emitter.emitting(), "Emitter should be off when COPPER (400) < 500 in >= mode");
        h.assertTrue(h.getBlockState(emitterRel).getSignal(h.getLevel(), h.absolutePos(emitterRel), Direction.SOUTH) == 0,
                "Inactive emitter must output 0 Redstone out the front");

        inv.apply(Map.of(), Map.of(BuiltinChemistry.COPPER, 200L));
        drive.matterNetwork(true).markChanged();
        emitter.serverTick();
        h.assertTrue(emitter.emitting(), "Emitter should turn on when COPPER (600) >= 500");
        // Note: Minecraft queries getSignal(..., directionFromNeighborToEmitter); block at NORTH (z-1) queries with Direction.SOUTH!
        h.assertTrue(h.getBlockState(emitterRel).getSignal(h.getLevel(), h.absolutePos(emitterRel), Direction.SOUTH) == 15,
                "Active emitter facing NORTH must output Redstone 15 to its NORTH front target");
        h.assertTrue(h.getBlockState(emitterRel).getSignal(h.getLevel(), h.absolutePos(emitterRel), Direction.WEST) == 0,
                "Active emitter must not leak Redstone out of its side faces");

        // Switch to < mode: 600 < 500 is false, so it turns off; extracting 200 -> 400 < 500 turns it back on.
        emitter.configure(BuiltinChemistry.COPPER, 500L, true);
        emitter.serverTick();
        h.assertTrue(!emitter.emitting(), "Emitter should turn off when 600 is not < 500");
        inv.apply(Map.of(BuiltinChemistry.COPPER, 200L), Map.of());
        drive.matterNetwork(true).markChanged();
        emitter.serverTick();
        h.assertTrue(emitter.emitting(), "Emitter should turn on when COPPER (400) < 500");

        // Monitor Primal Matter directly from the 1M Primal Cell.
        emitter.configure(primalKey, 2_000L, false);
        emitter.serverTick();
        h.assertTrue(emitter.emitting() && emitter.observedStock() == 2_500L,
                "Emitter failed to observe 2,500 Primal Matter from 1M Primal Cell");

        // Copy & paste Level Emitter setup via Configuration Card.
        h.setBlock(copyEmitterRel, ModContent.MATTER_LEVEL_EMITTER.get().defaultBlockState()
                .setValue(MatterLevelEmitterBlock.FACING, Direction.NORTH));
        var copyEmitter = h.getBlockEntity(copyEmitterRel, MatterLevelEmitterBlockEntity.class);
        copyEmitter.placedBy(owner);
        BlockPos absPos = h.absolutePos(emitterRel);
        owner.setPos(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5);

        ItemStack card = new ItemStack(ModContent.CONFIGURATION_CARD.get());
        owner.setShiftKeyDown(true);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), absPos, card, hit(h, emitterRel, Direction.UP)).consumesAction(),
                "Configuration Card failed to copy Matter Level Emitter setup");
        owner.setShiftKeyDown(false);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), h.absolutePos(copyEmitterRel), card, hit(h, copyEmitterRel, Direction.UP)).consumesAction(),
                "Configuration Card failed to paste Matter Level Emitter setup");
        h.assertTrue(primalKey.equals(copyEmitter.target()) && copyEmitter.threshold() == 2_000L && !copyEmitter.emitWhenBelow(),
                "Pasted Matter Level Emitter setup did not match source");
        h.succeed();
    }

    private static void soundMuteAndCrystalFuelDisplays(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos srcRel = new BlockPos(2, 2, 2);
        BlockPos dstRel = new BlockPos(4, 2, 2);
        var src = placeMachine(h, srcRel, MachineKind.DECOMPOSER, Direction.NORTH, owner);
        var dst = placeMachine(h, dstRel, MachineKind.DECOMPOSER, Direction.NORTH, owner);

        h.assertTrue(src.soundLevel() == MachineSoundLevel.HIGH && !src.soundMuted(),
                "Machine should default to HIGH (100%) volume");
        src.cycleSoundLevel(true);
        h.assertTrue(src.soundLevel() == MachineSoundLevel.MEDIUM && src.soundGain() == 0.65f,
                "First forward step should set MEDIUM (65%)");
        src.cycleSoundLevel(true);
        h.assertTrue(src.soundLevel() == MachineSoundLevel.LOW && src.soundGain() == 0.35f,
                "Second forward step should set LOW (35%)");
        src.cycleSoundLevel(true);
        h.assertTrue(src.soundLevel() == MachineSoundLevel.MUTED && src.soundMuted(),
                "Third forward step should set MUTED");
        src.cycleSoundLevel(false);
        h.assertTrue(src.soundLevel() == MachineSoundLevel.LOW && !src.soundMuted(),
                "Reverse step from MUTED should restore LOW (35%)");

        BlockPos absSrc = h.absolutePos(srcRel);
        owner.setPos(absSrc.getX() + 0.5, absSrc.getY(), absSrc.getZ() + 0.5);
        ItemStack card = new ItemStack(ModContent.CONFIGURATION_CARD.get());
        owner.setShiftKeyDown(true);
        ConfigurationCardInteractions.interact(owner, h.getLevel(), absSrc, card, hit(h, srcRel, Direction.UP));
        owner.setShiftKeyDown(false);
        ConfigurationCardInteractions.interact(owner, h.getLevel(), h.absolutePos(dstRel), card, hit(h, dstRel, Direction.UP));
        h.assertTrue(dst.soundLevel() == MachineSoundLevel.LOW,
                "Configuration Card should copy intermediate LOW sound volume to compatible machine");

        // Verify CrystalFuelDisplay produces 18 entries (9 fuels x 2 generator tiers) with ascending distinct yields and 4x industrial speed.
        var fuels = CrystalFuelData.fuels();
        h.assertTrue(fuels.size() == 9, "Expected 9 loaded crystal fuels on server, got " + fuels.size());
        var displays = CrystalFuelDisplay.from(fuels);
        h.assertTrue(displays.size() == 18, "Expected 18 CrystalFuelDisplay entries, got " + displays.size());
        var baseFirst = displays.get(0);
        var indFirst = displays.get(9);
        h.assertTrue(baseFirst.energy() == 768L && baseFirst.rate() == 128 && baseFirst.ticks() == 6,
                "Unexpected base Matrix Crystal display stats: " + baseFirst);
        h.assertTrue(indFirst.energy() == 768L && indFirst.rate() == 512 && indFirst.ticks() == 2,
                "Unexpected industrial Matrix Crystal display stats: " + indFirst);
        h.succeed();
    }

    private static void industrialBlueprintAndPreviewToggle(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos srcRel = new BlockPos(2, 3, 2);
        BlockPos dstRel = new BlockPos(6, 3, 2);
        var src = placeMachine(h, srcRel, MachineKind.INDUSTRIAL_REACTOR, Direction.NORTH, owner);
        var dst = placeMachine(h, dstRel, MachineKind.INDUSTRIAL_REACTOR, Direction.NORTH, owner);

        h.assertTrue(!src.structurePreviewEnabled(), "Structure preview should default to OFF");
        h.assertTrue(src.toggleStructurePreview() && src.structurePreviewEnabled(),
                "toggleStructurePreview should enable preview on industrial controller");
        h.assertTrue(src.toggleStructurePreview() && !src.structurePreviewEnabled(),
                "Second toggleStructurePreview should turn preview back OFF");
        src.setStructurePreviewEnabled(true);

        // Configure 3 Reaction Chambers + 2 Field Coils + height 6 + 4 Machine Ports; footprint auto-expands.
        h.assertTrue(src.adjustIndustrialBlueprint(IndustrialBlueprint.Parameter.CHAMBERS, 2),
                "Failed to add 2 Reaction Chambers to blueprint");
        h.assertTrue(src.adjustIndustrialBlueprint(IndustrialBlueprint.Parameter.COILS, 1),
                "Failed to add 1 Field Coil to blueprint");
        h.assertTrue(src.adjustIndustrialBlueprint(IndustrialBlueprint.Parameter.HEIGHT, 2),
                "Failed to increase blueprint height by 2");
        h.assertTrue(src.adjustIndustrialBlueprint(IndustrialBlueprint.Parameter.PORTS, 2),
                "Failed to increase blueprint ports by 2");

        var bp = src.industrialBlueprint();
        h.assertTrue(src.hasCustomBlueprint(), "Controller should report custom blueprint");
        h.assertTrue(bp.chambers() == 3 && bp.coils() == 2 && bp.height() == 6 && bp.ports() == 4 && bp.slotCapacity() >= 5,
                "Unexpected configured blueprint: " + bp);

        // Copy & paste blueprint + preview flag via Configuration Card.
        BlockPos absSrc = h.absolutePos(srcRel);
        owner.setPos(absSrc.getX() + 0.5, absSrc.getY(), absSrc.getZ() + 0.5);
        ItemStack card = new ItemStack(ModContent.CONFIGURATION_CARD.get());
        owner.setShiftKeyDown(true);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), absSrc, card, hit(h, srcRel, Direction.UP)).consumesAction(),
                "Configuration Card failed to copy Industrial Reactor setup");
        owner.setShiftKeyDown(false);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), h.absolutePos(dstRel), card, hit(h, dstRel, Direction.UP)).consumesAction(),
                "Configuration Card failed to paste Industrial Reactor setup");
        h.assertTrue(dst.industrialBlueprint().equals(bp) && dst.structurePreviewEnabled(),
                "Pasted Industrial Reactor blueprint/preview did not match source");

        h.assertTrue(src.resetIndustrialBlueprint() && !src.hasCustomBlueprint(),
                "resetIndustrialBlueprint should restore default blueprint");
        h.succeed();
    }

    private static void pressurizedCanisterTransfers(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos driveRel = new BlockPos(2, 2, 2);
        BlockPos cableRel = new BlockPos(2, 2, 3);
        BlockPos termRel = new BlockPos(2, 2, 4);

        var drive = placeMachine(h, driveRel, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_256K.get()));
        h.setBlock(cableRel, ModContent.MATTER_CABLE.get());
        CableOwners.placedBy(h.getLevel(), h.absolutePos(cableRel), owner.getUUID());
        var terminal = placeMachine(h, termRel, MachineKind.TERMINAL, Direction.SOUTH, owner);

        var net = drive.matterNetwork(true);
        var inv = Objects.requireNonNull(net.inventory());
        h.assertTrue(inv.apply(Map.of(), Map.of(BuiltinChemistry.HYDROGEN, 20_000L, BuiltinChemistry.WATER, 5_000L, BuiltinChemistry.COPPER, 8_000L)),
                "Failed to seed Drive with HYDROGEN, WATER, and COPPER");
        net.markChanged();

        BlockPos absDrive = h.absolutePos(driveRel);
        owner.setPos(absDrive.getX() + 0.5, absDrive.getY(), absDrive.getZ() + 0.5);

        // 1. Fill canister from Terminal inventory helper (HYDROGEN gas, capped at 16,000), while rejecting solid COPPER.
        ItemStack canister = new ItemStack(ModContent.PRESSURIZED_CANISTER.get());
        owner.getInventory().setItem(0, canister);
        h.assertTrue(PressurizedCanisterItem.transferInInventory(
                        owner.getInventory(), terminal.matterNetwork(true), terminal, BuiltinChemistry.COPPER, false) == 0,
                "Pressurized Canister must never extract SOLID substances");
        int extractedH2 = PressurizedCanisterItem.transferInInventory(
                owner.getInventory(), terminal.matterNetwork(true), terminal, BuiltinChemistry.HYDROGEN, false);
        h.assertTrue(extractedH2 == PressurizedCanisterItem.CAPACITY,
                "Expected 16,000 HYDROGEN extracted into canister, got " + extractedH2);
        h.assertTrue(inv.amount(BuiltinChemistry.HYDROGEN) == 4_000L,
                "Expected 4,000 HYDROGEN remaining in Drive, got " + inv.amount(BuiltinChemistry.HYDROGEN));

        // 2. Matter-filled canister rejects Capabilities.Fluid.ITEM insertion (strict mutual exclusion).
        var fluidHandlerWhileMatter = Objects.requireNonNull(ItemAccess.forStack(canister).getCapability(Capabilities.Fluid.ITEM));
        try (var tx = Transaction.openRoot()) {
            int acceptedWater = fluidHandlerWhileMatter.insert(FluidResource.of(Fluids.WATER), 1_000, tx);
            h.assertTrue(acceptedWater == 0, "Matter-filled canister must reject plain fluid capability insertion");
        }

        // 3. Direct right-click on Drive deposits all 16,000 HYDROGEN back into network storage.
        owner.setItemInHand(InteractionHand.MAIN_HAND, canister);
        owner.setShiftKeyDown(false);
        h.assertTrue(PressurizedCanisterItem.interactWithMachine(
                        canister, h.getLevel(), absDrive, owner, InteractionHand.MAIN_HAND, drive).consumesAction(),
                "Right-clicking Drive with matter canister should deposit into storage");
        h.assertTrue(PressurizedCanisterData.read(canister) instanceof PressurizedCanisterData.State.Empty,
                "Canister should be empty after depositing 16,000 HYDROGEN");
        h.assertTrue(inv.amount(BuiltinChemistry.HYDROGEN) == 20_000L,
                "Drive should hold all 20,000 HYDROGEN again");

        // 4. Plain fluid capability insertion/extraction works on an empty canister, and fluid canister rejects matter deposit.
        owner.getInventory().setItem(0, canister);
        var invSlotFluidHandler = Objects.requireNonNull(
                ItemAccess.forPlayerSlot(owner, 0).getCapability(Capabilities.Fluid.ITEM));
        try (var tx = Transaction.openRoot()) {
            int insertedFluid = invSlotFluidHandler.insert(FluidResource.of(Fluids.WATER), 6_000, tx);
            h.assertTrue(insertedFluid == 6_000, "Expected 6,000 mB water inserted, got " + insertedFluid);
            tx.commit();
        }
        ItemStack fluidFilled = owner.getInventory().getItem(0);
        h.assertTrue(PressurizedCanisterItem.storedAmount(fluidFilled) == 6_000
                        && PressurizedCanisterData.read(fluidFilled) instanceof PressurizedCanisterData.State.Fluid,
                "Canister should be in Fluid state with 6,000 mB");
        try (var tx = Transaction.openRoot()) {
            int drainedFluid = invSlotFluidHandler.extract(FluidResource.of(Fluids.WATER), 6_000, tx);
            h.assertTrue(drainedFluid == 6_000, "Expected 6,000 mB water extracted, got " + drainedFluid);
            tx.commit();
        }
        h.assertTrue(PressurizedCanisterData.read(owner.getInventory().getItem(0)) instanceof PressurizedCanisterData.State.Empty,
                "Canister should return to Empty state after draining all fluid");
        h.succeed();
    }

    private static void configurationCardCellFilters(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos stationRel = new BlockPos(2, 2, 2);
        BlockPos driveRel = new BlockPos(4, 2, 2);

        h.setBlock(stationRel, ModContent.CELL_TRANSFER_STATION.get());
        var station = h.getBlockEntity(stationRel, CellTransferStationBlockEntity.class);
        station.placedBy(owner);

        var drive = placeMachine(h, driveRel, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_64K.get()));

        var filterKeys = Set.of(BuiltinChemistry.COPPER, BuiltinChemistry.HYDROGEN);
        ItemStack sourceCell = new ItemStack(ModContent.MATTER_CELL_64K.get());
        sourceCell.set(CellPartitionData.TYPE.get(), CellPartitionData.allowOnly(filterKeys));

        ItemStack card = new ItemStack(ModContent.CONFIGURATION_CARD.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, card);
        owner.setItemInHand(InteractionHand.OFF_HAND, sourceCell);

        // 1. Sneak-use copies cell filter from offhand Matter Cell onto Configuration Card.
        owner.setShiftKeyDown(true);
        h.assertTrue(card.use(h.getLevel(), owner, InteractionHand.MAIN_HAND).consumesAction(),
                "Sneak-use with offhand Matter Cell should copy cell_filter onto Configuration Card");

        // 2. Normal use stamps filter onto an offhand Void Matter Cell.
        ItemStack voidCell = new ItemStack(ModContent.VOID_MATTER_CELL.get());
        owner.setItemInHand(InteractionHand.OFF_HAND, voidCell);
        owner.setShiftKeyDown(false);
        h.assertTrue(card.use(h.getLevel(), owner, InteractionHand.MAIN_HAND).consumesAction(),
                "Normal use with offhand Void Matter Cell should paste cell_filter");
        var pastedOffhand = CellPartitionData.read(voidCell).orElseThrow();
        h.assertTrue(pastedOffhand.restricted() && pastedOffhand.allowed().equals(filterKeys),
                "Offhand Void Matter Cell filter did not match copied keys");
        owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        // 3. Paste filter onto target cell inside Cell Transfer Station.
        station.setItem(1, new ItemStack(ModContent.MATTER_CELL_256K.get()));
        BlockPos absStation = h.absolutePos(stationRel);
        owner.setPos(absStation.getX() + 0.5, absStation.getY(), absStation.getZ() + 0.5);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), absStation, card, hit(h, stationRel, Direction.UP)).consumesAction(),
                "Pasting cell_filter onto Cell Transfer Station failed");
        var pastedStation = CellPartitionData.read(station.getItem(1)).orElseThrow();
        h.assertTrue(pastedStation.restricted() && pastedStation.allowed().equals(filterKeys),
                "Cell Transfer Station slot 1 cell filter did not match copied keys");

        // 4. Paste filter directly onto Bay 0 on the NORTH front face of Matter Drive.
        BlockPos absDrive = h.absolutePos(driveRel);
        owner.setPos(absDrive.getX() + 0.5, absDrive.getY(), absDrive.getZ() - 0.5);
        BlockHitResult bayZeroHit = new BlockHitResult(
                Vec3.atLowerCornerOf(absDrive).add(0.75, 0.75, 0.0),
                Direction.NORTH,
                absDrive,
                false);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), absDrive, card, bayZeroHit).consumesAction(),
                "Pasting cell_filter onto Matter Drive front bay 0 failed");
        var pastedDriveBay = CellPartitionData.read(drive.getItem(drive.cellSlotForBay(0))).orElseThrow();
        h.assertTrue(pastedDriveBay.restricted() && pastedDriveBay.allowed().equals(filterKeys),
                "Matter Drive bay 0 cell filter did not match copied keys");
        h.succeed();
    }

    private static void wrenchDiagnosticsAndTerminalMonitor(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var stranger = h.makeMockPlayer(GameType.SURVIVAL);

        BlockPos driveRel = new BlockPos(2, 2, 2);
        BlockPos cableRel = new BlockPos(3, 2, 2);
        BlockPos reactorRel = new BlockPos(4, 2, 2);
        BlockPos emitterRel = new BlockPos(3, 2, 1);

        var drive = placeMachine(h, driveRel, MachineKind.DRIVE, Direction.SOUTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_64K.get()));
        ItemStack voidCell = new ItemStack(ModContent.VOID_MATTER_CELL.get());
        voidCell.set(CellPartitionData.TYPE.get(), CellPartitionData.allowOnly(Set.of(BuiltinChemistry.OXYGEN)));
        drive.setItem(drive.cellSlotForBay(1), voidCell);

        h.setBlock(cableRel, ModContent.MATTER_CABLE.get());
        CableOwners.placedBy(h.getLevel(), h.absolutePos(cableRel), owner.getUUID());

        var reactor = placeMachine(h, reactorRel, MachineKind.REACTOR, Direction.NORTH, owner);
        h.assertTrue(reactor.selectRecipe("rematter:water_electrolysis"), "Reactor should select water_electrolysis");
        h.assertTrue(reactor.configure(owner, new MachineSettings(10_000L, "rematter:hydrogen", "gas", Set.of())),
                "Reactor should accept 10,000 HYDROGEN target cap");

        h.setBlock(emitterRel, ModContent.MATTER_LEVEL_EMITTER.get().defaultBlockState()
                .setValue(MatterLevelEmitterBlock.FACING, Direction.NORTH));
        var emitter = h.getBlockEntity(emitterRel, MatterLevelEmitterBlockEntity.class);
        emitter.placedBy(owner);
        emitter.configure(BuiltinChemistry.HYDROGEN, 500L, false);

        var net = drive.matterNetwork(true);
        var inv = Objects.requireNonNull(net.inventory());
        h.assertTrue(inv.apply(Map.of(), Map.of(BuiltinChemistry.HYDROGEN, 1_000L)), "Failed to seed 1,000 HYDROGEN");
        net.markChanged();
        emitter.serverTick();

        // 1. WrenchDiagnostics enforces ownership: stranger gets locked zeroed payload; owner gets live metrics.
        BlockPos absDrive = h.absolutePos(driveRel);
        var strangerDiag = Objects.requireNonNull(WrenchDiagnostics.inspect(h.getLevel(), absDrive, Direction.NORTH, stranger));
        h.assertTrue(strangerDiag.locked() && strangerDiag.matterUsed() == 0 && strangerDiag.matterCapacity() == 0,
                "Non-owner WrenchDiagnostics must be locked and zeroed");
        var ownerDiag = Objects.requireNonNull(WrenchDiagnostics.inspect(h.getLevel(), absDrive, Direction.NORTH, owner));
        h.assertTrue(!ownerDiag.locked() && ownerDiag.matterUsed() == 1_000L
                        && ownerDiag.matterCapacity() == MatterLimits.CELL_64K && ownerDiag.networkNodes() >= 3,
                "Owner WrenchDiagnostics missed live storage/network telemetry: " + ownerDiag);

        // 2. TerminalMonitorCollector reports drives, finite/void cells, target caps, emitters, and rolling rates.
        var rateTracker = new NetworkRateTracker();
        TerminalMonitorCollector.collect(drive.matterNetwork(true), rateTracker, 100L, 7);
        h.assertTrue(inv.apply(Map.of(), Map.of(BuiltinChemistry.HYDROGEN, 600L)), "Failed to add +600 HYDROGEN");
        net.markChanged();

        var monitor = TerminalMonitorCollector.collect(drive.matterNetwork(true), rateTracker, 160L, 7);
        h.assertTrue(monitor.drives() == 1 && monitor.finiteCells() == 1 && monitor.voidCells() == 1
                        && monitor.totalWorkers() == 1 && monitor.totalEmitters() == 1 && monitor.activeEmitters() == 1,
                "Unexpected TerminalMonitorPayload summary counts: " + monitor);
        boolean hasRate = monitor.rows().stream().anyMatch(r ->
                r.kind() == TerminalMonitorPayload.RowKind.RATE
                        && r.substanceId().equals("rematter:hydrogen")
                        && r.limit() == 12_000L);
        boolean hasCap = monitor.rows().stream().anyMatch(r ->
                r.kind() == TerminalMonitorPayload.RowKind.TARGET_CAP
                        && r.substanceId().equals("rematter:hydrogen")
                        && r.limit() == 10_000L);
        boolean hasEmitter = monitor.rows().stream().anyMatch(r ->
                r.kind() == TerminalMonitorPayload.RowKind.LEVEL_EMITTER
                        && r.substanceId().equals("rematter:hydrogen")
                        && r.active());
        boolean hasVoid = monitor.rows().stream().anyMatch(r ->
                r.kind() == TerminalMonitorPayload.RowKind.VOID_OVERFLOW
                        && r.substanceId().equals("rematter:oxygen"));
        h.assertTrue(hasRate && hasCap && hasEmitter && hasVoid,
                "TerminalMonitorCollector missing expected rows (rate=" + hasRate
                        + ", cap=" + hasCap + ", emitter=" + hasEmitter + ", void=" + hasVoid + ")");
        h.succeed();
    }
}
