package dev.latrox.rematter.content;

import com.mojang.authlib.GameProfile;
import dev.latrox.rematter.celltransfer.*;
import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.network.*;
import dev.latrox.rematter.primal.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Manual, filtered two-Cell resource transfers over actual server blocks and persistent item components. */
@EventBusSubscriber(modid = "rematter")
public final class CellTransferStationGameTests {
    private static final SubstanceKey IRON = SubstanceKey.of("rematter:iron", Phase.SOLID),
            WATER = SubstanceKey.of("rematter:water", Phase.LIQUID),
            GAS = SubstanceKey.of("future:unknown_gas", Phase.GAS),
            COPPER = SubstanceKey.of("rematter:copper", Phase.SOLID);
    private static final Map<String, Consumer<GameTestHelper>> TESTS = Map.of(
            "cell_station_selected_sources_and_partitions", CellTransferStationGameTests::ordinary,
            "cell_station_primal_selection_is_explicit", CellTransferStationGameTests::primal,
            "cell_station_aliases_unknowns_and_cross_kind_are_safe", CellTransferStationGameTests::rejected,
            "cell_station_configuration_rejects_stale_foreign_and_distant_actions", CellTransferStationGameTests::permissions,
            "cell_station_portable_contents_and_visual_privacy", CellTransferStationGameTests::portable,
            "cell_station_type_full_target_accepts_only_existing_keys", CellTransferStationGameTests::types,
            "cell_station_long_unknown_keys_survive_without_ui_truncation", CellTransferStationGameTests::longKey,
            "cell_station_late_permission_change_rolls_back", CellTransferStationGameTests::rollback,
            "cell_station_swap_preserves_identities_and_reverses_direction", CellTransferStationGameTests::swapCells,
            "cell_station_world_and_menu_cell_insertion", CellTransferStationGameTests::worldAndMenuInsertion);

