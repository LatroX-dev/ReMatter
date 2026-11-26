package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.ChannelMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.DirectionMode;
import dev.latrox.rematter.content.MatterCableRelayBlockEntity.GateMode;
import dev.latrox.rematter.energy.EnergyCubeBlockEntity;
import dev.latrox.rematter.energy.EnergyCubeTier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Verifies external-mod FE interoperability across Matter Cables, Matter Cable Relays, energized outer rail glow,
 * mixed-conduit loop safety, edge-by-edge security & trust revocation, late-activated capabilities,
 * universal wrench passthrough, authoritative synthesis stock, and cross-mod safety/routing tags.
 */
@EventBusSubscriber(modid = ReMatter.MOD_ID)
public final class ExternalModCompatGameTests {
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
    private static final Map<BlockPos, EnergyHandler> EXTERNAL_BLOCKS = new ConcurrentHashMap<>();

    static {
        TESTS.put("external_cable_energizes_border_when_idle_or_branched", ExternalModCompatGameTests::externalCableBorderGlow);
        TESTS.put("cables_deliver_fe_to_external_mods_and_block_self_loops", ExternalModCompatGameTests::externalFeDeliveryAndLoopGuard);
        TESTS.put("relay_external_mod_fe_gating_diode_cap_and_unowned", ExternalModCompatGameTests::relayExternalModCompat);
        TESTS.put("matter_wrench_passthrough_and_compat_tags", ExternalModCompatGameTests::wrenchPassthroughAndTags);
        TESTS.put("cable_security_isolation_scan_order_and_trust_revocation", ExternalModCompatGameTests::cableSecurityAndTrustRevocation);
        TESTS.put("late_activated_external_fe_and_authoritative_synth_stock", ExternalModCompatGameTests::lateActivatedCapabilityAndSynthStock);
    }

