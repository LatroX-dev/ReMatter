package dev.latrox.rematter.content;

import com.google.gson.JsonObject;
import dev.latrox.rematter.chemistry.SubstanceKey;
import net.minecraft.core.BlockPos;

/** A read-only first-fit observation, in the exact store/bay order used by conversion credit. */
final class ConverterDestination {
    record View(String status,BlockPos position,int bay,String item,long used,long capacity) {
        boolean ready(){return status.equals("ready");}
        JsonObject json(){
            var out=new JsonObject();out.addProperty("status",status);
            if(ready()){
                out.addProperty("x",position.getX());out.addProperty("y",position.getY());out.addProperty("z",position.getZ());
                out.addProperty("bay",bay+1);out.addProperty("item",item);out.addProperty("used",used);out.addProperty("capacity",capacity);out.addProperty("free",capacity-used);
            }
            return out;
        }
    }
    private ConverterDestination(){}
    static View observe(MachineMatterNetwork network,SubstanceKey target,long amount){
        if(!network.ready())return missing(network.status());
        for(var store:network.checkpointStores()){
            int bay=store.conversionBay(target,amount);
            if(bay<0)continue;
            var cell=store.cellViews().get(bay);
            return new View("ready",store.getBlockPos(),bay,cell.itemId(),cell.used(),cell.capacity());
        }
        return missing("no_compatible_cell");
    }
    static View missing(String status){return new View(status,BlockPos.ZERO,-1,"",0,0);}
}
