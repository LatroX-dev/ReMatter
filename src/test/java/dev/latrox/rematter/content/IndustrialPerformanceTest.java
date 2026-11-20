package dev.latrox.rematter.content;

import dev.latrox.rematter.content.preparation.PreparationStep;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialPerformanceTest {
    @Test void hardwareAddsCapacityWithoutAnAdditionalSpeedOrEnergyRole() {
        for(int count=1;count<=98;count++){
            var prep=new IndustrialPerformance(count,count,count,0,0);
            var reactor=new IndustrialPerformance(0,0,0,count,count);
            assertEquals(4,prep.workRate(MachineKind.INDUSTRIAL_PREPARATOR));
            assertEquals(2,reactor.workRate(MachineKind.INDUSTRIAL_REACTOR));
            assertEquals(count,reactor.field());assertEquals(count,prep.precision());
            assertEquals(16_000L*count,reactor.processRoom());
            assertEquals(0,prep.energySavingPercent(MachineKind.INDUSTRIAL_PREPARATOR));
            assertEquals(0,reactor.energySavingPercent(MachineKind.INDUSTRIAL_REACTOR));
            for(long price:new long[]{0,1,99,1_000_000,Long.MAX_VALUE})assertEquals(price,reactor.energyCost(MachineKind.INDUSTRIAL_REACTOR,price));
        }
    }
    @Test void recipeStepsShareOnlyTheirRequiredHardware() {
        var prep=new IndustrialPerformance(4,2,1,0,0);
        assertEquals(4,prep.jobCapacity(Set.of(PreparationStep.GRIND)));
        assertEquals(2,prep.jobCapacity(Set.of(PreparationStep.GRIND,PreparationStep.MIX)));
        assertEquals(1,prep.jobCapacity(Set.of(PreparationStep.SEPARATE)));
        assertEquals(0,prep.jobCapacity(Set.of()));
        var reactor=new IndustrialPerformance(0,0,0,8,1);
        assertTrue(reactor.supportsInputUnits(15_000));assertFalse(reactor.supportsInputUnits(60_000));
        assertTrue(new IndustrialPerformance(0,0,0,8,4).supportsInputUnits(60_000));
        assertFalse(reactor.supportsInputUnits(-1));
    }
    @Test void heightOpensWorkersWithoutCreatingFreeParallelCharges() {
        for(var kind:new MachineKind[]{MachineKind.INDUSTRIAL_REACTOR,MachineKind.INDUSTRIAL_PREPARATOR}){
            int minimum=IndustrialStructure.height(kind);
            assertEquals(0,IndustrialStructure.laneRoom(kind,minimum-1));
            for(int extra=0;extra<=4;extra++)assertEquals(Math.min(4,1+extra),IndustrialStructure.laneRoom(kind,minimum+extra));
        }
    }
    @Test void incompleteAndInvalidHardwareCannotWork() {
        assertEquals(0,new IndustrialPerformance(2,0,1,0,0).workRate(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(0,new IndustrialPerformance(0,0,0,0,3).workRate(MachineKind.INDUSTRIAL_REACTOR));
        assertThrows(IllegalArgumentException.class,()->new IndustrialPerformance(99,1,1,0,0));
        assertThrows(IllegalArgumentException.class,()->IndustrialPerformance.EMPTY.energyCost(MachineKind.INDUSTRIAL_REACTOR,-1));
    }
}
