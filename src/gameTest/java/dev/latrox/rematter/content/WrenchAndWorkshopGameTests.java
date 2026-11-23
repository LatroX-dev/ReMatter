package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.energy.EnergyCubeBlock;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeTier;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Server GameTests for Matter Wrench, Energy Cube face/break consistency, Drive front bays, Redstone modes, Comparators and Tool Salvage. */
@EventBusSubscriber(modid = "rematter")
public final class WrenchAndWorkshopGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static {
        TESTS.put("matter_wrench_rotates_cycles_ports_dismantles_and_enforces_owner", WrenchAndWorkshopGameTests::wrenchOperations);
        TESTS.put("energy_cube_empty_hand_face_cycle_and_break_never_duplicates_slots", WrenchAndWorkshopGameTests::energyCubeConsistency);
        TESTS.put("matter_drive_front_bays_insert_and_extract_cells_in_world", WrenchAndWorkshopGameTests::driveFrontBays);
        TESTS.put("redstone_modes_and_comparators_reflect_and_gate_machine_work", WrenchAndWorkshopGameTests::redstoneAndComparators);
        TESTS.put("decomposer_salvages_damaged_tools_safely_and_rejects_custom_or_analyzer", WrenchAndWorkshopGameTests::toolSalvageRuntime);
        TESTS.put("configuration_card_pairs_links_and_copies_machine_and_cube_setups", WrenchAndWorkshopGameTests::configurationCardOperations);
        TESTS.put("wireless_terminal_charges_binds_and_opens_network_session", WrenchAndWorkshopGameTests::wirelessTerminalOperations);
        TESTS.put("energy_cube_input_output_prevents_same_tick_feedback_oscillation", WrenchAndWorkshopGameTests::bidirectionalCubeAntiOscillation);
        TESTS.put("unformed_reactor_controller_locks_to_single_configuration_page_and_unlocks_when_formed", WrenchAndWorkshopGameTests::unformedReactorSingleConfigurationPage);
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("rematter_tests", path); }

    @SubscribeEvent
    public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach((name, fn) -> helper.register(id(name), fn)));
    }

    @SubscribeEvent
    public static void tests(RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(id("workshop_environment"));
        TESTS.forEach((name, fn) -> event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                new TestData<>(env, id("empty"), 1600, 0, true, Rotation.NONE, false, 1, 1, true, 2))));
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

    private static void wrenchOperations(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var stranger = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack wrench = new ItemStack(ModContent.MATTER_WRENCH.get());
        h.assertTrue(wrench.is(Tags.Items.TOOLS_WRENCH), "Matter Wrench must carry #c:tools/wrench");
        h.assertTrue(MatterWrenchItem.mode(wrench) == WrenchMode.PORTS, "Default wrench mode must be PORTS");

        BlockPos relPos = new BlockPos(2, 2, 2);
        var decomposer = placeMachine(h, relPos, MachineKind.DECOMPOSER, Direction.NORTH, owner);
        BlockPos absPos = h.absolutePos(relPos);
        owner.setPos(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5);
        stranger.setPos(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5);

        // PORTS mode cycles configurable sides and never rotates when clicking the closed front face.
        var backSide = MachinePortMode.Side.BACK;
        var initialBack = decomposer.portMode(backSide);
        h.assertTrue(WrenchInteractions.interact(owner, h.getLevel(), absPos, wrench, hit(h, relPos, Direction.SOUTH)).consumesAction(),
                "Wrench PORTS mode did not cycle configurable machine side");
        h.assertTrue(decomposer.portMode(backSide) != initialBack, "Machine back port mode did not change");
        WrenchInteractions.interact(owner, h.getLevel(), absPos, wrench, hit(h, relPos, Direction.NORTH));
        h.assertTrue(h.getBlockState(relPos).getValue(MachineBlock.FACING) == Direction.NORTH,
                "PORTS mode rotated machine when clicking closed front face");

        // Sneak + Right-Click while aiming at a block switches mode to ROTATE without dismantling!
        owner.setShiftKeyDown(true);
        h.assertTrue(WrenchInteractions.interact(owner, h.getLevel(), absPos, wrench, hit(h, relPos, Direction.NORTH)).consumesAction(),
                "Sneak-right-click on block did not switch wrench mode");
        owner.setShiftKeyDown(false);
        h.assertTrue(MatterWrenchItem.mode(wrench) == WrenchMode.ROTATE && !h.getBlockState(relPos).isAir(),
                "Wrench did not switch cleanly to ROTATE");

        // ROTATE mode rotates machines and Energy Cubes on any face without changing port modes.
        var keptBack = decomposer.portMode(backSide);
        h.assertTrue(WrenchInteractions.interact(owner, h.getLevel(), absPos, wrench, hit(h, relPos, Direction.WEST)).consumesAction(),
                "Wrench ROTATE mode did not rotate machine");
        h.assertTrue(h.getBlockState(relPos).getValue(MachineBlock.FACING) == Direction.EAST
                && decomposer.portMode(backSide) == keptBack, "ROTATE mode did not rotate cleanly");

        BlockPos cubePos = new BlockPos(3, 2, 2);
        h.setBlock(cubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get().defaultBlockState().setValue(EnergyCubeBlock.FACING, Direction.NORTH));
        h.getBlockEntity(cubePos, EnergyCubeBlockEntity.class).placedBy(owner);
        h.assertTrue(WrenchInteractions.interact(owner, h.getLevel(), h.absolutePos(cubePos), wrench, hit(h, cubePos, Direction.UP)).consumesAction()
                && h.getBlockState(cubePos).getValue(EnergyCubeBlock.FACING) == Direction.EAST,
                "Wrench ROTATE mode did not rotate Energy Cube");

        // Sneak + Right-Click switches to DISMANTLE; stranger is rejected, owner dismantles into inventory.
        owner.setShiftKeyDown(true);
        WrenchInteractions.interact(owner, h.getLevel(), absPos, wrench, hit(h, relPos, Direction.EAST));
        owner.setShiftKeyDown(false);
        h.assertTrue(MatterWrenchItem.mode(wrench) == WrenchMode.DISMANTLE, "Wrench did not switch to DISMANTLE");

        h.assertTrue(!WrenchInteractions.interact(stranger, h.getLevel(), absPos, wrench, hit(h, relPos, Direction.EAST)).consumesAction(),
                "Stranger dismantled another player's machine");
        h.assertTrue(!h.getBlockState(relPos).isAir(), "Machine disappeared after denied dismantle");

        BlockPos linkPos = new BlockPos(4, 2, 2);
        var linkBlock = dev.latrox.rematter.primal.PrimalContent.ENTANGLED_LINK.get();
        h.setBlock(linkPos, linkBlock);
        linkBlock.setPlacedBy(h.getLevel(), h.absolutePos(linkPos), h.getBlockState(linkPos), owner, new ItemStack(linkBlock));
        h.assertTrue(!WrenchInteractions.interact(stranger, h.getLevel(), h.absolutePos(linkPos), wrench, hit(h, linkPos, Direction.UP)).consumesAction(),
                "Stranger dismantled another player's Entangled Link");

        decomposer.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        h.assertTrue(WrenchInteractions.interact(owner, h.getLevel(), absPos, wrench, hit(h, relPos, Direction.EAST)).consumesAction(),
                "Owner could not dismantle owned machine in DISMANTLE mode");
        h.assertTrue(h.getBlockState(relPos).isAir(), "Dismantled machine block remained in world");
        boolean foundDropInInventory = false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            if (owner.getInventory().getItem(i).is(ModContent.MACHINES.get(MachineKind.DECOMPOSER).get().asItem())) {
                foundDropInInventory = true;
                break;
            }
        }
        h.assertTrue(foundDropInInventory, "Dismantled machine was not placed into owner's inventory");
        h.succeed();
    }

    private static void energyCubeConsistency(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos relPos = new BlockPos(2, 2, 2);
        h.setBlock(relPos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get().defaultBlockState().setValue(EnergyCubeBlock.FACING, Direction.NORTH));
        var cube = h.getBlockEntity(relPos, EnergyCubeBlockEntity.class);
        cube.placedBy(owner);
        BlockPos absPos = h.absolutePos(relPos);
        owner.setPos(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5);

        // Empty-hand Shift-Right-Click cycles the clicked face mode just like MachineBlock.
        owner.setShiftKeyDown(true);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var before = cube.sideMode(MachinePortMode.Side.FRONT);
        var res = h.getBlockState(relPos).useWithoutItem(h.getLevel(), owner, hit(h, relPos, Direction.NORTH));
        h.assertTrue(res.consumesAction() && cube.sideMode(MachinePortMode.Side.FRONT) != before,
                "Empty-hand sneak-right-click did not cycle Energy Cube face");

        // Breaking a populated Energy Cube must NOT scatter duplicate loose items via Containers.preRemoveSideEffects.
        cube.setItem(0, new ItemStack(Items.IRON_PICKAXE));
        h.getLevel().destroyBlock(absPos, true, owner);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absPos).inflate(2.5));
        long loosePickaxes = drops.stream().filter(e -> e.getItem().is(Items.IRON_PICKAXE)).count();
        long cubeDrops = drops.stream().filter(e -> e.getItem().is(ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get().asItem())).count();
        h.assertTrue(loosePickaxes == 0 && cubeDrops == 1,
                "Breaking Energy Cube leaked or duplicated slot items: loose=" + loosePickaxes + ", cubes=" + cubeDrops);
        h.succeed();
    }

    private static void driveFrontBays(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos relPos = new BlockPos(2, 2, 2);
        var drive = placeMachine(h, relPos, MachineKind.DRIVE, Direction.NORTH, owner);
        BlockPos absPos = h.absolutePos(relPos);
        owner.setPos(absPos.getX() + 0.5, absPos.getY(), absPos.getZ() + 0.5);

        // Click bottom-right bay (col 1, row 2 -> bay 5) on the NORTH front face while holding a 64k Cell.
        Vec3 bay5Point = new Vec3(absPos.getX() + 0.25, absPos.getY() + 3.5 / 16.0, absPos.getZ());
        var frontHit = new BlockHitResult(bay5Point, Direction.NORTH, absPos, false);
        ItemStack heldCell = new ItemStack(ModContent.MATTER_CELL_4M.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, heldCell);
        owner.setShiftKeyDown(false);
        var insertResult = h.getBlockState(relPos).useItemOn(heldCell, h.getLevel(), owner, InteractionHand.MAIN_HAND, frontHit);
        h.assertTrue(insertResult.consumesAction(), "Drive front bay did not accept cell click");
        int bay5Slot = MachineCellAccess.slotForBay(MachineKind.DRIVE, 5);
        h.assertTrue(drive.getItem(bay5Slot).is(ModContent.MATTER_CELL_4M.get()) && owner.getMainHandItem().isEmpty(),
                "Cell did not land in targeted Drive bay 5");

        // Shift-right-click with empty hand extracts the cell from bay 5 back into main hand.
        owner.setShiftKeyDown(true);
        var extractResult = h.getBlockState(relPos).useWithoutItem(h.getLevel(), owner, frontHit);
        h.assertTrue(extractResult.consumesAction(), "Drive front bay did not extract cell on sneak-right-click");
        h.assertTrue(drive.getItem(bay5Slot).isEmpty() && owner.getMainHandItem().is(ModContent.MATTER_CELL_4M.get()),
                "Extracted cell was not returned cleanly to player hand");
        h.succeed();
    }

    private static void redstoneAndComparators(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos drivePos = new BlockPos(2, 2, 2);
        BlockPos decompPos = new BlockPos(3, 2, 2);
        var drive = placeMachine(h, drivePos, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(MachineCellAccess.slotForBay(MachineKind.DRIVE, 0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        var decomp = placeMachine(h, decompPos, MachineKind.DECOMPOSER, Direction.NORTH, owner);
        decomp.energy().set(decomp.energyCapacity());
        decomp.setItem(0, new ItemStack(Items.COPPER_INGOT, 2));

        // Comparator reads stored energy on Decomposer (> 0) and 0 on empty Drive.
        BlockPos absDecomp = h.absolutePos(decompPos);
        h.assertTrue(h.getBlockState(decompPos).getAnalogOutputSignal(h.getLevel(), absDecomp, Direction.NORTH) > 0,
                "Decomposer comparator signal did not reflect stored FE");
        h.assertTrue(h.getBlockState(drivePos).getAnalogOutputSignal(h.getLevel(), h.absolutePos(drivePos), Direction.NORTH) == 0,
                "Empty Drive reported non-zero comparator signal");

        // HIGH mode pauses unpowered Decomposer and reports redstone_paused.
        decomp.setRedstoneMode(RedstoneMode.HIGH);
        decomp.serverTick();
        h.assertTrue(decomp.work().progress == 0 && "redstone_paused".equals(decomp.work().status),
                "Unpowered HIGH mode machine did not pause with redstone_paused: " + decomp.work().status);

        // PULSE mode runs exactly one batch per rising redstone edge.
        decomp.setRedstoneMode(RedstoneMode.PULSE);
        BlockPos signalPos = new BlockPos(3, 2, 3);
        h.setBlock(signalPos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        h.runAtTickTime(460, () -> {
            h.assertTrue(decomp.getItem(0).getCount() == 1,
                    "PULSE mode did not complete exactly one batch on single rising edge: remaining=" + decomp.getItem(0).getCount());
            h.assertTrue(decomp.work().progress == 0 && "redstone_paused".equals(decomp.work().status),
                    "PULSE mode started a second batch without a new rising edge: " + decomp.work().status);
            h.assertTrue(h.getBlockState(drivePos).getAnalogOutputSignal(h.getLevel(), h.absolutePos(drivePos), Direction.NORTH) >= 1,
                    "Drive comparator did not rise after storing decomposed copper");
            h.succeed();
        });
    }

    private static void toolSalvageRuntime(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var drive = placeMachine(h, new BlockPos(2, 2, 2), MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(MachineCellAccess.slotForBay(MachineKind.DRIVE, 0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        var decomp = placeMachine(h, new BlockPos(3, 2, 2), MachineKind.DECOMPOSER, Direction.NORTH, owner);
        var analyzer = placeMachine(h, new BlockPos(1, 2, 2), MachineKind.ANALYZER, Direction.NORTH, owner);
        decomp.energy().set(decomp.energyCapacity());

        // Half-damaged copper shovel (1 ingot = 400 ticks) yields strictly scaled copper in Decomposer,
        // is rejected by Analyzer, and is rejected when carrying CUSTOM_DATA.
        ItemStack halfShovel = new ItemStack(Items.COPPER_SHOVEL);
        int maxDamage = halfShovel.getMaxDamage();
        int halfDamage = maxDamage / 2;
        halfShovel.setDamageValue(halfDamage);

        h.assertTrue(!analyzer.canPlaceItem(0, halfShovel), "Analyzer must reject damaged salvage items");
        h.assertTrue(decomp.canPlaceItem(0, halfShovel), "Decomposer must accept unenchanted damaged copper shovel");

        ItemStack customTagged = halfShovel.copy();
        CompoundTag custom = new CompoundTag();
        custom.putString("tag", "nope");
        customTagged.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        h.assertTrue(!decomp.canPlaceItem(0, customTagged), "Decomposer must reject damaged tool carrying extra CUSTOM_DATA");

        decomp.setItem(0, halfShovel);
        long expectedCopper = (1_000L * (maxDamage - halfDamage)) / maxDamage;
        h.succeedWhen(() -> {
            long storedCopper = drive.matterStore().amount(SubstanceKey.of("rematter:copper", Phase.SOLID));
            h.assertTrue(decomp.getItem(0).isEmpty() && storedCopper == expectedCopper,
                    "Damaged copper shovel yielded " + storedCopper + " copper, expected " + expectedCopper);
        });
    }

    private static void configurationCardOperations(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack card = new ItemStack(ModContent.CONFIGURATION_CARD.get());

        // 1. Entangled Link non-occlusion + pairing via Configuration Card
        var linkBlock = dev.latrox.rematter.primal.PrimalContent.ENTANGLED_LINK.get();
        h.assertTrue(!linkBlock.defaultBlockState().canOcclude(), "Entangled Link must be non-occluding to prevent x-ray culling");
        BlockPos linkA = new BlockPos(2, 2, 2);
        BlockPos linkB = new BlockPos(5, 2, 2);
        h.setBlock(linkA, linkBlock);
        h.setBlock(linkB, linkBlock);
        linkBlock.setPlacedBy(h.getLevel(), h.absolutePos(linkA), h.getBlockState(linkA), owner, new ItemStack(linkBlock));
        linkBlock.setPlacedBy(h.getLevel(), h.absolutePos(linkB), h.getBlockState(linkB), owner, new ItemStack(linkBlock));

        owner.setPos(h.absolutePos(linkA).getX() + 0.5, h.absolutePos(linkA).getY(), h.absolutePos(linkA).getZ() + 0.5);
        owner.setShiftKeyDown(true);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), h.absolutePos(linkA), card, hit(h, linkA, Direction.UP)).consumesAction(),
                "Config Card failed to select first Entangled Link");
        owner.setShiftKeyDown(false);
        owner.setPos(h.absolutePos(linkB).getX() + 0.5, h.absolutePos(linkB).getY(), h.absolutePos(linkB).getZ() + 0.5);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), h.absolutePos(linkB), card, hit(h, linkB, Direction.UP)).consumesAction(),
                "Config Card failed to pair second Entangled Link");
        h.assertTrue(Objects.equals(
                dev.latrox.rematter.primal.EntangledLinks.peer(h.getLevel(), h.absolutePos(linkA), owner.getUUID()).orElse(null),
                h.absolutePos(linkB)), "Entangled Links were not paired by Configuration Card");

        // 2. Machine port + redstone copy/paste
        BlockPos m1Pos = new BlockPos(2, 2, 4);
        BlockPos m2Pos = new BlockPos(4, 2, 4);
        var m1 = placeMachine(h, m1Pos, MachineKind.DECOMPOSER, Direction.NORTH, owner);
        var m2 = placeMachine(h, m2Pos, MachineKind.DECOMPOSER, Direction.NORTH, owner);
        owner.setPos(h.absolutePos(m1Pos).getX() + 0.5, h.absolutePos(m1Pos).getY(), h.absolutePos(m1Pos).getZ() + 0.5);
        m1.cyclePort(MachinePortMode.Side.BACK, true);
        m1.setRedstoneMode(RedstoneMode.HIGH);

        owner.setShiftKeyDown(true);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), h.absolutePos(m1Pos), card, hit(h, m1Pos, Direction.UP)).consumesAction(),
                "Config Card failed to copy machine setup");
        owner.setShiftKeyDown(false);
        owner.setPos(h.absolutePos(m2Pos).getX() + 0.5, h.absolutePos(m2Pos).getY(), h.absolutePos(m2Pos).getZ() + 0.5);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), h.absolutePos(m2Pos), card, hit(h, m2Pos, Direction.UP)).consumesAction(),
                "Config Card failed to paste machine setup");
        h.assertTrue(m2.portMode(MachinePortMode.Side.BACK) == m1.portMode(MachinePortMode.Side.BACK)
                && m2.redstoneMode() == RedstoneMode.HIGH, "Pasted machine setup did not match source");

        // 3. Energy Cube cross-tier side mode copy/paste
        BlockPos c1Pos = new BlockPos(2, 2, 6);
        BlockPos c2Pos = new BlockPos(4, 2, 6);
        h.setBlock(c1Pos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get());
        h.setBlock(c2Pos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get());
        var c1 = h.getBlockEntity(c1Pos, EnergyCubeBlockEntity.class);
        var c2 = h.getBlockEntity(c2Pos, EnergyCubeBlockEntity.class);
        c1.placedBy(owner);
        c2.placedBy(owner);
        owner.setPos(h.absolutePos(c1Pos).getX() + 0.5, h.absolutePos(c1Pos).getY(), h.absolutePos(c1Pos).getZ() + 0.5);
        c1.cycleSide(owner, MachinePortMode.Side.FRONT);
        owner.setShiftKeyDown(true);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), h.absolutePos(c1Pos), card, hit(h, c1Pos, Direction.UP)).consumesAction(),
                "Config Card failed to copy Energy Cube sides");
        owner.setShiftKeyDown(false);
        owner.setPos(h.absolutePos(c2Pos).getX() + 0.5, h.absolutePos(c2Pos).getY(), h.absolutePos(c2Pos).getZ() + 0.5);
        h.assertTrue(ConfigurationCardInteractions.interact(owner, h.getLevel(), h.absolutePos(c2Pos), card, hit(h, c2Pos, Direction.UP)).consumesAction(),
                "Config Card failed to paste Energy Cube sides");
        h.assertTrue(c2.sideMode(MachinePortMode.Side.FRONT) == dev.latrox.rematter.energy.EnergyCubeSideMode.INPUT_OUTPUT,
                "Energy Cube cross-tier paste did not copy INPUT_OUTPUT mode");
        h.succeed();
    }

    private static void wirelessTerminalOperations(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);

        // Verify active machines expose bound primary + accent foley sounds while passive network devices remain silent
        for (MachineKind kind : MachineKind.values()) {
            var work = ModSounds.workingSound(kind);
            var accent = ModSounds.accentSound(kind);
            h.assertTrue((work == null) == (accent == null), "workingSound and accentSound disagree for " + kind);
            if (work != null) {
                h.assertTrue(work.isBound() && accent.isBound(), "Unbound foley SoundEvent for " + kind);
            }
        }
        h.assertTrue(ModSounds.workingSound(MachineKind.DECOMPOSER) != null
                && ModSounds.workingSound(MachineKind.ANALYZER) != null
                && ModSounds.workingSound(MachineKind.SYNTHESIZER) != null
                && ModSounds.workingSound(MachineKind.DRIVE) == null
                && ModSounds.workingSound(MachineKind.TERMINAL) == null,
                "ModSounds workingSound mapping disagrees with active vs passive machine roles");

        // 1. Charge Wireless Matter Terminal inside an Energy Cube slot via Capabilities.Energy.ITEM
        BlockPos cubePos = new BlockPos(2, 2, 2);
        h.setBlock(cubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get());
        var cube = h.getBlockEntity(cubePos, EnergyCubeBlockEntity.class);
        cube.placedBy(owner);
        try (var tx = Transaction.openRoot()) {
            Objects.requireNonNull(cube.sidedEnergy(Direction.UP)).insert(8_000, tx);
            tx.commit();
        }
        ItemStack terminal = new ItemStack(ModContent.WIRELESS_MATTER_TERMINAL.get());
        cube.setItem(0, terminal);
        cube.serverTick();
        ItemStack charged = cube.getItem(0).copy();
        h.assertTrue(WirelessMatterTerminalItem.energy(charged) == WirelessMatterTerminalItem.MAX_CHARGE_RATE,
                "Wireless Matter Terminal did not charge inside Energy Cube: " + WirelessMatterTerminalItem.energy(charged));

        // 2. Build Matter Network with Drive + Synthesizer + powered Wireless Access Point
        BlockPos apPos = new BlockPos(4, 2, 2);
        BlockPos cablePos = new BlockPos(4, 2, 3);
        BlockPos drivePos = new BlockPos(3, 2, 3);
        BlockPos synthPos = new BlockPos(5, 2, 3);
        h.setBlock(apPos, ModContent.WIRELESS_ACCESS_POINT.get().defaultBlockState().setValue(WirelessAccessPointBlock.FACING, Direction.NORTH));
        var ap = h.getBlockEntity(apPos, WirelessAccessPointBlockEntity.class);
        ap.placedBy(owner);
        h.setBlock(cablePos, ModContent.MATTER_CABLE.get());
        CableOwners.set(h.getLevel(), h.absolutePos(cablePos), owner.getUUID());
        var drive = placeMachine(h, drivePos, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(MachineCellAccess.slotForBay(MachineKind.DRIVE, 0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        var synth = placeMachine(h, synthPos, MachineKind.SYNTHESIZER, Direction.NORTH, owner);

        try (var tx = Transaction.openRoot()) {
            h.assertTrue(Objects.requireNonNull(ap.sidedEnergy(Direction.SOUTH)).insert(1_000, tx) == 1_000,
                    "Wireless Access Point back port did not accept FE");
            tx.commit();
        }
        ap.serverTick();
        h.assertTrue(ap.powered() && h.getBlockState(apPos).getValue(WirelessAccessPointBlock.ACTIVE),
                "Powered Wireless Access Point did not enter ACTIVE state");

        // 3. Bind terminal to AP and verify wireless MachineMenu + remote device management + slot lock
        charged.set(WirelessTerminalData.LINK.get(), WirelessTerminalData.of(h.getLevel().dimension(), h.absolutePos(apPos)));
        owner.getInventory().setItem(0, charged);
        owner.setPos(h.absolutePos(apPos).getX() + 10.5, h.absolutePos(apPos).getY(), h.absolutePos(apPos).getZ() + 0.5);

        var anchor = new TerminalAnchor.Wireless(ap, 0);
        h.assertTrue(TerminalNetwork.isValidAnchor(anchor, owner), "Wireless anchor should be valid within 128 blocks");
        h.assertTrue(TerminalNetwork.devices(anchor, owner, true).contains(synth),
                "Wireless Access Point did not discover network Synthesizer");
        h.assertTrue(TerminalNetwork.canManage(anchor, synth, owner),
                "Wireless anchor could not manage network Synthesizer");

        var wirelessMenu = new MachineMenu(7, owner.getInventory(), anchor);
        h.assertTrue(wirelessMenu.wireless() && !wirelessMenu.remote() && wirelessMenu.kind() == MachineKind.TERMINAL
                && wirelessMenu.stillValid(owner), "Wireless root MachineMenu invalid");

        var remoteMenu = new MachineMenu(8, owner.getInventory(), synth, anchor);
        h.assertTrue(remoteMenu.remote() && !remoteMenu.wireless() && remoteMenu.kind() == MachineKind.SYNTHESIZER
                && remoteMenu.stillValid(owner), "Remote Synthesizer menu opened via wireless anchor invalid");
        // Hotbar slot 0 (index FIRST_PLAYER_SLOT + 27 = 41) must be locked against pickup while in remote session!
        h.assertTrue(!remoteMenu.getSlot(MachineMenu.FIRST_PLAYER_SLOT + 27).mayPickup(owner),
                "Locked wireless terminal hotbar slot allowed pickup during remote session");

        // 4. Wireless Booster range scaling (128 -> 512 blocks) and Terminal dock auto-bind + charge
        owner.setPos(h.absolutePos(apPos).getX() + 300.5, h.absolutePos(apPos).getY(), h.absolutePos(apPos).getZ() + 0.5);
        h.assertTrue(!TerminalNetwork.isValidAnchor(anchor, owner), "Base AP without boosters should reject 300-block distance");
        ap.setItem(WirelessAccessPointBlockEntity.SLOT_BOOSTER, new ItemStack(ModContent.WIRELESS_BOOSTER.get(), 16));
        h.assertTrue(ap.effectiveRange() == WirelessAccessPointBlockEntity.MAX_RANGE,
                "16 Wireless Boosters did not scale AP range to 512: " + ap.effectiveRange());
        h.assertTrue(TerminalNetwork.isValidAnchor(anchor, owner), "Boosted AP should accept 300-block distance");

        try (var tx = Transaction.openRoot()) {
            Objects.requireNonNull(ap.sidedEnergy(Direction.SOUTH)).insert(16_000, tx);
            tx.commit();
        }
        ItemStack freshTerminal = new ItemStack(ModContent.WIRELESS_MATTER_TERMINAL.get());
        ap.setItem(WirelessAccessPointBlockEntity.SLOT_TERMINAL, freshTerminal);
        ap.serverTick();
        ItemStack dockedTerminal = ap.getItem(WirelessAccessPointBlockEntity.SLOT_TERMINAL);
        h.assertTrue(dockedTerminal.has(WirelessTerminalData.LINK.get())
                        && WirelessMatterTerminalItem.energy(dockedTerminal) > 0,
                "AP terminal dock did not auto-bind and charge inserted terminal");

        // 5. Cross-mod discharging of Wireless Matter Terminal into an empty Energy Cube Discharge Slot
        BlockPos drainCubePos = new BlockPos(2, 2, 4);
        h.setBlock(drainCubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get());
        var drainCube = h.getBlockEntity(drainCubePos, EnergyCubeBlockEntity.class);
        drainCube.placedBy(owner);
        int dischargeSlot = drainCube.tier().chargingSlots();
        drainCube.setItem(dischargeSlot, charged.copy());
        drainCube.serverTick();
        h.assertTrue(drainCube.energy() > 0
                        && WirelessMatterTerminalItem.energy(drainCube.getItem(dischargeSlot)) < WirelessMatterTerminalItem.energy(charged),
                "Energy Cube Discharge Slot did not extract FE from Wireless Matter Terminal");
        h.succeed();
    }

    private static void bidirectionalCubeAntiOscillation(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos posA = new BlockPos(2, 2, 2);
        BlockPos posB = new BlockPos(2, 2, 3);
        h.setBlock(posA, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get().defaultBlockState().setValue(EnergyCubeBlock.FACING, Direction.SOUTH));
        h.setBlock(posB, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get().defaultBlockState().setValue(EnergyCubeBlock.FACING, Direction.NORTH));
        var cubeA = h.getBlockEntity(posA, EnergyCubeBlockEntity.class);
        var cubeB = h.getBlockEntity(posB, EnergyCubeBlockEntity.class);
        cubeA.placedBy(owner);
        cubeB.placedBy(owner);
        owner.setPos(h.absolutePos(posA).getX() + 0.5, h.absolutePos(posA).getY(), h.absolutePos(posA).getZ() + 0.5);
        cubeA.cycleSide(owner, MachinePortMode.Side.FRONT);
        cubeB.cycleSide(owner, MachinePortMode.Side.FRONT);
        h.assertTrue(cubeA.sideMode(MachinePortMode.Side.FRONT) == dev.latrox.rematter.energy.EnergyCubeSideMode.INPUT_OUTPUT
                && cubeB.sideMode(MachinePortMode.Side.FRONT) == dev.latrox.rematter.energy.EnergyCubeSideMode.INPUT_OUTPUT,
                "Cubes did not switch facing ports to INPUT_OUTPUT");

        try (var tx = Transaction.openRoot()) {
            Objects.requireNonNull(cubeA.sidedEnergy(Direction.UP)).insert(2_048, tx);
            tx.commit();
        }
        h.assertTrue(h.getBlockState(posA).getValue(EnergyCubeBlock.CHARGE) >= 1,
                "Non-zero Energy Cube FE must immediately show CHARGE >= 1 on block state");
        // Tick A then B in the same server tick: A pushes 2048 FE to B, and B must NOT bounce it back to A!
        cubeA.serverTick();
        cubeB.serverTick();
        h.assertTrue(cubeA.energy() == 0 && cubeB.energy() == 2_048,
                "Bidirectional INPUT_OUTPUT cubes bounced FE back on same tick: A=" + cubeA.energy() + ", B=" + cubeB.energy());

        // Verify EnergyCubeItem Capabilities.Energy.ITEM charges and discharges in item form!
        BlockPos posC = new BlockPos(4, 2, 2);
        h.setBlock(posC, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get());
        var cubeC = h.getBlockEntity(posC, EnergyCubeBlockEntity.class);
        cubeC.placedBy(owner);
        try (var tx = Transaction.openRoot()) {
            Objects.requireNonNull(cubeC.sidedEnergy(Direction.UP)).insert(2_048, tx);
            tx.commit();
        }
        var basicCubeItem = (dev.latrox.rematter.energy.EnergyCubeItem) ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get().asItem();
        cubeC.setItem(0, new ItemStack(basicCubeItem));
        cubeC.serverTick();
        ItemStack chargedCubeItem = cubeC.getItem(0).copy();
        h.assertTrue(basicCubeItem.storedEnergy(chargedCubeItem) == 2_048 && cubeC.energy() == 0,
                "EnergyCubeItem did not charge inside Energy Cube Charge Slot");

        BlockPos posD = new BlockPos(4, 2, 4);
        h.setBlock(posD, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get());
        var cubeD = h.getBlockEntity(posD, EnergyCubeBlockEntity.class);
        cubeD.placedBy(owner);
        cubeD.setItem(cubeD.tier().chargingSlots(), chargedCubeItem);
        cubeD.serverTick();
        h.assertTrue(cubeD.energy() == 2_048 && basicCubeItem.storedEnergy(cubeD.getItem(cubeD.tier().chargingSlots())) == 0,
                "Energy Cube Discharge Slot did not discharge EnergyCubeItem");
        h.succeed();
    }

    private static void unformedReactorSingleConfigurationPage(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos controllerPos = new BlockPos(3, 2, 2);
        var reactor = placeMachine(h, controllerPos, MachineKind.INDUSTRIAL_REACTOR, Direction.NORTH, owner);
        reactor.setItem(MachineCellAccess.EFFICIENCY_SLOT, new ItemStack(ModContent.EFFICIENCY_UPGRADE.get()));
        owner.setPos(h.absolutePos(controllerPos).getX() + 0.5, h.absolutePos(controllerPos).getY(), h.absolutePos(controllerPos).getZ() + 0.5);

        var serverMenu = new MachineMenu(12, owner.getInventory(), reactor);
        owner.containerMenu = serverMenu;
        h.assertTrue(serverMenu.view() == MachineMenu.View.CONFIGURATION,
                "Server menu for unformed Industrial Reactor must start on CONFIGURATION immediately");
        var unformedSnapshot = reactor.snapshot(12);
        h.assertTrue(!unformedSnapshot.structure().formed(), "Standalone controller without shell must be unformed");

        var initBuf = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        MachineMenu.writeMachineInit(initBuf, reactor, false, -1);
        var clientMenu = new MachineMenu(12, owner.getInventory(), initBuf);
        h.assertTrue(clientMenu.kind() == MachineKind.INDUSTRIAL_REACTOR,
                "Frame-0 client menu must know controller kind before separate snapshot packet arrives");
        h.assertTrue(clientMenu.unformedIndustrial(),
                "Frame-0 client menu must detect unformed Industrial Reactor before separate snapshot packet arrives");
        h.assertTrue(clientMenu.view() == MachineMenu.View.CONFIGURATION,
                "Frame-0 client menu must open directly on CONFIGURATION page without flickering MAIN");
        h.assertTrue(clientMenu.extras().has("blueprint") && clientMenu.extras().has("blueprintBom"),
                "Frame-0 client menu must include saved blueprint and BOM before separate extras packet arrives");
        clientMenu.setView(MachineMenu.View.MAIN);
        h.assertTrue(clientMenu.view() == MachineMenu.View.CONFIGURATION,
                "Unformed Industrial Reactor must remain locked to single CONFIGURATION page");
        for (int i = 0; i < clientMenu.slots.size(); i++) {
            h.assertTrue(!clientMenu.slots.get(i).isActive(),
                    "No inventory or upgrade slot may remain active on single unformed CONFIGURATION page (slot " + i + ")");
        }

        // Toggling structure preview works directly from the single configuration page
        boolean initialPreview = reactor.structurePreviewEnabled();
        h.assertTrue(serverMenu.clickMenuButton(owner, MachineMenu.TOGGLE_STRUCTURE_PREVIEW),
                "TOGGLE_STRUCTURE_PREVIEW button click failed on unformed Industrial Reactor");
        h.assertTrue(reactor.structurePreviewEnabled() != initialPreview,
                "Structure preview state did not toggle on unformed Industrial Reactor");

        // Once the structure forms, accepting the formed snapshot unlocks tabs and switches to MAIN
        var formedView = new MachineSnapshot.StructureView(3, 3, 4, 2, 1, "ready", 0, 0, 0, 1, 1, 0);
        var formedSnapshot = new MachineSnapshot(
                12, MachineKind.INDUSTRIAL_REACTOR.ordinal(), "choose_process",
                unformedSnapshot.selected(), unformedSnapshot.selectedId(), unformedSnapshot.choiceCount(),
                unformedSnapshot.sampleBatchSize(), unformedSnapshot.used(), unformedSnapshot.capacity(),
                unformedSnapshot.automation(), unformedSnapshot.running(),
                unformedSnapshot.fluidAmount(), unformedSnapshot.fluidId(), unformedSnapshot.repeat(), unformedSnapshot.queuedBatches(),
                unformedSnapshot.preview(), unformedSnapshot.storageSource(), formedView,
                unformedSnapshot.network(), unformedSnapshot.networkJobs(),
                unformedSnapshot.backend(), unformedSnapshot.orders(), unformedSnapshot.cells());
        clientMenu.acceptSnapshot(formedSnapshot);
        h.assertTrue(!clientMenu.unformedIndustrial() && clientMenu.view() == MachineMenu.View.MAIN,
                "Formed Industrial Reactor must automatically unlock and switch to MAIN page");
        clientMenu.setView(MachineMenu.View.CONFIGURATION);
        h.assertTrue(clientMenu.view() == MachineMenu.View.CONFIGURATION,
                "Formed Industrial Reactor must allow switching between MAIN and CONFIGURATION");
        h.succeed();
    }
}
