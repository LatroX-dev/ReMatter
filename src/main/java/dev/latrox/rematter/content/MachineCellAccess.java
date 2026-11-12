package dev.latrox.rematter.content;

/** Stable inventory positions for the six Matter Drive bays. */
public final class MachineCellAccess {
    public static final int DRIVE_BAYS = 6;
    public static final int DRIVE_ROWS = 3;
    private MachineCellAccess() {}
    public static int slot(MachineKind kind) {
        return kind.isStorage() ? 0 : -1;
    }
    public static int bayCount(MachineKind kind) { return kind.isStorage() ? DRIVE_BAYS : 0; }
    public static int slotForBay(MachineKind kind, int bay) {
        if (bay < 0 || bay >= bayCount(kind)) throw new IndexOutOfBoundsException("Invalid cell bay");
        return bay == 0 ? 0 : bay + 4;
    }
    /** Bays are numbered from the top of the viewer's left column to the bottom of the right. */
    public static int driveColumn(int bay) { return bay / DRIVE_ROWS; }
    public static int driveRow(int bay) { return bay % DRIVE_ROWS; }
    /** Three public bits per bay: absent, three Matter tiers, unreadable, then three Primal tiers. */
    public static int decodeTier(int packed, int bay) {
        return bay >= 0 && bay < DRIVE_BAYS ? packed >>> (bay * 3) & 7 : 0;
    }
    public static int bayForSlot(MachineKind kind, int slot) {
        for (int bay = 0; bay < bayCount(kind); bay++) if (slotForBay(kind, bay) == slot) return bay;
        return -1;
    }
    public static boolean isCellSlot(MachineKind kind, int slot) { return bayForSlot(kind, slot) >= 0; }
    public static final int EFFICIENCY_SLOT=10, PARALLEL_SLOT=11, PRIMAL_SLOT=12, DISCOVERY_SLOT=13;
    public static final int INVENTORY_SIZE=14;
    public static int inventorySize(MachineKind kind) { return INVENTORY_SIZE; }
}
