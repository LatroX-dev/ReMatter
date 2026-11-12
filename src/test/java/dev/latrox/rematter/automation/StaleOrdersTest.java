package dev.latrox.rematter.automation;

import dev.latrox.rematter.automation.AutomationPort.*;
import dev.latrox.rematter.chemistry.BuiltinChemistry;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaleOrdersTest {
    private static final Resource WATER=Resource.matter(BuiltinChemistry.WATER), HYDROGEN=Resource.matter(BuiltinChemistry.HYDROGEN);
    private static final Recipe ORIGINAL=AutomationRecipeCatalogue.recipe("rematter:automation/process/rematter/electrolysis","chemical_reactor",
            Map.of(WATER,1000L),Map.of(HYDROGEN,1000L),4000,20);

    @Test void aVanishedRecipeRefundsTheExactTransferredInputsAfterTheGracePeriod() {
        var ledger=accepted(3);var stale=new StaleOrders(200);var released=new ArrayList<UUID>();
        var id=ledger.next().orElseThrow().id();
        var worker=UUID.randomUUID();assertTrue(ledger.claim(id,worker,ORIGINAL.revision()));
        assertFalse(stale.retire(ledger,recipeId->null,1_000,released::add));
        assertTrue(stale.waiting());
        assertFalse(stale.retire(ledger,recipeId->null,1_199,released::add));
        assertTrue(ledger.pendingRefunds().isEmpty(),"A transient reload gap does not cancel anything");
        assertTrue(stale.retire(ledger,recipeId->null,1_200,released::add));
        assertEquals(List.of(id),released,"A live worker lease is released before cancelling");
        assertFalse(stale.waiting());
        assertEquals(List.of(new Delivery(id,WATER,3000L)),ledger.pendingRefunds());
        assertEquals(0,ledger.remainingBatches());assertFalse(ledger.busy());
        assertFalse(stale.retire(ledger,recipeId->null,5_000,released::add),"A cancelled order is not cancelled twice");
    }

    @Test void aRecipeThatReturnsInTimeKeepsItsOrders() {
        var ledger=accepted(1);var stale=new StaleOrders(200);
        assertFalse(stale.retire(ledger,recipeId->null,0,ignored->fail()));
        assertFalse(stale.retire(ledger,Map.of(ORIGINAL.id(),ORIGINAL)::get,100,ignored->fail()));
        assertFalse(stale.waiting());
        assertFalse(stale.retire(ledger,recipeId->null,250,ignored->fail()),"The grace period restarts after a recovery");
        assertTrue(ledger.pendingRefunds().isEmpty());
    }

    @Test void processParameterChangesContinueAndMaterialChangesAreRefunded() {
        var ledger=accepted(2);var stale=new StaleOrders(0);
        var balanced=AutomationRecipeCatalogue.recipe(ORIGINAL.id(),ORIGINAL.workerKind(),ORIGINAL.inputs(),ORIGINAL.outputs(),8000,40);
        assertTrue(stale.retire(ledger,Map.of(ORIGINAL.id(),balanced)::get,0,ignored->fail()));
        assertEquals(balanced,ledger.next().orElseThrow().recipe());assertTrue(ledger.pendingRefunds().isEmpty());
        var recomposed=AutomationRecipeCatalogue.recipe(ORIGINAL.id(),ORIGINAL.workerKind(),Map.of(WATER,750L),ORIGINAL.outputs(),8000,40);
        assertTrue(stale.retire(ledger,Map.of(ORIGINAL.id(),recomposed)::get,0,ignored->fail()));
        assertEquals(2000,ledger.pendingRefunds().getFirst().amount());
    }

    private static OrderLedger accepted(int batches) {
        var ledger=new OrderLedger();
        assertTrue(ledger.accept(ORIGINAL,batches,ResourceAmounts.multiply(ORIGINAL.inputs(),batches),Action.EXECUTE));
        return ledger;
    }
}
