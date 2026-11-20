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
    @Test void aThrowingMutationStillClosesItsBracket() {
        var chunk=new ChunkRevisions.State();var before=chunk.current();
        assertThrows(IllegalStateException.class,()->chunk.mutate(()->{throw new IllegalStateException("callback");}));
        var after=chunk.current();
        assertTrue(after.stable());assertNotEquals(before,after);
        assertEquals("placed",chunk.mutate(()->{assertFalse(chunk.current().stable());return "placed";}));
        assertTrue(chunk.current().stable());
    }
    @Test void everyMutationAndTouchAdvancesTheSharedEpoch() {
        var chunk=new ChunkRevisions.State();
        long epoch=ChunkRevisions.epoch();
        chunk.touch();assertNotEquals(epoch,epoch=ChunkRevisions.epoch());
        chunk.begin();assertNotEquals(epoch,epoch=ChunkRevisions.epoch());
        chunk.end();assertNotEquals(epoch,ChunkRevisions.epoch());
    }
}
