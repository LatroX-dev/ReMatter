package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.MatterInventory;
import dev.latrox.rematter.chemistry.MatterNetworkInventory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static dev.latrox.rematter.chemistry.BuiltinChemistry.COPPER;
import static org.junit.jupiter.api.Assertions.*;

class MachineStorageSessionTest {
    @Test void publicTierWordPreservesPrimalCodesInEveryBay() {
        for (int bay = 0; bay < MachineCellAccess.DRIVE_BAYS; bay++) {
            for (int tier = 0; tier < 8; tier++) {
                int packed = 0;
                for (int other = 0; other < MachineCellAccess.DRIVE_BAYS; other++)
                    packed |= (other == bay ? tier : 4) << (other * 3);
                assertEquals(tier, MachineCellAccess.decodeTier(packed, bay), "Tier " + tier + " in bay " + bay);
            }
        }
        assertEquals(0, MachineCellAccess.decodeTier(-1, -1));
        assertEquals(0, MachineCellAccess.decodeTier(-1, MachineCellAccess.DRIVE_BAYS));
    }

    @Test void sixDriveBaysHaveDistinctStableIndices() {
        assertEquals(6, MachineCellAccess.bayCount(MachineKind.DRIVE));
        var slots = new java.util.HashSet<Integer>();
        for (int bay = 0; bay < 6; bay++) {
            int slot = MachineCellAccess.slotForBay(MachineKind.DRIVE, bay);
            assertTrue(slots.add(slot));
            assertEquals(bay, MachineCellAccess.bayForSlot(MachineKind.DRIVE, slot));
            assertTrue(slot < MachineCellAccess.inventorySize(MachineKind.DRIVE));
        }
        assertEquals(0, MachineCellAccess.slotForBay(MachineKind.DRIVE, 0));
        assertEquals(java.util.Set.of(0,5,6,7,8,9), slots);
        for (int reserved = 1; reserved <= 4; reserved++) assertFalse(MachineCellAccess.isCellSlot(MachineKind.DRIVE, reserved));
        assertThrows(IndexOutOfBoundsException.class, () -> MachineCellAccess.slotForBay(MachineKind.DRIVE, 6));
    }
    @Test void onlyTheDriveExposesCellBays() {
        for (var kind : MachineKind.values()) {
            if (kind.isStorage()) continue;
            assertEquals(-1, MachineCellAccess.slot(kind));
            assertEquals(0, MachineCellAccess.bayCount(kind));
        }
        for (var kind : new MachineKind[]{MachineKind.DECOMPOSER,MachineKind.SYNTHESIZER}) {
            assertEquals(MachineCellAccess.INVENTORY_SIZE,MachineCellAccess.inventorySize(kind));
        }
        assertEquals(0,MachineCellAccess.slot(MachineKind.DRIVE));
        assertEquals(MachineCellAccess.INVENTORY_SIZE,MachineCellAccess.inventorySize(MachineKind.DRIVE));
    }
    @Test void sameContentsInAnotherCellCannotTakeOverAnInProgressBatch() {
        var original=new MatterInventory(64000,512);original.insert(COPPER,1000);
        var replacement=MatterInventory.restore(original.snapshot(),512);
        var session=new MachineStorageSession();session.bind(original);
        assertFalse(session.accepts(replacement));
        assertThrows(IllegalStateException.class,()->session.bind(replacement));
        assertEquals(1000,original.amount(COPPER));assertEquals(1000,replacement.amount(COPPER));
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
    @Test void rediscoveryWrapperRetainsMembersButSplitsMergesAndReplacementDoNot() {
        var a = new MatterInventory(64000,512);
        var b = new MatterInventory(64000,512);
        var c = new MatterInventory(64000,512);
        a.insert(COPPER,600); b.insert(COPPER,400);
        var session = new MachineStorageSession();
        session.bind(new MatterNetworkInventory(List.of(a,b)));
        assertTrue(session.accepts(new MatterNetworkInventory(List.of(a,b))));
        assertFalse(session.accepts(new MatterNetworkInventory(List.of(a))));
        assertFalse(session.accepts(new MatterNetworkInventory(List.of(a,b,c))));
        assertFalse(session.accepts(new MatterNetworkInventory(List.of(a,MatterInventory.restore(b.snapshot(),512)))));
        assertEquals(600,a.amount(COPPER));
        assertEquals(400,b.amount(COPPER));
        assertEquals(0,c.total());
    }

}
