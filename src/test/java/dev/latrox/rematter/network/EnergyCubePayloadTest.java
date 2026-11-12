package dev.latrox.rematter.network;

import dev.latrox.rematter.energy.EnergyCubeTier;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnergyCubePayloadTest {
    @Test void fullTierCapacityUsesExactIntSyncWithoutShortTruncation(){
        for(var tier:EnergyCubeTier.values()){
            var snapshot=new EnergyCubePayload(4,tier.ordinal(),tier.capacity(),1,false);
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try{EnergyCubePayload.CODEC.encode(buffer,snapshot);assertEquals(snapshot,EnergyCubePayload.CODEC.decode(buffer));assertEquals(0,buffer.readableBytes());}
            finally{buffer.release();}
        }
    }
    @Test void malformedSnapshotsAreRejectedBeforeReachingTheClientMenu(){
        assertThrows(IllegalArgumentException.class,()->new EnergyCubePayload(-1,0,0,0,false));
        assertThrows(IllegalArgumentException.class,()->new EnergyCubePayload(1,3,0,0,false));
        assertThrows(IllegalArgumentException.class,()->new EnergyCubePayload(1,0,4_000_001,0,false));
        assertThrows(IllegalArgumentException.class,()->new EnergyCubePayload(1,0,0,3,false));
        assertThrows(IllegalArgumentException.class,()->new EnergyCubePayload(1,0,0,4096,false));
    }
}
