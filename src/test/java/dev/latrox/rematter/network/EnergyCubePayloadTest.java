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
            var snapshot=new EnergyCubePayload(4,tier.ordinal(),tier.capacity(),1,false,"Main Buffer");
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try{EnergyCubePayload.CODEC.encode(buffer,snapshot);assertEquals(snapshot,EnergyCubePayload.CODEC.decode(buffer));assertEquals(0,buffer.readableBytes());}
            finally{buffer.release();}
        }
        var rename=new EnergyCubePayload.Rename(4,"Reactor Reserve");
        var rbuf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{EnergyCubePayload.Rename.CODEC.encode(rbuf,rename);assertEquals(rename,EnergyCubePayload.Rename.CODEC.decode(rbuf));assertEquals(0,rbuf.readableBytes());}
        finally{rbuf.release();}
    }
    @Test void malformedSnapshotsAreRejectedBeforeReachingTheClientMenu(){
        assertThrows(IllegalArgumentException.class,()->new EnergyCubePayload(-1,0,0L,0,false));
        assertThrows(IllegalArgumentException.class,()->new EnergyCubePayload(1,EnergyCubeTier.values().length,0L,0,false));
        assertThrows(IllegalArgumentException.class,()->new EnergyCubePayload(1,0,EnergyCubeTier.BASIC.capacity()+1L,0,false));
        assertDoesNotThrow(()->new EnergyCubePayload(1,0,0L,3,false));
        assertThrows(IllegalArgumentException.class,()->new EnergyCubePayload(1,0,0L,-1,false));
        assertThrows(IllegalArgumentException.class,()->new EnergyCubePayload(1,0,0L,4096,false));
    }
}
