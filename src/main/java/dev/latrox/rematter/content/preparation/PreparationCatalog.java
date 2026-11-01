package dev.latrox.rematter.content.preparation;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Uses NeoForge's native recipe synchronization, after validating bounded preparation data. */
final class PreparationCatalog {
    private static final int MAX_BYTES=512*1024;
    private PreparationCatalog(){}
    static void sync(OnDatapackSyncEvent event){
        var level=event.getPlayerList().getServer().overworld();
        var entries=PreparationRecipes.serverRecipes(level);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(256,MAX_BYTES),level.registryAccess());
        try {
            buffer.writeVarInt(entries.size());
            for(var entry:entries){buffer.writeUtf(entry.id().toString(),512);PreparationRecipe.STREAM_CODEC.encode(buffer,entry.recipe());}
        } finally {buffer.release();}
        event.sendRecipes(PreparationRecipes.TYPE.get());
    }
}
