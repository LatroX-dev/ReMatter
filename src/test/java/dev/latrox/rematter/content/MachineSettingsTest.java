package dev.latrox.rematter.content;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineSettingsTest {
    @Test void targetsKeepSubstanceAndPhaseTogetherAndRejectOverflow() {
        assertThrows(IllegalArgumentException.class,()->new MachineSettings(1,"","gas",Set.of()));
        assertThrows(IllegalArgumentException.class,()->new MachineSettings(1,"rematter:water","unknown",Set.of()));
        assertThrows(IllegalArgumentException.class,()->new MachineSettings(Long.MAX_VALUE,"","",Set.of()));
        assertDoesNotThrow(()->new MachineSettings(16000,"rematter:water","liquid",Set.of()));
    }
    @Test void aGasCannotBeBothDiscardedAndTheActiveStockGoal() {
        assertThrows(IllegalArgumentException.class,()->new MachineSettings(1000,"rematter:oxygen","gas",Set.of("rematter:oxygen")));
        assertDoesNotThrow(()->new MachineSettings(0,"rematter:oxygen","gas",Set.of("rematter:oxygen")));
        assertDoesNotThrow(()->new MachineSettings(1000,"rematter:hydrogen","gas",Set.of("rematter:oxygen")));
    }
    @Test void networkReserveValidatesBoundsAndDefaultsToZero() {
        assertEquals(0L, new MachineSettings(0, "", "", Set.of()).networkReserve());
        assertEquals(50_000L, new MachineSettings(0, "", "", Set.of(), 50_000L).networkReserve());
        assertThrows(IllegalArgumentException.class, () -> new MachineSettings(0, "", "", Set.of(), -1L));
        assertThrows(IllegalArgumentException.class, () -> new MachineSettings(0, "", "", Set.of(), MachineSettings.MAX_TARGET + 1L));
    }
}
