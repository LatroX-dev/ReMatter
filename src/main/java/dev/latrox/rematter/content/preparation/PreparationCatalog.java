package dev.latrox.rematter.content.preparation;

import dev.latrox.rematter.ReMatter;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Uses NeoForge's native recipe synchronization, after validating bounded preparation data. */
final class PreparationCatalog {
    private static final int MAX_BYTES=512*1024;
    private PreparationCatalog(){}
    static void sync(OnDatapackSyncEvent event){
        var level=event.getPlayerList().getServer().overworld();
        var catalogue=PreparationRecipes.serverCatalogue(level);
        if(!catalogue.valid())return;
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(256,MAX_BYTES),level.registryAccess());
        try {
            buffer.writeVarInt(catalogue.entries().size());
            for(var entry:catalogue.entries()){buffer.writeUtf(entry.id().toString(),512);PreparationRecipe.STREAM_CODEC.encode(buffer,entry.recipe());}
        } catch(IndexOutOfBoundsException oversized) {
            ReMatter.LOGGER.error("Preparation recipes exceed {} bytes and are not sent to clients",MAX_BYTES);
            return;
        } finally {buffer.release();}
        event.sendRecipes(PreparationRecipes.TYPE.get());
    }
}
