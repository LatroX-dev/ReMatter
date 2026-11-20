package dev.latrox.rematter.research;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** GameTest-only fault boundary; no fault injection or recovery control is included in the production mod. */
public final class ResearchAuthorityIoFault implements AutoCloseable {
    public static final String MESSAGE="Injected directory failure after atomic publication";
    private final ResearchSavedData data;
    private final Path path;
    private final Runnable originalBoundary;

    private ResearchAuthorityIoFault(MinecraftServer server){
        data=ResearchSavedData.get(server);path=server.getWorldPath(LevelResource.ROOT).resolve("data/rematter/research-authority.json");
        try{
            var boundary=ResearchLedger.class.getDeclaredField("boundary");boundary.setAccessible(true);
            originalBoundary=(Runnable)boundary.get(data.ledger());
        }catch(ReflectiveOperationException problem){throw new IllegalStateException("Could not preserve the test authority callback",problem);}
        data.ledger().authority(()->{
            try{ResearchAuthority.writeState(path,data.ledger().toJson(),parent->{throw new IOException(MESSAGE);});}
            catch(IOException problem){throw new IllegalStateException(MESSAGE,problem);}
        });
    }
    public static ResearchAuthorityIoFault install(MinecraftServer server){return new ResearchAuthorityIoFault(server);}
    public ResearchLedger publishedState() throws IOException{return ResearchAuthority.readState(path);}
    /** Reuse the same disk reader/replacement as startup, while retaining the running GameTest server. */
    public void recoverPublishedState() throws IOException{
        data.replaceLedger(publishedState());data.ledger().authority(originalBoundary);
    }
    @Override public void close(){data.ledger().authority(originalBoundary);}
}
