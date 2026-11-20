package dev.latrox.rematter.client.anim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotionCueTest {
    @Test void firstSampleOnlyRecordsWhereTheMechanismStands() {
        var cue = new MotionCue(32, 0);
        // A controller that starts on a machine which has already turned many cycles must not play the backlog.
        assertFalse(cue.advance(32 * 57 + 5, 1, .6F));
        assertFalse(cue.advance(32 * 57 + 6, 1, .6F));
        assertTrue(cue.advance(32 * 58, 1, .6F));
    }
    @Test void firesExactlyOncePerCrossingAtAnySampleRate() {
        for (double step : new double[]{.25, 1, 1.4, 3.7}) {
            var cue = new MotionCue(24, 0);
            int fired = 0;
            double turns = 1;
            for (; turns < 24 * 50; turns += step) if (cue.advance(turns, 1, .6F)) fired++;
            // The last sample stood in cycle 49: forty-nine cue points lie between it and the first one.
            assertEquals(49, MotionCue.index(turns - step, 24, 0), "step " + step);
            assertEquals(49, fired, "step " + step);
        }
    }
    @Test void phaseOffsetMovesTheCuePointWithinTheCycle() {
        assertEquals(-1, MotionCue.index(59.9, 80, .75));
        assertEquals(0, MotionCue.index(60, 80, .75));
        assertEquals(0, MotionCue.index(139.9, 80, .75));
        assertEquals(1, MotionCue.index(140, 80, .75));
        var cue = new MotionCue(80, .75);
        assertFalse(cue.advance(0, 1, .6F));
        for (int turns = 1; turns < 60; turns++) assertFalse(cue.advance(turns, 1, .6F), "turns " + turns);
        assertTrue(cue.advance(60, 1, .6F));
        for (int turns = 61; turns < 140; turns++) assertFalse(cue.advance(turns, 1, .6F), "turns " + turns);
        assertTrue(cue.advance(140, 1, .6F));
    }
    @Test void crossingBelowMinimumDriveIsSilentAndSpent() {
        var cue = new MotionCue(32, 0);
        assertFalse(cue.advance(30, .59F, .6F));
        assertFalse(cue.advance(33, .59F, .6F));
        // The mechanism speeds up inside the same cycle: that cycle's cue is gone, the next one plays.
        assertFalse(cue.advance(40, 1, .6F));
        assertTrue(cue.advance(64, .6F, .6F));
    }
    @Test void jumpAcrossSeveralCyclesFiresOnceAndStandingStillOrReversingNever() {
        var cue = new MotionCue(20, 0);
        assertFalse(cue.advance(5, 1, .6F));
        assertTrue(cue.advance(5 + 20 * 7, 1, .6F));
        assertFalse(cue.advance(5 + 20 * 7, 1, .6F));
        assertFalse(cue.advance(5 + 20 * 7 + 3, 1, .6F));
        assertFalse(cue.advance(5, 1, .6F));
        assertFalse(cue.advance(Double.NaN, 1, .6F));
        assertTrue(cue.advance(25, 1, .6F));
    }
    @Test void resetStartsOverWithoutASound() {
        var cue = new MotionCue(24, 0);
        assertFalse(cue.advance(10, 1, .6F));
        cue.reset();
        // Another envelope, far ahead: the index is taken over, not played.
        assertFalse(cue.advance(24 * 900, 1, .6F));
        assertTrue(cue.advance(24 * 901, 1, .6F));
    }
    @Test void pitchFollowsLoopTempoAndProgressWithinThePlayableRange() {
        assertEquals(1, MotionCue.pitch(1, false, 1.4F, false, 1, 1, .5F));
        assertEquals(1.4F, MotionCue.pitch(1, true, 1.4F, false, 1, 1, .5F));
        assertEquals(.9F * 1.2F, MotionCue.pitch(.9F, true, 1.2F, false, 1, 1, 0), .00001F);
        assertEquals(.94F, MotionCue.pitch(1, true, 1, true, .94F, 1.08F, 0), .00001F);
        assertEquals(1.01F, MotionCue.pitch(1, true, 1, true, .94F, 1.08F, .5F), .00001F);
        assertEquals(1.08F, MotionCue.pitch(1, true, 1, true, .94F, 1.08F, 1), .00001F);
        assertEquals(1.08F, MotionCue.pitch(1, true, 1, true, .94F, 1.08F, 7), .00001F);
        assertEquals(1.4F * 1.08F, MotionCue.pitch(1, true, 1.4F, true, .94F, 1.08F, 1), .00001F);
        assertEquals(2, MotionCue.pitch(1.8F, true, 1.4F, false, 1, 1, 0));
        assertEquals(.5F, MotionCue.pitch(.5F, false, 1, true, .5F, .6F, 0));
        assertEquals(1, MotionCue.pitch(1, true, Float.NaN, false, 1, 1, 0));
    }
}
