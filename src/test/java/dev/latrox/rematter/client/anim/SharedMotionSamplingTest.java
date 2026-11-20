package dev.latrox.rematter.client.anim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedMotionSamplingTest {
    /**
     * The sound manager samples a machine's envelope in client ticks, the renderer in every frame. A tick runs before
     * the frame's real-time delta is measured, so the tick must use the committed frame time (AnimClock.lastFrame):
     * previewing with the delta that is known then, the previous frame's, runs ahead whenever frames get shorter.
     */
    @Test void tickSamplesAtTheCommittedFrameTimeLeaveTheDrawnMotionUntouched() {
        var clock = new FrameClock();
        var drawn = new WorkEnvelope(); var shared = new WorkEnvelope(); var previewed = new WorkEnvelope();
        var random = new java.util.Random(7);
        double previousDelta = 0;
        for (int frame = 0; frame < 2000; frame++) {
            double delta = .1 + random.nextDouble() * .8;
            boolean active = frame < 1500;
            // The machine is drawn once before it is heard, so all three envelopes start with the same sample.
            for (int tick = frame == 0 ? 0 : frame % 3 == 0 ? 2 : frame % 3 == 1 ? 1 : 0; tick > 0; tick--) {
                shared.sample(clock.sample(0, true), active, true, 0, 100, 1);
                previewed.sample(clock.sample(previousDelta, false), active, true, 0, 100, 1);
            }
            double time = clock.sample(delta, false);
            drawn.sample(time, active, true, 0, 100, 1);
            shared.sample(time, active, true, 0, 100, 1);
            previewed.sample(time, active, true, 0, 100, 1);
            assertEquals(drawn.turns(), shared.turns(), 1e-9, "frame " + frame);
            assertEquals(drawn.drive(), shared.drive(), "frame " + frame);
            assertEquals(drawn.engage(), shared.engage(), "frame " + frame);
            clock.commit(delta, false); clock.endFrame();
            previousDelta = delta;
        }
        assertTrue(drawn.turns() > 500);
        assertTrue(previewed.turns() > drawn.turns() + 10, "a previewed tick sample is expected to run ahead: " + previewed.turns() + " vs " + drawn.turns());
    }
    @Test void anUnseenMachineKeepsItsPhaseRunningFromTickSamplesAlone() {
        var clock = new FrameClock();
        var heard = new WorkEnvelope();
        for (int frame = 0; frame < 600; frame++) {
            // Sixty frames a second, one client tick every third frame, nothing drawn.
            if (frame % 3 == 0) heard.sample(clock.sample(0, true), true, true, 0, 100, 1);
            clock.commit(1.0 / 3, false); clock.endFrame();
        }
        assertEquals(1, heard.drive());
        // 199 ticks of clock have passed at the last sample; the 12-tick start costs six ticks of motion.
        assertEquals(199 - 6, heard.turns(), .5);
    }
}
