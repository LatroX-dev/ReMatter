package dev.latrox.rematter.content;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineAccessTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test void onlyTheOwnerOrAnOperatorMayUseAnOwnedMachine() {
        assertTrue(MachineAccess.allows(OWNER, OWNER, false, false));
        assertFalse(MachineAccess.allows(OWNER, STRANGER, false, false));
        assertTrue(MachineAccess.allows(OWNER, STRANGER, true, false));
    }

    @Test void fakePlayersActOnlyForTheOwnerTheyCarry() {
        assertTrue(MachineAccess.allows(OWNER, OWNER, false, true));
        assertFalse(MachineAccess.allows(OWNER, STRANGER, false, true));
        assertFalse(MachineAccess.allows(OWNER, STRANGER, true, true));
        assertFalse(MachineAccess.allows(null, STRANGER, false, true));
    }

    @Test void anUnownedMachineIsClaimedOnlyByARealParticipatingPlayer() {
        assertTrue(MachineAccess.allows(null, STRANGER, false, false));
        assertTrue(MachineAccess.claims(null, false, false));
        assertFalse(MachineAccess.claims(null, true, false));
        assertFalse(MachineAccess.claims(null, false, true));
        assertFalse(MachineAccess.claims(OWNER, false, false));
    }

    @Test void placementAssignsThePlacingPlayerAndNeverAFakePlayer() {
        assertEquals(STRANGER, MachineAccess.placedBy(STRANGER, false));
        assertNull(MachineAccess.placedBy(STRANGER, true));
        assertNull(MachineAccess.placedBy(null, false));
    }
}
