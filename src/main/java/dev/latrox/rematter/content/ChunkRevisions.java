package dev.latrox.rematter.content;

/** A chunk-local revision changes on both sides of nested mutations, without a global mutation lock. */
public final class ChunkRevisions {
    public record Revision(long value, int mutationDepth) { public boolean stable() { return mutationDepth == 0; } }
    public interface Tracked {
        Revision rematter$chunkRevision();
        void rematter$touchChunkRevision();
    }
    public static final class State {
        private long value;
        private int depth;
        public synchronized void begin() { value++; depth=Math.addExact(depth,1); }
        public synchronized void end() {
            if(depth==0)throw new IllegalStateException("Unbalanced chunk mutation");
            value++;depth--;
        }
        public synchronized Revision current() { return new Revision(value,depth); }
        public synchronized void touch() { value++; }
    }
    private ChunkRevisions() {}
    public static Revision current(Object chunk) {
        if(!(chunk instanceof Tracked tracked))throw new IllegalStateException("Required industrial chunk revision hook is missing");
        return tracked.rematter$chunkRevision();
    }
    static void touch(Object chunk) {
        if (!(chunk instanceof Tracked tracked)) throw new IllegalStateException("Required industrial chunk revision hook is missing");
        tracked.rematter$touchChunkRevision();
    }
}
