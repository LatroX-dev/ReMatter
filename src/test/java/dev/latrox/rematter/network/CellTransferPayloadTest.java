package dev.latrox.rematter.network;

import dev.latrox.rematter.celltransfer.CellTransferSelection;
import dev.latrox.rematter.chemistry.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CellTransferPayloadTest {
    private static SubstanceKey key(int number){String tail=Integer.toString(number);return SubstanceKey.of("future:"+"x".repeat(249-tail.length())+tail,Phase.SOLID);}
    @Test void theFullSourceAndRetainedSelectionWindowFitsTheClientboundPayloadLimit(){
        var rows=new ArrayList<CellTransferPayload.Entry>();var selected=new TreeSet<SubstanceKey>();
        for(int i=0;i<2048;i++){var key=key(i);rows.add(new CellTransferPayload.Entry(key,i<1536?1:0));if(i>=1536)selected.add(key);}
        var source=new CellTransferPayload.SlotView(1,true,512,64000,512);var target=new CellTransferPayload.SlotView(1,true,0,64000,0);
        var payload=new CellTransferPayload(9,123,"ready",true,false,false,new CellTransferSelection(true,false,selected),List.of(source,source,source,target,CellTransferPayload.SlotView.EMPTY,CellTransferPayload.SlotView.EMPTY),rows,1536,0,0,0);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{CellTransferPayload.CODEC.encode(buffer,payload);assertTrue(buffer.readableBytes()<1_048_576);assertEquals(payload,CellTransferPayload.CODEC.decode(buffer));assertEquals(0,buffer.readableBytes());}finally{buffer.release();}
    }
    @Test void aConfigurationDeltaStaysSmallEvenForTheLongestAllowedId(){
        var payload=new CellTransferConfigPayload(4,Long.MAX_VALUE,CellTransferConfigPayload.Action.CELL_TOGGLE,5,key(9),false);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{CellTransferConfigPayload.CODEC.encode(buffer,payload);assertTrue(buffer.readableBytes()<1024);assertEquals(payload,CellTransferConfigPayload.CODEC.decode(buffer));}finally{buffer.release();}
    }
    @Test void invalidSlotsKeysAndRowsAreRejectedBeforeHandling(){
        assertThrows(IllegalArgumentException.class,()->new CellTransferConfigPayload(1,0,CellTransferConfigPayload.Action.CELL_ANY,6,null,false));
        assertThrows(IllegalArgumentException.class,()->new CellTransferConfigPayload(1,0,CellTransferConfigPayload.Action.GLOBAL_CLEAR,0,null,false));
        assertThrows(IllegalArgumentException.class,()->new CellTransferConfigPayload(1,0,CellTransferConfigPayload.Action.CELL_TOGGLE,0,null,false));
        assertThrows(IllegalArgumentException.class,()->new CellTransferConfigPayload(1,-1,CellTransferConfigPayload.Action.TRANSFER,-1,null,false));
        assertThrows(IllegalArgumentException.class,()->new CellTransferPayload.SlotView(1,true,64001,64000,1));
    }
}
