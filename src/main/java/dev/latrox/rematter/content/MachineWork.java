package dev.latrox.rematter.content;

/** Active batch state. Progress and paid FE persist across stationary chunk reloads so long batches never lose paid energy. */
final class MachineWork {
    int progress;
    int duration;
    long energyPaid;
    long energyRate;
    int restoredLanes;
    String job = "";
    String status = "idle";
    boolean processedThisTick;
    int preparationStage = -1;
    boolean preparationMixing;
    BatchReservations.Ticket ticket;

    void reset() { progress = 0; duration = 0; energyPaid = 0; energyRate = 0; restoredLanes = 0; job = ""; }
}
