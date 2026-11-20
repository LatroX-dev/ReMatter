package dev.latrox.rematter.content;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisualActivityTest {
    @Test void aNewOrClockRewoundMachineNeverInventsWork() {
        var activity = new VisualActivity();
        assertFalse(activity.showing(0, 8));
        activity.worked(100);
        assertFalse(activity.showing(99, 8));
    }

    @Test void aVisualHoldExpiresWithoutChangingTheLastPaidWorkTick() {
        for (int hold : new int[]{8, 20}) {
            var activity = new VisualActivity();
            activity.worked(13);
            for (int now = 13; now <= 13 + hold; now++) assertTrue(activity.showing(now, hold));
            for (int now = 14 + hold; now < 100; now++) assertFalse(activity.showing(now, hold));
        }
    }

    @Test void theFastestAcceptedHandCranksDoNotBlinkBetweenWorkTicks() {
        var activity = new VisualActivity();
        for (int tick = 0; tick < 100; tick++) {
            // A crank reports work for three ticks, accepted cranks are five ticks apart.
            if (tick % 5 < 3) activity.worked(tick);
            assertTrue(activity.showing(tick, 8), "Gap at tick " + tick);
        }
        assertFalse(activity.showing(106, 8));
    }
}
