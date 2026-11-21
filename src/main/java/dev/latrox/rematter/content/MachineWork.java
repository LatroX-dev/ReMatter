package dev.latrox.rematter.content;

/** Transient state of the current batch. Nothing here is persisted: inputs commit at completion and unpaid/in-flight FE is restored on abort or save. */
final class MachineWork {
    int progress;
    int duration;
    long energyPaid;
    long energyRate;
    String job = "";
    String status = "idle";
    boolean processedThisTick;
    int preparationStage = -1;
    boolean preparationMixing;
    BatchReservations.Ticket ticket;

    void reset() { progress = 0; duration = 0; energyPaid = 0; energyRate = 0; job = ""; }
}