    private ExternalModCompatGameTests() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("rematter_tests", path);
    }

    @SubscribeEvent
    public static void registerTestCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.Energy.BLOCK,
                (level, pos, state, be, side) -> side == null ? null : EXTERNAL_BLOCKS.get(pos),
                Blocks.COPPER_BLOCK, Blocks.BARREL);
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> TESTS.forEach((name, fn) -> helper.register(id(name), fn)));
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(id("external_compat"));
        TESTS.forEach((name, fn) -> event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)),
                new TestData<>(env, id("empty"), 600, 0, true, Rotation.NONE, false, 1, 1, true, 2))));
    }

    private static void placeExternalFe(GameTestHelper h, BlockPos rel, EnergyHandler handler) {
        BlockPos abs = h.absolutePos(rel).immutable();
        EXTERNAL_BLOCKS.put(abs, handler);
        h.setBlock(rel, Blocks.COPPER_BLOCK);
        h.getLevel().invalidateCapabilities(abs);
    }

    private static void clearExternalFe(GameTestHelper h, BlockPos... rels) {
        for (BlockPos rel : rels) {
            BlockPos abs = h.absolutePos(rel).immutable();
            EXTERNAL_BLOCKS.remove(abs);
            h.getLevel().invalidateCapabilities(abs);
        }
    }

    /** Zero-reporting pass-through EnergyHandler modeling stateless conduits such as Ender IO EnergyConduitStorage. */
    private static EnergyHandler zeroReportingProxy(EnergyHandler downstream) {
        return new EnergyHandler() {
            @Override public long getAmountAsLong() { return 0L; }
            @Override public long getCapacityAsLong() { return 100_000L; }
            @Override public int insert(int amount, TransactionContext tx) { return downstream == null ? 0 : downstream.insert(amount, tx); }
            @Override public int extract(int amount, TransactionContext tx) { return 0; }
        };
    }

    private static void externalCableBorderGlow(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var level = h.getLevel();

        // Rig 1: Charged Mekanism-style external cable pushing into a bare T-branch of Matter Cables (committed tx, moved == 0)
        BlockPos extA = new BlockPos(1, 2, 2);
        BlockPos c1 = new BlockPos(2, 2, 2);
        BlockPos c2 = new BlockPos(3, 2, 2);
        BlockPos cBranch = new BlockPos(3, 2, 3);
        var sourceA = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        sourceA.set(10_000);
        placeExternalFe(h, extA, sourceA);
        for (BlockPos p : List.of(c1, c2, cBranch)) {
            h.setBlock(p, ModContent.MATTER_CABLE.get());
            CableOwners.set(level, h.absolutePos(p), owner.getUUID());
        }
        MatterCableBlock.refreshLoadedAround(level, h.absolutePos(c1));
        h.assertTrue(h.getBlockState(c1).getValue(MatterCableBlock.WEST)
                && h.getBlockState(c1).getValue(MatterCableBlock.DEVICE_BY_DIRECTION.get(Direction.WEST)),
                "Matter Cable must attach with device flange to external FE cable");

        var capA = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(c1), Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(capA.insert(5_000, tx) == 0, "Bare cable with no sinks should accept 0 FE");
            tx.commit();
        }
        h.assertTrue(h.getBlockState(c1).getValue(MatterCableBlock.POWERED)
                        && h.getBlockState(c2).getValue(MatterCableBlock.POWERED)
                        && h.getBlockState(cBranch).getValue(MatterCableBlock.POWERED),
                "Committed external FE offer from charged buffer must energize the full reachable cable tree");

        // Rig 2: External source with FE offering into a cable whose machine is 100% FULL, then ABORTING tx (Powah/Oritech/multi-acceptor style)
        BlockPos extB = new BlockPos(1, 2, 6);
        BlockPos cb1 = new BlockPos(2, 2, 6);
        BlockPos cb2 = new BlockPos(3, 2, 6);
        BlockPos fullDecPos = new BlockPos(4, 2, 6);
        var sourceB = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        sourceB.set(8_000);
        placeExternalFe(h, extB, sourceB);
        h.setBlock(cb1, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(cb1), owner.getUUID());
        h.setBlock(cb2, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(cb2), owner.getUUID());
        h.setBlock(fullDecPos, ModContent.MACHINES.get(MachineKind.DECOMPOSER).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH));
        var fullDec = h.getBlockEntity(fullDecPos, MachineBlockEntity.class);
        fullDec.placedBy(owner);
        fullDec.energy().set(fullDec.energyCapacity());

        var capB = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(cb1), Direction.WEST));
        try (var sim = Transaction.openRoot()) {
            h.assertTrue(capB.insert(4_000, sim) == 0, "Full machine should accept 0 FE");
            // Intentionally aborted!
        }

        // Rig 3: EMPTY external source (0 FE) performing BOTH aborted AND committed 0-FE probes must NOT light up the border!
        BlockPos extEmpty = new BlockPos(6, 2, 6);
        BlockPos cEmpty = new BlockPos(7, 2, 6);
        var emptySource = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        placeExternalFe(h, extEmpty, emptySource);
        h.setBlock(cEmpty, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(cEmpty), owner.getUUID());
        var capEmpty = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(cEmpty), Direction.WEST));
        try (var sim = Transaction.openRoot()) {
            h.assertTrue(capEmpty.insert(4_000, sim) == 0, "Aborted empty external probe must transfer 0 FE");
        }
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(capEmpty.insert(4_000, tx) == 0, "Committed empty external probe must transfer 0 FE");
            tx.commit();
        }
        h.assertFalse(h.getBlockState(cEmpty).getValue(MatterCableBlock.POWERED),
                "Committed 0 FE probe from empty source must NOT falsely energize cable border");

        // Rig 4: Zero-reporting stateless proxy (Ender IO style) stays dark on 0 FE offer, glows on actual committed FE flow!
        BlockPos extProxy = new BlockPos(6, 2, 2);
        BlockPos cProxy = new BlockPos(7, 2, 2);
        BlockPos decProxyPos = new BlockPos(8, 2, 2);
        placeExternalFe(h, extProxy, zeroReportingProxy(null));
        h.setBlock(cProxy, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(cProxy), owner.getUUID());
        var capProxy = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(cProxy), Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(capProxy.insert(1_000, tx) == 0, "Zero-reporting proxy into bare cable transfers 0 FE");
            tx.commit();
        }
        h.assertFalse(h.getBlockState(cProxy).getValue(MatterCableBlock.POWERED),
                "Zero-reporting proxy with 0 FE moved must NOT falsely energize cable border");
        h.setBlock(decProxyPos, ModContent.MACHINES.get(MachineKind.DECOMPOSER).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH));
        h.getBlockEntity(decProxyPos, MachineBlockEntity.class).placedBy(owner);
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(capProxy.insert(1_000, tx) == 1_000, "Zero-reporting proxy must power ReMatter Decomposer");
            tx.commit();
        }
        h.assertTrue(h.getBlockState(cProxy).getValue(MatterCableBlock.POWERED),
                "Actual committed FE transfer from zero-reporting proxy must energize cable border");

        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    h.assertTrue(h.getBlockState(cb1).getValue(MatterCableBlock.POWERED)
                                    && h.getBlockState(cb2).getValue(MatterCableBlock.POWERED),
                            "Deferred energize must light Matter Cables fed by a charged external buffer even when machines are full & tx aborts");
                    h.assertFalse(h.getBlockState(cEmpty).getValue(MatterCableBlock.POWERED),
                            "Empty external source (0 FE) must stay dark after server tick too");
                    clearExternalFe(h, extA, extB, extEmpty, extProxy);
                })
                .thenSucceed();
    }

    private static void externalFeDeliveryAndLoopGuard(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var level = h.getLevel();

        // 1. ReMatter Energy Cube -> Matter Cable -> External FE Sink
        BlockPos cubePos = new BlockPos(1, 2, 2);
        BlockPos cablePos = new BlockPos(2, 2, 2);
        BlockPos extSinkPos = new BlockPos(3, 2, 2);
        var extSink = new SimpleEnergyHandler(50_000, 50_000, 50_000);
        placeExternalFe(h, extSinkPos, extSink);
        h.setBlock(cablePos, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(cablePos), owner.getUUID());
        h.setBlock(cubePos, ModContent.ENERGY_CUBES.get(EnergyCubeTier.REINFORCED).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.EAST));
        var cube = h.getBlockEntity(cubePos, EnergyCubeBlockEntity.class);
        cube.placedBy(owner);
        var cubeTag = cube.saveCustomOnly(level.registryAccess());
        cubeTag.putLong("energy", 20_000L);
        cube.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), cubeTag));

        // 2. Buffered External Source -> Matter Cable -> Distinct External Sink
        BlockPos extSrcPos = new BlockPos(1, 2, 5);
        BlockPos cableMid = new BlockPos(2, 2, 5);
        BlockPos extDstPos = new BlockPos(3, 2, 5);
        var srcBuf = new SimpleEnergyHandler(50_000, 50_000, 50_000);
        srcBuf.set(10_000);
        var dstBuf = new SimpleEnergyHandler(50_000, 50_000, 50_000);
        placeExternalFe(h, extSrcPos, srcBuf);
        placeExternalFe(h, extDstPos, dstBuf);
        h.setBlock(cableMid, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(cableMid), owner.getUUID());

        var capMid = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(cableMid), Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            int moved = capMid.insert(3_000, tx);
            h.assertTrue(moved == 3_000, "Buffered External -> Matter Cable -> External must forward 3000 FE, got " + moved);
            tx.commit();
        }
        h.assertTrue(dstBuf.getAmountAsLong() == 3_000L, "External sink must receive 3000 FE");

        // 3. Direct Mekanism-style Self-Loop Backflow Guard (two distinct wrappers sharing one live buffer)
        BlockPos loopA = new BlockPos(5, 2, 5);
        BlockPos loopCable = new BlockPos(6, 2, 5);
        BlockPos loopB = new BlockPos(7, 2, 5);
        var sharedExternalNet = new SimpleEnergyHandler(50_000, 50_000, 50_000);
        sharedExternalNet.set(5_000);
        EnergyHandler portA = new EnergyHandler() {
            @Override public long getAmountAsLong() { return sharedExternalNet.getAmountAsLong(); }
            @Override public long getCapacityAsLong() { return sharedExternalNet.getCapacityAsLong(); }
            @Override public int insert(int amount, TransactionContext tx) { return sharedExternalNet.insert(amount, tx); }
            @Override public int extract(int amount, TransactionContext tx) { return sharedExternalNet.extract(amount, tx); }
        };
        EnergyHandler portB = new EnergyHandler() {
            @Override public long getAmountAsLong() { return sharedExternalNet.getAmountAsLong(); }
            @Override public long getCapacityAsLong() { return sharedExternalNet.getCapacityAsLong(); }
            @Override public int insert(int amount, TransactionContext tx) { return sharedExternalNet.insert(amount, tx); }
            @Override public int extract(int amount, TransactionContext tx) { return sharedExternalNet.extract(amount, tx); }
        };
        placeExternalFe(h, loopA, portA);
        placeExternalFe(h, loopB, portB);
        h.setBlock(loopCable, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(loopCable), owner.getUUID());

        var loopCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(loopCable), Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            int moved = loopCap.insert(5_000, tx);
            h.assertTrue(moved == 0, "Direct self-loop backflow must be blocked, got moved=" + moved);
            tx.commit();
        }
        h.assertTrue(sharedExternalNet.getAmountAsLong() == 5_000L, "Shared external network energy must remain 5000 FE");

        // 4. Mixed-Mod Conduit Ring Guard (Mekanism -> zero-reporting Ender IO proxy -> Matter Cable -> same Mekanism net!)
        //    Because the proxy reports getAmountAsLong() == 0, Matter Cable MUST refuse forwarding back to external blocks
        //    so Mekanism EnergyNetwork.tickEmit() cannot void FE — while still feeding ReMatter machines on the same cable!
        BlockPos mixProxyPos = new BlockPos(5, 2, 8);
        BlockPos mixCablePos = new BlockPos(6, 2, 8);
        BlockPos mixMekReturnPos = new BlockPos(7, 2, 8);
        var mekNet = new SimpleEnergyHandler(50_000, 50_000, 50_000);
        mekNet.set(8_000);
        placeExternalFe(h, mixProxyPos, zeroReportingProxy(mekNet));
        placeExternalFe(h, mixMekReturnPos, mekNet);
        h.setBlock(mixCablePos, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(mixCablePos), owner.getUUID());

        var mixCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(mixCablePos), Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            int moved = mixCap.insert(4_000, tx);
            h.assertTrue(moved == 0, "Zero-reporting proxy MUST NOT forward back into external Mekanism net, got moved=" + moved);
            tx.commit();
        }
        h.assertTrue(mekNet.getAmountAsLong() == 8_000L, "Mixed-conduit ring must preserve exact 8000 FE in Mekanism net");

        // Attach a ReMatter Decomposer on the mixed-conduit cable -> Decomposer MUST still receive FE!
        BlockPos mixDecPos = new BlockPos(6, 2, 9);
        h.setBlock(mixDecPos, ModContent.MACHINES.get(MachineKind.DECOMPOSER).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.SOUTH));
        var mixDec = h.getBlockEntity(mixDecPos, MachineBlockEntity.class);
        mixDec.placedBy(owner);
        try (var tx = Transaction.openRoot()) {
            int moved = mixCap.insert(2_500, tx);
            h.assertTrue(moved == 2_500, "Zero-reporting proxy must still deliver 2500 FE to ReMatter Decomposer, got " + moved);
            tx.commit();
        }
        h.assertTrue(mixDec.energy().getAmountAsLong() == 2_500L && mekNet.getAmountAsLong() == 8_000L,
                "Decomposer must get 2500 FE without touching external return loop");

        // 5. Scoped re-entrancy: Two physically separate ReMatter cable networks bridged synchronously by an external conduit!
        BlockPos net1Src = new BlockPos(1, 2, 9);
        BlockPos net1Cable = new BlockPos(2, 2, 9);
        BlockPos bridgePos = new BlockPos(3, 2, 9);
        BlockPos net2Cable = new BlockPos(4, 2, 9);
        BlockPos net2DecPos = new BlockPos(4, 2, 10);
        var net1Buf = new SimpleEnergyHandler(50_000, 50_000, 50_000);
        net1Buf.set(10_000);
        placeExternalFe(h, net1Src, net1Buf);
        h.setBlock(net1Cable, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(net1Cable), owner.getUUID());
        h.setBlock(net2Cable, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(net2Cable), owner.getUUID());
        h.setBlock(net2DecPos, ModContent.MACHINES.get(MachineKind.DECOMPOSER).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.SOUTH));
        var net2Dec = h.getBlockEntity(net2DecPos, MachineBlockEntity.class);
        net2Dec.placedBy(owner);
        var net2Cap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(net2Cable), Direction.WEST));
        placeExternalFe(h, bridgePos, zeroReportingProxy(net2Cap));
        var net1Cap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(net1Cable), Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            int moved = net1Cap.insert(1_500, tx);
            h.assertTrue(moved == 1_500, "Scoped re-entrancy must allow two separate ReMatter networks bridged externally, got " + moved);
            tx.commit();
        }
        h.assertTrue(net2Dec.energy().getAmountAsLong() == 1_500L, "Second ReMatter network must receive 1500 FE across external bridge");

        // 6. Overlapping multi-owner PUBLIC Basic cables (distinct Component objects sharing physical nodes {X, Y}):
        //    Synchronous re-entry back into Bob's cable Y during Alice's 4096 FE transfer must NOT throw "Native path allowance exceeded"!
        var bob = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos ovSrcPos = new BlockPos(1, 2, 12);
        BlockPos ovAliceX = new BlockPos(2, 2, 12);
        BlockPos ovBobY = new BlockPos(3, 2, 12);
        BlockPos ovProxyPos = new BlockPos(4, 2, 12);
        BlockPos ovDecPos = new BlockPos(3, 2, 13);
        var ovSrc = new SimpleEnergyHandler(50_000, 50_000, 50_000);
        ovSrc.set(20_000);
        var ovSink = new SimpleEnergyHandler(50_000, 50_000, 50_000);
        placeExternalFe(h, ovSrcPos, ovSrc);
        h.setBlock(ovAliceX, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(ovAliceX), owner.getUUID(), SecurityMode.PUBLIC);
        h.setBlock(ovBobY, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(ovBobY), bob.getUUID(), SecurityMode.PUBLIC);
        h.setBlock(ovDecPos, ModContent.MACHINES.get(MachineKind.DECOMPOSER).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.SOUTH));
        var ovBobDec = h.getBlockEntity(ovDecPos, MachineBlockEntity.class);
        ovBobDec.placedBy(bob);
        ovBobDec.setSecurityMode(bob, SecurityMode.PRIVATE);

        var ovBobCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(ovBobY), Direction.EAST));
        int[] reentrantMoved = {-1};
        EnergyHandler reentrantProxy = new EnergyHandler() {
            @Override public long getAmountAsLong() { return 0L; }
            @Override public long getCapacityAsLong() { return 50_000L; }
            @Override public int insert(int amount, TransactionContext tx) {
                // Attempt to re-enter Bob's distinct PUBLIC Component over the same physical Basic cables!
                reentrantMoved[0] = ovBobCap.insert(amount, tx);
                return ovSink.insert(amount, tx);
            }
            @Override public int extract(int amount, TransactionContext tx) { return 0; }
        };
        placeExternalFe(h, ovProxyPos, reentrantProxy);
        // Pre-warm Bob's separate Component view before Alice pushes!
        try (var sim = Transaction.openRoot()) {
            ovBobCap.insert(100, sim);
        }
        var ovAliceCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(ovAliceX), Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            int moved = ovAliceCap.insert(4_096, tx);
            h.assertTrue(moved == 4_096, "Overlapping multi-owner PUBLIC cable transfer must deliver full 4096 FE without throwing, got " + moved);
            tx.commit();
        }
        h.assertTrue(reentrantMoved[0] == 0,
                "Re-entry into overlapping physical cable node Y must return 0 cleanly instead of double-spending Basic budget, got " + reentrantMoved[0]);

        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    h.assertTrue(extSink.getAmountAsLong() > 0L,
                            "ReMatter Energy Cube must push FE across Matter Cable into external mod sink");
                    clearExternalFe(h, extSinkPos, extSrcPos, extDstPos, loopA, loopB, mixProxyPos, mixMekReturnPos, net1Src, bridgePos, ovSrcPos, ovProxyPos);
                })
                .thenSucceed();
    }

    private static void relayExternalModCompat(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var level = h.getLevel();

        // 1. Auto-alignment between two external FE blocks + axial-only Capabilities.Energy.BLOCK
        BlockPos extW = new BlockPos(1, 2, 2);
        BlockPos relayPos = new BlockPos(2, 2, 2);
        BlockPos extE = new BlockPos(3, 2, 2);
        var src = new SimpleEnergyHandler(50_000, 50_000, 50_000);
        src.set(20_000);
        var dst = new SimpleEnergyHandler(50_000, 50_000, 50_000);
        placeExternalFe(h, extW, src);
        placeExternalFe(h, extE, dst);

        var placeCtx = new BlockPlaceContext(level, owner, InteractionHand.MAIN_HAND,
                new ItemStack(ModContent.MATTER_CABLE_RELAY.get()),
                new BlockHitResult(Vec3.atCenterOf(h.absolutePos(relayPos)), Direction.UP, h.absolutePos(relayPos), false));
        var placedState = Objects.requireNonNull(ModContent.MATTER_CABLE_RELAY.get().getStateForPlacement(placeCtx));
        h.assertTrue(placedState.getValue(MatterCableRelayBlock.FACING).getAxis() == Direction.Axis.X,
                "Relay must auto-align along X axis between two external FE neighbors, got " + placedState.getValue(MatterCableRelayBlock.FACING));

        // Place unowned PUBLIC relay facing EAST (Input=WEST, Output=EAST)
        h.setBlock(relayPos, ModContent.MATTER_CABLE_RELAY.get().defaultBlockState()
                .setValue(MatterCableRelayBlock.FACING, Direction.EAST));
        var relay = h.getBlockEntity(relayPos, MatterCableRelayBlockEntity.class);
        BlockPos relayAbs = h.absolutePos(relayPos);

        // Energy Matrix AUTOMATIC port & Energy Cube must treat Relay as a conduit, NOT a direct external source!
        h.assertFalse(MatterCablePower.isDirectEnergyNeighbor(level, relayAbs),
                "Matter Cable Relay must be recognized as a conduit (not direct neighbor) by Energy Cube & AUTOMATIC Matrix ports");

        // Capability exposed ONLY on axial faces (WEST/EAST), null on NORTH/SOUTH/UP/DOWN!
        h.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, relayAbs, Direction.WEST) != null
                        && level.getCapability(Capabilities.Energy.BLOCK, relayAbs, Direction.EAST) != null,
                "Relay must expose Capabilities.Energy.BLOCK on both axial faces");
        h.assertTrue(level.getCapability(Capabilities.Energy.BLOCK, relayAbs, Direction.NORTH) == null
                        && level.getCapability(Capabilities.Energy.BLOCK, relayAbs, Direction.UP) == null,
                "Relay must NOT expose Capabilities.Energy.BLOCK on non-axial redstone side faces");

        // Unowned PUBLIC relay routes external FE directly!
        var westCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, relayAbs, Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(westCap.insert(1_200, tx) == 1_200, "Unowned PUBLIC relay must route direct external FE");
            tx.commit();
        }
        h.assertTrue(dst.getAmountAsLong() == 1_200L, "External sink behind unowned PUBLIC relay must receive 1200 FE");

        // Claim relay and configure 250 FE/t cap; run on the next game tick so the tick's transport budget starts fresh!
        relay.placedBy(owner);
        relay.configure(GateMode.ALWAYS_OPEN, ChannelMode.POWER_ONLY, DirectionMode.ONE_WAY,
                250, 0, MatterCableRelayBlockEntity.STATE_ALL, false, List.of(), "");

        h.startSequence()
                .thenExecuteAfter(1, () -> {
                    var freshWest = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, relayAbs, Direction.WEST));
                    try (var tx = Transaction.openRoot()) {
                        int moved = freshWest.insert(1_000, tx);
                        h.assertTrue(moved == 250, "250 FE/t relay cap must limit direct external FE to 250, got " + moved);
                        tx.commit();
                    }
                    try (var tx = Transaction.openRoot()) {
                        h.assertTrue(freshWest.insert(1_000, tx) == 0, "Remaining tick budget on 250 FE/t relay must be 0");
                        tx.commit();
                    }
                    var eastCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, relayAbs, Direction.EAST));
                    h.assertTrue(eastCap.getCapacityAsLong() == 0L, "ONE_WAY relay output face must report 0 input capacity");
                    try (var tx = Transaction.openRoot()) {
                        h.assertTrue(eastCap.insert(1_000, tx) == 0, "ONE_WAY relay must reject reverse external FE into output face");
                        tx.commit();
                    }
                })
                .thenExecuteAfter(1, () -> {
                    var freshWest = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, relayAbs, Direction.WEST));
                    try (var tx = Transaction.openRoot()) {
                        int moved = freshWest.insert(1_000, tx);
                        h.assertTrue(moved == 250, "Next tick must replenish 250 FE/t cap, got " + moved);
                        tx.commit();
                    }

                    // External blockstate rotation by 180 degrees (EAST -> WEST) outside Matter Wrench must invalidate caches & reverse diode!
                    level.setBlock(relayAbs, level.getBlockState(relayAbs).setValue(MatterCableRelayBlock.FACING, Direction.WEST), 3);
                    h.assertTrue(Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, relayAbs, Direction.WEST)).getCapacityAsLong() == 0L,
                            "After external 180-degree rotation to WEST, WEST face is now ONE_WAY output (0 input capacity)");

                    // MATTER_ONLY blocks external FE!
                    relay.configure(GateMode.ALWAYS_OPEN, ChannelMode.MATTER_ONLY, DirectionMode.BIDIRECTIONAL,
                            0, 0, MatterCableRelayBlockEntity.STATE_ALL, false, List.of(), "");
                    h.assertTrue(freshWest.getCapacityAsLong() == 0L, "MATTER_ONLY relay must report 0 FE capacity");
                    try (var tx = Transaction.openRoot()) {
                        h.assertTrue(freshWest.insert(1_000, tx) == 0, "MATTER_ONLY relay must block external FE");
                        tx.commit();
                    }

                    // REDSTONE_HIGH with no redstone signal blocks external FE!
                    relay.configure(GateMode.REDSTONE_HIGH, ChannelMode.BOTH, DirectionMode.BIDIRECTIONAL,
                            0, 0, MatterCableRelayBlockEntity.STATE_ALL, false, List.of(), "");
                    try (var tx = Transaction.openRoot()) {
                        h.assertTrue(freshWest.insert(1_000, tx) == 0, "Closed REDSTONE_HIGH relay must block external FE");
                        tx.commit();
                    }

                    // 2. External Source -> ONE_WAY Relay -> Matter Cable -> External Sink: verifies downstream cable border glow!
                    BlockPos ext2W = new BlockPos(1, 2, 6);
                    BlockPos relay2Pos = new BlockPos(2, 2, 6);
                    BlockPos cable2Pos = new BlockPos(3, 2, 6);
                    BlockPos ext2E = new BlockPos(4, 2, 6);
                    var src2 = new SimpleEnergyHandler(50_000, 50_000, 50_000);
                    src2.set(15_000);
                    var dst2 = new SimpleEnergyHandler(50_000, 50_000, 50_000);
                    placeExternalFe(h, ext2W, src2);
                    placeExternalFe(h, ext2E, dst2);
                    h.setBlock(relay2Pos, ModContent.MATTER_CABLE_RELAY.get().defaultBlockState()
                            .setValue(MatterCableRelayBlock.FACING, Direction.EAST));
                    var relay2 = h.getBlockEntity(relay2Pos, MatterCableRelayBlockEntity.class);
                    relay2.placedBy(owner);
                    relay2.configure(GateMode.ALWAYS_OPEN, ChannelMode.BOTH, DirectionMode.ONE_WAY,
                            0, 0, MatterCableRelayBlockEntity.STATE_ALL, false, List.of(), "");
                    h.setBlock(cable2Pos, ModContent.MATTER_CABLE.get());
                    CableOwners.set(level, h.absolutePos(cable2Pos), owner.getUUID());

                    var relay2West = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(relay2Pos), Direction.WEST));
                    try (var tx = Transaction.openRoot()) {
                        int moved = relay2West.insert(2_000, tx);
                        h.assertTrue(moved == 2_000, "External -> ONE_WAY Relay -> Matter Cable -> External must move 2000 FE, got " + moved);
                        tx.commit();
                    }
                    h.assertTrue(dst2.getAmountAsLong() == 2_000L, "External sink behind Relay + Matter Cable must receive 2000 FE");
                    h.assertTrue(h.getBlockState(cable2Pos).getValue(MatterCableBlock.POWERED),
                            "Matter Cable downstream of open Relay fed by external source must glow (POWERED=true)");

                    clearExternalFe(h, extW, extE, ext2W, ext2E);
                })
                .thenSucceed();
    }

    private static void wrenchPassthroughAndTags(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var level = h.getLevel();
        var wrench = new ItemStack(ModContent.MATTER_WRENCH.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, wrench);

        // 1. Shift+Right-Click on a non-ReMatter block (Stone): BOTH useOn AND the Minecraft fallback use() must return PASS!
        BlockPos stonePos = new BlockPos(2, 2, 2);
        h.setBlock(stonePos, Blocks.STONE);
        BlockPos stoneAbs = h.absolutePos(stonePos);
        // Position player 1.5 blocks away looking straight down at the top of the Stone block!
        owner.setPos(stoneAbs.getX() + 0.5, stoneAbs.getY() + 1.0, stoneAbs.getZ() + 0.5);
        owner.setXRot(90.0F);
        owner.setYRot(0.0F);
        owner.setShiftKeyDown(true);
        var nonRematterHit = new BlockHitResult(Vec3.atCenterOf(stoneAbs), Direction.UP, stoneAbs, false);
        var useOnRes = wrench.getItem().useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, nonRematterHit));
        h.assertTrue(useOnRes == InteractionResult.PASS,
                "Shift+Right-Click useOn with Matter Wrench on Stone must return PASS");
        var fallbackUseRes = wrench.getItem().use(level, owner, InteractionHand.MAIN_HAND);
        h.assertTrue(fallbackUseRes == InteractionResult.PASS,
                "Fallback MatterWrenchItem.use while aiming at non-ReMatter block must return PASS without stealing off-hand or cycling mode");
        h.assertTrue(MatterWrenchItem.mode(wrench) == WrenchMode.PORTS,
                "Mode must remain PORTS after Shift+Right-Click on non-ReMatter block + use() fallback");

        // 2. Looking up at open air -> MatterWrenchItem.use MUST cycle mode!
        owner.setXRot(-90.0F);
        var airUseRes = wrench.getItem().use(level, owner, InteractionHand.MAIN_HAND);
        h.assertTrue(airUseRes.consumesAction() && MatterWrenchItem.mode(wrench) == WrenchMode.ROTATE,
                "Shift+Right-Click in open air must still cycle wrench mode to ROTATE");

        // 3. Shift+Right-Click on a ReMatter Machine still cycles wrench mode as expected.
        BlockPos decPos = new BlockPos(4, 2, 2);
        h.setBlock(decPos, ModContent.MACHINES.get(MachineKind.DECOMPOSER).get().defaultBlockState());
        h.getBlockEntity(decPos, MachineBlockEntity.class).placedBy(owner);
        var decHit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(decPos)), Direction.UP, h.absolutePos(decPos), false);
        var rematterResult = WrenchInteractions.interact(owner, level, h.absolutePos(decPos), wrench, decHit);
        h.assertTrue(rematterResult.consumesAction() && MatterWrenchItem.mode(wrench) == WrenchMode.DISMANTLE,
                "Shift+Right-Click with Matter Wrench on a ReMatter block must still cycle mode to DISMANTLE");
        owner.setShiftKeyDown(false);

        // 4. Verify critical cross-mod item & block tags are loaded cleanly by the server!
        h.assertTrue(wrench.is(Tags.Items.TOOLS_WRENCH), "Matter Wrench must be in #c:tools/wrench");
        h.assertTrue(wrench.is(ItemTags.create(Identifier.fromNamespaceAndPath("c", "wrenches"))),
                "Matter Wrench must be in #c:wrenches");
        h.assertTrue(new ItemStack(ModContent.GUIDE.get()).is(ItemTags.create(Identifier.fromNamespaceAndPath("c", "books"))),
                "ReMatter Guide must be in #c:books");
        h.assertTrue(ModContent.MATTER_CABLE.get().defaultBlockState()
                        .is(BlockTags.create(Identifier.fromNamespaceAndPath("pipe_connector", "pipe_block"))),
                "Matter Cable must be in #pipe_connector:pipe_block");
        h.assertTrue(ModContent.MACHINES.get(MachineKind.DECOMPOSER).get().defaultBlockState()
                        .is(BlockTags.create(Identifier.fromNamespaceAndPath("c", "relocation_not_supported"))),
                "ReMatter machines must be in #c:relocation_not_supported");
        h.assertTrue(ModContent.MATTER_CABLE.get().defaultBlockState()
                        .is(BlockTags.create(Identifier.fromNamespaceAndPath("mekanism", "cardboard_blacklist"))),
                "Matter Cables must be in #mekanism:cardboard_blacklist");
        h.assertTrue(dev.latrox.rematter.primal.PrimalContent.DEEPSLATE_PRIMAL_ORE.get().defaultBlockState()
                        .is(BlockTags.create(Identifier.fromNamespaceAndPath("productivebees", "dupe_blacklist"))),
                "Primal Ores must be in #productivebees:dupe_blacklist");
        h.succeed();
    }

    private static void cableSecurityAndTrustRevocation(GameTestHelper h) {
        var alice = h.makeMockPlayer(GameType.SURVIVAL);
        var bob = h.makeMockPlayer(GameType.SURVIVAL);
        var level = h.getLevel();
        var trustData = SecurityTrustSavedData.get(level.getServer());

        // 1. Foreign PRIVATE Crystal Generator must NOT feed or energize Bob's cable (nor reach an external sink behind Bob's cable).
        BlockPos genPos = new BlockPos(1, 2, 2);
        BlockPos bobCablePos = new BlockPos(2, 2, 2);
        BlockPos extOutPos = new BlockPos(3, 2, 2);
        var extOut = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        placeExternalFe(h, extOutPos, extOut);
        h.setBlock(genPos, ModContent.MACHINES.get(MachineKind.CRYSTAL_GENERATOR).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH));
        var gen = h.getBlockEntity(genPos, MachineBlockEntity.class);
        gen.placedBy(alice);
        gen.setSecurityMode(alice, SecurityMode.PRIVATE);
        gen.energy().set(5_000);
        h.setBlock(bobCablePos, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(bobCablePos), bob.getUUID(), SecurityMode.PUBLIC);

        var bobCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(bobCablePos), Direction.WEST));
        MatterCablePower.energizeIfCable(bobCap);
        try (var tx = Transaction.openRoot()) {
            int moved = bobCap.insert(2_000, tx);
            h.assertTrue(moved == 0, "Foreign cable must reject FE pushed by Alice's PRIVATE Crystal Generator, got " + moved);
            tx.commit();
        }
        h.assertFalse(h.getBlockState(bobCablePos).getValue(MatterCableBlock.POWERED),
                "Foreign cable must NOT glow when Alice's PRIVATE Crystal Generator calls energizeIfCable");
        h.assertTrue(extOut.getAmountAsLong() == 0L, "External sink behind foreign cable must receive 0 FE from PRIVATE generator");

        // 2. Scan-order independence: [ExtA] -> [Alice PUBLIC Cable X] -- [Unowned PUBLIC Cable Y] <- [ExtB], with [Alice PRIVATE Decomposer] on X.
        //    Pushing at X first caches Alice's view; pushing at Y afterwards MUST NOT inherit Alice's identity to reach Alice's PRIVATE Decomposer!
        BlockPos extXPos = new BlockPos(1, 2, 5);
        BlockPos cableX = new BlockPos(2, 2, 5);
        BlockPos cableY = new BlockPos(3, 2, 5);
        BlockPos extYPos = new BlockPos(4, 2, 5);
        BlockPos alicePrivDecPos = new BlockPos(2, 2, 6);
        var srcX = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        srcX.set(20_000); // Full so srcX cannot absorb FE pushed from Y; only alicePrivDec has room!
        var srcY = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        srcY.set(20_000);
        placeExternalFe(h, extXPos, srcX);
        placeExternalFe(h, extYPos, srcY);
        h.setBlock(cableX, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(cableX), alice.getUUID(), SecurityMode.PUBLIC);
        h.setBlock(cableY, ModContent.MATTER_CABLE.get());
        // cableY stays unowned PUBLIC!
        h.setBlock(alicePrivDecPos, ModContent.MACHINES.get(MachineKind.DECOMPOSER).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.SOUTH));
        var alicePrivDec = h.getBlockEntity(alicePrivDecPos, MachineBlockEntity.class);
        alicePrivDec.placedBy(alice);
        alicePrivDec.setSecurityMode(alice, SecurityMode.PRIVATE);

        var capX = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(cableX), Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(capX.insert(1_000, tx) == 1_000, "Alice's own PUBLIC cable X must feed Alice's PRIVATE Decomposer");
            tx.commit();
        }
        var capY = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(cableY), Direction.EAST));
        try (var tx = Transaction.openRoot()) {
            int moved = capY.insert(1_000, tx);
            h.assertTrue(moved == 0, "Unowned PUBLIC cable Y must NOT inherit Alice's cached identity to feed Alice's PRIVATE Decomposer, got " + moved);
            tx.commit();
        }
        h.assertTrue(alicePrivDec.energy().getAmountAsLong() == 1_000L, "Only Alice's cable push (1000 FE) should reach Alice's PRIVATE Decomposer");

        // 3. Trust revocation immediately invalidates cached TRUSTED cable components without touching any blocks!
        BlockPos tSrcPos = new BlockPos(1, 2, 9);
        BlockPos tAliceCable = new BlockPos(2, 2, 9);
        BlockPos tBobCable = new BlockPos(3, 2, 9);
        BlockPos tBobDecPos = new BlockPos(4, 2, 9);
        var tSrc = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        tSrc.set(10_000);
        placeExternalFe(h, tSrcPos, tSrc);
        h.setBlock(tAliceCable, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(tAliceCable), alice.getUUID(), SecurityMode.TRUSTED);
        h.setBlock(tBobCable, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(tBobCable), bob.getUUID(), SecurityMode.TRUSTED);
        h.setBlock(tBobDecPos, ModContent.MACHINES.get(MachineKind.DECOMPOSER).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH));
        var tBobDec = h.getBlockEntity(tBobDecPos, MachineBlockEntity.class);
        tBobDec.placedBy(bob);
        tBobDec.setSecurityMode(bob, SecurityMode.TRUSTED);

        trustData.add(alice.getUUID(), bob.getUUID(), "Bob");
        trustData.add(bob.getUUID(), alice.getUUID(), "Alice");
        var tCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(tAliceCable), Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(tCap.insert(1_000, tx) == 1_000, "Mutually trusted cables must transfer FE");
            tx.commit();
        }
        // Revoke trust (only increments TRUST_EPOCH — zero chunk block mutations!).
        trustData.remove(alice.getUUID(), bob.getUUID());
        try (var tx = Transaction.openRoot()) {
            int moved = tCap.insert(1_000, tx);
            h.assertTrue(moved == 0, "Revoking trust must immediately block cached TRUSTED cable transfer, got " + moved);
            tx.commit();
        }
        trustData.clear(alice.getUUID());
        trustData.clear(bob.getUUID());

        // 4. FTB Teams party leave: A-TRUSTED -> B-TRUSTED -> External Sink (no ReMatter endpoint, no trustEpoch change!)
        BlockPos ftbSrcPos = new BlockPos(1, 2, 12);
        BlockPos ftbAliceCable = new BlockPos(2, 2, 12);
        BlockPos ftbBobCable = new BlockPos(3, 2, 12);
        BlockPos ftbExtSinkPos = new BlockPos(4, 2, 12);
        var ftbSrc = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        ftbSrc.set(10_000);
        var ftbExtSink = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        placeExternalFe(h, ftbSrcPos, ftbSrc);
        placeExternalFe(h, ftbExtSinkPos, ftbExtSink);
        h.setBlock(ftbAliceCable, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(ftbAliceCable), alice.getUUID(), SecurityMode.TRUSTED);
        h.setBlock(ftbBobCable, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(ftbBobCable), bob.getUUID(), SecurityMode.TRUSTED);

        boolean[] inSameFtbTeam = {true};
        dev.latrox.rematter.compat.TeamAccess.setSharedOverrideForTests((a, b) -> inSameFtbTeam[0]);
        try {
            var ftbCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(ftbAliceCable), Direction.WEST));
            try (var tx = Transaction.openRoot()) {
                int moved = ftbCap.insert(1_000, tx);
                h.assertTrue(moved == 1_000, "FTB teammates must route FE across TRUSTED cables to external sink, got " + moved);
                tx.commit();
            }
            // Simulate leaving FTB Team (zero chunk mutations, zero SecurityTrustSavedData/trustEpoch changes!)
            inSameFtbTeam[0] = false;
            try (var tx = Transaction.openRoot()) {
                int moved = ftbCap.insert(1_000, tx);
                h.assertTrue(moved == 0, "Leaving FTB Team must immediately sever cached A-TRUSTED -> B-TRUSTED -> external sink path, got " + moved);
                tx.commit();
            }
            h.assertTrue(ftbExtSink.getAmountAsLong() == 1_000L, "External sink must receive no FE after FTB team leave");
            // Simulate REJOINING FTB Team after the denied scan was cached -> rejected TRUSTED edge must invalidate & restore FE flow!
            inSameFtbTeam[0] = true;
            try (var tx = Transaction.openRoot()) {
                int moved = ftbCap.insert(1_000, tx);
                h.assertTrue(moved == 1_000, "Rejoining FTB Team must invalidate cached denied TRUSTED edge and restore FE flow, got " + moved);
                tx.commit();
            }
            h.assertTrue(ftbExtSink.getAmountAsLong() == 2_000L, "External sink must hold 2000 FE after FTB team rejoin");
        } finally {
            dev.latrox.rematter.compat.TeamAccess.setSharedOverrideForTests(null);
        }

        clearExternalFe(h, extOutPos, extXPos, extYPos, tSrcPos, ftbSrcPos, ftbExtSinkPos);
        h.succeed();
    }

    private static void lateActivatedCapabilityAndSynthStock(GameTestHelper h) {
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        var level = h.getLevel();

        // 1. Late-activated external FE port (BlockEntity block placed with null capability, later enabled via invalidateCapabilities only!)
        BlockPos srcPos = new BlockPos(1, 2, 2);
        BlockPos cablePos = new BlockPos(2, 2, 2);
        BlockPos lateBarrelPos = new BlockPos(3, 2, 2);
        BlockPos lateBarrelAbs = h.absolutePos(lateBarrelPos).immutable();
        var src = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        src.set(10_000);
        placeExternalFe(h, srcPos, src);
        h.setBlock(cablePos, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(cablePos), owner.getUUID());
        // Barrel placed with NO capability in EXTERNAL_BLOCKS (models disabled external mod face).
        EXTERNAL_BLOCKS.remove(lateBarrelAbs);
        h.setBlock(lateBarrelPos, Blocks.BARREL);
        level.invalidateCapabilities(lateBarrelAbs);

        var cap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(cablePos), Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(cap.insert(1_000, tx) == 0, "Disabled external port must accept 0 FE initially");
            tx.commit();
        }
        // Now activate the external port purely via capability invalidation (NO BlockState change, NO neighbor block change!).
        var lateSink = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        EXTERNAL_BLOCKS.put(lateBarrelAbs, lateSink);
        level.invalidateCapabilities(lateBarrelAbs);

        try (var tx = Transaction.openRoot()) {
            int moved = cap.insert(1_500, tx);
            h.assertTrue(moved == 1_500, "Late-activated external FE port must immediately receive FE after invalidateCapabilities, got " + moved);
            tx.commit();
        }
        h.assertTrue(lateSink.getAmountAsLong() == 1_500L, "Late-activated sink must hold 1500 FE");

        // 1b. Late-activated external FE port on a VANILLA NON-BlockEntity block (COPPER_BLOCK with initially null capability!)
        BlockPos cSrcPos = new BlockPos(6, 2, 2);
        BlockPos cCablePos = new BlockPos(7, 2, 2);
        BlockPos lateCopperPos = new BlockPos(8, 2, 2);
        BlockPos lateCopperAbs = h.absolutePos(lateCopperPos).immutable();
        var cSrc = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        cSrc.set(10_000);
        placeExternalFe(h, cSrcPos, cSrc);
        h.setBlock(cCablePos, ModContent.MATTER_CABLE.get());
        CableOwners.set(level, h.absolutePos(cCablePos), owner.getUUID());
        EXTERNAL_BLOCKS.remove(lateCopperAbs);
        h.setBlock(lateCopperPos, Blocks.COPPER_BLOCK);
        level.invalidateCapabilities(lateCopperAbs);

        var cCap = Objects.requireNonNull(level.getCapability(Capabilities.Energy.BLOCK, h.absolutePos(cCablePos), Direction.WEST));
        try (var tx = Transaction.openRoot()) {
            h.assertTrue(cCap.insert(1_000, tx) == 0, "Initially disabled COPPER_BLOCK FE provider must accept 0 FE");
            tx.commit();
        }
        var lateCopperSink = new SimpleEnergyHandler(20_000, 20_000, 20_000);
        EXTERNAL_BLOCKS.put(lateCopperAbs, lateCopperSink);
        level.invalidateCapabilities(lateCopperAbs);
        try (var tx = Transaction.openRoot()) {
            int moved = cCap.insert(1_500, tx);
            h.assertTrue(moved == 1_500, "Late-activated vanilla non-BE COPPER_BLOCK FE provider must immediately receive FE, got " + moved);
            tx.commit();
        }
        h.assertTrue(lateCopperSink.getAmountAsLong() == 1_500L, "Late-activated COPPER_BLOCK sink must hold 1500 FE");

        // 2. Authoritative synthesisStock in MachineExtensions.view() across >512 network substance types!
        //    Bay 0 holds 512 alphabetically earlier filler substances (overflowing MachineSnapshot.MAX_ENTRIES=512);
        //    Bay 1 holds rematter:iron & rematter:carbon, which sort AFTER filler_* and vanish from the 512-row UI stock list.
        BlockPos drivePos = new BlockPos(1, 2, 6);
        BlockPos synthPos = new BlockPos(2, 2, 6);
        h.setBlock(drivePos, ModContent.MACHINES.get(MachineKind.DRIVE).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH));
        var drive = h.getBlockEntity(drivePos, MachineBlockEntity.class);
        drive.placedBy(owner);
        var fillerCell = new ItemStack(ModContent.MATTER_CELL_4M.get());
        var fillerInv = new dev.latrox.rematter.chemistry.MatterInventory(MatterLimits.CELL_4M, MatterCellData.MAX_TYPES);
        for (int idx = 0; idx < 512; idx++) {
            fillerInv.insert(dev.latrox.rematter.chemistry.SubstanceKey.of(
                    String.format("aaa_filler:type_%03d", idx), dev.latrox.rematter.chemistry.Phase.SOLID), 10L);
        }
        fillerCell.set(MatterCellData.TYPE.get(), MatterCellData.from(fillerInv));
        drive.setItem(drive.cellSlotForBay(0), fillerCell);

        var matterCell = new ItemStack(ModContent.MATTER_CELL_4M.get());
        var matterInv = new dev.latrox.rematter.chemistry.MatterInventory(MatterLimits.CELL_4M, MatterCellData.MAX_TYPES);
        matterInv.insert(BuiltinChemistry.IRON, 4_000L);
        matterInv.insert(BuiltinChemistry.CARBON, 1_500L);
        matterCell.set(MatterCellData.TYPE.get(), MatterCellData.from(matterInv));
        drive.setItem(drive.cellSlotForBay(1), matterCell);

        h.setBlock(synthPos, ModContent.MACHINES.get(MachineKind.SYNTHESIZER).get().defaultBlockState()
                .setValue(MachineBlock.FACING, Direction.NORTH));
        var synth = h.getBlockEntity(synthPos, MachineBlockEntity.class);
        synth.placedBy(owner);

        var uiStock = MachineSnapshots.stock(synth, MachineSnapshot.MAX_ENTRIES);
        h.assertTrue(uiStock.totalTypes() == 514 && uiStock.entries().size() == 512,
                "Network must exceed 512-row stock list cap (total=514, listed=512)");
        h.assertTrue(uiStock.entries().stream().noneMatch(e -> e.id().equals("rematter:iron")),
                "512-row UI stock list should truncate rematter:iron behind 512 earlier types");

        var view = synth.extensions().view();
        h.assertTrue(view.has("synthesisStockReady") && view.get("synthesisStockReady").getAsBoolean(),
                "Synthesizer extras must report synthesisStockReady=true when connected to Drive");
        h.assertTrue(view.has("synthesisStockComplete") && view.get("synthesisStockComplete").getAsBoolean(),
                "Synthesizer extras must report synthesisStockComplete=true even with >512 total network types");
        var stockObj = view.getAsJsonObject("synthesisStock");
        h.assertTrue(stockObj != null && stockObj.get("rematter:iron|solid").getAsLong() == 4_000L
                        && stockObj.get("rematter:carbon|solid").getAsLong() == 1_500L,
                "Authoritative synthesisStock must report exact iron & carbon even when truncated from 512-row UI stock");

        h.startSequence()
                .thenExecuteAfter(2, () -> {
                    h.assertTrue(h.getBlockState(cablePos).getValue(MatterCableBlock.EAST)
                                    && h.getBlockState(cablePos).getValue(MatterCableBlock.DEVICE_BY_DIRECTION.get(Direction.EAST)),
                            "Matter Cable flange must connect automatically after late BARREL capability activation");
                    h.assertTrue(h.getBlockState(cCablePos).getValue(MatterCableBlock.EAST)
                                    && h.getBlockState(cCablePos).getValue(MatterCableBlock.DEVICE_BY_DIRECTION.get(Direction.EAST)),
                            "Matter Cable flange must connect automatically after late vanilla non-BE COPPER_BLOCK capability activation");
                    clearExternalFe(h, srcPos, lateBarrelPos, cSrcPos, lateCopperPos);
                })
                .thenSucceed();
    }
}
