package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;

/** Explicit, complete migration only. Inserting a disk never changes either inventory. */
final class LegacyStorageMigration {
    private LegacyStorageMigration() {}
    static String readiness(MatterInventory legacy, MatterInventory cell) {
        if (legacy == cell || !legacy.snapshot().reservations().isEmpty() || !cell.snapshot().reservations().isEmpty())
            return "migration_busy";
        if (cell.total() != 0) return "migration_disk_not_empty";
        if (!cell.hasTypeSpace(java.util.Map.of(), legacy.contents())) return "migration_disk_types";
        return cell.canApply(java.util.Map.of(), legacy.contents()) ? "ready" : "migration_disk_capacity";
    }
    static boolean canMigrate(MatterInventory legacy, MatterInventory cell) {
        return readiness(legacy, cell).equals("ready");
    }
    static boolean migrate(MatterInventory legacy, MatterInventory cell) {
        return canMigrate(legacy, cell) && legacy.transferAllTo(cell);
    }
}
