package dev.latrox.rematter.content;

/** Finite requests are consumed only after a complete authoritative resource commit. */
public record MachineRunState(boolean repeat, boolean running, int queuedBatches) {
    public static final int MAX_QUEUED_BATCHES = 64;

    public MachineRunState {
        if (queuedBatches < 0 || queuedBatches > MAX_QUEUED_BATCHES || (repeat && queuedBatches != 0)
                || (running && !repeat && queuedBatches == 0)) {
            throw new IllegalArgumentException("Invalid machine run state");
        }
    }

    public static MachineRunState manual() { return new MachineRunState(false, false, 0); }
    public static MachineRunState automatic() { return new MachineRunState(true, true, 0); }

    /** An inconsistent stored combination pauses the machine instead of guessing a mode. */
    public static MachineRunState restore(boolean running, boolean repeat, int queuedBatches) {
        try { return new MachineRunState(repeat, running, queuedBatches); }
        catch (IllegalArgumentException ignored) { return manual(); }
    }

    public MachineRunState requestBatch() {
        return new MachineRunState(false, true, repeat ? 1 : Math.min(MAX_QUEUED_BATCHES, queuedBatches + 1));
    }
    public MachineRunState toggleRepeat() { return repeat ? manual() : automatic(); }
    public MachineRunState pause() { return new MachineRunState(repeat, false, queuedBatches); }
    public MachineRunState toggleRunning() {
        if (running) return pause();
        return repeat || queuedBatches > 0 ? new MachineRunState(repeat, true, queuedBatches) : this;
    }
    public MachineRunState completed() {
        if (!running) throw new IllegalStateException("Cannot complete a paused machine");
        if (repeat) return this;
        int remaining = queuedBatches - 1;
        return new MachineRunState(false, remaining > 0, remaining);
    }
}
