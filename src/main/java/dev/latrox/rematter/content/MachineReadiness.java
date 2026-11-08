package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterStore;
import dev.latrox.rematter.chemistry.SubstanceKey;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** One ordered explanation for resource checks; no check consumes or reserves resources. */
public final class MachineReadiness {
    private @Nullable MatterStore checkedStore;
    private long checkedRevision;
    private Map<SubstanceKey, Long> checkedInputs = Map.of();
    private Map<SubstanceKey, Long> checkedOutputs = Map.of();
    private String storageVerdict = "";

    public static String check(boolean needsStorage, @Nullable MatterStore store,
                               Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                               boolean outputReady, long energy, long requiredEnergy) {
        return finish(needsStorage ? storage(store, inputs, outputs) : "ready", outputReady, energy, requiredEnergy);
    }

    /**
     * The same ordered check for one machine. The storage verdict is reused while the store, its revision and
     * the batch stay unchanged, so a running or blocked machine does not plan against unchanged stock every tick.
     */
    public String checkRepeated(boolean needsStorage, @Nullable MatterStore store,
                                Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs,
                                boolean outputReady, long energy, long requiredEnergy) {
        if (!needsStorage) return finish("ready", outputReady, energy, requiredEnergy);
        if (store == null) return finish(storage(null, inputs, outputs), outputReady, energy, requiredEnergy);
        long revision = store.revision();
        if (store != checkedStore || revision != checkedRevision || !inputs.equals(checkedInputs) || !outputs.equals(checkedOutputs)) {
            storageVerdict = storage(store, inputs, outputs);
            checkedStore = store;
            checkedRevision = revision;
            checkedInputs = Map.copyOf(inputs);
            checkedOutputs = Map.copyOf(outputs);
        }
        return finish(storageVerdict, outputReady, energy, requiredEnergy);
    }

    private static String storage(@Nullable MatterStore store, Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs) {
        if (store == null) return "no_storage";
        for (var input : inputs.entrySet()) {
            if (store.available(input.getKey()) < input.getValue()) return "missing_matter";
        }
        if (!store.canApply(inputs, outputs)) return store.hasTypeSpace(inputs, outputs) ? "storage_full" : "storage_types_full";
        return "ready";
    }

    private static String finish(String storage, boolean outputReady, long energy, long requiredEnergy) {
        if (!storage.equals("ready")) return storage;
        if (!outputReady) return "output_blocked";
        if (energy < requiredEnergy) return "insufficient_energy";
        return "ready";
    }
}
