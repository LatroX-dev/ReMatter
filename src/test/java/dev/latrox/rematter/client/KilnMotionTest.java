package dev.latrox.rematter.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KilnMotionTest {
    @Test void pressHeatAndCoolHaveDistinctMechanicalStates() {
        var motion = new KilnMotion();
        motion.advance(0, true, 0);
        for (int tick = 1; tick <= 30; tick++) motion.advance(tick, true, 0);
        assertEquals(1, motion.compression());
        assertEquals(0, motion.heat(), "Pressing must not show an already heated chamber");
        for (int tick = 31; tick <= 60; tick++) motion.advance(tick, true, 1);
        assertEquals(1, motion.compression());
        assertEquals(1, motion.heat());
        for (int tick = 61; tick <= 90; tick++) motion.advance(tick, true, 2);
        assertEquals(0, motion.compression());
        assertEquals(0, motion.heat());
    }

    @Test void pauseFreezesThePressButAllowsResidualHeatToFade() {
        var motion = new KilnMotion();
        motion.advance(0, true, 1);
        for (int tick = 1; tick <= 30; tick++) motion.advance(tick, true, 1);
        for (int tick = 31; tick <= 60; tick++) motion.advance(tick, false, 2);
        assertEquals(1, motion.compression(), "A paused process must not finish its return stroke");
        assertEquals(0, motion.heat());
        motion.advance(60.5, true, 2);
        assertTrue(motion.compression() < 1 && motion.compression() > .95F);
    }

    @Test void unseenAndInvalidTimeCannotSnapThePressOrEscapeBounds() {
        var motion = new KilnMotion();
        motion.advance(10_000, true, 0);
        assertEquals(0, motion.compression());
        motion.advance(Double.NaN, true, 1);
        motion.advance(Double.POSITIVE_INFINITY, true, 1);
        motion.advance(1_000_000, true, 0);
        assertEquals(.225F, motion.compression(), .00001F, "Unseen time is limited to five ticks");
        float pose = motion.compression();
        motion.advance(999_000, true, 0);
        assertEquals(pose, motion.compression());
        for (int tick = 999_001; tick < 1_002_000; tick++) {
            motion.advance(tick, tick % 97 != 0, tick % 3);
            assertTrue(Float.isFinite(motion.compression()) && motion.compression() >= 0 && motion.compression() <= 1);
            assertTrue(Float.isFinite(motion.heat()) && motion.heat() >= 0 && motion.heat() <= 1);
        }
    }

    @Test void unknownStageDoesNotOperateThePress() {
        var motion = new KilnMotion();
        motion.advance(0, false, -1);
        for (int tick = 1; tick <= 30; tick++) motion.advance(tick, true, -1);
        assertEquals(0, motion.compression());
        assertEquals(0, motion.heat());
    }
}
