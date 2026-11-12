package dev.latrox.rematter.celltransfer;

import dev.latrox.rematter.chemistry.*;
import dev.latrox.rematter.content.CellPartitionData;
import java.util.*;
import java.util.function.Predicate;

/** A station selection limits this transfer only; each ordinary Cell keeps its own independent insertion policy. */
public record CellTransferSelection(boolean all,boolean primalSelected,Set<SubstanceKey> keys) implements Predicate<SubstanceKey> {
    public static final CellTransferSelection ALL=new CellTransferSelection(true,false,Set.of());
    public CellTransferSelection {
        keys=Set.copyOf(keys);
        new CellPartitionData.Spec(true,keys);
    }
    @Override public boolean test(SubstanceKey key){return !ReservedSubstances.reserved(key)&&(all||keys.contains(key));}
    public boolean includesPrimal(){return all||primalSelected;}
    public CellTransferSelection toggle(SubstanceKey key){var next=all?new TreeSet<SubstanceKey>():new TreeSet<>(keys);if(!next.remove(key))next.add(key);return new CellTransferSelection(false,primalSelected,next);}
}