    private static Identifier id(String name) { return Identifier.fromNamespaceAndPath("rematter_tests", name); }
    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach((name, test) -> helper.register(id(name), test)));
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(id("cell_station_environment"));
        TESTS.forEach((name, test) -> event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                new TestData<>(env, id("empty"), 300, 0, true, Rotation.NONE, false, 1, 1, true, 2))));
    }

    private static CellTransferStationBlockEntity place(GameTestHelper h, BlockPos relative, Player owner) {
        h.setBlock(relative, ModContent.CELL_TRANSFER_STATION.get());
        var station = h.getBlockEntity(relative, CellTransferStationBlockEntity.class);
        station.placedBy(owner);
        owner.setPos(station.getBlockPos().getX() + .5, station.getBlockPos().getY(), station.getBlockPos().getZ() + .5);
        return station;
    }

    private static ItemStack cell(Map<SubstanceKey, Long> contents) {
        var stack = new ItemStack(ModContent.MATTER_CELL_4M.get());
        var store = new MatterInventory(MatterLimits.CELL_4M, 512);
        if (!store.apply(Map.of(), contents)) throw new IllegalArgumentException("Cell fixture exceeds capacity");
        stack.set(MatterCellData.TYPE.get(), MatterCellData.from(store));
        return stack;
    }

    private static ItemStack primal(long amount) {
        var stack = new ItemStack(PrimalContent.PRIMAL_CELL_16K.get());
        stack.set(PrimalCellData.TYPE.get(), PrimalCellData.from(new PrimalCellData.State(UUID.randomUUID(), amount, 0, Map.of())));
        return stack;
    }

    private static long amount(ItemStack cell, SubstanceKey key) {
        return ((MatterCellItem) cell.getItem()).open(cell).orElseThrow().amount(key);
    }

    private static boolean action(CellTransferStationBlockEntity station, Player player, CellTransferConfigPayload.Action action, int slot, SubstanceKey key, boolean value) {
        return station.configure(player, new CellTransferConfigPayload(1, station.revision(), action, slot, key, value));
    }

    private static void ordinary(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var station = place(h, new BlockPos(2, 2, 2), owner);
        station.setItem(0, cell(Map.of(IRON, 1000L, WATER, 200L, GAS, 100L, COPPER, 100L)));
        station.setItem(1, cell(Map.of()));
        h.assertTrue(action(station, owner, CellTransferConfigPayload.Action.CELL_TOGGLE, 1, IRON, false), "Could not configure Iron target");
        h.assertTrue(action(station, owner, CellTransferConfigPayload.Action.GLOBAL_CLEAR, -1, null, false), "Could not clear selection");
        action(station, owner, CellTransferConfigPayload.Action.GLOBAL_TOGGLE, -1, IRON, false);
        action(station, owner, CellTransferConfigPayload.Action.GLOBAL_TOGGLE, -1, WATER, false);
        var before = station.saveCustomOnly(h.getLevel().registryAccess());
        var preview = station.snapshot(1);
        h.assertTrue(before.equals(station.saveCustomOnly(h.getLevel().registryAccess())), "Preview changed persisted resources or policy");
        h.assertTrue(preview.movableOrdinary() == 1000 && preview.movablePrimal() == 0, "Target partition did not filter preview");
        var sourceId = station.getItem(0).get(MatterCellData.TYPE.get()).resolve(MatterLimits.CELL_4M).orElseThrow().cellId();
        h.assertTrue(station.transfer(owner, station.revision()) == 1000, "Selected filtered quantity was not exact");
        h.assertTrue(amount(station.getItem(1), IRON) == 1000 && amount(station.getItem(1), WATER) == 0, "Target Cell partition was ignored");
        h.assertTrue(amount(station.getItem(0), WATER) == 200 && amount(station.getItem(0), GAS) == 100 && amount(station.getItem(0), COPPER) == 100,
                "Unselected or unaccepted resources changed");
        h.assertTrue(sourceId.equals(station.getItem(0).get(MatterCellData.TYPE.get()).resolve(MatterLimits.CELL_4M).orElseThrow().cellId()), "Source identity changed");
        h.assertTrue(CellPartitionData.read(station.getItem(1)).orElseThrow().allowed().equals(Set.of(IRON)), "Cell partition was lost");
        h.succeed();
    }

    private static void primal(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var station = place(h, new BlockPos(2, 2, 2), owner);
        station.setItem(0, primal(3000));
        station.setItem(1, primal(0));
        action(station, owner, CellTransferConfigPayload.Action.GLOBAL_CLEAR, -1, null, false);
        action(station, owner, CellTransferConfigPayload.Action.GLOBAL_TOGGLE, -1, IRON, false);
        h.assertTrue(station.transfer(owner, station.revision()) == 0 && PrimalCells.view(station.getItem(0)).amount() == 3000,
                "Primal moved without its explicit selection");
        h.assertTrue(action(station, owner, CellTransferConfigPayload.Action.GLOBAL_PRIMAL, -1, null, true), "Could not select Primal channel");
        h.assertTrue(station.snapshot(1).movablePrimal() == 3000 && station.transfer(owner, station.revision()) == 3000, "Primal transfer failed");
        h.assertTrue(PrimalCells.view(station.getItem(1)).amount() == 3000 && PrimalCells.view(station.getItem(0)).amount() == 0,
                "Primal quantity was not transferred cleanly");
        var before = station.getItem(1).getComponentsPatch();
        h.assertTrue(!action(station, owner, CellTransferConfigPayload.Action.CELL_CLEAR, 1, null, false), "Primal accepted an ordinary partition");
        h.assertTrue(before.equals(station.getItem(1).getComponentsPatch()), "Rejected Primal formatting changed contents");
        h.succeed();
    }

    private static void rejected(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var station = place(h, new BlockPos(2, 2, 2), owner);
        var a = cell(Map.of(IRON, 100L));
        station.setItem(0, a);
        station.setItem(1, a.copy());
        h.assertTrue(station.snapshot(1).status().equals("same_cell") && station.transfer(owner, station.revision()) == 0,
                "Duplicate Cell identity transferred");
        station.setItem(1, ItemStack.EMPTY);
        station.setItem(1, a);
        h.assertTrue(station.getItem(1).isEmpty(), "One physical stack reference was installed into both slots");
        station.setItem(1, primal(0));
        h.assertTrue(station.snapshot(1).status().equals("type_mismatch") && station.transfer(owner, station.revision()) == 0,
                "Ordinary contents crossed into Primal");
        var raw = new CompoundTag();
        raw.putInt("schema", 99);
        raw.putLong("future", 34567);
        var locked = cell(Map.of());
        locked.set(MatterCellData.TYPE.get(), new MatterCellData(raw));
        station.setItem(0, locked);
        h.assertTrue(station.snapshot(1).locked() && station.transfer(owner, station.revision()) == 0, "Unknown Cell schema was not protected");
        h.assertTrue(raw.equals(locked.get(MatterCellData.TYPE.get()).copyTag()), "Unknown Cell contents changed");
        var unknownStation = new CompoundTag();
        unknownStation.putInt("schema", 72);
        unknownStation.putString("future_station", "preserve");
        station.loadWithComponents(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, h.getLevel().registryAccess(), unknownStation));
        station.placedBy(owner);
        h.assertTrue(station.locked(), "Unknown station schema became writable");
        var drop = ((CellTransferStationBlock) station.getBlockState().getBlock()).storedDrop(station, h.getLevel().registryAccess());
        h.assertTrue(unknownStation.equals(drop.get(DataComponents.BLOCK_ENTITY_DATA).copyTagWithoutId()), "Opaque station data was not preserved");
        h.succeed();
    }

    private static void permissions(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var station = place(h, new BlockPos(2, 2, 2), owner);
        station.setItem(0, cell(Map.of(IRON, 10L)));
        station.setItem(1, cell(Map.of()));
        long old = station.revision();
        station.setItem(0, cell(Map.of(WATER, 20L)));
        h.assertTrue(!station.configure(owner, new CellTransferConfigPayload(1, old, CellTransferConfigPayload.Action.GLOBAL_TOGGLE, -1, IRON, false)),
                "Stale selection applied to a replacement Cell");
        h.assertTrue(station.transfer(owner, old) == 0 && amount(station.getItem(0), WATER) == 20, "Stale Transfer changed replacement contents");
        station.setSecurityMode(owner, dev.latrox.rematter.content.SecurityMode.PRIVATE);
        var stranger = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ForeignCellTest"));
        stranger.setPos(owner.getX(), owner.getY(), owner.getZ());
        h.assertTrue(!action(station, stranger, CellTransferConfigPayload.Action.GLOBAL_CLEAR, -1, null, false) && station.transfer(stranger, station.revision()) == 0,
                "Foreign actor changed private station");
        owner.setPos(owner.getX() + 20, owner.getY(), owner.getZ());
        h.assertTrue(!action(station, owner, CellTransferConfigPayload.Action.GLOBAL_CLEAR, -1, null, false), "Distant actor configured station");
        h.succeed();
    }

    private static void portable(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var station = place(h, new BlockPos(2, 2, 2), owner);
        station.setItem(0, cell(Map.of(IRON, MatterLimits.CELL_4M)));
        station.setItem(1, primal(1000));
        action(station, owner, CellTransferConfigPayload.Action.CELL_TOGGLE, 0, IRON, false);
        action(station, owner, CellTransferConfigPayload.Action.GLOBAL_CLEAR, -1, null, false);
        action(station, owner, CellTransferConfigPayload.Action.GLOBAL_TOGGLE, -1, WATER, false);
        var tag = station.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(tag.keySet().equals(Set.of("cell_mask", "cell_tiers", "cell_statuses")) && tag.getIntOr("cell_mask", 0) == 3,
                "Public renderer update leaked private state or lost occupied slots");
        h.assertTrue(station.visibleCellTier(1) == 5 && station.visibleCellStatus(0) == CellStatus.FULL && station.visibleCellStatus(1) == CellStatus.STORED,
                "Installed Cell tier/lamp differs from quantity");
        var drops = Block.getDrops(station.getBlockState(), h.getLevel(), station.getBlockPos(), station);
        h.assertTrue(drops.size() == 1, "Station did not make one portable drop");
        var data = drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA);
        h.assertTrue(data != null && !data.contains("owner"), "Portable station retained old owner");
        var next = place(h, new BlockPos(5, 2, 2), owner);
        h.assertTrue(data.loadInto(next, h.getLevel().registryAccess()), "Portable station did not restore");
        next.placedBy(owner);
        for (int i = 0; i < 2; i++) {
            h.assertTrue(ItemStack.isSameItemSameComponents(station.getItem(i), next.getItem(i)),
                    "Portable Cell contents/partition changed at slot " + i);
        }
        h.assertTrue(next.selection().equals(station.selection()), "Station selection was not retained");
        h.assertTrue(((CellTransferStationBlock) station.getBlockState().getBlock()).getCloneItemStack(h.getLevel(), station.getBlockPos(), station.getBlockState(), true, owner).isEmpty(),
                "Creative data pick copied stored Cells");
        h.succeed();
    }

    private static void types(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var station = place(h, new BlockPos(2, 2, 2), owner);
        var keys = new TreeMap<SubstanceKey, Long>();
        for (int i = 0; i < 512; i++) keys.put(SubstanceKey.of("future:key_" + i, Phase.SOLID), 1L);
        var matching = SubstanceKey.of("future:key_511", Phase.SOLID);
        var newKey = SubstanceKey.of("future:new_key", Phase.GAS);
        station.setItem(0, cell(Map.of(matching, 9L, newKey, 11L)));
        station.setItem(1, cell(keys));
        h.assertTrue(station.snapshot(1).movableOrdinary() == 9 && station.transfer(owner, station.revision()) == 9,
                "Type-full target did not accept exactly its existing key");
        h.assertTrue(amount(station.getItem(0), newKey) == 11 && amount(station.getItem(1), matching) == 10,
                "Type limit lost or duplicated a resource");
        h.succeed();
    }

    private static void longKey(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var station = place(h, new BlockPos(2, 2, 2), owner);
        var key = SubstanceKey.of("future:" + "u".repeat(300), Phase.GAS);
        station.setItem(0, cell(Map.of(key, 500L)));
        station.setItem(1, cell(Map.of()));
        var preview = station.snapshot(1);
        h.assertTrue(preview.partial() && preview.available().isEmpty() && preview.movableOrdinary() == 500,
                "Long unknown ID was truncated or blocked instead of marked outside selector bounds");
        var before = station.getItem(0).getComponentsPatch();
        h.assertTrue(!action(station, owner, CellTransferConfigPayload.Action.CELL_TO_CONTENTS, 0, null, false),
                "Oversize partition key was accepted");
        h.assertTrue(before.equals(station.getItem(0).getComponentsPatch()), "Rejected oversize formatting changed Cell");
        h.assertTrue(station.transfer(owner, station.revision()) == 500 && amount(station.getItem(1), key) == 500,
                "ALL transfer did not retain full unknown key");
        h.succeed();
    }

    private static void rollback(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var station = place(h, new BlockPos(2, 2, 2), owner);
        station.setItem(0, cell(Map.of(IRON, 100L, WATER, 200L)));
        station.setItem(1, cell(Map.of()));
        var actor = new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(), new GameProfile(owner.getUUID(), "CellRollback")) {
            @Override public boolean isSpectator() { return amount(station.getItem(0), IRON) == 0; }
        };
        actor.setPos(owner.getX(), owner.getY(), owner.getZ());
        var before = station.saveCustomOnly(h.getLevel().registryAccess());
        h.assertTrue(station.snapshot(1).movableOrdinary() == 300, "Rollback fixture did not plan transfer");
        h.assertTrue(station.transfer(actor, station.revision()) == 0, "Transfer committed after permission changed");
        h.assertTrue(before.equals(station.saveCustomOnly(h.getLevel().registryAccess())), "Aborted transfer retained an intermediate Cell state");
        h.succeed();
    }

    private static void swapCells(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var station = place(h, new BlockPos(2, 2, 2), owner);
        station.setItem(0, cell(Map.of(IRON, 450L)));
        station.setItem(1, cell(Map.of()));
        var id0 = station.getItem(0).get(MatterCellData.TYPE.get()).resolve(MatterLimits.CELL_4M).orElseThrow().cellId();
        var id1 = station.getItem(1).get(MatterCellData.TYPE.get()).resolve(MatterLimits.CELL_4M).orElseThrow().cellId();
        h.assertTrue(station.transfer(owner, station.revision()) == 450, "Initial transfer failed");
        h.assertTrue(action(station, owner, CellTransferConfigPayload.Action.SWAP, -1, null, false), "Swap action failed");
        h.assertTrue(station.getItem(0).get(MatterCellData.TYPE.get()).resolve(MatterLimits.CELL_4M).orElseThrow().cellId().equals(id1)
                        && station.getItem(1).get(MatterCellData.TYPE.get()).resolve(MatterLimits.CELL_4M).orElseThrow().cellId().equals(id0),
                "Swap lost or altered Cell identities");
        h.assertTrue(station.transfer(owner, station.revision()) == 450 && amount(station.getItem(1), IRON) == 450,
                "Reverse transfer after Swap failed");
        h.succeed();
    }

    private static void worldAndMenuInsertion(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var station = place(h, new BlockPos(2, 2, 2), owner);
        var pos = station.getBlockPos();
        var state = station.getBlockState().setValue(CellTransferStationBlock.FACING, Direction.NORTH);
        h.getLevel().setBlock(pos, state, 3);

        // 1. Right-click left dock bay (modelX = 12 >= 8 -> Bay 0) from front/top with a Cell in hand
        var sourceCell = cell(Map.of(IRON, 250L));
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sourceCell);
        var leftHit = new net.minecraft.world.phys.BlockHitResult(
                new net.minecraft.world.phys.Vec3(pos.getX() + 12.0 / 16.0, pos.getY() + 9.0 / 16.0, pos.getZ() + 4.0 / 16.0),
                Direction.UP, pos, false);
        var res0 = state.useItemOn(owner.getMainHandItem(), h.getLevel(), owner, net.minecraft.world.InteractionHand.MAIN_HAND, leftHit);
        h.assertTrue(res0.consumesAction() && owner.getMainHandItem().isEmpty() && amount(station.getItem(0), IRON) == 250,
                "World right-click on left dock did not insert Source Cell into Bay 0");

        // 2. Right-click right dock bay (modelX = 4 < 8 -> Bay 1) from NORTH face with a Target Cell
        var targetCell = cell(Map.of());
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, targetCell);
        var rightHit = new net.minecraft.world.phys.BlockHitResult(
                new net.minecraft.world.phys.Vec3(pos.getX() + 4.0 / 16.0, pos.getY() + 4.5 / 16.0, pos.getZ()),
                Direction.NORTH, pos, false);
        var res1 = state.useItemOn(owner.getMainHandItem(), h.getLevel(), owner, net.minecraft.world.InteractionHand.MAIN_HAND, rightHit);
        h.assertTrue(res1.consumesAction() && owner.getMainHandItem().isEmpty() && !station.getItem(1).isEmpty(),
                "World right-click on right dock did not insert Target Cell into Bay 1");

        // 3. Shift+Right-Click empty hand extracts from clicked bay
        owner.setShiftKeyDown(true);
        var ext = state.useWithoutItem(h.getLevel(), owner, rightHit);
        owner.setShiftKeyDown(false);
        h.assertTrue(ext.consumesAction() && station.getItem(1).isEmpty() && !owner.getMainHandItem().isEmpty(),
                "Shift+right-click did not extract Target Cell from Bay 1");

        // 4. Menu insertion via quickMoveStack and safeInsert into Bay 1
        var menu = new CellTransferMenu(7, owner.getInventory(), station);
        ItemStack extracted = owner.getMainHandItem().copy();
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        owner.getInventory().setItem(9, extracted);
        var moved = menu.quickMoveStack(owner, CellTransferMenu.FIRST_PLAYER_SLOT);
        h.assertTrue(!moved.isEmpty() && !station.getItem(1).isEmpty(), "Shift-clicking Cell in menu did not populate empty Bay 1");
        var back = menu.quickMoveStack(owner, 1);
        h.assertTrue(!back.isEmpty() && station.getItem(1).isEmpty(), "Shift-clicking Bay 1 in menu did not extract Target Cell");
        var leftover = menu.getSlot(1).safeInsert(back.copy());
        h.assertTrue(leftover.isEmpty() && !station.getItem(1).isEmpty(), "Cursor click safeInsert into Bay 1 failed");
        h.succeed();
    }
}
