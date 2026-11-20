package dev.latrox.rematter.content;

import dev.latrox.rematter.content.preparation.PreparationTier;
import dev.latrox.rematter.content.preparation.PreparationWork;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialProcessingTest {
    @Test void allThreeFixedRolesNeedTheirOwnFullQuantity() {
        assertTrue(PreparationWork.hasRequiredCounts(3,1,1,3,1,1));
        assertTrue(PreparationWork.hasRequiredCounts(6,2,2,3,1,1));
        assertFalse(PreparationWork.hasRequiredCounts(2,2,1,3,1,1));
        assertFalse(PreparationWork.hasRequiredCounts(3,2,0,3,1,1));
        assertFalse(PreparationWork.hasRequiredCounts(3,0,2,3,1,1));
        assertTrue(PreparationWork.hasRequiredCounts(1,0,0,1,0,0));
    }
    @Test void ordinaryPreparationAndHeatTreatmentAreDifferentProcesses() {
        for(var kind:new MachineKind[]{MachineKind.HAND_PREPARATOR,MachineKind.PREPARATOR,MachineKind.INDUSTRIAL_PREPARATOR}) {
            assertTrue(PreparationTier.permits(kind,0,"preparation"));
            assertFalse(PreparationTier.permits(kind,0,"sintering"));
            assertFalse(PreparationTier.permits(kind,1,"sintering"));
        }
        assertFalse(PreparationTier.permits(MachineKind.HAND_PREPARATOR,1,"preparation"));
        assertTrue(PreparationTier.permits(MachineKind.SINTER_FURNACE,1,"sintering"));
        assertFalse(PreparationTier.permits(MachineKind.SINTER_FURNACE,0,"preparation"));
        assertFalse(PreparationTier.permits(MachineKind.SINTER_FURNACE,1,"unknown"));
        assertFalse(PreparationTier.permits(MachineKind.DECOMPOSER,0,"sintering"));
    }
    @Test void kilnOnlyAcceptsPreparedMaterialAndNeverSkipsMixing() {
        assertDoesNotThrow(()->PreparationWork.validateInputs(1,1,1,false,false,"sintering"));
        assertDoesNotThrow(()->PreparationWork.validateInputs(3,1,1,true,true,"preparation"));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validateInputs(3,1,1,true,true,"sintering"));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validateInputs(1,1,1,false,true,"preparation"));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validateInputs(0,1,1,true,true,"preparation"));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validateInputs(1,65,1,true,true,"preparation"));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validateInputs(1,1,-1,true,true,"preparation"));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validateInputs(1,1,1,false,false,"unknown"));
    }
    @Test void addedInputDoesNotBecomeACellOrShiftExistingMachineInventories() {
        for(var kind:new MachineKind[]{MachineKind.PREPARATOR,MachineKind.HAND_PREPARATOR,MachineKind.INDUSTRIAL_PREPARATOR,MachineKind.SINTER_FURNACE}) {
            assertEquals(MachineCellAccess.INVENTORY_SIZE,MachineCellAccess.inventorySize(kind));
            assertEquals(-1,MachineCellAccess.slot(kind));
        }
        assertEquals(MachineCellAccess.INVENTORY_SIZE,MachineCellAccess.inventorySize(MachineKind.DECOMPOSER));
        assertEquals(-1,MachineCellAccess.slot(MachineKind.DECOMPOSER));
        assertEquals(MachineCellAccess.INVENTORY_SIZE,MachineCellAccess.inventorySize(MachineKind.DRIVE));
        assertEquals(0,MachineCellAccess.slot(MachineKind.DRIVE));
    }
    @Test void kilnStagesDescribeItsActualWork() {
        assertEquals("pressing",PreparationWork.stageName(0,false,true));
        assertEquals("sintering",PreparationWork.stageName(1,false,true));
        assertEquals("cooling",PreparationWork.stageName(2,false,true));
        assertEquals("mixing",PreparationWork.stageName(1,true,false));
        assertEquals(1,PreparationTier.energyMultiplier(MachineKind.SINTER_FURNACE));
    }
}
