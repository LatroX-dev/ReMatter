package dev.latrox.rematter.content;

/** Stable inventory positions: existing input, output and upgrade slots never move. */
public final class MachineCellAccess {
    private MachineCellAccess() {}
    public static boolean hasLocalSlot(MachineKind kind) {
        return kind == MachineKind.DECOMPOSER || kind == MachineKind.SYNTHESIZER;
    }
    public static int slot(MachineKind kind) {
        return kind == MachineKind.DRIVE ? 0 : hasLocalSlot(kind) ? 3 : -1;
    }
    public static int inventorySize(MachineKind kind) { return hasLocalSlot(kind) || dev.latrox.rematter.content.preparation.PreparationTier.isPreparation(kind) ? 4 : 3; }
    public static boolean usesOwnStorage(MachineKind kind, boolean cellPresent) {
        return kind.isStorage() || hasLocalSlot(kind) && cellPresent;
    }
}
