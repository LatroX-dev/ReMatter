package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyMatterDataTest {
    @Test void oldUnlimitedTypeInventoryAndUnknownDefinitionsRemainExact() {
        var raw = sample(); var rows = raw.getListOrEmpty("contents");
        for(int i=0;i<513;i++) {
            var row = new CompoundTag(); row.putString("substance", "missing:retained_"+i);
            row.putString("phase", Phase.values()[i%3].name()); row.putLong("amount",i+1L); rows.add(row);
        }
        var inventory = LegacyMatterData.open(raw).orElseThrow();
        assertEquals(513,inventory.typeCount()); assertEquals(Integer.MAX_VALUE,inventory.maximumTypes());
        assertEquals(131841,inventory.total()); assertEquals(1_000_000,inventory.capacity());
        assertEquals(513,inventory.amount(SubstanceKey.of("missing:retained_512",Phase.values()[512%3])));
    }
    @Test void futureFieldsAndWrongNbtTypesRemainOpaqueAndRoundTripUnchanged() {
        var future = sample(); future.putLong("future_reserved",64);
        var rowField = sample(); var row = new CompoundTag(); row.putString("substance","rematter:copper");
        row.putString("phase","SOLID");row.putLong("amount",17);row.putString("future_owner","reserved");
        rowField.getListOrEmpty("contents").add(row);
        var fractional = sample(); fractional.putDouble("capacity",1_000_000.5);
        for (Tag raw : new Tag[]{future,rowField,fractional,StringTag.valueOf("unrecognized format")}) {
            assertTrue(LegacyMatterData.open(raw).isEmpty());
            var decoded = MatterStorageState.RAW_CODEC.parse(NbtOps.INSTANCE,raw).getOrThrow();
            assertEquals(raw,MatterStorageState.RAW_CODEC.encodeStart(NbtOps.INSTANCE,decoded).getOrThrow());
        }
    }
    private static CompoundTag sample() {
        var raw = new CompoundTag();raw.putInt("schema",1);raw.putLong("capacity",1_000_000);raw.put("contents",new ListTag());return raw;
    }
}
