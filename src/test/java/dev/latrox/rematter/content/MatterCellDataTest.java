package dev.latrox.rematter.content;

import net.minecraft.nbt.NbtOps;
import dev.latrox.rematter.chemistry.*;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatterCellDataTest {
    @Test void actualNetworkCodecPreservesAll512TypesAndExactLongQuantities() {
        var store=new MatterInventory(Long.MAX_VALUE,512);
        for(int i=0;i<512;i++)store.insert(new SubstanceKey(SubstanceId.parse("test:key_"+i),Phase.values()[i%3]),i==0?9_007_199_254_740_993L:i);
        var data=MatterCellData.from(store);
        var buffer=Unpooled.buffer();
        try {
            MatterCellData.STREAM_CODEC.encode(buffer,data);
            assertTrue(buffer.readableBytes()<1_048_576);
            var decoded=MatterCellData.STREAM_CODEC.decode(buffer);
            assertEquals(0,buffer.readableBytes());assertEquals(data,decoded);
            assertEquals(store.snapshot(),decoded.open(Long.MAX_VALUE).orElseThrow().snapshot());
        } finally {buffer.release();}
    }
    @Test void realCodecPreservesExactMixedPhasesAndUnknownDefinitions() {
        var unknown=new SubstanceKey(SubstanceId.parse("missing_mod:retained_gas"),Phase.GAS);
        var store=new MatterInventory(64000,512);store.insert(BuiltinChemistry.COPPER,1000);store.insert(unknown,17);
        var data=MatterCellData.from(store);
        var encoded=MatterCellData.CODEC.encodeStart(NbtOps.INSTANCE,data).getOrThrow();
        var decoded=MatterCellData.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow();
        assertEquals(data,decoded);assertEquals(store.snapshot(),decoded.open(64000).orElseThrow().snapshot());
    }
    @Test void unknownSchemaRetainsEveryRawFieldAndCannotOpen() {
        var raw=new CompoundTag();raw.putInt("schema",42);raw.putString("unknown_format","preserve exactly");raw.putLong("amount",Long.MAX_VALUE);
        var data=new MatterCellData(raw);raw.putLong("amount",0);
        assertTrue(data.open(64000).isEmpty());assertEquals(Long.MAX_VALUE,data.copyTag().getLongOr("amount",0));
        var copy=data.copyTag();copy.putInt("schema",1);assertEquals(42,data.copyTag().getIntOr("schema",0));
        var roundTrip=MatterCellData.CODEC.parse(NbtOps.INSTANCE,MatterCellData.CODEC.encodeStart(NbtOps.INSTANCE,data).getOrThrow()).getOrThrow();
        assertEquals(data,roundTrip);
    }
    @Test void actualItemTierLimitsCapacityWithoutTruncatingContents() {
        var store=new MatterInventory(1_000_000,512);store.insert(BuiltinChemistry.COPPER,70000);var data=MatterCellData.from(store);
        assertTrue(data.open(64000).isEmpty());assertEquals(70000,data.open(256000).orElseThrow().total());
        assertEquals(256000,data.open(256000).orElseThrow().capacity());
        var forged=data.copyTag();forged.putLong("capacity",Long.MAX_VALUE);
        assertTrue(new MatterCellData(forged).open(64000).isEmpty());assertEquals(70000,data.open(1_000_000).orElseThrow().total());
    }
    @Test void duplicateNegativeAndOversizedDataStayLockedAndRaw() {
        var store=new MatterInventory(64000,512);store.insert(BuiltinChemistry.COPPER,1000);var raw=MatterCellData.from(store).copyTag();
        var rows=raw.getListOrEmpty("contents");rows.add(rows.getCompoundOrEmpty(0).copy());
        var duplicate=new MatterCellData(raw);assertTrue(duplicate.open(64000).isEmpty());assertEquals(2,duplicate.copyTag().getListOrEmpty("contents").size());
        var bad=new CompoundTag();bad.putInt("schema",1);var badRows=new ListTag();
        var row=new CompoundTag();row.putString("substance","rematter:copper");row.putString("phase","SOLID");row.putLong("amount",-1);badRows.add(row);bad.put("contents",badRows);bad.put("reservations",new ListTag());
        assertTrue(new MatterCellData(bad).open(64000).isEmpty());
        var tooMany=new MatterInventory(64000);for(int i=0;i<513;i++)tooMany.insert(new SubstanceKey(SubstanceId.parse("test:key_"+i),Phase.SOLID),1);
        assertThrows(IllegalArgumentException.class,()->MatterCellData.from(tooMany));
    }
    @Test void onlyTheCurrentLayoutOpensAndAnyOtherLayoutStaysUnchanged() {
        var store=new MatterInventory(64000,512);store.insert(BuiltinChemistry.COPPER,1000);
        var current=MatterCellData.from(store).copyTag();
        assertEquals(Set.of("schema","contents","reservations"),current.keySet());
        assertEquals(1000,new MatterCellData(current).open(64000).orElseThrow().total());
        var withoutReservations=current.copy();withoutReservations.remove("reservations");
        var kept=new MatterCellData(withoutReservations);
        assertTrue(kept.open(64000).isEmpty());assertEquals(withoutReservations,kept.copyTag());
        var older=current.copy();older.putInt("schema",0);
        assertTrue(new MatterCellData(older).open(64000).isEmpty());
    }
    @Test void reservationsRemainOwnedAcrossCellPersistenceAndCanCompleteExactlyOnce() {
        var store=new MatterInventory(64000,512);store.insert(BuiltinChemistry.COPPER,1000);
        var token=store.reserve(Map.of(BuiltinChemistry.COPPER,1000L),Map.of()).orElseThrow();
        var restored=MatterCellData.from(store).open(64000).orElseThrow();
        assertEquals(1000,restored.total());assertEquals(0,restored.available(BuiltinChemistry.COPPER));
        assertTrue(restored.commit(token));assertFalse(restored.commit(token));assertEquals(0,restored.total());
    }
    @Test void malformedReservationAndFractionalQuantityNeverBecomeUsableContents() {
        var store=new MatterInventory(64000,512);store.insert(BuiltinChemistry.COPPER,1000);
        var raw=MatterCellData.from(store).copyTag();raw.putString("reservations","unknown encoding");
        var malformed=new MatterCellData(raw);assertTrue(malformed.open(64000).isEmpty());assertEquals(raw,malformed.copyTag());
        var fractional=MatterCellData.from(store).copyTag();fractional.getListOrEmpty("contents").getCompoundOrEmpty(0).putDouble("amount",1000.5);
        assertTrue(new MatterCellData(fractional).open(64000).isEmpty());
        var schema=MatterCellData.from(store).copyTag();schema.putFloat("schema",1.5F);
        assertTrue(new MatterCellData(schema).open(64000).isEmpty());
    }
}
