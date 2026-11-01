package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.COPPER;
import static org.junit.jupiter.api.Assertions.*;

class MachineStorageSessionTest {
    @Test void oldProcessorCellSlotsRemainRecoverableButNeverOverrideNetworkStorage() {
        for (var kind : new MachineKind[]{MachineKind.DECOMPOSER,MachineKind.SYNTHESIZER}) {
            assertEquals(3,MachineCellAccess.slot(kind));
            assertEquals(4,MachineCellAccess.inventorySize(kind));
            // A saved full or unknown cell is recovery-only, never another active resource owner.
            assertFalse(MachineCellAccess.usesOwnStorage(kind,true));
            assertFalse(MachineCellAccess.usesOwnStorage(kind,false));
        }
        assertEquals(0,MachineCellAccess.slot(MachineKind.DRIVE));
        assertEquals(0,MachineCellAccess.slot(MachineKind.STORAGE));
        assertEquals(3,MachineCellAccess.inventorySize(MachineKind.DRIVE));
        assertTrue(MachineCellAccess.usesOwnStorage(MachineKind.DRIVE,false));
        assertTrue(MachineCellAccess.usesOwnStorage(MachineKind.STORAGE,false));
        assertEquals(-1,MachineCellAccess.slot(MachineKind.ANALYZER));
    }
    @Test void sameContentsInAnotherCellCannotTakeOverAnInProgressBatch() {
        var original=new MatterInventory(64000,512);original.insert(COPPER,1000);
        var replacement=MatterInventory.restore(original.snapshot(),512);
        var session=new MachineStorageSession();session.bind(original);
        assertFalse(session.accepts(replacement));
        assertThrows(IllegalStateException.class,()->session.bind(replacement));
        assertEquals(1000,original.amount(COPPER));assertEquals(1000,replacement.amount(COPPER));
        assertTrue(original.snapshot().reservations().isEmpty());
        session.clear();session.bind(replacement);assertTrue(session.accepts(replacement));
    }
    @Test void rollbackRetainsTheBoundIdentityAndExactStock() {
        var inventory=new MatterInventory(64000,512);inventory.insert(COPPER,1000);
        var before=inventory.snapshot();var session=new MachineStorageSession();session.bind(inventory);
        assertTrue(inventory.apply(Map.of(COPPER,17L),Map.of()));
        inventory.restoreInPlace(before);
        assertTrue(session.accepts(inventory));assertEquals(before,inventory.snapshot());
    }
    @Test void pausingForCellChangeRetainsFiniteOrdersAndDoesNotConsumeStock() {
        var original=new MatterInventory(64000,512);original.insert(COPPER,2000);
        var before=original.snapshot();var session=new MachineStorageSession();session.bind(original);
        var queued=MachineRunState.manual().requestBatch().requestBatch();
        var paused=queued.pause();session.clear();
        assertFalse(paused.running());assertEquals(2,paused.queuedBatches());
        assertEquals(before,original.snapshot());
        var newStorage=new MatterInventory(64000,512);session.bind(newStorage);
        assertEquals(0,newStorage.total());assertEquals(2000,original.total());
    }
}
