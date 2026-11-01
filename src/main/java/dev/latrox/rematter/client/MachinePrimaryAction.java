package dev.latrox.rematter.client;

import dev.latrox.rematter.content.MachineKind;
import dev.latrox.rematter.content.MachineSnapshot;
import java.util.Objects;

/** Presentation only: the existing menu actions remain server-authoritative. */
public final class MachinePrimaryAction {
    public enum Action { NONE, CHOOSE_RECIPE, MAKE_ONCE, START, PAUSE, RESUME, CRANK }

    /** Translation keys are suffixes of screen.rematter; choosing a recipe opens the existing picker. */
    public record Decision(Action action, String labelKey, String hintKey, boolean enabled) {
        public int menuAction() {
            return switch (action) {
                case START, PAUSE, RESUME -> 2;
                case MAKE_ONCE -> 4;
                case CRANK -> 6;
                case NONE, CHOOSE_RECIPE -> -1;
            };
        }
        public boolean opensRecipeSelection() { return action == Action.CHOOSE_RECIPE; }
        public boolean visible() { return action != Action.NONE; }
    }
    private static final Decision NONE = new Decision(Action.NONE, "", "", false);
    private MachinePrimaryAction() {}

    public static Decision forSnapshot(MachineSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        MachineKind kind = MachineKind.values()[snapshot.kind()];
        if (snapshot.containerId() < 0 || !kind.processes()) return NONE;
        if (kind == MachineKind.HAND_PREPARATOR) {
            return new Decision(Action.CRANK, "crank", "crank_hint", !snapshot.preview().id().isEmpty());
        }
        if (selecting(kind)) {
            boolean validSelection = snapshot.selected() >= 0 && !snapshot.selectedId().isEmpty()
                    && snapshot.selectedId().equals(snapshot.preview().id())
                    && !snapshot.status().equals("selection_unavailable");
            if (!validSelection) {
                return new Decision(Action.CHOOSE_RECIPE, "choose_recipe", "choose_hint", snapshot.choiceCount() > 0);
            }
            // requestBatch() would turn off repeat or add to a paused finite queue. Resume preserves that order.
            if (snapshot.repeat() || snapshot.queuedBatches() > 0) {
                return snapshot.running() ? toggle(Action.PAUSE, "pause") : toggle(Action.RESUME, "resume");
            }
            return new Decision(Action.MAKE_ONCE, "start", "start_once_hint", true);
        }
        return snapshot.running() ? toggle(Action.PAUSE, "pause") : toggle(Action.START, "start");
    }

    private static Decision toggle(Action action, String label) { return new Decision(action, label, "pause_hint", true); }
    private static boolean selecting(MachineKind kind) {
        return kind == MachineKind.SYNTHESIZER || kind == MachineKind.ELECTROLYZER
                || kind == MachineKind.REACTOR || kind == MachineKind.INDUSTRIAL_REACTOR;
    }
}
