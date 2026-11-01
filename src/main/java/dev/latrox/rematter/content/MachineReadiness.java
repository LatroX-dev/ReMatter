package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.Map;

/** One ordered explanation for resource checks; no check consumes or reserves resources. */
public final class MachineReadiness {
    private MachineReadiness() {}

    public static String check(boolean needsStorage, MatterInventory store,
                               Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                               boolean outputReady, long energy, long requiredEnergy) {
        if (needsStorage) {
            if (store == null) return "no_storage";
            for (var input : inputs.entrySet()) {
                if (store.available(input.getKey()) < input.getValue()) return "missing_matter";
            }
            if (!store.hasTypeSpace(inputs, outputs)) return "storage_types_full";
            if (!store.canApply(inputs, outputs)) return "storage_full";
        }
        if (!outputReady) return "output_blocked";
        if (energy < requiredEnergy) return "insufficient_energy";
        return "ready";
    }
}
