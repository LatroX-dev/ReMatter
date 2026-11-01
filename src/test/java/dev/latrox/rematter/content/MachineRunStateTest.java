package dev.latrox.rematter.content;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineRunStateTest {
    @Test void newManualMachineCannotRunWithoutARequest() {
        var state = MachineRunState.manual();
        assertFalse(state.running());
        assertEquals(state, state.toggleRunning());
        assertFalse(state.repeat());
    }
    @Test void twoRequestsStopOnlyAfterExactlyTwoSuccessfulCompletions() {
        var state = MachineRunState.manual().requestBatch().requestBatch();
        assertEquals(2, state.queuedBatches());
        state = state.completed();
        assertTrue(state.running());
        assertEquals(1, state.queuedBatches());
        state = state.completed();
        assertEquals(MachineRunState.manual(), state);
        var finished = state;
        assertThrows(IllegalStateException.class, finished::completed);
    }
    @Test void pauseAndDiskRestoreRetainFiniteWorkWithoutConsumingIt() {
        var state = MachineRunState.manual().requestBatch().requestBatch().pause();
        var restored = MachineRunState.restore(1, state.running(), state.repeat(), state.queuedBatches());
        assertEquals(state, restored);
        assertFalse(restored.running());
        assertEquals(2, restored.queuedBatches());
        assertEquals(1, restored.toggleRunning().completed().queuedBatches());
    }
    @Test void legacyRunningAndPausedMachinesRetainContinuousMode() {
        var running = MachineRunState.restore(0, true, false, 64);
        assertTrue(running.repeat());
        for (int i = 0; i < 100; i++) running = running.completed();
        assertTrue(running.running());
        assertEquals(0, running.queuedBatches());
        assertEquals(new MachineRunState(true, false, 0), MachineRunState.restore(0, false, false, 0));
    }
    @Test void repeatExplicitlyReplacesQueueAndMakeOneBoundsRepeat() {
        var repeated = MachineRunState.manual().requestBatch().requestBatch().toggleRepeat();
        assertEquals(MachineRunState.automatic(), repeated);
        assertEquals(MachineRunState.manual(), repeated.toggleRepeat());
        assertEquals(new MachineRunState(false, true, 1), repeated.requestBatch());
        assertEquals(MachineRunState.manual(), repeated.requestBatch().completed());
    }
    @Test void queueIsBoundedEvenAfterRepeatedRequests() {
        var state = MachineRunState.manual();
        for (int i = 0; i < 1000; i++) state = state.requestBatch();
        assertEquals(64, state.queuedBatches());
        for (int i = 0; i < 64; i++) state = state.completed();
        assertEquals(MachineRunState.manual(), state);
    }
    @Test void invalidOrFutureControlMetadataFailsClosed() {
        assertEquals(MachineRunState.manual(), MachineRunState.restore(2, true, true, 0));
        assertEquals(MachineRunState.manual(), MachineRunState.restore(1, true, false, -1));
        assertEquals(MachineRunState.manual(), MachineRunState.restore(1, true, false, 65));
        assertEquals(MachineRunState.manual(), MachineRunState.restore(1, true, true, 1));
        assertEquals(MachineRunState.manual(), MachineRunState.restore(1, true, false, 0));
    }
}
