package dev.latrox.rematter.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MachineMotionTest {
    private static final double TOLERANCE = 1.0e-7;

    @Test void seedProducesRepeatableDistinctParkedPositions() {
        var first = new MachineMotion(41);
        var same = new MachineMotion(41);
        var other = new MachineMotion(42);
        assertEquals(first.phase(), same.phase());
        assertNotEquals(first.phase(), other.phase());
        assertEquals(0, first.drive());
        assertBounded(first);
    }

    @Test void firstObservationDoesNotCatchUpFromWorldCreation() {
        var motion = new MachineMotion(41);
        float initial = motion.phase();
        motion.advance(9_876_543_210.25, true);
        assertEquals(initial, motion.phase());
        assertEquals(0, motion.drive());
        motion.advance(9_876_543_210.5, true);
        assertTrue(motion.drive() > 0);
        assertBounded(motion);
    }

    @Test void idleRemainsStationaryRegardlessOfElapsedWorldTime() {
        var motion = new MachineMotion(41);
        float initial = motion.phase();
        motion.advance(0, false);
        for (int tick = 1; tick <= 200; tick++) motion.advance(tick, false);
        motion.advance(1_000_000, false);
        assertEquals(initial, motion.phase());
        assertEquals(0, motion.drive());
    }

    @Test void startingMotionIsIndependentOfFramePartitioning() {
        var whole = new MachineMotion(41);
        var frames = new MachineMotion(41);
        whole.advance(100, false);
        frames.advance(100, false);
        whole.advance(105, true);
        for (int frame = 1; frame <= 40; frame++) frames.advance(100 + frame / 8.0, true);
        assertEquals(whole.drive(), frames.drive(), TOLERANCE);
        assertEquals(whole.phase(), frames.phase(), TOLERANCE);
        assertTrue(whole.drive() > 0 && whole.drive() < 1);
    }

    @Test void coastingIsIndependentOfFramePartitioning() {
        var whole = runningMotion(41);
        var frames = runningMotion(41);
        whole.advance(45, false);
        for (int frame = 1; frame <= 40; frame++) frames.advance(40 + frame / 8.0, false);
        assertEquals(whole.drive(), frames.drive(), TOLERANCE);
        assertEquals(whole.phase(), frames.phase(), TOLERANCE);
        assertTrue(whole.drive() > 0 && whole.drive() < 1);
    }

    @Test void pausingAndChangingActivityAtTheSameTimeNeverSnapsTheAngle() {
        var motion = runningMotion(41);
        float phase = motion.phase();
        float drive = motion.drive();
        for (int frame = 0; frame < 100; frame++) motion.advance(40, frame % 2 == 0);
        assertEquals(phase, motion.phase());
        assertEquals(drive, motion.drive());
    }

    @Test void stopCoastsThenKeepsItsFinalAngleAndRestartDoesNotResetIt() {
        var motion = runningMotion(41);
        float runningDrive = motion.drive();
        float runningPhase = motion.phase();
        motion.advance(41, false);
        assertTrue(motion.drive() > 0 && motion.drive() < runningDrive);
        assertTrue(forwardDistance(runningPhase, motion.phase()) > 0);
        for (int tick = 42; tick <= 100; tick++) motion.advance(tick, false);
        assertEquals(0, motion.drive());
        float parked = motion.phase();
        motion.advance(200, false);
        motion.advance(200, true);
        assertEquals(parked, motion.phase());
        assertEquals(0, motion.drive());
        motion.advance(200.01, true);
        assertTrue(motion.drive() > 0 && motion.drive() < 0.01);
        assertTrue(forwardDistance(parked, motion.phase()) < 0.001);
    }

    @Test void anUnobservedGapDoesNotFastForwardTheMechanism() {
        var gap = runningMotion(41);
        var bounded = runningMotion(41);
        gap.advance(1_000_000, true);
        bounded.advance(45, true);
        assertEquals(bounded.phase(), gap.phase(), TOLERANCE);
        assertEquals(bounded.drive(), gap.drive(), TOLERANCE);
        assertBounded(gap);
    }

    @Test void invalidTimesAndClockRollbackCannotCorruptOrReverseMotion() {
        var motion = runningMotion(41);
        float phase = motion.phase();
        float drive = motion.drive();
        motion.advance(Double.NaN, false);
        motion.advance(Double.POSITIVE_INFINITY, false);
        motion.advance(Double.NEGATIVE_INFINITY, false);
        motion.advance(5, false);
        assertEquals(phase, motion.phase());
        assertEquals(drive, motion.drive());
        motion.advance(5.25, true);
        assertTrue(forwardDistance(phase, motion.phase()) < 0.01);
        assertBounded(motion);
    }

    @Test void repeatedCyclesRemainBoundedAcrossPhaseWrapping() {
        var motion = new MachineMotion(Long.MIN_VALUE);
        motion.advance(0, false);
        float previous = motion.phase();
        for (int tick = 1; tick <= 20_000; tick++) {
            motion.advance(tick, tick % 160 < 100);
            assertBounded(motion);
            assertTrue(forwardDistance(previous, motion.phase()) < 0.02);
            previous = motion.phase();
        }
    }

    private static MachineMotion runningMotion(long seed) {
        var motion = new MachineMotion(seed);
        motion.advance(0, true);
        for (int tick = 1; tick <= 40; tick++) motion.advance(tick, true);
        return motion;
    }

    private static double forwardDistance(float from, float to) {
        return (to - (double) from + 1) % 1;
    }

    private static void assertBounded(MachineMotion motion) {
        assertTrue(Float.isFinite(motion.phase()) && motion.phase() >= 0 && motion.phase() <= 1);
        assertTrue(Float.isFinite(motion.drive()) && motion.drive() >= 0 && motion.drive() <= 1);
    }
}
