package dev.latrox.rematter.content;

import dev.latrox.rematter.ReMatter;
import dev.latrox.rematter.chemistry.ChemistryRegistry;
import dev.latrox.rematter.content.preparation.PreparationTier;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Recipe selection and run control. A selection is kept by stable ID; its index follows the current catalogue. */
final class MachineControl {
    private final MachineBlockEntity machine;
    private MachineRunState runState;
    private int selection = -1;
    private String selectedId = "";

    MachineControl(MachineBlockEntity machine) {
        this.machine = machine;
        runState = batchControl(machine.kind()) || PreparationTier.manual(machine.kind()) ? MachineRunState.manual() : MachineRunState.automatic();
    }

    static boolean batchControl(MachineKind kind) {
        return kind.selectable();
    }

    MachineRunState runState() { return runState; }
    void runState(MachineRunState value) { runState = value; }
    String selectedId() { return selectedId; }
    boolean selectionUnavailable() { return selection < 0 && !selectedId.isEmpty(); }
    boolean hasSelection() { return selection >= 0; }

    List<MachineChoices.Choice> choices() { return MachineChoices.forKind(machine.kind(), ReMatter.chemistry()); }

    MachineChoices.@Nullable Choice selectedChoice() {
        var options = choices();
        return options.isEmpty() || selection < 0 ? null : options.get(Math.floorMod(selection, options.size()));
    }
    int selectedIndex() {
        var options = choices();
        return options.isEmpty() || selection < 0 ? -1 : Math.floorMod(selection, options.size());
    }

    void toggleRunning() {
        MachineKind kind = machine.kind();
        if (PreparationTier.manual(kind) || !kind.processes() && !kind.generator() || selectionUnavailable()) return;
        runState = runState.toggleRunning();
        machine.setChanged();
    }
    boolean requestBatch() {
        if (!batchControl(machine.kind()) || selection < 0 || choices().isEmpty()) return false;
        runState = runState.requestBatch();
        machine.setChanged();
        return true;
    }
    boolean toggleRepeat() {
        if (!batchControl(machine.kind()) || selection < 0 || choices().isEmpty()) return false;
        runState = runState.toggleRepeat();
        machine.setChanged();
        return true;
    }
    void select(int offset) {
        var options = choices();
        if (options.isEmpty()) return;
        int index = selection < 0 ? (offset < 0 ? options.size() - 1 : 0)
                : (int) Math.floorMod((long) selection + offset, options.size());
        select(options.get(index).id());
    }
    boolean select(String exactId) {
        var options = choices();
        for (int i = 0; i < options.size(); i++) {
            if (!options.get(i).id().equals(exactId)) continue;
            selection = i;
            if (!selectedId.equals(exactId)) {
                selectedId = exactId;
                runState = MachineRunState.manual();
                machine.resetProgress();
                machine.setChanged();
            }
            return true;
        }
        return false;
    }

    /** Re-resolves the saved selection after a catalogue reload. Returns false when it no longer exists. */
    boolean restore(ChemistryRegistry registry) {
        String previous = selectedId;
        var restored = RecipeSelection.restore(selectedId, choices().stream().map(MachineChoices.Choice::id).toList(),
                !batchControl(machine.kind()) || runState.repeat());
        selection = restored.index();
        selectedId = restored.id();
        if (!selectedId.equals(previous)) machine.setChanged();
        if (!restored.unavailable()) return true;
        runState = runState.pause();
        machine.setChanged();
        return false;
    }

    void load(MachineRunState state, String savedId) {
        runState = state;
        selectedId = savedId;
    }
}
