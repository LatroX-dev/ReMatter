package dev.latrox.rematter.chemistry;

import java.util.List;
import java.util.Map;

/** A live view of physical resource owners, never a second persisted pool. */
public interface MatterStore {
    long capacity();
    long total();
    long freeSpace();
    int typeCount();
    long amount(SubstanceKey key);
    long available(SubstanceKey key);
    long reserved(SubstanceKey key);
    Map<SubstanceKey, Long> contents();
    Map<SubstanceKey, Long> availableContents();
    boolean hasTypeSpace(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs);
    boolean canApply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs);
    boolean apply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs);
    /** Immutable, deterministically ordered inventory identities used to bind an in-progress batch. */
    List<MatterInventory> members();
}
