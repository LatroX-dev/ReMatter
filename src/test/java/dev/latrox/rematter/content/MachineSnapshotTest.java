package dev.latrox.rematter.content;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MachineSnapshotTest {
    @Test void asymmetricModuleCountsAndEnergySavingSurviveMenuCodec() {
        // Two cutters, three basins, two filters: 10 base work/tick, with one speed upgrade.
        var structure=new MachineSnapshot.StructureView(5,5,6,20,2,"ready",2,3,2,0,0,6);
        var snapshot=industrialSnapshot(MachineKind.INDUSTRIAL_PREPARATOR,structure);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            MachineSnapshot.CODEC.encode(buffer,snapshot);
            assertEquals(snapshot,MachineSnapshot.CODEC.decode(buffer));
            assertEquals(0,buffer.readableBytes());
        } finally {buffer.release();}
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
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                MachineSnapshot.CODEC.encode(buffer, snapshot);
                assertEquals(snapshot, MachineSnapshot.CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
        assertThrows(IllegalArgumentException.class, () ->
                new MachineSnapshot.StructureView(9,9,9,980,49,"ready",49,49,49,0,0,23));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.StructureView(9,9,9,196,98,"ready",0,0,0,1,98,0));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.StructureView(9,9,9,294,98,"ready",98,49,49,0,0,23));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.StructureView(5,5,6,36,18,"ready",0,0,0,1,18,0));
    }
    private static MachineSnapshot industrialSnapshot(MachineKind kind,MachineSnapshot.StructureView structure) {
        return new MachineSnapshot(3,kind.ordinal(),0,0,0,"ready",-1,"","",0,1,List.of(),true,0,0,
                false,false,0,"",false,0,MachineSnapshot.Preview.EMPTY,MachineSnapshot.CellView.EMPTY,"none",structure);
    }
    @Test void networkTotalsAreDistinctFromLocalDiskAndKeepLargeTypeCounts() {
        var network=new MachineSnapshot.NetworkView("ready",12,3,2,90000,128000,1024);
        var snapshot=new MachineSnapshot(3,MachineKind.DRIVE.ordinal(),0,0,0,"ready",-1,"","",0,1,
                List.of(),true,1000,64000,false,false,0,"",false,0,MachineSnapshot.Preview.EMPTY,
                MachineSnapshot.CellView.EMPTY,"local",MachineSnapshot.StructureView.EMPTY,network,false);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {MachineSnapshot.CODEC.encode(buffer,snapshot);var result=MachineSnapshot.CODEC.decode(buffer);
            assertEquals(snapshot,result);assertEquals(1000,result.used());assertEquals(90000,result.network().used());
        } finally {buffer.release();}
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("network_unloaded",12,3,2,1,100,1));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("ready",257,3,2,1,100,1));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("ready",1,3,2,1,100,1));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("ready",12,3,19,1,100,1));
    }
    @Test void processingMachineSnapshotsCannotAdvertiseAnInstalledCell() {
        var disk = new MachineSnapshot.CellView(true,true,"64k Matter Cell",64000,0,512);
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot(3,MachineKind.SYNTHESIZER.ordinal(),
                0,0,0,"ready",-1,"","",0,1,List.of(),true,0,0,false,false,0,"",false,0,
                MachineSnapshot.Preview.EMPTY,disk,"network"));
    }
    @Test void rectangularStructureAndEffectiveRateRoundTripWithoutChangingAxisOrder() {
        var structure = new MachineSnapshot.StructureView(4, 6, 5, 6,3,"ready",0,0,0,1,3,0);
        var snapshot = new MachineSnapshot(7, MachineKind.INDUSTRIAL_REACTOR.ordinal(), 64000, 2, 200, "working",
                -1, "", "", 0, 1, List.of(), true, 0, 0, false, true, 0, "", false, 0,
                MachineSnapshot.Preview.EMPTY, MachineSnapshot.CellView.EMPTY, "none", structure);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            MachineSnapshot.CODEC.encode(buffer, snapshot);
            var decoded = MachineSnapshot.CODEC.decode(buffer);
            assertEquals(snapshot, decoded);
            assertEquals(24, decoded.structure().volume());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
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
            assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot(1, kind.ordinal(), 0, 0, 0, "idle",
                    -1, "", "", 0, 1, List.of(), true, 0, 0, false, false, 0, "", false, 0,
                    MachineSnapshot.Preview.EMPTY, MachineSnapshot.CellView.EMPTY, "none", new MachineSnapshot.StructureView(3,3,3,4,1,"ready",0,0,0,1,1,0)));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(3,3,5,8,2,"ready",0,0,0,1,2,0));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(9,9,9,4,50,"ready",0,0,0,1,1,0));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(3,3,5,4,1,"structure_modules_missing"));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(3,3,5,0,0,"arbitrary_unbounded_message"));
    }
    @Test void blockedStructureReasonAndUnpaidProgressRoundTripWithoutClaimingWorkCapacity() {
        var structure=new MachineSnapshot.StructureView(5,4,5,0,0,"structure_module_incomplete");
        var snapshot=new MachineSnapshot(8,MachineKind.INDUSTRIAL_PREPARATOR.ordinal(),64000,12,90,structure.diagnostic(),
                -1,"","",0,1,List.of(),true,0,0,false,true,0,"",false,0,MachineSnapshot.Preview.EMPTY,
                MachineSnapshot.CellView.EMPTY,"none",structure);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {MachineSnapshot.CODEC.encode(buffer,snapshot);assertEquals(snapshot,MachineSnapshot.CODEC.decode(buffer));}
        finally {buffer.release();}
        assertFalse(structure.formed());assertEquals(0,structure.workRate());assertEquals(0,structure.moduleCount());
    }
    @Test void fullMountingSurfacesAndUpgradesRoundTripWithoutOverflowOrRateInflation() {
        var structure=new MachineSnapshot.StructureView(9,9,9,970,49,"ready",49,48,1,0,0,0);
        var snapshot=new MachineSnapshot(9,MachineKind.INDUSTRIAL_PREPARATOR.ordinal(),1_000_000,90,90,"ready",
                -1,"","",0,1,List.of(),true,0,0,false,true,0,"",false,0,MachineSnapshot.Preview.EMPTY,
                MachineSnapshot.CellView.EMPTY,"none",structure);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {MachineSnapshot.CODEC.encode(buffer,snapshot);assertEquals(snapshot,MachineSnapshot.CODEC.decode(buffer));}
        finally {buffer.release();}
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot(9,MachineKind.INDUSTRIAL_REACTOR.ordinal(),0,0,0,"ready",
                -1,"","",0,1,List.of(),true,0,0,false,true,0,"",false,0,MachineSnapshot.Preview.EMPTY,
                MachineSnapshot.CellView.EMPTY,"none",structure));
    }
    @Test void sixPhysicalBaysKeepTheirOrderAndUnreadableStateAcrossTheWire() {
        var primary=new MachineSnapshot.CellView(true,true,"64k Matter Cell",64000,2,512);
        var unknown=new MachineSnapshot.CellView(true,false,"Future cell",0,0,0);
        var cells=new java.util.ArrayList<>(Collections.nCopies(6,MachineSnapshot.CellView.EMPTY));
        cells.set(0,primary);cells.set(5,unknown);
        var snapshot=driveSnapshot(primary,cells);
        cells.set(5,MachineSnapshot.CellView.EMPTY); // Snapshot owns an immutable copy.
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            MachineSnapshot.CODEC.encode(buffer,snapshot);
            var decoded=MachineSnapshot.CODEC.decode(buffer);
            assertEquals(snapshot,decoded);assertEquals(6,decoded.cells().size());
            assertEquals(primary,decoded.cells().getFirst());assertEquals(unknown,decoded.cells().getLast());
            assertEquals(MachineSnapshot.CellView.EMPTY,decoded.cells().get(4));assertEquals(0,buffer.readableBytes());
        } finally {buffer.release();}
        assertThrows(IllegalArgumentException.class,()->driveSnapshot(primary,List.of(primary)));
        assertThrows(IllegalArgumentException.class,()->driveSnapshot(primary,Collections.nCopies(7,primary)));
        assertThrows(IllegalArgumentException.class,()->driveSnapshot(primary,Collections.nCopies(6,MachineSnapshot.CellView.EMPTY)));
    }
    @Test void networkCountsAllSixCellsPerDriveWithoutAcceptingInventedBays() {
        var full=new MachineSnapshot.NetworkView("ready",64,64,384,1,24_576_000,512);
        assertEquals(384,full.cells());
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("ready",64,64,385,1,24_576_000,512));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.NetworkView("ready",12,1,7,1,64000,1));
    }
    private static MachineSnapshot driveSnapshot(MachineSnapshot.CellView first,List<MachineSnapshot.CellView> bays) {
        return new MachineSnapshot(1,MachineKind.DRIVE.ordinal(),0,0,0,"unknown_schema",-1,"","",0,1,List.of(),false,
                0,0,false,false,0,"",false,0,MachineSnapshot.Preview.EMPTY,first,"none",MachineSnapshot.StructureView.EMPTY,
                MachineSnapshot.NetworkView.EMPTY,false,bays);
    }
    @Test void cellPresenceCapacityAndTypeCountRoundTripWithoutContents() {
        var cell=new MachineSnapshot.CellView(true,true,"64k Matter Cell",64000,2,512);
        var snapshot=new MachineSnapshot(1,MachineKind.DRIVE.ordinal(),0,0,0,"ready",-1,"","",0,1,List.of(),true,
                1000,64000,false,true,0,"",false,0,MachineSnapshot.Preview.EMPTY,cell,"local");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {MachineSnapshot.CODEC.encode(buffer,snapshot);assertEquals(snapshot,MachineSnapshot.CODEC.decode(buffer));}
        finally {buffer.release();}
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.CellView(true,true,"cell",64000,513,512));
    }
    @Test void wireRoundTripPreservesExactRequirementsAvailabilityAndQueue() {
        var input = new MachineSnapshot.Amount("rematter:copper", "Copper", "solid", 1000, 900);
        var preview = new MachineSnapshot.Preview("minecraft:copper_ingot", "Copper Ingot", List.of(input), List.of(), true,
                Long.MAX_VALUE, Integer.MAX_VALUE, "item", "minecraft:copper_ingot", 1);
        var snapshot = new MachineSnapshot(4, MachineKind.SYNTHESIZER.ordinal(), 123, 2, 40, "missing_matter", 0,
                "minecraft:copper_ingot", "Copper Ingot", 1, 1,
                List.of(new MachineSnapshot.Entry("rematter:copper", "Copper", "solid", 1000, 900)), true,
                1000, 2000, true, true, 0, "", false, 2, preview);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            MachineSnapshot.CODEC.encode(buffer, snapshot);
            assertEquals(snapshot, MachineSnapshot.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void maximalBoundedSnapshotFitsOneMegabyteEvenWithLongTranslatedNames() {
        String id = "test:" + "x".repeat(507);
        String name = "\u0800".repeat(256);
        var entry = new MachineSnapshot.Entry(id, name, "solid", Long.MAX_VALUE, Long.MAX_VALUE);
        var amount = new MachineSnapshot.Amount(id, name, "solid", Long.MAX_VALUE, Long.MAX_VALUE);
        var preview = new MachineSnapshot.Preview(id, name, Collections.nCopies(64, amount), Collections.nCopies(64, amount), false,
                Long.MAX_VALUE, Integer.MAX_VALUE, "matter", "", 0);
        var snapshot = new MachineSnapshot(1, 2, 1_000_000, 0, 0, "ready", 0, id, name, 1, 64,
                Collections.nCopies(512, entry), false, Long.MAX_VALUE, Long.MAX_VALUE, false, true, 16000, "water", true, 0, preview);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            MachineSnapshot.CODEC.encode(buffer, snapshot);
            assertTrue(buffer.readableBytes() < 1_048_576);
            assertEquals(snapshot, MachineSnapshot.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void malformedAmountsAndOversizedPreviewAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.Entry("test:water", "Water", "liquid", 10, 11));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.Amount("test:water", "Water", "liquid", -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.Amount("test:water", "Water", "unknown", 1, 0));
        var amount = new MachineSnapshot.Amount("test:water", "Water", "liquid", 1, 0);
        assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot.Preview("test:recipe", "Recipe",
                Collections.nCopies(65, amount), List.of(), true, 1, 1, "matter", "", 0));
    }
}
