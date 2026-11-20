package dev.latrox.rematter.automation;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomationBackendsTest {
    @Test void cyclingVisitsEveryInstalledBackend() {
        assertEquals(Optional.of("rs"), AutomationBackends.after(List.of("ae2","rs"),"ae2"));
        assertEquals(Optional.of("ae2"), AutomationBackends.after(List.of("ae2","rs"),"rs"));
        assertEquals(Optional.empty(), AutomationBackends.after(List.of("rs"),"rs"));
        assertEquals(Optional.empty(), AutomationBackends.after(List.of(),""));
    }
    @Test void aBackendWhoseModIsGoneCanSwitchToTheRemainingOne() {
        assertEquals(Optional.of("rs"), AutomationBackends.after(List.of("rs"),"ae2"));
        assertEquals(Optional.empty(), AutomationBackends.after(List.of(),"ae2"));
    }
}
