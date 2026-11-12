package dev.latrox.rematter.energy;

import java.util.UUID;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnergyCubeDataTest {
    private static CompoundTag valid(EnergyCubeTier tier){
        var raw=new CompoundTag();raw.putInt("schema",1);raw.putInt("energy",tier.capacity());raw.putInt("side_modes",1);
        var items=new ListTag();for(int i=0;i<tier.chargingSlots();i++)items.add(new CompoundTag());raw.put("items",items);return raw;
    }
    @Test void allTiersAcceptExactBoundsAndTheirOwnInventorySize(){
        for(var tier:EnergyCubeTier.values()){
            var raw=valid(tier);assertNull(EnergyCubeData.rejection(raw,tier,true,tier.chargingSlots()));
            raw.putInt("energy",0);assertNull(EnergyCubeData.rejection(raw,tier,true,tier.chargingSlots()));
        }
    }
    @Test void invalidEnergyIsProtectedInsteadOfBeingClamped(){
        for(var tier:EnergyCubeTier.values())for(int amount:new int[]{-1,tier.capacity()+1,Integer.MAX_VALUE}){
            var raw=valid(tier);raw.putInt("energy",amount);var before=raw.copy();
            assertNotNull(EnergyCubeData.rejection(raw,tier,true,tier.chargingSlots()));assertEquals(before,raw);
        }
        var raw=valid(EnergyCubeTier.BASIC);raw.putLong("energy",1);
        assertNotNull(EnergyCubeData.rejection(raw,EnergyCubeTier.BASIC,true,1));
    }
    @Test void futureFieldsSchemasAndUnreadableItemsArePreserved(){
        var raw=valid(EnergyCubeTier.BASIC);raw.putInt("schema",99);raw.putString("future_resource","do not discard");var before=raw.copy();
        assertNotNull(EnergyCubeData.rejection(raw,EnergyCubeTier.BASIC,true,1));assertEquals(before,raw);
        raw=valid(EnergyCubeTier.BASIC);assertNotNull(EnergyCubeData.rejection(raw,EnergyCubeTier.BASIC,false,1));
        assertNotNull(EnergyCubeData.rejection(raw,EnergyCubeTier.BASIC,true,2));
        raw.putString("items","not a list");assertNotNull(EnergyCubeData.rejection(raw,EnergyCubeTier.BASIC,true,1));
    }
    @Test void malformedOwnerAndReservedSideBitsCannotAuthorizeTransfers(){
        var raw=valid(EnergyCubeTier.BASIC);raw.putString("owner",UUID.randomUUID().toString());
        assertNull(EnergyCubeData.rejection(raw,EnergyCubeTier.BASIC,true,1));
        raw.putString("owner","not an owner");assertNotNull(EnergyCubeData.rejection(raw,EnergyCubeTier.BASIC,true,1));
        for(int packed:new int[]{-1,3,4096,3<<10})assertFalse(EnergyCubeData.validModes(packed));
        for(int mode=0;mode<3;mode++){int packed=0;for(int i=0;i<6;i++)packed|=mode<<(i*2);assertTrue(EnergyCubeData.validModes(packed));}
    }
    @Test void rawCodecKeepsUnknownNestedDataWhileIgnoringOnlyVanillaMetadata(){
        var raw=valid(EnergyCubeTier.BASIC);raw.putInt("schema",44);var future=new CompoundTag();future.putLong("stored",9000000000L);raw.put("future",future);
        var encoded=EnergyCubeData.RAW.codec().encodeStart(NbtOps.INSTANCE,raw).getOrThrow();
        assertEquals(raw,EnergyCubeData.RAW.codec().parse(NbtOps.INSTANCE,encoded).getOrThrow());
        var withMetadata=raw.copy();withMetadata.putString("id","rematter:energy_cube");withMetadata.putInt("x",17);
        assertEquals(raw,EnergyCubeData.RAW.codec().parse(NbtOps.INSTANCE,withMetadata).getOrThrow());
    }
    @Test void chunkPackingMetadataDoesNotLockCurrentPayloadOrHideFutureFields(){
        for(var tier:EnergyCubeTier.values())for(boolean packed:new boolean[]{false,true}){
            var raw=valid(tier);raw.putString("owner",UUID.randomUUID().toString());
            var disk=raw.copy();disk.putString("id","rematter:energy_cube");disk.putInt("x",17);disk.putInt("y",64);disk.putInt("z",-3);disk.putBoolean("keepPacked",packed);
            var before=disk.copy();var decoded=EnergyCubeData.RAW.codec().parse(NbtOps.INSTANCE,disk).getOrThrow();
            assertEquals(raw,decoded);assertEquals(before,disk);
            assertNull(EnergyCubeData.rejection(decoded,tier,true,tier.chargingSlots()));
            var future=new CompoundTag();future.putLong("stored",9_000_000_000L);disk.put("future_resource",future);
            var protectedPayload=raw.copy();protectedPayload.put("future_resource",future.copy());
            decoded=EnergyCubeData.RAW.codec().parse(NbtOps.INSTANCE,disk).getOrThrow();
            assertEquals(protectedPayload,decoded);
            assertNotNull(EnergyCubeData.rejection(decoded,tier,true,tier.chargingSlots()));
        }
    }
}
