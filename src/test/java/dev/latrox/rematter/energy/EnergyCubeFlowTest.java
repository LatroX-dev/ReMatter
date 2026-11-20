package dev.latrox.rematter.energy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnergyCubeFlowTest {
    @Test void oneTransientTransferDoesNotStartTheBand() {
        var flow = new EnergyCubeFlow();
        assertEquals(EnergyCubeBlock.Flow.NONE, flow.sample(0, 0));
        assertEquals(EnergyCubeBlock.Flow.NONE, flow.sample(10, 100));
        assertEquals(EnergyCubeBlock.Flow.NONE, flow.sample(20, 100));
        assertEquals(EnergyCubeBlock.Flow.NONE, flow.sample(30, 100));
    }

    @Test void twoEqualWindowsChooseNetDirectionAndSteadyStorageStopsIt() {
        var flow = new EnergyCubeFlow();
        flow.sample(0, 100);
        assertEquals(EnergyCubeBlock.Flow.NONE, flow.sample(10, 200));
        assertEquals(EnergyCubeBlock.Flow.IN, flow.sample(20, 300));
        assertEquals(EnergyCubeBlock.Flow.IN, flow.sample(30, 200));
        assertEquals(EnergyCubeBlock.Flow.OUT, flow.sample(40, 100));
        assertEquals(EnergyCubeBlock.Flow.OUT, flow.sample(50, 100));
        assertEquals(EnergyCubeBlock.Flow.NONE, flow.sample(60, 100));
    }

    @Test void frequentQueriesAndJitterCannotChangeFlowTwiceWithinASecond() {
        var flow = new EnergyCubeFlow();
        EnergyCubeBlock.Flow previous = EnergyCubeBlock.Flow.NONE;
        int lastChange = -20;
        for (int tick = 0; tick <= 150; tick++) {
            int energy = tick < 35 ? tick * 100 : tick < 75 ? 7000 - tick * 100 : 0;
            var shown = flow.sample(tick, energy);
            assertEquals(shown, flow.sample(tick, energy), "Repeated query changed direction");
            if (shown != previous) {
                assertTrue(tick - lastChange >= 20, "Two flow changes inside one second");
                lastChange = tick;
                previous = shown;
            }
        }
        assertEquals(EnergyCubeBlock.Flow.NONE, previous);
    }
}
