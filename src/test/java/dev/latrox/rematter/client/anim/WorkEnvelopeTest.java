package dev.latrox.rematter.client.anim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkEnvelopeTest {
    @Test void packetJitterNeverDrivesMechanismPhase() {
        var regular = new WorkEnvelope(); var delayed = new WorkEnvelope();
        double before = 0;
        for (int frame = 0; frame < 1800; frame++) {
            double time = frame / 3.0;
            // Both represent a base-rate job, but one loses updates and wraps much later.
            regular.sample(time, true, true, ((int)time / 4 * 4) % 100, 100, 1);
            delayed.sample(time, true, true, ((int)Math.max(0,time-2) / 12 * 12) % 100, 100, 1);
            assertEquals(regular.turns(), delayed.turns(), .000001);
            assertTrue(regular.turns() - before <= 1.4 / 3 + .0001);
            before = regular.turns();
        }
    }
    @Test void gapsEaseAndIdleStopsExactly() {
        var work = new WorkEnvelope();
        for (int t = 0; t < 50; t++) work.sample(t, true, true, t, 100, 1);
        double start = work.turns();
        work.sample(51, false, true, 50, 100, 1);
        assertEquals(1, work.drive());
        for (int t = 52; t < 120; t++) work.sample(t, false, false, 0, 0, 1);
        assertEquals(0, work.drive()); assertEquals(0, work.engage());
        double stopped = work.turns(); assertTrue(stopped > start);
        work.sample(121, false, false, 0, 0, 1); assertEquals(stopped, work.turns());
    }
    @Test void followerNeverOvertakesAcknowledgedRisingProgress() {
        var follower = new Follower();
        float previous = 0;
        for (int frame = 0; frame < 1000; frame++) {
            double target = Math.min(1, (frame / 17) / 40.0);
            float next = follower.advance(target, 1.0 / 3);
            assertTrue(next <= target + .000001); assertTrue(next >= previous);
            assertTrue(next - previous < .02); previous = next;
        }
    }
}
