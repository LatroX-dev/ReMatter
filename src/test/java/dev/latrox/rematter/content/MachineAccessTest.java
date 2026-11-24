package dev.latrox.rematter.content;

import dev.latrox.rematter.chemistry.Phase;
import dev.latrox.rematter.chemistry.SubstanceKey;
import dev.latrox.rematter.energy.EnergyCubeTier;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineAccessTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TEAMMATE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test void securityModeRoundTripAndStepping() {
        assertThrows(IllegalArgumentException.class, () -> SecurityMode.load(null));
        assertThrows(IllegalArgumentException.class, () -> SecurityMode.load(""));
        assertThrows(IllegalArgumentException.class, () -> SecurityMode.load("unknown"));
        assertEquals(SecurityMode.PUBLIC, SecurityMode.byIndex(-1));
        assertEquals(SecurityMode.PUBLIC, SecurityMode.byIndex(99));
        assertEquals(SecurityMode.PUBLIC, SecurityMode.load("public"));
        assertEquals(SecurityMode.TRUSTED, SecurityMode.load("trusted"));
        assertEquals(SecurityMode.PRIVATE, SecurityMode.load("private"));
        assertTrue(SecurityMode.validName("public"));
        assertTrue(SecurityMode.validName("trusted"));
        assertTrue(SecurityMode.validName("private"));
        assertFalse(SecurityMode.validName("invalid"));

        assertEquals(SecurityMode.TRUSTED, SecurityMode.PUBLIC.next());
        assertEquals(SecurityMode.PRIVATE, SecurityMode.TRUSTED.next());
        assertEquals(SecurityMode.PUBLIC, SecurityMode.PRIVATE.next());
        assertEquals(SecurityMode.PRIVATE, SecurityMode.PUBLIC.previous());
        assertEquals(SecurityMode.PUBLIC, SecurityMode.TRUSTED.previous());
        assertEquals(SecurityMode.TRUSTED, SecurityMode.PRIVATE.previous());
    }

    @Test void publicTrustedAndPrivatePermissionsMatchMekanismRules() {
        // PUBLIC: everyone can access
        assertTrue(MachineAccess.allows(OWNER, SecurityMode.PUBLIC, OWNER, false, false, false));
        assertTrue(MachineAccess.allows(OWNER, SecurityMode.PUBLIC, STRANGER, false, false, false));
        assertTrue(MachineAccess.allows(OWNER, SecurityMode.PUBLIC, STRANGER, false, true, false));

        // TRUSTED: owner, OP, and trusted/teammates can access; strangers blocked
        assertTrue(MachineAccess.allows(OWNER, SecurityMode.TRUSTED, OWNER, false, false, false));
        assertTrue(MachineAccess.allows(OWNER, SecurityMode.TRUSTED, TEAMMATE, false, false, true));
        assertTrue(MachineAccess.allows(OWNER, SecurityMode.TRUSTED, TEAMMATE, false, true, true));
        assertFalse(MachineAccess.allows(OWNER, SecurityMode.TRUSTED, STRANGER, false, false, false));
        assertFalse(MachineAccess.allows(OWNER, SecurityMode.TRUSTED, STRANGER, false, true, false));
        assertTrue(MachineAccess.allows(OWNER, SecurityMode.TRUSTED, STRANGER, true, false, false));

        // PRIVATE: only owner, owner's fake player, or real OP
        assertTrue(MachineAccess.allows(OWNER, SecurityMode.PRIVATE, OWNER, false, false, false));
        assertFalse(MachineAccess.allows(OWNER, SecurityMode.PRIVATE, TEAMMATE, false, false, true));
        assertFalse(MachineAccess.allows(OWNER, SecurityMode.PRIVATE, STRANGER, false, false, false));
        assertTrue(MachineAccess.allows(OWNER, SecurityMode.PRIVATE, STRANGER, true, false, false));
    }

    @Test void fakePlayersObeyDeviceSecurityAndCannotManageSecurity() {
        assertTrue(MachineAccess.allows(OWNER, SecurityMode.PRIVATE, OWNER, false, true, false));
        assertFalse(MachineAccess.allows(OWNER, SecurityMode.PRIVATE, STRANGER, false, true, false));
        assertFalse(MachineAccess.allows(OWNER, SecurityMode.PRIVATE, STRANGER, true, true, false));
        assertFalse(MachineAccess.allows(null, SecurityMode.PUBLIC, STRANGER, false, true, false));

        // Only real owner or real OP can manage security
        assertTrue(MachineAccess.canManage(OWNER, OWNER, false, false));
        assertTrue(MachineAccess.canManage(OWNER, STRANGER, true, false));
        assertFalse(MachineAccess.canManage(OWNER, TEAMMATE, false, false));
        assertFalse(MachineAccess.canManage(OWNER, OWNER, false, true));
        assertFalse(MachineAccess.canManage(OWNER, STRANGER, true, true));
    }

    @Test void sharedNetworksConnectWhenBothSidesPermitAndIsolateOnPrivate() {
        assertTrue(MachineAccess.canConnect(OWNER, SecurityMode.PRIVATE, OWNER, SecurityMode.PRIVATE, false, false));
        assertTrue(MachineAccess.canConnect(OWNER, SecurityMode.PUBLIC, STRANGER, SecurityMode.PUBLIC, false, false));
        assertTrue(MachineAccess.canConnect(OWNER, SecurityMode.TRUSTED, TEAMMATE, SecurityMode.PUBLIC, true, false));
        assertFalse(MachineAccess.canConnect(OWNER, SecurityMode.TRUSTED, STRANGER, SecurityMode.PUBLIC, false, false));
        assertTrue(MachineAccess.canConnect(OWNER, SecurityMode.TRUSTED, TEAMMATE, SecurityMode.TRUSTED, true, true));
        assertFalse(MachineAccess.canConnect(OWNER, SecurityMode.TRUSTED, TEAMMATE, SecurityMode.TRUSTED, true, false));
        assertFalse(MachineAccess.canConnect(OWNER, SecurityMode.PRIVATE, TEAMMATE, SecurityMode.PUBLIC, true, true));
        assertFalse(MachineAccess.canConnect(null, SecurityMode.PUBLIC, OWNER, SecurityMode.PUBLIC, false, false));
    }

    @Test void anUnownedMachineIsClaimedOnlyByARealParticipatingPlayer() {
        assertTrue(MachineAccess.allows(null, SecurityMode.PRIVATE, STRANGER, false, false, false));
        assertTrue(MachineAccess.claims(null, false, false));
        assertFalse(MachineAccess.claims(null, true, false));
        assertFalse(MachineAccess.claims(null, false, true));
        assertFalse(MachineAccess.claims(OWNER, false, false));
    }

    @Test void trustSavedDataPersistsEntriesLookupAndPreservesUnknownSchema() {
        var data = new SecurityTrustSavedData();
        assertFalse(data.add(OWNER, OWNER, "Self"));
        assertTrue(data.add(OWNER, TEAMMATE, "Alex"));
        assertFalse(data.add(OWNER, TEAMMATE, "Alex"));
        assertTrue(data.isTrusted(OWNER, TEAMMATE));
        assertFalse(data.isTrusted(OWNER, STRANGER));
        assertEquals(TEAMMATE, data.findTrusted(OWNER, "alex").orElseThrow());
        assertEquals(TEAMMATE, data.findTrusted(OWNER, TEAMMATE.toString()).orElseThrow());

        CompoundTag saved = data.toTag();
        var loaded = SecurityTrustSavedData.fromTag(saved);
        assertTrue(loaded.isTrusted(OWNER, TEAMMATE));
        assertEquals("Alex", loaded.nameOf(TEAMMATE).orElseThrow());
        assertTrue(loaded.remove(OWNER, TEAMMATE));
        assertFalse(loaded.isTrusted(OWNER, TEAMMATE));

        loaded.add(OWNER, TEAMMATE, "Alex");
        loaded.add(OWNER, STRANGER, "Sam");
        assertEquals(2, loaded.clear(OWNER));
        assertTrue(loaded.trustedOf(OWNER).isEmpty());

        // Per-player placement default persists and resets cleanly to PUBLIC
        assertEquals(SecurityMode.PUBLIC, loaded.defaultMode(OWNER));
        assertTrue(loaded.setDefaultMode(OWNER, SecurityMode.TRUSTED));
        assertEquals(SecurityMode.TRUSTED, SecurityTrustSavedData.fromTag(loaded.toTag()).defaultMode(OWNER));
        assertTrue(loaded.setDefaultMode(OWNER, SecurityMode.PRIVATE));
        assertEquals(SecurityMode.PRIVATE, SecurityTrustSavedData.fromTag(loaded.toTag()).defaultMode(OWNER));
        assertTrue(loaded.setDefaultMode(OWNER, SecurityMode.PUBLIC));
        assertEquals(SecurityMode.PUBLIC, SecurityTrustSavedData.fromTag(loaded.toTag()).defaultMode(OWNER));
        assertFalse(loaded.toTag().contains("defaults"));

        // Unknown future schema is preserved unchanged and refuses mutation
        CompoundTag future = new CompoundTag();
        future.putInt("schema", 99);
        future.putString("future_field", "keep");
        var preserved = SecurityTrustSavedData.fromTag(future);
        assertFalse(preserved.add(OWNER, TEAMMATE, "Alex"));
        assertFalse(preserved.setDefaultMode(OWNER, SecurityMode.PRIVATE));
        assertEquals(future, preserved.toTag());
    }

    @Test void securityModeColorsAndCleanConfigurationCardRoundTrips() {
        assertEquals(net.minecraft.ChatFormatting.GREEN, SecurityMode.PUBLIC.color());
        assertEquals(net.minecraft.ChatFormatting.AQUA, SecurityMode.TRUSTED.color());
        assertEquals(net.minecraft.ChatFormatting.RED, SecurityMode.PRIVATE.color());

        // Machine setup round-trip remains purely technical (security lives on Security Card)
        var machineSetup = new ConfigurationCardData.MachineSetup(
                MachineKind.DECOMPOSER, 0, RedstoneMode.IGNORED, false, false, true, false,
                "", new MachineSettings(0, "", "", Set.of()), MachineSoundLevel.HIGH, null, false, true);
        var machineCard = new ConfigurationCardData(ConfigurationCardData.fromMachine(machineSetup).copyTag());
        assertEquals(machineSetup, machineCard.machineSetup().orElseThrow());

        // Energy cube and level emitter round-trips
        var cubeCard = new ConfigurationCardData(ConfigurationCardData.fromEnergyCube(EnergyCubeTier.REINFORCED, 5).copyTag());
        assertEquals(5, cubeCard.energyCubeSetup().orElseThrow().packedSideModes());

        var emitterSetup = new ConfigurationCardData.EmitterSetup(
                SubstanceKey.of("rematter:iron", Phase.SOLID), 1000L, true);
        var emitterCard = new ConfigurationCardData(ConfigurationCardData.fromLevelEmitter(emitterSetup).copyTag());
        assertEquals(emitterSetup, emitterCard.levelEmitterSetup().orElseThrow());
    }
}
