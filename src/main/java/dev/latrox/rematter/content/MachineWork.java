package dev.latrox.rematter.content;

/** Transient state of the current batch. Nothing here is persisted: no input or FE is consumed before the commit. */
final class MachineWork {
    int progress;
    int duration;
    String job = "";
    String status = "idle";
    boolean processedThisTick;
    int preparationStage = -1;
    boolean preparationMixing;

    void reset() { progress = 0; duration = 0; job = ""; }
}
