package dev.latrox.rematter.content;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndustrialPerformanceTest {
    @Test void everyInstalledProcessCoreAddsWorkWithoutCreatingParallelRecipeOutputs() {
        var baseline = new IndustrialPerformance(1, 1, 1, 0, 0);
        assertEquals(4, baseline.workRate(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(6, new IndustrialPerformance(2, 1, 1, 0, 0).workRate(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(6, new IndustrialPerformance(1, 2, 1, 0, 0).workRate(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(2, new IndustrialPerformance(0, 0, 0, 1, 1).workRate(MachineKind.INDUSTRIAL_REACTOR));
        assertEquals(6, new IndustrialPerformance(0, 0, 0, 1, 3).workRate(MachineKind.INDUSTRIAL_REACTOR));
        assertEquals(0, new IndustrialPerformance(2, 0, 1, 0, 0).workRate(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(0, new IndustrialPerformance(0, 0, 0, 0, 3).workRate(MachineKind.INDUSTRIAL_REACTOR));
    }

    @Test void supportSavingsDiminishAndNeverChangeTheWorkRate() {
        int previous = -1;
        for (int count = 1; count <= 98; count++) {
            var prep = new IndustrialPerformance(1, 1, count, 0, 0);
            var reactor = new IndustrialPerformance(0, 0, 0, count, 1);
            int saving = prep.energySavingPercent(MachineKind.INDUSTRIAL_PREPARATOR);
            assertTrue(saving >= previous && saving < 25);
            assertEquals(saving, reactor.energySavingPercent(MachineKind.INDUSTRIAL_REACTOR));
            assertEquals(4, prep.workRate(MachineKind.INDUSTRIAL_PREPARATOR));
            assertEquals(2, reactor.workRate(MachineKind.INDUSTRIAL_REACTOR));
            previous = saving;
        }
        assertEquals(0, new IndustrialPerformance(1, 1, 1, 0, 0).energySavingPercent(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(6, new IndustrialPerformance(1, 1, 2, 0, 0).energySavingPercent(MachineKind.INDUSTRIAL_PREPARATOR));
        assertEquals(10, new IndustrialPerformance(1, 1, 3, 0, 0).energySavingPercent(MachineKind.INDUSTRIAL_PREPARATOR));
    }

    @Test void energyDebitsRoundUpWithoutOverflowOrZeroEnergyPositiveRecipes() {
        var hardware = new IndustrialPerformance(0, 0, 0, 49, 1);
        for (long energy : new long[]{0, 1, 2, 99, 100, 101, 1_000_000, Long.MAX_VALUE}) {
            long debit = hardware.energyCost(MachineKind.INDUSTRIAL_REACTOR, energy);
            var exact = java.math.BigInteger.valueOf(energy).multiply(java.math.BigInteger.valueOf(77));
            long expected = exact.add(java.math.BigInteger.valueOf(99)).divide(java.math.BigInteger.valueOf(100)).longValueExact();
            assertEquals(expected, debit);
            if (energy > 0) assertTrue(debit > 0);
        }
        assertThrows(IllegalArgumentException.class, () -> hardware.energyCost(MachineKind.INDUSTRIAL_REACTOR, -1));
        assertThrows(IllegalArgumentException.class, () -> new IndustrialPerformance(99, 1, 1, 0, 0));
    }
}
