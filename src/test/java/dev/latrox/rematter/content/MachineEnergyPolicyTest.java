package dev.latrox.rematter.content;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineEnergyPolicyTest {
    @Test void speedUpgradesMultiplyTheBaseRateUpToTheSlotLimit() {
        assertEquals(2, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.NONE, 4));
        assertEquals(4, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.SPEED, 1));
        assertEquals(10, MachineEnergyPolicy.speed(2, MachineEnergyPolicy.Upgrade.SPEED, 9));
        assertEquals(1, MachineEnergyPolicy.speed(1, MachineEnergyPolicy.Upgrade.EFFICIENCY, 4));
    }

    @Test void speedRaisesTheTotalPriceAndNeverRoundsItDown() {
        assertEquals(1000, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.NONE, 0, MachineKind.SYNTHESIZER, null));
        assertEquals(1250, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.SPEED, 1, MachineKind.SYNTHESIZER, null));
        assertEquals(2000, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.SPEED, 4, MachineKind.SYNTHESIZER, null));
        assertEquals(2000, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.SPEED, 64, MachineKind.SYNTHESIZER, null));
        for (long base = 1; base < 40; base++)
            assertTrue(MachineEnergyPolicy.cost(base, MachineEnergyPolicy.Upgrade.SPEED, 1, MachineKind.REACTOR, null) >= base);
    }

    @Test void efficiencySavesAtMostTwentyPercent() {
        assertEquals(950, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.EFFICIENCY, 1, MachineKind.DECOMPOSER, null));
        assertEquals(800, MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.EFFICIENCY, 9, MachineKind.DECOMPOSER, null));
    }

    @Test void industrialSupportsApplyAfterUpgradesAndTierMultipliersSaturate() {
        var supports = new IndustrialPerformance(0, 0, 0, 2, 1);
        long upgraded = MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.SPEED, 4, MachineKind.INDUSTRIAL_REACTOR, null);
        assertEquals(supports.energyCost(MachineKind.INDUSTRIAL_REACTOR, upgraded),
                MachineEnergyPolicy.cost(1000, MachineEnergyPolicy.Upgrade.SPEED, 4, MachineKind.INDUSTRIAL_REACTOR, supports));
        assertEquals(2000, MachineEnergyPolicy.scaled(1000, 2));
        assertEquals(0, MachineEnergyPolicy.scaled(1000, 0));
        assertEquals(Long.MAX_VALUE, MachineEnergyPolicy.scaled(Long.MAX_VALUE / 2 + 1, 2));
        assertThrows(IllegalArgumentException.class, () -> MachineEnergyPolicy.scaled(-1, 2));
    }
}
