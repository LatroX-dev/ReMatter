package dev.latrox.rematter.content;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MachineSnapshotTest {
    @Test void legacyStorageAndStagedDiskRoundTripAsDistinctResourceOwners() {
        var disk = new MachineSnapshot.CellView(true,true,"64k Matter Cell",64000,0,512);
        var snapshot = new MachineSnapshot(3,MachineKind.STORAGE.ordinal(),0,0,0,"ready",-1,"","",0,1,
                List.of(new MachineSnapshot.Entry("rematter:copper","Copper","solid",70000,70000)),true,
                70000,1_000_000,false,false,0,"",false,0,MachineSnapshot.Preview.EMPTY,disk,"legacy");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            MachineSnapshot.CODEC.encode(buffer,snapshot);
            assertEquals(snapshot,MachineSnapshot.CODEC.decode(buffer));
            assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot(3,MachineKind.SYNTHESIZER.ordinal(),
                0,0,0,"ready",-1,"","",0,1,List.of(),true,0,0,false,false,0,"",false,0,
                MachineSnapshot.Preview.EMPTY,disk,"legacy"));
    }
    @Test void rectangularStructureAndEffectiveRateRoundTripWithoutChangingAxisOrder() {
        var structure = new MachineSnapshot.StructureView(4, 6, 5, 6,3,"ready");
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
        assertEquals(343, new MachineSnapshot.StructureView(9, 9, 9, 980,49,"ready").volume());
    }
    @Test void malformedStructureSizesRatesAndControllerKindsAreRejected() {
        for (var values : List.of(new int[]{0,3,5,4}, new int[]{3,10,5,4}, new int[]{3,3,2,4},
                new int[]{3,3,5,0}, new int[]{9,9,9,981}))
            assertThrows(IllegalArgumentException.class,
                    () -> new MachineSnapshot.StructureView(values[0], values[1], values[2], values[3],1,"ready"));
        for (var kind : List.of(MachineKind.PREPARATOR, MachineKind.INDUSTRIAL_REACTOR))
            assertThrows(IllegalArgumentException.class, () -> new MachineSnapshot(1, kind.ordinal(), 0, 0, 0, "idle",
                    -1, "", "", 0, 1, List.of(), true, 0, 0, false, false, 0, "", false, 0,
                    MachineSnapshot.Preview.EMPTY, MachineSnapshot.CellView.EMPTY, "none", new MachineSnapshot.StructureView(3,3,3,4,1,"ready")));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(3,3,5,8,2,"ready"));
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot.StructureView(9,9,9,4,50,"ready"));
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
    @Test void maximumPhysicalColumnsAndUpgradesRoundTripWithoutOverflowOrRateInflation() {
        var snapshot=new MachineSnapshot(9,MachineKind.INDUSTRIAL_PREPARATOR.ordinal(),1_000_000,90,90,"ready",
                -1,"","",0,1,List.of(),true,0,0,false,true,0,"",false,0,MachineSnapshot.Preview.EMPTY,
                MachineSnapshot.CellView.EMPTY,"none",new MachineSnapshot.StructureView(9,9,9,980,49,"ready"));
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {MachineSnapshot.CODEC.encode(buffer,snapshot);assertEquals(snapshot,MachineSnapshot.CODEC.decode(buffer));}
        finally {buffer.release();}
        assertThrows(IllegalArgumentException.class,()->new MachineSnapshot(9,MachineKind.INDUSTRIAL_REACTOR.ordinal(),0,0,0,"ready",
                -1,"","",0,1,List.of(),true,0,0,false,true,0,"",false,0,MachineSnapshot.Preview.EMPTY,
                MachineSnapshot.CellView.EMPTY,"none",new MachineSnapshot.StructureView(9,9,9,980,49,"ready")));
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
