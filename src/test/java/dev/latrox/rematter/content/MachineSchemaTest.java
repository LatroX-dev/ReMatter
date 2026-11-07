package dev.latrox.rematter.content;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineSchemaTest {
    private static final String OWNER = "00000000-0000-0000-0000-000000000001";

    @Test void onlyTheCurrentSchemaWithACompleteInventoryIsRead() {
        assertNull(MachineSchema.rejection(Optional.of(MachineSchema.VERSION), Optional.of(OWNER), Optional.of(3), 3));
        assertNull(MachineSchema.rejection(Optional.of(MachineSchema.VERSION), Optional.empty(), Optional.of(10), 10));
        assertNotNull(MachineSchema.rejection(Optional.empty(), Optional.empty(), Optional.of(3), 3));
        assertNotNull(MachineSchema.rejection(Optional.of(MachineSchema.VERSION + 1), Optional.empty(), Optional.of(3), 3));
        assertNotNull(MachineSchema.rejection(Optional.of(0), Optional.empty(), Optional.of(3), 3));
    }

    @Test void surplusMissingOrUnreadableSlotsAreNeverTruncated() {
        assertNotNull(MachineSchema.rejection(Optional.of(MachineSchema.VERSION), Optional.empty(), Optional.of(10), 3));
        assertNotNull(MachineSchema.rejection(Optional.of(MachineSchema.VERSION), Optional.empty(), Optional.of(2), 3));
        assertNotNull(MachineSchema.rejection(Optional.of(MachineSchema.VERSION), Optional.empty(), Optional.empty(), 3));
    }

    @Test void aMalformedOwnerKeepsTheDataOpaqueInsteadOfLockingEveryone() {
        assertNotNull(MachineSchema.rejection(Optional.of(MachineSchema.VERSION), Optional.of("not-a-uuid"), Optional.of(3), 3));
        assertEquals(UUID.fromString(OWNER), MachineSchema.owner(OWNER));
        assertNull(MachineSchema.owner("1-1-1-1-1"));
        assertNull(MachineSchema.owner(""));
    }

    @Test void storedSelectionsMustBeBoundedChoiceIdentifiers() {
        assertEquals("minecraft:copper_ingot", MachineSchema.selection("minecraft:copper_ingot"));
        assertEquals("fluid:minecraft:water", MachineSchema.selection("fluid:minecraft:water"));
        assertEquals("", MachineSchema.selection(""));
        assertEquals("", MachineSchema.selection("minecraft:" + "x".repeat(MachineSnapshot.MAX_CHOICE_ID_LENGTH)));
        assertEquals("", MachineSchema.selection("Not A Choice"));
        assertEquals("", MachineSchema.selection("fluid:"));
        assertEquals("", MachineSchema.selection("copper"));
    }
}
