package dev.latrox.rematter.content;

import dev.latrox.rematter.content.preparation.PreparationWork;
import dev.latrox.rematter.content.preparation.PreparationTier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreparationWorkTest {
    @Test void threeActualStagesCoverExactlyFortyBaseWorkTicksEach() {
        int[] ticks=new int[3];
        for(int progress=1;progress<=120;progress++)ticks[PreparationWork.stage(progress,120)]++;
        assertArrayEquals(new int[]{40,40,40},ticks);
        assertEquals(-1,PreparationWork.stage(0,120));assertEquals(-1,PreparationWork.stage(121,120));
    }
    @Test void stageCalculationUsesCheckedBoundsWithoutMultiplicationOverflow() {
        assertEquals(2,PreparationWork.stage(Integer.MAX_VALUE,Integer.MAX_VALUE));
        assertEquals(0,PreparationWork.stage(1,3));assertEquals(1,PreparationWork.stage(2,3));assertEquals(2,PreparationWork.stage(3,3));
    }
    @Test void bothPhysicalInputsMustExistBeforeAnyBatchCanBeReady() {
        assertEquals("input",PreparationWork.readiness(false,true,true,true,true,4000,4000));
        assertEquals("secondary_input",PreparationWork.readiness(true,false,true,true,true,4000,4000));
        assertEquals("unsupported_components",PreparationWork.readiness(true,true,false,true,true,4000,4000));
        assertEquals("unsupported_input",PreparationWork.readiness(true,true,true,false,true,4000,4000));
    }
    @Test void OutputAndPowerAreIndependentWholeBatchPrerequisites() {
        assertEquals("output_blocked",PreparationWork.readiness(true,true,true,true,false,0,4000));
        assertEquals("energy",PreparationWork.readiness(true,true,true,true,true,3999,4000));
        assertEquals("ready",PreparationWork.readiness(true,true,true,true,true,4000,4000));
    }
    @Test void recipeCannotOverflowAStackOrChargeImpossibleOrNonpositiveWork() {
        assertDoesNotThrow(()->PreparationWork.validate(4,64,4000,120));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validate(65,64,4000,120));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validate(2,1,4000,120));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validate(0,64,4000,120));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validate(4,64,0,120));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validate(4,64,1_000_001,120));
        assertDoesNotThrow(()->PreparationWork.validate(4,64,PreparationWork.MAX_ENERGY,120));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validate(4,64,PreparationWork.MAX_ENERGY+1,120));
        assertThrows(IllegalArgumentException.class,()->PreparationWork.validate(4,64,4000,2));
    }
    @Test void secondaryFeedIsNeverTreatedAsALocalMatterCell() {
        for(var kind:new MachineKind[]{MachineKind.PREPARATOR,MachineKind.HAND_PREPARATOR,MachineKind.INDUSTRIAL_PREPARATOR}){
            assertEquals(MachineCellAccess.INVENTORY_SIZE,MachineCellAccess.inventorySize(kind));assertEquals(-1,MachineCellAccess.slot(kind));
            assertEquals(0,MachineCellAccess.bayCount(kind));
        }
    }
    @Test void singleIngredientExtractionRejectsStraySecondaryInsteadOfIgnoringOrConsumingIt() {
        assertEquals("secondary_slot_occupied",PreparationWork.readiness(true,true,false,true,true,true,4000,2000));
        assertEquals("ready",PreparationWork.readiness(true,false,false,true,true,true,2000,2000));
        assertEquals("secondary_input",PreparationWork.readiness(true,false,true,true,true,true,4000,4000));
    }
    @Test void manualTierNeverSpendsPowerOrPermitsAdvancedFractions() {
        assertTrue(PreparationTier.permits(MachineKind.HAND_PREPARATOR,0));
        assertFalse(PreparationTier.permits(MachineKind.HAND_PREPARATOR,1));
        assertEquals(0,PreparationTier.energyMultiplier(MachineKind.HAND_PREPARATOR));
        assertEquals(5,(360+PreparationWork.CRANK_WORK-1)/PreparationWork.CRANK_WORK);
        assertEquals(6,(480+PreparationWork.CRANK_WORK-1)/PreparationWork.CRANK_WORK);
        assertEquals(5,PreparationWork.CRANK_COOLDOWN);
        assertEquals("ready",PreparationWork.readiness(true,false,false,true,true,true,0,0));
    }
    @Test void industrialThroughputPaysMorePerBatchAndDoesNotChangeMaterialYield() {
        assertEquals(4,PreparationTier.speed(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(2,PreparationTier.energyMultiplier(MachineKind.INDUSTRIAL_PREPARATOR));
        assertTrue(PreparationTier.permits(MachineKind.INDUSTRIAL_PREPARATOR,1));
        assertEquals("dosing",PreparationWork.stageName(0,true));assertEquals("packing",PreparationWork.stageName(2,true));
        assertEquals("grinding",PreparationWork.stageName(0,false));assertEquals("collecting",PreparationWork.stageName(2,false));
    }
}
