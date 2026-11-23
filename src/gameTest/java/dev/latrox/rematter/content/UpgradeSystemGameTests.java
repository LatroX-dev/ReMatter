package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.network.MachineActionPayload;
import dev.latrox.rematter.primal.PrimalCellData;
import dev.latrox.rematter.primal.PrimalContent;
import dev.latrox.rematter.primal.PrimalPricing;
import dev.latrox.rematter.research.ResearchData;
import dev.latrox.rematter.research.ResearchService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** End-to-end runtime verification of the Mekanism-style upgrade system, dynamic buffers, and mid-batch persistence. */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class UpgradeSystemGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    static {
        TESTS.put("upgrade_compact_buffer_and_surcharge_cancellation", UpgradeSystemGameTests::compactBufferAndSurchargeCancellation);
        TESTS.put("upgrade_legacy_compact_efficiency_migration", UpgradeSystemGameTests::legacyCompactEfficiencyMigration);
        TESTS.put("upgrade_sneak_right_click_quick_install", UpgradeSystemGameTests::sneakRightClickQuickInstall);
        TESTS.put("upgrade_stationary_reload_large_batch_over_buffer", UpgradeSystemGameTests::stationaryReloadLargeBatchOverBuffer);
        TESTS.put("upgrade_large_primal_conversion_and_cancel_refund", UpgradeSystemGameTests::largePrimalConversionAndCancelRefund);
        TESTS.put("upgrade_cancel_machine_and_terminal_jobs", UpgradeSystemGameTests::cancelMachineAndTerminalJobs);
        TESTS.put("upgrade_tab_slot_visibility_and_non_overlap", UpgradeSystemGameTests::upgradeTabVisibility);
    }

    private UpgradeSystemGameTests() {}

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("rematter_tests", path); }

    @SubscribeEvent public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> TESTS.forEach((name, body) -> registry.register(id(name), body)));
    }

    @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
        TESTS.forEach((name, body) -> {
            var environment = event.registerEnvironment(id(name + "_batch"));
            event.registerTest(id(name), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                    new TestData<>(environment, id("empty"), 1_200, 0, true, Rotation.NONE, false, 1, 1, true, 2)));
        });
    }

    private static Player owner(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        ResearchService.ledger(h.getLevel().getServer()).visit(owner.getUUID(), h.getLevel().dimension().identifier().toString());
        return owner;
    }

    private static MachineBlockEntity place(GameTestHelper h, BlockPos relative, MachineKind kind, Player owner) {
        h.setBlock(relative, ModContent.MACHINES.get(kind).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        var machine = h.getBlockEntity(relative, MachineBlockEntity.class);
        machine.placedBy(owner);
        return machine;
    }

    private static void nextTick(GameTestHelper h, MachineBlockEntity... machines) {
        var level = h.getLevel();
        ((ServerLevelData) level.getLevelData()).setGameTime(Math.incrementExact(level.getGameTime()));
        for (var machine : machines) machine.serverTick();
    }

    private static ItemStack rematterItem(String name, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(ReMatter.MOD_ID, name)), count);
    }

    private static void compactBufferAndSurchargeCancellation(GameTestHelper h) {
        var owner = owner(h);
        var prep = place(h, new BlockPos(3, 2, 3), MachineKind.PREPARATOR, owner);
        var energyCap = h.getLevel().getCapability(Capabilities.Energy.BLOCK, prep.getBlockPos(), Direction.EAST);
        h.assertTrue(energyCap != null, "Missing energy capability on Preparator");

        h.assertTrue(prep.canPlaceItem(MachineCellAccess.SPEED_SLOT, new ItemStack(ModContent.SPEED_UPGRADE.get())),
                "Compact Preparator rejected Speed Upgrade in Speed slot");
        h.assertTrue(!prep.canPlaceItem(MachineCellAccess.SPEED_SLOT, new ItemStack(ModContent.EFFICIENCY_UPGRADE.get())),
                "Compact Preparator accepted Efficiency Upgrade in Speed-only slot");
        h.assertTrue(prep.canPlaceItem(MachineCellAccess.EFFICIENCY_SLOT, new ItemStack(ModContent.EFFICIENCY_UPGRADE.get())),
                "Compact Preparator rejected Efficiency Upgrade in dedicated Efficiency slot");

        int[] expectedCaps = {32_000, 64_000, 128_000, 256_000, 512_000};
        for (int count = 0; count <= 4; count++) {
            prep.setItem(MachineCellAccess.EFFICIENCY_SLOT,
                    count == 0 ? ItemStack.EMPTY : new ItemStack(ModContent.EFFICIENCY_UPGRADE.get(), count));
            h.assertTrue(prep.energyCapacity() == expectedCaps[count]
                            && energyCap.getCapacityAsLong() == expectedCaps[count],
                    "Efficiency x" + count + " did not scale capacity to " + expectedCaps[count]
                            + ", got " + prep.energyCapacity() + "/" + energyCap.getCapacityAsLong());
        }

        // Fill upgraded buffer to 512k FE via real capability across ticks, then verify removal clamps safely.
        prep.energy().set(512_000);
        h.assertTrue(prep.energy().getAmountAsLong() == 512_000, "Failed to hold 512k FE at 4x Efficiency");
        prep.setItem(MachineCellAccess.EFFICIENCY_SLOT, new ItemStack(ModContent.EFFICIENCY_UPGRADE.get(), 2));
        h.assertTrue(prep.energyCapacity() == 128_000 && prep.energy().getAmountAsLong() == 128_000,
                "Removing Efficiency Upgrades did not clamp stored FE to 128k: " + prep.energy().getAmountAsLong());
        prep.setItem(MachineCellAccess.EFFICIENCY_SLOT, ItemStack.EMPTY);
        h.assertTrue(prep.energyCapacity() == 32_000 && prep.energy().getAmountAsLong() == 32_000,
                "Removing all Efficiency Upgrades did not clamp stored FE to 32k: " + prep.energy().getAmountAsLong());

        // 4 Speed + 4 Efficiency simultaneously: 10x speed (32 ticks) at exact 100% base FE (4,000 FE)!
        prep.setItem(MachineCellAccess.SPEED_SLOT, new ItemStack(ModContent.SPEED_UPGRADE.get(), 4));
        prep.setItem(MachineCellAccess.EFFICIENCY_SLOT, new ItemStack(ModContent.EFFICIENCY_UPGRADE.get(), 4));
        h.assertTrue(prep.speed() == 10 && prep.cost(4_000) == 4_000 && prep.energyCapacity() == 512_000,
                "4 Speed + 4 Efficiency did not cancel surcharge 1:1");
        prep.energy().set(4_000);
        prep.setItem(0, rematterItem("matrix_crystal", 1));
        prep.setItem(3, rematterItem("gold_dust", 1));
        for (int t = 0; t < 32; t++) nextTick(h, prep);
        h.assertTrue(prep.getItem(1).is(BuiltInRegistries.ITEM.getValue(Identifier.parse("rematter:amber_matrix_crystal")))
                        && prep.energy().getAmountAsLong() == 0,
                "4 Speed + 4 Efficiency did not complete amber crystal in 32 ticks for exact 4,000 FE");

        // 0 Speed + 4 Efficiency: 1x speed (320 ticks) at 80% base FE (3,200 FE).
        prep.setItem(1, ItemStack.EMPTY);
        prep.setItem(MachineCellAccess.SPEED_SLOT, ItemStack.EMPTY);
        prep.energy().set(3_200);
        prep.setItem(0, rematterItem("matrix_crystal", 1));
        prep.setItem(3, rematterItem("gold_dust", 1));
        for (int t = 0; t < 320; t++) nextTick(h, prep);
        h.assertTrue(prep.getItem(1).getCount() == 1 && prep.energy().getAmountAsLong() == 0,
                "0 Speed + 4 Efficiency did not save 20% (3,200 FE exact)");
        h.succeed();
    }

    private static void legacyCompactEfficiencyMigration(GameTestHelper h) {
        var owner = owner(h);
        var decomposer = place(h, new BlockPos(3, 2, 3), MachineKind.DECOMPOSER, owner);
        var registries = h.getLevel().registryAccess();
        var raw = decomposer.saveCustomOnly(registries);

        // Simulate a legacy v0.8.1 compact machine with 4x Efficiency Upgrade in Slot 2 and 400,000 FE stored.
        var items = new java.util.ArrayList<ItemStack>(MachineCellAccess.INVENTORY_SIZE);
        for (int i = 0; i < MachineCellAccess.INVENTORY_SIZE; i++) items.add(ItemStack.EMPTY);
        items.set(MachineCellAccess.SPEED_SLOT, new ItemStack(ModContent.EFFICIENCY_UPGRADE.get(), 4));
        raw.put("items", ItemStack.OPTIONAL_CODEC.listOf()
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), items).getOrThrow());
        var power = new CompoundTag();
        power.putInt("energy", 400_000);
        raw.put("power", power);

        decomposer.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, raw));
        h.assertTrue(decomposer.getItem(MachineCellAccess.SPEED_SLOT).isEmpty(),
                "Legacy Slot 2 was not cleared after migration");
        h.assertTrue(decomposer.getItem(MachineCellAccess.EFFICIENCY_SLOT).is(ModContent.EFFICIENCY_UPGRADE.get())
                        && decomposer.getItem(MachineCellAccess.EFFICIENCY_SLOT).getCount() == 4,
                "Legacy Efficiency Upgrades were not migrated into Slot 10");
        h.assertTrue(decomposer.energyCapacity() == 512_000 && decomposer.energy().getAmountAsLong() == 400_000,
                "Migrated buffer capacity or stored FE was lost: cap=" + decomposer.energyCapacity()
                        + " stored=" + decomposer.energy().getAmountAsLong());
        h.succeed();
    }

    private static void sneakRightClickQuickInstall(GameTestHelper h) {
        var owner = owner(h);
        var synthPos = new BlockPos(3, 2, 3);
        var synth = place(h, synthPos, MachineKind.SYNTHESIZER, owner);
        var absSynth = h.absolutePos(synthPos);
        var hitEast = new BlockHitResult(absSynth.getCenter(), Direction.EAST, absSynth, false);
        var eastBefore = synth.portMode(MachinePortMode.Side.local(Direction.NORTH, Direction.EAST));
        owner.setShiftKeyDown(true);

        // Install 4x Speed, 4x Efficiency, and 1x Pressure Module via Sneak + Right-Click.
        for (int i = 0; i < 4; i++) {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.SPEED_UPGRADE.get(), 1));
            h.assertTrue(h.getBlockState(synthPos).useItemOn(owner.getMainHandItem(), h.getLevel(), owner, InteractionHand.MAIN_HAND, hitEast).consumesAction(),
                    "Sneak+Right-Click Speed Upgrade #" + (i + 1) + " failed");
            h.assertTrue(owner.getMainHandItem().isEmpty(), "Speed Upgrade was not consumed from hand");
        }
        for (int i = 0; i < 4; i++) {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.EFFICIENCY_UPGRADE.get(), 1));
            h.assertTrue(h.getBlockState(synthPos).useItemOn(owner.getMainHandItem(), h.getLevel(), owner, InteractionHand.MAIN_HAND, hitEast).consumesAction(),
                    "Sneak+Right-Click Efficiency Upgrade #" + (i + 1) + " failed");
            h.assertTrue(owner.getMainHandItem().isEmpty(), "Efficiency Upgrade was not consumed from hand");
        }
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.PRESSURE_MODULE.get(), 1));
        h.assertTrue(h.getBlockState(synthPos).useItemOn(owner.getMainHandItem(), h.getLevel(), owner, InteractionHand.MAIN_HAND, hitEast).consumesAction(),
                "Sneak+Right-Click Pressure Module failed");
        h.assertTrue(synth.speedUpgradeCount() == 4 && synth.efficiencyUpgradeCount() == 4 && synth.crystallizationTier() == 2,
                "Quick-installed upgrades did not land in all three dedicated Synthesizer slots");
        h.assertTrue(synth.energyCapacity() == 512_000, "Quick-installed Efficiency did not update buffer capacity");

        // Extra Speed Upgrade on full 4/4 must not be consumed AND must not cycle side port mode!
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.SPEED_UPGRADE.get(), 1));
        h.assertTrue(h.getBlockState(synthPos).useItemOn(owner.getMainHandItem(), h.getLevel(), owner, InteractionHand.MAIN_HAND, hitEast).consumesAction(),
                "Full upgrade quick-install did not consume click");
        h.assertTrue(owner.getMainHandItem().getCount() == 1, "Full upgrade slot unexpectedly consumed extra item");
        h.assertTrue(synth.portMode(MachinePortMode.Side.local(Direction.NORTH, Direction.EAST)) == eastBefore,
                "Upgrade quick-install accidentally cycled port side mode");

        // Non-upgradable Crystal Generator rejects upgrades without losing item or changing side mode.
        var genPos = new BlockPos(5, 2, 3);
        var gen = place(h, genPos, MachineKind.CRYSTAL_GENERATOR, owner);
        var absGen = h.absolutePos(genPos);
        var genHit = new BlockHitResult(absGen.getCenter(), Direction.EAST, absGen, false);
        var genEastBefore = gen.portMode(MachinePortMode.Side.local(Direction.NORTH, Direction.EAST));
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.EFFICIENCY_UPGRADE.get(), 1));
        h.assertTrue(h.getBlockState(genPos).useItemOn(owner.getMainHandItem(), h.getLevel(), owner, InteractionHand.MAIN_HAND, genHit).consumesAction(),
                "Unsupported upgrade on Crystal Generator did not consume click");
        h.assertTrue(owner.getMainHandItem().getCount() == 1 && gen.energyCapacity() == 32_000
                        && gen.portMode(MachinePortMode.Side.local(Direction.NORTH, Direction.EAST)) == genEastBefore,
                "Crystal Generator unexpectedly accepted Efficiency Upgrade or cycled port mode");

        // Industrial Reactor accepts Parallel + Efficiency and scales buffer up to 2,048,000 FE.
        var indPos = new BlockPos(7, 2, 3);
        var ind = place(h, indPos, MachineKind.INDUSTRIAL_REACTOR, owner);
        var absInd = h.absolutePos(indPos);
        var indHit = new BlockHitResult(absInd.getCenter(), Direction.EAST, absInd, false);
        h.assertTrue(ind.energyCapacity() == 128_000, "Industrial base buffer should be 128,000 FE");
        for (int i = 0; i < 3; i++) {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.PARALLEL_UPGRADE.get(), 1));
            h.assertTrue(h.getBlockState(indPos).useItemOn(owner.getMainHandItem(), h.getLevel(), owner, InteractionHand.MAIN_HAND, indHit).consumesAction(),
                    "Industrial Parallel quick-install failed");
        }
        for (int i = 0; i < 4; i++) {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.EFFICIENCY_UPGRADE.get(), 1));
            h.assertTrue(h.getBlockState(indPos).useItemOn(owner.getMainHandItem(), h.getLevel(), owner, InteractionHand.MAIN_HAND, indHit).consumesAction(),
                    "Industrial Efficiency quick-install failed");
        }
        h.assertTrue(ind.parallelUpgradeCount() == 3 && ind.efficiencyUpgradeCount() == 4 && ind.energyCapacity() == 2_048_000,
                "Industrial upgrades did not reach 3 Parallel / 4 Efficiency / 2,048,000 FE: cap=" + ind.energyCapacity());
        h.succeed();
    }

    private static void stationaryReloadLargeBatchOverBuffer(GameTestHelper h) {
        var owner = owner(h);
        for (var family : ResearchData.current().families()) {
            ResearchService.adminGrant(h.getLevel().getServer(), owner.getUUID(), family.id());
        }
        var drive = place(h, new BlockPos(2, 2, 2), MachineKind.DRIVE, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        var synth = place(h, new BlockPos(3, 2, 2), MachineKind.SYNTHESIZER, owner);
        h.assertTrue(synth.energyCapacity() == 32_000, "Unupgraded compact Synthesizer should have 32,000 FE base buffer");

        var profile = ReMatter.chemistry().profile("minecraft:gold_block").orElseThrow();
        h.assertTrue(profile.synthesisEnergy() == 144_000L, "Expected 144,000 FE Gold Block synthesis profile");
        for (var entry : profile.matter().entrySet()) {
            h.assertTrue(drive.matterStore().apply(Map.of(), Map.of(entry.getKey(), entry.getValue() * 2)), "Failed to seed Gold Block feedstock");
        }
        drive.setChanged();

        // Start a 144,000 FE batch on a 32,000 FE buffer with zero Efficiency Upgrades.
        synth.energy().set(32_000);
        h.assertTrue(synth.selectRecipe("minecraft:gold_block") && synth.requestBatch(), "Failed to request Gold Block");
        int guard = 0;
        while (synth.energy().getAmountAsLong() > 0 && guard++ < profile.ticks()) nextTick(h, synth);
        nextTick(h, synth);
        int savedProgress = synth.work().progress;
        long savedPaid = synth.work().energyPaid;
        h.assertTrue(savedProgress > 0 && savedPaid == 32_000L && synth.energy().getAmountAsLong() == 0
                        && "insufficient_energy".equals(synth.work().status),
                "Batch did not drain exact 32,000 FE and pause cleanly: progress=" + savedProgress
                        + " paid=" + savedPaid + " status=" + synth.work().status);

        // Stationary save/reload MUST preserve paid progress beyond the 32k buffer with zero FE loss!
        var registries = h.getLevel().registryAccess();
        var savedTag = synth.saveCustomOnly(registries);
        synth.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, savedTag));
        h.assertTrue(synth.work().progress == savedProgress && synth.work().energyPaid == 32_000L,
                "Stationary chunk reload lost mid-batch progress or paid FE: progress=" + synth.work().progress
                        + " paid=" + synth.work().energyPaid);

        // Supply remaining 112,000 FE in chunks (32k + 32k + 32k + 16k) and finish exact 144,000 FE batch!
        for (int chunk : new int[]{32_000, 32_000, 32_000, 16_000}) {
            synth.energy().set(chunk);
            guard = 0;
            while (synth.energy().getAmountAsLong() > 0 && synth.getItem(1).isEmpty() && guard++ < profile.ticks()) nextTick(h, synth);
        }
        h.assertTrue(synth.getItem(1).is(Items.GOLD_BLOCK) && synth.getItem(1).getCount() == 1,
                "Gold Block did not complete after stationary reload");
        h.assertTrue(synth.energy().getAmountAsLong() == 0 && synth.work().energyPaid == 0,
                "Total Gold Block FE debit was not exact 144,000 FE");

        // Second batch: pay 40,000 FE (> 32k capacity), then break machine -> portable drop strips work & clamps refund to 32k!
        synth.setItem(1, ItemStack.EMPTY);
        synth.energy().set(32_000);
        h.assertTrue(synth.requestBatch(), "Failed to request second Gold Block");
        guard = 0;
        while (synth.energy().getAmountAsLong() > 0 && guard++ < profile.ticks()) nextTick(h, synth);
        synth.energy().set(8_000);
        guard = 0;
        while (synth.energy().getAmountAsLong() > 0 && guard++ < profile.ticks()) nextTick(h, synth);
        h.assertTrue(synth.work().energyPaid == 40_000L, "Expected 40,000 FE paid before drop");
        var drops = Block.getDrops(synth.getBlockState(), h.getLevel(), synth.getBlockPos(), synth);
        var portable = new MachineBlockEntity(synth.getBlockPos(), synth.getBlockState());
        drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA).loadInto(portable, registries);
        h.assertTrue(portable.work().progress == 0 && portable.work().energyPaid == 0
                        && portable.energy().getAmountAsLong() == 32_000L,
                "Portable drop did not strip work and clamp refunded FE to 32,000: energy=" + portable.energy().getAmountAsLong());
        h.succeed();
    }

    private static void largePrimalConversionAndCancelRefund(GameTestHelper h) {
        var owner = owner(h);
        ResearchService.adminGrant(h.getLevel().getServer(), owner.getUUID(), "rematter:iron");
        var drive = place(h, new BlockPos(2, 2, 2), MachineKind.DRIVE, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        var converter = place(h, new BlockPos(3, 2, 2), MachineKind.PRIMAL_CONVERTER, owner);
        h.assertTrue(converter.energyCapacity() == MatterLimits.CONVERTER_ENERGY, "Converter base buffer should be 128,000 FE");

        var cell = new ItemStack(PrimalContent.PRIMAL_CELL_64K.get());
        cell.set(PrimalCellData.TYPE.get(), PrimalCellData.from(new PrimalCellData.State(UUID.randomUUID(), 64_000, 0, Map.of())));
        converter.setItem(MachineCellAccess.PRIMAL_SLOT, cell);

        // 4,000 u Iron = 256,000 FE (2x the converter's 128,000 FE buffer!).
        var quote = PrimalPricing.quote(ReMatter.chemistry(), BuiltinChemistry.IRON, 4_000, id -> id.equals(BuiltinChemistry.IRON.id()));
        h.assertTrue(quote.energy() == 256_000L, "Expected 256,000 FE for 4,000 u Iron, got " + quote.energy());
        h.assertTrue(converter.extensions().action(new MachineActionPayload(0, MachineActionPayload.Action.CONVERT_SELECT, "rematter:iron", 4_000)),
                "Converter rejected 4,000 u Iron");
        h.assertTrue(converter.extensions().action(new MachineActionPayload(0, MachineActionPayload.Action.CONVERT_START, "", 0)),
                "Converter rejected start for 4,000 u Iron");

        long spent = 0L;
        converter.energy().set(converter.energyCapacity());
        int half = quote.ticks() / 2;
        for (int i = 0; i < half; i++) {
            if (converter.energy().getAmountAsLong() < 4_000) converter.energy().set(converter.energyCapacity());
            long before = converter.energy().getAmountAsLong();
            nextTick(h, converter);
            spent += before - converter.energy().getAmountAsLong();
        }
        h.assertTrue(converter.currentEnergyRate() > 0L, "Active Primal Converter must report live positive FE/t");
        // Mid-conversion save/reload preserves conversion_energy_paid seamlessly.
        var saved = converter.saveCustomOnly(h.getLevel().registryAccess());
        h.assertTrue(saved.getLongOr("conversion_energy_paid", 0L) == spent && spent > 0L,
                "Saved converter did not persist exact conversion_energy_paid");
        converter.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved));

        for (int i = half; i < quote.ticks() + 2; i++) {
            if (converter.energy().getAmountAsLong() < 4_000) converter.energy().set(converter.energyCapacity());
            long before = converter.energy().getAmountAsLong();
            nextTick(h, converter);
            spent += before - converter.energy().getAmountAsLong();
        }
        h.assertTrue(spent == 256_000L, "Total converter FE paid across 128k buffer was not exact 256,000 FE: " + spent);
        h.assertTrue(drive.matterStore().amount(BuiltinChemistry.IRON) == 4_000L,
                "Converter did not deliver 4,000 u Iron to Drive: " + drive.matterStore().amount(BuiltinChemistry.IRON));

        // Cancel mid-conversion refunds paid FE up to buffer capacity.
        converter.energy().set(100_000);
        h.assertTrue(converter.extensions().action(new MachineActionPayload(0, MachineActionPayload.Action.CONVERT_START, "", 0)),
                "Second conversion did not start");
        for (int i = 0; i < 20; i++) nextTick(h, converter);
        h.assertTrue(converter.energy().getAmountAsLong() < 100_000, "Second conversion did not draw tick FE");
        h.assertTrue(converter.extensions().action(new MachineActionPayload(0, MachineActionPayload.Action.CONVERT_CANCEL, "", 0)),
                "Converter cancel failed");
        h.assertTrue(converter.energy().getAmountAsLong() == 100_000,
                "Cancelled conversion did not refund paid FE: " + converter.energy().getAmountAsLong());
        h.succeed();
    }

    private static void cancelMachineAndTerminalJobs(GameTestHelper h) {
        var owner = owner(h);
        ResearchService.adminGrant(h.getLevel().getServer(), owner.getUUID(), "rematter:silica");
        var drive = place(h, new BlockPos(2, 2, 2), MachineKind.DRIVE, owner);
        drive.setItem(drive.cellSlotForBay(0), new ItemStack(ModContent.MATTER_CELL_4M.get()));
        h.assertTrue(drive.matterStore().apply(Map.of(), Map.of(BuiltinChemistry.SILICA, 10_000L)), "Failed to seed silica");
        drive.setChanged();

        // 1. Synthesizer queued batches: cancel clears queue, stops running, refunds paid FE, keeps matter intact.
        var synth = place(h, new BlockPos(3, 2, 2), MachineKind.SYNTHESIZER, owner);
        synth.energy().set(32_000);
        h.assertTrue(synth.selectRecipe("minecraft:glass"), "Failed to select glass");
        for (int i = 0; i < 5; i++) h.assertTrue(synth.requestBatch(), "Failed to queue glass batch");
        for (int t = 0; t < 25; t++) nextTick(h, synth);
        h.assertTrue(synth.queuedBatchCount() == 5 && synth.work().progress == 25 && synth.work().energyPaid > 0,
                "Synthesizer batch did not start cleanly");
        h.assertTrue(synth.cancelJob(), "cancelJob returned false on active queued Synthesizer");
        h.assertTrue(synth.queuedBatchCount() == 0 && !synth.running() && synth.work().progress == 0 && synth.work().energyPaid == 0,
                "cancelJob did not clear Synthesizer queue and progress");
        h.assertTrue(synth.energy().getAmountAsLong() == 32_000 && drive.matterStore().amount(BuiltinChemistry.SILICA) == 10_000L,
                "cancelJob did not refund FE or touched stored silica");

        // 2. Synthesizer repeat (Loop) mode: cancel turns off repeat and refunds FE.
        h.assertTrue(synth.toggleRepeat(), "Failed to enable Loop");
        for (int t = 0; t < 10; t++) nextTick(h, synth);
        h.assertTrue(synth.runState().repeat() && synth.work().progress == 10, "Loop batch did not advance");
        h.assertTrue(synth.cancelJob(), "cancelJob failed on Loop job");
        h.assertTrue(!synth.runState().repeat() && !synth.running() && synth.work().progress == 0 && synth.energy().getAmountAsLong() == 32_000,
                "cancelJob did not stop Loop and refund FE");

        // 3. Direct processor (Decomposer): cancel pauses, resets progress, refunds FE, keeps input item.
        var decomp = place(h, new BlockPos(1, 2, 2), MachineKind.DECOMPOSER, owner);
        decomp.energy().set(32_000);
        decomp.setItem(0, new ItemStack(Items.SAND, 2));
        for (int t = 0; t < 30; t++) nextTick(h, decomp);
        h.assertTrue(decomp.work().progress == 30 && decomp.work().energyPaid > 0, "Decomposer did not start");
        h.assertTrue(decomp.cancelJob(), "cancelJob failed on active Decomposer");
        h.assertTrue(!decomp.running() && decomp.work().progress == 0 && decomp.energy().getAmountAsLong() == 32_000
                        && decomp.getItem(0).getCount() == 2,
                "cancelJob on Decomposer did not pause, refund FE, and keep sand intact");

        // 4. Terminal multi-step job cancelled directly from its leased Synthesizer cancels the whole Terminal job!
        var terminal = place(h, new BlockPos(2, 2, 3), MachineKind.TERMINAL, owner);
        // Face terminal SOUTH so its back (NORTH) links to the Drive at (2,2,2).
        h.getLevel().setBlockAndUpdate(terminal.getBlockPos(),
                ModContent.MACHINES.get(MachineKind.TERMINAL).get().defaultBlockState().setValue(MachineBlock.FACING, Direction.SOUTH));
        terminal = h.getBlockEntity(new BlockPos(2, 2, 3), MachineBlockEntity.class);
        terminal.placedBy(owner);
        var planCtx = TerminalJobCoordinator.buildNetworkContext(h.getLevel(), owner.getUUID(), null, terminal.matterNetwork(true));
        var plan = TerminalBatchPlanner.plan(ReMatter.chemistry(), TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM,
                "minecraft:glass", "", 3, planCtx);
        h.assertTrue(plan.executable(), "Glass terminal plan was not executable: " + plan.status());
        int jobId = TerminalJobCoordinator.submit(h.getLevel(), terminal.getBlockPos(), owner.getUUID(),
                TerminalBatchPlanner.TargetKind.SYNTHESIS_ITEM, "minecraft:glass", "", 3, plan);
        h.assertTrue(jobId > 0 && synth.queuedBatchCount() == 3, "Terminal job did not lease Synthesizer");
        for (int t = 0; t < 15; t++) nextTick(h, synth);
        h.assertTrue(synth.cancelJob(), "cancelJob failed on terminal-leased Synthesizer");
        h.assertTrue(TerminalJobCoordinator.jobsFor(h.getLevel(), owner.getUUID()).isEmpty()
                        && synth.queuedBatchCount() == 0 && synth.work().progress == 0 && synth.energy().getAmountAsLong() == 32_000,
                "Cancelling on leased worker did not cancel parent Terminal job and refund FE");
        h.succeed();
    }

    private static void upgradeTabVisibility(GameTestHelper h) {
        var owner = owner(h);
        // 1. Compact Synthesizer supports Speed, Efficiency, and Pressure Module inside View.UPGRADES only.
        var synth = place(h, new BlockPos(2, 2, 2), MachineKind.SYNTHESIZER, owner);
        var synthMenu = new MachineMenu(1, owner.getInventory(), synth);
        synthMenu.setView(MachineMenu.View.MAIN);
        h.assertTrue(!synthMenu.slots.get(MachineCellAccess.SPEED_SLOT).isActive()
                        && !synthMenu.slots.get(MachineCellAccess.EFFICIENCY_SLOT).isActive()
                        && !synthMenu.slots.get(MachineCellAccess.PARALLEL_SLOT).isActive(),
                "Upgrade slots must stay off the MAIN tab");
        synthMenu.setView(MachineMenu.View.UPGRADES);
        h.assertTrue(synthMenu.view() == MachineMenu.View.UPGRADES, "Synthesizer refused UPGRADES view");
        h.assertTrue(synthMenu.slots.get(MachineCellAccess.SPEED_SLOT).isActive()
                        && synthMenu.slots.get(MachineCellAccess.EFFICIENCY_SLOT).isActive()
                        && synthMenu.slots.get(MachineCellAccess.PARALLEL_SLOT).isActive()
                        && synthMenu.slots.get(MachineMenu.FIRST_PLAYER_SLOT).isActive(),
                "UPGRADES tab did not activate Speed, Efficiency, Pressure, and player inventory slots");
        assertNoSlotOverlap(h, synthMenu, "Synthesizer UPGRADES");

        // Shift-click quick-move installs upgrades cleanly into their dedicated slots.
        owner.getInventory().setItem(0, new ItemStack(ModContent.SPEED_UPGRADE.get(), 2));
        owner.getInventory().setItem(1, new ItemStack(ModContent.EFFICIENCY_UPGRADE.get(), 3));
        owner.getInventory().setItem(2, new ItemStack(ModContent.PRESSURE_MODULE.get(), 1));
        int hotbarStart = MachineMenu.FIRST_PLAYER_SLOT + 27;
        synthMenu.quickMoveStack(owner, hotbarStart);
        synthMenu.quickMoveStack(owner, hotbarStart + 1);
        synthMenu.quickMoveStack(owner, hotbarStart + 2);
        h.assertTrue(synth.speedUpgradeCount() == 2 && synth.efficiencyUpgradeCount() == 3 && synth.crystallizationTier() == 2,
                "Quick-move in UPGRADES tab did not route Speed/Efficiency/Pressure to their dedicated slots");

        // 2. Non-upgradable Crystal Generator rejects View.UPGRADES cleanly.
        var gen = place(h, new BlockPos(4, 2, 2), MachineKind.CRYSTAL_GENERATOR, owner);
        var genMenu = new MachineMenu(2, owner.getInventory(), gen);
        genMenu.setView(MachineMenu.View.UPGRADES);
        h.assertTrue(genMenu.view() == MachineMenu.View.MAIN, "Non-upgradable generator accepted UPGRADES view");
        h.succeed();
    }

    private static void assertNoSlotOverlap(GameTestHelper h, MachineMenu menu, String label) {
        var active = menu.slots.stream().filter(net.minecraft.world.inventory.Slot::isActive).toList();
        for (int a = 0; a < active.size(); a++) {
            for (int b = a + 1; b < active.size(); b++) {
                var first = active.get(a);
                var second = active.get(b);
                h.assertTrue(first.x + 16 <= second.x || second.x + 16 <= first.x
                                || first.y + 16 <= second.y || second.y + 16 <= first.y,
                        "Active slots overlap in " + label);
            }
        }
    }
}
