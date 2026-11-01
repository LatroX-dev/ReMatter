package dev.latrox.rematter.content;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkRevisionsTest {
    @Test void nestedCallbacksCannotPublishAStableObservation() {
        var chunk=new ChunkRevisions.State();var before=chunk.current();
        chunk.begin();var inside=chunk.current();
        assertFalse(inside.stable());assertNotEquals(before,inside);
        chunk.begin();assertEquals(2,chunk.current().mutationDepth());
        chunk.end();assertFalse(chunk.current().stable());
        chunk.end();var after=chunk.current();
        assertTrue(after.stable());assertNotEquals(before,after);assertNotEquals(inside,after);
    }
    @Test void noOpOrReplacementMutationStillInvalidatesEarlierStamps() {
        var chunk=new ChunkRevisions.State();var first=chunk.current();
        chunk.begin();chunk.end();
        var next=chunk.current();assertNotEquals(first,next);
        chunk.begin();chunk.end();
        assertNotEquals(next,chunk.current());
        assertEquals(0,new ChunkRevisions.State().current().value());
    }
    @Test void unfinishedMutationStaysUnsafeAndUnbalancedReturnsFailClosed() {
        var failed=new ChunkRevisions.State();failed.begin();
        assertFalse(failed.current().stable());
        assertThrows(IllegalStateException.class,()->new ChunkRevisions.State().end());
        assertThrows(IllegalStateException.class,()->ChunkRevisions.current(new Object()));
    }
}
