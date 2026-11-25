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
    Map<SubstanceKey, Long> contents();
    boolean hasTypeSpace(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs);
    boolean canApply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs);
    boolean apply(Map<SubstanceKey, Long> inputs, Map<SubstanceKey, Long> outputs);
    /** Changes whenever a committed change may alter any answer above; equal values mean nothing changed. */
    long revision();
    /** Immutable, deterministically ordered inventory identities used to bind an in-progress batch. */
    List<MatterInventory> members();
    /** True when excess output of {@code key} beyond all compatible finite cells is discarded cleanly. */
    default boolean voidsOverflow(SubstanceKey key) { return false; }
    /** Structural identity of overflow voiding policy used to validate bound unpaid batches. */
    default Object voidSignature() { return VoidPolicy.NONE; }
    /** True when physical member {@code index} may receive deposits of {@code key} through network relay filters. */
    default boolean canDepositMember(int index, SubstanceKey key) { return true; }
    /** True when at least one member accepts deposits of {@code key} through both cell and relay filters. */
    default boolean acceptsDeposit(SubstanceKey key) {
        var list = members();
        for (int i = 0; i < list.size(); i++) {
            if (canDepositMember(i, key) && list.get(i).accepts(key)) return true;
        }
        return false;
    }
}
