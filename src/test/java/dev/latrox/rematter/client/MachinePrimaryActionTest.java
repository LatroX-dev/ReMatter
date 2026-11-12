package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineCellAccess;
import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineRunState;
import dev.latrox.rematter.content.MachineSnapshot;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.client.MachinePrimaryAction.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class MachinePrimaryActionTest {
    private static final String RECIPE = "rematter:test_recipe";
    private static final MachineSnapshot.Preview PREVIEW = new MachineSnapshot.Preview(RECIPE, List.of(), List.of(),
            true, 1000, 20, "matter", "", 0, List.of());
    private static MachineSnapshot view(MachineKind kind, MachineRunState state, int choices, int selected,
                                        String selectedId, MachineSnapshot.Preview preview, String status) {
        return new MachineSnapshot(7, kind.ordinal(), status, selected, selectedId, choices, 1, 0, 0, false, state.running(),
                0, "", state.repeat(), state.queuedBatches(), preview, "none", MachineSnapshot.StructureView.EMPTY,
                MachineSnapshot.NetworkView.EMPTY, false, "", MachineSnapshot.OrdersView.EMPTY, Collections.nCopies(MachineCellAccess.bayCount(kind), MachineSnapshot.CellView.EMPTY));
    }
    private static MachineSnapshot selected(MachineKind kind, MachineRunState state) {
        return view(kind, state, 1, 0, RECIPE, PREVIEW, state.running() ? "working" : "paused");
    }
    private static MachineRunState send(MachineRunState state, MachinePrimaryAction.Decision decision) {
        return switch (decision.menuAction()) {
            case 2 -> state.toggleRunning();
            case 4 -> state.requestBatch();
            default -> throw new AssertionError("Not an existing order action");
        };
    }
    @Test void freshAndRemovedSelectionsOpenThePickerWithoutSendingAWorkOrder() {
        for (var kind : List.of(MachineKind.SYNTHESIZER, MachineKind.REACTOR, MachineKind.INDUSTRIAL_REACTOR)) {
            for (var state : List.of(MachineRunState.manual(), MachineRunState.automatic().pause())) {
                var snapshot = view(kind, state, 12, -1, "", MachineSnapshot.Preview.EMPTY, "choose_process");
                var decision = MachinePrimaryAction.forSnapshot(snapshot);
                assertEquals(CHOOSE_RECIPE, decision.action());assertTrue(decision.opensRecipeSelection());
                assertTrue(decision.enabled());assertEquals(-1, decision.menuAction());
                assertEquals("choose_recipe", decision.labelKey());
            }
            var removed = view(kind, MachineRunState.manual().requestBatch().pause(), 1, -1, "rematter:removed",
                    MachineSnapshot.Preview.EMPTY, "selection_unavailable");
            assertEquals(CHOOSE_RECIPE, MachinePrimaryAction.forSnapshot(removed).action());
        }
        var emptyCatalogue = view(MachineKind.SYNTHESIZER, MachineRunState.manual(), 0, -1, "", MachineSnapshot.Preview.EMPTY, "choose_process");
        assertFalse(MachinePrimaryAction.forSnapshot(emptyCatalogue).enabled());
    }
    @Test void staleOrMissingPreviewCannotAccidentallySubmitTheSavedSelection() {
        for (var snapshot : List.of(
                view(MachineKind.SYNTHESIZER, MachineRunState.manual(), 1, 0, RECIPE, MachineSnapshot.Preview.EMPTY, "paused"),
                view(MachineKind.SYNTHESIZER, MachineRunState.manual(), 1, 0, "rematter:other", PREVIEW, "paused"),
                view(MachineKind.SYNTHESIZER, MachineRunState.manual(), 1, 0, RECIPE, PREVIEW, "selection_unavailable"))) {
            assertEquals(CHOOSE_RECIPE, MachinePrimaryAction.forSnapshot(snapshot).action());
            assertEquals(-1, MachinePrimaryAction.forSnapshot(snapshot).menuAction());
        }
    }
    @Test void oneMainButtonMakesExactlyOneBatchThenPausesAndResumesTheSameFiniteOrder() {
        var state = MachineRunState.manual();
        var make = MachinePrimaryAction.forSnapshot(selected(MachineKind.SYNTHESIZER, state));
        assertEquals(MAKE_ONCE, make.action());assertEquals("start", make.labelKey());assertEquals("start_once_hint",make.hintKey());
        state = send(state, make);assertEquals(1, state.queuedBatches());assertFalse(state.repeat());
        var pause = MachinePrimaryAction.forSnapshot(selected(MachineKind.SYNTHESIZER, state));
        assertEquals(PAUSE, pause.action());state = send(state, pause);
        var resume = MachinePrimaryAction.forSnapshot(selected(MachineKind.SYNTHESIZER, state));
        assertEquals(RESUME, resume.action());state = send(state, resume);
        assertEquals(1, state.queuedBatches(), "Resume must not add another batch");
        state = state.completed();assertEquals(MachineRunState.manual(), state);
        assertEquals(MAKE_ONCE, MachinePrimaryAction.forSnapshot(selected(MachineKind.SYNTHESIZER, state)).action());
    }
    @Test void pausedFiniteQueueAndPausedRepeatArePreservedInsteadOfReplacedByMakeOnce() {
        var finite = MachineRunState.manual().requestBatch().requestBatch().pause();
        var repeat = MachineRunState.automatic().pause();
        for (var state : List.of(finite, repeat)) {
            for (var kind : List.of(MachineKind.SYNTHESIZER, MachineKind.REACTOR, MachineKind.INDUSTRIAL_REACTOR)) {
                var resume = MachinePrimaryAction.forSnapshot(selected(kind, state));
                assertEquals(RESUME, resume.action());assertEquals(2, resume.menuAction());
                var running = send(state, resume);
                assertEquals(state.repeat(), running.repeat());assertEquals(state.queuedBatches(), running.queuedBatches());
                var pause = MachinePrimaryAction.forSnapshot(selected(kind, running));
                assertEquals(PAUSE, pause.action());assertEquals(state, send(running, pause));
            }
        }
    }
    @Test void fullQueueAndBlockedResourcesStillAllowPausingTheExistingOrder() {
        for (var status : List.of("missing_matter", "energy", "output", "structure_module_incomplete")) {
            var state = new MachineRunState(false, true, MachineRunState.MAX_QUEUED_BATCHES);
            var snapshot = view(MachineKind.REACTOR, state, 1, 0, RECIPE, PREVIEW, status);
            var decision = MachinePrimaryAction.forSnapshot(snapshot);
            assertEquals(PAUSE, decision.action());assertTrue(decision.enabled());
            assertEquals(state.pause(), send(state, decision));
        }
        var process = MachinePrimaryAction.forSnapshot(selected(MachineKind.REACTOR, MachineRunState.manual()));
        assertEquals("start", process.labelKey());assertEquals(4, process.menuAction());
    }
    @Test void inputMachinesKeepStartPauseAndManualMachineOnlyCranks() {
        for (var kind : List.of(MachineKind.ANALYZER, MachineKind.DECOMPOSER, MachineKind.PREPARATOR, MachineKind.INDUSTRIAL_PREPARATOR)) {
            var active = view(kind, MachineRunState.automatic(), 0, -1, "", MachineSnapshot.Preview.EMPTY, "input");
            var inactive = view(kind, MachineRunState.automatic().pause(), 0, -1, "", MachineSnapshot.Preview.EMPTY, "paused");
            assertEquals(PAUSE, MachinePrimaryAction.forSnapshot(active).action());
            var start = MachinePrimaryAction.forSnapshot(inactive);assertEquals(START, start.action());assertEquals(2, start.menuAction());
        }
        var ready = view(MachineKind.HAND_PREPARATOR, MachineRunState.manual(), 0, -1, "", PREVIEW, "crank");
        var crank = MachinePrimaryAction.forSnapshot(ready);assertEquals(CRANK, crank.action());assertEquals(6, crank.menuAction());assertTrue(crank.enabled());
        var empty = view(MachineKind.HAND_PREPARATOR, MachineRunState.manual(), 0, -1, "", MachineSnapshot.Preview.EMPTY, "input");
        assertEquals(CRANK, MachinePrimaryAction.forSnapshot(empty).action());assertFalse(MachinePrimaryAction.forSnapshot(empty).enabled());
    }
    @Test void passiveAndNotYetReceivedSnapshotsDoNotExposeAWorkAction() {
        assertEquals(NONE, MachinePrimaryAction.forSnapshot(MachineSnapshot.EMPTY).action());
        for (var kind : List.of(MachineKind.DRIVE, MachineKind.TERMINAL)) {
            var decision = MachinePrimaryAction.forSnapshot(view(kind, MachineRunState.automatic(), 0, -1, "", MachineSnapshot.Preview.EMPTY, "ready"));
            assertEquals(NONE, decision.action());assertFalse(decision.visible());assertFalse(decision.enabled());assertEquals(-1, decision.menuAction());
        }
    }
}
