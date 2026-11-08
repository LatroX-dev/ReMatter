package dev.latrox.rematter.content;

import dev.latrox.rematter.network.MachineProgressPayload;
import dev.latrox.rematter.network.MachineStockPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MachineSnapshotTest {
    private static final List<MachineSnapshot.CellView> EMPTY_BAYS = Collections.nCopies(MachineCellAccess.DRIVE_BAYS, MachineSnapshot.CellView.EMPTY);

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, value);
            T decoded = codec.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally { buffer.release(); }
    }
    private static MachineSnapshot snapshot(MachineKind kind, String status, long used, long capacity, boolean running,
                                            MachineSnapshot.Preview preview, String source, MachineSnapshot.StructureView structure,
                                            MachineSnapshot.NetworkView network, List<MachineSnapshot.CellView> cells) {
        return new MachineSnapshot(3, kind.ordinal(), status, -1, "", 0, 1, used, capacity, false, running, 0, "", false, 0,
                preview, source, structure, network, false, "", MachineSnapshot.OrdersView.EMPTY, cells);
    }
    private static MachineSnapshot industrialSnapshot(MachineKind kind, MachineSnapshot.StructureView structure) {
        return snapshot(kind, "ready", 0, 0, false, MachineSnapshot.Preview.EMPTY, "none", structure, MachineSnapshot.NetworkView.EMPTY, List.of());
    }
    private static MachineSnapshot driveSnapshot(String status, List<MachineSnapshot.CellView> bays) {
        return snapshot(MachineKind.DRIVE, status, 0, 0, false, MachineSnapshot.Preview.EMPTY, "none",
                MachineSnapshot.StructureView.EMPTY, MachineSnapshot.NetworkView.EMPTY, bays);
    }

    @Test void asymmetricModuleCountsAndEnergySavingSurviveMenuCodec() {
        // Two cutters, three basins, two filters: 10 base work/tick, with one speed upgrade.
        var structure = new MachineSnapshot.StructureView(5,5,6,20,2,"ready",2,3,2,0,0,6);
        var snapshot = industrialSnapshot(MachineKind.INDUSTRIAL_PREPARATOR, structure);
        assertEquals(snapshot, roundTrip(MachineSnapshot.CODEC, snapshot));
    }
    @Test void moduleCountsCannotAdvertiseUnearnedRateOrEfficiency() {
        assertThrows(IllegalArgumentException.class,()->industrialSnapshot(MachineKind.INDUSTRIAL_PREPARATOR,
                new MachineSnapshot.StructureView(5,5,6,12,2,"ready",2,3,2,0,0,6)));
        assertThrows(IllegalArgumentException.class,()->industrialSnapshot(MachineKind.INDUSTRIAL_PREPARATOR,
                new MachineSnapshot.StructureView(5,5,6,10,2,"ready",2,3,2,0,0,12)));
        assertThrows(IllegalArgumentException.class,()->industrialSnapshot(MachineKind.INDUSTRIAL_REACTOR,
                new MachineSnapshot.StructureView(5,5,6,10,2,"ready",2,3,2,0,0,6)));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(3,3,5,6,1,"ready",1,2,1,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(5,5,6,0,0,"structure_modules_missing",1,1,1,0,0,0));
    }
    @Test void doubleSurfaceCountsRoundTripAndRemainBoundedByActualMountingSpace() {
        for (var kind : List.of(MachineKind.INDUSTRIAL_PREPARATOR, MachineKind.INDUSTRIAL_REACTOR)) {
            var structure = kind == MachineKind.INDUSTRIAL_PREPARATOR
                    ? new MachineSnapshot.StructureView(9,9,9,970,96,"ready",96,1,1,0,0,0)
                    : new MachineSnapshot.StructureView(9,9,9,970,97,"ready",0,0,0,1,97,0);
            var snapshot = industrialSnapshot(kind, structure);
            assertEquals(snapshot, roundTrip(MachineSnapshot.CODEC, snapshot));
        }
        assertThrows(IllegalArgumentException.class, () ->
                new MachineSnapshot.StructureView(9,9,9,980,49,"ready",49,49,49,0,0,23));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.StructureView(9,9,9,196,98,"ready",0,0,0,1,98,0));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.StructureView(9,9,9,294,98,"ready",98,49,49,0,0,23));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.StructureView(5,5,6,36,18,"ready",0,0,0,1,18,0));
    }
    @Test void networkTotalsAreDistinctFromLocalDiskAndKeepLargeTypeCounts() {
        var network = new MachineSnapshot.NetworkView("ready",12,3,2,90000,128000,1024);
        var snapshot = snapshot(MachineKind.DRIVE, "ready", 1000, 64000, false, MachineSnapshot.Preview.EMPTY, "local",
                MachineSnapshot.StructureView.EMPTY, network, EMPTY_BAYS);
        var result = roundTrip(MachineSnapshot.CODEC, snapshot);
        assertEquals(snapshot, result); assertEquals(1000, result.used()); assertEquals(90000, result.network().used());
        int largest = MatterPipeBlock.Tier.RESONANT.networkLimit();
        assertEquals(largest, new MachineSnapshot.NetworkView("ready",largest,3,2,1,100,1).nodes());
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("network_unloaded",12,3,2,1,100,1));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("ready",largest + 1,3,2,1,100,1));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("ready",1,3,2,1,100,1));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("ready",12,3,19,1,100,1));
    }
    @Test void processingMachineSnapshotsCannotAdvertiseAnInstalledCell() {
        var disk = new MachineSnapshot.CellView(true,true,"rematter:matter_cell_64k",64000,0,512);
        assertThrows(IllegalArgumentException.class,()->snapshot(MachineKind.SYNTHESIZER, "ready", 0, 0, false,
                MachineSnapshot.Preview.EMPTY, "network", MachineSnapshot.StructureView.EMPTY, MachineSnapshot.NetworkView.EMPTY, List.of(disk)));
    }
    @Test void rectangularStructureRoundTripsWithoutChangingAxisOrder() {
        var structure = new MachineSnapshot.StructureView(4, 6, 5, 6,3,"ready",0,0,0,1,3,0);
        var snapshot = snapshot(MachineKind.INDUSTRIAL_REACTOR, "working", 0, 0, true, MachineSnapshot.Preview.EMPTY, "none",
                structure, MachineSnapshot.NetworkView.EMPTY, List.of());
        var decoded = roundTrip(MachineSnapshot.CODEC, snapshot);
        assertEquals(snapshot, decoded);
        assertEquals(24, decoded.structure().volume());
        assertFalse(MachineSnapshot.StructureView.EMPTY.formed());
        assertEquals(0, MachineSnapshot.StructureView.EMPTY.volume());
        assertEquals(3,structure.moduleCount());
        assertEquals(343, new MachineSnapshot.StructureView(9, 9, 9, 970,49,"ready",49,48,1,0,0,0).volume());
    }
    @Test void malformedStructureSizesRatesAndControllerKindsAreRejected() {
        for (var values : List.of(new int[]{0,3,5,4}, new int[]{3,10,5,4}, new int[]{3,3,2,4},
                new int[]{3,3,5,0}, new int[]{9,9,9,981}))
            assertThrows(IllegalArgumentException.class,
                    () -> new MachineSnapshot.StructureView(values[0], values[1], values[2], values[3],1,"ready",0,0,0,1,1,0));
        for (var kind : List.of(MachineKind.PREPARATOR, MachineKind.INDUSTRIAL_REACTOR))
            assertThrows(IllegalArgumentException.class, () -> industrialSnapshot(kind, new MachineSnapshot.StructureView(3,3,3,4,1,"ready",0,0,0,1,1,0)));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(3,3,5,8,2,"ready",0,0,0,1,2,0));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(9,9,9,4,50,"ready",0,0,0,1,1,0));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(3,3,5,4,1,"structure_modules_missing",0,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(3,3,5,0,0,"arbitrary_unbounded_message",0,0,0,0,0,0));
    }
    @Test void blockedStructureReasonRoundTripsWithoutClaimingWorkCapacity() {
        var structure = new MachineSnapshot.StructureView(5,4,5,0,0,"structure_module_incomplete",0,0,0,0,0,0);
        var snapshot = snapshot(MachineKind.INDUSTRIAL_PREPARATOR, structure.diagnostic(), 0, 0, true, MachineSnapshot.Preview.EMPTY,
                "none", structure, MachineSnapshot.NetworkView.EMPTY, List.of());
        assertEquals(snapshot, roundTrip(MachineSnapshot.CODEC, snapshot));
        assertFalse(structure.formed()); assertEquals(0, structure.workRate()); assertEquals(0, structure.moduleCount());
    }
    @Test void unpaidProgressAndFullBufferTravelSeparatelyAndStayBounded() {
        var progress = new MachineProgressPayload(8, MachineBlockEntity.ENERGY_CAPACITY, 12, 90);
        assertEquals(progress, roundTrip(MachineProgressPayload.CODEC, progress));
        assertThrows(IllegalArgumentException.class, () -> new MachineProgressPayload(8, MachineBlockEntity.ENERGY_CAPACITY + 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MachineProgressPayload(8, 0, 91, 90));
        assertThrows(IllegalArgumentException.class, () -> new MachineProgressPayload(8, -1, 0, 0));
    }
    @Test void fullMountingSurfacesAndUpgradesRoundTripWithoutOverflowOrRateInflation() {
        var structure = new MachineSnapshot.StructureView(9,9,9,970,49,"ready",49,48,1,0,0,0);
        var snapshot = industrialSnapshot(MachineKind.INDUSTRIAL_PREPARATOR, structure);
        assertEquals(snapshot, roundTrip(MachineSnapshot.CODEC, snapshot));
        assertThrows(IllegalArgumentException.class,()->industrialSnapshot(MachineKind.INDUSTRIAL_REACTOR, structure));
    }
    @Test void sixPhysicalBaysKeepTheirOrderAndUnreadableStateAcrossTheWire() {
        var primary = new MachineSnapshot.CellView(true,true,"rematter:matter_cell_64k",64000,2,512);
        var unreadable = new MachineSnapshot.CellView(true,false,"rematter:matter_cell_1m",0,0,0);
        var cells = new java.util.ArrayList<>(EMPTY_BAYS);
        cells.set(0,primary); cells.set(5,unreadable);
        var snapshot = driveSnapshot("unknown_schema", cells);
        cells.set(5,MachineSnapshot.CellView.EMPTY); // Snapshot owns an immutable copy.
        var decoded = roundTrip(MachineSnapshot.CODEC, snapshot);
        assertEquals(snapshot,decoded); assertEquals(6,decoded.cells().size());
        assertEquals(primary,decoded.cells().getFirst()); assertEquals(unreadable,decoded.cells().getLast());
        assertEquals(MachineSnapshot.CellView.EMPTY,decoded.cells().get(4));
        assertThrows(IllegalArgumentException.class,()->driveSnapshot("ready", List.of(primary)));
        assertThrows(IllegalArgumentException.class,()->driveSnapshot("ready", Collections.nCopies(7,primary)));
    }
    @Test void mountedCellViewsNameTheItemIdForTheMenuAndTheSummary() {
        var readable = MatterCellMount.installedView(Identifier.fromNamespaceAndPath("rematter","matter_cell_64k"),true,64000,2);
        var unreadable = MatterCellMount.installedView(Identifier.fromNamespaceAndPath("rematter","matter_cell_1m"),false,1_000_000,0);
        assertEquals("rematter:matter_cell_64k",readable.itemId());
        var cells = new java.util.ArrayList<>(EMPTY_BAYS);
        cells.set(0,readable); cells.set(3,unreadable);
        var snapshot = driveSnapshot("unknown_schema", cells);
        assertEquals(snapshot, roundTrip(MachineSnapshot.CODEC, snapshot));
        var summary = new MachineBlockEntity.Summary("unknown_schema",0,0,1,0,0,"none",0,List.of(),false,0,"",
                MachineSnapshot.StructureView.EMPTY,cells,0,0);
        assertEquals(unreadable,summary.cells().get(3));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.CellView(true,true,"64k Matter Cell",64000,0,512));
    }
    @Test void networkCountsAllSixCellsPerDriveWithoutAcceptingInventedBays() {
        var full=new MachineSnapshot.NetworkView("ready",64,64,384,1,24_576_000,512);
        assertEquals(384,full.cells());
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("ready",64,64,385,1,24_576_000,512));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("ready",12,1,7,1,64000,1));
    }
    @Test void cellPresenceCapacityAndTypeCountRoundTripWithoutContents() {
        var cells = new java.util.ArrayList<>(EMPTY_BAYS);
        cells.set(0, new MachineSnapshot.CellView(true,true,"rematter:matter_cell_64k",64000,2,512));
        var snapshot = snapshot(MachineKind.DRIVE, "ready", 1000, 64000, true, MachineSnapshot.Preview.EMPTY, "local",
                MachineSnapshot.StructureView.EMPTY, MachineSnapshot.NetworkView.EMPTY, cells);
        assertEquals(snapshot, roundTrip(MachineSnapshot.CODEC, snapshot));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.CellView(true,true,"rematter:matter_cell_64k",64000,513,512));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.CellView(true,true,"",64000,0,512));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.CellView(false,false,"rematter:matter_cell_64k",0,0,0));
    }
    @Test void wireRoundTripPreservesExactRequirementsAvailabilityAndQueue() {
        var input = new MachineSnapshot.Amount("rematter:copper", "solid", 1000, 900, true);
        var preview = new MachineSnapshot.Preview("minecraft:copper_ingot", List.of(input), List.of(), true,
                Long.MAX_VALUE, Integer.MAX_VALUE, "item", "minecraft:copper_ingot", 1, List.of());
        var snapshot = new MachineSnapshot(4, MachineKind.SYNTHESIZER.ordinal(), "missing_matter", 0, "minecraft:copper_ingot", 1, 1,
                1000, 2000, true, true, 0, "", false, 2, preview, "network", MachineSnapshot.StructureView.EMPTY,
                MachineSnapshot.NetworkView.EMPTY, false, "", MachineSnapshot.OrdersView.EMPTY, List.of());
        assertEquals(snapshot, roundTrip(MachineSnapshot.CODEC, snapshot));
    }
    @Test void unknownSubstancesStayMarkedAcrossTheWire() {
        var unknown = new MachineSnapshot.Entry("rematter:removed_alloy", "solid", 9000, false);
        var stock = new MachineStockPayload(4, true, true, 2, List.of(unknown, new MachineSnapshot.Entry("rematter:copper", "solid", 5, true)), List.of());
        var decoded = roundTrip(MachineStockPayload.CODEC, stock);
        assertEquals(stock, decoded);
        assertFalse(decoded.changed().getFirst().known());
        var amount = new MachineSnapshot.Amount("rematter:removed_alloy", "solid", 10, 0, false);
        var preview = new MachineSnapshot.Preview("rematter:recipe", List.of(amount), List.of(), true, 1, 1, "matter", "", 0, List.of());
        assertFalse(roundTrip(MachineSnapshot.CODEC, snapshot(MachineKind.REACTOR, "selection_unavailable", 0, 0, false, preview, "none",
                MachineSnapshot.StructureView.EMPTY, MachineSnapshot.NetworkView.EMPTY, List.of())).preview().inputs().getFirst().known());
    }
    @Test void stockChangesReportTheTotalInsteadOfSilentlyTruncating() {
        var removed = new MachineSnapshot.StockKey("rematter:copper", "liquid");
        var change = new MachineStockPayload(2, false, true, 900, List.of(new MachineSnapshot.Entry("rematter:iron", "solid", 1, true)), List.of(removed));
        var decoded = roundTrip(MachineStockPayload.CODEC, change);
        assertEquals(change, decoded); assertEquals(900, decoded.totalTypes());
        var entry = new MachineSnapshot.Entry("rematter:iron", "solid", 1, true);
        assertThrows(IllegalArgumentException.class, () -> new MachineStockPayload(2, true, true, 0,
                Collections.nCopies(MachineSnapshot.MAX_ENTRIES + 1, entry), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MachineStockPayload(2, true, true, -1, List.of(), List.of()));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeVarInt(2); buffer.writeBoolean(true); buffer.writeBoolean(true); buffer.writeVarInt(0);
            buffer.writeVarInt(MachineSnapshot.MAX_ENTRIES + 1);
            assertThrows(IllegalArgumentException.class, () -> MachineStockPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void heldOrderViewsRoundTripAndRejectInconsistentCounts() {
        var copper = new MachineSnapshot.Amount("rematter:copper", "solid", 10, 0, true);
        var water = new MachineSnapshot.Resource("fluid", "minecraft:water", 100);
        String order = UUID.randomUUID().toString();
        var orders = new MachineSnapshot.OrdersView(2, 1, order, List.of(copper), List.of(water), true);
        var snapshot = new MachineSnapshot(4, MachineKind.NETWORK_INTERFACE.ordinal(), "automation_quarantined", -1, "", 0, 1,
                0, 0, true, false, 0, "", false, 0, MachineSnapshot.Preview.EMPTY, "none", MachineSnapshot.StructureView.EMPTY,
                MachineSnapshot.NetworkView.EMPTY, false, "ae2", orders, List.of());
        assertEquals(snapshot, roundTrip(MachineSnapshot.CODEC, snapshot));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.OrdersView(0, 0, order, List.of(copper), List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.OrdersView(0, 1, "", List.of(copper), List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.OrdersView(0, 1, "not-an-order", List.of(), List.of(), true));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.OrdersView(65, 0, "", List.of(), List.of(), true));
    }
    @Test void maximalBoundedViewsFitOneMegabyte() {
        String id = "test:" + "x".repeat(507);
        var entry = new MachineSnapshot.Entry(id, "solid", Long.MAX_VALUE, true);
        var amount = new MachineSnapshot.Amount(id, "solid", Long.MAX_VALUE, Long.MAX_VALUE, true);
        var resource = new MachineSnapshot.Resource("fluid", id, Long.MAX_VALUE);
        var preview = new MachineSnapshot.Preview(id, Collections.nCopies(64, amount), Collections.nCopies(64, amount), false,
                Long.MAX_VALUE, Integer.MAX_VALUE, "matter", "", 0, Collections.nCopies(64, resource));
        var snapshot = new MachineSnapshot(1, MachineKind.SYNTHESIZER.ordinal(), "ready", 0, id, 1, 64, Long.MAX_VALUE, Long.MAX_VALUE,
                false, true, MachineBlockEntity.TANK_CAPACITY, "minecraft:water", true, 0, preview, "network",
                MachineSnapshot.StructureView.EMPTY, MachineSnapshot.NetworkView.EMPTY, false, "", new MachineSnapshot.OrdersView(64, 64,
                UUID.randomUUID().toString(), Collections.nCopies(64, amount), Collections.nCopies(64, resource), false), List.of());
        var stock = new MachineStockPayload(1, true, true, Integer.MAX_VALUE, Collections.nCopies(MachineSnapshot.MAX_ENTRIES, entry),
                Collections.nCopies(MachineSnapshot.MAX_ENTRIES, entry.key()));
        assertFitsOneMegabyte(MachineSnapshot.CODEC, snapshot);
        assertFitsOneMegabyte(MachineStockPayload.CODEC, stock);
    }
    private static <T> void assertFitsOneMegabyte(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, value);
            assertTrue(buffer.readableBytes() < 1_048_576);
            assertEquals(value, codec.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void malformedAmountsAndOversizedPreviewAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.Entry("test:water", "liquid", -1, true));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.Amount("test:water", "liquid", -1, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.Amount("test:water", "unknown", 1, 0, true));
        var amount = new MachineSnapshot.Amount("test:water", "liquid", 1, 0, true);
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.Preview("test:recipe",
                Collections.nCopies(65, amount), List.of(), true, 1, 1, "matter", "", 0, List.of()));
    }
}
