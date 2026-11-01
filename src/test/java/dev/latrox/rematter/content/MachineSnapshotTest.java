package dev.latrox.rematter.content;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MachineSnapshotTest {
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
