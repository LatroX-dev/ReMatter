package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeTier;
import dev.latrox.rematter.energy.EnergyMatrixCellTier;
import dev.latrox.rematter.energy.EnergyMatrixProviderTier;
import dev.latrox.rematter.network.MachineActionPayload;
import dev.latrox.rematter.network.TerminalDevicesPayload;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlock;
import dev.latrox.rematter.primal.DimensionalEntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinkBlockEntity;
import dev.latrox.rematter.primal.EntangledLinkMenu;
import dev.latrox.rematter.primal.EntangledLinks;
import dev.latrox.rematter.primal.PrimalContent;
import java.util.LinkedHashMap;
import java.util.List;
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
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Server GameTests covering:
 * 1. Cross-dimension Dimensional Entangled Link (Overworld <-> Nether Matter + FE at Resonant rates, 0 BlockEntity ticks, unloaded safety).
 * 2. Wireless Access Point Dimensional Card (+64 FE/t, unlimited cross-dim reach) & Anchor Upgrade.
 * 3. Spatial Anchor (1x1 / 3x3, FE upkeep, Redstone gating) + Machine Anchor Upgrade + 16-chunk per-player quota enforcement.
 * 4. Custom device labels across UI actions, Anvil/DataComponents placement & drops, Terminal Devices listing, and base Entangled Link 64-block range.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class DimensionalAndAnchorGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static {
        TESTS.put("dimensional_entangled_link_cross_dimension_matter_and_energy", DimensionalAndAnchorGameTests::dimensionalLinkCrossDimension);
        TESTS.put("wireless_dimensional_card_and_anchor_upgrade_cross_dimension", DimensionalAndAnchorGameTests::wirelessDimensionalCardAndAnchor);
        TESTS.put("spatial_anchor_machine_anchor_and_quota_enforcement", DimensionalAndAnchorGameTests::spatialAnchorAndQuota);
        TESTS.put("custom_device_names_and_entangled_64_block_range", DimensionalAndAnchorGameTests::customNamesAndRange);
        TESTS.put("legacy_15_slot_machine_migrates_without_unsupported_data", DimensionalAndAnchorGameTests::legacy15SlotMigration);
        TESTS.put("matter_cable_relay_auto_aligns_and_connects_in_cable_gap", DimensionalAndAnchorGameTests::relayAutoPlacement);
        TESTS.put("compact_redstone_port_and_energy_matrix_power_output_port", DimensionalAndAnchorGameTests::compactRedstoneAndMatrixPowerOutput);
        TESTS.put("chunk_map_snapshot_and_spatial_anchor_32bit_energy", DimensionalAndAnchorGameTests::chunkMapAndSpatialAnchor32BitEnergy);
        TESTS.put("entangled_link_menu_rename_and_unpair", DimensionalAndAnchorGameTests::entangledLinkMenuAndUnpair);
        TESTS.put("v37_cable_power_glow_port_faces_and_custom_names", DimensionalAndAnchorGameTests::v37CableGlowFacesAndNames);
    }

    private DimensionalAndAnchorGameTests() {}

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("rematter_tests", path); }

    @SubscribeEvent
    public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach((name, fn) -> helper.register(id(name), fn)));
    }

    @SubscribeEvent
    public static void tests(RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(id("dimensional_anchor_environment"));
        TESTS.forEach((name, fn) -> event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                new TestData<>(env, id("empty"), 600, 0, true, Rotation.NONE, false, 1, 1, true, 2))));
    }

    private static MachineBlockEntity placeMachine(ServerLevel level, BlockPos pos, MachineKind kind, Direction facing, Player owner) {
        level.setBlock(pos, ModContent.MACHINES.get(kind).get().defaultBlockState().setValue(MachineBlock.FACING, facing), Block.UPDATE_ALL);
        var be = (MachineBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos));
        be.placedBy(owner);
        return be;
    }

    private static void fillCube(EnergyCubeBlockEntity cube, int amount) {
        try (var tx = Transaction.openRoot()) {
            Objects.requireNonNull(cube.sidedEnergy(Direction.UP)).insert(amount, tx);
            tx.commit();
        }
    }

    private static void dimensionalLinkCrossDimension(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel overworld = h.getLevel();
        ServerLevel nether = Objects.requireNonNull(overworld.getServer().getLevel(Level.NETHER), "Nether level must exist");

        // Overworld side (relative to test origin)
        BlockPos owDrivePos = h.absolutePos(new BlockPos(1, 2, 2));
        BlockPos owCablePos = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos owLinkPos = h.absolutePos(new BlockPos(3, 2, 2));
        BlockPos owCubePos = h.absolutePos(new BlockPos(2, 2, 3));

        // Nether side (high isolated test coordinates, force-loaded briefly via Anchor Upgrade then cleaned up)
        BlockPos neLinkPos = new BlockPos(200, 80, 200);
        BlockPos neCablePos = new BlockPos(201, 80, 200);
        BlockPos neMachinePos = new BlockPos(202, 80, 200);
        BlockPos neCubePos = new BlockPos(201, 80, 201);

        // Ensure Nether chunk is loaded for placement
        nether.getChunk(neLinkPos);

        try {
            var owDrive = placeMachine(overworld, owDrivePos, MachineKind.DRIVE, Direction.NORTH, owner);
            owDrive.setItem(owDrive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_16M.get()));
            overworld.setBlock(owCablePos, ModContent.RESONANT_MATTER_CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
            CableOwners.set(overworld, owCablePos, owner.getUUID());
            overworld.setBlock(owLinkPos, PrimalContent.DIMENSIONAL_ENTANGLED_LINK.get().defaultBlockState(), Block.UPDATE_ALL);
            var owLink = (DimensionalEntangledLinkBlockEntity) Objects.requireNonNull(overworld.getBlockEntity(owLinkPos));
            owLink.placedBy(owner, ItemStack.EMPTY);

            // Performance guarantee: 0 BlockEntityTicker!
            h.assertTrue(PrimalContent.DIMENSIONAL_ENTANGLED_LINK.get().getTicker(overworld,
                    overworld.getBlockState(owLinkPos), PrimalContent.DIMENSIONAL_ENTANGLED_LINK_ENTITY.get()) == null,
                    "Dimensional Entangled Link must have 0 BlockEntity ticks");

            overworld.setBlock(owCubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.RESONANT).get().defaultBlockState()
                    .setValue(MachineBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
            var owCube = (EnergyCubeBlockEntity) Objects.requireNonNull(overworld.getBlockEntity(owCubePos));
            owCube.placedBy(owner);
            fillCube(owCube, 2_000_000);

            // Nether side
            nether.setBlock(neLinkPos, PrimalContent.DIMENSIONAL_ENTANGLED_LINK.get().defaultBlockState(), Block.UPDATE_ALL);
            var neLink = (DimensionalEntangledLinkBlockEntity) Objects.requireNonNull(nether.getBlockEntity(neLinkPos));
            neLink.placedBy(owner, ItemStack.EMPTY);
            neLink.setItem(DimensionalEntangledLinkBlockEntity.SLOT_ANCHOR, new ItemStack(ModContent.ANCHOR_UPGRADE.get()));
            h.assertTrue(neLink.anchorStatus().active(), "Dimensional Link Anchor Upgrade must activate chunk ticket");

            nether.setBlock(neCablePos, ModContent.RESONANT_MATTER_CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
            CableOwners.set(nether, neCablePos, owner.getUUID());
            var neMachine = placeMachine(nether, neMachinePos, MachineKind.REACTOR, Direction.NORTH, owner);
            neMachine.setCustomName("Nether Reactor");

            nether.setBlock(neCubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.RESONANT).get().defaultBlockState()
                    .setValue(MachineBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
            var neCube = (EnergyCubeBlockEntity) Objects.requireNonNull(nether.getBlockEntity(neCubePos));
            neCube.placedBy(owner);

            // Pair across dimensions!
            h.assertTrue(EntangledLinks.pairDimensional(overworld, owLinkPos, nether, neLinkPos, owner),
                    "Pairing Dimensional Entangled Links across Overworld <-> Nether must succeed");
            h.assertTrue(overworld.getBlockState(owLinkPos).getValue(DimensionalEntangledLinkBlock.LINKED),
                    "Paired Overworld link must set LINKED=true");
            h.assertTrue(nether.getBlockState(neLinkPos).getValue(DimensionalEntangledLinkBlock.LINKED),
                    "Paired Nether link must set LINKED=true");

            // Verify Matter network spans Overworld Drive <-> Nether Machine at Resonant 8,192 u/t!
            var neNet = neMachine.matterNetwork(true);
            h.assertTrue(neNet.ready(), "Nether machine must reach Overworld Drive via Dimensional Entangled Link");
            h.assertTrue(neNet.matterRate(neMachine) == 8_192,
                    "Dimensional Entangled Link must provide Resonant 8,192 u/t rate, got " + neNet.matterRate(neMachine));
            h.assertTrue(owDrive.matterNetwork(true).manageableMachines().stream()
                            .anyMatch(m -> m == neMachine && "Nether Reactor".equals(m.customName())),
                    "Overworld network must list remote Nether machine with its custom name");

            // Verify FE transfer crosses dimensions via Energy Cube -> MatterCablePower!
            owCube.serverTick();
            h.assertTrue(neCube.energy() > 0L,
                    "FE must transfer across Overworld -> Nether Dimensional Entangled Link (neCube=" + neCube.energy() + ")");

            // Verify unpairing / severed remote does NOT break local Overworld network!
            owLink.clearPair();
            neLink.clearPair();
            h.assertTrue(owDrive.matterNetwork(true).ready(), "Local Overworld Drive must remain healthy after unpairing remote link");
            h.assertFalse(neMachine.matterNetwork(true).ready(), "Unpaired Nether machine must lose Overworld Drive access");
        } finally {
            ChunkAnchorManager.release(nether, neLinkPos);
            ChunkAnchorManager.release(overworld, owLinkPos);
            for (BlockPos p : List.of(neLinkPos, neCablePos, neMachinePos, neCubePos)) {
                nether.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        h.succeed();
    }

    private static void wirelessDimensionalCardAndAnchor(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel overworld = h.getLevel();
        BlockPos apPos = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos drivePos = h.absolutePos(new BlockPos(2, 1, 2));

        try {
            var drive = placeMachine(overworld, drivePos, MachineKind.DRIVE, Direction.NORTH, owner);
            drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));

            overworld.setBlock(apPos, ModContent.WIRELESS_ACCESS_POINT.get().defaultBlockState(), Block.UPDATE_ALL);
            var ap = (WirelessAccessPointBlockEntity) Objects.requireNonNull(overworld.getBlockEntity(apPos));
            ap.placedBy(owner, ItemStack.EMPTY);
            ap.setCustomName("Main Base AP");
            h.assertTrue("Main Base AP".equals(ap.customName()), "Access Point custom name must persist");

            int baseDrain = ap.effectiveDrain();
            h.assertTrue(baseDrain == WirelessAccessPointBlockEntity.STANDBY_DRAIN,
                    "Base AP standby drain must equal STANDBY_DRAIN (8 FE/t), got " + baseDrain);
            // Move player 5,000 blocks away -> out of range without Dimensional Card!
            owner.setPos(apPos.getX() + 5000.0, apPos.getY(), apPos.getZ());
            h.assertFalse(ap.canReach(owner), "AP without Dimensional Card must reject player 5,000 blocks away");

            // Install 16 Wireless Boosters into unified range slot -> 104 FE/t drain!
            ap.setItem(WirelessAccessPointBlockEntity.SLOT_RANGE, new ItemStack(ModContent.WIRELESS_BOOSTER.get(), 16));
            h.assertTrue(ap.boosterCount() == 16 && !ap.hasDimensionalCard(), "Boosters must occupy the unified range slot");
            h.assertTrue(ap.effectiveDrain() == WirelessAccessPointBlockEntity.STANDBY_DRAIN + 16 * WirelessAccessPointBlockEntity.BOOSTER_DRAIN,
                    "16 Wireless Boosters must draw 104 FE/t, got " + ap.effectiveDrain());

            // Replace with Dimensional Card in the same unified slot -> unlimited range & 256 FE/t (no booster double-drain)!
            ap.setItem(WirelessAccessPointBlockEntity.SLOT_RANGE, new ItemStack(ModContent.DIMENSIONAL_CARD.get()));
            h.assertTrue(ap.hasDimensionalCard() && ap.boosterCount() == 0, "Dimensional Card must replace Boosters in unified range slot");
            h.assertTrue(ap.effectiveDrain() == WirelessAccessPointBlockEntity.DIMENSIONAL_DRAIN,
                    "Dimensional Card must draw exactly DIMENSIONAL_DRAIN (256 FE/t), got " + ap.effectiveDrain());
            h.assertTrue(ap.canReach(owner), "AP with Dimensional Card must reach player at 5,000 blocks (and any dimension)");

            // Install Anchor Upgrade while UNPOWERED -> must NOT force chunk until powered!
            ap.setItem(WirelessAccessPointBlockEntity.SLOT_ANCHOR, new ItemStack(ModContent.ANCHOR_UPGRADE.get()));
            h.assertTrue(ap.hasAnchorUpgrade() && !ap.anchorStatus().active() && "missing_power".equals(ap.anchorStatus().reason()),
                    "Unpowered AP Anchor Upgrade must stay inactive with missing_power, got " + ap.anchorStatus());
            h.assertTrue(ap.effectiveDrain() == WirelessAccessPointBlockEntity.DIMENSIONAL_DRAIN + WirelessAccessPointBlockEntity.ANCHOR_DRAIN,
                    "Anchor Upgrade must add +16 FE/t (272 FE/t total), got " + ap.effectiveDrain());

            // Power the Access Point -> Anchor Upgrade now forces AP's own 1x1 chunk!
            try (var tx = Transaction.openRoot()) {
                Objects.requireNonNull(ap.sidedEnergy(Direction.DOWN)).insert(4_000, tx);
                tx.commit();
            }
            ap.serverTick();
            h.assertTrue(ap.anchorStatus().active() && ap.anchorStatus().forcedChunks() == 1,
                    "Powered AP Anchor Upgrade must force exactly 1 chunk, got " + ap.anchorStatus());

            // Drain power to zero -> Anchor Upgrade must release the chunk ticket!
            for (int t = 0; t < 20; t++) ap.serverTick();
            h.assertFalse(ap.anchorStatus().active(), "Depleted AP must release its chunk ticket, got " + ap.anchorStatus());
            h.assertTrue("missing_power".equals(ap.anchorStatus().reason()),
                    "Depleted AP anchor must report missing_power, got " + ap.anchorStatus().reason());

            // Restore power and remove Anchor Upgrade -> releases chunk immediately and drops drain back to 256 FE/t!
            try (var tx = Transaction.openRoot()) {
                Objects.requireNonNull(ap.sidedEnergy(Direction.DOWN)).insert(4_000, tx);
                tx.commit();
            }
            ap.serverTick();
            h.assertTrue(ap.anchorStatus().active(), "Repowered AP Anchor Upgrade must reactivate");
            ap.setItem(WirelessAccessPointBlockEntity.SLOT_ANCHOR, ItemStack.EMPTY);
            h.assertFalse(ap.anchorStatus().active(), "Removing AP Anchor Upgrade must release chunk ticket");
            h.assertTrue(ap.effectiveDrain() == WirelessAccessPointBlockEntity.DIMENSIONAL_DRAIN,
                    "Removing Anchor Upgrade must restore 256 FE/t drain");

            // Schema-1 migration: 4-slot dev payload holding BOTH Boosters and Dimensional Card must keep boosters losslessly!
            var legacyTag = new CompoundTag();
            legacyTag.putInt("schema", 1);
            legacyTag.putString("owner", owner.getUUID().toString());
            legacyTag.putInt("energy", 2_000);
            var legacyItems = List.of(
                    new ItemStack(ModContent.WIRELESS_BOOSTER.get(), 6),
                    ItemStack.EMPTY,
                    new ItemStack(ModContent.DIMENSIONAL_CARD.get(), 1),
                    new ItemStack(ModContent.ANCHOR_UPGRADE.get(), 1));
            var encodedItems = ItemStack.OPTIONAL_CODEC.listOf()
                    .encodeStart(overworld.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), legacyItems)
                    .getOrThrow();
            legacyTag.put("items", encodedItems);
            ap.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, overworld.registryAccess(), legacyTag));
            h.assertTrue(ap.hasDimensionalCard() && ap.hasAnchorUpgrade(),
                    "Schema-1 migration must preserve Dimensional Card and Anchor Upgrade");
            int droppedBoosters = ap.dropContentsList().stream()
                    .filter(s -> s.is(ModContent.WIRELESS_BOOSTER.get())).mapToInt(ItemStack::getCount).sum();
            h.assertTrue(droppedBoosters == 6,
                    "Schema-1 migration must retain displaced boosters in overflow instead of deleting them, got " + droppedBoosters);
            // Also verify 2-slot v0.10.0 Schema-1 payload (only Boosters + Terminal) migrates cleanly!
            var v010Tag = new CompoundTag();
            v010Tag.putInt("schema", 1);
            v010Tag.putString("owner", owner.getUUID().toString());
            var v010Items = List.of(new ItemStack(ModContent.WIRELESS_BOOSTER.get(), 4), ItemStack.EMPTY);
            v010Tag.put("items", ItemStack.OPTIONAL_CODEC.listOf()
                    .encodeStart(overworld.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), v010Items)
                    .getOrThrow());
            ap.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, overworld.registryAccess(), v010Tag));
            h.assertTrue(ap.boosterCount() == 4 && !ap.hasDimensionalCard(),
                    "v0.10.0 2-slot Schema-1 migration must preserve 4 boosters cleanly");
        } finally {
            ChunkAnchorManager.release(overworld, apPos);
        }
        h.succeed();
    }

    private static void spatialAnchorAndQuota(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = h.getLevel();

        BlockPos anchor1Pos = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos rsPos = h.absolutePos(new BlockPos(3, 2, 2));
        // Place second anchor far enough away (112 blocks = 7 chunks away) so 5x5 + 3x3 chunk areas don't overlap!
        BlockPos anchor2Pos = anchor1Pos.offset(112, 0, 0);
        BlockPos machinePos = h.absolutePos(new BlockPos(5, 2, 5));
        level.getChunk(anchor2Pos);

        try {
            // 1. Spatial Anchor #1: 1x1 mode (16 FE/t, 1 chunk)
            level.setBlock(anchor1Pos, ModContent.SPATIAL_ANCHOR.get().defaultBlockState(), Block.UPDATE_ALL);
            var a1 = (SpatialAnchorBlockEntity) Objects.requireNonNull(level.getBlockEntity(anchor1Pos));
            a1.placedBy(owner, ItemStack.EMPTY);
            a1.serverTick();
            h.assertTrue("missing_power".equals(a1.anchorStatus().reason()) && !a1.anchorStatus().active(),
                    "Unpowered Spatial Anchor must report missing_power, got " + a1.anchorStatus().reason());

            a1.setEnergyForTest(50_000);
            a1.serverTick();
            h.assertTrue(a1.anchorStatus().active() && a1.anchorStatus().forcedChunks() == 1,
                    "Powered 1x1 Spatial Anchor must force 1 chunk, got " + a1.anchorStatus());
            h.assertTrue(a1.energy() == 50_000 - SpatialAnchorBlockEntity.DRAIN_1X1,
                    "1x1 Spatial Anchor must consume 16 FE/t, remaining=" + a1.energy());

            // 2. Switch to 3x3 mode (144 FE/t, 9 chunks)
            a1.configure(1, RedstoneMode.IGNORED, "Base 3x3");
            int before3x3 = a1.energy();
            a1.serverTick();
            h.assertTrue(a1.anchorStatus().active() && a1.anchorStatus().forcedChunks() == 9,
                    "3x3 Spatial Anchor must force 9 chunks, got " + a1.anchorStatus());
            h.assertTrue(before3x3 - a1.energy() == SpatialAnchorBlockEntity.DRAIN_3X3,
                    "3x3 Spatial Anchor must consume 144 FE/t");

            // 3. Switch to 5x5 mode (400 FE/t, 25 chunks) + test custom 5-chunk mask (80 FE/t)!
            int customPlus = SpatialAnchorBlockEntity.MASK_1X1 | (1 << 7) | (1 << 11) | (1 << 13) | (1 << 17);
            a1.configureMask(customPlus, RedstoneMode.IGNORED, "Base Plus");
            int beforeCustom = a1.energy();
            a1.serverTick();
            h.assertTrue(a1.anchorStatus().active() && a1.anchorStatus().forcedChunks() == 5,
                    "Custom 5-chunk mask must force 5 chunks, got " + a1.anchorStatus());
            h.assertTrue(beforeCustom - a1.energy() == 5 * SpatialAnchorBlockEntity.DRAIN_PER_CHUNK,
                    "Custom 5-chunk mask must consume 80 FE/t");

            a1.configure(2, RedstoneMode.IGNORED, "Base 5x5");
            int before5x5 = a1.energy();
            a1.serverTick();
            h.assertTrue(a1.anchorStatus().active() && a1.anchorStatus().forcedChunks() == 25,
                    "5x5 Spatial Anchor must force 25 chunks, got " + a1.anchorStatus());
            h.assertTrue(before5x5 - a1.energy() == SpatialAnchorBlockEntity.DRAIN_5X5,
                    "5x5 Spatial Anchor must consume 400 FE/t");

            // 4. Second non-overlapping 3x3 Spatial Anchor exceeds 32-chunk default limit (25 + 9 = 34 > 32) -> "limit"!
            level.setBlock(anchor2Pos, ModContent.SPATIAL_ANCHOR.get().defaultBlockState(), Block.UPDATE_ALL);
            var a2 = (SpatialAnchorBlockEntity) Objects.requireNonNull(level.getBlockEntity(anchor2Pos));
            a2.placedBy(owner, ItemStack.EMPTY);
            a2.setEnergyForTest(50_000);
            a2.configure(1, RedstoneMode.IGNORED, "Outpost 3x3");
            a2.serverTick();
            h.assertFalse(a2.anchorStatus().active(), "Second 3x3 anchor (34 > 32 chunks) must be blocked by quota");
            h.assertTrue("limit".equals(a2.anchorStatus().reason()),
                    "Quota overflow must report 'limit', got " + a2.anchorStatus().reason());

            // Switching a2 to 1x1 (25 + 1 = 26 <= 32) succeeds cleanly!
            a2.configure(0, RedstoneMode.IGNORED, "Outpost 1x1");
            a2.serverTick();
            h.assertTrue(a2.anchorStatus().active() && a2.anchorStatus().forcedChunks() == 1,
                    "Downgrading second anchor to 1x1 (26/32) must activate");

            // 5. Redstone LOW pauses anchor #1 and frees its 25 chunks!
            a1.configure(2, RedstoneMode.LOW, "Base 5x5");
            level.setBlock(rsPos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            a1.serverTick();
            h.assertFalse(a1.anchorStatus().active(), "Redstone LOW + signal must pause Spatial Anchor");
            h.assertTrue("redstone_paused".equals(a1.anchorStatus().reason()),
                    "Expected redstone_paused, got " + a1.anchorStatus().reason());
            h.assertTrue(ChunkAnchorManager.ownerChunkCount(level.getServer(), owner.getUUID()) == 1,
                    "Paused 5x5 anchor must release its 25 chunks immediately");

            // 6. Machine Anchor Upgrade on a MachineBlockEntity
            var machine = placeMachine(level, machinePos, MachineKind.DECOMPOSER, Direction.NORTH, owner);
            machine.setItem(MachineCellAccess.ANCHOR_SLOT, new ItemStack(ModContent.ANCHOR_UPGRADE.get()));
            h.assertTrue(machine.hasAnchorUpgrade() && machine.anchorStatus().active() && machine.anchorStatus().forcedChunks() == 1,
                    "Machine Anchor Upgrade must force the machine's 1x1 chunk");
            machine.setItem(MachineCellAccess.ANCHOR_SLOT, ItemStack.EMPTY);
            h.assertFalse(machine.anchorStatus().active(), "Removing Machine Anchor Upgrade must release its chunk");
        } finally {
            ChunkAnchorManager.release(level, anchor1Pos);
            ChunkAnchorManager.release(level, anchor2Pos);
            ChunkAnchorManager.release(level, machinePos);
            level.setBlock(anchor2Pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(rsPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        h.succeed();
    }

    private static void customNamesAndRange(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = h.getLevel();

        // 1. Base Entangled Link 64-block range check
        BlockPos p0 = new BlockPos(0, 64, 0);
        h.assertTrue(EntangledLinks.withinRange(p0, new BlockPos(64, 64, 0)), "Base Entangled Link must allow 64 blocks");
        h.assertFalse(EntangledLinks.withinRange(p0, new BlockPos(65, 64, 0)), "Base Entangled Link must reject 65 blocks");

        // 2. Machine custom name via SET_CUSTOM_NAME action, TerminalDevicesPayload, and drop/re-place preservation
        BlockPos drivePos = h.absolutePos(new BlockPos(1, 2, 2));
        BlockPos termPos = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos decompPos = h.absolutePos(new BlockPos(3, 2, 2));
        BlockPos relayPos = h.absolutePos(new BlockPos(4, 2, 2));

        var drive = placeMachine(level, drivePos, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        var terminal = placeMachine(level, termPos, MachineKind.TERMINAL, Direction.NORTH, owner);
        var decomp = placeMachine(level, decompPos, MachineKind.DECOMPOSER, Direction.NORTH, owner);
        // Connect Drive, Terminal, and Decomposer via Matter Cables along their back (SOUTH) side
        for (int dx = 1; dx <= 3; dx++) {
            BlockPos cPos = h.absolutePos(new BlockPos(dx, 2, 3));
            level.setBlock(cPos, ModContent.MATTER_CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
            CableOwners.set(level, cPos, owner.getUUID());
        }
        owner.setPos(termPos.getX() + 0.5, termPos.getY(), termPos.getZ() + 0.5);

        h.assertTrue(decomp.extensions().action(owner,
                new MachineActionPayload(0, MachineActionPayload.Action.SET_CUSTOM_NAME, "Iron Decomposer #1", 0)),
                "SET_CUSTOM_NAME action must succeed");
        h.assertTrue("Iron Decomposer #1".equals(decomp.customName()), "Machine customName must update");
        h.assertTrue("Iron Decomposer #1".equals(decomp.getDisplayName().getString()), "Machine getDisplayName must use customName");

        // Terminal devices list exposes customName and dimensionId!
        var listed = TerminalNetwork.devices(new TerminalAnchor.Wired(terminal), owner, true);
        var entry = listed.stream().filter(m -> m == decomp).findFirst().orElseThrow();
        var deviceRow = new TerminalDevicesPayload.Device(
                entry.getBlockPos(), entry.kind(), entry.work().status, "", 0, 0, 0, false,
                0L, 0L, false, 0, 1, entry.customName(),
                entry.getLevel().dimension().identifier().toString());
        h.assertTrue("Iron Decomposer #1".equals(deviceRow.customName()), "Terminal device row must carry customName");
        h.assertTrue("minecraft:overworld".equals(deviceRow.dimensionId()), "Terminal device row must carry dimensionId");

        // Drop preserves DataComponents.CUSTOM_NAME!
        var drops = Block.getDrops(level.getBlockState(decompPos), level, decompPos, decomp);
        h.assertTrue(!drops.isEmpty() && "Iron Decomposer #1".equals(CustomNames.fromStack(drops.getFirst())),
                "Machine drops must preserve DataComponents.CUSTOM_NAME");

        // Matter Cable Relay custom name + Anvil placement preservation
        ItemStack namedRelayStack = new ItemStack(ModContent.MATTER_CABLE_RELAY_ITEM.get());
        namedRelayStack.set(DataComponents.CUSTOM_NAME, Component.literal("Reactor Cutoff Relay"));
        level.setBlock(relayPos, ModContent.MATTER_CABLE_RELAY.get().defaultBlockState(), Block.UPDATE_ALL);
        var relay = (MatterCableRelayBlockEntity) Objects.requireNonNull(level.getBlockEntity(relayPos));
        relay.placedBy(owner, namedRelayStack);
        h.assertTrue("Reactor Cutoff Relay".equals(relay.customName()),
                "Matter Cable Relay must adopt DataComponents.CUSTOM_NAME on placement");

        // Entangled Link custom name persistence via setPlacedBy
        BlockPos linkPos = h.absolutePos(new BlockPos(5, 2, 2));
        level.setBlock(linkPos, PrimalContent.ENTANGLED_LINK.get().defaultBlockState(), Block.UPDATE_ALL);
        ItemStack namedLinkStack = new ItemStack(PrimalContent.ENTANGLED_LINK_ITEM.get());
        namedLinkStack.set(DataComponents.CUSTOM_NAME, Component.literal("Quarry Bridge"));
        PrimalContent.ENTANGLED_LINK.get().setPlacedBy(level, linkPos, level.getBlockState(linkPos), owner, namedLinkStack);
        var link = (EntangledLinkBlockEntity) Objects.requireNonNull(level.getBlockEntity(linkPos));
        h.assertTrue("Quarry Bridge".equals(link.customName()),
                "Entangled Link must adopt DataComponents.CUSTOM_NAME on placement");

        h.succeed();
    }

    private static void legacy15SlotMigration(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        var decomp = placeMachine(level, pos, MachineKind.DECOMPOSER, Direction.NORTH, owner);
        decomp.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        var saved = decomp.saveCustomOnly(level.registryAccess());
        // Simulate pre-0.50 15-slot save (remove the 16th slot entry from the saved items list).
        ListTag items = saved.getListOrEmpty("items");
        h.assertTrue(items.size() == MachineCellAccess.INVENTORY_SIZE,
                "Expected fresh save to have " + MachineCellAccess.INVENTORY_SIZE + " slots, got " + items.size());
        items.remove(MachineCellAccess.INVENTORY_SIZE - 1);
        saved.put("items", items);

        decomp.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        h.assertTrue(!"unsupported_data".equals(decomp.snapshot(0).status()),
                "Legacy 15-slot machine must migrate cleanly without unsupported_data, got " + decomp.snapshot(0).status());
        h.assertTrue(decomp.getItem(0).is(Items.IRON_INGOT) && decomp.getItem(0).getCount() == 4,
                "Migrated 15-slot machine must preserve slot 0 contents");
        h.assertTrue(decomp.getItem(MachineCellAccess.ANCHOR_SLOT).isEmpty(),
                "Migrated 15-slot machine must initialize empty Anchor slot");
        h.assertTrue(decomp.cyclePort(MachinePortMode.Side.LEFT, true),
                "Migrated machine must remain live and allow port cycling");
        h.succeed();
    }

    private static void relayAutoPlacement(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = h.getLevel();
        BlockPos drivePos = h.absolutePos(new BlockPos(1, 2, 2));
        BlockPos leftCable = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos gapPos = h.absolutePos(new BlockPos(3, 2, 2));
        BlockPos rightCable = h.absolutePos(new BlockPos(4, 2, 2));
        BlockPos termPos = h.absolutePos(new BlockPos(5, 2, 2));

        var drive = placeMachine(level, drivePos, MachineKind.DRIVE, Direction.NORTH, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        // Terminal only connects via its BACK face, so face EAST to connect WEST to rightCable.
        var term = placeMachine(level, termPos, MachineKind.TERMINAL, Direction.EAST, owner);
        level.setBlock(leftCable, ModContent.MATTER_CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(rightCable, ModContent.MATTER_CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
        CableOwners.set(level, leftCable, owner.getUUID());
        CableOwners.set(level, rightCable, owner.getUUID());
        level.setBlock(gapPos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        // Place relay by clicking the FLOOR (UP face) while standing in the gap between two East-West cables!
        var hit = new BlockHitResult(Vec3.atCenterOf(gapPos.below()), Direction.UP, gapPos.below(), false);
        var ctx = new BlockPlaceContext(level, owner, InteractionHand.MAIN_HAND,
                new ItemStack(ModContent.MATTER_CABLE_RELAY_ITEM.get()), hit);
        var placedState = ModContent.MATTER_CABLE_RELAY.get().getStateForPlacement(ctx);
        h.assertTrue(placedState.getValue(MatterCableRelayBlock.FACING).getAxis() == Direction.Axis.X,
                "Relay placed between East-West cables must auto-align along X axis, got "
                        + placedState.getValue(MatterCableRelayBlock.FACING));

        level.setBlock(gapPos, placedState, Block.UPDATE_ALL);
        ModContent.MATTER_CABLE_RELAY.get().setPlacedBy(level, gapPos, placedState, owner,
                new ItemStack(ModContent.MATTER_CABLE_RELAY_ITEM.get()));
        h.assertTrue(term.matterNetwork(true).ready(),
                "Auto-aligned Matter Cable Relay must immediately connect Drive and Terminal");
        h.succeed();
    }

    private static void compactRedstoneAndMatrixPowerOutput(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = h.getLevel();

        // 1. Compact machine dedicated REDSTONE side port!
        BlockPos machPos = h.absolutePos(new BlockPos(2, 2, 2));
        var decomp = placeMachine(level, machPos, MachineKind.DECOMPOSER, Direction.NORTH, owner);
        // Cycle LEFT side (viewer-left = EAST for NORTH-facing machine) until REDSTONE!
        for (int i = 0; i < 16 && decomp.portMode(MachinePortMode.Side.LEFT) != MachinePortMode.REDSTONE; i++) {
            decomp.cyclePort(MachinePortMode.Side.LEFT, true);
        }
        h.assertTrue(decomp.portMode(MachinePortMode.Side.LEFT) == MachinePortMode.REDSTONE,
                "Compact machine must support REDSTONE port mode, got " + decomp.portMode(MachinePortMode.Side.LEFT));
        Direction redstoneWorld = MachinePortMode.Side.LEFT.world(Direction.NORTH);
        Direction otherWorld = MachinePortMode.Side.RIGHT.world(Direction.NORTH);
        // Signal on non-REDSTONE side (RIGHT = WEST) must be ignored because LEFT is a dedicated REDSTONE port!
        level.setBlock(machPos.relative(otherWorld), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        h.assertFalse(decomp.isRedstonePowered(),
                "Compact machine with dedicated REDSTONE port must ignore redstone on non-REDSTONE sides");
        // Signal on dedicated REDSTONE side (LEFT = EAST) must power it!
        level.setBlock(machPos.relative(redstoneWorld), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(decomp.isRedstonePowered(),
                "Compact machine with dedicated REDSTONE port must respond to redstone on that side");
        level.setBlock(machPos.relative(otherWorld), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(machPos.relative(redstoneWorld), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        // 2. Energy Matrix explicit POWER (input-only) vs POWER_OUTPUT (output-only + active push) + port cycling!
        BlockPos origin = h.absolutePos(new BlockPos(4, 1, 2));
        for (int dx = 0; dx < 4; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                for (int dz = 0; dz < 3; dz++) {
                    boolean boundary = dx == 0 || dx == 3 || dy == 0 || dy == 2 || dz == 0 || dz == 2;
                    if (!boundary) continue;
                    boolean edge = (dy == 0 || dy == 2) || ((dx == 0 || dx == 3) && (dz == 0 || dz == 2));
                    level.setBlock(origin.offset(dx, dy, dz),
                            (edge ? ModContent.ENERGY_MATRIX_CASING.get() : ModContent.ENERGY_MATRIX_GLASS.get()).defaultBlockState(),
                            Block.UPDATE_ALL);
                }
            }
        }
        var matrixCtrl = placeMachine(level, origin.offset(1, 1, 0), MachineKind.ENERGY_MATRIX, Direction.NORTH, owner);
        level.setBlock(origin.offset(1, 1, 1), ModContent.ENERGY_MATRIX_CELLS.get(EnergyMatrixCellTier.RESONANT).get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.offset(2, 1, 1), ModContent.ENERGY_MATRIX_PROVIDERS.get(EnergyMatrixProviderTier.RESONANT).get().defaultBlockState(), Block.UPDATE_ALL);

        BlockPos inPortPos = origin.offset(0, 1, 1);
        BlockPos outPortPos = origin.offset(3, 1, 1);
        level.setBlock(inPortPos, ModContent.ENERGY_MATRIX_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(outPortPos, ModContent.ENERGY_MATRIX_PORT.get().defaultBlockState(), Block.UPDATE_ALL);
        var inPort = (MachinePortBlockEntity) Objects.requireNonNull(level.getBlockEntity(inPortPos));
        var outPort = (MachinePortBlockEntity) Objects.requireNonNull(level.getBlockEntity(outPortPos));
        inPort.refreshNow();
        outPort.refreshNow();
        h.assertTrue(matrixCtrl.industrialStructureReady(), "Energy Matrix must form");

        owner.setPos(inPortPos.getX() + 0.5, inPortPos.getY(), inPortPos.getZ() + 0.5);
        for (int i = 0; i < 12 && inPort.mode() != MachinePortMode.POWER; i++) inPort.cycleMode(owner, Direction.WEST);
        owner.setPos(outPortPos.getX() + 0.5, outPortPos.getY(), outPortPos.getZ() + 0.5);
        for (int i = 0; i < 12 && outPort.mode() != MachinePortMode.POWER_OUTPUT; i++) outPort.cycleMode(owner, Direction.EAST);
        h.assertTrue(inPort.mode() == MachinePortMode.POWER, "Matrix input port must reach POWER");
        h.assertTrue(outPort.mode() == MachinePortMode.POWER_OUTPUT, "Matrix output port must reach POWER_OUTPUT");

        // Feed 100k FE into POWER port, verify POWER_OUTPUT rejects insert & actively pushes into adjacent Energy Cube!
        var inCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, inPortPos, Direction.WEST));
        var outCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, outPortPos, Direction.EAST));
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(inCap.insert(100_000, tx) == 100_000, "POWER port must accept FE input");
            h.assertTrue(inCap.extract(1_000, tx) == 0, "POWER (input) port must block FE extraction");
            h.assertTrue(outCap.insert(1_000, tx) == 0, "POWER_OUTPUT port must reject FE input");
            tx.commit();
        }
        // Disable matrixAutoOutput to prove explicit POWER_OUTPUT port STILL pushes FE!
        matrixCtrl.extensions().action(owner,
                new MachineActionPayload(0, MachineActionPayload.Action.TOGGLE_MATRIX_OUTPUT, "", 0));
        BlockPos cubePos = outPortPos.east();
        level.setBlock(cubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.RESONANT).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        var cube = (EnergyCubeBlockEntity) Objects.requireNonNull(level.getBlockEntity(cubePos));
        cube.placedBy(owner);
        matrixCtrl.serverTick();
        h.assertTrue(cube.energy() > 0L,
                "Energy Matrix POWER_OUTPUT port must actively push FE even when matrixAutoOutput is off, cube=" + cube.energy());
        h.succeed();
    }

    private static void chunkMapAndSpatialAnchor32BitEnergy(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = h.getLevel();
        BlockPos anchorPos = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos otherPos = anchorPos.offset(32, 0, 0); // 2 chunks east
        level.getChunk(otherPos);

        try {
            level.setBlock(anchorPos, ModContent.SPATIAL_ANCHOR.get().defaultBlockState(), Block.UPDATE_ALL);
            var anchor = (SpatialAnchorBlockEntity) Objects.requireNonNull(level.getBlockEntity(anchorPos));
            anchor.placedBy(owner, ItemStack.EMPTY);
            anchor.setEnergyForTest(100_000); // > Short.MAX_VALUE (32,767)
            anchor.configure(1, RedstoneMode.IGNORED, "Map Test Anchor");
            anchor.serverTick();

            // Register a second anchor 2 chunks east so FLAG_COVERED_OTHER shows up in the 5x5 map!
            ChunkAnchorManager.sync(level, otherPos, owner.getUUID(), ChunkAnchorManager.square(otherPos, 0));

            // Verify SpatialAnchorMenu 32-bit energy!
            var menu = new SpatialAnchorMenu(1, owner.getInventory(), anchor);
            h.assertTrue(menu.energy() == anchor.energy() && menu.energy() > 90_000,
                    "SpatialAnchorMenu must preserve 32-bit energy > 32767, got " + menu.energy());

            // Verify ChunkMapSnapshot 5x5 capture!
            var snap = ChunkMapSnapshot.capture(level, anchorPos, owner.getUUID(),
                    ChunkAnchorManager.square(anchorPos, anchor.radius()), owner);
            int coveredThis = 0;
            int coveredOther = 0;
            for (int gz = 0; gz < ChunkMapSnapshot.GRID; gz++) {
                for (int gx = 0; gx < ChunkMapSnapshot.GRID; gx++) {
                    byte f = snap.flagAt(gx, gz);
                    if ((f & ChunkMapSnapshot.FLAG_COVERED_THIS) != 0) coveredThis++;
                    if ((f & ChunkMapSnapshot.FLAG_COVERED_OTHER) != 0) coveredOther++;
                }
            }
            h.assertTrue(coveredThis == 9, "3x3 Spatial Anchor map must mark 9 chunks as COVERED_THIS, got " + coveredThis);
            h.assertTrue(coveredOther == 1, "Other anchor 2 chunks east must appear as COVERED_OTHER, got " + coveredOther);
            h.assertTrue((snap.flagAt(ChunkMapSnapshot.HALF, ChunkMapSnapshot.HALF) & ChunkMapSnapshot.FLAG_LOADED) != 0,
                    "Center chunk must be marked FLAG_LOADED");
            h.assertTrue(snap.colorAt(ChunkMapSnapshot.HALF, ChunkMapSnapshot.HALF, 0, 0) != 0,
                    "Loaded center chunk must capture non-zero terrain map colors");
        } finally {
            ChunkAnchorManager.release(level, anchorPos);
            ChunkAnchorManager.release(level, otherPos);
        }
        h.succeed();
    }

    private static void entangledLinkMenuAndUnpair(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = h.getLevel();
        BlockPos posA = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos posB = h.absolutePos(new BlockPos(6, 2, 2));

        level.setBlock(posA, PrimalContent.ENTANGLED_LINK.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(posB, PrimalContent.ENTANGLED_LINK.get().defaultBlockState(), Block.UPDATE_ALL);
        var linkA = (EntangledLinkBlockEntity) Objects.requireNonNull(level.getBlockEntity(posA));
        var linkB = (EntangledLinkBlockEntity) Objects.requireNonNull(level.getBlockEntity(posB));
        linkA.placedBy(owner, ItemStack.EMPTY);
        linkB.placedBy(owner, ItemStack.EMPTY);
        h.assertTrue(EntangledLinks.pair(level, posA, posB, owner), "Regular Entangled Links must pair");

        owner.setPos(posA.getX() + 0.5, posA.getY(), posA.getZ() + 0.5);
        var menu = (EntangledLinkMenu) Objects.requireNonNull(linkA.createMenu(1, owner.getInventory(), owner));
        h.assertTrue(menu.hasPeer() && menu.linkState() == 1 && menu.distance() == 4,
                "EntangledLinkMenu must report linked state & distance 4, got state=" + menu.linkState() + " dist=" + menu.distance());

        linkA.setCustomName("Workshop Bridge");
        h.assertTrue("Workshop Bridge".equals(linkA.customName()), "Entangled Link rename must apply");

        linkA.unpairBoth(level);
        h.assertTrue(EntangledLinks.inspect(level, linkA).state() == EntangledLinks.LinkState.UNPAIRED,
                "unpairBoth must clear local Entangled Link");
        h.assertTrue(EntangledLinks.inspect(level, linkB).state() == EntangledLinks.LinkState.UNPAIRED,
                "unpairBoth must clear remote Entangled Link");
        h.succeed();
    }

    private static void v37CableGlowFacesAndNames(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = h.getLevel();

        // 1. Matter Cable POWERED state flips true when energized by FE and dims back to false after 45 idle ticks!
        BlockPos cubePos = h.absolutePos(new BlockPos(2, 2, 2));
        BlockPos cablePos = h.absolutePos(new BlockPos(3, 2, 2));
        BlockPos cable2Pos = h.absolutePos(new BlockPos(4, 2, 2));
        level.setBlock(cablePos, ModContent.RESONANT_MATTER_CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(cable2Pos, ModContent.RESONANT_MATTER_CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
        CableOwners.set(level, cablePos, owner.getUUID(), SecurityMode.PUBLIC);
        CableOwners.set(level, cable2Pos, owner.getUUID(), SecurityMode.PUBLIC);
        h.assertFalse(level.getBlockState(cablePos).getValue(MatterCableBlock.POWERED),
                "Fresh Matter Cable without FE must start unpowered (dark)");

        level.setBlock(cubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.BASIC).get().defaultBlockState()
                .setValue(dev.latrox.rematter.energy.EnergyCubeBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        var cube = (EnergyCubeBlockEntity) Objects.requireNonNull(level.getBlockEntity(cubePos));
        cube.placedBy(owner);
        cube.setCustomName("North Substation");
        h.assertTrue("North Substation".equals(cube.customName()), "Energy Cube customName must apply");
        var cubeCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, cubePos, Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(cubeCap.insert(5_000, tx) == 5_000, "Failed to charge test Energy Cube");
            tx.commit();
        }
        cube.serverTick();
        h.assertTrue(level.getBlockState(cablePos).getValue(MatterCableBlock.POWERED)
                        && level.getBlockState(cable2Pos).getValue(MatterCableBlock.POWERED),
                "Energized Matter Cable run must switch POWERED=true (bright)");

        // Verify Energy Cube & Matter Level Emitter custom names survive NBT reload
        CompoundTag savedCube = cube.saveCustomOnly(level.registryAccess());
        h.assertTrue("North Substation".equals(savedCube.getStringOr("custom_name", "")),
                "Energy Cube custom_name must serialize to NBT");

        BlockPos emitterPos = h.absolutePos(new BlockPos(6, 2, 2));
        level.setBlock(emitterPos, ModContent.MATTER_LEVEL_EMITTER.get().defaultBlockState(), Block.UPDATE_ALL);
        var emitter = (MatterLevelEmitterBlockEntity) Objects.requireNonNull(level.getBlockEntity(emitterPos));
        emitter.placedBy(owner);
        emitter.setCustomName("Iron Stock Alarm");
        CompoundTag savedEmitter = emitter.saveCustomOnly(level.registryAccess());
        h.assertTrue("Iron Stock Alarm".equals(savedEmitter.getStringOr("custom_name", "")),
                "Matter Level Emitter custom_name must serialize to NBT");

        // 2. Port-face enforcement: Links & Spatial Anchor expose LEFT/RIGHT/BACK/DOWN and block FRONT & UP; AP exposes DOWN only!
        BlockPos linkPos = h.absolutePos(new BlockPos(2, 2, 5));
        BlockPos dimPos = h.absolutePos(new BlockPos(4, 2, 5));
        BlockPos anchorPos = h.absolutePos(new BlockPos(6, 2, 5));
        BlockPos apPos = h.absolutePos(new BlockPos(8, 2, 5));
        level.setBlock(linkPos, PrimalContent.ENTANGLED_LINK.get().defaultBlockState()
                .setValue(dev.latrox.rematter.primal.EntangledLinkBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        level.setBlock(dimPos, PrimalContent.DIMENSIONAL_ENTANGLED_LINK.get().defaultBlockState()
                .setValue(DimensionalEntangledLinkBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        level.setBlock(anchorPos, ModContent.SPATIAL_ANCHOR.get().defaultBlockState()
                .setValue(SpatialAnchorBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        level.setBlock(apPos, ModContent.WIRELESS_ACCESS_POINT.get().defaultBlockState()
                .setValue(WirelessAccessPointBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);

        for (var pos : List.of(linkPos, dimPos, anchorPos)) {
            h.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, pos, Direction.NORTH) == null,
                    "Front showcase face must not expose Energy capability at " + pos);
            h.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, pos, Direction.UP) == null,
                    "Top showcase face must not expose Energy capability at " + pos);
            for (var open : List.of(Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN)) {
                h.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, pos, open) != null,
                        "Expected Energy capability on " + open + " at " + pos);
            }
        }
        h.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, apPos, Direction.DOWN) != null,
                "Wireless AP must expose Energy capability on DOWN");
        for (var closed : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP)) {
            h.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, apPos, closed) == null,
                    "Wireless AP must NOT expose Energy capability on " + closed);
        }

        // Remove power source and wait 46 ticks so the 40-tick hysteresis clears POWERED back to false!
        level.setBlock(cubePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertFalse(level.getBlockState(cablePos).getValue(MatterCableBlock.POWERED),
                            "Unpowered Matter Cable must dim back to POWERED=false after hysteresis");
                    h.assertFalse(level.getBlockState(cable2Pos).getValue(MatterCableBlock.POWERED),
                            "Downstream unpowered Matter Cable must dim back to POWERED=false");
                })
                .thenSucceed();
    }
}
