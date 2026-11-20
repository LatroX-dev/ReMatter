package dev.latrox.rematter.celltransfer;

import dev.latrox.rematter.chemistry.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CellTransferSelectionTest {
    private static final SubstanceKey IRON=SubstanceKey.of("rematter:iron",Phase.SOLID),GAS=SubstanceKey.of("future:iron",Phase.GAS);
    @Test void allIncludesBothIndependentChannelsAndSelectedModeDoesNotMovePrimalImplicitly(){
        assertTrue(CellTransferSelection.ALL.test(IRON));assertTrue(CellTransferSelection.ALL.includesPrimal());
        var only=new CellTransferSelection(false,false,Set.of(IRON));assertTrue(only.test(IRON));assertFalse(only.test(GAS));assertFalse(only.includesPrimal());
        assertTrue(new CellTransferSelection(false,true,Set.of(IRON)).includesPrimal());
    }
    @Test void aClickFromAllStartsOnlyWithTheClickedKeyWhileReturningToSelectedCanRetainTheOldList(){
        var all=new CellTransferSelection(true,false,Set.of(GAS));assertEquals(Set.of(IRON),all.toggle(IRON).keys());
        var restored=new CellTransferSelection(false,all.primalSelected(),all.keys());assertEquals(Set.of(GAS),restored.keys());
        assertEquals(Set.of(),restored.toggle(GAS).keys());
    }
    @Test void selectionPreservesUnknownIdsAndExactPhasesButNeverOrdinaryPrimal(){
        var solid=SubstanceKey.of("future:iron",Phase.SOLID);var selected=new CellTransferSelection(false,false,Set.of(GAS));
        assertTrue(selected.test(GAS));assertFalse(selected.test(solid));
        assertThrows(IllegalArgumentException.class,()->new CellTransferSelection(false,false,Set.of(new SubstanceKey(ReservedSubstances.PRIMAL_MATTER,Phase.SOLID))));
    }
}
